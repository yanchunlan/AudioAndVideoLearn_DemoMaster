package com.example.advd.audioandvideolearn_demo_master.opensles1;

import android.Manifest;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.advd.audioandvideolearn_demo_master.R;
import com.example.advd.audioandvideolearn_demo_master.base_02.AudioGlobalConfig;
import com.example.advd.audioandvideolearn_demo_master.utils.FileUtils;
import com.example.advd.audioandvideolearn_demo_master.utils.PermissionUtils;

import java.io.File;

public class OpenSLESActivity extends AppCompatActivity implements View.OnClickListener {
    static {
        System.loadLibrary("OpenSLES_Record");
    }

    private static final int REQUEST_CODE_PERMISSIONS = 1001;

    private Button mBtnStartRecord;
    private Button mBtnStopRecord;
    private TextView mTvRecordPath;
    private File mFile;

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
            setContentView(R.layout.activity_open_sles);
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
                        Toast.makeText(OpenSLESActivity.this, "没有获得必要的权限", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }
    public native void startRecord(String path);

    public native void stopRecord();

    private void initView() {
        mBtnStartRecord = (Button) findViewById(R.id.btn_startRecord);
        mBtnStopRecord = (Button) findViewById(R.id.btn_stopRecord);
        mTvRecordPath = (TextView) findViewById(R.id.tv_recordPath);

        mBtnStartRecord.setOnClickListener(this);
        mBtnStopRecord.setOnClickListener(this);

        mFile = FileUtils.createTempFile(this,AudioGlobalConfig.FILE_DIR_NAME, "/opensles_record.pcm");
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_startRecord:
                startRecord(mFile.getAbsolutePath());
                break;
            case R.id.btn_stopRecord:
                stopRecord();
                mTvRecordPath.setText(mFile.getAbsolutePath());
                // 使用audacity播放
                /**
                 1. 使用Android OpenSL ES 开发：使用 OpenSL 播放 PCM 数据的demo进行播放。
                 2. 使用 ffplay 命令播放，命令为：ffplay -f s16le -ar 44100 -ac 2 temp.pcm
                 （命令由来：在录制代码里的参数为录制规格：PCM、2声道、44100HZ、16bit）
                 */
                break;
        }
    }
}
