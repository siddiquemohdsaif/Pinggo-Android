package com.w3n.pinggo.views.home;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.text.FontVariation;
import java.util.EnumMap;

/** Resolve font weights once, rather than recreating variable fonts on every text update. */
public final class ListFonts {
    private static final EnumMap<FontVariation, Typeface> fonts = new EnumMap<>(FontVariation.class);

    public static synchronized Typeface inter(Context context, FontVariation variation) {
        Typeface font = fonts.get(variation);
        if (font == null) {
            Typeface base = NativeFonts.load(context.getApplicationContext(), NativeFonts.INTER);
            font = Build.VERSION.SDK_INT >= 28
                    ? Typeface.create(base, variation.getWeight(), false)
                    : Typeface.create(base, variation.getWeight() >= 600 ? Typeface.BOLD : Typeface.NORMAL);
            fonts.put(variation, font);
        }
        return font;
    }

    private ListFonts() {}
}
