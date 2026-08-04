package com.w3n.wavestream.views.login;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.w3n.wavestream.R;
import com.w3n.wavestream.views.animator.TextViewAnimator;
import com.w3n.wavestream.views.animator.core.BitmapViewAnimator;

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

        float centerX = getWidth() / 2f;
        float logoSize = 0.224074f * getWidth();
        RectF logoRect = new RectF(centerX - logoSize / 2f, 0.020370f * getWidth(), centerX + logoSize / 2f, 0.020370f * getWidth() + logoSize);
        if (logoBitmap != null) {
            BitmapViewAnimator.addAnimation("login_logo_in", bitmapAnimators, logoBitmap, 0f, 1f,
                    logoSize * 0.82f, logoSize, logoSize * 0.82f, logoSize,
                    logoRect.left + logoSize * 0.09f, logoRect.left,
                    logoRect.top + logoSize * 0.09f, logoRect.top,
                    420L, false, null, false);
        }

        textAnimators.add(new TextViewAnimator("brand", getContext().getString(R.string.pinggo_brand),
                new RectF(0, 0.208796f * getWidth(), width, 0.336111f * getWidth()), 0.114583f * getWidth(),
                ContextCompat.getColor(getContext(), R.color.pinggo_title), null));
        textAnimators.add(new TextViewAnimator("tagline", getContext().getString(R.string.pinggo_tagline),
                new RectF(0, 0.346296f * getWidth(), width, 0.407407f * getWidth()), 0.035648f * getWidth(),
                ContextCompat.getColor(getContext(), R.color.pinggo_body_text), null));
        textAnimators.add(new TextViewAnimator("security", getContext().getString(R.string.pinggo_security),
                new RectF(0, 0.415046f * getWidth(), width, 0.471065f * getWidth()), 0.030556f * getWidth(),
                ContextCompat.getColor(getContext(), R.color.pinggo_action), null));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        BitmapViewAnimator.Draw(canvas, bitmapAnimators);
        if (bitmapAnimators.isEmpty() && logoBitmap != null) {
            float logoSize = 0.224074f * getWidth();
            float left = (getWidth() - logoSize) / 2f;
            canvas.drawBitmap(logoBitmap, null, new RectF(left, 0.020370f * getWidth(), left + logoSize, 0.020370f * getWidth() + logoSize), paint);
        }
        TextViewAnimator.Draw(canvas, textAnimators);
        postInvalidateOnAnimation();
    }
}
