package com.w3n.pinggo.call;

import android.content.Context;
import android.util.Log;
import android.util.Base64;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.Utils.JsonParserUtil;
import com.w3n.pinggo.data.repository.ChatRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.audio.JavaAudioDeviceModule;

/** Owns one WebRTC audio session and exchanges SDP/ICE through ChatRepository. */
public final class WebRTCCallClient implements ChatRepository.CallEventListener {
  private static final String TAG = "PingGoRtcSignal";
  public interface Listener {
    void onState(String state);
    void onRemoteMuteChanged(boolean muted);
    default void onServerConnectedAt(long connectedAtMs) {}
    default void onSignalingConnectionChanged(boolean connected) {}
    void onEnded(String reason);
    void onError(String message);
  }

  private final Context context;
  private final ChatRepository repository;
  private final Listener listener;
  private final ExecutorService rtcThread = Executors.newSingleThreadExecutor();
  private final List<IceCandidate> pendingCandidates = new ArrayList<>();
  private PeerConnectionFactory factory;
  private PeerConnection peerConnection;
  private JavaAudioDeviceModule audioDeviceModule;
  private AudioSource audioSource;
  private AudioTrack audioTrack;
  private String callId, chatId, localUserId, remoteUserId, mediaType = "audio";
  private JsonObject lastLocalDescriptionEvent;
  private boolean remoteDescriptionSet, ended;

  public WebRTCCallClient(Context context, ChatRepository repository, Listener listener) {
    this.context = context.getApplicationContext();
    this.repository = repository;
    this.listener = listener;
  }

  public void startOutgoing(String chatId, String localUserId, String remoteUserId) {
    startOutgoing(UUID.randomUUID().toString(), chatId, localUserId, remoteUserId, "audio");
  }

  public void startOutgoing(String callId, String chatId, String localUserId,
                            String remoteUserId, String mediaType) {
    this.callId = normalizeText(callId);
    this.chatId = normalizeText(chatId);
    this.localUserId = normalize(localUserId);
    this.remoteUserId = normalize(remoteUserId);
    this.mediaType = "video".equals(mediaType) ? "video" : "audio";
    Log.d(TAG, "startOutgoing callId=" + this.callId + " mediaType=" + this.mediaType);
    repository.setCallEventListener(this);
    rtcThread.execute(() -> {
      if (!initialize()) return;
      notifyState("Calling…");
      peerConnection.createOffer(new SimpleSdpObserver() {
        @Override public void onCreateSuccess(SessionDescription description) {
          setLocalAndSend(description, "call_invite");
        }
        @Override public void onCreateFailure(String error) { fail(error); }
      }, audioConstraints());
    });
  }

  public void startIncoming(String callId, String chatId, String localUserId, String remoteUserId,
                            String offer) {
    startIncoming(callId, chatId, localUserId, remoteUserId, offer, "audio");
  }

  public void startIncoming(String callId, String chatId, String localUserId, String remoteUserId,
                            String offer, String mediaType) {
    this.callId = callId;
    this.chatId = normalizeText(chatId);
    this.localUserId = normalize(localUserId);
    this.remoteUserId = normalize(remoteUserId);
    this.mediaType = "video".equals(mediaType) ? "video" : "audio";
    offer = normalizeSdp(offer);
    final String normalizedOffer = offer;
    Log.d(TAG, "startIncoming callId=" + this.callId + " mediaType=" + this.mediaType
        + " offerLength=" + normalizedOffer.length() + " offerHash=" + sdpHash(normalizedOffer));
    repository.setCallEventListener(this);
    rtcThread.execute(() -> {
      if (!initialize()) return;
      notifyState("Connecting…");
      SessionDescription remote = new SessionDescription(SessionDescription.Type.OFFER, normalizedOffer);
      peerConnection.setRemoteDescription(new SimpleSdpObserver() {
        @Override public void onSetSuccess() {
          Log.d(TAG, "remoteOfferSet callId=" + callId);
          remoteDescriptionSet = true;
          flushCandidates();
          peerConnection.createAnswer(new SimpleSdpObserver() {
            @Override public void onCreateSuccess(SessionDescription description) {
              Log.d(TAG, "answerCreated callId=" + callId
                  + " descriptionLength=" + description.description.length());
              setLocalAndSend(description, "call_answer");
            }
            @Override public void onCreateFailure(String error) { fail(error); }
          }, audioConstraints());
        }
        @Override public void onSetFailure(String error) { fail(error); }
      }, remote);
    });
  }

  private boolean initialize() {
    try {
      PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context)
          .setEnableInternalTracer(false).createInitializationOptions());
      audioDeviceModule = JavaAudioDeviceModule.builder(context).createAudioDeviceModule();
      factory = PeerConnectionFactory.builder().setAudioDeviceModule(audioDeviceModule).createPeerConnectionFactory();
      PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(Collections.singletonList(
          PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()));
      config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
      peerConnection = factory.createPeerConnection(config, observer);
      if (peerConnection == null) throw new IllegalStateException("Could not create PeerConnection.");
      audioSource = factory.createAudioSource(audioConstraints());
      audioTrack = factory.createAudioTrack("pinggo_audio", audioSource);
      audioTrack.setEnabled(true);
      peerConnection.addTrack(audioTrack, Collections.singletonList("pinggo_stream"));
      return true;
    } catch (Throwable error) {
      fail(error.getMessage() == null ? "WebRTC initialization failed." : error.getMessage());
      return false;
    }
  }

  private final PeerConnection.Observer observer = new PeerConnection.Observer() {
    @Override public void onIceCandidate(IceCandidate candidate) {
      JsonObject value = new JsonObject();
      value.addProperty("sdpMid", candidate.sdpMid);
      value.addProperty("sdpMLineIndex", candidate.sdpMLineIndex);
      value.addProperty("candidate", candidate.sdp);
      send("ice_candidate", "candidate", value);
    }
    @Override public void onConnectionChange(PeerConnection.PeerConnectionState state) {
      if (state == PeerConnection.PeerConnectionState.CONNECTED) notifyState("Connected");
      else if (state == PeerConnection.PeerConnectionState.DISCONNECTED) notifyState("Reconnecting…");
      else if (state == PeerConnection.PeerConnectionState.FAILED) fail("Voice connection failed.");
      else if (state == PeerConnection.PeerConnectionState.CLOSED) notifyState("Ended");
    }
    @Override public void onSignalingChange(PeerConnection.SignalingState state) {}
    @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {}
    @Override public void onIceConnectionReceivingChange(boolean receiving) {}
    @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) {}
    @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
    @Override public void onAddStream(MediaStream stream) {}
    @Override public void onRemoveStream(MediaStream stream) {}
    @Override public void onDataChannel(org.webrtc.DataChannel channel) {}
    @Override public void onRenegotiationNeeded() {}
    @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {}
  };

  private void setLocalAndSend(SessionDescription description, String eventType) {
    peerConnection.setLocalDescription(new SimpleSdpObserver() {
      @Override public void onSetSuccess() {
        Log.d(TAG, "localDescriptionSet callId=" + callId + " eventType=" + eventType);
        JsonObject sdp = new JsonObject();
        sdp.addProperty("type", description.type.canonicalForm());
        String normalizedDescription = normalizeSdp(description.description);
        sdp.addProperty("description", normalizedDescription);
        sdp.addProperty("descriptionBase64", Base64.encodeToString(
            normalizedDescription.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        Log.d(TAG, "sendSdp callId=" + callId + " eventType=" + eventType
            + " length=" + normalizedDescription.length()
            + " hash=" + sdpHash(normalizedDescription));
        send(eventType, "sdp", sdp);
      }
      @Override public void onSetFailure(String error) { fail(error); }
    }, description);
  }

  @Override public void onCallEvent(JsonObject event) {
    String type = JsonParserUtil.getString(event, "type");
    Log.d(TAG, "event callId=" + callId + " type=" + type
        + " eventCallId=" + JsonParserUtil.getString(event, "callId"));
    if ("call_socket_disconnected".equals(type)) {
      listener.onSignalingConnectionChanged(false);
      notifyState("Reconnecting…");
      return;
    }
    if ("call_socket_reconnected".equals(type)) {
      listener.onSignalingConnectionChanged(true);
      if (lastLocalDescriptionEvent != null) {
        repository.sendCallEvent(lastLocalDescriptionEvent.deepCopy());
      }
      return;
    }
    if (!callId.equals(JsonParserUtil.getString(event, "callId"))) return;
    if ("call_answer_ack".equals(type)) {
      long serverTime = event.has("serverTime") ? event.get("serverTime").getAsLong() : 0L;
      if (serverTime > 0) listener.onServerConnectedAt(serverTime);
      return;
    }
    if ("call_ringing".equals(type)) notifyState("Ringing…");
    else if ("call_answer".equals(type)) {
      long serverTime = event.has("serverTime") ? event.get("serverTime").getAsLong() : 0L;
      if (serverTime > 0) listener.onServerConnectedAt(serverTime);
      applyAnswer(event);
    }
    else if ("ice_candidate".equals(type)) addCandidate(event);
    else if ("call_mute".equals(type)) {
      boolean remoteMuted = event.has("muted") && event.get("muted").getAsBoolean();
      context.getMainExecutor().execute(() -> listener.onRemoteMuteChanged(remoteMuted));
    }
    else if ("call_reject".equals(type) || "call_busy".equals(type)
        || "call_unavailable".equals(type) || "call_no_answer".equals(type)
        || "call_end".equals(type)) {
      close(false);
      listener.onEnded(type.replace("call_", ""));
    } else if ("call_failed".equals(type)) fail(JsonParserUtil.getString(event, "message"));
  }

  private void applyAnswer(JsonObject event) {
    JsonObject sdp = event.has("sdp") && event.get("sdp").isJsonObject() ? event.getAsJsonObject("sdp") : null;
    if (sdp == null) { fail("Call answer is missing SDP."); return; }
    String answerText = decodeSdp(sdp);
    Log.d(TAG, "applyAnswer callId=" + callId + " length=" + answerText.length()
        + " hash=" + sdpHash(answerText));
    SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, answerText);
    rtcThread.execute(() -> peerConnection.setRemoteDescription(new SimpleSdpObserver() {
      @Override public void onSetSuccess() { remoteDescriptionSet = true; flushCandidates(); notifyState("Connecting…"); }
      @Override public void onSetFailure(String error) { fail(error); }
    }, answer));
  }

  private void addCandidate(JsonObject event) {
    JsonObject value = event.has("candidate") && event.get("candidate").isJsonObject()
        ? event.getAsJsonObject("candidate") : null;
    if (value == null) return;
    IceCandidate candidate = new IceCandidate(JsonParserUtil.getString(value, "sdpMid"),
        value.has("sdpMLineIndex") ? value.get("sdpMLineIndex").getAsInt() : 0,
        JsonParserUtil.getString(value, "candidate"));
    rtcThread.execute(() -> { if (remoteDescriptionSet) peerConnection.addIceCandidate(candidate); else pendingCandidates.add(candidate); });
  }
  private void flushCandidates() {
    for (IceCandidate candidate : pendingCandidates) peerConnection.addIceCandidate(candidate);
    pendingCandidates.clear();
  }
  public void setMuted(boolean muted) {
    if (audioTrack != null) audioTrack.setEnabled(!muted);
    JsonObject event = new JsonObject();
    event.addProperty("type", "call_mute");
    event.addProperty("callId", callId);
    event.addProperty("senderId", localUserId);
    event.addProperty("receiverId", remoteUserId);
    event.addProperty("muted", muted);
    repository.sendCallEvent(event);
  }
  public void endCall() { endCall("hangup"); }
  public void endCall(String reason) {
    JsonObject event = new JsonObject();
    event.addProperty("type", "call_end"); event.addProperty("callId", callId);
    event.addProperty("mediaType", mediaType); event.addProperty("senderId", localUserId);
    event.addProperty("receiverId", remoteUserId); event.addProperty("reason", reason);
    if (!chatId.isEmpty()) event.addProperty("chatId", chatId);
    boolean accepted = repository.sendCallEvent(event);
    Log.d(TAG, "send callId=" + callId + " type=call_end reason=" + reason
        + " acceptedByClient=" + accepted);
    close(false);
  }
  private void send(String type, String key, JsonObject value) {
    JsonObject event = new JsonObject();
    event.addProperty("type", type); event.addProperty("callId", callId);
    event.addProperty("mediaType", mediaType);
    event.addProperty("senderId", localUserId); event.addProperty("receiverId", remoteUserId);
    if (!chatId.isEmpty()) event.addProperty("chatId", chatId);
    if (key != null && value != null) event.add(key, value);
    if ("call_answer".equals(type)) {
      lastLocalDescriptionEvent = event.deepCopy();
    }
    boolean accepted = repository.sendCallEvent(event);
    Log.d(TAG, "send callId=" + callId + " type=" + type + " acceptedByClient=" + accepted);
  }
  private MediaConstraints audioConstraints() {
    MediaConstraints constraints = new MediaConstraints();
    constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
    constraints.optional.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
    constraints.optional.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
    constraints.optional.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
    return constraints;
  }
  private void notifyState(String state) { context.getMainExecutor().execute(() -> listener.onState(state)); }
  private void fail(String error) {
    Log.e(TAG, "failure callId=" + callId + " message=" + error);
    context.getMainExecutor().execute(() -> listener.onError(error)); close(false);
  }
  public void close(boolean notifyRemote) {
    if (ended) return; ended = true;
    Log.d(TAG, "close callId=" + callId + " notifyRemote=" + notifyRemote);
    if (notifyRemote) send("call_end", null, null);
    repository.setCallEventListener(null);
    rtcThread.execute(() -> {
      if (peerConnection != null) { peerConnection.close(); peerConnection.dispose(); }
      if (audioTrack != null) audioTrack.dispose();
      if (audioSource != null) audioSource.dispose();
      if (factory != null) factory.dispose();
      if (audioDeviceModule != null) { audioDeviceModule.release(); }
      rtcThread.shutdown();
    });
  }
  private static String normalize(String value) {
    if (value == null) return ""; String result = value.trim();
    if (result.startsWith("<plus>")) result = result.substring(6);
    return result.startsWith("+") ? result.substring(1) : result;
  }
  private static String normalizeText(String value) {
    return value == null ? "" : value.trim();
  }
  public static String decodeSdp(JsonObject sdp) {
    String encoded = JsonParserUtil.getString(sdp, "descriptionBase64");
    if (!encoded.isEmpty()) {
      try {
        return normalizeSdp(new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8));
      } catch (IllegalArgumentException error) {
        Log.e(TAG, "Invalid SDP Base64", error);
      }
    }
    return normalizeSdp(JsonParserUtil.getString(sdp, "description"));
  }
  private static String normalizeSdp(String value) {
    if (value == null) return "";
    String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
    while (normalized.contains("\n\n")) normalized = normalized.replace("\n\n", "\n");
    normalized = normalized.replace("\n", "\r\n");
    return normalized.endsWith("\r\n") ? normalized : normalized + "\r\n";
  }
  private static String sdpHash(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (int i = 0; i < 6; i++) result.append(String.format("%02x", digest[i]));
      return result.toString();
    } catch (Exception error) {
      return "unavailable";
    }
  }
  private static class SimpleSdpObserver implements SdpObserver {
    @Override public void onCreateSuccess(SessionDescription sdp) {}
    @Override public void onSetSuccess() {}
    @Override public void onCreateFailure(String error) {}
    @Override public void onSetFailure(String error) {}
  }
}
