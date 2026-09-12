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
import android.util.Log;
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
import com.w3n.pinggo.activity.LinkedDevicesActivity;
import com.w3n.pinggo.activity.VoiceCallActivity;
import com.w3n.pinggo.activity.VideoCallActivity;
import com.w3n.pinggo.activity.LiveKitCallActivity;
import com.w3n.pinggo.call.CallEngineToggle;
import com.w3n.pinggo.contacts.DeviceContactResolver;
import com.w3n.pinggo.call.WebRTCCallClient;
import com.w3n.pinggo.Database.CloudFunction.Utils.JsonParserUtil;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public final class PingGoNotificationManager {
    public static final String MESSAGE_CHANNEL_ID = "pinggo_messages";
    public static final String LINKED_DEVICE_CHANNEL_ID = "pinggo_linked_devices";
    public static final String EXTRA_CHAT_ID = "notificationChatId";
    public static final String EXTRA_MESSAGE_ID = "notificationMessageId";
    public static final String EXTRA_MESSAGE_IDS = "notificationMessageIds";
    public static final String EXTRA_SENDER_ID = "notificationSenderId";
    public static final String REMOTE_INPUT_REPLY = "notificationReply";
    public static final String ACTION_REPLY = "com.w3n.pinggo.notification.REPLY";
    public static final String ACTION_MARK_READ = "com.w3n.pinggo.notification.MARK_READ";
    public static final String ACTION_MUTE = "com.w3n.pinggo.notification.MUTE";
    public static final String ACTION_DISMISS = "com.w3n.pinggo.notification.DISMISS";
    public static final String ACTION_CALL_ANSWER = "com.w3n.pinggo.notification.CALL_ANSWER";
    public static final String ACTION_CALL_DECLINE = "com.w3n.pinggo.notification.CALL_DECLINE";
    public static final String ACTION_CALL_OPEN = "com.w3n.pinggo.notification.CALL_OPEN";
    public static final String EXTRA_CALL_ID = "notificationCallId";
    public static final String EXTRA_CALLER_NAME = "notificationCallerName";
    public static final String EXTRA_CALL_MEDIA_TYPE = "notificationCallMediaType";
    private static final String CALL_CHANNEL_ID = "pinggo_calls";
    private static final String GROUP_KEY = "pinggo_message_batch";
    private static final int SUMMARY_NOTIFICATION_ID = 0x4f000001;
    private static final String CALL_ACTION_PREFS = "PingGoCallNotificationActions";
    private static final String OFFER_NOTIFICATION_PREFIX = "offer-notification:";
    private static final String RESOLVED_NOTIFICATION_PREFIX = "resolved-notification:";
    private static final long OFFER_NOTIFICATION_TTL_MS = 2 * 60 * 60 * 1000L;

    private PingGoNotificationManager() { }

    public static void createChannels(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                MESSAGE_CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("New PingGo message notifications");
        channel.enableVibration(true);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
        NotificationChannel calls = new NotificationChannel(
                CALL_CHANNEL_ID, "Calls", NotificationManager.IMPORTANCE_HIGH);
        calls.setDescription("Incoming and missed PingGo calls");
        calls.enableVibration(true);
        calls.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        if (manager != null) manager.createNotificationChannel(calls);
        NotificationChannel linkedDevices = new NotificationChannel(
                LINKED_DEVICE_CHANNEL_ID, "Linked devices", NotificationManager.IMPORTANCE_HIGH);
        linkedDevices.setDescription("Device link and logout activity for your PingGo account");
        linkedDevices.enableVibration(true);
        if (manager != null) manager.createNotificationChannel(linkedDevices);
    }

    public static void showLinkedDeviceNotification(
            @NonNull Context context, Map<String, String> data) {
        String type = value(data, "type");
        String reason = value(data, "reason");
        String deviceId = value(data, "deviceId");
        String deviceName = value(data, "deviceName");
        if (deviceName.isEmpty()) deviceName = "Companion device";

        String title;
        String detail;
        if ("device_linked".equals(type)) {
            title = "Device linked";
            detail = deviceName + " was linked to your PingGo account.";
        } else if ("self_logout".equals(reason)) {
            title = "Companion device logged out";
            detail = deviceName + " logged out from your PingGo account.";
        } else {
            title = "Companion device detached";
            detail = deviceName + " was removed from your PingGo account.";
        }

        int notificationId = 0x53000000
                | ((deviceId.isEmpty() ? type : deviceId).hashCode() & 0x0fffffff);
        Intent openLinkedDevices = new Intent(context, LinkedDevicesActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, notificationId, openLinkedDevices,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Bitmap logo = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.pinggo_logo);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(
                context, LINKED_DEVICE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pinggo_notification)
                .setLargeIcon(logo)
                .setContentTitle(title)
                .setContentText(detail)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(detail))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(notificationId, notification.build());
        }
    }

    public static void showSessionLogoutNotification(
            @NonNull Context context, @NonNull String message) {
        createChannels(context);
        int notificationId = 0x5300ff01;
        Intent login = new Intent(context, com.w3n.pinggo.activity.LoginActivity.class)
                .putExtra(com.w3n.pinggo.activity.LoginActivity.EXTRA_LOGOUT_MESSAGE, message)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, notificationId, login,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(
                context, LINKED_DEVICE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pinggo_notification)
                .setContentTitle("Device logged out")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(notificationId, notification.build());
        }
    }

    public static void showIncomingCallNotification(Context context, JsonObject event) {
        Map<String, String> data = new java.util.HashMap<>();
        for (Map.Entry<String, com.google.gson.JsonElement> item : event.entrySet()) {
            if (item.getValue() != null && item.getValue().isJsonPrimitive())
                data.put(item.getKey(), item.getValue().getAsString());
            else if ("participantIds".equals(item.getKey()) && item.getValue() != null
                    && item.getValue().isJsonArray())
                data.put(item.getKey(), item.getValue().toString());
        }
        JsonObject sdp = event.has("sdp") && event.get("sdp").isJsonObject()
                ? event.getAsJsonObject("sdp") : null;
        showIncomingCallNotification(context, data,
                sdp == null ? "" : WebRTCCallClient.decodeSdp(sdp));
    }

    public static void showIncomingCallNotification(Context context, Map<String, String> data) {
        String encodedOffer = value(data, "offerDescriptionBase64");
        String plainOffer = value(data, "offerDescription");
        String offer = "";
        if (!encodedOffer.isEmpty() || !plainOffer.isEmpty()) {
            JsonObject sdp = new JsonObject();
            sdp.addProperty("type", value(data, "offerType"));
            sdp.addProperty("description", plainOffer);
            sdp.addProperty("descriptionBase64", encodedOffer);
            offer = WebRTCCallClient.decodeSdp(sdp);
        }
        showIncomingCallNotification(context, data, offer);
    }

    private static void showIncomingCallNotification(
            Context context, Map<String, String> data, String offer) {
        suppressMessageNotifications(context);
        String callId = value(data, "callId");
        String chatId = value(data, "chatId");
        String callerId = value(data, "callerId");
        String callerName = value(data, "callerName");
        boolean video = "video".equals(value(data, "mediaType"));
        boolean liveKit = "livekit".equals(value(data, "engine"));
        boolean conference = "group".equals(value(data, "callMode"))
                || "true".equalsIgnoreCase(value(data, "conference"));
        if (callId.isEmpty() || callerId.isEmpty()) return;
        android.content.SharedPreferences callPreferences = context.getSharedPreferences(
                CALL_ACTION_PREFS, Context.MODE_PRIVATE);
        long resolvedAt = callPreferences.getLong(RESOLVED_NOTIFICATION_PREFIX + callId, 0L);
        if (resolvedAt > 0L
                && System.currentTimeMillis() - resolvedAt < OFFER_NOTIFICATION_TTL_MS) {
            Log.i("PingGoCallTrace", "notification_duplicate_resolved_skipped callId=" + callId);
            return;
        }
        long richerNotificationAt = callPreferences.getLong(
                OFFER_NOTIFICATION_PREFIX + callId, 0L);
        if (offer.isEmpty() && richerNotificationAt > 0L
                && System.currentTimeMillis() - richerNotificationAt < OFFER_NOTIFICATION_TTL_MS) {
            Log.i("PingGoCallTrace", "notification_fcm_downgrade_skipped callId=" + callId
                    + " reason=websocket_notification_has_offer");
            return;
        }
        if (!offer.isEmpty()) {
            callPreferences.edit().putLong(OFFER_NOTIFICATION_PREFIX + callId,
                    System.currentTimeMillis()).apply();
        }
        Log.i("PingGoCallTrace", "notification_build_incoming callId=" + callId
                + " chatId=" + chatId + " media=" + (video ? "video" : "audio")
                + " engine=" + (liveKit ? "livekit" : "legacy")
                + " hasOffer=" + !offer.isEmpty());
        callerName = DeviceContactResolver.nameOrPhone(context, callerId);
        int id = callNotificationId(callId);
        Intent answerIntent;
        PendingIntent content;
        PendingIntent answer;
        if (!offer.isEmpty() || liveKit) {
            answerIntent = callActivityIntent(context, callId, chatId, callerId,
                    callerName, video, offer, true, liveKit, conference,
                    value(data, "participantIds"));
            answer = PendingIntent.getActivity(context, id, answerIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            content = PendingIntent.getActivity(context, id ^ 0x24680,
                    callActivityIntent(context, callId, chatId, callerId,
                            callerName, video, offer, false, liveKit, conference,
                            value(data, "participantIds")),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            answerIntent = callActionIntent(context, ACTION_CALL_ANSWER, callId, chatId,
                    callerId, callerName, video, false);
            answer = PendingIntent.getBroadcast(context, id, answerIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            content = PendingIntent.getBroadcast(context, id ^ 0x24680,
                    callActionIntent(context, ACTION_CALL_OPEN, callId, chatId,
                            callerId, callerName, video, false),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        PendingIntent decline = PendingIntent.getBroadcast(context, id ^ 0x13579,
                callActionIntent(context, ACTION_CALL_DECLINE, callId, chatId,
                        callerId, callerName, video, liveKit),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Bitmap avatar = downloadBitmap(value(data, "profilePhotoUrl"));
        Bitmap icon = avatar != null ? avatar : BitmapFactory.decodeResource(
                context.getResources(), R.drawable.pinggo_logo);
        Person caller = new Person.Builder().setName(callerName).setKey(callerId)
                .setIcon(icon == null ? null : IconCompat.createWithBitmap(icon)).build();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CALL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pinggo_notification).setLargeIcon(icon)
                .setContentTitle(callerName)
                .setContentText((conference ? "Incoming group " : "Incoming ")
                        + (video ? "video call" : "voice call"))
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX).setOngoing(true)
                .setAutoCancel(false).setContentIntent(content).setFullScreenIntent(content, true)
                .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decline, answer));
        notifyAllowed(context, id, builder);
        Log.i("PingGoCallTrace", "notification_posted callId=" + callId
                + " notificationId=" + id + " answerMode="
                + ((!offer.isEmpty() || liveKit) ? "direct_activity" : "wait_for_socket_invite"));
    }

    public static void showMissedCallNotification(Context context, Map<String, String> data) {
        suppressMessageNotifications(context);
        String callId = value(data, "callId");
        context.getSharedPreferences(CALL_ACTION_PREFS, Context.MODE_PRIVATE).edit()
                .remove(OFFER_NOTIFICATION_PREFIX + callId).apply();
        String chatId = value(data, "chatId");
        String callerName = value(data, "callerName");
        boolean video = "video".equals(value(data, "mediaType"));
        callerName = DeviceContactResolver.nameOrPhone(context, value(data, "callerId"));
        Intent open = new Intent(context, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_CHAT_ID, chatId)
                .putExtra(ChatActivity.EXTRA_CHAT_NAME, callerName)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, callNotificationId(callId), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Bitmap icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.pinggo_logo);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CALL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pinggo_notification).setLargeIcon(icon)
                .setContentTitle(callerName)
                .setContentText(video ? "Missed video call" : "Missed voice call")
                .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
                .setContentIntent(content);
        notifyAllowed(context, callNotificationId(callId), builder);
    }

    /** Hide message cards during a call without marking their messages as read. */
    private static void suppressMessageNotifications(Context context) {
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        for (NotificationStateStore.ChatState chat : NotificationStateStore.all(context)) {
            manager.cancel(notificationId(chat.chatId));
        }
        manager.cancel(SUMMARY_NOTIFICATION_ID);
        Log.i("PingGoCallTrace", "message_notifications_suppressed_for_call");
    }

    public static void clearCallNotification(Context context, String callId) {
        Log.i("PingGoCallTrace", "notification_cleared callId=" + callId);
        if (callId != null && !callId.trim().isEmpty()) {
            context.getSharedPreferences(CALL_ACTION_PREFS, Context.MODE_PRIVATE).edit()
                    .putLong(RESOLVED_NOTIFICATION_PREFIX + callId,
                            System.currentTimeMillis())
                    .remove(OFFER_NOTIFICATION_PREFIX + callId)
                    .apply();
        }
        NotificationManagerCompat.from(context).cancel(callNotificationId(callId));
    }

    /** Keeps an opened incoming call in the shade without showing it again as a heads-up call. */
    public static void markCallNotificationOpened(Context context, Intent activityIntent) {
        String callId = stringExtra(activityIntent, VoiceCallActivity.EXTRA_CALL_ID);
        if (callId.isEmpty()) return;
        String callerName = stringExtra(activityIntent, VoiceCallActivity.EXTRA_PHONE_NUMBER);
        boolean video = "video".equals(stringExtra(activityIntent,
                LiveKitCallActivity.EXTRA_MEDIA_TYPE))
                || activityIntent.getComponent() != null
                && VideoCallActivity.class.getName().equals(
                activityIntent.getComponent().getClassName());
        Intent reopen = new Intent(activityIntent)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(context,
                callNotificationId(callId) ^ 0x24680, reopen,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Bitmap icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.pinggo_logo);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CALL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pinggo_notification)
                .setLargeIcon(icon)
                .setContentTitle(callerName.isEmpty() ? "Incoming call" : callerName)
                .setContentText(video ? "Incoming video call" : "Incoming voice call")
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(content);
        notifyAllowed(context, callNotificationId(callId), builder);
        Log.i("PingGoCallTrace", "notification_opened_silenced callId=" + callId);
    }

    private static String stringExtra(Intent intent, String key) {
        String value = intent == null ? null : intent.getStringExtra(key);
        return value == null ? "" : value.trim();
    }

    public static void rememberCallAction(Context context, String callId, String action) {
        context.getSharedPreferences(CALL_ACTION_PREFS, Context.MODE_PRIVATE).edit()
                .putString(callId, action).apply();
    }

    public static String consumeCallAction(Context context, String callId) {
        android.content.SharedPreferences preferences = context.getSharedPreferences(
                CALL_ACTION_PREFS, Context.MODE_PRIVATE);
        String action = preferences.getString(callId, "");
        if (!action.isEmpty()) preferences.edit().remove(callId).apply();
        return action;
    }

    private static int callNotificationId(String callId) {
        return 0x30000000 | (callId == null ? 0 : callId.hashCode() & 0x0fffffff);
    }

    private static Intent callActivityIntent(Context context, String callId, String chatId,
            String callerId, String callerName, boolean video, String offer, boolean autoAccept,
            boolean liveKit, boolean conference, String participantIdsJson) {
        Intent intent = new Intent(context, liveKit ? LiveKitCallActivity.class
                : (video ? VideoCallActivity.class : VoiceCallActivity.class))
                .putExtra(VoiceCallActivity.EXTRA_CALL_ID, callId)
                .putExtra(VoiceCallActivity.EXTRA_CALL_CHAT_ID, chatId)
                .putExtra(VoiceCallActivity.EXTRA_CALLER_ID, callerId)
                .putExtra(VoiceCallActivity.EXTRA_PHONE_NUMBER, callerName)
                .putExtra(VoiceCallActivity.EXTRA_PROFILE_PATH,
                        ChatProfilePhotoStore.getLocalPath(context, callerId))
                .putExtra(VoiceCallActivity.EXTRA_SDP_OFFER, offer)
                .putExtra(VoiceCallActivity.EXTRA_AUTO_ACCEPT, autoAccept)
                .putExtra(VoiceCallActivity.EXTRA_CALL_ENGINE,
                        liveKit ? CallEngineToggle.LIVEKIT : CallEngineToggle.LEGACY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (liveKit) {
            intent.putExtra(LiveKitCallActivity.EXTRA_MEDIA_TYPE, video ? "video" : "audio");
            intent.putExtra(LiveKitCallActivity.EXTRA_INCOMING, true);
            intent.putExtra(LiveKitCallActivity.EXTRA_CONFERENCE_CALL, conference);
            intent.putStringArrayListExtra(LiveKitCallActivity.EXTRA_PARTICIPANT_IDS,
                    parseParticipantIds(participantIdsJson));
        }
        return intent;
    }

    private static ArrayList<String> parseParticipantIds(String json) {
        ArrayList<String> result = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return result;
        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(json);
            if (!parsed.isJsonArray()) return result;
            for (com.google.gson.JsonElement value : parsed.getAsJsonArray())
                if (value != null && value.isJsonPrimitive()) result.add(value.getAsString());
        } catch (RuntimeException ignored) { }
        return result;
    }

    private static Intent callActionIntent(Context context, String action, String callId,
            String chatId, String callerId, String callerName, boolean video, boolean liveKit) {
        return new Intent(context, NotificationActionReceiver.class).setAction(action)
                .putExtra(EXTRA_CALL_ID, callId).putExtra(EXTRA_CHAT_ID, chatId)
                .putExtra(EXTRA_SENDER_ID, callerId)
                .putExtra(EXTRA_CALLER_NAME, callerName)
                .putExtra(EXTRA_CALL_MEDIA_TYPE, video ? "video" : "audio")
                .putExtra(VoiceCallActivity.EXTRA_CALL_ENGINE,
                        liveKit ? CallEngineToggle.LIVEKIT : CallEngineToggle.LEGACY);
    }

    private static void notifyAllowed(Context context, int id, NotificationCompat.Builder builder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        }
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
        String senderName = DeviceContactResolver.nameOrPhone(context, senderId);
        String preview = latest.preview.isEmpty() ? "New message" : latest.preview;
        boolean groupChat = chatId.startsWith("grp_");
        String groupName = chat.groupName;
        if (groupName == null || groupName.isEmpty()) groupName = value(data, "chatName");
        if (groupName.isEmpty()) groupName = "Group";
        String conversationName = groupChat ? groupName : senderName;
        String notificationPreview = groupChat ? senderName + ": " + preview : preview;

        Bitmap avatar = downloadBitmap(groupChat ? chat.groupIcon : chat.profilePhotoUrl);
        Bitmap brandingIcon = avatar != null ? avatar
                : BitmapFactory.decodeResource(context.getResources(), R.drawable.pinggo_logo);
        Bitmap picture = chat.messages.size() == 1 && "image".equals(latest.messageType)
                ? downloadBitmap(latest.attachmentUrl) : null;
        int notificationId = notificationId(chatId);

        Intent openChat = new Intent(context, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_CHAT_ID, chatId)
                .putExtra(ChatActivity.EXTRA_CHAT_NAME, conversationName)
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
                .setContentTitle(conversationName)
                .setContentText(notificationPreview)
                .setSubText("PingGo")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setWhen(latest.receivedAt)
                .setShowWhen(true)
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
                    .bigPicture(picture).bigLargeIcon(brandingIcon).setSummaryText(notificationPreview));
        } else {
            Person self = new Person.Builder().setName("You").setKey("pinggo_self").build();
            NotificationCompat.MessagingStyle style =
                    new NotificationCompat.MessagingStyle(self)
                            .setConversationTitle(conversationName)
                            .setGroupConversation(groupChat);
            for (NotificationStateStore.MessageState storedMessage : chat.messages) {
                String storedSenderId = storedMessage.senderId == null
                        ? senderId : storedMessage.senderId;
                String storedSenderName = DeviceContactResolver.nameOrPhone(
                        context, storedSenderId);
                Person storedSender = new Person.Builder().setName(storedSenderName)
                        .setKey(storedSenderId).build();
                style.addMessage(storedMessage.preview, storedMessage.receivedAt, storedSender);
            }
            notification.setStyle(style);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (chats.size() > 1) {
            // Silent children provide per-chat expansion/actions, while only the summary
            // alerts and appears as the collapsed WhatsApp-style batch card.
            for (NotificationStateStore.ChatState storedChat : chats) {
                NotificationCompat.Builder child = storedChat.chatId.equals(chatId)
                        ? notification : storedChatNotification(context, storedChat);
                child.setGroup(GROUP_KEY)
                        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                        .setSilent(true)
                        .setOnlyAlertOnce(true);
                manager.notify(notificationId(storedChat.chatId), child.build());
            }
            showGroupSummary(context, chats);
        } else {
            manager.cancel(SUMMARY_NOTIFICATION_ID);
            manager.notify(notificationId, notification.build());
        }
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
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        int visibleLines = 0;
        for (NotificationStateStore.ChatState chat : chats) {
            totalMessages += chat.messages.size();
            NotificationStateStore.MessageState latest = chat.latest();
            String name = chat.chatId != null && chat.chatId.startsWith("grp_")
                    && chat.groupName != null && !chat.groupName.isEmpty()
                    ? chat.groupName
                    : DeviceContactResolver.nameOrPhone(context, chat.senderId);
            // InboxStyle is intentional here: this notification represents several
            // independent chats, not multiple people in one MessagingStyle conversation.
            if (visibleLines < 7) {
                String preview = latest.preview == null || latest.preview.trim().isEmpty()
                        ? "New message" : latest.preview.trim();
                style.addLine(name + ": " + preview);
                visibleLines++;
            }
        }
        String title = totalMessages + " messages from " + chats.size() + " chats";
        style.setBigContentTitle(title);
        if (chats.size() > visibleLines) {
            style.setSummaryText("+" + (chats.size() - visibleLines) + " more chats");
        } else {
            style.setSummaryText("PingGo");
        }
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
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setNumber(totalMessages)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            manager.notify(SUMMARY_NOTIFICATION_ID, summary.build());
        }
    }

    private static NotificationCompat.Builder storedChatNotification(
            Context context, NotificationStateStore.ChatState chat) {
        NotificationStateStore.MessageState latest = chat.latest();
        boolean groupChat = chat.chatId.startsWith("grp_");
        String senderId = latest.senderId == null || latest.senderId.isEmpty()
                ? chat.senderId : latest.senderId;
        String senderName = DeviceContactResolver.nameOrPhone(context, senderId);
        String conversationName = groupChat && chat.groupName != null
                && !chat.groupName.isEmpty() ? chat.groupName : senderName;
        int id = notificationId(chat.chatId);
        Intent openChat = new Intent(context, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_CHAT_ID, chat.chatId)
                .putExtra(ChatActivity.EXTRA_CHAT_NAME, conversationName)
                .putExtra(ChatActivity.EXTRA_OPEN_REQUEST_NANOS,
                        SystemClock.elapsedRealtimeNanos())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, id, openChat,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Bitmap avatar = downloadBitmap(groupChat ? chat.groupIcon : chat.profilePhotoUrl);
        Bitmap icon = avatar != null ? avatar : BitmapFactory.decodeResource(
                context.getResources(), R.drawable.pinggo_logo);
        Person self = new Person.Builder().setName("You").setKey("pinggo_self").build();
        NotificationCompat.MessagingStyle messageStyle =
                new NotificationCompat.MessagingStyle(self)
                        .setConversationTitle(conversationName)
                        .setGroupConversation(groupChat);
        for (NotificationStateStore.MessageState message : chat.messages) {
            String messageSenderId = message.senderId == null || message.senderId.isEmpty()
                    ? senderId : message.senderId;
            Person person = new Person.Builder()
                    .setName(DeviceContactResolver.nameOrPhone(context, messageSenderId))
                    .setKey(messageSenderId).build();
            messageStyle.addMessage(message.preview, message.receivedAt, person);
        }
        return new NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pinggo_notification)
                .setLargeIcon(icon)
                .setContentTitle(conversationName)
                .setContentText(latest.preview)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setWhen(latest.receivedAt)
                .setShowWhen(true)
                .setNumber(chat.messages.size())
                .setContentIntent(content)
                .setStyle(messageStyle)
                .setDeleteIntent(actionIntent(context, ACTION_DISMISS, id,
                        chat.chatId, latest.messageId, senderId))
                .addAction(replyAction(context, id, chat.chatId,
                        latest.messageId, senderId))
                .addAction(markReadAction(context, id, chat))
                .addAction(action(context, ACTION_MUTE, "Mute", id,
                        chat.chatId, latest.messageId, senderId));
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
