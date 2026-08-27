package com.w3n.pinggo.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;
import com.google.gson.JsonObject;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.call.WebRTCCallClient;
import com.w3n.pinggo.call.FloatingVoiceCallController;
import com.w3n.pinggo.call.ActiveCallRegistry;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.views.call.VoiceActiveCallView;

public class VoiceCallActivity extends AppCompatActivity
    implements VoiceActiveCallView.Listener, WebRTCCallClient.Listener,
    ChatRepository.CallEventListener {
  public static final String EXTRA_PHONE_NUMBER = "com.w3n.pinggo.EXTRA_CALL_PHONE_NUMBER";
  public static final String EXTRA_PROFILE_PATH = "com.w3n.pinggo.EXTRA_CALL_PROFILE_PATH";
  public static final String EXTRA_CALL_ID = "com.w3n.pinggo.EXTRA_CALL_ID";
  public static final String EXTRA_CALLER_ID = "com.w3n.pinggo.EXTRA_CALLER_ID";
  public static final String EXTRA_SDP_OFFER = "com.w3n.pinggo.EXTRA_SDP_OFFER";
  public static final String EXTRA_CALL_CHAT_ID = "com.w3n.pinggo.EXTRA_CALL_CHAT_ID";
  private VoiceActiveCallView callView;
  private AudioManager audioManager;
  private WebRTCCallClient callClient;
  private boolean speakerOn, muted;
  private final Handler timerHandler = new Handler(Looper.getMainLooper());
  private long connectedAt;
  private boolean timerRunning;
  private boolean incomingAccepted;
  private Ringtone incomingRingtone;
  private ToneGenerator outgoingTone;
  private boolean incomingToneActive, outgoingToneActive;
  private final Runnable incomingToneLoop = new Runnable() {
    @Override public void run() {
      if (!incomingToneActive) return;
      if (incomingRingtone != null && !incomingRingtone.isPlaying()) incomingRingtone.play();
      timerHandler.postDelayed(this, 2000);
    }
  };
  private final Runnable outgoingToneLoop = new Runnable() {
    @Override public void run() {
      if (!outgoingToneActive || outgoingTone == null) return;
      outgoingTone.startTone(ToneGenerator.TONE_SUP_RINGTONE, 2500);
      timerHandler.postDelayed(this, 4000);
    }
  };
  private final Runnable callTimer = new Runnable() {
    @Override public void run() {
      if (!timerRunning || callView == null) return;
      long elapsed = Math.max(0, SystemClock.elapsedRealtime() - connectedAt) / 1000;
      callView.setCallStatus(String.format(java.util.Locale.US, "%02d:%02d", elapsed / 60,
          elapsed % 60));
      FloatingVoiceCallController.getInstance().updateStatus(
          String.format(java.util.Locale.US, "%02d:%02d", elapsed / 60, elapsed % 60));
      timerHandler.postDelayed(this, 1000);
    }
  };
  private final ActivityResultLauncher<String> microphonePermission = registerForActivityResult(
      new ActivityResultContracts.RequestPermission(), granted -> {
        if (granted) startCall();
        else { Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_LONG).show(); finish(); }
      });

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    EdgeToEdge.enable(this);
    audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    ActiveCallRegistry.getInstance().register(this,
        getIntent().getStringExtra(EXTRA_CALL_CHAT_ID), ActiveCallRegistry.TYPE_VOICE);
    callView = new VoiceActiveCallView(this, getIntent().getStringExtra(EXTRA_PHONE_NUMBER),
        getIntent().getStringExtra(EXTRA_PROFILE_PATH), this);
    FloatingVoiceCallController.getInstance().begin(
        getIntent().getStringExtra(EXTRA_PHONE_NUMBER),
        getIntent().getStringExtra(EXTRA_PROFILE_PATH),
        () -> runOnUiThread(this::endFromFloatingView));
    setContentView(callView);
    ViewCompat.setOnApplyWindowInsetsListener(callView, (view, insets) -> {
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
      callView.setInsets(bars.top, bars.bottom);
      return insets;
    });
    ViewCompat.requestApplyInsets(callView);
    if (isIncoming()) {
      callView.showIncomingPrompt();
      FloatingVoiceCallController.getInstance().updateStatus("Ringing…");
      ChatRepository.getInstance(this).setCallEventListener(this);
      notifyCallerRinging();
      startIncomingRingtone();
    } else {
      startOutgoingTone();
      requestMicrophoneAndStart();
    }
  }

  private boolean isIncoming() {
    String offer = getIntent().getStringExtra(EXTRA_SDP_OFFER);
    return offer != null && !offer.isEmpty();
  }

  private void notifyCallerRinging() {
    JsonObject event = new JsonObject();
    event.addProperty("type", "call_ringing");
    event.addProperty("callId", getIntent().getStringExtra(EXTRA_CALL_ID));
    event.addProperty("senderId", LoginStateManager.getInstance().getUID(this));
    event.addProperty("receiverId", getIntent().getStringExtra(EXTRA_CALLER_ID));
    ChatRepository.getInstance(this).sendCallEvent(event);
  }

  private void requestMicrophoneAndStart() {
    if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED) startCall();
    else microphonePermission.launch(Manifest.permission.RECORD_AUDIO);
  }

  private void startCall() {
    callClient = new WebRTCCallClient(this, ChatRepository.getInstance(this), this);
    String local = LoginStateManager.getInstance().getUID(this);
    String incomingOffer = getIntent().getStringExtra(EXTRA_SDP_OFFER);
    if (incomingOffer != null && !incomingOffer.isEmpty()) {
      callClient.startIncoming(getIntent().getStringExtra(EXTRA_CALL_ID),
          getIntent().getStringExtra(EXTRA_CALL_CHAT_ID), local,
          getIntent().getStringExtra(EXTRA_CALLER_ID), incomingOffer);
    } else {
      callClient.startOutgoing(getIntent().getStringExtra(EXTRA_CALL_CHAT_ID), local,
          getIntent().getStringExtra(EXTRA_PHONE_NUMBER));
    }
  }

  @Override public void onBack() {
    FloatingVoiceCallController.getInstance().minimizeAndReturn(this);
  }
  @Override public void onAccept() {
    if (!isIncoming() || incomingAccepted) return;
    incomingAccepted = true;
    stopIncomingRingtone();
    callView.hideIncomingPrompt();
    callView.setCallStatus("Connecting…");
    requestMicrophoneAndStart();
  }
  @Override public void onReject() { rejectIncoming(); }
  private void rejectIncoming() {
    stopIncomingRingtone();
    if (!isIncoming() || incomingAccepted) { finish(); return; }
    JsonObject event = new JsonObject();
    event.addProperty("type", "call_reject");
    event.addProperty("callId", getIntent().getStringExtra(EXTRA_CALL_ID));
    event.addProperty("senderId", LoginStateManager.getInstance().getUID(this));
    event.addProperty("receiverId", getIntent().getStringExtra(EXTRA_CALLER_ID));
    ChatRepository.getInstance(this).sendCallEvent(event);
    finish();
  }
  @Override public void onEnd() { if (callClient != null) callClient.endCall(); finish(); }
  private void endFromFloatingView() {
    if (isIncoming() && !incomingAccepted) {
      rejectIncoming();
      return;
    }
    if (callClient != null) callClient.endCall();
    FloatingVoiceCallController.getInstance().clear();
    finish();
  }
  @Override public void onSpeaker() {
    speakerOn = !speakerOn;
    if (audioManager != null) audioManager.setSpeakerphoneOn(speakerOn);
    callView.setAudioState(speakerOn, muted);
  }
  @Override public void onMute() {
    muted = !muted;
    if (audioManager != null) audioManager.setMicrophoneMute(muted);
    if (callClient != null) callClient.setMuted(muted);
    callView.setAudioState(speakerOn, muted);
  }
  @Override public void onState(String state) {
    FloatingVoiceCallController.getInstance().updateStatus(state);
    if ("Connected".equals(state)) {
      ActiveCallRegistry.getInstance().setConnected(this, true);
      if (callView != null) callView.setCallConnected(true);
      stopCallTones();
      startCallTimer();
    } else if ("Connecting…".equals(state)) {
      ActiveCallRegistry.getInstance().setConnected(this, false);
      if (callView != null) callView.setCallConnected(false);
      stopCallTones();
      if (!timerRunning && callView != null) callView.setCallStatus(state);
    }
    else if (!timerRunning && callView != null) {
      ActiveCallRegistry.getInstance().setConnected(this, false);
      callView.setCallConnected(false);
      callView.setCallStatus(state);
    }
  }
  @Override public void onRemoteMuteChanged(boolean muted) {
    if (callView != null) callView.setRemoteMuted(muted);
  }
  @Override public void onCallEvent(JsonObject event) {
    if (incomingAccepted) return;
    String type = com.w3n.pinggo.Database.CloudFunction.Utils.JsonParserUtil.getString(event, "type");
    if ("call_socket_disconnected".equals(type)) {
      Toast.makeText(this, "Call connection lost.", Toast.LENGTH_SHORT).show();
      finish();
      return;
    }
    String callId = com.w3n.pinggo.Database.CloudFunction.Utils.JsonParserUtil.getString(event, "callId");
    if (!getIntent().getStringExtra(EXTRA_CALL_ID).equals(callId)) return;
    if ("call_end".equals(type) || "call_no_answer".equals(type)) {
      Toast.makeText(this, "Call ended.", Toast.LENGTH_SHORT).show();
      finish();
    }
  }
  private void startCallTimer() {
    if (timerRunning) return;
    timerRunning = true;
    connectedAt = SystemClock.elapsedRealtime();
    timerHandler.post(callTimer);
  }
  private void stopCallTimer() {
    timerRunning = false;
    timerHandler.removeCallbacks(callTimer);
  }
  private void startIncomingRingtone() {
    stopCallTones();
    incomingRingtone = RingtoneManager.getRingtone(this,
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
    if (incomingRingtone == null) return;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
      incomingRingtone.setLooping(true);
    }
    incomingToneActive = true;
    incomingRingtone.play();
    timerHandler.postDelayed(incomingToneLoop, 2000);
  }
  private void stopIncomingRingtone() {
    incomingToneActive = false;
    timerHandler.removeCallbacks(incomingToneLoop);
    if (incomingRingtone != null && incomingRingtone.isPlaying()) incomingRingtone.stop();
    incomingRingtone = null;
  }
  private void startOutgoingTone() {
    stopCallTones();
    outgoingTone = new ToneGenerator(AudioManager.STREAM_RING, 100);
    outgoingToneActive = true;
    outgoingToneLoop.run();
  }
  private void stopOutgoingTone() {
    outgoingToneActive = false;
    timerHandler.removeCallbacks(outgoingToneLoop);
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
  @Override public void onEnded(String reason) {
    FloatingVoiceCallController.getInstance().clear();
    stopCallTones();
    stopCallTimer();
    Toast.makeText(this, "Call " + reason.replace('_', ' ') + ".", Toast.LENGTH_SHORT).show(); finish();
  }
  @Override public void onError(String message) {
    FloatingVoiceCallController.getInstance().clear();
    stopCallTones();
    stopCallTimer();
    Toast.makeText(this, message == null || message.isEmpty() ? "Call failed." : message,
        Toast.LENGTH_LONG).show(); finish();
  }
  @Override protected void onDestroy() {
    stopCallTones();
    stopCallTimer();
    if (audioManager != null) {
      audioManager.setSpeakerphoneOn(false);
      audioManager.setMicrophoneMute(false);
    }
    if (callClient != null) callClient.close(true);
    else ChatRepository.getInstance(this).setCallEventListener(null);
    callClient = null;
    if (callView != null) callView.release();
    callView = null;
    FloatingVoiceCallController.getInstance().clear();
    ActiveCallRegistry.getInstance().clear(this);
    super.onDestroy();
  }
}
