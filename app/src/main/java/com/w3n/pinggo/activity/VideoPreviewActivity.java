package com.w3n.pinggo.activity;

import android.graphics.Color;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import com.w3n.pinggo.data.cache.MediaPreviewCache;

/** Full-screen video player opened from a video message. */
public final class VideoPreviewActivity extends AppCompatActivity {
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
  public static final String EXTRA_URI = "media_uri";
  public static final String EXTRA_PHONE_NUMBER = ImagePreviewActivity.EXTRA_PHONE_NUMBER;
  public static final String EXTRA_CHAT_ID = ImagePreviewActivity.EXTRA_CHAT_ID;
  public static final String EXTRA_MESSAGE_ID = ImagePreviewActivity.EXTRA_MESSAGE_ID;
  private static final float[] SPEED_VALUES = {.25f, .5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
  private static final String[] SPEED_LABELS = {
      "0.25×", "0.5×", "0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×"
  };
  private MediaPlayer mediaPlayer;
  private float playbackSpeed = 1f;
  private TextView speedControl;
  private TextView playPauseControl;
  private SeekBar seekBar;
  private VideoView video;
  private ViewGroup topBar;
  private WindowInsetsControllerCompat statusBarController;
  private final Handler seekHandler = new Handler(Looper.getMainLooper());
  private final Runnable seekUpdater = new Runnable() {
    @Override public void run() {
      if (video == null || seekBar == null) return;
      int duration = video.getDuration();
      if (duration > 0) {
        seekBar.setMax(duration);
        if (!seekBar.isPressed()) seekBar.setProgress(video.getCurrentPosition());
      }
      seekHandler.postDelayed(this, 250L);
    }
  };

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    statusBarController = new WindowInsetsControllerCompat(
        getWindow(), getWindow().getDecorView());
    getWindow().setStatusBarColor(Color.TRANSPARENT);
    statusBarController.setAppearanceLightStatusBars(false);
    getWindow().setNavigationBarColor(Color.BLACK);
    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(Color.BLACK);
    video = new VideoView(this);
    root.addView(video, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
    ProgressBar progress = new ProgressBar(this);
    root.addView(progress, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    speedControl = new TextView(this);
    speedControl.setText("1×");
    speedControl.setTextColor(Color.WHITE);
    speedControl.setTextSize(16f);
    speedControl.setGravity(Gravity.CENTER);
    speedControl.setBackgroundColor(Color.TRANSPARENT);
    int horizontalPadding = px(33f);
    speedControl.setPadding(horizontalPadding, 0, horizontalPadding, 0);
    speedControl.setOnClickListener(this::showSpeedMenu);
    playPauseControl = new TextView(this);
    playPauseControl.setText("▶");
    playPauseControl.setTextColor(Color.WHITE);
    playPauseControl.setTextSize(22f);
    playPauseControl.setGravity(Gravity.CENTER);
    playPauseControl.setBackgroundColor(Color.TRANSPARENT);
    playPauseControl.setOnClickListener(view -> togglePlayback());
    seekBar = new SeekBar(this);
    seekBar.setMax(1);
    seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
        if (fromUser && video != null) video.seekTo(progress);
      }

      @Override public void onStartTrackingTouch(SeekBar bar) { }

      @Override public void onStopTrackingTouch(SeekBar bar) { }
    });
    LinearLayout playbackControls = new LinearLayout(this);
    playbackControls.setOrientation(LinearLayout.HORIZONTAL);
    playbackControls.setGravity(Gravity.CENTER);
    playbackControls.setBackgroundColor(0x40000000);
    int controlHeight = px(132f);
    playbackControls.addView(playPauseControl, new LinearLayout.LayoutParams(
        px(154f), controlHeight));
    playbackControls.addView(seekBar, new LinearLayout.LayoutParams(
        0, controlHeight, 1f));
    playbackControls.addView(speedControl, new LinearLayout.LayoutParams(
        px(231f), controlHeight));
    FrameLayout.LayoutParams playbackParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, controlHeight,
        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
    playbackParams.leftMargin = px(44f);
    playbackParams.rightMargin = px(44f);
    playbackParams.bottomMargin = px(66f);
    root.addView(playbackControls, playbackParams);
    topBar = MediaPreviewTopBar.add(
        this,
        root,
        getIntent().getStringExtra(EXTRA_PHONE_NUMBER),
        null,
        this::forwardMessage);
    setMediaStatusBar(true);
    video.setOnClickListener(view -> toggleTopBar());
    setContentView(root);
    video.setOnPreparedListener(player -> {
      mediaPlayer = player;
      progress.setVisibility(android.view.View.GONE);
      seekBar.setMax(Math.max(1, video.getDuration()));
      video.start();
      applyPlaybackSpeed();
      playPauseControl.setText("Ⅱ");
      hideTopBar();
      seekHandler.removeCallbacks(seekUpdater);
      seekHandler.post(seekUpdater);
    });
    video.setOnCompletionListener(player -> {
      seekHandler.removeCallbacks(seekUpdater);
      playPauseControl.setText("▶");
      if (seekBar != null) seekBar.setProgress(seekBar.getMax());
    });
    video.setOnErrorListener((player, what, extra) -> {
      progress.setVisibility(android.view.View.GONE);
      Toast.makeText(this, "This file does not exist.", Toast.LENGTH_SHORT).show();
      finish();
      return true;
    });
    String value = getIntent().getStringExtra(EXTRA_URI);
    if (value == null || value.trim().isEmpty()) {
      Toast.makeText(this, "This file does not exist.", Toast.LENGTH_SHORT).show();
      finish();
      return;
    }
    MediaPreviewCache.resolveMedia(this, value, MediaPreviewCache.TYPE_VIDEO,
        new MediaPreviewCache.Callback<Uri>() {
      @Override public void onSuccess(Uri local) { video.setVideoURI(local); }

      @Override public void onError() {
        progress.setVisibility(android.view.View.GONE);
        Toast.makeText(VideoPreviewActivity.this, "This file does not exist.", Toast.LENGTH_SHORT).show();
        finish();
      }
    });
  }

  private void toggleTopBar() {
    if (topBar == null) return;
    boolean show = topBar.getVisibility() != android.view.View.VISIBLE;
    topBar.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
    setMediaStatusBar(show);
  }

  private void hideTopBar() {
    if (topBar != null) topBar.setVisibility(android.view.View.GONE);
    setMediaStatusBar(false);
  }

  private void setMediaStatusBar(boolean mediaHeaderVisible) {
    getWindow().setStatusBarColor(Color.TRANSPARENT);
    MediaPreviewTopBar.setStatusBarShade(
        topBar, mediaHeaderVisible ? 0x40000000 : Color.BLACK);
    if (statusBarController != null) {
      statusBarController.setAppearanceLightStatusBars(false);
    }
  }

  private void togglePlayback() {
    if (mediaPlayer == null || video == null) return;
    if (video.isPlaying()) {
      video.pause();
      playPauseControl.setText("▶");
    } else {
      video.start();
      applyPlaybackSpeed();
      playPauseControl.setText("Ⅱ");
      hideTopBar();
      seekHandler.removeCallbacks(seekUpdater);
      seekHandler.post(seekUpdater);
    }
  }

  private void showSpeedMenu(android.view.View anchor) {
    PopupMenu menu = new PopupMenu(this, anchor);
    for (int index = 0; index < SPEED_LABELS.length; index++) {
      menu.getMenu().add(0, index, index, SPEED_LABELS[index]);
    }
    menu.setOnMenuItemClickListener(item -> {
      int index = item.getItemId();
      if (index < 0 || index >= SPEED_VALUES.length) return false;
      playbackSpeed = SPEED_VALUES[index];
      speedControl.setText(SPEED_LABELS[index]);
      if (video != null && video.isPlaying()) applyPlaybackSpeed();
      return true;
    });
    menu.show();
  }

  private void applyPlaybackSpeed() {
    if (mediaPlayer == null) return;
    boolean wasPlaying = video != null && video.isPlaying();
    try {
      PlaybackParams params = mediaPlayer.getPlaybackParams();
      mediaPlayer.setPlaybackParams(params.setSpeed(playbackSpeed));
      if (!wasPlaying) mediaPlayer.pause();
    } catch (RuntimeException error) {
      playbackSpeed = 1f;
      speedControl.setText("1×");
      Toast.makeText(this, "Playback speed is not supported for this video.",
          Toast.LENGTH_SHORT).show();
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

  @Override protected void onDestroy() {
    seekHandler.removeCallbacks(seekUpdater);
    mediaPlayer = null;
    super.onDestroy();
  }

  private int px(float value) {
    return Math.round(figmaConfig.toRuntime(value,
        Math.max(1, getResources().getDisplayMetrics().widthPixels)));
  }
}
