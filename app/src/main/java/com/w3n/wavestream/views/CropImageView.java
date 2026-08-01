package com.w3n.wavestream.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class CropImageView extends View {
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageRect = new RectF();
    private final RectF cropRect = new RectF();

    private Bitmap bitmap;
    private int cropBoxSizePx;
    private float downX;
    private float downY;
    private boolean draggingCrop;

    public CropImageView(Context context) {
        super(context);
        init();
    }

    public CropImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        dimPaint.setColor(0x99000000);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(3));
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        resetRects();
        invalidate();
    }

    public void setCropBoxSizeDp(int cropBoxSizeDp) {
        cropBoxSizePx = Math.round(dp(cropBoxSizeDp));
        resetRects();
        invalidate();
    }

    public Bitmap getCroppedBitmap() {
        if (bitmap == null || imageRect.width() <= 0 || imageRect.height() <= 0) {
            return null;
        }

        float scaleX = bitmap.getWidth() / imageRect.width();
        float scaleY = bitmap.getHeight() / imageRect.height();
        int left = clamp(Math.round((cropRect.left - imageRect.left) * scaleX), 0, bitmap.getWidth() - 1);
        int top = clamp(Math.round((cropRect.top - imageRect.top) * scaleY), 0, bitmap.getHeight() - 1);
        int right = clamp(Math.round((cropRect.right - imageRect.left) * scaleX), left + 1, bitmap.getWidth());
        int bottom = clamp(Math.round((cropRect.bottom - imageRect.top) * scaleY), top + 1, bitmap.getHeight());
        int size = Math.min(right - left, bottom - top);

        return Bitmap.createBitmap(bitmap, left, top, size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetRects();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) {
            return;
        }

        canvas.drawBitmap(bitmap, null, imageRect, imagePaint);
        canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, cropRect.top, dimPaint);
        canvas.drawRect(imageRect.left, cropRect.bottom, imageRect.right, imageRect.bottom, dimPaint);
        canvas.drawRect(imageRect.left, cropRect.top, cropRect.left, cropRect.bottom, dimPaint);
        canvas.drawRect(cropRect.right, cropRect.top, imageRect.right, cropRect.bottom, dimPaint);
        canvas.drawRect(cropRect, borderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                draggingCrop = cropRect.contains(downX, downY);
                return draggingCrop;
            case MotionEvent.ACTION_MOVE:
                if (!draggingCrop) {
                    return false;
                }
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                moveCrop(dx, dy);
                downX = event.getX();
                downY = event.getY();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                draggingCrop = false;
                return true;
            default:
                return false;
        }
    }

    private void resetRects() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        float bitmapRatio = (float) bitmap.getWidth() / bitmap.getHeight();
        float viewRatio = (float) getWidth() / getHeight();
        float width;
        float height;
        if (bitmapRatio > viewRatio) {
            width = getWidth();
            height = width / bitmapRatio;
        } else {
            height = getHeight();
            width = height * bitmapRatio;
        }

        float left = (getWidth() - width) / 2f;
        float top = (getHeight() - height) / 2f;
        imageRect.set(left, top, left + width, top + height);

        float cropSize = cropBoxSizePx > 0 ? cropBoxSizePx : Math.min(imageRect.width(), imageRect.height()) * 0.72f;
        cropSize = Math.min(cropSize, Math.min(imageRect.width(), imageRect.height()));
        float cropLeft = imageRect.centerX() - cropSize / 2f;
        float cropTop = imageRect.centerY() - cropSize / 2f;
        cropRect.set(cropLeft, cropTop, cropLeft + cropSize, cropTop + cropSize);
    }

    private void moveCrop(float dx, float dy) {
        float nextLeft = cropRect.left + dx;
        float nextTop = cropRect.top + dy;
        float size = cropRect.width();

        nextLeft = Math.max(imageRect.left, Math.min(nextLeft, imageRect.right - size));
        nextTop = Math.max(imageRect.top, Math.min(nextTop, imageRect.bottom - size));
        cropRect.set(nextLeft, nextTop, nextLeft + size, nextTop + size);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
