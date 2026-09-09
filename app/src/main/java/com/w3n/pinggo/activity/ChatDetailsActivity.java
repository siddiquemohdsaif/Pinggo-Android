package com.w3n.pinggo.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.w3n.pinggo.R;
import com.w3n.pinggo.views.chat.NativeChatDetailsView;

import java.util.ArrayList;
import java.util.List;

/** Shared details surface for direct conversations and groups. */
public final class ChatDetailsActivity extends AppCompatActivity {
  private NativeChatDetailsView detailsView;
  public static final String EXTRA_CHAT_ID = "pinggo.details.CHAT_ID";
  public static final String EXTRA_IS_GROUP = "pinggo.details.IS_GROUP";
  public static final String EXTRA_NAME = "pinggo.details.NAME";
  public static final String EXTRA_PHONE = "pinggo.details.PHONE";
  public static final String EXTRA_PROFILE_PATH = "pinggo.details.PROFILE_PATH";
  public static final String EXTRA_DESCRIPTION = "pinggo.details.DESCRIPTION";
  public static final String EXTRA_MEMBER_COUNT = "pinggo.details.MEMBER_COUNT";
  public static final String EXTRA_ROLE = "pinggo.details.ROLE";

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    boolean group = getIntent().getBooleanExtra(EXTRA_IS_GROUP, false);
    String name = value(EXTRA_NAME, group ? "Group" : "Chat");

    String profilePath = getIntent().getStringExtra(EXTRA_PROFILE_PATH);
    Bitmap bitmap = profilePath == null ? null : BitmapFactory.decodeFile(profilePath);
    if (bitmap == null) bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pinggo_logo);
    List<String> details = new ArrayList<>();
    if (group) {
      String description = value(EXTRA_DESCRIPTION, "No group description");
      int members = getIntent().getIntExtra(EXTRA_MEMBER_COUNT, 0);
      String role = value(EXTRA_ROLE, "member");
      details.add(description);
      details.add(members > 0
          ? members + (members == 1 ? " member" : " members")
          : "Members");
      details.add("Your role: " + role);
    } else {
      details.add(value(EXTRA_PHONE, "Phone number unavailable"));
    }
    detailsView = new NativeChatDetailsView(this, group ? "Group details" : "Chat details",
        name, bitmap, details, this::finish);
    setContentView(detailsView);
    ViewCompat.setOnApplyWindowInsetsListener(detailsView, (view, insets) -> {
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
      detailsView.setInsets(bars.top, bars.bottom);
      return insets;
    });
    ViewCompat.requestApplyInsets(detailsView);
  }

  @Override protected void onDestroy() {
    if (detailsView != null) detailsView.release();
    detailsView = null;
    super.onDestroy();
  }

  private String value(String key, String fallback) {
    String value = getIntent().getStringExtra(key);
    return value == null || value.trim().isEmpty() ? fallback : value.trim();
  }

}
