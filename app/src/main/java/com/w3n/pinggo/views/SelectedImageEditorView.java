package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-image freehand/text editor used by the selected-media preview. */
final class SelectedImageEditorView extends View {
  private static final int EDIT_COLOR = 0xFF22C56E;
  private final Map<String, State> states = new HashMap<>();
  private final Matrix displayMatrix = new Matrix();
  private final Matrix inverseMatrix = new Matrix();
  private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint textBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private State state;
  private boolean drawing;
  private boolean strokeInProgress;
  private TextItem draggingText;
  private float dragOffsetX;
  private float dragOffsetY;
  private Runnable historyChangedListener;

  SelectedImageEditorView(Context context) {
    super(context);
    setBackgroundColor(Color.BLACK);
    strokePaint.setColor(EDIT_COLOR);
    strokePaint.setStyle(Paint.Style.STROKE);
    strokePaint.setStrokeCap(Paint.Cap.ROUND);
    strokePaint.setStrokeJoin(Paint.Join.ROUND);
    textPaint.setColor(Color.BLACK);
    textPaint.setTextAlign(Paint.Align.CENTER);
    textBackgroundPaint.setColor(Color.WHITE);
  }

  void bind(String key, Bitmap bitmap) {
    State saved = states.get(key);
    if (saved == null) {
      saved = new State(bitmap);
      states.put(key, saved);
    } else if (saved.bitmap == null) {
      saved.bitmap = bitmap;
    }
    state = saved;
    rebuildMatrix();
    invalidate();
  }

  void clearActive() {
    state = null;
    invalidate();
  }

  void setDrawing(boolean enabled) {
    drawing = enabled;
  }

  boolean isDrawing() { return drawing; }

  void setHistoryChangedListener(Runnable listener) {
    historyChangedListener = listener;
  }

  boolean canUndoStroke() {
    return state != null && !state.strokes.isEmpty();
  }

  boolean undoLastStroke() {
    if (state == null || state.strokes.isEmpty()) return false;
    state.strokes.remove(state.strokes.size() - 1);
    notifyHistoryChanged();
    invalidate();
    return true;
  }

  boolean hasAnyEdits() {
    for (State saved : states.values()) {
      if (saved.edited || !saved.strokes.isEmpty() || !saved.texts.isEmpty()) return true;
    }
    return false;
  }

  void addText(String value) {
    if (state == null) return;
    String text = value == null ? "" : value.trim();
    if (text.isEmpty() || state.bitmap == null) return;
    state.texts.add(new TextItem(
        text, state.bitmap.getWidth() / 2f, state.bitmap.getHeight() / 2f));
    invalidate();
  }

  void rotateClockwise() {
    if (state == null || state.bitmap == null) return;
    Bitmap old = state.bitmap;
    Matrix rotation = new Matrix();
    rotation.postRotate(90f);
    Bitmap rotated = Bitmap.createBitmap(old, 0, 0, old.getWidth(), old.getHeight(), rotation, true);
    Matrix annotationRotation = new Matrix();
    annotationRotation.setRotate(90f);
    annotationRotation.postTranslate(old.getHeight(), 0f);
    for (Path path : state.strokes) path.transform(annotationRotation);
    for (TextItem text : state.texts) {
      float oldX = text.x;
      text.x = old.getHeight() - text.y;
      text.y = oldX;
    }
    state.bitmap = rotated;
    state.edited = true;
    rebuildMatrix();
    invalidate();
  }

  boolean hasEdits(String key) {
    State saved = states.get(key);
    return saved != null && (saved.edited || !saved.strokes.isEmpty() || !saved.texts.isEmpty());
  }

  Bitmap export(String key) {
    State saved = states.get(key);
    if (saved == null || saved.bitmap == null) return null;
    Bitmap output = Bitmap.createBitmap(
        saved.bitmap.getWidth(), saved.bitmap.getHeight(), Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(output);
    canvas.drawBitmap(saved.bitmap, 0f, 0f, imagePaint);
    drawAnnotations(canvas, saved);
    return output;
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    rebuildMatrix();
  }

  @Override protected void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);
    if (state == null || state.bitmap == null) return;
    canvas.drawBitmap(state.bitmap, displayMatrix, imagePaint);
    canvas.save();
    canvas.concat(displayMatrix);
    canvas.clipRect(0f, 0f, state.bitmap.getWidth(), state.bitmap.getHeight());
    drawAnnotations(canvas, state);
    canvas.restore();
  }

  private void drawAnnotations(Canvas canvas, State target) {
    if (target.bitmap == null) return;
    float unit = Math.max(target.bitmap.getWidth(), target.bitmap.getHeight());
    strokePaint.setStrokeWidth(unit * 0.007f);
    for (Path path : target.strokes) canvas.drawPath(path, strokePaint);
    textPaint.setTextSize(unit * 0.075f);
    for (TextItem text : target.texts) {
      RectF background = textBounds(text, target);
      float radius = unit * 0.018f;
      canvas.drawRoundRect(background, radius, radius, textBackgroundPaint);
      canvas.drawText(text.value, text.x, text.y, textPaint);
    }
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    if (state == null || state.bitmap == null) return false;
    float[] point = {event.getX(), event.getY()};
    inverseMatrix.mapPoints(point);
    float x = point[0], y = point[1];
    if (drawing) return drawTouch(event, x, y);
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
      draggingText = findTextAt(x, y);
      if (draggingText != null) {
        dragOffsetX = draggingText.x - x;
        dragOffsetY = draggingText.y - y;
        return true;
      }
    }
    if (event.getActionMasked() == MotionEvent.ACTION_MOVE && draggingText != null) {
      draggingText.x = clamp(x + dragOffsetX, 0f, state.bitmap.getWidth());
      draggingText.y = clamp(y + dragOffsetY, 0f, state.bitmap.getHeight());
      invalidate();
      return true;
    }
    if (event.getActionMasked() == MotionEvent.ACTION_UP
        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
      draggingText = null;
    }
    return draggingText != null;
  }

  private boolean drawTouch(MotionEvent event, float x, float y) {
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
      if (!insideBitmap(x, y)) return false;
      Path path = new Path();
      path.moveTo(x, y);
      state.strokes.add(path);
      strokeInProgress = true;
      notifyHistoryChanged();
    } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && strokeInProgress) {
      state.strokes.get(state.strokes.size() - 1).lineTo(
          clamp(x, 0f, state.bitmap.getWidth()),
          clamp(y, 0f, state.bitmap.getHeight()));
    } else if (event.getActionMasked() == MotionEvent.ACTION_UP
        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
      strokeInProgress = false;
    }
    invalidate();
    return true;
  }

  private TextItem findTextAt(float x, float y) {
    if (state == null || state.bitmap == null) return null;
    textPaint.setTextSize(Math.max(state.bitmap.getWidth(), state.bitmap.getHeight()) * 0.075f);
    for (int index = state.texts.size() - 1; index >= 0; index--) {
      TextItem text = state.texts.get(index);
      if (textBounds(text, state).contains(x, y)) return text;
    }
    return null;
  }

  private RectF textBounds(TextItem text, State target) {
    float unit = Math.max(target.bitmap.getWidth(), target.bitmap.getHeight());
    float horizontalPadding = unit * 0.025f;
    float verticalPadding = unit * 0.014f;
    Paint.FontMetrics metrics = textPaint.getFontMetrics();
    float halfWidth = textPaint.measureText(text.value) / 2f;
    return new RectF(text.x - halfWidth - horizontalPadding,
        text.y + metrics.top - verticalPadding,
        text.x + halfWidth + horizontalPadding,
        text.y + metrics.bottom + verticalPadding);
  }

  private boolean insideBitmap(float x, float y) {
    return state != null && state.bitmap != null && x >= 0f && y >= 0f
        && x <= state.bitmap.getWidth() && y <= state.bitmap.getHeight();
  }

  private void notifyHistoryChanged() {
    if (historyChangedListener != null) historyChangedListener.run();
  }

  private void rebuildMatrix() {
    displayMatrix.reset();
    if (state == null || state.bitmap == null || getWidth() <= 0 || getHeight() <= 0) return;
    displayMatrix.setRectToRect(
        new RectF(0f, 0f, state.bitmap.getWidth(), state.bitmap.getHeight()),
        new RectF(0f, 0f, getWidth(), getHeight()), Matrix.ScaleToFit.CENTER);
    displayMatrix.invert(inverseMatrix);
  }

  void release() {
    states.clear();
    state = null;
  }

  private static float clamp(float value, float minimum, float maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private static final class State {
    Bitmap bitmap;
    final List<Path> strokes = new ArrayList<>();
    final List<TextItem> texts = new ArrayList<>();
    boolean edited;

    State(Bitmap bitmap) { this.bitmap = bitmap; }
  }

  private static final class TextItem {
    final String value;
    float x;
    float y;

    TextItem(String value, float x, float y) {
      this.value = value;
      this.x = x;
      this.y = y;
    }
  }
}
