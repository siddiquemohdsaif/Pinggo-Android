package com.w3n.pinggo.views;

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
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageRect = new RectF();
    private final RectF cropRect = new RectF();

    private Bitmap bitmap;
    private int minimumCropBoxSizePx;
    private int maximumCropBoxSizePx = Integer.MAX_VALUE;
    private float downX;
    private float downY;
    private boolean draggingCrop;
    private boolean resizingCrop;
    private float resizeStartDistance;
    private float resizeStartSize;

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
        borderPaint.setStrokeWidth(px(8.25f));
        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.FILL);
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        resetRects();
        invalidate();
    }

    public void setCropBoxSizePx(int cropBoxSizePx) {
        setCropBoxSizeRangePx(cropBoxSizePx, cropBoxSizePx);
    }

    /** Makes the square crop box responsive within the supplied size range. */
    public void setCropBoxSizeRangePx(int minimumSizePx, int maximumSizePx) {
        if (minimumSizePx < 0 || maximumSizePx <= 0 || minimumSizePx > maximumSizePx) {
            throw new IllegalArgumentException("Invalid crop box size range");
        }
        minimumCropBoxSizePx = Math.round(px(minimumSizePx));
        maximumCropBoxSizePx = Math.round(px(maximumSizePx));
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
        drawResizeHandles(canvas);
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
                resizingCrop = isNearCropBorder(downX, downY);
                draggingCrop = !resizingCrop && cropRect.contains(downX, downY);
                if (resizingCrop) {
                    resizeStartDistance = distanceFromCropCenter(downX, downY);
                    resizeStartSize = cropRect.width();
                }
                return resizingCrop || draggingCrop;
            case MotionEvent.ACTION_MOVE:
                if (resizingCrop) {
                    resizeCrop(event.getX(), event.getY());
                    invalidate();
                    return true;
                }
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
                resizingCrop = false;
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

        float availableSize = Math.min(imageRect.width(), imageRect.height());
        float cropSize = Math.min(availableSize, maximumCropBoxSizePx);
        if (availableSize >= minimumCropBoxSizePx) {
            cropSize = Math.max(cropSize, minimumCropBoxSizePx);
        }
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

    private void resizeCrop(float touchX, float touchY) {
        float centerX = cropRect.centerX();
        float centerY = cropRect.centerY();
        float distance = Math.max(Math.abs(touchX - centerX), Math.abs(touchY - centerY));
        float requestedSize = resizeStartSize + 2f * (distance - resizeStartDistance);

        float maximumAroundCenter = 2f * Math.min(
                Math.min(centerX - imageRect.left, imageRect.right - centerX),
                Math.min(centerY - imageRect.top, imageRect.bottom - centerY)
        );
        float maximumSize = Math.min(maximumCropBoxSizePx, maximumAroundCenter);
        float minimumSize = Math.min(minimumCropBoxSizePx, maximumSize);
        float size = Math.max(minimumSize, Math.min(requestedSize, maximumSize));
        float halfSize = size / 2f;
        cropRect.set(
                centerX - halfSize,
                centerY - halfSize,
                centerX + halfSize,
                centerY + halfSize
        );
    }

    private boolean isNearCropBorder(float x, float y) {
        float touchTarget = px(66f);
        if (x < cropRect.left - touchTarget || x > cropRect.right + touchTarget
                || y < cropRect.top - touchTarget || y > cropRect.bottom + touchTarget) {
            return false;
        }
        float nearestEdge = Math.min(
                Math.min(Math.abs(x - cropRect.left), Math.abs(x - cropRect.right)),
                Math.min(Math.abs(y - cropRect.top), Math.abs(y - cropRect.bottom))
        );
        return nearestEdge <= touchTarget;
    }

    private float distanceFromCropCenter(float x, float y) {
        return Math.max(Math.abs(x - cropRect.centerX()), Math.abs(y - cropRect.centerY()));
    }

    private void drawResizeHandles(Canvas canvas) {
        float radius = px(16.5f);
        canvas.drawCircle(cropRect.left, cropRect.top, radius, handlePaint);
        canvas.drawCircle(cropRect.right, cropRect.top, radius, handlePaint);
        canvas.drawCircle(cropRect.left, cropRect.bottom, radius, handlePaint);
        canvas.drawCircle(cropRect.right, cropRect.bottom, radius, handlePaint);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private float px(float value) {
    return figmaConfig.toRuntime(value, Math.max(1, getResources().getDisplayMetrics().widthPixels));
  }

}
