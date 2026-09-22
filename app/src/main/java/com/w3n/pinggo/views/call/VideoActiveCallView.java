package com.w3n.pinggo.views.call;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import java.util.ArrayList;
import java.util.List;

/** Self-contained active video-call screen. */
public final class VideoActiveCallView extends View {
  private static final String TILE_SWAP_TAG = "PingGoTileSwap";
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
  private static final int ACCENT = 0xFF019CC4;
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer background = layers.addLayer("background"), content = layers.addLayer("content");
  private final Listener listener;
  private final String phone;
  private final Bitmap profile, white = color(Color.WHITE), control = color(0xFF26333E);
  private final Bitmap selected = color(ACCENT), danger = color(0xFFE53935);
  private final Bitmap disabled = color(0xFF66717B);
  private int topInset, bottomInset;
  private boolean speakerOn, muted, held, cameraEnabled = true, incomingPrompt, callConnected, remoteMuted;
  private String callStatus = "Connecting…";
  private boolean conferenceMode;
  private String participantSummary = "";
  private boolean addMemberVisible;
  private boolean remoteCameraEnabled = true, buildPosted, released;
  private boolean callOnHold;
  private boolean peerHeld;
  private final ArrayList<String> heldCallIds = new ArrayList<>();
  private final ArrayList<String> heldCallNames = new ArrayList<>();

  private void requestBuild() {
    if (released || getWidth() <= 0 || buildPosted) return;
    buildPosted = true;
    post(() -> { buildPosted = false; if (!released) build(); });
  }

  public void setRemoteCameraEnabled(boolean enabled) {
    if (remoteCameraEnabled == enabled) return;
    remoteCameraEnabled = enabled;
    requestBuild();
  }
  public void setCallOnHold(boolean held) {
    if (callOnHold == held) return;
    callOnHold = held;
    requestBuild();
  }
  public void setPeerHeld(boolean held) {
    if (peerHeld == held) return;
    peerHeld = held;
    requestBuild();
  }
  public void setHeldCalls(List<String> ids, List<String> names) {
    ArrayList<String> newIds = ids == null ? new ArrayList<>() : new ArrayList<>(ids);
    ArrayList<String> newNames = names == null ? new ArrayList<>() : new ArrayList<>(names);
    if (heldCallIds.equals(newIds) && heldCallNames.equals(newNames)) return;
    heldCallIds.clear(); heldCallIds.addAll(newIds);
    heldCallNames.clear(); heldCallNames.addAll(newNames);
    requestBuild();
  }

  public VideoActiveCallView(Context context, String phone, String profilePath, Listener listener) {
    super(context);
    this.phone = phone == null || phone.trim().isEmpty() ? "Unknown" : phone;
    this.listener = listener;
    Bitmap decoded = profilePath == null ? null : BitmapFactory.decodeFile(profilePath);
    profile = circularBitmap(decoded == null ? avatar() : decoded);
    setClickable(true);
  }
  public void setInsets(int top, int bottom) {
    topInset = Math.max(0, top); bottomInset = Math.max(0, bottom);
    if (getWidth() > 0) build();
  }
  public void setAudioState(boolean speaker, boolean mute) {
    if (speakerOn == speaker && muted == mute) return;
    speakerOn = speaker; muted = mute;
    requestBuild();
  }
  public void setHeld(boolean value) {
    if (held == value) return;
    held = value;
    requestBuild();
  }
  public void setCallStatus(String status) {
    if (java.util.Objects.equals(callStatus, status)) return;
    callStatus = status == null || status.trim().isEmpty() ? "Video call" : status;
    if (getWidth() > 0) build();
  }
  public void setConferenceParticipants(boolean conference, String participants) {
    if (conferenceMode == conference && java.util.Objects.equals(participantSummary, participants)) return;
    conferenceMode = conference;
    participantSummary = participants == null ? "" : participants.trim();
    if (getWidth() > 0) build();
  }
  public void setCameraEnabled(boolean enabled) {
    if (cameraEnabled == enabled) return;
    cameraEnabled = enabled;
    requestBuild();
  }
  public void setCallConnected(boolean connected) {
    if (callConnected == connected) return;
    callConnected = connected;
    if (!connected) { muted = false; held = false; }
    if (getWidth() > 0) build();
  }
  public void setAddMemberVisible(boolean visible) {
    addMemberVisible = visible;
    if (getWidth() > 0) build();
  }
  public void setRemoteMuted(boolean value) {
    if (remoteMuted == value) return;
    remoteMuted = value;
    requestBuild();
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
    float w = getWidth(), h = getHeight(), top = topInset + px(27.5f);
    int overlayTextColor = conferenceMode ? 0xFF000E1A : Color.WHITE;
    if (callOnHold || !remoteCameraEnabled) {
      background.add(new Image.Builder(getContext(), "paused_video_bg", control,
          new RectF(0, 0, w, h)).setScaleType(Image.ScaleType.FIT_XY));
      float avatarSize = Math.min(px(440f), w * .46f);
      float avatarTop = Math.max(top + px(210f), h * .22f);
      background.add(new Image.Builder(getContext(), "paused_video_profile", profile,
          new RectF(w / 2f - avatarSize / 2f, avatarTop,
              w / 2f + avatarSize / 2f, avatarTop + avatarSize))
          .setScaleType(Image.ScaleType.CENTER_CROP));
    }
    // Video occupies the complete window. Navigation and controls are overlays,
    // not opaque header/footer shelves that crop the camera feed.
    button("back", control, "‹",
        new RectF(px(27.5f), top, px(159.5f), top + px(132f)), Color.WHITE,
        id -> listener.onBack());
    content.add(new Image.Builder(getContext(), "floating_profile", profile,
        new RectF(px(176f), top + px(11f), px(286f), top + px(121f)))
        .setScaleType(Image.ScaleType.CENTER_CROP));
    text("floating_contact", conferenceMode ? "Conference call" : phone,
        new RectF(px(308f), top, w - px(44f), top + px(132f)), sp(17), overlayTextColor,
        FontVariation.SEMI_BOLD, Text.Alignment.START);
    if (conferenceMode && !participantSummary.isEmpty()) {
      text("participants", participantSummary,
          new RectF(px(308f), top + px(66f), w - px(44f), top + px(132f)),
          sp(12), overlayTextColor, FontVariation.REGULAR, Text.Alignment.START);
    }
    text("status", displayedStatus(), new RectF(px(66f), top + px(137.5f), w - px(66f), top + px(302.5f)),
        sp(22), ACCENT, FontVariation.SEMI_BOLD, Text.Alignment.CENTER);
    buildHeldCallList(w, top + px(270f), overlayTextColor);
    if (remoteMuted) {
      float muteTop = heldCallIds.isEmpty() ? top + px(288.75f)
          : top + px(270f + heldCallIds.size() * 92f);
      text("remote_mute", phone + " is muted", new RectF(px(66f), muteTop, w - px(66f),
          muteTop + px(110f)), sp(14), overlayTextColor, FontVariation.REGULAR, Text.Alignment.CENTER);
    } else if (muted) {
      float muteTop = heldCallIds.isEmpty() ? top + px(288.75f)
          : top + px(270f + heldCallIds.size() * 92f);
      text("local_mute", "You are muted", new RectF(px(66f), muteTop, w - px(66f),
          muteTop + px(110f)), sp(14), overlayTextColor, FontVariation.REGULAR, Text.Alignment.CENTER);
    }
    if (incomingPrompt) incomingControls(w, h); else controls(w, h);
    if (!remoteCameraEnabled) {
      float cameraTop = heldCallIds.isEmpty() ? top + px(398.75f)
          : top + px(375f + heldCallIds.size() * 92f);
      text("remote_camera", phone + " • Camera off",
          new RectF(px(66f), cameraTop, w - px(66f), cameraTop + px(91.25f)),
          sp(14), overlayTextColor, FontVariation.REGULAR, Text.Alignment.CENTER);
    }
    invalidate();
  }
  private void incomingControls(float w, float h) {
    float bottom = h - bottomInset - px(77f), top = bottom - px(187f), gap = px(77f);
    float width = Math.min(px(357.5f), (w - px(132f) - gap) / 2);
    float x = (w - width * 2 - gap) / 2;
    button("reject", danger, "Reject", new RectF(x, top, x + width, bottom), Color.WHITE,
        id -> listener.onReject());
    x += width + gap;
    button("accept", selected, "Accept", new RectF(x, top, x + width, bottom), Color.WHITE,
        id -> listener.onAccept());
  }
  private void controls(float w, float h) {
    float bottom = h - bottomInset - px(66f), top = bottom - px(176f), gap = px(22f);
    int count = addMemberVisible ? 6 : 5;
    float width = Math.min(px(192.5f), (w - px(44f) - gap * (count - 1)) / count);
    float x = (w - (width * count + gap * (count - 1))) / 2;
    boolean interactive = callConnected && !peerHeld && !held;
    boolean holdInteractive = callConnected && !peerHeld;
    Button flipButton = button("flip", interactive && cameraEnabled ? control : disabled,
        "Flip", new RectF(x, top, x + width, bottom), Color.WHITE,
        id -> { if (interactive && cameraEnabled) listener.onFlipCamera(); });
    flipButton.setEnabled(interactive && cameraEnabled);
    x += width + gap;
    Button cameraButton = button("camera", !interactive ? disabled
            : cameraEnabled ? selected : control,
        cameraEnabled ? "Camera" : "Camera off",
        new RectF(x, top, x + width, bottom), Color.WHITE,
        id -> { if (interactive) listener.onCamera(); });
    cameraButton.setEnabled(interactive);
    x += width + gap;
    Button speakerButton = button("speaker", !interactive ? disabled
            : speakerOn ? selected : control, speakerOn ? "Speaker on" : "Speaker",
        new RectF(x, top, x + width, bottom), Color.WHITE,
        id -> { if (interactive) listener.onSpeaker(); });
    speakerButton.setEnabled(interactive);
    x += width + gap;
    Button muteButton = button("mute", !interactive ? disabled : muted ? selected : control,
        muted ? "Unmute" : "Mute", new RectF(x, top, x + width, bottom), Color.WHITE,
        id -> { if (interactive) listener.onMute(); });
    muteButton.setEnabled(interactive);
    x += width + gap;
    if (addMemberVisible) {
      Button addButton = button("add_member", interactive ? control : disabled, "Add",
          new RectF(x, top, x + width, bottom), Color.WHITE,
          id -> { if (interactive) listener.onAddMember(); });
      addButton.setEnabled(interactive);
      x += width + gap;
    }
    button("end", danger, "End", new RectF(x, top, x + width, bottom), Color.WHITE,
        id -> listener.onEnd());
    float holdWidth = px(192.5f), holdBottom = top - px(22f), holdTop = holdBottom - px(132f);
    Button holdButton = button("hold", !holdInteractive ? disabled : held ? selected : control,
        held ? "Resume" : "Hold",
        new RectF(w / 2f - holdWidth / 2f, holdTop, w / 2f + holdWidth / 2f, holdBottom),
        Color.WHITE, id -> { if (holdInteractive) listener.onHold(); });
    holdButton.setEnabled(holdInteractive);
  }
  private void buildHeldCallList(float width, float top, int textColor) {
    float rowHeight = px(78f), gap = px(14f), swapWidth = px(190f);
    for (int index = 0; index < heldCallIds.size(); index++) {
      String callId = heldCallIds.get(index);
      String callName = index < heldCallNames.size() && !heldCallNames.get(index).isEmpty()
          ? heldCallNames.get(index) : "Another call";
      float rowTop = top + index * (rowHeight + gap);
      text("held_call_" + index, callName + " • On hold",
          new RectF(px(66f), rowTop, width - swapWidth - px(88f), rowTop + rowHeight),
          sp(14), textColor, FontVariation.SEMI_BOLD, Text.Alignment.START);
      button("swap_call_" + index, selected, "Swap",
          new RectF(width - swapWidth - px(66f), rowTop,
              width - px(66f), rowTop + rowHeight), Color.WHITE,
          id -> listener.onSwapCall(callId));
    }
  }
  private String displayedStatus() {
    if (!peerHeld || !callStatus.startsWith("Call on hold")) return callStatus;
    return phone + " put the call on hold" + callStatus.substring("Call on hold".length());
  }
  private void text(String id, String value, RectF rect, float size, int color,
      FontVariation weight, Text.Alignment alignment) {
    content.add(new Text.Builder(getContext(), id, value, rect)
        .setFont(com.w3n.pinggo.views.home.ListFonts.inter(getContext(), weight))
        .clearFontVariations().setTextSizePx(size).setTextColor(color).setAlignment(alignment)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(1));
  }
  private Button button(String id, Bitmap image, String label, RectF rect, int color,
      Button.OnClickListener click) {
    return content.add(new Button.Builder(getContext(), id, image, label, rect)
        .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(px(66f))
        .setTextStyle(new com.ogfa.nativeviews.text.TextStyle.Builder()
            .setFont(com.w3n.pinggo.views.home.ListFonts.inter(getContext(), FontVariation.SEMI_BOLD))
            .clearFontVariations().setTextSizePx(sp(13)).setTextColor(color)
            .setAlignment(Text.Alignment.CENTER).setVerticalAlignment(Text.VerticalAlignment.CENTER)
            .setMaxLines(1).build())
        .setRippleEnabled(true).setWaitForRippleBeforeClick(false).setRippleColor(0x33FFFFFF).setOnClickListener(click));
  }
  @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
  @Override public boolean onTouchEvent(MotionEvent event) {
    boolean layersHandled = layers.onTouchEvent(event);
    // Conference video controls are drawn over the participant grid. Only actual
    // controls consume the event; empty overlay space belongs to tiles underneath.
    boolean fallbackHandled = !layersHandled && !conferenceMode && super.onTouchEvent(event);
    if (conferenceMode && (event.getActionMasked() == MotionEvent.ACTION_DOWN
        || event.getActionMasked() == MotionEvent.ACTION_UP
        || event.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
      Log.i(TILE_SWAP_TAG, "touch_video_overlay action="
          + MotionEvent.actionToString(event.getActionMasked())
          + " x=" + event.getX() + " y=" + event.getY()
          + " nativeLayers=" + layersHandled + " fallback=" + fallbackHandled
          + " handled=" + (layersHandled || fallbackHandled));
    }
    return layersHandled || fallbackHandled;
  }
  public void release() { released = true; layers.release(); recycle(white, control, selected, danger, disabled, profile); }
  private Bitmap avatar() {
    int size = Math.round(px(495f)); Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(0xFFD9F1F7); canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
    paint.setColor(ACCENT); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(size * .26f);
    Paint.FontMetrics metrics = paint.getFontMetrics();
    canvas.drawText("▣", size / 2f, size / 2f - (metrics.ascent + metrics.descent) / 2f, paint);
    return bitmap;
  }
  private static Bitmap circularBitmap(Bitmap source) {
    if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) return source;
    int size = Math.min(source.getWidth(), source.getHeight());
    float left = (source.getWidth() - size) / 2f;
    float top = (source.getHeight() - size) / 2f;
    Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(result);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    android.graphics.Matrix matrix = new android.graphics.Matrix();
    matrix.setTranslate(-left, -top);
    shader.setLocalMatrix(matrix);
    paint.setShader(shader);
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
    source.recycle();
    return result;
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
    void onBack(); void onSpeaker(); void onMute(); void onEnd();
    void onFlipCamera(); void onCamera();
    void onAccept(); void onReject();
    default void onHold() {}
    default void onAddMember() {}
    default void onSwapCall(String callId) {}
  }
}
