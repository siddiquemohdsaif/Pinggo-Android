package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
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
  private final ZoomableImageView image;
  private final NativeMediaTopBarView header;
  private final NativeReplyComposerView composer;
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
    image = new ZoomableImageView(context);
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

    composer = new NativeReplyComposerView(context, listener::onReply);
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
      composer.setBottomInset(bottomInset);
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

  private void setControlsVisible(boolean visible) {
    header.setVisibility(visible ? View.VISIBLE : View.GONE);
    composer.setVisibility(visible ? View.VISIBLE : View.GONE);
    setNavigationBarState(visible, HEADER_COLOR);
  }

  private void load(String source) {
    MediaPreviewCache.Thumbnail immediate = MediaPreviewCache.anyMemoryThumbnail(source, false);
    if (immediate != null) image.setPreviewBitmap(immediate.bitmap);
    android.util.DisplayMetrics display = getResources().getDisplayMetrics();
    MediaPreviewCache.loadImageForDisplay(getContext(), source,
        display.widthPixels, display.heightPixels, new MediaPreviewCache.Callback<Bitmap>() {
          @Override public void onSuccess(Bitmap result) {
            loading.setVisibility(GONE);
            image.setPreviewBitmap(result);
          }
          @Override public void onError() {
            loading.setVisibility(GONE);
            Toast.makeText(getContext(), "This file does not exist.", Toast.LENGTH_SHORT).show();
            if (immediate == null) listener.onClose();
          }
        });
  }

  @Override public void release() {
    composer.release();
    menu.release();
    header.release();
    loading.release();
    super.release();
  }

  private FrameLayout.LayoutParams match() {
    return new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
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

  /** Fit-center image surface with bounded pinch zoom and one-finger panning. */
  private static final class ZoomableImageView extends ImageView {
    private static final float MAX_ZOOM = 4f;
    private final Matrix transform = new Matrix();
    private final RectF mappedImage = new RectF();
    private final ScaleGestureDetector scaleDetector;
    private final float touchSlop;
    private float zoom = 1f;
    private float lastX, lastY, downX, downY;
    private boolean moved, multiTouch;

    ZoomableImageView(Context context) {
      super(context);
      setScaleType(ScaleType.MATRIX);
      touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
      scaleDetector = new ScaleGestureDetector(context,
          new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
              multiTouch = true;
              return getDrawable() != null;
            }

            @Override public boolean onScale(ScaleGestureDetector detector) {
              float requested = detector.getScaleFactor();
              if (!Float.isFinite(requested) || requested <= 0f) return false;
              float next = clamp(zoom * requested, 1f, MAX_ZOOM);
              float applied = next / zoom;
              zoom = next;
              transform.postScale(applied, applied,
                  detector.getFocusX(), detector.getFocusY());
              constrainTransform();
              return true;
            }
          });
    }

    void setPreviewBitmap(Bitmap bitmap) {
      super.setImageBitmap(bitmap);
      resetTransform();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
      super.onSizeChanged(width, height, oldWidth, oldHeight);
      resetTransform();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
      scaleDetector.onTouchEvent(event);
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          downX = lastX = event.getX();
          downY = lastY = event.getY();
          moved = false;
          multiTouch = false;
          return true;
        case MotionEvent.ACTION_POINTER_DOWN:
          multiTouch = true;
          return true;
        case MotionEvent.ACTION_MOVE:
          float x = event.getX();
          float y = event.getY();
          if (Math.abs(x - downX) > touchSlop || Math.abs(y - downY) > touchSlop)
            moved = true;
          if (!scaleDetector.isInProgress() && zoom > 1f && event.getPointerCount() == 1) {
            transform.postTranslate(x - lastX, y - lastY);
            constrainTransform();
          }
          lastX = x;
          lastY = y;
          return true;
        case MotionEvent.ACTION_UP:
          if (!moved && !multiTouch) performClick();
          return true;
        case MotionEvent.ACTION_CANCEL:
          return true;
        default:
          return true;
      }
    }

    @Override public boolean performClick() {
      super.performClick();
      return true;
    }

    private void resetTransform() {
      if (getDrawable() == null || getWidth() <= 0 || getHeight() <= 0) return;
      float drawableWidth = getDrawable().getIntrinsicWidth();
      float drawableHeight = getDrawable().getIntrinsicHeight();
      if (drawableWidth <= 0f || drawableHeight <= 0f) return;
      float fittedScale = Math.min(getWidth() / drawableWidth, getHeight() / drawableHeight);
      float dx = (getWidth() - drawableWidth * fittedScale) / 2f;
      float dy = (getHeight() - drawableHeight * fittedScale) / 2f;
      transform.reset();
      transform.postScale(fittedScale, fittedScale);
      transform.postTranslate(dx, dy);
      zoom = 1f;
      setImageMatrix(transform);
    }

    private void constrainTransform() {
      if (getDrawable() == null) return;
      mappedImage.set(0f, 0f, getDrawable().getIntrinsicWidth(),
          getDrawable().getIntrinsicHeight());
      transform.mapRect(mappedImage);
      float dx = mappedImage.width() <= getWidth()
          ? getWidth() / 2f - mappedImage.centerX()
          : mappedImage.left > 0f ? -mappedImage.left
          : mappedImage.right < getWidth() ? getWidth() - mappedImage.right : 0f;
      float dy = mappedImage.height() <= getHeight()
          ? getHeight() / 2f - mappedImage.centerY()
          : mappedImage.top > 0f ? -mappedImage.top
          : mappedImage.bottom < getHeight() ? getHeight() - mappedImage.bottom : 0f;
      transform.postTranslate(dx, dy);
      setImageMatrix(transform);
    }

    private static float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
    }
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
