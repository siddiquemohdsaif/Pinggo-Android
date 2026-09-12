package com.w3n.pinggo.notification;

import androidx.annotation.NonNull;
import android.util.Log;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.data.local.SessionLogoutManager;
import java.util.Collections;

public class PingGoMessagingService extends FirebaseMessagingService {
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        FcmTokenManager.saveAndUpload(getApplicationContext(), token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        String type = value(message, "type");
        String callId = value(message, "callId");
        Log.i("PingGoCallTrace", "fcm_received type=" + type + " callId=" + callId
                + " dataKeys=" + message.getData().keySet());
        if ("account_logout".equals(type)) {
            LoginStateManager login = LoginStateManager.getInstance();
            String currentAccount = login.getUID(this);
            String eventAccount = value(message, "accountId");
            long revokedAt = number(message, "revokedAt");
            if (currentAccount != null && currentAccount.equals(eventAccount)
                    && login.getLoginAt(this) <= revokedAt) {
                String logoutMessage = value(message, "message");
                if (logoutMessage.isEmpty()
                        && "device_unlinked".equals(value(message, "reason"))) {
                    logoutMessage = "This companion device was logged out by the primary device.";
                }
                SessionLogoutManager.forceLogout(this, logoutMessage);
            }
            return;
        }
        if (!LoginStateManager.getInstance().isLoggedIn(this)) {
            Log.w("PingGoCallTrace", "fcm_ignored_not_logged_in type=" + type
                    + " callId=" + callId);
            return;
        }
        if ("device_linked".equals(type) || "device_unlinked".equals(type)) {
            LoginStateManager login = LoginStateManager.getInstance();
            String currentAccount = login.getUID(this);
            String eventAccount = value(message, "accountId");
            if (login.isCompanionDevice(this)
                    || currentAccount == null || !currentAccount.equals(eventAccount)) {
                Log.w("PingGoCallTrace", "device_activity_fcm_ignored type=" + type);
                return;
            }
            PingGoNotificationManager.showLinkedDeviceNotification(this, message.getData());
        } else if ("new_message".equals(type)) {
            String chatId = value(message, "chatId");
            String messageId = value(message, "messageId");
            if (!chatId.isEmpty() && !messageId.isEmpty()) {
                ChatRepository repository = ChatRepository.getInstance(this);
                repository.connect();
                repository.markDelivered(
                        chatId, Collections.singletonList(messageId));
            }
            PingGoNotificationManager.showMessageNotification(this, message.getData());
        } else if ("call_incoming".equals(message.getData().get("type"))) {
            // Wake signaling immediately so the server can also deliver queued ICE candidates.
            ChatRepository.getInstance(this).connect();
            PingGoNotificationManager.showIncomingCallNotification(this, message.getData());
        } else if ("call_missed".equals(message.getData().get("type"))) {
            PingGoNotificationManager.showMissedCallNotification(this, message.getData());
        } else if ("call_cancelled".equals(message.getData().get("type"))) {
            PingGoNotificationManager.clearCallNotification(this,
                    value(message, "callId"));
        }
    }

    private static String value(RemoteMessage message, String key) {
        String value = message.getData().get(key);
        return value == null ? "" : value.trim();
    }

    private static long number(RemoteMessage message, String key) {
        try { return Long.parseLong(value(message, key)); }
        catch (Exception ignored) { return 0L; }
    }
}
