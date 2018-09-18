package com.example.advd.audioandvideolearn_demo_master.base_02.play;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import com.example.advd.audioandvideolearn_demo_master.base_02.AudioGlobalConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * author: ycl
 * date: 2018-09-16 17:33
 * desc:  播放有2种模式，MODE_STREAM/MODE_STATIC , 一个是while中边读play边write，一个是直接write之后就play
 */
public class AudioTrackUtil {
    private static final String TAG = "AudioTrackUtil";

    private AudioTrack mAudioTrack;
    private int mMinBufferSize;
    private File mFile;


    // mode == MODE_STREAM
    public void createAudioTrack(Context context, int sampleRateInHz, int channel) {
        if (mAudioTrack != null) {
            return;
        }
        int channelConfig = channel == 1 ? AudioFormat.CHANNEL_OUT_MONO
                : AudioFormat.CHANNEL_OUT_STEREO;// 此处是输出所以是out开头的
        mMinBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT);
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRateInHz,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                mMinBufferSize,
                AudioTrack.MODE_STREAM  // 如果采用STATIC模式，须先调用write写数据，然后再调用play,否则就是边读边写
        );
        mFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), AudioGlobalConfig.FILE_NAME);
        Log.i(TAG, "filePath: "+mFile.getAbsolutePath());
    }

    public void stopTrack() {
        stop();
        release();
    }

    public void start() {
        if (mAudioTrack != null) {
            new Thread(new AudioTrackRunnable()).start();
        }
    }

    private void pause() {
        if (mAudioTrack != null) {
            mAudioTrack.pause();
        }
    }

    private void stop() {
        if (mAudioTrack != null) {
            mAudioTrack.stop();
        }
    }

    private void release() {
        if (mAudioTrack != null) {
            mAudioTrack.release();
//            mAudioTrack = null;
        }
    }

    class AudioTrackRunnable implements Runnable {
        @Override
        public void run() {
            // mode == AudioTrack.MODE_STREAM
            byte[] data = new byte[mMinBufferSize];
            FileInputStream inputStream = null;
            int readCount = 0;
            try {
                inputStream = new FileInputStream(mFile);
                while (inputStream.available() > 0) {
                    readCount = inputStream.read(data);
                    Log.d(TAG, "run: readCount: " + readCount);
                    if (readCount == AudioTrack.ERROR_BAD_VALUE || readCount == AudioTrack.ERROR_INVALID_OPERATION) {
                        continue;
                    }
                    if (readCount != 0 && readCount != -1) {
                        mAudioTrack.play();
                        mAudioTrack.write(data, 0, readCount);
                    }
                }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (inputStream != null) {
                    try {
                        Log.i(TAG, "run: inputStream close");
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    // ----------------------------------------- static start -----------------------------------------
    // mode == MODE_STATIC
    public void createStaticAudioTrack(int sampleRateInHz, int channel, int minBufferSize) {
        if (mAudioTrack != null) {
            return;
        }
        int channelConfig = channel == 1 ? AudioFormat.CHANNEL_OUT_MONO
                : AudioFormat.CHANNEL_OUT_STEREO;// 此处是输出所以是out开头的
        if (minBufferSize <= 0) {
            mMinBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_16BIT);
        } else {
            mMinBufferSize = minBufferSize;
        }
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRateInHz,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                mMinBufferSize,
                AudioTrack.MODE_STATIC  // 如果采用STATIC模式，须先调用write写数据，然后再调用play,否则就是边读边写
        );
    }

    private void start(byte[] data) {
        if (mAudioTrack != null) {
            new Thread(new AudioTrackStaticRunnable(data)).start();
        }
    }

    class AudioTrackStaticRunnable implements Runnable {
        private byte[] data;

        public AudioTrackStaticRunnable(byte[] data) {
            this.data = data;
        }

        @Override
        public void run() {
            // mode == AudioTrack.MODE_STATIC
            mAudioTrack.write(data, 0, data.length);
            mAudioTrack.play();


            // mode == AudioTrack.MODE_STREAM
//            while (mAudioTrack.)

        }
    }
    // ----------------------------------------- static start -----------------------------------------


    // 专门提供给外部使用得
    public void start(Context context) {
        new AsyncTask<Context, Void, byte[]>() {
            @Override
            protected byte[] doInBackground(Context... voids) {
                Context context1 = voids[0];
                File mFile = new File(context1.getExternalFilesDir(Environment.DIRECTORY_MUSIC), AudioGlobalConfig.FILE_NAME);

                FileInputStream fileInputStream = null;
                ByteArrayOutputStream byteArrayOutputStream = null;
                byte[] data = null;
                try {
                    fileInputStream = new FileInputStream(mFile);
                    byteArrayOutputStream = new ByteArrayOutputStream();
                    byte[] bytes = new byte[1024];
                    int len = -1;
                    while ((len = fileInputStream.read(bytes)) != -1) {
                        byteArrayOutputStream.write(bytes, 0, bytes.length);
                    }
                    data = byteArrayOutputStream.toByteArray();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    if (fileInputStream != null) {
                        try {
                            fileInputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (byteArrayOutputStream != null) {
                        try {
                            byteArrayOutputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

                return data;
            }

            @Override
            protected void onPostExecute(byte[] data) {
                super.onPostExecute(data);
                createStaticAudioTrack(AudioGlobalConfig.SAMPLE_RATE_INHZ, 1, data.length);
                start(data);
            }
        }.execute(context);
    }


}
