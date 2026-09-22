package com.w3n.pinggo.call.session;

import android.content.Context;
import android.content.Intent;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.w3n.pinggo.Database.CloudFunction.RestApi.APIAuth;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.call.CallEngineToggle;
import com.w3n.pinggo.call.VideoCallController;
import com.w3n.pinggo.data.repository.ChatRepository;
import java.util.concurrent.CopyOnWriteArraySet;

/** Service-owned legacy WebRTC/video-socket call. */
public final class LegacyVideoCallSession implements CallSession, VideoCallController.Listener {
  private final Context context;
  private final Intent source;
  private final String callId, chatId;
  private final VideoCallController controller;
  private volatile CallSessionState state;
  private final CopyOnWriteArraySet<Observer> observers = new CopyOnWriteArraySet<>();
  private final boolean incoming;
  private boolean ended;
  private boolean permissionsReady;

  public LegacyVideoCallSession(@NonNull Context context, @NonNull Intent intent) {
    this.context = context.getApplicationContext();
    source = new Intent(intent);
    callId = value(intent, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALL_ID);
    chatId = value(intent, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALL_CHAT_ID);
    incoming = !value(intent, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_SDP_OFFER).isEmpty();
    state = CallSessionState.initial(incoming, true);
    CallAudioRouter.apply(this.context, true);
    controller = new VideoCallController(this.context, ChatRepository.getInstance(context), this,
        callId, chatId, LoginStateManager.getInstance().getUID(context),
        value(intent, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALLER_ID),
        value(intent, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_SDP_OFFER),
        LoginStateManager.getInstance().getENC(context), APIAuth.MEDIA_WS_URL);
  }

  public void onPermissionsReady() {
    if (permissionsReady) return;
    permissionsReady = true;
    controller.onPermissionsReady();
  }

  public void attachLocalSurface(@Nullable Surface surface) {
    controller.attachLocalSurface(surface);
  }
  public void attachRemoteSurface(@Nullable Surface surface) {
    controller.attachRemoteSurface(surface);
  }
  public void setDisplayRotation(int degrees) { controller.setDisplayRotation(degrees); }
  public void onForeground() { controller.onResume(); }
  public void onBackground() { controller.onPause(); }
  public void flipCamera() { controller.flipCamera(); }

  @NonNull @Override public String callId() { return callId; }
  @NonNull @Override public String chatId() { return chatId; }
  @NonNull @Override public String engine() { return CallEngineToggle.LEGACY; }
  @NonNull @Override public String mediaType() { return "video"; }
  @Override public boolean incoming() { return incoming; }
  @NonNull @Override public CallSessionState state() { return state; }
  @NonNull @Override public Intent sourceIntent() { return new Intent(source); }
  @Override public void addObserver(@NonNull Observer value) {
    observers.add(value);
    value.onSessionChanged(this, state);
  }
  @Override public void removeObserver(@NonNull Observer value) { observers.remove(value); }
  @Override public void accept() { controller.accept(); }
  @Override public void reject() { controller.reject(); }
  @Override public void setHeld(boolean held, @NonNull Runnable completion) {
    controller.setHeld(held, () -> {
      controller.setManualHeld(held);
      publish(state.withPhase(held ? CallSessionState.Phase.HELD
          : CallSessionState.Phase.CONNECTED, held ? "On hold" : "Call resumed"));
      completion.run();
    });
  }
  @Override public void setMuted(boolean muted) { controller.setMuted(muted); }
  @Override public void setSpeakerEnabled(boolean enabled) {
    if (!CallAudioRouter.apply(context, enabled)) return;
    publish(new CallSessionState(state.phase, state.status, state.muted, enabled,
        state.cameraEnabled, state.peerMuted, state.peerCameraEnabled,
        state.connectedAtMs));
  }
  @Override public void setCameraEnabled(boolean enabled) {
    controller.setCameraEnabled(enabled);
  }
  @Override public void end(@NonNull String reason) { controller.hangup(); }
  @Override public void release() {
    if (!ended) controller.terminate(
        VideoCallController.TerminationReason.ACTIVITY_DESTROYED, false);
    controller.attachLocalSurface(null);
    controller.attachRemoteSurface(null);
    CallAudioRouter.reset(context);
    observers.clear();
  }

  @Override public void onState(VideoCallController.CallState value,
      VideoCallController.ChannelState signaling, VideoCallController.ChannelState audio,
      VideoCallController.ChannelState video, String status) {
    CallSessionState.Phase phase;
    switch (value) {
      case RINGING: phase = CallSessionState.Phase.RINGING; break;
      case CALLING: phase = CallSessionState.Phase.CALLING; break;
      case CONNECTING: phase = CallSessionState.Phase.CONNECTING; break;
      case CONNECTED: phase = CallSessionState.Phase.CONNECTED; break;
      case ENDED: case ENDING: phase = CallSessionState.Phase.ENDED; break;
      default: phase = state.phase;
    }
    long connectedAt = phase == CallSessionState.Phase.CONNECTED && state.connectedAtMs == 0L
        ? System.currentTimeMillis() : state.connectedAtMs;
    publish(new CallSessionState(phase, status, controller.isMuted(),
        state.speakerEnabled, controller.isCameraEnabled(), state.peerMuted,
        state.peerCameraEnabled, connectedAt));
  }
  @Override public void onElapsed(String elapsed) {
    publish(state.withPhase(state.phase, elapsed));
  }
  @Override public void onRemoteMuted(boolean muted) {
    publish(new CallSessionState(state.phase, state.status, state.muted,
        state.speakerEnabled, state.cameraEnabled, muted,
        state.peerCameraEnabled, state.connectedAtMs));
  }
  @Override public void onRemoteCameraEnabled(boolean enabled) {
    publish(new CallSessionState(state.phase, state.status, state.muted,
        state.speakerEnabled, state.cameraEnabled, state.peerMuted, enabled,
        state.connectedAtMs));
  }
  @Override public void onCameraEnabled(boolean enabled) {
    publish(new CallSessionState(state.phase, state.status, state.muted,
        state.speakerEnabled, enabled, state.peerMuted, state.peerCameraEnabled,
        state.connectedAtMs));
  }
  @Override public void onFinished(VideoCallController.TerminationReason reason, String message) {
    ended = true;
    publish(state.withPhase(CallSessionState.Phase.ENDED, message));
    for (Observer current : observers)
      current.onSessionEnded(this, reason.name().toLowerCase());
  }
  @Override public void onError(String message) {
    publish(state.withPhase(CallSessionState.Phase.FAILED, message));
  }

  private void publish(CallSessionState value) {
    state = value;
    for (Observer current : observers) context.getMainExecutor().execute(
        () -> current.onSessionChanged(this, value));
  }
  private static String value(Intent intent, String key) {
    String result = intent.getStringExtra(key);
    return result == null ? "" : result.trim();
  }
}
