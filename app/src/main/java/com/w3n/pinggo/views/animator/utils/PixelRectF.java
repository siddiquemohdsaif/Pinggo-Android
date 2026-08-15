package com.w3n.pinggo.views.animator.utils;

import android.graphics.RectF;

public class PixelRectF extends RectF {
    public float leftPx;
    public float topPx;
    public float rightPx;
    public float bottomPx;

    public PixelRectF(int viewWidth, int leftPx, int topPx, int rightPx, int bottomPx) {
        this(viewWidth, viewWidth, leftPx, topPx, rightPx, bottomPx, true);
    }

    public PixelRectF(int viewWidth, int viewHeight, int leftPx, int topPx, int rightPx,
                      int bottomPx, boolean alignFromTop) {
        this(scale(viewWidth, leftPx), scale(viewWidth, topPx),
                scale(viewWidth, rightPx), scale(viewWidth, bottomPx), viewHeight,
                alignFromTop);
    }

    public PixelRectF(float leftPx, float topPx, float rightPx, float bottomPx, int viewHeight,
                      boolean alignFromTop) {
        this(leftPx, alignFromTop ? topPx : viewHeight - bottomPx, rightPx,
                alignFromTop ? bottomPx : viewHeight - topPx);
    }

    public PixelRectF(float leftPx, float topPx, float rightPx, float bottomPx) {
        super(leftPx, topPx, rightPx, bottomPx);
        syncPixelFields();
    }

    public PixelRectF(RectF rectF) {
        this(rectF.left, rectF.top, rectF.right, rectF.bottom);
    }

    public static PixelRectF fromRect(RectF rectF) {
        return new PixelRectF(rectF);
    }

    private static float scale(int viewWidth, int valuePx) {
        return valuePx / 1080f * viewWidth;
    }

    private void syncPixelFields() {
        leftPx = left;
        topPx = top;
        rightPx = right;
        bottomPx = bottom;
    }
}
