package com.w3n.pinggo.notification;

import androidx.annotation.NonNull;
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
        if (!LoginStateManager.getInstance().isLoggedIn(this)) return;
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
        }
    }

    private static String value(RemoteMessage message, String key) {
        String value = message.getData().get(key);
        return value == null ? "" : value.trim();
    }
}
