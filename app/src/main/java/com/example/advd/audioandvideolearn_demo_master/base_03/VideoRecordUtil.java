package com.example.advd.audioandvideolearn_demo_master.base_03;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import com.example.advd.audioandvideolearn_demo_master.base_04.H264Encoder;

import java.io.IOException;

/**
 * author: ycl
 * date: 2018-09-18 1:00
 * desc:
 */
public class VideoRecordUtil {
    private static final String TAG = "VideoRecordUtil";
    private Camera mCamera;
    private boolean isPushing;
    private int width = 1280, height = 720, frameRate = 30;
    private H264Encoder mH264Encoder;


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void createCamera() {
        mCamera = Camera.open();
        mCamera.setDisplayOrientation(90);

        /**
         * Camera Preview Callback的YUV常用格式有两种：
         * 一个是NV21，一个是YV12。Android一般默认使用YCbCr_420_SP的格式（NV21）
         */
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewFormat(ImageFormat.NV21); // 默认值也是它
        parameters.setPreviewSize(width, height); // 设置大小
        mCamera.setParameters(parameters);


        // 无宽高限制
        mCamera.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                Log.d(TAG, "onPreviewFrame: " + data);
                if (isPushing&&mH264Encoder!=null) {
                    mH264Encoder.putData(data);
                }
            }
        });

        // 有宽高限制
        /*byte[] data = new byte[480 * 320 * 4];
        mCamera.addCallbackBuffer(data);
        mCamera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                Log.d(TAG, "onPreviewFrame: "+data);
            }
        });*/


        mH264Encoder = new H264Encoder(width, height, frameRate);
    }

    public void bindHolder(SurfaceView mSurfaceView, TextureView mTextureView) {
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    if (mCamera != null) {
                        mCamera.setPreviewDisplay(holder);
                        startPush();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (!isPushing) {
                    return;
                }
                holder.removeCallback(this);
                stopPush();
            }
        });

        /*mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                try {
                    if (mCamera != null) {
                        mCamera.setPreviewTexture(surface);
                        startPush();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
              if (!isPushing) {
                    return;
                }
                holder.removeCallback(this);
                stopPush();
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });*/
    }

    public void startPush() {
        isPushing = true;
        if (mCamera != null) {
            mCamera.startPreview();
        }

        if (mH264Encoder != null) {
            mH264Encoder.startEncoder();
        }
    }

    public void stopPush() {
        isPushing = false;
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);// 此监听在camera关闭之前需要调用

            mCamera.stopPreview();
            mCamera.lock();
            mCamera.release();
            mCamera = null;
        }
        if (mH264Encoder != null) {
            mH264Encoder.stopEncoder();
        }
    }
}
