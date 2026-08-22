package com.w3n.pinggo.activity;

import android.media.AudioManager;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.w3n.pinggo.views.call.VoiceActiveCallView;

public class VoiceCallActivity extends AppCompatActivity implements VoiceActiveCallView.Listener {
  public static final String EXTRA_PHONE_NUMBER = "com.w3n.pinggo.EXTRA_CALL_PHONE_NUMBER";
  public static final String EXTRA_PROFILE_PATH = "com.w3n.pinggo.EXTRA_CALL_PROFILE_PATH";
  private VoiceActiveCallView callView;
  private AudioManager audioManager;
  private boolean speakerOn, muted;

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    EdgeToEdge.enable(this);
    audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    callView = new VoiceActiveCallView(this, getIntent().getStringExtra(EXTRA_PHONE_NUMBER),
        getIntent().getStringExtra(EXTRA_PROFILE_PATH), this);
    setContentView(callView);
    ViewCompat.setOnApplyWindowInsetsListener(callView, (view, insets) -> {
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
      callView.setInsets(bars.top, bars.bottom);
      return insets;
    });
    ViewCompat.requestApplyInsets(callView);
  }

  @Override public void onBack() { finish(); }
  @Override public void onEnd() { finish(); }
  @Override public void onSpeaker() {
    speakerOn = !speakerOn;
    if (audioManager != null) audioManager.setSpeakerphoneOn(speakerOn);
    callView.setAudioState(speakerOn, muted);
  }
  @Override public void onMute() {
    muted = !muted;
    if (audioManager != null) audioManager.setMicrophoneMute(muted);
    callView.setAudioState(speakerOn, muted);
  }
  @Override protected void onDestroy() {
    if (audioManager != null) {
      audioManager.setSpeakerphoneOn(false);
      audioManager.setMicrophoneMute(false);
    }
    if (callView != null) callView.release();
    callView = null;
    super.onDestroy();
  }
}
