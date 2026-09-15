package com.w3n.pinggo.data.repository;

import android.content.Context;

import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.WebSocket.ChatWebSocketClient;

/**
 * Realtime boundary for ChatRepository.
 *
 * Keeps socket lifecycle and transport details out of the repository so message,
 * presence, and attachment persistence can be extracted independently later.
 */
final class ChatRealtimeGateway {
  private final ChatWebSocketClient client;

  ChatRealtimeGateway(Context context, ChatWebSocketClient.Listener listener) {
    client = new ChatWebSocketClient(context, listener);
  }

  void connect(String userId, String credential, String deviceId) {
    client.connect(userId, credential, deviceId);
  }

  void disconnect() { client.disconnect(); }

  boolean send(JsonObject event) { return client.send(event); }

  boolean isAwaitingMessageAck(String clientMessageId) {
    return client.isAwaitingMessageAck(clientMessageId);
  }
}
