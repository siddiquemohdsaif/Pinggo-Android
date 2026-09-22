package com.w3n.pinggo.call.session;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.Utils.JsonParserUtil;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.call.CallEngineToggle;
import com.w3n.pinggo.call.WebRTCCallClient;
import com.w3n.pinggo.data.repository.ChatRepository;
import java.util.concurrent.CopyOnWriteArraySet;

/** Service-owned legacy WebRTC voice call. */
public final class LegacyVoiceCallSession implements CallSession,
    WebRTCCallClient.Listener, ChatRepository.CallEventListener {
  private final Context context;
  private final Intent source;
  private final ChatRepository repository;
  private final String callId, chatId, localId, peerId, offer;
  private final boolean incoming;
  private volatile CallSessionState state;
  private final CopyOnWriteArraySet<Observer> observers = new CopyOnWriteArraySet<>();
  private WebRTCCallClient client;
  private boolean accepted, ended;

  public LegacyVoiceCallSession(@NonNull Context context, @NonNull Intent intent) {
    this.context = context.getApplicationContext();
    source = new Intent(intent);
    callId = value(intent, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALL_ID);
    chatId = value(intent, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALL_CHAT_ID);
    peerId = value(intent, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALLER_ID);
    offer = value(intent, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_SDP_OFFER);
    incoming = !offer.isEmpty();
    localId = LoginStateManager.getInstance().getUID(context);
    repository = ChatRepository.getInstance(context);
    state = CallSessionState.initial(incoming, false);
    CallAudioRouter.apply(this.context, false);
    repository.setCallEventListener(this);
    if (incoming) {
      sendControl("call_ringing");
    } else {
      startClient();
    }
  }

  @NonNull @Override public String callId() { return callId; }
  @NonNull @Override public String chatId() { return chatId; }
  @NonNull @Override public String engine() { return CallEngineToggle.LEGACY; }
  @NonNull @Override public String mediaType() { return "audio"; }
  @Override public boolean incoming() { return incoming; }
  @NonNull @Override public CallSessionState state() { return state; }
  @NonNull @Override public Intent sourceIntent() { return new Intent(source); }
  @Override public void addObserver(@NonNull Observer value) {
    observers.add(value);
    value.onSessionChanged(this, state);
  }
  @Override public void removeObserver(@NonNull Observer value) { observers.remove(value); }

  @Override public void accept() {
    if (!incoming || accepted || ended) return;
    accepted = true;
    publish(state.withPhase(CallSessionState.Phase.CONNECTING, "Connecting…"));
    startClient();
  }

  @Override public void reject() {
    if (ended) return;
    sendControl("call_reject");
    endLocal("rejected");
  }

  private void startClient() {
    if (client != null || ended) return;
    client = new WebRTCCallClient(context, repository, this);
    if (incoming) client.startIncoming(callId, chatId, localId, peerId, offer, "audio");
    else client.startOutgoing(callId, chatId, localId, peerId, "audio");
  }

  @Override public void setHeld(boolean held, @NonNull Runnable completion) {
    if (ended) { completion.run(); return; }
    Runnable applied = () -> {
      sendControl(held ? "call_hold" : "call_resume");
      publish(state.withPhase(held ? CallSessionState.Phase.HELD
          : CallSessionState.Phase.CONNECTED, held ? "On hold" : "Call resumed"));
      completion.run();
    };
    if (client == null) applied.run();
    else client.setHeld(held, state.muted, applied);
  }

  @Override public void setMuted(boolean muted) {
    if (client != null) client.setMuted(muted);
    publish(new CallSessionState(state.phase, state.status, muted,
        state.speakerEnabled, state.cameraEnabled, state.peerMuted,
        state.peerCameraEnabled, state.connectedAtMs));
  }

  @Override public void setSpeakerEnabled(boolean enabled) {
    if (!CallAudioRouter.apply(context, enabled)) return;
    publish(new CallSessionState(state.phase, state.status, state.muted, enabled,
        state.cameraEnabled, state.peerMuted, state.peerCameraEnabled,
        state.connectedAtMs));
  }

  @Override public void setCameraEnabled(boolean enabled) { }

  @Override public void end(@NonNull String reason) {
    if (ended) return;
    if (client != null) client.endCall(reason); else sendControl("call_end");
    endLocal(reason);
  }

  @Override public void release() {
    repository.clearCallEventListener(this);
    if (client != null) client.close(false);
    CallAudioRouter.reset(context);
    observers.clear();
  }

  @Override public void onState(String value) {
    CallSessionState.Phase phase = state.phase;
    long connectedAt = state.connectedAtMs;
    if ("Connected".equals(value)) {
      phase = CallSessionState.Phase.CONNECTED;
      if (connectedAt == 0L) connectedAt = System.currentTimeMillis();
    } else if ("Connecting…".equals(value)) phase = CallSessionState.Phase.CONNECTING;
    else if (value.startsWith("Ringing")) phase = CallSessionState.Phase.RINGING;
    publish(new CallSessionState(phase, value, state.muted, state.speakerEnabled,
        false, state.peerMuted, false, connectedAt));
  }

  @Override public void onRemoteMuteChanged(boolean muted) {
    publish(new CallSessionState(state.phase, state.status, state.muted,
        state.speakerEnabled, false, muted, false, state.connectedAtMs));
  }

  @Override public void onServerConnectedAt(long value) {
    if (value <= 0L) return;
    publish(new CallSessionState(state.phase, state.status, state.muted,
        state.speakerEnabled, false, state.peerMuted, false, value));
  }

  @Override public void onSignalingConnectionChanged(boolean connected) {
    if (!connected) publish(state.withPhase(CallSessionState.Phase.CONNECTING,
        "Reconnecting…"));
  }

  @Override public void onPeerHoldChanged(boolean held) {
    Runnable applied = () -> publish(state.withPhase(state.phase,
        held ? "Call on hold" : "Call resumed"));
    if (client == null) applied.run();
    else client.setHeld(held, state.muted, applied);
  }

  @Override public void onEnded(String reason) { endLocal(reason); }
  @Override public void onError(String message) {
    publish(state.withPhase(CallSessionState.Phase.FAILED, message));
  }

  @Override public void onCallEvent(JsonObject event) {
    if (accepted || !callId.equals(JsonParserUtil.getString(event, "callId"))) return;
    String type = JsonParserUtil.getString(event, "type");
    if ("call_end".equals(type) || "call_no_answer".equals(type)
        || "call_reject".equals(type)) endLocal(type.replace("call_", ""));
  }

  private void sendControl(String type) {
    JsonObject event = new JsonObject();
    event.addProperty("type", type);
    event.addProperty("callId", callId);
    event.addProperty("senderId", localId);
    event.addProperty("receiverId", peerId);
    repository.sendCallEvent(event);
  }

  private void publish(CallSessionState value) {
    state = value;
    for (Observer current : observers) context.getMainExecutor().execute(
        () -> current.onSessionChanged(this, value));
  }

  private void endLocal(String reason) {
    if (ended) return;
    ended = true;
    String text;
    if ("no_answer".equals(reason)) text = "No answer.";
    else if ("rejected".equals(reason) || "reject".equals(reason)) text = "Call rejected.";
    else if ("signaling_disconnected".equals(reason)) text = "Call connection failed.";
    else if ("permission_denied".equals(reason)) text = "Call permission denied.";
    else text = "Call ended";
    publish(state.withPhase(CallSessionState.Phase.ENDED, text));
    for (Observer current : observers) context.getMainExecutor().execute(
        () -> current.onSessionEnded(this, reason));
  }

  private static String value(Intent intent, String key) {
    String result = intent.getStringExtra(key);
    return result == null ? "" : result.trim();
  }
}
