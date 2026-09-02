package com.w3n.pinggo.views.chat;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import com.ogfa.nativeviews.component.Component;
import com.ogfa.nativeviews.component.ComponentHost;

/** Lightweight, directly drawn chat bubble used by recycled message rows. */
final class MessageBubbleComponent implements Component {
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static final int INCOMING_FILL = 0xFFFEFEFE;
  private static final int INCOMING_BORDER = 0xFFE4EBF3;
  private static final int OUTGOING_FILL = 0xFFE2F6FE;
  private static final int OUTGOING_BORDER = 0xFFD6EBF4;

  private final String id;
  private final float figmaScale;
  private final RectF bounds = new RectF();
  private final RectF hitBounds = new RectF();
  private final Path path = new Path();
  private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
  private ComponentHost host;
  private boolean outgoing;
  private boolean tailEnabled = true;
  private float shapeScale = 1f;
  private boolean pathDirty = true;
  private boolean pressed;
  private boolean longPressed;
  private boolean released;
  private boolean visible = true;
  private Runnable clickAction;
  private Runnable longClickAction;
  private final Runnable triggerLongClick = () -> {
    if (!pressed || released || longClickAction == null) return;
    longPressed = true;
    longClickAction.run();
  };

  MessageBubbleComponent(String id, RectF initialBounds, float figmaScale, boolean outgoing) {
    if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Bubble id required.");
    this.id = id;
    this.figmaScale = Math.max(.01f, figmaScale);
    this.outgoing = outgoing;
    bounds.set(initialBounds);
    hitBounds.set(initialBounds);
    fill.setStyle(Paint.Style.FILL);
    border.setStyle(Paint.Style.STROKE);
    border.setStrokeWidth(3f);
    border.setStrokeJoin(Paint.Join.ROUND);
    border.setStrokeCap(Paint.Cap.ROUND);
    updateColors();
  }

  MessageBubbleComponent setRegion(RectF region) {
    ensureActive();
    if (region == null) throw new IllegalArgumentException("Bubble bounds required.");
    return setRegion(region.left, region.top, region.right, region.bottom);
  }

  MessageBubbleComponent show() { visible = true; return this; }
  MessageBubbleComponent hide() { visible = false; return this; }

  MessageBubbleComponent setRegion(float left, float top, float right, float bottom) {
    ensureActive();
    if (!sameBounds(bounds, left, top, right, bottom)) {
      bounds.set(left, top, right, bottom);
      pathDirty = true;
      invalidate();
    }
    return this;
  }

  MessageBubbleComponent setHitRegion(RectF region) {
    ensureActive();
    if (region == null) hitBounds.set(bounds);
    else hitBounds.set(region);
    return this;
  }

  MessageBubbleComponent setClickAction(Runnable action) {
    ensureActive();
    clickAction = action;
    return this;
  }

  MessageBubbleComponent setLongClickAction(Runnable action) {
    ensureActive();
    longClickAction = action;
    return this;
  }

  MessageBubbleComponent setOutgoing(boolean value) {
    ensureActive();
    if (outgoing != value) {
      outgoing = value;
      updateColors();
      pathDirty = true;
      invalidate();
    }
    return this;
  }

  MessageBubbleComponent setTailEnabled(boolean value) {
    ensureActive();
    if (tailEnabled != value) {
      tailEnabled = value;
      pathDirty = true;
      invalidate();
    }
    return this;
  }

  MessageBubbleComponent setShapeScale(float value) {
    ensureActive();
    float next = Math.max(.1f, value);
    if (Math.abs(shapeScale - next) > .001f) {
      shapeScale = next;
      pathDirty = true;
      invalidate();
    }
    return this;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public RectF getBounds() {
    return bounds;
  }

  @Override
  public void draw(Canvas canvas) {
    if (released || bounds.isEmpty()) return;
    if (pathDirty) rebuildPath();
    canvas.drawPath(path, fill);
    canvas.drawPath(path, border);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!isEnabled()) return false;
    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      pressed = hitBounds.contains(event.getX(), event.getY());
      longPressed = false;
      MAIN.removeCallbacks(triggerLongClick);
      if (pressed && longClickAction != null) {
        MAIN.postDelayed(triggerLongClick, ViewConfiguration.getLongPressTimeout());
      }
      return pressed;
    }
    if (action == MotionEvent.ACTION_MOVE) {
      pressed &= hitBounds.contains(event.getX(), event.getY());
      if (!pressed) MAIN.removeCallbacks(triggerLongClick);
      return true;
    }
    if (action == MotionEvent.ACTION_UP) {
      boolean activate = pressed && hitBounds.contains(event.getX(), event.getY());
      MAIN.removeCallbacks(triggerLongClick);
      pressed = false;
      if (activate && !longPressed) clickAction.run();
      longPressed = false;
      return true;
    }
    if (action == MotionEvent.ACTION_CANCEL) {
      MAIN.removeCallbacks(triggerLongClick);
      pressed = false;
      longPressed = false;
      return true;
    }
    return false;
  }

  @Override
  public boolean isVisible() {
    return !released && visible;
  }

  @Override
  public boolean isEnabled() {
    return !released && clickAction != null && !hitBounds.isEmpty();
  }

  @Override
  public void attach(ComponentHost owner) {
    ensureActive();
    if (host != null && host != owner) {
      throw new IllegalStateException("Bubble already belongs to another host.");
    }
    host = owner;
  }

  @Override
  public void release() {
    released = true;
    MAIN.removeCallbacks(triggerLongClick);
    pressed = false;
    longPressed = false;
    clickAction = null;
    longClickAction = null;
    host = null;
    path.reset();
  }

  private void rebuildPath() {
    pathDirty = false;
    path.reset();
    float strokeHalf = border.getStrokeWidth() / 2f;
    float tailWidth = px(11f);
    float tailHeight = px(19.25f);
    float top = bounds.top + strokeHalf;
    float bottom = Math.max(top + 1f, bounds.bottom - strokeHalf);
    float left = outgoing ? bounds.left + strokeHalf : bounds.left + tailWidth + strokeHalf;
    float right = outgoing ? bounds.right - tailWidth - strokeHalf : bounds.right - strokeHalf;
    if (!tailEnabled) {
      path.addRoundRect(
          bounds.left + strokeHalf,
          bounds.top + strokeHalf,
          bounds.right - strokeHalf,
          bounds.bottom - strokeHalf,
          px(44f), px(44f), Path.Direction.CW);
      return;
    }
    float radius = Math.min(px(44f) * shapeScale, Math.max(1f, (bottom - top) / 2f));
    float tailBottom = Math.min(bottom, top + tailHeight);

    if (outgoing) {
      path.moveTo(left + radius, top);
      path.lineTo(right, top);
      path.cubicTo(
          right + tailWidth * .30f,
          top + tailHeight * .06f,
          bounds.right - strokeHalf,
          top + tailHeight * .12f,
          bounds.right - strokeHalf,
          top + tailHeight * .28f);
      path.cubicTo(
          bounds.right - strokeHalf,
          top + tailHeight * .48f,
          right + tailWidth * .22f,
          top + tailHeight * .78f,
          right,
          tailBottom);
      path.lineTo(right, bottom - radius);
      path.quadTo(right, bottom, right - radius, bottom);
      path.lineTo(left + radius, bottom);
      path.quadTo(left, bottom, left, bottom - radius);
      path.lineTo(left, top + radius);
      path.quadTo(left, top, left + radius, top);
    } else {
      path.moveTo(left, top);
      path.cubicTo(
          left - tailWidth * .30f,
          top + tailHeight * .06f,
          bounds.left + strokeHalf,
          top + tailHeight * .12f,
          bounds.left + strokeHalf,
          top + tailHeight * .28f);
      path.cubicTo(
          bounds.left + strokeHalf,
          top + tailHeight * .48f,
          left - tailWidth * .22f,
          top + tailHeight * .78f,
          left,
          tailBottom);
      path.lineTo(left, bottom - radius);
      path.quadTo(left, bottom, left + radius, bottom);
      path.lineTo(right - radius, bottom);
      path.quadTo(right, bottom, right, bottom - radius);
      path.lineTo(right, top + radius);
      path.quadTo(right, top, right - radius, top);
    }
    path.close();
  }

  private void updateColors() {
    fill.setColor(outgoing ? OUTGOING_FILL : INCOMING_FILL);
    border.setColor(outgoing ? OUTGOING_BORDER : INCOMING_BORDER);
  }

  private void invalidate() {
    if (host != null) host.invalidateComponent();
  }

  private float px(float value) {
    return value * figmaScale;
  }

  private void ensureActive() {
    if (released) throw new IllegalStateException("Bubble has been released.");
  }

  private static boolean sameBounds(
      RectF first, float left, float top, float right, float bottom) {
    return Math.abs(first.left - left) < .5f
        && Math.abs(first.top - top) < .5f
        && Math.abs(first.right - right) < .5f
        && Math.abs(first.bottom - bottom) < .5f;
  }
}
