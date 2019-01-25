package com.example.advd.audioandvideolearn_demo_master.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import com.example.advd.audioandvideolearn_demo_master.R;
import com.example.advd.audioandvideolearn_demo_master.videoCut.egl.EGLUtils;
import com.example.advd.audioandvideolearn_demo_master.videoCut.egl.GLFramebuffer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * author:  ycl
 * date:  2019/1/14 10:29
 * desc:    视频裁剪
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class VideoCrop2 {
    private static final String TAG = "VideoCrop";

    private Resources resources;
    private MediaExtractor videoExtractor = null;
    private MediaCodec videoDecoder = null;
    private MediaCodec videoEncode = null;

    private MediaExtractor audioExtractor = null;
    private MediaCodec audioDecoder = null;
    private MediaCodec audioEncode = null;

    private MediaMetadataRetriever metadataRetriever;
    private MediaMuxer mediaMuxer = null;
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;

    private Handler videoHandler = null;
    private Handler eglHandler = null;
    private Handler audioDecoderHandler = null;
    private Handler audioEncodeHandler = null;

    private String outputVideoPath = null;
    private long startTime = 0;
    private long endTime = 0;

    private long videoStartTime = 0;
    private long audioStartTime = 0;


    private final Object decoderObject = new Object();
    private final Object audioObject = new Object();
    private final Object videoObject = new Object();


    private boolean isVideoInput = false; // 解码停止了就结束解码
    private boolean isDraw = false;
    private long presentationTimeUs;


    private boolean muxerStart = false;
    private boolean videoInit = false;
    private boolean audioInit = false;
    private boolean exit = false;
    private boolean videoExit = false; // 保证最后一帧执行完成之后再跳出去循环
    private boolean isComplete = false;

    // GL 绘制
    private EGLUtils mEglUtils = null;
    private GLFramebuffer mFramebuffer = null;


    // 统计帧的数量
    private int videoInputIndex = 0;
    private int videoOutputIndex = 0;
    private int videoAllOutputIndex = 0;
    private int videoDrawIndex = 0;

    // 解决掉帧问题
    private List<Long> presentationTimeUsList = null; // 存储output未获取到的数据
    private boolean outputStart = false;  // 对混合区输出的拦截


    public VideoCrop2(Context context) {
        this.resources = context.getApplicationContext().getResources();
        HandlerThread videoThread = new HandlerThread("VideoMediaCodec");
        videoThread.start();
        videoHandler = new Handler(videoThread.getLooper());

        HandlerThread eglThread = new HandlerThread("OpenGLVideoCrop");
        eglThread.start();
        eglHandler = new Handler(eglThread.getLooper());

        HandlerThread audioDecoderThread = new HandlerThread("AudioDecoderMediaCodec");
        audioDecoderThread.start();
        audioDecoderHandler = new Handler(audioDecoderThread.getLooper());

        HandlerThread audioEncodeThread = new HandlerThread("AudioEncodeMediaCodec");
        audioEncodeThread.start();
        audioEncodeHandler = new Handler(audioEncodeThread.getLooper());

    }

    public void start(String inputVideoPath, String outputVideoPath, long startTime, long endTime, boolean swipeWH) {
        if (TextUtils.isEmpty(inputVideoPath)) {
            if (encoderListener != null) {
                encoderListener.onError(resources.getString(R.string.vc_input_null));
            }
            return;
        }
        if (TextUtils.isEmpty(outputVideoPath)) {
            if (encoderListener != null) {
                encoderListener.onError(resources.getString(R.string.vc_output_null));
            }
            return;
        }
        File inputFile = new File(inputVideoPath);
        if (!inputFile.exists()) {
            if (encoderListener != null) {
                encoderListener.onError(resources.getString(R.string.vc_input_not_exist));
            }
            return;
        }
        File outputFile = new File(outputVideoPath);
        if (outputFile.exists()) {
            if (!outputFile.isFile()) {
                if (encoderListener != null) {
                    encoderListener.onError(resources.getString(R.string.vc_output_not_file));
                }
                return;
            }
            outputFile.delete();
        }
        this.outputVideoPath = outputVideoPath;
        if (startTime < 0) {
            if (encoderListener != null) {
                encoderListener.onError(resources.getString(R.string.vc_startTime_less_0));
            }
            return;
        }
        if (endTime < 0) {
            if (encoderListener != null) {
                encoderListener.onError(resources.getString(R.string.vc_endTime_less_0));
            }
            return;
        }
        if (startTime >= endTime) {
            if (encoderListener != null) {
                encoderListener.onError(resources.getString(R.string.vc_startTime_more_endTime));
            }
            return;
        }
        this.startTime = startTime;
        this.endTime = endTime;

        Log.d(TAG, "start inputVideoPath " + inputVideoPath + " \noutputVideoPath " + outputVideoPath
                + " \ninputSize " + inputFile.length() / (1024.0 * 1024.0) + " outputSize " + outputFile.length() / (1024.0 * 1024.0)
                + " \nstartTime " + startTime + " endTime " + endTime + " swipeWH " + swipeWH);

        videoExtractor = new MediaExtractor();
        audioExtractor = new MediaExtractor();
        metadataRetriever = new MediaMetadataRetriever();

        try {
            videoExtractor.setDataSource(inputVideoPath);
            audioExtractor.setDataSource(inputVideoPath);
            metadataRetriever.setDataSource(inputVideoPath);

            mediaMuxer = new MediaMuxer(outputVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            initAudioMediaCodec();
            initVideoMediaCodec(swipeWH);
        } catch (Exception e) {
            e.printStackTrace();
            if (encoderListener != null) {
                encoderListener.onError(resources.getString(R.string.vc_init_failed));
            }
        }
    }

    public void stop() {
        exit = true;
    }

    private void startVideoCrop() {
        if (encoderListener != null) {
            encoderListener.onStart();
        }

        if (metadataRetriever != null) {
            metadataRetriever.release();
            metadataRetriever = null;
        }

        isVideoInput = false;
        muxerStart = false;
        videoInit = false;
        audioInit = false;
        exit = false;
        videoExit = false;
        isComplete = false;


        videoInputIndex = 0;
        videoOutputIndex = 0;
        videoAllOutputIndex = 0;
        videoDrawIndex = 0;
        outputStart = false;

        // video
        if (videoDecoder != null && videoEncode != null && mediaMuxer != null) {
            if (audioDecoder == null && audioEncode == null) { // 单视频，就需要开启音频条件
                audioInit = true;
            }
            presentationTimeUsList = new ArrayList<>();
            videoHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "videoDecoder  start startTime " + startTime + " endTime " + endTime);
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    while (true) {
                        if (exit) {
                            break;
                        }

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
                        if (!isVideoInput) {
                            int run = MediaCodecUtils.extractorVideoInputBuffer(videoExtractor, videoDecoder);
                            if (run == -1) {
                                isVideoInput = true;
                            } else {
                                ++videoInputIndex;
                            }
                        }

//                        if (run == 1) {
//                       // decoder output
                        info.set(0, 0, 0, 0);
                        int outIndex = videoDecoder.dequeueOutputBuffer(info, 10000);
//                        Log.d(TAG, "run: videoDecoder  outIndex " + outIndex + " presentationTimeUs "
//                                + info.presentationTimeUs + " size " + info.size + " flag " + info.flags + " offset " + info.offset);
                        // 获取不到数据了就结束
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.d(TAG, "run: videoDecoder  info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0");
                            videoExit = true;
                        }
                        presentationTimeUs = info.presentationTimeUs;
                        if (outIndex >= 0) {
                            videoDecoder.releaseOutputBuffer(outIndex, true /* Surface init */);
                            boolean s = false;
                            synchronized (decoderObject) { // 等待绘制完成，之后才能从编码器里面取数据
                                try {
                                    decoderObject.wait(50);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                if (isDraw) {
                                    s = true;
                                }
                            }
                            if (s) {
                                if (!outputStart) { // 不能output，暂时不处理
                                    presentationTimeUsList.add(presentationTimeUs);
                                } else if (presentationTimeUsList.size() > 0) { // 已经能够output了,把以前的都遍历一遍
//                                    Log.d(TAG, "run: videoDecoder  presentationTimeUsList.size： " + presentationTimeUsList.size());
                                    for (Long time : presentationTimeUsList) {
                                        encodeVideoOutputBuffer(videoEncode, info, time);
                                    }
                                    presentationTimeUsList.clear();
                                }
                                encodeVideoOutputBuffer(videoEncode, info, presentationTimeUs);
                            } else {
                                Log.i(TAG, "run: not draw");
                                if (!outputStart) { // 不能output，暂时不处理
                                    presentationTimeUsList.add(presentationTimeUs);
                                }
                            }
                            if (presentationTimeUs > endTime) {
                                Log.d(TAG, "run: videoDecoder  > endTime  presentationTimeUs： " + presentationTimeUs);
                                videoEncode.signalEndOfInputStream();
                                break;
                            }
                        }
                        if (videoExit) {
                            videoEncode.signalEndOfInputStream();
                            break;
                        }
//                        } else if (run == -1) {
//                            videoEncode.signalEndOfInputStream();
//                            break;
//                        } else {
//                            try {
//                                Thread.sleep(50);
//                            } catch (InterruptedException e) {
//                                e.printStackTrace();
//                            }
//                        }
//                        Log.d(TAG, "run: videoDecoder videoInputIndex " + videoInputIndex + " videoDrawIndex " + videoDrawIndex
//                                + " videoAllOutputIndex " + videoAllOutputIndex + " videoOutputIndex " + videoOutputIndex);

                    }

                    eglHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mEglUtils.release();
                        }
                    });
                    Log.d(TAG, "run: videoDecoder release");
                    videoDecoder.stop();
                    videoDecoder.release();
                    videoDecoder = null;
                    videoExtractor.release();
                    videoExtractor = null;
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
                    Log.d(TAG, "run: audioDecoder release");
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
                                    if (audioStartTime == 0) {
                                        audioStartTime = presentationTimeUs;
                                    }
                                    info.presentationTimeUs = presentationTimeUs - audioStartTime;
                                    buffer.position(info.offset);
                                    buffer.limit(info.offset + info.size);
//                                    Log.d(TAG, "run: audioEncode presentationTimeUs "
//                                            + info.presentationTimeUs + " size " + info.size + " flag " + info.flags + " offset " + info.offset);
                                    // write
                                    mediaMuxer.writeSampleData(audioTrackIndex, buffer, info);
                                }
                                audioEncode.releaseOutputBuffer(outIndex, false);
                                if (presentationTimeUs > endTime) {
                                    Log.d(TAG, "run: audioEncode  > endTime  presentationTimeUs：" + presentationTimeUs);
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
                            Log.d(TAG, "run: audioEncode  info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0");
                            break;
                        }
                    }

                    Log.d(TAG, "run: audioEncode release");
                    audioEncode.stop();
                    audioEncode.release();
                    audioEncode = null;
                    muxerRelease();
                }
            });
        }
    }

    private void encodeVideoOutputBuffer(MediaCodec videoEncode, MediaCodec.BufferInfo info, long presentationTimeUs) {
        int outIndex = videoEncode.dequeueOutputBuffer(info, 50000); // 此处超时5000才能保证执行到最后一帧，设置1000发现视频帧数缺失
//        Log.d(TAG, "run: videoEncode  outIndex " + outIndex + " presentationTimeUs " + presentationTimeUs + " info.presentationTimeUs "
//                + info.presentationTimeUs + " size " + info.size + " flag "
//                + info.flags + " offset " + info.offset);
        if (outIndex >= 0) {
            if (!outputStart) {  // 拦截处理，false被拦截
                outputStart = true;
                return;
            }
            ByteBuffer buffer = MediaCodecUtils.getOutputBuffer(videoEncode, outIndex);
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                info.size = 0;
//                                Log.d(TAG, "run: videoEncode size==0");
                videoEncode.releaseOutputBuffer(outIndex, false);
            }
            if (info.size != 0) {
//                                Log.d(TAG, "run: videoEncode size!=0");
                if (presentationTimeUs >= startTime && presentationTimeUs <= endTime) {
                    if (videoStartTime == 0) {
                        videoStartTime = presentationTimeUs;
                    }

                    info.presentationTimeUs = presentationTimeUs - videoStartTime;
                    buffer.position(info.offset);
                    buffer.limit(info.offset + info.size);
//                                    Log.d(TAG, "run: videoEncode presentationTimeUs "
//                                            + info.presentationTimeUs + " size " + info.size
//                                            + " flag " + info.flags + " offset " + info.offset);
                    // write
                    mediaMuxer.writeSampleData(videoTrackIndex, buffer, info);
                    ++videoOutputIndex;
                    if (encoderListener != null) {
                        encoderListener.onProgress((int) ((presentationTimeUs - videoStartTime) * 100.0f / (endTime - startTime)));
                    }
                }
                ++videoAllOutputIndex;
                videoEncode.releaseOutputBuffer(outIndex, false);
                                /*if (presentationTimeUs > endTime) {
//                                    Log.d(TAG, "run: videoEncode  > endTime");
                                    break;
                                }*/
            }
        } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (mediaMuxer != null && videoTrackIndex == -1) { // 保证执行一次
                videoTrackIndex = mediaMuxer.addTrack(videoEncode.getOutputFormat());
                videoInit = true;
//                                Log.d(TAG, "run: videoEncode   mediaMuxer.addTrack");
                initMuxer();
            }
        }
    }

    private void initVideoMediaCodec(boolean sSwipeWH) throws IOException {
        for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
            final MediaFormat format = videoExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                videoExtractor.selectTrack(i);
               /* if (startTime != 0) {
                    videoExtractor.seekTo(startTime, i);
                }*/
                long videoTotalTime = format.getLong(MediaFormat.KEY_DURATION);
                Log.d(TAG, "initVideoMediaCodec:   from format  videoTotalTime " + videoTotalTime);
                if (endTime == 0) {
                    this.endTime = videoTotalTime;
                } else if (endTime > videoTotalTime) { // 取最小值
                    this.endTime = videoTotalTime;
                } else if (endTime + 100000 >= videoTotalTime) { // 误差是0.1s内，如果是最后的值，就不裁剪最后
                    this.endTime = videoTotalTime;
                }

                String bit = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
                Log.d(TAG, "initVideoMediaCodec:  bitrate " + bit);
                int bitrate = bit == null ? 0 : Integer.parseInt(bit);

                boolean swipeWH = false;  // 宽高是否置位
                try {
                    // 最终是根据内部定下来的
                    String width = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);//宽
                    String height = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);//高
                    String rotation = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                    Log.d(TAG, "initVideoMediaCodec:  rotation " + rotation + " width " + width + " height " + height);
                    int rota = rotation == null ? 0 : Integer.parseInt(rotation);
                    swipeWH = (rota == 270 && Integer.parseInt(height) > Integer.parseInt(width)) ? true : false;
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "initVideoMediaCodec: Exception ");
                    swipeWH = sSwipeWH;
                }

                final int inputVideoWidth = format.getInteger(swipeWH ? MediaFormat.KEY_HEIGHT : MediaFormat.KEY_WIDTH);
                final int inputVideoHeight = format.getInteger(swipeWH ? MediaFormat.KEY_WIDTH : MediaFormat.KEY_HEIGHT);
                Log.d(TAG, "initVideoMediaCodec: swipeWH 是否置位 " + swipeWH + " inputVideoWidth " + inputVideoWidth + " inputVideoHeight  " + inputVideoHeight);
                videoEncode = MediaCodecUtils.createVideoEnCodec(format, mime, inputVideoWidth, inputVideoHeight, bitrate);


                videoDecoder = MediaCodec.createDecoderByType(mime);
                eglHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Surface surface = videoEncode.createInputSurface();
                        videoEncode.start();
                        isDraw = false;
                        mEglUtils = new EGLUtils();
                        mEglUtils.initEGL(surface);
                        mFramebuffer = new GLFramebuffer();
                        mFramebuffer.onCreated();
                        mFramebuffer.onChanged(inputVideoWidth, inputVideoHeight);
                        SurfaceTexture surfaceTexture = mFramebuffer.getSurfaceTexture();
                        surfaceTexture.setDefaultBufferSize(inputVideoWidth, inputVideoHeight);
                        surfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                            @Override
                            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                                ++videoDrawIndex;
                                mFramebuffer.onDrawFrame();
                                mEglUtils.swap();
                                synchronized (decoderObject) {
                                    isDraw = true;
                                    decoderObject.notifyAll();
                                }
                            }
                        });
                        videoDecoder.configure(format, new Surface(surfaceTexture), null, 0 /* Decoder */);
                        videoDecoder.start();
                        startVideoCrop();
                    }
                });
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
                Log.d(TAG, "initVideoMediaCodec:  audioTotalTime " + audioTotalTime);
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
//            Log.d(TAG, "initMuxer: ");
            muxerStart = true;
            mediaMuxer.start();
        }
    }

    private synchronized void muxerRelease() {
        if (audioEncode == null && videoEncode == null && mediaMuxer != null) {
            Log.d(TAG, "muxerRelease:");
            try {
                mediaMuxer.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaMuxer.release();
            mediaMuxer = null;

            if (exit) { // 取消的任务，删除output资源
                File f = new File(outputVideoPath);
                if (f.exists()) {
                    f.delete();
                }
                f = null;
                outputVideoPath = null;
                if (encoderListener != null) {
                    encoderListener.onError(resources.getString(R.string.vc_cancel_video_cut));
                }
            }


            isComplete = true;

            if (encoderListener != null) {
                encoderListener.onComplete(outputVideoPath);
            }

            // release

            videoHandler.removeCallbacksAndMessages(null);
            eglHandler.removeCallbacksAndMessages(null);
            audioDecoderHandler.removeCallbacksAndMessages(null);
            audioEncodeHandler.removeCallbacksAndMessages(null);

            videoHandler = null;
            eglHandler = null;
            audioDecoderHandler = null;
            audioEncodeHandler = null;

            mEglUtils = null;
            mFramebuffer = null;


            videoTrackIndex = -1;
            audioTrackIndex = -1;

            outputVideoPath = null;
            startTime = 0;
            endTime = 0;

            videoStartTime = 0;
            audioStartTime = 0;

            isDraw = false;
            isVideoInput = false;
            presentationTimeUs = 0;

            muxerStart = false;
            videoInit = false;
            audioInit = false;
            exit = false;
            videoExit = false;


            videoInputIndex = 0;
            videoOutputIndex = 0;
            videoAllOutputIndex = 0;
            videoDrawIndex = 0;
            presentationTimeUsList = null;
            outputStart = false;
        }
    }


    public boolean isComplete() {
        return isComplete;
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
