package com.example.advd.audioandvideolearn_demo_master.base_02.record;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import com.example.advd.audioandvideolearn_demo_master.base_02.AudioGlobalConfig;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * author: ycl
 * date: 2018-09-16 16:07
 * desc:
 */
public class AudioRecordUtil {
    private static final String TAG = "AudioRecordUtil";

    private int mMinBufferSize;
    private AudioRecord mAudioRecord;
    private boolean mIsPushing;
    private File mFile;

    // 采样率 通道 格式
    public void createAudioRecord(Context context, int sampleRateInHz, int channel) {
        if (mAudioRecord != null) {
            return;
        }
        // 单声道 立体声
        int channelConfig = channel == 1 ?
                AudioFormat.CHANNEL_IN_MONO :
                AudioFormat.CHANNEL_IN_STEREO;
        mMinBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT);
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,// 麦克风
                sampleRateInHz,// 44100,22050,11025
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,// 采样编码 一般是pcm编码16位
                mMinBufferSize); // 缓冲区数据大小

        mFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), AudioGlobalConfig.FILE_NAME);
        if (!mFile.mkdir()) {
            Log.i(TAG, "createAudioRecord:   Directory not created");
        }
        if (mFile.exists()) {
            mFile.delete();
        }
        Log.i(TAG, "filePath: "+mFile.getAbsolutePath());
    }


    public synchronized void start() {
        if (mAudioRecord != null) {
            mIsPushing = true;
            new Thread(new AudioRecordRunnable()).start();
        }
    }

    public synchronized void stopRecord() {
        stop();
        release();
    }

    private void stop() {
        if (mAudioRecord != null) {
            mIsPushing = false;
            mAudioRecord.stop();
        }
    }

    private void release() {
        if (mAudioRecord != null) {
            mAudioRecord.release();
//            mAudioRecord = null;
        }
    }

    class AudioRecordRunnable implements Runnable {
        private byte[] mData;

        public AudioRecordRunnable() {
            mData = new byte[mMinBufferSize];
        }

        @Override
        public void run() {
            mAudioRecord.startRecording();
            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(mFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            if (fileOutputStream != null) {
                while (mIsPushing) {
                    int len = mAudioRecord.read(mData, 0, mData.length);

                    Log.d(TAG, "录制音频 run: len: " + len);
                    // 对象属性没有初始化 AudioRecord.ERROR_INVALID_OPERATION
                    // 参数没有形成有效的索引 AudioRecord.ERROR_BAD_VALUE
                    if (len > 0) {
                        try {
                            // 生成的文件是PCM文件，可以用AudioTrack播放，或者加入头部成WAV,MP4等等格式文件
                            fileOutputStream.write(mData);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                try {
                    Log.i(TAG, "run: fileOutputStream.close");
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


}
