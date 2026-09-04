package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

/** Native-AAR backed base container for media screens. */
public class NativeMediaScreenView extends FrameLayout {
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer background = layers.addLayer("media_background");
  private final Bitmap black = colorBitmap(Color.BLACK);
  private boolean navigationBarVisible = true;
  private int navigationBarColor = Color.BLACK;
  private Runnable systemBarsChangedListener;

  public NativeMediaScreenView(@NonNull Context context) {
    super(context);
    setWillNotDraw(false);
    // Full-screen media overlays must own empty-area taps instead of allowing them to
    // reach message rows behind the overlay.
    setClickable(true);
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    background.clear();
    if (width > 0 && height > 0) {
      background.add(new Image.Builder(getContext(), "media_background_fill", black,
          new RectF(0f, 0f, width, height)).setScaleType(Image.ScaleType.FIT_XY));
    }
  }

  @Override protected void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);
    layers.draw(canvas);
  }

  public void release() {
    systemBarsChangedListener = null;
    layers.release();
    if (!black.isRecycled()) black.recycle();
  }

  public final boolean isNavigationBarVisible() {
    return navigationBarVisible;
  }

  public final int getNavigationBarColor() {
    return navigationBarColor;
  }

  public final void setOnSystemBarsChangedListener(Runnable listener) {
    systemBarsChangedListener = listener;
  }

  protected final void setNavigationBarState(boolean visible, int color) {
    boolean changed = navigationBarVisible != visible || navigationBarColor != color;
    navigationBarVisible = visible;
    navigationBarColor = color;
    if (changed && systemBarsChangedListener != null) systemBarsChangedListener.run();
  }

  private static Bitmap colorBitmap(int color) {
    Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(color);
    return bitmap;
  }
}
