package com.w3n.pinggo.views.chat;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.view.MotionEvent;
import com.ogfa.nativeviews.component.Component;
import com.ogfa.nativeviews.component.ComponentHost;

/** Draws a background-prepared message layout without invoking native Text setters. */
final class PreparedMessageTextComponent implements Component {
  private final String id;
  private final RectF bounds = new RectF();
  private ComponentHost host;
  private StaticLayout layout;
  private boolean visible;

  PreparedMessageTextComponent(String id) { this.id = id; }

  PreparedMessageTextComponent bind(RectF region, StaticLayout value, boolean show) {
    bounds.set(region);
    layout = value;
    visible = show && value != null;
    return this;
  }

  @Override public String getId() { return id; }
  @Override public RectF getBounds() { return bounds; }
  @Override public boolean isVisible() { return visible; }
  @Override public boolean isEnabled() { return false; }
  @Override public boolean onTouchEvent(MotionEvent event) { return false; }
  @Override public void attach(ComponentHost owner) { host = owner; }
  @Override public void release() { host = null; layout = null; visible = false; }

  @Override public void draw(Canvas canvas) {
    if (!visible || layout == null || bounds.isEmpty()) return;
    canvas.save();
    canvas.translate(bounds.left, bounds.top);
    layout.draw(canvas);
    canvas.restore();
  }
}
