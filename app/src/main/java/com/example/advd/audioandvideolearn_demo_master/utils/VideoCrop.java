package com.example.advd.audioandvideolearn_demo_master.utils;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * author:  ycl
 * date:  2019/1/14 10:29
 * desc:    视频裁剪
 */
public class VideoCrop {
    private static final String TAG = "VideoCrop";

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void videoCrop(String inputVideoPath, String outputVideoPath, long startTime, long endTime) {
        MediaMetadataRetriever mMetRet = new MediaMetadataRetriever();
        mMetRet.setDataSource(inputVideoPath);
        MediaExtractor mediaExtractor = new MediaExtractor();
        MediaMuxer mediaMuxer = null;

        try {
            mediaExtractor.setDataSource(inputVideoPath);
            int audioTrackIndex = 0;
            int videoTrackIndex = 0;

            long videoDuration = 0;
            long audeoDuration = 0;

            int audioMaxSize = 0;
            int videoMaxSize = 0;

            mediaMuxer = new MediaMuxer(outputVideoPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);


            for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                MediaFormat format = mediaExtractor.getTrackFormat(i);
                long duration = format.getLong(MediaFormat.KEY_DURATION);

                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {

                    int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ?
                            format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
                    int channelCount = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ?
                            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
                    int bitRate = format.containsKey(MediaFormat.KEY_BIT_RATE) ?
                            format.getInteger(MediaFormat.KEY_BIT_RATE) : 128000;
                    audioMaxSize = format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) ?
                            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) : 100 * 1024;

                    MediaFormat encodeFormat = MediaFormat.createAudioFormat(mime, sampleRate, channelCount);//参数对应-> mime type、采样率、声道数
                    encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);//比特率
                    encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                    encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioMaxSize);


                    audioTrackIndex = mediaMuxer.addTrack(encodeFormat);
                    audeoDuration = duration;

                } else if (mime.startsWith("video/")) {
                    int videoRotation = 0;
                    int mInputVideoWidth = 0;     //输入视频的宽度
                    int mInputVideoHeight = 0;    //输入视频的高度

                    String rotation = mMetRet.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                    if (rotation != null) {
                        videoRotation = Integer.valueOf(rotation);
                    }
                    if (videoRotation == 90 || videoRotation == 270) {
                        mInputVideoHeight = format.getInteger(MediaFormat.KEY_WIDTH);
                        mInputVideoWidth = format.getInteger(MediaFormat.KEY_HEIGHT);
                    } else {
                        mInputVideoWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                        mInputVideoHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
                    }
                    videoMaxSize = format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) ?
                            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) : 100 * 1024;

                    int BIT_RATE = mInputVideoWidth * mInputVideoHeight * 5;
                    MediaFormat mediaFormat = MediaFormat.createVideoFormat(mime, mInputVideoWidth, mInputVideoHeight);
                    mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
                    mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, format.getInteger(MediaFormat.KEY_FRAME_RATE));
                    mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                    mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                    mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, videoMaxSize);

                    videoTrackIndex = mediaMuxer.addTrack(mediaFormat);
                    videoDuration = duration;
                }
            }


            mediaMuxer.start();

            for (int i = 0; i < 2; i++) {
                ByteBuffer buffer = null;
                if (i == 0) {
                    mediaExtractor.selectTrack(audioTrackIndex);
                    if (startTime != 0) {
                        mediaExtractor.seekTo(startTime, audioTrackIndex);
                    }
                    if (endTime == 0) {
                        endTime = audeoDuration;
                    }
                    buffer = ByteBuffer.allocate(audioMaxSize);
                } else {
                    mediaExtractor.selectTrack(videoTrackIndex);
                    if (startTime != 0) {
                        mediaExtractor.seekTo(startTime, videoTrackIndex);
                    }
                    if (endTime == 0) {
                        endTime = videoDuration;
                    }
                    buffer = ByteBuffer.allocate(videoMaxSize);
                }


                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                info.offset = 0;
                info.size = 0;
                info.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
                info.presentationTimeUs = 0;


                int index = 0;
                int sampleSize = -1;
                boolean returnBack = false;
                while (!returnBack&&(sampleSize=mediaExtractor.readSampleData(buffer, 0)) >= 0) {
                    long presentationTimeUs = mediaExtractor.getSampleTime();
                    if (presentationTimeUs >= startTime && presentationTimeUs <= endTime) {
                        info.set(0, sampleSize,
                                presentationTimeUs - startTime, mediaExtractor.getSampleFlags());
                        mediaMuxer.writeSampleData(audioTrackIndex, buffer, info);
                    } else if (presentationTimeUs <= endTime) {
                        returnBack=true;
                    }
                    Log.d(TAG, i==0?"audio":"video"+"   videoCrop: index: " + (index++)+" presentationTimeUs: "+presentationTimeUs);
                    mediaExtractor.advance();
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            mMetRet.release();
            mMetRet = null;

            mediaExtractor.release();
            mediaExtractor = null;

            if (mediaMuxer != null) {
                try {
                    mediaMuxer.stop();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
                mediaMuxer.release();
                mediaMuxer = null;
            }
        }

    }
}
