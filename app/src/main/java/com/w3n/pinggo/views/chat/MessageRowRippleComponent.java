package com.w3n.pinggo.views.chat;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import com.ogfa.nativeviews.component.Component;
import com.ogfa.nativeviews.component.ComponentHost;

/** Touch-origin ripple overlay scoped to a message bubble or its inner preview. */
final class MessageRowRippleComponent implements Component {
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static final long EXPAND_MS = 260L;
  private static final long FADE_MS = 150L;
  private static final int RIPPLE_COLOR = 0xFFA9B3BB;
  private static final int RIPPLE_ALPHA = 0x40;
  private final String id;
  private final RectF bounds = new RectF();
  private final RectF hitBounds = new RectF();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path clip = new Path();
  private ComponentHost host;
  private Runnable clickAction;
  private Runnable longClickAction;
  private float originX;
  private float originY;
  private float maximumRadius;
  private float cornerRadius;
  private long startedAt;
  private long fadeStartedAt;
  private boolean pressed;
  private boolean longPressed;
  private boolean drawingRipple;
  private boolean fading;
  private boolean released;

  private final Runnable triggerLongClick = () -> {
    if (!pressed || released || longClickAction == null) return;
    longPressed = true;
    longClickAction.run();
  };

  private final Runnable animate = new Runnable() {
    @Override public void run() {
      if (released || !drawingRipple) return;
      invalidate();
      if (fading && SystemClock.uptimeMillis() - fadeStartedAt >= FADE_MS) {
        drawingRipple = false;
        fading = false;
        invalidate();
        return;
      }
      MAIN.postDelayed(this, 16L);
    }
  };

  MessageRowRippleComponent(String id) {
    this.id = id;
    paint.setColor(RIPPLE_COLOR);
  }

  MessageRowRippleComponent bind(
      RectF visualRegion, RectF touchRegion, float radius,
      Runnable clickAction, Runnable longClickAction) {
    bounds.set(visualRegion);
    hitBounds.set(touchRegion);
    cornerRadius = Math.max(0f, Math.min(radius, visualRegion.height() / 2f));
    this.clickAction = clickAction;
    this.longClickAction = longClickAction;
    return this;
  }

  @Override public String getId() { return id; }
  @Override public RectF getBounds() { return bounds; }
  @Override public boolean isVisible() { return !released; }
  @Override public boolean isEnabled() {
    return !released && clickAction != null && !bounds.isEmpty() && !hitBounds.isEmpty();
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!isEnabled()) return false;
    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      if (!hitBounds.contains(event.getX(), event.getY())) return false;
      pressed = true;
      longPressed = false;
      fading = false;
      drawingRipple = true;
      originX = event.getX();
      originY = event.getY();
      maximumRadius = farthestCornerRadius(originX, originY);
      startedAt = SystemClock.uptimeMillis();
      MAIN.removeCallbacks(triggerLongClick);
      MAIN.removeCallbacks(animate);
      MAIN.postDelayed(triggerLongClick, ViewConfiguration.getLongPressTimeout());
      MAIN.post(animate);
      return true;
    }
    if (action == MotionEvent.ACTION_MOVE) {
      if (!hitBounds.contains(event.getX(), event.getY())) {
        pressed = false;
        MAIN.removeCallbacks(triggerLongClick);
        startFade();
      }
      return true;
    }
    if (action == MotionEvent.ACTION_UP) {
      boolean activate = pressed && hitBounds.contains(event.getX(), event.getY());
      MAIN.removeCallbacks(triggerLongClick);
      pressed = false;
      if (activate && !longPressed) clickAction.run();
      longPressed = false;
      startFade();
      return true;
    }
    if (action == MotionEvent.ACTION_CANCEL) {
      MAIN.removeCallbacks(triggerLongClick);
      pressed = false;
      longPressed = false;
      startFade();
      return true;
    }
    return false;
  }

  @Override
  public void draw(Canvas canvas) {
    if (!drawingRipple || bounds.isEmpty()) return;
    long now = SystemClock.uptimeMillis();
    float expansion = Math.min(1f, (now - startedAt) / (float) EXPAND_MS);
    expansion = 1f - (1f - expansion) * (1f - expansion);
    float alpha = fading ? Math.max(0f, 1f - (now - fadeStartedAt) / (float) FADE_MS) : 1f;
    paint.setAlpha(Math.round(RIPPLE_ALPHA * alpha));
    clip.reset();
    clip.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW);
    canvas.save();
    canvas.clipPath(clip);
    canvas.drawCircle(originX, originY, maximumRadius * expansion, paint);
    canvas.restore();
  }

  @Override public void attach(ComponentHost owner) { host = owner; }

  @Override
  public void release() {
    released = true;
    pressed = false;
    drawingRipple = false;
    clickAction = null;
    longClickAction = null;
    MAIN.removeCallbacks(triggerLongClick);
    MAIN.removeCallbacks(animate);
    host = null;
  }

  private void startFade() {
    if (!drawingRipple) return;
    fading = true;
    fadeStartedAt = SystemClock.uptimeMillis();
    MAIN.removeCallbacks(animate);
    MAIN.post(animate);
  }

  private float farthestCornerRadius(float x, float y) {
    float horizontal = Math.max(x - bounds.left, bounds.right - x);
    float vertical = Math.max(y - bounds.top, bounds.bottom - y);
    return (float) Math.hypot(horizontal, vertical);
  }

  private void invalidate() {
    if (host != null) host.invalidateComponent();
  }
}
