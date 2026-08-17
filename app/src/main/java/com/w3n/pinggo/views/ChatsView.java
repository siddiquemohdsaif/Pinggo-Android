package com.w3n.pinggo.views;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.pinggo.R;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.activity.NewChatActivity;
import com.w3n.pinggo.data.local.ChatEntity;
import com.w3n.pinggo.data.local.PresenceEntity;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.modals.Chat;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatsView extends ScrollView {
    public interface OnChatClickListener {
        void onChatClick(Chat chat);
    }

    private final OnChatClickListener onChatClickListener;
    private final LinearLayout listContainer;
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(3);
    private final ChatRepository chatRepository;
    private boolean hasLoadedChats;

    public ChatsView(Context context, OnChatClickListener onChatClickListener) {
        super(context);
        this.onChatClickListener = onChatClickListener;
        chatRepository = ChatRepository.getInstance(context);
        setFillViewport(true);
        listContainer = createChatList();
        addView(listContainer, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
    }

    public void loadChats() {
        if (hasLoadedChats) {
            return;
        }
        hasLoadedChats = true;
        showStatus("Loading chats...");

        String phoneNumber = getCurrentPhoneNumber();
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            showStatus("Login data missing.");
            return;
        }

        chatRepository.observeChats().observeForever(chatEntities -> post(() -> {
            List<Chat> cachedChats = toChats(chatEntities);
            renderChats(cachedChats);
        }));
        chatRepository.refreshChatList(phoneNumber);
    }

    private LinearLayout createChatList() {
        LinearLayout listContainer = new LinearLayout(getContext());
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(16), dp(8), dp(16), dp(96));
        return listContainer;
    }

    private void renderChats(List<Chat> chats) {
        listContainer.removeAllViews();
        if (chats.isEmpty()) {
            showEmptyChats();
            return;
        }

        listContainer.setGravity(Gravity.TOP);

        for (Chat chat : chats) {
            listContainer.addView(createChatRow(chat));
        }
    }

    private void showEmptyChats() {
        listContainer.removeAllViews();
        listContainer.setGravity(Gravity.CENTER);

        LinearLayout emptyState = new LinearLayout(getContext());
        emptyState.setOrientation(LinearLayout.VERTICAL);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(dp(24), dp(24), dp(24), dp(24));
        emptyState.setBackgroundResource(android.R.drawable.list_selector_background);
        emptyState.setClickable(true);
        emptyState.setFocusable(true);
        emptyState.setOnClickListener(v -> getContext().startActivity(
                new Intent(getContext(), NewChatActivity.class)
        ));

        ImageView chatIcon = new ImageView(getContext());
        chatIcon.setImageResource(R.drawable.ic_chat);
        chatIcon.setColorFilter(getContext().getColor(R.color.pinggo_action));
        chatIcon.setContentDescription(getContext().getString(R.string.start_new_conversation));
        emptyState.addView(chatIcon, new LinearLayout.LayoutParams(dp(64), dp(64)));

        TextView message = new TextView(getContext());
        message.setText(R.string.start_new_conversation);
        message.setTextColor(getContext().getColor(R.color.pinggo_action));
        message.setTextSize(18);
        message.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        messageParams.topMargin = dp(12);
        emptyState.addView(message, messageParams);

        listContainer.addView(emptyState, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private View createChatRow(Chat chat) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setOnClickListener(v -> onChatClickListener.onChatClick(chat));

        FrameLayout profileContainer = new FrameLayout(getContext());
        profileContainer.setTag(chat);

        ContactAvatarView profileIcon =
                new ContactAvatarView(getContext(), getAvatarText(chat));

        profileContainer.addView(
                profileIcon,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        loadProfilePhoto(chat.getProfilePhotoUrl(), profileContainer);

        row.addView(
                profileContainer,
                new LinearLayout.LayoutParams(dp(52), dp(52))
        );

        // Container for name + presence
        LinearLayout textContainer = new LinearLayout(getContext());
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setGravity(Gravity.CENTER_VERTICAL);
        textContainer.setPadding(dp(16), 0, 0, 0);

        // Name
        TextView nameTextView = new TextView(getContext());
        nameTextView.setText(chat.getPhoneNumber());
        nameTextView.setTextColor(getContext().getColor(R.color.primary_text));
        nameTextView.setTextSize(18);
        nameTextView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        textContainer.addView(
                nameTextView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        // Presence / preview
        TextView presenceTextView = new TextView(getContext());
        renderPresence(
                presenceTextView,
                chat.isOnline(),
                chat.getLastSeen()
        );

        textContainer.addView(
                presenceTextView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        // Add vertical text container beside profile image
        row.addView(
                textContainer,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        return row;
    }
    private void renderPresence(TextView presenceTextView,boolean isOnline,long lastSeen) {
        if (isOnline) {
            updatePresenceText(presenceTextView,"online");
            return;
        }
        if (lastSeen > 0) {
            updatePresenceText(presenceTextView,"last seen " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(lastSeen)));
            return;
        }
    }

    private void updatePresenceText(TextView presenceTextView,String text) {
        if (presenceTextView != null) {
            presenceTextView.setText(text);
            presenceTextView.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void showStatus(String message) {
        listContainer.removeAllViews();
        listContainer.setGravity(Gravity.TOP);
        TextView statusTextView = new TextView(getContext());
        statusTextView.setText(message);
        statusTextView.setTextColor(getContext().getColor(R.color.secondary_text));
        statusTextView.setTextSize(16);
        statusTextView.setGravity(Gravity.CENTER);
        statusTextView.setPadding(dp(16), dp(32), dp(16), dp(32));
        listContainer.addView(statusTextView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private List<Chat> parseChats(Object object) {
        List<Chat> chats = new ArrayList<>();
        if (!(object instanceof JsonObject)) {
            return chats;
        }

        JsonArray userProfiles = getArray((JsonObject) object, "userProfiles");
        if (userProfiles == null) {
            return chats;
        }

        for (JsonElement element : userProfiles) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject profile = element.getAsJsonObject();
            String phoneNumber = getString(profile, "phoneNumber");
            if (phoneNumber.isEmpty()) {
                continue;
            }
            chats.add(new Chat(
                    getString(profile, "chatId"),
                    phoneNumber,
                    getString(profile, "profilePhotoUrl"),
                    ChatProfilePhotoStore.getLocalPath(getContext(), phoneNumber),
                    getBoolean(profile,"isOnline"),
                    getLong(profile,"lastSeen")
            ));
        }
        return chats;
    }

    private List<Chat> toChats(List<ChatEntity> chatEntities) {
        List<Chat> chats = new ArrayList<>();
        if (chatEntities == null) {
            return chats;
        }

        for (ChatEntity chatEntity : chatEntities) {
            chats.add(new Chat(
                    chatEntity.chatId,
                    chatEntity.contactName == null || chatEntity.contactName.isEmpty()
                            ? chatEntity.otherUserId
                            : chatEntity.contactName,
                    chatEntity.profilePhotoUrl,
                    chatEntity.localProfilePhotoPath == null || chatEntity.localProfilePhotoPath.isEmpty()
                            ? ChatProfilePhotoStore.getLocalPath(getContext(), chatEntity.otherUserId)
                            : chatEntity.localProfilePhotoPath,
                    chatEntity.isOnline,
                    chatEntity.lastSeen
            ));
        }
        return chats;
    }

    private JsonArray getArray(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return null;
        }
        return element.getAsJsonArray();
    }

    private String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.getAsString();
    }

    private boolean getBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return false;
        }
        return element.getAsBoolean();
    }

    private long getLong(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return 0;
        }
        return element.getAsLong();
    }

    private String getCurrentPhoneNumber() {
        String uid = LoginStateManager.getInstance().getUID(getContext());
        if (uid == null) {
            return "";
        }
        if (uid.startsWith("<plus>")) {
            return uid.substring("<plus>".length());
        }
        return uid.startsWith("+") ? uid.substring(1) : uid;
    }

    private String getAvatarText(Chat chat) {
        String phoneNumber = chat.getPhoneNumber();
        return phoneNumber == null || phoneNumber.isEmpty() ? "?" : phoneNumber;
    }

    private void loadProfilePhoto(String profilePhotoUrl, FrameLayout profileContainer) {
        Object chatTag = profileContainer.getTag();
        if (chatTag instanceof Chat) {
            String localPath = ((Chat) chatTag).getLocalProfilePhotoPath();
            Bitmap cachedBitmap = BitmapFactory.decodeFile(localPath);
            if (cachedBitmap != null) {
                showProfileBitmap(profileContainer, cachedBitmap);
                return;
            }
        }

        if (profilePhotoUrl == null || profilePhotoUrl.trim().isEmpty()) {
            return;
        }

        imageExecutor.execute(() -> {
            Object tag = profileContainer.getTag();
            if (!(tag instanceof Chat)) {
                return;
            }
            Chat chat = (Chat) tag;
            String localPath = ChatProfilePhotoStore.downloadAndStore(
                    getContext(),
                    chat.getPhoneNumber(),
                    profilePhotoUrl
            );
            Bitmap bitmap = BitmapFactory.decodeFile(localPath);
            if (bitmap == null) {
                return;
            }
            post(() -> showProfileBitmap(profileContainer, bitmap));
        });
    }

    private void showProfileBitmap(FrameLayout profileContainer, Bitmap bitmap) {
        ImageView imageView = new ImageView(getContext());
        imageView.setImageBitmap(bitmap);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setClipToOutline(true);
        profileContainer.removeAllViews();
        profileContainer.addView(imageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
