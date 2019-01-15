package com.example.advd.audioandvideolearn_demo_master.utils;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Build;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * author:  ycl
 * date:  2019/1/15 16:18
 * desc:
 */
public class MediaCodecUtils {

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
    public static MediaCodec createVideoEnCodec(MediaMetadataRetriever retriever, MediaFormat format, String mime) throws IOException {
        int videoRotation = 0;
        int mInputVideoWidth = 0;     //输入视频的宽度
        int mInputVideoHeight = 0;    //输入视频的高度

        String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
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

        int BIT_RATE = mInputVideoWidth * mInputVideoHeight * 5;
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
