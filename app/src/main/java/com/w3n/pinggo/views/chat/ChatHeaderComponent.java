package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import com.w3n.pinggo.R;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;

/** Owns the standard conversation header and its presence state. */
public final class ChatHeaderComponent {
  private static final int PRIMARY = 0xFF000E1A;
  private static final int SECONDARY = 0xFF687382;
  private final Context context;
  private final String chatName;
  private final ChatViewListener listener;
  private final Bitmap statusBarBackground;
  private final Bitmap headerBackground;
  private final Bitmap transparent;
  private final Bitmap profile;
  private final Bitmap back;
  private final Bitmap voiceCall;
  private final Bitmap videoCall;
  private final Bitmap more;
  private Text presence;
  private String presenceValue = "connecting...";
  private boolean callActionsVisible = true;
  private boolean profileVisible = true;

  /** Simplified details header using the conversation header's exact geometry and assets. */
  public static View detailsHeader(
      Context context, String titleValue, Runnable onBack, java.util.function.Consumer<View> onMore) {
    return new View(context) {
      final com.ogfa.nativeviews.zlayer.ZLayerGroup layers =
          new com.ogfa.nativeviews.zlayer.ZLayerGroup(this);
      final ZLayer layer = layers.addLayer("details_header");
      final Bitmap background = BitmapFactory.decodeResource(getResources(),
          R.drawable.conversation_header_background);
      final Bitmap backIcon = BitmapFactory.decodeResource(getResources(), R.drawable.conversation_back);
      final Bitmap moreIcon = BitmapFactory.decodeResource(getResources(), R.drawable.home_overflow_dots);
      final Bitmap clear = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
      @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, Math.max(1, Math.round(width * 170f / 1080f)));
      }
      @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        layer.clear(); float scale = width / 1080f;
        layer.add(new Image.Builder(context, "background", background, new RectF(0, 0, width, height))
            .setScaleType(Image.ScaleType.FIT_XY));
        layer.add(new Image.Builder(context, "back_icon", backIcon, new RectF(51f * scale,
            60f * scale, 102f * scale, 111f * scale)).setScaleType(Image.ScaleType.FIT_CENTER));
        layer.add(new Button.Builder(context, "back_touch", clear, "", new RectF(25f * scale,
            34f * scale, 128f * scale, 137f * scale)).setRippleEnabled(true)
            .setOnClickListener(id -> onBack.run()));
        layer.add(new Text.Builder(context, "title", titleValue == null ? "" : titleValue,
            new RectF(152f * scale, 0, 950f * scale, height)).setFont(NativeFonts.INTER)
            .setFontVariations(FontVariation.BOLD).setTextSizePx(50f * scale).setTextColor(PRIMARY)
            .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(1));
        layer.add(new Image.Builder(context, "more_icon", moreIcon, new RectF(1000f * scale,
            55f * scale, 1032f * scale, 112f * scale)).setScaleType(Image.ScaleType.FIT_CENTER));
        layer.add(new Button.Builder(context, "more_touch", clear, "", new RectF(972f * scale,
            27f * scale, 1060f * scale, 140f * scale)).setRippleEnabled(true)
            .setOnClickListener(id -> { if (onMore != null) onMore.accept(this); }));
      }
      @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
      @Override public boolean onTouchEvent(MotionEvent event) {
        return layers.onTouchEvent(event) || super.onTouchEvent(event);
      }
    };
  }

  ChatHeaderComponent(
      Context context, String chatName, ChatViewListener listener, Bitmap statusBarBackground,
      Bitmap headerBackground, Bitmap transparent, Bitmap profile, Bitmap back,
      Bitmap voiceCall, Bitmap videoCall, Bitmap more) {
    this.context = context;
    this.chatName = chatName;
    this.listener = listener;
    this.statusBarBackground = statusBarBackground;
    this.headerBackground = headerBackground;
    this.transparent = transparent;
    this.profile = profile;
    this.back = back;
    this.voiceCall = voiceCall;
    this.videoCall = videoCall;
    this.more = more;
  }

  float build(ZLayer background, ZLayer content, float width, float top, float scale) {
    float bottom = top + 170f * scale;
    // Hiding the status bar dispatches a legitimate zero top inset. The native Image
    // primitive rejects zero-height bounds, so omit this layer until the inset returns.
    if (top > 0f && Float.isFinite(top)) {
      background.add(new Image.Builder(context, "status_bar_background", statusBarBackground,
          new RectF(0, 0, width, top)).setScaleType(Image.ScaleType.FIT_XY));
    }
    background.add(new Image.Builder(context, "header_background", headerBackground,
        new RectF(0, top, width, bottom)).setScaleType(Image.ScaleType.FIT_XY));
    iconButton(content, "back", back,
        new RectF(51f * scale, top + 60f * scale, 102f * scale, top + 111f * scale),
        new RectF(25f * scale, top + 34f * scale, 128f * scale, top + 137f * scale),
        id -> listener.onBack());
    Image profileImage = content.add(new Image.Builder(context, "profile", profile,
        new RectF(152f * scale, top + 34f * scale, 254f * scale, top + 136f * scale))
        .setScaleType(Image.ScaleType.CENTER_CROP));
    profileImage.setVisible(profileVisible);
    text(content, "name", chatName,
        new RectF(285f * scale, top + 42f * scale, 742f * scale, top + 91f * scale),
        38f * scale, PRIMARY, FontVariation.MEDIUM);
    presence = text(content, "presence", presenceValue,
        new RectF(285f * scale, top + 95f * scale, 742f * scale, top + 139f * scale),
        31f * scale, SECONDARY, FontVariation.REGULAR);
    presence.setVisible(!presenceValue.isEmpty());
    // One transparent target covers the avatar and labels, stopping 25 Figma px
    // before the voice-call target so the actions never overlap.
    content.add(new Button.Builder(context, "chat_details_touch", transparent, "",
        new RectF(135f * scale, top + 20f * scale, 700f * scale, top + 150f * scale))
        .setImageScaleType(Image.ScaleType.FIT_XY)
        .setCornerRadiusPx(0)
        .setRippleEnabled(true).setWaitForRippleBeforeClick(true)
        .setRippleColor(0x10019CC4)
        .setOnClickListener(id -> listener.onChatDetails()));
    if (callActionsVisible) {
      iconButton(content, "video_call", videoCall,
          new RectF(869f * scale, top + 55f * scale, 926f * scale, top + 112f * scale),
          new RectF(844f * scale, top + 30f * scale, 951f * scale, top + 137f * scale),
          id -> listener.onVideoCall());
      iconButton(content, "voice_call", voiceCall,
          new RectF(750f * scale, top + 55f * scale, 807f * scale, top + 112f * scale),
          new RectF(725f * scale, top + 30f * scale, 832f * scale, top + 137f * scale),
          id -> listener.onVoiceCall());
    }
    iconButton(content, "more", more,
        new RectF(1000f * scale, top + 55f * scale, 1032f * scale, top + 112f * scale),
        new RectF(972f * scale, top + 27f * scale, 1060f * scale, top + 140f * scale),
        id -> listener.onMore());
    return bottom;
  }

  void setPresence(String value) {
    presenceValue = value == null ? "" : value;
    if (presence != null) presence.setText(presenceValue).setVisible(!presenceValue.isEmpty());
  }

  void setProfileVisible(boolean visible) {
    profileVisible = visible;
  }

  void setCallActionsVisible(boolean visible) { callActionsVisible = visible; }

  private Text text(
      ZLayer layer, String id, String value, RectF region, float size, int color,
      FontVariation variation) {
    return layer.add(new Text.Builder(context, id, value, region)
        .setFont(NativeFonts.INTER)
        .setFontVariations(variation)
        .setTextSizePx(size)
        .setTextColor(color)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER)
        .setAlignment(Text.Alignment.START)
        .setMaxLines(1));
  }

  private Button iconButton(
      ZLayer layer, String id, Bitmap icon, RectF iconBounds, RectF touchBounds,
      Button.OnClickListener click) {
    layer.add(new Image.Builder(context, id + "_icon", icon, iconBounds)
        .setScaleType(Image.ScaleType.FIT_CENTER));
    return layer.add(new Button.Builder(context, id + "_touch", transparent, "", touchBounds)
        .setImageScaleType(Image.ScaleType.FIT_XY)
        .setCornerRadiusPx(0)
        .setRippleEnabled(true).setWaitForRippleBeforeClick(true)
        .setRippleColor(0x16019CC4)
        .setOnClickListener(click));
  }
}
