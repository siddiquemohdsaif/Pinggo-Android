package com.w3n.pinggo.views.home;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import com.ogfa.nativeviews.component.Component;
import com.ogfa.nativeviews.component.ComponentHost;

/** Touch-origin ripple overlay used by recycled rows in the chat list. */
final class ChatRowRippleComponent implements Component {
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static final long EXPAND_DURATION_MS = 260L;
  private static final long FADE_DURATION_MS = 150L;
  private static final int RIPPLE_COLOR = 0xFFE9EDF0;
  private final String id;
  private final RectF bounds = new RectF();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private ComponentHost host;
  private Runnable clickAction;
  private Runnable longClickAction;
  private float originX;
  private float originY;
  private float maximumRadius;
  private long startedAt;
  private long fadeStartedAt;
  private boolean pressed;
  private boolean longPressed;
  private boolean fading;
  private boolean visible;
  private boolean released;
  private final Runnable triggerLongClick = () -> {
    if (!pressed || released || longClickAction == null) return;
    longPressed = true;
    longClickAction.run();
  };
  private final Runnable animate = new Runnable() {
    @Override public void run() {
      if (released || !visible) return;
      invalidate();
      if (fading && SystemClock.uptimeMillis() - fadeStartedAt >= FADE_DURATION_MS) {
        visible = false;
        fading = false;
        invalidate();
        return;
      }
      MAIN.postDelayed(this, 16L);
    }
  };

  ChatRowRippleComponent(String id, RectF initialBounds) {
    this.id = id;
    bounds.set(initialBounds);
    paint.setColor(RIPPLE_COLOR);
  }

  ChatRowRippleComponent bind(RectF region, Runnable click, Runnable longClick) {
    bounds.set(region);
    clickAction = click;
    longClickAction = longClick;
    return this;
  }

  @Override public String getId() { return id; }
  @Override public RectF getBounds() { return bounds; }
  @Override public boolean isVisible() { return !released; }
  @Override public boolean isEnabled() { return !released && clickAction != null; }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!isEnabled()) return false;
    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      if (!bounds.contains(event.getX(), event.getY())) return false;
      pressed = true;
      longPressed = false;
      fading = false;
      visible = true;
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
      if (!bounds.contains(event.getX(), event.getY())) {
        pressed = false;
        MAIN.removeCallbacks(triggerLongClick);
        startFade();
      }
      return true;
    }
    if (action == MotionEvent.ACTION_UP) {
      boolean activate = pressed && bounds.contains(event.getX(), event.getY());
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
    if (!visible || bounds.isEmpty()) return;
    long now = SystemClock.uptimeMillis();
    float expansion = Math.min(1f, (now - startedAt) / (float) EXPAND_DURATION_MS);
    expansion = 1f - (1f - expansion) * (1f - expansion);
    float alpha = 1f;
    if (fading) alpha = Math.max(0f, 1f - (now - fadeStartedAt) / (float) FADE_DURATION_MS);
    paint.setAlpha(Math.round(255f * alpha));
    canvas.save();
    canvas.clipRect(bounds);
    canvas.drawCircle(originX, originY, maximumRadius * expansion, paint);
    canvas.restore();
  }

  @Override public void attach(ComponentHost owner) { host = owner; }

  @Override
  public void release() {
    released = true;
    pressed = false;
    visible = false;
    clickAction = null;
    longClickAction = null;
    MAIN.removeCallbacks(triggerLongClick);
    MAIN.removeCallbacks(animate);
    host = null;
  }

  private void startFade() {
    if (!visible) return;
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
