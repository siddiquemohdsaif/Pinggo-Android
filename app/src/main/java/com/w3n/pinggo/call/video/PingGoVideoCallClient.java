package com.w3n.pinggo.call.video;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.Surface;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayDeque;
import java.util.Queue;

public final class PingGoVideoCallClient {
    private static final String TAG = "PingGoVideoMedia";
    public interface Listener {
        void onState(String state);
        void onConnected();
        void onRemoteFrame(int width, int height);
        void onRemoteCameraChanged(boolean enabled);
        void onEnded(String reason);
        void onError(String message);
    }

    private static final int WIDTH = 2560;
    private static final int HEIGHT = 1440;
    private static final int MAX_FPS = 30;
    private static final int JPEG_QUALITY = 74;
    private static final int MAX_REMOTE_FRAME_QUEUE = 3;
    private static final long METRICS_INTERVAL_MS = 5_000L;

    private final Context context;
    private final Listener listener;
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor();
    private final Object remoteFrameLock = new Object();
    private final Queue<WebStreamJpegFrame> remoteFrameQueue = new ArrayDeque<>();
    private final LatestFilteredFrameWorker frameWorker;
    private WebStreamClient client;
    private WebStreamCall call;
    private boolean localSurfaceReady;
    private boolean remoteSurfaceReady;
    private boolean resumed;
    private boolean mediaConnected;
    private boolean front = true;
    private boolean cameraEnabled = true;
    private boolean renderRunning;
    private boolean released;
    private long metricsStartedAt = System.currentTimeMillis();
    private long receivedFrames, droppedRemoteFrames;

    public PingGoVideoCallClient(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        // Loading this class initializes camera_pipeline before native worker calls.
        NativeVideoPipeline.nativeSetMainPreviewRendering(true);
        frameWorker = new LatestFilteredFrameWorker(context, new LatestFilteredFrameWorker.FilteredFrameSource() {
            @Override public int getMaxFps() { return MAX_FPS; }
            @Override public int getJpegQualityPercent() { return JPEG_QUALITY; }
            @Override public LatestFilteredFrameWorker.SendResult onLatestFilteredJpeg(
                    byte[] jpeg, int width, int height, long timestampMs) {
                long started = System.nanoTime();
                WebStreamCall active = call;
                if (active == null || active.getState() != WebStreamCall.State.CONNECTED || !cameraEnabled) {
                    return new LatestFilteredFrameWorker.SendResult(false,
                            (System.nanoTime() - started) / 1_000_000.0);
                }
                int[] transform = NativeVideoPipeline.nativeGetPreviewTransform();
                int rotation = transform != null && transform.length > 0 ? transform[0] : 0;
                boolean isFront = transform != null && transform.length > 1 ? transform[1] != 0 : front;
                WebStreamCall.SendOutcome result = active.sendJpeg(jpeg, width, height, MAX_FPS,
                        0, timestampMs, isFront, rotation);
                return new LatestFilteredFrameWorker.SendResult(result.sent, result.queued,
                        result.sequence, (System.nanoTime() - started) / 1_000_000.0);
            }
        });
    }

    public void attachLocalSurface(Surface surface) {
        String result = NativeVideoPipeline.nativeSetSurface(surface, context.getAssets());
        localSurfaceReady = surface != null && !result.startsWith("Error:");
        startCameraIfReady();
    }

    public void attachRemoteSurface(Surface surface) {
        String result = NativeVideoPipeline.nativeSetRemoteSurface(surface, context.getAssets());
        remoteSurfaceReady = surface != null && !result.startsWith("Error:");
    }

    public void setDisplayRotation(int degrees) {
        NativeVideoPipeline.nativeSetDisplayRotation(degrees);
    }

    public void start(String callId, String userId, String credential, String serverUrl) {
        if (released || call != null) return;
        listener.onState("Connecting video…");
        client = new WebStreamClient.Builder(context).userId(userId).displayName(userId)
                .authToken(credential).serverUrl(serverUrl).build();
        call = client.joinCall(callId, new WebStreamClient.Listener() {
            @Override public void onConnecting() { listener.onState("Connecting video…"); }
            @Override public void onWaitingForPeer() { listener.onState("Waiting for video…"); }
            @Override public void onConnected() {
                mediaConnected = true;
                frameWorker.start();
                startCameraIfReady();
                listener.onConnected();
            }
            @Override public void onJpegReceived(WebStreamJpegFrame frame) {
                synchronized (remoteFrameLock) {
                    while (remoteFrameQueue.size() >= MAX_REMOTE_FRAME_QUEUE) {
                        remoteFrameQueue.poll();
                        droppedRemoteFrames++;
                    }
                    remoteFrameQueue.add(frame);
                    receivedFrames++;
                }
                logMetricsIfDue(callId);
                scheduleRemoteRender();
            }
            @Override public void onRemoteCameraChanged(boolean enabled) {
                listener.onRemoteCameraChanged(enabled);
            }
            @Override public void onDisconnected() {
                Log.d(TAG, "media disconnected callId=" + callId
                        + " released=" + released + " mediaConnected=" + mediaConnected
                        + " resumed=" + resumed);
                mediaConnected = false;
                stopCamera();
                if (released) return;
                listener.onEnded("Video disconnected");
            }
            @Override public void onError(Throwable error) {
                Log.e(TAG, "media error callId=" + callId + " released=" + released,
                        error);
                listener.onError(error == null ? "Video connection failed" : error.getMessage());
            }
        });
    }

    public void onResume() {
        resumed = true;
        if (mediaConnected) frameWorker.start();
        startCameraIfReady();
    }

    public void onPause() {
        resumed = false;
        frameWorker.stop();
        NativeVideoPipeline.nativeStop();
    }

    public void switchCamera() {
        front = !front;
        NativeVideoPipeline.nativeStop();
        startCameraIfReady();
    }

    public void setCameraEnabled(boolean enabled) {
        cameraEnabled = enabled;
        WebStreamCall active = call;
        if (active != null) active.sendMediaState(enabled, false);
        if (enabled) startCameraIfReady(); else NativeVideoPipeline.nativeStop();
    }

    public boolean isCameraEnabled() { return cameraEnabled; }

    private void startCameraIfReady() {
        if (!released && resumed && cameraEnabled && localSurfaceReady &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
            NativeVideoPipeline.nativeSetMainPreviewRendering(true);
            String result = NativeVideoPipeline.nativeStart(front, WIDTH, HEIGHT);
            if (result.startsWith("Error:")) listener.onError(result);
        }
    }

    private void scheduleRemoteRender() {
        synchronized (this) {
            if (renderRunning) return;
            renderRunning = true;
        }
        renderExecutor.execute(() -> {
            while (true) {
                WebStreamJpegFrame frame;
                synchronized (remoteFrameLock) {
                    frame = remoteFrameQueue.poll();
                    if (frame == null) {
                        synchronized (PingGoVideoCallClient.this) {
                            renderRunning = false;
                        }
                        return;
                    }
                }
                if (remoteSurfaceReady && NativeVideoPipeline.nativeRenderJpegToMainSurface(
                        frame.getJpegData(), frame.getRotationDegrees(), frame.isFrontCamera())) {
                    listener.onRemoteFrame(frame.getWidth(), frame.getHeight());
                }
            }
        });
    }

    private void stopCamera() {
        frameWorker.stop();
        NativeVideoPipeline.nativeStop();
    }

    private void logMetricsIfDue(String callId) {
        long now = System.currentTimeMillis();
        long elapsed = now - metricsStartedAt;
        if (elapsed < METRICS_INTERVAL_MS) return;
        long received;
        long dropped;
        int queued;
        synchronized (remoteFrameLock) {
            received = receivedFrames; dropped = droppedRemoteFrames;
            queued = remoteFrameQueue.size(); receivedFrames = 0; droppedRemoteFrames = 0;
        }
        Log.i(TAG, "metrics callId=" + callId + " intervalMs=" + elapsed
                + " receivedFps=" + String.format(java.util.Locale.US, "%.1f",
                received * 1000.0 / Math.max(1, elapsed))
                + " droppedRemoteFrames=" + dropped + " queuedFrames=" + queued);
        metricsStartedAt = now;
    }

    public void release() { release("unspecified"); }

    public void release(String source) {
        Log.d(TAG, "release requested source=" + source + " released=" + released
                + " mediaConnected=" + mediaConnected + " callState="
                + (call == null ? "null" : call.getState()));
        if (released) return;
        released = true;
        stopCamera();
        if (call != null) call.leave();
        if (client != null) client.release();
        call = null; client = null;
        synchronized (remoteFrameLock) {
            remoteFrameQueue.clear();
        }
        frameWorker.shutdown();
        NativeVideoPipeline.nativeSetSurface(null, context.getAssets());
        NativeVideoPipeline.nativeSetRemoteSurface(null, context.getAssets());
        renderExecutor.shutdown();
    }
}
