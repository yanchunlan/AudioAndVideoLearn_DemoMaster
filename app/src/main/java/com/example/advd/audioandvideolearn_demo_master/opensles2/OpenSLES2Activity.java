package com.example.advd.audioandvideolearn_demo_master.opensles2;

import android.Manifest;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.advd.audioandvideolearn_demo_master.R;
import com.example.advd.audioandvideolearn_demo_master.utils.PermissionUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class OpenSLES2Activity extends AppCompatActivity implements View.OnClickListener {

    static {
        System.loadLibrary("OpenSLES_Player");
    }

    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    private Button mBtnPlayAssets;
    private Button mBtnPlayPcm;
    private Button mBtnPlayJavapcm;

    private boolean isPlaying = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PermissionUtils.requestPermissions(this,
                new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.MODIFY_AUDIO_SETTINGS,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.CAMERA},
                REQUEST_CODE_PERMISSIONS,
                okRunnable);
    }

    private Runnable okRunnable = new Runnable() {
        @Override
        public void run() {
            setContentView(R.layout.activity_open_sles2);
            initView();
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionUtils.onRequestPermissionsResult(requestCode == REQUEST_CODE_PERMISSIONS,
                grantResults,
                okRunnable,
                new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(OpenSLES2Activity.this, "没有获得必要的权限", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    private void initView() {
        mBtnPlayAssets = (Button) findViewById(R.id.btn_play_assets);
        mBtnPlayPcm = (Button) findViewById(R.id.btn_play_pcm);
        mBtnPlayJavapcm = (Button) findViewById(R.id.btn_play_javapcm);

        mBtnPlayAssets.setOnClickListener(this);
        mBtnPlayPcm.setOnClickListener(this);
        mBtnPlayJavapcm.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_play_assets: // 播放assets
                playAudioByOpenSL_1assets(getAssets(), "gebitaishang.mp3");
                break;
            case R.id.btn_play_pcm: // 播放pcm
                playAudioByOpenSL_pcm(Environment.getExternalStorageDirectory().getAbsolutePath() + "test/test.pcm");
                break;
            case R.id.btn_play_javapcm:  // 从java层获取PCM数据，底层播放
                if (!isPlaying) {
                    isPlaying = true;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                InputStream in = new FileInputStream(Environment.getExternalStorageDirectory()
                                        .getAbsolutePath() + "test/test.pcm");
                                byte[] buffer = new byte[44100 * 2 * 2];
                                int n = -1;
                                while ((n = in.read(buffer)) != -1) {
                                    sendPcmData(buffer, n);
                                    Thread.sleep(800);
                                }
                                isPlaying = false;
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                }
                break;
        }
    }

    public native void playAudioByOpenSL_1assets(AssetManager assetManager, String fileName);

    public native void playAudioByOpenSL_pcm(String pcmPath);

    public native void sendPcmData(byte[] data, int size); // java层提供pcm数据，opensl底层播放
}
