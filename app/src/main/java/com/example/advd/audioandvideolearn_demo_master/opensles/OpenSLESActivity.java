package com.example.advd.audioandvideolearn_demo_master.opensles;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.advd.audioandvideolearn_demo_master.R;

public class OpenSLESActivity extends AppCompatActivity implements View.OnClickListener {
    static {
        System.loadLibrary("OpenSLES_Record");
    }

    private Button mBtnStartRecord;
    private Button mBtnStopRecord;
    private TextView mTvRecordPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_open_sles);
        initView();
    }


    public native void startRecord(String path);

    public native void stopRecord();

    private void initView() {
        mBtnStartRecord = (Button) findViewById(R.id.btn_startRecord);
        mBtnStopRecord = (Button) findViewById(R.id.btn_stopRecord);
        mTvRecordPath = (TextView) findViewById(R.id.tv_recordPath);

        mBtnStartRecord.setOnClickListener(this);
        mBtnStopRecord.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_startRecord:
                startRecord(Environment.getExternalStorageDirectory().getAbsolutePath() + "/opensles_record.pcm");
                break;
            case R.id.btn_stopRecord:
                stopRecord();
                mTvRecordPath.setText(Environment.getExternalStorageDirectory().getAbsolutePath() + "/opensles_record.pcm");
                // 使用audacity播放
                break;
        }
    }
}
