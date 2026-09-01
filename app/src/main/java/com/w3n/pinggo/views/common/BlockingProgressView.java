package com.w3n.pinggo.views.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.progress.Progress;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

/** Reusable full-screen modal scrim with a native circular progress indicator. */
public final class BlockingProgressView extends View {
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
    private static final int SCRIM_COLOR = 0x99000000;
    private static final int ACCENT_COLOR = 0xFF019CC4;

    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer progressLayer = layers.addLayer("blocking_progress");
    private final Bitmap scrimBitmap = colorBitmap(SCRIM_COLOR);

    public BlockingProgressView(@NonNull Context context) {
        this(context, null);
    }

    public BlockingProgressView(@NonNull Context context,
                                @Nullable AttributeSet attributes) {
        super(context, attributes);
        setClickable(true);
        setFocusable(true);
        setVisibility(GONE);
        progressLayer.setTouchPolicy(ZLayer.TouchPolicy.MODAL);
    }

    public void setLoading(boolean loading) {
        setVisibility(loading ? VISIBLE : GONE);
        if (loading) {
            bringToFront();
            invalidate();
        }
    }

    public boolean isLoading() {
        return getVisibility() == VISIBLE;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width <= 0 || height <= 0) return;

        progressLayer.clear();
        progressLayer.add(new Image.Builder(getContext(), "progress_scrim", scrimBitmap,
                new RectF(0f, 0f, width, height))
                .setScaleType(Image.ScaleType.FIT_XY));

        float indicatorSize = px(198f);
        float left = (width - indicatorSize) / 2f;
        float top = (height - indicatorSize) / 2f;
        progressLayer.add(new Progress.Builder(getContext(), "circular_progress",
                new RectF(left, top, left + indicatorSize, top + indicatorSize))
                .setStyle(Progress.Style.CIRCULAR)
                .setMode(Progress.Mode.INDETERMINATE)
                .setTrackColor(0x33FFFFFF)
                .setProgressColor(ACCENT_COLOR)
                .setThicknessPx(px(16.5f))
                .setIndeterminateSweepAngle(110f)
                .setIndeterminateDuration(900L)
                .setStrokeCap(Progress.StrokeCap.ROUND));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        layers.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        layers.onTouchEvent(event);
        return true;
    }

    private float px(float value) {
    return figmaConfig.toRuntime(value, Math.max(1, getResources().getDisplayMetrics().widthPixels));
  }

    private static Bitmap colorBitmap(int color) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }
}
