package com.w3n.pinggo.call.session;

import android.content.Context;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/** Activity-scoped incoming ringtone and outgoing ringback player. */
public final class CallTonePlayer {
  private final Context context;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Ringtone ringtone;
  private ToneGenerator ringback;
  private boolean incoming, outgoing;
  private final Runnable incomingLoop = new Runnable() {
    @Override public void run() {
      if (!incoming) return;
      if (ringtone != null && !ringtone.isPlaying()) ringtone.play();
      handler.postDelayed(this, 2_000L);
    }
  };
  private final Runnable outgoingLoop = new Runnable() {
    @Override public void run() {
      if (!outgoing || ringback == null) return;
      ringback.startTone(ToneGenerator.TONE_SUP_RINGTONE, 2_500);
      handler.postDelayed(this, 4_000L);
    }
  };

  public CallTonePlayer(Context context) { this.context = context; }

  public void show(CallSessionState.Phase phase, boolean isIncoming) {
    if (phase == CallSessionState.Phase.INCOMING
        || (isIncoming && phase == CallSessionState.Phase.RINGING)) {
      startIncoming();
    } else if (!isIncoming && (phase == CallSessionState.Phase.CALLING
        || phase == CallSessionState.Phase.RINGING)) {
      startOutgoing();
    } else {
      stop();
    }
  }

  private void startIncoming() {
    if (incoming) return;
    stop();
    ringtone = RingtoneManager.getRingtone(context,
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
    if (ringtone == null) return;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ringtone.setLooping(true);
    incoming = true;
    ringtone.play();
    handler.postDelayed(incomingLoop, 2_000L);
  }

  private void startOutgoing() {
    if (outgoing) return;
    stop();
    ringback = new ToneGenerator(AudioManager.STREAM_RING, 100);
    outgoing = true;
    outgoingLoop.run();
  }

  public void stop() {
    incoming = false;
    outgoing = false;
    handler.removeCallbacks(incomingLoop);
    handler.removeCallbacks(outgoingLoop);
    if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
    ringtone = null;
    if (ringback != null) {
      ringback.stopTone();
      ringback.release();
      ringback = null;
    }
  }
}
