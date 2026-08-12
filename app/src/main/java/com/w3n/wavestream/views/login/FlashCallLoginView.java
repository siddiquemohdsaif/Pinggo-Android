package com.w3n.wavestream.views.login;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.card.Card;
import com.ogfa.nativeviews.card.DropShadow;
import com.ogfa.nativeviews.component.FigmaConfig;
import com.ogfa.nativeviews.component.Position;
import com.ogfa.nativeviews.component.Size;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.wavestream.R;

import java.util.Locale;

/** Native-view implementation of the automatic flash-call verification screen. */
public class FlashCallLoginView extends View {
    private static final float REFERENCE_WIDTH = 1080f;
    private static final int ACCENT_COLOR = 0xFF019CC4;
    private static final int PRIMARY_TEXT_COLOR = 0xFF000E1A;
    private static final int MUTED_TEXT_COLOR = 0xFF656565;

    private final FigmaConfig figmaConfig = new FigmaConfig(REFERENCE_WIDTH);
    private final ZLayerGroup layerGroup = new ZLayerGroup(this);
    private final ZLayer backgroundLayer = layerGroup.addLayer("background");
    private final ZLayer foregroundLayer = layerGroup.addLayer("foreground");
    private final ZLayer cardLayer = layerGroup.addLayer("flash_call_card");
    private final Bitmap backgroundBitmap;
    private final Bitmap illustrationBitmap;
    private final Bitmap backBitmap;
    private final Bitmap spinnerBitmap = createSpinnerBitmap();
    private final Bitmap clockBitmap = createClockBitmap();
    private final String phoneNumber;

    private ZLayer cardContent;
    private Text smsCountdownText;
    private android.os.CountDownTimer countDownTimer;
    private int remainingSeconds = 60;
    private OnBackListener backListener;
    private OnSmsAvailableListener smsAvailableListener;
    private int statusBarInset;
    private int navigationBarInset;

    public FlashCallLoginView(Context context, @NonNull String phoneNumber) {
        super(context);
        this.phoneNumber = formatPhoneNumber(phoneNumber);
        setBackgroundColor(Color.WHITE);
        setClickable(true);
        setFocusable(true);
        backgroundBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.pinggo_login_background);
        illustrationBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.pinggo_flash_call_illustration);
        backBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_email_back);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && height > 0) buildScreen();
    }

    public void setInsets(int statusBarInset, int navigationBarInset) {
        int safeTop = Math.max(0, statusBarInset);
        int safeBottom = Math.max(0, navigationBarInset);
        if (this.statusBarInset == safeTop && this.navigationBarInset == safeBottom) return;
        this.statusBarInset = safeTop;
        this.navigationBarInset = safeBottom;
        if (getWidth() > 0 && getHeight() > 0) buildScreen();
    }

    public void setOnBackListener(OnBackListener listener) {
        backListener = listener;
    }

    public void setOnSmsAvailableListener(OnSmsAvailableListener listener) {
        smsAvailableListener = listener;
    }

    private void buildScreen() {
        backgroundLayer.clear();
        foregroundLayer.clear();
        cardLayer.clear();
        cardContent = null;
        smsCountdownText = null;

        backgroundLayer.add(new Image.Builder(getContext(), "login_background", backgroundBitmap,
                position(0f, 0f), new Size(getWidth(), getHeight()))
                .setScaleType(Image.ScaleType.CENTER_CROP));
        addBackButton();
        addIllustration();
        addLegalNotice();
        addVerificationCard();
        updateCountdownText();
        invalidate();
    }

    private void addBackButton() {
        foregroundLayer.add(new Button.Builder(getContext(), "back_button", backBitmap,
                position(87f, 85f + designUnits(statusBarInset)), new Size(64f, 64f))
                .setImageScaleType(Image.ScaleType.FIT_CENTER)
                .setRippleEnabled(true)
                .setRippleColor(0x22000000)
                .setOnClickListener(id -> {
                    if (backListener != null) backListener.onBack();
                }));
    }

    private void addIllustration() {
        foregroundLayer.add(new Image.Builder(getContext(), "flash_call_illustration",
                illustrationBitmap, position(0f, 333f+ designUnits(statusBarInset)), new Size(720f, 508f))
                .horizontalCenter(true)
                .setScaleType(Image.ScaleType.FIT_XY));
    }

    private void addVerificationCard() {
        Position cardPosition = new Position(this, figmaConfig,
                Position.HorizontalMarginFrom.LEFT, Position.VerticalMarginFrom.BOTTOM,
                0f, 206.620f + designUnits(navigationBarInset));
        Card card = cardLayer.add(new Card.Builder(getContext(), "phone_verification_card",
                cardPosition, new Size(940.563f, 900f))
                .setBackgroundColor(Color.WHITE)
                .setCornerRadius(63.380f)
                .horizontalCenter(true)
                .setDropShadow(new DropShadow(0f, 5.070f, 35.493f, 5.070f,
                        Color.argb(13, 0, 0, 0))));
        cardContent = card.getContentLayer();
        cardContent.setTouchPolicy(ZLayer.TouchPolicy.BLOCK_BELOW);

        addCardText(card, "title", getString(R.string.phone_verification),
                70f, 820f, 80f, 50f, PRIMARY_TEXT_COLOR, FontVariation.BOLD, 1);
        addCardText(card, "calling_message",
                getString(R.string.calling_your_phone, phoneNumber),
                165f, 820f, 70f, 33f, MUTED_TEXT_COLOR, FontVariation.REGULAR, 1);
        addCardText(card, "instructions", getString(R.string.flash_call_instructions),
                265f, 760f, 110f, 33f, MUTED_TEXT_COLOR, FontVariation.REGULAR, 2);

        addImage(card, "waiting_spinner", spinnerBitmap, 225f, 426f, 52f, 52f);
        addCardTextAt(card, "waiting_message", getString(R.string.waiting_for_call),
                300f, 411f, 570f, 80f, 34f,
                ACCENT_COLOR, FontVariation.REGULAR);

        addImage(card, "clock", clockBitmap, 205f, 764f, 42f, 42f);
        smsCountdownText = addCardTextAt(card, "sms_countdown", "",
                260f, 744f, 620f, 80f, 31f,
                0xFFA7ADB8, FontVariation.REGULAR);
    }

    private void addLegalNotice() {
        Position legalPosition = new Position(this, figmaConfig,
                Position.HorizontalMarginFrom.LEFT, Position.VerticalMarginFrom.BOTTOM,
                0f, 72.254f + designUnits(navigationBarInset));
        foregroundLayer.add(new Text.Builder(getContext(), "legal_notice", createLegalNotice(),
                legalPosition, new Size(671.831f, 91.268f))
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSize(29.155f)
                .setLineHeightPercent(130.2f)
                .setTextColor(MUTED_TEXT_COLOR)
                .setAlignment(Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .horizontalCenter(true)
                .setMaxLines(2));
    }

    private void startCountdown() {
        if (countDownTimer != null) countDownTimer.cancel();
        remainingSeconds = 60;
        updateCountdownText();
        countDownTimer = new android.os.CountDownTimer(60_000L, 1_000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingSeconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                updateCountdownText();
            }

            @Override
            public void onFinish() {
                remainingSeconds = 0;
                updateCountdownText();
                if (smsAvailableListener != null) smsAvailableListener.onSmsAvailable();
            }
        }.start();
    }

    private void updateCountdownText() {
        if (smsCountdownText == null) return;
        smsCountdownText.setText(remainingSeconds > 0
                ? getString(R.string.sms_available_countdown,
                        String.format(Locale.US, "%d:%02d",
                                remainingSeconds / 60, remainingSeconds % 60))
                : getString(R.string.sms_available_now));
        invalidate();
    }

    private void addCardText(Card card, String id, String value, float top, float width,
                             float height, float textSize, int color,
                             FontVariation variation, int maxLines) {
        cardContent.add(new Text.Builder(getContext(), id, value,
                cardPosition(card, 0f, top), new Size(width, height))
                .setFont(NativeFonts.INTER)
                .setFontVariations(variation)
                .setTextSize(textSize)
                .setTextColor(color)
                .setAlignment(Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .horizontalCenter(true)
                .setMaxLines(maxLines));
    }

    private Text addCardTextAt(Card card, String id, String value, float left, float top,
                               float width, float height, float textSize, int color,
                               FontVariation variation) {
        Text text = new Text.Builder(getContext(), id, value,
                cardPosition(card, left, top), new Size(width, height))
                .setFont(NativeFonts.INTER)
                .setFontVariations(variation)
                .setTextSize(textSize)
                .setTextColor(color)
                .setAlignment(Text.Alignment.START)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setWrapEnabled(false)
                .build(this);
        cardContent.add(text);
        return text;
    }

    private void addImage(Card card, String id, Bitmap bitmap, float left, float top,
                          float width, float height) {
        cardContent.add(new Image.Builder(getContext(), id, bitmap,
                cardPosition(card, left, top), new Size(width, height))
                .setScaleType(Image.ScaleType.FIT_XY));
    }

    private Position cardPosition(Card card, float left, float top) {
        float scale = figmaConfig.getScale(getWidth());
        android.graphics.RectF bounds = card.getBounds();
        return new Position(this, figmaConfig, Position.HorizontalMarginFrom.LEFT,
                Position.VerticalMarginFrom.TOP,
                bounds.left / scale + left, bounds.top / scale + top);
    }

    private Position position(float left, float top) {
        return new Position(this, figmaConfig, Position.HorizontalMarginFrom.LEFT,
                Position.VerticalMarginFrom.TOP, left, top);
    }

    private SpannableString createLegalNotice() {
        SpannableString notice = new SpannableString(getString(R.string.terms_notice));
        colorPhrase(notice, "Terms of Service");
        colorPhrase(notice, "Privacy Policy");
        return notice;
    }

    private static void colorPhrase(SpannableString text, String phrase) {
        int start = text.toString().indexOf(phrase);
        if (start >= 0) {
            text.setSpan(new ForegroundColorSpan(ACCENT_COLOR), start,
                    start + phrase.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static Bitmap createSpinnerBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int index = 0; index < 12; index++) {
            double angle = Math.toRadians(index * 30d);
            paint.setColor(Color.argb(55 + index * 16, 1, 156, 196));
            float x = 32f + (float) Math.cos(angle) * 23f;
            float y = 32f + (float) Math.sin(angle) * 23f;
            canvas.drawCircle(x, y, 3.5f, paint);
        }
        return bitmap;
    }

    private static Bitmap createClockBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(52, 52, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFFA7ADB8);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.5f);
        canvas.drawCircle(26f, 26f, 19f, paint);
        canvas.drawLine(26f, 26f, 26f, 14f, paint);
        canvas.drawLine(26f, 26f, 35f, 31f, paint);
        return bitmap;
    }

    private static String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return "";
        String normalized = phoneNumber.trim().replaceAll("\\s+", "");
        if (normalized.startsWith("+91") && normalized.length() == 13) {
            return "+91 " + normalized.substring(3, 8) + " " + normalized.substring(8);
        }
        return phoneNumber.trim();
    }

    private float designUnits(int pixels) {
        return getWidth() <= 0 ? 0f : pixels / figmaConfig.getScale(getWidth());
    }

    private String getString(int resourceId) {
        return getContext().getString(resourceId);
    }

    private String getString(int resourceId, Object... formatArgs) {
        return getContext().getString(resourceId, formatArgs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startCountdown();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        layerGroup.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return layerGroup.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return layerGroup.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    public interface OnBackListener {
        void onBack();
    }

    public interface OnSmsAvailableListener {
        void onSmsAvailable();
    }
}
