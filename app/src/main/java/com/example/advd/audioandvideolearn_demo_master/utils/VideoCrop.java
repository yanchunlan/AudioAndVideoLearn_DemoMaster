package com.example.advd.audioandvideolearn_demo_master.utils;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
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

    private MediaExtractor videoExtractor;
    private MediaCodec videoDecoder;
    private MediaCodec videoEncode;

    private MediaExtractor audioExtractor;
    private MediaCodec audioDecoder;
    private MediaCodec audioEncode;


    private MediaMetadataRetriever metadataRetriever;
    private MediaMuxer mediaMuxer;
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;


    private Handler videoDecoderHandler;
    private Handler videoEncodeHandler;
    private Handler audioDecoderHandler;
    private Handler audioEncodeHandler;


    private long startTime = 0;
    private long endTime = 0;


    private final Object audioObject = new Object();
    private final Object videoObject = new Object();

    private boolean muxerStart = false;
    private boolean videoInit = false;
    private boolean audioInit = false;


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

    public void videoCrop(String inputVideoPath, String outputVideoPath, long startTime, long endTime) {
        File inputFile = new File(inputVideoPath);
        if (!inputFile.exists()) {
            if (encoderListener != null) {
                encoderListener.onError("inputVideoPath not exists");
            }
            return;
        }
        File outputFile = new File(outputVideoPath);
        if (!outputFile.exists()) {
            if (encoderListener != null) {
                encoderListener.onError("outputVideoPath not exists");
            }
            return;
        }
        if (startTime >= endTime) {
            if (encoderListener != null) {
                encoderListener.onError("startTime more than the endTime");
            }
            return;
        }

        metadataRetriever = new MediaMetadataRetriever();
        metadataRetriever.setDataSource(inputVideoPath);

        videoExtractor = new MediaExtractor();
        audioExtractor = new MediaExtractor();
        try {
            videoExtractor.setDataSource(inputVideoPath);
            audioExtractor.setDataSource(inputVideoPath);

            mediaMuxer = new MediaMuxer(outputVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            initAudioMediaCodec();
            initVideoMediaCodec();
            start();
        } catch (IOException e) {
            e.printStackTrace();
            if (encoderListener != null) {
                encoderListener.onError("init video Cropping failed ");
            }
        }
    }

    private void start() {
        Log.d(TAG, "start: startTime "+startTime+" endTime "+endTime);
        if (encoderListener != null) {
            encoderListener.onStart();
        }
        muxerStart = false;
        videoInit = false;
        audioInit = false;
        // 暂时不需要了
        if (metadataRetriever != null) {
            metadataRetriever.release();
            metadataRetriever = null;
        }

        // video
        videoDecoderHandler.post(new Runnable() {
            @Override
            public void run() {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (true) {
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
                    extractorVideoInputBuffer(videoExtractor, videoDecoder);

                    // decoder output
                    int outIndex = videoDecoder.dequeueOutputBuffer(info, 10000);
                    if (outIndex >= 0) {
                        ByteBuffer buffer = MediaCodecUtils.getOutputBuffer(videoDecoder, outIndex);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            info.size = 0;
//                                videoDecoder.releaseOutputBuffer(outIndex, false);
                        }
                        if (info.size != 0) {
                            long presentationTimeUs = info.presentationTimeUs;
                            if (presentationTimeUs >= startTime && presentationTimeUs <= endTime) {
                                info.presentationTimeUs = presentationTimeUs - startTime;
                                buffer.position(info.offset);
                                buffer.limit(info.offset + info.size);

                                // encode input
                                encodeInputBuffer(buffer, videoEncode, info);
                            }
                            videoDecoder.releaseOutputBuffer(outIndex, false);
                            if (presentationTimeUs > endTime) {
//                                    videoEncode.signalEndOfInputStream();
                                break;
                            }
                        }
                    }

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
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
        videoEncodeHandler.post(new Runnable() {
            @Override
            public void run() {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (true) {
                    int outIndex = videoEncode.dequeueOutputBuffer(info, 1000);
                    if (outIndex >= 0) {
                        ByteBuffer buffer = MediaCodecUtils.getOutputBuffer(videoEncode, outIndex);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            info.size = 0;
//                            videoDecoder.releaseOutputBuffer(outIndex, false);
                        }
                        if (info.size != 0) {
                            long presentationTimeUs = info.presentationTimeUs;
                            if (presentationTimeUs >= startTime && presentationTimeUs <= endTime) {
                                info.presentationTimeUs = presentationTimeUs - startTime;
                                buffer.position(info.offset);
                                buffer.limit(info.offset + info.size);
                                // write
                                mediaMuxer.writeSampleData(videoTrackIndex, buffer, info);
                            }
                            if (encoderListener != null) {
                                encoderListener.onProgress((int) ((presentationTimeUs - startTime) * 100.0f / (endTime - startTime)));
                            }
                            videoEncode.releaseOutputBuffer(outIndex, false);
                            if (presentationTimeUs > endTime) {
                                break;
                            }
                        }
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (mediaMuxer != null && videoTrackIndex == -1) {
                            videoTrackIndex = mediaMuxer.addTrack(videoEncode.getOutputFormat());
                            videoInit = true;
                            initMuxer();
                        }
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
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

        // audio
        audioDecoderHandler.post(new Runnable() {
            @Override
            public void run() {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (true) {
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
                    extractorInputBuffer(audioExtractor, audioDecoder);

                    // decoder output
                    int outIndex = audioDecoder.dequeueOutputBuffer(info, 10000);

                    Log.d(TAG, "run: audioDecoder  outIndex "+outIndex+" presentationTimeUs "
                                +info.presentationTimeUs+" size "+info.size+" flag "+info.flags+" offset "+info.offset);

                    if (outIndex >= 0) {
                        ByteBuffer buffer = MediaCodecUtils.getOutputBuffer(audioDecoder, outIndex);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            info.size = 0;
                            Log.d(TAG, "run: audioDecoder size==0");
//                            videoDecoder.releaseOutputBuffer(outIndex, false);
                        }
                        if (info.size != 0) {
                            Log.d(TAG, "run: audioDecoder size!=0");
                            long presentationTimeUs = info.presentationTimeUs;
                            if (presentationTimeUs >= startTime && presentationTimeUs <= endTime) {
                                info.presentationTimeUs = presentationTimeUs - startTime;
                                buffer.position(info.offset);
                                buffer.limit(info.offset + info.size);
                                Log.d(TAG, "run: audioDecoder presentationTimeUs "
                                        +info.presentationTimeUs+" size "+info.size+" flag "+info.flags+" offset "+info.offset);

                                // encode input
                                encodeInputBuffer(buffer, audioEncode, info);
                            }
                            audioDecoder.releaseOutputBuffer(outIndex, false);
                            if (presentationTimeUs > endTime) {
                                Log.d(TAG, "run: audioDecoder  > endTime");
                                break;
                            }
                        }
                    }

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG, "run: audioDecoder  info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0");
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
        audioEncodeHandler.post(new Runnable() {
            @Override
            public void run() {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (true) {
                    int outIndex = audioEncode.dequeueOutputBuffer(info, 1000);
                    Log.d(TAG, "run: audioEncode  outIndex "+outIndex+" presentationTimeUs "
                            +info.presentationTimeUs+" size "+info.size+" flag "+info.flags+" offset "+info.offset);

                    if (outIndex >= 0) {
                        ByteBuffer buffer = MediaCodecUtils.getOutputBuffer(audioEncode, outIndex);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            info.size = 0;
                            Log.d(TAG, "run: audioEncode size==0");
//                            videoDecoder.releaseOutputBuffer(outIndex, false);
                        }
                        if (info.size != 0) {
                            Log.d(TAG, "run: audioEncode size!=0");
                            long presentationTimeUs = info.presentationTimeUs;
                            if (presentationTimeUs >= startTime && presentationTimeUs <= endTime) {
                                info.presentationTimeUs = presentationTimeUs - startTime;
                                buffer.position(info.offset);
                                buffer.limit(info.offset + info.size);
                                Log.d(TAG, "run: audioEncode presentationTimeUs "
                                        +info.presentationTimeUs+" size "+info.size+" flag "+info.flags+" offset "+info.offset);
                                // write
                                mediaMuxer.writeSampleData(audioTrackIndex, buffer, info);
                            }
                            audioEncode.releaseOutputBuffer(outIndex, false);
                            if (presentationTimeUs > endTime) {
                                Log.d(TAG, "run: audioEncode  > endTime");
                                break;
                            }
                        }
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (mediaMuxer != null && audioTrackIndex == -1) {
                            audioTrackIndex = mediaMuxer.addTrack(audioEncode.getOutputFormat());
                            audioInit = true;

                            Log.d(TAG, "run: audioEncode   mediaMuxer.addTrack");
//                            initMuxer();
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

    private int extractorVideoInputBuffer(MediaExtractor mediaExtractor, MediaCodec mediaCodec) {
        int inputIndex = mediaCodec.dequeueInputBuffer(50000);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer = MediaCodecUtils.getInputBuffer(mediaCodec, inputIndex);
            long sampleTime = mediaExtractor.getSampleTime();
            int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);
            if (mediaExtractor.advance()) {
                mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);
                return 1;
            } else {
                if (sampleSize > 0) {
                    mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);
//                    mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    return 1;
                } else {
//                    mediaCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    return -1;
                }
            }
        }
        return 0;
    }

    private void extractorInputBuffer(MediaExtractor mediaExtractor, MediaCodec mediaCodec) {
        int inputIndex = mediaCodec.dequeueInputBuffer(50000);
        Log.d(TAG, "audioDecoder extractorInputBuffer: inputIndex "+inputIndex);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer = MediaCodecUtils.getInputBuffer(mediaCodec, inputIndex);
            long sampleTime = mediaExtractor.getSampleTime();
            int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);
            Log.d(TAG, "audioDecoder extractorInputBuffer: sampleTime "+sampleTime+" sampleSize "+sampleSize);
            if (mediaExtractor.advance()) {
                mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);
                Log.d(TAG, "audioDecoder extractorInputBuffer: advance ");
            } else {// 结束的数据
                if (sampleSize > 0) {
                    mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    Log.d(TAG, "audioDecoder extractorInputBuffer: >0 ");
                } else {// 结束了也要添加一个为0 的数据
                    mediaCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    Log.d(TAG, "audioDecoder extractorInputBuffer: <=0 ");
                }
            }
        }
    }

    private void encodeInputBuffer(ByteBuffer buffer, MediaCodec mediaCodec, MediaCodec.BufferInfo info) {
        int inputIndex = mediaCodec.dequeueInputBuffer(50000);
        Log.d(TAG, "audioDecoder encodeInputBuffer:inputIndex "+inputIndex);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer = MediaCodecUtils.getInputBuffer(mediaCodec, inputIndex);
            inputBuffer.clear();
            inputBuffer.put(buffer);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) { // 结束的数据
                Log.d(TAG, "audioDecoder encodeInputBuffer: !=0");
                mediaCodec.queueInputBuffer(inputIndex, 0, buffer.limit(), info.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else { // 正常数据
                Log.d(TAG, "audioDecoder encodeInputBuffer: ==0");
                mediaCodec.queueInputBuffer(inputIndex, 0, buffer.limit(), info.presentationTimeUs, 0);
            }

        }
    }

    private void initVideoMediaCodec() throws IOException {
        for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
            final MediaFormat format = videoExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                videoExtractor.selectTrack(i);
                if (startTime != 0) {
                    videoExtractor.seekTo(startTime, i);
                }
                videoDecoder = MediaCodec.createDecoderByType(mime);
                videoDecoder.configure(format, null, null, 0 /* Decoder */);

                videoEncode = MediaCodecUtils.createVideoEnCodec(metadataRetriever, format, mime);

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
                audioDecoder = MediaCodec.createDecoderByType(mime);
                audioDecoder.configure(format, null, null, 0 /* Decoder */);

                audioEncode = MediaCodecUtils.createAudioEnCodec(format, mime);

                audioExtractor.seekTo(startTime, i);
                if (endTime == 0) {
                    this.endTime = format.getLong(MediaFormat.KEY_DURATION);
                }

                audioDecoder.start();
                audioEncode.start();
                break;
            }
        }
    }

    private synchronized void initMuxer() {
//        if (videoInit && audioInit) {
        Log.d(TAG, "initMuxer: ");
            muxerStart = true;
            mediaMuxer.start();
//        }
    }

    private synchronized void muxerRelease() {
        if (audioEncode == null && videoEncode == null && mediaMuxer != null) {
            Log.d(TAG, "muxerRelease:");
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
            if (encoderListener != null) {
                encoderListener.onStop();
            }

            release();
        }
    }

    public void release() {
        videoTrackIndex = -1;
        audioTrackIndex = -1;

        startTime = 0;
        endTime = 0;

        muxerStart = false;
        videoInit = false;
        audioInit = false;
    }

    private OnEncoderListener encoderListener;

    public void setEncoderListener(OnEncoderListener encoderListener) {
        this.encoderListener = encoderListener;
    }

    public interface OnEncoderListener {
        void onStart();

        void onStop();

        void onProgress(int progress);

        void onError(String msg);
    }
}
