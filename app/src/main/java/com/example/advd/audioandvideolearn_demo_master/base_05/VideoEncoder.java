package com.example.advd.audioandvideolearn_demo_master.base_05;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * author: ycl
 * date: 2018-12-17 0:34
 * desc:
 */
public class VideoEncoder implements Runnable {
    private static final String TAG = "VideoEncoder";

    private MediaCodec mEncoder;

    private String mime = MediaFormat.MIMETYPE_VIDEO_AVC; // avc
    private int rate = 256000; //波特率，256kb
    private int frameRate = 24;  //帧率，24帧
    private int frameInterval = 1; //关键帧一秒一关键帧

    private int fpsTime;
    private boolean isRecording;
    private Thread mThread;
    private int width;
    private int height;

    private FileOutputStream fos;
    private String mSaveName;


    // 外部写入一帧数据进来
    private byte[] nowFeedData;
    private long nowTimeStep;
    private boolean hasNewData = false;

    private byte[] yuv; // rgb转换yuv临时的文件
    private byte[] mHeadInfo = null; // 关键帧头部信息


    public VideoEncoder() {
        fpsTime = 1000 / frameRate;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void prepare(int width, int height) throws IOException {
        this.width = width;
        this.height = height;

        // init Codec
        MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
        format.setInteger(MediaFormat.KEY_BIT_RATE, rate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, frameInterval);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
        mEncoder = MediaCodec.createEncoderByType(mime);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); //设置为编码器

        File f = new File(System.currentTimeMillis() + mSaveName);
        if (!f.mkdir()) {
            Log.i(TAG, "createAudioRecord:   Directory not created");
        }
        if (f.exists()) {
            f.delete();
        }
        fos = new FileOutputStream(f);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void start() throws InterruptedException {
        mEncoder.start();

        if (mThread != null && mThread.isAlive()) {
            isRecording = false;
            mThread.join();
        }

        isRecording = true;
        mThread = new Thread(this);
        mThread.start();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void stop() throws InterruptedException, IOException {
        mThread.join();

        isRecording = false;

        mEncoder.stop();
        mEncoder.release();

        fos.flush();
        fos.close();
    }


    @Override
    public void run() {
        while (isRecording) {
            long time = System.currentTimeMillis();

            if (nowFeedData != null) {
                try {
                    readOutputData(nowFeedData, nowTimeStep);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            long lt = System.currentTimeMillis() - time;

            if (fpsTime > lt) { // 控制fps准时  时间太快就需要等待，保证帧率一致
                try {
                    Thread.sleep(fpsTime - lt);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private ByteBuffer getInputBuffer(int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return mEncoder.getInputBuffer(index);
        } else {
            return mEncoder.getInputBuffers()[index];
        }
    }

    private ByteBuffer getOutputBuffer(int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return mEncoder.getOutputBuffer(index);
        } else {
            return mEncoder.getOutputBuffers()[index];
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void readOutputData(byte[] data, long timeStep) throws IOException {
        int inIndex = mEncoder.dequeueInputBuffer(-1);
        Log.d(TAG, " inIndex:" + inIndex);
        if (inIndex >= 0) {
            if (hasNewData) {
                if (yuv == null) {
                    yuv = new byte[width * height * 3 / 2];
                }
                // 外部传入的数据都需要转换为YUV数据,才能进行后续的操作
                rgbaToYuv(data, width, height, yuv);
            }
            ByteBuffer buffer = getInputBuffer(inIndex);
            buffer.clear();
            buffer.put(yuv);
            mEncoder.queueInputBuffer(inIndex, 0, yuv.length, timeStep, 0);
        }

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outIndex = mEncoder.dequeueOutputBuffer(info, 0);
        while (outIndex >= 0) {
            Log.d(TAG, " outIndex: " + outIndex);
            ByteBuffer buffer = getOutputBuffer(outIndex);

            // 获取临时数据
            byte[] temp = new byte[info.size];
            buffer.get(temp);
            if (info.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                //把编码信息保存下来，关键帧上要用
                Log.e(TAG, "start frame");
                mHeadInfo = new byte[info.size];
                mHeadInfo = temp;
            } else if (info.flags % 8 == MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                //关键帧比普通帧是多了个帧头的，保存了编码的信息
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
                //普通帧 写入文件
                fos.write(temp, 0, temp.length);
            }

            mEncoder.releaseOutputBuffer(outIndex, false);
            outIndex = mEncoder.dequeueOutputBuffer(info, 0);
        }
    }


    /**
     *  RGBA转YUV的方法，这是最简单粗暴的方式，在使用的时候，一般不会选择在Java层，用这种方式做转换
     */
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

    /**
     * 由外部写入一帧数据
     *
     * @param data   GL处理好后，readpix出来的RGBA数据喂进来，
     * @param timeStep
     */
    public void feedData(final byte[] data, final long timeStep) {
        hasNewData = true;
        nowFeedData = data;
        nowTimeStep = timeStep;
    }
}
