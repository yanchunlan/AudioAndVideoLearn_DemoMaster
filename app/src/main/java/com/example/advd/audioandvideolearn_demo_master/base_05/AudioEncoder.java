package com.example.advd.audioandvideolearn_demo_master.base_05;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.IntentFilter;
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
import java.nio.ByteBuffer;


/**
 * author: ycl
 * date: 2018-12-16 23:55
 * desc: 音频编码类
 */
public class AudioEncoder implements Runnable {
    private static final String TAG = "AudioEncoder";

    private AudioRecord mRecord;
    private MediaCodec mEncoder;

    // 录音设置
    private String mime = MediaFormat.MIMETYPE_AUDIO_AAC; // aac
    private int rate = 256000; //波特率，256kb

    private int sampleRate = 44100; //采样率，默认44.1k
    private int channelCount = 2;  //音频采样通道，默认2通道
    private int channelConfig = channelCount == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO;////通道设置，默认立体声
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT; //设置采样数据格式，默认16比特PCM

    private int bufferSize;
    private Thread mThread;
    private boolean isRecording;

    private String mSaveName;
    private FileOutputStream fos;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void prepare( ) throws IOException {
        // init Codec
        MediaFormat format = MediaFormat.createAudioFormat(mime, sampleRate, channelCount);
        format.setInteger(MediaFormat.KEY_BIT_RATE, rate);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mEncoder = MediaCodec.createEncoderByType(mime);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); //设置为编码器

        // init record
        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        mRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,// 麦克风
                sampleRate,// 44100,22050,11025
                channelConfig,
                audioFormat,// 采样编码 一般是pcm编码16位
                bufferSize); // 缓冲区数据大小

        File f = new File( System.currentTimeMillis() + mSaveName);
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
        mRecord.startRecording();

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
        mRecord.stop();

        mEncoder.stop();
        mEncoder.release();

        fos.flush();
        fos.close();
    }


    @Override
    public void run() {
        while (isRecording) {
            try {
                readOutputData();
            } catch (IOException e) {
                e.printStackTrace();
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
    private void readOutputData() throws IOException {
        int inIndex = mEncoder.dequeueInputBuffer(-1);
        Log.d(TAG, " inIndex:" + inIndex);
        if (inIndex >= 0) {
            ByteBuffer buffer = getInputBuffer(inIndex);
            buffer.clear();
            int length = mRecord.read(buffer, bufferSize);
            Log.d(TAG, " length: " + length);
            if (length > 0) {
                mEncoder.queueInputBuffer(inIndex, 0,
                        length, System.nanoTime() / 1000, 0);
            }
        }

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outIndex;
        do {
            outIndex = mEncoder.dequeueOutputBuffer(info, 0);
            Log.d(TAG, " outIndex: " + outIndex);
            if (outIndex >= 0) {
                ByteBuffer buffer = getOutputBuffer(outIndex);
                buffer.position(info.offset);

                // 添加aac头部，buffer数据设置到temp
                byte[] temp = new byte[info.size + 7];
                buffer.get(temp, 7, info.size);
                addADTStoPacket(temp, temp.length);

                fos.write(temp);
                mEncoder.releaseOutputBuffer(outIndex, false);
            }
        } while (outIndex >= 0);
    }

    /**
     * 给编码出的aac裸流添加adts头字段
     *
     * @param packet    要空出前7个字节，否则会搞乱数据
     * @param packetLen
     */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        int freqIdx = 4;  //44.1KHz
        int chanCfg = 2;  //CPE
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    // ############################################

    public void setMime(String mime) {
        this.mime = mime;
    }

    public void setRate(int rate) {
        this.rate = rate;
    }

    public void setSaveName(String saveName) {
        mSaveName = saveName;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public void setChannelCount(int channelCount) {
        this.channelCount = channelCount;
    }
}
