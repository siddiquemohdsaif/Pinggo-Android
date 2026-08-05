package com.w3n.wavestream.views.animator.core;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.w3n.wavestream.views.animator.utils.PixelRectF;

import java.util.ArrayList;
import java.util.Iterator;

public class BitmapRotateViewAnimator {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Interpolator interpolator = new DecelerateInterpolator();

    private String animName;
    private Bitmap bitmap;
    private long startTime;
    private long duration;
    private boolean repeat;
    private boolean stopped;

    private float startAlpha;
    private float endAlpha;
    private float startAngle;
    private float endAngle;
    private PixelRectF rectF;

    public void startAnimation(String animName, Bitmap bitmap, float startAlpha, float endAlpha,
                               float startAngle, float endAngle, RectF rectF, long duration,
                               boolean repeat) {
        startAnimation(animName, bitmap, startAlpha, endAlpha, startAngle, endAngle,
                PixelRectF.fromRect(rectF), duration, repeat);
    }

    public void startAnimation(String animName, Bitmap bitmap, float startAlpha, float endAlpha,
                               float startAngle, float endAngle, PixelRectF rectF, long duration,
                               boolean repeat) {
        this.animName = animName;
        this.bitmap = bitmap;
        this.startAlpha = startAlpha;
        this.endAlpha = endAlpha;
        this.startAngle = startAngle;
        this.endAngle = endAngle;
        this.rectF = new PixelRectF(rectF);
        this.duration = Math.max(1L, duration);
        this.repeat = repeat;
        this.startTime = System.currentTimeMillis();
        this.stopped = false;
    }

    public String getAnimName() {
        return animName;
    }

    public void stopAnimation() {
        stopped = true;
    }

    public boolean isFinished() {
        return stopped || (!repeat && System.currentTimeMillis() - startTime >= duration);
    }

    public void onDraw(Canvas canvas) {
        if (bitmap == null || rectF == null || stopped) {
            return;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (repeat) {
            elapsed %= duration;
        }
        float progress = Math.min(1f, elapsed / (float) duration);
        float eased = interpolator.getInterpolation(progress);
        float alpha = startAlpha + ((endAlpha - startAlpha) * eased);
        float angle = startAngle + ((endAngle - startAngle) * eased);

        paint.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255f))));
        canvas.save();
        canvas.rotate(angle, rectF.centerX(), rectF.centerY());
        canvas.drawBitmap(bitmap, null, rectF, paint);
        canvas.restore();
    }

    public static void addAnimation(String animName, ArrayList<BitmapRotateViewAnimator> animators,
                                    Bitmap bitmap, float startAlpha, float endAlpha,
                                    float startAngle, float endAngle, RectF rectF,
                                    long duration, boolean repeat) {
        addAnimation(animName, animators, bitmap, startAlpha, endAlpha, startAngle, endAngle,
                PixelRectF.fromRect(rectF), duration, repeat);
    }

    public static void addAnimation(String animName, ArrayList<BitmapRotateViewAnimator> animators,
                                    Bitmap bitmap, float startAlpha, float endAlpha,
                                    float startAngle, float endAngle, PixelRectF rectF,
                                    long duration, boolean repeat) {
        removeAnimation(animName, animators);
        BitmapRotateViewAnimator animator = new BitmapRotateViewAnimator();
        animator.startAnimation(animName, bitmap, startAlpha, endAlpha, startAngle, endAngle,
                rectF, duration, repeat);
        animators.add(animator);
    }

    public static void removeAnimation(String animName, ArrayList<BitmapRotateViewAnimator> animators) {
        for (Iterator<BitmapRotateViewAnimator> iterator = animators.iterator(); iterator.hasNext(); ) {
            if (animName.equals(iterator.next().getAnimName())) {
                iterator.remove();
            }
        }
    }

    public static void Draw(Canvas canvas, ArrayList<BitmapRotateViewAnimator> animators) {
        for (Iterator<BitmapRotateViewAnimator> iterator = animators.iterator(); iterator.hasNext(); ) {
            BitmapRotateViewAnimator animator = iterator.next();
            animator.onDraw(canvas);
            if (animator.isFinished()) {
                iterator.remove();
            }
        }
    }
}
