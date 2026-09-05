package com.w3n.pinggo.data.cache;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.util.LruCache;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Shared memory/disk media cache used by chat rows and full-screen preview activities. */
public final class MediaPreviewCache {
  public static final String TYPE_IMAGE = "image";
  public static final String TYPE_VIDEO = "video";
  public static final String TYPE_FILE = "file";
  public interface Callback<T> {
    void onSuccess(T value);
    void onError();
    default void onProgress(long downloadedBytes, long totalBytes) { }
  }

  public static final class Thumbnail {
    public final Bitmap bitmap;
    public final String duration;
    public final boolean portrait;

    Thumbnail(Bitmap bitmap, String duration) {
      this(bitmap, duration, bitmap != null && bitmap.getHeight() > bitmap.getWidth());
    }

    Thumbnail(Bitmap bitmap, String duration, boolean portrait) {
      this.bitmap = bitmap;
      this.duration = duration == null ? "" : duration;
      this.portrait = portrait;
    }
  }

  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static final ExecutorService IO = Executors.newFixedThreadPool(3);
  private static final ConcurrentLinkedQueue<Runnable> DEFERRED_DECODES =
      new ConcurrentLinkedQueue<>();
  private static volatile boolean decodingPaused;
  private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, Boolean> ORIENTATIONS =
      new ConcurrentHashMap<>();
  // Keep chat thumbnails bounded independently of large application heap limits.
  private static final int MEMORY_KB = Math.min(48 * 1024, Math.max(8 * 1024,
      (int) (Runtime.getRuntime().maxMemory() / 1024L / 16L)));
  private static final LruCache<String, Thumbnail> MEMORY =
      new LruCache<String, Thumbnail>(MEMORY_KB) {
        @Override protected int sizeOf(String key, Thumbnail value) {
          return Math.max(1, value.bitmap.getAllocationByteCount() / 1024);
        }
      };

  private MediaPreviewCache() { }

  /** Defers new thumbnail decoding while a chat list is actively flinging. */
  public static void setDecodingPaused(boolean paused) {
    decodingPaused = paused;
    if (paused) return;
    Runnable task;
    while ((task = DEFERRED_DECODES.poll()) != null) IO.execute(task);
  }

  public static boolean isDecodingPaused() { return decodingPaused; }

  /** Lightweight counters for separating thumbnail memory from other native allocations. */
  public static String diagnostics() {
    return "thumbnailCacheKb=" + MEMORY.size()
        + " thumbnailEntries=" + MEMORY.snapshot().size()
        + " thumbnailEvictions=" + MEMORY.evictionCount()
        + " deferredDecodes=" + DEFERRED_DECODES.size()
        + " mediaLocks=" + LOCKS.size();
  }

  private static void executeDecode(Runnable task) {
    if (decodingPaused) DEFERRED_DECODES.offer(task);
    else IO.execute(task);
  }

  public static Boolean cachedPortrait(String source, boolean video) {
    return ORIENTATIONS.get(orientationKey(source, video));
  }

  /** Blocking metadata-only preparation. Call from a background thread before row measurement. */
  public static Boolean prepareOrientation(Context context, String source, boolean video) {
    if (source == null || source.trim().isEmpty()) return null;
    String key = orientationKey(source, video);
    Boolean cached = ORIENTATIONS.get(key);
    if (cached != null) return cached;
    try {
      Uri local = resolveMediaBlocking(
          context.getApplicationContext(), source, video ? TYPE_VIDEO : TYPE_IMAGE);
      boolean portrait;
      if (video) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
          retriever.setDataSource(context, local);
          int width = metadataInt(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
          int height = metadataInt(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
          int rotation = metadataInt(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
          if (rotation == 90 || rotation == 270) {
            int swap = width; width = height; height = swap;
          }
          if (width <= 0 || height <= 0) return null;
          portrait = height > width;
        } finally {
          retriever.release();
        }
      } else {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(local)) {
          BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        portrait = bounds.outHeight > bounds.outWidth;
      }
      ORIENTATIONS.put(key, portrait);
      return portrait;
    } catch (Exception error) {
      return null;
    }
  }

  public static Thumbnail memoryThumbnail(
      String source, boolean video, int targetWidth, int targetHeight) {
    return MEMORY.get(thumbnailKey(source, video, targetWidth, targetHeight));
  }

  public static Thumbnail anyMemoryThumbnail(String source, boolean video) {
    String prefix = String.valueOf(source) + '|' + video + '|';
    for (java.util.Map.Entry<String, Thumbnail> entry : MEMORY.snapshot().entrySet()) {
      if (entry.getKey().startsWith(prefix)) return entry.getValue();
    }
    return null;
  }

  /** Returns true only when the media can be opened without another network request. */
  public static boolean isMediaReady(Context context, String source, String mediaType) {
    if (source == null || source.trim().isEmpty()) return false;
    try {
      Uri uri = Uri.parse(source);
      String scheme = uri.getScheme();
      if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
        File stored = new File(
            persistentDirectory(mediaType), hash(source) + extension(source, mediaType));
        return stored.isFile() && stored.length() > 0;
      }
      try (AssetFileDescriptor descriptor =
               context.getContentResolver().openAssetFileDescriptor(uri, "r")) {
        return descriptor != null;
      }
    } catch (Exception error) {
      return false;
    }
  }

  public static void loadImageForDisplay(
      Context context, String source, int targetWidth, int targetHeight, Callback<Bitmap> callback) {
    String key = thumbnailKey(source, false, targetWidth, targetHeight);
    Thumbnail memory = MEMORY.get(key);
    if (memory != null) {
      MAIN.post(() -> callback.onSuccess(memory.bitmap));
      return;
    }
    Context app = context.getApplicationContext();
    executeDecode(() -> {
      try {
        Uri local = resolveMediaBlocking(app, source, TYPE_IMAGE);
        Bitmap bitmap = decodeSampled(app, local, targetWidth, targetHeight);
        if (bitmap == null) throw new IllegalStateException();
        MEMORY.put(key, new Thumbnail(bitmap, ""));
        MAIN.post(() -> callback.onSuccess(bitmap));
      } catch (Exception error) {
        MAIN.post(callback::onError);
      }
    });
  }

  public static void loadThumbnail(
      Context context,
      String source,
      boolean video,
      int targetWidth,
      int targetHeight,
      Callback<Thumbnail> callback) {
    int width = bucket(targetWidth);
    int height = bucket(targetHeight);
    String memoryKey = thumbnailKey(source, video, width, height);
    Thumbnail cached = MEMORY.get(memoryKey);
    if (cached != null) {
      ORIENTATIONS.put(orientationKey(source, video), cached.portrait);
      MAIN.post(() -> callback.onSuccess(cached));
      return;
    }
    Context app = context.getApplicationContext();
    executeDecode(() -> {
      try {
        Object lock = LOCKS.computeIfAbsent(memoryKey, ignored -> new Object());
        Thumbnail result;
        synchronized (lock) {
          result = MEMORY.get(memoryKey);
          if (result == null) {
            result = createOrReadThumbnail(app, source, video, width, height, callback);
            result.bitmap.prepareToDraw();
            MEMORY.put(memoryKey, result);
          }
        }
        ORIENTATIONS.put(orientationKey(source, video), result.portrait);
        LOCKS.remove(memoryKey, lock);
        Thumbnail delivered = result;
        MAIN.post(() -> callback.onSuccess(delivered));
      } catch (Exception error) {
        MAIN.post(callback::onError);
      }
    });
  }

  /** Resolves a remote URL once to disk; local content/file URIs pass through unchanged. */
  public static void resolveMedia(Context context, String source, Callback<Uri> callback) {
    Context app = context.getApplicationContext();
    IO.execute(() -> {
      try {
        Uri result = resolveMediaBlocking(app, source, TYPE_FILE);
        MAIN.post(() -> callback.onSuccess(result));
      } catch (Exception error) {
        MAIN.post(callback::onError);
      }
    });
  }

  public static void resolveMedia(
      Context context, String source, String mediaType, Callback<Uri> callback) {
    Context app = context.getApplicationContext();
    IO.execute(() -> {
      try {
        Uri result = resolveMediaBlocking(app, source, mediaType);
        MAIN.post(() -> callback.onSuccess(result));
      } catch (Exception error) {
        MAIN.post(callback::onError);
      }
    });
  }

  private static Thumbnail createOrReadThumbnail(
      Context context, String source, boolean video, int width, int height,
      Callback<?> callback) throws Exception {
    if (source == null || source.trim().isEmpty()) throw new IllegalArgumentException();
    File directory = cacheDirectory(context);
    String key = hash(source + "|first-frame-v2|" + video + "|" + width + "x" + height);
    File thumbnailFile = new File(directory, "thumb_" + key + ".jpg");
    File durationFile = new File(directory, "duration_" + key + ".txt");
    if (thumbnailFile.isFile() && thumbnailFile.length() > 0) {
      Bitmap diskBitmap = decodeSampled(thumbnailFile, width, height);
      if (diskBitmap != null) {
        String duration = durationFile.isFile() ? readText(durationFile) : "";
        return new Thumbnail(diskBitmap, duration);
      }
    }

    Uri local = resolveMediaBlocking(
        context, source, video ? TYPE_VIDEO : TYPE_IMAGE, callback);
    Bitmap bitmap;
    String duration = "";
    boolean portrait;
    if (video) {
      MediaMetadataRetriever retriever = new MediaMetadataRetriever();
      try {
        retriever.setDataSource(context, local);
        int sourceWidth = metadataInt(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        int sourceHeight = metadataInt(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        int rotation = metadataInt(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        if (rotation == 90 || rotation == 270) {
          int swap = sourceWidth;
          sourceWidth = sourceHeight;
          sourceHeight = swap;
        }
        Bitmap frame;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
          try {
            float requestedScale = sourceWidth > 0 && sourceHeight > 0
                ? Math.min(width / (float) sourceWidth, height / (float) sourceHeight) : 1f;
            int decodedWidth = sourceWidth > 0
                ? Math.max(1, Math.round(sourceWidth * Math.min(1f, requestedScale))) : width;
            int decodedHeight = sourceHeight > 0
                ? Math.max(1, Math.round(sourceHeight * Math.min(1f, requestedScale))) : height;
            frame = retriever.getScaledFrameAtTime(
                0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                decodedWidth, decodedHeight);
          } catch (RuntimeException unsupportedScaledFrame) {
            frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST);
          }
        } else {
          frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST);
        }
        if (frame == null) {
          frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_NEXT_SYNC);
        }
        if (frame == null) throw new IllegalStateException();
        portrait = sourceWidth > 0 && sourceHeight > 0
            ? sourceHeight > sourceWidth : frame.getHeight() > frame.getWidth();
        bitmap = scaleDown(frame, width, height);
        if (bitmap != frame) frame.recycle();
        String milliseconds = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION);
        if (milliseconds != null) duration = formatDuration(Long.parseLong(milliseconds));
      } finally {
        retriever.release();
      }
    } else {
      Bitmap decoded = decodeSampled(context, local, width, height);
      if (decoded == null) throw new IllegalStateException();
      portrait = decoded.getHeight() > decoded.getWidth();
      bitmap = scaleDown(decoded, width, height);
      if (bitmap != decoded) decoded.recycle();
    }
    try (FileOutputStream output = new FileOutputStream(thumbnailFile)) {
      bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output);
    }
    if (!duration.isEmpty()) {
      try (FileOutputStream output = new FileOutputStream(durationFile)) {
        output.write(duration.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      }
    }
    ORIENTATIONS.put(orientationKey(source, video), portrait);
    return new Thumbnail(bitmap, duration, portrait);
  }

  private static Uri resolveMediaBlocking(
      Context context, String source, String mediaType) throws Exception {
    return resolveMediaBlocking(context, source, mediaType, null);
  }

  private static Uri resolveMediaBlocking(
      Context context, String source, String mediaType, Callback<?> callback) throws Exception {
    if (source == null || source.trim().isEmpty()) throw new IllegalArgumentException();
    Uri uri = Uri.parse(source);
    String scheme = uri.getScheme();
    if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) return uri;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
        && !Environment.isExternalStorageManager()) {
      throw new SecurityException("All files access is required");
    }
    File storage = persistentDirectory(mediaType);
    if (!storage.exists() && !storage.mkdirs()) {
      throw new java.io.IOException("Unable to create PingGo media folder");
    }
    File destination = new File(storage, hash(source) + extension(source, mediaType));
    if (destination.isFile() && destination.length() > 0) return Uri.fromFile(destination);
    String lockKey = "original|" + source;
    Object lock = LOCKS.computeIfAbsent(lockKey, ignored -> new Object());
    synchronized (lock) {
      if (!destination.isFile() || destination.length() == 0) {
        File temporary = new File(destination.getParentFile(), destination.getName() + ".part");
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        long totalBytes = connection.getContentLengthLong();
        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(temporary)) {
          byte[] buffer = new byte[32 * 1024];
          int read;
          long downloadedBytes = 0L;
          long lastReportedBytes = 0L;
          long lastReportedAt = android.os.SystemClock.elapsedRealtime();
          while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
            downloadedBytes += read;
            long now = android.os.SystemClock.elapsedRealtime();
            if (callback != null && ((downloadedBytes - lastReportedBytes >= 64L * 1024L
                && now - lastReportedAt >= 80L)
                || (totalBytes > 0L && downloadedBytes >= totalBytes))) {
              lastReportedBytes = downloadedBytes;
              lastReportedAt = now;
              long deliveredBytes = downloadedBytes;
              MAIN.post(() -> callback.onProgress(deliveredBytes, totalBytes));
            }
          }
          if (callback != null && downloadedBytes != lastReportedBytes) {
            long deliveredBytes = downloadedBytes;
            MAIN.post(() -> callback.onProgress(deliveredBytes, totalBytes));
          }
        } finally {
          connection.disconnect();
        }
        if (!temporary.renameTo(destination)) {
          copy(temporary, destination);
          temporary.delete();
        }
      }
    }
    LOCKS.remove(lockKey, lock);
    return Uri.fromFile(destination);
  }

  private static Bitmap decodeSampled(Context context, Uri uri, int width, int height)
      throws Exception {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    try (InputStream input = context.getContentResolver().openInputStream(uri)) {
      BitmapFactory.decodeStream(input, null, bounds);
    }
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, width, height);
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    try (InputStream input = context.getContentResolver().openInputStream(uri)) {
      Bitmap decoded = BitmapFactory.decodeStream(input, null, options);
      if (decoded == null) return null;
      Bitmap result = scaleDown(decoded, width, height);
      if (result != decoded) decoded.recycle();
      return result;
    }
  }

  private static Bitmap decodeSampled(File file, int width, int height) throws Exception {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, width, height);
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    if (decoded == null) return null;
    Bitmap result = scaleDown(decoded, width, height);
    if (result != decoded) decoded.recycle();
    return result;
  }

  private static int sampleSize(int sourceWidth, int sourceHeight, int width, int height) {
    int sample = 1;
    while (sourceWidth / (sample * 2) >= width && sourceHeight / (sample * 2) >= height) {
      sample *= 2;
    }
    return Math.max(1, sample);
  }

  private static Bitmap scaleDown(Bitmap source, int width, int height) {
    float scale = Math.min(1f, Math.min(width / (float) source.getWidth(),
        height / (float) source.getHeight()));
    if (scale >= 1f) return source;
    return Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth() * scale)),
        Math.max(1, Math.round(source.getHeight() * scale)), true);
  }

  private static File cacheDirectory(Context context) {
    File directory = new File(context.getCacheDir(), "chat_media");
    if (!directory.exists()) directory.mkdirs();
    return directory;
  }

  @SuppressWarnings("deprecation")
  private static File persistentDirectory(String mediaType) {
    String child = TYPE_VIDEO.equals(mediaType)
        ? "Videos" : TYPE_FILE.equals(mediaType) ? "Files" : "Images";
    return new File(new File(Environment.getExternalStorageDirectory(), "PingGo"), child);
  }

  private static String extension(String source, String mediaType) {
    try {
      String path = Uri.parse(source).getPath();
      if (path != null) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot > slash && path.length() - dot <= 8) {
          String extension = path.substring(dot).toLowerCase(Locale.US);
          if (extension.matches("\\.[a-z0-9]{1,7}")) return extension;
        }
      }
    } catch (RuntimeException ignored) { }
    return TYPE_VIDEO.equals(mediaType) ? ".mp4"
        : TYPE_IMAGE.equals(mediaType) ? ".jpg" : ".bin";
  }

  private static void copy(File source, File destination) throws Exception {
    try (FileInputStream input = new FileInputStream(source);
         FileOutputStream output = new FileOutputStream(destination)) {
      byte[] buffer = new byte[32 * 1024];
      int read;
      while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
    }
  }

  private static String readText(File file) throws Exception {
    byte[] bytes = new byte[(int) Math.min(128, file.length())];
    int count;
    try (FileInputStream input = new FileInputStream(file)) { count = input.read(bytes); }
    return count <= 0 ? "" : new String(bytes, 0, count,
        java.nio.charset.StandardCharsets.UTF_8);
  }

  private static int bucket(int value) {
    return Math.max(64, ((Math.max(1, value) + 63) / 64) * 64);
  }

  private static String thumbnailKey(String source, boolean video, int width, int height) {
    return String.valueOf(source) + '|' + (video ? "first-frame-v2" : "image-v1") + '|'
        + bucket(width) + 'x' + bucket(height);
  }

  private static String orientationKey(String source, boolean video) {
    return String.valueOf(source) + '|' + (video ? "video" : "image");
  }

  private static String formatDuration(long milliseconds) {
    long seconds = Math.max(0, milliseconds / 1000L);
    return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
  }

  private static int metadataInt(MediaMetadataRetriever retriever, int key) {
    try {
      String value = retriever.extractMetadata(key);
      return value == null ? 0 : Integer.parseInt(value);
    } catch (RuntimeException error) {
      return 0;
    }
  }

  private static String hash(String value) throws Exception {
    byte[] digest = MessageDigest.getInstance("SHA-256")
        .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    StringBuilder result = new StringBuilder(digest.length * 2);
    for (byte item : digest) result.append(String.format(Locale.US, "%02x", item & 0xff));
    return result.toString();
  }
}
