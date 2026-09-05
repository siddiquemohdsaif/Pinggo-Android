package com.w3n.pinggo.views.call;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import java.util.Locale;

/** AAR-native call detail screen. */
public final class CallDetailView extends View {
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer background = layers.addLayer("background");
  private final ZLayer content = layers.addLayer("content");
  private final Listener listener;
  private final String name;
  private final String dateTime;
  private final String duration;
  private final boolean video;
  private final Bitmap white = colorBitmap(Color.WHITE);
  private final Bitmap accent = colorBitmap(0xFF019CC4);
  private int topInset;
  private int bottomInset;

  public CallDetailView(
      Context context,
      String name,
      String dateTime,
      String duration,
      boolean video,
      Listener listener) {
    super(context);
    this.name = name;
    this.dateTime = dateTime;
    this.duration = duration;
    this.video = video;
    this.listener = listener;
    setBackgroundColor(0xFFF7F9FB);
    setClickable(true);
  }

  public void setInsets(int top, int bottom) {
    topInset = Math.max(0, top);
    bottomInset = Math.max(0, bottom);
    if (getWidth() > 0) build();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (w > 0 && h > 0) build();
  }

  private void build() {
    background.clear();
    content.clear();
    float w = getWidth();
    float top = topInset + px(27.5f);
    background.add(
        new Image.Builder(getContext(), "bg", white, new RectF(0, 0, w, getHeight()))
            .setScaleType(Image.ScaleType.FIT_XY));
    addButton(
        "back",
        white,
        "‹",
        new RectF(px(22f), top, px(154f), top + px(132f)),
        0xFF000E1A,
        id -> listener.onBack());
    addText(
        "title",
        "Call details",
        new RectF(px(176f), top, w - px(55f), top + px(132f)),
        sp(23),
        0xFF000E1A,
        FontVariation.BOLD,
        Text.Alignment.START);
    float avatarTop = top + px(231f);
    content.add(
        new Image.Builder(
                getContext(),
                "avatar",
                avatar(name),
                new RectF(w / 2 - px(159.5f), avatarTop, w / 2 + px(159.5f), avatarTop + px(319f)))
            .setScaleType(Image.ScaleType.CENTER_CROP));
    addText(
        "name",
        name,
        new RectF(px(66f), avatarTop + px(352f), w - px(66f), avatarTop + px(478.5f)),
        sp(24),
        0xFF000E1A,
        FontVariation.BOLD,
        Text.Alignment.CENTER);
    addText(
        "type",
        video ? "Video call" : "Voice call",
        new RectF(px(66f), avatarTop + px(495f), w - px(66f), avatarTop + px(594f)),
        sp(16),
        0xFF019CC4,
        FontVariation.SEMI_BOLD,
        Text.Alignment.CENTER);
    float cardTop = avatarTop + px(687.5f);
    addText(
        "date_label",
        "Date and time",
        new RectF(px(77f), cardTop, w - px(77f), cardTop + px(77f)),
        sp(13),
        0xFF687382,
        FontVariation.REGULAR,
        Text.Alignment.START);
    addText(
        "date",
        dateTime,
        new RectF(px(77f), cardTop + px(77f), w - px(77f), cardTop + px(192.5f)),
        sp(17),
        0xFF000E1A,
        FontVariation.SEMI_BOLD,
        Text.Alignment.START);
    addText(
        "duration_label",
        "Duration",
        new RectF(px(77f), cardTop + px(253f), w - px(77f), cardTop + px(330f)),
        sp(13),
        0xFF687382,
        FontVariation.REGULAR,
        Text.Alignment.START);
    addText(
        "duration",
        duration,
        new RectF(px(77f), cardTop + px(330f), w - px(77f), cardTop + px(445.5f)),
        sp(17),
        0xFF000E1A,
        FontVariation.SEMI_BOLD,
        Text.Alignment.START);
    addButton(
        "call",
        accent,
        video ? "Start video call" : "Call again",
        new RectF(
            px(77f),
            getHeight() - bottomInset - px(209f),
            w - px(77f),
            getHeight() - bottomInset - px(55f)),
        Color.WHITE,
        id -> listener.onCallAgain(video));
    invalidate();
  }

  private void addText(
      String id,
      String value,
      RectF rect,
      float size,
      int color,
      FontVariation weight,
      Text.Alignment alignment) {
    content.add(
        new Text.Builder(getContext(), id, value, rect)
            .setFont(NativeFonts.INTER)
            .setFontVariations(weight)
            .setTextSizePx(size)
            .setTextColor(color)
            .setAlignment(alignment)
            .setVerticalAlignment(Text.VerticalAlignment.CENTER)
            .setMaxLines(2));
  }

  private void addButton(
      String id, Bitmap bitmap, String label, RectF rect, int color, Button.OnClickListener click) {
    content.add(
        new Button.Builder(getContext(), id, bitmap, label, rect)
            .setImageScaleType(Image.ScaleType.FIT_XY)
            .setCornerRadiusPx(px(44f))
            .setFont(NativeFonts.INTER)
            .setFontVariations(FontVariation.SEMI_BOLD)
            .setTextSizePx(sp(16))
            .setTextColor(color)
            .setRippleEnabled(true).setWaitForRippleBeforeClick(true)
            .setRippleColor(0x22019CC4)
            .setOnClickListener(click));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    layers.draw(canvas);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return layers.onTouchEvent(event) || super.onTouchEvent(event);
  }

  public void release() {
    layers.release();
    recycle(white, accent);
  }

  private Bitmap avatar(String value) {
    int size = Math.round(px(319f));
    Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(0xFFD9F1F7);
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
    String label =
        value == null || value.trim().isEmpty()
            ? "?"
            : value.trim().substring(0, 1).toUpperCase(Locale.US);
    paint.setColor(0xFF019CC4);
    paint.setTextSize(size * .42f);
    paint.setTextAlign(Paint.Align.CENTER);
    Paint.FontMetrics metrics = paint.getFontMetrics();
    canvas.drawText(label, size / 2f, size / 2f - (metrics.ascent + metrics.descent) / 2f, paint);
    return bitmap;
  }

  private float px(float v) {
    return figmaConfig.toRuntime(v, Math.max(1, getResources().getDisplayMetrics().widthPixels));
  }

  private float sp(float v) {
    return v * getResources().getDisplayMetrics().scaledDensity;
  }

  private static Bitmap colorBitmap(int c) {
    Bitmap b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    b.eraseColor(c);
    return b;
  }

  private static void recycle(Bitmap... bs) {
    for (Bitmap b : bs) if (b != null && !b.isRecycled()) b.recycle();
  }

  public interface Listener {
    void onBack();

    void onCallAgain(boolean video);
  }
}
