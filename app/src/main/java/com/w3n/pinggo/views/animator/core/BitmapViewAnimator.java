package com.w3n.pinggo.views.animator.core;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.w3n.pinggo.views.animator.utils.PixelRectF;

import java.util.ArrayList;
import java.util.Iterator;

public class BitmapViewAnimator {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Interpolator interpolator = new DecelerateInterpolator();

    private String animName;
    private Bitmap bitmap;
    private long startTime;
    private long duration;
    private boolean repeat;
    private boolean autoDelete;
    private boolean stopped;
    private PixelRectF visibleWindow;

    private float startAlpha;
    private float endAlpha;
    private float startWidth;
    private float endWidth;
    private float startHeight;
    private float endHeight;
    private float startLeft;
    private float endLeft;
    private float startTop;
    private float endTop;

    public void startAnimation(String animName, Bitmap bitmap, float startAlpha, float endAlpha,
                               float startWidth, float endWidth, float startHeight, float endHeight,
                               float startLeft, float endLeft, float startTop, float endTop,
                               long duration, boolean repeat, RectF visibleWindow, boolean autoDelete) {
        startAnimation(animName, bitmap, startAlpha, endAlpha, startWidth, endWidth,
                startHeight, endHeight, startLeft, endLeft, startTop, endTop, duration, repeat,
                visibleWindow == null ? null : PixelRectF.fromRect(visibleWindow), autoDelete);
    }

    public void startAnimation(String animName, Bitmap bitmap, float startAlpha, float endAlpha,
                               float startWidth, float endWidth, float startHeight, float endHeight,
                               float startLeft, float endLeft, float startTop, float endTop,
                               long duration, boolean repeat, PixelRectF visibleWindow, boolean autoDelete) {
        this.animName = animName;
        this.bitmap = bitmap;
        this.startAlpha = startAlpha;
        this.endAlpha = endAlpha;
        this.startWidth = startWidth;
        this.endWidth = endWidth;
        this.startHeight = startHeight;
        this.endHeight = endHeight;
        this.startLeft = startLeft;
        this.endLeft = endLeft;
        this.startTop = startTop;
        this.endTop = endTop;
        this.duration = Math.max(1L, duration);
        this.repeat = repeat;
        this.visibleWindow = visibleWindow == null ? null : new PixelRectF(visibleWindow);
        this.autoDelete = autoDelete;
        this.startTime = System.currentTimeMillis();
        this.stopped = false;
    }

    public String getAnimName() {
        return animName;
    }

    public boolean shouldDelete() {
        return autoDelete && isFinished();
    }

    public boolean isFinished() {
        return stopped || (!repeat && System.currentTimeMillis() - startTime >= duration);
    }

    public void stopAnimation() {
        stopped = true;
    }

    public void onDraw(Canvas canvas) {
        if (bitmap == null || stopped) {
            return;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (repeat) {
            elapsed %= duration;
        }

        float progress = Math.min(1f, elapsed / (float) duration);
        float eased = interpolator.getInterpolation(progress);
        float alpha = lerp(startAlpha, endAlpha, eased);
        float width = lerp(startWidth, endWidth, eased);
        float height = lerp(startHeight, endHeight, eased);
        float left = lerp(startLeft, endLeft, eased);
        float top = lerp(startTop, endTop, eased);
        RectF rect = new RectF(left, top, left + width, top + height);

        if (visibleWindow != null && !RectF.intersects(visibleWindow, rect)) {
            return;
        }

        paint.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255f))));
        canvas.drawBitmap(bitmap, null, rect, paint);
    }

    private static float lerp(float start, float end, float progress) {
        return start + ((end - start) * progress);
    }

    public static void addAnimation(String animName, ArrayList<BitmapViewAnimator> animators,
                                    Bitmap bitmap, float startAlpha, float endAlpha,
                                    float startWidth, float endWidth, float startHeight, float endHeight,
                                    float startLeft, float endLeft, float startTop, float endTop,
                                    long duration, boolean repeat) {
        addAnimation(animName, animators, bitmap, startAlpha, endAlpha, startWidth, endWidth,
                startHeight, endHeight, startLeft, endLeft, startTop, endTop, duration, repeat,
                null, false);
    }

    public static void addAnimation(String animName, ArrayList<BitmapViewAnimator> animators,
                                    Bitmap bitmap, float startAlpha, float endAlpha,
                                    float startWidth, float endWidth, float startHeight, float endHeight,
                                    float startLeft, float endLeft, float startTop, float endTop,
                                    long duration, boolean repeat, RectF visibleWindow, boolean autoDelete) {
        addAnimation(animName, animators, bitmap, startAlpha, endAlpha, startWidth, endWidth,
                startHeight, endHeight, startLeft, endLeft, startTop, endTop, duration, repeat,
                visibleWindow == null ? null : PixelRectF.fromRect(visibleWindow), autoDelete);
    }

    public static void addAnimation(String animName, ArrayList<BitmapViewAnimator> animators,
                                    Bitmap bitmap, float startAlpha, float endAlpha,
                                    float startWidth, float endWidth, float startHeight, float endHeight,
                                    float startLeft, float endLeft, float startTop, float endTop,
                                    long duration, boolean repeat, PixelRectF visibleWindow, boolean autoDelete) {
        removeAnimation(animName, animators);
        BitmapViewAnimator animator = new BitmapViewAnimator();
        animator.startAnimation(animName, bitmap, startAlpha, endAlpha, startWidth, endWidth,
                startHeight, endHeight, startLeft, endLeft, startTop, endTop, duration, repeat,
                visibleWindow, autoDelete);
        animators.add(animator);
    }

    public static void removeAnimation(String animName, ArrayList<BitmapViewAnimator> animators) {
        for (Iterator<BitmapViewAnimator> iterator = animators.iterator(); iterator.hasNext(); ) {
            BitmapViewAnimator animator = iterator.next();
            if (animName.equals(animator.getAnimName())) {
                iterator.remove();
            }
        }
    }

    public static void Draw(Canvas canvas, ArrayList<BitmapViewAnimator> animators) {
        for (Iterator<BitmapViewAnimator> iterator = animators.iterator(); iterator.hasNext(); ) {
            BitmapViewAnimator animator = iterator.next();
            animator.onDraw(canvas);
            if (animator.shouldDelete()) {
                iterator.remove();
            }
        }
    }
}
