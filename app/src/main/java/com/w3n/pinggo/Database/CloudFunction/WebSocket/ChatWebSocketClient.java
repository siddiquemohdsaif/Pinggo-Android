package com.w3n.pinggo.Database.CloudFunction.WebSocket;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.RestApi.APIAuth;
import com.w3n.pinggo.Database.CloudFunction.Utils.JsonParserUtil;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class ChatWebSocketClient {
    public interface Listener {
        void onConnected();

        void onEvent(JsonObject event);

        void onClosed();

        void onFailure(String error);
    }

    private final OkHttpClient client = new OkHttpClient();
    private final Listener listener;
    private final List<JsonObject> pendingEvents = new ArrayList<>();
    private WebSocket webSocket;
    private boolean authenticated;

    public ChatWebSocketClient(Listener listener) {
        this.listener = listener;
    }

    public void connect(String userId, String encryptedCredential) {
        authenticated = false;
        String authToken = userId + "_" + encryptedCredential;
        Request request = new Request.Builder()
                .url(APIAuth.WS_URL)
                .addHeader("Authorization", "Bearer " + authToken)
                .build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                sendAuth(userId, encryptedCredential);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d("CHAT_REPOSITORY", "received: " + text);
                JsonObject event = JsonParserUtil.parseObject(text);
                if (event == null) {
                    if (listener != null) {
                        listener.onFailure("Invalid WebSocket JSON.");
                    }
                    return;
                }
                String type = JsonParserUtil.getString(event, "type");
                if ("auth_success".equals(type) && listener != null) {
                    authenticated = true;
                    flushPendingEvents();
                    listener.onConnected();
                }
                if (listener != null) {
                    listener.onEvent(event);
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                authenticated = false;
                if (listener != null) {
                    listener.onClosed();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, @Nullable Response response) {
                authenticated = false;
                if (listener != null) {
                    Log.d("CHAT_REPOSITORY", "onFailure: " + t.getMessage());
                    listener.onFailure(t == null || t.getMessage() == null
                            ? "WebSocket failed."
                            : t.getMessage());
                }
            }
        });
    }

    public void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "App closed");
            webSocket = null;
        }
        authenticated = false;
    }

    public boolean send(JsonObject event) {
        Log.d("CHAT_REPOSITORY", "send: " + event);
        if (webSocket == null) {
            return false;
        }
        if (!authenticated && !"auth".equals(JsonParserUtil.getString(event, "type"))) {
            pendingEvents.add(event);
            Log.d("CHAT_REPOSITORY", "queued_until_auth: " + event);
            return true;
        }
        return webSocket.send(event.toString());
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
        pendingEvents.clear();
        for (JsonObject event : events) {
            Log.d("CHAT_REPOSITORY", "flush: " + event);
            webSocket.send(event.toString());
        }
    }
}
