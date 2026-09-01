package com.w3n.pinggo.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.call.ActiveCallRegistry;
import com.w3n.pinggo.call.FloatingVideoCallController;
import com.w3n.pinggo.call.VideoCallController;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.views.call.VideoActiveCallView;
import java.util.Map;

public class VideoCallActivity extends AppCompatActivity implements VideoActiveCallView.Listener,
    VideoCallController.Listener {
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
  private static final String TAG = "PingGoVideoCall";
  private VideoActiveCallView callView;
  private AudioManager audioManager;
  private VideoCallController controller;
  private SurfaceView remoteSurface, localSurface;
  private TextView remoteCameraOffView, localCameraOffView;
  private boolean speakerOn = true;
  private final Handler toneHandler = new Handler(Looper.getMainLooper());
  private Ringtone incomingRingtone;
  private ToneGenerator outgoingTone;
  private boolean incomingToneActive, outgoingToneActive;
  private final Runnable incomingToneLoop = new Runnable() {
    @Override public void run() {
      if (!incomingToneActive) return;
      if (incomingRingtone != null && !incomingRingtone.isPlaying()) incomingRingtone.play();
      toneHandler.postDelayed(this, 2_000L);
    }
  };
  private final Runnable outgoingToneLoop = new Runnable() {
    @Override public void run() {
      if (!outgoingToneActive || outgoingTone == null) return;
      outgoingTone.startTone(ToneGenerator.TONE_SUP_RINGTONE, 2_500);
      toneHandler.postDelayed(this, 4_000L);
    }
  };
  private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(
      new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionsResult);

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    EdgeToEdge.enable(this);
    ActiveCallRegistry.getInstance().register(this, value(VoiceCallActivity.EXTRA_CALL_CHAT_ID),
        ActiveCallRegistry.TYPE_VIDEO);
    audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    if (audioManager != null) audioManager.setSpeakerphoneOn(true);
    buildCallScreen();
    controller = new VideoCallController(this, ChatRepository.getInstance(this), this,
        value(VoiceCallActivity.EXTRA_CALL_ID), value(VoiceCallActivity.EXTRA_CALL_CHAT_ID),
        LoginStateManager.getInstance().getUID(this), value(VoiceCallActivity.EXTRA_CALLER_ID),
        value(VoiceCallActivity.EXTRA_SDP_OFFER), LoginStateManager.getInstance().getENC(this),
        com.w3n.pinggo.Database.CloudFunction.RestApi.APIAuth.MEDIA_WS_URL);
    FloatingVideoCallController.getInstance().begin(controller::hangup,
        controller::attachRemoteSurface);
    requestPermissionsAndStart();
  }

  private void buildCallScreen() {
    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(Color.rgb(16, 24, 32));
    remoteSurface = new SurfaceView(this);
    root.addView(remoteSurface, new FrameLayout.LayoutParams(-1, -1));
    remoteCameraOffView = cameraDisabledView();
    remoteCameraOffView.setVisibility(View.GONE);
    root.addView(remoteCameraOffView, new FrameLayout.LayoutParams(-1, -1));
    localSurface = new SurfaceView(this);
    localSurface.setZOrderMediaOverlay(true);
    root.addView(localSurface, new FrameLayout.LayoutParams(-1, -1));
    localCameraOffView = cameraDisabledView();
    localCameraOffView.setVisibility(View.GONE);
    root.addView(localCameraOffView, new FrameLayout.LayoutParams(-1, -1));
    callView = new VideoActiveCallView(this, value(VoiceCallActivity.EXTRA_PHONE_NUMBER),
        value(VoiceCallActivity.EXTRA_PROFILE_PATH), this);
    callView.setAudioState(true, false);
    root.addView(callView, new FrameLayout.LayoutParams(-1, -1));
    setContentView(root);
    ViewCompat.setOnApplyWindowInsetsListener(callView, (view, insets) -> {
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
      callView.setInsets(bars.top, bars.bottom); return insets;
    });
    ViewCompat.requestApplyInsets(callView);
    remoteSurface.getHolder().addCallback(new SurfaceCallback(false));
    localSurface.getHolder().addCallback(new SurfaceCallback(true));
    updateRotation();
  }

  private void requestPermissionsAndStart() {
    boolean camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED;
    boolean microphone = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED;
    if (camera && microphone) onMediaPermissionsReady();
    else permissions.launch(new String[] { Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO });
  }
  private TextView cameraDisabledView() {
    TextView view = new TextView(this);
    view.setBackgroundColor(Color.BLACK);
    view.setText("Camera disabled");
    view.setTextColor(Color.WHITE);
    view.setTextSize(16);
    view.setGravity(Gravity.CENTER);
    return view;
  }
  private void onPermissionsResult(Map<String, Boolean> result) {
    if (Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA)) &&
        Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO))) onMediaPermissionsReady();
    else { Toast.makeText(this, "Camera and microphone permissions are required.",
        Toast.LENGTH_LONG).show(); finish(); }
  }
  private void onMediaPermissionsReady() {
    controller.onPermissionsReady();
  }

  @Override protected void onResume() {
    super.onResume(); if (controller != null) {
      controller.onResume();
      if (remoteSurface.getHolder().getSurface().isValid())
        controller.attachRemoteSurface(remoteSurface.getHolder().getSurface());
      updateRotation();
    }
  }
  @Override protected void onPause() {
    if (controller != null && !FloatingVideoCallController.getInstance().isMinimized())
      controller.onPause();
    super.onPause();
  }
  @Override public void onBack() {
    if (controller.isIncomingUnanswered()) controller.reject();
    else FloatingVideoCallController.getInstance().minimizeAndReturn(this);
  }
  @Override public void onEnd() { controller.hangup(); }
  @Override public void onSpeaker() {
    speakerOn = !speakerOn;
    if (audioManager != null) audioManager.setSpeakerphoneOn(speakerOn);
    callView.setAudioState(speakerOn, controller.isMuted());
  }
  @Override public void onMute() {
    controller.toggleMute(); callView.setAudioState(speakerOn, controller.isMuted());
  }
  @Override public void onFlipCamera() { controller.flipCamera(); }
  @Override public void onCamera() { controller.toggleCamera(); }
  @Override public void onAccept() {
    stopIncomingRingtone(); controller.accept(); callView.showIncomingPrompt(false);
  }
  @Override public void onReject() { stopCallTones(); controller.reject(); }
  @Override public void onState(VideoCallController.CallState state,
      VideoCallController.ChannelState signaling, VideoCallController.ChannelState audio,
      VideoCallController.ChannelState video, String status) {
    runOnUiThread(() -> {
      if (callView == null) return;
      callView.setCallStatus(status);
      FloatingVideoCallController.getInstance().updateStatus(status);
      callView.setCallConnected(state == VideoCallController.CallState.CONNECTED);
      if (state == VideoCallController.CallState.RINGING) {
        callView.showIncomingPrompt(true); startIncomingRingtone();
      } else if (state == VideoCallController.CallState.CALLING) {
        startOutgoingTone();
      } else if (state == VideoCallController.CallState.CONNECTING ||
          state == VideoCallController.CallState.CONNECTED ||
          state == VideoCallController.CallState.ENDING ||
          state == VideoCallController.CallState.ENDED) {
        stopCallTones();
      }
      if (state == VideoCallController.CallState.CONNECTED) {
        ActiveCallRegistry.getInstance().setConnected(this, true);
        showConnectedLayout();
      }
    });
  }
  @Override public void onElapsed(String elapsed) { runOnUiThread(() -> {
    FloatingVideoCallController.getInstance().updateStatus(elapsed);
    if (callView != null) callView.setCallStatus(elapsed); }); }
  @Override public void onRemoteMuted(boolean value) { runOnUiThread(() -> {
    if (callView != null) callView.setRemoteMuted(value); }); }
  @Override public void onRemoteCameraEnabled(boolean enabled) {
    runOnUiThread(() -> {
      remoteCameraOffView.setVisibility(enabled ? View.GONE : View.VISIBLE);
      if (!enabled && remoteCameraOffView != null) remoteCameraOffView.bringToFront();
      if (localSurface != null) localSurface.bringToFront();
      if (localCameraOffView != null && localCameraOffView.getVisibility() == View.VISIBLE)
        localCameraOffView.bringToFront();
      if (callView != null) callView.bringToFront();
    });
  }
  @Override public void onCameraEnabled(boolean enabled) { runOnUiThread(() -> {
    localCameraOffView.setVisibility(enabled ? View.GONE : View.VISIBLE);
    if (callView != null) callView.setCameraEnabled(enabled);
    if (!enabled) localCameraOffView.bringToFront(); if (callView != null) callView.bringToFront();
  }); }
  @Override public void onFinished(VideoCallController.TerminationReason reason, String message) {
    runOnUiThread(() -> { if (!isFinishing()) {
      stopCallTones();
      FloatingVideoCallController.getInstance().clear();
      if (reason != VideoCallController.TerminationReason.LOCAL_HANGUP &&
          reason != VideoCallController.TerminationReason.ACTIVITY_DESTROYED)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
      finish();
    }});
  }
  @Override public void onError(String message) { runOnUiThread(() ->
      Toast.makeText(this, message, Toast.LENGTH_SHORT).show()); }
  private void showConnectedLayout() {
    if (localSurface == null) return;
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(px(330f), px(495f),
        Gravity.TOP | Gravity.END);
    params.topMargin = px(198f);
    params.rightMargin = px(44f);
    localSurface.setLayoutParams(params);
    if (localCameraOffView != null) localCameraOffView.setLayoutParams(new FrameLayout.LayoutParams(params));
    localSurface.bringToFront();
    if (localCameraOffView != null && localCameraOffView.getVisibility() == View.VISIBLE)
      localCameraOffView.bringToFront();
    if (callView != null) callView.bringToFront();
  }
  private void startIncomingRingtone() {
    if (incomingToneActive) return;
    stopCallTones();
    incomingRingtone = RingtoneManager.getRingtone(this,
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
    if (incomingRingtone == null) return;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
      incomingRingtone.setLooping(true);
    incomingToneActive = true; incomingRingtone.play();
    toneHandler.postDelayed(incomingToneLoop, 2_000L);
  }
  private void stopIncomingRingtone() {
    incomingToneActive = false; toneHandler.removeCallbacks(incomingToneLoop);
    if (incomingRingtone != null && incomingRingtone.isPlaying()) incomingRingtone.stop();
    incomingRingtone = null;
  }
  private void startOutgoingTone() {
    if (outgoingToneActive) return;
    stopCallTones();
    outgoingTone = new ToneGenerator(AudioManager.STREAM_RING, 100);
    outgoingToneActive = true; outgoingToneLoop.run();
  }
  private void stopOutgoingTone() {
    outgoingToneActive = false; toneHandler.removeCallbacks(outgoingToneLoop);
    if (outgoingTone != null) {
      outgoingTone.stopTone(); outgoingTone.release(); outgoingTone = null;
    }
  }
  private void stopCallTones() { stopIncomingRingtone(); stopOutgoingTone(); }
  @Override protected void onDestroy() {
    stopCallTones();
    FloatingVideoCallController.getInstance().clear();
    if (controller != null) controller.destroy(); controller = null;
    if (audioManager != null) audioManager.setSpeakerphoneOn(false);
    if (callView != null) callView.release(); callView = null;
    ActiveCallRegistry.getInstance().clear(this); super.onDestroy();
  }
  private void updateRotation() {
    if (controller != null && getDisplay() != null)
      controller.setDisplayRotation(getDisplay().getRotation() * 90);
  }
  private String value(String key) {
    String result = getIntent().getStringExtra(key); return result == null ? "" : result.trim();
  }
  private int px(float value) {
    return Math.round(figmaConfig.toRuntime(value, Math.max(1, getResources().getDisplayMetrics().widthPixels)));
  }
  private final class SurfaceCallback implements SurfaceHolder.Callback {
    private final boolean local;
    SurfaceCallback(boolean local) { this.local = local; }
    @Override public void surfaceCreated(@NonNull SurfaceHolder holder) { attach(holder); }
    @Override public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int w, int h) { updateRotation(); attach(holder); }
    @Override public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
      if (controller == null) return;
      if (local) controller.attachLocalSurface(null); else controller.attachRemoteSurface(null);
    }
    private void attach(SurfaceHolder holder) {
      if (controller == null) return;
      if (local) controller.attachLocalSurface(holder.getSurface());
      else controller.attachRemoteSurface(holder.getSurface());
    }
  }
}
