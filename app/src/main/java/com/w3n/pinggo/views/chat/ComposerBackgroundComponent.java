package com.w3n.pinggo.views.chat;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import com.ogfa.nativeviews.component.Component;
import com.ogfa.nativeviews.component.ComponentHost;

/** Directly drawn message-editor background without a bitmap allocation. */
final class ComposerBackgroundComponent implements Component {
  private static final int FILL = 0xFFF9FBFE;
  private static final int BORDER = 0xFFD5DFEB;

  private final String id;
  private final RectF bounds = new RectF();
  private final RectF borderBounds = new RectF();
  private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float cornerRadius;
  private boolean released;

  ComposerBackgroundComponent(String id, RectF bounds, float cornerRadius, float borderWidth) {
    this.id = id;
    this.bounds.set(bounds);
    this.cornerRadius = Math.max(0f, cornerRadius);
    fillPaint.setColor(FILL);
    fillPaint.setStyle(Paint.Style.FILL);
    borderPaint.setColor(BORDER);
    borderPaint.setStyle(Paint.Style.STROKE);
    borderPaint.setStrokeWidth(Math.max(0f, borderWidth));
    borderBounds.set(bounds);
    borderBounds.inset(borderPaint.getStrokeWidth() / 2f,
        borderPaint.getStrokeWidth() / 2f);
  }

  @Override public String getId() { return id; }
  @Override public RectF getBounds() { return bounds; }

  ComposerBackgroundComponent setBounds(RectF nextBounds) {
    bounds.set(nextBounds);
    borderBounds.set(nextBounds);
    borderBounds.inset(borderPaint.getStrokeWidth() / 2f,
        borderPaint.getStrokeWidth() / 2f);
    return this;
  }

  @Override public void draw(Canvas canvas) {
    if (!released && !bounds.isEmpty()) {
      canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, fillPaint);
      float inset = borderPaint.getStrokeWidth() / 2f;
      float borderRadius = Math.max(0f, cornerRadius - inset);
      canvas.drawRoundRect(borderBounds, borderRadius, borderRadius, borderPaint);
    }
  }

  @Override public boolean onTouchEvent(MotionEvent event) { return false; }
  @Override public boolean isVisible() { return !released; }
  @Override public boolean isEnabled() { return false; }
  @Override public void attach(ComponentHost host) { }

  @Override public void release() {
    released = true;
  }
}
