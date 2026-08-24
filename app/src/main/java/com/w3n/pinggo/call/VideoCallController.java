package com.w3n.pinggo.call;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.Utils.JsonParserUtil;
import com.w3n.pinggo.call.video.PingGoVideoCallClient;
import com.w3n.pinggo.data.repository.ChatRepository;
import java.util.Locale;

/** Owns one video-call session. UI code only renders callbacks and forwards actions. */
public final class VideoCallController {
  public enum CallState { IDLE, CALLING, RINGING, CONNECTING, CONNECTED, ENDING, ENDED }
  public enum ChannelState { IDLE, CONNECTING, CONNECTED, RECONNECTING, FAILED, CLOSED }
  public enum TerminationReason {
    LOCAL_HANGUP, REMOTE_HANGUP, REJECTED, NO_ANSWER, MEDIA_FAILURE,
    SIGNALING_FAILURE, PERMISSION_DENIED, ACTIVITY_DESTROYED
  }
  public interface Listener {
    void onState(CallState state, ChannelState signaling, ChannelState audio,
        ChannelState video, String status);
    void onElapsed(String elapsed);
    void onRemoteMuted(boolean muted);
    void onRemoteCameraEnabled(boolean enabled);
    void onCameraEnabled(boolean enabled);
    void onFinished(TerminationReason reason, String message);
    void onError(String message);
  }

  private static final String TAG = "PingGoVideoSession";
  private static final long MEDIA_RECONNECT_WINDOW_MS = 10_000L;
  private static final long MEDIA_RETRY_MS = 1_500L;
  private final Context context;
  private final ChatRepository repository;
  private final Listener listener;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final String callId, chatId, localId, remoteId, offer, credential, mediaUrl;
  private final boolean incoming;
  private CallState state = CallState.IDLE;
  private ChannelState signaling = ChannelState.IDLE, audio = ChannelState.IDLE,
      video = ChannelState.IDLE;
  private WebRTCCallClient audioClient;
  private PingGoVideoCallClient videoClient;
  private Surface localSurface, remoteSurface;
  private boolean resumed, muted, remoteMuted, cameraEnabled = true, remoteCameraEnabled = true;
  private long connectedElapsedAt, connectedServerAt, mediaReconnectDeadline;
  private boolean terminated;
  private final ChatRepository.CallEventListener preAcceptListener;

  public VideoCallController(Context context, ChatRepository repository, Listener listener,
      String callId, String chatId, String localId, String remoteId, String offer,
      String credential, String mediaUrl) {
    this.context = context.getApplicationContext(); this.repository = repository;
    this.listener = listener; this.callId = clean(callId); this.chatId = clean(chatId);
    this.localId = clean(localId); this.remoteId = clean(remoteId); this.offer = clean(offer);
    this.credential = clean(credential); this.mediaUrl = clean(mediaUrl);
    preAcceptListener = this::onPreAcceptEvent;
    incoming = !this.offer.isEmpty();
    state = incoming ? CallState.RINGING : CallState.CALLING;
    signaling = ChannelState.CONNECTED;
    if (incoming) { repository.setCallEventListener(preAcceptListener); sendControl("call_ringing"); }
    publish(incoming ? "Incoming video call" : "Calling…");
  }

  public boolean isIncomingUnanswered() { return incoming && state == CallState.RINGING; }
  public boolean isConnected() { return state == CallState.CONNECTED; }
  public boolean isMuted() { return muted; }
  public boolean isCameraEnabled() { return cameraEnabled; }
  public void onPermissionsReady() { if (!incoming) startAudio(); }
  public void accept() {
    if (state != CallState.RINGING || terminated) return;
    state = CallState.CONNECTING; publish("Connecting…"); startAudio();
  }
  public void reject() {
    if (state != CallState.RINGING || terminated) return;
    sendControl("call_reject"); terminate(TerminationReason.REJECTED, false);
  }
  public void hangup() { terminate(TerminationReason.LOCAL_HANGUP, true); }
  public void permissionDenied() { terminate(TerminationReason.PERMISSION_DENIED, incoming); }

  public void onResume() { resumed = true; if (videoClient != null) videoClient.onResume(); }
  public void onPause() { resumed = false; if (videoClient != null) videoClient.onPause(); }
  public void attachLocalSurface(Surface value) {
    localSurface = value; if (videoClient != null) videoClient.attachLocalSurface(value);
  }
  public void attachRemoteSurface(Surface value) {
    remoteSurface = value; if (videoClient != null) videoClient.attachRemoteSurface(value);
  }
  public void setDisplayRotation(int degrees) {
    if (videoClient != null) videoClient.setDisplayRotation(degrees);
  }
  public void flipCamera() { if (videoClient != null && state == CallState.CONNECTED) videoClient.switchCamera(); }
  public void toggleCamera() {
    if (videoClient == null || state != CallState.CONNECTED) return;
    cameraEnabled = !cameraEnabled; videoClient.setCameraEnabled(cameraEnabled);
    listener.onCameraEnabled(cameraEnabled);
  }
  public void toggleMute() {
    if (audioClient == null || state != CallState.CONNECTED) return;
    muted = !muted; audioClient.setMuted(muted); listener.onState(state, signaling, audio, video,
        formatElapsed());
  }

  private void startAudio() {
    if (terminated || audio != ChannelState.IDLE) return;
    state = CallState.CONNECTING; audio = ChannelState.CONNECTING; publish("Connecting audio…");
    audioClient = new WebRTCCallClient(context, repository, audioListener);
    if (incoming) audioClient.startIncoming(callId, chatId, localId, remoteId, offer, "video");
    else audioClient.startOutgoing(callId, chatId, localId, remoteId, "video");
  }

  private final WebRTCCallClient.Listener audioListener = new WebRTCCallClient.Listener() {
    @Override public void onState(String value) {
      handler.post(() -> {
        if (terminated) return;
        if ("Reconnecting…".equals(value)) signaling = ChannelState.RECONNECTING;
        else signaling = ChannelState.CONNECTED;
        if ("Connected".equals(value)) {
          audio = ChannelState.CONNECTED;
          if (muted && audioClient != null) audioClient.setMuted(true);
          startVideo(false);
        }
        publish(value);
      });
    }
    @Override public void onRemoteMuteChanged(boolean value) {
      remoteMuted = value; handler.post(() -> listener.onRemoteMuted(value));
    }
    @Override public void onServerConnectedAt(long value) { connectedServerAt = value; }
    @Override public void onSignalingConnectionChanged(boolean connected) {
      handler.post(() -> {
        if (terminated) return;
        signaling = connected ? ChannelState.CONNECTED : ChannelState.RECONNECTING;
        if (connected && muted && audioClient != null) audioClient.setMuted(true);
        publish(connected ? (state == CallState.CONNECTED ? formatElapsed() : "Connecting…")
            : "Reconnecting signaling…");
      });
    }
    @Override public void onEnded(String reason) {
      handler.post(() -> terminate(reasonToTermination(reason), false));
    }
    @Override public void onError(String message) {
      handler.post(() -> { audio = ChannelState.FAILED; terminate(
          TerminationReason.SIGNALING_FAILURE, true); });
    }
  };

  private void startVideo(boolean reconnecting) {
    if (terminated || audio != ChannelState.CONNECTED) return;
    if (videoClient != null) videoClient.release(reconnecting ? "reconnect" : "replace");
    video = reconnecting ? ChannelState.RECONNECTING : ChannelState.CONNECTING;
    publish(reconnecting ? "Reconnecting video…" : "Connecting video…");
    videoClient = new PingGoVideoCallClient(context, videoListener);
    videoClient.attachLocalSurface(localSurface); videoClient.attachRemoteSurface(remoteSurface);
    if (resumed) videoClient.onResume();
    videoClient.start(callId, localId, credential, mediaUrl);
  }

  private final PingGoVideoCallClient.Listener videoListener = new PingGoVideoCallClient.Listener() {
    @Override public void onState(String value) { handler.post(() -> { if (!terminated) publish(value); }); }
    @Override public void onConnected() { handler.post(() -> {
      if (terminated) return;
      video = ChannelState.CONNECTED; mediaReconnectDeadline = 0;
      if (videoClient != null) videoClient.setCameraEnabled(cameraEnabled);
      state = CallState.CONNECTED;
      if (connectedElapsedAt == 0) connectedElapsedAt = SystemClock.elapsedRealtime();
      publish(formatElapsed()); handler.removeCallbacks(timer); handler.post(timer);
    }); }
    @Override public void onRemoteFrame(int width, int height) {}
    @Override public void onRemoteCameraChanged(boolean enabled) {
      remoteCameraEnabled = enabled; handler.post(() -> listener.onRemoteCameraEnabled(enabled));
    }
    @Override public void onEnded(String reason) { handler.post(VideoCallController.this::retryVideo); }
    @Override public void onError(String message) { handler.post(VideoCallController.this::retryVideo); }
  };

  private void retryVideo() {
    if (terminated || audio != ChannelState.CONNECTED) return;
    long now = SystemClock.elapsedRealtime();
    if (mediaReconnectDeadline == 0) mediaReconnectDeadline = now + MEDIA_RECONNECT_WINDOW_MS;
    if (now >= mediaReconnectDeadline) {
      video = ChannelState.FAILED; terminate(TerminationReason.MEDIA_FAILURE, true); return;
    }
    video = ChannelState.RECONNECTING; publish("Reconnecting video…");
    handler.removeCallbacks(mediaRetry); handler.postDelayed(mediaRetry, MEDIA_RETRY_MS);
  }
  private final Runnable mediaRetry = () -> startVideo(true);
  private final Runnable timer = new Runnable() {
    @Override public void run() {
      if (state != CallState.CONNECTED || terminated) return;
      listener.onElapsed(formatElapsed()); handler.postDelayed(this, 1000L);
    }
  };

  public void terminate(TerminationReason reason, boolean notifyServer) {
    if (terminated) return;
    terminated = true; state = CallState.ENDING; publish("Ending…");
    handler.removeCallbacks(timer); handler.removeCallbacks(mediaRetry);
    if (audioClient != null) { if (notifyServer) audioClient.endCall(reason.name().toLowerCase(Locale.US));
      else audioClient.close(false); }
    audioClient = null; audio = ChannelState.CLOSED;
    if (videoClient != null) videoClient.release("terminate:" + reason); videoClient = null;
    video = ChannelState.CLOSED; signaling = ChannelState.CONNECTED;
    repository.setCallEventListener(null); state = CallState.ENDED;
    Log.d(TAG, "terminated callId=" + callId + " reason=" + reason + " notifyServer=" + notifyServer);
    listener.onFinished(reason, terminationMessage(reason));
  }
  public void destroy() {
    if (!terminated) terminate(TerminationReason.ACTIVITY_DESTROYED,
        state == CallState.CONNECTED || state == CallState.CONNECTING || state == CallState.CALLING);
  }

  private void onPreAcceptEvent(JsonObject event) {
    if (terminated || !callId.equals(JsonParserUtil.getString(event, "callId"))) return;
    String type = JsonParserUtil.getString(event, "type");
    if ("call_end".equals(type) || "call_no_answer".equals(type)) handler.post(() ->
        terminate("call_no_answer".equals(type) ? TerminationReason.NO_ANSWER :
            TerminationReason.REMOTE_HANGUP, false));
  }
  private void sendControl(String type) {
    JsonObject event = new JsonObject(); event.addProperty("type", type);
    event.addProperty("callId", callId); event.addProperty("senderId", localId);
    event.addProperty("receiverId", remoteId); event.addProperty("mediaType", "video");
    repository.sendCallEvent(event);
  }
  private void publish(String status) { listener.onState(state, signaling, audio, video, status); }
  private String formatElapsed() {
    long seconds = connectedServerAt > 0
        ? Math.max(0, System.currentTimeMillis() - connectedServerAt) / 1000
        : connectedElapsedAt == 0 ? 0 :
          Math.max(0, SystemClock.elapsedRealtime() - connectedElapsedAt) / 1000;
    return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
  }
  private static TerminationReason reasonToTermination(String value) {
    if ("reject".equals(value) || "busy".equals(value)) return TerminationReason.REJECTED;
    if ("no_answer".equals(value)) return TerminationReason.NO_ANSWER;
    return TerminationReason.REMOTE_HANGUP;
  }
  private static String terminationMessage(TerminationReason reason) {
    switch (reason) {
      case MEDIA_FAILURE: return "Video connection failed.";
      case SIGNALING_FAILURE: return "Call connection failed.";
      case NO_ANSWER: return "No answer.";
      case REJECTED: return "Call rejected.";
      default: return "Call ended.";
    }
  }
  private static String clean(String value) { return value == null ? "" : value.trim(); }
}
