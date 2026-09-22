package com.w3n.pinggo.call;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import java.lang.ref.WeakReference;

/** Tracks the single foreground call host; media sessions live in CallSessionService. */
public final class ActiveCallRegistry {
  public interface PictureInPictureHangupListener {
    void onPictureInPictureHangup();
  }

  public static final String TYPE_VOICE = "voice";
  public static final String TYPE_VIDEO = "video";
  private static final ActiveCallRegistry INSTANCE = new ActiveCallRegistry();
  private WeakReference<Activity> activity = new WeakReference<>(null);
  private String chatId = "", type = "";
  private boolean connected;
  private WeakReference<Activity> callPicker = new WeakReference<>(null);
  private String pickerCallId = "";

  private ActiveCallRegistry() {}
  public static ActiveCallRegistry getInstance() { return INSTANCE; }

  public synchronized void register(Activity activity, String chatId, String type) {
    Activity current = this.activity.get();
    if (current != null && current != activity && !current.isFinishing()
        && !current.isDestroyed() && isIncoming(activity)) return;
    activate(activity, chatId, type);
  }
  public synchronized void activate(Activity activity, String chatId, String type) {
    this.activity = new WeakReference<>(activity);
    this.chatId = normalize(chatId);
    this.type = normalize(type);
    connected = false;
  }
  public synchronized void setConnected(Activity owner, boolean value) {
    if (activity.get() != owner) return;
    connected = value;
    if (value && pickerCallId.equals(normalize(owner.getIntent().getStringExtra(
        com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALL_ID)))) {
      Activity picker = callPicker.get();
      callPicker.clear();
      pickerCallId = "";
      if (picker != null) picker.runOnUiThread(() -> {
        if (!picker.isFinishing() && !picker.isDestroyed()) picker.finish();
      });
    }
  }
  public synchronized void closePickerWhenConnected(Activity picker, String callId) {
    callPicker = new WeakReference<>(picker);
    pickerCallId = normalize(callId);
  }
  public synchronized void clearCallPicker(Activity picker) {
    if (callPicker.get() != picker) return;
    callPicker.clear();
    pickerCallId = "";
  }
  public synchronized boolean hasActiveCall() {
    Activity current = activity.get();
    return current != null && !current.isFinishing() && !current.isDestroyed();
  }
  public synchronized boolean matches(String chatId, String type) {
    return hasActiveCall() && this.chatId.equals(normalize(chatId)) && this.type.equals(normalize(type));
  }
  public synchronized boolean isConnected() { return hasActiveCall() && connected; }
  public synchronized boolean isInPictureInPicture() {
    Activity current = activity.get();
    return current != null && !current.isFinishing() && !current.isDestroyed()
        && CallPictureInPicture.isActive(current);
  }
  public synchronized String getType() { return type; }
  public void requestPictureInPictureHangup() {
    Activity current;
    synchronized (this) {
      current = activity.get();
      if (current == null || current.isFinishing() || current.isDestroyed()
          || !CallPictureInPicture.isActive(current)
          || !(current instanceof PictureInPictureHangupListener)) return;
    }
    Activity owner = current;
    owner.runOnUiThread(() -> {
      if (!owner.isFinishing() && !owner.isDestroyed()) {
        ((PictureInPictureHangupListener) owner).onPictureInPictureHangup();
      }
    });
  }
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
    callPicker.clear(); pickerCallId = "";
  }
  private static String normalize(String value) { return value == null ? "" : value.trim(); }
  private static boolean isIncoming(Activity activity) {
    Intent intent = activity.getIntent();
    String offer = intent.getStringExtra(com.w3n.pinggo.call.session.CallActivityContract.EXTRA_SDP_OFFER);
    return (offer != null && !offer.trim().isEmpty())
        || intent.getBooleanExtra(com.w3n.pinggo.call.session.CallActivityContract.EXTRA_INCOMING, false);
  }
}
