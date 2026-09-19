package com.w3n.pinggo.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.util.Log;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.call.ActiveCallRegistry;
import com.w3n.pinggo.call.FloatingVideoCallController;
import com.w3n.pinggo.call.VideoCallController;
import com.w3n.pinggo.call.CallPictureInPicture;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.notification.PingGoNotificationManager;
import com.w3n.pinggo.views.call.VideoActiveCallView;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import java.util.Map;

public class VideoCallActivity extends AppCompatActivity implements VideoActiveCallView.Listener,
    VideoCallController.Listener, ActiveCallRegistry.PictureInPictureHangupListener {
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig = new com.ogfa.nativeviews.component.FigmaConfig(
      1080f);
  private static final String TAG = "PingGoVideoCall";
  private VideoActiveCallView callView;
  private AudioManager audioManager;
  private VideoCallController controller;
  private FrameLayout videoRoot;
  private SurfaceView remoteSurface, localSurface;
  private View remoteCameraOffView, localCameraOffView;
  private final java.util.List<ZLayerGroup> cameraDisabledLayers = new java.util.ArrayList<>();
  private boolean speakerOn = true;
  private boolean callConnected;
  private boolean pipEligible;
  private boolean pipLayoutActive;
  private boolean pipSessionActive;
  private boolean pipActivityStopped;
  private int pipExitCheckAttempts;
  private final Handler toneHandler = new Handler(Looper.getMainLooper());
  private Ringtone incomingRingtone;
  private ToneGenerator outgoingTone;
  private boolean incomingToneActive, outgoingToneActive;
  private final Runnable incomingToneLoop = new Runnable() {
    @Override
    public void run() {
      if (!incomingToneActive)
        return;
      if (incomingRingtone != null && !incomingRingtone.isPlaying())
        incomingRingtone.play();
      toneHandler.postDelayed(this, 2_000L);
    }
  };
  private final Runnable outgoingToneLoop = new Runnable() {
    @Override
    public void run() {
      if (!outgoingToneActive || outgoingTone == null)
        return;
      outgoingTone.startTone(ToneGenerator.TONE_SUP_RINGTONE, 2_500);
      toneHandler.postDelayed(this, 4_000L);
    }
  };
  private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(
      new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionsResult);

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    String offer = getIntent().getStringExtra(VoiceCallActivity.EXTRA_SDP_OFFER);
    String selectedEngine = getIntent().getStringExtra(VoiceCallActivity.EXTRA_CALL_ENGINE);
    if ((offer == null || offer.isEmpty()) && (selectedEngine == null || selectedEngine.isEmpty())) {
      com.w3n.pinggo.call.CallEngineChooser.show(this, "video",
          getIntent().getStringExtra(VoiceCallActivity.EXTRA_CALL_CHAT_ID), engine -> {
            getIntent().putExtra(VoiceCallActivity.EXTRA_CALL_ENGINE, engine);
            openCallScreen();
          });
      return;
    }
    openCallScreen();
  }

  private void openCallScreen() {
    String routedOffer = getIntent().getStringExtra(VoiceCallActivity.EXTRA_SDP_OFFER);
    String selectedEngine = getIntent().getStringExtra(VoiceCallActivity.EXTRA_CALL_ENGINE);
    Log.i("PingGoCallTrace", "video_engine_route callId="
        + value(VoiceCallActivity.EXTRA_CALL_ID) + " engine=" + selectedEngine
        + " incoming=" + (routedOffer != null && !routedOffer.isEmpty()));
    if (com.w3n.pinggo.call.CallEngineToggle.LIVEKIT.equals(selectedEngine)
        && (routedOffer == null || routedOffer.isEmpty())) {
      Intent liveKit = new Intent(getIntent());
      liveKit.setClass(this, LiveKitCallActivity.class);
      liveKit.putExtra(LiveKitCallActivity.EXTRA_MEDIA_TYPE, "video");
      startActivity(liveKit);
      finish();
      return;
    }
    Log.i("PingGoCallTrace", "video_activity_created callId="
        + value(VoiceCallActivity.EXTRA_CALL_ID) + " hasOffer="
        + !value(VoiceCallActivity.EXTRA_SDP_OFFER).isEmpty() + " autoAccept="
        + getIntent().getBooleanExtra(VoiceCallActivity.EXTRA_AUTO_ACCEPT, false));
    if (!value(VoiceCallActivity.EXTRA_SDP_OFFER).isEmpty()) {
      if (getIntent().getBooleanExtra(VoiceCallActivity.EXTRA_AUTO_ACCEPT, false))
        PingGoNotificationManager.clearCallNotification(this,
            value(VoiceCallActivity.EXTRA_CALL_ID));
      else PingGoNotificationManager.markCallNotificationOpened(this, getIntent());
    }
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    ActiveCallRegistry.getInstance().register(this, value(VoiceCallActivity.EXTRA_CALL_CHAT_ID),
        ActiveCallRegistry.TYPE_VIDEO);
    audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    if (audioManager != null)
      audioManager.setSpeakerphoneOn(true);
    buildCallScreen();
    controller = new VideoCallController(this, ChatRepository.getInstance(this), this,
        value(VoiceCallActivity.EXTRA_CALL_ID), value(VoiceCallActivity.EXTRA_CALL_CHAT_ID),
        LoginStateManager.getInstance().getUID(this), value(VoiceCallActivity.EXTRA_CALLER_ID),
        value(VoiceCallActivity.EXTRA_SDP_OFFER), LoginStateManager.getInstance().getENC(this),
        com.w3n.pinggo.Database.CloudFunction.RestApi.APIAuth.MEDIA_WS_URL);
    FloatingVideoCallController.getInstance().begin(controller::hangup,
        controller::attachRemoteSurface);
    pipEligible = !controller.isIncomingUnanswered();
    CallPictureInPicture.configure(this, pipEligible);
    getOnBackPressedDispatcher().addCallback(this,
        new androidx.activity.OnBackPressedCallback(true) {
          @Override public void handleOnBackPressed() { minimizeCall(); }
        });
    requestPermissionsAndStart();
  }

  private void buildCallScreen() {
    videoRoot = new FrameLayout(this);
    videoRoot.setBackgroundColor(Color.rgb(16, 24, 32));
    remoteSurface = new SurfaceView(this);
    videoRoot.addView(remoteSurface, new FrameLayout.LayoutParams(-1, -1));
    remoteCameraOffView = cameraDisabledView();
    remoteCameraOffView.setVisibility(View.GONE);
    videoRoot.addView(remoteCameraOffView, new FrameLayout.LayoutParams(-1, -1));
    localSurface = new SurfaceView(this);
    localSurface.setZOrderMediaOverlay(true);
    videoRoot.addView(localSurface, new FrameLayout.LayoutParams(-1, -1));
    localCameraOffView = cameraDisabledView();
    localCameraOffView.setVisibility(View.GONE);
    videoRoot.addView(localCameraOffView, new FrameLayout.LayoutParams(-1, -1));
    callView = new VideoActiveCallView(this, value(VoiceCallActivity.EXTRA_PHONE_NUMBER),
        value(VoiceCallActivity.EXTRA_PROFILE_PATH), this);
    callView.setAudioState(true, false);
    videoRoot.addView(callView, new FrameLayout.LayoutParams(-1, -1));
    videoRoot.addOnLayoutChangeListener((view, left, top, right, bottom,
        oldLeft, oldTop, oldRight, oldBottom) -> {
      if (pipLayoutActive && bottom > top) layoutPictureInPictureSurfaces(bottom - top);
    });
    setContentView(videoRoot);
    ViewCompat.setOnApplyWindowInsetsListener(callView, (view, insets) -> {
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
      callView.setInsets(bars.top, bars.bottom);
      return insets;
    });
    ViewCompat.requestApplyInsets(callView);
    remoteSurface.getHolder().addCallback(new SurfaceCallback(false));
    localSurface.getHolder().addCallback(new SurfaceCallback(true));
    updateRotation();
  }

  private void requestPermissionsAndStart() {
    boolean camera = ContextCompat.checkSelfPermission(this,
        Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    boolean microphone = ContextCompat.checkSelfPermission(this,
        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    Log.i("PingGoCallTrace", "video_permission_check callId="
        + value(VoiceCallActivity.EXTRA_CALL_ID) + " camera=" + camera
        + " microphone=" + microphone);
    if (camera && microphone)
      onMediaPermissionsReady();
    else
      permissions.launch(new String[] { Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO });
  }

  private View cameraDisabledView() {
    return new View(this) {
      final ZLayerGroup layers = new ZLayerGroup(this);
      final ZLayer content = layers.addLayer("camera_disabled");
      { cameraDisabledLayers.add(layers); setBackgroundColor(Color.BLACK); }
      @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){
        content.clear();
        content.add(new Text.Builder(VideoCallActivity.this,"camera_disabled_text","Camera disabled",
            new RectF(0,0,width,height)).setTextColor(Color.WHITE)
            .setTextSizePx(16f*getResources().getDisplayMetrics().scaledDensity).setAlignment(Text.Alignment.CENTER)
            .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(1));
      }
      @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);layers.draw(canvas);}
      @Override public boolean onTouchEvent(MotionEvent event){return layers.onTouchEvent(event)||super.onTouchEvent(event);}
    };
  }

  private void onPermissionsResult(Map<String, Boolean> result) {
    Log.i("PingGoCallTrace", "video_permission_result callId="
        + value(VoiceCallActivity.EXTRA_CALL_ID) + " result=" + result);
    if (Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA)) &&
        Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO)))
      onMediaPermissionsReady();
    else {
      Toast.makeText(this, "Camera and microphone permissions are required.",
          Toast.LENGTH_LONG).show();
      finish();
    }
  }

  private void onMediaPermissionsReady() {
    Log.i("PingGoCallTrace", "video_media_ready callId="
        + value(VoiceCallActivity.EXTRA_CALL_ID) + " incomingUnanswered="
        + controller.isIncomingUnanswered() + " autoAccept="
        + getIntent().getBooleanExtra(VoiceCallActivity.EXTRA_AUTO_ACCEPT, false));
    controller.onPermissionsReady();
    if (controller.isIncomingUnanswered()
        && getIntent().getBooleanExtra(VoiceCallActivity.EXTRA_AUTO_ACCEPT, false)) {
      onAccept();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    pipActivityStopped = false;
    // Returning from PiP to the full activity is not a dismissal.
    if (!CallPictureInPicture.isActive(this)) {
      pipSessionActive = false;
      pipExitCheckAttempts = 0;
      applyPictureInPictureLayout(false);
    }
    if (controller != null) {
      controller.onResume();
      if (remoteSurface.getHolder().getSurface().isValid())
        controller.attachRemoteSurface(remoteSurface.getHolder().getSurface());
      updateRotation();
    }
  }

  @Override
  protected void onPause() {
    if (controller != null && !CallPictureInPicture.isActive(this) && !pipEligible
        && !FloatingVideoCallController.getInstance().isMinimized())
      controller.onPause();
    super.onPause();
  }

  @Override protected void onUserLeaveHint() {
    super.onUserLeaveHint();
    if (pipEligible) CallPictureInPicture.enter(this);
  }

  @Override protected void onStop() {
    super.onStop();
    if (!pipSessionActive) return;
    pipActivityStopped = true;
    boolean stillInPip = CallPictureInPicture.isActive(this);
    Log.i("PingGoDisconnectHook", "stage=pip_on_stop engine=webrtc media=video callId="
        + value(VoiceCallActivity.EXTRA_CALL_ID) + " stillInPip=" + stillInPip);
    // PiP dismissal does not reliably destroy the activity on every Android vendor build.
    schedulePipExitCheck("on_stop");
  }

  private void schedulePipExitCheck(String trigger) {
    if (!pipSessionActive
        || pipExitCheckAttempts >= CallPictureInPicture.DISMISS_CONFIRMATION_MAX_ATTEMPTS) return;
    int attempt = ++pipExitCheckAttempts;
    toneHandler.postDelayed(() -> {
      if (!pipSessionActive) return;
      boolean inPip = CallPictureInPicture.isActive(this);
      Log.i("PingGoDisconnectHook", "stage=pip_exit_check engine=webrtc media=video callId="
          + value(VoiceCallActivity.EXTRA_CALL_ID) + " trigger=" + trigger
          + " attempt=" + attempt + " inPip=" + inPip + " focus=" + hasWindowFocus());
      if (!inPip) disconnectDismissedPip("exit_check");
      else schedulePipExitCheck(trigger);
    }, CallPictureInPicture.DISMISS_CONFIRMATION_MS);
  }

  private void disconnectDismissedPip(String lifecycle) {
    if (!pipSessionActive || controller == null) return;
    pipSessionActive = false;
    Log.i("PingGoCallTrace", "video_pip_dismissed callId="
        + value(VoiceCallActivity.EXTRA_CALL_ID));
    Log.i("PingGoDisconnectHook", "stage=pip_dismissed engine=webrtc media=video callId="
        + value(VoiceCallActivity.EXTRA_CALL_ID) + " action=hangup lifecycle=" + lifecycle);
    controller.hangup();
  }

  @Override public void onPictureInPictureModeChanged(boolean inPictureInPictureMode,
      @NonNull Configuration newConfig) {
    super.onPictureInPictureModeChanged(inPictureInPictureMode, newConfig);
    Log.i("PingGoDisconnectHook", "stage=pip_mode_changed engine=webrtc media=video callId="
        + value(VoiceCallActivity.EXTRA_CALL_ID) + " inPip=" + inPictureInPictureMode);
    if (inPictureInPictureMode) {
      pipSessionActive = true;
      pipActivityStopped = false;
      pipExitCheckAttempts = 0;
    } else {
      pipExitCheckAttempts = 0;
      if (pipSessionActive && pipActivityStopped && !hasWindowFocus()) {
        Log.i("PingGoDisconnectHook", "stage=pip_exit_direct engine=webrtc media=video callId="
            + value(VoiceCallActivity.EXTRA_CALL_ID) + " trigger=mode_changed");
        disconnectDismissedPip("mode_changed_direct");
      } else {
        schedulePipExitCheck("mode_changed");
      }
    }
    if (inPictureInPictureMode) applyPictureInPictureLayout(true);
  }

  @Override
  public void onBack() {
    if (controller.isIncomingUnanswered())
      controller.reject();
    else minimizeCall();
  }

  private void minimizeCall() {
    if (controller == null) return;
    if (controller.isIncomingUnanswered()) {
      controller.reject();
      return;
    }
    if (!CallPictureInPicture.enter(this))
      FloatingVideoCallController.getInstance().minimizeAndReturn(this);
  }

  @Override
  public void onEnd() {
    controller.hangup();
  }

  @Override public void onPictureInPictureHangup() {
    Log.i("PingGoDisconnectHook", "stage=pip_hangup_action engine=webrtc media=video callId="
        + value(VoiceCallActivity.EXTRA_CALL_ID));
    pipSessionActive = false;
    if (controller != null) controller.hangup();
    if (!isFinishing()) finish();
  }

  @Override
  public void onSpeaker() {
    speakerOn = !speakerOn;
    if (audioManager != null)
      audioManager.setSpeakerphoneOn(speakerOn);
    callView.setAudioState(speakerOn, controller.isMuted());
  }

  @Override
  public void onMute() {
    controller.toggleMute();
    callView.setAudioState(speakerOn, controller.isMuted());
  }

  @Override
  public void onFlipCamera() {
    controller.flipCamera();
  }

  @Override
  public void onCamera() {
    controller.toggleCamera();
  }

  @Override
  public void onAccept() {
    Log.i("PingGoCallTrace", "video_answer_requested callId="
        + value(VoiceCallActivity.EXTRA_CALL_ID) + " incomingUnanswered="
        + controller.isIncomingUnanswered());
    stopIncomingRingtone();
    pipEligible = true;
    CallPictureInPicture.configure(this, true);
    PingGoNotificationManager.clearCallNotification(this,
        value(VoiceCallActivity.EXTRA_CALL_ID));
    controller.accept();
    callView.showIncomingPrompt(false);
  }

  @Override
  public void onReject() {
    stopCallTones();
    PingGoNotificationManager.clearCallNotification(this,
        value(VoiceCallActivity.EXTRA_CALL_ID));
    controller.reject();
  }

  @Override
  public void onState(VideoCallController.CallState state,
      VideoCallController.ChannelState signaling, VideoCallController.ChannelState audio,
      VideoCallController.ChannelState video, String status) {
    Log.i("PingGoCallTrace", "video_state callId="
        + value(VoiceCallActivity.EXTRA_CALL_ID) + " state=" + state
        + " signaling=" + signaling + " audio=" + audio + " video=" + video
        + " status=" + status);
    runOnUiThread(() -> {
      if (callView == null)
        return;
      callView.setCallStatus(status);
      FloatingVideoCallController.getInstance().updateStatus(status);
      callConnected = state == VideoCallController.CallState.CONNECTED;
      callView.setCallConnected(callConnected);
      if (callConnected && !pipEligible) {
        pipEligible = true;
        CallPictureInPicture.configure(this, true);
      }
      if (state == VideoCallController.CallState.RINGING) {
        callView.showIncomingPrompt(true);
        startIncomingRingtone();
      } else if (state == VideoCallController.CallState.CALLING) {
        startOutgoingTone();
      } else if (state == VideoCallController.CallState.CONNECTING ||
          state == VideoCallController.CallState.CONNECTED ||
          state == VideoCallController.CallState.ENDING ||
          state == VideoCallController.CallState.ENDED) {
        stopCallTones();
      }
      if (state == VideoCallController.CallState.CONNECTED
          || state == VideoCallController.CallState.ENDING
          || state == VideoCallController.CallState.ENDED) {
        PingGoNotificationManager.clearCallNotification(this,
            value(VoiceCallActivity.EXTRA_CALL_ID));
      }
      if (state == VideoCallController.CallState.CONNECTED) {
        ActiveCallRegistry.getInstance().setConnected(this, true);
        showConnectedLayout();
      }
    });
  }

  @Override
  public void onElapsed(String elapsed) {
    runOnUiThread(() -> {
      FloatingVideoCallController.getInstance().updateStatus(elapsed);
      if (callView != null)
        callView.setCallStatus(elapsed);
    });
  }

  @Override
  public void onRemoteMuted(boolean value) {
    runOnUiThread(() -> {
      if (callView != null)
        callView.setRemoteMuted(value);
    });
  }

  @Override
  public void onRemoteCameraEnabled(boolean enabled) {
    runOnUiThread(() -> {
      if (callView != null) callView.setRemoteCameraEnabled(enabled);
      remoteCameraOffView.setVisibility(enabled ? View.GONE : View.VISIBLE);
      if (!enabled && remoteCameraOffView != null)
        remoteCameraOffView.bringToFront();
      if (localSurface != null)
        localSurface.bringToFront();
      if (localCameraOffView != null && localCameraOffView.getVisibility() == View.VISIBLE)
        localCameraOffView.bringToFront();
      if (callView != null)
        callView.bringToFront();
    });
  }

  @Override
  public void onCameraEnabled(boolean enabled) {
    runOnUiThread(() -> {
      localCameraOffView.setVisibility(enabled ? View.GONE : View.VISIBLE);
      if (callView != null)
        callView.setCameraEnabled(enabled);
      if (!enabled)
        localCameraOffView.bringToFront();
      if (callView != null)
        callView.bringToFront();
    });
  }

  @Override
  public void onFinished(VideoCallController.TerminationReason reason, String message) {
    runOnUiThread(() -> {
      if (!isFinishing()) {
        stopCallTones();
        FloatingVideoCallController.getInstance().clear();
        if (reason != VideoCallController.TerminationReason.LOCAL_HANGUP &&
            reason != VideoCallController.TerminationReason.ACTIVITY_DESTROYED)
          Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
      }
    });
  }

  @Override
  public void onError(String message) {
    runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
  }

  private void showConnectedLayout() {
    if (localSurface == null)
      return;
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(px(330f), px(495f),
        Gravity.TOP | Gravity.END);
    params.topMargin = px(198f);
    params.rightMargin = px(44f);
    localSurface.setLayoutParams(params);
    if (localCameraOffView != null)
      localCameraOffView.setLayoutParams(new FrameLayout.LayoutParams(params));
    localSurface.bringToFront();
    if (localCameraOffView != null && localCameraOffView.getVisibility() == View.VISIBLE)
      localCameraOffView.bringToFront();
    if (callView != null)
      callView.bringToFront();
  }

  /** PiP shows the remote and local callers as two equal vertical tiles. */
  private void applyPictureInPictureLayout(boolean pip) {
    if (remoteSurface == null || localSurface == null) return;
    pipLayoutActive = pip;
    if (callView != null) callView.setVisibility(pip ? View.GONE : View.VISIBLE);
    int match = FrameLayout.LayoutParams.MATCH_PARENT;
    if (pip) {
      int height = videoRoot == null ? 0 : videoRoot.getHeight();
      layoutPictureInPictureSurfaces(height > 0
          ? height : getResources().getDisplayMetrics().heightPixels);
    } else {
      remoteSurface.setLayoutParams(new FrameLayout.LayoutParams(match, match));
      if (remoteCameraOffView != null)
        remoteCameraOffView.setLayoutParams(new FrameLayout.LayoutParams(match, match));
      if (callConnected) showConnectedLayout();
      else {
        localSurface.setLayoutParams(new FrameLayout.LayoutParams(match, match));
        if (localCameraOffView != null)
          localCameraOffView.setLayoutParams(new FrameLayout.LayoutParams(match, match));
      }
      if (callView != null) callView.bringToFront();
    }
  }

  private void layoutPictureInPictureSurfaces(int containerHeight) {
    int match = FrameLayout.LayoutParams.MATCH_PARENT;
    int half = Math.max(1, containerHeight / 2);
    FrameLayout.LayoutParams remote = new FrameLayout.LayoutParams(match, half, Gravity.TOP);
    remoteSurface.setLayoutParams(remote);
    if (remoteCameraOffView != null)
      remoteCameraOffView.setLayoutParams(new FrameLayout.LayoutParams(remote));
    FrameLayout.LayoutParams local = new FrameLayout.LayoutParams(match, half, Gravity.BOTTOM);
    localSurface.setLayoutParams(local);
    if (localCameraOffView != null)
      localCameraOffView.setLayoutParams(new FrameLayout.LayoutParams(local));
    localSurface.bringToFront();
    if (localCameraOffView != null && localCameraOffView.getVisibility() == View.VISIBLE)
      localCameraOffView.bringToFront();
  }

  private void startIncomingRingtone() {
    if (incomingToneActive)
      return;
    stopCallTones();
    incomingRingtone = RingtoneManager.getRingtone(this,
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
    if (incomingRingtone == null)
      return;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
      incomingRingtone.setLooping(true);
    incomingToneActive = true;
    incomingRingtone.play();
    toneHandler.postDelayed(incomingToneLoop, 2_000L);
  }

  private void stopIncomingRingtone() {
    incomingToneActive = false;
    toneHandler.removeCallbacks(incomingToneLoop);
    if (incomingRingtone != null && incomingRingtone.isPlaying())
      incomingRingtone.stop();
    incomingRingtone = null;
  }

  private void startOutgoingTone() {
    if (outgoingToneActive)
      return;
    stopCallTones();
    outgoingTone = new ToneGenerator(AudioManager.STREAM_RING, 100);
    outgoingToneActive = true;
    outgoingToneLoop.run();
  }

  private void stopOutgoingTone() {
    outgoingToneActive = false;
    toneHandler.removeCallbacks(outgoingToneLoop);
    if (outgoingTone != null) {
      outgoingTone.stopTone();
      outgoingTone.release();
      outgoingTone = null;
    }
  }

  private void stopCallTones() {
    stopIncomingRingtone();
    stopOutgoingTone();
  }

  @Override
  protected void onDestroy() {
    stopCallTones();
    CallPictureInPicture.configure(this, false);
    FloatingVideoCallController.getInstance().clear();
    disconnectDismissedPip("on_destroy");
    if (controller != null)
      controller.destroy();
    controller = null;
    if (audioManager != null)
      audioManager.setSpeakerphoneOn(false);
    if (callView != null)
      callView.release();
    for (ZLayerGroup layers : cameraDisabledLayers) layers.release();
    cameraDisabledLayers.clear();
    callView = null;
    ActiveCallRegistry.getInstance().clear(this);
    super.onDestroy();
  }

  private void updateRotation() {
    if (controller != null && getDisplay() != null)
      controller.setDisplayRotation(getDisplay().getRotation() * 90);
  }

  private String value(String key) {
    String result = getIntent().getStringExtra(key);
    return result == null ? "" : result.trim();
  }

  private int px(float value) {
    return Math.round(figmaConfig.toRuntime(value, Math.max(1, getResources().getDisplayMetrics().widthPixels)));
  }

  private final class SurfaceCallback implements SurfaceHolder.Callback {
    private final boolean local;

    SurfaceCallback(boolean local) {
      this.local = local;
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
      attach(holder);
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int w, int h) {
      updateRotation();
      attach(holder);
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
      if (controller == null)
        return;
      if (local)
        controller.attachLocalSurface(null);
      else
        controller.attachRemoteSurface(null);
    }

    private void attach(SurfaceHolder holder) {
      if (controller == null)
        return;
      if (local)
        controller.attachLocalSurface(holder.getSurface());
      else
        controller.attachRemoteSurface(holder.getSurface());
    }
  }
}
