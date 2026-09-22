package com.w3n.pinggo.call.session;

import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Media-engine-neutral call owned outside the call Activity. */
public interface CallSession {
  interface Observer {
    void onSessionChanged(@NonNull CallSession session, @NonNull CallSessionState state);
    void onSessionEnded(@NonNull CallSession session, @NonNull String reason);
  }

  @NonNull String callId();
  @NonNull String chatId();
  @NonNull String engine();
  @NonNull String mediaType();
  boolean incoming();
  @NonNull CallSessionState state();
  @NonNull Intent sourceIntent();

  void addObserver(@NonNull Observer observer);
  void removeObserver(@NonNull Observer observer);
  void accept();
  void reject();
  void setHeld(boolean held, @NonNull Runnable completion);
  void setMuted(boolean muted);
  void setSpeakerEnabled(boolean enabled);
  void setCameraEnabled(boolean enabled);
  void end(@NonNull String reason);
  void release();
}
