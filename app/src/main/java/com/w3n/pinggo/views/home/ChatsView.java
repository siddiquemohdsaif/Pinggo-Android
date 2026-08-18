package com.w3n.pinggo.views.home;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.lifecycle.Observer;

import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.R;
import com.w3n.pinggo.activity.NewChatActivity;
import com.w3n.pinggo.data.local.ChatEntity;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.modals.Chat;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Scrollable chat list implemented with native-views-release.aar components. */
public final class ChatsView extends View {
    private static final int PRIMARY = 0xFF000E1A;
    private static final int SECONDARY = 0xFF687382;
    private static final int ACCENT = 0xFF019CC4;

    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer listLayer = layers.addLayer("chat_list");
    private final ZLayer stateLayer = layers.addLayer("chat_state");
    private final ChatAdapter adapter = new ChatAdapter();
    private final ChatRepository repository;
    private final OnChatClickListener clickListener;
    private final Bitmap dividerBitmap = colorBitmap(0xFFE5EAF0);
    private final Bitmap actionBitmap = colorBitmap(ACCENT);
    private final Observer<List<ChatEntity>> observer = entities -> post(() -> submit(toChats(entities)));
    private ComponentList<Chat> list;
    private Text status;
    private Button emptyAction;
    private boolean loaded;
    private boolean observing;
    private String statusMessage = "Loading chats...";

    public ChatsView(Context context, OnChatClickListener clickListener) {
        super(context);
        this.clickListener = clickListener;
        repository = ChatRepository.getInstance(context);
        setClickable(true);
    }

    public void loadChats() {
        if (loaded) return;
        loaded = true;
        showStatus("Loading chats...");
        String phone = currentPhoneNumber();
        if (phone.isEmpty()) {
            showStatus("Login data missing.");
            return;
        }
        startObserving();
        repository.refreshChatList(phone);
    }

    public void submit(List<Chat> chats) {
        adapter.submit(chats);
        showStatus(adapter.getItemCount() == 0 ? getResources().getString(
                R.string.start_new_conversation) : "");
    }

    private void startObserving() {
        if (observing) return;
        observing = true;
        repository.observeChats().observeForever(observer);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (loaded && !currentPhoneNumber().isEmpty()) startObserving();
    }

    @Override protected void onDetachedFromWindow() {
        if (observing) {
            repository.observeChats().removeObserver(observer);
            observing = false;
        }
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width <= 0 || height <= 0) return;
        listLayer.clear();
        stateLayer.clear();
        list = listLayer.add(new ComponentList.Builder<Chat>(getContext(), "chat_component_list",
                new RectF(0, 0, width, height)).setOrientation(ComponentList.Orientation.VERTICAL)
                .setItemSize(dp(76)).setPaddingPx(dp(12), dp(4), dp(12), dp(96))
                .setAdapter(adapter).setClipToBounds(true).setOverscrollEnabled(false)
                .setOnItemClickListener((componentList, chat, position) ->
                        clickListener.onChatClick(chat)));
        status = stateLayer.add(new Text.Builder(getContext(), "chat_status", statusMessage,
                new RectF(dp(20), dp(28), width - dp(20), dp(112)))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
                .setTextSizePx(sp(16)).setTextColor(SECONDARY).setAlignment(Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(2));
        emptyAction = stateLayer.add(new Button.Builder(getContext(), "start_chat", actionBitmap,
                getResources().getString(R.string.start_new_conversation),
                new RectF(dp(36), dp(124), width - dp(36), dp(180)))
                .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(dp(16))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.SEMI_BOLD)
                .setTextSizePx(sp(16)).setTextColor(Color.WHITE).setRippleEnabled(true)
                .setRippleColor(0x33FFFFFF).setOnClickListener(id -> getContext().startActivity(
                        new Intent(getContext(), NewChatActivity.class))));
        updateVisibility();
    }

    private void showStatus(String message) {
        statusMessage = message == null ? "" : message;
        updateVisibility();
    }

    private void updateVisibility() {
        if (list == null || status == null || emptyAction == null) return;
        boolean hasChats = adapter.getItemCount() > 0;
        boolean empty = !hasChats && statusMessage.equals(
                getResources().getString(R.string.start_new_conversation));
        list.setVisible(hasChats).setEnabled(hasChats);
        status.setText(statusMessage).setVisible(!hasChats);
        emptyAction.setVisible(empty).setEnabled(empty);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        return layers.onTouchEvent(event) || super.onTouchEvent(event);
    }

    public void release() {
        if (observing) repository.observeChats().removeObserver(observer);
        observing = false;
        layers.release();
        recycle(dividerBitmap, actionBitmap);
    }

    private final class ChatAdapter extends ComponentList.Adapter<Chat> {
        private final List<Chat> chats = new ArrayList<>();
        void submit(List<Chat> values) {
            chats.clear();
            if (values != null) chats.addAll(values);
            notifyDataSetChanged();
        }
        @Override public int getItemCount() { return chats.size(); }
        @Override public Chat getItem(int position) { return chats.get(position); }
        @Override public long getItemId(int position) {
            String id = chats.get(position).getChatId();
            return id == null || id.isEmpty() ? position : id.hashCode();
        }
        @Override public void onCreateItem(ComponentList.Item item, int type) {
            ComponentList.ItemScope scope = item.getScope();
            float width = scope.width();
            float height = scope.height();
            ZLayer row = item.addLayer("row");
            row.add(new Image.Builder(getContext(), scope.id("avatar"), avatar("?"),
                    new RectF(dp(8), dp(10), dp(64), dp(66)))
                    .setScaleType(Image.ScaleType.CENTER_CROP));
            row.add(rowText(scope.id("name"), new RectF(dp(80), dp(7), width, dp(40)),
                    sp(17), PRIMARY, FontVariation.SEMI_BOLD));
            row.add(rowText(scope.id("presence"), new RectF(dp(80), dp(38), width, dp(68)),
                    sp(14), SECONDARY, FontVariation.REGULAR));
            row.add(new Image.Builder(getContext(), scope.id("divider"), dividerBitmap,
                    new RectF(dp(80), height - dp(1), width, height))
                    .setScaleType(Image.ScaleType.FIT_XY));
        }
        @Override public void onBindItem(ComponentList.Item item, Chat chat, int position) {
            item.find("avatar", Image.class).setBitmap(chatAvatar(chat));
            item.find("name", Text.class).setText(chat.getContactName());
            item.find("presence", Text.class).setText(presence(chat));
            item.find("divider", Image.class).setVisible(position < chats.size() - 1);
        }
    }

    private Text.Builder rowText(String id, RectF bounds, float size, int color,
                                 FontVariation variation) {
        return new Text.Builder(getContext(), id, "", bounds).setFont(NativeFonts.INTER)
                .setFontVariations(variation).setTextSizePx(size).setTextColor(color)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER).setWrapEnabled(false);
    }

    private Bitmap chatAvatar(Chat chat) {
        String path = chat.getLocalProfilePhotoPath();
        if (path == null || path.trim().isEmpty()) {
            path = ChatProfilePhotoStore.getLocalPath(getContext(), chat.getPhoneNumber());
        }
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        return bitmap == null ? avatar(chat.getContactName()) : bitmap;
    }

    private Bitmap avatar(String value) {
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

    private String presence(Chat chat) {
        if (chat.isOnline()) return "online";
        return chat.getLastSeen() <= 0 ? "" : "last seen "
                + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(chat.getLastSeen()));
    }

    private List<Chat> toChats(List<ChatEntity> entities) {
        List<Chat> chats = new ArrayList<>();
        if (entities == null) return chats;
        for (ChatEntity entity : entities) {
            String name = entity.contactName == null || entity.contactName.isEmpty()
                    ? entity.otherUserId : entity.contactName;
            String path = entity.localProfilePhotoPath == null
                    || entity.localProfilePhotoPath.isEmpty()
                    ? ChatProfilePhotoStore.getLocalPath(getContext(), entity.otherUserId)
                    : entity.localProfilePhotoPath;
            chats.add(new Chat(entity.chatId, name, entity.profilePhotoUrl, path,
                    entity.isOnline, entity.lastSeen));
        }
        return chats;
    }

    private String currentPhoneNumber() {
        String uid = LoginStateManager.getInstance().getUID(getContext());
        if (uid == null) return "";
        if (uid.startsWith("<plus>")) return uid.substring("<plus>".length());
        return uid.startsWith("+") ? uid.substring(1) : uid;
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
    private static Bitmap colorBitmap(int color) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }
    private static void recycle(Bitmap... values) {
        for (Bitmap value : values) if (value != null && !value.isRecycled()) value.recycle();
    }

    public interface OnChatClickListener { void onChatClick(Chat chat); }
}
