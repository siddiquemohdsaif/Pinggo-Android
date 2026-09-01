package com.w3n.pinggo.views.chat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import com.ogfa.nativeviews.component.Component;
import com.ogfa.nativeviews.component.ComponentHost;

/** Fixed-size document preview drawn inside a tail-less file-message bubble. */
final class FilePreviewComponent implements Component {
  private static final int PANEL_COLOR = 0xFFF8F9FA;
  private static final int TITLE_COLOR = 0xFF131D2F;
  private static final int SUBTITLE_COLOR = 0xFF687382;
  private static final float TITLE_LINE_SPACING = 4f;
  private static final float TITLE_SUBTITLE_GAP = 12f;
  private final String id;
  private final Bitmap documentIcon;
  private final Typeface typeface;
  private final float figmaScale;
  private final RectF bounds = new RectF();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private ComponentHost host;
  private String title = "";
  private String subtitle = "";
  private boolean visible;
  private boolean released;

  FilePreviewComponent(String id, Bitmap documentIcon, Typeface typeface, float figmaScale) {
    this.id = id;
    this.documentIcon = documentIcon;
    this.typeface = typeface;
    this.figmaScale = Math.max(.01f, figmaScale);
  }

  FilePreviewComponent bind(RectF region, String fileTitle, String fileSubtitle) {
    bounds.set(region);
    title = fileTitle == null || fileTitle.trim().isEmpty() ? "File" : fileTitle.trim();
    subtitle = fileSubtitle == null ? "" : fileSubtitle;
    visible = true;
    invalidate();
    return this;
  }

  FilePreviewComponent hide() {
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
  @Override public void release() { released = true; host = null; }

  @Override public void draw(Canvas canvas) {
    if (!isVisible() || bounds.isEmpty()) return;
    float scale = bounds.width() / 596f;
    float inset = 12f * scale;
    RectF inner = new RectF(bounds.left + inset, bounds.top + inset,
        bounds.right - inset, bounds.bottom - inset);
    paint.setColor(PANEL_COLOR);
    float bubbleRadius = 44f * figmaScale;
    canvas.drawRoundRect(inner, bubbleRadius, bubbleRadius, paint);

    RectF icon = new RectF(
        inner.left + 30f * scale,
        inner.top + 17f * scale,
        inner.left + (30f + 57f) * scale,
        inner.top + (17f + 70f) * scale);
    if (documentIcon != null && !documentIcon.isRecycled()) {
      canvas.drawBitmap(documentIcon, null, icon, paint);
    }

    float textLeft = inner.left + 116f * scale;
    float textRight = inner.right - 18f * scale;
    TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    titlePaint.setTypeface(typeface);
    titlePaint.setColor(TITLE_COLOR);
    titlePaint.setTextSize(34f * scale);
    int titleWidth = Math.max(1, Math.round(textRight - textLeft));
    StaticLayout titleLayout = StaticLayout.Builder.obtain(
            title, 0, title.length(), titlePaint, titleWidth)
        .setIncludePad(false)
        .setLineSpacing(TITLE_LINE_SPACING * scale, 1f)
        .build();
    canvas.save();
    canvas.translate(textLeft, inner.top + 19f * scale);
    titleLayout.draw(canvas);
    canvas.restore();
    paint.setTypeface(typeface);
    paint.setColor(SUBTITLE_COLOR);
    paint.setTextSize(30f * scale);
    String fittedSubtitle = ellipsize(subtitle, paint, Math.max(1f, textRight - textLeft));
    float subtitleTop = inner.top + 19f * scale + titleLayout.getHeight()
        + TITLE_SUBTITLE_GAP * scale;
    drawFromTop(canvas, fittedSubtitle, textLeft, subtitleTop, paint);
  }

  private static void drawFromTop(
      Canvas canvas, String value, float left, float top, Paint paint) {
    Paint.FontMetrics metrics = paint.getFontMetrics();
    canvas.drawText(value, left, top - metrics.ascent, paint);
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
