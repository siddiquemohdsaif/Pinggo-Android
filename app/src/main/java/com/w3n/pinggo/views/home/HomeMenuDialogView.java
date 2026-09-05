package com.w3n.pinggo.views.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.component.FigmaConfig;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.R;

/** Overflow menu shown from the three-dot action on the home screen. */
public final class HomeMenuDialogView extends View {
    private static final float FIGMA_WIDTH = 1080f;
    private static final int TEXT_COLOR = 0xFF101C31;

    public interface Listener {
        void onNewChat();
        void onNewGroup();
        void onLinkedDevices();
        void onSettings();
    }

    private final FigmaConfig figmaConfig = new FigmaConfig(FIGMA_WIDTH);
    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer menuLayer = layers.addLayer("home_overflow_menu");
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap transparent = colorBitmap(Color.TRANSPARENT);
    private final Bitmap newChat = resourceBitmap(R.drawable.home_menu_new_chat);
    private final Bitmap newGroup = resourceBitmap(R.drawable.home_menu_new_group);
    private final Bitmap linkedDevices = resourceBitmap(R.drawable.home_menu_linked_devices);
    private final Bitmap settings = resourceBitmap(R.drawable.home_menu_settings);
    private final Listener listener;
    private RectF menuBounds = new RectF();

    public HomeMenuDialogView(@NonNull Context context, @NonNull Listener listener) {
        super(context);
        this.listener = listener;
        setClickable(true);
        setFocusableInTouchMode(true);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setVisibility(INVISIBLE);
        menuLayer.setTouchPolicy(ZLayer.TouchPolicy.MODAL);
        cardPaint.setColor(Color.WHITE);
        cardPaint.setShadowLayer(22f, 0f, 8f, 0x24000000);
    }

    public void show() {
        if (getWidth() > 0) buildMenu(getWidth());
        setVisibility(VISIBLE);
        bringToFront();
        requestFocus();
        invalidate();
    }

    public boolean dismissIfShowing() {
        if (getVisibility() != VISIBLE) return false;
        setVisibility(INVISIBLE);
        return true;
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0) buildMenu(width);
    }

    private void buildMenu(int hostWidth) {
        menuLayer.clear();
        float scale = figmaConfig.getScale(hostWidth);
        float menuWidth = 397f * scale;
        float menuHeight = 600f * scale;
        float left = hostWidth - 40f * scale - menuWidth;
        WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(this);
        Insets statusBars = windowInsets == null ? Insets.NONE
                : windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
        float top = statusBars.top + 154f * scale;
        menuBounds.set(left, top, left + menuWidth, top + menuHeight);

        addOption("new_chat", newChat, R.string.new_chat, 25f, false,
                () -> listener.onNewChat());
        addOption("new_group", newGroup, R.string.new_group, 162f, false,
                () -> listener.onNewGroup());
        addOption("linked_devices", linkedDevices, R.string.linked_devices, 299f, true,
                () -> listener.onLinkedDevices());
        addOption("settings", settings, R.string.settings, 436f, false,
                () -> listener.onSettings());
    }

    private void addOption(String id, Bitmap icon, int labelResource, float rowTop,
                           boolean doubleLine,
                           Runnable action) {
        float scale = figmaConfig.getScale(getWidth());
        float top = menuBounds.top + rowTop * scale;
        float iconLeft = menuBounds.left + 40f * scale;
        float iconSize = 73f * scale;
        menuLayer.add(new Image.Builder(getContext(), id + "_icon", icon,
                new RectF(iconLeft, top + 32f * scale,
                        iconLeft + iconSize, top + 32f * scale + iconSize))
                .setScaleType(Image.ScaleType.FIT_CENTER));
        float textTop = top + (doubleLine ? 31f : 39f) * scale;
        float textHeight = (doubleLine ? 76f : 48f) * scale;
        menuLayer.add(new Text.Builder(getContext(), id + "_label",
                getResources().getString(labelResource),
                new RectF(menuBounds.left + 145f * scale, textTop,
                        menuBounds.right - 24f * scale, textTop + textHeight))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
                .setTextSizePx(34f * scale).setTextColor(TEXT_COLOR)
                .setAlignment(Text.Alignment.START)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setMaxLines(doubleLine ? 2 : 1));
        menuLayer.add(new Button.Builder(getContext(), id + "_touch", transparent, "",
                new RectF(menuBounds.left, top, menuBounds.right, top + 137f * scale))
                .setImageScaleType(Image.ScaleType.FIT_XY).setRippleEnabled(true).setWaitForRippleBeforeClick(true)
                .setRippleColor(0x12019CC4).setOnClickListener(value -> {
                    dismissIfShowing();
                    action.run();
                }));
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (!menuBounds.isEmpty()) {
            float radius = 54f * figmaConfig.getScale(getWidth());
            canvas.drawRoundRect(menuBounds, radius, radius, cardPaint);
        }
        layers.draw(canvas);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                && !menuBounds.contains(event.getX(), event.getY())) {
            dismissIfShowing();
            return true;
        }
        layers.onTouchEvent(event);
        return true;
    }

    public void release() {
        layers.release();
        recycle(transparent, newChat, newGroup, linkedDevices, settings);
    }

    private static Bitmap colorBitmap(int color) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }
    private Bitmap resourceBitmap(int resource) {
        return BitmapFactory.decodeResource(getResources(), resource);
    }
    private static void recycle(Bitmap... values) {
        for (Bitmap value : values) {
            if (value != null && !value.isRecycled()) value.recycle();
        }
    }
}
