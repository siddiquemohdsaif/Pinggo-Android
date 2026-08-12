package com.w3n.wavestream.views.login;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.w3n.wavestream.R;
import com.w3n.wavestream.views.animator.TextViewAnimator;
import com.w3n.wavestream.views.animator.core.BitmapViewAnimator;
import com.w3n.wavestream.views.animator.utils.PixelRectF;

import java.util.ArrayList;

public class LoginHeaderAnimatorView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final ArrayList<BitmapViewAnimator> bitmapAnimators = new ArrayList<>();
    private final ArrayList<TextViewAnimator> textAnimators = new ArrayList<>();
    private Bitmap logoBitmap;

    public LoginHeaderAnimatorView(Context context) {
        super(context);
        init();
    }

    public LoginHeaderAnimatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LoginHeaderAnimatorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        logoBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pinggo_logo);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildAnimators(w);
    }

    private void buildAnimators(int width) {
        textAnimators.clear();
        bitmapAnimators.clear();

        int logoLeftPx = (getRootView().getWidth() - 242) / 2;
        PixelRectF logoRect = new PixelRectF(width,logoLeftPx, 22,
                logoLeftPx + 242, 22 + 242);
        float logoSize = logoRect.width();
        if (logoBitmap != null) {
            BitmapViewAnimator.addAnimation("login_logo_in", bitmapAnimators, logoBitmap, 0f, 1f,
                    logoSize * 0.82f, logoSize, logoSize * 0.82f, logoSize,
                    logoRect.left + logoSize * 0.09f, logoRect.left,
                    logoRect.top + logoSize * 0.09f, logoRect.top,
                    420L, false, null, false);
        }

        textAnimators.add(new TextViewAnimator("brand", getContext().getString(R.string.pinggo_brand),
                new PixelRectF(width, 0, 258, 1080, 395),
                124,
                ContextCompat.getColor(getContext(), R.color.pinggo_title),Paint.Align.CENTER,TextViewAnimator.FONT_INTER,TextViewAnimator.WEIGHT_BOLD, null));
        textAnimators.add(new TextViewAnimator("tagline", getContext().getString(R.string.pinggo_tagline),
                new PixelRectF(width,0, 406, 1080, 472),
                38,
                ContextCompat.getColor(getContext(), R.color.pinggo_body_text),Paint.Align.CENTER,TextViewAnimator.FONT_INTER,TextViewAnimator.WEIGHT_MEDIUM, null));
        textAnimators.add(new TextViewAnimator("security", getContext().getString(R.string.pinggo_security),
                new PixelRectF(width, 0, 481, 1080, 541),
                33,
                ContextCompat.getColor(getContext(), R.color.pinggo_action),Paint.Align.CENTER,TextViewAnimator.FONT_INTER,TextViewAnimator.WEIGHT_MEDIUM, null));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        BitmapViewAnimator.Draw(canvas, bitmapAnimators);
        TextViewAnimator.Draw(canvas, textAnimators);
        postInvalidateOnAnimation();
    }
}
