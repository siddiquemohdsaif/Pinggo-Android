package com.w3n.pinggo.views.chat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import com.ogfa.nativeviews.component.Component;
import com.ogfa.nativeviews.component.ComponentHost;

/** Single-line call record using the same inner-card treatment as a file message. */
final class CallPreviewComponent implements Component {
  private static final int PANEL_COLOR = 0xFFF8F9FA;
  private static final int TITLE_COLOR = 0xFF131D2F;
  private final String id;
  private final Typeface typeface;
  private final RectF bounds = new RectF();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private ComponentHost host;
  private Bitmap icon;
  private String title = "";
  private boolean forwarded;
  private boolean visible;
  private boolean released;

  CallPreviewComponent(String id, Typeface typeface, float figmaScale) {
    this.id = id;
    this.typeface = typeface;
  }

  CallPreviewComponent bind(
      RectF region, Bitmap callIcon, String callTitle, boolean isForwarded) {
    bounds.set(region);
    icon = callIcon;
    title = callTitle == null ? "" : callTitle.trim();
    forwarded = isForwarded;
    visible = true;
    invalidate();
    return this;
  }

  CallPreviewComponent hide() {
    visible = false;
    invalidate();
    return this;
  }

  @Override public String getId() { return id; }
  @Override public RectF getBounds() { return bounds; }
  @Override public boolean isVisible() { return visible && !released; }
  @Override public boolean isEnabled() { return false; }
  @Override public boolean onTouchEvent(MotionEvent event) { return false; }
  @Override public void attach(ComponentHost owner) { host = owner; }
  @Override public void release() { released = true; visible = false; host = null; icon = null; }

  @Override
  public void draw(Canvas canvas) {
    if (!isVisible() || bounds.isEmpty()) return;
    float scale = bounds.width() / 596f;
    float inset = 12f * scale;
    // The forwarded header has already positioned the top of its inner surface. Applying
    // this component's regular top inset again creates a second, visible gap.
    RectF inner = new RectF(bounds.left + inset, bounds.top + (forwarded ? 0f : inset),
        bounds.right - inset, bounds.bottom - inset);
    paint.setColor(PANEL_COLOR);
    float radius = 44f * scale;
    canvas.drawRoundRect(inner, radius, radius, paint);

    float iconSize = 40f * scale;
    float iconLeft = inner.left + 30f * scale + (57f * scale - iconSize) / 2f;
    float iconTop = inner.centerY() - iconSize / 2f;
    if (icon != null && !icon.isRecycled()) {
      canvas.drawBitmap(icon, null,
          new RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize), paint);
    }

    paint.setTypeface(typeface);
    paint.setColor(TITLE_COLOR);
    paint.setTextSize(34f * scale);
    String fitted = ellipsize(title, paint,
        Math.max(1f, inner.right - 18f * scale - (inner.left + 116f * scale)));
    Paint.FontMetrics metrics = paint.getFontMetrics();
    float baseline = inner.centerY() - (metrics.ascent + metrics.descent) / 2f;
    canvas.drawText(fitted, inner.left + 116f * scale, baseline, paint);
  }

  private static String ellipsize(String value, Paint paint, float maximumWidth) {
    if (paint.measureText(value) <= maximumWidth) return value;
    String suffix = "...";
    float suffixWidth = paint.measureText(suffix);
    int end = value.length();
    while (end > 0 && paint.measureText(value, 0, end) + suffixWidth > maximumWidth) end--;
    return value.substring(0, end).trim() + suffix;
  }

  private void invalidate() { if (host != null) host.invalidateComponent(); }
}
