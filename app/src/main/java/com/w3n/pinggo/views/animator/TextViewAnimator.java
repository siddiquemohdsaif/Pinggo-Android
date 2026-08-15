package com.w3n.pinggo.views.animator;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.view.MotionEvent;

import androidx.core.content.res.ResourcesCompat;

import com.w3n.pinggo.R;
import com.w3n.pinggo.views.animator.button.Region;
import com.w3n.pinggo.views.animator.utils.PixelRectF;

import java.util.ArrayList;

public class TextViewAnimator {
    private static final float SHRINK_SCALE = 0.96f;
    public static final String FONT_ROBOTO = "roboto";
    public static final String FONT_INTER = "inter";
    public static final String FONT_SANS_SERIF = "sans-serif";

    public static final int WEIGHT_THIN = 100;
    public static final int WEIGHT_EXTRA_LIGHT = 200;
    public static final int WEIGHT_LIGHT = 300;
    public static final int WEIGHT_REGULAR = 400;
    public static final int WEIGHT_MEDIUM = 500;
    public static final int WEIGHT_SEMI_BOLD = 600;
    public static final int WEIGHT_BOLD = 700;
    public static final int WEIGHT_EXTRA_BOLD = 800;
    public static final int WEIGHT_BLACK = 900;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final String id;
    private final String text;
    private final OnClickListener clickListener;
    private final Region region;
    private final Rect textBounds = new Rect();
    private PixelRectF rectF;
    private boolean clickable;
    private boolean pressed;

    public TextViewAnimator(String id, String text, RectF rectF, int textSizePx,
                            int textColor, OnClickListener clickListener) {
        this(id, text, PixelRectF.fromRect(rectF), textSizePx, textColor, clickListener);
    }

    public TextViewAnimator(String id, String text, PixelRectF rectF, int textSizePx,
                            int textColor, OnClickListener clickListener) {
        this(id, text, rectF, textSizePx, textColor, Paint.Align.CENTER, false, clickListener);
    }

    public TextViewAnimator(String id, String text, PixelRectF rectF, int textSizePx,
                            int textColor, Paint.Align align, boolean bold,
                            OnClickListener clickListener) {
        this(id, text, rectF, textSizePx, textColor, align, FONT_SANS_SERIF,
                bold ? WEIGHT_BOLD : WEIGHT_REGULAR, clickListener);
    }

    public TextViewAnimator(String id, String text, RectF rectF, int textSizePx,
                            int textColor, Paint.Align align, String fontFamily,
                            int fontWeight, OnClickListener clickListener) {
        this(id, text, PixelRectF.fromRect(rectF), textSizePx, textColor, align, fontFamily,
                fontWeight, clickListener);
    }

    public TextViewAnimator(String id, String text, PixelRectF rectF, int textSizePx,
                            int textColor, Paint.Align align, String fontFamily,
                            int fontWeight, OnClickListener clickListener) {
        this(null, id, text, rectF, textSizePx, textColor, align, fontFamily, fontWeight, clickListener);
    }

    public TextViewAnimator(Context context, String id, String text, RectF rectF, int textSizePx,
                            int textColor, Paint.Align align, String fontFamily,
                            int fontWeight, OnClickListener clickListener) {
        this(context, id, text, PixelRectF.fromRect(rectF), textSizePx, textColor, align,
                fontFamily, fontWeight, clickListener);
    }

    public TextViewAnimator(Context context, String id, String text, PixelRectF rectF, int textSizePx,
                            int textColor, Paint.Align align, String fontFamily,
                            int fontWeight, OnClickListener clickListener) {
        this.id = id;
        this.text = text;
        this.rectF = new PixelRectF(rectF);
        this.clickListener = clickListener;
        this.clickable = clickListener != null;
        this.region = new Region(rectF.left, rectF.right, rectF.top, rectF.bottom, id);
        paint.setColor(textColor);
        paint.setTextSize(textSizeInPixels(context, textSizePx));
        paint.setTextAlign(align);
        paint.setTypeface(createTypeface(context, fontFamily, fontWeight));
        paint.setFakeBoldText(Build.VERSION.SDK_INT < Build.VERSION_CODES.P && fontWeight >= WEIGHT_SEMI_BOLD);
    }

    private static float textSizeInPixels(Context context, int textSizePx) {
        int width = context != null
                ? context.getResources().getDisplayMetrics().widthPixels
                : Resources.getSystem().getDisplayMetrics().widthPixels;
        return textSizePx / 1080f * width;
    }

    private static Typeface createTypeface(Context context, String fontFamily, int fontWeight) {
        String family = fontFamily == null || fontFamily.trim().isEmpty()
                ? FONT_SANS_SERIF
                : fontFamily;
        int clampedWeight = Math.max(WEIGHT_THIN, Math.min(WEIGHT_BLACK, fontWeight));
        Typeface baseTypeface = getBaseTypeface(context, family);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(baseTypeface, clampedWeight, false);
        }
        return Typeface.create(baseTypeface, clampedWeight >= WEIGHT_SEMI_BOLD
                ? Typeface.BOLD
                : Typeface.NORMAL);
    }

    private static Typeface getBaseTypeface(Context context, String fontFamily) {
        if (context != null && FONT_INTER.equalsIgnoreCase(fontFamily)) {
            Typeface interTypeface = ResourcesCompat.getFont(context, R.font.inter_opsz_wght);
            if (interTypeface != null) {
                return interTypeface;
            }
        }
        return Typeface.create(fontFamily, Typeface.NORMAL);
    }

    public void setRect(RectF rectF) {
        setRect(PixelRectF.fromRect(rectF));
    }

    public void setRect(PixelRectF rectF) {
        this.rectF = new PixelRectF(rectF);
        region.updateRegion(rectF.left, rectF.right, rectF.top, rectF.bottom);
    }

    public void setClickable(boolean clickable) {
        this.clickable = clickable;
    }

    public void onDraw(Canvas canvas) {
        float scale = pressed ? SHRINK_SCALE : 1f;
        canvas.save();
        canvas.scale(scale, scale, rectF.centerX(), rectF.centerY());
        String[] lines = text.split("\\n", -1);
        String boundsText = text.replace('\n', ' ');
        paint.getTextBounds(boundsText, 0, boundsText.length(), textBounds);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float x;
        if (paint.getTextAlign() == Paint.Align.LEFT) {
            x = rectF.left;
        } else if (paint.getTextAlign() == Paint.Align.RIGHT) {
            x = rectF.right;
        } else {
            x = rectF.centerX();
        }
        float lineHeight = metrics.descent - metrics.ascent;
        float totalTextHeight = lineHeight * lines.length;
        float firstBaseline = rectF.centerY() - (totalTextHeight / 2f) - metrics.ascent;
        for (int i = 0; i < lines.length; i++) {
            canvas.drawText(lines[i], x, firstBaseline + (i * lineHeight), paint);
        }
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

    public static TextViewAnimator create(String id, String text, RectF rectF, int textSizePx,
                                          OnClickListener clickListener) {
        return create(id, text, PixelRectF.fromRect(rectF), textSizePx, clickListener);
    }

    public static TextViewAnimator create(String id, String text, PixelRectF rectF, int textSizePx,
                                          OnClickListener clickListener) {
        return new TextViewAnimator(id, text, rectF, textSizePx, Color.WHITE, clickListener);
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
