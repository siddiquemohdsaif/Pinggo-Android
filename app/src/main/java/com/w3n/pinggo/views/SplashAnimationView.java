package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.ogfa.nativeviews.animation.AnimatorComponent;
import com.ogfa.nativeviews.animation.LottieAnimator;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

/** Hosts the splash Lottie animation through the native-views AAR. */
public final class SplashAnimationView extends View {
    private static final String ANIMATION_NAME = "pinggo_splash_animation";
    private static final float ANIMATION_SIZE_PX = 350f;

    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer animationLayer = layers.addLayer("splash_animation_layer");
    private boolean animationBuilt;

    public SplashAnimationView(Context context) {
        this(context, null);
    }

    public SplashAnimationView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LottieAnimator.preload(context, ANIMATION_NAME);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (animationBuilt || width <= 0 || height <= 0) {
            return;
        }

        animationBuilt = true;
        float size = Math.min(ANIMATION_SIZE_PX, Math.min(width, height));
        float left = (width - size) / 2f;
        float top = (height - size) / 2f;

        animationLayer.add(new LottieAnimator.Builder(
                getContext(),
                "splash_animation",
                ANIMATION_NAME,
                new RectF(left, top, left + size, top + size)
        ).setRepeatCount(AnimatorComponent.INFINITE));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        layers.draw(canvas);
    }

    public void release() {
        layers.release();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
