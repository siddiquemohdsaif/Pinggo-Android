package com.w3n.pinggo.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.w3n.pinggo.data.local.CallEntity;
import com.w3n.pinggo.data.repository.CallRepository;
import com.w3n.pinggo.contacts.DeviceContactResolver;
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

/**
 * Hosts the AAR-native home surface and owns lifecycle, data, and navigation.
 */
public class HomeActivity extends AppCompatActivity implements HomeView.Listener {
    private static final int SELECTION_STATUS_BAR_COLOR = 0xFFE9EDF0;
    private static final int HOME_SYSTEM_BAR_COLOR = 0xFFF7F9FB;
    private HomeView homeView;
    private HomeMenuDialogView homeMenuDialog;
    private ChatRepository repository;
    private List<ChatEntity> latestChatEntities = new ArrayList<>();
    private CallRepository callRepository;
    private List<CallEntity> latestCallEntities = new ArrayList<>();
    private String nextCallCursor;
    private boolean callListHasMore;
    private boolean callListLoading;
    private int callListGeneration;
    private int callListVisibleLimit = 20;
    private boolean callPaginationRevealPending;
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
            });
    private final ActivityResultLauncher<String> contactsPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted)
                    warmDeviceContacts();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        homeView = new HomeView(this, this);
        setContentView(homeView);
        ExitAppController.install(this, null);
        ViewGroup content = findViewById(android.R.id.content);
        homeMenuDialog = new HomeMenuDialogView(this, new HomeMenuDialogView.Listener() {
            @Override
            public void onNewChat() {
                HomeActivity.this.onNewChat();
            }

            @Override
            public void onNewGroup() {
                HomeActivity.this.onNewGroup();
            }

            @Override
            public void onLinkedDevices() {
                Toast.makeText(HomeActivity.this, "Linked Devices", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSettings() {
                openSettings();
            }
        });
        content.addView(homeMenuDialog, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (homeMenuDialog != null && homeMenuDialog.dismissIfShowing())
                    return;
                if (homeView != null && homeView.clearChatSelection())
                    return;

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
        requestContactsPermission();
        requestNotificationPermission();
    }

    private void requestContactsPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
            warmDeviceContacts();
        else
            contactsPermission.launch(Manifest.permission.READ_CONTACTS);
    }

    private void warmDeviceContacts() {
        DeviceContactResolver.warmUp(this, () -> {
            if (homeView == null)
                return;
            homeView.submitChats(toChats(latestChatEntities));
            submitCachedCalls(latestCallEntities);
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void configureSystemBars() {
        Window window = getWindow();
        // Keep the activity full-sized when the IME opens. Screens that need to
        // react to the keyboard do so through WindowInsetsCompat.
        WindowCompat.setDecorFitsSystemWindows(window, false);

        window.setStatusBarColor(HOME_SYSTEM_BAR_COLOR);
        window.setNavigationBarColor(HOME_SYSTEM_BAR_COLOR);

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
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
            submitCachedCalls(latestCallEntities);
            repository.acknowledgePendingIncomingDeliveries();
        });
        String uid = LoginStateManager.getInstance().getUID(this);
        if (uid != null && !uid.trim().isEmpty()) {
            repository.ensureChatListLoaded(normalizeAccountId(uid));
            callRepository = CallRepository.getInstance(this);
            callRepository.observeLatestCalls(uid).observe(this, calls -> {
                latestCallEntities = calls == null ? new ArrayList<>() : calls;
                submitCachedCalls(latestCallEntities);
                if (callPaginationRevealPending) {
                    callPaginationRevealPending = false;
                    homeView.setCallsPaginationLoading(false);
                }
            });
        }
    }

    @Override
    public void onOpenChat(Chat chat) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, chat.getContactName());
        intent.putExtra(ChatActivity.EXTRA_CHAT_ID, chat.getChatId());
        intent.putExtra(ChatActivity.EXTRA_PROFILE_PHOTO_URL, chat.getProfilePhotoUrl());
        String localPath = chat.getLocalProfilePhotoPath();
        boolean groupChat = chat.getChatId() != null && chat.getChatId().startsWith("grp_");
        if (!groupChat && (localPath == null || localPath.trim().isEmpty())) {
            localPath = ChatProfilePhotoStore.getLocalPath(this, chat.getPhoneNumber());
        }
        intent.putExtra(ChatActivity.EXTRA_LOCAL_PROFILE_PHOTO_PATH, localPath);
        intent.putExtra(ChatActivity.EXTRA_OPEN_REQUEST_NANOS, SystemClock.elapsedRealtimeNanos());
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repository == null)
            return;
        refreshServerCalls();
        repository.acknowledgePendingIncomingDeliveries();
        repository.setEventListener(new ChatRepository.EventListener() {
            @Override
            public void onTyping(String chatId, String userId, boolean typing) {
                if (homeView != null)
                    homeView.setChatTyping(chatId, typing);
            }

            @Override
            public void onSocketError(String error) {
            }

            @Override
            public void onTotalUnread(int totalUnread) {
                if (homeView != null)
                    homeView.setTotalUnread(totalUnread);
            }

            @Override
            public void onCallsChanged() {
                refreshServerCalls();
            }
        });
    }

    private void refreshServerCalls() {
        callListGeneration++;
        callListLoading = false;
        nextCallCursor = null;
        callListHasMore = true;
        callListVisibleLimit = 20;
        callPaginationRevealPending = false;
        if (homeView != null) {
            homeView.setCallsPaginationLoading(false);
            submitCachedCalls(latestCallEntities);
        }
        loadServerCalls();
    }

    private void loadServerCalls() {
        if (callListLoading || callPaginationRevealPending || !callListHasMore)
            return;
        String uid = LoginStateManager.getInstance().getUID(this);
        if (uid == null || uid.trim().isEmpty())
            return;
        callListLoading = true;
        final int requestGeneration = callListGeneration;
        final String requestedCursor = nextCallCursor;
        final boolean pagination = requestedCursor != null && !requestedCursor.isEmpty();
        if (pagination && homeView != null)
            homeView.setCallsPaginationLoading(true);
        AppFunctionManager.getInstance().getCallList(uid, 20, requestedCursor,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        if (requestGeneration != callListGeneration)
                            return;
                        callListLoading = false;
                        if (!(object instanceof JsonObject)) {
                            callPaginationRevealPending = false;
                            if (homeView != null) homeView.setCallsPaginationLoading(false);
                            return;
                        }
                        JsonObject response = (JsonObject) object;
                        JsonArray values = response.getAsJsonArray("calls");
                        if (values == null)
                            values = new JsonArray();
                        if (pagination && values.size() > 0) {
                            callListVisibleLimit += values.size();
                            callPaginationRevealPending = true;
                        }
                        if (callRepository != null)
                            callRepository.cachePage(uid, values);
                        nextCallCursor = jsonString(response, "nextCursor");
                        callListHasMore = response.has("hasMore")
                                && response.get("hasMore").getAsBoolean()
                                && !nextCallCursor.isEmpty();
                        if (pagination && values.size() == 0 && homeView != null)
                            homeView.setCallsPaginationLoading(false);
                    }

                    @Override
                    public void onError(String error) {
                        if (requestGeneration == callListGeneration)
                            callListLoading = false;
                        callPaginationRevealPending = false;
                        if (homeView != null) homeView.setCallsPaginationLoading(false);
                    }
                });
    }

    @Override
    public void onLoadMoreCalls() {
        loadServerCalls();
    }

    private void submitCachedCalls(List<CallEntity> values) {
        List<CallLog> calls = new ArrayList<>();
        Map<String, ChatEntity> chatsById = new HashMap<>();
        for (ChatEntity chat : latestChatEntities)
            chatsById.put(chat.chatId, chat);
        String ownId = normalizeAccountId(LoginStateManager.getInstance().getUID(this));
        DateFormat rowTime = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
        DateFormat fullTime = DateFormat.getDateTimeInstance(
                DateFormat.LONG, DateFormat.SHORT, Locale.getDefault());
        if (values == null)
            values = new ArrayList<>();
        int visibleCount = Math.min(callListVisibleLimit, values.size());
        for (int index = 0; index < visibleCount; index++) {
            CallEntity call = values.get(index);
            String chatId = call.chatId == null ? "" : call.chatId;
            String callerId = call.callerId == null ? "" : call.callerId;
            String receiverId = call.receiverId == null ? "" : call.receiverId;
            String otherId = ownId.equals(normalizeAccountId(callerId))
                    ? receiverId
                    : callerId;
            ChatEntity chat = chatsById.get(chatId);
            String contact = DeviceContactResolver.cachedNameOrPhone(otherId);
            long endedAt = call.endedAt;
            long duration = call.durationSeconds;
            boolean outgoing = ownId.equals(normalizeAccountId(callerId));
            boolean missed = call.connectedAt == null || call.connectedAt <= 0;
            Date date = new Date(endedAt > 0 ? endedAt : call.createdAt);
            calls.add(new CallLog(chatId, call.messageId, otherId,
                    contact, rowTime.format(date), fullTime.format(date),
                    formatCallDuration(duration),
                    "video".equals(call.mediaType), outgoing, missed));
        }
        if (homeView != null)
            homeView.submitCalls(calls);
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
        if (seconds <= 0)
            return "0 sec";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remaining = seconds % 60;
        if (hours > 0)
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, remaining);
        return String.format(Locale.getDefault(), "%d:%02d", minutes, remaining);
    }

    @Override
    public void onOpenCall(CallLog callLog) {
        Intent intent = new Intent(this, CallDetailActivity.class);
        intent.putExtra(CallDetailActivity.EXTRA_CHAT_ID, callLog.getChatId());
        intent.putExtra(CallDetailActivity.EXTRA_PHONE_NUMBER, callLog.getPhoneNumber());
        intent.putExtra(CallDetailActivity.EXTRA_CONTACT_NAME, callLog.getContactName());
        intent.putExtra(CallDetailActivity.EXTRA_CALLED_TIME, callLog.getCalledTime());
        intent.putExtra(CallDetailActivity.EXTRA_FULL_CALLED_DATE_TIME,
                callLog.getFullCalledDateTime());
        intent.putExtra(CallDetailActivity.EXTRA_DURATION, callLog.getDuration());
        intent.putExtra(CallDetailActivity.EXTRA_IS_VIDEO_CALL, callLog.isVideoCall());
        startActivity(intent);
    }

    @Override
    public void onStartCall(CallLog callLog, boolean video) {
        openCall(callLog.getChatId(), callLog.getPhoneNumber(), "", video);
    }

    private void openCall(String chatId, String phoneNumber, String profilePath, boolean video) {
        Intent intent = new Intent(this, video ? VideoCallActivity.class : VoiceCallActivity.class);
        intent.putExtra(VoiceCallActivity.EXTRA_CALL_CHAT_ID, chatId);
        intent.putExtra(VoiceCallActivity.EXTRA_CALL_ID, java.util.UUID.randomUUID().toString());
        intent.putExtra(VoiceCallActivity.EXTRA_CALLER_ID, phoneNumber);
        intent.putExtra(VoiceCallActivity.EXTRA_PHONE_NUMBER,
                DeviceContactResolver.cachedNameOrPhone(phoneNumber));
        intent.putExtra(VoiceCallActivity.EXTRA_PROFILE_PATH, profilePath);
        startActivity(intent);
    }

    @Override
    public void onNewChat() {
        startActivity(new Intent(this, NewChatActivity.class));
    }

    @Override
    public void onNewGroup() {
        Intent intent = new Intent(this, NewChatActivity.class);
        intent.putExtra(NewChatActivity.EXTRA_CREATE_GROUP, true);
        startActivity(intent);
    }

    @Override
    public void onMakeCall() {
        Toast.makeText(this, R.string.make_call, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onOpenMenuDialog() {
        if (homeMenuDialog != null)
            homeMenuDialog.show();
    }

    @Override
    public void onBulkGroup(List<Chat> chats) {
        if (homeView != null)
            homeView.clearChatSelection();
        Toast.makeText(this, "Create group with " + chats.size() + " selected chats",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBulkPin(List<Chat> chats) {
        boolean allPinned = !chats.isEmpty();
        for (Chat chat : chats)
            allPinned &= chat.isPinned();
        applyBulkSetting(chats, "pin", allPinned ? 0 : 1,
                allPinned ? "Chats unpinned" : "Chats pinned", false);
    }

    @Override
    public void onBulkMute(List<Chat> chats) {
        boolean allMuted = !chats.isEmpty();
        for (Chat chat : chats)
            allMuted &= chat.isMuted();
        applyBulkSetting(chats, "mute", allMuted ? 0 : -1,
                allMuted ? "Chats unmuted" : "Chats muted", false);
    }

    @Override
    public void onBulkDelete(List<Chat> chats) {
        applyBulkSetting(chats, "delete", 1, "Chats deleted", true);
    }

    @Override
    public void onChatSelectionChanged(boolean selected) {
        getWindow().setStatusBarColor(selected
                ? SELECTION_STATUS_BAR_COLOR
                : HOME_SYSTEM_BAR_COLOR);
    }

    private void applyBulkSetting(List<Chat> chats, String setting, long value,
            String successMessage, boolean deleteLocal) {
        if (repository == null || chats == null || chats.isEmpty())
            return;
        if (homeView != null)
            homeView.clearChatSelection();
        List<String> chatIds = new ArrayList<>();
        for (Chat chat : chats) {
            if (chat.getChatId() != null && !chat.getChatId().trim().isEmpty()) {
                chatIds.add(chat.getChatId());
            }
        }
        repository.updateChatSettings(chatIds, setting, value,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        if (deleteLocal) {
                            for (String chatId : chatIds)
                                repository.deleteLocalChat(chatId);
                        }
                        finishBulkOperation(successMessage);
                    }

                    @Override
                    public void onError(String error) {
                        finishBulkOperation("Chats could not be updated");
                    }

                    private void finishBulkOperation(String message) {
                        Toast.makeText(HomeActivity.this, message, Toast.LENGTH_SHORT).show();
                        String uid = LoginStateManager.getInstance().getUID(HomeActivity.this);
                        if (uid != null)
                            repository.refreshChatList(normalizeAccountId(uid));
                    }
                });
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private List<Chat> toChats(List<ChatEntity> entities) {
        List<Chat> chats = new ArrayList<>();
        if (entities == null)
            return chats;
        for (ChatEntity entity : entities) {
            String contact = entity.isGroup
                    ? safeGroupName(entity.contactName)
                    : DeviceContactResolver.cachedNameOrPhone(entity.otherUserId);
            String localPath = entity.localProfilePhotoPath;
            if (!entity.isGroup && (localPath == null || localPath.isEmpty())) {
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

    private static String safeGroupName(String value) {
        return value == null || value.trim().isEmpty() ? "Group" : value.trim();
    }

    private String homeMessagePreview(ChatEntity entity) {
        String text = entity.lastMessage;
        if (text == null)
            return null;
        String type = entity.lastMessageType == null ? "" : entity.lastMessageType;
        String action = "chat_report".equalsIgnoreCase(type) ? "reported"
                : "chat_block".equalsIgnoreCase(type) ? "blocked"
                        : "chat_unblock".equalsIgnoreCase(type) ? "unblocked" : "";
        if (action.isEmpty())
            return text;
        String ownNumber = normalizeAccountId(
                LoginStateManager.getInstance().getUID(this));
        String normalizedText = text.trim();
        String[] participants = normalizedText.split(" " + action + " ", 2);
        if (!ownNumber.isEmpty() && participants.length == 2)
            return (ownNumber.equals(normalizeAccountId(participants[0])) ? "You" : participants[0])
                    + " " + action + " "
                    + (ownNumber.equals(normalizeAccountId(participants[1]))
                            ? "You"
                            : participants[1]);
        return normalizedText;
    }

    private static String normalizeAccountId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("<plus>")) {
            return normalized.substring("<plus>".length());
        }
        return normalized.startsWith("+") ? normalized.substring(1) : normalized;
    }

    @Override
    protected void onDestroy() {
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
