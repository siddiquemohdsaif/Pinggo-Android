package com.w3n.wavestream.utils.login;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

/** Draws and clips the country popup's bottom-rounded card and drop shadow. */
public final class CountryCardLayout extends FrameLayout {
    private static final int SHADOW_COLOR = 0x0D000000;

    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cardPath = new Path();
    private final float bottomRadius;
    private final float shadowSpread;

    public CountryCardLayout(Context context, float bottomRadius, float shadowOffsetY,
                             float shadowBlur, float shadowSpread) {
        super(context);
        this.bottomRadius = bottomRadius;
        this.shadowSpread = shadowSpread;
        shadowPaint.setColor(SHADOW_COLOR);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setShadowLayer(shadowBlur, 0f, shadowOffsetY, SHADOW_COLOR);
        cardPaint.setColor(Color.WHITE);
        cardPaint.setStyle(Paint.Style.FILL);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        RectF cardBounds = cardBounds();
        RectF shadowBounds = new RectF(cardBounds);
        shadowBounds.inset(-shadowSpread, -shadowSpread);
        canvas.drawPath(bottomRoundedPath(shadowBounds, bottomRadius + shadowSpread),
                shadowPaint);
        cardPath.set(bottomRoundedPath(cardBounds, bottomRadius));
        canvas.drawPath(cardPath, cardPaint);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int save = canvas.save();
        canvas.clipPath(cardPath);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    private RectF cardBounds() {
        return new RectF(getPaddingLeft(), getPaddingTop(),
                getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
    }

    private static Path bottomRoundedPath(RectF bounds, float radius) {
        Path path = new Path();
        float[] radii = {0f, 0f, 0f, 0f, radius, radius, radius, radius};
        path.addRoundRect(bounds, radii, Path.Direction.CW);
        return path;
    }
}
