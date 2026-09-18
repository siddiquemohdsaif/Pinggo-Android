package com.w3n.pinggo.activity;

import com.google.gson.JsonObject;
import com.w3n.pinggo.views.chat.MediaRecordTypes;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaRecordTypesTest {
  @Test public void serverNumericTypesDecode() {
    JsonObject record = new JsonObject();
    record.addProperty("messageType", 1); assertEquals("image", MediaRecordTypes.type(record));
    record.addProperty("messageType", 2); assertEquals("video", MediaRecordTypes.type(record));
    record.addProperty("messageType", 4); assertEquals("file", MediaRecordTypes.type(record));
    record.addProperty("messageType", 0); assertEquals("text", MediaRecordTypes.type(record));
  }
  @Test public void localStringTypesRemainSupported() {
    JsonObject record = new JsonObject();
    record.addProperty("messageType", "VIDEO"); assertEquals("video", MediaRecordTypes.type(record));
  }
  @Test public void malformedTypesAreSkipped() {
    JsonObject record = new JsonObject();
    assertEquals("", MediaRecordTypes.type(record));
    record.addProperty("messageType", 99); assertEquals("", MediaRecordTypes.type(record));
    record.addProperty("messageType", 1.5); assertEquals("", MediaRecordTypes.type(record));
    record.addProperty("messageType", "other"); assertEquals("", MediaRecordTypes.type(record));
  }
}
