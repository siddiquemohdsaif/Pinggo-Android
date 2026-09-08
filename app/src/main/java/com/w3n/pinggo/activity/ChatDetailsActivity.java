package com.w3n.pinggo.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.w3n.pinggo.R;

/** Shared details surface for direct conversations and groups. */
public final class ChatDetailsActivity extends AppCompatActivity {
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

    LinearLayout page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setBackgroundColor(0xFFF7F9FB);
    page.setPadding(dp(20), 0, dp(20), dp(32));

    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    ImageButton back = new ImageButton(this);
    back.setImageResource(android.R.drawable.ic_media_previous);
    back.setBackgroundColor(Color.TRANSPARENT);
    back.setContentDescription("Back");
    back.setOnClickListener(view -> finish());
    header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(56)));
    TextView title = label(group ? "Group details" : "Chat details", 23, true);
    header.addView(title, new LinearLayout.LayoutParams(0, dp(72), 1f));
    page.addView(header);

    ScrollView scroll = new ScrollView(this);
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setGravity(Gravity.CENTER_HORIZONTAL);
    content.setPadding(0, dp(24), 0, dp(24));

    ImageView avatar = new ImageView(this);
    avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
    String profilePath = getIntent().getStringExtra(EXTRA_PROFILE_PATH);
    Bitmap bitmap = profilePath == null ? null : BitmapFactory.decodeFile(profilePath);
    avatar.setImageBitmap(bitmap);
    if (bitmap == null)
      avatar.setImageResource(R.drawable.pinggo_logo);
    content.addView(avatar, new LinearLayout.LayoutParams(dp(132), dp(132)));

    TextView nameView = label(name, 24, true);
    nameView.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    nameParams.topMargin = dp(18);
    content.addView(nameView, nameParams);

    if (group) {
      String description = value(EXTRA_DESCRIPTION, "No group description");
      int members = getIntent().getIntExtra(EXTRA_MEMBER_COUNT, 0);
      String role = value(EXTRA_ROLE, "member");
      content.addView(detail(description));
      content.addView(detail(members > 0
          ? members + (members == 1 ? " member" : " members")
          : "Members"));
      content.addView(detail("Your role: " + role));
    } else {
      content.addView(detail(value(EXTRA_PHONE, "Phone number unavailable")));
    }
    scroll.addView(content, new ScrollView.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    page.addView(scroll, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    setContentView(page);

    ViewCompat.setOnApplyWindowInsetsListener(page, (view, insets) -> {
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
      view.setPadding(dp(20), bars.top, dp(20), bars.bottom + dp(24));
      return insets;
    });
    ViewCompat.requestApplyInsets(page);
  }

  private TextView detail(String value) {
    TextView view = label(value, 16, false);
    view.setTextColor(0xFF687382);
    view.setGravity(Gravity.CENTER);
    view.setPadding(dp(16), dp(14), dp(16), dp(14));
    return view;
  }

  private TextView label(String value, int sp, boolean bold) {
    TextView view = new TextView(this);
    view.setText(value);
    view.setTextSize(sp);
    view.setTextColor(0xFF000E1A);
    if (bold)
      view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    return view;
  }

  private String value(String key, String fallback) {
    String value = getIntent().getStringExtra(key);
    return value == null || value.trim().isEmpty() ? fallback : value.trim();
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
