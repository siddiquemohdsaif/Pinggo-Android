package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.VideoView;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.w3n.pinggo.R;
import com.w3n.pinggo.data.cache.MediaPreviewCache;
import com.w3n.pinggo.views.chat.ConversationMenuDialogView;
import java.util.Arrays;

/** Complete full-screen video-message overlay. */
public final class VideoPreviewView extends NativeMediaScreenView {
  private static final int HEADER_COLOR = 0xFF4B565E;
  private static final float[] SPEEDS = {.25f, .5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
  private static final String[] LABELS =
      {"0.25×", "0.5×", "0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×"};
  private final Listener listener;
  private final VideoView video;
  private final SeekBar seek;
  private final NativeMediaTopBarView header;
  private final NativeVideoControlsView controls;
  private final FrameLayout composer;
  private final EditText replyInput;
  private final ConversationMenuDialogView menu;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private MediaPlayer player;
  private float speed = 1f;
  private boolean released;
  private final Runnable update = new Runnable() {
    @Override public void run() {
      if (released) return;
      int duration = video.getDuration();
      if (duration > 0) {
        seek.setMax(duration);
        if (!seek.isPressed()) seek.setProgress(video.getCurrentPosition());
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
    seek = new SeekBar(context);
    seek.setBackgroundColor(Color.TRANSPARENT);
    FrameLayout.LayoutParams seekParams = new FrameLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, dp(48), Gravity.BOTTOM);
    seekParams.leftMargin = dp(70);
    seekParams.rightMargin = dp(110);
    seekParams.bottomMargin = dp(100);
    addView(seek, seekParams);
    controls = new NativeVideoControlsView(context, new NativeVideoControlsView.Listener() {
      @Override public void onPlayPause() { toggle(); }
      @Override public void onSpeed(View anchor) { showSpeed(anchor); }
    });
    FrameLayout.LayoutParams controlsParams = new FrameLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, dp(78), Gravity.BOTTOM);
    controlsParams.bottomMargin = dp(84);
    addView(controls, controlsParams);
    header = new NativeMediaTopBarView(context, senderId, sentTime, true,
        new NativeMediaTopBarView.Listener() {
          @Override public void onBack() { listener.onClose(); }
          @Override public void onForward() { listener.onForward(); }
          @Override public void onMore(View anchor) { menu.show(); }
        });
    header.setBackgroundColor(0xB34B565E);
    addView(header, new FrameLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, NativeMediaTopBarView.contentHeightPx(context), Gravity.TOP));
    composer = new FrameLayout(context);
    composer.setBackgroundColor(0x66000000);
    replyInput = new EditText(context);
    replyInput.setSingleLine(false);
    replyInput.setHorizontallyScrolling(false);
    replyInput.setMaxLines(4);
    replyInput.setHint("Reply");
    replyInput.setHintTextColor(0xFF9EA8AE);
    replyInput.setTextColor(Color.WHITE);
    replyInput.setTextSize(17f);
    replyInput.setPadding(dp(18), 0, dp(58), 0);
    replyInput.setBackground(rounded(0xFF1D2A31, dp(28)));
    replyInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
    replyInput.setOnEditorActionListener((view, actionId, event) -> {
      if (actionId != EditorInfo.IME_ACTION_SEND) return false;
      sendReply();
      return true;
    });
    FrameLayout.LayoutParams inputParams = new FrameLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, dp(56), Gravity.TOP);
    inputParams.setMargins(dp(12), dp(8), dp(12), 0);
    composer.addView(replyInput, inputParams);
    ImageButton send = new ImageButton(context);
    send.setImageResource(R.drawable.conversation_send);
    send.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    send.setPadding(dp(13), dp(13), dp(13), dp(13));
    send.setBackground(rounded(0xFF019CC4, dp(26)));
    send.setContentDescription("Send reply");
    send.setOnClickListener(view -> sendReply());
    FrameLayout.LayoutParams sendParams = new FrameLayout.LayoutParams(
        dp(48), dp(48), Gravity.TOP | Gravity.END);
    sendParams.setMargins(0, dp(12), dp(16), 0);
    composer.addView(send, sendParams);
    addView(composer, new FrameLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, dp(72), Gravity.BOTTOM));
    addView(menu, new FrameLayout.LayoutParams(
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
      composer.setPadding(0, 0, 0, bottomInset);
      composer.setLayoutParams(composerParams);
      FrameLayout.LayoutParams updatedControls =
          (FrameLayout.LayoutParams) controls.getLayoutParams();
      updatedControls.bottomMargin = dp(84) + bottomInset;
      controls.setLayoutParams(updatedControls);
      FrameLayout.LayoutParams updatedSeek = (FrameLayout.LayoutParams) seek.getLayoutParams();
      updatedSeek.bottomMargin = dp(100) + bottomInset;
      seek.setLayoutParams(updatedSeek);
      return insets;
    });
    video.setOnClickListener(view -> setControlsVisible(header.getVisibility() != VISIBLE));
    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
        if (fromUser) video.seekTo(progress);
      }
      @Override public void onStartTrackingTouch(SeekBar bar) {}
      @Override public void onStopTrackingTouch(SeekBar bar) {}
    });
    video.setOnPreparedListener(prepared -> {
      player = prepared;
      video.start();
      applySpeed();
      controls.setPlaying(true);
      handler.post(update);
    });
    video.setOnCompletionListener(completed -> {
      controls.setPlaying(false);
      seek.setProgress(seek.getMax());
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

  private void sendReply() {
    String reply = replyInput.getText().toString().trim();
    if (reply.isEmpty()) return;
    listener.onReply(reply);
    replyInput.setText("");
  }

  private void setControlsVisible(boolean visible) {
    int visibility = visible ? VISIBLE : GONE;
    header.setVisibility(visibility);
    controls.setVisibility(visibility);
    seek.setVisibility(visibility);
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
    PopupMenu menu = new PopupMenu(getContext(), anchor);
    for (int index = 0; index < LABELS.length; index++) {
      menu.getMenu().add(0, index, index, LABELS[index]);
    }
    menu.setOnMenuItemClickListener(item -> {
      int index = item.getItemId();
      if (index < 0 || index >= SPEEDS.length) return false;
      speed = SPEEDS[index];
      controls.setSpeedLabel(LABELS[index]);
      applySpeed();
      return true;
    });
    menu.show();
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
    replyInput.clearFocus();
    menu.release();
    handler.removeCallbacks(update);
    video.stopPlayback();
    player = null;
    header.release();
    controls.release();
    super.release();
  }

  private GradientDrawable rounded(int color, float radius) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(color);
    drawable.setCornerRadius(radius);
    return drawable;
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
