package com.w3n.pinggo.call.session;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Process-level registry used by CallActivity and the foreground call service. */
public final class CallSessionRegistry implements CallSession.Observer {
  private static final String CALL_SWAP_TAG = "PingGoCallSwap";
  public interface Listener {
    void onActiveSessionChanged(@Nullable CallSession session);
  }

  private final Map<String, CallSession> sessions = new LinkedHashMap<>();
  private final List<Listener> listeners = new ArrayList<>();
  private String activeCallId = "";
  private boolean switching;

  public synchronized void addListener(@NonNull Listener listener) {
    if (!listeners.contains(listener)) listeners.add(listener);
  }

  public synchronized void removeListener(@NonNull Listener listener) {
    listeners.remove(listener);
  }

  public synchronized void put(@NonNull CallSession session) {
    sessions.put(session.callId(), session);
    session.addObserver(this);
    if (activeCallId.isEmpty()) activeCallId = session.callId();
    notifyListenersLocked();
  }

  @Nullable public synchronized CallSession get(String callId) {
    return sessions.get(normalize(callId));
  }

  @Nullable public synchronized CallSession active() {
    return sessions.get(activeCallId);
  }

  @NonNull public synchronized List<CallSession> sessions() {
    return new ArrayList<>(sessions.values());
  }

  public synchronized boolean hasHeldSession() {
    for (CallSession session : sessions.values()) {
      if (!session.callId().equals(activeCallId)
          && session.state().phase == CallSessionState.Phase.HELD) return true;
    }
    return false;
  }

  @Nullable public synchronized CallSession heldSession() {
    return mostRecentHeldLocked();
  }

  @NonNull public synchronized List<CallSession> heldSessions() {
    List<CallSession> result = new ArrayList<>();
    for (CallSession session : sessions.values()) {
      if (!session.callId().equals(activeCallId)
          && session.state().phase == CallSessionState.Phase.HELD) result.add(session);
    }
    return result;
  }

  public void activate(@NonNull String callId, @NonNull Runnable completion) {
    final CallSession previous;
    final CallSession next;
    synchronized (this) {
      next = sessions.get(normalize(callId));
      previous = sessions.get(activeCallId);
      if (next == null) { completion.run(); return; }
      if (next == previous) { completion.run(); return; }
      if (switching) return;
      switching = true;
    }
    Runnable activated = () -> {
      synchronized (CallSessionRegistry.this) {
        activeCallId = next.callId();
        switching = false;
        Log.i(CALL_SWAP_TAG, "activated activeCallId=" + activeCallId
            + " previousCallId=" + (previous == null ? "" : previous.callId())
            + " previousPhase=" + (previous == null ? "none" : previous.state().phase));
        notifyListenersLocked();
      }
      completion.run();
    };
    CallSessionState.Phase nextPhase = next.state().phase;
    boolean waitingForAnswer = nextPhase == CallSessionState.Phase.INCOMING
        || nextPhase == CallSessionState.Phase.RINGING;
    Runnable resumeNext = waitingForAnswer
        ? activated : () -> next.setHeld(false, activated);
    if (previous == null) resumeNext.run();
    else previous.setHeld(true, resumeNext);
  }

  public void swap(@NonNull Runnable completion) {
    final CallSession target;
    synchronized (this) {
      target = mostRecentHeldLocked();
    }
    if (target == null) {
      Log.w(CALL_SWAP_TAG, "swap_ignored reason=no_held_session activeCallId="
          + activeCallId);
      completion.run();
      return;
    }
    Log.i(CALL_SWAP_TAG, "swap_requested from=" + activeCallId
        + " to=" + target.callId());
    activate(target.callId(), completion);
  }

  public synchronized void remove(String callId) {
    CallSession removed = sessions.remove(normalize(callId));
    if (removed == null) return;
    removed.removeObserver(this);
    removed.release();
    if (removed.callId().equals(activeCallId)) {
      CallSession replacement = mostRecentHeldLocked();
      if (replacement == null) replacement = mostRecentAvailableLocked();
      activeCallId = replacement == null ? "" : replacement.callId();
    }
    notifyListenersLocked();
  }

  private CallSession mostRecentHeldLocked() {
    CallSession result = null;
    for (CallSession session : sessions.values()) {
      if (!session.callId().equals(activeCallId)
          && session.state().phase == CallSessionState.Phase.HELD) result = session;
    }
    return result;
  }

  private CallSession mostRecentAvailableLocked() {
    CallSession result = null;
    for (CallSession session : sessions.values()) {
      CallSessionState.Phase phase = session.state().phase;
      if (phase != CallSessionState.Phase.ENDED && phase != CallSessionState.Phase.FAILED)
        result = session;
    }
    return result;
  }

  @Override public void onSessionChanged(@NonNull CallSession session,
      @NonNull CallSessionState state) {
    if (state.phase == CallSessionState.Phase.ENDED
        || state.phase == CallSessionState.Phase.FAILED) remove(session.callId());
  }

  @Override public void onSessionEnded(@NonNull CallSession session, @NonNull String reason) {
    remove(session.callId());
  }

  private void notifyListenersLocked() {
    CallSession active = sessions.get(activeCallId);
    for (Listener listener : new ArrayList<>(listeners))
      listener.onActiveSessionChanged(active);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
