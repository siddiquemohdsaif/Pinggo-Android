package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;

/** Continuous vertical color picker used by the selected-media drawing editor. */
final class EditorColorPaletteView extends View {
  interface Listener { void onColorSelected(int color); }

  private static final int[] COLORS = {
      Color.BLACK, Color.WHITE, 0xFFFF3030, 0xFFFF9F1C, 0xFFFFE23B,
      0xFF22C56E, 0xFF35D9C5, 0xFF38A9FF, 0xFF5257E5, 0xFFB64CFF, 0xFFFF3F8E
  };
  private final Paint palettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF paletteBounds = new RectF();
  private final Listener listener;
  private float selectedFraction = .50f;

  EditorColorPaletteView(Context context, Listener listener) {
    super(context);
    this.listener = listener;
    setClickable(true);
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    float barWidth = dp(17.1f);
    paletteBounds.set((width - barWidth) / 2f, dp(12f),
        (width + barWidth) / 2f, height - dp(12f));
    palettePaint.setShader(new LinearGradient(0f, paletteBounds.top, 0f, paletteBounds.bottom,
        COLORS, null, Shader.TileMode.CLAMP));
  }

  @Override protected void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);
    float radius = paletteBounds.width() / 2f;
    canvas.drawRoundRect(paletteBounds, radius, radius, palettePaint);
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    int action = event.getActionMasked();
    if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE
        && action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) return false;
    if (action == MotionEvent.ACTION_DOWN && getParent() != null) {
      getParent().requestDisallowInterceptTouchEvent(true);
    }
    if (action != MotionEvent.ACTION_CANCEL) select(event.getY());
    if (action == MotionEvent.ACTION_UP) performClick();
    if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
        && getParent() != null) {
      getParent().requestDisallowInterceptTouchEvent(false);
    }
    return true;
  }

  @Override public boolean performClick() {
    super.performClick();
    return true;
  }

  private void select(float y) {
    selectedFraction = clamp((y - paletteBounds.top) / Math.max(1f, paletteBounds.height()));
    if (listener != null) listener.onColorSelected(colorAt(selectedFraction));
    invalidate();
  }

  private static int colorAt(float fraction) {
    float position = clamp(fraction) * (COLORS.length - 1);
    int start = Math.min(COLORS.length - 1, (int) position);
    int end = Math.min(COLORS.length - 1, start + 1);
    float amount = position - start;
    return Color.argb(blend(Color.alpha(COLORS[start]), Color.alpha(COLORS[end]), amount),
        blend(Color.red(COLORS[start]), Color.red(COLORS[end]), amount),
        blend(Color.green(COLORS[start]), Color.green(COLORS[end]), amount),
        blend(Color.blue(COLORS[start]), Color.blue(COLORS[end]), amount));
  }

  private static int blend(int start, int end, float amount) {
    return Math.round(start + (end - start) * amount);
  }

  private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
  private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
