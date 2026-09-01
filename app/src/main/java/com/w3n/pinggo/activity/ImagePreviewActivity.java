package com.w3n.pinggo.activity;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import com.w3n.pinggo.data.cache.MediaPreviewCache;

/** Full-screen image viewer opened from an image message. */
public final class ImagePreviewActivity extends AppCompatActivity {
  public static final String EXTRA_URI = "media_uri";
  public static final String EXTRA_PHONE_NUMBER = "media_phone_number";
  public static final String EXTRA_CHAT_ID = "media_chat_id";
  public static final String EXTRA_MESSAGE_ID = "media_message_id";
  private ViewGroup topBar;
  private WindowInsetsControllerCompat statusBarController;
  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    statusBarController = new WindowInsetsControllerCompat(
        getWindow(), getWindow().getDecorView());
    getWindow().setStatusBarColor(Color.TRANSPARENT);
    statusBarController.setAppearanceLightStatusBars(false);
    getWindow().setNavigationBarColor(Color.BLACK);
    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(Color.BLACK);
    ImageView image = new ImageView(this);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    root.addView(image, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    ProgressBar progress = new ProgressBar(this);
    FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
    root.addView(progress, progressParams);
    topBar = MediaPreviewTopBar.add(
        this,
        root,
        getIntent().getStringExtra(EXTRA_PHONE_NUMBER),
        null,
        this::forwardMessage);
    setMediaStatusBar(true);
    image.setOnClickListener(view -> toggleTopBar());
    setContentView(root);
    String value = getIntent().getStringExtra(EXTRA_URI);
    MediaPreviewCache.Thumbnail immediate =
        MediaPreviewCache.anyMemoryThumbnail(value, false);
    if (immediate != null) image.setImageBitmap(immediate.bitmap);
    android.util.DisplayMetrics display = getResources().getDisplayMetrics();
    MediaPreviewCache.loadImageForDisplay(this, value,
        display.widthPixels, display.heightPixels,
        new MediaPreviewCache.Callback<Bitmap>() {
      @Override public void onSuccess(Bitmap result) {
        progress.setVisibility(android.view.View.GONE);
        image.setImageBitmap(result);
      }

      @Override public void onError() {
        progress.setVisibility(android.view.View.GONE);
        Toast.makeText(ImagePreviewActivity.this, "This file does not exist.", Toast.LENGTH_SHORT).show();
        if (immediate == null) finish();
      }
    });
  }

  private void toggleTopBar() {
    if (topBar == null) return;
    boolean show = topBar.getVisibility() != android.view.View.VISIBLE;
    topBar.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
    setMediaStatusBar(show);
  }

  private void setMediaStatusBar(boolean mediaHeaderVisible) {
    getWindow().setStatusBarColor(Color.TRANSPARENT);
    MediaPreviewTopBar.setStatusBarShade(
        topBar, mediaHeaderVisible ? 0x40000000 : Color.BLACK);
    if (statusBarController != null) {
      statusBarController.setAppearanceLightStatusBars(false);
    }
  }

  private void forwardMessage() {
    String chatId = getIntent().getStringExtra(EXTRA_CHAT_ID);
    String messageId = getIntent().getStringExtra(EXTRA_MESSAGE_ID);
    if (chatId == null || chatId.isEmpty() || messageId == null || messageId.isEmpty()) {
      Toast.makeText(this, "Message cannot be forwarded.", Toast.LENGTH_SHORT).show();
      return;
    }
    Intent intent = new Intent(this, NewChatActivity.class);
    intent.putExtra(NewChatActivity.EXTRA_FORWARD_SOURCE_CHAT_ID, chatId);
    java.util.ArrayList<String> messageIds = new java.util.ArrayList<>();
    messageIds.add(messageId);
    intent.putStringArrayListExtra(NewChatActivity.EXTRA_FORWARD_MESSAGE_IDS, messageIds);
    startActivity(intent);
  }
}
