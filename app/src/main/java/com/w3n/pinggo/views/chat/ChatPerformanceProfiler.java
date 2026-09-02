package com.w3n.pinggo.views.chat;

import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.FrameMetrics;
import android.view.Window;
import com.w3n.pinggo.data.cache.MediaPreviewCache;
import java.util.Locale;

/** Low-overhead timing and frame profiler for a single chat screen session. */
public final class ChatPerformanceProfiler {
  public static final String TAG = "PingGoChatPerf";
  private static final double SLOW_FRAME_MS = 24.0;
  private static final double LOG_FRAME_MS = 50.0;
  private static final double FROZEN_FRAME_MS = 700.0;

  private final String chatId;
  private final long openRequestNanos;
  private final long createdNanos = SystemClock.elapsedRealtimeNanos();
  private HandlerThread frameThread;
  private Window attachedWindow;
  private boolean firstDrawLogged;
  private boolean contentSubmitted;
  private boolean firstContentDrawLogged;
  private boolean scrolling;
  private long scrollStartedNanos;
  private long scrollFrames;
  private long scrollSlowFrames;
  private long scrollFrozenFrames;
  private double scrollMaxFrameMs;
  private long scrollBindCount;
  private double scrollBindTotalMs;
  private double scrollMaxBindMs;
  private long scrollReusedRows;
  private double scrollSetupMs;
  private double scrollAttachmentMs;
  private double scrollReplyMs;
  private double scrollTextMs;
  private double scrollMetadataMs;
  private int scrollFirstPosition = -1;
  private int scrollLastPosition = -1;
  private int scrollItemCount;

  private final Window.OnFrameMetricsAvailableListener frameListener =
      (window, metrics, dropCountSinceLastInvocation) -> onFrame(metrics, dropCountSinceLastInvocation);

  public ChatPerformanceProfiler(String chatId, long requestedNanos) {
    this.chatId = chatId == null ? "" : chatId;
    openRequestNanos = requestedNanos > 0L ? requestedNanos : createdNanos;
    log("session_start", "requestedToProfilerMs=" + ms(createdNanos - openRequestNanos));
  }

  public void attach(Window window) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || window == null || attachedWindow != null) {
      return;
    }
    frameThread = new HandlerThread("PingGoChatFrameMetrics");
    frameThread.start();
    attachedWindow = window;
    window.addOnFrameMetricsAvailableListener(frameListener, new Handler(frameThread.getLooper()));
  }

  public void activityCreated(long startedNanos) {
    logDuration("activity_create", startedNanos, memoryFields());
  }

  public synchronized void contentSubmitted(int count) {
    if (!contentSubmitted && count > 0) {
      contentSubmitted = true;
      log("first_content_submit", "count=" + count + " openElapsedMs=" + openElapsedMs());
    }
  }

  public synchronized void viewDraw(
      long durationNanos, int itemCount, int firstVisible, int lastVisible) {
    double durationMs = nanosToMs(durationNanos);
    if (!firstDrawLogged) {
      firstDrawLogged = true;
      log("first_view_draw", "drawMs=" + fmt(durationMs) + " openElapsedMs=" + openElapsedMs());
    }
    if (contentSubmitted && !firstContentDrawLogged && itemCount > 0) {
      firstContentDrawLogged = true;
      log(
          "first_content_draw",
          "drawMs=" + fmt(durationMs)
              + " count=" + itemCount
              + " visible=" + firstVisible + "-" + lastVisible
              + " openElapsedMs=" + openElapsedMs()
              + " " + memoryFields());
    }
    if (durationMs >= 8.0) {
      log(
          "view_draw_slow",
          "drawMs=" + fmt(durationMs)
              + " count=" + itemCount
              + " visible=" + firstVisible + "-" + lastVisible
              + " scrolling=" + scrolling);
    }
  }

  public void adapterSubmit(
      long durationNanos,
      int oldCount,
      int newCount,
      int inserted,
      int changed,
      int removed,
      int moved) {
    log(
        "adapter_submit",
        "durationMs=" + ms(durationNanos)
            + " old=" + oldCount
            + " new=" + newCount
            + " inserted=" + inserted
            + " changed=" + changed
            + " removed=" + removed
            + " moved=" + moved);
  }

  public void metricPrepared(long durationNanos, String messageId, boolean background) {
    if (durationNanos < 2_000_000L) return;
    log(
        "metric_slow",
        "durationMs=" + ms(durationNanos)
            + " messageId=" + safe(messageId)
            + " background=" + background);
  }

  public synchronized void rowBound(long durationNanos, int position, String messageId) {
    double durationMs = nanosToMs(durationNanos);
    if (scrolling) {
      scrollBindCount++;
      scrollBindTotalMs += durationMs;
      scrollMaxBindMs = Math.max(scrollMaxBindMs, durationMs);
    }
    // Avoid perturbing a fling with one log write per normal row bind. The scroll summary retains
    // aggregate bind cost, while individual events are reserved for unusually expensive binds.
    if (durationMs < 16.0) return;
    log(
        "bind_slow",
        "durationMs=" + fmt(durationMs)
            + " position=" + position
            + " messageId=" + safe(messageId));
  }

  public synchronized void rowBindSections(int position, String messageId, long setupNanos,
      long attachmentNanos, long replyNanos, long textNanos, long metadataNanos) {
    long total = setupNanos + attachmentNanos + replyNanos + textNanos + metadataNanos;
    if (scrolling) {
      scrollSetupMs += nanosToMs(setupNanos);
      scrollAttachmentMs += nanosToMs(attachmentNanos);
      scrollReplyMs += nanosToMs(replyNanos);
      scrollTextMs += nanosToMs(textNanos);
      scrollMetadataMs += nanosToMs(metadataNanos);
      if (total < 30_000_000L) return;
    } else if (total < 16_000_000L) return;
    log("bind_sections",
        "totalMs=" + ms(total)
            + " setupMs=" + ms(setupNanos)
            + " attachmentMs=" + ms(attachmentNanos)
            + " replyMs=" + ms(replyNanos)
            + " textMs=" + ms(textNanos)
            + " metadataMs=" + ms(metadataNanos)
            + " position=" + position
            + " messageId=" + safe(messageId));
  }

  public synchronized void rowReused(int position, String messageId) {
    if (scrolling) scrollReusedRows++;
  }

  public void operation(String name, long startedNanos, String fields) {
    logDuration(name, startedNanos, fields);
  }

  public synchronized void scrollStart(int firstVisible, int lastVisible, int itemCount) {
    if (scrolling) return;
    scrolling = true;
    scrollStartedNanos = SystemClock.elapsedRealtimeNanos();
    scrollFrames = 0;
    scrollSlowFrames = 0;
    scrollFrozenFrames = 0;
    scrollMaxFrameMs = 0.0;
    scrollBindCount = 0;
    scrollBindTotalMs = 0.0;
    scrollMaxBindMs = 0.0;
    scrollReusedRows = 0;
    scrollSetupMs = scrollAttachmentMs = scrollReplyMs = 0.0;
    scrollTextMs = scrollMetadataMs = 0.0;
    scrollFirstPosition = firstVisible;
    scrollLastPosition = lastVisible;
    scrollItemCount = itemCount;
    log(
        "scroll_start",
        "visible=" + firstVisible + "-" + lastVisible + " count=" + itemCount);
  }

  public synchronized void scrollProgress(int firstVisible, int lastVisible, int itemCount) {
    if (!scrolling) return;
    scrollFirstPosition = firstVisible;
    scrollLastPosition = lastVisible;
    scrollItemCount = itemCount;
  }

  public synchronized void scrollEnd(int firstVisible, int lastVisible, int itemCount) {
    if (!scrolling) return;
    scrollProgress(firstVisible, lastVisible, itemCount);
    scrolling = false;
    log(
        "scroll_end",
        "durationMs=" + ms(SystemClock.elapsedRealtimeNanos() - scrollStartedNanos)
            + " frames=" + scrollFrames
            + " slowFrames=" + scrollSlowFrames
            + " frozenFrames=" + scrollFrozenFrames
            + " maxFrameMs=" + fmt(scrollMaxFrameMs)
            + " binds=" + scrollBindCount
            + " bindTotalMs=" + fmt(scrollBindTotalMs)
            + " maxBindMs=" + fmt(scrollMaxBindMs)
            + " reusedRows=" + scrollReusedRows
            + " setupTotalMs=" + fmt(scrollSetupMs)
            + " attachmentTotalMs=" + fmt(scrollAttachmentMs)
            + " replyTotalMs=" + fmt(scrollReplyMs)
            + " textTotalMs=" + fmt(scrollTextMs)
            + " metadataTotalMs=" + fmt(scrollMetadataMs)
            + " visible=" + scrollFirstPosition + "-" + scrollLastPosition
            + " count=" + scrollItemCount
            + " " + memoryFields());
  }

  public synchronized void release() {
    if (scrolling) scrollEnd(scrollFirstPosition, scrollLastPosition, scrollItemCount);
    if (attachedWindow != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      attachedWindow.removeOnFrameMetricsAvailableListener(frameListener);
    }
    attachedWindow = null;
    if (frameThread != null) frameThread.quitSafely();
    frameThread = null;
    log("session_end", "openElapsedMs=" + openElapsedMs() + " " + memoryFields());
  }

  private synchronized void onFrame(FrameMetrics metrics, int droppedCallbacks) {
    double totalMs = metricMs(metrics, FrameMetrics.TOTAL_DURATION);
    if (scrolling) {
      scrollFrames++;
      scrollMaxFrameMs = Math.max(scrollMaxFrameMs, totalMs);
      if (totalMs >= SLOW_FRAME_MS) scrollSlowFrames++;
      if (totalMs >= FROZEN_FRAME_MS) scrollFrozenFrames++;
    }
    // Keep counting every slow frame, but avoid making profiling itself noisy during a fling.
    if (totalMs < LOG_FRAME_MS) return;
    log(
        totalMs >= FROZEN_FRAME_MS ? "frame_frozen" : "frame_slow",
        "totalMs=" + fmt(totalMs)
            + " inputMs=" + fmt(metricMs(metrics, FrameMetrics.INPUT_HANDLING_DURATION))
            + " animationMs=" + fmt(metricMs(metrics, FrameMetrics.ANIMATION_DURATION))
            + " layoutMs=" + fmt(metricMs(metrics, FrameMetrics.LAYOUT_MEASURE_DURATION))
            + " drawMs=" + fmt(metricMs(metrics, FrameMetrics.DRAW_DURATION))
            + " syncMs=" + fmt(metricMs(metrics, FrameMetrics.SYNC_DURATION))
            + " commandMs=" + fmt(metricMs(metrics, FrameMetrics.COMMAND_ISSUE_DURATION))
            + " swapMs=" + fmt(metricMs(metrics, FrameMetrics.SWAP_BUFFERS_DURATION))
            + " callbackDrops=" + droppedCallbacks
            + " scrolling=" + scrolling);
  }

  private void logDuration(String event, long startedNanos, String fields) {
    log(event, "durationMs=" + ms(SystemClock.elapsedRealtimeNanos() - startedNanos)
        + (fields == null || fields.isEmpty() ? "" : " " + fields));
  }

  private void log(String event, String fields) {
    Log.i(TAG, "event=" + event + " chatId=" + safe(chatId) + " " + fields);
  }

  private String openElapsedMs() {
    return ms(SystemClock.elapsedRealtimeNanos() - openRequestNanos);
  }

  private static double metricMs(FrameMetrics metrics, int metric) {
    long nanos = metrics.getMetric(metric);
    return nanos < 0L ? 0.0 : nanosToMs(nanos);
  }

  private static String memoryFields() {
    Runtime runtime = Runtime.getRuntime();
    long javaUsed = runtime.totalMemory() - runtime.freeMemory();
    return "javaHeapMb=" + (javaUsed / (1024L * 1024L))
        + " nativeHeapMb=" + (Debug.getNativeHeapAllocatedSize() / (1024L * 1024L))
        + " " + MediaPreviewCache.diagnostics();
  }

  private static String safe(String value) {
    if (value == null) return "";
    return value.replace(' ', '_').replace('\n', '_').replace('\r', '_');
  }

  private static String ms(long nanos) {
    return fmt(nanosToMs(nanos));
  }

  private static double nanosToMs(long nanos) {
    return nanos / 1_000_000.0;
  }

  private static String fmt(double value) {
    return String.format(Locale.US, "%.3f", value);
  }
}
