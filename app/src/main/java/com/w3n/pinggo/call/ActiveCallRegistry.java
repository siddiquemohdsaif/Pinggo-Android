package com.w3n.pinggo.call;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import java.lang.ref.WeakReference;

/** Tracks the single call activity owned by this app process. */
public final class ActiveCallRegistry {
  public static final String TYPE_VOICE = "voice";
  public static final String TYPE_VIDEO = "video";
  private static final ActiveCallRegistry INSTANCE = new ActiveCallRegistry();
  private WeakReference<Activity> activity = new WeakReference<>(null);
  private String chatId = "", type = "";
  private boolean connected;

  private ActiveCallRegistry() {}
  public static ActiveCallRegistry getInstance() { return INSTANCE; }

  public synchronized void register(Activity activity, String chatId, String type) {
    this.activity = new WeakReference<>(activity);
    this.chatId = normalize(chatId);
    this.type = normalize(type);
    connected = false;
  }
  public synchronized void setConnected(Activity owner, boolean value) {
    if (activity.get() == owner) connected = value;
  }
  public synchronized boolean hasActiveCall() {
    Activity current = activity.get();
    return current != null && !current.isFinishing() && !current.isDestroyed();
  }
  public synchronized boolean matches(String chatId, String type) {
    return hasActiveCall() && this.chatId.equals(normalize(chatId)) && this.type.equals(normalize(type));
  }
  public synchronized boolean isConnected() { return hasActiveCall() && connected; }
  public synchronized String getType() { return type; }
  public synchronized void openExisting(Context context) {
    Activity current = activity.get();
    if (current == null) return;
    Intent intent = new Intent(context, current.getClass());
    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    context.startActivity(intent);
  }
  public synchronized void clear(Activity owner) {
    if (activity.get() != owner) return;
    activity.clear(); chatId = ""; type = ""; connected = false;
  }
  private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
