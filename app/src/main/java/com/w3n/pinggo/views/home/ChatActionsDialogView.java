package com.w3n.pinggo.views.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.w3n.pinggo.modals.Chat;

import java.util.Locale;

/** AAR-native long-press action dialog for a chat-list row. */
public final class ChatActionsDialogView extends View {
    public enum Action {
        MUTE_ONE_DAY,
        MUTE_ONE_WEEK,
        MUTE_ONE_MONTH,
        MUTE_UNTIL_UNMUTED,
        UNMUTE,
        PIN,
        ARCHIVE
    }

    public interface Listener {
        void onChatAction(Chat chat, Action action);
    }

    private static final int PRIMARY = 0xFF000E1A;
    private static final int SECONDARY = 0xFF687382;
    private static final int ACCENT = 0xFF019CC4;

    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer dialogLayer = layers.addLayer("chat_actions_dialog_layer");
    private final ZLayer muteDialogLayer = layers.addLayer("mute_duration_dialog_layer");
    private final Bitmap actionBackground = colorBitmap(0xFFF3F6F8);
    private final Bitmap cancelBackground = colorBitmap(0xFFE8EEF2);
    private final Listener listener;
    private Dialog dialog;
    private Dialog muteDialog;
    private Chat pendingChat;
    private boolean currentMuted;

    public ChatActionsDialogView(@NonNull Context context, @NonNull Listener listener) {
        super(context);
        this.listener = listener;
        setClickable(true);
        setFocusable(true);
        setVisibility(INVISIBLE);
        dialogLayer.setTouchPolicy(ZLayer.TouchPolicy.MODAL);
        muteDialogLayer.setTouchPolicy(ZLayer.TouchPolicy.MODAL);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && pendingChat != null) buildDialog(width, pendingChat);
    }

    public void show(Chat chat) {
        if (chat == null) return;
        pendingChat = chat;
        currentMuted = chat.isMuted();
        if (getWidth() > 0) buildDialog(getWidth(), chat);
        setVisibility(VISIBLE);
        bringToFront();
        if (dialog != null) dialog.show();
        invalidate();
    }

    private void buildDialog(int hostWidth, Chat chat) {
        dialogLayer.clear();
        float width = Math.min(hostWidth - dp(40), dp(390));
        float height = dp(430);
        Bitmap avatar = loadAvatar(chat);
        dialog = dialogLayer.add(new Dialog.Builder(getContext(), "chat_actions_dialog",
                new RectF(0, 0, width, height))
                .horizontalCenter(true).verticalCenter(true)
                .setBackgroundColor(Color.WHITE).setCornerRadiusPx(dp(26))
                .setDropShadowPx(new DropShadow(0, dp(8), dp(28), dp(2),
                        Color.argb(42, 0, 0, 0)))
                .setDimEnabled(true).setDimColor(Color.BLACK).setDimAlpha(0.48f)
                .setDismissOnBackPressed(true)
                .setOutsideTouchPolicy(Dialog.OutsideTouchPolicy.IGNORE)
                .setInitiallyShown(false)
                .setOnDismissListener((id, reason) -> setVisibility(INVISIBLE))
                .setContent((nativeDialog, content, scope) -> {
                    float contentWidth = scope.width();
                    float avatarSize = dp(72);
                    content.add(new Image.Builder(getContext(), scope.id("profile"), avatar,
                            scope.rect((contentWidth - avatarSize) / 2, dp(22),
                                    avatarSize, avatarSize))
                            .setScaleType(Image.ScaleType.CENTER_CROP));
                    content.add(new Text.Builder(getContext(), scope.id("phone"),
                            chat.getPhoneNumber(), scope.rect(dp(24), dp(102),
                                    contentWidth - dp(48), dp(34)))
                            .setFont(NativeFonts.INTER).setFontVariations(FontVariation.SEMI_BOLD)
                            .setTextSizePx(sp(18)).setTextColor(PRIMARY)
                            .setAlignment(Text.Alignment.CENTER)
                            .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(1));

                    addMuteToggle(content, scope, chat);
                    addAction(nativeDialog, content, scope, "pin",
                            chat.isPinned() ? "Unpin chat" : "Pin chat",
                            dp(208), chat, Action.PIN);
                    addAction(nativeDialog, content, scope, "archive",
                            chat.isArchived() ? "Unarchive" : "Archive",
                            dp(266), chat, Action.ARCHIVE);
                    content.add(new Button.Builder(getContext(), scope.id("cancel"),
                            cancelBackground, "Cancel",
                            scope.rect(dp(22), dp(344), contentWidth - dp(44), dp(52)))
                            .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(dp(14))
                            .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
                            .setTextSizePx(sp(16)).setTextColor(SECONDARY)
                            .setRippleEnabled(true).setRippleColor(0x16000000)
                            .setOnClickListener(id -> nativeDialog.dismiss(
                                    Dialog.DismissReason.ACTION)));
                }));
    }

    private void showMuteOptions(Chat chat) {
        if (chat == null || getWidth() <= 0) return;
        muteDialogLayer.clear();
        float width = Math.min(getWidth() - dp(40), dp(390));
        float height = dp(400);
        muteDialog = muteDialogLayer.add(new Dialog.Builder(getContext(), "mute_duration_dialog",
                new RectF(0, 0, width, height))
                .horizontalCenter(true).verticalCenter(true)
                .setBackgroundColor(Color.WHITE).setCornerRadiusPx(dp(26))
                .setDropShadowPx(new DropShadow(0, dp(8), dp(28), dp(2),
                        Color.argb(42, 0, 0, 0)))
                .setDimEnabled(true).setDimColor(Color.BLACK).setDimAlpha(0.48f)
                .setDismissOnBackPressed(true)
                .setOutsideTouchPolicy(Dialog.OutsideTouchPolicy.IGNORE)
                .setInitiallyShown(false)
                .setContent((nativeDialog, content, scope) -> {
                    content.add(new Text.Builder(getContext(), scope.id("title"),
                            "Mute notifications for", scope.rect(dp(22), dp(18),
                                    scope.width() - dp(44), dp(42)))
                            .setFont(NativeFonts.INTER).setFontVariations(FontVariation.BOLD)
                            .setTextSizePx(sp(20)).setTextColor(PRIMARY)
                            .setAlignment(Text.Alignment.CENTER)
                            .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(1));
                    addMuteDuration(nativeDialog, content, scope, "one_day", "1 day",
                            dp(76), chat, Action.MUTE_ONE_DAY);
                    addMuteDuration(nativeDialog, content, scope, "one_week", "1 week",
                            dp(134), chat, Action.MUTE_ONE_WEEK);
                    addMuteDuration(nativeDialog, content, scope, "one_month", "1 month",
                            dp(192), chat, Action.MUTE_ONE_MONTH);
                    addMuteDuration(nativeDialog, content, scope, "until_unmuted",
                            "Until I unmute", dp(250), chat, Action.MUTE_UNTIL_UNMUTED);
                    content.add(new Button.Builder(getContext(), scope.id("cancel"),
                            cancelBackground, "Cancel",
                            scope.rect(dp(22), dp(322), scope.width() - dp(44), dp(50)))
                            .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(dp(14))
                            .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
                            .setTextSizePx(sp(16)).setTextColor(SECONDARY)
                            .setRippleEnabled(true).setRippleColor(0x16000000)
                            .setOnClickListener(id -> nativeDialog.dismiss(
                                    Dialog.DismissReason.ACTION)));
                }));
        setVisibility(VISIBLE);
        bringToFront();
        muteDialog.show();
        invalidate();
    }

    private void addMuteDuration(Dialog nativeDialog, ZLayer content, Dialog.Scope scope,
                                 String id, String label, float top, Chat chat, Action action) {
        content.add(new Button.Builder(getContext(), scope.id(id), actionBackground, label,
                scope.rect(dp(22), top, scope.width() - dp(44), dp(48)))
                .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(dp(14))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
                .setTextSizePx(sp(16)).setTextColor(PRIMARY)
                .setRippleEnabled(true).setRippleColor(0x16000000)
                .setOnClickListener(buttonId -> {
                    nativeDialog.dismiss(Dialog.DismissReason.ACTION);
                    currentMuted = true;
                    post(() -> refreshParentDialog(chat));
                    listener.onChatAction(chat, action);
                }));
    }

    private Button addMuteToggle(ZLayer content, Dialog.Scope scope, Chat chat) {
        Button button = content.add(new Button.Builder(getContext(), scope.id("mute"),
                actionBackground,
                currentMuted ? "Unmute notifications" : "Mute notifications",
                scope.rect(dp(22), dp(150), scope.width() - dp(44), dp(48)))
                .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(dp(14))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
                .setTextSizePx(sp(16)).setTextColor(PRIMARY)
                .setRippleEnabled(true).setRippleColor(0x16000000)
                .setOnClickListener(buttonId -> {
                    if (currentMuted) {
                        currentMuted = false;
                        refreshParentDialog(chat);
                        listener.onChatAction(chat, Action.UNMUTE);
                    } else {
                        showMuteOptions(chat);
                    }
                }));
        return button;
    }

    private void refreshParentDialog(Chat chat) {
        if (chat == null || getWidth() <= 0) return;
        buildDialog(getWidth(), chat);
        setVisibility(VISIBLE);
        bringToFront();
        if (dialog != null) dialog.show();
        invalidate();
    }

    private void addAction(Dialog nativeDialog, ZLayer content, Dialog.Scope scope,
                           String id, String label, float top, Chat chat, Action action) {
        content.add(new Button.Builder(getContext(), scope.id(id), actionBackground, label,
                scope.rect(dp(22), top, scope.width() - dp(44), dp(48)))
                .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(dp(14))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
                .setTextSizePx(sp(16)).setTextColor(PRIMARY)
                .setRippleEnabled(true).setRippleColor(0x16000000)
                .setOnClickListener(buttonId -> {
                    nativeDialog.dismiss(Dialog.DismissReason.ACTION);
                    if (action == null) {
                        post(() -> showMuteOptions(chat));
                    } else {
                        listener.onChatAction(chat, action);
                    }
                }));
    }

    private Bitmap loadAvatar(Chat chat) {
        String path = chat.getLocalProfilePhotoPath();
        if (path == null || path.trim().isEmpty()) {
            path = ChatProfilePhotoStore.getLocalPath(getContext(), chat.getPhoneNumber());
        }
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        return bitmap == null ? avatar(chat.getPhoneNumber()) : bitmap;
    }

    private Bitmap avatar(String value) {
        int size = Math.max(1, Math.round(dp(72)));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFFD9F1F7);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        String label = value == null || value.trim().isEmpty() ? "?"
                : value.trim().substring(0, 1).toUpperCase(Locale.US);
        paint.setColor(ACCENT); paint.setTextSize(size * .42f);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(label, size / 2f,
                size / 2f - (metrics.ascent + metrics.descent) / 2f, paint);
        return bitmap;
    }

    public boolean dismissIfShowing() {
        if (muteDialog != null && muteDialog.isShowing()) {
            muteDialog.dismiss(Dialog.DismissReason.BACK_PRESSED);
            return true;
        }
        if (dialog == null || !dialog.isShowing()) return false;
        dialog.dismiss(Dialog.DismissReason.BACK_PRESSED);
        return true;
    }

    public void release() {
        dialogLayer.clear();
        muteDialogLayer.clear();
        layers.release();
        actionBackground.recycle();
        cancelBackground.recycle();
    }

    @Override protected void onDraw(@NonNull Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
    @Override public boolean onTouchEvent(MotionEvent event) { layers.onTouchEvent(event); return true; }
    @Override public boolean onCheckIsTextEditor() { return layers.onCheckIsTextEditor(); }
    @Override public InputConnection onCreateInputConnection(EditorInfo attrs) {
        InputConnection connection = layers.onCreateInputConnection(attrs);
        return connection != null ? connection : super.onCreateInputConnection(attrs);
    }
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        return layers.onKeyDown(code, event) || super.onKeyDown(code, event);
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
    private static Bitmap colorBitmap(int color) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color); return bitmap;
    }
}
