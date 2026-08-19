package com.w3n.pinggo.views.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.dialog.Dialog;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.R;
import com.w3n.pinggo.views.CropImageView;

/** Full-screen AAR dialog whose center hosts the interactive Android crop surface. */
public final class NativeCropDialogView extends FrameLayout {
  public interface Listener {
    void onRetry();
    void onConfirm(Bitmap bitmap);
    void onInvalidCrop();
    void onDismiss();
  }

  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer dialogLayer = layers.addLayer("crop_dialog_layer");
  private final CropImageView cropView;
  private final Listener listener;
  private final Bitmap secondary = colorBitmap(0xFFF0F3F6);
  private final Bitmap primary = colorBitmap(0xFF019CC4);
  private Dialog dialog;
  private boolean built;

  public NativeCropDialogView(@NonNull Context context, @NonNull Bitmap source,
      int minimumCropDp, int maximumCropDp, @NonNull Listener listener) {
    super(context);
    this.listener = listener;
    setWillNotDraw(false);
    setClickable(true);
    dialogLayer.setTouchPolicy(ZLayer.TouchPolicy.MODAL);
    cropView = new CropImageView(context);
    cropView.setBackgroundColor(Color.BLACK);
    cropView.setCropBoxSizeRangeDp(minimumCropDp, maximumCropDp);
    cropView.setBitmap(source);
    addView(cropView, new LayoutParams(1, 1));
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    if (width <= 0 || height <= 0 || built) return;
    built = true;
    float margin = dp(16);
    dialog = dialogLayer.add(new Dialog.Builder(getContext(), "crop_dialog",
        new RectF(margin, margin, width - margin, height - margin))
        .setBackgroundColor(Color.WHITE)
        .setCornerRadiusPx(dp(24))
        .removeDropShadow()
        .setDimEnabled(true)
        .setDimAlpha(0.5f)
        .setDismissOnBackPressed(true)
        .setInitiallyShown(true)
        .setOnDismissListener((id, reason) -> listener.onDismiss())
        .setContent((nativeDialog, content, scope) -> {
          float contentWidth = scope.width();
          float horizontal = dp(20);
          content.add(new Text.Builder(getContext(), scope.id("title"),
              getContext().getString(R.string.select_crop_region),
              scope.rect(horizontal, dp(18), contentWidth - horizontal * 2f, dp(48)))
              .setFont(NativeFonts.INTER)
              .setFontVariations(FontVariation.BOLD)
              .setTextSizePx(dp(22))
              .setTextColor(0xFF000E1A)
              .setAlignment(Text.Alignment.CENTER)
              .setVerticalAlignment(Text.VerticalAlignment.CENTER)
              .setMaxLines(1));
          float gap = dp(12);
          float buttonWidth = (contentWidth - horizontal * 2f - gap) / 2f;
          float buttonTop = scope.height() - dp(72);
          content.add(button(scope, "retry", secondary,
              getContext().getString(R.string.retry), horizontal, buttonTop, buttonWidth,
              0xFF000E1A, id -> {
                nativeDialog.dismiss(Dialog.DismissReason.ACTION);
                listener.onRetry();
              }));
          content.add(button(scope, "confirm", primary,
              getContext().getString(android.R.string.ok), horizontal + buttonWidth + gap,
              buttonTop, buttonWidth, Color.WHITE, id -> {
                Bitmap result = cropView.getCroppedBitmap();
                if (result == null) listener.onInvalidCrop();
                else {
                  nativeDialog.dismiss(Dialog.DismissReason.ACTION);
                  listener.onConfirm(result);
                }
              }));
        }));
    requestLayout();
  }

  private Button button(Dialog.Scope scope, String id, Bitmap background, String label,
      float left, float top, float width, int textColor, Button.OnClickListener listener) {
    return new Button.Builder(getContext(), scope.id(id), background, label,
        scope.rect(left, top, width, dp(52)))
        .setImageScaleType(Image.ScaleType.FIT_XY)
        .setCornerRadiusPx(dp(14))
        .setFont(NativeFonts.INTER)
        .setFontVariations(FontVariation.MEDIUM)
        .setTextSizePx(dp(16))
        .setTextColor(textColor)
        .setRippleEnabled(true)
        .setOnClickListener(listener)
        .build(this);
  }

  @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int margin = Math.round(dp(36));
    int top = Math.round(dp(90));
    int bottom = Math.max(top + 1, getHeight() - Math.round(dp(104)));
    cropView.layout(margin, top, Math.max(margin + 1, getWidth() - margin), bottom);
  }

  @Override protected void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);
    layers.draw(canvas);
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    return layers.onTouchEvent(event) || super.onTouchEvent(event);
  }

  public boolean dismissIfShowing() {
    if (dialog == null || !dialog.isShowing()) return false;
    dialog.dismiss(Dialog.DismissReason.BACK_PRESSED);
    return true;
  }

  public void release() {
    layers.release();
    secondary.recycle();
    primary.recycle();
  }

  private float dp(float value) {
    return value * getResources().getDisplayMetrics().density;
  }

  private static Bitmap colorBitmap(int color) {
    Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(color);
    return bitmap;
  }
}
