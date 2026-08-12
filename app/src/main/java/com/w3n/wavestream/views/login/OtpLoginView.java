package com.w3n.wavestream.views.login;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.CountDownTimer;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.ogfa.nativeviews.button.Button;
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

/** Native-view implementation shared by email, WhatsApp, and SMS OTP verification. */
public class OtpLoginView extends View {
    public enum Channel {
        EMAIL,
        WHATSAPP,
        SMS
    }

    private static final float REFERENCE_WIDTH = 1080f;
    private static final int PRIMARY_TEXT_COLOR = 0xFF17233A;
    private static final int MUTED_TEXT_COLOR = 0xFF4E5665;
    private static final int ACCENT_COLOR = 0xFF019CC4;

    private final FigmaConfig figmaConfig = new FigmaConfig(REFERENCE_WIDTH);
    private final ZLayerGroup layerGroup = new ZLayerGroup(this);
    private final ZLayer backgroundLayer = layerGroup.addLayer("background");
    private final ZLayer contentLayer = layerGroup.addLayer("content");
    private final Bitmap backgroundBitmap;
    private final Bitmap illustrationBitmap;
    private final Bitmap backBitmap;
    private final Bitmap digitBoxBitmap;
    private final Bitmap keyBitmap;
    private final Bitmap backspaceKeyBitmap;
    private final Channel channel;
    private final String identifier;
    private final StringBuilder otp = new StringBuilder(6);
    private final Text[] digitTexts = new Text[6];

    private Text countdownText;
    private Text otpErrorText;
    private CountDownTimer countDownTimer;
    private int remainingSeconds = 25;
    private boolean cursorVisible = true;
    private final Runnable cursorBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            cursorVisible = !cursorVisible;
            updateOtpDisplay();
            postDelayed(this, 500L);
        }
    };
    private OnBackListener backListener;
    private OnOtpCompleteListener otpCompleteListener;
    private int statusBarInset;

    public OtpLoginView(Context context, @NonNull Channel channel,
                        @NonNull String identifier) {
        super(context);
        this.channel = channel;
        this.identifier = channel == Channel.WHATSAPP || channel == Channel.SMS
                ? formatPhoneNumber(identifier) : identifier;
        setBackgroundColor(Color.WHITE);
        setClickable(true);
        setFocusable(true);
        backgroundBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.pinggo_login_background);
        int illustrationResource = R.drawable.pinggo_email_illustration;
        if (channel == Channel.WHATSAPP) {
            illustrationResource = R.drawable.pinggo_whatsapp_illustration;
        } else if (channel == Channel.SMS) {
            illustrationResource = R.drawable.pinggo_sms_illustration;
        }
        illustrationBitmap = BitmapFactory.decodeResource(
                getResources(), illustrationResource);
        backBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_email_back);
        digitBoxBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.otp_digit_box);
        keyBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.otp_key);
        backspaceKeyBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.otp_backspace_key);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && height > 0) buildScreen();
    }

    public void setStatusBarInset(int statusBarInset) {
        int safeInset = Math.max(0, statusBarInset);
        if (this.statusBarInset == safeInset) return;
        this.statusBarInset = safeInset;
        if (getWidth() > 0 && getHeight() > 0) buildScreen();
    }

    public String getOtp() {
        return otp.toString();
    }

    public void setOnBackListener(OnBackListener listener) {
        backListener = listener;
    }

    public void setOnOtpCompleteListener(OnOtpCompleteListener listener) {
        otpCompleteListener = listener;
    }

    private void buildScreen() {
        backgroundLayer.clear();
        contentLayer.clear();
        countdownText = null;
        otpErrorText = null;
        for (int index = 0; index < digitTexts.length; index++) digitTexts[index] = null;

        backgroundLayer.add(new Image.Builder(getContext(), "login_background", backgroundBitmap,
                position(0f, 0f), new Size(getWidth(), getHeight()))
                .setScaleType(Image.ScaleType.CENTER_CROP));
        addBackButton();
        addIllustration();
        addInstructions();
        addOtpBoxes();
        addOtpError();
        addResendMessage();
        addKeypad();
        updateOtpDisplay();
        updateCountdownText();
        invalidate();
    }

    private void addBackButton() {
        contentLayer.add(new Button.Builder(getContext(), "back_button", backBitmap,
                position(87f, 85f + designUnits(statusBarInset)), new Size(64f, 64f))
                .setImageScaleType(Image.ScaleType.FIT_CENTER)
                .setRippleEnabled(true)
                .setRippleColor(0x22000000)
                .setOnClickListener(id -> {
                    if (backListener != null) backListener.onBack();
                }));
    }

    private void addIllustration() {
        boolean isPhoneChannel = channel != Channel.EMAIL;
        float width = isPhoneChannel ? 410f : 405f;
        float height = isPhoneChannel ? 289f : 270f;
        float top = isPhoneChannel ? 243f : 288f;
        contentLayer.add(new Image.Builder(getContext(), "otp_illustration", illustrationBitmap,
                position(0f, top+designUnits(statusBarInset)), new Size(width, height))
                        .horizontalCenter(true)
                .setScaleType(Image.ScaleType.FIT_XY));
    }

    private void addInstructions() {
        boolean isPhoneChannel = channel != Channel.EMAIL;
        int descriptionResource = channel == Channel.WHATSAPP
                ? R.string.otp_whatsapp_description
                : channel == Channel.SMS
                        ? R.string.otp_sms_description : R.string.otp_email_description;
        addCenteredText("title", getString(R.string.enter_verification_code),
                670f, 830f, 75f, 51f, Color.BLACK, FontVariation.BOLD, 1);
        addCenteredText("description", getString(descriptionResource),
                755f, isPhoneChannel ? 900f : 760f, 60f, 38f,
                MUTED_TEXT_COLOR, FontVariation.REGULAR, 1);
        addCenteredText("identifier", identifier,
                807f, 900f, 65f, 38f, PRIMARY_TEXT_COLOR, FontVariation.BOLD, 1);
    }

    private void addOtpBoxes() {
        float boxWidth = 120f;
        float gap = 24f;
        float startX = 120f;
        for (int index = 0; index < digitTexts.length; index++) {
            float left = startX + index * (boxWidth + gap);
            contentLayer.add(new Image.Builder(getContext(), "otp_box_" + index,
                    digitBoxBitmap, position(left, 952f), new Size(boxWidth, 128f))
                    .setScaleType(Image.ScaleType.FIT_XY));
            Text digitText = new Text.Builder(getContext(), "otp_digit_" + index, "",
                    position(left, 952f), new Size(boxWidth, 128f))
                    .setFont(NativeFonts.INTER)
                    .setFontVariations(FontVariation.REGULAR)
                    .setTextSize(57f)
                    .setTextColor(PRIMARY_TEXT_COLOR)
                    .setAlignment(Text.Alignment.CENTER)
                    .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                    .setWrapEnabled(false)
                    .build(this);
            digitTexts[index] = digitText;
            contentLayer.add(digitText);
        }
    }

    private void addResendMessage() {
        addText("resend_prefix", getString(R.string.didnt_receive_code),
                255f, 1155f, 320f, 60f, 32f, MUTED_TEXT_COLOR);
        addText("resend_action", getString(R.string.resend),
                574f, 1155f, 125f, 60f, 32f, ACCENT_COLOR);
        countdownText = addText("resend_countdown", "",
                700f, 1155f, 190f, 60f, 32f, MUTED_TEXT_COLOR);
    }

    private void addOtpError() {
        otpErrorText = new Text.Builder(getContext(), "otp_error", "",
                position(0f, 1090f), new Size(800f, 50f))
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSize(27f)
                .setTextColor(0xFFD32F2F)
                .setAlignment(Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .horizontalCenter(true)
                .setMaxLines(1)
                .build(this);
        otpErrorText.setVisible(false);
        contentLayer.add(otpErrorText);
    }

    public void showOtpError(String message) {
        if (otpErrorText != null) otpErrorText.setText(message).setVisible(true);
        invalidate();
    }

    public void clearOtpError() {
        if (otpErrorText != null) otpErrorText.setVisible(false);
        invalidate();
    }

    private void addKeypad() {
        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}};
        float keyWidth = 310f;
        float keyHeight = 132f;
        float startX = 50f;
        float horizontalGap = 25f;
        float verticalGap = 18f;
        float lastRowBottomOffset = 286f;
        float rowStep = keyHeight + verticalGap;
        for (int row = 0; row < keys.length; row++) {
            float bottomOffset = lastRowBottomOffset + (3f - row) * rowStep;
            for (int column = 0; column < keys[row].length; column++) {
                String value = keys[row][column];
                addNumberKey(value,
                        startX + column * (keyWidth + horizontalGap),
                        bottomOffset, keyWidth, keyHeight);
            }
        }
        addNumberKey("0", startX + keyWidth + horizontalGap,
                lastRowBottomOffset, keyWidth, keyHeight);
        contentLayer.add(new Button.Builder(getContext(), "backspace_key", backspaceKeyBitmap,
                bottomPosition(startX + 2f * (keyWidth + horizontalGap), lastRowBottomOffset),
                new Size(keyWidth, keyHeight))
                .setImageScaleType(Image.ScaleType.FIT_XY)
                .setRippleEnabled(true)
                .setRippleColor(0x22019CC4)
                .setOnClickListener(id -> removeLastDigit()));
    }

    private void addNumberKey(String value, float left, float bottomOffset,
                              float width, float height) {
        contentLayer.add(new Button.Builder(getContext(), "key_" + value, keyBitmap, value,
                bottomPosition(left, bottomOffset), new Size(width, height))
                .setImageScaleType(Image.ScaleType.FIT_XY)
                .setCornerRadius(20f)
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSize(57f)
                .setTextColor(PRIMARY_TEXT_COLOR)
                .setRippleEnabled(true)
                .setRippleColor(0x22019CC4)
                .setOnClickListener(id -> appendDigit(value)));
    }

    private void appendDigit(String value) {
        if (otp.length() >= 6) return;
        clearOtpError();
        otp.append(value);
        updateOtpDisplay();
        if (otp.length() == 6 && otpCompleteListener != null) {
            otpCompleteListener.onOtpComplete(otp.toString());
        }
    }

    private void removeLastDigit() {
        if (otp.length() == 0) return;
        clearOtpError();
        otp.deleteCharAt(otp.length() - 1);
        updateOtpDisplay();
    }

    private void updateOtpDisplay() {
        for (int index = 0; index < digitTexts.length; index++) {
            if (digitTexts[index] != null) {
                digitTexts[index].setText(index < otp.length()
                        ? String.valueOf(otp.charAt(index))
                        : index == otp.length() && otp.length() < 6 && cursorVisible
                                ? "|" : "");
            }
        }
        invalidate();
    }

    private void startCountdown() {
        if (countDownTimer != null) countDownTimer.cancel();
        remainingSeconds = 25;
        updateCountdownText();
        countDownTimer = new CountDownTimer(25_000L, 1_000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingSeconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                updateCountdownText();
            }

            @Override
            public void onFinish() {
                remainingSeconds = 0;
                updateCountdownText();
            }
        }.start();
    }

    private void updateCountdownText() {
        if (countdownText == null) return;
        countdownText.setText(remainingSeconds > 0
                ? String.format(Locale.US, "in 00:%02d", remainingSeconds)
                : "");
        invalidate();
    }

    private Text addText(String id, String value, float left, float top, float width,
                         float height, float textSize, int color) {
        Text text = new Text.Builder(getContext(), id, value,
                position(left, top), new Size(width, height))
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSize(textSize)
                .setTextColor(color)
                .setAlignment(Text.Alignment.START)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setWrapEnabled(false)
                .build(this);
        contentLayer.add(text);
        return text;
    }

    private void addCenteredText(String id, String value, float top, float width,
                                 float height, float textSize, int color,
                                 FontVariation variation, int maxLines) {
        contentLayer.add(new Text.Builder(getContext(), id, value,
                position(0f, top), new Size(width, height))
                .setFont(NativeFonts.INTER)
                .setFontVariations(variation)
                .setTextSize(textSize)
                .setTextColor(color)
                .setAlignment(Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .horizontalCenter(true)
                .setMaxLines(maxLines));
    }

    private Position position(float left, float top) {
        return new Position(this, figmaConfig, Position.HorizontalMarginFrom.LEFT,
                Position.VerticalMarginFrom.TOP, left, top);
    }

    private Position bottomPosition(float left, float bottomOffset) {
        return new Position(this, figmaConfig, Position.HorizontalMarginFrom.LEFT,
                Position.VerticalMarginFrom.BOTTOM, left, bottomOffset);
    }

    private float designUnits(int pixels) {
        return getWidth() <= 0 ? 0f : pixels / figmaConfig.getScale(getWidth());
    }

    private String getString(int resourceId) {
        return getContext().getString(resourceId);
    }

    private static String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return "";
        String normalized = phoneNumber.trim().replaceAll("\\s+", "");
        if (normalized.startsWith("+91") && normalized.length() == 13) {
            return "+91 " + normalized.substring(3, 8) + " " + normalized.substring(8);
        }
        return phoneNumber.trim();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(cursorBlinkRunnable);
        cursorVisible = true;
        postDelayed(cursorBlinkRunnable, 500L);
        startCountdown();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(cursorBlinkRunnable);
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
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            appendDigit(String.valueOf(keyCode - KeyEvent.KEYCODE_0));
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            removeLastDigit();
            return true;
        }
        return layerGroup.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    public interface OnBackListener {
        void onBack();
    }

    public interface OnOtpCompleteListener {
        void onOtpComplete(String otp);
    }
}
