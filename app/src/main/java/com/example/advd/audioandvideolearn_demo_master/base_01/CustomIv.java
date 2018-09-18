package com.example.advd.audioandvideolearn_demo_master.base_01;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import com.example.advd.audioandvideolearn_demo_master.R;

public class CustomIv extends View {
        private Paint mPaint;
        private Bitmap mBitmap;

        public CustomIv(Context context) {
            this(context, null);
        }

        public CustomIv(Context context, @Nullable AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public CustomIv(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            init();
        }

        private void init() {
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setColor(Color.RED);
            mPaint.setTextSize(15);
            mPaint.setStyle(Paint.Style.STROKE);

            mBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (canvas != null) {
                canvas.drawBitmap(mBitmap, 0, 0, mPaint);
            }
        }
    }