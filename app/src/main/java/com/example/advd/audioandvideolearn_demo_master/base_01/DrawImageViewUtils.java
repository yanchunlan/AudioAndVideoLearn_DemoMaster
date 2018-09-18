package com.example.advd.audioandvideolearn_demo_master.base_01;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;

import com.example.advd.audioandvideolearn_demo_master.R;


/**
 * author: ycl
 * date: 2018-09-16 11:10
 * desc: 绘制图片的三种方式
 */
public class DrawImageViewUtils {

    public static void drawIv(Resources resources, ImageView imageView) {
        Bitmap bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground);
        imageView.setImageBitmap(bitmap);
    }

    public static void drawSurfaceViewIv(final Resources resources, SurfaceView surfaceView) {
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (holder != null) {
                    Paint paint = new Paint();
                    paint.setAntiAlias(true);
                    paint.setColor(Color.RED);
                    paint.setTextSize(15);
                    paint.setStyle(Paint.Style.STROKE);

                    Bitmap bitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher);
                    Canvas canvas = holder.lockCanvas();
                    canvas.drawBitmap(bitmap, 0, 0, paint);
                    holder.unlockCanvasAndPost(canvas);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
    }


}


