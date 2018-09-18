package com.example.advd.audioandvideolearn_demo_master.base_01;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceView;
import android.widget.ImageView;

import com.example.advd.audioandvideolearn_demo_master.R;

public class Base01Activity extends AppCompatActivity {

    private ImageView mIv;
    private SurfaceView mSurfaceView;
    private CustomIv mCustomIv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base01);
        initView();
    }


    private void initView() {
        mIv = (ImageView) findViewById(R.id.iv);
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mCustomIv = (CustomIv) findViewById(R.id.customIv);

        DrawImageViewUtils.drawIv(getResources(), mIv);
        DrawImageViewUtils.drawSurfaceViewIv(getResources(), mSurfaceView);
    }
}
