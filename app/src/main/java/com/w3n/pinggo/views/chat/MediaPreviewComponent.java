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
  private float spinnerAngle;
  private float contentScale = 1f;
  private final Runnable animateSpinner = new Runnable() {
    @Override public void run() {
      if (!loading || released || !visible) return;
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

  MediaPreviewComponent bind(RectF region, String value, boolean isVideo, float scale) {
    bounds.set(region);
    contentScale = Math.max(.1f, scale);
    visible = true;
    int targetWidth = Math.max(1, Math.round(region.width()));
    int targetHeight = Math.max(1, Math.round(region.height()));
    if (!source.equals(String.valueOf(value)) || video != isVideo
        || loadedWidth != targetWidth || loadedHeight != targetHeight) {
      source = String.valueOf(value);
      video = isVideo;
      loadedWidth = targetWidth;
      loadedHeight = targetHeight;
      bitmap = null;
      duration = "";
      MAIN.removeCallbacks(animateSpinner);
      int request = ++generation;
      MediaPreviewCache.Thumbnail cached = MediaPreviewCache.memoryThumbnail(
          source, video, targetWidth, targetHeight);
      if (cached != null) {
        bitmap = cached.bitmap;
        duration = cached.duration;
        loading = false;
        notifyOrientation(cached);
      } else {
        loading = true;
        MAIN.post(animateSpinner);
        MediaPreviewCache.loadThumbnail(context, source, video, targetWidth, targetHeight,
            new MediaPreviewCache.Callback<MediaPreviewCache.Thumbnail>() {
              @Override public void onSuccess(MediaPreviewCache.Thumbnail result) {
                if (request != generation || released) return;
                bitmap = result.bitmap;
                duration = result.duration;
                loading = false;
                MAIN.removeCallbacks(animateSpinner);
                notifyOrientation(result);
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
      }
    }
    invalidate();
    return this;
  }

  MediaPreviewComponent hide() {
    visible = false;
    generation++;
    source = "";
    loadedWidth = 0;
    loadedHeight = 0;
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
    canvas.drawRoundRect(bounds, px(33f) * contentScale, px(33f) * contentScale, paint);
    if (bitmap != null && !bitmap.isRecycled()) {
      Rect sourceRect = centerCrop(bitmap.getWidth(), bitmap.getHeight(), bounds);
      android.graphics.Path mediaClip = new android.graphics.Path();
      mediaClip.addRoundRect(bounds, px(33f) * contentScale, px(33f) * contentScale,
          android.graphics.Path.Direction.CW);
      canvas.save();
      canvas.clipPath(mediaClip);
      canvas.drawBitmap(bitmap, sourceRect, bounds, paint);
      canvas.restore();
    }
    if (loading) drawSpinner(canvas);
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
    paint.setColor(0xFF019CC4);
    float radius = px(44f) * contentScale;
    canvas.drawArc(new RectF(bounds.centerX() - radius, bounds.centerY() - radius,
        bounds.centerX() + radius, bounds.centerY() + radius), spinnerAngle, 275, false, paint);
    paint.setStyle(Paint.Style.FILL);
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
