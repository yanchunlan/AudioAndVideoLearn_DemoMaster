package com.example.advd.audioandvideolearn_demo_master.base_05;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


/**
 * author: ycl
 * date: 2018-12-16 23:55
 * desc: 音频编码类
 */
public class AudioEncoder implements Runnable {

    private AudioRecord mRecord;
    private MediaCodec mEncoder;

    // 录音设置
    private String mime = MediaFormat.MIMETYPE_AUDIO_AAC; // aac
    private int rate = 256000; // 采样率

    private int sampleRate = 44100; //采样率，默认44.1k
    private int channelCount = 2;  //音频采样通道，默认2通道
    private int channelConfig = channelCount == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO;////通道设置，默认立体声
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT; //设置采样数据格式，默认16比特PCM

    private int bufferSize;
    private byte[] buffer;
    private Thread mThread;
    private boolean isRecording;

    private String mSaveName;
    private FileOutputStream fos;


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void prepare(Context context) throws IOException {
        // init Codec
        MediaFormat format = MediaFormat.createAudioFormat(mime, sampleRate, channelCount);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, rate);
        mEncoder = MediaCodec.createEncoderByType(mime);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); //设置为编码器

        // init record
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        mRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,// 麦克风
                sampleRate,// 44100,22050,11025
                channelConfig,
                audioFormat,// 采样编码 一般是pcm编码16位
                bufferSize); // 缓冲区数据大小

        buffer = new byte[bufferSize];
        File f = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), System.currentTimeMillis() + mSaveName);
        if (!f.mkdir()) {
            Log.i("", "createAudioRecord:   Directory not created");
        }
        if (f.exists()) {
            f.delete();
        }
        fos = new FileOutputStream(f);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void start() throws InterruptedException {
        mEncoder.start();
        mRecord.startRecording();

        if (mThread != null && mThread.isAlive()) {
            isRecording = false;
            mThread.join();
        }

        isRecording = true;
        mThread = new Thread();
        mThread.start();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void stop() throws InterruptedException, IOException {

        mThread.join();

        isRecording = false;
        mRecord.stop();

        mEncoder.stop();
        mEncoder.release();

        fos.flush();
        fos.close();
    }

    @Override
    public void run() {

        while (isRecording) {


        }
    }
}
