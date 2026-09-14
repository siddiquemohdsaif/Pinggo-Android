package com.w3n.pinggo.activity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.w3n.pinggo.R;
import com.w3n.pinggo.modals.CallLog;
import com.w3n.pinggo.contacts.DeviceContactResolver;
import com.w3n.pinggo.views.call.CallDetailView;
import java.util.UUID;
import java.util.Collections;

public class CallDetailActivity extends AppCompatActivity implements CallDetailView.Listener {
  public static final String EXTRA_CHAT_ID = "com.w3n.pinggo.EXTRA_CALL_CHAT_ID";
  public static final String EXTRA_PHONE_NUMBER = "com.w3n.pinggo.EXTRA_CALL_PHONE_NUMBER";
  public static final String EXTRA_CONTACT_NAME = "com.w3n.pinggo.EXTRA_CONTACT_NAME";
  public static final String EXTRA_CALLED_TIME = "com.w3n.pinggo.EXTRA_CALLED_TIME";
  public static final String EXTRA_FULL_CALLED_DATE_TIME = "com.w3n.pinggo.EXTRA_FULL_CALLED_DATE_TIME";
  public static final String EXTRA_DURATION = "com.w3n.pinggo.EXTRA_DURATION";
  public static final String EXTRA_IS_VIDEO_CALL = "com.w3n.pinggo.EXTRA_IS_VIDEO_CALL";
  public static final String EXTRA_IS_CONFERENCE = "com.w3n.pinggo.EXTRA_IS_CONFERENCE";
  public static final String EXTRA_IS_OUTGOING = "com.w3n.pinggo.EXTRA_IS_OUTGOING";
  public static final String EXTRA_IS_MISSED = "com.w3n.pinggo.EXTRA_IS_MISSED";
  public static final String EXTRA_PROFILE_PATH = "com.w3n.pinggo.EXTRA_PROFILE_PATH";
  private CallDetailView detailView;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    String calledTime = value(EXTRA_CALLED_TIME, getString(R.string.unknown_time));
    detailView = new CallDetailView(
        this,
        getIntent().getBooleanExtra(EXTRA_IS_CONFERENCE, false)
            ? value(EXTRA_CONTACT_NAME, "Conference call")
            : DeviceContactResolver.nameOrPhone(this, value(EXTRA_PHONE_NUMBER, "")),
        value(EXTRA_PHONE_NUMBER, ""),
        value(EXTRA_FULL_CALLED_DATE_TIME, calledTime),
        value(EXTRA_DURATION, getString(R.string.unknown_duration)),
        getIntent().getBooleanExtra(EXTRA_IS_VIDEO_CALL, false),
        value(EXTRA_PROFILE_PATH, ""),
        this);
    setContentView(detailView);
    ViewCompat.setOnApplyWindowInsetsListener(
        detailView,
        (view, insets) -> {
          Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
          detailView.setInsets(bars.top, bars.bottom);
          return insets;
    });
    ViewCompat.requestApplyInsets(detailView);
    CallLog selectedCall = new CallLog(
        value(EXTRA_CHAT_ID, ""), "", value(EXTRA_PHONE_NUMBER, ""),
        value(EXTRA_CONTACT_NAME, getString(R.string.call)), calledTime,
        value(EXTRA_FULL_CALLED_DATE_TIME, calledTime),
        value(EXTRA_DURATION, getString(R.string.unknown_duration)),
        getIntent().getBooleanExtra(EXTRA_IS_VIDEO_CALL, false),
        getIntent().getBooleanExtra(EXTRA_IS_OUTGOING, false),
        getIntent().getBooleanExtra(EXTRA_IS_MISSED, false),
        getIntent().getBooleanExtra(EXTRA_IS_CONFERENCE, false));
    detailView.submitCalls(Collections.singletonList(selectedCall));
  }

  private String value(String key, String fallback) {
    String value = getIntent().getStringExtra(key);
    return value == null || value.trim().isEmpty() ? fallback : value;
  }

  @Override
  public void onBack() {
    finish();
  }

  @Override
  public void onCallAgain(boolean video) {
    openCall(video);
  }

  @Override
  public void onVoiceCall() {
    openCall(false);
  }

  @Override
  public void onVideoCall() {
    openCall(true);
  }

  @Override
  public void onMessage() {
    Intent intent = new Intent(this, ChatActivity.class);
    intent.putExtra(ChatActivity.EXTRA_CHAT_ID, value(EXTRA_CHAT_ID, ""));
    intent.putExtra(ChatActivity.EXTRA_CHAT_NAME,
        DeviceContactResolver.cachedNameOrPhone(value(EXTRA_PHONE_NUMBER, "")));
    startActivity(intent);
  }

  @Override
  public void onLoadMoreCalls() {
    // The detail screen represents only the row selected in the Calls tab.
  }

  private void openCall(boolean video) {
    String phone = value(EXTRA_PHONE_NUMBER, "");
    Intent intent = new Intent(this, video ? VideoCallActivity.class : VoiceCallActivity.class);
    intent.putExtra(VoiceCallActivity.EXTRA_CALL_CHAT_ID, value(EXTRA_CHAT_ID, ""));
    intent.putExtra(VoiceCallActivity.EXTRA_CALL_ID, UUID.randomUUID().toString());
    intent.putExtra(VoiceCallActivity.EXTRA_CALLER_ID, phone);
    intent.putExtra(VoiceCallActivity.EXTRA_PHONE_NUMBER,
        DeviceContactResolver.cachedNameOrPhone(phone));
    startActivity(intent);
  }

  @Override
  protected void onDestroy() {
    if (detailView != null)
      detailView.release();
    detailView = null;
    super.onDestroy();
  }
}
