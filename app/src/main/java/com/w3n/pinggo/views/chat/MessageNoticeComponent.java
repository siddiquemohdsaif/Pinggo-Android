package com.w3n.pinggo.views.chat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import com.ogfa.nativeviews.component.Component;
import com.ogfa.nativeviews.component.ComponentHost;

/** Recycled icon-and-text notice used for deleted and forwarded messages. */
final class MessageNoticeComponent implements Component {
  private static final int TEXT_COLOR = 0xFF131D2F;
  private final String id;
  private final RectF bounds = new RectF();
  private final Bitmap icon;
  private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
  private ComponentHost host;
  private String text = "";
  private float iconSize;
  private float textGap;
  private boolean visible;
  private boolean released;

  MessageNoticeComponent(String id, Bitmap icon, Typeface typeface, float textSize) {
    this.id = id;
    this.icon = icon;
    textPaint.setColor(TEXT_COLOR);
    textPaint.setTextSize(textSize);
    textPaint.setTypeface(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
        ? Typeface.create(typeface, 100, false)
        : Typeface.create(typeface, Typeface.NORMAL));
    textPaint.setTextSkewX(-0.22f);
  }

  MessageNoticeComponent bind(
      RectF region, String value, float requestedIconSize, float requestedTextGap) {
    bounds.set(region);
    text = value == null ? "" : value;
    iconSize = requestedIconSize;
    textGap = requestedTextGap;
    visible = !text.isEmpty();
    invalidate();
    return this;
  }

  MessageNoticeComponent hide() {
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
  @Override public void release() { released = true; visible = false; host = null; }

  @Override
  public void draw(Canvas canvas) {
    if (!isVisible() || bounds.isEmpty()) return;
    float iconTop = bounds.centerY() - iconSize / 2f;
    if (icon != null && !icon.isRecycled()) {
      canvas.drawBitmap(icon, null,
          new RectF(bounds.left, iconTop, bounds.left + iconSize, iconTop + iconSize), iconPaint);
    }
    Paint.FontMetrics metrics = textPaint.getFontMetrics();
    float baseline = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f;
    canvas.drawText(text, bounds.left + iconSize + textGap, baseline, textPaint);
  }

  private void invalidate() { if (host != null) host.invalidateComponent(); }
}
