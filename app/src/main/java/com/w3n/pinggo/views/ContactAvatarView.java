package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.View;

import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

public class ContactAvatarView extends View {
    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer content = layers.addLayer("contact_avatar");
    private final String initial;
    private final int backgroundColor;

    public ContactAvatarView(Context context, String contactName) {
        super(context);
        String safeName = contactName == null || contactName.trim().isEmpty() ? "?" : contactName.trim();
        initial = safeName.substring(0, 1).toUpperCase(java.util.Locale.getDefault());
        backgroundColor = profileColor(safeName);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        content.clear();
        Bitmap background = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        background.eraseColor(backgroundColor);
        content.add(new com.ogfa.nativeviews.image.Image.Builder(getContext(), "background", background,
                new RectF(0, 0, width, height)).setScaleType(
                com.ogfa.nativeviews.image.Image.ScaleType.FIT_XY));
        content.add(new Text.Builder(getContext(), "initial", initial, new RectF(0, 0, width, height))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.BOLD)
                .setTextSizePx(Math.min(width, height) * .42f).setTextColor(Color.WHITE)
                .setAlignment(Text.Alignment.CENTER).setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setMaxLines(1));
    }

    @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }

    private int profileColor(String contactName) {
        int[] colors = {
                Color.rgb(29, 103, 210),
                Color.rgb(21, 128, 112),
                Color.rgb(178, 88, 27),
                Color.rgb(140, 82, 170),
                Color.rgb(201, 63, 83),
                Color.rgb(67, 111, 86)
        };
        return colors[Math.floorMod(contactName.hashCode(), colors.length)];
    }
}
