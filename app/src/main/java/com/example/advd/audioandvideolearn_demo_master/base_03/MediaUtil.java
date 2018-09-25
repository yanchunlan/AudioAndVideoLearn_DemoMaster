package com.example.advd.audioandvideolearn_demo_master.base_03;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * author: ycl
 * date: 2018-09-18 20:51
 * desc: 音视频 文件的 分割与合并
 *         编解码流控
 */
public class MediaUtil {
    private static final String TAG = "MediaUtil";

    /**
     * 音频和视频的数据进行分离(分离出来视频)
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void mediaExtractor(String path, int buffer_length) throws IOException {
        MediaExtractor mediaExtractor = new MediaExtractor();
        mediaExtractor.setDataSource(path);

        int videoTrackIndex = -1;
        int numTracks = mediaExtractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = mediaExtractor.getTrackFormat(i);
            String mine = format.getString(MediaFormat.KEY_MIME);
            if (mine.startsWith("video/")) {
                // 视频部分
                videoTrackIndex = i;
                break;
            }
        }
        // 得到视频的轨道
        mediaExtractor.selectTrack(videoTrackIndex);

        // 视频的format 及相关信息
        MediaFormat format = mediaExtractor.getTrackFormat(videoTrackIndex);
        int width = format.getInteger(MediaFormat.KEY_WIDTH);
        int height = format.getInteger(MediaFormat.KEY_HEIGHT);
        int time = format.getInteger(MediaFormat.KEY_DURATION);
        Log.d(TAG, "mediaExtractor: w: " + width + " h: " + height + " time " + time);

        ByteBuffer inputBuffer = ByteBuffer.allocate(buffer_length);
        while (mediaExtractor.readSampleData(inputBuffer, 0) >= 0) {
            int trackIndex = mediaExtractor.getSampleTrackIndex();
            long presentationTimeUs = mediaExtractor.getSampleTime();
            mediaExtractor.advance();
        }

        // 接下来根据buffer做出操作
        // ...
        // ...
        // ...

        mediaExtractor.release();
        mediaExtractor = null;
    }

    /**
     * 把音频与视频混合成一个音视频文件
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void mediaMuxer(String path, int buffer_length, boolean isAudioSample) throws IOException {
        MediaMuxer mediaMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        MediaFormat audioFormat = new MediaFormat();
        MediaFormat videoFormat = new MediaFormat();
        int audioTrackIndex = mediaMuxer.addTrack(audioFormat);
        int videoTrackIndex = mediaMuxer.addTrack(videoFormat);

        //开始合成文件
        mediaMuxer.start();

        ByteBuffer inputBuffer = ByteBuffer.allocate(buffer_length);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        boolean isFinsh = false;
        while (!isFinsh) {
//            isFinsh== getInputBuffer(inputBuffer, isAudioSample, bufferInfo);
            if (!isFinsh) {
                int currentTrackIndex = isAudioSample ? audioTrackIndex : videoTrackIndex;
                mediaMuxer.writeSampleData(currentTrackIndex, inputBuffer, bufferInfo);
            }
        }

        mediaMuxer.stop();
        mediaMuxer.release();
    }

    /**
     * 案列：
     * 从MP4文件中提取视频并生成新的视频文件
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private boolean mp4_Extractor_Muxer(String originalPath, String armsPAth, int buffer_length) throws IOException {
        // 分割
        MediaExtractor mediaExtractor = new MediaExtractor();
        mediaExtractor.setDataSource(originalPath);

        MediaMuxer mediaMuxer = null;
        int framerate = -1; // 帧率
        int videoTrackIndex = -1;
        int numTracks = mediaExtractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = mediaExtractor.getTrackFormat(i);
            String mine = format.getString(MediaFormat.KEY_MIME);
            if (mine.startsWith("video/")) {
                // 视频部分 混合
                framerate = format.getInteger(MediaFormat.KEY_FRAME_RATE);
                mediaExtractor.selectTrack(i);
                mediaMuxer = new MediaMuxer(armsPAth, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                videoTrackIndex = mediaMuxer.addTrack(format);
                mediaMuxer.start();
            }
        }

        if (mediaMuxer == null) return false;

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        info.offset = 0;
        info.size = 0;
        info.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
        info.presentationTimeUs = 0;

        ByteBuffer inputBuffer = ByteBuffer.allocate(buffer_length);
        while (mediaExtractor.readSampleData(inputBuffer, 0) >= 0) {
            info.presentationTimeUs += 1000 * 1000 / framerate;
            mediaMuxer.writeSampleData(videoTrackIndex, inputBuffer, info);
            mediaExtractor.advance();
        }
        mediaExtractor.release();

        mediaMuxer.stop();
        mediaMuxer.release();
        return true;
    }


    /**
     * 硬编码流控 动态调整目标码率
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void mediaCodec(int bieRate) throws IOException {
        // 硬编码流控
        MediaFormat mediaFormat = new MediaFormat();
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bieRate);
        mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);

        MediaCodec mediaCodec = MediaCodec.createByCodecName("");
        mediaCodec.configure(mediaFormat,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);

        Bundle params = new Bundle();
        params.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bieRate);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mediaCodec.setParameters(params);
        }
    }

}
