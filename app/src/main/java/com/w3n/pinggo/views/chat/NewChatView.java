package com.w3n.pinggo.views.chat;

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
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** AAR-native contact discovery list. */
public final class NewChatView extends View {
  private static final int PRIMARY = 0xFF000E1A, SECONDARY = 0xFF687382, ACCENT = 0xFF019CC4;
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer background = layers.addLayer("background");
  private final ZLayer content = layers.addLayer("content");
  private final ItemAdapter adapter = new ItemAdapter();
  private final Listener listener;
  private final Bitmap white = colorBitmap(Color.WHITE),
      divider = colorBitmap(0xFFE5EAF0),
      accent = colorBitmap(ACCENT);
  private ComponentList<Item> list;
  private Text status;
  private Text title;
  private int topInset, bottomInset;
  private String statusMessage = "Loading contacts...";
  private String titleValue = "New Chat";

  public NewChatView(Context context, Listener listener) {
    super(context);
    this.listener = listener;
    setBackgroundColor(0xFFF7F9FB);
    setClickable(true);
  }

  public void setInsets(int top, int bottom) {
    topInset = Math.max(0, top);
    bottomInset = Math.max(0, bottom);
    if (getWidth() > 0) build();
  }

  public void showStatus(String value) {
    statusMessage = value == null ? "" : value;
    adapter.submit(new ArrayList<>());
    update();
  }

  public void setTitle(String value) {
    titleValue = value == null || value.trim().isEmpty() ? "New Chat" : value.trim();
    if (title != null) title.setText(titleValue);
    invalidate();
  }

  public void submitItems(List<Item> items) {
    adapter.submit(items);
    statusMessage = adapter.getItemCount() == 0 ? "No contacts found." : "";
    update();
  }

  @Override
  protected void onSizeChanged(int w, int h, int ow, int oh) {
    super.onSizeChanged(w, h, ow, oh);
    if (w > 0 && h > 0) build();
  }

  private void build() {
    background.clear();
    content.clear();
    float w = getWidth(), top = topInset + dp(10);
    background.add(
        new Image.Builder(getContext(), "bg", white, new RectF(0, 0, w, getHeight()))
            .setScaleType(Image.ScaleType.FIT_XY));
    addButton(
        content,
        "back",
        white,
        "‹",
        new RectF(dp(8), top, dp(56), top + dp(48)),
        PRIMARY,
        id -> listener.onBack());
    title = content.add(
        text(
            "title",
            titleValue,
            new RectF(dp(64), top, w - dp(20), top + dp(48)),
            sp(24),
            PRIMARY,
            FontVariation.BOLD));
    float listTop = top + dp(60);
    list =
        content.add(
            new ComponentList.Builder<Item>(
                    getContext(), "contacts", new RectF(0, listTop, w, getHeight() - bottomInset))
                .setOrientation(ComponentList.Orientation.VERTICAL)
                .setItemSizeProvider(
                    (item, position) -> item.type == Item.DIVIDER ? dp(52) : dp(76))
                .setPaddingPx(dp(12), dp(4), dp(12), dp(24))
                .setAdapter(adapter)
                .setClipToBounds(true)
                .setOverscrollEnabled(false)
                .setOnItemClickListener(
                    (componentList, item, position) -> {
                      if (item.type == Item.FOUND) listener.onOpenChat(item);
                      else if (item.type == Item.INVITE) listener.onInvite(item.phoneNumber);
                    }));
    status =
        content.add(
            new Text.Builder(
                    getContext(),
                    "status",
                    statusMessage,
                    new RectF(dp(20), listTop + dp(20), w - dp(20), listTop + dp(120)))
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSizePx(sp(16))
                .setTextColor(SECONDARY)
                .setAlignment(Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setMaxLines(2));
    update();
  }

  private void update() {
    if (list == null || status == null) return;
    boolean empty = adapter.getItemCount() == 0;
    list.setVisible(!empty).setEnabled(!empty);
    status.setText(statusMessage).setVisible(empty);
    invalidate();
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    layers.draw(c);
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    return layers.onTouchEvent(e) || super.onTouchEvent(e);
  }

  public void release() {
    layers.release();
    recycle(white, divider, accent);
  }

  private final class ItemAdapter extends ComponentList.Adapter<Item> {
    private final List<Item> items = new ArrayList<>();

    void submit(List<Item> values) {
      items.clear();
      if (values != null) items.addAll(values);
      notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
      return items.size();
    }

    @Override
    public Item getItem(int p) {
      return items.get(p);
    }

    @Override
    public int getItemViewType(int p) {
      return items.get(p).type;
    }

    @Override
    public long getItemId(int p) {
      return (items.get(p).type + "|" + items.get(p).phoneNumber).hashCode();
    }

    @Override
    public void onCreateItem(ComponentList.Item item, int type) {
      ComponentList.ItemScope s = item.getScope();
      float w = s.width(), h = s.height();
      ZLayer row = item.addLayer("row");
      if (type == Item.DIVIDER) {
        row.add(
            text(
                s.id("label"),
                "",
                new RectF(dp(8), 0, w, h),
                sp(14),
                SECONDARY,
                FontVariation.BOLD));
        return;
      }
      row.add(
          new Image.Builder(
                  getContext(),
                  s.id("avatar"),
                  avatar("?"),
                  new RectF(dp(8), dp(10), dp(64), dp(66)))
              .setScaleType(Image.ScaleType.CENTER_CROP));
      row.add(
          text(
              s.id("name"),
              "",
              new RectF(dp(80), dp(7), w - dp(76), dp(40)),
              sp(17),
              PRIMARY,
              FontVariation.SEMI_BOLD));
      row.add(
          text(
              s.id("detail"),
              "",
              new RectF(dp(80), dp(38), w - dp(76), dp(68)),
              sp(14),
              SECONDARY,
              FontVariation.REGULAR));
      row.add(
          new Button.Builder(
                  getContext(),
                  s.id("invite"),
                  accent,
                  "Invite",
                  new RectF(w - dp(72), dp(17), w - dp(4), dp(59)))
              .setImageScaleType(Image.ScaleType.FIT_XY)
              .setCornerRadiusPx(dp(12))
              .setFont(NativeFonts.INTER)
              .setFontVariations(FontVariation.SEMI_BOLD)
              .setTextSizePx(sp(13))
              .setTextColor(Color.WHITE)
              .setRippleEnabled(true)
              .setOnClickListener(
                  id -> {
                    int p = item.getPosition();
                    if (p >= 0 && p < items.size()) listener.onInvite(items.get(p).phoneNumber);
                  }));
      row.add(
          new Image.Builder(
                  getContext(), s.id("divider"), divider, new RectF(dp(80), h - dp(1), w, h))
              .setScaleType(Image.ScaleType.FIT_XY));
    }

    @Override
    public void onBindItem(ComponentList.Item holder, Item value, int p) {
      if (value.type == Item.DIVIDER) {
        holder.find("label", Text.class).setText(value.phoneNumber);
        return;
      }
      holder.find("avatar", Image.class).setBitmap(photo(value));
      holder.find("name", Text.class).setText(value.phoneNumber);
      holder
          .find("detail", Text.class)
          .setText(value.type == Item.FOUND ? "Tap to chat" : "Not on PingGo");
      holder
          .find("invite", Button.class)
          .setVisible(value.type == Item.INVITE)
          .setEnabled(value.type == Item.INVITE);
      holder.find("divider", Image.class).setVisible(p < items.size() - 1);
    }
  }

  private Bitmap photo(Item item) {
    String path = ChatProfilePhotoStore.getLocalPath(getContext(), item.phoneNumber);
    Bitmap b = BitmapFactory.decodeFile(path);
    return b == null ? avatar(item.phoneNumber) : b;
  }

  private Bitmap avatar(String v) {
    int s = Math.round(dp(56));
    Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    Paint p = new Paint(1);
    p.setColor(0xFFD9F1F7);
    c.drawCircle(s / 2f, s / 2f, s / 2f, p);
    String x = v == null || v.isEmpty() ? "?" : v.substring(0, 1).toUpperCase(Locale.US);
    p.setColor(ACCENT);
    p.setTextSize(s * .42f);
    p.setTextAlign(Paint.Align.CENTER);
    Paint.FontMetrics m = p.getFontMetrics();
    c.drawText(x, s / 2f, s / 2f - (m.ascent + m.descent) / 2, p);
    return b;
  }

  private Text.Builder text(String id, String v, RectF r, float sz, int color, FontVariation fv) {
    return new Text.Builder(getContext(), id, v, r)
        .setFont(NativeFonts.INTER)
        .setFontVariations(fv)
        .setTextSizePx(sz)
        .setTextColor(color)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER)
        .setWrapEnabled(false);
  }

  private void addButton(
      ZLayer l,
      String id,
      Bitmap b,
      String label,
      RectF r,
      int color,
      Button.OnClickListener click) {
    l.add(
        new Button.Builder(getContext(), id, b, label, r)
            .setImageScaleType(Image.ScaleType.FIT_XY)
            .setCornerRadiusPx(dp(12))
            .setFont(NativeFonts.INTER)
            .setFontVariations(FontVariation.SEMI_BOLD)
            .setTextSizePx(sp(18))
            .setTextColor(color)
            .setRippleEnabled(true)
            .setOnClickListener(click));
  }

  private float dp(float v) {
    return v * getResources().getDisplayMetrics().density;
  }

  private float sp(float v) {
    return v * getResources().getDisplayMetrics().scaledDensity;
  }

  private static Bitmap colorBitmap(int c) {
    Bitmap b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    b.eraseColor(c);
    return b;
  }

  private static void recycle(Bitmap... bs) {
    for (Bitmap b : bs) if (b != null && !b.isRecycled()) b.recycle();
  }

  public static final class Item {
    public static final int FOUND = 0, INVITE = 1, DIVIDER = 2;
    public final int type;
    public final String phoneNumber, chatId, profilePhotoUrl;

    private Item(int t, String p, String c, String u) {
      type = t;
      phoneNumber = p;
      chatId = c;
      profilePhotoUrl = u;
    }

    public static Item found(String p, String c, String u) {
      return new Item(FOUND, p, c, u);
    }

    public static Item invite(String p) {
      return new Item(INVITE, p, "", "");
    }

    public static Item divider(String label) {
      return new Item(DIVIDER, label, "", "");
    }
  }

  public interface Listener {
    void onBack();

    void onOpenChat(Item item);

    void onInvite(String phoneNumber);
  }
}
