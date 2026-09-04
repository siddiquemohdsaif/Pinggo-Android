package com.w3n.pinggo.views;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.w3n.pinggo.R;
import com.w3n.pinggo.views.common.NativePromptDialogView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.FileProvider;
import android.content.ContentValues;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;

/** Previews captured or gallery media and owns selection changes before final send. */
public class SelectedMediaOverlayView extends NativeMediaScreenView {
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
  public static final String EXTRA_URIS = "com.w3n.pinggo.PREVIEW_URIS";
  public static final String EXTRA_TYPES = "com.w3n.pinggo.PREVIEW_TYPES";
  public static final String EXTRA_CAPTURED = "com.w3n.pinggo.PREVIEW_CAPTURED";
  private static final int ACCENT = 0xFF019CC4;

  private final List<PreviewItem> items = new ArrayList<>();
  private final ExecutorService thumbnailExecutor = Executors.newSingleThreadExecutor();
  private final ExecutorService videoFrameExecutor = Executors.newSingleThreadExecutor();
  private final Listener listener;
  private final String senderId;
  private final FrameLayout root;
  private ImageView imagePreview;
  private SelectedImageEditorView imageEditor;
  private TextureView videoPreview;
  private NativeFilePreviewView filePreview;
  private FrameLayout videoTools;
  private VideoTrimStripView videoTrimStrip;
  private NativeSelectedVideoToolsView videoInfo;
  private final Map<String, VideoEditState> videoEdits = new HashMap<>();
  private HorizontalScrollView selectionStrip;
  private LinearLayout selectionItems;
  private NativeSelectedMediaChromeView chrome;
  private FrameLayout captionBar;
  private EditText captionInput;
  private EditorTextInput textInput;
  private View editorScrim;
  private NativePromptDialogView editPrompt;
  private int editorMode = NativeSelectedMediaChromeView.EDITOR_NORMAL;
  private PreviewItem activeItem;
  private MediaPlayer videoPlayer;
  private Uri pendingVideoUri;
  private int videoFrameGeneration;
  private boolean captured;
  private final Runnable enforceVideoTrim = new Runnable() {
    @Override public void run() {
      if (videoPlayer == null || activeItem == null || !activeItem.isVideo()) return;
      VideoEditState edit = videoEdit(activeItem);
      long start = Math.round(edit.durationMs * edit.startFraction);
      long end = Math.round(edit.durationMs * edit.endFraction);
      if (end > start && videoPlayer.getCurrentPosition() >= end) {
        videoPlayer.seekTo((int) Math.min(Integer.MAX_VALUE, start));
      }
      postDelayed(this, 100L);
    }
  };

  public SelectedMediaOverlayView(Context context, List<Uri> uris, List<String> types,
      boolean captured, Listener listener) {
    this(context, uris, types, captured, "", listener);
  }

  public SelectedMediaOverlayView(Context context, List<Uri> uris, List<String> types,
      boolean captured, String senderId, Listener listener) {
    super(context);
    setNavigationBarState(true, NativeSelectedMediaChromeView.HEADER_COLOR);
    this.root = this;
    this.listener = listener;
    this.senderId = senderId == null ? "" : senderId;
    this.captured = captured;
    readItems(uris, types);
    if (items.isEmpty()) {
      post(() -> listener.onCancel(new ArrayList<>(), new ArrayList<>()));
      return;
    }
    buildUi();
    refreshPreview(items.get(0));
  }

  private void readItems(List<Uri> uris, List<String> types) {
    if (uris == null) return;
    for (int index = 0; index < uris.size(); index++) {
      Uri uri = uris.get(index);
      if (uri == null) continue;
      boolean duplicate = false;
      for (PreviewItem item : items) duplicate |= item.uri.equals(uri);
      if (duplicate) continue;
      String type = types != null && index < types.size() ? types.get(index) : "Image";
      items.add(new PreviewItem(uri, normalizedType(type), selectedFileName(uri)));
    }
  }

  private void buildUi() {
    imagePreview = new ImageView(getContext());
    imagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
    imagePreview.setVisibility(View.GONE);
    root.addView(imagePreview, match());
    imageEditor = new SelectedImageEditorView(getContext());
    imageEditor.setHistoryChangedListener(() -> {
      if (chrome != null) chrome.setUndoAvailable(imageEditor.canUndoStroke());
    });
    imageEditor.setVisibility(View.GONE);
    root.addView(imageEditor, match());

    videoPreview = new TextureView(getContext());
    videoPreview.setVisibility(View.GONE);
    videoPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
      @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int w, int h) {
        startVideo();
      }
      @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int w, int h) {
        if (videoPlayer != null) transformVideo(videoPlayer.getVideoWidth(), videoPlayer.getVideoHeight());
      }
      @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        releaseVideo();
        return true;
      }
      @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
    });
    root.addView(videoPreview, match());

    filePreview = new NativeFilePreviewView(getContext());
    filePreview.setVisibility(View.GONE);
    root.addView(filePreview, match());

    videoTools = new FrameLayout(getContext());
    videoTools.setVisibility(View.GONE);
    videoTrimStrip = new VideoTrimStripView(getContext(), this::onVideoRangeChanged);
    FrameLayout.LayoutParams trimParams = new FrameLayout.LayoutParams(
        px(918f), px(112f), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    videoTools.addView(videoTrimStrip, trimParams);
    videoInfo = new NativeSelectedVideoToolsView(getContext(), this::toggleVideoNoise);
    FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, px(72f), Gravity.BOTTOM);
    infoParams.leftMargin = px(22f);
    videoTools.addView(videoInfo, infoParams);
    FrameLayout.LayoutParams videoToolsParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, px(198f), Gravity.TOP);
    videoToolsParams.topMargin = px(185f);
    root.addView(videoTools, videoToolsParams);

    selectionStrip = new HorizontalScrollView(getContext());
    selectionStrip.setHorizontalScrollBarEnabled(false);
    selectionStrip.setBackgroundColor(Color.TRANSPARENT);
    selectionStrip.setPadding(px(22f), px(19.25f), px(22f), px(19.25f));
    selectionItems = new LinearLayout(getContext());
    selectionItems.setOrientation(LinearLayout.HORIZONTAL);
    selectionItems.setGravity(Gravity.CENTER_VERTICAL);
    selectionStrip.addView(selectionItems, new HorizontalScrollView.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    FrameLayout.LayoutParams stripParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, px(150f), Gravity.BOTTOM);
    stripParams.bottomMargin = px(300f);
    root.addView(selectionStrip, stripParams);

    captionBar = new FrameLayout(getContext());
    captionBar.setBackground(rounded(0xFF121820, px(58f)));
    Button gallery = new Button(getContext());
    gallery.setText("▣+");
    gallery.setTextColor(Color.WHITE);
    gallery.setTextSize(25f);
    gallery.setBackgroundColor(Color.TRANSPARENT);
    gallery.setOnClickListener(view -> {
      Selection selection = selection();
      listener.onCameraRequested(selection.uris, selection.types);
    });
    captionBar.addView(gallery, new FrameLayout.LayoutParams(px(110f), px(110f), Gravity.START));
    captionInput = new EditText(getContext());
    captionInput.setSingleLine(true);
    captionInput.setHint("Add a caption...");
    captionInput.setHintTextColor(0xFFB8C0C7);
    captionInput.setTextColor(Color.WHITE);
    captionInput.setTextSize(18f);
    captionInput.setBackgroundColor(Color.TRANSPARENT);
    FrameLayout.LayoutParams captionParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, px(110f));
    captionParams.leftMargin = px(110f);
    captionParams.rightMargin = px(24f);
    captionBar.addView(captionInput, captionParams);
    FrameLayout.LayoutParams captionBarParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, px(110f), Gravity.BOTTOM);
    captionBarParams.leftMargin = px(22f);
    captionBarParams.rightMargin = px(22f);
    captionBarParams.bottomMargin = px(178f);
    root.addView(captionBar, captionBarParams);

    editorScrim = new View(getContext());
    editorScrim.setBackgroundColor(0x88000000);
    editorScrim.setClickable(true);
    editorScrim.setVisibility(View.GONE);
    root.addView(editorScrim, match());

    textInput = new EditorTextInput(getContext());
    textInput.setKeyboardDismissListener(() -> post(this::finishEditorMode));
    textInput.setSingleLine(true);
    textInput.setGravity(Gravity.CENTER);
    textInput.setTextColor(Color.BLACK);
    textInput.setTextSize(30f);
    textInput.setBackground(rounded(Color.WHITE, px(18f)));
    textInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
    textInput.setVisibility(View.GONE);
    textInput.setOnEditorActionListener((view, actionId, event) -> {
      boolean keyboardDone = actionId == EditorInfo.IME_ACTION_DONE
          || event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
          && event.getAction() == KeyEvent.ACTION_UP;
      if (!keyboardDone) return false;
      finishEditorMode();
      return true;
    });
    FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, px(130f), Gravity.CENTER);
    textParams.leftMargin = px(80f);
    textParams.rightMargin = px(80f);
    root.addView(textInput, textParams);

    chrome = new NativeSelectedMediaChromeView(getContext(), captured, senderId,
        new NativeSelectedMediaChromeView.Listener() {
          @Override public void onBack() { onBackPressed(); }
          @Override public void onRemove() { removeActiveOrRetake(); }
          @Override public void onRemoveAll() { deselectAll(); }
          @Override public void onSend() { sendSelection(); }
          @Override public void onDownload() { downloadActiveEdit(); }
          @Override public void onRotate() { if (activeItem != null && activeItem.isImage()) imageEditor.rotateClockwise(); }
          @Override public void onText() { beginEditorText(); }
          @Override public void onDraw() { beginDrawing(); }
          @Override public void onDone() { finishEditorMode(); }
          @Override public void onUndo() { imageEditor.undoLastStroke(); }
        });
    root.addView(chrome, match());
    ViewCompat.setOnApplyWindowInsetsListener(chrome, (view, insets) -> {
      Insets status = insets.getInsets(WindowInsetsCompat.Type.statusBars());
      chrome.setTopInset(status.top);
      FrameLayout.LayoutParams videoParams = (FrameLayout.LayoutParams) videoTools.getLayoutParams();
      videoParams.topMargin = status.top + px(165f);
      videoTools.setLayoutParams(videoParams);
      return insets;
    });
    rebuildStrip();
  }

  private void rebuildStrip() {
    selectionItems.removeAllViews();
    selectionStrip.setVisibility(items.size() > 1 ? View.VISIBLE : View.GONE);
    chrome.setState(previewTitle(), items.size() > 1);
    for (PreviewItem item : items) {
      ImageView thumbnail = new ImageView(getContext());
      thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
      thumbnail.setOnClickListener(view -> refreshPreview(item));
      selectionItems.addView(thumbnail, thumbnailParams());
      if (item.isFile()) {
        thumbnail.setImageResource(R.drawable.chat_document);
        continue;
      }
      thumbnailExecutor.execute(() -> {
        Bitmap bitmap = loadThumbnail(item.uri);
        post(() -> {
          if (isAttachedToWindow() && bitmap != null) thumbnail.setImageBitmap(bitmap);
        });
      });
    }
  }

  private LinearLayout.LayoutParams thumbnailParams() {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(px(192.5f), px(192.5f));
    params.setMarginEnd(px(19.25f));
    return params;
  }

  private Bitmap loadThumbnail(Uri uri) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return getContext().getContentResolver().loadThumbnail(uri, new Size(px(264f), px(264f)), null);
      }
    } catch (IOException | RuntimeException ignored) { }
    return null;
  }

  private void prepareVideoFrames(PreviewItem item) {
    int generation = ++videoFrameGeneration;
    final int frameCount = 9;
    VideoEditState edit = videoEdit(item);
    videoTrimStrip.clearFrames(frameCount);
    videoTrimStrip.setRange(edit.startFraction, edit.endFraction);
    videoInfo.setNoiseEnabled(edit.audioEnabled);
    updateVideoMetadata(item, edit);
    videoFrameExecutor.execute(() -> extractVideoFrames(item, generation, frameCount));
  }

  private void extractVideoFrames(PreviewItem item, int generation, int frameCount) {
    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    try {
      retriever.setDataSource(getContext(), item.uri);
      long duration = parseLong(retriever.extractMetadata(
          MediaMetadataRetriever.METADATA_KEY_DURATION));
      long size = selectedFileSize(item.uri);
      post(() -> {
        if (generation == videoFrameGeneration && activeItem == item) {
          VideoEditState edit = videoEdit(item);
          edit.durationMs = duration;
          edit.originalSize = size;
          updateVideoMetadata(item, edit);
        }
      });
      for (int index = 0; index < frameCount; index++) {
        if (generation != videoFrameGeneration || Thread.currentThread().isInterrupted()) return;
        long positionMs = frameCount <= 1 ? 0L : duration * index / (frameCount - 1L);
        Bitmap frame = retriever.getFrameAtTime(positionMs * 1000L,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        final int frameIndex = index;
        post(() -> {
          if (generation != videoFrameGeneration || activeItem != item) return;
          if (frame != null) videoTrimStrip.setFrame(frameIndex, frame);
        });
      }
    } catch (RuntimeException ignored) {
    } finally {
      try { retriever.release(); } catch (IOException | RuntimeException ignored) {}
    }
  }

  private void toggleVideoNoise() {
    if (activeItem == null || !activeItem.isVideo()) return;
    VideoEditState edit = videoEdit(activeItem);
    edit.audioEnabled = !edit.audioEnabled;
    videoInfo.setNoiseEnabled(edit.audioEnabled);
    if (videoPlayer != null) {
      float volume = edit.audioEnabled ? 1f : 0f;
      videoPlayer.setVolume(volume, volume);
    }
  }

  private void onVideoRangeChanged(float start, float end, boolean finished) {
    if (activeItem == null || !activeItem.isVideo()) return;
    VideoEditState edit = videoEdit(activeItem);
    edit.startFraction = start;
    edit.endFraction = end;
    updateVideoMetadata(activeItem, edit);
    long position = Math.round(edit.durationMs * start);
    if (videoPlayer != null) videoPlayer.seekTo((int) Math.min(Integer.MAX_VALUE, position));
  }

  private VideoEditState videoEdit(PreviewItem item) {
    VideoEditState state = videoEdits.get(item.uri.toString());
    if (state == null) {
      state = new VideoEditState();
      state.originalSize = selectedFileSize(item.uri);
      videoEdits.put(item.uri.toString(), state);
    }
    return state;
  }

  private void updateVideoMetadata(PreviewItem item, VideoEditState edit) {
    float selectedFraction = Math.max(0f, edit.endFraction - edit.startFraction);
    long duration = Math.round(edit.durationMs * selectedFraction);
    long size = Math.round(edit.originalSize * selectedFraction
        * (edit.audioEnabled ? 1f : 0.88f));
    videoInfo.setMetadata(formatDuration(duration) + " • " + formatSize(size));
  }

  private void refreshPreview(PreviewItem item) {
    activeItem = item;
    pendingVideoUri = null;
    releaseVideo();
    imagePreview.setImageDrawable(null);
    imagePreview.setVisibility(View.GONE);
    imageEditor.clearActive();
    imageEditor.setVisibility(View.GONE);
    videoPreview.setVisibility(View.GONE);
    filePreview.setVisibility(View.GONE);
    videoTools.setVisibility(View.GONE);
    videoFrameGeneration++;
    if (item.isVideo()) {
      pendingVideoUri = item.uri;
      videoPreview.setVisibility(View.VISIBLE);
      videoTools.setVisibility(View.VISIBLE);
      prepareVideoFrames(item);
      startVideo();
    } else if (item.isImage()) {
      imageEditor.setVisibility(View.VISIBLE);
      android.util.DisplayMetrics display = getResources().getDisplayMetrics();
      com.w3n.pinggo.data.cache.MediaPreviewCache.loadImageForDisplay(
          getContext(), item.uri.toString(), display.widthPixels, display.heightPixels,
          new com.w3n.pinggo.data.cache.MediaPreviewCache.Callback<Bitmap>() {
            @Override public void onSuccess(Bitmap bitmap) {
              if (activeItem == item) imageEditor.bind(item.uri.toString(), bitmap);
            }
            @Override public void onError() {
              Toast.makeText(getContext(), "Unable to preview this image.", Toast.LENGTH_SHORT).show();
            }
          });
    } else {
      filePreview.setFileName(item.name);
      filePreview.setVisibility(View.VISIBLE);
    }
    chrome.setState(item.isFile() ? item.name : previewTitle(), items.size() > 1);
    chrome.setMediaType(item.type);
    chrome.setEditorMode(NativeSelectedMediaChromeView.EDITOR_NORMAL);
    captionBar.setVisibility(View.VISIBLE);
  }

  private String previewTitle() {
    if (items.size() > 1) return items.size() + " selected";
    PreviewItem item = activeItem == null && !items.isEmpty() ? items.get(0) : activeItem;
    return item != null && item.isVideo() ? "Video preview"
        : item != null && item.isFile() ? "File preview" : "Photo preview";
  }

  private void beginEditorText() {
    if (activeItem == null || !activeItem.isImage()) return;
    imageEditor.setDrawing(false);
    setEditorMode(NativeSelectedMediaChromeView.EDITOR_TEXT);
    textInput.setText("");
    textInput.setVisibility(View.VISIBLE);
    textInput.requestFocus();
    textInput.setSelection(textInput.length());
    InputMethodManager keyboard =
        (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (keyboard != null) keyboard.showSoftInput(textInput, InputMethodManager.SHOW_IMPLICIT);
  }

  private void commitEditorText() {
    imageEditor.addText(textInput.getText().toString());
    textInput.setVisibility(View.GONE);
    textInput.clearFocus();
    InputMethodManager keyboard =
        (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (keyboard != null) keyboard.hideSoftInputFromWindow(textInput.getWindowToken(), 0);
  }

  private void beginDrawing() {
    if (activeItem == null || !activeItem.isImage()) return;
    imageEditor.setDrawing(true);
    chrome.setUndoAvailable(imageEditor.canUndoStroke());
    setEditorMode(NativeSelectedMediaChromeView.EDITOR_DRAW);
  }

  private void finishEditorMode() {
    if (editorMode == NativeSelectedMediaChromeView.EDITOR_NORMAL) return;
    if (editorMode == NativeSelectedMediaChromeView.EDITOR_TEXT) commitEditorText();
    imageEditor.setDrawing(false);
    setEditorMode(NativeSelectedMediaChromeView.EDITOR_NORMAL);
  }

  private void setEditorMode(int mode) {
    editorMode = mode;
    chrome.setEditorMode(mode);
    chrome.setUndoAvailable(mode == NativeSelectedMediaChromeView.EDITOR_DRAW
        && imageEditor.canUndoStroke());
    boolean normal = mode == NativeSelectedMediaChromeView.EDITOR_NORMAL;
    boolean text = mode == NativeSelectedMediaChromeView.EDITOR_TEXT;
    editorScrim.setVisibility(text ? View.VISIBLE : View.GONE);
    if (!text) textInput.setVisibility(View.GONE);
    selectionStrip.setVisibility(normal && items.size() > 1 ? View.VISIBLE : View.GONE);
    captionBar.setVisibility(normal ? View.VISIBLE : View.GONE);
  }

  private void downloadActiveEdit() {
    downloadActiveEdit(null);
  }

  private void downloadActiveEdit(Runnable after) {
    if (activeItem != null && activeItem.isVideo()) {
      downloadVideoCopy(activeItem, after);
      return;
    }
    if (activeItem == null || !activeItem.isImage()) {
      if (after != null) after.run();
      return;
    }
    Bitmap edited = imageEditor.export(activeItem.uri.toString());
    if (edited == null) {
      if (after != null) after.run();
      return;
    }
    thumbnailExecutor.execute(() -> {
      Uri destination = null;
      boolean saved = false;
      try {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,
            "PingGo_edit_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          values.put(MediaStore.MediaColumns.RELATIVE_PATH,
              Environment.DIRECTORY_PICTURES + "/PingGo");
          values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }
        destination = getContext().getContentResolver().insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (destination == null) throw new IOException("Unable to create gallery image");
        try (java.io.OutputStream output =
                 getContext().getContentResolver().openOutputStream(destination)) {
          if (output == null || !edited.compress(Bitmap.CompressFormat.JPEG, 94, output)) {
            throw new IOException("Unable to write gallery image");
          }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          ContentValues ready = new ContentValues();
          ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
          getContext().getContentResolver().update(destination, ready, null, null);
        }
        saved = true;
      } catch (IOException | RuntimeException error) {
        if (destination != null) {
          try { getContext().getContentResolver().delete(destination, null, null); }
          catch (RuntimeException ignored) {}
        }
      } finally {
        edited.recycle();
      }
      boolean completed = saved;
      post(() -> {
        Toast.makeText(getContext(), completed
            ? "Edited image saved to gallery."
            : "Unable to save edited image.", Toast.LENGTH_SHORT).show();
        if (after != null) after.run();
      });
    });
  }

  private void downloadVideoCopy(PreviewItem item, Runnable after) {
    thumbnailExecutor.execute(() -> {
      Uri destination = null;
      boolean saved = false;
      try {
        Uri source = renderEditedVideo(item);
        if (source == null) throw new IOException("Unable to prepare edited video");
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,
            "PingGo_" + System.currentTimeMillis() + "_" + item.name);
        String mime = getContext().getContentResolver().getType(item.uri);
        values.put(MediaStore.MediaColumns.MIME_TYPE,
            mime == null || !mime.startsWith("video/") ? "video/mp4" : mime);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          values.put(MediaStore.MediaColumns.RELATIVE_PATH,
              Environment.DIRECTORY_MOVIES + "/PingGo");
          values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }
        destination = getContext().getContentResolver().insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (destination == null) throw new IOException("Unable to create gallery video");
        try (InputStream input = getContext().getContentResolver().openInputStream(source);
             OutputStream output = getContext().getContentResolver().openOutputStream(destination)) {
          if (input == null || output == null) throw new IOException("Unable to open video");
          byte[] buffer = new byte[64 * 1024];
          int read;
          while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          ContentValues ready = new ContentValues();
          ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
          getContext().getContentResolver().update(destination, ready, null, null);
        }
        saved = true;
      } catch (IOException | RuntimeException error) {
        if (destination != null) {
          try { getContext().getContentResolver().delete(destination, null, null); }
          catch (RuntimeException ignored) {}
        }
      }
      boolean completed = saved;
      post(() -> {
        Toast.makeText(getContext(), completed
            ? "Video saved to gallery."
            : "Unable to save video.", Toast.LENGTH_SHORT).show();
        if (after != null) after.run();
      });
    });
  }

  private void startVideo() {
    SurfaceTexture texture = videoPreview.getSurfaceTexture();
    if (pendingVideoUri == null || texture == null || videoPlayer != null) return;
    MediaPlayer player = new MediaPlayer();
    Surface surface = new Surface(texture);
    try {
      player.setDataSource(getContext(), pendingVideoUri);
      player.setSurface(surface);
      player.setLooping(true);
      player.setOnPreparedListener(prepared -> {
        transformVideo(prepared.getVideoWidth(), prepared.getVideoHeight());
        VideoEditState edit = activeItem != null && activeItem.isVideo()
            ? videoEdit(activeItem) : new VideoEditState();
        if (edit.durationMs <= 0L) edit.durationMs = prepared.getDuration();
        float volume = edit.audioEnabled ? 1f : 0f;
        prepared.setVolume(volume, volume);
        prepared.seekTo((int) Math.min(Integer.MAX_VALUE,
            Math.round(edit.durationMs * edit.startFraction)));
        prepared.start();
        removeCallbacks(enforceVideoTrim);
        post(enforceVideoTrim);
      });
      player.setOnErrorListener((failed, what, extra) -> {
        if (failed == videoPlayer) releaseVideo();
        Toast.makeText(getContext(), "Unable to preview this video.", Toast.LENGTH_SHORT).show();
        return true;
      });
      videoPlayer = player;
      player.prepareAsync();
    } catch (IOException | RuntimeException error) {
      try { player.release(); } catch (RuntimeException ignored) { }
      videoPlayer = null;
      Toast.makeText(getContext(), "Unable to preview this video.", Toast.LENGTH_SHORT).show();
    } finally {
      surface.release();
    }
  }

  private void transformVideo(int videoWidth, int videoHeight) {
    int width = videoPreview.getWidth();
    int height = videoPreview.getHeight();
    if (videoWidth <= 0 || videoHeight <= 0 || width <= 0 || height <= 0) return;
    float scale = Math.min(width / (float) videoWidth, height / (float) videoHeight);
    Matrix matrix = new Matrix();
    matrix.setScale(videoWidth * scale / width, videoHeight * scale / height,
        width / 2f, height / 2f);
    videoPreview.setTransform(matrix);
  }

  private void removeActiveOrRetake() {
    if (captured) {
      items.clear();
      returnSelection();
      return;
    }
    PreviewItem removed = activeItem == null ? items.get(0) : activeItem;
    items.remove(removed);
    if (items.isEmpty()) {
      returnSelection();
      return;
    }
    rebuildStrip();
    refreshPreview(items.get(0));
  }

  private void deselectAll() {
    items.clear();
    returnSelection();
  }

  private void sendSelection() {
    if (textInput.getVisibility() == View.VISIBLE) commitEditorText();
    String caption = captionInput.getText().toString().trim();
    thumbnailExecutor.execute(() -> {
      Selection selection = editedSelection();
      post(() -> listener.onSend(selection.uris, selection.types, caption));
    });
  }

  private void returnSelection() {
    Selection selection = selection();
    listener.onCancel(selection.uris, selection.types);
  }

  private Selection selection() {
    ArrayList<Uri> uris = new ArrayList<>();
    ArrayList<String> types = new ArrayList<>();
    for (PreviewItem item : items) {
      uris.add(item.uri);
      types.add(item.type);
    }
    return new Selection(uris, types);
  }

  private Selection editedSelection() {
    ArrayList<Uri> uris = new ArrayList<>();
    ArrayList<String> types = new ArrayList<>();
    for (PreviewItem item : items) {
      Uri uri = item.uri;
      if (item.isImage() && imageEditor.hasEdits(item.uri.toString())) {
        Bitmap edited = imageEditor.export(item.uri.toString());
        Uri output = edited == null ? null : writeEditedImage(edited);
        if (edited != null) edited.recycle();
        if (output != null) uri = output;
      } else if (item.isVideo() && isVideoEdited(item)) {
        Uri output = renderEditedVideo(item);
        if (output != null) uri = output;
      }
      uris.add(uri);
      types.add(item.type);
    }
    return new Selection(uris, types);
  }

  private boolean isVideoEdited(PreviewItem item) {
    VideoEditState edit = videoEdits.get(item.uri.toString());
    return edit != null && (!edit.audioEnabled || edit.startFraction > 0.0001f
        || edit.endFraction < 0.9999f);
  }

  private Uri renderEditedVideo(PreviewItem item) {
    VideoEditState edit = videoEdit(item);
    if (!isVideoEdited(item)) return item.uri;
    long durationMs = edit.durationMs > 0L ? edit.durationMs : readVideoDuration(item.uri);
    if (durationMs <= 0L) return null;
    long requestedStartUs = Math.round(durationMs * edit.startFraction * 1000d);
    long endUs = Math.round(durationMs * edit.endFraction * 1000d);
    File directory = new File(getContext().getCacheDir(), "selected_media_edits");
    if (!directory.exists() && !directory.mkdirs()) return null;
    File output = new File(directory, "video_edit_" + UUID.randomUUID() + ".mp4");
    MediaExtractor extractor = new MediaExtractor();
    MediaMuxer muxer = null;
    boolean muxerStarted = false;
    try {
      long actualStartUs = previousVideoSync(item.uri, requestedStartUs);
      extractor.setDataSource(getContext(), item.uri, null);
      Map<Integer, Integer> trackMap = new HashMap<>();
      int bufferSize = 1024 * 1024;
      for (int track = 0; track < extractor.getTrackCount(); track++) {
        MediaFormat format = extractor.getTrackFormat(track);
        String mime = format.getString(MediaFormat.KEY_MIME);
        boolean video = mime != null && mime.startsWith("video/");
        boolean audio = mime != null && mime.startsWith("audio/");
        if (!video && !(audio && edit.audioEnabled)) continue;
        trackMap.put(track, trackMap.size());
        extractor.selectTrack(track);
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
          bufferSize = Math.max(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
        }
      }
      if (trackMap.isEmpty()) throw new IOException("No video track");
      muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      Map<Integer, Integer> muxerTracks = new HashMap<>();
      for (Map.Entry<Integer, Integer> ignored : trackMap.entrySet()) {
        int sourceTrack = ignored.getKey();
        muxerTracks.put(sourceTrack, muxer.addTrack(extractor.getTrackFormat(sourceTrack)));
      }
      int rotation = readVideoRotation(item.uri);
      if (rotation != 0) muxer.setOrientationHint(rotation);
      muxer.start();
      muxerStarted = true;
      extractor.seekTo(actualStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
      ByteBuffer buffer = ByteBuffer.allocateDirect(Math.min(bufferSize, 16 * 1024 * 1024));
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      while (true) {
        long sampleTime = extractor.getSampleTime();
        if (sampleTime < 0L || sampleTime > endUs) break;
        int sourceTrack = extractor.getSampleTrackIndex();
        Integer destinationTrack = muxerTracks.get(sourceTrack);
        if (destinationTrack != null && sampleTime >= actualStartUs) {
          buffer.clear();
          int size = extractor.readSampleData(buffer, 0);
          if (size < 0) break;
          info.set(0, size, Math.max(0L, sampleTime - actualStartUs),
              extractor.getSampleFlags());
          muxer.writeSampleData(destinationTrack, buffer, info);
        }
        if (!extractor.advance()) break;
      }
      muxer.stop();
      muxerStarted = false;
      return FileProvider.getUriForFile(
          getContext(), getContext().getPackageName() + ".files", output);
    } catch (IOException | RuntimeException error) {
      if (output.exists()) output.delete();
      return null;
    } finally {
      extractor.release();
      if (muxer != null) {
        if (muxerStarted) {
          try { muxer.stop(); } catch (RuntimeException ignored) {}
        }
        try { muxer.release(); } catch (RuntimeException ignored) {}
      }
    }
  }

  private long previousVideoSync(Uri uri, long requestedUs) throws IOException {
    MediaExtractor probe = new MediaExtractor();
    try {
      probe.setDataSource(getContext(), uri, null);
      for (int track = 0; track < probe.getTrackCount(); track++) {
        String mime = probe.getTrackFormat(track).getString(MediaFormat.KEY_MIME);
        if (mime != null && mime.startsWith("video/")) {
          probe.selectTrack(track);
          probe.seekTo(requestedUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
          return Math.max(0L, probe.getSampleTime());
        }
      }
      return requestedUs;
    } finally {
      probe.release();
    }
  }

  private long readVideoDuration(Uri uri) {
    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    try {
      retriever.setDataSource(getContext(), uri);
      return parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
    } catch (RuntimeException error) {
      return 0L;
    } finally {
      try { retriever.release(); } catch (IOException | RuntimeException ignored) {}
    }
  }

  private int readVideoRotation(Uri uri) {
    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    try {
      retriever.setDataSource(getContext(), uri);
      return (int) parseLong(
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
    } catch (RuntimeException error) {
      return 0;
    } finally {
      try { retriever.release(); } catch (IOException | RuntimeException ignored) {}
    }
  }

  private Uri writeEditedImage(Bitmap bitmap) {
    File directory = new File(getContext().getCacheDir(), "selected_media_edits");
    if (!directory.exists() && !directory.mkdirs()) return null;
    File output = new File(directory, "edit_" + UUID.randomUUID() + ".jpg");
    try (FileOutputStream stream = new FileOutputStream(output)) {
      if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)) return null;
      return FileProvider.getUriForFile(
          getContext(), getContext().getPackageName() + ".files", output);
    } catch (IOException | RuntimeException error) {
      if (output.exists()) output.delete();
      return null;
    }
  }

  public void addItems(List<Uri> uris, List<String> types) {
    int previousSize = items.size();
    readItems(uris, types);
    if (items.size() == previousSize) return;
    rebuildStrip();
    if (activeItem == null) refreshPreview(items.get(0));
  }

  public void onBackPressed() {
    if (editPrompt != null) {
      removeEditPrompt();
      return;
    }
    if (editorMode != NativeSelectedMediaChromeView.EDITOR_NORMAL) {
      finishEditorMode();
      return;
    }
    if (imageEditor.hasAnyEdits()) {
      showUnsavedEditPrompt();
      return;
    }
    returnSelection();
  }

  private void showUnsavedEditPrompt() {
    if (editPrompt != null) return;
    editPrompt = NativePromptDialogView.actions(getContext(),
        Arrays.asList("Save edited photo", "Discard changes", "Cancel"), index -> {
          removeEditPrompt();
          if (index == 0) {
            downloadActiveEdit(this::returnSelection);
          } else if (index == 1) {
            returnSelection();
          }
        }, this::removeEditPrompt);
    root.addView(editPrompt, match());
  }

  private void removeEditPrompt() {
    NativePromptDialogView current = editPrompt;
    editPrompt = null;
    if (current == null) return;
    if (current.getParent() instanceof ViewGroup) {
      ((ViewGroup) current.getParent()).removeView(current);
    }
    current.release();
  }

  public void onHostResume() {
    if (pendingVideoUri != null) startVideo();
  }

  public void onHostPause() {
    releaseVideo();
  }

  @Override public void release() {
    pendingVideoUri = null;
    releaseVideo();
    thumbnailExecutor.shutdownNow();
    videoFrameGeneration++;
    videoFrameExecutor.shutdownNow();
    if (filePreview != null) filePreview.release();
    if (videoInfo != null) videoInfo.release();
    if (chrome != null) chrome.release();
    if (imageEditor != null) imageEditor.release();
    removeEditPrompt();
    super.release();
  }

  private void releaseVideo() {
    removeCallbacks(enforceVideoTrim);
    MediaPlayer player = videoPlayer;
    videoPlayer = null;
    if (player == null) return;
    try { player.stop(); } catch (RuntimeException ignored) { }
    try { player.release(); } catch (RuntimeException ignored) { }
  }

  private FrameLayout.LayoutParams match() {
    return new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
  }

  private int px(float value) {
    return Math.round(figmaConfig.toRuntime(value, Math.max(1, getResources().getDisplayMetrics().widthPixels)));
  }

  private static String normalizedType(String type) {
    if ("Video".equalsIgnoreCase(type)) return "Video";
    if ("File".equalsIgnoreCase(type)) return "File";
    return "Image";
  }

  private String selectedFileName(Uri uri) {
    try (Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (column >= 0) {
          String value = cursor.getString(column);
          if (value != null && !value.trim().isEmpty()) return value.trim();
        }
      }
    } catch (RuntimeException ignored) { }
    String segment = uri.getLastPathSegment();
    return segment == null || segment.trim().isEmpty() ? "File" : segment;
  }

  private long selectedFileSize(Uri uri) {
    try (Cursor cursor = getContext().getContentResolver().query(
        uri, new String[] {OpenableColumns.SIZE}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int column = cursor.getColumnIndex(OpenableColumns.SIZE);
        if (column >= 0 && !cursor.isNull(column)) return Math.max(0L, cursor.getLong(column));
      }
    } catch (RuntimeException ignored) {}
    return 0L;
  }

  private static long parseLong(String value) {
    try { return value == null ? 0L : Long.parseLong(value); }
    catch (NumberFormatException ignored) { return 0L; }
  }

  private static String formatDuration(long milliseconds) {
    long seconds = Math.max(0L, milliseconds / 1000L);
    return String.format(java.util.Locale.US, "%d:%02d", seconds / 60L, seconds % 60L);
  }

  private static String formatSize(long bytes) {
    if (bytes < 1024L) return bytes + " B";
    if (bytes < 1024L * 1024L) return Math.round(bytes / 1024f) + " kB";
    return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f));
  }

  private static final class PreviewItem {
    final Uri uri;
    final String type;
    final String name;

    PreviewItem(Uri uri, String type, String name) {
      this.uri = uri;
      this.type = type;
      this.name = name;
    }

    boolean isImage() { return "Image".equals(type); }
    boolean isVideo() { return "Video".equals(type); }
    boolean isFile() { return "File".equals(type); }
  }

  private static final class VideoEditState {
    long durationMs;
    long originalSize;
    float startFraction;
    float endFraction = 1f;
    boolean audioEnabled = true;
  }

  private GradientDrawable rounded(int color, float radius) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(color);
    drawable.setCornerRadius(radius);
    return drawable;
  }

  private static final class Selection {
    final ArrayList<Uri> uris;
    final ArrayList<String> types;
    Selection(ArrayList<Uri> uris, ArrayList<String> types) {
      this.uris = uris;
      this.types = types;
    }
  }

  private final class EditorTextInput extends EditText {
    private Runnable keyboardDismissListener;

    EditorTextInput(Context context) { super(context); }

    void setKeyboardDismissListener(Runnable listener) {
      keyboardDismissListener = listener;
    }

    @Override public boolean onKeyPreIme(int keyCode, KeyEvent event) {
      if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP
          && keyboardDismissListener != null) {
        keyboardDismissListener.run();
        return true;
      }
      return super.onKeyPreIme(keyCode, event);
    }
  }

  public interface Listener {
    void onSend(ArrayList<Uri> uris, ArrayList<String> types);
    default void onSend(ArrayList<Uri> uris, ArrayList<String> types, String caption) {
      onSend(uris, types);
    }
    void onCancel(ArrayList<Uri> uris, ArrayList<String> types);
    default void onAddMedia() {}
    default void onCameraRequested(ArrayList<Uri> uris, ArrayList<String> types) {
      onAddMedia();
    }
  }
}
