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
import com.ogfa.nativeviews.progress.Progress;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

/** Companion QR page rendered entirely by native-views-release.aar. */
public final class NativeCompanionLinkView extends View {
  private static final int PRIMARY = 0xFF000E1A;
  private static final int SECONDARY = 0xFF687382;
  private static final int ACCENT = 0xFF019CC4;
  private final FigmaConfig config = new FigmaConfig(1080f);
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer background = layers.addLayer("companion_background");
  private final ZLayer content = layers.addLayer("companion_content");
  private final Runnable back;
  private final Bitmap page = colorBitmap(0xFFF7F9FB);
  private final Bitmap white = colorBitmap(Color.WHITE);
  private final Bitmap transparent = colorBitmap(Color.TRANSPARENT);
  private Image qrImage;
  private Text statusText;
  private Progress progress;
  private Bitmap qrBitmap;
  private String status;
  private boolean loading = true;
  private int topInset;
  private int bottomInset;

  public NativeCompanionLinkView(Context context, String initialStatus, Runnable back) {
    super(context); status = initialStatus; this.back = back;
    setClickable(true); setBackgroundColor(0xFFF7F9FB);
  }

  public void setInsets(int top, int bottom) {
    topInset = Math.max(0, top); bottomInset = Math.max(0, bottom);
    if (getWidth() > 0) build();
  }

  public void setQrBitmap(Bitmap bitmap) {
    Bitmap previous = qrBitmap; qrBitmap = bitmap;
    if (qrImage != null) qrImage.setBitmap(bitmap == null ? white : bitmap);
    if (previous != null && previous != bitmap && !previous.isRecycled()) previous.recycle();
    invalidate();
  }

  public void setStatus(String value) {
    status = value == null ? "" : value;
    if (statusText != null) statusText.setText(status);
    invalidate();
  }

  public void setLoading(boolean value) {
    loading = value;
    if (progress != null) progress.setVisible(value);
    invalidate();
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    if (width > 0 && height > 0) build();
  }

  private void build() {
    background.clear(); content.clear();
    float width = getWidth();
    background.add(new Image.Builder(getContext(), "page_background", page,
        new RectF(0, 0, width, getHeight())).setScaleType(Image.ScaleType.FIT_XY));
    float headerTop = topInset, headerHeight = px(170f);
    content.add(new Button.Builder(getContext(), "back", transparent, "‹",
        new RectF(px(20f), headerTop, px(155f), headerTop + headerHeight))
        .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
        .setTextSizePx(px(82f)).setTextColor(PRIMARY).setRippleEnabled(true)
        .setRippleColor(0x18019CC4).setOnClickListener(id -> back.run()));
    addText("title", "Link this phone",
        new RectF(px(165f), headerTop, width - px(45f), headerTop + headerHeight),
        px(52f), PRIMARY, FontVariation.SEMI_BOLD, Text.Alignment.START, 1);
    float helpTop = headerTop + headerHeight;
    addText("help", "On your primary phone, open Linked devices, tap Link a device, then scan this QR code.",
        new RectF(px(70f), helpTop, width - px(70f), helpTop + px(190f)),
        px(39f), SECONDARY, FontVariation.REGULAR, Text.Alignment.CENTER, 3);
    float qrSize = Math.min(px(720f), width - px(160f));
    float qrTop = helpTop + px(225f), qrLeft = (width - qrSize) / 2f;
    qrImage = content.add(new Image.Builder(getContext(), "pairing_qr", qrBitmap == null ? white : qrBitmap,
        new RectF(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize))
        .setScaleType(Image.ScaleType.FIT_CENTER));
    float statusTop = qrTop + qrSize + px(55f);
    statusText = content.add(new Text.Builder(getContext(), "pairing_status", status,
        new RectF(px(80f), statusTop, width - px(80f), statusTop + px(120f)))
        .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
        .setTextSizePx(px(38f)).setTextColor(SECONDARY).setAlignment(Text.Alignment.CENTER)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(2));
    float indicator = px(76f);
    float progressTop = Math.min(statusTop + px(135f), getHeight() - bottomInset - indicator - px(35f));
    progress = content.add(new Progress.Builder(getContext(), "pairing_progress",
        new RectF((width - indicator) / 2f, progressTop, (width + indicator) / 2f, progressTop + indicator))
        .setStyle(Progress.Style.CIRCULAR).setMode(Progress.Mode.INDETERMINATE)
        .setTrackColor(0x22019CC4).setProgressColor(ACCENT).setThicknessPx(px(9f))
        .setIndeterminateDuration(850L).setVisible(loading));
    invalidate();
  }

  private void addText(String id, String value, RectF bounds, float size, int color,
      FontVariation variation, Text.Alignment alignment, int maxLines) {
    content.add(new Text.Builder(getContext(), id, value, bounds).setFont(NativeFonts.INTER)
        .setFontVariations(variation).setTextSizePx(size).setTextColor(color).setAlignment(alignment)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(maxLines));
  }
  @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
  @Override public boolean onTouchEvent(MotionEvent event) { return layers.onTouchEvent(event) || super.onTouchEvent(event); }
  public void release() {
    layers.release();
    if (qrBitmap != null && !qrBitmap.isRecycled()) qrBitmap.recycle();
    recycle(page, white, transparent);
  }
  private float px(float value) { return config.toRuntime(value, Math.max(1, getResources().getDisplayMetrics().widthPixels)); }
  private static Bitmap colorBitmap(int color) { Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); bitmap.eraseColor(color); return bitmap; }
  private static void recycle(Bitmap... bitmaps) { for (Bitmap bitmap : bitmaps) if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle(); }
}
