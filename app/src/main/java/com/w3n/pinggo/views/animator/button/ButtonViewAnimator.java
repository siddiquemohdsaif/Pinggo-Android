package com.w3n.pinggo.views.animator.button;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.w3n.pinggo.views.animator.utils.PixelRectF;

import java.util.ArrayList;
import java.util.Iterator;

public class ButtonViewAnimator {
    private static final float SHRINK_SCALE = 0.94f;
    private static final long PRESS_DURATION = 90L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final String id;
    private final OnClickListener clickListener;
    private final Region buttonRegion;
    private PixelRectF rectF;
    private Bitmap bitmap;
    private boolean clickable = true;
    private boolean pressed;
    private PressAnimation pressAnimation;

    public ButtonViewAnimator(OnClickListener clickListener, String id, Bitmap bitmap, RectF rectF) {
        this(clickListener, id, bitmap, PixelRectF.fromRect(rectF));
    }

    public ButtonViewAnimator(OnClickListener clickListener, String id, Bitmap bitmap, PixelRectF rectF) {
        this.clickListener = clickListener;
        this.id = id;
        this.bitmap = bitmap;
        this.rectF = new PixelRectF(rectF);
        this.buttonRegion = new Region(rectF.left, rectF.right, rectF.top, rectF.bottom, id);
        if (clickListener == null) {
            clickable = false;
        }
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public void setRect(RectF rectF) {
        setRect(PixelRectF.fromRect(rectF));
    }

    public void setRect(PixelRectF rectF) {
        this.rectF = new PixelRectF(rectF);
        buttonRegion.updateRegion(rectF.left, rectF.right, rectF.top, rectF.bottom);
    }

    public void onDraw(Canvas canvas) {
        if (bitmap == null) {
            return;
        }

        if (pressAnimation != null) {
            pressAnimation.applyAnimationPressed(canvas);
        }
        canvas.drawBitmap(bitmap, null, rectF, paint);
        if (pressAnimation != null) {
            pressAnimation.restoreAnimationPressed(canvas);
            if (pressAnimation.isAnimationFinished()) {
                pressAnimation = null;
            }
        }
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (!clickable) {
            return false;
        }

        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (buttonRegion.regionClickedDown(x, y)) {
                    pressed = true;
                    pressAnimation = new PressAnimation(true, System.currentTimeMillis(),
                            PRESS_DURATION, rectF.centerX(), rectF.centerY(), SHRINK_SCALE);
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (pressed && !buttonRegion.regionClickedMove(x, y)) {
                    pressed = false;
                    pressAnimation = new PressAnimation(false, System.currentTimeMillis(),
                            PRESS_DURATION, rectF.centerX(), rectF.centerY(), SHRINK_SCALE);
                    return false;
                }
                return pressed;
            case MotionEvent.ACTION_UP:
                if (pressed) {
                    pressed = false;
                    pressAnimation = new PressAnimation(false, System.currentTimeMillis(),
                            PRESS_DURATION, rectF.centerX(), rectF.centerY(), SHRINK_SCALE);
                    if (buttonRegion.isRegionClicked(x, y) && clickListener != null) {
                        clickListener.onClick(id);
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                pressAnimation = new PressAnimation(false, System.currentTimeMillis(),
                        PRESS_DURATION, rectF.centerX(), rectF.centerY(), SHRINK_SCALE);
                break;
            default:
                break;
        }
        return false;
    }

    public static void Draw(Canvas canvas, ArrayList<ButtonViewAnimator> buttons) {
        for (ButtonViewAnimator button : buttons) {
            button.onDraw(canvas);
        }
    }

    public static boolean HandleTouch(MotionEvent event, ArrayList<ButtonViewAnimator> buttons) {
        for (Iterator<ButtonViewAnimator> iterator = buttons.iterator(); iterator.hasNext(); ) {
            if (iterator.next().onTouchEvent(event)) {
                return true;
            }
        }
        return false;
    }

    public interface OnClickListener {
        void onClick(String id);
    }
}
