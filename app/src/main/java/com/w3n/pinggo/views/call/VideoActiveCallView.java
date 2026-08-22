package com.w3n.pinggo.views.call;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

/** Self-contained active video-call screen. */
public final class VideoActiveCallView extends View {
  private static final int ACCENT = 0xFF019CC4;
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer background = layers.addLayer("background"), content = layers.addLayer("content");
  private final Listener listener;
  private final String phone;
  private final Bitmap profile, dark = color(0xFF101820), control = color(0xFF26333E);
  private final Bitmap selected = color(ACCENT), danger = color(0xFFE53935);
  private int topInset, bottomInset;
  private boolean speakerOn, muted;

  public VideoActiveCallView(Context context, String phone, String profilePath, Listener listener) {
    super(context);
    this.phone = phone == null || phone.trim().isEmpty() ? "Unknown" : phone;
    this.listener = listener;
    Bitmap decoded = profilePath == null ? null : BitmapFactory.decodeFile(profilePath);
    profile = decoded == null ? avatar() : decoded;
    setClickable(true);
  }
  public void setInsets(int top, int bottom) {
    topInset = Math.max(0, top); bottomInset = Math.max(0, bottom);
    if (getWidth() > 0) build();
  }
  public void setAudioState(boolean speaker, boolean mute) {
    speakerOn = speaker; muted = mute;
    if (getWidth() > 0) build();
  }
  @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh); if (w > 0 && h > 0) build();
  }
  private void build() {
    background.clear(); content.clear();
    float w = getWidth(), h = getHeight(), top = topInset + dp(10);
    background.add(new Image.Builder(getContext(), "video_surface", dark, new RectF(0, 0, w, h))
        .setScaleType(Image.ScaleType.FIT_XY));
    button("back", control, "‹", new RectF(dp(10), top, dp(58), top + dp(48)), Color.WHITE,
        id -> listener.onBack());
    content.add(new Image.Builder(getContext(), "header_profile", profile,
        new RectF(dp(68), top + dp(4), dp(108), top + dp(44))).setScaleType(Image.ScaleType.CENTER_CROP));
    text("phone", phone, new RectF(dp(118), top, w - dp(16), top + dp(48)), sp(17), Color.WHITE,
        FontVariation.SEMI_BOLD, Text.Alignment.START);
    text("status", "Video call", new RectF(dp(24), top + dp(90), w - dp(24), top + dp(150)),
        sp(22), Color.WHITE, FontVariation.SEMI_BOLD, Text.Alignment.CENTER);
    controls(w, h); invalidate();
  }
  private void controls(float w, float h) {
    float bottom = h - bottomInset - dp(24), top = bottom - dp(64), gap = dp(18);
    float width = Math.min(dp(92), (w - dp(48) - gap * 2) / 3);
    float x = (w - (width * 3 + gap * 2)) / 2;
    button("speaker", speakerOn ? selected : control, speakerOn ? "Speaker on" : "Speaker",
        new RectF(x, top, x + width, bottom), Color.WHITE, id -> listener.onSpeaker());
    x += width + gap;
    button("mute", muted ? selected : control, muted ? "Unmute" : "Mute",
        new RectF(x, top, x + width, bottom), Color.WHITE, id -> listener.onMute());
    x += width + gap;
    button("end", danger, "End", new RectF(x, top, x + width, bottom), Color.WHITE,
        id -> listener.onEnd());
  }
  private void text(String id, String value, RectF rect, float size, int color,
      FontVariation weight, Text.Alignment alignment) {
    content.add(new Text.Builder(getContext(), id, value, rect).setFont(NativeFonts.INTER)
        .setFontVariations(weight).setTextSizePx(size).setTextColor(color).setAlignment(alignment)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(1));
  }
  private void button(String id, Bitmap image, String label, RectF rect, int color,
      Button.OnClickListener click) {
    content.add(new Button.Builder(getContext(), id, image, label, rect)
        .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(dp(24)).setFont(NativeFonts.INTER)
        .setFontVariations(FontVariation.SEMI_BOLD).setTextSizePx(sp(13)).setTextColor(color)
        .setRippleEnabled(true).setRippleColor(0x33FFFFFF).setOnClickListener(click));
  }
  @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
  @Override public boolean onTouchEvent(MotionEvent event) {
    return layers.onTouchEvent(event) || super.onTouchEvent(event);
  }
  public void release() { layers.release(); recycle(dark, control, selected, danger, profile); }
  private Bitmap avatar() {
    int size = Math.round(dp(180)); Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(0xFF26333E); canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
    paint.setColor(Color.WHITE); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(size * .26f);
    Paint.FontMetrics metrics = paint.getFontMetrics();
    canvas.drawText("▣", size / 2f, size / 2f - (metrics.ascent + metrics.descent) / 2f, paint);
    return bitmap;
  }
  private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
  private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
  private static Bitmap color(int c) { Bitmap b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); b.eraseColor(c); return b; }
  private static void recycle(Bitmap... values) {
    for (Bitmap value : values) if (value != null && !value.isRecycled()) value.recycle();
  }
  public interface Listener { void onBack(); void onSpeaker(); void onMute(); void onEnd(); }
}
