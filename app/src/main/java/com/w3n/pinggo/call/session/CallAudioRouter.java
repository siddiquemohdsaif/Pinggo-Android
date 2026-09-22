package com.w3n.pinggo.call.session;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

/** Consistent communication-device routing shared by every call engine. */
public final class CallAudioRouter {
  public interface InterruptionListener {
    void onAudioFocusInterrupted(boolean interrupted);
  }
  private static final String EXTERNAL_CALL_TAG = "PingGoExternalCall";
  private static AudioManager focusAudio;
  private static AudioFocusRequest focusRequest;
  private static boolean focusRequested;
  private static InterruptionListener interruptionListener;
  private static final AudioManager.OnAudioFocusChangeListener FOCUS_LISTENER =
      CallAudioRouter::onAudioFocusChange;
  private CallAudioRouter() { }

  public static synchronized void setInterruptionListener(InterruptionListener listener) {
    interruptionListener = listener;
  }

  public static boolean apply(Context context, boolean speaker) {
    AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    if (audio == null) return false;
    requestFocus(audio);
    audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      AudioDeviceInfo fallback = null;
      for (AudioDeviceInfo device : audio.getAvailableCommunicationDevices()) {
        if (speaker && device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
          return audio.setCommunicationDevice(device);
        if (!speaker && device.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
          return audio.setCommunicationDevice(device);
        if (!speaker && fallback == null
            && device.getType() != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) fallback = device;
      }
      return !speaker && fallback != null && audio.setCommunicationDevice(fallback);
    }
    audio.setSpeakerphoneOn(speaker);
    return audio.isSpeakerphoneOn() == speaker;
  }

  public static void reset(Context context) {
    AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    if (audio == null) return;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.clearCommunicationDevice();
    else audio.setSpeakerphoneOn(false);
    audio.setMicrophoneMute(false);
    audio.setMode(AudioManager.MODE_NORMAL);
  }

  public static void suspendForExternalCall(Context context) {
    Log.i(EXTERNAL_CALL_TAG, "audio_routing_suspended");
    reset(context);
  }

  public static synchronized void release(Context context) {
    AudioManager audio = focusAudio != null ? focusAudio
        : (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    if (audio != null && focusRequested) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null)
        audio.abandonAudioFocusRequest(focusRequest);
      else audio.abandonAudioFocus(FOCUS_LISTENER);
    }
    focusRequested = false;
    focusRequest = null;
    focusAudio = null;
    reset(context);
  }

  private static synchronized void requestFocus(AudioManager audio) {
    if (focusRequested && focusAudio == audio) return;
    focusAudio = audio;
    int result;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
          .setAudioAttributes(new AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
          .setAcceptsDelayedFocusGain(true)
          .setOnAudioFocusChangeListener(FOCUS_LISTENER)
          .build();
      result = audio.requestAudioFocus(focusRequest);
    } else {
      result = audio.requestAudioFocus(FOCUS_LISTENER,
          AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
    }
    focusRequested = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        || result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED;
    Log.i(EXTERNAL_CALL_TAG, "audio_focus_requested result=" + result
        + " retained=" + focusRequested);
  }

  private static void onAudioFocusChange(int change) {
    Log.i(EXTERNAL_CALL_TAG, "audio_focus_changed change=" + change);
    InterruptionListener listener;
    synchronized (CallAudioRouter.class) { listener = interruptionListener; }
    if (listener == null) return;
    if (change == AudioManager.AUDIOFOCUS_GAIN) listener.onAudioFocusInterrupted(false);
    else if (change == AudioManager.AUDIOFOCUS_LOSS
        || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
      listener.onAudioFocusInterrupted(true);
  }
}
