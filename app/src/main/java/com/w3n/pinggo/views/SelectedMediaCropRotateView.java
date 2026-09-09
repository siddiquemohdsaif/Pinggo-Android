package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.component.FigmaConfig;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

/** Full-screen crop/rotate surface used by selected image preview. */
final class SelectedMediaCropRotateView extends FrameLayout {
  interface Listener { void onCancel(); void onDone(Bitmap bitmap); }

  private final CropImageView cropView;

  SelectedMediaCropRotateView(Context context, Bitmap bitmap, Listener listener) {
    super(context);
    setBackgroundColor(Color.BLACK);
    setClickable(true);
    cropView = new CropImageView(context);
    cropView.setBackgroundColor(Color.BLACK);
    cropView.setFreeformCrop(true);
    cropView.setBitmap(bitmap);
    LayoutParams cropParams = new LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    cropParams.setMargins(0, dp(90), 0, dp(110));
    addView(cropView, cropParams);

    addView(new ControlsView(context, listener), new LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
  }

  private final class ControlsView extends View {
    private final FigmaConfig config = new FigmaConfig(1080f);
    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer controls = layers.addLayer("crop_controls");
    private final Bitmap transparent = colorBitmap(Color.TRANSPARENT);
    private final Listener listener;
    ControlsView(Context context, Listener listener) {
      super(context); this.listener = listener; setClickable(true);
    }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
      controls.clear();
      float scale = config.getScale(width);
      add("cancel", "Cancel", new RectF(20f * scale, height - 100f * scale,
          250f * scale, height - 18f * scale), id -> listener.onCancel());
      add("rotate", "↻", new RectF(width / 2f - 100f * scale, height - 110f * scale,
          width / 2f + 100f * scale, height - 14f * scale), id -> cropView.rotateClockwise());
      add("done", "Done", new RectF(width - 250f * scale, height - 100f * scale,
          width - 20f * scale, height - 18f * scale), id -> {
        Bitmap result = cropView.getCroppedBitmap();
        if (result != null) listener.onDone(result);
      });
    }
    private void add(String id, String label, RectF bounds, Button.OnClickListener click) {
      controls.add(new Button.Builder(getContext(), id, transparent, label, bounds)
          .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
          .setTextSizePx("↻".equals(label) ? dp(34) : dp(18)).setTextColor(
              "↻".equals(label) ? Color.WHITE : 0xFF22C56E)
          .setRippleEnabled(true).setRippleColor(0x22FFFFFF).setOnClickListener(click));
    }
    @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
    @Override public boolean onTouchEvent(MotionEvent event) {
      return layers.onTouchEvent(event) || super.onTouchEvent(event);
    }
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
  private static Bitmap colorBitmap(int color) {
    Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(color); return bitmap;
  }
}
