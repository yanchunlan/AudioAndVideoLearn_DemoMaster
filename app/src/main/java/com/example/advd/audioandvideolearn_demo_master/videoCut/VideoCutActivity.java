package com.example.advd.audioandvideolearn_demo_master.videoCut;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.advd.audioandvideolearn_demo_master.R;
import com.example.advd.audioandvideolearn_demo_master.utils.GetPathFromUri4kitkat;
import com.example.advd.audioandvideolearn_demo_master.utils.PermissionUtils;
import com.example.advd.audioandvideolearn_demo_master.utils.VideoCrop;

public class VideoCutActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "VideoCutActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 1001;

    private Button mButton;
    private Button mButton1;

    private VideoCrop crop;


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
            setContentView(R.layout.activity_video_cut);
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
                        Toast.makeText(VideoCutActivity.this, "没有获得必要的权限", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    private void initView() {
        mButton = (Button) findViewById(R.id.button);
        mButton1 = (Button) findViewById(R.id.button1);
        mButton.setOnClickListener(this);
        mButton1.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button:
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("video/mp4"); //选择视频 （mp4 3gp 是android支持的视频格式）
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, 1);
                break;
            case R.id.button1:
                if (crop != null) {
                    crop.stop();
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            String path = getRealFilePath(data.getData());
            if (path != null) {
                Log.d(TAG, "onActivityResult: path: " + path);
                crop = new VideoCrop();
                crop.setEncoderListener(new VideoCrop.OnEncoderListener() {
                    @Override
                    public void onStart() {
                        Log.d(TAG, "onStart: ");
                    }

                    @Override
                    public void onComplete(String path) {
                        Log.d(TAG, "onComplete: "+path);
                    }

                    @Override
                    public void onProgress(int progress) {
                        Log.d(TAG, "onProgress: " + progress);
                    }

                    @Override
                    public void onError(String msg) {
                        Log.d(TAG, "onError: " + msg);
                    }
                });
                crop.start(path,
                        Environment.getExternalStorageDirectory().getAbsolutePath() + "/VideoCut.mp4",
                        0 * 1000000, 53 * 1000000);
            }
        }
    }

    public String getRealFilePath(final Uri uri) {
        if (null == uri) return null;
        final String scheme = uri.getScheme();
        String data = null;
        if (scheme == null) {
            Log.e(TAG, "scheme is null");
            data = uri.getPath();
        } else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            data = uri.getPath();
            Log.e(TAG, "SCHEME_FILE");
        } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            data = GetPathFromUri4kitkat.getPath(getApplicationContext(), uri);
        }
        return data;
    }
}
