package com.example.advd.audioandvideolearn_demo_master.base_03;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import android.widget.Toast;

import com.example.advd.audioandvideolearn_demo_master.R;

import java.util.ArrayList;
import java.util.List;

public class Base03Activity extends AppCompatActivity {
    private static final String TAG = "Base03Activity";

    private SurfaceView mSurfaceView;
    private TextureView mTextureView;
    private VideoRecordUtil mVideoRecordUtil;

    private static final int REQUEST_CODE_PERMISSIONS = 1002;
    // 需要申请的运行时权限
    private String[] permissions = new String[]{
            Manifest.permission.CAMERA
    };
    // 被用户拒绝的权限列表
    private List<String> mPermissionList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base03);
        requestPermissions();
        initView();
        initData();
    }

    private void initData() {
        mVideoRecordUtil = new VideoRecordUtil();
        mVideoRecordUtil.createCamera();
        mVideoRecordUtil.bindHolder(mSurfaceView, mTextureView);
    }


    private void initView() {
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mTextureView = (TextureView) findViewById(R.id.textureView);
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
