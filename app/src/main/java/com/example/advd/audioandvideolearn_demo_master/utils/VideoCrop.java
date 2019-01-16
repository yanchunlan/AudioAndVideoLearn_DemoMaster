package com.example.advd.audioandvideolearn_demo_master.utils;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * author:  ycl
 * date:  2019/1/14 10:29
 * desc:    视频裁剪
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class VideoCrop {
    private static final String TAG = "VideoCrop";

    private MediaExtractor videoExtractor = null;
    private MediaCodec videoDecoder = null;
    private MediaCodec videoEncode = null;

    private MediaExtractor audioExtractor = null;
    private MediaCodec audioDecoder = null;
    private MediaCodec audioEncode = null;


    private MediaMuxer mediaMuxer = null;
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;


    private Handler videoDecoderHandler = null;
    private Handler videoEncodeHandler = null;
    private Handler audioDecoderHandler = null;
    private Handler audioEncodeHandler = null;


    private String outputVideoPath = null;
    private long startTime = 0;
    private long endTime = 0;


    private final Object audioObject = new Object();
    private final Object videoObject = new Object();

    private boolean muxerStart = false;
    private boolean videoInit = false;
    private boolean audioInit = false;
    private boolean exit = false;


    public VideoCrop() {
        HandlerThread videoDecoderThread = new HandlerThread("videoDecoderMediaCodec");
        videoDecoderThread.start();
        videoDecoderHandler = new Handler(videoDecoderThread.getLooper());

        HandlerThread videoEncodeThread = new HandlerThread("videoEncodeMediaCodec");
        videoEncodeThread.start();
        videoEncodeHandler = new Handler(videoEncodeThread.getLooper());


        HandlerThread audioDecoderThread = new HandlerThread("AudioDecoderMediaCodec");
        audioDecoderThread.start();
        audioDecoderHandler = new Handler(audioDecoderThread.getLooper());

        HandlerThread audioEncodeThread = new HandlerThread("AudioEncodeMediaCodec");
        audioEncodeThread.start();
        audioEncodeHandler = new Handler(audioEncodeThread.getLooper());
    }

    public void start(String inputVideoPath, String outputVideoPath, long startTime, long endTime) {
        if (TextUtils.isEmpty(inputVideoPath)) {
            if (encoderListener != null) {
                encoderListener.onError("inputVideoPath is null");
            }
            return;
        }
        if (TextUtils.isEmpty(outputVideoPath)) {
            if (encoderListener != null) {
                encoderListener.onError("outputVideoPath is null");
            }
            return;
        }
        File inputFile = new File(inputVideoPath);
        if (!inputFile.exists()) {
            if (encoderListener != null) {
                encoderListener.onError("inputVideoPath not exists");
            }
            return;
        }
        File outputFile = new File(outputVideoPath);
        if (outputFile.exists()) {
            if (!outputFile.isFile()) {
                if (encoderListener != null) {
                    encoderListener.onError("outputVideoPath not file");
                }
                return;
            }
            outputFile.delete();
        }
        this.outputVideoPath = outputVideoPath;
        if (startTime < 0 || endTime < 0) {
            if (encoderListener != null) {
                encoderListener.onError("startTime or endTime Less than 0");
            }
            return;
        }
        if (startTime >= endTime) {
            if (encoderListener != null) {
                encoderListener.onError("startTime more than the endTime");
            }
            return;
        }
        this.startTime = startTime;
        this.endTime = endTime;


        videoExtractor = new MediaExtractor();
        audioExtractor = new MediaExtractor();
        try {
            videoExtractor.setDataSource(inputVideoPath);
            audioExtractor.setDataSource(inputVideoPath);

            mediaMuxer = new MediaMuxer(outputVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            initAudioMediaCodec();
            initVideoMediaCodec();
            startVideoCrop();
        } catch (IOException e) {
            e.printStackTrace();
            if (encoderListener != null) {
                encoderListener.onError("init video Cropping failed ");
            }
        }
    }

    public void stop() {
        exit = true;
    }

    private void startVideoCrop() {
        Log.d(TAG, "start: startTime " + startTime + " endTime " + endTime);
        if (encoderListener != null) {
            encoderListener.onStart();
        }
        muxerStart = false;
        videoInit = false;
        audioInit = false;
        exit = false;

        // video
        if (videoDecoder != null && videoEncode != null) {
            if (audioDecoder == null && audioEncode == null) { // 单视频，就需要开启音频条件
                audioInit = true;
            }
            videoDecoderHandler.post(new Runnable() {
                @Override
                public void run() {
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    while (true) {
                        if (exit) {
                            break;
                        }

                        // 等待混合开启之后，开始解码音频
                        // 流程  音频编码 audioInit=true--> 视频解码开启 -> 视频编码 videoInit=true -> 开始音频解码
                        if (!audioInit) {  // 音频没有混合就不开始解码
                            synchronized (videoObject) {
                                try {
                                    videoObject.wait(50);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            continue;
                        }

                        // decoder input
                        MediaCodecUtils.extractorInputBuffer(videoExtractor, videoDecoder);

                        // decoder output
                        info.set(0, 0, 0, 0);
                        int outIndex = videoDecoder.dequeueOutputBuffer(info, 10000);
                        Log.d(TAG, "run: videoDecoder  outIndex " + outIndex + " presentationTimeUs "
                                + info.presentationTimeUs + " size " + info.size + " flag " + info.flags + " offset " + info.offset);

                        if (outIndex >= 0) {
                            ByteBuffer buffer = MediaCodecUtils.getOutputBuffer(videoDecoder, outIndex);
                            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                info.size = 0;
                                Log.d(TAG, "run: videoDecoder size==0");
//                                videoDecoder.releaseOutputBuffer(outIndex, false);
                            }
                            if (info.size != 0) {
                                Log.d(TAG, "run: videoDecoder size!=0");
                                if (info.presentationTimeUs >= startTime) {
                                    buffer.position(info.offset);
                                    buffer.limit(info.offset + info.size);
                                    if (info.presentationTimeUs > endTime) {
                                        info.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM; // 传递结束标识
                                    }
                                    Log.d(TAG, "run: videoDecoder presentationTimeUs "
                                            + info.presentationTimeUs + " size " + info.size + " flag " + info.flags + " offset " + info.offset);

                                    // encode input
                                    if (videoEncode != null) {
                                        MediaCodecUtils.encodeInputBuffer(buffer, videoEncode, info);
                                    }
                                }
                                videoDecoder.releaseOutputBuffer(outIndex, false);
                                if (info.presentationTimeUs > endTime) {
                                    Log.d(TAG, "run: videoDecoder  > endTime");
//                                    videoEncode.signalEndOfInputStream();
                                    break;
                                }
                            }
                        }

                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.d(TAG, "run: videoDecoder  info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0");
//                            videoEncode.signalEndOfInputStream(); // 如果是surfaceView就可以控制停止，如果不是，只有自己传递结束标识
                            // 即使结束了也要传递结束标识   目的是把结束标识传递到解码器，控制解码停止
                            ByteBuffer buffer = MediaCodecUtils.getOutputBuffer(videoDecoder, outIndex);
                            buffer.position(info.offset);
                            buffer.limit(info.offset + info.size);
                            if (videoEncode != null) {
                                MediaCodecUtils.encodeInputBuffer(buffer, videoEncode, info);
                            }
                            break;
                        }
                    }

                    Log.d(TAG, "run: videoDecoder release");
                    videoDecoder.stop();
                    videoDecoder.release();
                    videoDecoder = null;
                    videoExtractor.release();
                    videoExtractor = null;
                }
            });
        }

        // 如果只是视频，没有音频参与，视频就会一直获取不到数据
        if (videoEncode != null && mediaMuxer != null) {
            videoEncodeHandler.post(new Runnable() {
                @Override
                public void run() {
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    while (true) {
                        if (exit && videoDecoder == null) {
                            break;
                        }

                        info.set(0, 0, 0, 0);
                        int outIndex = videoEncode.dequeueOutputBuffer(info, 1000);
                        Log.d(TAG, "run: videoEncode  outIndex " + outIndex + " presentationTimeUs "
                                + info.presentationTimeUs + " size " + info.size + " flag "
                                + info.flags + " offset " + info.offset);
                        if (outIndex >= 0) {
                            ByteBuffer buffer = MediaCodecUtils.getOutputBuffer(videoEncode, outIndex);
                            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                info.size = 0;
                                Log.d(TAG, "run: videoEncode size==0");
//                                videoEncode.releaseOutputBuffer(outIndex, false);
                            }
                            if (info.size != 0) {
                                Log.d(TAG, "run: videoEncode size!=0");
                                long presentationTimeUs = info.presentationTimeUs;
                                if (presentationTimeUs >= startTime && presentationTimeUs <= endTime) {
                                    info.presentationTimeUs = presentationTimeUs - startTime;
                                    buffer.position(info.offset);
                                    buffer.limit(info.offset + info.size);
                                    Log.d(TAG, "run: videoEncode presentationTimeUs "
                                            + info.presentationTimeUs + " size " + info.size
                                            + " flag " + info.flags + " offset " + info.offset);


                                    // write
                                    mediaMuxer.writeSampleData(videoTrackIndex, buffer, info);
                                }
                                if (encoderListener != null) {
                                    encoderListener.onProgress((int) ((presentationTimeUs - startTime) * 100.0f / (endTime - startTime)));
                                }
                                videoEncode.releaseOutputBuffer(outIndex, false);
                                if (presentationTimeUs > endTime) {
                                    Log.d(TAG, "run: videoEncode  > endTime");
                                    break;
                                }
                            }
                        } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            if (mediaMuxer != null && videoTrackIndex == -1) { // 保证执行一次
                                videoTrackIndex = mediaMuxer.addTrack(videoEncode.getOutputFormat());
                                videoInit = true;
                                Log.d(TAG, "run: videoEncode   mediaMuxer.addTrack");
                                initMuxer();

                            }
                        }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.d(TAG, "run: videoEncode  info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0");

                            break;
                        }
                    }
                    Log.d(TAG, "run: videoEncode release");
                    videoEncode.stop();
                    videoEncode.release();
                    videoEncode = null;
                    muxerRelease();
                }
            });
        }

        // audio
        if (audioDecoder != null && audioEncode != null) {
            audioDecoderHandler.post(new Runnable() {
                @Override
                public void run() {
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    while (true) {
                        if (exit) {
                            break;
                        }

                        // 等待混合开启之后，开始解码音频
                        // 流程  音频编码 audioInit=true--> 视频解码开启 -> 视频编码 videoInit=true -> 开始音频解码
                        if (!muxerStart) {
                            synchronized (audioObject) {
                                try {
                                    audioObject.wait(50);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            continue;
                        }


                        // decoder input
                        MediaCodecUtils.extractorInputBuffer(audioExtractor, audioDecoder);

                        // decoder output
                        int outIndex = audioDecoder.dequeueOutputBuffer(info, 10000);

//                        Log.d(TAG, "run: audioDecoder  outIndex " + outIndex + " presentationTimeUs "
//                                + info.presentationTimeUs + " size " + info.size + " flag " + info.flags + " offset " + info.offset);

                        if (outIndex >= 0) {
                            ByteBuffer buffer = MediaCodecUtils.getOutputBuffer(audioDecoder, outIndex);
                            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                info.size = 0;
//                                Log.d(TAG, "run: audioDecoder size==0");
                                audioDecoder.releaseOutputBuffer(outIndex, false);
                            }
                            if (info.size != 0) {
//                                Log.d(TAG, "run: audioDecoder size!=0");
                                if (info.presentationTimeUs >= startTime) {
                                    buffer.position(info.offset);
                                    buffer.limit(info.offset + info.size);
//                                    Log.d(TAG, "run: audioDecoder presentationTimeUs "
//                                            + info.presentationTimeUs + " size " + info.size + " flag " + info.flags + " offset " + info.offset);

                                    // encode input
                                    if (audioEncode != null) {
                                        MediaCodecUtils.encodeInputBuffer(buffer, audioEncode, info);
                                    }
                                }
                                audioDecoder.releaseOutputBuffer(outIndex, false);
                                if (info.presentationTimeUs > endTime) {
//                                    Log.d(TAG, "run: audioDecoder  > endTime");
                                    break;
                                }
                            }
                        }

                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                            Log.d(TAG, "run: audioDecoder  info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0");
                            break;
                        }
                    }
//                    Log.d(TAG, "run: audioDecoder release");
                    audioDecoder.stop();
                    audioDecoder.release();
                    audioDecoder = null;
                    audioExtractor.release();
                    audioExtractor = null;
                }
            });
        }

        if (audioEncode != null && mediaMuxer != null) {
            audioEncodeHandler.post(new Runnable() {
                @Override
                public void run() {
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    while (true) {
                        if (exit && audioDecoder == null) {
                            break;
                        }

                        int outIndex = audioEncode.dequeueOutputBuffer(info, 1000);
//                        Log.d(TAG, "run: audioEncode  outIndex " + outIndex + " presentationTimeUs "
//                                + info.presentationTimeUs + " size " + info.size + " flag " + info.flags + " offset " + info.offset);

                        if (outIndex >= 0) {
                            ByteBuffer buffer = MediaCodecUtils.getOutputBuffer(audioEncode, outIndex);
                            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                info.size = 0;
//                                Log.d(TAG, "run: audioEncode size==0");
                                audioEncode.releaseOutputBuffer(outIndex, false);
                            }
                            if (info.size != 0) {
//                                Log.d(TAG, "run: audioEncode size!=0");
                                long presentationTimeUs = info.presentationTimeUs;
                                if (presentationTimeUs >= startTime && presentationTimeUs <= endTime) {
                                    info.presentationTimeUs = presentationTimeUs - startTime;
                                    buffer.position(info.offset);
                                    buffer.limit(info.offset + info.size);
//                                    Log.d(TAG, "run: audioEncode presentationTimeUs "
//                                            + info.presentationTimeUs + " size " + info.size + " flag " + info.flags + " offset " + info.offset);
                                    // write
                                    mediaMuxer.writeSampleData(audioTrackIndex, buffer, info);
                                }
                                audioEncode.releaseOutputBuffer(outIndex, false);
                                if (presentationTimeUs > endTime) {
//                                    Log.d(TAG, "run: audioEncode  > endTime");
                                    break;
                                }
                            }
                        } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            if (mediaMuxer != null && audioTrackIndex == -1) {
                                audioTrackIndex = mediaMuxer.addTrack(audioEncode.getOutputFormat());
                                audioInit = true;

//                                Log.d(TAG, "run: audioEncode   mediaMuxer.addTrack");
//                                initMuxer();
                            }
                        }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                            Log.d(TAG, "run: audioEncode  info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0");
                            break;
                        }
                    }

//                    Log.d(TAG, "run: audioEncode release");
                    audioEncode.stop();
                    audioEncode.release();
                    audioEncode = null;
                    muxerRelease();
                }
            });
        }
    }

    private void initVideoMediaCodec() throws IOException {
        for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
            final MediaFormat format = videoExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                videoExtractor.selectTrack(i);
//                if (startTime != 0) { // seek 之后就不能读出视频的位置了
//                    videoExtractor.seekTo(startTime, i);
//                }
                long videoTotalTime = format.getLong(MediaFormat.KEY_DURATION);
                if (endTime == 0) {
                    this.endTime = videoTotalTime;
                } else if (endTime > videoTotalTime) { // 取最小值
                    this.endTime = videoTotalTime;
                }

                videoDecoder = MediaCodec.createDecoderByType(mime);
                videoDecoder.configure(format, null, null, 0 /* Decoder */);

                videoEncode = MediaCodecUtils.createVideoEnCodec(format, mime);

                videoDecoder.start();
                videoEncode.start();
                break;
            }
        }
    }

    private void initAudioMediaCodec() throws IOException {
        for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
            MediaFormat format = audioExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                audioExtractor.selectTrack(i);
                if (startTime != 0) {
                    audioExtractor.seekTo(startTime, i);
                }
                long audioTotalTime = format.getLong(MediaFormat.KEY_DURATION);
                if (endTime == 0) {
                    this.endTime = audioTotalTime;
                }
               /* else if (endTime > audioTotalTime) {  // 取最小值
                    this.endTime = audioTotalTime;
                }*/

                audioDecoder = MediaCodec.createDecoderByType(mime);
                audioDecoder.configure(format, null, null, 0 /* Decoder */);

                audioEncode = MediaCodecUtils.createAudioEnCodec(format, mime);

                audioExtractor.seekTo(startTime, i);

                audioDecoder.start();
                audioEncode.start();
                break;
            }
        }
    }

    private synchronized void initMuxer() {
        if (videoInit && audioInit) {
            Log.d(TAG, "initMuxer: ");
            muxerStart = true;
            mediaMuxer.start();
        }
    }

    private synchronized void muxerRelease() {
        if (audioEncode == null && videoEncode == null && mediaMuxer != null) {
            Log.d(TAG, "muxerRelease:");
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;

            if (exit) { // 取消的任务，删除output资源
                File f = new File(outputVideoPath);
                if (f.exists()) {
                    f.delete();
                }
                outputVideoPath = null;
            }
            if (encoderListener != null) {
                encoderListener.onComplete(outputVideoPath);
            }

            // release
            videoTrackIndex = -1;
            audioTrackIndex = -1;

            outputVideoPath = null;
            startTime = 0;
            endTime = 0;

            muxerStart = false;
            videoInit = false;
            audioInit = false;
            exit = false;
        }
    }


    private OnEncoderListener encoderListener;

    public void setEncoderListener(OnEncoderListener encoderListener) {
        this.encoderListener = encoderListener;
    }

    public interface OnEncoderListener {
        void onStart();

        void onComplete(String outPutPath);

        void onProgress(int progress);

        void onError(String msg);
    }
}
