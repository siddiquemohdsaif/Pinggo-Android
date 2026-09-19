package com.w3n.pinggo.views.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.data.cache.MediaPreviewCache;

import java.io.File;

/** Minimal reusable full-screen profile-photo preview. */
public final class ProfilePhotoPreviewView extends View {
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer content = layers.addLayer("profile_photo_preview");
  private final Bitmap transparent = colorBitmap(Color.TRANSPARENT);
  private Bitmap photo;
  private String phone = "";
  private Runnable close;
  private int topInset;
  private int bottomInset;
  private int loadGeneration;

  public ProfilePhotoPreviewView(Context context) {
    super(context);
    setBackgroundColor(Color.BLACK);
    setClickable(true);
    setVisibility(GONE);
    ViewCompat.setOnApplyWindowInsetsListener(this, (view, insets) -> {
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
      setInsets(bars.top, bars.bottom);
      return insets;
    });
  }

  public void show(Bitmap fallback, String originalSource, String phone, Runnable close) {
    int generation = ++loadGeneration;
    String source = normalizeSource(originalSource);
    // A list avatar is circular. Keep it hidden while the uncropped original loads.
    this.photo = source.isEmpty() ? fallback : null;
    this.phone = phone == null ? "" : phone.trim();
    this.close = close;
    setVisibility(VISIBLE);
    if (getWidth() > 0) build(getWidth(), getHeight());
    bringToFront();
    if (!source.isEmpty()) {
      android.util.DisplayMetrics display = getResources().getDisplayMetrics();
      MediaPreviewCache.loadImageForDisplay(getContext(), source,
          display.widthPixels, display.heightPixels,
          new MediaPreviewCache.Callback<Bitmap>() {
            @Override public void onSuccess(Bitmap original) {
              if (generation != loadGeneration || getVisibility() != VISIBLE) return;
              photo = original;
              build(getWidth(), getHeight());
            }

            @Override public void onError() {
              if (generation != loadGeneration || getVisibility() != VISIBLE) return;
              photo = fallback;
              build(getWidth(), getHeight());
            }
          });
    }
  }

  public void setInsets(int top, int bottom) {
    topInset = Math.max(0, top);
    bottomInset = Math.max(0, bottom);
    if (getWidth() > 0 && getVisibility() == VISIBLE) build(getWidth(), getHeight());
  }

  public boolean dismiss() {
    if (getVisibility() != VISIBLE) return false;
    loadGeneration++;
    setVisibility(GONE);
    photo = null;
    close = null;
    content.clear();
    return true;
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    if (getVisibility() == VISIBLE) build(width, height);
  }

  private void build(int width, int height) {
    content.clear();
    if (width <= 0 || height <= 0) return;
    float density = getResources().getDisplayMetrics().density;
    float headerTop = topInset;
    float headerHeight = 64f * density;
    content.add(new Button.Builder(getContext(), "profile_preview_back", transparent, "‹",
        new RectF(0, headerTop, 72f * density, headerTop + headerHeight))
        .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
        .setTextSizePx(42f * density).setTextColor(Color.WHITE)
        .setRippleEnabled(true).setRippleColor(0x33FFFFFF)
        .setOnClickListener(id -> {
          Runnable action = close;
          if (action != null) action.run();
        }));
    content.add(new Text.Builder(getContext(), "profile_preview_phone", phone,
        new RectF(72f * density, headerTop, width - 24f * density, headerTop + headerHeight))
        .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
        .setTextSizePx(18f * getResources().getDisplayMetrics().scaledDensity)
        .setTextColor(Color.WHITE).setVerticalAlignment(Text.VerticalAlignment.CENTER)
        .setMaxLines(1));
    if (photo != null && !photo.isRecycled()) {
      float imageTop = headerTop + headerHeight;
      float imageBottom = Math.max(imageTop, height - bottomInset);
      content.add(new Image.Builder(getContext(), "profile_preview_image", photo,
          new RectF(0, imageTop, width, imageBottom)).setScaleType(Image.ScaleType.FIT_CENTER));
    }
    invalidate();
  }

  @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
  @Override public boolean onTouchEvent(MotionEvent event) {
    return layers.onTouchEvent(event) || super.onTouchEvent(event);
  }
  public void release() {
    layers.release();
    if (!transparent.isRecycled()) transparent.recycle();
  }
  private static Bitmap colorBitmap(int color) {
    Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(color);
    return bitmap;
  }

  private static String normalizeSource(String source) {
    if (source == null || source.trim().isEmpty()) return "";
    String value = source.trim();
    File file = new File(value);
    return file.isAbsolute() ? Uri.fromFile(file).toString() : value;
  }
}
