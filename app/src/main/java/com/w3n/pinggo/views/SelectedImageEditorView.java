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
  interface TextEditListener { void onTextEditRequested(String value, int color); }
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
  private TextItem scalingText;
  private TextItem editingText;
  private float dragOffsetX;
  private float dragOffsetY;
  private float pinchStartDistance;
  private float pinchStartScale;
  private float textTouchStartX;
  private float textTouchStartY;
  private boolean textMoved;
  private Runnable historyChangedListener;
  private TextEditListener textEditListener;
  private int drawingColor = EDIT_COLOR;
  private int textColor = Color.WHITE;

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

  void setDrawingColor(int color) { drawingColor = color; }

  void setTextColor(int color) { textColor = color; }

  void setTextEditListener(TextEditListener listener) { textEditListener = listener; }

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
        text, state.bitmap.getWidth() / 2f, state.bitmap.getHeight() / 2f, textColor));
    invalidate();
  }

  boolean commitEditedText(String value, int color) {
    if (editingText == null) return false;
    String normalized = value == null ? "" : value.trim();
    if (!normalized.isEmpty()) {
      editingText.value = normalized;
      editingText.color = color;
    }
    editingText = null;
    invalidate();
    return true;
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
    for (Stroke stroke : state.strokes) stroke.path.transform(annotationRotation);
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

  Bitmap activeExport() {
    if (state == null) return null;
    for (Map.Entry<String, State> entry : states.entrySet()) {
      if (entry.getValue() == state) return export(entry.getKey());
    }
    return null;
  }

  void replaceActiveBitmap(Bitmap bitmap) {
    if (state == null || bitmap == null) return;
    state.bitmap = bitmap;
    state.strokes.clear();
    state.texts.clear();
    state.edited = true;
    rebuildMatrix();
    notifyHistoryChanged();
    invalidate();
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
    for (Stroke stroke : target.strokes) {
      strokePaint.setColor(stroke.color);
      canvas.drawPath(stroke.path, strokePaint);
    }
    for (TextItem text : target.texts) {
      textPaint.setTextSize(unit * 0.075f * text.scale * lineScale(text.value));
      textPaint.setColor(text.color);
      textBackgroundPaint.setColor(contrastBackground(text.color));
      String[] lines = text.value.split("\\n", -1);
      Paint.FontMetrics metrics = textPaint.getFontMetrics();
      float lineHeight = metrics.descent - metrics.ascent;
      float baseline = text.y - (lines.length - 1) * lineHeight / 2f;
      float backgroundRadius = textPaint.getTextSize() * .16f;
      // Paint every opaque background first. Otherwise the following line's background can
      // cover the descenders of the line above it.
      for (String line : lines) {
        float halfWidth = textPaint.measureText(line) / 2f;
        float horizontalPadding = unit * .015f;
        float verticalPadding = unit * .004f;
        RectF lineBackground = new RectF(
            text.x - halfWidth - horizontalPadding,
            baseline + metrics.ascent - verticalPadding,
            text.x + halfWidth + horizontalPadding,
            baseline + metrics.descent + verticalPadding);
        canvas.drawRoundRect(
            lineBackground, backgroundRadius, backgroundRadius, textBackgroundPaint);
        baseline += lineHeight;
      }
      baseline = text.y - (lines.length - 1) * lineHeight / 2f;
      for (String line : lines) {
        canvas.drawText(line, text.x, baseline, textPaint);
        baseline += lineHeight;
      }
    }
  }

  @Override public boolean onTouchEvent(MotionEvent event) {
    if (state == null || state.bitmap == null) return false;
    float[] point = {event.getX(), event.getY()};
    inverseMatrix.mapPoints(point);
    float x = point[0], y = point[1];
    if (drawing) return drawTouch(event, x, y);
    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
      float[] midpoint = {(event.getX(0) + event.getX(1)) / 2f,
          (event.getY(0) + event.getY(1)) / 2f};
      inverseMatrix.mapPoints(midpoint);
      scalingText = findTextAt(midpoint[0], midpoint[1]);
      if (scalingText != null) {
        pinchStartDistance = pointerDistance(event);
        pinchStartScale = scalingText.scale;
        draggingText = null;
        return true;
      }
    }
    if (action == MotionEvent.ACTION_MOVE && scalingText != null && event.getPointerCount() >= 2) {
      float distance = pointerDistance(event);
      if (pinchStartDistance > 0f) {
        scalingText.scale = clamp(pinchStartScale * distance / pinchStartDistance,
            minimumTextScale(scalingText), 5f);
        invalidate();
      }
      return true;
    }
    if (action == MotionEvent.ACTION_DOWN) {
      draggingText = findTextAt(x, y);
      if (draggingText != null) {
        dragOffsetX = draggingText.x - x;
        dragOffsetY = draggingText.y - y;
        textTouchStartX = x;
        textTouchStartY = y;
        textMoved = false;
        return true;
      }
    }
    if (action == MotionEvent.ACTION_MOVE && draggingText != null) {
      float threshold = 8f / Math.max(.001f, displayScale());
      if (Math.hypot(x - textTouchStartX, y - textTouchStartY) > threshold) textMoved = true;
      draggingText.x = clamp(x + dragOffsetX, 0f, state.bitmap.getWidth());
      draggingText.y = clamp(y + dragOffsetY, 0f, state.bitmap.getHeight());
      invalidate();
      return true;
    }
    boolean handled = draggingText != null || scalingText != null;
    if (action == MotionEvent.ACTION_UP && draggingText != null && !textMoved) {
      editingText = draggingText;
      if (textEditListener != null) {
        textEditListener.onTextEditRequested(editingText.value, editingText.color);
      }
    }
    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      draggingText = null;
      scalingText = null;
    }
    return handled;
  }

  private boolean drawTouch(MotionEvent event, float x, float y) {
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
      if (!insideBitmap(x, y)) return false;
      Path path = new Path();
      path.moveTo(x, y);
      state.strokes.add(new Stroke(path, drawingColor));
      strokeInProgress = true;
      notifyHistoryChanged();
    } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && strokeInProgress) {
      state.strokes.get(state.strokes.size() - 1).path.lineTo(
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
    for (int index = state.texts.size() - 1; index >= 0; index--) {
      TextItem text = state.texts.get(index);
      textPaint.setTextSize(Math.max(state.bitmap.getWidth(), state.bitmap.getHeight())
          * 0.075f * text.scale * lineScale(text.value));
      if (textBounds(text, state).contains(x, y)) return text;
    }
    return null;
  }

  private RectF textBounds(TextItem text, State target) {
    float unit = Math.max(target.bitmap.getWidth(), target.bitmap.getHeight());
    float horizontalPadding = unit * 0.015f;
    float verticalPadding = unit * 0.014f;
    Paint.FontMetrics metrics = textPaint.getFontMetrics();
    String[] lines = text.value.split("\\n", -1);
    float halfWidth = 0f;
    for (String line : lines) halfWidth = Math.max(halfWidth, textPaint.measureText(line) / 2f);
    float lineHeight = metrics.descent - metrics.ascent;
    float firstBaseline = text.y - (lines.length - 1) * lineHeight / 2f;
    return new RectF(text.x - halfWidth - horizontalPadding,
        firstBaseline + metrics.top - verticalPadding,
        text.x + halfWidth + horizontalPadding,
        firstBaseline + metrics.bottom + (lines.length - 1) * lineHeight + verticalPadding);
  }

  private static float pointerDistance(MotionEvent event) {
    if (event.getPointerCount() < 2) return 0f;
    return (float) Math.hypot(event.getX(1) - event.getX(0),
        event.getY(1) - event.getY(0));
  }

  private float displayScale() {
    float[] values = new float[9];
    displayMatrix.getValues(values);
    return Math.max(Math.abs(values[Matrix.MSCALE_X]), Math.abs(values[Matrix.MSCALE_Y]));
  }

  private static int contrastBackground(int foreground) {
    double luminance = (Color.red(foreground) * .299
        + Color.green(foreground) * .587 + Color.blue(foreground) * .114) / 255d;
    return luminance > .58d ? 0xFF252A30 : 0xFFE4E7EA;
  }

  private float minimumTextScale(TextItem text) { return .05f; }

  private static float lineScale(String value) {
    int lines = value == null || value.isEmpty() ? 1 : value.split("\\n", -1).length;
    return lines <= 5 ? 1f : Math.max(.55f, 1f - (lines - 5) * .07f);
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
    final List<Stroke> strokes = new ArrayList<>();
    final List<TextItem> texts = new ArrayList<>();
    boolean edited;

    State(Bitmap bitmap) { this.bitmap = bitmap; }
  }

  private static final class Stroke {
    final Path path;
    final int color;

    Stroke(Path path, int color) {
      this.path = path;
      this.color = color;
    }
  }

  private static final class TextItem {
    String value;
    int color;
    float x;
    float y;
    float scale = 1f;

    TextItem(String value, float x, float y, int color) {
      this.value = value;
      this.x = x;
      this.y = y;
      this.color = color;
    }
  }
}
