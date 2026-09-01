package com.w3n.pinggo.views.chat;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import com.ogfa.nativeviews.component.Component;
import com.ogfa.nativeviews.component.ComponentHost;

/** Lightweight WhatsApp-style recording indicator: live dot, timer, and waveform. */
final class AudioRecordingComponent implements Component {
  private static final int RECORDING_RED = 0xFFE53935;
  private static final int WAVE_COLOR = 0xFF8793A3;
  private static final int MAX_SAMPLES = 72;
  private final String id;
  private final RectF bounds = new RectF();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float[] samples = new float[MAX_SAMPLES];
  private ComponentHost host;
  private long elapsedMs;
  private int sampleStart;
  private int sampleCount;
  private float smoothedLevel;
  private boolean visible;
  private boolean released;

  AudioRecordingComponent(String id) {
    this.id = id;
  }

  AudioRecordingComponent bind(RectF region, long elapsed) {
    bounds.set(region);
    elapsedMs = Math.max(0L, elapsed);
    visible = true;
    invalidate();
    return this;
  }

  AudioRecordingComponent setRecordingSample(long elapsed, int amplitude) {
    elapsedMs = Math.max(0L, elapsed);
    appendSample(normalizeAmplitude(amplitude));
    invalidate();
    return this;
  }

  AudioRecordingComponent hide() {
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
    float unit = Math.max(1f, bounds.height() / 118f);
    float centerY = bounds.centerY();
    paint.setColor(RECORDING_RED);
    canvas.drawCircle(bounds.left + 13f * unit, centerY, 8f * unit, paint);

    paint.setColor(WAVE_COLOR);
    paint.setStrokeWidth(Math.max(2f, 3f * unit));
    paint.setStrokeCap(Paint.Cap.ROUND);
    float waveLeft = bounds.left + 155f * unit;
    float waveRight = bounds.right - 8f * unit;
    float gap = 13f * unit;
    int barCount = Math.max(1, (int) Math.floor((waveRight - waveLeft) / gap) + 1);
    int visibleSamples = Math.min(sampleCount, barCount);
    int emptyBars = barCount - visibleSamples;
    for (int index = 0; index < barCount; index++) {
      float x = waveLeft + index * gap;
      float level = index < emptyBars ? .07f : sampleAt(sampleCount - visibleSamples
          + index - emptyBars);
      float half = (5f + level * 31f) * unit;
      canvas.drawLine(x, centerY - half, x, centerY + half, paint);
    }
  }

  private void appendSample(float level) {
    float response = level > smoothedLevel ? .72f : .28f;
    smoothedLevel += (level - smoothedLevel) * response;
    float value = Math.max(.07f, Math.min(1f, smoothedLevel));
    if (sampleCount < MAX_SAMPLES) {
      samples[(sampleStart + sampleCount) % MAX_SAMPLES] = value;
      sampleCount++;
    } else {
      samples[sampleStart] = value;
      sampleStart = (sampleStart + 1) % MAX_SAMPLES;
    }
  }

  private float sampleAt(int index) {
    if (index < 0 || index >= sampleCount) return .07f;
    return samples[(sampleStart + index) % MAX_SAMPLES];
  }

  private static float normalizeAmplitude(int amplitude) {
    if (amplitude <= 0) return .07f;
    float logarithmic = (float) (Math.log10(1d + Math.min(32767, amplitude))
        / Math.log10(32768d));
    return Math.max(.07f, Math.min(1f, (logarithmic - .48f) / .52f));
  }

  private void invalidate() { if (host != null) host.invalidateComponent(); }
}
