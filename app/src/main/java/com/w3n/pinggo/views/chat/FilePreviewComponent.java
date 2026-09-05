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
  private final RectF bounds = new RectF();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private ComponentHost host;
  private String title = "";
  private String subtitle = "";
  private int attachmentState;
  private long downloadedBytes;
  private long totalBytes;
  private boolean visible;
  private boolean released;

  FilePreviewComponent(String id, Bitmap documentIcon, Typeface typeface, float figmaScale) {
    this.id = id;
    this.documentIcon = documentIcon;
    this.typeface = typeface;
  }

  FilePreviewComponent bind(
      RectF region, String fileTitle, String fileSubtitle, int state,
      long currentBytes, long fileBytes) {
    bounds.set(region);
    title = fileTitle == null || fileTitle.trim().isEmpty() ? "File" : fileTitle.trim();
    subtitle = fileSubtitle == null ? "" : fileSubtitle;
    attachmentState = state;
    downloadedBytes = Math.max(0L, currentBytes);
    totalBytes = Math.max(0L, fileBytes);
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
    float bubbleRadius = 44f * scale;
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
    boolean downloading = attachmentState == 2;
    boolean downloadRequired = attachmentState == 1;
    String shownSubtitle = downloading
        ? formatSize(downloadedBytes) + " / "
            + (totalBytes > 0L ? formatSize(totalBytes) : "unknown")
        : downloadRequired
            ? (totalBytes > 0L ? formatSize(totalBytes) : "File")
        : subtitle;
    float subtitleTop = inner.top + 19f * scale + titleLayout.getHeight()
        + TITLE_SUBTITLE_GAP * scale;
    float subtitleLeft = textLeft;
    if (downloading) {
      float progressRadius = 11f * scale;
      float progressCenterX = textLeft + progressRadius;
      Paint.FontMetrics subtitleMetrics = paint.getFontMetrics();
      float progressCenterY = subtitleTop
          + (subtitleMetrics.descent - subtitleMetrics.ascent) / 2f;
      RectF progressRing = new RectF(
          progressCenterX - progressRadius, progressCenterY - progressRadius,
          progressCenterX + progressRadius, progressCenterY + progressRadius);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeCap(Paint.Cap.ROUND);
      paint.setStrokeWidth(4f * scale);
      paint.setColor(0xFFD7E5EA);
      canvas.drawArc(progressRing, 0f, 360f, false, paint);
      paint.setColor(0xFF019CC4);
      if (totalBytes > 0L) {
        float fraction = Math.min(1f, downloadedBytes / (float) totalBytes);
        canvas.drawArc(progressRing, -90f, 360f * fraction, false, paint);
      } else {
        canvas.drawArc(progressRing, -90f, 270f, false, paint);
      }
      paint.setStyle(Paint.Style.FILL);
      subtitleLeft += 34f * scale;
    } else if (downloadRequired) {
      float iconSize = 22f * scale;
      float centerX = textLeft + iconSize / 2f;
      Paint.FontMetrics subtitleMetrics = paint.getFontMetrics();
      float centerY = subtitleTop + (subtitleMetrics.descent - subtitleMetrics.ascent) / 2f;
      drawDownloadIcon(canvas, centerX, centerY, iconSize, scale);
      subtitleLeft += 34f * scale;
    }
    paint.setColor(SUBTITLE_COLOR);
    String fittedSubtitle = ellipsize(
        shownSubtitle, paint, Math.max(1f, textRight - subtitleLeft));
    drawFromTop(canvas, fittedSubtitle, subtitleLeft, subtitleTop, paint);
  }

  private void drawDownloadIcon(
      Canvas canvas, float centerX, float centerY, float size, float scale) {
    paint.setColor(0xFF019CC4);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(3.5f * scale);
    paint.setStrokeCap(Paint.Cap.ROUND);
    float top = centerY - size * .5f;
    float bottom = centerY + size * .15f;
    canvas.drawLine(centerX, top, centerX, bottom, paint);
    canvas.drawLine(centerX, bottom, centerX - size * .3f, centerY - size * .12f, paint);
    canvas.drawLine(centerX, bottom, centerX + size * .3f, centerY - size * .12f, paint);
    canvas.drawLine(centerX - size * .38f, centerY + size * .45f,
        centerX + size * .38f, centerY + size * .45f, paint);
    paint.setStyle(Paint.Style.FILL);
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

  private static String formatSize(long bytes) {
    if (bytes < 1024L) return bytes + " B";
    if (bytes < 1024L * 1024L) {
      return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024d);
    }
    return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024d * 1024d));
  }

  private void invalidate() { if (host != null) host.invalidateComponent(); }
}
