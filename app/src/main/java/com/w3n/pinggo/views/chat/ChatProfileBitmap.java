package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import java.util.Locale;

/** Creates the circular chat profile bitmap without exposing bitmap preparation to ChatView. */
final class ChatProfileBitmap {
  private ChatProfileBitmap() {}

  static Bitmap load(Context context, String photoPath, String name, int fallbackSize, int accent) {
    Bitmap decoded = photoPath == null || photoPath.trim().isEmpty()
        ? null : BitmapFactory.decodeFile(photoPath);
    if (decoded == null) return avatar(name, fallbackSize, accent);
    Bitmap profile = circleCrop(decoded);
    decoded.recycle();
    return profile;
  }

  private static Bitmap avatar(String value, int size, int accent) {
    int resolvedSize = Math.max(1, size);
    Bitmap bitmap = Bitmap.createBitmap(resolvedSize, resolvedSize, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(0xFFD9F1F7);
    canvas.drawCircle(resolvedSize / 2f, resolvedSize / 2f, resolvedSize / 2f, paint);
    String initial = value == null || value.isEmpty()
        ? "?" : value.substring(0, 1).toUpperCase(Locale.US);
    paint.setColor(accent);
    paint.setTextSize(resolvedSize * .42f);
    paint.setTextAlign(Paint.Align.CENTER);
    Paint.FontMetrics metrics = paint.getFontMetrics();
    canvas.drawText(
        initial,
        resolvedSize / 2f,
        resolvedSize / 2f - (metrics.ascent + metrics.descent) / 2f,
        paint);
    return bitmap;
  }

  private static Bitmap circleCrop(Bitmap source) {
    int size = Math.min(source.getWidth(), source.getHeight());
    Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(result);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    float scale = Math.max(size / (float) source.getWidth(), size / (float) source.getHeight());
    android.graphics.Matrix matrix = new android.graphics.Matrix();
    matrix.setScale(scale, scale);
    matrix.postTranslate(
        (size - source.getWidth() * scale) / 2f,
        (size - source.getHeight() * scale) / 2f);
    shader.setLocalMatrix(matrix);
    paint.setShader(shader);
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
    return result;
  }
}
