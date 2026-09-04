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
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

/** AAR-native transparent play and playback-speed controls. */
public final class NativeVideoControlsView extends View {
  private final FigmaConfig figma = new FigmaConfig(1080f);
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer layer = layers.addLayer("video_controls");
  private final Bitmap transparent = color(Color.TRANSPARENT);
  private final Listener listener;
  private boolean playing;
  private String speed = "1×";

  public NativeVideoControlsView(Context context, Listener listener) {
    super(context);
    this.listener = listener;
    setClickable(true);
    setBackgroundColor(Color.TRANSPARENT);
  }

  public void setPlaying(boolean value) {
    playing = value;
    build();
  }

  public void setSpeedLabel(String value) {
    speed = value;
    build();
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    build();
  }

  private void build() {
    int width = getWidth(), height = getHeight();
    if (width <= 0 || height <= 0) return;
    layer.clear();
    layer.add(button("play", playing ? "Ⅱ" : "▶",
        new RectF(px(18), 0, px(150), height), id -> listener.onPlayPause()));
    layer.add(button("speed", speed,
        new RectF(width - px(210), 0, width - px(18), height),
        id -> listener.onSpeed(this)));
    invalidate();
  }

  private Button.Builder button(
      String id, String label, RectF region, Button.OnClickListener listener) {
    return new Button.Builder(getContext(), id, transparent, label, region)
        .setFont(NativeFonts.INTER)
        .setFontVariations(FontVariation.SEMI_BOLD)
        .setTextSizePx(17 * getResources().getDisplayMetrics().scaledDensity)
        .setTextColor(Color.WHITE)
        .setRippleEnabled(true)
        .setRippleColor(0x33FFFFFF)
        .setOnClickListener(listener);
  }

  @Override protected void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);
    layers.draw(canvas);
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    return layers.onTouchEvent(event) || super.onTouchEvent(event);
  }

  public void release() {
    layers.release();
    if (!transparent.isRecycled()) transparent.recycle();
  }

  private float px(float value) {
    return figma.toRuntime(value, Math.max(1, getResources().getDisplayMetrics().widthPixels));
  }

  private static Bitmap color(int color) {
    Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(color);
    return bitmap;
  }

  public interface Listener {
    void onPlayPause();
    void onSpeed(View anchor);
  }
}
