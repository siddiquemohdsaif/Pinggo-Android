package com.w3n.pinggo.call.session;

import android.app.Service;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;
import androidx.core.content.ContextCompat;
import androidx.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Owns call sessions independently from the lifecycle of the single call Activity. */
public final class CallSessionService extends Service implements CallSessionRegistry.Listener {
  private static final String EXTERNAL_CALL_TAG = "PingGoExternalCall";
  private static final CallSessionRegistry REGISTRY = new CallSessionRegistry();
  private static volatile boolean externalCallActive;
  public final class LocalBinder extends Binder {
    public CallSessionService service() { return CallSessionService.this; }
  }

  private final LocalBinder binder = new LocalBinder();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Set<String> externallyHeldCallIds = new LinkedHashSet<>();
  private TelephonyManager telephony;
  private AudioManager recordingAudioManager;
  private AudioManager.AudioRecordingCallback recordingCallback;
  private TelephonyCallback telephonyCallback;
  private PhoneStateListener legacyPhoneListener;
  private boolean cellularCallActive, audioFocusInterrupted, voipCommunicationActive;
  private boolean externalFocusCandidate;
  private boolean captureSilenced;
  private int recordingConfigurationCount;
  private final Runnable captureConflictConfirmation = new Runnable() {
    @Override public void run() {
      if (!captureSilenced || !externalFocusCandidate
          || cellularCallActive || voipCommunicationActive) {
        Log.i(EXTERNAL_CALL_TAG, "capture_conflict_ignored silenced=" + captureSilenced
            + " audioFocus=" + audioFocusInterrupted
            + " externalFocusCandidate=" + externalFocusCandidate
            + " cellularConnected=" + cellularCallActive
            + " voipConnected=" + voipCommunicationActive);
        return;
      }
      CallSession active = REGISTRY.active();
      if (active == null || active.state().phase != CallSessionState.Phase.CONNECTED) {
        Log.i(EXTERNAL_CALL_TAG, "capture_conflict_ignored reason=no_connected_pinggo_call");
        return;
      }
      voipCommunicationActive = true;
      Log.i(EXTERNAL_CALL_TAG, "capture_conflict_confirmed callId=" + active.callId()
          + " configurations=" + recordingConfigurationCount);
      updateExternalCallState("confirmed_microphone_conflict");
      CallAudioRouter.suspendForExternalCall(CallSessionService.this);
      handler.removeCallbacks(externalCallEndProbe);
      handler.postDelayed(externalCallEndProbe, 1000L);
    }
  };
  private final Runnable externalCallEndProbe = new Runnable() {
    @Override public void run() {
      if (!voipCommunicationActive || cellularCallActive) return;
      AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
      int mode = audio == null ? AudioManager.MODE_INVALID : audio.getMode();
      boolean connected = mode == AudioManager.MODE_IN_CALL
          || mode == AudioManager.MODE_IN_COMMUNICATION
          || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
              && mode == AudioManager.MODE_COMMUNICATION_REDIRECT);
      Log.i(EXTERNAL_CALL_TAG, "voip_end_probe mode=" + mode
          + " connected=" + connected);
      if (!connected) {
        voipCommunicationActive = false;
        updateExternalCallState("voip_audio_mode_ended");
      } else handler.postDelayed(this, 1000L);
    }
  };
  public CallSessionRegistry registry() { return REGISTRY; }
  public static CallSessionRegistry sharedRegistry() { return REGISTRY; }
  public static boolean isExternalCallActive() { return externalCallActive; }

  @Override public void onCreate() {
    super.onCreate();
    REGISTRY.addListener(this);
    CallAudioRouter.setInterruptionListener(interrupted -> handler.post(() ->
        handleAudioFocusInterruption(interrupted)));
    registerPhoneStateMonitoring();
    registerRecordingMonitoring();
  }

  @Nullable @Override public IBinder onBind(Intent intent) { return binder; }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    registerPhoneStateMonitoring();
    return START_STICKY;
  }

  @Override public void onDestroy() {
    externalCallActive = false;
    handler.removeCallbacks(captureConflictConfirmation);
    handler.removeCallbacks(externalCallEndProbe);
    REGISTRY.removeListener(this);
    unregisterPhoneStateMonitoring();
    unregisterRecordingMonitoring();
    CallAudioRouter.setInterruptionListener(null);
    for (CallSession session : REGISTRY.sessions()) REGISTRY.remove(session.callId());
    CallAudioRouter.release(this);
    super.onDestroy();
  }

  @SuppressWarnings("deprecation")
  private void registerPhoneStateMonitoring() {
    if (telephonyCallback != null || legacyPhoneListener != null) return;
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
        != PackageManager.PERMISSION_GRANTED) {
      Log.i(EXTERNAL_CALL_TAG, "cellular_monitor_unavailable permission=READ_PHONE_STATE");
      return;
    }
    telephony = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
    if (telephony == null) return;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      final class CallStateCallback extends TelephonyCallback
          implements TelephonyCallback.CallStateListener {
        @Override public void onCallStateChanged(int state) { handlePhoneState(state); }
      }
      telephonyCallback = new CallStateCallback();
      telephony.registerTelephonyCallback(getMainExecutor(), telephonyCallback);
    } else {
      legacyPhoneListener = new PhoneStateListener() {
        @Override public void onCallStateChanged(int state, String phoneNumber) {
          handlePhoneState(state);
        }
      };
      telephony.listen(legacyPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);
    }
    Log.i(EXTERNAL_CALL_TAG, "cellular_monitor_registered");
  }

  @SuppressWarnings("deprecation")
  private void unregisterPhoneStateMonitoring() {
    if (telephony == null) return;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null)
      telephony.unregisterTelephonyCallback(telephonyCallback);
    else if (legacyPhoneListener != null)
      telephony.listen(legacyPhoneListener, PhoneStateListener.LISTEN_NONE);
    telephonyCallback = null;
    legacyPhoneListener = null;
  }

  private void handlePhoneState(int state) {
    // RINGING only announces the incoming call. OFFHOOK means it was answered
    // or dialled and is now allowed to place the Pinggo call on hold.
    cellularCallActive = state == TelephonyManager.CALL_STATE_OFFHOOK;
    Log.i(EXTERNAL_CALL_TAG, "cellular_state state=" + state
        + " connected=" + cellularCallActive);
    updateExternalCallState("cellular");
  }

  private void handleAudioFocusInterruption(boolean interrupted) {
    audioFocusInterrupted = interrupted;
    handler.removeCallbacks(captureConflictConfirmation);
    handler.removeCallbacks(externalCallEndProbe);
    if (interrupted) {
      if (cellularCallActive) {
        Log.i(EXTERNAL_CALL_TAG, "audio_focus_lost source=connected_cellular");
        return;
      }
      CallSession active = REGISTRY.active();
      externalFocusCandidate = recordingConfigurationCount > 0
          && active != null
          && active.state().phase == CallSessionState.Phase.CONNECTED;
      // MODE_IN_COMMUNICATION cannot identify the owner. Pinggo/WebRTC itself
      // uses that mode. AudioSwitch also replaces our focus during startup,
      // before Pinggo recording is active. Only arm external-call detection if
      // focus is lost after a connected call is already recording.
      maybeConfirmCaptureConflict("audio_focus_lost");
      Log.i(EXTERNAL_CALL_TAG,
          "voip_focus_lost externalCandidate=" + externalFocusCandidate
              + " recordingConfigurations=" + recordingConfigurationCount
              + " routingSuspended=false");
      return;
    }
    externalFocusCandidate = false;
    captureSilenced = false;
    voipCommunicationActive = false;
    updateExternalCallState("audio_focus_restored");
    CallSession active = REGISTRY.active();
    if (active != null && active.state().phase == CallSessionState.Phase.CONNECTED)
      CallAudioRouter.apply(this, active.state().speakerEnabled);
  }

  private void registerRecordingMonitoring() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || recordingCallback != null) return;
    recordingAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    if (recordingAudioManager == null) return;
    recordingCallback = new AudioManager.AudioRecordingCallback() {
      @Override public void onRecordingConfigChanged(
          List<AudioRecordingConfiguration> configurations) {
        boolean silenced = false;
        if (configurations != null) {
          for (AudioRecordingConfiguration configuration : configurations) {
            if (configuration != null && configuration.isClientSilenced()) {
              silenced = true;
              break;
            }
          }
        }
        final boolean captureSilenced = silenced;
        handler.post(() -> handleCaptureSilenced(captureSilenced,
            configurations == null ? 0 : configurations.size()));
      }
    };
    recordingAudioManager.registerAudioRecordingCallback(recordingCallback, handler);
    Log.i(EXTERNAL_CALL_TAG, "recording_monitor_registered");
  }

  private void unregisterRecordingMonitoring() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        && recordingAudioManager != null && recordingCallback != null)
      recordingAudioManager.unregisterAudioRecordingCallback(recordingCallback);
    recordingCallback = null;
    recordingAudioManager = null;
  }

  private void handleCaptureSilenced(boolean silenced, int configurationCount) {
    captureSilenced = silenced;
    recordingConfigurationCount = configurationCount;
    handler.removeCallbacks(captureConflictConfirmation);
    Log.i(EXTERNAL_CALL_TAG, "recording_state configurations=" + configurationCount
        + " pinggoCaptureSilenced=" + silenced
        + " audioFocus=" + audioFocusInterrupted);
    if (!silenced) return;
    maybeConfirmCaptureConflict("recording_callback");
  }

  private void maybeConfirmCaptureConflict(String source) {
    if (!captureSilenced || !externalFocusCandidate
        || voipCommunicationActive || cellularCallActive) {
      Log.i(EXTERNAL_CALL_TAG, "capture_conflict_not_scheduled source=" + source
          + " silenced=" + captureSilenced
          + " audioFocus=" + audioFocusInterrupted
          + " externalFocusCandidate=" + externalFocusCandidate);
      return;
    }
    // isClientSilenced() alone is not proof of another call: Android can also
    // silence an app because of foreground/background capture policy. Require
    // a separate audio-focus takeover and a short stable period before holding.
    handler.postDelayed(captureConflictConfirmation, 750L);
    Log.i(EXTERNAL_CALL_TAG, "capture_conflict_pending source=" + source
        + " delayMs=750 configurations=" + recordingConfigurationCount);
  }

  private void updateExternalCallState(String source) {
    boolean interrupted = cellularCallActive || voipCommunicationActive;
    externalCallActive = interrupted;
    Log.i(EXTERNAL_CALL_TAG, "external_state source=" + source
        + " cellularConnected=" + cellularCallActive
        + " voipConnected=" + voipCommunicationActive
        + " audioFocus=" + audioFocusInterrupted
        + " interrupted=" + interrupted);
    if (interrupted) {
      holdForExternalCall(source);
    } else {
      resumeAfterExternalCall(source);
    }
  }

  private void holdForExternalCall(String source) {
    for (CallSession session : REGISTRY.sessions()) {
      if (session.state().phase != CallSessionState.Phase.CONNECTED) continue;
      if (!externallyHeldCallIds.add(session.callId())) continue;
      Log.i(EXTERNAL_CALL_TAG, "auto_hold_requested callId=" + session.callId()
          + " source=" + source);
      session.setHeld(true, () -> Log.i(EXTERNAL_CALL_TAG,
          "auto_hold_applied callId=" + session.callId()));
    }
    // Routing is yielded when focus is lost or telephony becomes connected.
    if (cellularCallActive && !audioFocusInterrupted)
      CallAudioRouter.suspendForExternalCall(this);
  }

  private void resumeAfterExternalCall(String source) {
    CallSession active = REGISTRY.active();
    if (active == null || !externallyHeldCallIds.remove(active.callId())) return;
    if (active.state().phase != CallSessionState.Phase.HELD) return;
    Log.i(EXTERNAL_CALL_TAG, "auto_resume_requested callId=" + active.callId()
        + " source=" + source);
    active.setHeld(false, () -> {
      CallAudioRouter.apply(this, active.state().speakerEnabled);
      Log.i(EXTERNAL_CALL_TAG, "auto_resume_applied callId=" + active.callId());
    });
  }

  @Override public void onActiveSessionChanged(@Nullable CallSession session) {
    if (session != null) return;
    externallyHeldCallIds.clear();
    CallAudioRouter.release(this);
    Log.i(EXTERNAL_CALL_TAG, "audio_focus_released reason=no_pinggo_calls");
  }
}
