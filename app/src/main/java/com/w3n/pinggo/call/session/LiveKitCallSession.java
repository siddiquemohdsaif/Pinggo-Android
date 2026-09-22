package com.w3n.pinggo.call.session;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.JsonParserUtil;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.call.CallEngineToggle;
import com.w3n.pinggo.call.LatestMediaState;
import com.w3n.pinggo.call.PingGoLiveKitAudio;
import com.w3n.pinggo.data.repository.ChatRepository;
import io.livekit.android.ConnectOptions;
import io.livekit.android.LiveKit;
import io.livekit.android.RoomOptions;
import io.livekit.android.room.Room;
import io.livekit.android.room.participant.VideoTrackPublishOptions;
import io.livekit.android.room.track.CameraPosition;
import io.livekit.android.room.track.LocalVideoTrack;
import io.livekit.android.room.track.LocalVideoTrackOptions;
import io.livekit.android.room.track.TrackPublication;
import io.livekit.android.room.track.VideoTrack;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import kotlin.ResultKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;

/** Service-owned LiveKit direct, conference, and group call. */
public final class LiveKitCallSession implements CallSession, ChatRepository.CallEventListener {
  private static final long UNANSWERED_TIMEOUT_MS = 50_000L;
  private static final String TILE_SWAP_TAG = "PingGoTileSwap";
  private static final String INVITE_TILE_TAG = "PingGoInviteTile";
  private final Context context;
  private final Intent source;
  private final ChatRepository repository;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ExecutorService mediaExecutor = Executors.newSingleThreadExecutor();
  private final String callId, chatId, peerId, mediaType;
  private final boolean incoming;
  private volatile boolean conference;
  private volatile boolean multiPartyCall;
  private final ArrayList<String> participantIds;
  private final ArrayList<String> tileOrder = new ArrayList<>();
  private final Set<String> departedParticipantIds = new LinkedHashSet<>();
  private final Set<String> pendingParticipantIds = new LinkedHashSet<>();
  private final Room room;
  private volatile LocalVideoTrack localPreviewTrack;
  private volatile CallSessionState state;
  private final CopyOnWriteArraySet<Observer> observers = new CopyOnWriteArraySet<>();
  private final LatestMediaState micState = new LatestMediaState(false);
  private final LatestMediaState cameraState = new LatestMediaState(true);
  private boolean started, ended, everHadRemoteParticipant;
  private boolean conferenceInvitePending, conferenceInviteCreated;
  private final Runnable unansweredTimeout = this::handleUnansweredTimeout;
  private final Runnable finishIfStillAlone = this::handleFinishIfStillAlone;

  private void handleUnansweredTimeout() {
    if (ended || everHadRemoteParticipant) return;
    if (!incoming) sendControl("call_end");
    endLocal("no_answer");
  }

  private void handleFinishIfStillAlone() {
    if (ended || !everHadRemoteParticipant || !room.getRemoteParticipants().isEmpty()) return;
    sendControl(multiPartyCall ? "call_leave" : "call_end");
    endLocal("remote_left");
  }

  private final Runnable participantPoll = new Runnable() {
    @Override public void run() {
      if (ended) return;
      int count = room.getRemoteParticipants().size();
      handler.removeCallbacks(finishIfStillAlone);
      if (count > 0) {
        everHadRemoteParticipant = true;
        handler.removeCallbacks(unansweredTimeout);
      } else if (everHadRemoteParticipant) {
        handler.postDelayed(finishIfStillAlone, 100L);
      }
      conference = count > 1;
      if (count > 0 && state.phase != CallSessionState.Phase.HELD
          && !state.status.contains("on hold")) {
        long connectedAt = state.connectedAtMs == 0L
            ? System.currentTimeMillis() : state.connectedAtMs;
        publish(new CallSessionState(CallSessionState.Phase.CONNECTED, "Connected",
            state.muted, state.speakerEnabled, state.cameraEnabled, state.peerMuted,
            state.peerCameraEnabled, connectedAt));
      }
      handler.postDelayed(this, 500L);
    }
  };

  public LiveKitCallSession(@NonNull Context context, @NonNull Intent intent) {
    this.context = context.getApplicationContext();
    source = new Intent(intent);
    callId = value(intent, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALL_ID);
    chatId = value(intent, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALL_CHAT_ID);
    peerId = value(intent, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALLER_ID);
    mediaType = "video".equals(value(intent, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_MEDIA_TYPE))
        ? "video" : "audio";
    incoming = intent.getBooleanExtra(com.w3n.pinggo.call.session.CallActivityContract.EXTRA_INCOMING, false);
    conference = intent.getBooleanExtra(com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CONFERENCE_CALL, false)
        || chatId.startsWith("grp_");
    multiPartyCall = conference;
    ArrayList<String> ids = intent.getStringArrayListExtra(
        com.w3n.pinggo.call.session.CallActivityContract.EXTRA_PARTICIPANT_IDS);
    participantIds = ids == null ? new ArrayList<>() : new ArrayList<>(ids);
    state = CallSessionState.initial(incoming, "video".equals(mediaType));
    CallAudioRouter.apply(this.context, "video".equals(mediaType));
    repository = ChatRepository.getInstance(context);
    repository.setCallEventListener(this);
    room = LiveKit.INSTANCE.create(this.context, new RoomOptions(),
        PingGoLiveKitAudio.createOverrides());
    if (incoming) {
      sendControl("call_ringing");
      if ("video".equals(mediaType)) prepareLocalPreview();
    } else start();
    handler.postDelayed(unansweredTimeout, UNANSWERED_TIMEOUT_MS);
    handler.post(participantPoll);
  }

  public Room room() { return room; }
  @Nullable public VideoTrack localPreviewTrack() { return localPreviewTrack; }
  public void onPermissionsReady() {
    if ("video".equals(mediaType) && !started) prepareLocalPreview();
  }
  public boolean conference() { return conference; }
  /** True while the conference grid is needed for connected or ringing participants. */
  public boolean showsParticipantGrid() { return conference || !pendingParticipantIds.isEmpty(); }
  public ArrayList<String> participantIds() { return new ArrayList<>(participantIds); }
  public ArrayList<String> pendingParticipantIds() {
    return new ArrayList<>(pendingParticipantIds);
  }
  public boolean participantPending(String participantId) {
    return pendingParticipantIds.contains(normalizedParticipantId(participantId));
  }
  public ArrayList<String> tileOrder() { return new ArrayList<>(tileOrder); }
  public boolean participantDeparted(String participantId) {
    return departedParticipantIds.contains(normalizedParticipantId(participantId));
  }
  public void addParticipants(@NonNull ArrayList<String> ids) {
    if (ids.isEmpty() || ended) return;
    multiPartyCall = true;
    JsonObject event = controlEvent("call_add_participants");
    JsonArray members = new JsonArray();
    for (String id : ids) {
      if (id != null && !id.trim().isEmpty()) {
        String value = id.trim();
        String normalized = normalizedParticipantId(value);
        if (normalized.isEmpty() || participantIds.stream()
            .anyMatch(existing -> normalizedParticipantId(existing).equals(normalized))) continue;
        members.add(value);
        departedParticipantIds.remove(normalized);
        participantIds.add(value);
        pendingParticipantIds.add(normalized);
        Log.i(INVITE_TILE_TAG, "pending_added callId=" + callId
            + " participant=" + normalized + " source=local_invite");
      }
    }
    if (members.size() == 0) return;
    event.add("participantIds", members);
    repository.sendCallEvent(event);
    publish(state.withPhase(state.phase, "Inviting members…"));
  }

  public void updateTileOrder(@NonNull ArrayList<String> orderedIds) {
    if (ended || orderedIds.isEmpty()) return;
    tileOrder.clear();
    JsonArray order = new JsonArray();
    for (String id : orderedIds) {
      String normalized = normalizedParticipantId(id);
      if (normalized.isEmpty() || tileOrder.contains(normalized)) continue;
      tileOrder.add(normalized);
      order.add(normalized);
    }
    JsonObject event = controlEvent("call_tile_order");
    event.add("order", order);
    boolean sent = repository.sendCallEvent(event);
    Log.i(TILE_SWAP_TAG, "signal_send callId=" + callId + " sender="
        + LoginStateManager.getInstance().getUID(context) + " order=" + tileOrder
        + " sent=" + sent);
  }

  @NonNull @Override public String callId() { return callId; }
  @NonNull @Override public String chatId() { return chatId; }
  @NonNull @Override public String engine() { return CallEngineToggle.LIVEKIT; }
  @NonNull @Override public String mediaType() { return mediaType; }
  @Override public boolean incoming() { return incoming; }
  @NonNull @Override public CallSessionState state() { return state; }
  @NonNull @Override public Intent sourceIntent() { return new Intent(source); }
  @Override public void addObserver(@NonNull Observer value) {
    observers.add(value);
    value.onSessionChanged(this, state);
  }
  @Override public void removeObserver(@NonNull Observer value) { observers.remove(value); }
  @Override public void accept() { if (incoming) start(); }
  @Override public void reject() { sendControl("call_reject"); endLocal("rejected"); }

  private void start() {
    if (started || ended) return;
    boolean needsConferenceAck = multiPartyCall && !incoming && !chatId.startsWith("grp_")
        && !conferenceInviteCreated;
    if (needsConferenceAck) {
      if (conferenceInvitePending) return;
      conferenceInvitePending = true;
      publish(state.withPhase(CallSessionState.Phase.CONNECTING, "Starting conference…"));
      sendControl("call_invite");
      return;
    }
    beginAuthorization();
  }

  private void beginAuthorization() {
    if (started || ended) return;
    started = true;
    publish(state.withPhase(CallSessionState.Phase.CONNECTING, "Authorizing…"));
    AppFunctionManager.getInstance().getLiveKitToken(callId, chatId, mediaType,
        new AppFunctionManager.Callback() {
          @Override public void onSuccess(Object value) {
            if (!(value instanceof JsonObject)) { fail("Invalid LiveKit response."); return; }
            JsonObject json = (JsonObject) value;
            connect(JsonParserUtil.getString(json, "serverUrl"),
                JsonParserUtil.getString(json, "participantToken"));
          }
          @Override public void onError(String error) { fail(error); }
        });
  }

  private void connect(String url, String token) {
    publish(state.withPhase(CallSessionState.Phase.CONNECTING, "Connecting…"));
    invoke(room, "connect", new Object[] { url, token, new ConnectOptions() }, ignored -> {
      setMedia("setMicrophoneEnabled", true, () -> { });
      if ("video".equals(mediaType)) publishPreviewOrEnableCamera(() ->
          finishConnectedMediaStart());
      else finishConnectedMediaStart();
    }, error -> fail(error.getMessage()));
  }

  private void finishConnectedMediaStart() {
    if (incoming) sendControl("call_answer");
    else if (!conferenceInviteCreated) sendControl("call_invite");
    handler.removeCallbacks(unansweredTimeout);
    handler.postDelayed(unansweredTimeout, UNANSWERED_TIMEOUT_MS);
  }

  @Override public void setHeld(boolean held, @NonNull Runnable completion) {
    Runnable afterMicrophone = () -> {
      if ("video".equals(mediaType))
        setMedia("setCameraEnabled", !held && state.cameraEnabled,
            () -> finishHold(held, completion),
            () -> finishHoldFailure("Unable to change camera setting.", completion));
      else finishHold(held, completion);
    };
    setMedia("setMicrophoneEnabled", !held && !state.muted, afterMicrophone,
        () -> finishHoldFailure("Unable to change microphone setting.", completion));
  }

  private void finishHold(boolean held, Runnable completion) {
    sendControl(held ? "call_hold" : "call_resume");
    publish(state.withPhase(held ? CallSessionState.Phase.HELD
        : CallSessionState.Phase.CONNECTED, held ? "On hold" : "Call resumed"));
    completion.run();
  }

  private void finishHoldFailure(String message, Runnable completion) {
    failTransient(message);
    completion.run();
  }

  @Override public void setMuted(boolean muted) {
    micState.request(muted);
    applyRequestedMute();
  }
  @Override public void setSpeakerEnabled(boolean enabled) {
    if (!CallAudioRouter.apply(context, enabled)) return;
    publish(new CallSessionState(state.phase, state.status, state.muted, enabled,
        state.cameraEnabled, state.peerMuted, state.peerCameraEnabled, state.connectedAtMs));
  }
  @Override public void setCameraEnabled(boolean enabled) {
    if (!"video".equals(mediaType)) return;
    cameraState.request(enabled);
    applyRequestedCamera();
  }

  public void flipCamera() {
    if (!"video".equals(mediaType) || ended || !state.cameraEnabled) return;
    mediaExecutor.execute(() -> {
      try {
        TrackPublication publication = room.getLocalParticipant()
            .getTrackPublication(io.livekit.android.room.track.Track.Source.CAMERA);
        if (publication == null || !(publication.getTrack() instanceof LocalVideoTrack)) return;
        LocalVideoTrack track = (LocalVideoTrack) publication.getTrack();
        CameraPosition current = track.getOptions().getPosition();
        track.switchCamera(null, current == CameraPosition.FRONT
            ? CameraPosition.BACK : CameraPosition.FRONT);
      } catch (Throwable error) {
        handler.post(() -> failTransient("Unable to switch camera."));
      }
    });
  }
  @Override public void end(@NonNull String reason) {
    JsonObject event = controlEvent(
        multiPartyCall && everHadRemoteParticipant ? "call_leave" : "call_end");
    event.addProperty("reason", reason);
    repository.sendCallEvent(event);
    endLocal(reason);
  }
  @Override public void release() {
    ended = true;
    handler.removeCallbacks(participantPoll);
    handler.removeCallbacks(unansweredTimeout);
    handler.removeCallbacks(finishIfStillAlone);
    repository.clearCallEventListener(this);
    releaseUnpublishedPreview();
    room.disconnect();
    mediaExecutor.shutdown();
    CallAudioRouter.reset(context);
    observers.clear();
  }

  @Override public void onCallEvent(JsonObject event) {
    if (!callId.equals(JsonParserUtil.getString(event, "callId"))) return;
    String type = JsonParserUtil.getString(event, "type");
    if ("call_invite_ack".equals(type) && conferenceInvitePending) {
      conferenceInvitePending = false;
      conferenceInviteCreated = true;
      beginAuthorization();
    } else if ("call_failed".equals(type)) {
      fail(JsonParserUtil.getString(event, "message"));
    } else if (type.endsWith("_ack")
        && "ended".equals(JsonParserUtil.getString(event, "state"))) {
      endLocal("ended");
    } else if ("call_tile_order_ack".equals(type)) {
      Log.i(TILE_SWAP_TAG, "signal_ack callId=" + callId + " state="
          + JsonParserUtil.getString(event, "state") + " serverTime="
          + JsonParserUtil.getLong(event, "serverTime"));
    } else if ("call_answer".equals(type)) {
      String answeredId = normalizedParticipantId(JsonParserUtil.getString(event, "senderId"));
      if (!answeredId.isEmpty() && pendingParticipantIds.remove(answeredId)) {
        Log.i(INVITE_TILE_TAG, "pending_removed callId=" + callId
            + " participant=" + answeredId + " reason=answered");
      }
      handler.removeCallbacks(unansweredTimeout);
      publish(state.withPhase(everHadRemoteParticipant ? state.phase
          : CallSessionState.Phase.CONNECTING,
          everHadRemoteParticipant ? "Participant joined" : "Joining…"));
    } else if ("call_waiting".equals(type))
      publish(state.withPhase(CallSessionState.Phase.RINGING, "Busy on another call"));
    else if ("call_ringing".equals(type) && !state.status.contains("Busy"))
      publish(state.withPhase(CallSessionState.Phase.RINGING, "Ringing…"));
    else if ("call_hold".equals(type) || "call_resume".equals(type)) {
      boolean held = "call_hold".equals(type);
      String affected = JsonParserUtil.getString(event,
          held ? "heldUserId" : "resumedUserId");
      String local = LoginStateManager.getInstance().getUID(context);
      if (!affected.equals(local)) applyPeerHold(held);
    }
    else if ("call_mute".equals(type)) {
      boolean muted = event.has("muted") && event.get("muted").getAsBoolean();
      boolean camera = !event.has("cameraEnabled") || event.get("cameraEnabled").getAsBoolean();
      publish(new CallSessionState(state.phase, state.status, state.muted,
          state.speakerEnabled, state.cameraEnabled, muted, camera, state.connectedAtMs));
    } else if ("call_participants_added".equals(type)) {
      multiPartyCall = true;
      Set<String> serverParticipants = new LinkedHashSet<>();
      String localId = normalizedParticipantId(
          LoginStateManager.getInstance().getUID(context));
      if (event.has("participantIds") && event.get("participantIds").isJsonArray()) {
        for (JsonElement value : event.getAsJsonArray("participantIds")) {
          if (value == null || !value.isJsonPrimitive()) continue;
          String participantId = value.getAsString().trim();
          if (!participantId.isEmpty()) {
            String normalized = normalizedParticipantId(participantId);
            serverParticipants.add(normalized);
            departedParticipantIds.remove(normalized);
            if (!participantIds.contains(participantId)) participantIds.add(participantId);
          }
        }
      }
      if (event.has("addedParticipantIds")
          && event.get("addedParticipantIds").isJsonArray()) {
        for (JsonElement value : event.getAsJsonArray("addedParticipantIds")) {
          if (value == null || !value.isJsonPrimitive()) continue;
          String participantId = normalizedParticipantId(value.getAsString());
          if (participantId.isEmpty() || participantId.equals(localId)) continue;
          pendingParticipantIds.add(participantId);
          Log.i(INVITE_TILE_TAG, "pending_added callId=" + callId
              + " participant=" + participantId + " source=server_confirmed");
        }
        ArrayList<String> rejected = new ArrayList<>();
        for (String pendingId : pendingParticipantIds) {
          if (!pendingId.equals(localId) && !serverParticipants.contains(pendingId))
            rejected.add(pendingId);
        }
        for (String rejectedId : rejected) {
          pendingParticipantIds.remove(rejectedId);
          participantIds.removeIf(value ->
              normalizedParticipantId(value).equals(rejectedId));
          Log.i(INVITE_TILE_TAG, "pending_removed callId=" + callId
              + " participant=" + rejectedId + " reason=server_rejected");
        }
      }
      publish(state.withPhase(state.phase, "Members invited"));
    } else if ("call_tile_order".equals(type)) {
      ArrayList<String> previousOrder = new ArrayList<>(tileOrder);
      tileOrder.clear();
      if (event.has("order") && event.get("order").isJsonArray()) {
        for (JsonElement value : event.getAsJsonArray("order")) {
          if (value == null || !value.isJsonPrimitive()) continue;
          String participantId = normalizedParticipantId(value.getAsString());
          if (!participantId.isEmpty() && !tileOrder.contains(participantId))
            tileOrder.add(participantId);
        }
      }
      Log.i(TILE_SWAP_TAG, "signal_receive callId=" + callId + " sender="
          + JsonParserUtil.getString(event, "senderId") + " oldOrder=" + previousOrder
          + " newOrder=" + tileOrder);
      publish(state.withPhase(state.phase, state.status));
    } else if ("call_leave".equals(type)) {
      String departedId = normalizedParticipantId(JsonParserUtil.getString(event, "senderId"));
      if (!departedId.isEmpty()) departedParticipantIds.add(departedId);
      if (!departedId.isEmpty() && pendingParticipantIds.remove(departedId)) {
        Log.i(INVITE_TILE_TAG, "pending_removed callId=" + callId
            + " participant=" + departedId + " reason="
            + JsonParserUtil.getString(event, "reason"));
      }
      participantIds.removeIf(value -> normalizedParticipantId(value).equals(departedId));
      publish(state.withPhase(state.phase, "A participant left"));
    } else if ("call_end".equals(type) || "call_reject".equals(type)
        || "call_no_answer".equals(type) || "call_busy".equals(type))
      endLocal(type.replace("call_", ""));
  }

  private void sendControl(String type) {
    repository.sendCallEvent(controlEvent(type));
  }

  private void applyPeerHold(boolean held) {
    if (multiPartyCall) {
      publish(state.withPhase(state.phase, held ? "Participant is on hold" : "Call resumed"));
      return;
    }
    Runnable finished = () -> publish(state.withPhase(state.phase,
        held ? "Call on hold" : "Call resumed"));
    Runnable afterMicrophone = () -> {
      if ("video".equals(mediaType))
        setMedia("setCameraEnabled", !held && state.cameraEnabled, finished,
            () -> failTransient("Unable to follow the remote hold state."));
      else finished.run();
    };
    setMedia("setMicrophoneEnabled", !held && !state.muted, afterMicrophone,
        () -> failTransient("Unable to follow the remote hold state."));
  }

  private void sendMediaState(boolean muted, boolean cameraEnabled) {
    JsonObject event = controlEvent("call_mute");
    event.addProperty("muted", muted);
    event.addProperty("cameraEnabled", cameraEnabled);
    repository.sendCallEvent(event);
  }

  private void applyRequestedMute() {
    if (ended || !micState.needsApply()) return;
    boolean targetMuted = micState.begin();
    setMedia("setMicrophoneEnabled", !targetMuted, () -> {
      micState.complete(true);
      boolean applied = micState.applied();
      publish(new CallSessionState(state.phase, state.status, applied,
          state.speakerEnabled, state.cameraEnabled, state.peerMuted,
          state.peerCameraEnabled, state.connectedAtMs));
      sendMediaState(applied, state.cameraEnabled);
      applyRequestedMute();
    }, () -> {
      micState.complete(false);
      failTransient("Unable to change microphone setting.");
      applyRequestedMute();
    });
  }

  private void applyRequestedCamera() {
    if (ended || !cameraState.needsApply()) return;
    boolean targetEnabled = cameraState.begin();
    setMedia("setCameraEnabled", targetEnabled, () -> {
      cameraState.complete(true);
      boolean applied = cameraState.applied();
      publish(new CallSessionState(state.phase, state.status, state.muted,
          state.speakerEnabled, applied, state.peerMuted,
          state.peerCameraEnabled, state.connectedAtMs));
      sendMediaState(state.muted, applied);
      applyRequestedCamera();
    }, () -> {
      cameraState.complete(false);
      failTransient("Unable to change camera setting.");
      applyRequestedCamera();
    });
  }

  private void prepareLocalPreview() {
    if (localPreviewTrack != null) return;
    try {
      LocalVideoTrack track = room.getLocalParticipant().createVideoTrack(
          "preview", new LocalVideoTrackOptions(), null);
      track.startCapture();
      localPreviewTrack = track;
    } catch (Throwable ignored) { }
  }

  private void publishPreviewOrEnableCamera(Runnable completion) {
    LocalVideoTrack preview = localPreviewTrack;
    if (preview == null) {
      setMedia("setCameraEnabled", true, completion);
      return;
    }
    invoke(room.getLocalParticipant(), "publishVideoTrack",
        new Object[] { preview, new VideoTrackPublishOptions(), null }, ignored -> {
          localPreviewTrack = null;
          completion.run();
        }, error -> {
          releaseUnpublishedPreview();
          setMedia("setCameraEnabled", true, completion);
        });
  }

  private void releaseUnpublishedPreview() {
    LocalVideoTrack preview = localPreviewTrack;
    localPreviewTrack = null;
    if (preview == null) return;
    try { preview.stopCapture(); } catch (Throwable ignored) { }
    try { preview.dispose(); } catch (Throwable ignored) { }
  }

  private JsonObject controlEvent(String type) {
    JsonObject event = new JsonObject();
    event.addProperty("type", type);
    event.addProperty("engine", "livekit");
    event.addProperty("callId", callId);
    event.addProperty("chatId", chatId);
    event.addProperty("senderId", LoginStateManager.getInstance().getUID(context));
    event.addProperty("receiverId", peerId);
    event.addProperty("mediaType", mediaType);
    if (multiPartyCall) {
      event.addProperty("conference", true);
      event.addProperty("callMode", "group");
      JsonArray ids = new JsonArray();
      String ownId = normalizedParticipantId(LoginStateManager.getInstance().getUID(context));
      if (!ownId.isEmpty()) ids.add(ownId);
      for (String id : participantIds) ids.add(id);
      event.add("participantIds", ids);
    }
    return event;
  }

  private void setMedia(String method, boolean enabled, Runnable completion) {
    setMedia(method, enabled, completion, completion);
  }

  private void setMedia(String method, boolean enabled, Runnable success, Runnable failure) {
    invoke(room.getLocalParticipant(), method, new Object[] { enabled },
        result -> {
          if (result instanceof Boolean && !((Boolean) result)) failure.run();
          else success.run();
        }, error -> failure.run());
  }

  private void invoke(Object target, String name, Object[] arguments,
      Consumer<Object> success, Consumer<Throwable> failure) {
    mediaExecutor.execute(() -> {
      try {
        Method selected = null;
        for (Method method : target.getClass().getMethods()) {
          if (method.getName().equals(name)
              && method.getParameterTypes().length == arguments.length + 1
              && Continuation.class.isAssignableFrom(method.getParameterTypes()[
                  method.getParameterTypes().length - 1])) { selected = method; break; }
        }
        if (selected == null) throw new NoSuchMethodException(name);
        Object[] values = new Object[arguments.length + 1];
        System.arraycopy(arguments, 0, values, 0, arguments.length);
        values[arguments.length] = new Continuation<Object>() {
          @Override public CoroutineContext getContext() { return EmptyCoroutineContext.INSTANCE; }
          @Override public void resumeWith(Object result) {
            try { ResultKt.throwOnFailure(result); handler.post(() -> success.accept(result)); }
            catch (Throwable error) { handler.post(() -> failure.accept(error)); }
          }
        };
        Object result = selected.invoke(target, values);
        if (result != IntrinsicsKt.getCOROUTINE_SUSPENDED())
          handler.post(() -> success.accept(result));
      } catch (Throwable error) { handler.post(() -> failure.accept(error)); }
    });
  }

  private void fail(String message) {
    publish(state.withPhase(CallSessionState.Phase.FAILED,
        message == null ? "Call connection failed" : message));
  }
  private void failTransient(String message) {
    publish(state.withPhase(state.phase, message));
  }
  private void publish(CallSessionState value) {
    state = value;
    for (Observer current : observers)
      handler.post(() -> current.onSessionChanged(this, value));
  }
  private void endLocal(String reason) {
    if (ended) return;
    ended = true;
    String text;
    if ("no_answer".equals(reason)) text = "No answer.";
    else if ("rejected".equals(reason) || "reject".equals(reason)) text = "Call rejected.";
    else if ("busy".equals(reason)) text = "User is busy.";
    else if ("remote_left".equals(reason)) text = "Everyone left the call.";
    else if ("permission_denied".equals(reason)) text = "Call permission denied.";
    else text = "Call ended";
    publish(state.withPhase(CallSessionState.Phase.ENDED, text));
    for (Observer current : observers)
      handler.post(() -> current.onSessionEnded(this, reason));
  }
  private static String value(Intent intent, String key) {
    String result = intent.getStringExtra(key);
    return result == null ? "" : result.trim();
  }

  private static String normalizedParticipantId(String value) {
    if (value == null) return "";
    String normalized = value.trim();
    if (normalized.startsWith("<plus>")) normalized = normalized.substring(6);
    else if (normalized.startsWith("+")) normalized = normalized.substring(1);
    return normalized;
  }
}
