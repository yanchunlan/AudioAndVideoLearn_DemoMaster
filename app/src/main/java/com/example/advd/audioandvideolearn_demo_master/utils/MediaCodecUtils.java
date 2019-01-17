package com.example.advd.audioandvideolearn_demo_master.utils;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * author:  ycl
 * date:  2019/1/15 16:18
 * desc:
 */
public class MediaCodecUtils {
    private static final String TAG = "MediaCodecUtils";

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static int checkColorFormat(String mime) {
        if (Build.MODEL.equals("HUAWEI P6-C00")) {
            return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
        }
        int count = 0;
        try {
            count = MediaCodecList.getCodecCount();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (count != 0) {
            for (int i = 0; i < count; i++) {
                try {
                    MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                    if (info != null && info.isEncoder()) {
                        String[] types = info.getSupportedTypes();
                        if (types != null && types.length > 0) {
                            for (String type : types) {
                                if (type.equals(mime)) {
                                    MediaCodecInfo.CodecCapabilities c = info.getCapabilitiesForType(type);
                                    if (c != null && c.colorFormats != null && c.colorFormats.length > 0) {
                                        for (int j = 0; j < c.colorFormats.length; j++) {
                                            if (c.colorFormats[j] == MediaCodecInfo.CodecCapabilities
                                                    .COLOR_FormatYUV420Planar) {
                                                return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
                                            } else if (c.colorFormats[j] == MediaCodecInfo.CodecCapabilities
                                                    .COLOR_FormatYUV420SemiPlanar) {
                                                return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static MediaCodec createAudioEnCodec(MediaFormat format, String mime) throws IOException {
        int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ?
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
        int channelCount = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ?
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
        int bitRate = format.containsKey(MediaFormat.KEY_BIT_RATE) ?
                format.getInteger(MediaFormat.KEY_BIT_RATE) : 128000;
        int audioMaxSize = format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE) ?
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) : 100 * 1024;

        MediaCodec audioEncode = MediaCodec.createEncoderByType(mime);
        MediaFormat encodeFormat = MediaFormat.createAudioFormat(mime, sampleRate, channelCount);//参数对应-> mime type、采样率、声道数
        encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);//比特率
        encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioMaxSize);
        audioEncode.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        return audioEncode;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static MediaCodec createVideoEnCodec(MediaFormat format, String mime) throws IOException {
        // 0   1280 720   ok
        // 0  1692  720    花屏
        int mInputVideoWidth = format.getInteger(MediaFormat.KEY_WIDTH);
        int mInputVideoHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
        Log.d(TAG, "createVideoEnCodec: mInputVideoWidth " + mInputVideoWidth + " mInputVideoHeight  " + mInputVideoHeight);

        int BIT_RATE = mInputVideoWidth * mInputVideoHeight * 3;
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(mime, mInputVideoWidth, mInputVideoHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, format.getInteger(MediaFormat.KEY_FRAME_RATE));
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecUtils.checkColorFormat(mime));
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
        }

        MediaCodec videoEncode = MediaCodec.createEncoderByType(mime);
        videoEncode.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        return videoEncode;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static void extractorInputBuffer(MediaExtractor mediaExtractor, MediaCodec mediaCodec) {
        int inputIndex = mediaCodec.dequeueInputBuffer(50000);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer = MediaCodecUtils.getInputBuffer(mediaCodec, inputIndex);
            long sampleTime = mediaExtractor.getSampleTime();
            int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);
            if (mediaExtractor.advance()) {
                mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);
            } else {// 结束的数据
                if (sampleSize > 0) {
                    mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {// 结束了也要添加一个为0 的数据
                    mediaCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static void encodeInputBuffer(ByteBuffer buffer, MediaCodec mediaCodec, MediaCodec.BufferInfo info) {
        int inputIndex = mediaCodec.dequeueInputBuffer(50000);
        if (inputIndex >= 0) {
            ByteBuffer inputBuffer = MediaCodecUtils.getInputBuffer(mediaCodec, inputIndex);
            inputBuffer.clear();
            inputBuffer.put(buffer);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) { // 结束的数据
                mediaCodec.queueInputBuffer(inputIndex, 0, buffer.limit(), info.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else { // 正常数据
                mediaCodec.queueInputBuffer(inputIndex, 0, buffer.limit(), info.presentationTimeUs, 0);
            }
        }
    }

    public static ByteBuffer getInputBuffer(MediaCodec codec, int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return codec.getInputBuffer(index);
        } else {
            return codec.getInputBuffers()[index];
        }
    }

    public static ByteBuffer getOutputBuffer(MediaCodec codec, int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return codec.getOutputBuffer(index);
        } else {
            return codec.getOutputBuffers()[index];
        }
    }
}
