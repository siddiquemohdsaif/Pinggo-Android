package com.w3n.pinggo.views.chat;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import com.ogfa.nativeviews.component.Component;
import com.ogfa.nativeviews.component.ComponentHost;

/** Directly drawn, recycled date pill displayed at local calendar-day boundaries. */
final class DateNotifierComponent implements Component {
  private static final float MESSAGE_SCALE = 1.15f;
  static final float HEIGHT_PX = 51f * MESSAGE_SCALE;
  private static final float VERTICAL_PADDING_DP = 8f;

  private static final int BACKGROUND_COLOR = 0xFF5C6B85;
  private static final int TEXT_COLOR = 0xFFF9FBFE;
  private static final float TEXT_SIZE_PX = 28f * MESSAGE_SCALE;
  private static final float HORIZONTAL_PADDING_PX = 36f;
  private static final float CORNER_RADIUS_PX = 20f;

  private final String id;
  private final float density;
  private final RectF bounds = new RectF();
  private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private ComponentHost host;
  private String label = "";
  private boolean visible;
  private boolean released;

  DateNotifierComponent(String id, Typeface typeface, float density) {
    this.id = id;
    this.density = Math.max(1f, density);
    backgroundPaint.setColor(BACKGROUND_COLOR);
    backgroundPaint.setStyle(Paint.Style.FILL);
    textPaint.setColor(TEXT_COLOR);
    textPaint.setTextSize(TEXT_SIZE_PX);
    textPaint.setTypeface(typeface);
    textPaint.setTextAlign(Paint.Align.CENTER);
  }

  DateNotifierComponent bind(String value, float rowWidth) {
    ensureActive();
    String next = value == null ? "" : value;
    boolean nextVisible = !next.isEmpty();
    float width = nextVisible
        ? (float) Math.ceil(textPaint.measureText(next) + HORIZONTAL_PADDING_PX * 2f)
        : 0f;
    float verticalPadding = VERTICAL_PADDING_DP * density;
    float left = (rowWidth - width) / 2f;
    boolean changed = !label.equals(next)
        || visible != nextVisible
        || !sameBounds(left, verticalPadding, left + width, verticalPadding + HEIGHT_PX);
    label = next;
    visible = nextVisible;
    bounds.set(left, verticalPadding, left + width, verticalPadding + HEIGHT_PX);
    if (changed) invalidate();
    return this;
  }

  @Override public String getId() { return id; }
  @Override public RectF getBounds() { return bounds; }

  @Override
  public void draw(Canvas canvas) {
    if (released || !visible || bounds.isEmpty()) return;
    canvas.drawRoundRect(bounds, CORNER_RADIUS_PX, CORNER_RADIUS_PX, backgroundPaint);
    Paint.FontMetrics metrics = textPaint.getFontMetrics();
    float baseline = bounds.centerY() - (metrics.ascent + metrics.descent) / 2f;
    canvas.drawText(label, bounds.centerX(), baseline, textPaint);
  }

  @Override public boolean onTouchEvent(MotionEvent event) { return false; }
  @Override public boolean isVisible() { return visible && !released; }
  @Override public boolean isEnabled() { return false; }

  @Override
  public void attach(ComponentHost owner) {
    ensureActive();
    if (host != null && host != owner) {
      throw new IllegalStateException("Date notifier already belongs to another host.");
    }
    host = owner;
  }

  @Override
  public void release() {
    released = true;
    visible = false;
    host = null;
  }

  private boolean sameBounds(float left, float top, float right, float bottom) {
    return Math.abs(bounds.left - left) < .5f
        && Math.abs(bounds.top - top) < .5f
        && Math.abs(bounds.right - right) < .5f
        && Math.abs(bounds.bottom - bottom) < .5f;
  }

  private void invalidate() {
    if (host != null) host.invalidateComponent();
  }

  private void ensureActive() {
    if (released) throw new IllegalStateException("Date notifier has been released.");
  }

  static float blockHeight(float density) {
    return HEIGHT_PX + VERTICAL_PADDING_DP * Math.max(1f, density) * 2f;
  }
}
