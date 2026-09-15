package com.w3n.pinggo.Database.CloudFunction.WebSocket;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.w3n.pinggo.Database.CloudFunction.RestApi.APIAuth;
import com.w3n.pinggo.Database.CloudFunction.Utils.JsonParserUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class ChatWebSocketClient {
    private static final String TAG = "PingGoChatSocket";
    private static final String MESSAGE_TRACE_TAG = "PingGoMessageTrace";
    private static final int MAX_PENDING_EVENTS = 128;
    private static final int MAX_UNACKNOWLEDGED_MESSAGES = 256;
    private static final String QUEUE_PREFERENCES = "pinggo_realtime_queue_v1";
    private static final String KEY_UNACKNOWLEDGED = "unacknowledged";
    public interface Listener {
        void onConnected();

        void onEvent(JsonObject event);

        void onClosed(int code, String reason);

        void onFailure(String error);
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .build();
    private final Listener listener;
    private final SharedPreferences queuePreferences;
    private final ExecutorService queueIo = Executors.newSingleThreadExecutor();
    private final List<JsonObject> pendingEvents = new ArrayList<>();
    private final Map<String, JsonObject> unacknowledgedMessages = new ConcurrentHashMap<>();
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private WebSocket webSocket;
    private boolean authenticated;
    private boolean connecting;
    private boolean intentionalDisconnect;
    private String lastUserId;
    private String lastEncryptedCredential;
    private String lastDeviceId;
    private String restoredQueueUserId;
    private int reconnectAttempts;

    public ChatWebSocketClient(Context context, Listener listener) {
        this.listener = listener;
        queuePreferences = context.getApplicationContext().getSharedPreferences(
                QUEUE_PREFERENCES, Context.MODE_PRIVATE);
    }

    public void connect(String userId, String encryptedCredential, String deviceId) {
        lastUserId = userId;
        lastEncryptedCredential = encryptedCredential;
        lastDeviceId = deviceId;
        intentionalDisconnect = false;
        restoreQueueForUser(userId);
        if (connecting || authenticated) return;
        connecting = true;
        Log.d(TAG, "connect url=" + APIAuth.WS_URL + " attempt=" + reconnectAttempts);
        authenticated = false;
        String authToken = userId + "_" + encryptedCredential;
        Request request = new Request.Builder()
                .url(APIAuth.WS_URL)
                .addHeader("Authorization", "Bearer " + authToken)
                .build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                connecting = false;
                Log.d(TAG, "open responseCode=" + response.code());
                sendAuth(userId, encryptedCredential, deviceId);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                JsonObject event = JsonParserUtil.parseObject(text);
                if (event == null) {
                    if (listener != null) {
                        listener.onFailure("Invalid WebSocket JSON.");
                    }
                    return;
                }
                String type = JsonParserUtil.getString(event, "type");
                if ("new_message".equals(type)) {
                    JsonObject message = event.has("message") && event.get("message").isJsonObject()
                            ? event.getAsJsonObject("message") : new JsonObject();
                    JsonObject attachment = message.has("attachment")
                            && message.get("attachment").isJsonObject()
                            ? message.getAsJsonObject("attachment") : new JsonObject();
                    Log.d(MESSAGE_TRACE_TAG, "stage=websocket_arrived"
                            + " chatId=" + JsonParserUtil.getString(message, "chatId")
                            + " messageId=" + JsonParserUtil.getString(message, "id")
                            + " clientMessageId=" + JsonParserUtil.getString(message, "clientMessageId")
                            + " messageType=" + JsonParserUtil.getString(message, "messageType")
                            + " hasAttachment=" + message.has("attachment")
                            + " width=" + JsonParserUtil.getLong(attachment, "width")
                            + " height=" + JsonParserUtil.getLong(attachment, "height")
                            + " orientation=" + JsonParserUtil.getString(attachment, "orientation")
                            + " size=" + JsonParserUtil.getLong(attachment, "size")
                            + " durationMs=" + JsonParserUtil.getLong(attachment, "durationMs"));
                }
                if (type.startsWith("call_") || "ice_candidate".equals(type)
                        || "auth_success".equals(type)) {
                    Log.d(TAG, "receive type=" + type + " callId="
                            + JsonParserUtil.getString(event, "callId"));
                }
                if ("message_ack".equals(type) || "message_failed".equals(type)
                        || "group_message_ack".equals(type) || "group_message_failed".equals(type)) {
                    String clientMessageId = JsonParserUtil.getString(event, "clientMessageId");
                    if (!clientMessageId.isEmpty()) {
                        unacknowledgedMessages.remove(clientMessageId);
                        persistUnacknowledgedMessages();
                    }
                }
                if ("auth_success".equals(type) && listener != null) {
                    authenticated = true;
                    reconnectAttempts = 0;
                    reconnectHandler.removeCallbacksAndMessages(null);
                    flushPendingEvents();
                    resendUnacknowledgedMessages();
                    listener.onConnected();
                }
                if (listener != null) {
                    listener.onEvent(event);
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.w(TAG, "closed code=" + code + " reason=" + reason);
                authenticated = false;
                connecting = false;
                // Authentication failure and remote device revocation are terminal
                // for these credentials. Never let an unlinked companion reconnect.
                if (code == 4001 || code == 4003) {
                    intentionalDisconnect = true;
                    reconnectHandler.removeCallbacksAndMessages(null);
                    lastEncryptedCredential = null;
                }
                if (listener != null) {
                    listener.onClosed(code, reason);
                }
                scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, @Nullable Response response) {
                Log.e(TAG, "failure responseCode=" + (response == null ? 0 : response.code()), t);
                authenticated = false;
                connecting = false;
                if (listener != null) {
                    listener.onFailure(t == null || t.getMessage() == null
                            ? "WebSocket failed."
                            : t.getMessage());
                }
                scheduleReconnect();
            }
        });
    }

    public void disconnect() {
        intentionalDisconnect = true;
        reconnectHandler.removeCallbacksAndMessages(null);
        if (webSocket != null) {
            webSocket.close(1000, "App closed");
            webSocket = null;
        }
        authenticated = false;
        connecting = false;
    }

    public boolean send(JsonObject event) {
        String type = JsonParserUtil.getString(event, "type");
        if (type.startsWith("call_") || "ice_candidate".equals(type)) {
            Log.d(TAG, "send requested type=" + type + " callId="
                    + JsonParserUtil.getString(event, "callId")
                    + " socket=" + (webSocket != null) + " authenticated=" + authenticated);
        }
        if ("send_message".equals(type) || "send_group_message".equals(type)) {
            String clientMessageId = JsonParserUtil.getString(event, "clientMessageId");
            if (!clientMessageId.isEmpty()) {
                putBoundedUnacknowledged(clientMessageId, event.deepCopy());
                persistUnacknowledgedMessages();
            }
            if (webSocket == null || !authenticated) {
                scheduleReconnect();
                return true;
            }
            return webSocket.send(event.toString());
        }
        if ((webSocket == null || !authenticated) && !"auth".equals(type)) {
            if (pendingEvents.size() >= MAX_PENDING_EVENTS) pendingEvents.remove(0);
            pendingEvents.add(event.deepCopy());
            Log.d(TAG, "queued type=" + type + " pendingCount=" + pendingEvents.size());
            scheduleReconnect();
            return true;
        }
        if (webSocket == null) return false;
        return webSocket.send(event.toString());
    }

    public boolean isAwaitingMessageAck(String clientMessageId) {
        return clientMessageId != null
                && !clientMessageId.isEmpty()
                && unacknowledgedMessages.containsKey(clientMessageId);
    }

    private void sendAuth(String userId, String encryptedCredential, String deviceId) {
        JsonObject auth = new JsonObject();
        auth.addProperty("type", "auth");
        auth.addProperty("userId", userId);
        auth.addProperty("encryptedCredential", encryptedCredential);
        auth.addProperty("deviceId", deviceId);
        send(auth);
    }

    private void flushPendingEvents() {
        if (webSocket == null || pendingEvents.isEmpty()) {
            return;
        }
        List<JsonObject> events = new ArrayList<>(pendingEvents);
        Log.d(TAG, "flushPending count=" + events.size());
        pendingEvents.clear();
        for (JsonObject event : events) {
            webSocket.send(event.toString());
        }
    }

    private void resendUnacknowledgedMessages() {
        if (webSocket == null || !authenticated || unacknowledgedMessages.isEmpty()) return;
        for (Map.Entry<String, JsonObject> entry : new ArrayList<>(unacknowledgedMessages.entrySet())) {
            webSocket.send(entry.getValue().toString());
        }
    }

    private void putBoundedUnacknowledged(String id, JsonObject event) {
        if (!unacknowledgedMessages.containsKey(id)
                && unacknowledgedMessages.size() >= MAX_UNACKNOWLEDGED_MESSAGES) {
            String oldest = unacknowledgedMessages.keySet().iterator().next();
            unacknowledgedMessages.remove(oldest);
        }
        unacknowledgedMessages.put(id, event);
    }

    private void restoreQueueForUser(String userId) {
        String normalized = userId == null ? "" : userId.trim();
        if (normalized.isEmpty() || normalized.equals(restoredQueueUserId)) return;
        restoredQueueUserId = normalized;
        unacknowledgedMessages.clear();
        queueIo.execute(() -> restoreUnacknowledgedMessages(normalized));
    }

    private void restoreUnacknowledgedMessages(String userId) {
        try {
            JsonArray stored = JsonParser.parseString(
                    queuePreferences.getString(queueKey(userId), "[]")).getAsJsonArray();
            if (!userId.equals(restoredQueueUserId)) return;
            for (JsonElement value : stored) {
                if (!value.isJsonObject()) continue;
                JsonObject event = value.getAsJsonObject();
                String id = JsonParserUtil.getString(event, "clientMessageId");
                if (!id.isEmpty()) putBoundedUnacknowledged(id, event.deepCopy());
            }
            if (authenticated && userId.equals(lastUserId)) resendUnacknowledgedMessages();
        } catch (RuntimeException error) {
            queuePreferences.edit().remove(queueKey(userId)).apply();
        }
    }

    private void persistUnacknowledgedMessages() {
        String userId = lastUserId;
        if (userId == null || userId.trim().isEmpty()) return;
        List<JsonObject> snapshot = new ArrayList<>();
        for (JsonObject event : unacknowledgedMessages.values()) snapshot.add(event.deepCopy());
        queueIo.execute(() -> persistSnapshot(userId, snapshot));
    }

    private void persistSnapshot(String userId, List<JsonObject> snapshot) {
        JsonArray stored = new JsonArray();
        for (JsonObject event : snapshot) stored.add(event);
        queuePreferences.edit().putString(queueKey(userId), stored.toString()).apply();
    }

    private static String queueKey(String userId) {
        return KEY_UNACKNOWLEDGED + "_" + Integer.toHexString(userId.hashCode());
    }

    private void scheduleReconnect() {
        if (intentionalDisconnect || connecting || authenticated
                || lastUserId == null || lastEncryptedCredential == null) return;
        reconnectHandler.removeCallbacksAndMessages(null);
        long delayMs = Math.min(30000L, 2000L << Math.min(reconnectAttempts, 4));
        reconnectAttempts++;
        Log.d(TAG, "scheduleReconnect delayMs=" + delayMs + " attempt=" + reconnectAttempts);
        reconnectHandler.postDelayed(() -> {
            if (!intentionalDisconnect && !connecting && !authenticated) {
                webSocket = null;
                connect(lastUserId, lastEncryptedCredential, lastDeviceId);
            }
        }, delayMs);
    }
}
