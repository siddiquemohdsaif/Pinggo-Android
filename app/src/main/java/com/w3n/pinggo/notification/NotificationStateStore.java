package com.w3n.pinggo.notification;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class NotificationStateStore {
    private static final String PREFS = "PingGoNotificationState";
    private static final String STATE = "unreadConversations";

    private NotificationStateStore() { }

    static synchronized List<ChatState> add(Context context, Map<String, String> data) {
        JSONObject root = read(context);
        String chatId = value(data, "chatId");
        if (chatId.isEmpty()) return parse(root);
        try {
            JSONObject chat = root.optJSONObject(chatId);
            if (chat == null) chat = new JSONObject();
            chat.put("chatId", chatId);
            chat.put("senderId", value(data, "senderId"));
            chat.put("senderName", value(data, "senderName"));
            chat.put("profilePhotoUrl", value(data, "profilePhotoUrl"));
            chat.put("groupName", value(data, "groupName"));
            chat.put("groupIcon", value(data, "groupIcon"));
            chat.put("hidden", false);
            JSONArray messages = chat.optJSONArray("messages");
            if (messages == null) messages = new JSONArray();
            String messageId = value(data, "messageId");
            boolean duplicate = false;
            for (int index = 0; index < messages.length(); index++) {
                if (messageId.equals(messages.optJSONObject(index).optString("messageId"))) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                JSONObject message = new JSONObject();
                message.put("messageId", messageId);
                message.put("messageType", value(data, "messageType"));
                message.put("preview", value(data, "preview"));
                message.put("attachmentUrl", value(data, "attachmentUrl"));
                message.put("senderId", value(data, "senderId"));
                message.put("senderName", value(data, "senderName"));
                message.put("receivedAt", System.currentTimeMillis());
                messages.put(message);
            }
            chat.put("messages", messages);
            root.put(chatId, chat);
            write(context, root);
        } catch (JSONException ignored) { }
        return parse(root);
    }

    static synchronized List<ChatState> remove(Context context, String chatId) {
        JSONObject root = read(context);
        root.remove(chatId == null ? "" : chatId);
        write(context, root);
        return parse(root);
    }

    static synchronized List<ChatState> hide(Context context, String chatId) {
        JSONObject root = read(context);
        JSONObject chat = root.optJSONObject(chatId == null ? "" : chatId);
        if (chat != null) {
            try {
                chat.put("hidden", true);
                root.put(chatId, chat);
                write(context, root);
            } catch (JSONException ignored) { }
        }
        return parse(root);
    }

    static synchronized List<ChatState> all(Context context) {
        return parse(read(context));
    }

    private static JSONObject read(Context context) {
        String json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(STATE, "{}");
        try { return new JSONObject(json); }
        catch (JSONException ignored) { return new JSONObject(); }
    }

    private static void write(Context context, JSONObject root) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(STATE, root.toString()).apply();
    }

    private static List<ChatState> parse(JSONObject root) {
        List<ChatState> chats = new ArrayList<>();
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            JSONObject value = root.optJSONObject(keys.next());
            if (value == null) continue;
            if (value.optBoolean("hidden", false)) continue;
            ChatState chat = new ChatState();
            chat.chatId = value.optString("chatId");
            chat.senderId = value.optString("senderId");
            chat.senderName = value.optString("senderName", chat.senderId);
            chat.profilePhotoUrl = value.optString("profilePhotoUrl");
            chat.groupName = value.optString("groupName");
            chat.groupIcon = value.optString("groupIcon");
            JSONArray messages = value.optJSONArray("messages");
            for (int index = 0; messages != null && index < messages.length(); index++) {
                JSONObject valueMessage = messages.optJSONObject(index);
                if (valueMessage == null) continue;
                MessageState message = new MessageState();
                message.messageId = valueMessage.optString("messageId");
                message.messageType = valueMessage.optString("messageType", "text");
                message.preview = valueMessage.optString("preview", "New message");
                message.attachmentUrl = valueMessage.optString("attachmentUrl");
                message.senderId = valueMessage.optString("senderId", chat.senderId);
                message.senderName = valueMessage.optString("senderName", message.senderId);
                message.receivedAt = valueMessage.optLong("receivedAt", System.currentTimeMillis());
                chat.messages.add(message);
            }
            if (!chat.chatId.isEmpty() && !chat.messages.isEmpty()) chats.add(chat);
        }
        Collections.sort(chats, (left, right) ->
                Long.compare(right.latest().receivedAt, left.latest().receivedAt));
        return chats;
    }

    private static String value(Map<String, String> data, String key) {
        String value = data.get(key);
        return value == null ? "" : value.trim();
    }

    static final class ChatState {
        String chatId, senderId, senderName, profilePhotoUrl, groupName, groupIcon;
        final List<MessageState> messages = new ArrayList<>();
        MessageState latest() { return messages.get(messages.size() - 1); }
    }

    static final class MessageState {
        String messageId, messageType, preview, attachmentUrl, senderId, senderName;
        long receivedAt;
    }
}
