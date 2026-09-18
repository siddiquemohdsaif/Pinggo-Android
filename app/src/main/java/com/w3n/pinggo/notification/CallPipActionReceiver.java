package com.w3n.pinggo.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.w3n.pinggo.call.ActiveCallRegistry;
import com.w3n.pinggo.call.CallPictureInPicture;

/** Handles explicit call controls exposed by the system Picture-in-Picture window. */
public final class CallPipActionReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context context, Intent intent) {
    if (intent == null || !CallPictureInPicture.ACTION_HANG_UP.equals(intent.getAction())) return;
    Log.i("PingGoDisconnectHook", "stage=pip_hangup_action_received");
    ActiveCallRegistry.getInstance().requestPictureInPictureHangup();
  }
}
