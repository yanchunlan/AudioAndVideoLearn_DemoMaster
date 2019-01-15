package com.example.advd.audioandvideolearn_demo_master.base_05;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * author: ycl
 * date: 2018-12-17 21:59
 * desc:
 */
public class CameraRecorder {

    private static final String TAG = "CameraRecorder";
    private final Object lock = new Object();

    private MediaMuxer mMuxer; // 混合
    private String path;        //文件保存的路径
    private String postfix;     //文件后缀

    // audio
    private String audioMime = MediaFormat.MIMETYPE_AUDIO_AAC;   //音频编码的Mime
    private AudioRecord mRecorder;   //录音器
    private MediaCodec mAudioEnc;   //编码器，用于音频编码
    private int audioRate = 128000;   //音频编码的密钥比特率
    private int sampleRate = 48000;   //音频采样率
    private int channelCount = 2;     //音频编码通道数
    private int channelConfig = AudioFormat.CHANNEL_IN_STEREO;   //音频录制通道,默认为立体声
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT; //音频录制格式，默认为PCM16Bit

    private Thread mAudioThread;
    private boolean isRecording;
    private int bufferSize;

    // video
    private String videoMime = MediaFormat.MIMETYPE_VIDEO_AVC; //视频编码格式
    private MediaCodec mVideoEnc;
    private int videoRate = 2048000;       //视频编码波特率
    private int frameRate = 24;           //视频编码帧率
    private int frameInterval = 1;        //视频编码关键帧，1秒一关键帧

    private int fpsTime;
    private Thread mVideoThread;
    private boolean mStartFlag = false;
    private int width;
    private int height;

    private byte[] nowFeedData;
    //    private long nowTimeStep;
    private boolean hasNewData = false;


    // all
    private int mAudioTrack = -1;
    private int mVideoTrack = -1;
    private boolean isStop = true;

    private long nanoTime;

    private boolean cancelFlag = false;
    private boolean isAlign = false;

    private byte[] yuv;
    private int convertType;


    public CameraRecorder() {
        fpsTime = 1000 / frameRate;
    }

    public void setSavePath(String path, String postfix) {
        this.path = path;
        this.postfix = postfix;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public int prepare(int width, int height) throws IOException {
        // init audioCodec
        MediaFormat format = MediaFormat.createAudioFormat(audioMime, sampleRate, channelCount);
        format.setInteger(MediaFormat.KEY_BIT_RATE, audioRate);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mAudioEnc = MediaCodec.createEncoderByType(audioMime);
        mAudioEnc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); //设置为编码器

        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC,// 麦克风
                sampleRate,// 44100,22050,11025
                channelConfig,
                audioFormat,// 采样编码 一般是pcm编码16位
                bufferSize); // 缓冲区数据大小


        // init videoCodec
        this.width = width;
        this.height = height;

        MediaFormat vformat = MediaFormat.createVideoFormat(videoMime, width, height);
        vformat.setInteger(MediaFormat.KEY_BIT_RATE, videoRate);
        vformat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        vformat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, frameInterval);
        // 此处需要检查颜色通道，最好是手机支持的才行
        vformat.setInteger(MediaFormat.KEY_COLOR_FORMAT, checkColorFormat(videoMime));
        mVideoEnc = MediaCodec.createEncoderByType(videoMime);
        mVideoEnc.configure(vformat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); //设置为编码器

        // 硬编码流控
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Bundle params = new Bundle();
            params.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, videoRate);
            mVideoEnc.setParameters(params);
        }
        // init muxer
        mMuxer = new MediaMuxer(path + "." + postfix, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        return 0;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public int start() throws InterruptedException {
        //记录起始时间
        nanoTime = System.nanoTime();
        synchronized (lock) {
            if (mAudioThread != null && mAudioThread.isAlive()) {
                isRecording = false;
                mAudioThread.join();
            }
            if (mVideoThread != null && mVideoThread.isAlive()) {
                mStartFlag = false;
                mVideoThread.join();
            }

            mAudioEnc.start();
            mRecorder.startRecording();
            isRecording = true;
            mAudioThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (isRecording) {
                        try {
                            if (audioStep()) {
                                break;
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            mAudioThread.start();

            mVideoEnc.start();
            mStartFlag = true;
            mVideoThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (mStartFlag) {
                        long time = System.currentTimeMillis();
                        if (nowFeedData != null) {
                            try {
                                if (videoStep(nowFeedData)) {
                                    break;
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        long lt = System.currentTimeMillis() - time;
                        if (fpsTime > lt) { // 控制fps准时
                            try {
                                Thread.sleep(fpsTime - lt);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
            mVideoThread.start();
        }


        return 0;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void stop() throws InterruptedException, IOException {
        synchronized (lock) {
            mAudioThread.join();
            isRecording = false;
            mRecorder.stop();
            mAudioEnc.stop();
            mAudioEnc.release();

            mVideoThread.join();
            mStartFlag = false;
            mVideoEnc.stop();
            mVideoEnc.release();

            mAudioTrack = -1;
            mVideoTrack = -1;
            mMuxer.stop();
            mMuxer.release();
        }
    }


    private ByteBuffer getInputBuffer(MediaCodec codec, int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return codec.getInputBuffer(index);
        } else {
            return codec.getInputBuffers()[index];
        }
    }

    private ByteBuffer getOutputBuffer(MediaCodec codec, int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return codec.getOutputBuffer(index);
        } else {
            return codec.getOutputBuffers()[index];
        }
    }


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private boolean audioStep() throws IOException {
        int inIndex = mAudioEnc.dequeueInputBuffer(-1);
        Log.d(TAG, " inIndex:" + inIndex);
        if (inIndex >= 0) {
            ByteBuffer buffer = getInputBuffer(mAudioEnc, inIndex);
            buffer.clear();
            int length = mRecorder.read(buffer, bufferSize);
            Log.d(TAG, " length: " + length);
            if (length > 0) {
                // 结束就释放结束标识flag
                mAudioEnc.queueInputBuffer(inIndex, 0,
                        length, System.nanoTime() / 1000,
                        isRecording ? 0 : MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
        }

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outIndex;
        do {
            outIndex = mAudioEnc.dequeueOutputBuffer(info, 0);
            Log.d(TAG, " outIndex: " + outIndex);
            if (outIndex >= 0) {
                ByteBuffer buffer = getOutputBuffer(mAudioEnc, outIndex);
                buffer.position(info.offset);

               /* // 添加aac头部，buffer数据设置到temp
                byte[] temp = new byte[info.size + 7];
                buffer.get(temp, 7, info.size);
                addADTStoPacket(temp, temp.length); */

                // 音频轨，视频轨，size，缓冲区时间轴存在
                if (mAudioTrack >= 0 && mVideoTrack >= 0 && info.size > 0 && info.presentationTimeUs > 0) {
                    mMuxer.writeSampleData(mAudioTrack, buffer, info);
                }

                mAudioEnc.releaseOutputBuffer(outIndex, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "audio  end");
                    return true;
                }
            } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) { // 超时

            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { // 输出格式已经更改
                mAudioTrack = mMuxer.addTrack(mAudioEnc.getOutputFormat());
                Log.d(TAG, "add audio track: " + mAudioTrack);
                // 轨道数据都存在就开始混合
                if (mAudioTrack >= 0 && mVideoTrack >= 0) {
                    mMuxer.start();
                }
            }
        } while (outIndex >= 0);
        return false;
    }


    /**
     * 给编码出的aac裸流添加adts头字段
     *
     * @param packet    要空出前7个字节，否则会搞乱数据
     * @param packetLen
     */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        int freqIdx = 4;  //44.1KHz
        int chanCfg = 2;  //CPE
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    // 定时调用，如果没有新数据，就用上一个数据
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private boolean videoStep(byte[] data) throws IOException {
        int inIndex = mVideoEnc.dequeueInputBuffer(-1);
        Log.d(TAG, " inIndex:" + inIndex);
        if (inIndex >= 0) {
            if (hasNewData) {
                if (yuv == null) {
                    yuv = new byte[width * height * 3 / 2];
                }
                // 外部传入的数据都需要转换为YUV数据,才能进行后续的操作
                rgbaToYuv(data, width, height, yuv);
            }
            ByteBuffer buffer = getInputBuffer(mVideoEnc, inIndex);
            buffer.clear();
            buffer.put(yuv);
            //结束时，发送结束标志，在编码完成后结束
            mVideoEnc.queueInputBuffer(inIndex, 0, yuv.length,
                    (System.nanoTime() - nanoTime) / 1000,
                    mStartFlag ? 0 : MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outIndex = mVideoEnc.dequeueOutputBuffer(info, 0);
        do {
            Log.d(TAG, " outIndex: " + outIndex);
            if (outIndex >= 0) {
                // 获取临时数据
               /* byte[] temp = new byte[info.size];
                buffer.get(temp);
                if (info.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) { // 不是媒体数据
                    Log.e(TAG, "start frame");
                    mHeadInfo = new byte[info.size];
                    mHeadInfo = temp;
                } else if (info.flags % 8 == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                    Log.d(TAG, "info.flags ->" + info.flags);
                    Log.e(TAG, "key frame");

                    // 把头部数据，关键帧数据copy到keyframe 里面去
                    byte[] keyframe = new byte[temp.length + mHeadInfo.length];
                    System.arraycopy(mHeadInfo, 0, keyframe, 0, mHeadInfo.length);
                    System.arraycopy(temp, 0, keyframe, mHeadInfo.length, temp.length);
                    fos.write(keyframe, 0, keyframe.length);
                } else if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                    Log.e(TAG, "end frame");
                } else {
                    fos.write(temp, 0, temp.length);
                }*/

                ByteBuffer buffer = getOutputBuffer(mVideoEnc, outIndex);
                // 音频轨，视频轨，size，缓冲区时间轴存在
                if (mAudioTrack >= 0 && mVideoTrack >= 0 && info.size > 0 && info.presentationTimeUs > 0) {
                    mMuxer.writeSampleData(mVideoTrack, buffer, info);
                }

                mVideoEnc.releaseOutputBuffer(outIndex, false);
                // video 多一个此处
                outIndex = mVideoEnc.dequeueOutputBuffer(info, 0);

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "video  end");
                    return true;
                }

            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { // 输出格式已经更改
                mVideoTrack = mMuxer.addTrack(mVideoEnc.getOutputFormat());
                Log.d(TAG, "add video track: " + mAudioTrack);
                // 轨道数据都存在就开始混合
                if (mAudioTrack >= 0 && mVideoTrack >= 0) {
                    mMuxer.start();
                }
            }
        } while (outIndex >= 0);
        return false;
    }


    // 此方法有缺陷，应该根据 convertType 去根据不同的机型支持去适配才对，并且是c层装换比较快
    private void rgbaToYuv(byte[] rgba, int width, int height, byte[] yuv) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uIndex = frameSize;
        int vIndex = frameSize + frameSize / 4;

        int R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                index = j * width + i;
                if (rgba[index * 4] > 127 || rgba[index * 4] < -128) {
                    Log.e(TAG, "-->" + rgba[index * 4]);
                }
                R = rgba[index * 4] & 0xFF;
                G = rgba[index * 4 + 1] & 0xFF;
                B = rgba[index * 4 + 2] & 0xFF;

                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                yuv[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv[uIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                    yuv[vIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private int checkColorFormat(String mime) {
        if (Build.MODEL.equals("HUAWEI P6-C00")) {
            convertType = DataConvert.BGRA_YUV420SP;
            return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
        }
        int count = 0;
        try {
            count = MediaCodecList.getCodecCount();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (count != 0) {
            for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
                MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                if (info.isEncoder()) {
                    String[] types = info.getSupportedTypes();
                    for (String type : types) {
                        if (type.equals(mime)) {
                            Log.e("YUV", "type-->" + type);
                            MediaCodecInfo.CodecCapabilities c = info.getCapabilitiesForType(type);
                            Log.e("YUV", "color-->" + Arrays.toString(c.colorFormats));
                            for (int j = 0; j < c.colorFormats.length; j++) {
                                if (c.colorFormats[j] == MediaCodecInfo.CodecCapabilities
                                        .COLOR_FormatYUV420Planar) {
                                    convertType = DataConvert.RGBA_YUV420P;
                                    return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
                                } else if (c.colorFormats[j] == MediaCodecInfo.CodecCapabilities
                                        .COLOR_FormatYUV420SemiPlanar) {
                                    convertType = DataConvert.RGBA_YUV420SP;
                                    return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
                                }
                            }
                        }
                    }
                }
            }
        }
        convertType = DataConvert.RGBA_YUV420SP;
        return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
    }

    /**
     * 由外部喂入一帧数据
     *
     * @param data     RGBA数据
     * @param timeStep camera附带时间戳
     */
    public void feedData(final byte[] data, final long timeStep) {
        hasNewData = true;
        nowFeedData = data;
//        nowTimeStep = timeStep;
    }
}
