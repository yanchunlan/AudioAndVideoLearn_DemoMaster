package com.example.advd.audioandvideolearn_demo_master

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.example.advd.audioandvideolearn_demo_master.base_01.Base01Activity
import com.example.advd.audioandvideolearn_demo_master.base_02.Base02Activity
import com.example.advd.audioandvideolearn_demo_master.base_03.Base03Activity
import com.example.advd.audioandvideolearn_demo_master.base_04.MediaMuxerActivity
import com.example.advd.audioandvideolearn_demo_master.opensles1.OpenSLESActivity
import com.example.advd.audioandvideolearn_demo_master.opensles2.OpenSLES2Activity
import com.example.advd.audioandvideolearn_demo_master.videoCut.VideoCutActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.startActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tv_title.text = stringFromJNI()



        button0.setOnClickListener {
            startActivity<Base01Activity>()
        }

        button1.setOnClickListener {
            startActivity<Base02Activity>()
        }

        button2.setOnClickListener {
            startActivity<Base03Activity>()
        }
        button3.setOnClickListener {
            startActivity<MediaMuxerActivity>()
        }
        button4.setOnClickListener {
            startActivity<OpenSLESActivity>()
        }
        button5.setOnClickListener {
            startActivity<OpenSLES2Activity>()
        }
        button6.setOnClickListener {
            startActivity<VideoCutActivity>()
        }

    }

    external fun stringFromJNI(): String

    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }
}
