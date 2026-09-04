package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.component.FigmaConfig;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

/** AAR-native, WhatsApp-style camera controls. */
public final class NativeCameraChromeView extends View {

    public static final int HEADER_COLOR = Color.BLACK;

    private final FigmaConfig figma = new FigmaConfig(1080f);
    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer layer = layers.addLayer("camera_chrome");
    private final Bitmap panel = color(0xF006070C);
    private final Bitmap control = color(0xE6121720);
    private final Bitmap selected = color(0xFF202124);
    private final Bitmap accent = color(0xFF21A366);
    private final Bitmap recordingColor = color(0xFFE53935);
    private final Bitmap white = color(Color.WHITE);
    private final Bitmap clear = color(Color.TRANSPARENT);
    private final Listener listener;

    private int topInset;
    private int selectedCount;
    private boolean video;
    private boolean recording;
    private boolean modesEnabled = true;
    private boolean flashOn;
    private boolean flashAvailable;
    private String timer = "00:00";

    public NativeCameraChromeView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
    }

    public void setTopInset(int value) {
        topInset = Math.max(0, value);
        build();
    }

    public void setState(
            boolean video,
            boolean recording,
            boolean modesEnabled,
            int selectedCount,
            boolean flashOn,
            boolean flashAvailable,
            String timer) {
        this.video = video;
        this.recording = recording;
        this.modesEnabled = modesEnabled;
        this.selectedCount = selectedCount;
        this.flashOn = flashOn;
        this.flashAvailable = flashAvailable;
        this.timer = timer == null ? "00:00" : timer;
        build();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        build();
    }

    private void build() {
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        layer.clear();
        float footerHeight = px(700f);
        layer.add(
                new Image.Builder(
                                getContext(),
                                "camera_footer",
                                panel,
                                new RectF(0f, height - footerHeight, width, height))
                        .setScaleType(Image.ScaleType.FIT_XY));

        float top = topInset + px(34f);
        float topButtonSize = px(112f);
        layer.add(
                button(
                        "back",
                        "×",
                        control,
                        new RectF(px(34f), top, px(34f) + topButtonSize, top + topButtonSize),
                        id -> listener.onBack(),
                        40f,
                        Color.WHITE,
                        topButtonSize / 2f));

        Button flashButton =
                layer.add(
                        button(
                                "flash",
                                flashOn ? "⚡" : "ϟ",
                                control,
                                new RectF(
                                        width - px(34f) - topButtonSize,
                                        top,
                                        width - px(34f),
                                        top + topButtonSize),
                                id -> listener.onFlash(),
                                28f,
                                flashAvailable ? Color.WHITE : 0xFF687382,
                                topButtonSize / 2f));
        flashButton.setEnabled(flashAvailable && !recording);

        if (video) {
            float timerWidth = px(178f);
            layer.add(
                    button(
                            "video_timer",
                            timer,
                            control,
                            new RectF(
                                    (width - timerWidth) / 2f,
                                    top + px(8f),
                                    (width + timerWidth) / 2f,
                                    top + px(100f)),
                            id -> {},
                            17f,
                            Color.WHITE,
                            px(46f)));
        }

        float centerX = width / 2f;
        float controlsY = height - px(500f);
        float sideSize = px(124f);
        layer.add(
                button(
                        "gallery",
                        "▣",
                        control,
                        new RectF(
                                px(34f),
                                controlsY - sideSize / 2f,
                                px(34f) + sideSize,
                                controlsY + sideSize / 2f),
                        id -> listener.onGallery(),
                        32f,
                        Color.WHITE,
                        sideSize / 2f));
        Button flipButton =
                layer.add(
                        button(
                                "flip",
                                "↻",
                                control,
                                new RectF(
                                        width - px(34f) - sideSize,
                                        controlsY - sideSize / 2f,
                                        width - px(34f),
                                        controlsY + sideSize / 2f),
                                id -> listener.onFlip(),
                                38f,
                                Color.WHITE,
                                sideSize / 2f));
        flipButton.setEnabled(modesEnabled);

        float outerSize = px(194f);
        float gapSize = px(168f);
        float innerSize = px(recording ? 104f : 142f);
        circleImage("capture_outer", white, centerX, controlsY, outerSize);
        circleImage("capture_gap", panel, centerX, controlsY, gapSize);
        circleImage(
                "capture_inner", recording ? recordingColor : white, centerX, controlsY, innerSize);
        layer.add(
                button(
                        "capture",
                        "",
                        clear,
                        new RectF(
                                centerX - outerSize / 2f,
                                controlsY - outerSize / 2f,
                                centerX + outerSize / 2f,
                                controlsY + outerSize / 2f),
                        id -> listener.onCapture(),
                        1f,
                        Color.TRANSPARENT,
                        outerSize / 2f));

        layer.add(
                text(
                        "gallery_handle",
                        video ? "⌃" : "—",
                        new RectF(centerX - px(50f), height - px(735f), centerX + px(50f), height - px(675f)),
                        24f,
                        Color.WHITE,
                        Text.Alignment.CENTER));

        float modeY = height - px(340f);
        Button videoButton =
                layer.add(
                        button(
                                "video",
                                "Video",
                                video ? selected : clear,
                                new RectF(centerX - px(230f), modeY, centerX - px(20f), modeY + px(112f)),
                                id -> {
                                    if (modesEnabled) {
                                        listener.onVideo();
                                    }
                                },
                                18f,
                                Color.WHITE,
                                px(56f)));
        Button photoButton =
                layer.add(
                        button(
                                "photo",
                                "Photo",
                                video ? clear : selected,
                                new RectF(centerX + px(20f), modeY, centerX + px(230f), modeY + px(112f)),
                                id -> {
                                    if (modesEnabled) {
                                        listener.onPhoto();
                                    }
                                },
                                18f,
                                Color.WHITE,
                                px(56f)));
        videoButton.setEnabled(modesEnabled);
        photoButton.setEnabled(modesEnabled);

        if (!video && selectedCount > 0) {
            String label = selectedCount > 1 ? "✓" + selectedCount : "✓";
            layer.add(
                    button(
                            "gallery_confirm",
                            label,
                            accent,
                            new RectF(
                                    width - px(180f),
                                    height - px(920f),
                                    width - px(40f),
                                    height - px(780f)),
                            id -> listener.onGalleryConfirm(),
                            22f,
                            Color.WHITE,
                            px(70f)));
        }

        invalidate();
    }

    private void circleImage(String id, Bitmap bitmap, float centerX, float centerY, float size) {
        layer.add(
                new Button.Builder(
                                getContext(),
                                id,
                                bitmap,
                                "",
                                new RectF(
                                        centerX - size / 2f,
                                        centerY - size / 2f,
                                        centerX + size / 2f,
                                        centerY + size / 2f))
                        .setImageScaleType(Image.ScaleType.FIT_XY)
                        .setCornerRadiusPx(size / 2f)
                        .setRippleEnabled(false));
    }

    private Button.Builder button(
            String id,
            String label,
            Bitmap background,
            RectF bounds,
            Button.OnClickListener clickListener,
            float textSize,
            int textColor,
            float cornerRadius) {
        return new Button.Builder(getContext(), id, background, label, bounds)
                .setImageScaleType(Image.ScaleType.FIT_XY)
                .setCornerRadiusPx(cornerRadius)
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.SEMI_BOLD)
                .setTextSizePx(sp(textSize))
                .setTextColor(textColor)
                .setRippleEnabled(true)
                .setRippleColor(0x33FFFFFF)
                .setOnClickListener(clickListener);
    }

    private Text.Builder text(
            String id,
            String value,
            RectF bounds,
            float textSize,
            int textColor,
            Text.Alignment alignment) {
        return new Text.Builder(getContext(), id, value, bounds)
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSizePx(sp(textSize))
                .setTextColor(textColor)
                .setAlignment(alignment)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setMaxLines(1);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        layers.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return layers.onTouchEvent(event);
    }

    public void release() {
        layers.release();
        recycle(panel, control, selected, accent, recordingColor, white, clear);
    }

    private float px(float value) {
        return figma.toRuntime(
                value, Math.max(1, getResources().getDisplayMetrics().widthPixels));
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private static Bitmap color(int color) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }

    private static void recycle(Bitmap... bitmaps) {
        for (Bitmap bitmap : bitmaps) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    public interface Listener {
        void onBack();

        void onPhoto();

        void onVideo();

        void onCapture();

        void onGalleryConfirm();

        void onFlash();

        void onFlip();

        void onGallery();
    }
}
