package com.w3n.pinggo.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.w3n.pinggo.R;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.modals.CallLog;
import com.w3n.pinggo.views.call.CallDetailView;
import java.util.UUID;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CallDetailActivity extends AppCompatActivity implements CallDetailView.Listener {
  public static final String EXTRA_CHAT_ID = "com.w3n.pinggo.EXTRA_CALL_CHAT_ID";
  public static final String EXTRA_PHONE_NUMBER = "com.w3n.pinggo.EXTRA_CALL_PHONE_NUMBER";
  public static final String EXTRA_CONTACT_NAME = "com.w3n.pinggo.EXTRA_CONTACT_NAME";
  public static final String EXTRA_CALLED_TIME = "com.w3n.pinggo.EXTRA_CALLED_TIME";
  public static final String EXTRA_FULL_CALLED_DATE_TIME =
      "com.w3n.pinggo.EXTRA_FULL_CALLED_DATE_TIME";
  public static final String EXTRA_DURATION = "com.w3n.pinggo.EXTRA_DURATION";
  public static final String EXTRA_IS_VIDEO_CALL = "com.w3n.pinggo.EXTRA_IS_VIDEO_CALL";
  private CallDetailView detailView;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    String calledTime = value(EXTRA_CALLED_TIME, getString(R.string.unknown_time));
    detailView =
        new CallDetailView(
            this,
            value(EXTRA_CONTACT_NAME, getString(R.string.call)),
            value(EXTRA_PHONE_NUMBER, ""),
            value(EXTRA_FULL_CALLED_DATE_TIME, calledTime),
            value(EXTRA_DURATION, getString(R.string.unknown_duration)),
            getIntent().getBooleanExtra(EXTRA_IS_VIDEO_CALL, false),
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
    loadCallHistory(value(EXTRA_CHAT_ID, ""));
  }

  private void loadCallHistory(String chatId) {
    if (chatId.isEmpty()) {
      detailView.showError("Call history unavailable.");
      return;
    }
    String userId = LoginStateManager.getInstance().getUID(this);
    detailView.showLoading();
    AppFunctionManager.getInstance().getCallLogs(userId, chatId,
        new AppFunctionManager.Callback() {
          @Override public void onSuccess(Object object) {
            if (isFinishing() || !(object instanceof JsonObject)) return;
            JsonArray values = ((JsonObject) object).getAsJsonArray("calls");
            List<CallLog> calls = new ArrayList<>();
            DateFormat rowTime = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
            DateFormat fullTime = DateFormat.getDateTimeInstance(
                DateFormat.LONG, DateFormat.SHORT, Locale.getDefault());
            String currentUser = normalize(LoginStateManager.getInstance().getUID(
                CallDetailActivity.this));
            if (values != null) for (JsonElement element : values) {
              if (!element.isJsonObject()) continue;
              JsonObject call = element.getAsJsonObject();
              long endedAt = number(call, "endedAt");
              Date date = new Date(endedAt > 0 ? endedAt : number(call, "createdAt"));
              calls.add(new CallLog(chatId, string(call, "messageId"),
                  value(EXTRA_PHONE_NUMBER, ""),
                  value(EXTRA_CONTACT_NAME, getString(R.string.call)),
                  rowTime.format(date), fullTime.format(date),
                  formatDuration(number(call, "durationSeconds")),
                  "video".equals(string(call, "mediaType")),
                  currentUser.equals(normalize(string(call, "callerId"))),
                  number(call, "connectedAt") <= 0));
            }
            detailView.submitCalls(calls);
          }
          @Override public void onError(String error) {
            if (!isFinishing()) detailView.showError("Unable to load call history.");
          }
        });
  }

  private static String string(JsonObject object, String key) {
    JsonElement value = object.get(key);
    return value == null || value.isJsonNull() ? "" : value.getAsString();
  }

  private static long number(JsonObject object, String key) {
    JsonElement value = object.get(key);
    return value == null || value.isJsonNull() ? 0L : value.getAsLong();
  }

  private static String normalize(String value) {
    String result = value == null ? "" : value.trim();
    if (result.startsWith("<plus>")) result = result.substring(6);
    return result.startsWith("+") ? result.substring(1) : result;
  }

  private static String formatDuration(long seconds) {
    if (seconds <= 0) return "0 sec";
    long hours = seconds / 3600;
    long minutes = (seconds % 3600) / 60;
    long remaining = seconds % 60;
    return hours > 0
        ? String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, remaining)
        : String.format(Locale.getDefault(), "%d:%02d", minutes, remaining);
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

  @Override public void onVoiceCall() { openCall(false); }
  @Override public void onVideoCall() { openCall(true); }
  @Override public void onMessage() {
    Intent intent = new Intent(this, ChatActivity.class);
    intent.putExtra(ChatActivity.EXTRA_CHAT_ID, value(EXTRA_CHAT_ID, ""));
    intent.putExtra(ChatActivity.EXTRA_CHAT_NAME,
        value(EXTRA_CONTACT_NAME, getString(R.string.call)));
    startActivity(intent);
  }

  private void openCall(boolean video) {
    String phone = value(EXTRA_PHONE_NUMBER, "");
    Intent intent = new Intent(this, video ? VideoCallActivity.class : VoiceCallActivity.class);
    intent.putExtra(VoiceCallActivity.EXTRA_CALL_CHAT_ID, value(EXTRA_CHAT_ID, ""));
    intent.putExtra(VoiceCallActivity.EXTRA_CALL_ID, UUID.randomUUID().toString());
    intent.putExtra(VoiceCallActivity.EXTRA_CALLER_ID, phone);
    intent.putExtra(VoiceCallActivity.EXTRA_PHONE_NUMBER,
        phone.isEmpty() ? "Unknown" : "+" + phone);
    startActivity(intent);
  }

  @Override
  protected void onDestroy() {
    if (detailView != null) detailView.release();
    detailView = null;
    super.onDestroy();
  }
}
