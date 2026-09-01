package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;

/** Owns the standard conversation header and its presence state. */
final class ChatHeaderComponent {
  private static final int PRIMARY = 0xFF000E1A;
  private static final int SECONDARY = 0xFF687382;
  private final Context context;
  private final String chatName;
  private final ChatViewListener listener;
  private final Bitmap white;
  private final Bitmap headerBackground;
  private final Bitmap transparent;
  private final Bitmap profile;
  private final Bitmap back;
  private final Bitmap voiceCall;
  private final Bitmap videoCall;
  private final Bitmap more;
  private Text presence;
  private String presenceValue = "connecting...";

  ChatHeaderComponent(
      Context context, String chatName, ChatViewListener listener, Bitmap white,
      Bitmap headerBackground, Bitmap transparent, Bitmap profile, Bitmap back,
      Bitmap voiceCall, Bitmap videoCall, Bitmap more) {
    this.context = context;
    this.chatName = chatName;
    this.listener = listener;
    this.white = white;
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
    background.add(new Image.Builder(context, "status_bar_background", white,
        new RectF(0, 0, width, top)).setScaleType(Image.ScaleType.FIT_XY));
    background.add(new Image.Builder(context, "header_background", headerBackground,
        new RectF(0, top, width, bottom)).setScaleType(Image.ScaleType.FIT_XY));
    iconButton(content, "back", back,
        new RectF(51f * scale, top + 60f * scale, 102f * scale, top + 111f * scale),
        new RectF(25f * scale, top + 34f * scale, 128f * scale, top + 137f * scale),
        id -> listener.onBack());
    content.add(new Image.Builder(context, "profile", profile,
        new RectF(152f * scale, top + 34f * scale, 254f * scale, top + 136f * scale))
        .setScaleType(Image.ScaleType.CENTER_CROP));
    text(content, "name", chatName,
        new RectF(285f * scale, top + 42f * scale, 742f * scale, top + 91f * scale),
        38f * scale, PRIMARY, FontVariation.MEDIUM);
    presence = text(content, "presence", presenceValue,
        new RectF(285f * scale, top + 95f * scale, 742f * scale, top + 139f * scale),
        31f * scale, SECONDARY, FontVariation.REGULAR);
    presence.setVisible(!presenceValue.isEmpty());
    iconButton(content, "video_call", videoCall,
        new RectF(869f * scale, top + 55f * scale, 926f * scale, top + 112f * scale),
        new RectF(844f * scale, top + 30f * scale, 951f * scale, top + 137f * scale),
        id -> listener.onVideoCall());
    iconButton(content, "voice_call", voiceCall,
        new RectF(750f * scale, top + 55f * scale, 807f * scale, top + 112f * scale),
        new RectF(725f * scale, top + 30f * scale, 832f * scale, top + 137f * scale),
        id -> listener.onVoiceCall());
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
        .setRippleEnabled(true)
        .setRippleColor(0x16019CC4)
        .setOnClickListener(click));
  }
}
