package com.w3n.pinggo.activity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.Toast;

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
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.modals.CallLog;
import com.w3n.pinggo.modals.Chat;
import com.w3n.pinggo.views.common.ExitAppController;
import com.w3n.pinggo.views.home.HomeMenuDialogView;
import com.w3n.pinggo.views.home.HomeView;

import java.util.ArrayList;
import java.util.List;

/** Hosts the AAR-native home surface and owns lifecycle, data, and navigation. */
public class HomeActivity extends AppCompatActivity implements HomeView.Listener {
    private static final int SELECTION_STATUS_BAR_COLOR = 0xFFE9EDF0;
    private static final int BOTTOM_SYSTEM_NAVIGATION_COLOR = 0xFFF9FBFE;
    private HomeView homeView;
    private HomeMenuDialogView homeMenuDialog;
    private ChatRepository repository;

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
            homeView.submitChats(toChats(entities));
            repository.acknowledgePendingIncomingDeliveries();
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
                    entity.lastMessage, entity.lastMessageTime,
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
