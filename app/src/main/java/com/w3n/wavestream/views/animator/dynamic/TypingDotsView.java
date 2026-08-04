package com.w3n.wavestream.views.animator.dynamic;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

public class TypingDotsView implements CustomDynamicView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int color;

    public TypingDotsView(int color) {
        this.color = color;
    }

    @Override
    public void onDraw(Canvas canvas, float progress, RectF rectF) {
        paint.setColor(color);
        float radius = Math.min(rectF.width(), rectF.height()) / 12f;
        float gap = radius * 3f;
        float centerX = rectF.centerX() - gap;
        float centerY = rectF.centerY();

        for (int i = 0; i < 3; i++) {
            float phase = (progress + (i * 0.18f)) % 1f;
            float alpha = phase < 0.5f ? 0.35f + phase : 1.35f - phase;
            paint.setAlpha((int) (Math.max(0.35f, Math.min(1f, alpha)) * 255f));
            canvas.drawCircle(centerX + (gap * i), centerY, radius, paint);
        }
        paint.setAlpha(255);
    }

    @Override
    public long getDuration() {
        return 900L;
    }
}
