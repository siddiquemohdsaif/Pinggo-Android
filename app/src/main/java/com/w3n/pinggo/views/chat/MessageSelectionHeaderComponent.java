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
import com.w3n.pinggo.data.local.MessageEntity;
import java.util.List;
import java.util.function.Predicate;

/** Builds and owns the action header shown while chat messages are selected. */
final class MessageSelectionHeaderComponent {
  private static final int SECONDARY = 0xFF687382;
  private final Context context;
  private final ChatViewListener listener;
  private final Runnable clearSelection;
  private final Predicate<MessageEntity> pinnedByCurrentUser;
  private final Bitmap headerBackground;
  private final Bitmap statusBackground;
  private final Bitmap transparent;
  private final Bitmap back;
  private final Bitmap reply;
  private final Bitmap copy;
  private final Bitmap forward;
  private final Bitmap pin;
  private final Bitmap unpin;
  private final Bitmap delete;

  MessageSelectionHeaderComponent(
      Context context,
      ChatViewListener listener,
      Runnable clearSelection,
      Predicate<MessageEntity> pinnedByCurrentUser,
      Bitmap headerBackground,
      Bitmap statusBackground,
      Bitmap transparent,
      Bitmap back,
      Bitmap reply,
      Bitmap copy,
      Bitmap forward,
      Bitmap pin,
      Bitmap unpin,
      Bitmap delete) {
    this.context = context;
    this.listener = listener;
    this.clearSelection = clearSelection;
    this.pinnedByCurrentUser = pinnedByCurrentUser;
    this.headerBackground = headerBackground;
    this.statusBackground = statusBackground;
    this.transparent = transparent;
    this.back = back;
    this.reply = reply;
    this.copy = copy;
    this.forward = forward;
    this.pin = pin;
    this.unpin = unpin;
    this.delete = delete;
  }

  void build(ZLayer layer, List<MessageEntity> selected, float width, float top, float scale) {
    layer.add(new Image.Builder(context, "message_selection_status_bar", statusBackground,
        new RectF(0f, 0f, width, top)).setScaleType(Image.ScaleType.FIT_XY));
    layer.add(new Image.Builder(context, "message_selection_header", headerBackground,
        new RectF(0f, top, width, top + 170f * scale)).setScaleType(Image.ScaleType.FIT_XY));
    layer.add(new Button.Builder(context, "message_selection_header_touch", transparent, "",
        new RectF(0f, top, width, top + 170f * scale))
        .setImageScaleType(Image.ScaleType.FIT_XY)
        .setRippleEnabled(false)
        .setOnClickListener(id -> { }));
    iconButton(layer, "message_selection_back", back,
        new RectF(51f * scale, top + 60f * scale, 102f * scale, top + 111f * scale),
        new RectF(25f * scale, top + 34f * scale, 128f * scale, top + 137f * scale),
        id -> clearSelection.run());
    layer.add(new Text.Builder(context, "message_selection_count",
        String.valueOf(selected.size()),
        new RectF(165f * scale, top + 57f * scale, 300f * scale, top + 117f * scale))
        .setFont(NativeFonts.INTER)
        .setFontVariations(FontVariation.MEDIUM)
        .setTextSizePx(50f * scale)
        .setTextColor(SECONDARY)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER)
        .setAlignment(Text.Alignment.START)
        .setMaxLines(1));
    if (selected.size() == 1) {
      action(layer, "reply", reply, 535f, top, scale,
          () -> listener.onReplySelected(selected.get(0)));
    }
    action(layer, "copy", copy, 645f, top, scale,
        () -> listener.onCopySelected(selected));
    action(layer, "forward", forward, 755f, top, scale,
        () -> listener.onForwardSelected(selected));
    boolean allPinned = !selected.isEmpty();
    for (MessageEntity message : selected) allPinned &= pinnedByCurrentUser.test(message);
    final boolean shouldUnpin = allPinned;
    action(layer, shouldUnpin ? "unpin" : "pin", shouldUnpin ? unpin : pin,
        865f, top, scale,
        () -> {
          if (shouldUnpin) listener.onUnpinSelected(selected);
          else listener.onPinSelected(selected);
        });
    action(layer, "delete", delete, 975f, top, scale,
        () -> listener.onDeleteSelected(selected));
  }

  private void action(
      ZLayer layer, String id, Bitmap icon, float centerX, float top, float scale,
      Runnable action) {
    float half = 25.5f;
    iconButton(layer, "message_selection_" + id, icon,
        new RectF((centerX - half) * scale, top + 60f * scale,
            (centerX + half) * scale, top + 111f * scale),
        new RectF((centerX - 49f) * scale, top + 35f * scale,
            (centerX + 49f) * scale, top + 136f * scale),
        value -> action.run());
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
