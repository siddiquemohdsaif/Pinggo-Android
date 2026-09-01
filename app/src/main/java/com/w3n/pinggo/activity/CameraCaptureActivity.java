package com.w3n.pinggo.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.database.Cursor;
import android.content.ContentUris;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns live photo/video capture and the media-only gallery selection surface. */
@SuppressWarnings("deprecation")
public final class CameraCaptureActivity extends AppCompatActivity {
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
  public static final String EXTRA_MEDIA_TYPE = "com.w3n.pinggo.CAPTURE_MEDIA_TYPE";
  public static final String EXTRA_MEDIA_TYPES = "com.w3n.pinggo.CAPTURE_MEDIA_TYPES";
  private static final int ACCENT = 0xFF019CC4;
  private final ActivityResultLauncher<String[]> permissions =
      registerForActivityResult(
          new ActivityResultContracts.RequestMultiplePermissions(), this::onPermissionsResult);
  private FrameLayout root;
  private SurfaceView cameraPreview;
  private LinearLayout modeControls;
  private LinearLayout galleryItemsView;
  private Button galleryConfirm;
  private Button photoMode;
  private Button videoMode;
  private Button capture;
  private TextView title;
  private Camera camera;
  private MediaRecorder recorder;
  private File capturedFile;
  private final List<GalleryItem> galleryItems = new ArrayList<>();
  private final LinkedHashMap<String, GalleryItem> selectedGallery = new LinkedHashMap<>();
  private final ExecutorService galleryExecutor = Executors.newSingleThreadExecutor();
  private boolean galleryLoaded;
  private boolean surfaceReady;
  private boolean videoModeSelected;
  private boolean recording;
  private boolean showingResult;
  private int cameraId = -1;
  private final ActivityResultLauncher<Intent> selectedMediaPreview =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(), result -> {
            Intent data = result.getData();
            if (result.getResultCode() == RESULT_OK && data != null) {
              setResult(RESULT_OK, data);
              capturedFile = null;
              finish();
              return;
            }
            updateGallerySelection(data);
            if (capturedFile != null) capturedFile.delete();
            capturedFile = null;
            showingResult = false;
            cameraPreview.setVisibility(View.VISIBLE);
            modeControls.setVisibility(View.VISIBLE);
            title.setText(videoModeSelected ? "Video" : "Photo");
            openCameraIfReady();
          });

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(Color.BLACK);
    getWindow().setNavigationBarColor(Color.BLACK);
    buildUi();
    requestMediaPermissions();
  }

  private void buildUi() {
    root = new FrameLayout(this);
    root.setBackgroundColor(Color.BLACK);
    setContentView(root);

    cameraPreview = new SurfaceView(this);
    root.addView(cameraPreview, match());
    cameraPreview.getHolder().addCallback(new SurfaceHolder.Callback() {
      @Override public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        openCameraIfReady();
      }
      @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera != null && !recording && !showingResult) restartPreview();
      }
      @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        releaseCamera();
      }
    });

    LinearLayout topBar = new LinearLayout(this);
    topBar.setGravity(Gravity.CENTER_VERTICAL);
    topBar.setPadding(px(33f), px(22f), px(33f), px(22f));
    topBar.setBackgroundColor(0x66000000);
    Button back = textButton("‹", 34, Color.WHITE);
    back.setOnClickListener(view -> handleBack());
    topBar.addView(back, new LinearLayout.LayoutParams(px(154f), px(154f)));
    title = new TextView(this);
    title.setText("Camera");
    title.setTextColor(Color.WHITE);
    title.setTextSize(20);
    title.setGravity(Gravity.CENTER_VERTICAL);
    topBar.addView(title, new LinearLayout.LayoutParams(0, px(154f), 1f));
    FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, px(198f), Gravity.TOP);
    root.addView(topBar, topParams);
    final int topBarBaseHeight = px(198f);
    final int topBarHorizontalPadding = px(33f);
    final int topBarVerticalPadding = px(22f);
    ViewCompat.setOnApplyWindowInsetsListener(topBar, (view, windowInsets) -> {
      Insets status = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
      ViewGroup.LayoutParams params = view.getLayoutParams();
      params.height = topBarBaseHeight + status.top;
      view.setLayoutParams(params);
      view.setPadding(topBarHorizontalPadding, topBarVerticalPadding + status.top,
          topBarHorizontalPadding, topBarVerticalPadding);
      return windowInsets;
    });
    ViewCompat.requestApplyInsets(topBar);

    modeControls = new LinearLayout(this);
    modeControls.setOrientation(LinearLayout.VERTICAL);
    modeControls.setGravity(Gravity.CENTER);
    modeControls.setPadding(px(49.5f), px(22f), px(49.5f), px(60.5f));
    modeControls.setBackgroundColor(0x55000000);

    FrameLayout galleryContainer = new FrameLayout(this);
    HorizontalScrollView galleryScroll = new HorizontalScrollView(this);
    galleryScroll.setHorizontalScrollBarEnabled(false);
    galleryScroll.setClipToPadding(false);
    galleryScroll.setPadding(0, 0, px(170.5f), 0);
    galleryItemsView = new LinearLayout(this);
    galleryItemsView.setOrientation(LinearLayout.HORIZONTAL);
    galleryItemsView.setGravity(Gravity.CENTER_VERTICAL);
    galleryScroll.addView(galleryItemsView, new HorizontalScrollView.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    galleryContainer.addView(galleryScroll, match());
    galleryConfirm = textButton("✓", 28, Color.WHITE);
    galleryConfirm.setBackground(circle(ACCENT, 0, ACCENT));
    galleryConfirm.setVisibility(View.GONE);
    galleryConfirm.setOnClickListener(view -> showGalleryResult());
    FrameLayout.LayoutParams confirmParams = new FrameLayout.LayoutParams(
        px(143f), px(143f), Gravity.END | Gravity.CENTER_VERTICAL);
    confirmParams.setMarginEnd(px(11f));
    galleryContainer.addView(galleryConfirm, confirmParams);
    modeControls.addView(galleryContainer, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, px(236.5f)));

    LinearLayout modes = new LinearLayout(this);
    modes.setGravity(Gravity.CENTER);
    photoMode = modeButton("PHOTO", true);
    videoMode = modeButton("VIDEO", false);
    photoMode.setOnClickListener(view -> selectMode(false));
    videoMode.setOnClickListener(view -> selectMode(true));
    modes.addView(photoMode, new LinearLayout.LayoutParams(px(302.5f), px(115.5f)));
    modes.addView(videoMode, new LinearLayout.LayoutParams(px(302.5f), px(115.5f)));
    modeControls.addView(modes);
    capture = textButton("●", 54, Color.WHITE);
    capture.setBackground(circle(Color.WHITE, 8.25f, ACCENT));
    capture.setTextColor(ACCENT);
    capture.setOnClickListener(view -> capture());
    LinearLayout.LayoutParams captureParams = new LinearLayout.LayoutParams(px(225.5f), px(225.5f));
    captureParams.topMargin = px(22f);
    modeControls.addView(capture, captureParams);
    FrameLayout.LayoutParams modeParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, px(690.25f), Gravity.BOTTOM);
    root.addView(modeControls, modeParams);

  }

  private void requestMediaPermissions() {
    boolean cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED;
    boolean microphoneGranted = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    boolean galleryGranted = hasGalleryPermission();
    if (galleryGranted) loadGallery();
    if (cameraGranted && microphoneGranted && galleryGranted) {
      openCameraIfReady();
      return;
    }
    List<String> requested = new ArrayList<>();
    if (!cameraGranted) requested.add(Manifest.permission.CAMERA);
    if (!microphoneGranted) requested.add(Manifest.permission.RECORD_AUDIO);
    if (!galleryGranted) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        requested.add(Manifest.permission.READ_MEDIA_IMAGES);
        requested.add(Manifest.permission.READ_MEDIA_VIDEO);
      } else {
        requested.add(Manifest.permission.READ_EXTERNAL_STORAGE);
      }
    }
    permissions.launch(requested.toArray(new String[0]));
  }

  private void onPermissionsResult(Map<String, Boolean> result) {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED) {
      openCameraIfReady();
    } else {
      Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_SHORT).show();
      finish();
    }
    if (hasGalleryPermission()) loadGallery();
  }

  private boolean hasGalleryPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
              == PackageManager.PERMISSION_GRANTED
          || ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
              == PackageManager.PERMISSION_GRANTED;
    }
    return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        == PackageManager.PERMISSION_GRANTED;
  }

  private void loadGallery() {
    if (galleryLoaded) return;
    galleryLoaded = true;
    galleryExecutor.execute(() -> {
      List<GalleryItem> loaded = new ArrayList<>();
      Uri collection = MediaStore.Files.getContentUri("external");
      String[] projection = {
          MediaStore.Files.FileColumns._ID,
          MediaStore.Files.FileColumns.MEDIA_TYPE
      };
      String selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=? OR "
          + MediaStore.Files.FileColumns.MEDIA_TYPE + "=?";
      String[] arguments = {
          String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE),
          String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
      };
      String order = MediaStore.Files.FileColumns.DATE_ADDED + " DESC";
      try (Cursor cursor = getContentResolver().query(
          collection, projection, selection, arguments, order)) {
        if (cursor != null) {
          int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
          int typeColumn = cursor.getColumnIndexOrThrow(
              MediaStore.Files.FileColumns.MEDIA_TYPE);
          while (cursor.moveToNext() && loaded.size() < 60) {
            long id = cursor.getLong(idColumn);
            boolean video = cursor.getInt(typeColumn)
                == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
            Uri uri = ContentUris.withAppendedId(collection, id);
            Bitmap thumbnail = loadGalleryThumbnail(uri, video);
            if (thumbnail != null) loaded.add(new GalleryItem(uri, video, thumbnail));
          }
        }
      } catch (RuntimeException error) {
        runOnUiThread(() -> Toast.makeText(this,
            "Unable to load the gallery.", Toast.LENGTH_SHORT).show());
      }
      runOnUiThread(() -> showGalleryItems(loaded));
    });
  }

  private Bitmap loadGalleryThumbnail(Uri uri, boolean video) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return getContentResolver().loadThumbnail(uri, new Size(px(264f), px(264f)), null);
      }
      long id = ContentUris.parseId(uri);
      return video
          ? MediaStore.Video.Thumbnails.getThumbnail(getContentResolver(), id,
              MediaStore.Video.Thumbnails.MINI_KIND, null)
          : MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(), id,
              MediaStore.Images.Thumbnails.MINI_KIND, null);
    } catch (IOException | RuntimeException error) {
      return null;
    }
  }

  private void showGalleryItems(List<GalleryItem> loaded) {
    if (isFinishing() || isDestroyed()) return;
    galleryItems.clear();
    galleryItems.addAll(loaded);
    galleryItemsView.removeAllViews();
    for (GalleryItem item : galleryItems) addGalleryTile(item);
  }

  private void addGalleryTile(GalleryItem item) {
    FrameLayout tile = new FrameLayout(this);
    ImageView thumbnail = new ImageView(this);
    thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
    thumbnail.setImageBitmap(item.thumbnail);
    tile.addView(thumbnail, match());
    if (item.video) {
      TextView video = new TextView(this);
      video.setText("▶");
      video.setTextColor(Color.WHITE);
      video.setTextSize(18);
      video.setGravity(Gravity.CENTER);
      video.setBackgroundColor(0x55000000);
      tile.addView(video, match());
    }
    TextView check = new TextView(this);
    check.setText("✓");
    check.setTextColor(Color.WHITE);
    check.setTextSize(15);
    check.setGravity(Gravity.CENTER);
    check.setBackground(circle(ACCENT, 0, ACCENT));
    check.setVisibility(View.GONE);
    FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(
        px(68.75f), px(68.75f), Gravity.TOP | Gravity.END);
    checkParams.setMargins(px(8.25f), px(8.25f), px(8.25f), px(8.25f));
    tile.addView(check, checkParams);
    tile.setOnClickListener(view -> {
      String key = item.uri.toString();
      boolean selected;
      if (selectedGallery.containsKey(key)) {
        selectedGallery.remove(key);
        selected = false;
      } else {
        selectedGallery.put(key, item);
        selected = true;
      }
      check.setVisibility(selected ? View.VISIBLE : View.GONE);
      tile.setBackground(selected ? rounded(ACCENT, px(22f)) : null);
      int padding = selected ? px(8.25f) : 0;
      tile.setPadding(padding, padding, padding, padding);
      galleryConfirm.setVisibility(selectedGallery.isEmpty() ? View.GONE : View.VISIBLE);
      galleryConfirm.setText(selectedGallery.size() > 1
          ? "✓" + selectedGallery.size() : "✓");
    });
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(px(209f), px(209f));
    params.setMarginEnd(px(19.25f));
    galleryItemsView.addView(tile, params);
  }

  private void showGalleryResult() {
    if (selectedGallery.isEmpty()) return;
    openSelectedPreview(new ArrayList<>(selectedGallery.values()), false);
  }

  private void openSelectedPreview(List<GalleryItem> selected, boolean capturedMedia) {
    if (selected == null || selected.isEmpty()) return;
    showingResult = true;
    releaseCamera();
    ArrayList<Uri> uris = new ArrayList<>();
    ArrayList<String> types = new ArrayList<>();
    for (GalleryItem item : selected) {
      Uri uri = item.uri;
      if (capturedMedia && capturedFile != null && "file".equals(uri.getScheme())) {
        uri = FileProvider.getUriForFile(
            this, getPackageName() + ".files", capturedFile);
      }
      uris.add(uri);
      types.add(item.video ? "Video" : "Image");
    }
    Intent preview = new Intent(this, SelectedMediaPreviewActivity.class);
    preview.putParcelableArrayListExtra(SelectedMediaPreviewActivity.EXTRA_URIS, uris);
    preview.putStringArrayListExtra(SelectedMediaPreviewActivity.EXTRA_TYPES, types);
    preview.putExtra(SelectedMediaPreviewActivity.EXTRA_CAPTURED, capturedMedia);
    preview.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    selectedMediaPreview.launch(preview);
  }

  @SuppressWarnings("deprecation")
  private void updateGallerySelection(Intent data) {
    if (data == null) return;
    ArrayList<Uri> remaining = data.getParcelableArrayListExtra(
        SelectedMediaPreviewActivity.EXTRA_URIS);
    if (remaining == null) return;
    java.util.Set<String> keys = new java.util.HashSet<>();
    for (Uri uri : remaining) if (uri != null) keys.add(uri.toString());
    selectedGallery.entrySet().removeIf(entry -> !keys.contains(entry.getKey()));
    refreshGallerySelectionViews();
  }

  private void refreshGallerySelectionViews() {
    galleryItemsView.removeAllViews();
    for (GalleryItem item : galleryItems) addGalleryTile(item);
    galleryConfirm.setVisibility(selectedGallery.isEmpty() ? View.GONE : View.VISIBLE);
    galleryConfirm.setText(selectedGallery.size() > 1
        ? "✓" + selectedGallery.size() : "✓");
  }

  private void openCameraIfReady() {
    if (!surfaceReady || showingResult || camera != null
        || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) return;
    try {
      cameraId = findBackCamera();
      camera = Camera.open(cameraId);
      camera.setDisplayOrientation(displayOrientation(cameraId));
      Camera.Parameters parameters = camera.getParameters();
      if (parameters.getSupportedFocusModes() != null
          && parameters.getSupportedFocusModes().contains(
          Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
      }
      camera.setParameters(parameters);
      camera.setPreviewDisplay(cameraPreview.getHolder());
      camera.startPreview();
    } catch (IOException | RuntimeException error) {
      releaseCamera();
      Toast.makeText(this, "Unable to open the camera.", Toast.LENGTH_SHORT).show();
    }
  }

  private void restartPreview() {
    releaseCamera();
    openCameraIfReady();
  }

  private void selectMode(boolean video) {
    if (recording || showingResult) return;
    videoModeSelected = video;
    photoMode.setTextColor(video ? 0xFFABB4C0 : Color.WHITE);
    videoMode.setTextColor(video ? Color.WHITE : 0xFFABB4C0);
    title.setText(video ? "Video" : "Photo");
  }

  private void capture() {
    if (camera == null || showingResult) return;
    if (!videoModeSelected) takePhoto();
    else if (recording) stopVideoRecording(true);
    else startVideoRecording();
  }

  private void takePhoto() {
    File output = newOutputFile(false);
    if (output == null) return;
    Camera.Parameters parameters = camera.getParameters();
    parameters.setRotation(captureOrientation(cameraId));
    camera.setParameters(parameters);
    camera.takePicture(null, null, (data, source) -> {
      try (FileOutputStream stream = new FileOutputStream(output)) {
        stream.write(data);
        capturedFile = output;
        showPhotoResult();
      } catch (IOException error) {
        output.delete();
        Toast.makeText(this, "Unable to save the photo.", Toast.LENGTH_SHORT).show();
        restartPreview();
      }
    });
  }

  private void startVideoRecording() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
      permissions.launch(new String[] {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
      return;
    }
    File output = newOutputFile(true);
    if (output == null) return;
    MediaRecorder next = new MediaRecorder();
    try {
      camera.unlock();
      next.setCamera(camera);
      next.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
      next.setVideoSource(MediaRecorder.VideoSource.CAMERA);
      CamcorderProfile profile = CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)
          ? CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_720P)
          : CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_HIGH);
      next.setProfile(profile);
      next.setOutputFile(output.getAbsolutePath());
      next.setPreviewDisplay(cameraPreview.getHolder().getSurface());
      next.setOrientationHint(captureOrientation(cameraId));
      next.setMaxDuration(5 * 60 * 1000);
      next.setMaxFileSize(24L * 1024L * 1024L);
      next.setOnInfoListener((mediaRecorder, what, extra) -> {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED
            || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
          stopVideoRecording(true);
        }
      });
      next.prepare();
      next.start();
      recorder = next;
      capturedFile = output;
      recording = true;
      capture.setText("■");
      capture.setTextColor(0xFFE53935);
      title.setText("Recording video");
      photoMode.setEnabled(false);
      videoMode.setEnabled(false);
    } catch (IOException | RuntimeException error) {
      try { next.release(); } catch (RuntimeException ignored) {}
      output.delete();
      capturedFile = null;
      recording = false;
      releaseCamera();
      openCameraIfReady();
      Toast.makeText(this, "Unable to start video recording.", Toast.LENGTH_SHORT).show();
    }
  }

  private void stopVideoRecording(boolean showResult) {
    MediaRecorder active = recorder;
    recorder = null;
    recording = false;
    boolean saved = false;
    if (active != null) {
      try {
        active.stop();
        saved = true;
      } catch (RuntimeException ignored) {
      }
      try { active.release(); } catch (RuntimeException ignored) {}
    }
    releaseCamera();
    capture.setText("●");
    capture.setTextColor(ACCENT);
    photoMode.setEnabled(true);
    videoMode.setEnabled(true);
    if (showResult && saved && capturedFile != null && capturedFile.isFile()) showVideoResult();
    else {
      if (capturedFile != null) capturedFile.delete();
      capturedFile = null;
      openCameraIfReady();
    }
  }

  private File newOutputFile(boolean video) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        && !Environment.isExternalStorageManager()) {
      Toast.makeText(this, "All files access is required for PingGo media.",
          Toast.LENGTH_LONG).show();
      return null;
    }
    String folder = video ? "Videos" : "Images";
    File directory = new File(Environment.getExternalStorageDirectory(), "PingGo/" + folder);
    if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory()) {
      Toast.makeText(this, "Unable to create the PingGo media folder.",
          Toast.LENGTH_SHORT).show();
      return null;
    }
    return new File(directory,
        (video ? "VID_" : "IMG_") + System.currentTimeMillis() + (video ? ".mp4" : ".jpg"));
  }

  private void showPhotoResult() {
    openSelectedPreview(Collections.singletonList(
        new GalleryItem(Uri.fromFile(capturedFile), false, null)), true);
  }

  private void showVideoResult() {
    openSelectedPreview(Collections.singletonList(
        new GalleryItem(Uri.fromFile(capturedFile), true, null)), true);
  }

  private void handleBack() {
    if (recording) {
      stopVideoRecording(false);
      return;
    }
    finish();
  }

  @Override
  public void onBackPressed() {
    handleBack();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (!showingResult) openCameraIfReady();
  }

  @Override
  protected void onPause() {
    if (recording) stopVideoRecording(false);
    else releaseCamera();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    if (recorder != null) stopVideoRecording(false);
    releaseCamera();
    if (capturedFile != null && !isChangingConfigurations()) capturedFile.delete();
    galleryExecutor.shutdownNow();
    super.onDestroy();
  }

  private static final class GalleryItem {
    final Uri uri;
    final boolean video;
    final Bitmap thumbnail;

    GalleryItem(Uri uri, boolean video, Bitmap thumbnail) {
      this.uri = uri;
      this.video = video;
      this.thumbnail = thumbnail;
    }
  }

  private void releaseCamera() {
    Camera active = camera;
    camera = null;
    if (active == null) return;
    try { active.stopPreview(); } catch (RuntimeException ignored) {}
    try { active.release(); } catch (RuntimeException ignored) {}
  }

  private int findBackCamera() {
    Camera.CameraInfo info = new Camera.CameraInfo();
    for (int index = 0; index < Camera.getNumberOfCameras(); index++) {
      Camera.getCameraInfo(index, info);
      if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return index;
    }
    return 0;
  }

  private int displayOrientation(int id) {
    Camera.CameraInfo info = new Camera.CameraInfo();
    Camera.getCameraInfo(id, info);
    int degrees = displayRotationDegrees();
    return info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
        ? (360 - (info.orientation + degrees) % 360) % 360
        : (info.orientation - degrees + 360) % 360;
  }

  private int captureOrientation(int id) {
    Camera.CameraInfo info = new Camera.CameraInfo();
    Camera.getCameraInfo(id, info);
    int degrees = displayRotationDegrees();
    return info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
        ? (info.orientation - degrees + 360) % 360
        : (info.orientation + degrees) % 360;
  }

  private int displayRotationDegrees() {
    int rotation = getWindowManager().getDefaultDisplay().getRotation();
    if (rotation == Surface.ROTATION_90) return 90;
    if (rotation == Surface.ROTATION_180) return 180;
    if (rotation == Surface.ROTATION_270) return 270;
    return 0;
  }

  private Button modeButton(String label, boolean selected) {
    Button button = textButton(label, 14, selected ? Color.WHITE : 0xFFABB4C0);
    button.setBackgroundColor(Color.TRANSPARENT);
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

  private GradientDrawable circle(int fill, float strokeWidthPx, int strokeColor) {
    GradientDrawable drawable = rounded(fill, px(275f));
    drawable.setStroke(px(strokeWidthPx), strokeColor);
    return drawable;
  }

  private FrameLayout.LayoutParams match() {
    return new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
  }

  private int px(float value) {
    return Math.round(figmaConfig.toRuntime(value, Math.max(1, getResources().getDisplayMetrics().widthPixels)));
  }

}
