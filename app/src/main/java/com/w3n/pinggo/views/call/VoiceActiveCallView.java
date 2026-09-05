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

/** Self-contained active voice-call screen. */
public final class VoiceActiveCallView extends View {
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
  private static final int ACCENT = 0xFF019CC4;
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer background = layers.addLayer("background"), content = layers.addLayer("content");
  private final Listener listener;
  private final String phone;
  private final Bitmap profile, light = color(0xFFF7F9FB), white = color(Color.WHITE);
  private final Bitmap control = color(0xFF26333E), selected = color(ACCENT), danger = color(0xFFE53935);
  private final Bitmap accept = color(0xFF2EAD62);
  private final Bitmap disabled = color(0xFFB8C0C8);
  private int topInset, bottomInset;
  private boolean speakerOn, muted;
  private String callStatus = "Calling…";
  private boolean remoteMuted;
  private boolean incomingPrompt;
  private boolean callConnected;

  public VoiceActiveCallView(Context context, String phone, String profilePath, Listener listener) {
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
  public void setCallStatus(String status) {
    callStatus = status == null || status.trim().isEmpty() ? "Calling…" : status;
    if (getWidth() > 0) build();
  }
  public void setRemoteMuted(boolean muted) {
    remoteMuted = muted;
    if (getWidth() > 0) build();
  }
  public void showIncomingPrompt() {
    incomingPrompt = true;
    callStatus = "Incoming voice call";
    if (getWidth() > 0) build();
  }
  public void hideIncomingPrompt() {
    incomingPrompt = false;
    if (getWidth() > 0) build();
  }
  public void setCallConnected(boolean connected) {
    callConnected = connected;
    if (!connected) muted = false;
    if (getWidth() > 0) build();
  }
  @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh); if (w > 0 && h > 0) build();
  }

  private void build() {
    background.clear(); content.clear();
    float w = getWidth(), h = getHeight(), top = topInset + px(27.5f);
    background.add(new Image.Builder(getContext(), "bg", light, new RectF(0, 0, w, h))
        .setScaleType(Image.ScaleType.FIT_XY));
    button("back", white, "‹", new RectF(px(27.5f), top, px(159.5f), top + px(132f)), 0xFF000E1A,
        id -> listener.onBack());
    text("phone", phone, new RectF(px(187f), top, w - px(55f), top + px(132f)), sp(20), 0xFF000E1A,
        FontVariation.SEMI_BOLD, Text.Alignment.START);
    float size = Math.min(px(462f), w * .44f), avatarTop = top + px(346.5f);
    content.add(new Image.Builder(getContext(), "profile", profile,
        new RectF(w / 2 - size / 2, avatarTop, w / 2 + size / 2, avatarTop + size))
        .setScaleType(Image.ScaleType.CENTER_CROP));
    text("status", callStatus, new RectF(px(66f), avatarTop + size + px(60.5f), w - px(66f),
        avatarTop + size + px(187f)), sp(18), ACCENT, FontVariation.SEMI_BOLD, Text.Alignment.CENTER);
    if (remoteMuted) {
      text("remote_mute", "Muted himself",
          new RectF(px(66f), avatarTop + size + px(181.5f), w - px(66f), avatarTop + size + px(286f)),
          sp(14), 0xFF687382, FontVariation.REGULAR, Text.Alignment.CENTER);
    }
    if (incomingPrompt) incomingControls(w, h); else controls(w, h);
    invalidate();
  }

  private void incomingControls(float w, float h) {
    float bottom = h - bottomInset - px(66f), top = bottom - px(176f), width = px(324.5f), gap = px(77f);
    float x = (w - width * 2 - gap) / 2;
    button("reject", danger, "Reject", new RectF(x, top, x + width, bottom), Color.WHITE,
        id -> listener.onReject());
    x += width + gap;
    button("accept", accept, "Accept", new RectF(x, top, x + width, bottom), Color.WHITE,
        id -> listener.onAccept());
  }

  private void controls(float w, float h) {
    float bottom = h - bottomInset - px(66f), top = bottom - px(176f), gap = px(49.5f);
    float width = Math.min(px(253f), (w - px(132f) - gap * 2) / 3);
    float x = (w - (width * 3 + gap * 2)) / 2;
    button("speaker", speakerOn ? selected : control, speakerOn ? "Speaker on" : "Speaker",
        new RectF(x, top, x + width, bottom), Color.WHITE, id -> listener.onSpeaker());
    x += width + gap;
    Button muteButton = button("mute", !callConnected ? disabled : muted ? selected : control,
        muted ? "Unmute" : "Mute", new RectF(x, top, x + width, bottom), Color.WHITE,
        id -> { if (callConnected) listener.onMute(); });
    muteButton.setEnabled(callConnected);
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
  private Button button(String id, Bitmap image, String label, RectF rect, int color,
      Button.OnClickListener click) {
    return content.add(new Button.Builder(getContext(), id, image, label, rect)
        .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(px(66f)).setFont(NativeFonts.INTER)
        .setFontVariations(FontVariation.SEMI_BOLD).setTextSizePx(sp(13)).setTextColor(color)
        .setRippleEnabled(true).setWaitForRippleBeforeClick(true).setRippleColor(0x33FFFFFF).setOnClickListener(click));
  }
  @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
  @Override public boolean onTouchEvent(MotionEvent event) {
    return layers.onTouchEvent(event) || super.onTouchEvent(event);
  }
  public void release() { layers.release(); recycle(light, white, control, selected, danger, accept, disabled, profile); }
  private Bitmap avatar() {
    int size = Math.round(px(495f)); Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(0xFFD9F1F7); canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
    paint.setColor(ACCENT); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(size * .28f);
    Paint.FontMetrics metrics = paint.getFontMetrics();
    canvas.drawText("☎", size / 2f, size / 2f - (metrics.ascent + metrics.descent) / 2f, paint);
    return bitmap;
  }
  private float px(float v) {
    return figmaConfig.toRuntime(v, Math.max(1, getResources().getDisplayMetrics().widthPixels));
  }
  private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
  private static Bitmap color(int c) { Bitmap b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); b.eraseColor(c); return b; }
  private static void recycle(Bitmap... values) {
    for (Bitmap value : values) if (value != null && !value.isRecycled()) value.recycle();
  }
  public interface Listener {
    void onBack(); void onAccept(); void onReject(); void onSpeaker(); void onMute(); void onEnd();
  }
}
