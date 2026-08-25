package com.w3n.pinggo.activity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
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
import com.w3n.pinggo.views.home.ChatActionsDialogView;
import com.w3n.pinggo.views.home.HomeView;

import java.util.ArrayList;
import java.util.List;

/** Hosts the AAR-native home surface and owns lifecycle, data, and navigation. */
public class HomeActivity extends AppCompatActivity implements HomeView.Listener {
    private HomeView homeView;
    private ChatActionsDialogView chatActionsDialog;
    private ChatRepository repository;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        homeView = new HomeView(this, this);
        setContentView(homeView);
        ExitAppController.install(this, null);
        chatActionsDialog = new ChatActionsDialogView(this, (chat, action) -> {
            String setting;
            long value;
            String successMessage;
            long now = System.currentTimeMillis();
            switch (action) {
                case MUTE_ONE_DAY:
                    setting = "mute"; value = now + 24L * 60 * 60 * 1000;
                    successMessage = "Notifications muted for 1 day"; break;
                case MUTE_ONE_WEEK:
                    setting = "mute"; value = now + 7L * 24 * 60 * 60 * 1000;
                    successMessage = "Notifications muted for 1 week"; break;
                case MUTE_ONE_MONTH:
                    setting = "mute"; value = now + 30L * 24 * 60 * 60 * 1000;
                    successMessage = "Notifications muted for 1 month"; break;
                case MUTE_UNTIL_UNMUTED:
                    setting = "mute"; value = -1;
                    successMessage = "Notifications muted until you unmute"; break;
                case UNMUTE:
                    setting = "mute"; value = 0;
                    successMessage = "Notifications unmuted"; break;
                case PIN:
                    setting = "pin"; value = chat.isPinned() ? 0 : 1;
                    successMessage = chat.isPinned() ? "Chat unpinned" : "Chat pinned"; break;
                default:
                    setting = "archive"; value = chat.isArchived() ? 0 : 1;
                    successMessage = chat.isArchived() ? "Chat unarchived" : "Chat archived";
                    break;
            }
            updateChatSetting(chat, setting, value, successMessage);
        });
        ViewGroup content = findViewById(android.R.id.content);
        content.addView(chatActionsDialog, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
        window.setNavigationBarColor(systemBarColor);

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
        repository.observeChats().observe(this,
                entities -> homeView.submitChats(toChats(entities)));
        String uid = LoginStateManager.getInstance().getUID(this);
        if (uid != null && !uid.trim().isEmpty()) {
            repository.refreshChatList(normalizeAccountId(uid));
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
        startActivity(intent);
    }

    private void updateChatSetting(Chat chat, String setting, long value,
                                   String successMessage) {
        if (repository == null || chat == null) return;
        repository.updateChatSetting(chat.getChatId(), setting, value,
                new AppFunctionManager.Callback() {
                    @Override public void onSuccess(Object object) {
                        Toast.makeText(HomeActivity.this, successMessage,
                                Toast.LENGTH_SHORT).show();
                        String uid = LoginStateManager.getInstance().getUID(HomeActivity.this);
                        if (uid != null) repository.refreshChatList(normalizeAccountId(uid));
                    }

                    @Override public void onError(String error) {
                        Toast.makeText(HomeActivity.this,
                                error == null || error.trim().isEmpty()
                                        ? "Unable to update chat" : error,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override public void onChatLongPress(Chat chat) {
        if (chatActionsDialog != null) chatActionsDialog.show(chat);
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

    @Override public void onMakeCall() {
        Toast.makeText(this, R.string.make_call, Toast.LENGTH_SHORT).show();
    }

    @Override public void onOpenSettings() {
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
                    entity.lastMessage, entity.lastMessageTime, entity.unreadCount,
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
        if (chatActionsDialog != null) {
            chatActionsDialog.release();
            chatActionsDialog = null;
        }
        super.onDestroy();
    }
}
