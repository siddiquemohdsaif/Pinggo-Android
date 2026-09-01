package com.w3n.pinggo.views.chat;

import android.graphics.Bitmap;

/** Immutable content rendered inside a quoted-message reply card. */
final class ReplyContent {
  static final String TEXT = "text";
  static final String IMAGE = "image";
  static final String VIDEO = "video";
  static final String AUDIO = "audio";
  static final String FILE = "file";
  static final String LOCATION = "location";
  static final String VOICE_CALL = "voice_call";
  static final String VIDEO_CALL = "video_call";

  final String type;
  final String text;
  final String source;
  final String title;
  final String subtitle;
  final long durationMs;
  final float[] waveform;
  final Double latitude;
  final Double longitude;
  final Bitmap icon;
  final String signature;

  private ReplyContent(
      String type, String text, String source, String title, String subtitle,
      long durationMs, float[] waveform, Double latitude, Double longitude, Bitmap icon) {
    this.type = value(type, TEXT);
    this.text = value(text, "");
    this.source = value(source, "");
    this.title = value(title, "");
    this.subtitle = value(subtitle, "");
    this.durationMs = Math.max(0L, durationMs);
    this.waveform = waveform;
    this.latitude = latitude;
    this.longitude = longitude;
    this.icon = icon;
    this.signature = this.type + '\u0001' + this.text + '\u0001' + this.source
        + '\u0001' + this.title + '\u0001' + this.subtitle + '\u0001' + this.durationMs
        + '\u0001' + waveformSignature(waveform) + '\u0001' + String.valueOf(latitude)
        + '\u0001' + String.valueOf(longitude);
  }

  static ReplyContent text(String value) {
    return new ReplyContent(TEXT, value, "", "", "", 0L, null, null, null, null);
  }

  static ReplyContent media(String type, String source) {
    return new ReplyContent(type, "", source, "", "", 0L, null, null, null, null);
  }

  static ReplyContent audio(long durationMs, float[] waveform) {
    return new ReplyContent(AUDIO, "", "", "", "", durationMs, waveform,
        null, null, null);
  }

  static ReplyContent file(String title, String subtitle) {
    return new ReplyContent(FILE, "", "", title, subtitle, 0L, null,
        null, null, null);
  }

  static ReplyContent location(double latitude, double longitude) {
    return new ReplyContent(LOCATION, "", "", "", "", 0L, null,
        latitude, longitude, null);
  }

  static ReplyContent call(String type, String title, Bitmap icon) {
    return new ReplyContent(type, "", "", title, "", 0L, null,
        null, null, icon);
  }

  boolean isMedia() {
    return IMAGE.equals(type) || VIDEO.equals(type);
  }

  boolean isCall() {
    return VOICE_CALL.equals(type) || VIDEO_CALL.equals(type);
  }

  private static String value(String value, String fallback) {
    return value == null ? fallback : value;
  }

  private static String waveformSignature(float[] values) {
    if (values == null || values.length == 0) return "";
    StringBuilder signature = new StringBuilder(values.length * 4);
    for (float value : values) signature.append(Math.round(value * 100f)).append(',');
    return signature.toString();
  }
}
