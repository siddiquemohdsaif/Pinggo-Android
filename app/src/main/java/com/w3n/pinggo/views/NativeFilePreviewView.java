package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;

import com.ogfa.nativeviews.component.FigmaConfig;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.R;

/** AAR-native document icon and filename preview. */
public final class NativeFilePreviewView extends View {

    private final FigmaConfig figma = new FigmaConfig(1080f);
    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer layer = layers.addLayer("file_preview");
    private final Bitmap icon =
            BitmapFactory.decodeResource(getResources(), R.drawable.chat_document);

    private String name = "File";

    public NativeFilePreviewView(Context context) {
        super(context);
    }

    public void setFileName(String value) {
        name = value == null || value.trim().isEmpty() ? "File" : value;
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
        float iconWidth = px(250.25f);
        float iconHeight = px(308f);
        float top = Math.max(px(220f), (height - iconHeight) / 2f - px(90f));

        layer.add(
                new Image.Builder(
                                getContext(),
                                "document_icon",
                                icon,
                                new RectF(
                                        (width - iconWidth) / 2f,
                                        top,
                                        (width + iconWidth) / 2f,
                                        top + iconHeight))
                        .setScaleType(Image.ScaleType.FIT_CENTER));

        layer.add(
                new Text.Builder(
                                getContext(),
                                "document_name",
                                name,
                                new RectF(
                                        px(88f),
                                        top + iconHeight + px(49.5f),
                                        width - px(88f),
                                        top + iconHeight + px(210f)))
                        .setFont(NativeFonts.INTER)
                        .setFontVariations(FontVariation.REGULAR)
                        .setTextSizePx(20f * getResources().getDisplayMetrics().scaledDensity)
                        .setTextColor(Color.WHITE)
                        .setAlignment(Text.Alignment.CENTER)
                        .setVerticalAlignment(Text.VerticalAlignment.TOP)
                        .setMaxLines(4));

        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        layers.draw(canvas);
    }

    public void release() {
        layers.release();
        if (icon != null && !icon.isRecycled()) {
            icon.recycle();
        }
    }

    private float px(float value) {
        return figma.toRuntime(
                value, Math.max(1, getResources().getDisplayMetrics().widthPixels));
    }
}
