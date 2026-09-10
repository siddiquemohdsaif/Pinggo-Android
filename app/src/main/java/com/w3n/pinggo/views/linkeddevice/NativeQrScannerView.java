package com.w3n.pinggo.views.linkeddevice;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.component.FigmaConfig;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

/** Native-AAR chrome drawn over Pinggo's own camera QR preview. */
public final class NativeQrScannerView extends View {
  public interface Listener {
    void onBack();
    void onToggleTorch();
    void onPermissionAction();
  }

  private final FigmaConfig config = new FigmaConfig(1080f);
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer chrome = layers.addLayer("qr_scanner_chrome");
  private final Listener listener;
  private final Bitmap clear = colorBitmap(Color.TRANSPARENT);
  private final Bitmap scrim = colorBitmap(0xB8000000);
  private final Bitmap control = colorBitmap(0xE61A2028);
  private final Bitmap accent = colorBitmap(0xFF019CC4);
  private Text statusText;
  private Button torchButton;
  private Button permissionButton;
  private String status = "Point your camera at the QR code";
  private boolean torchAvailable;
  private boolean torchOn;
  private boolean permissionActionVisible;
  private int topInset;
  private int bottomInset;

  public NativeQrScannerView(Context context, Listener listener) {
    super(context);
    this.listener = listener;
    setClickable(true);
    setBackgroundColor(Color.TRANSPARENT);
  }

  public void setInsets(int top, int bottom) {
    topInset = Math.max(0, top);
    bottomInset = Math.max(0, bottom);
    if (getWidth() > 0) build();
  }

  public void setStatus(String value, boolean showPermissionAction) {
    status = value == null ? "" : value;
    permissionActionVisible = showPermissionAction;
    updateState();
  }

  public void setTorchState(boolean available, boolean on) {
    torchAvailable = available;
    torchOn = on;
    if (torchButton != null) {
      torchButton.setLabel(on ? "Torch on" : "Torch")
          .setVisible(available).setEnabled(available);
    }
    invalidate();
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    if (width > 0 && height > 0) build();
  }

  private void build() {
    chrome.clear();
    float width = getWidth();
    float height = getHeight();
    float headerBottom = topInset + px(170f);
    float frameSize = Math.min(width - px(130f), px(760f));
    float frameLeft = (width - frameSize) / 2f;
    float frameTop = Math.max(headerBottom + px(165f), (height - frameSize) * .40f);
    float frameRight = frameLeft + frameSize;
    float frameBottom = frameTop + frameSize;

    addImage("top_scrim", scrim, new RectF(0, 0, width, frameTop));
    addImage("left_scrim", scrim, new RectF(0, frameTop, frameLeft, frameBottom));
    addImage("right_scrim", scrim, new RectF(frameRight, frameTop, width, frameBottom));
    addImage("bottom_scrim", scrim, new RectF(0, frameBottom, width, height));

    float line = Math.max(px(8f), 3f);
    float corner = px(125f);
    addImage("tl_h", accent, new RectF(frameLeft, frameTop, frameLeft + corner, frameTop + line));
    addImage("tl_v", accent, new RectF(frameLeft, frameTop, frameLeft + line, frameTop + corner));
    addImage("tr_h", accent, new RectF(frameRight - corner, frameTop, frameRight, frameTop + line));
    addImage("tr_v", accent, new RectF(frameRight - line, frameTop, frameRight, frameTop + corner));
    addImage("bl_h", accent, new RectF(frameLeft, frameBottom - line, frameLeft + corner, frameBottom));
    addImage("bl_v", accent, new RectF(frameLeft, frameBottom - corner, frameLeft + line, frameBottom));
    addImage("br_h", accent, new RectF(frameRight - corner, frameBottom - line, frameRight, frameBottom));
    addImage("br_v", accent, new RectF(frameRight - line, frameBottom - corner, frameRight, frameBottom));

    chrome.add(button("back", control, "‹",
        new RectF(px(26f), topInset + px(25f), px(154f), headerBottom - px(17f)),
        px(72f), id -> listener.onBack()));
    addText("title", "Scan QR code",
        new RectF(px(180f), topInset, width - px(180f), headerBottom),
        px(49f), FontVariation.SEMI_BOLD, 1);

    torchButton = chrome.add(button("torch", control, torchOn ? "Torch on" : "Torch",
        new RectF(width - px(290f), topInset + px(34f), width - px(34f), headerBottom - px(25f)),
        px(34f), id -> listener.onToggleTorch()));

    float statusTop = frameBottom + px(48f);
    statusText = chrome.add(new Text.Builder(getContext(), "scanner_status", status,
        new RectF(px(70f), statusTop, width - px(70f), statusTop + px(145f)))
        .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
        .setTextSizePx(px(39f)).setTextColor(Color.WHITE)
        .setAlignment(Text.Alignment.CENTER).setVerticalAlignment(Text.VerticalAlignment.CENTER)
        .setMaxLines(3));
    permissionButton = chrome.add(button("permission", accent, "Allow camera",
        new RectF(px(180f), statusTop + px(165f), width - px(180f), statusTop + px(285f)),
        px(38f), id -> listener.onPermissionAction()));
    updateState();
  }

  private void updateState() {
    if (statusText != null) statusText.setText(status);
    if (permissionButton != null) permissionButton.setVisible(permissionActionVisible)
        .setEnabled(permissionActionVisible);
    if (torchButton != null) torchButton.setLabel(torchOn ? "Torch on" : "Torch")
        .setVisible(torchAvailable).setEnabled(torchAvailable);
    invalidate();
  }

  private void addImage(String id, Bitmap bitmap, RectF bounds) {
    chrome.add(new Image.Builder(getContext(), id, bitmap, bounds).setScaleType(Image.ScaleType.FIT_XY));
  }

  private void addText(String id, String value, RectF bounds, float size,
      FontVariation variation, int maxLines) {
    chrome.add(new Text.Builder(getContext(), id, value, bounds).setFont(NativeFonts.INTER)
        .setFontVariations(variation).setTextSizePx(size).setTextColor(Color.WHITE)
        .setAlignment(Text.Alignment.CENTER).setVerticalAlignment(Text.VerticalAlignment.CENTER)
        .setMaxLines(maxLines));
  }

  private Button.Builder button(String id, Bitmap background, String label, RectF bounds,
      float textSize, Button.OnClickListener click) {
    return new Button.Builder(getContext(), id, background, label, bounds)
        .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(px(38f))
        .setFont(NativeFonts.INTER).setFontVariations(FontVariation.SEMI_BOLD)
        .setTextSizePx(textSize).setTextColor(Color.WHITE).setRippleEnabled(true)
        .setWaitForRippleBeforeClick(true).setRippleColor(0x33FFFFFF).setOnClickListener(click);
  }

  @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
  @Override public boolean onTouchEvent(MotionEvent event) {
    return layers.onTouchEvent(event) || super.onTouchEvent(event);
  }

  public void release() {
    layers.release();
    recycle(clear, scrim, control, accent);
  }

  private float px(float value) {
    return config.toRuntime(value, Math.max(1, getResources().getDisplayMetrics().widthPixels));
  }
  private static Bitmap colorBitmap(int color) {
    Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(color);
    return bitmap;
  }
  private static void recycle(Bitmap... bitmaps) {
    for (Bitmap bitmap : bitmaps) if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
  }
}
