package com.w3n.pinggo.views.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.NonNull;

import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.card.DropShadow;
import com.ogfa.nativeviews.dialog.Dialog;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.R;

/** Full-screen host for the native-view exit confirmation dialog. */
public final class ExitAppDialogView extends View {
    private static final int PRIMARY_TEXT = 0xFF000E1A;
    private static final int MUTED_TEXT = 0xFF656565;
    private static final int ACCENT = 0xFF019CC4;

    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer dialogLayer = layers.addLayer("exit_dialog_layer");
    private final Bitmap cancelBackground = colorBitmap(0xFFF0F3F6);
    private final Bitmap exitBackground = colorBitmap(ACCENT);
    private final Runnable exitAction;
    private Dialog dialog;
    private boolean pendingShow;

    public ExitAppDialogView(@NonNull Context context, @NonNull Runnable exitAction) {
        super(context);
        this.exitAction = exitAction;
        setClickable(true);
        setFocusable(true);
        setVisibility(INVISIBLE);
        dialogLayer.setTouchPolicy(ZLayer.TouchPolicy.MODAL);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width <= 0 || height <= 0) return;
        buildDialog(width);
        if (pendingShow) showDialog();
    }

    private void buildDialog(int hostWidth) {
        dialogLayer.clear();
        float width = Math.min(hostWidth - dp(48f), dp(380f));
        float height = dp(230f);
        dialog = dialogLayer.add(new Dialog.Builder(getContext(), "exit_app_dialog",
                new RectF(0f, 0f, width, height))
                .horizontalCenter(true)
                .verticalCenter(true)
                .setBackgroundColor(Color.WHITE)
                .setCornerRadiusPx(dp(28f))
                .setDropShadowPx(new DropShadow(0f, dp(8f), dp(28f), dp(2f),
                        Color.argb(38, 0, 0, 0)))
                .setDimEnabled(true)
                .setDimColor(Color.BLACK)
                .setDimAlpha(0.48f)
                .setDismissOnBackPressed(true)
                .setOutsideTouchPolicy(Dialog.OutsideTouchPolicy.IGNORE)
                .setInitiallyShown(false)
                .setOnDismissListener((id, reason) -> {
                    pendingShow = false;
                    setVisibility(INVISIBLE);
                })
                .setContent((nativeDialog, content, scope) -> {
                    float contentWidth = scope.width();
                    content.add(new Text.Builder(getContext(), scope.id("title"),
                            getContext().getString(R.string.exit_app_title),
                            scope.rect(dp(24f), dp(24f), contentWidth - dp(48f), dp(44f)))
                            .setFont(NativeFonts.INTER)
                            .setFontVariations(FontVariation.BOLD)
                            .setTextSizePx(dp(22f))
                            .setTextColor(PRIMARY_TEXT)
                            .setAlignment(Text.Alignment.CENTER)
                            .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                            .setMaxLines(1));
                    content.add(new Text.Builder(getContext(), scope.id("message"),
                            getContext().getString(R.string.exit_app_message),
                            scope.rect(dp(24f), dp(72f), contentWidth - dp(48f), dp(52f)))
                            .setFont(NativeFonts.INTER)
                            .setFontVariations(FontVariation.REGULAR)
                            .setTextSizePx(dp(16f))
                            .setTextColor(MUTED_TEXT)
                            .setAlignment(Text.Alignment.CENTER)
                            .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                            .setMaxLines(2));

                    float buttonTop = dp(148f);
                    float buttonGap = dp(12f);
                    float horizontalPadding = dp(24f);
                    float buttonWidth = (contentWidth - horizontalPadding * 2f - buttonGap) / 2f;
                    content.add(new Button.Builder(getContext(), scope.id("cancel"),
                            cancelBackground, getContext().getString(R.string.cancel),
                            scope.rect(horizontalPadding, buttonTop, buttonWidth, dp(52f)))
                            .setImageScaleType(com.ogfa.nativeviews.image.Image.ScaleType.FIT_XY)
                            .setCornerRadiusPx(dp(16f))
                            .setFont(NativeFonts.INTER)
                            .setFontVariations(FontVariation.MEDIUM)
                            .setTextSizePx(dp(16f))
                            .setTextColor(PRIMARY_TEXT)
                            .setRippleEnabled(true)
                            .setRippleColor(0x16000000)
                            .setOnClickListener(id -> nativeDialog.dismiss(
                                    Dialog.DismissReason.ACTION)));
                    content.add(new Button.Builder(getContext(), scope.id("exit"),
                            exitBackground, getContext().getString(R.string.exit_app_action),
                            scope.rect(horizontalPadding + buttonWidth + buttonGap,
                                    buttonTop, buttonWidth, dp(52f)))
                            .setImageScaleType(com.ogfa.nativeviews.image.Image.ScaleType.FIT_XY)
                            .setCornerRadiusPx(dp(16f))
                            .setFont(NativeFonts.INTER)
                            .setFontVariations(FontVariation.BOLD)
                            .setTextSizePx(dp(16f))
                            .setTextColor(Color.WHITE)
                            .setRippleEnabled(true)
                            .setRippleColor(0x33FFFFFF)
                            .setOnClickListener(id -> exitAction.run()));
                }));
    }

    public void showDialog() {
        pendingShow = true;
        setVisibility(VISIBLE);
        bringToFront();
        if (dialog != null) dialog.show();
        invalidate();
    }

    public boolean dismissIfShowing() {
        if (dialog == null || !dialog.isShowing()) return false;
        dialog.dismiss(Dialog.DismissReason.BACK_PRESSED);
        return true;
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    @Override
    protected void onDraw(@NonNull android.graphics.Canvas canvas) {
        super.onDraw(canvas);
        layers.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        layers.onTouchEvent(event);
        return true;
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return layers.onCheckIsTextEditor();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection connection = layers.onCreateInputConnection(outAttrs);
        return connection != null ? connection : super.onCreateInputConnection(outAttrs);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return layers.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static Bitmap colorBitmap(int color) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }
}
