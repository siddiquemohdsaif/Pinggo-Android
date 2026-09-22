package com.w3n.pinggo.call.session;

import androidx.annotation.NonNull;

/** Immutable state rendered by the single call Activity. */
public final class CallSessionState {
  public enum Phase { INCOMING, CALLING, RINGING, CONNECTING, CONNECTED, HELD, ENDED, FAILED }

  public final Phase phase;
  public final String status;
  public final boolean muted;
  public final boolean speakerEnabled;
  public final boolean cameraEnabled;
  public final boolean peerMuted;
  public final boolean peerCameraEnabled;
  public final long connectedAtMs;

  public CallSessionState(@NonNull Phase phase, String status, boolean muted,
      boolean speakerEnabled, boolean cameraEnabled, boolean peerMuted,
      boolean peerCameraEnabled, long connectedAtMs) {
    this.phase = phase;
    this.status = status == null ? "" : status;
    this.muted = muted;
    this.speakerEnabled = speakerEnabled;
    this.cameraEnabled = cameraEnabled;
    this.peerMuted = peerMuted;
    this.peerCameraEnabled = peerCameraEnabled;
    this.connectedAtMs = Math.max(0L, connectedAtMs);
  }

  public static CallSessionState initial(boolean incoming, boolean video) {
    return new CallSessionState(incoming ? Phase.INCOMING : Phase.CALLING,
        incoming ? "Incoming " + (video ? "video" : "voice") + " call" : "Calling…",
        false, video, video, false, video, 0L);
  }

  public CallSessionState withPhase(Phase value, String text) {
    return new CallSessionState(value, text, muted, speakerEnabled, cameraEnabled,
        peerMuted, peerCameraEnabled, connectedAtMs);
  }
}
