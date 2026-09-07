package com.w3n.pinggo.notification;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;
import com.w3n.pinggo.R;
import com.w3n.pinggo.activity.ChatActivity;
import com.w3n.pinggo.activity.HomeActivity;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public final class PingGoNotificationManager {
    public static final String MESSAGE_CHANNEL_ID = "pinggo_messages";
    public static final String EXTRA_CHAT_ID = "notificationChatId";
    public static final String EXTRA_MESSAGE_ID = "notificationMessageId";
    public static final String EXTRA_MESSAGE_IDS = "notificationMessageIds";
    public static final String EXTRA_SENDER_ID = "notificationSenderId";
    public static final String REMOTE_INPUT_REPLY = "notificationReply";
    public static final String ACTION_REPLY = "com.w3n.pinggo.notification.REPLY";
    public static final String ACTION_MARK_READ = "com.w3n.pinggo.notification.MARK_READ";
    public static final String ACTION_MUTE = "com.w3n.pinggo.notification.MUTE";
    public static final String ACTION_DISMISS = "com.w3n.pinggo.notification.DISMISS";
    private static final String GROUP_KEY = "pinggo_messages";
    private static final int SUMMARY_NOTIFICATION_ID = 0x4f000001;

    private PingGoNotificationManager() { }

    public static void createChannels(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                MESSAGE_CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("New PingGo message notifications");
        channel.enableVibration(true);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    public static void showMessageNotification(
            @NonNull Context context, @NonNull Map<String, String> data) {
        String chatId = value(data, "chatId");
        String senderId = value(data, "senderId");
        if (chatId.isEmpty() || senderId.isEmpty()) return;
        List<NotificationStateStore.ChatState> chats = NotificationStateStore.add(context, data);
        NotificationStateStore.ChatState chat = findChat(chats, chatId);
        if (chat == null) return;
        NotificationStateStore.MessageState latest = chat.latest();
        String senderName = chat.senderName.isEmpty() ? senderId : chat.senderName;
        String preview = latest.preview.isEmpty() ? "New message" : latest.preview;

        Bitmap avatar = downloadBitmap(chat.profilePhotoUrl);
        Bitmap brandingIcon = avatar != null ? avatar
                : BitmapFactory.decodeResource(context.getResources(), R.drawable.pinggo_logo);
        Bitmap picture = chat.messages.size() == 1 && "image".equals(latest.messageType)
                ? downloadBitmap(latest.attachmentUrl) : null;
        int notificationId = notificationId(chatId);

        Intent openChat = new Intent(context, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_CHAT_ID, chatId)
                .putExtra(ChatActivity.EXTRA_CHAT_NAME, senderName)
                .putExtra(ChatActivity.EXTRA_OPEN_REQUEST_NANOS,
                        SystemClock.elapsedRealtimeNanos())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, notificationId, openChat,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Person.Builder personBuilder = new Person.Builder().setName(senderName).setKey(senderId);
        if (brandingIcon != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(brandingIcon));
        }
        Person sender = personBuilder.build();

        NotificationCompat.Builder notification = new NotificationCompat.Builder(
                context, MESSAGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pinggo_notification)
                .setLargeIcon(brandingIcon)
                .setContentTitle(senderName)
                .setContentText(preview)
                .setSubText("PingGo")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setWhen(latest.receivedAt)
                .setShowWhen(true)
                .setGroup(GROUP_KEY)
                .setNumber(chat.messages.size())
                .setContentIntent(contentIntent)
                .setDeleteIntent(actionIntent(context, ACTION_DISMISS, notificationId,
                        chatId, latest.messageId, senderId))
                .addAction(replyAction(context, notificationId, chatId, latest.messageId, senderId))
                .addAction(markReadAction(context, notificationId, chat))
                .addAction(action(context, ACTION_MUTE, "Mute",
                        notificationId, chatId, latest.messageId, senderId));

        if (picture != null) {
            notification.setStyle(new NotificationCompat.BigPictureStyle()
                    .bigPicture(picture).bigLargeIcon(brandingIcon).setSummaryText(senderName));
        } else {
            Person self = new Person.Builder().setName("You").setKey("pinggo_self").build();
            NotificationCompat.MessagingStyle style =
                    new NotificationCompat.MessagingStyle(self).setConversationTitle(senderName);
            for (NotificationStateStore.MessageState storedMessage : chat.messages) {
                style.addMessage(storedMessage.preview, storedMessage.receivedAt, sender);
            }
            notification.setStyle(style);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManagerCompat.from(context).notify(notificationId, notification.build());
        showGroupSummary(context, chats);
    }

    public static void clearChatNotification(@NonNull Context context, String chatId) {
        List<NotificationStateStore.ChatState> chats =
                NotificationStateStore.remove(context, chatId);
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        manager.cancel(notificationId(chatId));
        showGroupSummary(context, chats);
    }

    public static void hideChatNotification(@NonNull Context context, String chatId) {
        List<NotificationStateStore.ChatState> chats =
                NotificationStateStore.hide(context, chatId);
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        manager.cancel(notificationId(chatId));
        showGroupSummary(context, chats);
    }

    private static void showGroupSummary(
            Context context, List<NotificationStateStore.ChatState> chats) {
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        // Some Android skins (notably MIUI) dismiss every grouped child when the
        // summary is cancelled. Keep the summary while one visible chat remains
        // so dismissing/reading one conversation cannot remove its siblings.
        if (chats.isEmpty()) {
            manager.cancel(SUMMARY_NOTIFICATION_ID);
            return;
        }
        int totalMessages = 0;
        Person self = new Person.Builder().setName("You").setKey("pinggo_self").build();
        NotificationCompat.MessagingStyle style = new NotificationCompat.MessagingStyle(self)
                .setGroupConversation(true);
        for (NotificationStateStore.ChatState chat : chats) {
            totalMessages += chat.messages.size();
            NotificationStateStore.MessageState latest = chat.latest();
            String name = chat.senderName.isEmpty() ? chat.senderId : chat.senderName;
            Person sender = new Person.Builder().setName(name).setKey(chat.senderId).build();
            style.addMessage(latest.preview, latest.receivedAt, sender);
        }
        String title = totalMessages + " messages from " + chats.size() + " chats";
        style.setConversationTitle(title);
        Intent openApp = new Intent(context, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, SUMMARY_NOTIFICATION_ID, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Bitmap logo = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.pinggo_logo);
        NotificationCompat.Builder summary = new NotificationCompat.Builder(
                context, MESSAGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pinggo_notification)
                .setLargeIcon(logo)
                .setContentTitle("PingGo")
                .setContentText(title)
                .setStyle(style)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setNumber(totalMessages)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            manager.notify(SUMMARY_NOTIFICATION_ID, summary.build());
        }
    }

    private static NotificationStateStore.ChatState findChat(
            List<NotificationStateStore.ChatState> chats, String chatId) {
        for (NotificationStateStore.ChatState chat : chats) {
            if (chat.chatId.equals(chatId)) return chat;
        }
        return null;
    }

    public static int notificationId(String chatId) {
        return 0x50000000 | (chatId == null ? 0 : chatId.hashCode() & 0x0fffffff);
    }

    private static NotificationCompat.Action replyAction(
            Context context, int requestCode, String chatId, String messageId, String senderId) {
        RemoteInput input = new RemoteInput.Builder(REMOTE_INPUT_REPLY).setLabel("Reply").build();
        return new NotificationCompat.Action.Builder(0, "Reply",
                actionIntent(context, ACTION_REPLY, requestCode, chatId, messageId, senderId))
                .addRemoteInput(input).setAllowGeneratedReplies(true).build();
    }

    private static NotificationCompat.Action action(
            Context context, String action, String label, int requestCode,
            String chatId, String messageId, String senderId) {
        return new NotificationCompat.Action.Builder(0, label,
                actionIntent(context, action, requestCode, chatId, messageId, senderId)).build();
    }

    private static NotificationCompat.Action markReadAction(
            Context context, int requestCode, NotificationStateStore.ChatState chat) {
        Intent intent = new Intent(context, NotificationActionReceiver.class)
                .setAction(ACTION_MARK_READ)
                .putExtra(EXTRA_CHAT_ID, chat.chatId)
                .putExtra(EXTRA_SENDER_ID, chat.senderId);
        ArrayList<String> messageIds = new ArrayList<>();
        for (NotificationStateStore.MessageState message : chat.messages) {
            if (!message.messageId.isEmpty()) messageIds.add(message.messageId);
        }
        intent.putStringArrayListExtra(EXTRA_MESSAGE_IDS, messageIds);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode ^ ACTION_MARK_READ.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Action.Builder(0, "Mark as read", pendingIntent).build();
    }

    private static PendingIntent actionIntent(
            Context context, String action, int requestCode, String chatId,
            String messageId, String senderId) {
        Intent intent = new Intent(context, NotificationActionReceiver.class)
                .setAction(action)
                .putExtra(EXTRA_CHAT_ID, chatId)
                .putExtra(EXTRA_MESSAGE_ID, messageId)
                .putExtra(EXTRA_SENDER_ID, senderId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        flags |= ACTION_REPLY.equals(action)
                ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, requestCode ^ action.hashCode(), intent, flags);
    }

    private static Bitmap downloadBitmap(String source) {
        if (source.isEmpty()) return null;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(source).openConnection();
            connection.setConnectTimeout(3500);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(true);
            try (InputStream input = connection.getInputStream()) {
                return BitmapFactory.decodeStream(input);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String value(Map<String, String> data, String key) {
        String value = data.get(key);
        return value == null ? "" : value.trim();
    }
}
