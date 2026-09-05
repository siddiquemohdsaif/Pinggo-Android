package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;
import com.ogfa.nativeviews.component.Component;
import com.ogfa.nativeviews.component.ComponentHost;
import com.w3n.pinggo.data.cache.MediaPreviewCache;

/** Recyclable, asynchronously loaded image/video thumbnail used inside a message bubble. */
final class MediaPreviewComponent implements Component {
  interface OrientationListener {
    void onOrientationAvailable(String source, boolean portrait);
  }
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private final String id;
  private final Context context;
  private final float figmaScale;
  private final OrientationListener orientationListener;
  private final RectF bounds = new RectF();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private ComponentHost host;
  private Bitmap bitmap;
  private String source = "";
  private String duration = "";
  private boolean video;
  private boolean loading;
  private boolean visible;
  private boolean released;
  private int generation;
  private int loadedWidth;
  private int loadedHeight;
  private long metadataDurationMs;
  private long metadataTotalBytes;
  private long downloadedBytes;
  private long totalBytes;
  private float spinnerAngle;
  private float contentScale = 1f;
  private final Runnable animateSpinner = new Runnable() {
    @Override public void run() {
      if (!loading || released || !visible) return;
      if (totalBytes > 0L) {
        invalidate();
        return;
      }
      spinnerAngle = (spinnerAngle + 12f) % 360f;
      invalidate();
      MAIN.postDelayed(this, 16L);
    }
  };

  MediaPreviewComponent(
      String id, Context context, float figmaScale, OrientationListener orientationListener) {
    this.id = id;
    this.context = context.getApplicationContext();
    this.figmaScale = Math.max(.01f, figmaScale);
    this.orientationListener = orientationListener;
  }

  MediaPreviewComponent bind(
      RectF region, String value, boolean isVideo, float scale, long suppliedDurationMs,
      long suppliedTotalBytes) {
    bounds.set(region);
    contentScale = Math.max(.1f, scale);
    visible = true;
    int targetWidth = Math.max(1, Math.round(region.width()));
    int targetHeight = Math.max(1, Math.round(region.height()));
    if (!source.equals(String.valueOf(value)) || video != isVideo
        || loadedWidth != targetWidth || loadedHeight != targetHeight
        || metadataDurationMs != suppliedDurationMs
        || metadataTotalBytes != suppliedTotalBytes) {
      source = String.valueOf(value);
      video = isVideo;
      loadedWidth = targetWidth;
      loadedHeight = targetHeight;
      metadataDurationMs = suppliedDurationMs;
      metadataTotalBytes = Math.max(0L, suppliedTotalBytes);
      downloadedBytes = 0L;
      totalBytes = metadataTotalBytes;
      bitmap = null;
      duration = isVideo ? formatDuration(suppliedDurationMs) : "";
      MAIN.removeCallbacks(animateSpinner);
      int request = ++generation;
      MediaPreviewCache.Thumbnail cached = MediaPreviewCache.memoryThumbnail(
          source, video, targetWidth, targetHeight);
      if (cached != null) {
        Log.d("PingGoMessageTrace", "stage=media_thumbnail_ready source=memory"
            + " mediaType=" + (video ? "video" : "image")
            + " sourceKey=" + Integer.toHexString(source.hashCode()));
        bitmap = cached.bitmap;
        if (!cached.duration.isEmpty()) duration = cached.duration;
        loading = false;
        notifyOrientation(cached);
      } else if (!MediaPreviewCache.isDecodingPaused()) {
        Log.d("PingGoMessageTrace", "stage=media_thumbnail_loading"
            + " mediaType=" + (video ? "video" : "image")
            + " sourceKey=" + Integer.toHexString(source.hashCode()));
        loading = true;
        MAIN.post(animateSpinner);
        MediaPreviewCache.loadThumbnail(context, source, video, targetWidth, targetHeight,
            new MediaPreviewCache.Callback<MediaPreviewCache.Thumbnail>() {
              @Override public void onSuccess(MediaPreviewCache.Thumbnail result) {
                if (request != generation || released) return;
                bitmap = result.bitmap;
                if (!result.duration.isEmpty()) duration = result.duration;
                loading = false;
                Log.d("PingGoMessageTrace", "stage=media_thumbnail_ready source=async"
                    + " mediaType=" + (video ? "video" : "image")
                    + " sourceKey=" + Integer.toHexString(source.hashCode()));
                MAIN.removeCallbacks(animateSpinner);
                notifyOrientation(result);
                invalidate();
              }

              @Override public void onProgress(long downloaded, long total) {
                if (request != generation || released) return;
                downloadedBytes = Math.max(0L, downloaded);
                totalBytes = total > 0L ? total : metadataTotalBytes;
                invalidate();
              }

              @Override public void onError() {
                if (request != generation || released) return;
                loading = false;
                MAIN.removeCallbacks(animateSpinner);
                Toast.makeText(context, "This file does not exist.", Toast.LENGTH_SHORT).show();
                invalidate();
              }
            });
      } else {
        // A scroll-aware prefetch will populate the cache and rebind this row after the fling.
        loading = false;
      }
    }
    if (bitmap == null && !loading) {
      MediaPreviewCache.Thumbnail warmed = MediaPreviewCache.memoryThumbnail(
          source, video, targetWidth, targetHeight);
      if (warmed != null) {
        bitmap = warmed.bitmap;
        if (!warmed.duration.isEmpty()) duration = warmed.duration;
        notifyOrientation(warmed);
      }
    }
    invalidate();
    return this;
  }

  private static String formatDuration(long durationMs) {
    if (durationMs <= 0L) return "";
    long totalSeconds = durationMs / 1000L;
    return String.format(java.util.Locale.US, "%d:%02d", totalSeconds / 60L,
        totalSeconds % 60L);
  }

  MediaPreviewComponent hide() {
    visible = false;
    generation++;
    source = "";
    loadedWidth = 0;
    loadedHeight = 0;
    metadataDurationMs = 0L;
    metadataTotalBytes = 0L;
    downloadedBytes = 0L;
    totalBytes = 0L;
    loading = false;
    MAIN.removeCallbacks(animateSpinner);
    bitmap = null;
    invalidate();
    return this;
  }

  @Override public String getId() { return id; }
  @Override public RectF getBounds() { return bounds; }
  @Override public boolean isVisible() { return visible && !released; }
  @Override public boolean isEnabled() { return false; }
  @Override public boolean onTouchEvent(MotionEvent event) { return false; }
  @Override public void attach(ComponentHost owner) { host = owner; }
  @Override public void release() {
    released = true;
    generation++;
    MAIN.removeCallbacks(animateSpinner);
    host = null;
    bitmap = null;
  }

  @Override public void draw(Canvas canvas) {
    if (!isVisible() || bounds.isEmpty()) return;
    paint.setColor(0xFFE8EDF2);
    float radius = px(44f) * contentScale;
    canvas.drawRoundRect(bounds, radius, radius, paint);
    if (bitmap != null && !bitmap.isRecycled()) {
      Rect sourceRect = centerCrop(bitmap.getWidth(), bitmap.getHeight(), bounds);
      android.graphics.Path mediaClip = new android.graphics.Path();
      mediaClip.addRoundRect(bounds, radius, radius,
          android.graphics.Path.Direction.CW);
      canvas.save();
      canvas.clipPath(mediaClip);
      canvas.drawBitmap(bitmap, sourceRect, bounds, paint);
      canvas.restore();
    }
    if (loading) drawSpinner(canvas);
    else if (bitmap == null) drawDownloadRequired(canvas);
    if (video && bitmap != null) {
      paint.setColor(0xB3000000);
      canvas.drawCircle(bounds.centerX(), bounds.centerY(), px(74.25f) * contentScale, paint);
      paint.setColor(Color.WHITE);
      android.graphics.Path triangle = new android.graphics.Path();
      triangle.moveTo(bounds.centerX() - px(19.25f) * contentScale,
          bounds.centerY() - px(33f) * contentScale);
      triangle.lineTo(bounds.centerX() + px(33f) * contentScale, bounds.centerY());
      triangle.lineTo(bounds.centerX() - px(19.25f) * contentScale,
          bounds.centerY() + px(33f) * contentScale);
      triangle.close();
      canvas.drawPath(triangle, paint);
      if (!duration.isEmpty()) {
        paint.setTextSize(px(33f) * contentScale);
        paint.setColor(Color.WHITE);
        paint.setShadowLayer(px(5.5f), 0, px(2.75f), Color.BLACK);
        canvas.drawText(duration, bounds.left + px(27.5f) * contentScale,
            bounds.bottom - px(27.5f) * contentScale, paint);
        paint.clearShadowLayer();
      }
    }
  }

  private void drawSpinner(Canvas canvas) {
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(px(8.25f) * contentScale);
    paint.setStrokeCap(Paint.Cap.ROUND);
    float radius = px(44f) * contentScale;
    RectF ring = new RectF(bounds.centerX() - radius, bounds.centerY() - radius,
        bounds.centerX() + radius, bounds.centerY() + radius);
    boolean determinate = totalBytes > 0L;
    if (determinate) {
      paint.setColor(0x40019CC4);
      canvas.drawArc(ring, 0f, 360f, false, paint);
      paint.setColor(0xFF019CC4);
      float progress = Math.min(1f, downloadedBytes / (float) totalBytes);
      canvas.drawArc(ring, -90f, 360f * progress, false, paint);
    } else {
      paint.setColor(0xFF019CC4);
      canvas.drawArc(ring, spinnerAngle, 275f, false, paint);
    }
    paint.setStyle(Paint.Style.FILL);
    String progressText = formatSize(downloadedBytes) + " / "
        + (totalBytes > 0L ? formatSize(totalBytes) : "unknown");
    paint.setTextSize(px(25f) * contentScale);
    paint.setColor(0xFF334155);
    paint.setTextAlign(Paint.Align.CENTER);
    canvas.drawText(progressText, bounds.centerX(),
        bounds.centerY() + radius + px(42f) * contentScale, paint);
    paint.setTextAlign(Paint.Align.LEFT);
  }

  private void drawDownloadRequired(Canvas canvas) {
    float scale = contentScale;
    float circleRadius = px(48f) * scale;
    float centerX = bounds.centerX();
    float centerY = bounds.centerY() - px(16f) * scale;
    paint.setColor(0xE6FFFFFF);
    paint.setStyle(Paint.Style.FILL);
    canvas.drawCircle(centerX, centerY, circleRadius, paint);
    paint.setColor(0xFF019CC4);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(px(7f) * scale);
    paint.setStrokeCap(Paint.Cap.ROUND);
    float shaftTop = centerY - px(23f) * scale;
    float shaftBottom = centerY + px(13f) * scale;
    canvas.drawLine(centerX, shaftTop, centerX, shaftBottom, paint);
    canvas.drawLine(centerX, shaftBottom, centerX - px(18f) * scale,
        centerY - px(4f) * scale, paint);
    canvas.drawLine(centerX, shaftBottom, centerX + px(18f) * scale,
        centerY - px(4f) * scale, paint);
    paint.setStyle(Paint.Style.FILL);
    String size = metadataTotalBytes > 0L ? formatSize(metadataTotalBytes) : "File";
    paint.setTextSize(px(25f) * scale);
    paint.setColor(0xFF334155);
    paint.setTextAlign(Paint.Align.CENTER);
    canvas.drawText(size, centerX, centerY + circleRadius + px(38f) * scale, paint);
    paint.setTextAlign(Paint.Align.LEFT);
  }

  private static String formatSize(long bytes) {
    if (bytes < 1024L) return bytes + " B";
    if (bytes < 1024L * 1024L) {
      return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024d);
    }
    return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024d * 1024d));
  }

  private static Rect centerCrop(int width, int height, RectF destination) {
    float target = destination.width() / destination.height();
    float actual = width / (float) height;
    if (actual > target) {
      int cropWidth = Math.round(height * target);
      int left = (width - cropWidth) / 2;
      return new Rect(left, 0, left + cropWidth, height);
    }
    int cropHeight = Math.round(width / target);
    int top = (height - cropHeight) / 2;
    return new Rect(0, top, width, top + cropHeight);
  }

  private float px(float value) { return value * figmaScale; }
  private void notifyOrientation(MediaPreviewCache.Thumbnail thumbnail) {
    if (orientationListener != null) {
      String loadedSource = source;
      MAIN.post(() -> orientationListener.onOrientationAvailable(loadedSource, thumbnail.portrait));
    }
  }
  private void invalidate() { if (host != null) host.invalidateComponent(); }
}
