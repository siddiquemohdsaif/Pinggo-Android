package com.w3n.pinggo.activity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.R;
import com.w3n.pinggo.data.local.ChatEntity;
import com.w3n.pinggo.data.local.MessageEntity;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.modals.CallLog;
import com.w3n.pinggo.modals.Chat;
import com.w3n.pinggo.views.common.ExitAppController;
import com.w3n.pinggo.views.home.HomeMenuDialogView;
import com.w3n.pinggo.views.home.HomeView;

import java.util.ArrayList;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Hosts the AAR-native home surface and owns lifecycle, data, and navigation. */
public class HomeActivity extends AppCompatActivity implements HomeView.Listener {
    private static final int SELECTION_STATUS_BAR_COLOR = 0xFFE9EDF0;
    private static final int BOTTOM_SYSTEM_NAVIGATION_COLOR = 0xFFF9FBFE;
    private HomeView homeView;
    private HomeMenuDialogView homeMenuDialog;
    private ChatRepository repository;
    private List<ChatEntity> latestChatEntities = new ArrayList<>();
    private List<MessageEntity> latestCallMessages = new ArrayList<>();
    private JsonArray latestServerCalls;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        homeView = new HomeView(this, this);
        setContentView(homeView);
        ExitAppController.install(this, null);
        ViewGroup content = findViewById(android.R.id.content);
        homeMenuDialog = new HomeMenuDialogView(this, new HomeMenuDialogView.Listener() {
            @Override public void onNewChat() { HomeActivity.this.onNewChat(); }
            @Override public void onNewGroup() {
                Toast.makeText(HomeActivity.this, "New Group", Toast.LENGTH_SHORT).show();
            }
            @Override public void onLinkedDevices() {
                Toast.makeText(HomeActivity.this, "Linked Devices", Toast.LENGTH_SHORT).show();
            }
            @Override public void onSettings() { openSettings(); }
        });
        content.addView(homeMenuDialog, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (homeMenuDialog != null && homeMenuDialog.dismissIfShowing()) return;
                if (homeView != null && homeView.clearChatSelection()) return;

                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
        ViewCompat.setOnApplyWindowInsetsListener(homeView, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            homeView.setInsets(bars.top, bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(homeView);
        loadChats();
    }

    private void configureSystemBars() {
        Window window = getWindow();
        int systemBarColor = ContextCompat.getColor(
                this, R.color.login_system_bar_background);
        window.setStatusBarColor(systemBarColor);
        window.setNavigationBarColor(BOTTOM_SYSTEM_NAVIGATION_COLOR);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
    }

    private void loadChats() {
        homeView.showChatLoading();
        repository = ChatRepository.getInstance(this);
        // Covers a fresh login completed after Application.onCreate().
        repository.connect();
        repository.observeChats().observe(this, entities -> {
            latestChatEntities = entities == null ? new ArrayList<>() : entities;
            homeView.submitChats(toChats(entities));
            if (latestServerCalls != null) submitServerCalls(latestServerCalls);
            else submitCalls();
            repository.acknowledgePendingIncomingDeliveries();
        });
        repository.observeCallMessages().observe(this, messages -> {
            latestCallMessages = messages == null ? new ArrayList<>() : messages;
            if (latestServerCalls == null) submitCalls();
        });
        String uid = LoginStateManager.getInstance().getUID(this);
        if (uid != null && !uid.trim().isEmpty()) {
            repository.ensureChatListLoaded(normalizeAccountId(uid));
        }
    }

    @Override public void onOpenChat(Chat chat) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, chat.getContactName());
        intent.putExtra(ChatActivity.EXTRA_CHAT_ID, chat.getChatId());
        intent.putExtra(ChatActivity.EXTRA_PROFILE_PHOTO_URL, chat.getProfilePhotoUrl());
        String localPath = chat.getLocalProfilePhotoPath();
        if (localPath == null || localPath.trim().isEmpty()) {
            localPath = ChatProfilePhotoStore.getLocalPath(this, chat.getPhoneNumber());
        }
        intent.putExtra(ChatActivity.EXTRA_LOCAL_PROFILE_PHOTO_PATH, localPath);
        intent.putExtra(ChatActivity.EXTRA_OPEN_REQUEST_NANOS, SystemClock.elapsedRealtimeNanos());
        startActivity(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        if (repository == null) return;
        loadServerCalls();
        repository.acknowledgePendingIncomingDeliveries();
        repository.setEventListener(new ChatRepository.EventListener() {
            @Override public void onTyping(String chatId, String userId, boolean typing) {
                if (homeView != null) homeView.setChatTyping(chatId, typing);
            }
            @Override public void onSocketError(String error) { }
            @Override public void onTotalUnread(int totalUnread) {
                if (homeView != null) homeView.setTotalUnread(totalUnread);
            }
        });
    }

    private void loadServerCalls() {
        String uid = LoginStateManager.getInstance().getUID(this);
        if (uid == null || uid.trim().isEmpty()) return;
        AppFunctionManager.getInstance().getCallList(uid, new AppFunctionManager.Callback() {
            @Override public void onSuccess(Object object) {
                if (!(object instanceof JsonObject)) return;
                JsonArray values = ((JsonObject) object).getAsJsonArray("calls");
                if (values == null) return;
                latestServerCalls = values.deepCopy();
                submitServerCalls(latestServerCalls);
            }
            @Override public void onError(String error) { /* Keep locally cached call rows. */ }
        });
    }

    private void submitServerCalls(JsonArray values) {
        List<CallLog> calls = new ArrayList<>();
        Map<String, ChatEntity> chatsById = new HashMap<>();
        for (ChatEntity chat : latestChatEntities) chatsById.put(chat.chatId, chat);
        String ownId = normalizeAccountId(LoginStateManager.getInstance().getUID(this));
        DateFormat rowTime = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
        DateFormat fullTime = DateFormat.getDateTimeInstance(
                DateFormat.LONG, DateFormat.SHORT, Locale.getDefault());
        for (JsonElement element : values) {
            if (!element.isJsonObject()) continue;
            JsonObject call = element.getAsJsonObject();
            String chatId = jsonString(call, "chatId");
            String callerId = jsonString(call, "callerId");
            String receiverId = jsonString(call, "receiverId");
            String otherId = ownId.equals(normalizeAccountId(callerId))
                    ? receiverId : callerId;
            ChatEntity chat = chatsById.get(chatId);
            String contact = chat != null && chat.contactName != null
                    && !chat.contactName.trim().isEmpty() ? chat.contactName : otherId;
            long endedAt = jsonLong(call, "endedAt");
            long duration = jsonLong(call, "durationSeconds");
            Date date = new Date(endedAt > 0 ? endedAt : jsonLong(call, "createdAt"));
            calls.add(new CallLog(contact, rowTime.format(date), fullTime.format(date),
                    formatCallDuration(duration),
                    "video".equals(jsonString(call, "mediaType"))));
        }
        if (homeView != null) homeView.submitCalls(calls);
    }

    private static String jsonString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static long jsonLong(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? 0L : value.getAsLong();
    }

    private static String formatCallDuration(long seconds) {
        if (seconds <= 0) return "0 sec";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remaining = seconds % 60;
        if (hours > 0) return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, remaining);
        return String.format(Locale.getDefault(), "%d:%02d", minutes, remaining);
    }

    @Override public void onOpenCall(CallLog callLog) {
        Intent intent = new Intent(this, CallDetailActivity.class);
        intent.putExtra(CallDetailActivity.EXTRA_CONTACT_NAME, callLog.getContactName());
        intent.putExtra(CallDetailActivity.EXTRA_CALLED_TIME, callLog.getCalledTime());
        intent.putExtra(CallDetailActivity.EXTRA_FULL_CALLED_DATE_TIME,
                callLog.getFullCalledDateTime());
        intent.putExtra(CallDetailActivity.EXTRA_DURATION, callLog.getDuration());
        intent.putExtra(CallDetailActivity.EXTRA_IS_VIDEO_CALL, callLog.isVideoCall());
        startActivity(intent);
    }

    @Override public void onNewChat() {
        startActivity(new Intent(this, NewChatActivity.class));
    }

    @Override public void onNewGroup() {
        Toast.makeText(this, "Create a group", Toast.LENGTH_SHORT).show();
    }

    @Override public void onMakeCall() {
        Toast.makeText(this, R.string.make_call, Toast.LENGTH_SHORT).show();
    }

    @Override public void onOpenMenuDialog() {
        if (homeMenuDialog != null) homeMenuDialog.show();
    }

    @Override public void onBulkGroup(List<Chat> chats) {
        if (homeView != null) homeView.clearChatSelection();
        Toast.makeText(this, "Create group with " + chats.size() + " selected chats",
                Toast.LENGTH_SHORT).show();
    }

    @Override public void onBulkPin(List<Chat> chats) {
        boolean allPinned = !chats.isEmpty();
        for (Chat chat : chats) allPinned &= chat.isPinned();
        applyBulkSetting(chats, "pin", allPinned ? 0 : 1,
                allPinned ? "Chats unpinned" : "Chats pinned", false);
    }

    @Override public void onBulkMute(List<Chat> chats) {
        boolean allMuted = !chats.isEmpty();
        for (Chat chat : chats) allMuted &= chat.isMuted();
        applyBulkSetting(chats, "mute", allMuted ? 0 : -1,
                allMuted ? "Chats unmuted" : "Chats muted", false);
    }

    @Override public void onBulkDelete(List<Chat> chats) {
        applyBulkSetting(chats, "delete", 1, "Chats deleted", true);
    }

    @Override public void onChatSelectionChanged(boolean selected) {
        getWindow().setStatusBarColor(selected
                ? SELECTION_STATUS_BAR_COLOR
                : ContextCompat.getColor(this, R.color.login_system_bar_background));
    }

    private void applyBulkSetting(List<Chat> chats, String setting, long value,
                                  String successMessage, boolean deleteLocal) {
        if (repository == null || chats == null || chats.isEmpty()) return;
        if (homeView != null) homeView.clearChatSelection();
        List<String> chatIds = new ArrayList<>();
        for (Chat chat : chats) {
            if (chat.getChatId() != null && !chat.getChatId().trim().isEmpty()) {
                chatIds.add(chat.getChatId());
            }
        }
        repository.updateChatSettings(chatIds, setting, value,
                new AppFunctionManager.Callback() {
                    @Override public void onSuccess(Object object) {
                        if (deleteLocal) {
                            for (String chatId : chatIds) repository.deleteLocalChat(chatId);
                        }
                        finishBulkOperation(successMessage);
                    }
                    @Override public void onError(String error) {
                        finishBulkOperation("Chats could not be updated");
                    }
                    private void finishBulkOperation(String message) {
                        Toast.makeText(HomeActivity.this, message, Toast.LENGTH_SHORT).show();
                        String uid = LoginStateManager.getInstance().getUID(HomeActivity.this);
                        if (uid != null) repository.refreshChatList(normalizeAccountId(uid));
                    }
                });
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private List<Chat> toChats(List<ChatEntity> entities) {
        List<Chat> chats = new ArrayList<>();
        if (entities == null) return chats;
        for (ChatEntity entity : entities) {
            String contact = entity.contactName == null || entity.contactName.isEmpty()
                    ? entity.otherUserId : entity.contactName;
            String localPath = entity.localProfilePhotoPath;
            if (localPath == null || localPath.isEmpty()) {
                localPath = ChatProfilePhotoStore.getLocalPath(this, entity.otherUserId);
            }
            chats.add(new Chat(entity.chatId, contact, entity.profilePhotoUrl, localPath,
                    homeMessagePreview(entity), entity.lastMessageTime,
                    normalizeAccountId(entity.lastMessageSenderId).equals(
                            normalizeAccountId(LoginStateManager.getInstance().getUID(this))),
                    entity.lastMessageDeliveredTime, entity.lastMessageReadTime,
                    entity.lastMessageStatus,
                    entity.lastMessageType, entity.lastMessageAttachmentName,
                    entity.unreadCount,
                    entity.pinned, entity.notificationMuted, entity.archived,
                    entity.isOnline, entity.lastSeen));
        }
        return chats;
    }

    private void submitCalls() {
        if (homeView == null) return;
        Map<String, ChatEntity> chatsById = new HashMap<>();
        for (ChatEntity chat : latestChatEntities) chatsById.put(chat.chatId, chat);
        String ownId = normalizeAccountId(LoginStateManager.getInstance().getUID(this));
        DateFormat rowTime = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
        DateFormat fullTime = DateFormat.getDateTimeInstance(
                DateFormat.LONG, DateFormat.SHORT, Locale.getDefault());
        List<CallLog> calls = new ArrayList<>();
        for (MessageEntity message : latestCallMessages) {
            ChatEntity chat = chatsById.get(message.chatId);
            String otherId = ownId.equals(normalizeAccountId(message.senderId))
                    ? message.receiverId : message.senderId;
            String contactName = chat != null && chat.contactName != null
                    && !chat.contactName.trim().isEmpty() ? chat.contactName : otherId;
            Date date = new Date(message.sentTime);
            calls.add(new CallLog(contactName, rowTime.format(date), fullTime.format(date),
                    callDuration(message.text), "video_call".equals(message.messageType)));
        }
        homeView.submitCalls(calls);
    }

    private static String callDuration(String text) {
        if (text == null) return "0 sec";
        int close = text.indexOf(']');
        String value = close >= 0 ? text.substring(close + 1).trim() : text.trim();
        if (value.isEmpty() || value.toLowerCase(Locale.US).contains("missed")
                || value.toLowerCase(Locale.US).contains("connect")) return "0 sec";
        return value;
    }

    private String homeMessagePreview(ChatEntity entity) {
        String text = entity.lastMessage;
        if (text == null) return null;
        String type = entity.lastMessageType == null ? "" : entity.lastMessageType;
        String action = "chat_report".equalsIgnoreCase(type) ? "reported"
                : "chat_block".equalsIgnoreCase(type) ? "blocked"
                : "chat_unblock".equalsIgnoreCase(type) ? "unblocked" : "";
        if (action.isEmpty()) return text;
        String ownNumber = normalizeAccountId(
                LoginStateManager.getInstance().getUID(this));
        String normalizedText = text.trim();
        String[] participants = normalizedText.split(" " + action + " ", 2);
        if (!ownNumber.isEmpty() && participants.length == 2) return
                (ownNumber.equals(normalizeAccountId(participants[0])) ? "You" : participants[0])
                        + " " + action + " "
                        + (ownNumber.equals(normalizeAccountId(participants[1]))
                        ? "You" : participants[1]);
        return normalizedText;
    }

    private static String normalizeAccountId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("<plus>")) {
            return normalized.substring("<plus>".length());
        }
        return normalized.startsWith("+") ? normalized.substring(1) : normalized;
    }

    @Override protected void onDestroy() {
        if (homeView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(homeView, null);
            homeView.release();
            homeView = null;
        }
        if (homeMenuDialog != null) {
            homeMenuDialog.release();
            homeMenuDialog = null;
        }
        super.onDestroy();
    }
}
