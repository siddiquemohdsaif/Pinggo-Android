package com.w3n.pinggo.views.animator.dynamic;

import android.graphics.Canvas;

import com.w3n.pinggo.views.animator.utils.PixelRectF;

public interface CustomDynamicView {
    void onDraw(Canvas canvas, float progress, PixelRectF rectF);

    long getDuration();
}
