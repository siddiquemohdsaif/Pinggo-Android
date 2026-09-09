package com.w3n.pinggo.views.chat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.component.FigmaConfig;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

import java.util.ArrayList;
import java.util.List;

/** Simple chat-details page rendered entirely with native-views AAR components. */
public final class NativeChatDetailsView extends View {
  private final FigmaConfig config = new FigmaConfig(1080f);
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer content = layers.addLayer("details_content");
  private final Bitmap transparent = colorBitmap(Color.TRANSPARENT);
  private final String title;
  private final String name;
  private final Bitmap avatar;
  private final List<String> details = new ArrayList<>();
  private final Runnable back;
  private int topInset;
  private int bottomInset;

  public NativeChatDetailsView(@NonNull android.content.Context context, String title, String name,
      Bitmap avatar, List<String> details, Runnable back) {
    super(context);
    this.title = title; this.name = name; this.avatar = avatar; this.back = back;
    if (details != null) this.details.addAll(details);
    setClickable(true);
    setBackgroundColor(0xFFF7F9FB);
  }

  public void setInsets(int top, int bottom) {
    topInset = Math.max(0, top); bottomInset = Math.max(0, bottom);
    if (getWidth() > 0) build(getWidth(), getHeight());
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    if (width > 0 && height > 0) build(width, height);
  }

  private void build(int width, int height) {
    content.clear();
    float scale = config.getScale(width);
    float headerTop = topInset;
    float headerHeight = 170f * scale;
    content.add(new Button.Builder(getContext(), "details_back", transparent, "‹",
        new RectF(20f * scale, headerTop, 155f * scale, headerTop + headerHeight))
        .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
        .setTextSizePx(82f * scale).setTextColor(0xFF000E1A)
        .setRippleEnabled(true).setRippleColor(0x18019CC4)
        .setOnClickListener(id -> back.run()));
    content.add(new Text.Builder(getContext(), "details_title", title,
        new RectF(165f * scale, headerTop, width - 45f * scale, headerTop + headerHeight))
        .setFont(NativeFonts.INTER).setFontVariations(FontVariation.SEMI_BOLD)
        .setTextSizePx(52f * scale).setTextColor(0xFF000E1A)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(1));
    float avatarSize = 330f * scale;
    float avatarTop = headerTop + headerHeight + 55f * scale;
    content.add(new Image.Builder(getContext(), "details_avatar", avatar,
        new RectF((width - avatarSize) / 2f, avatarTop,
            (width + avatarSize) / 2f, avatarTop + avatarSize))
        .setScaleType(Image.ScaleType.CENTER_CROP));
    float nameTop = avatarTop + avatarSize + 32f * scale;
    content.add(new Text.Builder(getContext(), "details_name", name,
        new RectF(60f * scale, nameTop, width - 60f * scale, nameTop + 95f * scale))
        .setFont(NativeFonts.INTER).setFontVariations(FontVariation.BOLD)
        .setTextSizePx(60f * scale).setTextColor(0xFF000E1A)
        .setAlignment(Text.Alignment.CENTER).setVerticalAlignment(Text.VerticalAlignment.CENTER)
        .setMaxLines(1));
    float listTop = nameTop + 125f * scale;
    content.add(new ComponentList.Builder<String>(getContext(), "details_list",
        new RectF(0, listTop, width, Math.max(listTop, height - bottomInset)))
        .setOrientation(ComponentList.Orientation.VERTICAL).setItemSize(125f * scale)
        .setAdapter(new DetailAdapter()).setScrollEnabled(true).setClipToBounds(true)
        .setOverscrollEnabled(false));
    invalidate();
  }

  @Override protected void onDraw(@NonNull Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
  @Override public boolean onTouchEvent(MotionEvent event) {
    return layers.onTouchEvent(event) || super.onTouchEvent(event);
  }

  public void release() {
    layers.release();
    if (!transparent.isRecycled()) transparent.recycle();
    if (avatar != null && !avatar.isRecycled()) avatar.recycle();
  }

  private final class DetailAdapter extends ComponentList.Adapter<String> {
    @Override public int getItemCount() { return details.size(); }
    @Override public String getItem(int position) { return details.get(position); }
    @Override public long getItemId(int position) { return position; }
    @Override public void onCreateItem(ComponentList.Item item, int type) {
      float scale = config.getScale(getWidth());
      item.addLayer("detail_row").add(new Text.Builder(getContext(), item.getScope().id("value"), "",
          new RectF(55f * scale, 0, item.getScope().width() - 55f * scale,
              item.getScope().height()))
          .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
          .setTextSizePx(41f * scale).setTextColor(0xFF687382)
          .setAlignment(Text.Alignment.CENTER).setVerticalAlignment(Text.VerticalAlignment.CENTER)
          .setMaxLines(2));
    }
    @Override public void onBindItem(ComponentList.Item item, String value, int position) {
      item.find("value", Text.class).setText(value);
    }
  }

  private static Bitmap colorBitmap(int color) {
    Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(color); return bitmap;
  }
}
