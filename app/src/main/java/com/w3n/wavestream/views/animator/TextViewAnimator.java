package com.w3n.wavestream.views.animator;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.w3n.wavestream.views.animator.button.Region;

import java.util.ArrayList;

public class TextViewAnimator {
    private static final float SHRINK_SCALE = 0.96f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final String id;
    private final String text;
    private final OnClickListener clickListener;
    private final Region region;
    private final Rect textBounds = new Rect();
    private RectF rectF;
    private boolean clickable;
    private boolean pressed;

    public TextViewAnimator(String id, String text, RectF rectF, float textSize,
                            int textColor, OnClickListener clickListener) {
        this(id, text, rectF, textSize, textColor, Paint.Align.CENTER, false, clickListener);
    }

    public TextViewAnimator(String id, String text, RectF rectF, float textSize,
                            int textColor, Paint.Align align, boolean bold,
                            OnClickListener clickListener) {
        this.id = id;
        this.text = text;
        this.rectF = new RectF(rectF);
        this.clickListener = clickListener;
        this.clickable = clickListener != null;
        this.region = new Region(rectF.left, rectF.right, rectF.top, rectF.bottom, id);
        paint.setColor(textColor);
        paint.setTextSize(textSize);
        paint.setTextAlign(align);
        paint.setFakeBoldText(bold);
    }

    public void setRect(RectF rectF) {
        this.rectF = new RectF(rectF);
        region.updateRegion(rectF.left, rectF.right, rectF.top, rectF.bottom);
    }

    public void setClickable(boolean clickable) {
        this.clickable = clickable;
    }

    public void onDraw(Canvas canvas) {
        float scale = pressed ? SHRINK_SCALE : 1f;
        canvas.save();
        canvas.scale(scale, scale, rectF.centerX(), rectF.centerY());
        paint.getTextBounds(text, 0, text.length(), textBounds);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float x;
        if (paint.getTextAlign() == Paint.Align.LEFT) {
            x = rectF.left;
        } else if (paint.getTextAlign() == Paint.Align.RIGHT) {
            x = rectF.right;
        } else {
            x = rectF.centerX();
        }
        float y = rectF.centerY() - ((metrics.ascent + metrics.descent) / 2f);
        canvas.drawText(text, x, y, paint);
        canvas.restore();
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (!clickable) {
            return false;
        }

        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (region.regionClickedDown(x, y)) {
                    pressed = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (pressed && !region.regionClickedMove(x, y)) {
                    pressed = false;
                    return false;
                }
                return pressed;
            case MotionEvent.ACTION_UP:
                if (pressed) {
                    pressed = false;
                    if (region.isRegionClicked(x, y) && clickListener != null) {
                        clickListener.onClick(id);
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                break;
            default:
                break;
        }
        return false;
    }

    public static TextViewAnimator create(String id, String text, RectF rectF, float textSize,
                                          OnClickListener clickListener) {
        return new TextViewAnimator(id, text, rectF, textSize, Color.WHITE, clickListener);
    }

    public static void Draw(Canvas canvas, ArrayList<TextViewAnimator> textAnimators) {
        for (TextViewAnimator animator : textAnimators) {
            animator.onDraw(canvas);
        }
    }

    public static boolean HandleTouch(MotionEvent event, ArrayList<TextViewAnimator> textAnimators) {
        for (TextViewAnimator animator : textAnimators) {
            if (animator.onTouchEvent(event)) {
                return true;
            }
        }
        return false;
    }

    public interface OnClickListener {
        void onClick(String id);
    }
}
