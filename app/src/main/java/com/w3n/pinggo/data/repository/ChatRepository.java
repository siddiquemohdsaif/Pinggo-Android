package com.w3n.pinggo.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.data.local.MessageDao;
import com.w3n.pinggo.data.local.MessageEntity;
import com.w3n.pinggo.data.local.MessageStatus;
import com.w3n.pinggo.data.local.ChatDao;
import com.w3n.pinggo.data.local.ChatEntity;
import com.w3n.pinggo.data.local.PresenceDao;
import com.w3n.pinggo.data.local.PresenceEntity;
import com.w3n.pinggo.data.local.PingGoDatabase;
import com.w3n.pinggo.data.remote.AuthenticatedApiFactory;
import com.w3n.pinggo.data.remote.ChatApiService;
import com.w3n.pinggo.data.remote.ChatWebSocketClient;
import com.w3n.pinggo.data.remote.JsonParserUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatRepository implements ChatWebSocketClient.Listener {
    public interface EventListener {
        void onTyping(String chatId, String userId, boolean typing);

        void onSocketError(String error);
    }

    private static volatile ChatRepository instance;

    private final Context appContext;
    private final MessageDao messageDao;
    private final ChatDao chatDao;
    private final PresenceDao presenceDao;
    private final ChatApiService chatApi;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ChatWebSocketClient socketClient;
    private EventListener eventListener;
    private String currentUserId;

    private ChatRepository(Context context) {
        appContext = context.getApplicationContext();
        PingGoDatabase database = PingGoDatabase.getInstance(appContext);
        messageDao = database.messageDao();
        chatDao = database.chatDao();
        presenceDao = database.presenceDao();
        chatApi = AuthenticatedApiFactory.createChatApi(appContext);
        socketClient = new ChatWebSocketClient(this);
    }

    public static ChatRepository getInstance(Context context) {
        if (instance != null) {
            return instance;
        }
        synchronized (ChatRepository.class) {
            if (instance == null) {
                instance = new ChatRepository(context);
            }
        }
        return instance;
    }

    public static void resetInstance() {
        synchronized (ChatRepository.class) {
            if (instance != null) {
                instance.disconnect();
            }
            instance = null;
        }
    }

    public void setEventListener(EventListener eventListener) {
        this.eventListener = eventListener;
    }

    public LiveData<List<MessageEntity>> observeMessages(String chatId) {
        return messageDao.observeMessages(chatId);
    }

    public LiveData<List<ChatEntity>> observeChats() {
        return chatDao.observeChats();
    }

    public LiveData<PresenceEntity> observePresence(String userId) {
        return presenceDao.observePresence(normalizeAccountId(userId));
    }

    public void connect() {
        currentUserId = normalizeAccountId(LoginStateManager.getInstance().getUID(appContext));
        String encryptedCredential = LoginStateManager.getInstance().getENC(appContext);
        if (currentUserId.isEmpty() || encryptedCredential == null || encryptedCredential.trim().isEmpty()) {
            return;
        }
        socketClient.connect(currentUserId, encryptedCredential);
        Log.d("CHAT_REPOSITORY", "connect: ");
    }

    public void disconnect() {
        socketClient.disconnect();
    }

    public void sendMessage(String chatId, String receiverId, String text, String repliedMessageId) {
        String senderId = currentUserId == null || currentUserId.isEmpty()
                ? normalizeAccountId(LoginStateManager.getInstance().getUID(appContext))
                : currentUserId;
        String normalizedReceiverId = normalizeAccountId(receiverId);
        String clientMessageId = "local_" + UUID.randomUUID();
        long now = System.currentTimeMillis();

        MessageEntity localMessage = new MessageEntity(
                clientMessageId,
                clientMessageId,
                chatId,
                senderId,
                normalizedReceiverId,
                text,
                repliedMessageId,
                now,
                null,
                null,
                MessageStatus.SENDING
        );

        ioExecutor.execute(() -> messageDao.upsert(localMessage));

        JsonObject event = new JsonObject();
        event.addProperty("type", "send_message");
        event.addProperty("clientMessageId", clientMessageId);
        event.addProperty("chatId", chatId);
        event.addProperty("senderId", senderId);
        event.addProperty("receiverId", normalizedReceiverId);
        event.addProperty("text", text);
        if (repliedMessageId != null && !repliedMessageId.trim().isEmpty()) {
            event.addProperty("repliedMessageId", repliedMessageId);
        }

        boolean sentToSocket = socketClient.send(event);
        if (!sentToSocket) {
            ioExecutor.execute(() -> messageDao.updateStatusByClientMessageId(clientMessageId, MessageStatus.FAILED));
        }
    }

    public void markSeen(String chatId, List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }

        JsonObject event = new JsonObject();
        JsonArray ids = new JsonArray();
        for (String messageId : messageIds) {
            ids.add(messageId);
        }
        event.addProperty("type", "message_seen");
        event.addProperty("chatId", chatId);
        event.add("messageIds", ids);
        socketClient.send(event);
    }

    public void markDelivered(String chatId, List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }

        JsonObject event = new JsonObject();
        JsonArray ids = new JsonArray();
        for (String messageId : messageIds) {
            ids.add(messageId);
        }
        event.addProperty("type", "message_delivered");
        event.addProperty("chatId", chatId);
        event.add("messageIds", ids);
        socketClient.send(event);
    }

    public void hideLocalMessage(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return;
        }
        ioExecutor.execute(() -> messageDao.deleteByMessageId(messageId));
    }

    public void updateLocalMessageText(String messageId, String text) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return;
        }
        ioExecutor.execute(() -> messageDao.updateText(messageId, text));
    }

    public void markLocalMessageDeleted(String messageId) {
        updateLocalMessageText(messageId, "This Message was deleted");
    }

    public void editMessage(String chatId, String messageId, String text) {
        String senderId = currentUserId == null || currentUserId.isEmpty()
                ? normalizeAccountId(LoginStateManager.getInstance().getUID(appContext))
                : currentUserId;
        if (chatId == null || chatId.trim().isEmpty() || messageId == null || messageId.trim().isEmpty()) {
            notifySocketError("Message id missing.");
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            notifySocketError("Message text missing.");
            return;
        }

        updateLocalMessageText(messageId, text);

        JsonObject event = new JsonObject();
        event.addProperty("type", "edit_message");
        event.addProperty("chatId", chatId);
        event.addProperty("messageId", messageId);
        event.addProperty("senderId", senderId);
        event.addProperty("text", text);

        if (!socketClient.send(event)) {
            notifySocketError("Unable to edit message while socket is disconnected.");
        }
    }

    public void deleteOwnMessage(String chatId, String messageId) {
        String senderId = currentUserId == null || currentUserId.isEmpty()
                ? normalizeAccountId(LoginStateManager.getInstance().getUID(appContext))
                : currentUserId;
        if (chatId == null || chatId.trim().isEmpty() || messageId == null || messageId.trim().isEmpty()) {
            notifySocketError("Message id missing.");
            return;
        }

        markLocalMessageDeleted(messageId);

        JsonObject event = new JsonObject();
        event.addProperty("type", "delete_message");
        event.addProperty("chatId", chatId);
        event.addProperty("messageId", messageId);
        event.addProperty("senderId", senderId);

        if (!socketClient.send(event)) {
            notifySocketError("Unable to delete message while socket is disconnected.");
        }
    }

    public void deleteOpponentMessage(String chatId, String messageId) {
        if (chatId == null || chatId.trim().isEmpty() || messageId == null || messageId.trim().isEmpty()) {
            notifySocketError("Message id missing.");
            return;
        }

        hideLocalMessage(messageId);

        JsonObject event = new JsonObject();
        event.addProperty("type", "delete_opponent_message");
        event.addProperty("chatId", chatId);
        event.addProperty("messageId", messageId);

        if (!socketClient.send(event)) {
            notifySocketError("Unable to delete message while socket is disconnected.");
        }
    }

    public void sendTyping(String chatId, String receiverId, boolean typing) {
        JsonObject event = new JsonObject();
        event.addProperty("type", typing ? "typing_start" : "typing_stop");
        event.addProperty("chatId", chatId);
        event.addProperty("receiverId", normalizeAccountId(receiverId));
        socketClient.send(event);
    }

    public void syncAfterReconnect(String phoneNumber) {
        ioExecutor.execute(() -> {
            Long lastSync = messageDao.getLastSyncTime();
            JsonObject body = new JsonObject();
            body.addProperty("phoneNumber", toServerPhoneNumber(phoneNumber));
            body.addProperty("lastSyncTime", lastSync == null ? 0L : lastSync);
            chatApi.syncMessages(body).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    if (!response.isSuccessful() || response.body() == null) {
                        return;
                    }
                    JsonArray messages = response.body().getAsJsonArray("messages");
                    if (messages == null) {
                        return;
                    }
                    List<MessageEntity> entities = new ArrayList<>();
                    for (JsonElement element : messages) {
                        if (element != null && element.isJsonObject()) {
                            MessageEntity message = toMessageEntity(element.getAsJsonObject());
                            if (message != null) {
                                entities.add(message);
                            }
                        }
                    }
                    ioExecutor.execute(() -> messageDao.upsertAll(entities));
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                    notifySocketError(t);
                }
            });
        });
    }

    public void refreshChatList(String phoneNumber) {
        JsonObject body = new JsonObject();
        body.addProperty("phoneNumber", toServerPhoneNumber(phoneNumber));
        chatApi.getChatList(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }
                JsonArray userProfiles = response.body().getAsJsonArray("userProfiles");
                if (userProfiles == null) {
                    return;
                }
                List<ChatEntity> chats = new ArrayList<>();
                long now = System.currentTimeMillis();
                for (JsonElement element : userProfiles) {
                    if (element != null && element.isJsonObject()) {
                        ChatEntity chat = toChatEntity(element.getAsJsonObject(), now);
                        if (chat != null) {
                            chats.add(chat);
                        }
                    }
                }
                ioExecutor.execute(() -> chatDao.upsertAll(chats));
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                notifySocketError(t);
            }
        });
    }

    public void hydrateChat(String chatId, String phoneNumber) {
        if (chatId == null || chatId.trim().isEmpty()) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("chatId", chatId);
        body.addProperty("phoneNumber", toServerPhoneNumber(phoneNumber));
        chatApi.getChat(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }
                JsonElement chatElement = response.body().get("chat");
                if (chatElement == null || !chatElement.isJsonObject()) {
                    return;
                }
                List<MessageEntity> messages = parseChatDocument(chatElement.getAsJsonObject(), chatId);
                ioExecutor.execute(() -> messageDao.upsertAll(messages));
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                notifySocketError(t);
            }
        });
    }

    public void syncPresence(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        JsonObject body = new JsonObject();
        JsonArray ids = new JsonArray();
        for (String userId : userIds) {
            ids.add(normalizeAccountId(userId));
        }
        body.add("userIds", ids);
        chatApi.syncPresence(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }
                JsonArray presence = response.body().getAsJsonArray("presence");
                if (presence == null) {
                    return;
                }
                List<PresenceEntity> entities = new ArrayList<>();
                for (JsonElement element : presence) {
                    if (element != null && element.isJsonObject()) {
                        entities.add(toPresenceEntity(element.getAsJsonObject()));
                    }
                }
                ioExecutor.execute(() -> presenceDao.upsertAll(entities));
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                notifySocketError(t);
            }
        });
    }

    public void uploadFcmToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("fcmToken", token);
        chatApi.updateFcmToken(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                notifySocketError(t);
            }
        });
    }

    public void syncFromFcmTap(String phoneNumber) {
        connect();
        syncAfterReconnect(phoneNumber);
    }

    @Override
    public void onConnected() {
        String phoneNumber = LoginStateManager.getInstance().getUID(appContext);
        syncAfterReconnect(phoneNumber);
    }

    @Override
    public void onEvent(JsonObject event) {
        String type = JsonParserUtil.getString(event, "type");
        if ("message_ack".equals(type)) {
            handleMessageAck(event);
        } else if ("message_failed".equals(type)) {
            handleMessageFailed(event);
        } else if ("new_message".equals(type)) {
            Log.d("CHAT_REPOSITORY", "on_new_message: " + event);
            handleNewMessage(event);
        } else if ("message_seen".equals(type)) {
            handleMessageSeen(event);
        } else if ("message_seen_ack".equals(type)) {
            handleMessageSeen(event);
        } else if ("message_delivered".equals(type)) {
            handleMessageDelivered(event);
        } else if ("message_delivered_ack".equals(type)) {
            handleMessageDelivered(event);
        } else if ("edit_message_ack".equals(type)) {
            handleMessageEdited(event);
        } else if ("edit_message_failed".equals(type)) {
            notifySocketError(JsonParserUtil.getString(event, "message"));
        } else if ("delete_message_ack".equals(type)) {
            handleMessageDeleted(event);
        } else if ("delete_message_failed".equals(type)) {
            notifySocketError(JsonParserUtil.getString(event, "message"));
        } else if ("delete_opponent_message_ack".equals(type)) {
            handleOpponentMessageDeleted(event);
        } else if ("delete_opponent_message_failed".equals(type)) {
            notifySocketError(JsonParserUtil.getString(event, "message"));
        } else if ("message_edited".equals(type)) {
            handleMessageEdited(event);
        } else if ("message_deleted".equals(type)) {
            handleMessageDeleted(event);
        } else if ("typing_start".equals(type)) {
            notifyTyping(event, true);
        } else if ("typing_stop".equals(type)) {
            notifyTyping(event, false);
        } else if ("online_status".equals(type)) {
            handleOnlineStatus(event);
        }
    }

    @Override
    public void onClosed() {
    }

    @Override
    public void onFailure(String error) {
        notifySocketError(error);
    }

    private void handleMessageAck(JsonObject event) {
        String clientMessageId = JsonParserUtil.getString(event, "clientMessageId");
        String messageId = JsonParserUtil.getString(event, "messageId");
        long sentTime = JsonParserUtil.getLong(event, "sentTime");
        ioExecutor.execute(() -> messageDao.applyAck(clientMessageId, messageId, MessageStatus.SENT, sentTime));
    }

    private void handleMessageFailed(JsonObject event) {
        String clientMessageId = JsonParserUtil.getString(event, "clientMessageId");
        ioExecutor.execute(() -> messageDao.updateStatusByClientMessageId(clientMessageId, MessageStatus.FAILED));
    }

    private void handleNewMessage(JsonObject event) {
        JsonElement messageElement = event.get("message");
        if (messageElement == null || !messageElement.isJsonObject()) {
            return;
        }
        MessageEntity message = toMessageEntity(messageElement.getAsJsonObject());
        if (message == null) {
            return;
        }
        ioExecutor.execute(() -> {
            messageDao.upsert(message);
            messageDao.markDelivered(
                    Collections.singletonList(message.messageId),
                    MessageStatus.DELIVERED,
                    System.currentTimeMillis()
            );
        });
        markDelivered(message.chatId, Collections.singletonList(message.messageId));
    }

    private void handleMessageDelivered(JsonObject event) {
        JsonArray ids = event.getAsJsonArray("messageIds");
        if (ids == null) {
            return;
        }
        List<String> messageIds = new ArrayList<>();
        for (JsonElement id : ids) {
            messageIds.add(id.getAsString());
        }
        long deliveredTime = JsonParserUtil.getLong(event, "deliveredTime");
        ioExecutor.execute(() -> messageDao.markDelivered(messageIds, MessageStatus.DELIVERED, deliveredTime));
    }

    private void handleMessageSeen(JsonObject event) {
        JsonArray ids = event.getAsJsonArray("messageIds");
        if (ids == null) {
            return;
        }
        List<String> messageIds = new ArrayList<>();
        for (JsonElement id : ids) {
            messageIds.add(id.getAsString());
        }
        long readTime = JsonParserUtil.getLong(event, "readTime");
        ioExecutor.execute(() -> messageDao.markSeen(messageIds, MessageStatus.SEEN, readTime));
    }

    private void handleMessageEdited(JsonObject event) {
        String messageId = JsonParserUtil.getString(event, "messageId");
        String text = JsonParserUtil.getString(event, "text");
        if (messageId.isEmpty() || text.isEmpty()) {
            return;
        }
        updateLocalMessageText(messageId, text);
    }

    private void handleMessageDeleted(JsonObject event) {
        String messageId = JsonParserUtil.getString(event, "messageId");
        if (messageId.isEmpty()) {
            JsonElement messageElement = event.get("message");
            if (messageElement != null && messageElement.isJsonObject()) {
                messageId = JsonParserUtil.getString(messageElement.getAsJsonObject(), "id");
            }
        }
        markLocalMessageDeleted(messageId);
    }

    private void handleOpponentMessageDeleted(JsonObject event) {
        String messageId = JsonParserUtil.getString(event, "messageId");
        hideLocalMessage(messageId);
    }

    private void handleOnlineStatus(JsonObject event) {
        PresenceEntity presence = toPresenceEntity(event);
        ioExecutor.execute(() -> presenceDao.upsert(presence));
    }

    private void notifyTyping(JsonObject event, boolean typing) {
        if (eventListener == null) {
            return;
        }
        String chatId = JsonParserUtil.getString(event, "chatId");
        String userId = JsonParserUtil.getString(event, "userId");
        mainHandler.post(() -> eventListener.onTyping(chatId, userId, typing));
    }

    private void notifySocketError(Throwable throwable) {
        notifySocketError(throwable == null || throwable.getMessage() == null ? "Network error." : throwable.getMessage());
    }

    private void notifySocketError(String error) {
        if (eventListener != null) {
            mainHandler.post(() -> eventListener.onSocketError(error));
        }
    }

    private MessageEntity toMessageEntity(JsonObject message) {
        if ("gone".equals(JsonParserUtil.getString(message, "visible"))) {
            return null;
        }
        String id = JsonParserUtil.getString(message, "id");
        if (id.isEmpty()) {
            id = JsonParserUtil.getString(message, "messageId");
        }
        String status = JsonParserUtil.getString(message, "status");
        if (status.isEmpty()) {
            status = MessageStatus.SENT;
        }
        return new MessageEntity(
                id,
                JsonParserUtil.getString(message, "clientMessageId"),
                JsonParserUtil.getString(message, "chatId"),
                normalizeAccountId(JsonParserUtil.getString(message, "senderId")),
                normalizeAccountId(JsonParserUtil.getString(message, "receiverId")),
                JsonParserUtil.getString(message, "text"),
                JsonParserUtil.getString(message, "repliedMessageId"),
                JsonParserUtil.getLong(message, "sentTime"),
                getNullableLong(message, "deliveredTime"),
                getNullableLong(message, "readTime"),
                status
        );
    }

    private List<MessageEntity> parseChatDocument(JsonObject chat, String chatId) {
        List<MessageEntity> messages = new ArrayList<>();
        for (java.util.Map.Entry<String, JsonElement> entry : chat.entrySet()) {
            JsonElement element = entry.getValue();
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject message = element.getAsJsonObject();
            if (!message.has("text")) {
                continue;
            }
            if (!message.has("id") || message.get("id").isJsonNull() || message.get("id").getAsString().isEmpty()) {
                message.addProperty("id", entry.getKey());
            }
            if (!message.has("chatId") || message.get("chatId").isJsonNull() || message.get("chatId").getAsString().isEmpty()) {
                message.addProperty("chatId", chatId);
            }
            MessageEntity entity = toMessageEntity(message);
            if (entity != null) {
                messages.add(entity);
            }
        }
        return messages;
    }

    private PresenceEntity toPresenceEntity(JsonObject presence) {
        String userId = JsonParserUtil.getString(presence, "userId");
        boolean isOnline = presence.has("isOnline") && !presence.get("isOnline").isJsonNull() && presence.get("isOnline").getAsBoolean();
        return new PresenceEntity(
                userId,
                isOnline,
                getNullableLong(presence, "lastSeen"),
                System.currentTimeMillis()
        );
    }

    private ChatEntity toChatEntity(JsonObject profile, long updatedAt) {
        String chatId = JsonParserUtil.getString(profile, "chatId");
        String phoneNumber = JsonParserUtil.getString(profile, "phoneNumber");
        boolean isOnline = JsonParserUtil.getBoolean(profile,"isOnline");
        long lastSeen = JsonParserUtil.getLong(profile,"lastSeen");

        if (chatId.isEmpty()) {
            return null;
        }
        String profilePhotoUrl = JsonParserUtil.getString(profile, "profilePhotoUrl");
        return new ChatEntity(
                chatId,
                phoneNumber,
                normalizeAccountId(phoneNumber),
                profilePhotoUrl,
                "",
                "",
                isOnline,
                lastSeen,
                updatedAt
        );
    }

    private Long getNullableLong(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeAccountId(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.startsWith("<plus>")) {
            return normalized.substring("<plus>".length());
        }
        if (normalized.startsWith("+")) {
            return normalized.substring(1);
        }
        return normalized;
    }

    private String toServerPhoneNumber(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.startsWith("<plus>")) {
            return normalized.substring("<plus>".length());
        }
        if (normalized.startsWith("+")) {
            return normalized.substring(1);
        }
        return normalized;
    }
}
