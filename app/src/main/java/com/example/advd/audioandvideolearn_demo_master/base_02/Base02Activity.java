package com.example.advd.audioandvideolearn_demo_master.base_02;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.advd.audioandvideolearn_demo_master.R;
import com.example.advd.audioandvideolearn_demo_master.base_02.play.AudioTrackUtil;
import com.example.advd.audioandvideolearn_demo_master.base_02.record.AudioRecordUtil;

import java.util.ArrayList;
import java.util.List;

public class Base02Activity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "Base02Activity";

    private Button mBtStart;
    private Button mBtEnd;
    private Button mBtStartPlay;
    private Button mBtEndPlay;

    private AudioRecordUtil mAudioRecordUtil;
    private AudioTrackUtil mAudioTrackUtil;


    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    // 需要申请的运行时权限
    private String[] permissions = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
    };
    // 被用户拒绝的权限列表
    private List<String> mPermissionList = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base02);
        initView();
        initData();
        requestPermissions();
    }

    private void initData() {
        mAudioRecordUtil = new AudioRecordUtil();
        mAudioRecordUtil.createAudioRecord(this,
                AudioGlobalConfig.SAMPLE_RATE_INHZ,
                AudioGlobalConfig.CHANNEL_CONFIG);

        mAudioTrackUtil = new AudioTrackUtil();
        mAudioTrackUtil.createAudioTrack(this,
                AudioGlobalConfig.SAMPLE_RATE_INHZ,
                AudioGlobalConfig.CHANNEL_CONFIG);
    }

    private void initView() {
        mBtStart = (Button) findViewById(R.id.bt_start);
        mBtEnd = (Button) findViewById(R.id.bt_end);
        mBtStartPlay = (Button) findViewById(R.id.bt_start_play);
        mBtEndPlay = (Button) findViewById(R.id.bt_end_play);

        mBtStart.setOnClickListener(this);
        mBtEnd.setOnClickListener(this);
        mBtStartPlay.setOnClickListener(this);
        mBtEndPlay.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_start:
                if (mAudioRecordUtil != null) {
                    mAudioRecordUtil.start();
                }
                break;
            case R.id.bt_end:
                if (mAudioRecordUtil != null) {
                    mAudioRecordUtil.stopRecord();
                }
                break;
            case R.id.bt_start_play:
                if (mAudioTrackUtil != null) {
                    mAudioTrackUtil.start();
                }
                break;
            case R.id.bt_end_play:
                if (mAudioTrackUtil != null) {
                    mAudioTrackUtil.stopTrack();
                }
                break;
        }
    }


    // --------------------------------------   Permissions  start  ---------------------------------------------
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String p : permissions) {
                if (ContextCompat.checkSelfPermission(this, p) !=
                        PackageManager.PERMISSION_GRANTED) {
                    mPermissionList.add(p);
                }
            }
            // 只申请被用户拒绝过了的权限
            if (!mPermissionList.isEmpty()) {
                String[] permission = mPermissionList.toArray(new String[mPermissionList.size()]);
                ActivityCompat.requestPermissions(this, permission, REQUEST_CODE_PERMISSIONS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            for (int results : grantResults) {
                if (results != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "onRequestPermissionsResult: results: " + results);
                    Toast.makeText(this, "权限禁止", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    // --------------------------------------   Permissions  end  ---------------------------------------------

}
