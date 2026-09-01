package com.w3n.pinggo.views.chat;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import com.ogfa.nativeviews.component.Component;
import com.ogfa.nativeviews.component.ComponentHost;

/** Rounded inner surface behind the content of a forwarded text message. */
final class ForwardedMessagePanelComponent implements Component {
  private static final int PANEL_COLOR = 0xFFF8F9FA;
  private final String id;
  private final float figmaScale;
  private final RectF bounds = new RectF();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private ComponentHost host;
  private boolean visible;
  private boolean released;

  ForwardedMessagePanelComponent(String id, float figmaScale) {
    this.id = id;
    this.figmaScale = Math.max(.01f, figmaScale);
    paint.setColor(PANEL_COLOR);
  }

  ForwardedMessagePanelComponent bind(RectF region) {
    bounds.set(region);
    visible = true;
    invalidate();
    return this;
  }

  ForwardedMessagePanelComponent hide() {
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
    float radius = 44f * figmaScale;
    canvas.drawRoundRect(bounds, radius, radius, paint);
  }

  private void invalidate() { if (host != null) host.invalidateComponent(); }
}
