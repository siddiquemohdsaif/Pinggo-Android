package com.w3n.wavestream.views.animator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.w3n.wavestream.views.animator.button.ButtonViewAnimator;
import com.w3n.wavestream.views.animator.core.BitmapRotateViewAnimator;
import com.w3n.wavestream.views.animator.core.BitmapSerialViewAnimator;
import com.w3n.wavestream.views.animator.core.BitmapViewAnimator;
import com.w3n.wavestream.views.animator.dialog.CustomViewDialog;
import com.w3n.wavestream.views.animator.dynamic.DynamicViewAnimator;
import com.w3n.wavestream.views.animator.dynamic.TypingDotsView;

import java.util.ArrayList;

public class WaveAnimatorView extends View {
    private final ArrayList<BitmapViewAnimator> bitmapAnimators = new ArrayList<>();
    private final ArrayList<BitmapRotateViewAnimator> rotateAnimators = new ArrayList<>();
    private final ArrayList<BitmapSerialViewAnimator> serialAnimators = new ArrayList<>();
    private final ArrayList<ButtonViewAnimator> buttonAnimators = new ArrayList<>();
    private final ArrayList<TextViewAnimator> textAnimators = new ArrayList<>();
    private final ArrayList<DynamicViewAnimator> dynamicAnimators = new ArrayList<>();
    private final ArrayList<CustomViewDialog> dialogs = new ArrayList<>();
    private TouchMissListener touchMissListener;

    public WaveAnimatorView(Context context) {
        super(context);
        init();
    }

    public WaveAnimatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaveAnimatorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setClickable(false);
    }

    public ArrayList<BitmapViewAnimator> getBitmapAnimators() {
        return bitmapAnimators;
    }

    public ArrayList<ButtonViewAnimator> getButtonAnimators() {
        return buttonAnimators;
    }

    public ArrayList<TextViewAnimator> getTextAnimators() {
        return textAnimators;
    }

    public ArrayList<DynamicViewAnimator> getDynamicAnimators() {
        return dynamicAnimators;
    }

    public ArrayList<CustomViewDialog> getDialogs() {
        return dialogs;
    }

    public void setTouchMissListener(TouchMissListener touchMissListener) {
        this.touchMissListener = touchMissListener;
    }

    public void showTypingDots(RectF rectF) {
        dynamicAnimators.clear();
        dynamicAnimators.add(new DynamicViewAnimator(new TypingDotsView(Color.GRAY), -1, rectF));
        invalidate();
    }

    public void hideTypingDots() {
        dynamicAnimators.clear();
        invalidate();
    }

    public void pulseBitmap(String id, Bitmap bitmap, RectF rectF) {
        BitmapViewAnimator.addAnimation(id, bitmapAnimators, bitmap, 0f, 1f,
                rectF.width() * 0.8f, rectF.width(), rectF.height() * 0.8f, rectF.height(),
                rectF.left + (rectF.width() * 0.1f), rectF.left,
                rectF.top + (rectF.height() * 0.1f), rectF.top, 180L, false,
                null, true);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        BitmapViewAnimator.Draw(canvas, bitmapAnimators);
        BitmapRotateViewAnimator.Draw(canvas, rotateAnimators);
        BitmapSerialViewAnimator.Draw(canvas, serialAnimators);
        ButtonViewAnimator.Draw(canvas, buttonAnimators);
        TextViewAnimator.Draw(canvas, textAnimators);
        DynamicViewAnimator.Draw(canvas, dynamicAnimators);
        CustomViewDialog.Draw(canvas, dialogs);

        if (!bitmapAnimators.isEmpty()
                || !rotateAnimators.isEmpty()
                || !serialAnimators.isEmpty()
                || !buttonAnimators.isEmpty()
                || !textAnimators.isEmpty()
                || !dynamicAnimators.isEmpty()
                || !dialogs.isEmpty()) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!dialogs.isEmpty()) {
            CustomViewDialog.HandleTouch(event, dialogs);
            invalidate();
            return true;
        }
        if (ButtonViewAnimator.HandleTouch(event, buttonAnimators)) {
            invalidate();
            return true;
        }
        if (TextViewAnimator.HandleTouch(event, textAnimators)) {
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP && touchMissListener != null) {
            touchMissListener.onTouchMiss(event);
        }
        return false;
    }

    public interface TouchMissListener {
        void onTouchMiss(MotionEvent event);
    }
}
