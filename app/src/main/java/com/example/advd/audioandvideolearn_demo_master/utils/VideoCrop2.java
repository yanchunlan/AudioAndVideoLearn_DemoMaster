package com.example.advd.audioandvideolearn_demo_master.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
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

    private final Object decoderObject = new Object();
    private final Object audioObject = new Object();
    private final Object videoObject = new Object();


    private boolean isVideoInput = false;
    private boolean isDraw = false;
    private long presentationTimeUs;


    private boolean muxerStart = false;
    private boolean videoInit = false;
    private boolean audioInit = false;
    private boolean exit = false;
    private boolean isComplete = false;


    private EGLUtils mEglUtils = null;
    private GLFramebuffer mFramebuffer = null;


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

    public void start(String inputVideoPath, String outputVideoPath, long startTime, long endTime) {
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
        Log.d(TAG, "start inputVideoPath " + inputVideoPath + " outputVideoPath " + outputVideoPath
                + " startTime " + startTime + " endTime " + endTime);

        videoExtractor = new MediaExtractor();
        audioExtractor = new MediaExtractor();

        try {
            videoExtractor.setDataSource(inputVideoPath);
            audioExtractor.setDataSource(inputVideoPath);

            mediaMuxer = new MediaMuxer(outputVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            initAudioMediaCodec();
            initVideoMediaCodec();
        } catch (IOException e) {
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

        isVideoInput = false;
        muxerStart = false;
        videoInit = false;
        audioInit = false;
        exit = false;
        isComplete = false;

        // video
        if (videoDecoder != null && videoEncode != null && mediaMuxer != null) {
            if (audioDecoder == null && audioEncode == null) { // 单视频，就需要开启音频条件
                audioInit = true;
            }
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
                            }
                        }

//                        if (run == 1) {

//                       // decoder output
                        info.set(0, 0, 0, 0);
                        int outIndex = videoDecoder.dequeueOutputBuffer(info, 10000);

                        // 获取不到数据了就结束
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.d(TAG, "run: videoDecoder  info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0");
                            videoEncode.signalEndOfInputStream();
                            break;
                        }

                        presentationTimeUs = info.presentationTimeUs;
//                        Log.d(TAG, "run: videoDecoder  outIndex " + outIndex + " presentationTimeUs "
//                                + info.presentationTimeUs + " size " + info.size + " flag " + info.flags + " offset " + info.offset + "  run  " + run);
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
                                encodeVideoOutputBuffer(videoEncode, info, presentationTimeUs);
                            } else {
                                Log.i(TAG, "run: not draw");
                            }
                            if (presentationTimeUs > endTime) {
                                Log.d(TAG, "run: videoDecoder  > endTime");
                                videoEncode.signalEndOfInputStream();
                                break;
                            }
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
        int outIndex = videoEncode.dequeueOutputBuffer(info, 1000);
//                        Log.d(TAG, "run: videoEncode  outIndex " + outIndex + " presentationTimeUs "
//                                + info.presentationTimeUs + " size " + info.size + " flag "
//                                + info.flags + " offset " + info.offset);
        if (outIndex >= 0) {
            ByteBuffer buffer = MediaCodecUtils.getOutputBuffer(videoEncode, outIndex);
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                info.size = 0;
//                                Log.d(TAG, "run: videoEncode size==0");
                videoEncode.releaseOutputBuffer(outIndex, false);
            }
            if (info.size != 0) {
//                                Log.d(TAG, "run: videoEncode size!=0");
                if (presentationTimeUs >= startTime && presentationTimeUs <= endTime) {
                    info.presentationTimeUs = presentationTimeUs - startTime;
                    buffer.position(info.offset);
                    buffer.limit(info.offset + info.size);
//                                    Log.d(TAG, "run: videoEncode presentationTimeUs "
//                                            + info.presentationTimeUs + " size " + info.size
//                                            + " flag " + info.flags + " offset " + info.offset);
                    // write
                    mediaMuxer.writeSampleData(videoTrackIndex, buffer, info);
                    if (encoderListener != null) {
                        encoderListener.onProgress((int) ((presentationTimeUs - startTime) * 100.0f / (endTime - startTime)));
                    }
                }
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

    private void initVideoMediaCodec() throws IOException {
        for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
            final MediaFormat format = videoExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                videoExtractor.selectTrack(i);
               /* if (startTime != 0) {
                    videoExtractor.seekTo(startTime, i);
                }*/
                long videoTotalTime = format.getLong(MediaFormat.KEY_DURATION);
//                AvLog.d("videoTotalTime "+videoTotalTime);
                if (endTime == 0) {
                    this.endTime = videoTotalTime;
                } else if (endTime > videoTotalTime) { // 取最小值
                    this.endTime = videoTotalTime;
                }

                final int inputVideoWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                final int inputVideoHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
                Log.d(TAG, "createVideoEnCodec: inputVideoWidth " + inputVideoWidth + " inputVideoHeight  " + inputVideoHeight);
                videoEncode = MediaCodecUtils.createVideoEnCodec(format, mime, inputVideoWidth, inputVideoHeight);


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

            isDraw = false;
            isVideoInput = false;

            muxerStart = false;
            videoInit = false;
            audioInit = false;
            exit = false;
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
