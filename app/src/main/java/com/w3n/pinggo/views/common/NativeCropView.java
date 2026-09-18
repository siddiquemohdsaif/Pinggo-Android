package com.w3n.pinggo.views.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.R;
import com.w3n.pinggo.views.CropImageView;
import java.util.Collections;

/** Full-screen crop surface with view-based controls. */
public final class NativeCropView extends FrameLayout {
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
  public interface Listener {
    void onRetry();
    void onConfirm(Bitmap bitmap);
    void onInvalidCrop();
    void onDismiss();
  }

  private final CropImageView cropView;
  private final ControlsView controlsView;
  private final Listener listener;
  private boolean dismissed;

  public NativeCropView(@NonNull Context context, @NonNull Bitmap source,
      int minimumCropPx, int maximumCropPx, @NonNull Listener listener) {
    super(context);
    this.listener = listener;
    setBackgroundColor(Color.BLACK);
    setClickable(true);
    cropView = new CropImageView(context);
    cropView.setBackgroundColor(Color.BLACK);
    cropView.setCropBoxSizeRangePx(minimumCropPx, maximumCropPx);
    cropView.setBitmap(source);
    addView(cropView, new LayoutParams(1, 1));
    controlsView = new ControlsView(context);
    addView(controlsView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
  }

  @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int controlsHeight = Math.round(px(191f));
    int cropBottom = Math.max(1, getHeight() - controlsHeight);
    cropView.layout(0, 0, getWidth(), cropBottom);
    controlsView.layout(0, cropBottom, getWidth(), getHeight());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      cropView.setSystemGestureExclusionRects(Collections.singletonList(
          new Rect(0, 0, cropView.getWidth(), cropView.getHeight())));
    }
  }

  public boolean dismissIfShowing() {
    if (dismissed) return false;
    dismissed = true;
    listener.onDismiss();
    return true;
  }

  public void release() {
    controlsView.release();
  }

  private final class ControlsView extends View {
    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer controls = layers.addLayer("crop_controls");
    private final Bitmap secondary = colorBitmap(0xFFF0F3F6);
    private final Bitmap primary = colorBitmap(0xFF019CC4);
    ControlsView(Context context) { super(context); setBackgroundColor(Color.BLACK); setClickable(true); }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
      controls.clear();
      float horizontal = px(24f);
      float gap = px(24f);
      float buttonWidth = (width - horizontal * 2f - gap * 2f) / 3f;
      float top = px(12f);
      add("retry", secondary, getContext().getString(R.string.retry), horizontal, top,
          buttonWidth, 0xFF000E1A, id -> { dismissIfShowing(); listener.onRetry(); });
      add("rotate", secondary, getContext().getString(R.string.rotate),
          horizontal + buttonWidth + gap, top, buttonWidth, 0xFF000E1A,
          id -> cropView.rotateClockwise());
      add("confirm", primary, getContext().getString(android.R.string.ok),
          horizontal + (buttonWidth + gap) * 2f, top, buttonWidth, Color.WHITE, id -> {
            Bitmap result = cropView.getCroppedBitmap();
            if (result == null) listener.onInvalidCrop();
            else { dismissIfShowing(); listener.onConfirm(result); }
          });
    }
    private void add(String id, Bitmap background, String label, float left, float top,
        float width, int textColor, Button.OnClickListener click) {
      controls.add(new Button.Builder(getContext(), id, background, label,
          new RectF(left, top, left + width, top + px(143f)))
          .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(px(38.5f))
          .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
          .setTextSizePx(px(44f)).setTextColor(textColor).setRippleEnabled(true)
          .setWaitForRippleBeforeClick(false).setOnClickListener(click));
    }
    @Override protected void onDraw(@NonNull Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
    @Override public boolean onTouchEvent(MotionEvent event) {
      return layers.onTouchEvent(event) || super.onTouchEvent(event);
    }
    void release() { layers.release(); secondary.recycle(); primary.recycle(); }
  }

  private float px(float value) {
    return figmaConfig.toRuntime(value, Math.max(1, getResources().getDisplayMetrics().widthPixels));
  }

  private static Bitmap colorBitmap(int color) {
    Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(color);
    return bitmap;
  }
}
