/*
 * Copyright (C) 2013 The ChameleonOS Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.recent;

import android.R;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.ImageView;

public class CircleMemoryMeter extends ImageView {
    private Handler mHandler;
    private Context mContext;

    // state variables
    private boolean mAttached;      // whether or not attached to a window
    private long    mLevel;         // current meter level

    private int     mCircleSize;    // draw size of circle.
    private RectF   mRectLeft;      // contains the precalculated rect used in drawArc(), derived from mCircleSize

    // quiet a lot of paint variables. helps to move cpu-usage from actual drawing to initialization
    private Paint   mPaintText;
    private Paint   mPaintGray;
    private Paint   mPaintGreen;
    private Paint   mPaintOrange;
    private Paint   mPaintRed;

    private Path    mTextArc;

    private long    mLowLevel;
    private long    mMediumLevel;
    private long    mHighLevel;
    private float   mArcOffset;

    private String  mAvailableMemory;
    private String  mTotalMemory;


    // runnable to invalidate view via mHandler.postDelayed() call
    private final Runnable mInvalidate = new Runnable() {
        public void run() {
            if(mAttached) {
                invalidate();
            }
        }
    };

    public CircleMemoryMeter(Context context) {
        this(context, null);
    }

    public CircleMemoryMeter(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircleMemoryMeter(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mContext = context;
        mHandler = new Handler();

        // initialize and setup all paint variables
        // stroke width is later set in initSizeBasedStuff()
        Resources res = getResources();

        mPaintText = new Paint();
        mPaintText.setAntiAlias(true);
        mPaintText.setDither(true);
        mPaintText.setStyle(Paint.Style.STROKE);

        mPaintGray = new Paint(mPaintText);
        mPaintGreen = new Paint(mPaintText);
        mPaintOrange = new Paint(mPaintText);
        mPaintRed = new Paint(mPaintText);

        mPaintGray.setStrokeCap(Paint.Cap.BUTT);
        mPaintGreen.setStrokeCap(Paint.Cap.BUTT);
        mPaintOrange.setStrokeCap(Paint.Cap.BUTT);
        mPaintRed.setStrokeCap(Paint.Cap.BUTT);

        mPaintGreen.setColor(res.getColor(R.color.holo_blue_light));
        mPaintOrange.setColor(res.getColor(R.color.holo_orange_light));
        mPaintGray.setColor(res.getColor(R.color.darker_gray));
        mPaintRed.setColor(res.getColor(R.color.holo_red_light));

        mPaintText.setColor(res.getColor(R.color.black));
        mPaintText.setTextAlign(Align.CENTER);
        mPaintText.setStyle(Paint.Style.FILL_AND_STROKE);
        mPaintText.setFakeBoldText(true);
    }

    public void setLevels(long lowLevel, long mediumLevel, long highLevel) {
        mLowLevel = lowLevel;
        mMediumLevel = mediumLevel;
        mHighLevel = highLevel;
    }

    protected long getLevel() {
        return mLevel;
    }

    public void setCurrentLevel(long level) {
        mLevel = level;
        mAvailableMemory = "" + (level / 1048576L) + "M";
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
            mHandler.postDelayed(mInvalidate, 250);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            mAttached = false;
            mRectLeft = null; // makes sure, size based variables get
                                // recalculated on next attach
            mCircleSize = 0;    // makes sure, mCircleSize is reread from icons on
                                // next attach
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        MemoryInfo mi = new MemoryInfo();
        ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        am.getMemoryInfo(mi);
        long free = mi.availMem;
        // threshold is where the system would consider it low memory and start killing off
        // processes so let's deduct that from the total so the indicator will be in the red
        // well before that so the user knows memory is gettting low
        long total = mi.totalMem;
        mTotalMemory = "" + (total / 1048576L) + "M";
        setLevels((long)(total * 0.2f), (long)(total * 0.5f), total);
        setCurrentLevel(free);
    }

    public void update() {
        postDelayed(new Runnable() {
            public void run() {
                updateMemoryInfo();
                invalidate();
            }
        }, 500);
    }

    private void updateMemoryInfo() {
        MemoryInfo mi = new MemoryInfo();
        ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        am.getMemoryInfo(mi);
        long free = mi.availMem;
        setCurrentLevel(free);
        mHandler.removeCallbacks(mInvalidate);
        mHandler.postDelayed(mInvalidate, 100);
    }

    protected void drawCircle(Canvas canvas, long level, RectF drawRect) {
        Paint usePaint = mPaintGreen;
        long internalLevel = level;

        if (internalLevel <= mLowLevel)
            usePaint = mPaintRed;
        else if (internalLevel <= mMediumLevel)
            usePaint = mPaintOrange;
        else
            usePaint = mPaintGreen;

        int normalizedLevel = (int)((float)level / (float)mHighLevel * 100f);

        // draw thin gray ring first
        canvas.drawArc(drawRect, 270, 360, false, mPaintGray);
        // draw colored arc representing charge level
        canvas.drawArc(drawRect, 180, 3.6f * normalizedLevel, false, usePaint);
        canvas.drawTextOnPath(mAvailableMemory + "/" + mTotalMemory, mTextArc, 0, mArcOffset, mPaintText);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mRectLeft == null) {
            init();
        }

        updateMemoryInfo();
        drawCircle(canvas,
                   getLevel(),
                   mRectLeft);
    }

    /***
     * initializes all size dependent variables
     * sets stroke width and text size of all involved paints
     */
    private void init() {
        if (mCircleSize == 0) {
            initSizeMeasureIconHeight();
        }

        float strokeWidth = mCircleSize / 6.5f;
        float levelStrokeWidth = strokeWidth / 1.5f;
        mPaintRed.setStrokeWidth(levelStrokeWidth);
        mPaintGreen.setStrokeWidth(levelStrokeWidth);
        mPaintOrange.setStrokeWidth(levelStrokeWidth);
        mPaintGray.setStrokeWidth(strokeWidth);

        // calculate rectangle for drawArc calls
        int pLeft = getPaddingLeft();
        mRectLeft = new RectF(pLeft + strokeWidth / 2.0f, 0 + strokeWidth / 2.0f, mCircleSize
                - strokeWidth / 2.0f + pLeft, mCircleSize - strokeWidth / 2.0f);

        mTextArc = new Path();
        mTextArc.addArc(mRectLeft, 180, 180);
        mPaintText.setTextSize(strokeWidth);
        mArcOffset = (strokeWidth - levelStrokeWidth);

        // force new measurement for wrap-content xml tag
        onMeasure(0, 0);
    }

    private void initSizeMeasureIconHeight() {
        mCircleSize = Math.min(getWidth(), getHeight());
    }
}
