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
  private final Bitmap disabled = color(0xFF66717B);
  private int topInset, bottomInset;
  private boolean speakerOn, muted, cameraEnabled = true, incomingPrompt, callConnected, remoteMuted;
  private String callStatus = "Connecting…";

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
  public void setCallStatus(String status) {
    callStatus = status == null || status.trim().isEmpty() ? "Video call" : status;
    if (getWidth() > 0) build();
  }
  public void setCameraEnabled(boolean enabled) {
    cameraEnabled = enabled;
    if (getWidth() > 0) build();
  }
  public void setCallConnected(boolean connected) {
    callConnected = connected;
    if (!connected) muted = false;
    if (getWidth() > 0) build();
  }
  public void setRemoteMuted(boolean value) {
    remoteMuted = value;
    if (getWidth() > 0) build();
  }
  public void showIncomingPrompt(boolean show) {
    incomingPrompt = show;
    if (getWidth() > 0) build();
  }
  @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh); if (w > 0 && h > 0) build();
  }
  private void build() {
    background.clear(); content.clear();
    float w = getWidth(), h = getHeight(), top = topInset + dp(10);
    button("back", control, "‹", new RectF(dp(10), top, dp(58), top + dp(48)), Color.WHITE,
        id -> listener.onBack());
    content.add(new Image.Builder(getContext(), "header_profile", profile,
        new RectF(dp(68), top + dp(4), dp(108), top + dp(44))).setScaleType(Image.ScaleType.CENTER_CROP));
    text("phone", phone, new RectF(dp(118), top, w - dp(16), top + dp(48)), sp(17), Color.WHITE,
        FontVariation.SEMI_BOLD, Text.Alignment.START);
    text("status", callStatus, new RectF(dp(24), top + dp(90), w - dp(24), top + dp(150)),
        sp(22), Color.WHITE, FontVariation.SEMI_BOLD, Text.Alignment.CENTER);
    if (remoteMuted) {
      text("remote_mute", phone + " is muted", new RectF(dp(24), top + dp(145), w - dp(24),
          top + dp(185)), sp(14), 0xFFCCD3D9, FontVariation.REGULAR, Text.Alignment.CENTER);
    } else if (muted) {
      text("local_mute", "You are muted", new RectF(dp(24), top + dp(145), w - dp(24),
          top + dp(185)), sp(14), 0xFFCCD3D9, FontVariation.REGULAR, Text.Alignment.CENTER);
    }
    if (incomingPrompt) incomingControls(w, h); else controls(w, h);
    invalidate();
  }
  private void incomingControls(float w, float h) {
    float bottom = h - bottomInset - dp(28), top = bottom - dp(68), gap = dp(28);
    float width = Math.min(dp(130), (w - dp(48) - gap) / 2);
    float x = (w - width * 2 - gap) / 2;
    button("reject", danger, "Reject", new RectF(x, top, x + width, bottom), Color.WHITE,
        id -> listener.onReject());
    x += width + gap;
    button("accept", selected, "Accept", new RectF(x, top, x + width, bottom), Color.WHITE,
        id -> listener.onAccept());
  }
  private void controls(float w, float h) {
    float bottom = h - bottomInset - dp(24), top = bottom - dp(64), gap = dp(8);
    float width = Math.min(dp(70), (w - dp(24) - gap * 4) / 5);
    float x = (w - (width * 5 + gap * 4)) / 2;
    button("flip", control, "Flip", new RectF(x, top, x + width, bottom), Color.WHITE,
        id -> listener.onFlipCamera());
    x += width + gap;
    button("camera", cameraEnabled ? selected : control, cameraEnabled ? "Camera" : "Camera off",
        new RectF(x, top, x + width, bottom), Color.WHITE, id -> listener.onCamera());
    x += width + gap;
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
  public void release() { layers.release(); recycle(dark, control, selected, danger, disabled, profile); }
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
  public interface Listener {
    void onBack(); void onSpeaker(); void onMute(); void onEnd();
    void onFlipCamera(); void onCamera();
    void onAccept(); void onReject();
  }
}
