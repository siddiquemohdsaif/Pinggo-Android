package com.w3n.wavestream.views.animator.button;

import android.graphics.Canvas;

public class PressAnimation {
    private final boolean downAnimation;
    private final long startTime;
    private final long duration;
    private final float pivotX;
    private final float pivotY;
    private final float shrinkScale;

    public PressAnimation(boolean downAnimation, long startTime, long duration,
                          float pivotX, float pivotY, float shrinkScale) {
        this.downAnimation = downAnimation;
        this.startTime = startTime;
        this.duration = Math.max(1L, duration);
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.shrinkScale = shrinkScale;
    }

    public void applyAnimationPressed(Canvas canvas) {
        float progress = Math.min(1f, (System.currentTimeMillis() - startTime) / (float) duration);
        float eased = 1f - ((1f - progress) * (1f - progress));
        float scale = downAnimation
                ? 1f - ((1f - shrinkScale) * eased)
                : shrinkScale + ((1f - shrinkScale) * eased);
        canvas.save();
        canvas.scale(scale, scale, pivotX, pivotY);
    }

    public void restoreAnimationPressed(Canvas canvas) {
        canvas.restore();
    }

    public boolean isAnimationFinished() {
        return System.currentTimeMillis() - startTime >= duration;
    }
}
