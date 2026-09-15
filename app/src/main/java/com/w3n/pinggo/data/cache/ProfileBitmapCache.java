package com.w3n.pinggo.data.cache;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Process-wide, size-aware cache for profile photos and generated fallback avatars. */
public final class ProfileBitmapCache {
  private static final ProfileBitmapCache INSTANCE = new ProfileBitmapCache();
  private final LruCache<String, Bitmap> cache;
  private final ExecutorService decoder = Executors.newFixedThreadPool(2);
  private final Handler main = new Handler(Looper.getMainLooper());
  private final Map<String, List<Runnable>> waiting = new HashMap<>();

  private ProfileBitmapCache() {
    int maxKb = Math.max(4 * 1024, (int) (Runtime.getRuntime().maxMemory() / 1024L / 16L));
    cache = new LruCache<String, Bitmap>(maxKb) {
      @Override protected int sizeOf(String key, Bitmap value) {
        return Math.max(1, value.getAllocationByteCount() / 1024);
      }
    };
  }

  public static ProfileBitmapCache get() { return INSTANCE; }

  /**
   * Returns a cache-owned bitmap and invokes onLoaded on the main thread after an async decode.
   * Callers may retain the bitmap while visible but must never call {@link Bitmap#recycle()}.
   */
  public Bitmap request(String path, String name, int size, int accent, Runnable onLoaded) {
    int target = Math.max(1, size);
    String photoKey = "photo|" + (path == null ? "" : path) + '|' + target;
    Bitmap hit = cache.get(photoKey);
    if (usable(hit)) return hit;
    Bitmap fallback = fallback(name, target, accent);
    if (path == null || path.trim().isEmpty()) return fallback;
    synchronized (waiting) {
      List<Runnable> callbacks = waiting.get(photoKey);
      if (callbacks != null) {
        // Recycled rows may bind repeatedly while one decode is running. Bound
        // callback growth so a slow disk read cannot retain an entire screen.
        if (onLoaded != null && callbacks.size() < 32) callbacks.add(onLoaded);
        return fallback;
      }
      callbacks = new ArrayList<>();
      if (onLoaded != null) callbacks.add(onLoaded);
      waiting.put(photoKey, callbacks);
    }
    decoder.execute(() -> {
      Bitmap decoded = decodeScaled(path, target);
      if (decoded != null) cache.put(photoKey, decoded);
      List<Runnable> callbacks;
      synchronized (waiting) { callbacks = waiting.remove(photoKey); }
      if (decoded != null && callbacks != null)
        main.post(() -> { for (Runnable callback : callbacks) callback.run(); });
    });
    return fallback;
  }

  private Bitmap fallback(String name, int size, int accent) {
    String initial = name == null || name.trim().isEmpty()
        ? "?" : name.trim().substring(0, 1).toUpperCase(Locale.US);
    String key = "fallback|" + initial + '|' + size + '|' + accent;
    Bitmap hit = cache.get(key);
    if (usable(hit)) return hit;
    Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(0xFFD9F1F7);
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
    paint.setColor(accent);
    paint.setTextSize(size * .42f);
    paint.setTextAlign(Paint.Align.CENTER);
    Paint.FontMetrics metrics = paint.getFontMetrics();
    canvas.drawText(initial, size / 2f,
        size / 2f - (metrics.ascent + metrics.descent) / 2f, paint);
    cache.put(key, bitmap);
    return bitmap;
  }

  private static Bitmap decodeScaled(String path, int target) {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(path, bounds);
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
    int shortest = Math.min(bounds.outWidth, bounds.outHeight);
    int sample = 1;
    while (shortest / (sample * 2) >= target) sample *= 2;
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = sample;
    Bitmap source = BitmapFactory.decodeFile(path, options);
    if (source == null) return null;
    Bitmap result = circleCrop(source, target);
    if (source != result && !source.isRecycled()) source.recycle();
    return result;
  }

  private static Bitmap circleCrop(Bitmap source, int size) {
    Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(output);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    float scale = Math.max(size / (float) source.getWidth(), size / (float) source.getHeight());
    Matrix matrix = new Matrix();
    matrix.setScale(scale, scale);
    matrix.postTranslate((size - source.getWidth() * scale) / 2f,
        (size - source.getHeight() * scale) / 2f);
    shader.setLocalMatrix(matrix);
    paint.setShader(shader);
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
    return output;
  }

  private static boolean usable(Bitmap bitmap) {
    return bitmap != null && !bitmap.isRecycled();
  }
}
