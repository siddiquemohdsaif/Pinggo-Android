package com.w3n.pinggo.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.AudioDeviceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.JsonParserUtil;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.call.ActiveCallRegistry;
import com.w3n.pinggo.contacts.DeviceContactResolver;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.views.call.VideoActiveCallView;
import com.w3n.pinggo.views.call.VoiceActiveCallView;
import io.livekit.android.LiveKit;
import io.livekit.android.ConnectOptions;
import io.livekit.android.LiveKitOverrides;
import io.livekit.android.RoomOptions;
import io.livekit.android.renderer.SurfaceViewRenderer;
import io.livekit.android.room.Room;
import io.livekit.android.room.participant.RemoteParticipant;
import io.livekit.android.room.track.Track;
import io.livekit.android.room.track.TrackPublication;
import io.livekit.android.room.track.VideoTrack;
import io.livekit.android.room.track.LocalVideoTrack;
import io.livekit.android.room.track.CameraPosition;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import kotlin.ResultKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;

/** Java-only LiveKit call screen for direct and group voice/video calls. */
public final class LiveKitCallActivity extends AppCompatActivity
    implements ChatRepository.CallEventListener, VoiceActiveCallView.Listener,
    VideoActiveCallView.Listener {
  private static final String TAG = "PingGoLiveKit";
  private static final String WAITING_TILE_KEY = "__waiting_participant__";
  private static final long UNANSWERED_TIMEOUT_MS = 50000L;
  public static final String EXTRA_MEDIA_TYPE = "com.w3n.pinggo.EXTRA_LIVEKIT_MEDIA_TYPE";
  public static final String EXTRA_INCOMING = "com.w3n.pinggo.EXTRA_LIVEKIT_INCOMING";
  public static final String EXTRA_CONFERENCE_CALL = "com.w3n.pinggo.EXTRA_LIVEKIT_CONFERENCE_CALL";
  public static final String EXTRA_PARTICIPANT_IDS = "com.w3n.pinggo.EXTRA_LIVEKIT_PARTICIPANT_IDS";
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Map<String, RenderedTrack> renderers = new LinkedHashMap<>();
  private final Map<String, ParticipantTile> participantTiles = new LinkedHashMap<>();
  private final Set<String> knownParticipantIds = new LinkedHashSet<>();
  private final Set<String> observedParticipantIds = new LinkedHashSet<>();
  private final Set<String> departedParticipantIds = new LinkedHashSet<>();
  private Room room;
  private GridLayout grid;
  private FrameLayout videoRoot;
  private RenderedTrack localRenderer;
  private VoiceActiveCallView voiceView;
  private VideoActiveCallView videoView;
  private AudioManager audioManager;
  private boolean connected, muted, cameraEnabled = true, speakerOn, destroyed, ending;
  private boolean microphoneChangePending, cameraChangePending;
  private boolean conference, everHadRemoteParticipant;
  private long timerStartedAt;
  private final Runnable unansweredTimeout = () -> {
    if (destroyed || everHadRemoteParticipant) return;
    Log.i(TAG, "unanswered_timeout_finish callId=" + callId());
    com.w3n.pinggo.notification.PingGoNotificationManager.clearCallNotification(
        this, callId());
    if (!incoming()) sendControl("call_end");
    finish();
  };
  private final Runnable callTimer = new Runnable() {
    @Override public void run() {
      if (destroyed || timerStartedAt == 0L) return;
      long elapsed = Math.max(0L, (SystemClock.elapsedRealtime() - timerStartedAt) / 1000L);
      setStatus(String.format(java.util.Locale.US, "%02d:%02d:%02d",
          elapsed / 3600L, (elapsed % 3600L) / 60L, elapsed % 60L));
      handler.postDelayed(this, 1000L);
    }
  };
  private final Runnable finishIfStillAlone = () -> {
    if (!destroyed && !ending && everHadRemoteParticipant && room != null
        && room.getRemoteParticipants().isEmpty()) {
      Log.i(TAG, "last_participant_finish callId=" + callId()
          + " conference=" + conference);
      com.w3n.pinggo.notification.PingGoNotificationManager.clearCallNotification(
          this, callId());
      sendControl(conference ? "call_leave" : "call_end");
      finish();
    }
  };

  private final ActivityResultLauncher<Intent> memberPicker = registerForActivityResult(
      new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
        ArrayList<String> ids = result.getData().getStringArrayListExtra(
            NewChatActivity.RESULT_MEMBER_IDS);
        if (ids == null || ids.isEmpty()) return;
        conference = true;
        if (voiceView != null) voiceView.setParticipantTileMode(true);
        JsonObject event = baseControl("call_add_participants");
        com.google.gson.JsonArray members = new com.google.gson.JsonArray();
        for (String id : ids) members.add(id);
        event.add("participantIds", members);
        Log.i(TAG, "member_invite_send callId=" + callId() + " count=" + ids.size());
        ChatRepository.getInstance(this).sendCallEvent(event);
        setStatus("Inviting " + ids.size() + " member" + (ids.size() == 1 ? "…" : "s…"));
      });

  private final ActivityResultLauncher<String[]> permissionRequest = registerForActivityResult(
      new ActivityResultContracts.RequestMultiplePermissions(), grants -> {
        boolean allowed = true;
        for (Boolean grant : grants.values()) allowed &= Boolean.TRUE.equals(grant);
        if (allowed) authorizeAndConnect();
        else fail("Required call permissions were denied.");
      });

  private final Runnable participantSync = new Runnable() {
    @Override public void run() {
      if (destroyed || room == null) return;
      syncVideoTracks();
      handler.postDelayed(this, 500L);
    }
  };

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    conference = chatId().startsWith("grp_")
        || getIntent().getBooleanExtra(EXTRA_CONFERENCE_CALL, false);
    ArrayList<String> initialParticipants = getIntent().getStringArrayListExtra(
        EXTRA_PARTICIPANT_IDS);
    if (initialParticipants != null) replaceKnownParticipants(initialParticipants);
    if (incoming()) {
      if (getIntent().getBooleanExtra(VoiceCallActivity.EXTRA_AUTO_ACCEPT, false))
        com.w3n.pinggo.notification.PingGoNotificationManager.clearCallNotification(
            this, callId());
      else com.w3n.pinggo.notification.PingGoNotificationManager
          .markCallNotificationOpened(this, getIntent());
    }
    buildUi();
    handler.postDelayed(unansweredTimeout, UNANSWERED_TIMEOUT_MS);
    audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    speakerOn = isVideo() && applySpeakerRoute(true);
    if (!isVideo()) applySpeakerRoute(false);
    updateAudioState();
    room = LiveKit.INSTANCE.create(
        getApplicationContext(), new RoomOptions(), new LiveKitOverrides());
    Log.i(TAG, "activity_created callId=" + callId() + " chatId=" + chatId()
        + " media=" + mediaType() + " incoming=" + incoming());
    ChatRepository.getInstance(this).setCallEventListener(this);
    ActiveCallRegistry.getInstance().register(this, chatId(),
        isVideo() ? ActiveCallRegistry.TYPE_VIDEO : ActiveCallRegistry.TYPE_VOICE);
    if (incoming() && !getIntent().getBooleanExtra(VoiceCallActivity.EXTRA_AUTO_ACCEPT, false)) {
      setStatus("Incoming " + mediaType() + " call");
      if (isVideo()) videoView.showIncomingPrompt(true); else voiceView.showIncomingPrompt();
      sendControl("call_ringing");
    } else requestPermissions();
  }

  private void buildUi() {
    if (isVideo()) {
      videoRoot = new FrameLayout(this);
      videoRoot.setBackgroundColor(Color.rgb(16, 24, 32));
      grid = new GridLayout(this);
      grid.setColumnCount(2);
      videoRoot.addView(grid, new FrameLayout.LayoutParams(-1, -1));
      videoView = new VideoActiveCallView(this,
          value(VoiceCallActivity.EXTRA_PHONE_NUMBER),
          value(VoiceCallActivity.EXTRA_PROFILE_PATH), this);
      videoView.setConferenceParticipants(conference, "");
      videoView.setAddMemberVisible(!chatId().startsWith("grp_"));
      videoView.setCallStatus("Preparing call…");
      videoRoot.addView(videoView, new FrameLayout.LayoutParams(-1, -1));
      setContentView(videoRoot);
      applySystemBarInsets(videoView);
    } else {
      videoRoot = new FrameLayout(this);
      videoRoot.setBackgroundColor(Color.rgb(16, 24, 32));
      grid = new GridLayout(this);
      grid.setColumnCount(1);
      videoRoot.addView(grid, new FrameLayout.LayoutParams(-1, -1));
      voiceView = new VoiceActiveCallView(this,
          value(VoiceCallActivity.EXTRA_PHONE_NUMBER),
          value(VoiceCallActivity.EXTRA_PROFILE_PATH), this);
      // A direct LiveKit voice call should use the same profile-photo layout as
      // the existing WebRTC voice screen. The participant grid is only needed
      // once the call becomes a conference.
      voiceView.setParticipantTileMode(conference);
      voiceView.setConferenceParticipants(conference, "");
      voiceView.setAddMemberVisible(!chatId().startsWith("grp_"));
      voiceView.setCallStatus("Preparing call…");
      videoRoot.addView(voiceView, new FrameLayout.LayoutParams(-1, -1));
      setContentView(videoRoot);
      applySystemBarInsets(voiceView);
    }
    addWaitingParticipantTile();
  }

  private void addWaitingParticipantTile() {
    if (grid == null) return;
    String identity = value(VoiceCallActivity.EXTRA_CALLER_ID);
    String ownId = LoginStateManager.getInstance().getUID(this);
    if (conference && !knownParticipantIds.isEmpty()) {
      for (String participantId : new ArrayList<>(knownParticipantIds)) {
        if (participantId == null || participantId.trim().isEmpty()
            || participantId.equals(ownId)) continue;
        ParticipantTile tile = new ParticipantTile(this, participantId,
            participantId.equals(value(VoiceCallActivity.EXTRA_CALLER_ID))
                ? value(VoiceCallActivity.EXTRA_PROFILE_PATH) : null);
        participantTiles.put(participantId, tile);
        enableVideoTileDragging(tile);
        grid.addView(tile);
      }
      updateRemoteVideoLayout();
      return;
    }
    ParticipantTile tile = new ParticipantTile(this, identity,
        value(VoiceCallActivity.EXTRA_PROFILE_PATH));
    participantTiles.put(WAITING_TILE_KEY, tile);
    grid.addView(tile);
    updateRemoteVideoLayout();
  }

  private void applySystemBarInsets(android.view.View target) {
    ViewCompat.setOnApplyWindowInsetsListener(target, (view, insets) -> {
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
      if (voiceView != null) voiceView.setInsets(bars.top, bars.bottom);
      if (videoView != null) videoView.setInsets(bars.top, bars.bottom);
      Log.d(TAG, "system_bar_insets top=" + bars.top + " bottom=" + bars.bottom);
      return insets;
    });
    ViewCompat.requestApplyInsets(target);
  }

  private void requestPermissions() {
    List<String> missing = new ArrayList<>();
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.RECORD_AUDIO);
    if (isVideo() && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.CAMERA);
    Log.i(TAG, "permission_check callId=" + callId() + " missing=" + missing);
    if (missing.isEmpty()) authorizeAndConnect();
    else permissionRequest.launch(missing.toArray(new String[0]));
  }

  private void authorizeAndConnect() {
    setStatus("Authorizing…");
    Log.i(TAG, "token_request callId=" + callId() + " chatId=" + chatId()
        + " media=" + mediaType());
    AppFunctionManager.getInstance().getLiveKitToken(callId(), chatId(), mediaType(),
        new AppFunctionManager.Callback() {
          @Override public void onSuccess(Object value) {
            Log.i(TAG, "token_success callId=" + callId());
            if (!(value instanceof JsonObject)) { fail("Invalid LiveKit response."); return; }
            JsonObject json = (JsonObject) value;
            connect(JsonParserUtil.getString(json, "serverUrl"),
                JsonParserUtil.getString(json, "participantToken"));
          }
          @Override public void onError(String error) {
            Log.e(TAG, "token_failed callId=" + callId() + " error=" + error);
            fail(error);
          }
        });
  }

  private void connect(String url, String token) {
    setStatus("Connecting…");
    Log.i(TAG, "connect_start callId=" + callId() + " url=" + url);
    invokeSuspend(room, "connect", new Object[] { url, token, new ConnectOptions() }, () -> {
      Log.i(TAG, "connect_success callId=" + callId());
      setMediaEnabled("setMicrophoneEnabled", true);
      if (isVideo()) setMediaEnabled("setCameraEnabled", true);
      sendControl(incoming() ? "call_answer" : "call_invite");
      handler.removeCallbacks(unansweredTimeout);
      handler.postDelayed(unansweredTimeout, UNANSWERED_TIMEOUT_MS);
      handler.post(participantSync);
    });
  }

  /** Calls Kotlin suspend APIs without adding Kotlin source to this Java project. */
  private void invokeSuspend(Object target, String name, Object[] arguments, Runnable success) {
    invokeSuspendResult(target, name, arguments, ignored -> success.run());
  }

  private void invokeSuspendResult(Object target, String name, Object[] arguments,
      Consumer<Object> success) {
    invokeSuspendResult(target, name, arguments, success,
        error -> fail(error == null ? "LiveKit operation failed." : error.getMessage()));
  }

  private void invokeSuspendResult(Object target, String name, Object[] arguments,
      Consumer<Object> success, Consumer<Throwable> failure) {
    try {
      Method selected = null;
      for (Method method : target.getClass().getMethods()) {
        if (method.getName().equals(name)
            && method.getParameterTypes().length == arguments.length + 1
            && Continuation.class.isAssignableFrom(
                method.getParameterTypes()[method.getParameterTypes().length - 1])) {
          selected = method;
          break;
        }
      }
      if (selected == null) throw new NoSuchMethodException(name);
      Object[] values = new Object[arguments.length + 1];
      System.arraycopy(arguments, 0, values, 0, arguments.length);
      values[arguments.length] = new Continuation<Object>() {
        @Override public CoroutineContext getContext() { return EmptyCoroutineContext.INSTANCE; }
        @Override public void resumeWith(Object result) {
          try {
            ResultKt.throwOnFailure(result);
            runOnUiThread(() -> success.accept(result));
          }
          catch (Throwable error) { runOnUiThread(() -> failure.accept(error)); }
        }
      };
      Object result = selected.invoke(target, values);
      if (result != IntrinsicsKt.getCOROUTINE_SUSPENDED())
        runOnUiThread(() -> success.accept(result));
    } catch (Throwable error) {
      Log.e(TAG, "suspend_call_failed callId=" + callId() + " method=" + name, error);
      runOnUiThread(() -> failure.accept(error));
    }
  }

  private void setMediaEnabled(String method, boolean enabled) {
    setMediaEnabled(method, enabled, () -> { });
  }

  private void setMediaEnabled(String method, boolean enabled, Runnable success) {
    setMediaEnabled(method, enabled, success, () -> { });
  }

  private void setMediaEnabled(String method, boolean enabled, Runnable success,
      Runnable failure) {
    invokeSuspendResult(room.getLocalParticipant(), method, new Object[] { enabled }, result -> {
          if (result instanceof Boolean && !((Boolean) result)) {
            failure.run();
            Toast.makeText(this, "LiveKit could not change this setting.",
                Toast.LENGTH_SHORT).show();
            return;
          }
          syncLocalVideoTrack();
          syncVideoTracks();
          success.run();
        }, error -> {
          failure.run();
          Log.e(TAG, "media_change_failed callId=" + callId() + " method=" + method, error);
          Toast.makeText(this, "Unable to change the call setting.", Toast.LENGTH_SHORT).show();
        });
  }

  private void syncLocalVideoTrack() {
    if (!isVideo() || videoRoot == null || room == null) return;
    TrackPublication publication = room.getLocalParticipant().getTrackPublication(Track.Source.CAMERA);
    if (publication == null || !(publication.getTrack() instanceof VideoTrack)) {
      detachLocalVideo();
      return;
    }
    VideoTrack track = (VideoTrack) publication.getTrack();
    if (localRenderer != null && localRenderer.track == track) return;
    detachLocalVideo();
    SurfaceViewRenderer renderer = new SurfaceViewRenderer(this);
    room.initVideoRenderer(renderer);
    renderer.setZOrderMediaOverlay(true);
    track.addRenderer(renderer);
    int width = dp(112), height = dp(154);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height,
        Gravity.TOP | Gravity.END);
    params.topMargin = dp(92);
    params.rightMargin = dp(16);
    videoRoot.addView(renderer, Math.max(1, videoRoot.indexOfChild(videoView)), params);
    localRenderer = new RenderedTrack(track, renderer);
    Log.i(TAG, "local_video_attached callId=" + callId());
  }

  private void syncVideoTracks() {
    if (destroyed || room == null) return;
    int remoteCount = room.getRemoteParticipants().size();
    boolean participantConnected = remoteCount > 0;
    if (connected != participantConnected) {
      setConnected(participantConnected);
      Log.i(TAG, "participant_connection_changed callId=" + callId()
          + " connected=" + participantConnected + " remoteCount=" + remoteCount);
    }
    if (remoteCount > 0) {
      everHadRemoteParticipant = true;
      handler.removeCallbacks(unansweredTimeout);
      removeParticipantTile(WAITING_TILE_KEY);
      if (timerStartedAt == 0L) {
        timerStartedAt = SystemClock.elapsedRealtime();
        handler.post(callTimer);
      }
    }
    if (remoteCount > 1) conference = true;
    handler.removeCallbacks(finishIfStillAlone);
    if (remoteCount == 0 && everHadRemoteParticipant)
      handler.postDelayed(finishIfStillAlone, 100L);
    List<String> active = new ArrayList<>();
    for (Map.Entry<io.livekit.android.room.participant.Participant.Identity,
        RemoteParticipant> entry : room.getRemoteParticipants().entrySet()) {
      RemoteParticipant participant = entry.getValue();
      String identity = entry.getKey() == null ? "" : entry.getKey().getValue();
      String normalizedIdentity = normalizedParticipantId(identity);
      String key = normalizedIdentity.isEmpty()
          ? "participant_" + System.identityHashCode(participant) : normalizedIdentity;
      if (departedParticipantIds.contains(key)) {
        detachVideoTrack(key);
        removeParticipantTile(key);
        continue;
      }
      active.add(key);
      if (!normalizedIdentity.isEmpty()) {
        knownParticipantIds.add(normalizedIdentity);
        observedParticipantIds.add(normalizedIdentity);
      }
      ensureParticipantTile(key, normalizedIdentity);
      TrackPublication publication = participant.getTrackPublication(Track.Source.CAMERA);
      if (publication != null && publication.getTrack() instanceof VideoTrack)
        attachVideo(key, (VideoTrack) publication.getTrack());
      else detachVideoTrack(key);
    }
    for (String key : new ArrayList<>(participantTiles.keySet())) {
      if (WAITING_TILE_KEY.equals(key) && remoteCount == 0 && !everHadRemoteParticipant) continue;
      if (!active.contains(key) && (departedParticipantIds.contains(key)
          || observedParticipantIds.contains(key) || !knownParticipantIds.contains(key))) {
        if (observedParticipantIds.contains(key)) knownParticipantIds.remove(key);
        removeParticipantTile(key);
      }
    }
    // Rebind the entire grid on every room snapshot. This refreshes existing tiles
    // as well as newly joined/removed participants across all call members.
    updateRemoteVideoLayout();
    for (ParticipantTile tile : participantTiles.values()) tile.invalidate();
    grid.requestLayout();
    grid.invalidate();
    List<String> participantNames = new ArrayList<>();
    String ownId = normalizedParticipantId(LoginStateManager.getInstance().getUID(this));
    for (String participantId : knownParticipantIds) {
      String normalizedId = normalizedParticipantId(participantId);
      if (!normalizedId.isEmpty() && !normalizedId.equals(ownId))
        participantNames.add(DeviceContactResolver.cachedNameOrPhone(participantId));
    }
    String names = android.text.TextUtils.join(", ", participantNames);
    if (voiceView != null) {
      voiceView.setParticipantTileMode(conference);
      voiceView.setConferenceParticipants(conference, names);
    }
    if (videoView != null) videoView.setConferenceParticipants(conference, names);
    if (remoteCount > 0 && timerStartedAt == 0L) {
      setStatus("Connected");
    } else if (!everHadRemoteParticipant) {
      setStatus("Ringing…");
    }
  }

  private void refreshParticipantTiles(String reason) {
    Log.i(TAG, "participant_refresh_requested callId=" + callId() + " reason=" + reason);
    handler.post(() -> {
      if (!destroyed) syncVideoTracks();
    });
    // Signalling can arrive just before LiveKit publishes its room participant update.
    handler.postDelayed(() -> {
      if (!destroyed) syncVideoTracks();
    }, 300L);
  }

  private void ensureParticipantTile(String key, String identity) {
    if (grid == null || participantTiles.containsKey(key)) return;
    String preferredProfilePath = normalizedParticipantId(identity).equals(
        normalizedParticipantId(value(VoiceCallActivity.EXTRA_CALLER_ID)))
        ? value(VoiceCallActivity.EXTRA_PROFILE_PATH) : null;
    ParticipantTile tile = new ParticipantTile(this, identity, preferredProfilePath);
    participantTiles.put(key, tile);
    enableVideoTileDragging(tile);
    grid.addView(tile);
    Log.i(TAG, "participant_tile_added callId=" + callId() + " participant=" + identity
        + " tileCount=" + participantTiles.size());
    updateRemoteVideoLayout();
  }

  private void attachVideo(String key, VideoTrack track) {
    RenderedTrack current = renderers.get(key);
    if (current != null && current.track == track) return;
    detachVideoTrack(key);
    ensureParticipantTile(key, key);
    ParticipantTile tile = participantTiles.get(key);
    if (tile == null) return;
    SurfaceViewRenderer renderer = new SurfaceViewRenderer(this);
    room.initVideoRenderer(renderer);
    track.addRenderer(renderer);
    renderers.put(key, new RenderedTrack(track, renderer));
    tile.addView(renderer, new FrameLayout.LayoutParams(-1, -1));
    tile.name.bringToFront();
    updateRemoteVideoLayout();
  }

  private void detachVideoTrack(String key) {
    RenderedTrack value = renderers.remove(key);
    if (value == null) return;
    value.track.removeRenderer(value.renderer);
    ParticipantTile tile = participantTiles.get(key);
    if (tile != null) tile.removeView(value.renderer);
    value.renderer.release();
  }

  private void removeParticipantTile(String key) {
    detachVideoTrack(key);
    ParticipantTile tile = participantTiles.remove(key);
    if (tile != null && grid != null) {
      grid.removeView(tile);
      tile.release();
      Log.i(TAG, "participant_tile_removed callId=" + callId() + " participant=" + key
          + " tileCount=" + participantTiles.size());
    }
    updateRemoteVideoLayout();
  }

  private void updateRemoteVideoLayout() {
    if (grid == null) return;
    int count = grid.getChildCount();
    if (count == 0) return;
    int columns = count <= 2 ? 1 : 2;
    int rows = count == 1 ? 1 : count == 2 ? 2 : (count + 1) / 2;
    // Expand first so new child specs are valid; shrinking happens after specs change.
    if (columns > grid.getColumnCount()) grid.setColumnCount(columns);
    if (rows > grid.getRowCount()) grid.setRowCount(rows);
    int screenWidth = getResources().getDisplayMetrics().widthPixels;
    int screenHeight = getResources().getDisplayMetrics().heightPixels;
    for (int index = 0; index < count; index++) {
      boolean lastWide = columns == 2 && count % 2 == 1 && index == count - 1;
      int row = columns == 1 ? index : index / 2;
      int column = columns == 1 ? 0 : index % 2;
      GridLayout.LayoutParams params = new GridLayout.LayoutParams(
          GridLayout.spec(row, 1, 1f),
          GridLayout.spec(lastWide ? 0 : column, lastWide ? 2 : 1, 1f));
      int margin = dp(2);
      params.width = Math.max(1,
          (lastWide || columns == 1 ? screenWidth : screenWidth / 2) - margin * 2);
      params.height = Math.max(1,
          (count == 1 ? screenHeight : screenHeight / rows) - margin * 2);
      params.setMargins(margin, margin, margin, margin);
      grid.getChildAt(index).setLayoutParams(params);
    }
    // Apply the smaller explicit counts only after every remaining child has its
    // new row/column spec. GridLayout validates counts immediately and otherwise
    // sees stale indices while participant tiles are being removed.
    grid.setColumnCount(columns);
    grid.setRowCount(rows);
  }

  private void enableVideoTileDragging(View tile) {
    tile.setOnTouchListener(new View.OnTouchListener() {
      float downRawX, downRawY;
      boolean dragging;

      @Override public boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
          case MotionEvent.ACTION_DOWN:
            downRawX = event.getRawX();
            downRawY = event.getRawY();
            dragging = false;
            if (grid != null) grid.requestDisallowInterceptTouchEvent(true);
            return true;
          case MotionEvent.ACTION_MOVE:
            float dx = event.getRawX() - downRawX;
            float dy = event.getRawY() - downRawY;
            if (!dragging && Math.hypot(dx, dy) > dp(8)) {
              dragging = true;
              view.setAlpha(0.82f);
              view.setElevation(dp(12));
            }
            if (dragging) {
              view.setTranslationX(dx);
              view.setTranslationY(dy);
            }
            return true;
          case MotionEvent.ACTION_UP:
            if (dragging) reorderVideoTile(view, event.getRawX(), event.getRawY());
            resetDraggedTile(view);
            if (grid != null) grid.requestDisallowInterceptTouchEvent(false);
            return true;
          case MotionEvent.ACTION_CANCEL:
            resetDraggedTile(view);
            if (grid != null) grid.requestDisallowInterceptTouchEvent(false);
            return true;
          default:
            return false;
        }
      }
    });
  }

  private void reorderVideoTile(View dragged, float rawX, float rawY) {
    if (grid == null || grid.getChildCount() < 2) return;
    int from = grid.indexOfChild(dragged);
    int target = from;
    int[] location = new int[2];
    for (int index = 0; index < grid.getChildCount(); index++) {
      View candidate = grid.getChildAt(index);
      if (candidate == dragged) continue;
      candidate.getLocationOnScreen(location);
      if (rawX >= location[0] && rawX <= location[0] + candidate.getWidth()
          && rawY >= location[1] && rawY <= location[1] + candidate.getHeight()) {
        target = index;
        break;
      }
    }
    if (target != from) {
      grid.removeView(dragged);
      grid.addView(dragged, target);
      Log.i(TAG, "video_tile_moved callId=" + callId() + " from=" + from
          + " to=" + target);
    }
    updateRemoteVideoLayout();
  }

  private void resetDraggedTile(View view) {
    view.animate().translationX(0f).translationY(0f).alpha(1f)
        .setDuration(120L).start();
    view.setElevation(0f);
  }

  private void detachLocalVideo() {
    if (localRenderer == null) return;
    localRenderer.track.removeRenderer(localRenderer.renderer);
    if (videoRoot != null) videoRoot.removeView(localRenderer.renderer);
    localRenderer.renderer.release();
    localRenderer = null;
  }

  private void toggleMute() {
    Log.i(TAG, "control_click callId=" + callId() + " control=mute connected="
        + connected + " pending=" + microphoneChangePending + " currentMuted=" + muted);
    if (!connected) {
      Log.w(TAG, "control_ignored callId=" + callId()
          + " control=mute reason=not_connected");
      return;
    }
    if (microphoneChangePending) {
      Log.w(TAG, "control_ignored callId=" + callId()
          + " control=mute reason=change_pending");
      return;
    }
    final boolean targetMuted = !muted;
    microphoneChangePending = true;
    Log.i(TAG, "control_started callId=" + callId()
        + " control=mute requestedMuted=" + targetMuted);
    setMediaEnabled("setMicrophoneEnabled", !targetMuted, () -> {
      microphoneChangePending = false;
      muted = targetMuted;
      updateAudioState();
      Log.i(TAG, "control_completed callId=" + callId()
          + " control=mute muted=" + muted);
    }, () -> {
      microphoneChangePending = false;
      Log.e(TAG, "control_failed callId=" + callId()
          + " control=mute requestedMuted=" + targetMuted);
    });
  }

  private void toggleCamera() {
    Log.i(TAG, "control_click callId=" + callId() + " control=camera connected="
        + connected + " videoCall=" + isVideo() + " pending=" + cameraChangePending
        + " currentEnabled=" + cameraEnabled);
    if (!connected || !isVideo()) {
      Log.w(TAG, "control_ignored callId=" + callId()
          + " control=camera reason=" + (!connected ? "not_connected" : "not_video_call"));
      return;
    }
    if (cameraChangePending) {
      Log.w(TAG, "control_ignored callId=" + callId()
          + " control=camera reason=change_pending");
      return;
    }
    final boolean targetEnabled = !cameraEnabled;
    cameraChangePending = true;
    Log.i(TAG, "control_started callId=" + callId()
        + " control=camera requestedEnabled=" + targetEnabled);
    setMediaEnabled("setCameraEnabled", targetEnabled, () -> {
      cameraChangePending = false;
      cameraEnabled = targetEnabled;
      if (videoView != null) videoView.setCameraEnabled(cameraEnabled);
      Log.i(TAG, "control_completed callId=" + callId()
          + " control=camera enabled=" + cameraEnabled);
    }, () -> {
      cameraChangePending = false;
      Log.e(TAG, "control_failed callId=" + callId()
          + " control=camera requestedEnabled=" + targetEnabled);
    });
  }

  private void sendControl(String type) {
    JsonObject event = baseControl(type);
    Log.i(TAG, "signal_send callId=" + callId() + " type=" + type + " chatId=" + chatId());
    ChatRepository.getInstance(this).sendCallEvent(event);
  }

  private JsonObject baseControl(String type) {
    JsonObject event = new JsonObject();
    event.addProperty("type", type);
    event.addProperty("engine", "livekit");
    event.addProperty("callId", callId());
    event.addProperty("chatId", chatId());
    event.addProperty("senderId", LoginStateManager.getInstance().getUID(this));
    event.addProperty("receiverId", value(VoiceCallActivity.EXTRA_CALLER_ID));
    event.addProperty("mediaType", mediaType());
    return event;
  }

  private void setStatus(String value) {
    if (voiceView != null) voiceView.setCallStatus(value);
    if (videoView != null) videoView.setCallStatus(value);
  }

  private void setConnected(boolean value) {
    connected = value;
    if (voiceView != null) voiceView.setCallConnected(value);
    if (videoView != null) videoView.setCallConnected(value);
  }

  private void updateAudioState() {
    if (voiceView != null) voiceView.setAudioState(speakerOn, muted);
    if (videoView != null) videoView.setAudioState(speakerOn, muted);
  }

  @Override public void onBack() {
    Log.i(TAG, "control_click callId=" + callId() + " control=back");
    finish();
    Log.i(TAG, "control_completed callId=" + callId() + " control=back action=finish");
  }

  @Override public void onAccept() {
    Log.i(TAG, "control_click callId=" + callId() + " control=accept");
    com.w3n.pinggo.notification.PingGoNotificationManager.clearCallNotification(
        this, callId());
    if (videoView != null) videoView.showIncomingPrompt(false);
    if (voiceView != null) voiceView.hideIncomingPrompt();
    requestPermissions();
    Log.i(TAG, "control_completed callId=" + callId()
        + " control=accept action=permission_check_started");
  }

  @Override public void onReject() {
    Log.i(TAG, "control_click callId=" + callId() + " control=reject");
    com.w3n.pinggo.notification.PingGoNotificationManager.clearCallNotification(
        this, callId());
    sendControl("call_reject");
    Log.i(TAG, "control_completed callId=" + callId()
        + " control=reject action=signal_sent");
    finish();
  }

  @Override public void onSpeaker() {
    boolean requested = !speakerOn;
    Log.i(TAG, "control_click callId=" + callId()
        + " control=speaker requestedEnabled=" + requested);
    if (!applySpeakerRoute(requested)) {
      Log.e(TAG, "control_failed callId=" + callId()
          + " control=speaker requestedEnabled=" + requested);
      Toast.makeText(this, "Unable to change audio output.", Toast.LENGTH_SHORT).show();
      return;
    }
    speakerOn = requested;
    updateAudioState();
    Log.i(TAG, "control_completed callId=" + callId()
        + " control=speaker enabled=" + speakerOn);
  }

  @Override public void onMute() { toggleMute(); }
  @Override public void onEnd() { hangup(); }
  @Override public void onCamera() { toggleCamera(); }

  @Override public void onFlipCamera() {
    Log.i(TAG, "control_click callId=" + callId()
        + " control=flip_camera enabled=" + cameraEnabled);
    try {
      TrackPublication publication = room.getLocalParticipant()
          .getTrackPublication(Track.Source.CAMERA);
      if (publication == null || !(publication.getTrack() instanceof LocalVideoTrack)
          || !cameraEnabled) {
        Log.w(TAG, "control_ignored callId=" + callId()
            + " control=flip_camera reason=camera_track_unavailable");
        return;
      }
      LocalVideoTrack track = (LocalVideoTrack) publication.getTrack();
      CameraPosition current = track.getOptions().getPosition();
      CameraPosition target = current == CameraPosition.FRONT
          ? CameraPosition.BACK : CameraPosition.FRONT;
      track.switchCamera(null, target);
      Log.i(TAG, "control_completed callId=" + callId()
          + " control=flip_camera position=" + target);
    } catch (Throwable error) {
      Log.e(TAG, "control_failed callId=" + callId() + " control=flip_camera", error);
      Toast.makeText(this, "Unable to switch camera.", Toast.LENGTH_SHORT).show();
    }
  }

  private boolean applySpeakerRoute(boolean enabled) {
    if (audioManager == null) return false;
    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      AudioDeviceInfo fallback = null;
      for (AudioDeviceInfo device : audioManager.getAvailableCommunicationDevices()) {
        if (enabled && device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
          return audioManager.setCommunicationDevice(device);
        if (!enabled && device.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
          return audioManager.setCommunicationDevice(device);
        if (!enabled && fallback == null && device.getType() != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
          fallback = device;
      }
      return !enabled && fallback != null && audioManager.setCommunicationDevice(fallback);
    }
    audioManager.setSpeakerphoneOn(enabled);
    return audioManager.isSpeakerphoneOn() == enabled;
  }

  @Override public void onAddMember() {
    Log.i(TAG, "control_click callId=" + callId()
        + " control=add_member connected=" + connected);
    if (!connected) {
      Log.w(TAG, "control_ignored callId=" + callId()
          + " control=add_member reason=not_connected");
      Toast.makeText(this, "Wait for the call to connect.", Toast.LENGTH_SHORT).show();
      return;
    }
    ArrayList<String> excluded = new ArrayList<>();
    String ownId = LoginStateManager.getInstance().getUID(this);
    if (ownId != null && !ownId.trim().isEmpty()) excluded.add(ownId);
    String originalPeerId = value(VoiceCallActivity.EXTRA_CALLER_ID);
    if (!originalPeerId.isEmpty() && !excluded.contains(originalPeerId))
      excluded.add(originalPeerId);
    if (room != null) {
      for (io.livekit.android.room.participant.Participant.Identity identity
          : room.getRemoteParticipants().keySet()) {
        if (identity != null && identity.getValue() != null
            && !identity.getValue().trim().isEmpty()) excluded.add(identity.getValue());
      }
    }
    Intent intent = new Intent(this, NewChatActivity.class)
        .putExtra(NewChatActivity.EXTRA_SELECT_CALL_MEMBERS, true)
        .putStringArrayListExtra(NewChatActivity.EXTRA_EXCLUDED_MEMBER_IDS, excluded);
    Log.i(TAG, "member_picker_open callId=" + callId()
        + " excludedParticipants=" + excluded.size());
    memberPicker.launch(intent);
    Log.i(TAG, "control_completed callId=" + callId()
        + " control=add_member action=member_picker_opened");
  }

  private void hangup() {
    if (ending) {
      Log.w(TAG, "control_ignored callId=" + callId() + " control=end reason=already_ending");
      return;
    }
    ending = true;
    String signal = conference ? "call_leave" : "call_end";
    Log.i(TAG, "control_click callId=" + callId() + " control=end signal=" + signal);
    sendControl(signal);
    Log.i(TAG, "control_completed callId=" + callId()
        + " control=end action=signal_sent signal=" + signal);
    finish();
  }

  @Override public void onCallEvent(JsonObject event) {
    if (!callId().equals(JsonParserUtil.getString(event, "callId"))) return;
    String type = JsonParserUtil.getString(event, "type");
    Log.i(TAG, "signal_receive callId=" + callId() + " type=" + type);
    runOnUiThread(() -> {
      if (type.endsWith("_ack") && "ended".equals(
          JsonParserUtil.getString(event, "state"))) {
        com.w3n.pinggo.notification.PingGoNotificationManager.clearCallNotification(
            this, callId());
        finish();
        return;
      }
      if ("call_ringing".equals(type)) setStatus("Ringing…");
      else if ("call_answer".equals(type)) {
        handler.removeCallbacks(unansweredTimeout);
        setStatus("Joining…");
        refreshParticipantTiles("call_answer");
      }
      else if ("call_participants_added".equals(type)) {
        if (event.has("participantIds") && event.get("participantIds").isJsonArray()) {
          ArrayList<String> participantIds = new ArrayList<>();
          for (com.google.gson.JsonElement value : event.getAsJsonArray("participantIds")) {
            String participantId = value == null ? "" : value.getAsString().trim();
            if (!participantId.isEmpty()) participantIds.add(participantId);
          }
          // The server uses an empty list for a no-op add acknowledgement. Do not
          // erase the roster already visible to the participants in that case.
          if (!participantIds.isEmpty()) replaceKnownParticipants(participantIds);
        }
        setStatus("Members invited");
        refreshParticipantTiles("participants_added");
      }
      else if ("call_leave".equals(type)) {
        String departedId = normalizedParticipantId(
            JsonParserUtil.getString(event, "senderId"));
        if (!departedId.isEmpty()) {
          departedParticipantIds.add(departedId);
          knownParticipantIds.remove(departedId);
          removeParticipantTile(departedId);
        }
        setStatus("A participant left");
        refreshParticipantTiles("participant_left");
      }
      else if ("call_reject".equals(type) || "call_busy".equals(type)
          || "call_no_answer".equals(type) || "call_end".equals(type)) {
        ending = true;
        com.w3n.pinggo.notification.PingGoNotificationManager.clearCallNotification(
            this, callId());
        finish();
      }
    });
  }

  private void fail(String message) {
    Log.e(TAG, "call_failed callId=" + callId() + " message=" + message);
    runOnUiThread(() -> {
      if (destroyed || isFinishing()) return;
      Toast.makeText(this, message == null ? "LiveKit call failed." : message,
          Toast.LENGTH_LONG).show();
      finish();
    });
  }

  @Override protected void onDestroy() {
    Log.i(TAG, "activity_destroyed callId=" + callId() + " connected=" + connected
        + " remoteParticipants=" + (room == null ? 0 : room.getRemoteParticipants().size()));
    destroyed = true;
    handler.removeCallbacks(participantSync);
    handler.removeCallbacks(finishIfStillAlone);
    handler.removeCallbacks(callTimer);
    handler.removeCallbacks(unansweredTimeout);
    // Teardown does not need a layout pass. Removing tiles individually invokes
    // updateRemoteVideoLayout between removals and can expose stale GridLayout specs.
    for (String key : new ArrayList<>(renderers.keySet())) detachVideoTrack(key);
    for (ParticipantTile tile : participantTiles.values()) tile.release();
    participantTiles.clear();
    if (grid != null) grid.removeAllViews();
    detachLocalVideo();
    if (voiceView != null) voiceView.release();
    if (videoView != null) videoView.release();
    if (room != null) room.disconnect();
    if (audioManager != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        audioManager.clearCommunicationDevice();
      else audioManager.setSpeakerphoneOn(false);
      audioManager.setMode(AudioManager.MODE_NORMAL);
    }
    ChatRepository.getInstance(this).clearCallEventListener(this);
    ActiveCallRegistry.getInstance().clear(this);
    super.onDestroy();
  }

  private String mediaType() { return "video".equals(value(EXTRA_MEDIA_TYPE)) ? "video" : "audio"; }
  private boolean isVideo() { return "video".equals(mediaType()); }
  private boolean incoming() { return getIntent().getBooleanExtra(EXTRA_INCOMING, false); }
  private String callId() { return value(VoiceCallActivity.EXTRA_CALL_ID); }
  private String chatId() { return value(VoiceCallActivity.EXTRA_CALL_CHAT_ID); }
  private String value(String key) {
    String result = getIntent().getStringExtra(key);
    return result == null ? "" : result.trim();
  }

  private void replaceKnownParticipants(List<String> participantIds) {
    knownParticipantIds.clear();
    String ownId = normalizedParticipantId(LoginStateManager.getInstance().getUID(this));
    for (String participantId : participantIds) {
      String normalizedId = normalizedParticipantId(participantId);
      if (normalizedId.isEmpty()) continue;
      departedParticipantIds.remove(normalizedId);
      knownParticipantIds.add(normalizedId);
      if (!normalizedId.equals(ownId)) ensureParticipantTile(normalizedId, normalizedId);
    }
    if (knownParticipantIds.size() > 2) conference = true;
  }

  private static String normalizedParticipantId(String value) {
    if (value == null) return "";
    String normalized = value.trim();
    if (normalized.startsWith("<plus>")) normalized = normalized.substring(6);
    else if (normalized.startsWith("+")) normalized = normalized.substring(1);
    return normalized;
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private static Bitmap circularBitmap(Bitmap source) {
    if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) return source;
    int size = Math.min(source.getWidth(), source.getHeight());
    int left = (source.getWidth() - size) / 2;
    int top = (source.getHeight() - size) / 2;
    Bitmap square = Bitmap.createBitmap(source, left, top, size, size);
    Bitmap circle = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(circle);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    paint.setShader(new BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
    square.recycle();
    if (square != source) source.recycle();
    return circle;
  }

  private static final class RenderedTrack {
    final VideoTrack track;
    final SurfaceViewRenderer renderer;
    RenderedTrack(VideoTrack track, SurfaceViewRenderer renderer) {
      this.track = track;
      this.renderer = renderer;
    }
  }

  private final class ParticipantTile extends FrameLayout {
    final TextView name;
    final String displayName;
    final Bitmap avatarBitmap;
    final View avatar;

    ParticipantTile(android.content.Context context, String identity) {
      this(context, identity, null);
    }

    ParticipantTile(android.content.Context context, String identity, String preferredProfilePath) {
      super(context);
      setBackgroundColor(0xFF18242E);
      setWillNotDraw(false);
      displayName = DeviceContactResolver.cachedNameOrPhone(identity);
      String profilePath = preferredProfilePath == null || preferredProfilePath.trim().isEmpty()
          ? ChatProfilePhotoStore.getLocalPath(context, identity) : preferredProfilePath;
      Bitmap bitmap = profilePath == null ? null : BitmapFactory.decodeFile(profilePath);
      avatarBitmap = bitmap == null ? null : circularBitmap(bitmap);
      int avatarSize = dp(isVideo() ? 156 : 232);
      GradientDrawable avatarBackground = new GradientDrawable();
      avatarBackground.setShape(GradientDrawable.OVAL);
      avatarBackground.setColor(0xFF315063);
      if (avatarBitmap != null && !avatarBitmap.isRecycled()) {
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageBitmap(avatarBitmap);
        image.setBackground(avatarBackground);
        avatar = image;
      } else {
        TextView initial = new TextView(context);
        initial.setText(displayName == null || displayName.trim().isEmpty()
            ? "?" : displayName.trim().substring(0, 1)
                .toUpperCase(java.util.Locale.ROOT));
        initial.setTextColor(Color.WHITE);
        initial.setTextSize(isVideo() ? 48f : 72f);
        initial.setGravity(Gravity.CENTER);
        initial.setBackground(avatarBackground);
        avatar = initial;
      }
      addView(avatar, new FrameLayout.LayoutParams(avatarSize, avatarSize, Gravity.CENTER));
      name = new TextView(context);
      name.setText(displayName);
      name.setTextColor(Color.WHITE);
      name.setTextSize(14f);
      name.setGravity(Gravity.CENTER_VERTICAL);
      name.setPadding(dp(12), 0, dp(12), 0);
      name.setBackgroundColor(0x99000000);
      FrameLayout.LayoutParams label = new FrameLayout.LayoutParams(-1, dp(40),
          Gravity.BOTTOM);
      addView(name, label);
    }

    void release() {
      if (avatarBitmap != null && !avatarBitmap.isRecycled()) avatarBitmap.recycle();
    }
  }
}
