package com.w3n.pinggo.Database.CloudFunction.WebSocket;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.RestApi.APIAuth;
import com.w3n.pinggo.Database.CloudFunction.Utils.JsonParserUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class ChatWebSocketClient {
    private static final String TAG = "PingGoChatSocket";
    public interface Listener {
        void onConnected();

        void onEvent(JsonObject event);

        void onClosed();

        void onFailure(String error);
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .build();
    private final Listener listener;
    private final List<JsonObject> pendingEvents = new ArrayList<>();
    private final Map<String, JsonObject> unacknowledgedMessages = new ConcurrentHashMap<>();
    private final Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private WebSocket webSocket;
    private boolean authenticated;
    private boolean connecting;
    private boolean intentionalDisconnect;
    private String lastUserId;
    private String lastEncryptedCredential;
    private int reconnectAttempts;

    public ChatWebSocketClient(Listener listener) {
        this.listener = listener;
    }

    public void connect(String userId, String encryptedCredential) {
        lastUserId = userId;
        lastEncryptedCredential = encryptedCredential;
        intentionalDisconnect = false;
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
                sendAuth(userId, encryptedCredential);
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
                if (type.startsWith("call_") || "ice_candidate".equals(type)
                        || "auth_success".equals(type)) {
                    Log.d(TAG, "receive type=" + type + " callId="
                            + JsonParserUtil.getString(event, "callId"));
                }
                if ("message_ack".equals(type) || "message_failed".equals(type)) {
                    String clientMessageId = JsonParserUtil.getString(event, "clientMessageId");
                    if (!clientMessageId.isEmpty()) unacknowledgedMessages.remove(clientMessageId);
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
                if (listener != null) {
                    listener.onClosed();
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
        if ("send_message".equals(type)) {
            String clientMessageId = JsonParserUtil.getString(event, "clientMessageId");
            if (!clientMessageId.isEmpty()) unacknowledgedMessages.put(clientMessageId, event.deepCopy());
            if (webSocket == null || !authenticated) {
                scheduleReconnect();
                return true;
            }
            return webSocket.send(event.toString());
        }
        if ((webSocket == null || !authenticated) && !"auth".equals(type)) {
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

    private void sendAuth(String userId, String encryptedCredential) {
        JsonObject auth = new JsonObject();
        auth.addProperty("type", "auth");
        auth.addProperty("userId", userId);
        auth.addProperty("encryptedCredential", encryptedCredential);
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
                connect(lastUserId, lastEncryptedCredential);
            }
        }, delayMs);
    }
}
