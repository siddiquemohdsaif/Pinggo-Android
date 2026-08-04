package com.w3n.wavestream.views.animator.dynamic;

import android.graphics.Canvas;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Iterator;

public class DynamicViewAnimator {
    private final CustomDynamicView customDynamicView;
    private final RectF rectF;
    private final int repeatCount;
    private long startTime;
    private boolean stopped;

    public DynamicViewAnimator(CustomDynamicView customDynamicView, int repeatCount, RectF rectF) {
        this.customDynamicView = customDynamicView;
        this.repeatCount = repeatCount;
        this.rectF = new RectF(rectF);
        this.startTime = System.currentTimeMillis();
    }

    public void restart() {
        stopped = false;
        startTime = System.currentTimeMillis();
    }

    public void stop() {
        stopped = true;
    }

    public boolean isAnimating() {
        if (stopped) {
            return false;
        }
        if (repeatCount < 0) {
            return true;
        }
        long duration = Math.max(1L, customDynamicView.getDuration());
        return System.currentTimeMillis() - startTime < duration * repeatCount;
    }

    public void draw(Canvas canvas) {
        if (!isAnimating()) {
            return;
        }
        long duration = Math.max(1L, customDynamicView.getDuration());
        float progress = ((System.currentTimeMillis() - startTime) % duration) / (float) duration;
        customDynamicView.onDraw(canvas, progress, rectF);
    }

    public static void Draw(Canvas canvas, ArrayList<DynamicViewAnimator> animators) {
        for (Iterator<DynamicViewAnimator> iterator = animators.iterator(); iterator.hasNext(); ) {
            DynamicViewAnimator animator = iterator.next();
            animator.draw(canvas);
            if (!animator.isAnimating()) {
                iterator.remove();
            }
        }
    }
}
