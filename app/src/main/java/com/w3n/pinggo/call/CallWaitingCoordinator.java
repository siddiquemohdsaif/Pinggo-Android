package com.w3n.pinggo.call;

import android.app.Activity;
import android.content.Intent;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

/** Keeps the accepted call active while retaining older calls in a held stack. */
public final class CallWaitingCoordinator {
  public interface Session {
    void onCallHeld(String waitingCallId, Runnable onHeld);
    void onCallResumed(String endedCallId);
    void onCallSwapHeld(String resumedCallId, Runnable onHeld);
    void onCallSwapResumed(String heldCallId);
  }

  private static final CallWaitingCoordinator INSTANCE = new CallWaitingCoordinator();
  private final LinkedHashMap<String, WeakReference<Activity>> calls = new LinkedHashMap<>();
  private final Set<String> activatedCalls = new LinkedHashSet<>();
  private String activeCallId = "";
  private String switchingCallId = "";

  private CallWaitingCoordinator() {}
  public static CallWaitingCoordinator getInstance() { return INSTANCE; }

  public synchronized void register(Activity activity) {
    prune();
    String callId = callId(activity);
    calls.put(callId, new WeakReference<>(activity));
    if (activeCallId.isEmpty()) {
      activeCallId = callId;
      activatedCalls.add(callId);
    }
  }

  /** Called only when the user accepts a waiting call, never while it merely rings. */
  public void activate(Activity activity) {
    activate(activity, () -> { });
  }

  /** Runs onReady only after the previous call has released its media capture. */
  public void activate(Activity activity, Runnable onReady) {
    Activity previous;
    String nextCallId = callId(activity);
    synchronized (this) {
      prune();
      if (nextCallId.equals(activeCallId)) {
        activity.runOnUiThread(onReady);
        return;
      }
      previous = activity(activeCallId);
      activeCallId = nextCallId;
      activatedCalls.add(nextCallId);
    }
    if (previous instanceof Session) {
      previous.runOnUiThread(() -> ((Session) previous).onCallHeld(nextCallId,
          () -> activity.runOnUiThread(onReady)));
    } else {
      activity.runOnUiThread(onReady);
    }
  }

  public void unregister(Activity activity) {
    Activity resume = null;
    String endedCallId = callId(activity);
    synchronized (this) {
      WeakReference<Activity> owner = calls.get(endedCallId);
      if (owner == null || owner.get() != activity) return;
      calls.remove(endedCallId);
      activatedCalls.remove(endedCallId);
      if (endedCallId.equals(activeCallId)) {
        activeCallId = "";
        for (Map.Entry<String, WeakReference<Activity>> entry : calls.entrySet()) {
          if (!activatedCalls.contains(entry.getKey())) continue;
          Activity candidate = entry.getValue().get();
          if (candidate != null && !candidate.isFinishing() && !candidate.isDestroyed()) {
            activeCallId = entry.getKey();
            resume = candidate;
          }
        }
      }
    }
    if (resume instanceof Session) {
      Activity held = resume;
      held.runOnUiThread(() -> {
        ((Session) held).onCallResumed(endedCallId);
        Intent intent = new Intent(held, held.getClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        held.startActivity(intent);
      });
    }
  }

  /** True when this active call can be exchanged with another accepted held call. */
  public synchronized boolean hasHeldCall(Activity activity) {
    prune();
    String currentCallId = callId(activity);
    return currentCallId.equals(activeCallId) && heldActivity(currentCallId) != null;
  }

  /** Holds the foreground call, resumes the most recent held call, and foregrounds it. */
  public boolean swap(Activity activity) {
    Activity target;
    String currentCallId = callId(activity);
    String targetCallId;
    synchronized (this) {
      prune();
      if (!currentCallId.equals(activeCallId) || !switchingCallId.isEmpty()
          || !(activity instanceof Session)) return false;
      target = heldActivity(currentCallId);
      if (!(target instanceof Session)) return false;
      targetCallId = callId(target);
      switchingCallId = currentCallId;
    }
    Activity resumed = target;
    String resumedCallId = targetCallId;
    ((Session) activity).onCallSwapHeld(resumedCallId, () -> activity.runOnUiThread(() -> {
      synchronized (CallWaitingCoordinator.this) {
        if (!switchingCallId.equals(currentCallId)) return;
        switchingCallId = "";
        activeCallId = resumedCallId;
      }
      ((Session) resumed).onCallSwapResumed(currentCallId);
      Intent intent = new Intent(resumed, resumed.getClass());
      intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
      resumed.startActivity(intent);
    }));
    return true;
  }

  private Activity heldActivity(String currentCallId) {
    Activity result = null;
    for (Map.Entry<String, WeakReference<Activity>> entry : calls.entrySet()) {
      if (entry.getKey().equals(currentCallId)
          || !activatedCalls.contains(entry.getKey())) continue;
      Activity candidate = entry.getValue().get();
      if (candidate != null && !candidate.isFinishing() && !candidate.isDestroyed())
        result = candidate;
    }
    return result;
  }

  private Activity activity(String callId) {
    WeakReference<Activity> reference = calls.get(callId);
    return reference == null ? null : reference.get();
  }
  private void prune() {
    Iterator<Map.Entry<String, WeakReference<Activity>>> iterator = calls.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, WeakReference<Activity>> entry = iterator.next();
      Activity activity = entry.getValue().get();
      if (activity == null || activity.isDestroyed()) {
        activatedCalls.remove(entry.getKey());
        iterator.remove();
      }
    }
    if (!calls.containsKey(activeCallId)) activeCallId = "";
  }
  private static String callId(Activity activity) {
    String value = activity.getIntent().getStringExtra(com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALL_ID);
    return value == null ? "" : value.trim();
  }
}
