package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.VideoView;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.w3n.pinggo.R;
import com.w3n.pinggo.data.cache.MediaPreviewCache;
import com.w3n.pinggo.views.chat.ConversationMenuDialogView;
import com.ogfa.nativeviews.progress.Progress;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import java.util.Arrays;

/** Complete full-screen video-message overlay. */
public final class VideoPreviewView extends NativeMediaScreenView {
  private static final int HEADER_COLOR = 0xFF4B565E;
  private static final float[] SPEEDS = {.25f, .5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
  private static final String[] LABELS =
      {"0.25×", "0.5×", "0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×"};
  private final Listener listener;
  private final VideoView video;
  private final ZLayerGroup timelineLayers;
  private final ZLayer timelineLayer;
  private final View timelineHost;
  private Progress seek;
  private Text playbackTime;
  private final NativeMediaTopBarView header;
  private final NativeVideoControlsView controls;
  private final NativeReplyComposerView composer;
  private final ConversationMenuDialogView speedMenu;
  private final ConversationMenuDialogView menu;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private MediaPlayer player;
  private float speed = 1f;
  private boolean released;
  private boolean updatingTimeline;
  private final Runnable update = new Runnable() {
    @Override public void run() {
      if (released) return;
      int duration = video.getDuration();
      if (duration > 0) {
        int position = video.getCurrentPosition();
        if (seek != null) {
          updatingTimeline = true;
          seek.setProgressPercent(position * 100f / duration);
          updatingTimeline = false;
        }
        updatePlaybackTime(position, duration);
      }
      handler.postDelayed(this, 250);
    }
  };
  public VideoPreviewView(@NonNull Context context, String source, String senderId,
      String sentTime, Listener listener) {
    super(context);
    this.listener = listener;
    setNavigationBarState(true, HEADER_COLOR);
    menu = new ConversationMenuDialogView(context,
        Arrays.asList("Show in chat", "Download", "Share", "Delete", "View in gallery"),
        this::onMenuOptionSelected);
    video = new VideoView(context);
    addView(video, new FrameLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
    timelineLayers = new ZLayerGroup(this);
    timelineLayer = timelineLayers.addLayer("video_timeline");
    timelineHost = new View(context) {
      @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        timelineLayer.clear();
        if (width <= 0 || height <= 0) return;
        seek = timelineLayer.add(new Progress.Builder(getContext(), "video_seek",
            new RectF(0f, 0f, width, dp(20)))
            .setStyle(Progress.Style.LINEAR).setMode(Progress.Mode.DETERMINATE)
            .setTrackColor(0x66FFFFFF).setProgressColor(Color.WHITE)
            .setThicknessPx(dp(4)).setCornerRadiusPx(dp(2))
            .setProgressPercent(0f)
            .setOnProgressChangedListener((id, percent) -> {
              if (updatingTimeline) return;
              int duration = video.getDuration();
              if (duration <= 0) return;
              int position = Math.round(duration * Math.max(0f, Math.min(100f, percent)) / 100f);
              video.seekTo(position);
              updatePlaybackTime(position, duration);
            }));
        playbackTime = timelineLayer.add(new Text.Builder(getContext(), "video_time",
            "0:00 / 0:00", new RectF(0f, dp(18), width, height))
            .setTextColor(Color.WHITE).setTextSizePx(dp(12))
            .setAlignment(Text.Alignment.CENTER)
            .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(1));
      }
      @Override protected void onDraw(Canvas canvas) { timelineLayers.draw(canvas); }
      @Override public boolean onTouchEvent(MotionEvent event) {
        return timelineLayers.onTouchEvent(event) || super.onTouchEvent(event);
      }
    };
    timelineHost.setClickable(true);
    timelineHost.setContentDescription("Video playback timeline");
    FrameLayout.LayoutParams timelineParams = new FrameLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, dp(48), Gravity.BOTTOM);
    timelineParams.leftMargin = dp(70);
    timelineParams.rightMargin = dp(110);
    timelineParams.bottomMargin = dp(84);
    addView(timelineHost, timelineParams);
    controls = new NativeVideoControlsView(context, new NativeVideoControlsView.Listener() {
      @Override public void onPlayPause() { toggle(); }
      @Override public void onSpeed(View anchor) { showSpeed(anchor); }
    });
    speedMenu = new ConversationMenuDialogView(context, Arrays.asList(LABELS), option -> {
      int index = Arrays.asList(LABELS).indexOf(option);
      if (index < 0) return;
      speed = SPEEDS[index]; controls.setSpeedLabel(LABELS[index]); applySpeed();
    });
    FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, dp(78), Gravity.BOTTOM);
    controlsParams.bottomMargin = dp(84);
    addView(controls, controlsParams);
    // The controls view spans the full width and overlaps the seek bar vertically.
    // Keep the seek bar above it so its transparent center cannot consume scrub gestures.
    timelineHost.bringToFront();
    header = new NativeMediaTopBarView(context, senderId, sentTime, true,
        new NativeMediaTopBarView.Listener() {
          @Override public void onBack() { listener.onClose(); }
          @Override public void onForward() { listener.onForward(); }
          @Override public void onMore(View anchor) { menu.show(); }
        });
    header.setBackgroundColor(0xB34B565E);
    addView(header, new FrameLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, NativeMediaTopBarView.contentHeightPx(context), Gravity.TOP));
    composer = new NativeReplyComposerView(context, listener::onReply);
    addView(composer, new FrameLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, dp(72), Gravity.BOTTOM));
    addView(menu, new FrameLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(speedMenu, new FrameLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    ViewCompat.setOnApplyWindowInsetsListener(this, (view, insets) -> {
      Insets status = insets.getInsets(WindowInsetsCompat.Type.statusBars());
      Insets navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
      Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
      header.setTopInset(status.top);
      ViewGroup.LayoutParams headerParams = header.getLayoutParams();
      headerParams.height = NativeMediaTopBarView.contentHeightPx(getContext()) + status.top;
      header.setLayoutParams(headerParams);
      int bottomInset = Math.max(navigation.bottom, ime.bottom);
      FrameLayout.LayoutParams composerParams =
          (FrameLayout.LayoutParams) composer.getLayoutParams();
      composerParams.height = dp(72) + bottomInset;
      composer.setBottomInset(bottomInset);
      composer.setLayoutParams(composerParams);
      FrameLayout.LayoutParams updatedControls =
          (FrameLayout.LayoutParams) controls.getLayoutParams();
      updatedControls.bottomMargin = dp(84) + bottomInset;
      controls.setLayoutParams(updatedControls);
      FrameLayout.LayoutParams updatedTimeline =
          (FrameLayout.LayoutParams) timelineHost.getLayoutParams();
      updatedTimeline.bottomMargin = dp(84) + bottomInset;
      timelineHost.setLayoutParams(updatedTimeline);
      return insets;
    });
    video.setOnClickListener(view -> setControlsVisible(header.getVisibility() != VISIBLE));
    video.setOnPreparedListener(prepared -> {
      player = prepared;
      int duration = prepared.getDuration();
      if (duration > 0) {
        updatePlaybackTime(video.getCurrentPosition(), duration);
      }
      video.start();
      applySpeed();
      controls.setPlaying(true);
      handler.post(update);
    });
    video.setOnCompletionListener(completed -> {
      controls.setPlaying(false);
      int duration = video.getDuration();
      if (seek != null) {
        updatingTimeline = true;
        seek.setProgressPercent(100f);
        updatingTimeline = false;
      }
      updatePlaybackTime(duration, duration);
      handler.removeCallbacks(update);
    });
    video.setOnErrorListener((failed, what, extra) -> {
      Toast.makeText(context, "This file does not exist.", Toast.LENGTH_SHORT).show();
      listener.onClose();
      return true;
    });
    MediaPreviewCache.resolveMedia(context, source, MediaPreviewCache.TYPE_VIDEO,
        new MediaPreviewCache.Callback<Uri>() {
          @Override public void onSuccess(Uri uri) {
            if (!released) video.setVideoURI(uri);
          }
          @Override public void onError() {
            Toast.makeText(context, "This file does not exist.", Toast.LENGTH_SHORT).show();
            listener.onClose();
          }
        });
  }

  private void onMenuOptionSelected(String option) {
    if ("Show in chat".equals(option)) listener.onShowInChat();
    else if ("Download".equals(option)) listener.onDownload();
    else if ("Share".equals(option)) listener.onShare();
    else if ("Delete".equals(option)) listener.onDelete();
    else if ("View in gallery".equals(option)) listener.onViewInGallery();
  }

  public boolean dismissMenu() { return menu.dismissIfShowing(); }

  private void setControlsVisible(boolean visible) {
    int visibility = visible ? VISIBLE : GONE;
    header.setVisibility(visibility);
    controls.setVisibility(visibility);
    timelineHost.setVisibility(visibility);
    composer.setVisibility(visibility);
    setNavigationBarState(visible, HEADER_COLOR);
  }

  private void toggle() {
    if (player == null) return;
    if (video.isPlaying()) {
      video.pause();
      controls.setPlaying(false);
    } else {
      video.start();
      applySpeed();
      controls.setPlaying(true);
      handler.post(update);
    }
  }

  private void showSpeed(View anchor) {
    speedMenu.show();
  }

  private void applySpeed() {
    if (player == null) return;
    boolean playing = video.isPlaying();
    try {
      PlaybackParams params = player.getPlaybackParams();
      player.setPlaybackParams(params.setSpeed(speed));
      if (!playing) player.pause();
    } catch (RuntimeException error) {
      speed = 1f;
      controls.setSpeedLabel("1×");
      Toast.makeText(getContext(), "Playback speed is not supported for this video.",
          Toast.LENGTH_SHORT).show();
    }
  }

  private void updatePlaybackTime(int positionMs, int durationMs) {
    int safeDuration = Math.max(0, durationMs);
    int safePosition = Math.max(0, Math.min(positionMs, safeDuration));
    String value = formatTime(safePosition) + " / " + formatTime(safeDuration);
    if (playbackTime != null) playbackTime.setText(value);
    timelineHost.setContentDescription("Video playback time " + value);
  }

  private static String formatTime(int milliseconds) {
    long totalSeconds = Math.max(0L, milliseconds) / 1000L;
    long hours = totalSeconds / 3600L;
    long minutes = (totalSeconds % 3600L) / 60L;
    long seconds = totalSeconds % 60L;
    if (hours > 0L) {
      return String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
    }
    return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds);
  }

  public void onHostPause() {
    if (video.isPlaying()) {
      video.pause();
      controls.setPlaying(false);
    }
    handler.removeCallbacks(update);
  }

  public void onHostResume() {
    if (video.isPlaying()) handler.post(update);
  }

  @Override public void release() {
    if (released) return;
    released = true;
    composer.release();
    menu.release();
    speedMenu.release();
    handler.removeCallbacks(update);
    video.stopPlayback();
    player = null;
    timelineLayers.release();
    header.release();
    controls.release();
    super.release();
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  public interface Listener {
    void onClose();
    void onForward();
    void onShowInChat();
    void onDownload();
    void onShare();
    void onDelete();
    void onViewInGallery();
    void onReply(String text);
  }
}
