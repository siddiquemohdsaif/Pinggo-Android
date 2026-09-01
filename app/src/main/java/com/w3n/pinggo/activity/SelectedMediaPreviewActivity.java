package com.w3n.pinggo.activity;

import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.w3n.pinggo.R;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Previews captured or gallery media and owns selection changes before final send. */
public final class SelectedMediaPreviewActivity extends AppCompatActivity {
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
  public static final String EXTRA_URIS = "com.w3n.pinggo.PREVIEW_URIS";
  public static final String EXTRA_TYPES = "com.w3n.pinggo.PREVIEW_TYPES";
  public static final String EXTRA_CAPTURED = "com.w3n.pinggo.PREVIEW_CAPTURED";
  private static final int ACCENT = 0xFF019CC4;

  private final List<PreviewItem> items = new ArrayList<>();
  private final ExecutorService thumbnailExecutor = Executors.newSingleThreadExecutor();
  private FrameLayout root;
  private ImageView imagePreview;
  private TextureView videoPreview;
  private LinearLayout filePreview;
  private TextView fileName;
  private HorizontalScrollView selectionStrip;
  private LinearLayout selectionItems;
  private LinearLayout actions;
  private Button backAction;
  private Button deselectAllAction;
  private TextView title;
  private PreviewItem activeItem;
  private MediaPlayer videoPlayer;
  private Uri pendingVideoUri;
  private boolean captured;

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(Color.BLACK);
    getWindow().setNavigationBarColor(Color.BLACK);
    captured = getIntent().getBooleanExtra(EXTRA_CAPTURED, false);
    readItems(getIntent());
    if (items.isEmpty()) {
      finish();
      return;
    }
    buildUi();
    refreshPreview(items.get(0));
  }

  @SuppressWarnings("deprecation")
  private void readItems(Intent intent) {
    ArrayList<Uri> uris = intent.getParcelableArrayListExtra(EXTRA_URIS);
    ArrayList<String> types = intent.getStringArrayListExtra(EXTRA_TYPES);
    if (uris == null) return;
    for (int index = 0; index < uris.size(); index++) {
      Uri uri = uris.get(index);
      if (uri == null) continue;
      String type = types != null && index < types.size() ? types.get(index) : "Image";
      items.add(new PreviewItem(uri, normalizedType(type), selectedFileName(uri)));
    }
  }

  private void buildUi() {
    root = new FrameLayout(this);
    root.setBackgroundColor(Color.BLACK);
    setContentView(root);

    imagePreview = new ImageView(this);
    imagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
    imagePreview.setVisibility(View.GONE);
    root.addView(imagePreview, match());

    videoPreview = new TextureView(this);
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

    filePreview = new LinearLayout(this);
    filePreview.setOrientation(LinearLayout.VERTICAL);
    filePreview.setGravity(Gravity.CENTER);
    filePreview.setPadding(px(88f), px(330f), px(88f), px(522.5f));
    filePreview.setVisibility(View.GONE);
    ImageView document = new ImageView(this);
    document.setImageResource(R.drawable.chat_document);
    document.setScaleType(ImageView.ScaleType.FIT_CENTER);
    filePreview.addView(document, new LinearLayout.LayoutParams(px(250.25f), px(308f)));
    fileName = new TextView(this);
    fileName.setTextColor(Color.WHITE);
    fileName.setTextSize(20);
    fileName.setGravity(Gravity.CENTER);
    fileName.setMaxLines(4);
    LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    nameParams.topMargin = px(49.5f);
    filePreview.addView(fileName, nameParams);
    root.addView(filePreview, match());

    LinearLayout topBar = new LinearLayout(this);
    topBar.setGravity(Gravity.CENTER_VERTICAL);
    topBar.setPadding(px(33f), px(22f), px(33f), px(22f));
    topBar.setBackgroundColor(0x66000000);
    Button back = textButton("‹", 34, Color.WHITE);
    back.setOnClickListener(view -> returnSelection());
    topBar.addView(back, new LinearLayout.LayoutParams(px(154f), px(154f)));
    title = new TextView(this);
    title.setTextColor(Color.WHITE);
    title.setTextSize(20);
    title.setGravity(Gravity.CENTER_VERTICAL);
    topBar.addView(title, new LinearLayout.LayoutParams(0, px(154f), 1f));
    root.addView(topBar, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, px(198f), Gravity.TOP));
    ViewCompat.setOnApplyWindowInsetsListener(topBar, (view, insets) -> {
      Insets status = insets.getInsets(WindowInsetsCompat.Type.statusBars());
      ViewGroup.LayoutParams params = view.getLayoutParams();
      params.height = px(198f) + status.top;
      view.setLayoutParams(params);
      view.setPadding(px(33f), px(22f) + status.top, px(33f), px(22f));
      return insets;
    });
    ViewCompat.requestApplyInsets(topBar);

    actions = new LinearLayout(this);
    actions.setGravity(Gravity.CENTER);
    actions.setPadding(px(55f), px(49.5f), px(55f), px(49.5f));
    actions.setBackgroundColor(0x88000000);
    backAction = actionButton(captured ? "Retake" : "Deselect", 0xFF3B4654);
    backAction.setOnClickListener(view -> removeActiveOrRetake());
    deselectAllAction = actionButton("Deselect all", 0xFF3B4654);
    deselectAllAction.setOnClickListener(view -> deselectAll());
    Button send = actionButton("Send", ACCENT);
    send.setOnClickListener(view -> sendSelection());
    LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, px(159.5f), 1f);
    actionParams.setMarginStart(px(22f));
    actionParams.setMarginEnd(px(22f));
    actions.addView(backAction, actionParams);
    actions.addView(deselectAllAction, actionParams);
    actions.addView(send, actionParams);
    root.addView(actions, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, px(258.5f), Gravity.BOTTOM));

    selectionStrip = new HorizontalScrollView(this);
    selectionStrip.setHorizontalScrollBarEnabled(false);
    selectionStrip.setBackgroundColor(0x88000000);
    selectionStrip.setPadding(px(22f), px(19.25f), px(22f), px(19.25f));
    selectionItems = new LinearLayout(this);
    selectionItems.setOrientation(LinearLayout.HORIZONTAL);
    selectionItems.setGravity(Gravity.CENTER_VERTICAL);
    selectionStrip.addView(selectionItems, new HorizontalScrollView.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    FrameLayout.LayoutParams stripParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, px(236.5f), Gravity.BOTTOM);
    stripParams.bottomMargin = px(258.5f);
    root.addView(selectionStrip, stripParams);
    rebuildStrip();
  }

  private void rebuildStrip() {
    selectionItems.removeAllViews();
    selectionStrip.setVisibility(items.size() > 1 ? View.VISIBLE : View.GONE);
    deselectAllAction.setVisibility(items.size() > 1 ? View.VISIBLE : View.GONE);
    for (PreviewItem item : items) {
      ImageView thumbnail = new ImageView(this);
      thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
      thumbnail.setOnClickListener(view -> refreshPreview(item));
      selectionItems.addView(thumbnail, thumbnailParams());
      if (item.isFile()) {
        thumbnail.setImageResource(R.drawable.chat_document);
        continue;
      }
      thumbnailExecutor.execute(() -> {
        Bitmap bitmap = loadThumbnail(item.uri);
        runOnUiThread(() -> {
          if (!isFinishing() && bitmap != null) thumbnail.setImageBitmap(bitmap);
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
        return getContentResolver().loadThumbnail(uri, new Size(px(264f), px(264f)), null);
      }
    } catch (IOException | RuntimeException ignored) { }
    return null;
  }

  private void refreshPreview(PreviewItem item) {
    activeItem = item;
    pendingVideoUri = null;
    releaseVideo();
    imagePreview.setImageDrawable(null);
    imagePreview.setVisibility(View.GONE);
    videoPreview.setVisibility(View.GONE);
    filePreview.setVisibility(View.GONE);
    if (item.isVideo()) {
      pendingVideoUri = item.uri;
      videoPreview.setVisibility(View.VISIBLE);
      startVideo();
    } else if (item.isImage()) {
      imagePreview.setImageURI(item.uri);
      imagePreview.setVisibility(View.VISIBLE);
    } else {
      fileName.setText(item.name);
      filePreview.setVisibility(View.VISIBLE);
    }
    title.setText(items.size() > 1 ? items.size() + " selected"
        : item.isVideo() ? "Video preview" : item.isImage() ? "Photo preview" : "File preview");
  }

  private void startVideo() {
    SurfaceTexture texture = videoPreview.getSurfaceTexture();
    if (pendingVideoUri == null || texture == null || videoPlayer != null) return;
    MediaPlayer player = new MediaPlayer();
    Surface surface = new Surface(texture);
    try {
      player.setDataSource(this, pendingVideoUri);
      player.setSurface(surface);
      player.setLooping(true);
      player.setOnPreparedListener(prepared -> {
        transformVideo(prepared.getVideoWidth(), prepared.getVideoHeight());
        prepared.start();
      });
      player.setOnErrorListener((failed, what, extra) -> {
        if (failed == videoPlayer) releaseVideo();
        Toast.makeText(this, "Unable to preview this video.", Toast.LENGTH_SHORT).show();
        return true;
      });
      videoPlayer = player;
      player.prepareAsync();
    } catch (IOException | RuntimeException error) {
      try { player.release(); } catch (RuntimeException ignored) { }
      videoPlayer = null;
      Toast.makeText(this, "Unable to preview this video.", Toast.LENGTH_SHORT).show();
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
    setResult(RESULT_OK, selectionIntent());
    finish();
  }

  private void returnSelection() {
    setResult(RESULT_CANCELED, selectionIntent());
    finish();
  }

  private Intent selectionIntent() {
    ArrayList<Uri> uris = new ArrayList<>();
    ArrayList<String> types = new ArrayList<>();
    for (PreviewItem item : items) {
      uris.add(item.uri);
      types.add(item.type);
    }
    Intent result = new Intent();
    result.putParcelableArrayListExtra(EXTRA_URIS, uris);
    result.putStringArrayListExtra(EXTRA_TYPES, types);
    if (!uris.isEmpty()) {
      result.setData(uris.get(0));
      ClipData clip = ClipData.newUri(getContentResolver(), "PingGo media", uris.get(0));
      for (int index = 1; index < uris.size(); index++) {
        clip.addItem(new ClipData.Item(uris.get(index)));
      }
      result.setClipData(clip);
      result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      result.putExtra(CameraCaptureActivity.EXTRA_MEDIA_TYPE, types.get(0));
      result.putStringArrayListExtra(CameraCaptureActivity.EXTRA_MEDIA_TYPES, types);
    }
    return result;
  }

  @Override public void onBackPressed() { returnSelection(); }

  @Override protected void onResume() {
    super.onResume();
    if (pendingVideoUri != null) startVideo();
  }

  @Override protected void onPause() {
    releaseVideo();
    super.onPause();
  }

  @Override protected void onDestroy() {
    pendingVideoUri = null;
    releaseVideo();
    thumbnailExecutor.shutdownNow();
    super.onDestroy();
  }

  private void releaseVideo() {
    MediaPlayer player = videoPlayer;
    videoPlayer = null;
    if (player == null) return;
    try { player.stop(); } catch (RuntimeException ignored) { }
    try { player.release(); } catch (RuntimeException ignored) { }
  }

  private Button actionButton(String label, int color) {
    Button button = textButton(label, 17, Color.WHITE);
    button.setBackground(rounded(color, px(49.5f)));
    return button;
  }

  private Button textButton(String label, float size, int color) {
    Button button = new Button(this);
    button.setAllCaps(false);
    button.setText(label);
    button.setTextSize(size);
    button.setTextColor(color);
    button.setGravity(Gravity.CENTER);
    button.setPadding(0, 0, 0, 0);
    return button;
  }

  private GradientDrawable rounded(int color, float radius) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(color);
    drawable.setCornerRadius(radius);
    return drawable;
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
    try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
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
}
