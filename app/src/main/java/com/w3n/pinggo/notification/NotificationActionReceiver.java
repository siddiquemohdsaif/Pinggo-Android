package com.w3n.pinggo.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.RemoteInput;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.data.repository.ChatRepository;
import java.util.concurrent.atomic.AtomicBoolean;
import com.google.gson.JsonObject;

public class NotificationActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Context appContext = context.getApplicationContext();
        String action = intent.getAction();
        String callId = value(intent, PingGoNotificationManager.EXTRA_CALL_ID);
        Log.i("PingGoCallTrace", "notification_action action=" + action
                + " callId=" + callId);
        if (!LoginStateManager.getInstance().isLoggedIn(appContext)) {
            Log.w("PingGoCallTrace", "notification_action_ignored_not_logged_in callId=" + callId);
            return;
        }
        String chatId = value(intent, PingGoNotificationManager.EXTRA_CHAT_ID);
        String messageId = value(intent, PingGoNotificationManager.EXTRA_MESSAGE_ID);
        String senderId = value(intent, PingGoNotificationManager.EXTRA_SENDER_ID);
        if (chatId.isEmpty()) return;

        ChatRepository repository = ChatRepository.getInstance(appContext);
        if (PingGoNotificationManager.ACTION_CALL_DECLINE.equals(action)) {
            PingGoNotificationManager.rememberCallAction(appContext, callId, action);
            JsonObject event = new JsonObject();
            event.addProperty("type", "call_reject");
            event.addProperty("callId", callId);
            event.addProperty("receiverId", senderId);
            repository.connect();
            repository.sendCallEvent(event);
            PingGoNotificationManager.clearCallNotification(appContext, callId);
            return;
        }
        if (PingGoNotificationManager.ACTION_CALL_ANSWER.equals(action)) {
            PingGoNotificationManager.rememberCallAction(appContext, callId, action);
            Log.i("PingGoCallTrace", "answer_saved_connecting_socket callId=" + callId
                    + " chatId=" + chatId);
            repository.connect();
            PingGoNotificationManager.clearCallNotification(appContext,
                    callId);
            return;
        }
        if (PingGoNotificationManager.ACTION_CALL_OPEN.equals(action)) {
            PingGoNotificationManager.rememberCallAction(appContext, callId, action);
            Log.i("PingGoCallTrace", "open_saved_connecting_socket callId=" + callId
                    + " chatId=" + chatId);
            repository.connect();
            return;
        }
        if (PingGoNotificationManager.ACTION_REPLY.equals(action)) {
            Bundle results = RemoteInput.getResultsFromIntent(intent);
            CharSequence reply = results == null
                    ? null : results.getCharSequence(PingGoNotificationManager.REMOTE_INPUT_REPLY);
            if (reply != null && !reply.toString().trim().isEmpty() && !senderId.isEmpty()) {
                repository.connect();
                repository.sendMessage(chatId, senderId, reply.toString().trim(), null);
                PingGoNotificationManager.clearChatNotification(appContext, chatId);
            }
        } else if (PingGoNotificationManager.ACTION_MARK_READ.equals(action)) {
            PendingResult pendingResult = goAsync();
            AtomicBoolean finished = new AtomicBoolean(false);
            Runnable finish = () -> {
                if (finished.compareAndSet(false, true)) pendingResult.finish();
            };
            repository.connect();
            repository.markAllSeen(chatId, new ChatRepository.SeenCallback() {
                @Override public void onSuccess() {
                    PingGoNotificationManager.clearChatNotification(appContext, chatId);
                    finish.run();
                }

                @Override public void onError(String message) {
                    // Keep this chat visible so the user can retry the action.
                    finish.run();
                }
            });
            new Handler(Looper.getMainLooper()).postDelayed(finish, 8_000L);
        } else if (PingGoNotificationManager.ACTION_MUTE.equals(action)) {
            AppFunctionManager.getInstance().applyAuth(appContext);
            repository.updateChatSetting(chatId, "mute", -1L,
                    new AppFunctionManager.Callback() {
                        @Override public void onSuccess(Object value) { }
                        @Override public void onError(String error) { }
                    });
            PingGoNotificationManager.clearChatNotification(appContext, chatId);
        } else if (PingGoNotificationManager.ACTION_DISMISS.equals(action)) {
            PingGoNotificationManager.hideChatNotification(appContext, chatId);
        }
    }

    private static String value(Intent intent, String key) {
        String value = intent.getStringExtra(key);
        return value == null ? "" : value.trim();
    }
}
