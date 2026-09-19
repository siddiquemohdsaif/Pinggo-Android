package com.w3n.pinggo.views;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.database.Cursor;
import android.content.ContentUris;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.Text;
import android.graphics.RectF;
import android.graphics.Canvas;
import android.view.MotionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns live photo/video capture and the media-only gallery selection surface. */
@SuppressWarnings("deprecation")
public class CameraCaptureOverlayView extends NativeMediaScreenView {
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
  public static final String EXTRA_MEDIA_TYPE = "com.w3n.pinggo.CAPTURE_MEDIA_TYPE";
  public static final String EXTRA_MEDIA_TYPES = "com.w3n.pinggo.CAPTURE_MEDIA_TYPES";
  private static final int ACCENT = 0xFF019CC4;
  private final Listener listener;
  private final FrameLayout root;
  private SurfaceView cameraPreview;
  private FrameLayout modeControls;
  private final ZLayerGroup galleryLayers = new ZLayerGroup(this);
  private final ZLayer galleryLayer = galleryLayers.addLayer("camera_gallery_list");
  private final GalleryAdapter galleryAdapter = new GalleryAdapter();
  private ComponentList<GalleryItem> galleryList;
  private NativeCameraChromeView chrome;
  private Camera camera;
  private MediaRecorder recorder;
  private File capturedFile;
  private final List<GalleryItem> temporaryCaptures = new ArrayList<>();
  private final List<GalleryItem> galleryItems = new ArrayList<>();
  private final LinkedHashMap<String, GalleryItem> selectedGallery = new LinkedHashMap<>();
  private final ExecutorService galleryExecutor = Executors.newSingleThreadExecutor();
  private final Handler recordingTimerHandler = new Handler(Looper.getMainLooper());
  private final Runnable recordingTimerUpdater = new Runnable() {
    @Override public void run() {
      if (!recording) return;
      updateChrome();
      recordingTimerHandler.postDelayed(this, 250L);
    }
  };
  private boolean galleryLoaded;
  private boolean surfaceReady;
  private boolean videoModeSelected;
  private boolean recording;
  private boolean showingResult;
  private boolean flashEnabled;
  private boolean released;
  private boolean finalizingSend;
  private long recordingStartedElapsedMs;
  private int cameraId = -1;
  public CameraCaptureOverlayView(Context context, Listener listener) {
    super(context);
    setNavigationBarState(true, NativeCameraChromeView.HEADER_COLOR);
    this.root = this;
    this.listener = listener;
    buildUi();
    requestMediaPermissions();
  }

  private void buildUi() {
    cameraPreview = new SurfaceView(getContext());
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

    modeControls = new FrameLayout(getContext());
    chrome = new NativeCameraChromeView(getContext(), new NativeCameraChromeView.Listener() {
      @Override public void onBack() { handleBack(); }
      @Override public void onPhoto() { selectMode(false); }
      @Override public void onVideo() { selectMode(true); }
      @Override public void onCapture() { capture(); }
      @Override public void onGalleryConfirm() { showGalleryResult(); }
      @Override public void onFlash() { toggleFlash(); }
      @Override public void onFlip() { flipCamera(); }
      @Override public void onGallery() { listener.onGalleryRequested(videoModeSelected); }
    });
    modeControls.addView(chrome, match());
    root.addView(modeControls, match());
    ViewCompat.setOnApplyWindowInsetsListener(chrome, (view, windowInsets) -> {
      Insets status = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
      chrome.setTopInset(status.top);
      return windowInsets;
    });
    updateChrome();

  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    if (width <= 0 || height <= 0) return;
    galleryLayer.clear();
    galleryList = galleryLayer.add(new ComponentList.Builder<GalleryItem>(
        getContext(), "camera_gallery_component_list",
        new RectF(0f, height - px(910f), width, height - px(700f)))
        .setOrientation(ComponentList.Orientation.HORIZONTAL)
        .setItemSize(px(209f))
        .setItemSpacingPx(px(19.25f))
        .setPaddingPx(0f, 0f, px(170.5f), 0f)
        .setAdapter(galleryAdapter)
        .setClipToBounds(true)
        .setScrollEnabled(true)
        .setOverscrollEnabled(false)
        .setOnItemClickListener((component, item, position) -> toggleGalleryItem(item)));
  }

  @Override protected void dispatchDraw(Canvas canvas) {
    if (cameraPreview != null && cameraPreview.getVisibility() == View.VISIBLE) {
      drawChild(canvas, cameraPreview, getDrawingTime());
    }
    if (isGalleryListVisible()) galleryLayers.draw(canvas);
    if (modeControls != null && modeControls.getVisibility() == View.VISIBLE) {
      drawChild(canvas, modeControls, getDrawingTime());
    }
  }

  @Override public boolean dispatchTouchEvent(MotionEvent event) {
    if (isGalleryListVisible() && galleryLayers.onTouchEvent(event)) return true;
    return super.dispatchTouchEvent(event);
  }

  private boolean isGalleryListVisible() {
    return !videoModeSelected && !showingResult && galleryList != null
        && modeControls != null && modeControls.getVisibility() == View.VISIBLE;
  }

  private void requestMediaPermissions() {
    boolean cameraGranted = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED;
    boolean microphoneGranted = ContextCompat.checkSelfPermission(
        getContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    boolean galleryGranted = hasGalleryPermission();
    boolean legacyWriteGranted = Build.VERSION.SDK_INT > Build.VERSION_CODES.P
        || ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED;
    if (galleryGranted) loadGallery();
    if (cameraGranted && microphoneGranted && galleryGranted && legacyWriteGranted) {
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
    if (!legacyWriteGranted) requested.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    listener.onPermissionsRequired(requested.toArray(new String[0]));
  }

  public void onPermissionsResult(Map<String, Boolean> result) {
    if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED) {
      openCameraIfReady();
    } else {
      Toast.makeText(getContext(), "Camera permission is required.", Toast.LENGTH_SHORT).show();
      listener.onClose();
    }
    if (hasGalleryPermission()) loadGallery();
  }

  public void onExternalGalleryPicked(ArrayList<Uri> uris, ArrayList<String> types) {
    if (uris == null || uris.isEmpty() || showingResult) return;
    showingResult = true;
    releaseCamera();
    listener.onPreviewRequested(uris, types == null ? new ArrayList<>() : types, false);
  }

  private boolean hasGalleryPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_MEDIA_IMAGES)
              == PackageManager.PERMISSION_GRANTED
          || ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_MEDIA_VIDEO)
              == PackageManager.PERMISSION_GRANTED;
    }
    return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
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
      try (Cursor cursor = getContext().getContentResolver().query(
          collection, projection, selection, arguments, order)) {
        if (cursor != null) {
          int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
          int typeColumn = cursor.getColumnIndexOrThrow(
              MediaStore.Files.FileColumns.MEDIA_TYPE);
          while (cursor.moveToNext()) {
            long id = cursor.getLong(idColumn);
            boolean video = cursor.getInt(typeColumn)
                == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
            Uri uri = ContentUris.withAppendedId(collection, id);
            loaded.add(new GalleryItem(uri, video, null));
          }
        }
      } catch (RuntimeException error) {
        post(() -> Toast.makeText(getContext(),
            "Unable to load the gallery.", Toast.LENGTH_SHORT).show());
      }
      post(() -> showGalleryItems(loaded));
    });
  }

  private Bitmap loadGalleryThumbnail(Uri uri, boolean video) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return getContext().getContentResolver().loadThumbnail(uri, new Size(px(264f), px(264f)), null);
      }
      long id = ContentUris.parseId(uri);
      return video
          ? MediaStore.Video.Thumbnails.getThumbnail(getContext().getContentResolver(), id,
              MediaStore.Video.Thumbnails.MINI_KIND, null)
          : MediaStore.Images.Thumbnails.getThumbnail(getContext().getContentResolver(), id,
              MediaStore.Images.Thumbnails.MINI_KIND, null);
    } catch (IOException | RuntimeException error) {
      return null;
    }
  }

  private void showGalleryItems(List<GalleryItem> loaded) {
    if (!isAttachedToWindow()) return;
    galleryItems.clear();
    galleryItems.addAll(loaded);
    rebuildGalleryTiles();
  }

  private void rebuildGalleryTiles() {
    galleryAdapter.notifyDataSetChanged();
  }

  private void toggleGalleryItem(GalleryItem item) {
    if (temporaryCaptures.contains(item)) {
      removeTemporaryCapture(item, true);
      rebuildGalleryTiles();
      updateChrome();
      return;
    }
    String key = item.uri.toString();
    boolean selected;
    if (selectedGallery.containsKey(key)) {
      selectedGallery.remove(key);
      selected = false;
    } else {
      selectedGallery.put(key, item);
      selected = true;
    }
    galleryAdapter.notifyDataSetChanged();
    updateChrome();
  }

  private GalleryItem galleryItemAt(int position) {
    return position < temporaryCaptures.size() ? temporaryCaptures.get(position)
        : galleryItems.get(position - temporaryCaptures.size());
  }

  private void requestGalleryThumbnail(GalleryItem item) {
    if (item.thumbnailRequested || released) return;
    item.thumbnailRequested = true;
    try {
      galleryExecutor.execute(() -> {
        if (released) return;
        Bitmap thumbnail = loadGalleryThumbnail(item.uri, item.video);
        post(() -> {
          if (released) { if (thumbnail != null) thumbnail.recycle(); return; }
          item.thumbnail = thumbnail;
          galleryAdapter.notifyDataSetChanged();
        });
      });
    } catch (RuntimeException ignored) { item.thumbnailRequested = false; }
  }

  private final class GalleryAdapter extends ComponentList.Adapter<GalleryItem> {
    private final Bitmap empty = Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);
    @Override public int getItemCount() { return temporaryCaptures.size()+galleryItems.size(); }
    @Override public GalleryItem getItem(int position) { return galleryItemAt(position); }
    @Override public long getItemId(int position) { return getItem(position).uri.toString().hashCode(); }
    @Override public void onCreateItem(ComponentList.Item item, int type) {
      float w=item.getScope().width(),h=item.getScope().height();
      ZLayer row=item.addLayer("tile");
      row.add(new Image.Builder(getContext(),item.getScope().id("thumbnail"),empty,new RectF(0,0,w,h)).setScaleType(Image.ScaleType.CENTER_CROP));
      row.add(new Text.Builder(getContext(),item.getScope().id("video"),"▶",new RectF(0,0,w,h)).setTextColor(Color.WHITE).setTextSizePx(px(55f)).setAlignment(Text.Alignment.CENTER).setVerticalAlignment(Text.VerticalAlignment.CENTER));
      row.add(new Text.Builder(getContext(),item.getScope().id("check"),"✓",new RectF(w-px(75f),0,w,px(75f))).setTextColor(ACCENT).setTextSizePx(px(65f)).setAlignment(Text.Alignment.CENTER));
    }
    @Override public void onBindItem(ComponentList.Item holder,GalleryItem item,int position) {
      holder.find("thumbnail",Image.class).setBitmap(item.thumbnail==null?empty:item.thumbnail);
      holder.find("video",Text.class).setVisible(item.video);
      holder.find("check",Text.class).setVisible(temporaryCaptures.contains(item)||selectedGallery.containsKey(item.uri.toString()));
      if(item.thumbnail==null)requestGalleryThumbnail(item);
    }
    void release() { empty.recycle(); }
  }

  private void showGalleryResult() {
    if (selectedGallery.isEmpty() && temporaryCaptures.isEmpty()) return;
    ArrayList<GalleryItem> selected = new ArrayList<>();
    selected.addAll(temporaryCaptures);
    selected.addAll(selectedGallery.values());
    openSelectedPreview(selected, false);
  }

  private void openSelectedPreview(List<GalleryItem> selected, boolean capturedMedia) {
    if (selected == null || selected.isEmpty()) return;
    showingResult = true;
    releaseCamera();
    ArrayList<Uri> uris = new ArrayList<>();
    ArrayList<String> types = new ArrayList<>();
    for (GalleryItem item : selected) {
      Uri uri = item.uri;
      if (item.temporaryFile != null && "file".equals(uri.getScheme())) {
        uri = FileProvider.getUriForFile(
            getContext(), getContext().getPackageName() + ".files", item.temporaryFile);
      }
      uris.add(uri);
      types.add(item.video ? "Video" : "Image");
    }
    listener.onPreviewRequested(uris, types, capturedMedia);
  }

  public void onPreviewResult(boolean send, ArrayList<Uri> remaining, ArrayList<String> types) {
    onPreviewResult(send, remaining, types, "");
  }

  public void onPreviewResult(boolean send, ArrayList<Uri> remaining, ArrayList<String> types,
      String caption) {
    if (remaining == null) remaining = new ArrayList<>();
    java.util.Set<String> keys = new java.util.HashSet<>();
    for (Uri uri : remaining) if (uri != null) keys.add(uri.toString());
    if (send) {
      persistCapturesAndSend(remaining, types, caption);
      return;
    }
    selectedGallery.entrySet().removeIf(entry -> !keys.contains(entry.getKey()));
    retainTemporaryCaptures(keys);
    refreshGallerySelectionViews();
    if (temporaryCaptures.isEmpty()) clearCapturedFile(true);
    showingResult = false;
    cameraPreview.setVisibility(View.VISIBLE);
    modeControls.setVisibility(View.VISIBLE);
    updateChrome();
    openCameraIfReady();
  }

  private void refreshGallerySelectionViews() {
    rebuildGalleryTiles();
    updateChrome();
  }

  private void openCameraIfReady() {
    if (!surfaceReady || showingResult || camera != null
        || ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) return;
    try {
      if (cameraId < 0 || cameraId >= Camera.getNumberOfCameras()) cameraId = findBackCamera();
      camera = Camera.open(cameraId);
      camera.setDisplayOrientation(displayOrientation(cameraId));
      Camera.Parameters parameters = camera.getParameters();
      if (parameters.getSupportedFocusModes() != null
          && parameters.getSupportedFocusModes().contains(
          Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
      }
      applyFlash(parameters);
      camera.setParameters(parameters);
      camera.setPreviewDisplay(cameraPreview.getHolder());
      camera.startPreview();
      updateChrome();
    } catch (IOException | RuntimeException error) {
      releaseCamera();
      Toast.makeText(getContext(), "Unable to open the camera.", Toast.LENGTH_SHORT).show();
    }
  }

  private void restartPreview() {
    releaseCamera();
    openCameraIfReady();
  }

  private void selectMode(boolean video) {
    if (recording || showingResult) return;
    videoModeSelected = video;
    invalidate();
    if (flashEnabled && !supportsFlash(video)) {
      flashEnabled = false;
      updateCameraFlash();
    }
    updateChrome();
  }

  private void updateChrome() {
    if (chrome == null) return;
    chrome.setState(
        videoModeSelected,
        recording,
        !recording,
        videoModeSelected ? 0 : selectedGallery.size() + temporaryCaptures.size(),
        flashEnabled,
        supportsFlash(videoModeSelected),
        recordingTime());
  }

  private String recordingTime() {
    long elapsedSeconds = recording
        ? Math.max(0L, SystemClock.elapsedRealtime() - recordingStartedElapsedMs) / 1000L
        : 0L;
    return String.format(Locale.US, "%02d:%02d", elapsedSeconds / 60L, elapsedSeconds % 60L);
  }

  private boolean supportsFlash(boolean videoMode) {
    if (camera == null) return false;
    try {
      List<String> modes = camera.getParameters().getSupportedFlashModes();
      if (modes == null) return false;
      return videoMode
          ? modes.contains(Camera.Parameters.FLASH_MODE_TORCH)
          : modes.contains(Camera.Parameters.FLASH_MODE_TORCH)
              || modes.contains(Camera.Parameters.FLASH_MODE_ON);
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private void toggleFlash() {
    if (recording || camera == null || !supportsFlash(videoModeSelected)) return;
    flashEnabled = !flashEnabled;
    updateCameraFlash();
    updateChrome();
  }

  private void updateCameraFlash() {
    if (camera == null) return;
    try {
      Camera.Parameters parameters = camera.getParameters();
      applyFlash(parameters);
      camera.setParameters(parameters);
    } catch (RuntimeException error) {
      flashEnabled = false;
    }
  }

  private void applyFlash(Camera.Parameters parameters) {
    List<String> modes = parameters.getSupportedFlashModes();
    if (modes == null || modes.isEmpty()) return;
    String target = Camera.Parameters.FLASH_MODE_OFF;
    if (flashEnabled) {
      if (modes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
        target = Camera.Parameters.FLASH_MODE_TORCH;
      } else if (!videoModeSelected && modes.contains(Camera.Parameters.FLASH_MODE_ON)) {
        target = Camera.Parameters.FLASH_MODE_ON;
      }
    }
    if (modes.contains(target)) parameters.setFlashMode(target);
  }

  private void flipCamera() {
    if (recording || showingResult || Camera.getNumberOfCameras() < 2) return;
    int nextCamera = findAlternateCamera(cameraId);
    if (nextCamera == cameraId) return;
    flashEnabled = false;
    releaseCamera();
    cameraId = nextCamera;
    openCameraIfReady();
  }

  private int findAlternateCamera(int currentId) {
    Camera.CameraInfo current = new Camera.CameraInfo();
    Camera.getCameraInfo(currentId, current);
    Camera.CameraInfo candidate = new Camera.CameraInfo();
    for (int index = 0; index < Camera.getNumberOfCameras(); index++) {
      if (index == currentId) continue;
      Camera.getCameraInfo(index, candidate);
      if (candidate.facing != current.facing) return index;
    }
    return currentId;
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
        boolean keepInStrip = !selectedGallery.isEmpty() || !temporaryCaptures.isEmpty();
        capturedFile = output;
        if (keepInStrip) showTemporaryCapturedPhoto();
        else showPhotoResult();
      } catch (IOException error) {
        output.delete();
        Toast.makeText(getContext(), "Unable to save the photo.", Toast.LENGTH_SHORT).show();
        restartPreview();
      }
    });
  }

  private void startVideoRecording() {
    if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
      listener.onPermissionsRequired(new String[] {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
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
      recordingStartedElapsedMs = SystemClock.elapsedRealtime();
      recordingTimerHandler.removeCallbacks(recordingTimerUpdater);
      recordingTimerHandler.post(recordingTimerUpdater);
      updateChrome();
    } catch (IOException | RuntimeException error) {
      try { next.release(); } catch (RuntimeException ignored) {}
      output.delete();
      capturedFile = null;
      recording = false;
      releaseCamera();
      openCameraIfReady();
      Toast.makeText(getContext(), "Unable to start video recording.", Toast.LENGTH_SHORT).show();
    }
  }

  private void stopVideoRecording(boolean showResult) {
    MediaRecorder active = recorder;
    recorder = null;
    recording = false;
    recordingTimerHandler.removeCallbacks(recordingTimerUpdater);
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
    updateChrome();
    if (showResult && saved && capturedFile != null && capturedFile.isFile()) showVideoResult();
    else {
      if (capturedFile != null) capturedFile.delete();
      capturedFile = null;
      openCameraIfReady();
    }
  }

  private File newOutputFile(boolean video) {
    File directory = new File(getContext().getCacheDir(), "camera_captures");
    if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory()) {
      Toast.makeText(getContext(), "Unable to create temporary capture storage.",
          Toast.LENGTH_SHORT).show();
      return null;
    }
    return new File(directory,
        (video ? "VID_" : "IMG_") + System.currentTimeMillis() + (video ? ".mp4" : ".jpg"));
  }

  private void showPhotoResult() {
    openSelectedPreview(Collections.singletonList(
        new GalleryItem(Uri.fromFile(capturedFile), false, null, capturedFile)), true);
  }

  private void showTemporaryCapturedPhoto() {
    File file = capturedFile;
    capturedFile = null;
    Bitmap thumbnail = loadCapturedThumbnail(file);
    temporaryCaptures.add(0, new GalleryItem(Uri.fromFile(file), false, thumbnail, file));
    rebuildGalleryTiles();
    if (galleryList != null) galleryList.scrollToPosition(0);
    updateChrome();
    restartPreview();
  }

  private Bitmap loadCapturedThumbnail(File file) {
    if (file == null || !file.isFile()) return null;
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
    int target = px(264f);
    int sampleSize = 1;
    while (bounds.outWidth / sampleSize > target * 2
        || bounds.outHeight / sampleSize > target * 2) {
      sampleSize *= 2;
    }
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = sampleSize;
    return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
  }

  private Uri capturedContentUri(GalleryItem item) {
    return FileProvider.getUriForFile(
        getContext(), getContext().getPackageName() + ".files", item.temporaryFile);
  }

  private void removeTemporaryCapture(GalleryItem item, boolean deleteFile) {
    if (item == null || !temporaryCaptures.remove(item)) return;
    if (item != null && item.thumbnail != null && !item.thumbnail.isRecycled()) {
      item.thumbnail.recycle();
    }
    if (deleteFile && item.temporaryFile != null) item.temporaryFile.delete();
  }

  private void retainTemporaryCaptures(java.util.Set<String> keys) {
    for (int index = temporaryCaptures.size() - 1; index >= 0; index--) {
      GalleryItem item = temporaryCaptures.get(index);
      if (!keys.contains(capturedContentUri(item).toString())) {
        removeTemporaryCapture(item, true);
      }
    }
  }

  private void persistCapturesAndSend(
      ArrayList<Uri> remaining, ArrayList<String> types, String caption) {
    if (finalizingSend) return;
    finalizingSend = true;
    ArrayList<Uri> sources = new ArrayList<>(remaining);
    ArrayList<String> mediaTypes = types == null ? new ArrayList<>() : new ArrayList<>(types);
    galleryExecutor.execute(() -> {
      ArrayList<Uri> permanent = new ArrayList<>();
      ArrayList<Uri> published = new ArrayList<>();
      try {
        for (int index = 0; index < sources.size(); index++) {
          String type = index < mediaTypes.size() ? mediaTypes.get(index) : "Image";
          Uri source = sources.get(index);
          Uri saved = persistAppCapture(source, "Video".equalsIgnoreCase(type), index);
          permanent.add(saved);
          if (isAppFileUri(source)) published.add(saved);
        }
        post(() -> {
          finalizingSend = false;
          clearTemporaryCaptures(true);
          clearCapturedFile(true);
          listener.onSend(permanent, mediaTypes, caption);
        });
      } catch (IOException | RuntimeException error) {
        for (Uri uri : published) {
          try { getContext().getContentResolver().delete(uri, null, null); }
          catch (RuntimeException ignored) {}
        }
        post(() -> {
          finalizingSend = false;
          Toast.makeText(getContext(), "Unable to save captured media.",
              Toast.LENGTH_SHORT).show();
        });
      }
    });
  }

  private Uri persistAppCapture(Uri source, boolean video, int index) throws IOException {
    if (source == null) throw new IOException("Missing captured media");
    if (!isAppFileUri(source)) return source;
    String extension = video ? ".mp4" : ".jpg";
    String name = (video ? "VID_" : "IMG_") + System.currentTimeMillis()
        + "_" + index + extension;
    File directory = new File(Environment.getExternalStorageDirectory(),
        "PingGo" + File.separator + (video ? "Videos" : "Images"));
    if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory()) {
      throw new IOException("Unable to create PingGo media folder");
    }
    File destination = new File(directory, name);
    boolean complete = false;
    try (InputStream input = getContext().getContentResolver().openInputStream(source);
         FileOutputStream output = new FileOutputStream(destination)) {
      if (input == null) throw new IOException("Unable to open captured media");
      byte[] buffer = new byte[64 * 1024];
      int read;
      while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
      complete = true;
      MediaScannerConnection.scanFile(getContext(),
          new String[] {destination.getAbsolutePath()},
          new String[] {video ? "video/mp4" : "image/jpeg"}, null);
      return FileProvider.getUriForFile(
          getContext(), getContext().getPackageName() + ".files", destination);
    } finally {
      if (!complete && destination.exists()) destination.delete();
    }
  }

  private boolean isAppFileUri(Uri uri) {
    return uri != null
        && (getContext().getPackageName() + ".files").equals(uri.getAuthority());
  }

  private void clearTemporaryCaptures(boolean deleteFiles) {
    for (int index = temporaryCaptures.size() - 1; index >= 0; index--) {
      removeTemporaryCapture(temporaryCaptures.get(index), deleteFiles);
    }
  }

  private void clearCapturedFile(boolean deleteFile) {
    File file = capturedFile;
    capturedFile = null;
    if (deleteFile && file != null) file.delete();
  }

  private void showVideoResult() {
    openSelectedPreview(Collections.singletonList(
        new GalleryItem(Uri.fromFile(capturedFile), true, null, capturedFile)), true);
  }

  private void handleBack() {
    if (finalizingSend) return;
    if (recording) {
      stopVideoRecording(false);
      return;
    }
    listener.onClose();
  }

  public void onBackPressed() {
    handleBack();
  }

  public void onHostResume() {
    if (!showingResult) openCameraIfReady();
  }

  public void onHostPause() {
    if (recording) stopVideoRecording(false);
    else releaseCamera();
  }

  @Override public void release() {
    released = true;
    recordingTimerHandler.removeCallbacks(recordingTimerUpdater);
    if (recorder != null) stopVideoRecording(false);
    releaseCamera();
    clearTemporaryCaptures(true);
    clearCapturedFile(true);
    galleryExecutor.shutdownNow();
    selectedGallery.clear();
    for (GalleryItem item : galleryItems) {
      if (item.thumbnail != null && !item.thumbnail.isRecycled()) item.thumbnail.recycle();
    }
    galleryItems.clear();
    galleryLayers.release();
    galleryAdapter.release();
    if (chrome != null) chrome.release();
    super.release();
  }

  private static final class GalleryItem {
    final Uri uri;
    final boolean video;
    final File temporaryFile;
    volatile Bitmap thumbnail;
    volatile boolean thumbnailRequested;

    GalleryItem(Uri uri, boolean video, Bitmap thumbnail) {
      this(uri, video, thumbnail, null);
    }

    GalleryItem(Uri uri, boolean video, Bitmap thumbnail, File temporaryFile) {
      this.uri = uri;
      this.video = video;
      this.thumbnail = thumbnail;
      this.temporaryFile = temporaryFile;
      this.thumbnailRequested = thumbnail != null || temporaryFile != null;
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
    int rotation = ((android.app.Activity) getContext()).getWindowManager().getDefaultDisplay().getRotation();
    if (rotation == Surface.ROTATION_90) return 90;
    if (rotation == Surface.ROTATION_180) return 180;
    if (rotation == Surface.ROTATION_270) return 270;
    return 0;
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

  public interface Listener {
    void onPermissionsRequired(String[] permissions);
    void onPreviewRequested(ArrayList<Uri> uris, ArrayList<String> types, boolean captured);
    void onGalleryRequested(boolean videoOnly);
    void onSend(ArrayList<Uri> uris, ArrayList<String> types);
    default void onSend(ArrayList<Uri> uris, ArrayList<String> types, String caption) {
      onSend(uris, types);
    }
    void onClose();
  }

}
