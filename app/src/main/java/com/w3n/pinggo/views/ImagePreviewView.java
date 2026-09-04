package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.ogfa.nativeviews.progress.Progress;
import com.w3n.pinggo.R;
import com.w3n.pinggo.data.cache.MediaPreviewCache;
import com.w3n.pinggo.views.chat.ConversationMenuDialogView;
import java.util.Arrays;

/** Complete full-screen image-message overlay. */
public final class ImagePreviewView extends NativeMediaScreenView {
  private static final int HEADER_COLOR = 0xFF4B565E;
  private final ImageView image;
  private final NativeMediaTopBarView header;
  private final FrameLayout composer;
  private final EditText replyInput;
  private final ConversationMenuDialogView menu;
  private final NativeProgressOverlay loading;
  private final Listener listener;

  public ImagePreviewView(@NonNull Context context, String source, String senderId,
      String sentTime, Listener listener) {
    super(context);
    this.listener = listener;
    setNavigationBarState(true, HEADER_COLOR);
    menu = new ConversationMenuDialogView(context,
        Arrays.asList("Show in chat", "Download", "Share", "Delete", "View in gallery"),
        this::onMenuOptionSelected);
    image = new ImageView(context);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    addView(image, match());
    loading = new NativeProgressOverlay(context);
    addView(loading, match());
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

    addView(menu, match());

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
      return insets;
    });
    image.setOnClickListener(view -> setControlsVisible(header.getVisibility() != View.VISIBLE));
    load(source);
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
    header.setVisibility(visible ? View.VISIBLE : View.GONE);
    composer.setVisibility(visible ? View.VISIBLE : View.GONE);
    setNavigationBarState(visible, HEADER_COLOR);
  }

  private void load(String source) {
    MediaPreviewCache.Thumbnail immediate = MediaPreviewCache.anyMemoryThumbnail(source, false);
    if (immediate != null) image.setImageBitmap(immediate.bitmap);
    android.util.DisplayMetrics display = getResources().getDisplayMetrics();
    MediaPreviewCache.loadImageForDisplay(getContext(), source,
        display.widthPixels, display.heightPixels, new MediaPreviewCache.Callback<Bitmap>() {
          @Override public void onSuccess(Bitmap result) {
            loading.setVisibility(GONE);
            image.setImageBitmap(result);
          }
          @Override public void onError() {
            loading.setVisibility(GONE);
            Toast.makeText(getContext(), "This file does not exist.", Toast.LENGTH_SHORT).show();
            if (immediate == null) listener.onClose();
          }
        });
  }

  @Override public void release() {
    replyInput.clearFocus();
    menu.release();
    header.release();
    loading.release();
    super.release();
  }

  private FrameLayout.LayoutParams match() {
    return new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
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

  private static final class NativeProgressOverlay extends View {
    private final com.ogfa.nativeviews.zlayer.ZLayerGroup layers =
        new com.ogfa.nativeviews.zlayer.ZLayerGroup(this);
    private final com.ogfa.nativeviews.zlayer.ZLayer layer = layers.addLayer("loading");
    NativeProgressOverlay(Context context) { super(context); }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
      layer.clear();
      float size = 64 * getResources().getDisplayMetrics().density;
      layer.add(new Progress.Builder(getContext(), "image_loading",
          new android.graphics.RectF((width - size) / 2, (height - size) / 2,
              (width + size) / 2, (height + size) / 2))
          .setStyle(Progress.Style.CIRCULAR)
          .setMode(Progress.Mode.INDETERMINATE)
          .setProgressColor(0xFF019CC4));
    }
    @Override protected void onDraw(android.graphics.Canvas canvas) {
      super.onDraw(canvas);
      layers.draw(canvas);
    }
    void release() { layers.release(); }
  }
}
