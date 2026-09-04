package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;

/** Continuous video-frame strip with WhatsApp-style draggable trim handles. */
final class VideoTrimStripView extends View {
  private static final float MIN_RANGE = 0.05f;
  private final List<Bitmap> frames = new ArrayList<>();
  private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Paint shadePaint = new Paint();
  private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private Listener listener;
  private float startFraction;
  private float endFraction = 1f;
  private int draggingHandle;

  VideoTrimStripView(Context context, Listener listener) {
    super(context);
    this.listener = listener;
    shadePaint.setColor(0x99000000);
    borderPaint.setColor(0xFF22C879);
    borderPaint.setStyle(Paint.Style.STROKE);
    borderPaint.setStrokeWidth(dp(3));
    handlePaint.setColor(0xFF22C879);
    setBackgroundColor(0xFF252A30);
    setClickable(true);
  }

  void setFrame(int index, Bitmap bitmap) {
    while (frames.size() <= index) frames.add(null);
    frames.set(index, bitmap);
    invalidate();
  }

  void clearFrames(int count) {
    frames.clear();
    for (int index = 0; index < count; index++) frames.add(null);
    invalidate();
  }

  void setRange(float start, float end) {
    startFraction = clamp(start, 0f, 1f - MIN_RANGE);
    endFraction = clamp(end, startFraction + MIN_RANGE, 1f);
    invalidate();
  }

  @Override protected void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);
    float width = getWidth();
    float height = getHeight();
    if (width <= 0 || height <= 0) return;
    int count = Math.max(1, frames.size());
    float cellWidth = width / count;
    for (int index = 0; index < count; index++) {
      Bitmap frame = index < frames.size() ? frames.get(index) : null;
      RectF destination = new RectF(index * cellWidth, 0, (index + 1) * cellWidth, height);
      if (frame == null) {
        canvas.drawRect(destination, shadePaint);
      } else {
        drawCenterCrop(canvas, frame, destination);
      }
    }
    float left = startFraction * width;
    float right = endFraction * width;
    canvas.drawRect(0, 0, left, height, shadePaint);
    canvas.drawRect(right, 0, width, height, shadePaint);
    float halfStroke = borderPaint.getStrokeWidth() / 2f;
    canvas.drawRect(left + halfStroke, halfStroke, right - halfStroke, height - halfStroke,
        borderPaint);
    float handleWidth = dp(12);
    canvas.drawRoundRect(new RectF(left, 0, left + handleWidth, height),
        dp(4), dp(4), handlePaint);
    canvas.drawRoundRect(new RectF(right - handleWidth, 0, right, height),
        dp(4), dp(4), handlePaint);
  }

  private void drawCenterCrop(Canvas canvas, Bitmap bitmap, RectF destination) {
    float sourceRatio = bitmap.getWidth() / (float) bitmap.getHeight();
    float targetRatio = destination.width() / destination.height();
    Rect source;
    if (sourceRatio > targetRatio) {
      int shownWidth = Math.round(bitmap.getHeight() * targetRatio);
      int left = (bitmap.getWidth() - shownWidth) / 2;
      source = new Rect(left, 0, left + shownWidth, bitmap.getHeight());
    } else {
      int shownHeight = Math.round(bitmap.getWidth() / targetRatio);
      int top = (bitmap.getHeight() - shownHeight) / 2;
      source = new Rect(0, top, bitmap.getWidth(), top + shownHeight);
    }
    canvas.drawBitmap(bitmap, source, destination, imagePaint);
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    float width = getWidth();
    if (width <= 0) return false;
    float x = clamp(event.getX(), 0f, width);
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
      float leftDistance = Math.abs(x - startFraction * width);
      float rightDistance = Math.abs(x - endFraction * width);
      draggingHandle = leftDistance <= rightDistance ? 1 : 2;
      getParent().requestDisallowInterceptTouchEvent(true);
    } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
      if (draggingHandle == 1) {
        startFraction = clamp(x / width, 0f, endFraction - MIN_RANGE);
      } else if (draggingHandle == 2) {
        endFraction = clamp(x / width, startFraction + MIN_RANGE, 1f);
      }
      notifyRange(false);
      invalidate();
    } else if (event.getActionMasked() == MotionEvent.ACTION_UP
        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
      notifyRange(true);
      draggingHandle = 0;
      getParent().requestDisallowInterceptTouchEvent(false);
    }
    return true;
  }

  private void notifyRange(boolean finished) {
    if (listener != null) listener.onRangeChanged(startFraction, endFraction, finished);
  }

  private float dp(float value) {
    return value * getResources().getDisplayMetrics().density;
  }

  private static float clamp(float value, float minimum, float maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  interface Listener {
    void onRangeChanged(float startFraction, float endFraction, boolean finished);
  }
}
