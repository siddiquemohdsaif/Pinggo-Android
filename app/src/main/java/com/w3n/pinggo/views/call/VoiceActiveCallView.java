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
    float w = getWidth(), h = getHeight(), top = topInset + dp(10);
    background.add(new Image.Builder(getContext(), "bg", light, new RectF(0, 0, w, h))
        .setScaleType(Image.ScaleType.FIT_XY));
    button("back", white, "‹", new RectF(dp(10), top, dp(58), top + dp(48)), 0xFF000E1A,
        id -> listener.onBack());
    text("phone", phone, new RectF(dp(68), top, w - dp(20), top + dp(48)), sp(20), 0xFF000E1A,
        FontVariation.SEMI_BOLD, Text.Alignment.START);
    float size = Math.min(dp(168), w * .44f), avatarTop = top + dp(126);
    content.add(new Image.Builder(getContext(), "profile", profile,
        new RectF(w / 2 - size / 2, avatarTop, w / 2 + size / 2, avatarTop + size))
        .setScaleType(Image.ScaleType.CENTER_CROP));
    text("status", callStatus, new RectF(dp(24), avatarTop + size + dp(22), w - dp(24),
        avatarTop + size + dp(68)), sp(18), ACCENT, FontVariation.SEMI_BOLD, Text.Alignment.CENTER);
    if (remoteMuted) {
      text("remote_mute", "Muted himself",
          new RectF(dp(24), avatarTop + size + dp(66), w - dp(24), avatarTop + size + dp(104)),
          sp(14), 0xFF687382, FontVariation.REGULAR, Text.Alignment.CENTER);
    }
    if (incomingPrompt) incomingControls(w, h); else controls(w, h);
    invalidate();
  }

  private void incomingControls(float w, float h) {
    float bottom = h - bottomInset - dp(24), top = bottom - dp(64), width = dp(118), gap = dp(28);
    float x = (w - width * 2 - gap) / 2;
    button("reject", danger, "Reject", new RectF(x, top, x + width, bottom), Color.WHITE,
        id -> listener.onReject());
    x += width + gap;
    button("accept", accept, "Accept", new RectF(x, top, x + width, bottom), Color.WHITE,
        id -> listener.onAccept());
  }

  private void controls(float w, float h) {
    float bottom = h - bottomInset - dp(24), top = bottom - dp(64), gap = dp(18);
    float width = Math.min(dp(92), (w - dp(48) - gap * 2) / 3);
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
        .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(dp(24)).setFont(NativeFonts.INTER)
        .setFontVariations(FontVariation.SEMI_BOLD).setTextSizePx(sp(13)).setTextColor(color)
        .setRippleEnabled(true).setRippleColor(0x33FFFFFF).setOnClickListener(click));
  }
  @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
  @Override public boolean onTouchEvent(MotionEvent event) {
    return layers.onTouchEvent(event) || super.onTouchEvent(event);
  }
  public void release() { layers.release(); recycle(light, white, control, selected, danger, accept, disabled, profile); }
  private Bitmap avatar() {
    int size = Math.round(dp(180)); Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(0xFFD9F1F7); canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
    paint.setColor(ACCENT); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(size * .28f);
    Paint.FontMetrics metrics = paint.getFontMetrics();
    canvas.drawText("☎", size / 2f, size / 2f - (metrics.ascent + metrics.descent) / 2f, paint);
    return bitmap;
  }
  private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
  private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }
  private static Bitmap color(int c) { Bitmap b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); b.eraseColor(c); return b; }
  private static void recycle(Bitmap... values) {
    for (Bitmap value : values) if (value != null && !value.isRecycled()) value.recycle();
  }
  public interface Listener {
    void onBack(); void onAccept(); void onReject(); void onSpeaker(); void onMute(); void onEnd();
  }
}
