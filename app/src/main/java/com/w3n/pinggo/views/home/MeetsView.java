package com.w3n.pinggo.views.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Placeholder content screen for the Meet tab. */
public final class MeetsView extends View {
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MeetsView(Context context) {
        super(context);
        textPaint.setColor(0xFF687382);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(16));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = getHeight() / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(getResources().getString(com.w3n.pinggo.R.string.no_meetings),
                getWidth() / 2f, baseline, textPaint);
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
