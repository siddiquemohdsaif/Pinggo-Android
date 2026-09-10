package com.w3n.pinggo.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.w3n.pinggo.views.linkeddevice.NativeQrScannerView;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Pinggo-owned camera scanner. ZXing decoding is local and needs no Google Play service. */
@SuppressWarnings("deprecation")
public final class QrScannerActivity extends AppCompatActivity {
  public static final String EXTRA_RESULT = "com.w3n.pinggo.QR_SCAN_RESULT";
  private static final int MAX_RESULT_LENGTH = 8192;
  private static final long DECODE_INTERVAL_MS = 140L;

  private final ExecutorService decoderExecutor = Executors.newSingleThreadExecutor();
  private final AtomicBoolean decodeRunning = new AtomicBoolean();
  private final AtomicBoolean accepted = new AtomicBoolean();
  private final MultiFormatReader reader = new MultiFormatReader();
  private final ActivityResultLauncher<String> cameraPermission = registerForActivityResult(
      new ActivityResultContracts.RequestPermission(), this::onCameraPermissionResult);
  private SurfaceView preview;
  private NativeQrScannerView scannerView;
  private Camera camera;
  private boolean resumed;
  private boolean surfaceReady;
  private boolean torchAvailable;
  private boolean torchOn;
  private int previewWidth;
  private int previewHeight;
  private long lastDecodeAt;

  private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
    @Override public void surfaceCreated(SurfaceHolder holder) {
      surfaceReady = true;
      openCameraIfReady();
    }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      if (camera != null) restartPreview();
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
      surfaceReady = false;
      releaseCamera();
    }
  };

  @Override protected void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    configureReader();

    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(0xFF000000);
    preview = new SurfaceView(this);
    preview.getHolder().addCallback(surfaceCallback);
    root.addView(preview, match());
    scannerView = new NativeQrScannerView(this, new NativeQrScannerView.Listener() {
      @Override public void onBack() { cancelScan(); }
      @Override public void onToggleTorch() { toggleTorch(); }
      @Override public void onPermissionAction() { requestOrOpenCameraSettings(); }
    });
    root.addView(scannerView, match());
    setContentView(root);
    ViewCompat.setOnApplyWindowInsetsListener(scannerView, (view, insets) -> {
      Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
      scannerView.setInsets(bars.top, bars.bottom);
      return insets;
    });
    ViewCompat.requestApplyInsets(scannerView);

    if (!hasCameraPermission()) cameraPermission.launch(Manifest.permission.CAMERA);
  }

  private void configureReader() {
    Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
    hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
    hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
    reader.setHints(hints);
  }

  @Override protected void onResume() {
    super.onResume();
    resumed = true;
    openCameraIfReady();
  }

  @Override protected void onPause() {
    resumed = false;
    releaseCamera();
    super.onPause();
  }

  private void onCameraPermissionResult(boolean granted) {
    if (granted) {
      scannerView.setStatus("Point your camera at the QR code", false);
      openCameraIfReady();
    } else {
      scannerView.setStatus("Camera access is required to scan the companion QR code.", true);
    }
  }

  private void requestOrOpenCameraSettings() {
    if (hasCameraPermission()) {
      openCameraIfReady();
    } else if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
      cameraPermission.launch(Manifest.permission.CAMERA);
    } else {
      Intent settings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.fromParts("package", getPackageName(), null));
      startActivity(settings);
    }
  }

  private boolean hasCameraPermission() {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED;
  }

  private void openCameraIfReady() {
    if (!resumed || !surfaceReady || camera != null || !hasCameraPermission()
        || isFinishing() || accepted.get()) return;
    try {
      int cameraId = findBackCamera();
      camera = Camera.open(cameraId);
      Camera.Parameters parameters = camera.getParameters();
      Camera.Size size = choosePreviewSize(parameters.getSupportedPreviewSizes());
      if (size != null) parameters.setPreviewSize(size.width, size.height);
      parameters.setPreviewFormat(ImageFormat.NV21);
      List<String> focusModes = parameters.getSupportedFocusModes();
      if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
      } else if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
      }
      List<String> flashModes = parameters.getSupportedFlashModes();
      torchAvailable = flashModes != null && flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH);
      torchOn = false;
      if (torchAvailable) parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
      camera.setParameters(parameters);
      Camera.Size actual = camera.getParameters().getPreviewSize();
      previewWidth = actual.width;
      previewHeight = actual.height;
      camera.setDisplayOrientation(displayOrientation(cameraId));
      camera.setPreviewDisplay(preview.getHolder());
      camera.setPreviewCallback((data, source) -> analyze(data));
      camera.startPreview();
      scannerView.setTorchState(torchAvailable, false);
      scannerView.setStatus("Point your camera at the QR code", false);
    } catch (Exception error) {
      releaseCamera();
      scannerView.setStatus("Unable to start the camera. Close other camera apps and try again.", true);
    }
  }

  private void restartPreview() {
    Camera active = camera;
    if (active == null || accepted.get()) return;
    try {
      active.stopPreview();
      active.setPreviewDisplay(preview.getHolder());
      active.setPreviewCallback((data, source) -> analyze(data));
      active.startPreview();
    } catch (Exception error) {
      releaseCamera();
      scannerView.setStatus("Camera preview stopped. Tap below to try again.", true);
    }
  }

  private void analyze(byte[] frame) {
    if (frame == null || accepted.get() || previewWidth <= 0 || previewHeight <= 0) return;
    long now = SystemClock.elapsedRealtime();
    if (now - lastDecodeAt < DECODE_INTERVAL_MS || !decodeRunning.compareAndSet(false, true)) return;
    lastDecodeAt = now;
    byte[] copy = Arrays.copyOf(frame, frame.length);
    try {
      decoderExecutor.execute(() -> decode(copy, previewWidth, previewHeight));
    } catch (RejectedExecutionException ignored) {
      decodeRunning.set(false);
    }
  }

  private void decode(byte[] frame, int width, int height) {
    try {
      PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
          frame, width, height, 0, 0, width, height, false);
      BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
      Result result = reader.decodeWithState(bitmap);
      String value = result == null ? null : result.getText();
      if (value != null && !value.isEmpty() && value.length() <= MAX_RESULT_LENGTH) {
        runOnUiThread(() -> accept(value));
      }
    } catch (Exception ignored) {
      // A normal preview frame usually has no QR. Continue with the next throttled frame.
    } finally {
      reader.reset();
      decodeRunning.set(false);
    }
  }

  private void accept(String value) {
    if (!resumed || isFinishing() || !accepted.compareAndSet(false, true)) return;
    if (camera != null) camera.setPreviewCallback(null);
    Intent result = new Intent().putExtra(EXTRA_RESULT, value);
    setResult(RESULT_OK, result);
    finish();
  }

  private void toggleTorch() {
    Camera active = camera;
    if (active == null || !torchAvailable) return;
    try {
      Camera.Parameters parameters = active.getParameters();
      torchOn = !torchOn;
      parameters.setFlashMode(torchOn
          ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
      active.setParameters(parameters);
      scannerView.setTorchState(true, torchOn);
    } catch (RuntimeException error) {
      torchOn = false;
      scannerView.setTorchState(false, false);
    }
  }

  private void releaseCamera() {
    Camera active = camera;
    camera = null;
    torchOn = false;
    torchAvailable = false;
    if (scannerView != null) scannerView.setTorchState(false, false);
    if (active == null) return;
    try { active.setPreviewCallback(null); } catch (RuntimeException ignored) {}
    try { active.stopPreview(); } catch (RuntimeException ignored) {}
    try { active.release(); } catch (RuntimeException ignored) {}
  }

  private int findBackCamera() {
    Camera.CameraInfo info = new Camera.CameraInfo();
    for (int id = 0; id < Camera.getNumberOfCameras(); id++) {
      Camera.getCameraInfo(id, info);
      if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return id;
    }
    if (Camera.getNumberOfCameras() > 0) return 0;
    throw new IllegalStateException("No camera available");
  }

  private Camera.Size choosePreviewSize(List<Camera.Size> sizes) {
    if (sizes == null || sizes.isEmpty()) return null;
    float displayRatio = (float) getResources().getDisplayMetrics().widthPixels
        / Math.max(1, getResources().getDisplayMetrics().heightPixels);
    Camera.Size best = sizes.get(0);
    double bestScore = Double.MAX_VALUE;
    for (Camera.Size size : sizes) {
      long area = (long) size.width * size.height;
      double ratio = (double) Math.min(size.width, size.height) / Math.max(size.width, size.height);
      double ratioPenalty = Math.abs(ratio - displayRatio) * 5_000_000d;
      double areaPenalty = Math.abs(area - 1280d * 720d);
      if (area > 1920L * 1080L) areaPenalty += area;
      double score = ratioPenalty + areaPenalty;
      if (score < bestScore) { best = size; bestScore = score; }
    }
    return best;
  }

  private int displayOrientation(int cameraId) {
    Camera.CameraInfo info = new Camera.CameraInfo();
    Camera.getCameraInfo(cameraId, info);
    int rotation = getWindowManager().getDefaultDisplay().getRotation();
    int degrees = rotation == Surface.ROTATION_90 ? 90
        : rotation == Surface.ROTATION_180 ? 180
        : rotation == Surface.ROTATION_270 ? 270 : 0;
    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      return (360 - ((info.orientation + degrees) % 360)) % 360;
    }
    return (info.orientation - degrees + 360) % 360;
  }

  private void cancelScan() {
    setResult(RESULT_CANCELED);
    finish();
  }

  @Override public void onBackPressed() { cancelScan(); }

  @Override protected void onDestroy() {
    preview.getHolder().removeCallback(surfaceCallback);
    releaseCamera();
    decoderExecutor.shutdownNow();
    scannerView.release();
    super.onDestroy();
  }

  private FrameLayout.LayoutParams match() {
    return new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
  }
}
