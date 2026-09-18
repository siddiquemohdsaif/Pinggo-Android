package com.w3n.pinggo.views.chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.pinggo.data.local.MessageTypeCodec;

public final class MediaRecordTypes {
  public static String type(JsonObject record) {
    try {
      JsonElement value = record.get("messageType");
      if (value == null || !value.isJsonPrimitive()) return "";
      if (value.getAsJsonPrimitive().isNumber()) {
        int code = value.getAsInt();
        if (value.getAsDouble() != code) return "";
        return MessageTypeCodec.decode(code);
      }
      String name = value.getAsString();
      return MessageTypeCodec.decode(MessageTypeCodec.encode(name));
    } catch (RuntimeException invalid) { return ""; }
  }
  private MediaRecordTypes() { }
}
