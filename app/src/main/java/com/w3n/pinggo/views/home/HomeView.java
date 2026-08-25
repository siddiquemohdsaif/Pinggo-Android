package com.w3n.pinggo.views.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;

import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.textfield.TextField;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.w3n.pinggo.R;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.modals.CallLog;
import com.w3n.pinggo.modals.Chat;

import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Home screen rendered entirely with components from native-views-release.aar. */
public final class HomeView extends View {
    private static final int BACKGROUND = 0xFFF7F9FB;
    private static final int SURFACE = Color.WHITE;
    private static final int PRIMARY = 0xFF000E1A;
    private static final int SECONDARY = 0xFF687382;
    private static final int ACCENT = 0xFF019CC4;
    private static final int DIVIDER = 0xFFE5EAF0;

    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer backgroundLayer = layers.addLayer("background");
    private final ZLayer contentLayer = layers.addLayer("content");
    private final ZLayer navigationLayer = layers.addLayer("navigation");
    private final ChatAdapter chatAdapter = new ChatAdapter();
    private final CallAdapter callAdapter = new CallAdapter();
    private final Listener listener;
    private final Bitmap backgroundBitmap = colorBitmap(BACKGROUND);
    private final Bitmap surfaceBitmap = colorBitmap(SURFACE);
    private final Bitmap accentBitmap = colorBitmap(ACCENT);
    private final Bitmap selectedBitmap = colorBitmap(0xFFE9F5F8);
    private final Bitmap dividerBitmap = colorBitmap(DIVIDER);

    private ComponentList<Chat> chatList;
    private ComponentList<CallLog> callList;
    private Text title;
    private Text status;
    private TextField search;
    private Button overflow;
    private Button action;
    private Button chatsTab;
    private Button callsTab;
    private int topInset;
    private int bottomInset;
    private boolean showingChats = true;
    private String chatStatus = "Loading chats...";

    public HomeView(@NonNull Context context, @NonNull Listener listener) {
        super(context);
        this.listener = listener;
        setBackgroundColor(BACKGROUND);
        setClickable(true);
        setFocusableInTouchMode(true);
    }

    public void setInsets(int top, int bottom) {
        top = Math.max(0, top);
        bottom = Math.max(0, bottom);
        if (topInset == top && bottomInset == bottom) return;
        topInset = top;
        bottomInset = bottom;
        if (getWidth() > 0) buildScreen();
    }

    public void showChatLoading() {
        chatStatus = "Loading chats...";
        updateChatState();
    }

    public void submitChats(List<Chat> chats) {
        chatAdapter.submit(chats, search == null ? "" : search.getText());
        chatStatus = chatAdapter.getItemCount() == 0 ? "Start a new conversation" : "";
        updateChatState();
    }

    public void submitCalls(List<CallLog> calls) {
        callAdapter.submit(calls);
        updateCallState();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && height > 0) buildScreen();
    }

    private void buildScreen() {
        // Keep the layers registered with ZLayerGroup. Clearing the group removes
        // the layers themselves, so adding components to these retained fields
        // would build an invisible tree that ZLayerGroup.draw() cannot reach.
        backgroundLayer.clear();
        contentLayer.clear();
        navigationLayer.clear();
        float width = getWidth();
        float height = getHeight();
        float top = topInset + dp(12);
        float navTop = height - dp(68) - bottomInset;
        float searchTop = top + dp(56);
        float listTop = searchTop + dp(60);

        backgroundLayer.add(image("background", backgroundBitmap, new RectF(0, 0, width, height)));
        title = contentLayer.add(text("title", getString(R.string.app_name),
                new RectF(dp(20), top, width - dp(72), top + dp(48)), sp(28), PRIMARY,
                FontVariation.BOLD));
        overflow = addButton(contentLayer, "overflow", surfaceBitmap, "⋮",
                new RectF(width - dp(60), top, width - dp(12), top + dp(48)), PRIMARY, dp(12),
                id -> listener.onOpenSettings());

        search = contentLayer.add(new TextField.Builder(getContext(), "search",
                new RectF(dp(20), searchTop, width - dp(20), searchTop + dp(48)))
                .setHint(getString(R.string.search)).setInputType(InputType.TYPE_CLASS_TEXT)
                .setImeOptions(EditorInfo.IME_ACTION_DONE).setMaxLength(80)
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
                .setTextSizePx(sp(16)).setTextColor(PRIMARY).setHintColor(SECONDARY)
                .setCursorColor(ACCENT).setCursorWidthPx(dp(2))
                .setBackgroundColor(SURFACE, SURFACE).setStrokeColor(DIVIDER, ACCENT)
                .setStrokeWidthPx(dp(1)).setCornerRadiusPx(dp(14)).setPaddingPx(dp(16), dp(10))
                .setOnTextChangedListener((id, value) -> {
                    chatAdapter.filter(value);
                    chatStatus = chatAdapter.getItemCount() == 0
                            ? "No matching conversations" : "";
                    updateChatState();
                }).setOnEditorActionListener((id, actionId) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) hideKeyboard();
                    return actionId == EditorInfo.IME_ACTION_DONE;
                }));

        chatList = contentLayer.add(new ComponentList.Builder<Chat>(getContext(), "chats",
                new RectF(0, listTop, width, navTop))
                .setOrientation(ComponentList.Orientation.VERTICAL).setItemSize(dp(76))
                .setPaddingPx(dp(12), dp(4), dp(12), dp(88)).setAdapter(chatAdapter)
                .setClipToBounds(true).setOverscrollEnabled(false)
                .setOnItemLongClickListener((list, chat, position) -> {
                    listener.onChatLongPress(chat);
                    return true;
                })
                .setOnItemClickListener((list, chat, position) -> listener.onOpenChat(chat)));
        callList = contentLayer.add(new ComponentList.Builder<CallLog>(getContext(), "calls",
                new RectF(0, top + dp(56), width, navTop))
                .setOrientation(ComponentList.Orientation.VERTICAL).setItemSize(dp(76))
                .setPaddingPx(dp(12), dp(4), dp(12), dp(88)).setAdapter(callAdapter)
                .setClipToBounds(true).setOverscrollEnabled(false)
                .setOnItemClickListener((list, call, position) -> listener.onOpenCall(call)));
        status = contentLayer.add(new Text.Builder(getContext(), "status", chatStatus,
                new RectF(dp(24), listTop + dp(20), width - dp(24), listTop + dp(120)))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
                .setTextSizePx(sp(16)).setTextColor(SECONDARY).setAlignment(Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(2));
        action = addButton(contentLayer, "action", accentBitmap, getString(R.string.new_chat),
                new RectF(width - dp(156), navTop - dp(72), width - dp(20), navTop - dp(16)),
                Color.WHITE, dp(18), id -> {
                    if (showingChats) listener.onNewChat(); else listener.onMakeCall();
                });

        navigationLayer.add(image("nav_background", surfaceBitmap,
                new RectF(0, navTop, width, height)));
        navigationLayer.add(image("nav_divider", dividerBitmap,
                new RectF(0, navTop, width, navTop + dp(1))));
        chatsTab = addButton(navigationLayer, "chat_tab", selectedBitmap,
                getString(R.string.chats), new RectF(0, navTop, width / 2, navTop + dp(68)),
                ACCENT, 0, id -> showChats());
        callsTab = addButton(navigationLayer, "call_tab", surfaceBitmap,
                getString(R.string.call), new RectF(width / 2, navTop, width, navTop + dp(68)),
                SECONDARY, 0, id -> showCalls());
        if (showingChats) showChats(); else showCalls();
    }

    private void showChats() {
        showingChats = true;
        if (title == null) return;
        title.setText(getString(R.string.app_name));
        overflow.setVisible(true);
        search.setVisible(true);
        callList.setVisible(false).setEnabled(false);
        chatsTab.setBitmap(selectedBitmap).setTextColor(ACCENT);
        callsTab.setBitmap(surfaceBitmap).setTextColor(SECONDARY);
        action.setLabel(getString(R.string.new_chat));
        updateChatState();
    }

    private void showCalls() {
        showingChats = false;
        if (title == null) return;
        search.clearFocus();
        hideKeyboard();
        title.setText(getString(R.string.call));
        overflow.setVisible(false);
        search.setVisible(false);
        chatList.setVisible(false).setEnabled(false);
        chatsTab.setBitmap(surfaceBitmap).setTextColor(SECONDARY);
        callsTab.setBitmap(selectedBitmap).setTextColor(ACCENT);
        action.setLabel(getString(R.string.make_call));
        updateCallState();
    }

    private void updateChatState() {
        if (!showingChats || chatList == null || status == null) return;
        boolean empty = chatAdapter.getItemCount() == 0;
        chatList.setVisible(!empty).setEnabled(!empty);
        status.setText(chatStatus).setVisible(empty);
        invalidate();
    }

    private void updateCallState() {
        if (showingChats || callList == null || status == null) return;
        boolean empty = callAdapter.getItemCount() == 0;
        callList.setVisible(!empty).setEnabled(!empty);
        status.setText(empty ? "No calls found." : "").setVisible(empty);
        invalidate();
    }

    private Button addButton(ZLayer layer, String id, Bitmap bitmap, String label, RectF bounds,
                             int color, float radius, Button.OnClickListener click) {
        return layer.add(new Button.Builder(getContext(), id, bitmap, label, bounds)
                .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(radius)
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.SEMI_BOLD)
                .setTextSizePx(sp(15)).setTextColor(color).setRippleEnabled(true)
                .setRippleColor(0x22019CC4).setRippleOrigin(Button.RippleOrigin.TOUCH)
                .setOnClickListener(click));
    }

    private Image.Builder image(String id, Bitmap bitmap, RectF bounds) {
        return new Image.Builder(getContext(), id, bitmap, bounds).setScaleType(Image.ScaleType.FIT_XY);
    }

    private Text.Builder text(String id, String value, RectF bounds, float size, int color,
                              FontVariation variation) {
        return new Text.Builder(getContext(), id, value, bounds).setFont(NativeFonts.INTER)
                .setFontVariations(variation).setTextSizePx(size).setTextColor(color)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER).setWrapEnabled(false);
    }

    @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        return layers.onTouchEvent(event) || super.onTouchEvent(event);
    }
    @Override public boolean onCheckIsTextEditor() { return layers.onCheckIsTextEditor(); }
    @Override public InputConnection onCreateInputConnection(EditorInfo attrs) {
        InputConnection result = layers.onCreateInputConnection(attrs);
        return result != null ? result : super.onCreateInputConnection(attrs);
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        return layers.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    public void release() {
        layers.release();
        for (Bitmap bitmap : new Bitmap[]{backgroundBitmap, surfaceBitmap, accentBitmap,
                selectedBitmap, dividerBitmap}) {
            if (!bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private void hideKeyboard() {
        InputMethodManager input = (InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (input != null) input.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    private final class ChatAdapter extends ComponentList.Adapter<Chat> {
        private final List<Chat> all = new ArrayList<>();
        private final List<Chat> visible = new ArrayList<>();

        void submit(List<Chat> chats, String query) {
            all.clear();
            if (chats != null) all.addAll(chats);
            filter(query);
        }

        void filter(String query) {
            String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
            visible.clear();
            for (Chat chat : all) {
                String name = chat.getContactName() == null ? "" : chat.getContactName();
                if (needle.isEmpty() || name.toLowerCase(Locale.US).contains(needle)) visible.add(chat);
            }
            notifyDataSetChanged();
        }

        @Override public int getItemCount() { return visible.size(); }
        @Override public Chat getItem(int position) { return visible.get(position); }
        @Override public long getItemId(int position) {
            String id = visible.get(position).getChatId();
            return id == null || id.isEmpty() ? position : id.hashCode();
        }
        @Override public void onCreateItem(ComponentList.Item item, int type) {
            addRow(item, true);
        }
        @Override public void onBindItem(ComponentList.Item item, Chat chat, int position) {
            if (position >= visible.size() - 3) {
                ChatRepository.getInstance(getContext()).loadNextChatListPage();
            }
            item.find("avatar", Image.class).setBitmap(chatAvatar(chat));
            item.find("name", Text.class).setText(chat.getContactName());
            item.find("detail", Text.class).setText(lastMessage(chat));
            item.find("kind", Text.class).setText(lastMessageTime(chat)).setVisible(true);
            item.find("unread", Text.class).setText(unreadCount(chat))
                    .setVisible(chat.getUnreadCount() > 0);
            item.find("divider", Image.class).setVisible(position < visible.size() - 1);
        }
    }

    private final class CallAdapter extends ComponentList.Adapter<CallLog> {
        private final List<CallLog> calls = new ArrayList<>();
        void submit(List<CallLog> values) {
            calls.clear();
            if (values != null) calls.addAll(values);
            notifyDataSetChanged();
        }
        @Override public int getItemCount() { return calls.size(); }
        @Override public CallLog getItem(int position) { return calls.get(position); }
        @Override public void onCreateItem(ComponentList.Item item, int type) { addRow(item, false); }
        @Override public void onBindItem(ComponentList.Item item, CallLog call, int position) {
            item.find("avatar", Image.class).setBitmap(avatarBitmap(call.getContactName()));
            item.find("name", Text.class).setText(call.getContactName());
            item.find("detail", Text.class).setText(call.getCalledTime());
            item.find("kind", Text.class).setText(call.isVideoCall() ? "▣" : "☎").setVisible(true);
            item.find("unread", Text.class).setVisible(false);
            item.find("divider", Image.class).setVisible(position < calls.size() - 1);
        }
    }

    private void addRow(ComponentList.Item item, boolean chat) {
        ComponentList.ItemScope scope = item.getScope();
        float width = scope.width();
        float height = scope.height();
        ZLayer row = item.addLayer("row");
        row.add(image(scope.id("avatar"), avatarBitmap("?"),
                new RectF(dp(8), dp(10), dp(64), dp(66))).setScaleType(Image.ScaleType.CENTER_CROP));
        row.add(text(scope.id("name"), "", new RectF(dp(80), dp(7),
                width - dp(chat ? 120 : 58), dp(40)),
                sp(17), PRIMARY, FontVariation.SEMI_BOLD));
        row.add(text(scope.id("detail"), "", new RectF(dp(80), dp(38), width - dp(58), dp(68)),
                sp(14), SECONDARY, FontVariation.REGULAR));
        Text kind = row.add(new Text.Builder(getContext(), scope.id("kind"), "",
                new RectF(width - dp(chat ? 112 : 56), dp(chat ? 7 : 0),
                        width - dp(8), chat ? dp(40) : height)).useDefaultFont()
                .setTextSizePx(sp(chat ? 12 : 22)).setTextColor(chat ? SECONDARY : PRIMARY)
                .setAlignment(chat ? Text.Alignment.END : Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER));
        kind.setVisible(!chat);
        row.add(new Text.Builder(getContext(), scope.id("unread"), "",
                new RectF(width - dp(56), dp(40), width - dp(8), dp(68))).useDefaultFont()
                .setTextSizePx(sp(13)).setTextColor(ACCENT)
                .setFontVariations(FontVariation.SEMI_BOLD)
                .setAlignment(Text.Alignment.END)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)).setVisible(false);
        row.add(image(scope.id("divider"), dividerBitmap,
                new RectF(dp(80), height - dp(1), width, height)));
    }

    private Bitmap chatAvatar(Chat chat) {
        String path = chat.getLocalProfilePhotoPath();
        if (path == null || path.trim().isEmpty()) {
            path = ChatProfilePhotoStore.getLocalPath(getContext(), chat.getPhoneNumber());
        }
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        return bitmap == null ? avatarBitmap(chat.getContactName()) : bitmap;
    }

    private String lastMessage(Chat chat) {
        String message = chat.getLastMessage();
        return message == null || message.trim().isEmpty()
                ? "Start conversation" : message.trim();
    }

    private String lastMessageTime(Chat chat) {
        long timestamp = chat.getLastMessageTime();
        if (timestamp <= 0) return "";
        Calendar messageDate = Calendar.getInstance();
        messageDate.setTimeInMillis(timestamp);
        Calendar today = Calendar.getInstance();
        boolean sameDate = messageDate.get(Calendar.ERA) == today.get(Calendar.ERA)
                && messageDate.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && messageDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
        String pattern = sameDate ? "hh:mm a" : "dd/MM/yyyy";
        return new SimpleDateFormat(pattern, Locale.getDefault())
                .format(new Date(timestamp)).toLowerCase(Locale.getDefault());
    }

    private String unreadCount(Chat chat) {
        return chat.getUnreadCount() > 99 ? "99+" : String.valueOf(chat.getUnreadCount());
    }

    private Bitmap avatarBitmap(String value) {
        int size = Math.max(1, Math.round(dp(56)));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFFD9F1F7);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        String label = value == null || value.trim().isEmpty() ? "?"
                : value.trim().substring(0, 1).toUpperCase(Locale.US);
        paint.setColor(ACCENT);
        paint.setTextSize(size * .42f);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(label, size / 2f,
                size / 2f - (metrics.ascent + metrics.descent) / 2f, paint);
        return bitmap;
    }

    private String getString(int id) { return getResources().getString(id); }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
    private static Bitmap colorBitmap(int color) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }

    public interface Listener {
        void onOpenChat(Chat chat);
        void onChatLongPress(Chat chat);
        void onOpenCall(CallLog callLog);
        void onNewChat();
        void onMakeCall();
        void onOpenSettings();
    }

}
