package com.w3n.pinggo.views.home;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
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
  private static final String RIPPLE_TIMING_TAG = "RippleTiming";
  private final String id;
  private final RectF bounds = new RectF();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private ComponentHost host;
  private Runnable clickAction;
  private Runnable longClickAction;
  private Runnable pendingClickAction;
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
  private final Runnable dispatchPendingClick;
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
        dispatchPendingClick.run();
        return;
      }
      MAIN.postDelayed(this, 16L);
    }
  };

  ChatRowRippleComponent(String id, RectF initialBounds) {
    this.id = id;
    dispatchPendingClick = () -> {
      Runnable action = pendingClickAction;
      pendingClickAction = null;
      if (!released && action != null) {
        long now = SystemClock.uptimeMillis();
        Log.i(RIPPLE_TIMING_TAG, "component=chat_row event=action_dispatch id=" + this.id
            + " uptimeMs=" + now
            + " elapsedFromRippleStartMs=" + Math.max(0L, now - startedAt)
            + " elapsedFromFadeStartMs=" + Math.max(0L, now - fadeStartedAt));
        action.run();
      }
    };
    bounds.set(initialBounds);
    paint.setColor(RIPPLE_COLOR);
  }

  ChatRowRippleComponent bind(RectF region, Runnable click, Runnable longClick) {
    cancelPendingClick();
    bounds.set(region);
    clickAction = click;
    longClickAction = longClick;
    return this;
  }

  @Override public String getId() { return id; }
  @Override public RectF getBounds() { return bounds; }
  @Override public boolean isVisible() { return !released; }
  @Override public boolean isEnabled() {
    return !released && clickAction != null && pendingClickAction == null;
  }

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
      Log.i(RIPPLE_TIMING_TAG, "component=chat_row event=ripple_start id=" + id
          + " uptimeMs=" + startedAt
          + " expandDurationMs=" + EXPAND_DURATION_MS
          + " fadeDurationMs=" + FADE_DURATION_MS);
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
        cancelPendingClick();
        startFade();
      }
      return true;
    }
    if (action == MotionEvent.ACTION_UP) {
      boolean activate = pressed && bounds.contains(event.getX(), event.getY());
      MAIN.removeCallbacks(triggerLongClick);
      pressed = false;
      if (activate && !longPressed) {
        pendingClickAction = clickAction;
        startFade();
      } else {
        startFade();
      }
      longPressed = false;
      return true;
    }
    if (action == MotionEvent.ACTION_CANCEL) {
      MAIN.removeCallbacks(triggerLongClick);
      pressed = false;
      longPressed = false;
      cancelPendingClick();
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
    cancelPendingClick();
    MAIN.removeCallbacks(triggerLongClick);
    MAIN.removeCallbacks(animate);
    host = null;
  }

  private void startFade() {
    if (!visible) return;
    fading = true;
    fadeStartedAt = SystemClock.uptimeMillis();
    if (pendingClickAction != null) {
      Log.i(RIPPLE_TIMING_TAG, "component=chat_row event=fade_start id=" + id
          + " uptimeMs=" + fadeStartedAt
          + " elapsedFromRippleStartMs=" + Math.max(0L, fadeStartedAt - startedAt));
    }
    MAIN.removeCallbacks(animate);
    MAIN.post(animate);
  }

  private void cancelPendingClick() {
    pendingClickAction = null;
    MAIN.removeCallbacks(dispatchPendingClick);
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
