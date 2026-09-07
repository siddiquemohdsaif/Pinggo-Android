package com.w3n.pinggo.notification;

import androidx.annotation.NonNull;
import android.util.Log;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.data.repository.ChatRepository;
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
        if (!LoginStateManager.getInstance().isLoggedIn(this)) {
            Log.w("PingGoCallTrace", "fcm_ignored_not_logged_in type=" + type
                    + " callId=" + callId);
            return;
        }
        if ("new_message".equals(message.getData().get("type"))) {
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
}
