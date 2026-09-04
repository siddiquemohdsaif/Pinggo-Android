package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.component.FigmaConfig;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

/** Native-AAR video metadata and noise toggle shown beneath the frame timeline. */
final class NativeSelectedVideoToolsView extends View {
  private final FigmaConfig figma = new FigmaConfig(1080f);
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer layer = layers.addLayer("selected_video_tools");
  private final Bitmap background = color(0xCC30343A);
  private final Bitmap active = color(0xFF20B86A);
  private final Listener listener;
  private String metadata = "0:00 • 0 B";
  private boolean noiseEnabled = true;

  NativeSelectedVideoToolsView(Context context, Listener listener) {
    super(context);
    this.listener = listener;
  }

  void setMetadata(String value) {
    metadata = value == null ? "" : value;
    build();
  }

  void setNoiseEnabled(boolean enabled) {
    noiseEnabled = enabled;
    build();
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    build();
  }

  private void build() {
    float width = getWidth();
    float height = getHeight();
    if (width <= 0 || height <= 0) return;
    layer.clear();
    layer.add(new Button.Builder(getContext(), "noise", noiseEnabled ? active : background,
        noiseEnabled ? "Noise on" : "Noise off", new RectF(0, 0, px(245), height))
        .setImageScaleType(Image.ScaleType.FIT_XY)
        .setCornerRadiusPx(height / 2f)
        .setFont(NativeFonts.INTER)
        .setFontVariations(FontVariation.SEMI_BOLD)
        .setTextSizePx(16 * getResources().getDisplayMetrics().scaledDensity)
        .setTextColor(Color.WHITE)
        .setRippleEnabled(true)
        .setOnClickListener(id -> listener.onNoiseToggle()));
    layer.add(new Button.Builder(getContext(), "metadata", background, metadata,
        new RectF(px(260), 0, Math.min(width, px(620)), height))
        .setImageScaleType(Image.ScaleType.FIT_XY)
        .setCornerRadiusPx(height / 2f)
        .setFont(NativeFonts.INTER)
        .setFontVariations(FontVariation.REGULAR)
        .setTextSizePx(16 * getResources().getDisplayMetrics().scaledDensity)
        .setTextColor(Color.WHITE));
    invalidate();
  }

  @Override protected void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);
    layers.draw(canvas);
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    return layers.onTouchEvent(event);
  }

  void release() {
    layers.release();
    if (!background.isRecycled()) background.recycle();
    if (!active.isRecycled()) active.recycle();
  }

  private float px(float value) {
    return figma.toRuntime(value, Math.max(1, getResources().getDisplayMetrics().widthPixels));
  }

  private static Bitmap color(int value) {
    Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(value);
    return bitmap;
  }

  interface Listener {
    void onNoiseToggle();
  }
}
