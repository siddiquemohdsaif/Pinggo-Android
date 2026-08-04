package com.w3n.wavestream.views.animator.dynamic;

import android.graphics.Canvas;
import android.graphics.RectF;

public interface CustomDynamicView {
    void onDraw(Canvas canvas, float progress, RectF rectF);

    long getDuration();
}
