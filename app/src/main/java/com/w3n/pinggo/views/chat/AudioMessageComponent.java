package com.w3n.pinggo.views.chat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import com.ogfa.nativeviews.component.Component;
import com.ogfa.nativeviews.component.ComponentHost;
import java.util.Locale;

/** Tail-less voice-message content with avatar, play/stop control, waveform, and duration. */
final class AudioMessageComponent implements Component {
  interface Listener { void onToggle(String messageId); }

  private static final int ACTIVE = 0xFF019CC4;
  private static final int INACTIVE = 0xFFA8B3C0;
  private static final int TIME = 0xFF5C6B85;
  private static final float[] WAVE = {
      .32f, .48f, .68f, .88f, .62f, .40f, .76f, 1f, .72f, .50f, .36f,
      .58f, .82f, .66f, .44f, .74f, .94f, .56f, .38f, .64f, .86f, .70f
  };
  private final String id;
  private final Listener listener;
  private final RectF bounds = new RectF();
  private final RectF controlBounds = new RectF();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private ComponentHost host;
  private Bitmap avatar;
  private String messageId = "";
  private long progressMs;
  private long durationMs;
  private float[] waveform = WAVE;
  private boolean playing;
  private boolean pressed;
  private boolean visible;
  private boolean released;

  AudioMessageComponent(String id, Listener listener) {
    this.id = id;
    this.listener = listener;
  }

  AudioMessageComponent bind(
      RectF region, Bitmap profile, String key, boolean isPlaying,
      long progress, long duration, float[] speechWaveform) {
    bounds.set(region);
    avatar = profile;
    messageId = key == null ? "" : key;
    playing = isPlaying;
    progressMs = Math.max(0L, progress);
    durationMs = Math.max(0L, duration);
    waveform = speechWaveform == null || speechWaveform.length == 0
        ? WAVE : speechWaveform;
    visible = true;
    updateControlBounds();
    invalidate();
    return this;
  }

  AudioMessageComponent hide() {
    visible = false;
    pressed = false;
    invalidate();
    return this;
  }

  @Override public String getId() { return id; }
  @Override public RectF getBounds() { return bounds; }
  @Override public boolean isVisible() { return visible && !released; }
  @Override public boolean isEnabled() { return isVisible(); }
  @Override public void attach(ComponentHost owner) { host = owner; }
  @Override public void release() { released = true; visible = false; host = null; avatar = null; }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!isVisible()) return false;
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
      pressed = controlBounds.contains(event.getX(), event.getY());
      return bounds.contains(event.getX(), event.getY());
    }
    if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
      pressed = false;
      return true;
    }
    if (event.getActionMasked() == MotionEvent.ACTION_UP) {
      boolean activate = pressed && controlBounds.contains(event.getX(), event.getY());
      pressed = false;
      if (activate && listener != null) listener.onToggle(messageId);
      return true;
    }
    return pressed;
  }

  @Override
  public void draw(Canvas canvas) {
    if (!isVisible() || bounds.isEmpty()) return;
    float scale = bounds.width() / 620f;
    float centerY = bounds.top + 69f * scale;
    float avatarRadius = 47f * scale;
    float avatarCenterX = bounds.left + 68f * scale;
    if (avatar != null && !avatar.isRecycled()) {
      canvas.save();
      Path avatarClip = new Path();
      avatarClip.addCircle(avatarCenterX, centerY, avatarRadius, Path.Direction.CW);
      canvas.clipPath(avatarClip);
      canvas.drawBitmap(avatar, null,
          new RectF(avatarCenterX - avatarRadius, centerY - avatarRadius,
              avatarCenterX + avatarRadius, centerY + avatarRadius), paint);
      canvas.restore();
    } else {
      paint.setColor(0xFFD9F1F7);
      canvas.drawCircle(avatarCenterX, centerY, avatarRadius, paint);
    }

    float controlCenterX = bounds.left + 164f * scale;
    paint.setColor(ACTIVE);
    if (playing) {
      float half = 15f * scale;
      canvas.drawRoundRect(new RectF(controlCenterX - half, centerY - half,
          controlCenterX + half, centerY + half), 3f * scale, 3f * scale, paint);
    } else {
      Path triangle = new Path();
      triangle.moveTo(controlCenterX - 13f * scale, centerY - 22f * scale);
      triangle.lineTo(controlCenterX + 24f * scale, centerY);
      triangle.lineTo(controlCenterX - 13f * scale, centerY + 22f * scale);
      triangle.close();
      canvas.drawPath(triangle, paint);
    }

    float waveLeft = bounds.left + 226f * scale;
    float waveRight = bounds.right - 25f * scale;
    float progress = durationMs <= 0L ? 0f : Math.min(1f, progressMs / (float) durationMs);
    float activeEdge = waveLeft + (waveRight - waveLeft) * progress;
    float gap = Math.max(5f * scale,
        (waveRight - waveLeft) / Math.max(1, waveform.length - 1));
    paint.setStrokeWidth(Math.max(2f, 3f * scale));
    paint.setStrokeCap(Paint.Cap.ROUND);
    int index = 0;
    for (float x = waveLeft; x <= waveRight + .5f && index < waveform.length; x += gap) {
      paint.setColor(x <= activeEdge ? ACTIVE : INACTIVE);
      float half = (8f + 25f * waveform[index]) * scale;
      canvas.drawLine(x, centerY - half, x, centerY + half, paint);
      index++;
    }

    paint.setColor(TIME);
    paint.setTextSize(25f * scale);
    paint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
    long shown = playing && progressMs > 0L ? progressMs : durationMs;
    canvas.drawText(formatDuration(shown), bounds.left + 218f * scale,
        bounds.bottom - 17f * scale, paint);
  }

  private void updateControlBounds() {
    float scale = bounds.width() / 620f;
    float centerX = bounds.left + 164f * scale;
    float centerY = bounds.top + 69f * scale;
    controlBounds.set(centerX - 42f * scale, centerY - 42f * scale,
        centerX + 42f * scale, centerY + 42f * scale);
  }

  private static String formatDuration(long milliseconds) {
    long seconds = Math.max(0L, milliseconds / 1000L);
    return String.format(Locale.US, "%d:%02d", seconds / 60L, seconds % 60L);
  }

  private void invalidate() { if (host != null) host.invalidateComponent(); }
}
