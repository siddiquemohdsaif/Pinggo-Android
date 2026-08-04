package com.w3n.wavestream.views.animator.dialog;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.w3n.wavestream.views.animator.button.Region;

public class MessageBubbleDialog extends CustomViewDialog {
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String message;
    private RectF cardRect = new RectF();
    private Region cardRegion;

    public MessageBubbleDialog(String message) {
        this.message = message;
        scrimPaint.setColor(0x66000000);
        cardPaint.setColor(Color.WHITE);
        textPaint.setColor(0xFF202124);
        textPaint.setTextSize(42f);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void onCreate(View view, boolean closable, String id, CustomDialogCallback callback) {
        super.onCreate(view, closable, id, callback);
        float width = view.getWidth() * 0.78f;
        float height = 180f;
        float left = (view.getWidth() - width) / 2f;
        float top = (view.getHeight() - height) / 2f;
        cardRect = new RectF(left, top, left + width, top + height);
        cardRegion = new Region(cardRect.left, cardRect.right, cardRect.top, cardRect.bottom, id);
    }

    @Override
    public void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), scrimPaint);
        canvas.drawRoundRect(cardRect, 24f, 24f, cardPaint);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float y = cardRect.centerY() - ((metrics.ascent + metrics.descent) / 2f);
        canvas.drawText(message, cardRect.centerX(), y, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return closeOutsideClick(event, cardRegion) || cardRegion.isRegionClicked(event.getX(), event.getY());
    }
}
