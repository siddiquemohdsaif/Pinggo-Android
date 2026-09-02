package com.w3n.pinggo.views.chat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.view.MotionEvent;
import com.ogfa.nativeviews.component.Component;
import com.ogfa.nativeviews.component.ComponentHost;

/** One draw node for timestamp, delivery state, and pin state. */
final class MessageMetadataComponent implements Component {
  private final String id;
  private final RectF bounds = new RectF();
  private final RectF deliveryBounds = new RectF();
  private final RectF pinnedBounds = new RectF();
  private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private ComponentHost host;
  private StaticLayout timeLayout;
  private Bitmap delivery;
  private Bitmap pinned;
  private boolean showDelivery;
  private boolean showPinned;

  MessageMetadataComponent(String id) { this.id = id; }

  MessageMetadataComponent bind(RectF timeRegion, StaticLayout time, Bitmap deliveryBitmap,
      RectF deliveryRegion, boolean deliveryVisible, Bitmap pinnedBitmap,
      RectF pinnedRegion, boolean pinnedVisible) {
    bounds.set(timeRegion);
    timeLayout = time;
    delivery = deliveryBitmap;
    deliveryBounds.set(deliveryRegion);
    showDelivery = deliveryVisible;
    pinned = pinnedBitmap;
    pinnedBounds.set(pinnedRegion);
    showPinned = pinnedVisible;
    return this;
  }

  @Override public String getId() { return id; }
  @Override public RectF getBounds() { return bounds; }
  @Override public boolean isVisible() { return timeLayout != null; }
  @Override public boolean isEnabled() { return false; }
  @Override public boolean onTouchEvent(MotionEvent event) { return false; }
  @Override public void attach(ComponentHost owner) { host = owner; }
  @Override public void release() {
    host = null; timeLayout = null; delivery = null; pinned = null;
  }

  @Override public void draw(Canvas canvas) {
    if (timeLayout == null) return;
    canvas.save();
    canvas.translate(bounds.left, bounds.top);
    timeLayout.draw(canvas);
    canvas.restore();
    if (showDelivery && delivery != null && !delivery.isRecycled()) {
      canvas.drawBitmap(delivery, null, deliveryBounds, bitmapPaint);
    }
    if (showPinned && pinned != null && !pinned.isRecycled()) {
      canvas.drawBitmap(pinned, null, pinnedBounds, bitmapPaint);
    }
  }
}
