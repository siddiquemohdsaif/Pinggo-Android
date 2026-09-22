package com.w3n.pinggo.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.w3n.pinggo.call.ActiveCallRegistry;
import com.w3n.pinggo.call.CallPictureInPicture;
import com.w3n.pinggo.call.FloatingVideoCallController;
import com.w3n.pinggo.call.FloatingVoiceCallController;
import com.w3n.pinggo.call.session.CallActivityContract;
import com.w3n.pinggo.call.session.CallSession;
import com.w3n.pinggo.call.session.CallSessionFactory;
import com.w3n.pinggo.call.session.CallSessionRegistry;
import com.w3n.pinggo.call.session.CallSessionService;
import com.w3n.pinggo.call.session.CallSessionState;
import com.w3n.pinggo.call.session.CallTonePlayer;
import com.w3n.pinggo.call.session.LegacyVideoCallSession;
import com.w3n.pinggo.call.session.LiveKitCallSession;
import com.w3n.pinggo.contacts.DeviceContactResolver;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.notification.PingGoNotificationManager;
import com.w3n.pinggo.views.call.VideoActiveCallView;
import com.w3n.pinggo.views.call.VoiceActiveCallView;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Locale;
import io.livekit.android.renderer.TextureViewRenderer;
import io.livekit.android.room.Room;
import io.livekit.android.room.participant.RemoteParticipant;
import io.livekit.android.room.track.Track;
import io.livekit.android.room.track.VideoTrack;
import io.livekit.android.room.track.TrackPublication;

/** Single UI host for every legacy WebRTC and LiveKit call session. */
public final class CallActivity extends PingGoActivity implements
    VoiceActiveCallView.Listener, VideoActiveCallView.Listener,
    CallSession.Observer, CallSessionRegistry.Listener,
    ActiveCallRegistry.PictureInPictureHangupListener {
  private static final String TILE_SWAP_TAG = "PingGoTileSwap";
  private static final String CALL_SWAP_TAG = "PingGoCallSwap";
  private final CallSessionRegistry registry = CallSessionService.sharedRegistry();
  private CallSession displayed;
  private VoiceActiveCallView voiceView;
  private VideoActiveCallView videoView;
  private FrameLayout videoRoot;
  private SurfaceView localSurface, remoteSurface;
  private View localCameraOffView;
  private GridLayout liveKitGrid;
  private FrameLayout liveKitDirectRemote;
  private final Map<String, RenderedLiveKitTrack> liveKitTracks = new LinkedHashMap<>();
  private final Map<String, ParticipantTile> liveKitTiles = new LinkedHashMap<>();
  private final ArrayList<String> liveKitVisualOrder = new ArrayList<>();
  private final Set<String> knownLiveKitParticipants = new LinkedHashSet<>();
  private RenderedLiveKitTrack liveKitLocalTrack;
  private ParticipantTile pipLocalTile;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final CallTonePlayer tones = new CallTonePlayer(this);
  private Intent pendingPermissionIntent;
  private boolean pendingPermissionAccept;
  private boolean pipEligible;
  private boolean pipSessionActive, pipActivityStopped, pipLayoutActive;
  private int pipExitCheckAttempts;
  private String lastTransientMessage = "";
  private final Set<String> completedCallIds = new HashSet<>();
  private final Runnable callTimer = new Runnable() {
    @Override public void run() {
      if (displayed == null) return;
      CallSessionState current = displayed.state();
      if (!isEstablished(current)) return;
      render(current);
    }
  };
  private final Runnable liveKitVideoSync = new Runnable() {
    @Override public void run() {
      if (!(displayed instanceof LiveKitCallSession) || videoRoot == null) return;
      syncLiveKitVideo((LiveKitCallSession) displayed);
      handler.postDelayed(this, 500L);
    }
  };

  private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(
      new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissions);
  private final ActivityResultLauncher<String> phoneStatePermission = registerForActivityResult(
      new ActivityResultContracts.RequestPermission(), granted -> {
        Log.i("PingGoExternalCall", "phone_permission_result granted=" + granted);
        startService(new Intent(this, CallSessionService.class));
      });
  private final ActivityResultLauncher<Intent> memberPicker = registerForActivityResult(
      new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != RESULT_OK || result.getData() == null
            || !(displayed instanceof LiveKitCallSession)) return;
        ArrayList<String> ids = result.getData().getStringArrayListExtra(
            NewChatActivity.RESULT_MEMBER_IDS);
        if (ids != null) ((LiveKitCallSession) displayed).addParticipants(ids);
      });

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    startService(new Intent(this, CallSessionService.class));
    registry.addListener(this);
    getOnBackPressedDispatcher().addCallback(this,
        new androidx.activity.OnBackPressedCallback(true) {
          @Override public void handleOnBackPressed() { minimize(); }
        });
    boolean mediaPermissionsReady = hasPermissions(getIntent());
    handleIntent(getIntent());
    if (mediaPermissionsReady
        && !value(getIntent(), CallActivityContract.EXTRA_CALL_ID).isEmpty()
        && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
        && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED)
      phoneStatePermission.launch(Manifest.permission.READ_PHONE_STATE);
  }

  @Override protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    handleIntent(intent);
  }

  private void handleIntent(Intent intent) {
    String callId = value(intent, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALL_ID);
    boolean incoming = !value(intent,
        com.w3n.pinggo.call.session.CallActivityContract.EXTRA_SDP_OFFER).isEmpty()
        || intent.getBooleanExtra(
            com.w3n.pinggo.call.session.CallActivityContract.EXTRA_INCOMING, false);
    if (incoming && !intent.getBooleanExtra(
        com.w3n.pinggo.call.session.CallActivityContract.EXTRA_AUTO_ACCEPT, false))
      PingGoNotificationManager.markCallNotificationOpened(this, intent);
    if (callId.isEmpty()) {
      CallSession active = registry.active();
      if (active == null) finish(); else show(active);
      return;
    }
    CallSession existing = registry.get(callId);
    if (existing != null) {
      show(existing);
      if (intent.getBooleanExtra(com.w3n.pinggo.call.session.CallActivityContract.EXTRA_AUTO_ACCEPT, false))
        acceptWithPermissions(existing);
      return;
    }
    if (!incoming && !hasPermissions(intent)) {
      pendingPermissionIntent = new Intent(intent);
      requestPermissions(intent);
      return;
    }
    CallSession session = CallSessionFactory.create(this, intent);
    registry.put(session);
    show(session);
    if (incoming && isVideo(intent) && !hasPermissions(intent)) {
      pendingPermissionIntent = session.sourceIntent();
      pendingPermissionAccept = false;
      requestPermissions(pendingPermissionIntent);
    }
    if (intent.getBooleanExtra(com.w3n.pinggo.call.session.CallActivityContract.EXTRA_AUTO_ACCEPT, false))
      acceptWithPermissions(session);
  }

  private void show(@NonNull CallSession session) {
    if (displayed == session && (voiceView != null || videoView != null)) {
      registerActiveSession(session);
      render(session.state());
      return;
    }
    if (displayed != null) displayed.removeObserver(this);
    tones.stop();
    pipEligible = false;
    CallPictureInPicture.configure(this, false);
    lastTransientMessage = "";
    releaseViews();
    displayed = session;
    displayed.addObserver(this);
    boolean video = "video".equals(session.mediaType());
    String name = value(session.sourceIntent(), com.w3n.pinggo.call.session.CallActivityContract.EXTRA_PHONE_NUMBER);
    String profile = value(session.sourceIntent(), com.w3n.pinggo.call.session.CallActivityContract.EXTRA_PROFILE_PATH);
    if (video) buildVideo(name, profile); else buildVoice(name, profile);
    render(session.state());
    registerActiveSession(session);
    beginFloatingFallback(session, name, profile);
  }

  private void beginFloatingFallback(CallSession session, String name, String profile) {
    FloatingVoiceCallController.getInstance().clear();
    FloatingVideoCallController.getInstance().clear();
    if ("video".equals(session.mediaType()) && session instanceof LegacyVideoCallSession) {
      LegacyVideoCallSession video = (LegacyVideoCallSession) session;
      FloatingVideoCallController.getInstance().begin(
          () -> session.end("floating_hangup"), video::attachRemoteSurface);
    } else {
      FloatingVoiceCallController.getInstance().begin(name, profile,
          () -> session.end("floating_hangup"));
    }
  }

  private void registerActiveSession(@NonNull CallSession session) {
    if (session != registry.active()) return;
    ActiveCallRegistry.getInstance().activate(this, session.chatId(),
        "video".equals(session.mediaType()) ? ActiveCallRegistry.TYPE_VIDEO
            : ActiveCallRegistry.TYPE_VOICE);
  }

  private void buildVoice(String name, String profile) {
    voiceView = new VoiceActiveCallView(this, name, profile, this);
    voiceView.setAddMemberVisible(displayed instanceof LiveKitCallSession);
    boolean tiledConference = displayed instanceof LiveKitCallSession
        && ((LiveKitCallSession) displayed).showsParticipantGrid();
    if (displayed instanceof LiveKitCallSession) {
      voiceView.setConferenceParticipants(tiledConference, "");
      voiceView.setParticipantTileMode(tiledConference);
    }
    if (tiledConference) {
      videoRoot = new FrameLayout(this);
      videoRoot.setBackgroundColor(0xFFF7F9FB);
      liveKitGrid = createLiveKitGrid();
      videoRoot.addView(liveKitGrid, new FrameLayout.LayoutParams(-1, -1));
      videoRoot.addView(voiceView, new FrameLayout.LayoutParams(-1, -1));
      setContentView(videoRoot);
      handler.post(liveKitVideoSync);
    } else {
      setContentView(voiceView);
    }
    applyInsets(voiceView);
  }

  private void buildVideo(String name, String profile) {
    videoRoot = new FrameLayout(this);
    videoRoot.setBackgroundColor(0xFFF7F9FB);
    if (displayed instanceof LiveKitCallSession) {
      if (((LiveKitCallSession) displayed).showsParticipantGrid()) {
        liveKitGrid = createLiveKitGrid();
        videoRoot.addView(liveKitGrid, new FrameLayout.LayoutParams(-1, -1));
      } else {
        liveKitDirectRemote = new FrameLayout(this);
        liveKitDirectRemote.setBackgroundColor(0xFFF7F9FB);
        videoRoot.addView(liveKitDirectRemote, new FrameLayout.LayoutParams(-1, -1));
      }
    } else {
      remoteSurface = new SurfaceView(this);
      videoRoot.addView(remoteSurface, new FrameLayout.LayoutParams(-1, -1));
      localSurface = new SurfaceView(this);
      localSurface.setZOrderMediaOverlay(true);
      FrameLayout.LayoutParams local = localVideoLayout(false);
      videoRoot.addView(localSurface, local);
      localCameraOffView = cameraDisabledView();
      localCameraOffView.setLayoutParams(new FrameLayout.LayoutParams(local));
      localCameraOffView.setVisibility(View.GONE);
      videoRoot.addView(localCameraOffView);
    }
    videoView = new VideoActiveCallView(this, name, profile, this);
    videoView.setAddMemberVisible(displayed instanceof LiveKitCallSession);
    if (displayed instanceof LiveKitCallSession)
      videoView.setConferenceParticipants(
          ((LiveKitCallSession) displayed).showsParticipantGrid(), "");
    videoRoot.addView(videoView, new FrameLayout.LayoutParams(-1, -1));
    setContentView(videoRoot);
    applyInsets(videoView);
    if (displayed instanceof LegacyVideoCallSession) {
      LegacyVideoCallSession legacy = (LegacyVideoCallSession) displayed;
      localSurface.getHolder().addCallback(new SurfaceBinder(legacy, true));
      remoteSurface.getHolder().addCallback(new SurfaceBinder(legacy, false));
      if (hasPermissions(displayed.sourceIntent())) legacy.onPermissionsReady();
      legacy.onForeground();
      updateLegacyRotation();
    } else if (displayed instanceof LiveKitCallSession) {
      handler.post(liveKitVideoSync);
    }
  }

  private View cameraDisabledView() {
    TextView view = new TextView(this);
    view.setBackgroundColor(Color.BLACK);
    view.setTextColor(Color.WHITE);
    view.setTextSize(14f);
    view.setGravity(Gravity.CENTER);
    view.setText("Camera disabled");
    return view;
  }

  private void syncLiveKitVideo(LiveKitCallSession session) {
    if (liveKitGrid == null && liveKitDirectRemote == null) return;
    boolean tiledMode = session.showsParticipantGrid();
    if (tiledMode && liveKitGrid == null) promoteToConferenceLayout();
    else if (!tiledMode && liveKitGrid != null && videoView != null)
      demoteToDirectLayout();
    Room room = session.room();
    boolean conference = tiledMode;
    Set<String> active = new HashSet<>();
    String ownId = normalizedParticipantId(LoginStateManager.getInstance().getUID(this));
    knownLiveKitParticipants.clear();
    for (String participantId : session.participantIds()) {
      String normalized = normalizedParticipantId(participantId);
      if (!normalized.isEmpty() && !normalized.equals(ownId)
          && !session.participantDeparted(normalized))
        knownLiveKitParticipants.add(normalized);
    }
    String peerId = normalizedParticipantId(value(session.sourceIntent(),
        com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALLER_ID));
    if (!peerId.isEmpty() && !peerId.equals(ownId) && !session.participantDeparted(peerId))
      knownLiveKitParticipants.add(peerId);
    if (conference) {
      for (String participantId : knownLiveKitParticipants) {
        ensureLiveKitTile(session, participantId, participantId);
        ParticipantTile tile = liveKitTiles.get(participantId);
        if (tile != null && session.participantPending(participantId)) {
          if (tile.setRingingState())
            Log.i("PingGoInviteTile", "tile_rendered callId=" + session.callId()
                + " participant=" + participantId + " state=ringing");
        }
      }
    }

    boolean directMicOff = false;
    boolean directCameraOff = true;
    boolean foundDirectParticipant = false;

    for (Map.Entry<io.livekit.android.room.participant.Participant.Identity,
        RemoteParticipant> entry : room.getRemoteParticipants().entrySet()) {
      RemoteParticipant participant = entry.getValue();
      String identity = entry.getKey() == null ? "" : entry.getKey().getValue();
      String normalized = normalizedParticipantId(identity);
      String key = normalized.isEmpty()
          ? "remote_" + System.identityHashCode(participant) : normalized;
      if (session.participantDeparted(key)) {
        removeLiveKitTile(key);
        continue;
      }
      active.add(key);
      if (!normalized.isEmpty()) knownLiveKitParticipants.add(normalized);
      if (conference) ensureLiveKitTile(session, key, normalized);

      TrackPublication publication = participant.getTrackPublication(Track.Source.CAMERA);
      TrackPublication microphone = participant.getTrackPublication(Track.Source.MICROPHONE);
      Object rawTrack = publication == null ? null : publication.getTrack();
      ParticipantTile tile = liveKitTiles.get(key);
      if (tile != null) tile.setMediaState(microphone == null || microphone.getMuted(),
          "video".equals(session.mediaType())
              && (publication == null || publication.getMuted()
                  || !(rawTrack instanceof VideoTrack)));
      if (!conference && !foundDirectParticipant) {
        foundDirectParticipant = true;
        directMicOff = microphone == null || microphone.getMuted();
        directCameraOff = publication == null || publication.getMuted()
            || !(rawTrack instanceof VideoTrack);
      }
      if (!(rawTrack instanceof VideoTrack) || publication.getMuted()) {
        removeLiveKitTrack(key);
        continue;
      }
      RenderedLiveKitTrack rendered = liveKitTracks.get(key);
      if (rendered != null && rendered.track == rawTrack) {
        attachLiveKitRemoteRenderer(rendered.renderer, tile, conference);
        continue;
      }
      removeLiveKitTrack(key);
      TextureViewRenderer renderer = new TextureViewRenderer(this);
      room.initVideoRenderer(renderer);
      renderer.setScalingType(livekit.org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL);
      ((VideoTrack) rawTrack).addRenderer(renderer);
      liveKitTracks.put(key, new RenderedLiveKitTrack((VideoTrack) rawTrack, renderer));
      attachLiveKitRemoteRenderer(renderer, tile, conference);
    }

    for (String key : new ArrayList<>(liveKitTiles.keySet())) {
      if (!active.contains(key) && !knownLiveKitParticipants.contains(key))
        removeLiveKitTile(key);
    }
    for (String key : new ArrayList<>(liveKitTracks.keySet()))
      if (!active.contains(key)) removeLiveKitTrack(key);
    if (conference) {
      applySharedTileOrder(session.tileOrder());
      layoutLiveKitGrid();
    }

    ArrayList<String> participantNames = new ArrayList<>();
    for (String participantId : knownLiveKitParticipants)
      participantNames.add(DeviceContactResolver.cachedNameOrPhone(participantId));
    String names = android.text.TextUtils.join(", ", participantNames);
    if (voiceView != null) {
      voiceView.setParticipantTileMode(tiledMode);
      voiceView.setConferenceParticipants(tiledMode, names);
    }
    if (videoView != null) {
      videoView.setConferenceParticipants(conference, names);
      if (!conference) {
        videoView.setRemoteMuted(directMicOff);
        videoView.setRemoteCameraEnabled(foundDirectParticipant && !directCameraOff);
      }
    }

    TrackPublication localPublication = room.getLocalParticipant()
        .getTrackPublication(Track.Source.CAMERA);
    Object localTrack = localPublication == null ? null : localPublication.getTrack();
    if (!(localTrack instanceof VideoTrack)) localTrack = session.localPreviewTrack();
    if (localTrack instanceof VideoTrack
        && (liveKitLocalTrack == null || liveKitLocalTrack.track != localTrack)) {
      removeLiveKitLocalTrack();
      TextureViewRenderer renderer = new TextureViewRenderer(this);
      room.initVideoRenderer(renderer);
      renderer.setMirror(true);
      renderer.setScalingType(livekit.org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL);
      ((VideoTrack) localTrack).addRenderer(renderer);
      FrameLayout.LayoutParams local = liveKitLocalVideoLayout();
      videoRoot.addView(renderer, Math.max(0, videoRoot.indexOfChild(videoView)), local);
      liveKitLocalTrack = new RenderedLiveKitTrack((VideoTrack) localTrack, renderer);
      if (pipLayoutActive) ensurePipLocalTile();
    } else if (!(localTrack instanceof VideoTrack)) removeLiveKitLocalTrack();
    updateVideoPresentation(session.state());
    if (videoView != null) videoView.bringToFront();
  }

  private GridLayout createLiveKitGrid() {
    GridLayout grid = new DiagnosticGridLayout(this);
    grid.setBackgroundColor(0xFFF7F9FB);
    grid.setColumnCount(2);
    grid.setClipChildren(false);
    grid.setClipToPadding(false);
    return grid;
  }

  private void promoteToConferenceLayout() {
    if (videoRoot == null || liveKitGrid != null) return;
    liveKitGrid = createLiveKitGrid();
    int overlayIndex = videoView == null ? videoRoot.getChildCount()
        : Math.max(0, videoRoot.indexOfChild(videoView));
    videoRoot.addView(liveKitGrid, overlayIndex, new FrameLayout.LayoutParams(-1, -1));
    if (liveKitDirectRemote != null && liveKitDirectRemote.getParent() == videoRoot)
      videoRoot.removeView(liveKitDirectRemote);
    liveKitDirectRemote = null;
  }

  private void demoteToDirectLayout() {
    if (videoRoot == null || liveKitGrid == null) return;
    removePipLocalTile();
    liveKitDirectRemote = new FrameLayout(this);
    liveKitDirectRemote.setBackgroundColor(0xFFF7F9FB);
    int gridIndex = Math.max(0, videoRoot.indexOfChild(liveKitGrid));
    videoRoot.addView(liveKitDirectRemote, gridIndex,
        new FrameLayout.LayoutParams(-1, -1));
    for (RenderedLiveKitTrack rendered : liveKitTracks.values())
      attachLiveKitRemoteRenderer(rendered.renderer, null, false);
    for (ParticipantTile tile : liveKitTiles.values()) {
      if (tile.getParent() instanceof ViewGroup)
        ((ViewGroup) tile.getParent()).removeView(tile);
      tile.release();
    }
    liveKitTiles.clear();
    liveKitVisualOrder.clear();
    if (liveKitGrid.getParent() == videoRoot) videoRoot.removeView(liveKitGrid);
    liveKitGrid = null;
  }

  private void attachLiveKitRemoteRenderer(TextureViewRenderer renderer,
      ParticipantTile tile, boolean conference) {
    ViewGroup destination = conference ? tile : liveKitDirectRemote;
    if (destination == null || renderer.getParent() == destination) return;
    if (renderer.getParent() instanceof ViewGroup)
      ((ViewGroup) renderer.getParent()).removeView(renderer);
    destination.addView(renderer, 0, new FrameLayout.LayoutParams(-1, -1));
  }

  private void layoutLiveKitGrid() {
    if (liveKitGrid == null) return;
    ArrayList<View> children = orderedLiveKitChildren();
    int count = children.size();
    if (count == 0) return;
    int columns = count <= 2 ? 1 : 2;
    int rows = count == 1 ? 1 : count == 2 ? 2 : (count + 1) / 2;
    int safeColumns = Math.max(columns, Math.max(count, liveKitGrid.getColumnCount()));
    int safeRows = Math.max(rows, Math.max(count, liveKitGrid.getRowCount()));
    liveKitGrid.setColumnCount(safeColumns);
    liveKitGrid.setRowCount(safeRows);
    int width = liveKitGrid.getWidth() > 0 ? liveKitGrid.getWidth()
        : videoRoot != null && videoRoot.getWidth() > 0 ? videoRoot.getWidth()
        : getResources().getDisplayMetrics().widthPixels;
    int height = liveKitGrid.getHeight() > 0 ? liveKitGrid.getHeight()
        : videoRoot != null && videoRoot.getHeight() > 0 ? videoRoot.getHeight()
        : getResources().getDisplayMetrics().heightPixels;
    for (int index = 0; index < count; index++) {
      boolean lastWide = columns == 2 && count % 2 == 1 && index == count - 1;
      int row = columns == 1 ? index : index / 2;
      int column = columns == 1 ? 0 : index % 2;
      GridLayout.LayoutParams params = new GridLayout.LayoutParams(
          GridLayout.spec(row, 1, 1f),
          GridLayout.spec(lastWide ? 0 : column, lastWide ? 2 : 1, 1f));
      int margin = dp(2);
      params.width = Math.max(1,
          (lastWide || columns == 1 ? width : width / 2) - margin * 2);
      params.height = Math.max(1,
          (count == 1 ? height : height / rows) - margin * 2);
      params.setMargins(margin, margin, margin, margin);
      View child = children.get(index);
      child.setLayoutParams(params);
      if (child instanceof ParticipantTile) {
        boolean horizontalSwap = columns == 2 && index % 2 == 0 && index + 1 < count;
        ((ParticipantTile) child).setSwapAvailability(index < count - 1, horizontalSwap);
      }
    }
    liveKitGrid.setColumnCount(columns);
    liveKitGrid.setRowCount(rows);
    liveKitGrid.requestLayout();
    liveKitGrid.invalidate();
  }

  private ArrayList<View> orderedLiveKitChildren() {
    ArrayList<View> children = new ArrayList<>();
    for (String key : liveKitVisualOrder) {
      ParticipantTile tile = liveKitTiles.get(key);
      if (tile != null && tile.getParent() == liveKitGrid && !children.contains(tile))
        children.add(tile);
    }
    for (Map.Entry<String, ParticipantTile> entry : liveKitTiles.entrySet()) {
      ParticipantTile tile = entry.getValue();
      if (tile.getParent() == liveKitGrid && !children.contains(tile)) {
        children.add(tile);
        if (!liveKitVisualOrder.contains(entry.getKey()))
          liveKitVisualOrder.add(entry.getKey());
      }
    }
    if (pipLocalTile != null && pipLocalTile.getParent() == liveKitGrid)
      children.add(pipLocalTile);
    return children;
  }

  private void ensureLiveKitTile(LiveKitCallSession session, String key, String identity) {
    if (liveKitGrid == null || liveKitTiles.containsKey(key)) return;
    String preferredProfile = normalizedParticipantId(identity).equals(normalizedParticipantId(
        value(session.sourceIntent(),
            com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALLER_ID)))
        ? value(session.sourceIntent(),
            com.w3n.pinggo.call.session.CallActivityContract.EXTRA_PROFILE_PATH) : null;
    ParticipantTile tile = new ParticipantTile(this, identity, preferredProfile,
        "video".equals(session.mediaType()));
    tile.setOrderKey(key);
    liveKitTiles.put(key, tile);
    if (!liveKitVisualOrder.contains(key)) liveKitVisualOrder.add(key);
    tile.enableSwapControl(() -> moveLiveKitTile(tile));
    liveKitGrid.addView(tile);
    layoutLiveKitGrid();
  }

  private void removeLiveKitTile(String key) {
    removeLiveKitTrack(key);
    ParticipantTile tile = liveKitTiles.remove(key);
    liveKitVisualOrder.remove(key);
    if (tile == null) return;
    Log.i("PingGoInviteTile", "tile_removed callId=" + displayedCallId()
        + " participant=" + key);
    if (tile.getParent() instanceof ViewGroup) ((ViewGroup) tile.getParent()).removeView(tile);
    tile.release();
    layoutLiveKitGrid();
  }

  private void removeLiveKitTrack(String key) {
    RenderedLiveKitTrack rendered = liveKitTracks.remove(key);
    if (rendered == null) return;
    rendered.track.removeRenderer(rendered.renderer);
    if (rendered.renderer.getParent() instanceof ViewGroup)
      ((ViewGroup) rendered.renderer.getParent()).removeView(rendered.renderer);
    rendered.renderer.release();
  }
  private void removeLiveKitLocalTrack() {
    if (liveKitLocalTrack == null) return;
    liveKitLocalTrack.track.removeRenderer(liveKitLocalTrack.renderer);
    if (liveKitLocalTrack.renderer.getParent() instanceof android.view.ViewGroup)
      ((android.view.ViewGroup) liveKitLocalTrack.renderer.getParent())
          .removeView(liveKitLocalTrack.renderer);
    liveKitLocalTrack.renderer.release();
    liveKitLocalTrack = null;
  }

  private void moveLiveKitTile(ParticipantTile tile) {
    if (liveKitGrid == null || tile == null) {
      Log.w(TILE_SWAP_TAG, "swap_ignored reason=missing_grid_or_tile grid="
          + (liveKitGrid != null) + " tile=" + (tile != null));
      return;
    }
    String key = tile.orderKey();
    int from = liveKitVisualOrder.indexOf(key);
    int target = Math.min(liveKitVisualOrder.size() - 1, from + 1);
    String oldPositions = tilePositionsSnapshot();
    if (from < 0 || target == from) {
      Log.w(TILE_SWAP_TAG, "swap_ignored callId=" + displayedCallId()
          + " key=" + key + " reason=" + (from < 0 ? "key_not_in_order" : "last_tile")
          + " from=" + from + " target=" + target + " positions=" + oldPositions);
      return;
    }
    String next = liveKitVisualOrder.get(target);
    liveKitVisualOrder.set(target, key);
    liveKitVisualOrder.set(from, next);
    layoutLiveKitGrid();
    Log.i(TILE_SWAP_TAG, "swap_applied callId=" + displayedCallId()
        + " moved=" + key + " swappedWith=" + next
        + " oldIndex=" + from + " newIndex=" + target
        + " oldPositions=" + oldPositions
        + " newPositions=" + tilePositionsSnapshot());
    publishSharedTileOrder();
  }

  private void publishSharedTileOrder() {
    if (!(displayed instanceof LiveKitCallSession) || liveKitGrid == null) return;
    ArrayList<String> order = new ArrayList<>();
    for (String key : liveKitVisualOrder) {
      ParticipantTile tile = liveKitTiles.get(key);
      if (tile != null && tile.getParent() == liveKitGrid && !order.contains(key))
        order.add(key);
    }
    String ownId = normalizedParticipantId(LoginStateManager.getInstance().getUID(this));
    if (!ownId.isEmpty() && !order.contains(ownId)) order.add(ownId);
    Log.i(TILE_SWAP_TAG, "order_publish callId=" + displayedCallId()
        + " order=" + order);
    ((LiveKitCallSession) displayed).updateTileOrder(order);
  }

  private void applySharedTileOrder(ArrayList<String> order) {
    if (order.isEmpty()) return;
    if (liveKitGrid == null) {
      Log.w(TILE_SWAP_TAG, "order_apply_ignored callId=" + displayedCallId()
          + " reason=missing_grid order=" + order);
      return;
    }
    String oldPositions = tilePositionsSnapshot();
    ArrayList<String> nextOrder = new ArrayList<>();
    for (String value : order) {
      String key = normalizedParticipantId(value);
      ParticipantTile tile = liveKitTiles.get(key);
      if (tile != null && tile.getParent() == liveKitGrid && !nextOrder.contains(key))
        nextOrder.add(key);
    }
    for (String key : liveKitVisualOrder)
      if (liveKitTiles.containsKey(key) && !nextOrder.contains(key)) nextOrder.add(key);
    for (String key : liveKitTiles.keySet())
      if (!nextOrder.contains(key)) nextOrder.add(key);
    liveKitVisualOrder.clear();
    liveKitVisualOrder.addAll(nextOrder);
    String newPositions = tilePositionsSnapshot();
    if (!oldPositions.equals(newPositions))
      Log.i(TILE_SWAP_TAG, "order_applied callId=" + displayedCallId()
          + " received=" + order + " oldPositions=" + oldPositions
          + " newPositions=" + newPositions);
  }

  private String displayedCallId() {
    return displayed == null ? "" : displayed.callId();
  }

  private String tilePositionsSnapshot() {
    ArrayList<View> children = orderedLiveKitChildren();
    int count = children.size();
    int columns = count <= 2 ? 1 : 2;
    ArrayList<String> positions = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      View child = children.get(index);
      String key = child instanceof ParticipantTile
          ? ((ParticipantTile) child).orderKey() : "local_pip";
      int row = columns == 1 ? index : index / 2;
      int column = columns == 1 ? 0 : index % 2;
      positions.add(key + "@" + index + "(r" + row + ",c" + column + ")");
    }
    return positions.toString();
  }

  private static boolean shouldLogTouch(MotionEvent event) {
    int action = event.getActionMasked();
    return action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP
        || action == MotionEvent.ACTION_CANCEL;
  }

  private static String touchAction(MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN: return "DOWN";
      case MotionEvent.ACTION_UP: return "UP";
      case MotionEvent.ACTION_CANCEL: return "CANCEL";
      default: return String.valueOf(event.getActionMasked());
    }
  }

  private final class DiagnosticGridLayout extends GridLayout {
    DiagnosticGridLayout(android.content.Context context) { super(context); }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
      String hit = "none";
      for (View child : orderedLiveKitChildren()) {
        if (event.getX() >= child.getLeft() && event.getX() < child.getRight()
            && event.getY() >= child.getTop() && event.getY() < child.getBottom()) {
          hit = child instanceof ParticipantTile
              ? ((ParticipantTile) child).orderKey() : child.getClass().getSimpleName();
          break;
        }
      }
      if (shouldLogTouch(event))
        Log.i(TILE_SWAP_TAG, "touch_grid_enter action=" + touchAction(event)
            + " x=" + event.getX() + " y=" + event.getY() + " hit=" + hit);
      boolean handled = super.dispatchTouchEvent(event);
      if (shouldLogTouch(event))
        Log.i(TILE_SWAP_TAG, "touch_grid_exit action=" + touchAction(event)
            + " handled=" + handled + " hit=" + hit);
      return handled;
    }
  }

  private void applyInsets(View view) {
    ViewCompat.setOnApplyWindowInsetsListener(view, (target, insets) -> {
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
      if (target instanceof VoiceActiveCallView)
        ((VoiceActiveCallView) target).setInsets(bars.top, bars.bottom);
      if (target instanceof VideoActiveCallView)
        ((VideoActiveCallView) target).setInsets(bars.top, bars.bottom);
      return insets;
    });
    ViewCompat.requestApplyInsets(view);
  }

  private void render(CallSessionState state) {
    if (displayed == null) return;
    handler.removeCallbacks(callTimer);
    Intent source = displayed.sourceIntent();
    boolean wasIncoming = !value(source, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_SDP_OFFER).isEmpty()
        || source.getBooleanExtra(com.w3n.pinggo.call.session.CallActivityContract.EXTRA_INCOMING, false);
    boolean incoming = wasIncoming && (state.phase == CallSessionState.Phase.INCOMING
        || state.phase == CallSessionState.Phase.RINGING);
    tones.show(state.phase, wasIncoming);
    com.w3n.pinggo.call.session.CallAudioRouter.apply(this, state.speakerEnabled);
    boolean established = isEstablished(state);
    String status = displayStatus(state);
    updateHeldCallPresentation();
    if (voiceView != null) {
      voiceView.setCallStatus(status);
      voiceView.setAudioState(state.speakerEnabled, state.muted);
      voiceView.setHeld(state.phase == CallSessionState.Phase.HELD);
      voiceView.setRemoteMuted(state.peerMuted);
      voiceView.setPeerHeld(isDirectPeerHeld(state));
      voiceView.setCallConnected(established);
      if (incoming) voiceView.showIncomingPrompt(); else voiceView.hideIncomingPrompt();
    }
    if (videoView != null) {
      videoView.setCallStatus(status);
      videoView.setAudioState(state.speakerEnabled, state.muted);
      videoView.setHeld(state.phase == CallSessionState.Phase.HELD);
      videoView.setCameraEnabled(state.cameraEnabled);
      videoView.setRemoteMuted(state.peerMuted);
      boolean directLiveKit = displayed instanceof LiveKitCallSession
          && !((LiveKitCallSession) displayed).showsParticipantGrid();
      if (!directLiveKit) videoView.setRemoteCameraEnabled(state.peerCameraEnabled);
      videoView.setCallOnHold(isMediaPaused(state));
      videoView.setPeerHeld(isDirectPeerHeld(state));
      videoView.setCallConnected(established);
      videoView.showIncomingPrompt(incoming);
      updateVideoPresentation(state);
    }
    if (established) handler.postDelayed(callTimer, 1000L);
    if ((established || state.phase == CallSessionState.Phase.CONNECTING
        || state.phase == CallSessionState.Phase.CALLING
        || state.phase == CallSessionState.Phase.RINGING) && !pipEligible) {
      pipEligible = true;
      CallPictureInPicture.configure(this, true);
    }
    String floatingStatus = displayStatus(state);
    FloatingVoiceCallController.getInstance().updateStatus(floatingStatus);
    FloatingVideoCallController.getInstance().updateStatus(floatingStatus);
    if (established) PingGoNotificationManager.clearCallNotification(this, displayed.callId());
    if (state.status.startsWith("Unable") && !state.status.equals(lastTransientMessage)) {
      lastTransientMessage = state.status;
      Toast.makeText(this, state.status, Toast.LENGTH_SHORT).show();
    }
    CallSession active = registry.active();
    ActiveCallRegistry.getInstance().setConnected(this, active != null
        && active.state().phase == CallSessionState.Phase.CONNECTED);
  }

  @Override public void onSessionChanged(@NonNull CallSession session,
      @NonNull CallSessionState state) {
    runOnUiThread(() -> {
      if (session != displayed) return;
      if (state.phase == CallSessionState.Phase.ENDED
          || state.phase == CallSessionState.Phase.FAILED) {
        tones.stop();
        PingGoNotificationManager.clearCallNotification(this, session.callId());
        if (state.phase == CallSessionState.Phase.FAILED) {
          Toast.makeText(this, state.status.isEmpty() ? "Call failed." : state.status,
              Toast.LENGTH_LONG).show();
        } else if (!state.status.isEmpty() && !"Call ended".equals(state.status)) {
          Toast.makeText(this, state.status, Toast.LENGTH_SHORT).show();
        }
        completeSession(session);
      } else {
        if (session instanceof LiveKitCallSession
            && ((!((LiveKitCallSession) session).showsParticipantGrid()
                    && liveKitGrid != null)
                || ("audio".equals(session.mediaType())
                    && ((LiveKitCallSession) session).showsParticipantGrid()
                    && liveKitGrid == null))) {
          Intent source = session.sourceIntent();
          releaseViews();
          String name = value(source,
              com.w3n.pinggo.call.session.CallActivityContract.EXTRA_PHONE_NUMBER);
          String profile = value(source,
              com.w3n.pinggo.call.session.CallActivityContract.EXTRA_PROFILE_PATH);
          if ("video".equals(session.mediaType())) buildVideo(name, profile);
          else buildVoice(name, profile);
          syncLiveKitVideo((LiveKitCallSession) session);
        }
        render(state);
      }
    });
  }
  @Override public void onSessionEnded(@NonNull CallSession session, @NonNull String reason) {
    runOnUiThread(() -> completeSession(session));
  }
  private void completeSession(@NonNull CallSession session) {
    if (!completedCallIds.add(session.callId())) return;
    PingGoNotificationManager.clearCallNotification(this, session.callId());
    registry.remove(session.callId());
    CallSession active = registry.active();
    if (active == null) {
      if (!isFinishing()) finish();
    } else if (active.state().phase == CallSessionState.Phase.HELD) {
      active.setHeld(false, () -> show(active));
    } else {
      show(active);
    }
  }
  @Override public void onActiveSessionChanged(CallSession session) {
    if (session != null && session.state().phase != CallSessionState.Phase.INCOMING)
      runOnUiThread(() -> show(session));
  }

  @Override public void onAccept() {
    if (displayed != null) acceptWithPermissions(displayed);
  }
  private void acceptWithPermissions(CallSession session) {
    if (!hasPermissions(session.sourceIntent())) {
      pendingPermissionIntent = session.sourceIntent();
      pendingPermissionAccept = true;
      requestPermissions(pendingPermissionIntent);
      return;
    }
    registry.activate(session.callId(), () -> {
      show(session);
      session.accept();
      PingGoNotificationManager.clearCallNotification(this, session.callId());
    });
  }
  @Override public void onReject() {
    if (displayed == null) return;
    tones.stop();
    PingGoNotificationManager.clearCallNotification(this, displayed.callId());
    displayed.reject();
  }
  @Override public void onSpeaker() {
    if (displayed != null) displayed.setSpeakerEnabled(!displayed.state().speakerEnabled);
  }
  @Override public void onMute() {
    if (displayed != null) displayed.setMuted(!displayed.state().muted);
  }
  @Override public void onHold() {
    if (displayed == null) return;
    if (displayed.state().phase == CallSessionState.Phase.HELD
        && CallSessionService.isExternalCallActive()) {
      Log.w("PingGoExternalCall", "resume_blocked callId=" + displayed.callId()
          + " reason=external_call_active");
      Toast.makeText(this, "Another call is using the microphone.", Toast.LENGTH_SHORT).show();
      return;
    }
    displayed.setHeld(displayed.state().phase != CallSessionState.Phase.HELD, () -> { });
  }

  @Override public void onSwapCall(String callId) {
    if (displayed == null || callId == null || callId.trim().isEmpty()) return;
    if (CallSessionService.isExternalCallActive()) {
      Log.w(CALL_SWAP_TAG, "swap_ignored activeCallId=" + displayed.callId()
          + " targetCallId=" + callId + " reason=external_call_active");
      Toast.makeText(this, "Finish the other call before switching.", Toast.LENGTH_SHORT).show();
      return;
    }
    CallSession target = registry.get(callId);
    if (target == null || target.state().phase != CallSessionState.Phase.HELD) {
      Log.w(CALL_SWAP_TAG, "swap_ignored activeCallId=" + displayed.callId()
          + " targetCallId=" + callId + " reason=target_not_held");
      updateHeldCallPresentation();
      return;
    }
    Log.i(CALL_SWAP_TAG, "swap_clicked activeCallId=" + displayed.callId()
        + " heldCallId=" + target.callId());
    registry.activate(target.callId(), () -> Log.i(CALL_SWAP_TAG,
        "swap_completed activeCallId="
            + (registry.active() == null ? "" : registry.active().callId())));
  }

  private void updateHeldCallPresentation() {
    ArrayList<String> ids = new ArrayList<>();
    ArrayList<String> names = new ArrayList<>();
    for (CallSession held : registry.heldSessions()) {
      if (displayed != null && held.callId().equals(displayed.callId())) continue;
      ids.add(held.callId());
      String name = value(held.sourceIntent(),
          com.w3n.pinggo.call.session.CallActivityContract.EXTRA_PHONE_NUMBER);
      names.add(name.isEmpty() ? "Another call" : name);
    }
    if (voiceView != null) voiceView.setHeldCalls(ids, names);
    if (videoView != null) videoView.setHeldCalls(ids, names);
  }
  @Override public void onEnd() { if (displayed != null) displayed.end("hangup"); }
  @Override public void onCamera() {
    if (displayed != null) displayed.setCameraEnabled(!displayed.state().cameraEnabled);
  }
  @Override public void onFlipCamera() {
    if (displayed instanceof LegacyVideoCallSession)
      ((LegacyVideoCallSession) displayed).flipCamera();
    else if (displayed instanceof LiveKitCallSession)
      ((LiveKitCallSession) displayed).flipCamera();
  }
  @Override public void onAddMember() {
    if (!(displayed instanceof LiveKitCallSession)) return;
    LiveKitCallSession session = (LiveKitCallSession) displayed;
    ArrayList<String> excluded = session.participantIds();
    String peer = value(session.sourceIntent(), com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALLER_ID);
    if (!peer.isEmpty() && !excluded.contains(peer)) excluded.add(peer);
    Intent picker = new Intent(this, NewChatActivity.class)
        .putExtra(NewChatActivity.EXTRA_SELECT_CALL_MEMBERS, true)
        .putStringArrayListExtra(NewChatActivity.EXTRA_EXCLUDED_MEMBER_IDS, excluded);
    memberPicker.launch(picker);
  }
  @Override public void onBack() { minimize(); }
  @Override public void onPictureInPictureHangup() { onEnd(); }

  private void minimize() {
    if (displayed == null) return;
    if (pipEligible && CallPictureInPicture.enter(this)) return;
    if ("video".equals(displayed.mediaType()) && displayed instanceof LegacyVideoCallSession)
      FloatingVideoCallController.getInstance().minimizeAndReturn(this);
    else FloatingVoiceCallController.getInstance().minimizeAndReturn(this);
  }
  @Override protected void onUserLeaveHint() {
    super.onUserLeaveHint();
    if (pipEligible) CallPictureInPicture.enter(this);
  }
  @Override public void onPictureInPictureModeChanged(boolean inPip,
      @NonNull Configuration config) {
    super.onPictureInPictureModeChanged(inPip, config);
    if (inPip) {
      pipSessionActive = true;
      pipActivityStopped = false;
      pipExitCheckAttempts = 0;
    } else if (pipSessionActive && pipActivityStopped && !hasWindowFocus()) {
      disconnectDismissedPip();
    } else if (!inPip) {
      schedulePipExitCheck();
    }
    applyPictureInPictureLayout(inPip);
      if (voiceView != null) voiceView.setPictureInPictureMode(inPip,
        liveKitGrid != null && displayed instanceof LiveKitCallSession
            && ((LiveKitCallSession) displayed).showsParticipantGrid());
  }

  @Override protected void onResume() {
    super.onResume();
    pipActivityStopped = false;
    if (!CallPictureInPicture.isActive(this)) {
      pipSessionActive = false;
      pipExitCheckAttempts = 0;
      applyPictureInPictureLayout(false);
      if (voiceView != null) voiceView.setPictureInPictureMode(false, false);
    }
    if (displayed instanceof LegacyVideoCallSession) {
      ((LegacyVideoCallSession) displayed).onForeground();
      updateLegacyRotation();
    }
  }

  @Override protected void onStop() {
    super.onStop();
    if (pipSessionActive) {
      pipActivityStopped = true;
      schedulePipExitCheck();
    } else if (displayed instanceof LegacyVideoCallSession
        && !FloatingVideoCallController.getInstance().isMinimized()) {
      ((LegacyVideoCallSession) displayed).onBackground();
    }
  }

  private void schedulePipExitCheck() {
    if (!pipSessionActive
        || pipExitCheckAttempts >= CallPictureInPicture.DISMISS_CONFIRMATION_MAX_ATTEMPTS) return;
    pipExitCheckAttempts++;
    handler.postDelayed(() -> {
      if (!pipSessionActive) return;
      if (!CallPictureInPicture.isActive(this)) disconnectDismissedPip();
      else schedulePipExitCheck();
    }, CallPictureInPicture.DISMISS_CONFIRMATION_MS);
  }

  private void disconnectDismissedPip() {
    if (!pipSessionActive) return;
    pipSessionActive = false;
    if (displayed != null) displayed.end("pip_dismissed");
  }

  private void applyPictureInPictureLayout(boolean pip) {
    pipLayoutActive = pip;
    if (videoRoot == null) return;
    if (videoView != null) videoView.setVisibility(pip ? View.GONE : View.VISIBLE);
    int width = Math.max(1, videoRoot.getWidth());
    int height = Math.max(1, videoRoot.getHeight());
    if (remoteSurface != null && localSurface != null) {
      if (pip) {
        FrameLayout.LayoutParams remote = new FrameLayout.LayoutParams(-1, height / 2,
            Gravity.TOP);
        FrameLayout.LayoutParams local = new FrameLayout.LayoutParams(-1, height - height / 2,
            Gravity.BOTTOM);
        remoteSurface.setLayoutParams(remote);
        localSurface.setLayoutParams(local);
        if (localCameraOffView != null) localCameraOffView.setLayoutParams(
            new FrameLayout.LayoutParams(local));
      } else {
        remoteSurface.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        localSurface.setLayoutParams(localVideoLayout(isEstablished(displayed.state())));
        if (localCameraOffView != null) localCameraOffView.setLayoutParams(
            new FrameLayout.LayoutParams(localVideoLayout(isEstablished(displayed.state()))));
      }
    }
    if (liveKitGrid != null) {
      if (pip) ensurePipLocalTile(); else removePipLocalTile();
      for (ParticipantTile tile : liveKitTiles.values()) tile.setCompact(pip);
      if (pipLocalTile != null) pipLocalTile.setCompact(true);
      layoutLiveKitGrid();
    }
    if (!pip && videoView != null) videoView.bringToFront();
  }

  private void ensurePipLocalTile() {
    if (liveKitGrid == null) return;
    if (pipLocalTile == null) {
      pipLocalTile = new ParticipantTile(this,
          LoginStateManager.getInstance().getUID(this), null,
          displayed != null && "video".equals(displayed.mediaType()));
      pipLocalTile.overrideName("You");
    }
    if (pipLocalTile.getParent() != liveKitGrid) {
      if (pipLocalTile.getParent() instanceof ViewGroup)
        ((ViewGroup) pipLocalTile.getParent()).removeView(pipLocalTile);
      liveKitGrid.addView(pipLocalTile);
    }
    if (liveKitLocalTrack != null)
      moveLiveKitLocalRenderer(pipLocalTile, new FrameLayout.LayoutParams(-1, -1), 0);
  }

  private void removePipLocalTile() {
    if (pipLocalTile == null) return;
    if (liveKitLocalTrack != null)
      moveLiveKitLocalRenderer(videoRoot, liveKitLocalVideoLayout(),
          Math.max(0, videoRoot.indexOfChild(videoView)));
    if (pipLocalTile.getParent() instanceof ViewGroup)
      ((ViewGroup) pipLocalTile.getParent()).removeView(pipLocalTile);
    pipLocalTile.release();
    pipLocalTile = null;
  }

  private void moveLiveKitLocalRenderer(ViewGroup destination,
      FrameLayout.LayoutParams params, int index) {
    if (liveKitLocalTrack == null || destination == null) return;
    TextureViewRenderer renderer = liveKitLocalTrack.renderer;
    if (renderer.getParent() instanceof ViewGroup)
      ((ViewGroup) renderer.getParent()).removeView(renderer);
    destination.addView(renderer, Math.min(Math.max(0, index), destination.getChildCount()), params);
    renderer.bringToFront();
  }

  private boolean hasPermissions(Intent intent) {
    boolean microphone = ContextCompat.checkSelfPermission(this,
        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    return microphone && (!isVideo(intent) || ContextCompat.checkSelfPermission(this,
        Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED);
  }
  private void requestPermissions(Intent intent) {
    permissions.launch(isVideo(intent)
        ? new String[] { Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA,
            Manifest.permission.READ_PHONE_STATE }
        : new String[] { Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE });
  }
  private void onPermissions(Map<String, Boolean> values) {
    startService(new Intent(this, CallSessionService.class));
    Intent pending = pendingPermissionIntent;
    boolean acceptAfterPermission = pendingPermissionAccept;
    pendingPermissionIntent = null;
    pendingPermissionAccept = false;
    if (pending == null) return;
    if (!hasPermissions(pending)) {
      Toast.makeText(this, "Call permissions are required.", Toast.LENGTH_LONG).show();
      String deniedCallId = value(pending,
          com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALL_ID);
      CallSession denied = registry.get(deniedCallId);
      PingGoNotificationManager.clearCallNotification(this, deniedCallId);
      if (denied != null) {
        if (denied.incoming()) denied.reject(); else denied.end("permission_denied");
        registry.remove(deniedCallId);
      }
      if (registry.active() == null && !isFinishing()) finish();
      return;
    }
    String callId = value(pending, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALL_ID);
    CallSession existing = registry.get(callId);
    if (existing == null) {
      handleIntent(pending);
    } else if (acceptAfterPermission) {
      acceptWithPermissions(existing);
    } else if (existing instanceof LegacyVideoCallSession) {
      ((LegacyVideoCallSession) existing).onPermissionsReady();
    } else if (existing instanceof LiveKitCallSession) {
      ((LiveKitCallSession) existing).onPermissionsReady();
    }
  }
  private boolean isVideo(Intent intent) {
    return "video".equals(value(intent, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_MEDIA_TYPE))
        || intent.getBooleanExtra(CallActivityContract.EXTRA_VIDEO, false);
  }

  private void releaseViews() {
    handler.removeCallbacks(callTimer);
    handler.removeCallbacks(liveKitVideoSync);
    for (String key : new java.util.ArrayList<>(liveKitTracks.keySet()))
      removeLiveKitTrack(key);
    for (String key : new ArrayList<>(liveKitTiles.keySet()))
      removeLiveKitTile(key);
    knownLiveKitParticipants.clear();
    liveKitVisualOrder.clear();
    removePipLocalTile();
    removeLiveKitLocalTrack();
    if (displayed instanceof LegacyVideoCallSession)
      ((LegacyVideoCallSession) displayed).onBackground();
    if (voiceView != null) voiceView.release();
    if (videoView != null) videoView.release();
    voiceView = null; videoView = null; videoRoot = null; liveKitGrid = null;
    liveKitDirectRemote = null;
    localSurface = null; remoteSurface = null; localCameraOffView = null;
  }
  @Override protected void onDestroy() {
    if (displayed != null) displayed.removeObserver(this);
    registry.removeListener(this);
    releaseViews();
    tones.stop();
    handler.removeCallbacksAndMessages(null);
    CallPictureInPicture.configure(this, false);
    if (registry.sessions().isEmpty()) {
      FloatingVoiceCallController.getInstance().clear();
      FloatingVideoCallController.getInstance().clear();
    }
    ActiveCallRegistry.getInstance().clear(this);
    super.onDestroy();
  }
  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private void updateVideoPresentation(CallSessionState state) {
    if (videoRoot == null) return;
    boolean established = isEstablished(state);
    boolean showRemoteVideo = established && !isMediaPaused(state);
    if (remoteSurface != null)
      remoteSurface.setVisibility(showRemoteVideo ? View.VISIBLE : View.GONE);
    if (liveKitGrid != null)
      liveKitGrid.setVisibility(isMediaPaused(state) ? View.GONE : View.VISIBLE);
    if (liveKitDirectRemote != null)
      liveKitDirectRemote.setVisibility(isMediaPaused(state) ? View.GONE : View.VISIBLE);
    if (localSurface != null) {
      localSurface.setVisibility(isMediaPaused(state) || !state.cameraEnabled
          ? View.GONE : View.VISIBLE);
      if (!pipLayoutActive) localSurface.setLayoutParams(localVideoLayout(established));
      localSurface.bringToFront();
    }
    if (localCameraOffView != null) {
      localCameraOffView.setVisibility(!isMediaPaused(state) && !state.cameraEnabled
          ? View.VISIBLE : View.GONE);
      if (!pipLayoutActive) localCameraOffView.setLayoutParams(localVideoLayout(established));
      if (localCameraOffView.getVisibility() == View.VISIBLE) localCameraOffView.bringToFront();
    }
    if (liveKitLocalTrack != null) {
      liveKitLocalTrack.renderer.setVisibility(
          isMediaPaused(state) || !state.cameraEnabled ? View.GONE : View.VISIBLE);
      if (!pipLayoutActive)
        liveKitLocalTrack.renderer.setLayoutParams(liveKitLocalVideoLayout());
      liveKitLocalTrack.renderer.bringToFront();
    }
    if (videoView != null && !pipLayoutActive) videoView.bringToFront();
  }

  private FrameLayout.LayoutParams localVideoLayout(boolean floating) {
    if (!floating) return new FrameLayout.LayoutParams(-1, -1);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(112), dp(154),
        android.view.Gravity.TOP | android.view.Gravity.END);
    params.topMargin = dp(16);
    params.rightMargin = dp(16);
    return params;
  }

  private FrameLayout.LayoutParams liveKitLocalVideoLayout() {
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(112), dp(154),
        Gravity.TOP | Gravity.END);
    params.topMargin = dp(92);
    params.rightMargin = dp(16);
    return params;
  }

  private static boolean isEstablished(CallSessionState state) {
    return state.phase == CallSessionState.Phase.CONNECTED
        || state.phase == CallSessionState.Phase.HELD;
  }

  private static String displayStatus(CallSessionState state) {
    if (!isEstablished(state) || state.connectedAtMs <= 0L) return state.status;
    long elapsed = Math.max(0L, System.currentTimeMillis() - state.connectedAtMs) / 1000L;
    String timer = elapsed >= 3600L
        ? String.format(Locale.US, "%02d:%02d:%02d", elapsed / 3600L,
            (elapsed % 3600L) / 60L, elapsed % 60L)
        : String.format(Locale.US, "%02d:%02d", elapsed / 60L, elapsed % 60L);
    if (state.phase == CallSessionState.Phase.HELD) return "On hold • " + timer;
    if (isHoldNotice(state.status)) return state.status + " • " + timer;
    return timer;
  }

  private static boolean isMediaPaused(CallSessionState state) {
    return state.phase == CallSessionState.Phase.HELD || isDirectPeerHeld(state);
  }

  private static boolean isDirectPeerHeld(CallSessionState state) {
    return state.phase != CallSessionState.Phase.HELD && "Call on hold".equals(state.status);
  }

  private static boolean isHoldNotice(String status) {
    return "Call on hold".equals(status) || "Participant is on hold".equals(status);
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
    if (square != source) square.recycle();
    source.recycle();
    return circle;
  }

  private final class SurfaceBinder implements SurfaceHolder.Callback {
    private final LegacyVideoCallSession session;
    private final boolean local;
    SurfaceBinder(LegacyVideoCallSession session, boolean local) {
      this.session = session; this.local = local;
    }
    @Override public void surfaceCreated(@NonNull SurfaceHolder holder) { bind(holder.getSurface()); }
    @Override public void surfaceChanged(@NonNull SurfaceHolder holder, int format,
        int width, int height) { updateLegacyRotation(); bind(holder.getSurface()); }
    @Override public void surfaceDestroyed(@NonNull SurfaceHolder holder) { bind(null); }
    private void bind(Surface value) {
      if (local) session.attachLocalSurface(value); else session.attachRemoteSurface(value);
    }
  }

  private void updateLegacyRotation() {
    if (!(displayed instanceof LegacyVideoCallSession)) return;
    int rotation = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
        ? getDisplay().getRotation() : getWindowManager().getDefaultDisplay().getRotation();
    ((LegacyVideoCallSession) displayed).setDisplayRotation(rotation * 90);
  }

  private static final class RenderedLiveKitTrack {
    final VideoTrack track;
    final TextureViewRenderer renderer;
    RenderedLiveKitTrack(VideoTrack track, TextureViewRenderer renderer) {
      this.track = track; this.renderer = renderer;
    }
  }

  private final class ParticipantTile extends FrameLayout {
    private final ZLayerGroup avatarLayers = new ZLayerGroup(this);
    private final ZLayerGroup labelLayers = new ZLayerGroup(this);
    private final ZLayer avatarLayer = avatarLayers.addLayer("participant_avatar");
    private final ZLayer labelLayer = labelLayers.addLayer("participant_label");
    private String displayName;
    private final Bitmap avatarBitmap;
    private final Bitmap avatarBackground = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    private final Bitmap labelBackground = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    private final boolean videoCall;
    private Text name;
    private TextView swapArrow;
    private boolean canSwap, horizontalSwap;
    private String orderKey = "";
    private String mediaLabel = "";

    ParticipantTile(android.content.Context context, String identity, String preferredProfilePath,
        boolean videoCall) {
      super(context);
      this.videoCall = videoCall;
      setBackgroundColor(0xFFF7F9FB);
      setWillNotDraw(false);
      setClickable(true);
      displayName = DeviceContactResolver.cachedNameOrPhone(identity);
      String profilePath = preferredProfilePath;
      if (profilePath == null || profilePath.trim().isEmpty())
        profilePath = ChatProfilePhotoStore.getLocalPath(context, identity);
      Bitmap bitmap = profilePath == null ? null : BitmapFactory.decodeFile(profilePath);
      avatarBitmap = bitmap == null ? null : circularBitmap(bitmap);
      avatarBackground.eraseColor(0xFF315063);
      labelBackground.eraseColor(0xE6FFFFFF);
    }

    void setOrderKey(String value) {
      orderKey = normalizedParticipantId(value);
    }

    String orderKey() { return orderKey; }

    void enableSwapControl(Runnable swapNext) {
      if (swapArrow != null) return;
      swapArrow = new TextView(getContext());
      swapArrow.setTextColor(Color.WHITE);
      swapArrow.setTextSize(20f);
      swapArrow.setGravity(Gravity.CENTER);
      android.graphics.drawable.GradientDrawable background =
          new android.graphics.drawable.GradientDrawable();
      background.setColor(0xD926333E);
      background.setCornerRadius(dp(18));
      background.setStroke(dp(1), 0x66019CC4);
      swapArrow.setBackground(background);
      swapArrow.setElevation(dp(20));
      swapArrow.setOnTouchListener((view, event) -> {
        if (shouldLogTouch(event))
          Log.i(TILE_SWAP_TAG, "touch_swap_control action=" + touchAction(event)
              + " key=" + orderKey + " x=" + event.getX() + " y=" + event.getY()
              + " enabled=" + view.isEnabled() + " clickable=" + view.isClickable());
        return false;
      });
      swapArrow.setOnClickListener(view -> {
        Log.i(TILE_SWAP_TAG, "button_clicked callId=" + displayedCallId()
            + " key=" + orderKey + " canSwap=" + canSwap
            + " horizontal=" + horizontalSwap + " compact=" + compact
            + " visibility=" + swapArrow.getVisibility());
        if (canSwap) swapNext.run();
        else Log.w(TILE_SWAP_TAG, "button_ignored callId=" + displayedCallId()
            + " key=" + orderKey + " reason=swap_not_available");
      });
      FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(44), dp(36),
          Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
      params.bottomMargin = dp(44);
      swapArrow.setLayoutParams(params);
      addView(swapArrow);
      setSwapAvailability(false, false);
    }

    void setSwapAvailability(boolean enabled, boolean horizontal) {
      canSwap = enabled;
      horizontalSwap = horizontal;
      if (swapArrow != null) {
        swapArrow.setText(horizontal ? "⇄" : "⇅");
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) swapArrow.getLayoutParams();
        params.gravity = horizontal ? Gravity.END | Gravity.CENTER_VERTICAL
            : Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.rightMargin = horizontal ? dp(6) : 0;
        params.bottomMargin = horizontal ? 0 : dp(44);
        swapArrow.setLayoutParams(params);
        swapArrow.setVisibility(!compact && enabled ? View.VISIBLE : View.GONE);
      }
    }

    void setMediaState(boolean micOff, boolean cameraOff) {
      String label = displayName + (micOff ? " • Mic off" : "")
          + (cameraOff ? " • Camera off" : "");
      if (label.equals(mediaLabel)) return;
      mediaLabel = label;
      if (name != null) name.setText(label);
    }

    boolean setRingingState() {
      String label = displayName + " • Ringing…";
      if (label.equals(mediaLabel)) return false;
      mediaLabel = label;
      if (name != null) name.setText(label);
      return true;
    }

    void overrideName(String value) {
      displayName = value == null ? "" : value;
      mediaLabel = displayName;
      buildNativeTile(getWidth(), getHeight());
    }

    private boolean compact;
    void setCompact(boolean value) {
      compact = value;
      setSwapAvailability(canSwap, horizontalSwap);
      buildNativeTile(getWidth(), getHeight());
    }

    void release() {
      avatarLayers.release();
      labelLayers.release();
      if (avatarBitmap != null && !avatarBitmap.isRecycled()) avatarBitmap.recycle();
      if (!avatarBackground.isRecycled()) avatarBackground.recycle();
      if (!labelBackground.isRecycled()) labelBackground.recycle();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
      super.onSizeChanged(width, height, oldWidth, oldHeight);
      buildNativeTile(width, height);
    }

    private void buildNativeTile(int width, int height) {
      if (width <= 0 || height <= 0) return;
      avatarLayer.clear();
      labelLayer.clear();
      float avatarSize = dp(compact ? 64 : videoCall ? 156 : 232);
      avatarSize = Math.min(avatarSize, Math.min(width, height) * 0.62f);
      float left = (width - avatarSize) / 2f;
      float top = (height - avatarSize) / 2f;
      avatarLayer.add(new Button.Builder(getContext(), "participant_avatar",
          avatarBitmap != null && !avatarBitmap.isRecycled() ? avatarBitmap : avatarBackground,
          "", new RectF(left, top, left + avatarSize, top + avatarSize))
          .setImageScaleType(Image.ScaleType.CENTER_CROP).setCornerRadiusPx(avatarSize / 2f)
          .setRippleEnabled(false));
      if (avatarBitmap == null || avatarBitmap.isRecycled()) {
        String initial = displayName == null || displayName.trim().isEmpty() ? "?"
            : displayName.trim().substring(0, 1).toUpperCase(Locale.ROOT);
        avatarLayer.add(new Text.Builder(getContext(), "participant_initial", initial,
            new RectF(left, top, left + avatarSize, top + avatarSize))
            .setFont(NativeFonts.INTER).setFontVariations(FontVariation.BOLD)
            .setTextColor(Color.WHITE).setTextSizePx(dp(compact ? 28 : videoCall ? 48 : 72))
            .setAlignment(Text.Alignment.CENTER)
            .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(1));
      }
      float labelHeight = dp(compact ? 22 : 40);
      labelLayer.add(new Button.Builder(getContext(), "participant_label_background",
          labelBackground, "", new RectF(0, height - labelHeight, width, height))
          .setRippleEnabled(false));
      name = labelLayer.add(new Text.Builder(getContext(), "participant_name",
          mediaLabel.isEmpty() ? displayName : mediaLabel,
          new RectF(dp(compact ? 4 : 12), height - labelHeight,
              width - dp(compact ? 4 : 12), height))
          .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
          .setTextColor(0xFF000E1A).setTextSizePx(dp(compact ? 10 : 14))
          .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(1));
      invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      avatarLayers.draw(canvas);
    }

    @Override protected void dispatchDraw(Canvas canvas) {
      super.dispatchDraw(canvas);
      labelLayers.draw(canvas);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
      boolean insideSwap = swapArrow != null && swapArrow.getVisibility() == View.VISIBLE
          && event.getX() >= swapArrow.getLeft() && event.getX() < swapArrow.getRight()
          && event.getY() >= swapArrow.getTop() && event.getY() < swapArrow.getBottom();
      if (shouldLogTouch(event))
        Log.i(TILE_SWAP_TAG, "touch_tile_enter action=" + touchAction(event)
            + " key=" + orderKey + " x=" + event.getX() + " y=" + event.getY()
            + " insideSwap=" + insideSwap + " swapBounds="
            + (swapArrow == null ? "none" : "[" + swapArrow.getLeft() + ","
                + swapArrow.getTop() + "-" + swapArrow.getRight() + ","
                + swapArrow.getBottom() + "]"));
      boolean handled = super.dispatchTouchEvent(event);
      if (shouldLogTouch(event))
        Log.i(TILE_SWAP_TAG, "touch_tile_exit action=" + touchAction(event)
            + " key=" + orderKey + " handled=" + handled
            + " insideSwap=" + insideSwap);
      return handled;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
      boolean intercepted = super.onInterceptTouchEvent(event);
      if (shouldLogTouch(event))
        Log.i(TILE_SWAP_TAG, "touch_tile_intercept action=" + touchAction(event)
            + " key=" + orderKey + " intercepted=" + intercepted);
      return intercepted;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
      boolean labelHandled = labelLayers.onTouchEvent(event);
      boolean avatarHandled = !labelHandled && avatarLayers.onTouchEvent(event);
      boolean fallbackHandled = !labelHandled && !avatarHandled && super.onTouchEvent(event);
      boolean handled = labelHandled || avatarHandled || fallbackHandled;
      if (shouldLogTouch(event))
        Log.i(TILE_SWAP_TAG, "touch_tile_fallback action=" + touchAction(event)
            + " key=" + orderKey + " label=" + labelHandled
            + " avatar=" + avatarHandled + " fallback=" + fallbackHandled
            + " handled=" + handled);
      return handled;
    }
  }
}
