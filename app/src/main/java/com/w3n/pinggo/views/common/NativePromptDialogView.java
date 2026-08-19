package com.w3n.pinggo.views.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.NonNull;

import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.dialog.Dialog;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.textfield.TextField;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.R;

import java.util.List;

/** One-shot AAR-native message, input, or action-list dialog host. */
public final class NativePromptDialogView extends View {
  public interface InputHandler { boolean onSubmit(String value); }
  public interface ActionHandler { void onAction(int index); }

  private enum Mode { MESSAGE, INPUT, ACTIONS }

  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer dialogLayer = layers.addLayer("prompt_dialog_layer");
  private final Bitmap secondary = colorBitmap(0xFFF0F3F6);
  private final Bitmap primary = colorBitmap(0xFF019CC4);
  private final Mode mode;
  private final String title;
  private final String message;
  private final String initialValue;
  private final int inputType;
  private final List<String> actions;
  private final InputHandler inputHandler;
  private final ActionHandler actionHandler;
  private final Runnable dismissHandler;
  private Dialog dialog;
  private TextField field;
  private boolean built;

  private NativePromptDialogView(Context context, Mode mode, String title, String message,
      String initialValue, int inputType, List<String> actions, InputHandler inputHandler,
      ActionHandler actionHandler, Runnable dismissHandler) {
    super(context);
    this.mode = mode;
    this.title = title == null ? "" : title;
    this.message = message == null ? "" : message;
    this.initialValue = initialValue == null ? "" : initialValue;
    this.inputType = inputType;
    this.actions = actions;
    this.inputHandler = inputHandler;
    this.actionHandler = actionHandler;
    this.dismissHandler = dismissHandler;
    setClickable(true);
    setFocusableInTouchMode(true);
    dialogLayer.setTouchPolicy(ZLayer.TouchPolicy.MODAL);
  }

  public static NativePromptDialogView message(Context context, String title, String message,
      Runnable onDismiss) {
    return new NativePromptDialogView(context, Mode.MESSAGE, title, message, "", 0,
        null, null, null, onDismiss);
  }

  public static NativePromptDialogView input(Context context, String title, String initialValue,
      int inputType, InputHandler handler, Runnable onDismiss) {
    return new NativePromptDialogView(context, Mode.INPUT, title, "", initialValue, inputType,
        null, handler, null, onDismiss);
  }

  public static NativePromptDialogView actions(Context context, List<String> actions,
      ActionHandler handler, Runnable onDismiss) {
    return new NativePromptDialogView(context, Mode.ACTIONS, "", "", "", 0,
        actions, null, handler, onDismiss);
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    if (built || width <= 0 || height <= 0) return;
    built = true;
    float dialogWidth = Math.min(width - dp(40), dp(390));
    float dialogHeight = mode == Mode.ACTIONS
        ? dp(32 + actions.size() * 58) : mode == Mode.INPUT ? dp(250) : dp(230);
    dialog = dialogLayer.add(new Dialog.Builder(getContext(), "native_prompt",
        new RectF(0, 0, dialogWidth, dialogHeight))
        .horizontalCenter(true).verticalCenter(true)
        .setBackgroundColor(Color.WHITE).setCornerRadiusPx(dp(24))
        .setDimEnabled(true).setDimAlpha(0.48f)
        .setInitiallyShown(true).setDismissOnBackPressed(true)
        .setOnDismissListener((id, reason) -> { if (dismissHandler != null) dismissHandler.run(); })
        .setContent((nativeDialog, content, scope) -> buildContent(nativeDialog, content, scope)));
  }

  private void buildContent(Dialog nativeDialog, ZLayer content, Dialog.Scope scope) {
    float width = scope.width();
    if (mode == Mode.ACTIONS) {
      for (int index = 0; index < actions.size(); index++) {
        final int selected = index;
        content.add(button(scope, "action_" + index, secondary, actions.get(index),
            dp(16), dp(16 + index * 58), width - dp(32), 0xFF000E1A, id -> {
              nativeDialog.dismiss(Dialog.DismissReason.ACTION);
              actionHandler.onAction(selected);
            }));
      }
      return;
    }
    content.add(new Text.Builder(getContext(), scope.id("title"), title,
        scope.rect(dp(22), dp(18), width - dp(44), dp(42)))
        .setFont(NativeFonts.INTER).setFontVariations(FontVariation.BOLD)
        .setTextSizePx(dp(21)).setTextColor(0xFF000E1A)
        .setAlignment(Text.Alignment.CENTER).setVerticalAlignment(Text.VerticalAlignment.CENTER)
        .setMaxLines(1));
    if (mode == Mode.MESSAGE) {
      content.add(new Text.Builder(getContext(), scope.id("message"), message,
          scope.rect(dp(22), dp(68), width - dp(44), dp(70)))
          .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
          .setTextSizePx(dp(16)).setTextColor(0xFF656565)
          .setAlignment(Text.Alignment.CENTER).setVerticalAlignment(Text.VerticalAlignment.CENTER)
          .setMaxLines(3));
    } else {
      field = content.add(new TextField.Builder(getContext(), scope.id("field"),
          scope.rect(dp(22), dp(78), width - dp(44), dp(58)))
          .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
          .setText(initialValue).setInputType(inputType)
          .setTextSizePx(dp(17)).setTextColor(0xFF000E1A).setHintColor(0xFF7A8792)
          .setBackgroundColor(0xFFF7FAFC, Color.WHITE)
          .setStrokeColor(0xFFD5DEE7, 0xFF019CC4)
          .setCornerRadiusPx(dp(12)).setPaddingPx(dp(14), dp(8)));
    }
    float buttonTop = scope.height() - dp(70);
    if (mode == Mode.INPUT) {
      float gap = dp(10), pad = dp(22);
      float buttonWidth = (width - pad * 2 - gap) / 2;
      content.add(button(scope, "cancel", secondary, getContext().getString(android.R.string.cancel),
          pad, buttonTop, buttonWidth, 0xFF000E1A,
          id -> nativeDialog.dismiss(Dialog.DismissReason.ACTION)));
      content.add(button(scope, "confirm", primary, getContext().getString(R.string.confirm),
          pad + buttonWidth + gap,
          buttonTop, buttonWidth, Color.WHITE, id -> {
            if (inputHandler.onSubmit(field.getText().trim())) {
              nativeDialog.dismiss(Dialog.DismissReason.ACTION);
            }
          }));
    } else {
      content.add(button(scope, "ok", primary, getContext().getString(android.R.string.ok),
          dp(22), buttonTop, width - dp(44), Color.WHITE,
          id -> nativeDialog.dismiss(Dialog.DismissReason.ACTION)));
    }
  }

  private Button button(Dialog.Scope scope, String id, Bitmap background, String label,
      float left, float top, float width, int textColor, Button.OnClickListener listener) {
    return new Button.Builder(getContext(), scope.id(id), background, label,
        scope.rect(left, top, width, dp(50)))
        .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(dp(13))
        .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
        .setTextSizePx(dp(16)).setTextColor(textColor).setRippleEnabled(true)
        .setOnClickListener(listener).build(this);
  }

  @Override protected void onDraw(@NonNull Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
  @Override public boolean onTouchEvent(MotionEvent event) { return layers.onTouchEvent(event) || super.onTouchEvent(event); }
  @Override public boolean onCheckIsTextEditor() { return layers.onCheckIsTextEditor(); }
  @Override public InputConnection onCreateInputConnection(EditorInfo attrs) {
    InputConnection connection = layers.onCreateInputConnection(attrs);
    return connection != null ? connection : super.onCreateInputConnection(attrs);
  }
  @Override public boolean onKeyDown(int code, KeyEvent event) {
    return layers.onKeyDown(code, event) || super.onKeyDown(code, event);
  }
  public void release() { layers.release(); secondary.recycle(); primary.recycle(); }
  private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
  private static Bitmap colorBitmap(int color) {
    Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(color); return bitmap;
  }
}
