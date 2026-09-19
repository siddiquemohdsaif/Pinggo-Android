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
import com.w3n.pinggo.R;
import com.w3n.pinggo.contacts.DeviceContactResolver;
import com.w3n.pinggo.data.cache.ProfileBitmapCache;
import com.w3n.pinggo.views.home.ChatRowRippleComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;

/** AAR-native contact discovery list. */
public final class NewChatView extends View {
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
  private static final int PRIMARY = 0xFF000E1A, SECONDARY = 0xFF687382, ACCENT = 0xFF019CC4;
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer background = layers.addLayer("background");
  private final ZLayer content = layers.addLayer("content");
  private final ItemAdapter adapter = new ItemAdapter();
  private final Listener listener;
  private final Bitmap white = colorBitmap(Color.WHITE),
      divider = colorBitmap(0xFFE5EAF0),
      accent = colorBitmap(ACCENT),
      selectionBackground = BitmapFactory.decodeResource(
          getResources(), R.drawable.chat_selection_background),
      selectionCheck = BitmapFactory.decodeResource(
          getResources(), R.drawable.chat_selection_check);
  private ComponentList<Item> list;
  private Text status;
  private Text title;
  private Button groupAction;
  private int topInset, bottomInset;
  private String statusMessage = "Loading contacts...";
  private String titleValue = "New Chat";
  private boolean groupMode;
  private String groupActionLabel = "Create";
  private final Set<String> selectedMembers = new LinkedHashSet<>();
  private final List<Item> sourceItems = new ArrayList<>();
  private String searchQuery = "";
  private com.ogfa.nativeviews.textfield.TextField searchField;
  private String searchValue = "";
  private boolean chatSections;
  private final Set<String> existingAccounts = new LinkedHashSet<>();

  public void setChatSections(List<String> accounts) {
    chatSections = true;
    existingAccounts.clear();
    if (accounts != null) existingAccounts.addAll(accounts);
  }

  public NewChatView(Context context, Listener listener) {
    super(context);
    setFocusableInTouchMode(true);
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

  public void setGroupMode(boolean enabled) {
    groupMode = enabled;
    titleValue = enabled ? "New group" : "New Chat";
    selectedMembers.clear();
    if (getWidth() > 0) build();
  }

  /** Seeds group selection when this screen is opened from the Chats selection toolbar. */
  public void setSelectedMembers(List<String> memberIds) {
    selectedMembers.clear();
    if (memberIds != null) {
      for (String memberId : memberIds) {
        String normalized = memberId == null ? "" : memberId.trim();
        if (!normalized.isEmpty()) selectedMembers.add(normalized);
      }
    }
    if (getWidth() > 0) {
      applyFilter();
      updateGroupAction();
    }
  }

  public void setGroupActionLabel(String value) {
    groupActionLabel = value == null || value.trim().isEmpty() ? "Create" : value.trim();
    updateGroupAction();
  }

  public void submitItems(List<Item> items) {
    sourceItems.clear();
    if (items != null) sourceItems.addAll(items);
    applyFilter();
  }

  public void setSearchQuery(String query) {
    searchQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    applyFilter();
  }

  private void applyFilter() {
    List<Item> visible = new ArrayList<>();
    List<Item> selected = new ArrayList<>();
    List<Item> available = new ArrayList<>();
    List<Item> inviteItems = new ArrayList<>();
    List<Item> existingChats = new ArrayList<>();
    for (Item item : sourceItems) {
      if (item.type == Item.DIVIDER) continue;
      String name = item.displayName == null || item.displayName.trim().isEmpty()
          ? DeviceContactResolver.cachedNameOrPhone(item.phoneNumber) : item.displayName;
      boolean matches = searchQuery.isEmpty()
          || item.phoneNumber.toLowerCase(Locale.ROOT).contains(searchQuery)
          || name.toLowerCase(Locale.ROOT).contains(searchQuery);
      if (!matches) continue;
      if (item.type == Item.INVITE) inviteItems.add(item);
      else if (groupMode && selectedMembers.contains(item.phoneNumber)) selected.add(item);
      else if (chatSections && existingAccounts.contains(item.phoneNumber)) existingChats.add(item);
      else available.add(item);
    }
    if (groupMode && !selected.isEmpty()) {
      visible.add(Item.divider("Selected"));
      visible.addAll(selected);
    }
    if (groupMode && !chatSections && !available.isEmpty()) visible.add(Item.divider("Chats"));
    if (chatSections && !existingChats.isEmpty()) {
      visible.add(Item.divider("Chats"));
      visible.addAll(existingChats);
    }
    if (chatSections && !available.isEmpty()) visible.add(Item.divider("PingGo chats"));
    visible.addAll(available);
    if (!inviteItems.isEmpty()) {
      visible.add(Item.divider("Invite"));
      visible.addAll(inviteItems);
    }
    adapter.submit(visible);
    statusMessage = adapter.getItemCount() == 0 ? "No contacts found." : "";
    if (!searchQuery.isEmpty() && adapter.getItemCount() == 0)
      statusMessage = "No matching contacts.";
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
    float w = getWidth(), top = topInset + px(27.5f);
    background.add(
        new Image.Builder(getContext(), "bg", white, new RectF(0, 0, w, getHeight()))
            .setScaleType(Image.ScaleType.FIT_XY));
    addButton(
        content,
        "back",
        white,
        "‹",
        new RectF(px(22f), top, px(154f), top + px(132f)),
        PRIMARY,
        id -> listener.onBack());
    title = content.add(
        text(
            "title",
            titleValue,
            new RectF(px(176f), top, w - (groupMode ? px(310f) : px(165f)), top + px(132f)),
            sp(24),
            PRIMARY,
            FontVariation.BOLD));
    if (groupMode) {
      groupAction = addButton(content, "create_group", accent,
          groupActionLabel + " (" + selectedMembers.size() + ")",
          new RectF(w - px(295f), top + px(15f), w - px(33f), top + px(117f)),
          Color.WHITE, id -> {
            if (selectedMembers.isEmpty()) listener.onGroupSelectionRequired();
            else listener.onCreateGroup(new ArrayList<>(selectedMembers));
          });
    } else {
      addButton(content, "more", white, "⋮",
          new RectF(w - px(154f), top, w - px(22f), top + px(132f)),
          PRIMARY, id -> listener.onMore());
    }
    searchField = content.add(new com.ogfa.nativeviews.textfield.TextField.Builder(getContext(), "contact_search",
        new RectF(px(44f), top + px(172f), w - px(44f), top + px(304f)))
        .setText(searchValue).setHint("Search name or phone number").setMaxLength(80)
        .setInputType(android.text.InputType.TYPE_CLASS_TEXT).setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
        .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
        .setTextSizePx(sp(15)).setTextColor(PRIMARY).setHintColor(SECONDARY)
        .setBackgroundColor(0xFFF7F9FB, Color.WHITE).setStrokeColor(0xFFE5EAF0, ACCENT)
        .setCornerRadiusPx(px(33f)).setPaddingPx(px(33f), px(22f))
        .setOnTextChangedListener((id, value) -> { searchValue = value; setSearchQuery(value); }));
    float listTop = top + px(330f);
    list =
        content.add(
            new ComponentList.Builder<Item>(
                    getContext(), "contacts", new RectF(0, listTop, w, getHeight() - bottomInset))
                .setOrientation(ComponentList.Orientation.VERTICAL)
                .setItemSizeProvider(
                    (item, position) -> item.type == Item.DIVIDER ? px(112f) : px(185f))
                .setPaddingPx(0, px(11f), 0, px(66f))
                .setAdapter(adapter)
                .setClipToBounds(true)
                .setOverscrollEnabled(false)
                .setOnItemClickListener(
                    (componentList, item, position) -> {
                      if (item.type == Item.FOUND && groupMode) {
                        if (!selectedMembers.add(item.phoneNumber)) selectedMembers.remove(item.phoneNumber);
                        applyFilter();
                        updateGroupAction();
                      } else if (item.type == Item.FOUND) listener.onOpenChat(item);
                      else if (item.type == Item.INVITE) listener.onInvite(item.phoneNumber);
                    }));
    status =
        content.add(
            new Text.Builder(
                    getContext(),
                    "status",
                    statusMessage,
                    new RectF(px(55f), listTop + px(55f), w - px(55f), listTop + px(330f)))
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

  private void updateGroupAction() {
    if (groupAction != null)
      groupAction.setLabel(groupActionLabel + " (" + selectedMembers.size() + ")");
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

  @Override public boolean onCheckIsTextEditor() { return layers.onCheckIsTextEditor(); }
  @Override public android.view.inputmethod.InputConnection onCreateInputConnection(android.view.inputmethod.EditorInfo info) {
    return layers.onCreateInputConnection(info);
  }
  @Override public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
    return layers.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
  }

  public void release() {
    layers.release();
    recycle(white, divider, accent, selectionBackground, selectionCheck);
  }

  private final class ItemAdapter extends ComponentList.Adapter<Item> {
    private final List<Item> items = new ArrayList<>();

    void submit(List<Item> values) {
      List<Item> updated = values == null ? new ArrayList<>() : values;
      for (int target = 0; target < updated.size(); target++) {
        Item next = updated.get(target);
        int existing = indexOf(next, target);
        if (existing < 0) {
          items.add(target, next);
          notifyItemInserted(target);
        } else {
          if (existing != target) {
            Item moved = items.remove(existing);
            items.add(target, moved);
            notifyItemMoved(existing, target);
          }
          Item previous = items.set(target, next);
          if (!previous.sameContent(next)) notifyItemChanged(target);
        }
      }
      for (int index = items.size() - 1; index >= updated.size(); index--) {
        items.remove(index);
        notifyItemRemoved(index);
      }
    }

    private int indexOf(Item target, int start) {
      for (int index = Math.max(0, start); index < items.size(); index++)
        if (items.get(index).sameIdentity(target)) return index;
      return -1;
    }

    int indexOfPhone(String phone) {
      for (int index = 0; index < items.size(); index++)
        if (items.get(index).type == Item.FOUND
            && items.get(index).phoneNumber.equals(phone)) return index;
      return -1;
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
                new RectF(px(50f), 0, w, h),
                sp(14),
                SECONDARY,
                FontVariation.BOLD));
        return;
      }
      row.add(new Image.Builder(getContext(), s.id("selection_background"),
          selectionBackground, new RectF(0, 0, w, h)).setScaleType(Image.ScaleType.FIT_XY));
      row.add(new ChatRowRippleComponent(s.id("row_ripple"), new RectF(0, 0, w, h)));
      row.add(
          new Image.Builder(
                  getContext(),
                  s.id("avatar"),
                  avatar("?"),
                  new RectF(px(50f), px(27f), px(182f), px(159f)))
              .setScaleType(Image.ScaleType.CENTER_CROP));
      row.add(new Image.Builder(getContext(), s.id("selection_check"), selectionCheck,
          new RectF(px(128f), px(111f), px(184f), px(167f)))
          .setScaleType(Image.ScaleType.FIT_XY));
      row.add(
          text(
              s.id("name"),
              "",
              new RectF(px(220f), px(38f), w - px(230f), px(92f)),
              sp(16),
              PRIMARY,
              FontVariation.MEDIUM));
      row.add(
          text(
              s.id("detail"),
              "",
              new RectF(px(220f), px(103f), w - px(210f), px(157f)),
              sp(14),
              SECONDARY,
              FontVariation.REGULAR));
      row.add(
          new Button.Builder(
                  getContext(),
                  s.id("invite"),
                  accent,
                  "Invite",
                  new RectF(w - px(210f), px(39f), w - px(42f), px(146f)))
              .setImageScaleType(Image.ScaleType.FIT_XY)
              .setCornerRadiusPx(px(33f))
              .setFont(NativeFonts.INTER)
              .setFontVariations(FontVariation.SEMI_BOLD)
              .setTextSizePx(sp(13))
              .setTextColor(Color.WHITE)
              .setRippleEnabled(true).setWaitForRippleBeforeClick(true)
              .setOnClickListener(
                  id -> {
                    int p = item.getPosition();
                    if (p >= 0 && p < items.size()) listener.onInvite(items.get(p).phoneNumber);
                  }));
      row.add(
          new Image.Builder(
                  getContext(), s.id("divider"), divider,
                  new RectF(px(220f), h - Math.max(1f, px(1f)), w, h))
              .setScaleType(Image.ScaleType.FIT_XY));
    }

    @Override
    public void onBindItem(ComponentList.Item holder, Item value, int p) {
      if (value.type == Item.DIVIDER) {
        holder.find("label", Text.class).setText(value.phoneNumber);
        return;
      }
      Bitmap avatar = photo(value);
      Image avatarImage = holder.find("avatar", Image.class);
      avatarImage.setBitmap(avatar);
      if (value.type == Item.FOUND) {
        avatarImage.setOnClickListener(id -> {
          String source = ChatProfilePhotoStore.getLocalPath(getContext(), value.phoneNumber);
          if ((source == null || source.trim().isEmpty())
              && value.profilePhotoUrl != null) source = value.profilePhotoUrl;
          listener.onProfilePhoto(value, avatarImage.getBitmap(), source);
        });
      }
      boolean selected = groupMode && selectedMembers.contains(value.phoneNumber);
      holder.find("selection_background", Image.class).setVisible(selected);
      holder.find("selection_check", Image.class).setVisible(selected);
      String displayName = value.displayName == null || value.displayName.trim().isEmpty()
          ? DeviceContactResolver.cachedNameOrPhone(value.phoneNumber) : value.displayName;
      holder.find("name", Text.class).setText(displayName);
      holder
          .find("detail", Text.class)
          .setText(value.type == Item.FOUND
              ? (selected
                  ? "Selected" : groupMode ? "Tap to select" : "Tap to chat")
              : "Not on PingGo");
      holder
          .find("invite", Button.class)
          .setVisible(value.type == Item.INVITE)
          .setEnabled(value.type == Item.INVITE);
      holder.find("divider", Image.class).setVisible(p < items.size() - 1);
    }
  }

  private Bitmap photo(Item item) {
    String path = ChatProfilePhotoStore.getLocalPath(getContext(), item.phoneNumber);
    String displayName = item.displayName == null || item.displayName.trim().isEmpty()
        ? DeviceContactResolver.cachedNameOrPhone(item.phoneNumber) : item.displayName;
    return ProfileBitmapCache.get().request(path, displayName,
        Math.max(1, Math.round(px(132f))), ACCENT, () -> {
          int position = adapter.indexOfPhone(item.phoneNumber);
          if (position >= 0) adapter.notifyItemChanged(position);
        });
  }

  private Bitmap avatar(String v) {
    int s = Math.round(px(154f));
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

  private Button addButton(
      ZLayer l,
      String id,
      Bitmap b,
      String label,
      RectF r,
      int color,
      Button.OnClickListener click) {
    return l.add(
        new Button.Builder(getContext(), id, b, label, r)
            .setImageScaleType(Image.ScaleType.FIT_XY)
            .setCornerRadiusPx(px(33f))
            .setFont(NativeFonts.INTER)
            .setFontVariations(FontVariation.SEMI_BOLD)
            .setTextSizePx(sp(18))
            .setTextColor(color)
            .setRippleEnabled(true).setWaitForRippleBeforeClick(true)
            .setOnClickListener(click));
  }

  private float px(float v) {
    return figmaConfig.toRuntime(v, Math.max(1, getResources().getDisplayMetrics().widthPixels));
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
    public final String phoneNumber, chatId, profilePhotoUrl, displayName;

    private Item(int t, String p, String c, String u, String n) {
      type = t;
      phoneNumber = p == null ? "" : p;
      chatId = c == null ? "" : c;
      profilePhotoUrl = u == null ? "" : u;
      displayName = n == null ? "" : n;
    }

    public static Item found(String p, String c, String u) {
      return found(p, c, u, "");
    }

    public static Item found(String p, String c, String u, String n) {
      return new Item(FOUND, p, c, u, n);
    }

    public static Item invite(String p) {
      return new Item(INVITE, p, "", "", "");
    }

    public static Item divider(String label) {
      return new Item(DIVIDER, label, "", "", "");
    }

    boolean sameIdentity(Item other) {
      return other != null && type == other.type && phoneNumber.equals(other.phoneNumber);
    }

    boolean sameContent(Item other) {
      return sameIdentity(other) && chatId.equals(other.chatId)
          && profilePhotoUrl.equals(other.profilePhotoUrl)
          && displayName.equals(other.displayName);
    }
  }

  public interface Listener {
    void onBack();

    void onOpenChat(Item item);

    void onProfilePhoto(Item item, Bitmap fallback, String originalSource);

    void onInvite(String phoneNumber);

    void onCreateGroup(List<String> memberIds);

    void onGroupSelectionRequired();

    void onMore();
  }
}
