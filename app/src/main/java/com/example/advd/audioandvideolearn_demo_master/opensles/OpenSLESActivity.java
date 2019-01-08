package com.example.advd.audioandvideolearn_demo_master.opensles;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.example.advd.audioandvideolearn_demo_master.R;

public class OpenSLESActivity extends AppCompatActivity {
    static {
        System.loadLibrary("OpenSLES_Record");
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_open_sles);
    }


    public native void startRecord(String path);
    public native void stopRecord( );
}
