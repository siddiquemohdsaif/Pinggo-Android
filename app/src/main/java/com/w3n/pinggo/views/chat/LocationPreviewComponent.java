package com.w3n.pinggo.views.chat;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import com.ogfa.nativeviews.component.Component;
import com.ogfa.nativeviews.component.ComponentHost;

/** Lightweight map-style location banner with no external map SDK dependency. */
final class LocationPreviewComponent implements Component {
  private static final int PANEL_COLOR = 0xFFF8F9FA;
  private static final int MAP_COLOR = 0xFFE6F2EC;
  private static final int MAP_BLOCK_COLOR = 0xFFD4E8DB;
  private static final int ROAD_COLOR = 0xFFFFFFFF;
  private static final int ACCENT_COLOR = 0xFF019CC4;
  private static final int COORDINATE_COLOR = 0xFF5C6B85;
  private final String id;
  private final RectF bounds = new RectF();
  private final RectF inner = new RectF();
  private final Path clip = new Path();
  private final Path pin = new Path();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint coordinatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint actionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private ComponentHost host;
  private String coordinates = "";
  private boolean visible;
  private boolean released;

  LocationPreviewComponent(String id, Typeface typeface, float figmaScale) {
    this.id = id;
    coordinatePaint.setColor(COORDINATE_COLOR);
    coordinatePaint.setTypeface(typeface);
    actionPaint.setColor(ACCENT_COLOR);
    actionPaint.setTypeface(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
        ? Typeface.create(typeface, 600, false) : Typeface.create(typeface, Typeface.BOLD));
  }

  LocationPreviewComponent bind(RectF region, double latitude, double longitude) {
    bounds.set(region);
    coordinates = String.format(java.util.Locale.US, "%.6f, %.6f", latitude, longitude);
    visible = true;
    invalidate();
    return this;
  }

  LocationPreviewComponent hide() {
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
    float scale = bounds.width() / 620f;
    float inset = 12f * scale;
    float radius = 44f * scale;
    inner.set(bounds.left + inset, bounds.top + inset,
        bounds.right - inset, bounds.bottom - inset);
    paint.setColor(PANEL_COLOR);
    canvas.drawRoundRect(inner, radius, radius, paint);

    float actionHeight = 72f * scale;
    RectF map = new RectF(inner.left, inner.top, inner.right, inner.bottom - actionHeight);
    clip.reset();
    clip.addRoundRect(inner, radius, radius, Path.Direction.CW);
    canvas.save();
    canvas.clipPath(clip);
    paint.setColor(MAP_COLOR);
    canvas.drawRect(map, paint);
    paint.setColor(MAP_BLOCK_COLOR);
    canvas.drawRect(map.left + 28f * scale, map.top + 22f * scale,
        map.left + 174f * scale, map.top + 91f * scale, paint);
    canvas.drawRect(map.right - 196f * scale, map.top + 28f * scale,
        map.right - 34f * scale, map.top + 102f * scale, paint);
    canvas.drawRect(map.left + 55f * scale, map.bottom - 75f * scale,
        map.left + 230f * scale, map.bottom - 20f * scale, paint);
    paint.setColor(ROAD_COLOR);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(16f * scale);
    canvas.drawLine(map.left - 8f * scale, map.bottom - 45f * scale,
        map.right + 8f * scale, map.top + 50f * scale, paint);
    paint.setStrokeWidth(11f * scale);
    canvas.drawLine(map.left + 120f * scale, map.top - 8f * scale,
        map.right - 80f * scale, map.bottom + 8f * scale, paint);
    paint.setStyle(Paint.Style.FILL);

    float centerX = map.centerX();
    float centerY = map.centerY() - 8f * scale;
    float pinRadius = 20f * scale;
    paint.setColor(ACCENT_COLOR);
    canvas.drawCircle(centerX, centerY, pinRadius, paint);
    pin.reset();
    pin.moveTo(centerX - 12f * scale, centerY + 13f * scale);
    pin.lineTo(centerX + 12f * scale, centerY + 13f * scale);
    pin.lineTo(centerX, centerY + 38f * scale);
    pin.close();
    canvas.drawPath(pin, paint);
    paint.setColor(ROAD_COLOR);
    canvas.drawCircle(centerX, centerY, 7f * scale, paint);

    coordinatePaint.setTextSize(22f * scale);
    float pillPadding = 10f * scale;
    float pillWidth = coordinatePaint.measureText(coordinates) + pillPadding * 2f;
    RectF pill = new RectF(map.left + 14f * scale, map.bottom - 44f * scale,
        map.left + 14f * scale + pillWidth, map.bottom - 10f * scale);
    paint.setColor(0xEFFFFFFF);
    canvas.drawRoundRect(pill, 10f * scale, 10f * scale, paint);
    drawCentered(canvas, coordinates, pill, coordinatePaint);
    canvas.restore();

    actionPaint.setTextSize(27f * scale);
    RectF action = new RectF(inner.left + 18f * scale, map.bottom,
        inner.right - 170f * scale, inner.bottom);
    drawCenteredStart(canvas, "Open in map", action, actionPaint);
  }

  private static void drawCentered(Canvas canvas, String value, RectF region, Paint textPaint) {
    textPaint.setTextAlign(Paint.Align.CENTER);
    Paint.FontMetrics metrics = textPaint.getFontMetrics();
    canvas.drawText(value, region.centerX(),
        region.centerY() - (metrics.ascent + metrics.descent) / 2f, textPaint);
  }

  private static void drawCenteredStart(
      Canvas canvas, String value, RectF region, Paint textPaint) {
    textPaint.setTextAlign(Paint.Align.LEFT);
    Paint.FontMetrics metrics = textPaint.getFontMetrics();
    canvas.drawText(value, region.left,
        region.centerY() - (metrics.ascent + metrics.descent) / 2f, textPaint);
  }

  private void invalidate() { if (host != null) host.invalidateComponent(); }
}
