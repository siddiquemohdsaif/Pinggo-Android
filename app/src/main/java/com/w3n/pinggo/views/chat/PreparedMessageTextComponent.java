package com.w3n.pinggo.views.chat;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Paint;
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
  private String highlight = "";
  private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

  PreparedMessageTextComponent(String id) {
    this.id = id;
    highlightPaint.setColor(0x66FFE066);
  }

  PreparedMessageTextComponent bind(RectF region, StaticLayout value, boolean show) {
    bounds.set(region);
    layout = value;
    visible = show && value != null;
    return this;
  }

  PreparedMessageTextComponent setHighlight(String value) {
    highlight = value == null ? "" : value.trim().toLowerCase(java.util.Locale.US);
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
    if (!highlight.isEmpty()) {
      String text = layout.getText().toString();
      String lower = text.toLowerCase(java.util.Locale.US);
      int from = 0;
      while ((from = lower.indexOf(highlight, from)) >= 0) {
        int end = from + highlight.length();
        int firstLine = layout.getLineForOffset(from);
        int lastLine = layout.getLineForOffset(Math.max(from, end - 1));
        for (int line = firstLine; line <= lastLine; line++) {
          int lineStart = Math.max(from, layout.getLineStart(line));
          int lineEnd = Math.min(end, layout.getLineEnd(line));
          canvas.drawRect(layout.getPrimaryHorizontal(lineStart), layout.getLineTop(line),
              layout.getPrimaryHorizontal(lineEnd), layout.getLineBottom(line), highlightPaint);
        }
        from = end;
      }
    }
    layout.draw(canvas);
    canvas.restore();
  }
}
