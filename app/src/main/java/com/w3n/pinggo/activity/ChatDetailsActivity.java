package com.w3n.pinggo.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.w3n.pinggo.R;
import com.w3n.pinggo.views.chat.NativeChatDetailsView;
import com.w3n.pinggo.views.home.ProfilePhotoPreviewView;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/** Shared details surface for direct conversations and groups. */
public final class ChatDetailsActivity extends PingGoActivity {
  private NativeChatDetailsView detailsView;
  private ProfilePhotoPreviewView profilePhotoPreview;
  private Bitmap profileBitmap;
  private String profilePath;
  private String phone;
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
    boolean group = getIntent().getBooleanExtra(EXTRA_IS_GROUP, false);
    String name = value(EXTRA_NAME, group ? "Group" : "Chat");

    profilePath = getIntent().getStringExtra(EXTRA_PROFILE_PATH);
    phone = value(EXTRA_PHONE, "");
    Bitmap bitmap = profilePath == null ? null : BitmapFactory.decodeFile(profilePath);
    if (bitmap == null) bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pinggo_logo);
    profileBitmap = bitmap;
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
      details.add(phone.isEmpty() ? "Phone number unavailable" : phone);
    }
    detailsView = new NativeChatDetailsView(this, group ? "Group details" : "Chat details",
        name, bitmap, details, this::finish, this::showProfilePhoto);
    setContentView(detailsView);
    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override public void handleOnBackPressed() {
        if (profilePhotoPreview != null) { closeProfilePhotoPreview(); return; }
        setEnabled(false);
        getOnBackPressedDispatcher().onBackPressed();
        setEnabled(true);
      }
    });
    ViewCompat.setOnApplyWindowInsetsListener(detailsView, (view, insets) -> {
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
      detailsView.setInsets(bars.top, bars.bottom);
      return insets;
    });
    ViewCompat.requestApplyInsets(detailsView);
  }

  private void showProfilePhoto() {
    closeProfilePhotoPreview();
    profilePhotoPreview = new ProfilePhotoPreviewView(this);
    ((ViewGroup) findViewById(android.R.id.content)).addView(profilePhotoPreview,
        new ViewGroup.LayoutParams(-1, -1));
    profilePhotoPreview.show(profileBitmap, profilePath, phone,
        this::closeProfilePhotoPreview);
    ViewCompat.requestApplyInsets(profilePhotoPreview);
  }

  private void closeProfilePhotoPreview() {
    ProfilePhotoPreviewView current = profilePhotoPreview;
    profilePhotoPreview = null;
    if (current == null) return;
    current.dismiss();
    if (current.getParent() instanceof ViewGroup)
      ((ViewGroup) current.getParent()).removeView(current);
    current.release();
  }

  @Override protected void onDestroy() {
    closeProfilePhotoPreview();
    if (detailsView != null) detailsView.release();
    detailsView = null;
    super.onDestroy();
  }

  private String value(String key, String fallback) {
    String value = getIntent().getStringExtra(key);
    return value == null || value.trim().isEmpty() ? fallback : value.trim();
  }

}
