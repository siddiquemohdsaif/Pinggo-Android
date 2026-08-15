package com.w3n.pinggo.views.animator.core;

import android.graphics.Bitmap;

public class AnimationProperties {
    public final Bitmap bitmap;
    public final float startPosX;
    public final float endPosX;
    public final float startPosY;
    public final float endPosY;
    public final long duration;
    public final float startAlpha;
    public final float endAlpha;
    public final float startWidth;
    public final float startHeight;
    public final float endWidth;
    public final float endHeight;

    private AnimationProperties(Builder builder) {
        bitmap = builder.bitmap;
        startPosX = builder.startPosX;
        endPosX = builder.endPosX;
        startPosY = builder.startPosY;
        endPosY = builder.endPosY;
        duration = builder.duration;
        startAlpha = builder.startAlpha;
        endAlpha = builder.endAlpha;
        startWidth = builder.startWidth;
        startHeight = builder.startHeight;
        endWidth = builder.endWidth;
        endHeight = builder.endHeight;
    }

    public static class Builder {
        private final Bitmap bitmap;
        private final float startPosX;
        private final float endPosX;
        private final float startPosY;
        private final float endPosY;
        private final long duration;

        private float startAlpha = 1f;
        private float endAlpha = 1f;
        private float startWidth;
        private float startHeight;
        private float endWidth;
        private float endHeight;

        public Builder(Bitmap bitmap, float startPosX, float endPosX, float startPosY,
                       float endPosY, long duration) {
            this.bitmap = bitmap;
            this.startPosX = startPosX;
            this.endPosX = endPosX;
            this.startPosY = startPosY;
            this.endPosY = endPosY;
            this.duration = duration;
            this.startWidth = bitmap.getWidth();
            this.startHeight = bitmap.getHeight();
            this.endWidth = bitmap.getWidth();
            this.endHeight = bitmap.getHeight();
        }

        public Builder addAlpha(float startAlpha, float endAlpha) {
            this.startAlpha = startAlpha;
            this.endAlpha = endAlpha;
            return this;
        }

        public Builder addStartSize(float startWidth, float startHeight) {
            this.startWidth = startWidth;
            this.startHeight = startHeight;
            return this;
        }

        public Builder addEndSize(float endWidth, float endHeight) {
            this.endWidth = endWidth;
            this.endHeight = endHeight;
            return this;
        }

        public AnimationProperties build() {
            return new AnimationProperties(this);
        }
    }
}
