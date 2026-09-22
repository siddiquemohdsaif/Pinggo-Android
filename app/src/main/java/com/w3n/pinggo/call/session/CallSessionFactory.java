package com.w3n.pinggo.call.session;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import com.w3n.pinggo.call.CallEngineToggle;

/** Builds an engine-specific session while keeping CallActivity engine-neutral. */
public final class CallSessionFactory {
  private CallSessionFactory() { }

  @NonNull public static CallSession create(@NonNull Context context, @NonNull Intent intent) {
    String engine = value(intent, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_CALL_ENGINE);
    if (engine.isEmpty()) engine = CallEngineToggle.selectedEngine();
    String media = value(intent, com.w3n.pinggo.call.session.CallActivityContract.EXTRA_MEDIA_TYPE);
    if (media.isEmpty()) media = intent.getBooleanExtra(CallActivityContract.EXTRA_VIDEO, false)
        ? "video" : "audio";
    if (CallEngineToggle.LIVEKIT.equals(engine))
      return new LiveKitCallSession(context, intent);
    return "video".equals(media) ? new LegacyVideoCallSession(context, intent)
        : new LegacyVoiceCallSession(context, intent);
  }

  private static String value(Intent intent, String key) {
    String result = intent.getStringExtra(key);
    return result == null ? "" : result.trim();
  }
}
