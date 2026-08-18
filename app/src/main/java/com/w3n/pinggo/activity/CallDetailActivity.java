package com.w3n.pinggo.activity;

import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.w3n.pinggo.R;
import com.w3n.pinggo.views.call.CallDetailView;

public class CallDetailActivity extends AppCompatActivity implements CallDetailView.Listener {
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
    EdgeToEdge.enable(this);
    String calledTime = value(EXTRA_CALLED_TIME, getString(R.string.unknown_time));
    detailView =
        new CallDetailView(
            this,
            value(EXTRA_CONTACT_NAME, getString(R.string.call)),
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
    Toast.makeText(this, video ? R.string.video_call : R.string.voice_call, Toast.LENGTH_SHORT)
        .show();
  }

  @Override
  protected void onDestroy() {
    if (detailView != null) detailView.release();
    detailView = null;
    super.onDestroy();
  }
}
