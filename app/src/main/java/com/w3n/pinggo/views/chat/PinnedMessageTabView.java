package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.component.FigmaConfig;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.w3n.pinggo.data.local.MessageEntity;

/** Single-row pinned-message header with position count and previous/next navigation. */
final class PinnedMessageTabView {
  interface MessageClickListener { void onMessageClick(MessageEntity message); }
  interface NavigateListener { void onNavigate(int direction); }

  private static final int PRIMARY = 0xFF131D2F;
  private static final int SECONDARY = 0xFF687382;
  private static final float OUTER_VERTICAL_PADDING_PX = 16.5f;
  private static final float ROW_HEIGHT_PX = 154f;
  private static final float COUNT_WIDTH_PX = 70f;
  private static final float BUTTON_WIDTH_PX = 88f;

  private final Context context;
  private final PinnedMessageAdapter adapter;
  private final Bitmap background;
  private final Bitmap divider;
  private final Bitmap transparent;
  private final FigmaConfig figmaConfig = new FigmaConfig(1080f);
  private final MessageClickListener messageClickListener;
  private final NavigateListener navigateListener;
  private ComponentList<MessageEntity> list;
  private Text count;

  PinnedMessageTabView(
      Context context,
      PinnedMessageAdapter adapter,
      Bitmap background,
      Bitmap divider,
      Bitmap transparent,
      MessageClickListener messageClickListener,
      NavigateListener navigateListener) {
    this.context = context;
    this.adapter = adapter;
    this.background = background;
    this.divider = divider;
    this.transparent = transparent;
    this.messageClickListener = messageClickListener;
    this.navigateListener = navigateListener;
  }

  float height(boolean visible) {
    return visible ? px(OUTER_VERTICAL_PADDING_PX * 2f + ROW_HEIGHT_PX) : 0f;
  }

  void build(ZLayer layer, float top, float width, int activeIndex, int total) {
    float bottom = top + height(total > 0);
    float controlsWidth = px(COUNT_WIDTH_PX + BUTTON_WIDTH_PX * 2f);
    float rowTop = top + px(OUTER_VERTICAL_PADDING_PX);
    float rowBottom = bottom - px(OUTER_VERTICAL_PADDING_PX);
    layer.add(new Image.Builder(context, "pinned_tab_background", background,
        new RectF(0f, top, width, bottom)).setScaleType(Image.ScaleType.FIT_XY));
    layer.add(new Image.Builder(context, "pinned_tab_divider", divider,
        new RectF(0f, bottom - px(2.75f), width, bottom))
        .setScaleType(Image.ScaleType.FIT_XY));
    list = layer.add(new ComponentList.Builder<MessageEntity>(context, "pinned_messages",
            new RectF(0f, rowTop, width - controlsWidth, rowBottom))
        .setOrientation(ComponentList.Orientation.VERTICAL)
        .setItemSize(px(ROW_HEIGHT_PX))
        .setPaddingPx(0f, 0f, 0f, 0f)
        .setAdapter(adapter)
        .setClipToBounds(true)
        .setScrollBarEnabled(false)
        .setOverscrollEnabled(false)
        .setOnItemClickListener((componentList, message, position) -> {
          if (messageClickListener != null) messageClickListener.onMessageClick(message);
        }));
    float countLeft = width - controlsWidth;
    count = layer.add(new Text.Builder(context, "pinned_message_count",
            countText(activeIndex, total),
            new RectF(countLeft, rowTop, countLeft + px(COUNT_WIDTH_PX), rowBottom))
        .setFont(NativeFonts.INTER)
        .setFontVariations(FontVariation.MEDIUM)
        .setTextSizePx(sp(12f))
        .setTextColor(SECONDARY)
        .setAlignment(Text.Alignment.CENTER)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER));
    addButton(layer, "previous_pinned_message", "↑",
        new RectF(countLeft + px(COUNT_WIDTH_PX), rowTop,
            countLeft + px(COUNT_WIDTH_PX + BUTTON_WIDTH_PX), rowBottom), -1);
    addButton(layer, "next_pinned_message", "↓",
        new RectF(width - px(BUTTON_WIDTH_PX), rowTop, width, rowBottom), 1);
  }

  void updateCount(int activeIndex, int total) {
    if (count != null) count.setText(countText(activeIndex, total));
  }

  void scrollToOnlyRow() {
    if (list != null) list.scrollToPosition(0);
  }

  private void addButton(ZLayer layer, String id, String label, RectF bounds, int direction) {
    layer.add(new Button.Builder(context, id, transparent, label, bounds)
        .setImageScaleType(Image.ScaleType.FIT_XY)
        .setCornerRadiusPx(0f)
        .setFont(NativeFonts.INTER)
        .setFontVariations(FontVariation.SEMI_BOLD)
        .setTextSizePx(sp(18f))
        .setTextColor(PRIMARY)
        .setRippleEnabled(true).setWaitForRippleBeforeClick(true)
        .setRippleColor(0x22019CC4)
        .setOnClickListener(componentId -> {
          if (navigateListener != null) navigateListener.onNavigate(direction);
        }));
  }

  private static String countText(int activeIndex, int total) {
    return total <= 0 ? "" : (activeIndex + 1) + " / " + total;
  }

  private float px(float value) {
    return figmaConfig.toRuntime(
        value, Math.max(1, context.getResources().getDisplayMetrics().widthPixels));
  }

  private float sp(float value) {
    return value * context.getResources().getDisplayMetrics().scaledDensity;
  }
}
