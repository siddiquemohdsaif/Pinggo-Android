package com.w3n.pinggo.activity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.w3n.pinggo.R;
import com.w3n.pinggo.modals.CallLog;
import com.w3n.pinggo.contacts.DeviceContactResolver;
import com.w3n.pinggo.views.call.CallDetailView;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Collections;
import java.util.LinkedHashSet;

public class CallDetailActivity extends AppCompatActivity implements CallDetailView.Listener {
  private static final int SYSTEM_BAR_COLOR = 0xFFF7F9FB;
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
  public static final String EXTRA_PARTICIPANT_IDS =
      "com.w3n.pinggo.EXTRA_CALL_PARTICIPANT_IDS";
  private CallDetailView detailView;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    configureSystemBars();
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
        getIntent().getBooleanExtra(EXTRA_IS_CONFERENCE, false),
        getIntent().getStringArrayListExtra(EXTRA_PARTICIPANT_IDS),
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

  private void configureSystemBars() {
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    getWindow().setStatusBarColor(SYSTEM_BAR_COLOR);
    getWindow().setNavigationBarColor(SYSTEM_BAR_COLOR);
    WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
        getWindow(), getWindow().getDecorView());
    controller.setAppearanceLightStatusBars(true);
    controller.setAppearanceLightNavigationBars(true);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
      getWindow().setNavigationBarContrastEnforced(false);
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
    boolean conference = getIntent().getBooleanExtra(EXTRA_IS_CONFERENCE, false);
    ArrayList<String> participants = conferenceParticipants();
    Intent intent = new Intent(this, conference ? LiveKitCallActivity.class
        : video ? VideoCallActivity.class : VoiceCallActivity.class);
    intent.putExtra(VoiceCallActivity.EXTRA_CALL_CHAT_ID, value(EXTRA_CHAT_ID, ""));
    intent.putExtra(VoiceCallActivity.EXTRA_CALL_ID, UUID.randomUUID().toString());
    intent.putExtra(VoiceCallActivity.EXTRA_CALLER_ID,
        conference && !participants.isEmpty() ? participants.get(0) : phone);
    intent.putExtra(VoiceCallActivity.EXTRA_PHONE_NUMBER,
        conference ? value(EXTRA_CONTACT_NAME, "Conference call")
            : DeviceContactResolver.cachedNameOrPhone(phone));
    intent.putExtra(VoiceCallActivity.EXTRA_PROFILE_PATH, value(EXTRA_PROFILE_PATH, ""));
    if (conference) {
      intent.putExtra(LiveKitCallActivity.EXTRA_MEDIA_TYPE, video ? "video" : "audio");
      intent.putExtra(LiveKitCallActivity.EXTRA_CONFERENCE_CALL, true);
      intent.putStringArrayListExtra(LiveKitCallActivity.EXTRA_PARTICIPANT_IDS, participants);
    }
    startActivity(intent);
  }

  private ArrayList<String> conferenceParticipants() {
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    ArrayList<String> saved = getIntent().getStringArrayListExtra(EXTRA_PARTICIPANT_IDS);
    if (saved != null) {
      for (String participant : saved) {
        String normalized = normalizeAccountId(participant);
        if (!normalized.isEmpty()) unique.add(normalized);
      }
    }
    String fallback = normalizeAccountId(value(EXTRA_PHONE_NUMBER, ""));
    if (!fallback.isEmpty()) unique.add(fallback);
    return new ArrayList<>(unique);
  }

  private static String normalizeAccountId(String value) {
    if (value == null) return "";
    String normalized = value.trim();
    if (normalized.startsWith("<plus>")) normalized = normalized.substring(6);
    else if (normalized.startsWith("+")) normalized = normalized.substring(1);
    return normalized;
  }

  @Override
  protected void onDestroy() {
    if (detailView != null)
      detailView.release();
    detailView = null;
    super.onDestroy();
  }
}
