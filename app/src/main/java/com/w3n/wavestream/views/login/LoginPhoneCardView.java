package com.w3n.wavestream.views.login;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.hbb20.CCPCountry;
import com.hbb20.CountryCodePicker;
import com.w3n.wavestream.R;
import com.w3n.wavestream.views.animator.TextViewAnimator;
import com.w3n.wavestream.views.animator.button.ButtonViewAnimator;
import com.w3n.wavestream.views.animator.dialog.CustomViewDialog;
import com.w3n.wavestream.views.animator.dialog.MessageBubbleDialog;
import com.w3n.wavestream.views.animator.utils.PixelRectF;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.michaelrocks.libphonenumber.android.AsYouTypeFormatter;
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;

public class LoginPhoneCardView extends View {
    private static final int FIELD_NONE = 0;
    private static final int FIELD_PHONE = 1;
    private static final int FIELD_OTP = 2;
    private static final int FIELD_COUNTRY = 3;
    private static final int TEXT_SIZE_INPUT = 14;
    private static final int TEXT_SIZE_LABEL = 12;
    private static final float FIELD_HEIGHT_SCALE = 0.90f;
    private static final long CURSOR_BLINK_INTERVAL_MS = 300L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final ArrayList<TextViewAnimator> textAnimators = new ArrayList<>();
    private final ArrayList<ButtonViewAnimator> buttonAnimators = new ArrayList<>();
    private final ArrayList<CustomViewDialog> dialogs = new ArrayList<>();
    private final RectF cardRect = new RectF();
    private final RectF countryRect = new RectF();
    private final RectF countryDropdownRect = new RectF();
    private final RectF phoneRect = new RectF();
    private final RectF otpRect = new RectF();
    private final PixelRectF nextButtonRect = new PixelRectF(0f, 0f, 0f, 0f);
    private final PixelRectF confirmButtonRect = new PixelRectF(0f, 0f, 0f, 0f);
    private final RectF countryLabelBackgroundRect = new RectF();
    private final RectF phoneLabelBackgroundRect = new RectF();
    private final RectF countryLabelRect = new RectF();
    private final RectF phoneLabelRect = new RectF();
    private TextView countryLabelTextView;
    private TextView phoneLabelTextView;
    private CountryCodePicker countryCodePicker;
    private PhoneNumberUtil phoneNumberUtil;
    private PopupWindow countryPopupWindow;
    private Bitmap arrowBitmap;
    private Bitmap dropdownDownBitmap;
    private Bitmap dropdownUpBitmap;
    private Bitmap lockBitmap;
    private Bitmap selectedFlagBitmap;
    private AnimatorClickListener otpClickListener;
    private AnimatorClickListener confirmClickListener;
    private String phoneNumber = "";
    private String formattedPhoneNumber = "";
    private String otp = "";
    private String countryQuery = "";
    private String countryName = "India";
    private String countryCode = "+91";
    private final Editable inputEditable = new SpannableStringBuilder();
    private boolean otpVisible;
    private boolean otpRequestInProgress;
    private boolean otpVerifyInProgress;
    private boolean countryPopupOpen;
    private int activeField = FIELD_NONE;
    private int retryCountdownSeconds;
    private String pressedButtonId;

    public LoginPhoneCardView(Context context) {
        super(context);
        init();
    }

    public LoginPhoneCardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LoginPhoneCardView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        arrowBitmap = bitmapFromDrawable(R.drawable.ic_pinggo_arrow);
        dropdownDownBitmap = bitmapFromDrawable(R.drawable.ic_dropdown_down);
        dropdownUpBitmap = bitmapFromDrawable(R.drawable.ic_dropdown_up);
        lockBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_pinggo_lock);
        countryLabelTextView = createFloatingLabelTextView(R.string.country, R.color.pinggo_muted_text);
        phoneLabelTextView = createFloatingLabelTextView(R.string.phone_number, R.color.pinggo_action);
        phoneNumberUtil = PhoneNumberUtil.createInstance(getContext());
        countryCodePicker = new CountryCodePicker(getContext());
        countryCodePicker.setDefaultCountryUsingNameCode("IN");
        countryCodePicker.resetToDefaultCountry();
        syncSelectedCountry();
        countryCodePicker.setOnCountryChangeListener(() -> {
            syncSelectedCountry();
            invalidate();
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width == 0) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        int height = calculateHeight(width);
        setMeasuredDimension(width, height);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = activeField == FIELD_OTP
                ? InputType.TYPE_CLASS_NUMBER
                : activeField == FIELD_COUNTRY
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                : InputType.TYPE_CLASS_PHONE;
        outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE;
        return new BaseInputConnection(this, true) {
            @Override
            public Editable getEditable() {
                inputEditable.clear();
                if (activeField == FIELD_OTP) {
                    inputEditable.append(otp);
                } else if (activeField == FIELD_COUNTRY) {
                    inputEditable.append(countryQuery);
                } else {
                    inputEditable.append(phoneNumber);
                }
                return inputEditable;
            }

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                appendInput(text == null ? "" : text.toString(), false);
                return true;
            }

            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition) {
                appendInput(text == null ? "" : text.toString(), true);
                return true;
            }

            @Override
            public boolean performEditorAction(int editorAction) {
                if (editorAction == EditorInfo.IME_ACTION_DONE
                        || editorAction == EditorInfo.IME_ACTION_GO
                        || editorAction == EditorInfo.IME_ACTION_NEXT
                        || editorAction == EditorInfo.IME_ACTION_SEND
                        || editorAction == EditorInfo.IME_ACTION_UNSPECIFIED) {
                    activeField = FIELD_NONE;
                    hideKeyboard();
                    invalidate();
                    return true;
                }
                return super.performEditorAction(editorAction);
            }

            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                deleteLastInputCharacter();
                return true;
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                        activeField = FIELD_NONE;
                        hideKeyboard();
                        invalidate();
                        return true;
                    }
                    if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
                        deleteLastInputCharacter();
                        return true;
                    }
                    if (activeField == FIELD_COUNTRY) {
                        char unicodeChar = (char) event.getUnicodeChar();
                        if (!Character.isISOControl(unicodeChar)) {
                            appendInput(String.valueOf(unicodeChar), false);
                            return true;
                        }
                    } else {
                        int digit = digitFromKeyCode(event.getKeyCode());
                        if (digit >= 0) {
                            appendInput(String.valueOf(digit), false);
                            return true;
                        }
                    }
                }
                return super.sendKeyEvent(event);
            }
        };
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return activeField == FIELD_PHONE || activeField == FIELD_OTP || activeField == FIELD_COUNTRY;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            activeField = FIELD_NONE;
            if (countryPopupWindow != null) {
                countryPopupWindow.dismiss();
            }
            hideKeyboard();
            invalidate();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            deleteLastInputCharacter();
            return true;
        }
        if (activeField == FIELD_COUNTRY) {
            char unicodeChar = (char) event.getUnicodeChar();
            if (!Character.isISOControl(unicodeChar)) {
                appendInput(String.valueOf(unicodeChar), false);
                return true;
            }
        } else {
            int digit = digitFromKeyCode(keyCode);
            if (digit >= 0) {
                appendInput(String.valueOf(digit), false);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        buildLayout();
        drawCard(canvas);
        drawInputs(canvas);
        TextViewAnimator.Draw(canvas, textAnimators);
        drawInputCursors(canvas);
        ButtonViewAnimator.Draw(canvas, buttonAnimators);
        CustomViewDialog.Draw(canvas, dialogs);
        if (!dialogs.isEmpty()) {
            postInvalidateOnAnimation();
        } else if (hasActiveInputCursor()) {
            postInvalidateDelayed(CURSOR_BLINK_INTERVAL_MS);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!dialogs.isEmpty()) {
            CustomViewDialog.HandleTouch(event, dialogs);
            invalidate();
            return true;
        }
        if (handleAnimatorButtonTouch(event)) {
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (countryRect.contains(event.getX(), event.getY())) {
                if (countryDropdownRect.contains(event.getX(), event.getY())) {
                    toggleCountryPopup();
                } else {
                    focusCountryField();
                }
                return true;
            }
            if (phoneRect.contains(event.getX(), event.getY())) {
                focusField(FIELD_PHONE);
                return true;
            }
            if (otpVisible && otpRect.contains(event.getX(), event.getY())) {
                focusField(FIELD_OTP);
                return true;
            }
        }
        return true;
    }

    public void setOnOtpAnimatorClickListener(AnimatorClickListener listener) {
        otpClickListener = listener;
        invalidate();
    }

    public void setOnConfirmAnimatorClickListener(AnimatorClickListener listener) {
        confirmClickListener = listener;
        invalidate();
    }

    public void refreshAnimators() {
        invalidate();
    }

    public void setOtpRequestInProgress(boolean otpRequestInProgress) {
        this.otpRequestInProgress = otpRequestInProgress;
        invalidate();
    }

    public void setOtpVerifyInProgress(boolean otpVerifyInProgress) {
        this.otpVerifyInProgress = otpVerifyInProgress;
        invalidate();
    }

    public void setRetryCountdownSeconds(int retryCountdownSeconds) {
        this.retryCountdownSeconds = Math.max(0, retryCountdownSeconds);
        invalidate();
    }

    public void showAnimatorDialog(String message) {
        CustomViewDialog.addDialog(dialogs, new MessageBubbleDialog(message),
                this, true, "login_card_message", id -> invalidate());
        invalidate();
    }

    public void showOtpFields() {
        otpVisible = true;
        activeField = FIELD_OTP;
        requestLayout();
        post(() -> {
            requestFocus();
            showKeyboard();
            invalidate();
        });
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getFullNumberWithPlus() {
        return countryCode + phoneNumber;
    }

    public boolean isValidFullNumber() {
        try {
            Phonenumber.PhoneNumber parsedNumber = phoneNumberUtil.parse(
                    countryCode + phoneNumber,
                    countryCodePicker.getSelectedCountryNameCode()
            );
            return phoneNumberUtil.isValidNumberForRegion(parsedNumber, countryCodePicker.getSelectedCountryNameCode());
        } catch (Exception ignored) {
            return false;
        }
    }

    public String getOtp() {
        return otp;
    }

    public boolean isCountryFieldActive() {
        return activeField == FIELD_COUNTRY;
    }

    public boolean isRawTouchInsideCountryField(float rawX, float rawY) {
        buildLayout();
        int[] location = new int[2];
        getLocationOnScreen(location);
        float localX = rawX - location[0];
        float localY = rawY - location[1];
        return countryRect.contains(localX, localY);
    }

    public boolean handleCountryOutsideTap(boolean keyboardVisible) {
        if (countryPopupWindow == null || !countryPopupWindow.isShowing()) {
            return false;
        }
        if (activeField == FIELD_COUNTRY && keyboardVisible) {
            hideKeyboardOnly();
            return true;
        }
        countryPopupWindow.dismiss();
        return true;
    }

    public void updateCountryPopupPosition() {
        if (countryPopupWindow == null || !countryPopupWindow.isShowing()) {
            return;
        }
        buildLayout();
        int[] location = new int[2];
        getLocationOnScreen(location);
        countryPopupWindow.update(
                location[0] + Math.round(countryRect.left),
                location[1] + Math.round(countryRect.bottom),
                Math.round(countryRect.width()),
                Math.max(dp(72), Math.round(cardRect.bottom - countryRect.bottom))
        );
    }

    public int getActiveInputBottomOnScreen() {
        buildLayout();
        RectF activeRect = activeField == FIELD_COUNTRY
                ? countryRect
                : activeField == FIELD_OTP
                ? otpRect
                : phoneRect;
        if (activeRect.isEmpty()) {
            activeRect = phoneRect;
        }
        int[] location = new int[2];
        getLocationOnScreen(location);
        return location[1] + Math.round(activeRect.bottom);
    }

    private int calculateHeight(int width) {
        float w = getRefWidth();
        float height = 0.071296f * w
                + textHeight(20)
                + 0.030556f * w
                + (textHeight(13) * 2f)
                + 0.045833f * w
                + getCountryFieldHeight(w)
                + 0.030556f * w
                + getPhoneFieldHeight(w)
                + 0.045833f * w
                + 0.040741f * w
                + 0.063657f * w
                + 0.127315f * w
                + 0.063657f * w;
        if (otpVisible) {
            height += 0.045833f * w
                    + textHeight(14)
                    + 0.020370f * w
                    + getOtpFieldHeight(w)
                    + 0.040741f * w
                    + 0.142593f * w;
        }
        return Math.round(height + getShadowExtraHeight());
    }

    private void buildLayout() {
        int width = getWidth();
        float w = getRefWidth();
        float horizontal = 0.063657f * w;
        float y = 0.071296f * w;
        cardRect.set(0, 0, getWidth(), getHeight() - getShadowExtraHeight());
        textAnimators.clear();
        buttonAnimators.clear();

        addText("title", getString(R.string.enter_phone_number),
                new PixelRectF(horizontal, y, width - horizontal, y + textHeight(20)),
                20, R.color.primary_text, TextViewAnimator.WEIGHT_BOLD, Paint.Align.CENTER);
        y += textHeight(20) + 0.030556f * w;

        addText("hint", getString(R.string.verification_code_hint),
                new PixelRectF(horizontal, y, width - horizontal, y + textHeight(13) * 2f),
                13, R.color.pinggo_body_text, TextViewAnimator.WEIGHT_REGULAR, Paint.Align.CENTER);
        y += textHeight(13) * 2f + 0.045833f * w;

        countryRect.set(horizontal, y, width - horizontal, y + getCountryFieldHeight(w));
        addLabel("country_label", getString(R.string.country), countryRect.left + 0.035648f * w,
                countryRect.top, R.color.pinggo_muted_text);
        y = countryRect.bottom + 0.030556f * w;

        phoneRect.set(horizontal, y, width - horizontal, y + getPhoneFieldHeight(w));
        addLabel("phone_label", getString(R.string.phone_number), phoneRect.left + 0.035648f * w,
                phoneRect.top, R.color.pinggo_action);
        y = phoneRect.bottom + 0.045833f * w;

        addText("security", getString(R.string.phone_safe_message),
                new PixelRectF(horizontal + 0.071111f * w, y, width - horizontal, y + 0.040741f * w),
                13, R.color.pinggo_body_text, TextViewAnimator.WEIGHT_REGULAR, Paint.Align.LEFT);
        y += 0.040741f * w + 0.063657f * w;

        nextButtonRect.set(horizontal, y, width - horizontal, y + 0.127315f * w);
        boolean otpButtonEnabled = !otpRequestInProgress && (!otpVisible || retryCountdownSeconds == 0);
        addButton("next", nextButtonRect, getOtpButtonLabel(), otpButtonEnabled, id -> {
            if (otpClickListener != null) {
                otpClickListener.onClick();
            }
        });
        y = nextButtonRect.bottom;

        if (otpVisible) {
            y += 0.045833f * w;
            addText("otp_hint", getString(R.string.otp_hint),
                    new PixelRectF(horizontal, y, width - horizontal, y + textHeight(14)),
                    14, R.color.secondary_text, TextViewAnimator.WEIGHT_REGULAR, Paint.Align.CENTER);
            y += textHeight(14) + 0.020370f * w;
            otpRect.set(horizontal, y, width - horizontal, y + getOtpFieldHeight(w));
            y = otpRect.bottom + 0.040741f * w;
            confirmButtonRect.set(horizontal, y, width - horizontal, y + 0.142593f * w);
            addButton("confirm", confirmButtonRect, getConfirmButtonLabel(), !otpVerifyInProgress, id -> {
                if (confirmClickListener != null) {
                    confirmClickListener.onClick();
                }
            });
        } else {
            otpRect.setEmpty();
            confirmButtonRect.setEmpty();
        }
    }

    private void drawCard(Canvas canvas) {
        paint.setColor(ContextCompat.getColor(getContext(), R.color.white));
        paint.setStyle(Paint.Style.FILL);
        paint.setShadowLayer(dp(10), 0, dp(6), 0x22000000);
        canvas.drawRoundRect(cardRect, dp(24), dp(24), paint);
        paint.clearShadowLayer();
    }

    private void drawInputs(Canvas canvas) {
        float w = getRefWidth();
        boolean countryFocused = activeField == FIELD_COUNTRY;
        boolean countryActive = countryFocused || countryPopupOpen;
        boolean phoneFocused = activeField == FIELD_PHONE;
        drawRoundedBox(canvas, countryRect, R.color.white,
                countryActive ? R.color.pinggo_action : R.color.pinggo_input_stroke,
                countryActive ? dp(2) : dp(1), dp(12));
        drawLabelBackground(canvas, countryLabelBackgroundRect);
        drawSelectedFlag(canvas, countryRect.left + 0.045833f * w, countryRect.centerY() - 0.025f * w,
                0.081481f * w, 0.05f * w);
        String countryDisplayText = countryFocused ? countryQuery : countryName;
        float countryValueLeft = countryRect.left + 0.172222f * w;
        PixelRectF countryValueRect = new PixelRectF(countryValueLeft, countryRect.top,
                countryRect.right - 0.112037f * w, countryRect.bottom);
        addText("country_value", countryDisplayText,
                countryValueRect,
                TEXT_SIZE_INPUT, R.color.primary_text,
                TextViewAnimator.WEIGHT_REGULAR, Paint.Align.LEFT);
        drawDropdown(canvas);

        drawRoundedBox(canvas, phoneRect, R.color.white,
                phoneFocused ? R.color.pinggo_action : R.color.pinggo_input_stroke,
                phoneFocused ? dp(2) : dp(1), dp(12));
        drawLabelBackground(canvas, phoneLabelBackgroundRect);
        float phoneCodeLeft = phoneRect.left + 0.045833f * w;
        float dividerX = phoneRect.left + 0.185f * w;
        addText("phone_code", countryCode,
                new PixelRectF(phoneCodeLeft, phoneRect.top, dividerX - 0.025f * w, phoneRect.bottom),
                TEXT_SIZE_INPUT, R.color.primary_text, TextViewAnimator.WEIGHT_REGULAR, Paint.Align.LEFT);
        paint.setColor(ContextCompat.getColor(getContext(), R.color.pinggo_input_stroke));
        paint.setStrokeWidth(dp(1));
        canvas.drawLine(dividerX, phoneRect.centerY() - 0.031829f * w, dividerX, phoneRect.centerY() + 0.031829f * w, paint);
        float phoneValueLeft = dividerX + 0.045f * w;
        PixelRectF phoneValueRect = new PixelRectF(phoneValueLeft, phoneRect.top,
                phoneRect.right - 0.035648f * w, phoneRect.bottom);
        addText("phone_value", phoneNumber.isEmpty() ? getString(R.string.phone_number) : formattedPhoneNumber,
                phoneValueRect,
                TEXT_SIZE_INPUT, phoneNumber.isEmpty() ? R.color.secondary_text : R.color.primary_text,
                TextViewAnimator.WEIGHT_REGULAR, Paint.Align.LEFT);

        drawLock(canvas);

        if (otpVisible) {
            drawRoundedBox(canvas, otpRect, R.color.white, R.color.pinggo_input_stroke, dp(1), dp(12));
            addText("otp_value", otp.isEmpty() ? getString(R.string.enter_otp) : otp,
                    new PixelRectF(otpRect.left + 0.040741f * w, otpRect.top,
                            otpRect.right - 0.040741f * w, otpRect.bottom),
                    TEXT_SIZE_INPUT, otp.isEmpty() ? R.color.secondary_text : R.color.primary_text,
                    TextViewAnimator.WEIGHT_REGULAR, Paint.Align.LEFT);
        }

        drawFloatingLabel(canvas, countryLabelTextView, countryLabelRect,
                countryActive ? R.color.pinggo_action : R.color.pinggo_muted_text);
        drawFloatingLabel(canvas, phoneLabelTextView, phoneLabelRect,
                phoneFocused ? R.color.pinggo_action : R.color.pinggo_muted_text);
    }

    private void drawInputCursors(Canvas canvas) {
        if (!shouldDrawCursor()) {
            return;
        }
        float w = getRefWidth();
        if (activeField == FIELD_COUNTRY) {
            drawCursor(canvas, true,
                    new RectF(countryRect.left + 0.172222f * w, countryRect.top,
                            countryRect.right - 0.112037f * w, countryRect.bottom),
                    countryQuery);
        } else if (activeField == FIELD_PHONE) {
            float dividerX = phoneRect.left + 0.185f * w;
            float phoneValueLeft = dividerX + 0.045f * w;
            drawCursor(canvas, true,
                    new RectF(phoneValueLeft, phoneRect.top,
                            phoneRect.right - 0.035648f * w, phoneRect.bottom),
                    phoneNumber.isEmpty() ? "" : formattedPhoneNumber);
        } else if (activeField == FIELD_OTP) {
            drawCursor(canvas, true,
                    new RectF(otpRect.left + 0.040741f * w, otpRect.top,
                            otpRect.right - 0.040741f * w, otpRect.bottom),
                    otp);
        }
    }

    private boolean hasActiveInputCursor() {
        return activeField == FIELD_COUNTRY || activeField == FIELD_PHONE || activeField == FIELD_OTP;
    }

    private boolean shouldDrawCursor() {
        return hasActiveInputCursor()
                && ((System.currentTimeMillis() / CURSOR_BLINK_INTERVAL_MS) % 2L == 0L);
    }

    private void drawRoundedBox(Canvas canvas, RectF rect, int fillColorId, int strokeColorId, float strokeWidth, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ContextCompat.getColor(getContext(), fillColorId));
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(ContextCompat.getColor(getContext(), strokeColorId));
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSelectedFlag(Canvas canvas, float left, float top, float width, float height) {
        if (selectedFlagBitmap == null) {
            return;
        }
        canvas.drawBitmap(selectedFlagBitmap, null, new RectF(left, top, left + width, top + height), paint);
    }

    private void drawDropdown(Canvas canvas) {
        float w = getRefWidth();
        float cx = countryRect.right - 0.076389f * w;
        float cy = countryRect.centerY();
        float size = 0.061111f * w;
        RectF dropdownRect = new RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
        Bitmap dropdownBitmap = countryPopupOpen ? dropdownUpBitmap : dropdownDownBitmap;
        if (dropdownBitmap != null) {
            canvas.drawBitmap(dropdownBitmap, null, dropdownRect, paint);
        }
        countryDropdownRect.set(countryRect.right - 0.16f * w, countryRect.top,
                countryRect.right, countryRect.bottom);
    }

    private void drawLock(Canvas canvas) {
        if (lockBitmap == null) {
            return;
        }
        float w = getRefWidth();
        float size = 0.035648f * w;
        float securityTop = phoneRect.bottom + 0.045833f * w;
        RectF rect = new RectF(phoneRect.left + 0.022f * w, securityTop + 0.0018f * w,
                phoneRect.left + 0.022f * w + size - 0.0018f * w, securityTop + 0.0032f * w + size);
        canvas.drawBitmap(lockBitmap, null, rect, paint);
    }

    private void drawCursor(Canvas canvas, boolean active, RectF textRect, String value) {
        if (!active) {
            return;
        }
        Paint cursorPaint = paint;
        cursorPaint.setStyle(Paint.Style.FILL);
        cursorPaint.setTextSize(textSizeInPixels(TEXT_SIZE_INPUT));
        cursorPaint.setTextAlign(Paint.Align.LEFT);
        cursorPaint.setSubpixelText(true);
        Typeface interTypeface = ResourcesCompat.getFont(getContext(), R.font.inter_opsz_wght);
        if (interTypeface != null) {
            cursorPaint.setTypeface(interTypeface);
        }
        cursorPaint.setStrokeWidth(dp(2));
        cursorPaint.setColor(ContextCompat.getColor(getContext(), R.color.pinggo_action));
        Paint.FontMetrics metrics = cursorPaint.getFontMetrics();
        float lineHeight = metrics.descent - metrics.ascent;
        float baseline = textRect.centerY() - (lineHeight / 2f) - metrics.ascent;
        float x = textRect.left + cursorPaint.measureText(value == null ? "" : value) + dp(3);
        x = Math.min(x, textRect.right - dp(2));
        canvas.drawLine(x, baseline + metrics.ascent, x, baseline + metrics.descent, cursorPaint);
    }

    private boolean handleAnimatorButtonTouch(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (nextButtonRect.isEmpty()) {
                buildLayout();
            }
            pressedButtonId = buttonIdAt(event.getX(), event.getY());
            if (pressedButtonId != null) {
                ButtonViewAnimator.HandleTouch(event, buttonAnimators);
                invalidate();
                return true;
            }
            return false;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            String buttonId = pressedButtonId;
            pressedButtonId = null;
            if (buttonId != null && isButtonTouchInside(buttonId, event.getX(), event.getY())) {
                if ("next".equals(buttonId) && otpClickListener != null) {
                    otpClickListener.onClick();
                } else if ("confirm".equals(buttonId) && confirmClickListener != null) {
                    confirmClickListener.onClick();
                }
                invalidate();
                return true;
            }
            invalidate();
            return buttonId != null;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            pressedButtonId = null;
            ButtonViewAnimator.HandleTouch(event, buttonAnimators);
            invalidate();
            return false;
        }

        if (pressedButtonId != null) {
            ButtonViewAnimator.HandleTouch(event, buttonAnimators);
            invalidate();
            return true;
        }
        return false;
    }

    private String buttonIdAt(float x, float y) {
        if (nextButtonRect.contains(x, y)) {
            return "next";
        }
        if (otpVisible && confirmButtonRect.contains(x, y)) {
            return "confirm";
        }
        return null;
    }

    private boolean isButtonTouchInside(String buttonId, float x, float y) {
        if ("next".equals(buttonId)) {
            return nextButtonRect.contains(x, y);
        }
        return "confirm".equals(buttonId) && confirmButtonRect.contains(x, y);
    }

    private void addText(String id, String text, PixelRectF rect, int textSize, int colorId,
                         int weight, Paint.Align align) {
        textAnimators.add(new TextViewAnimator(
                getContext(), id, text, rect, textSizePx(textSize),
                ContextCompat.getColor(getContext(), colorId), align,
                TextViewAnimator.FONT_INTER, weight, null));
    }

    private void addLabel(String id, String text, float left, float top, int colorId) {
        TextView labelTextView = "country_label".equals(id) ? countryLabelTextView : phoneLabelTextView;
        int[] labelSize = measureFloatingLabel(labelTextView);
        float labelWidth = labelSize[0];
        float labelHeight = labelSize[1];
        float labelTop = top - (labelHeight * 0.36f);
        RectF background = "country_label".equals(id) ? countryLabelBackgroundRect : phoneLabelBackgroundRect;
        background.set(left, labelTop, left + labelWidth, labelTop + labelHeight);
        RectF labelRect = "country_label".equals(id) ? countryLabelRect : phoneLabelRect;
        labelRect.set(background);
    }

    private void drawLabelBackground(Canvas canvas, RectF background) {
        if (background.isEmpty()) {
            return;
        }
        paint.setColor(ContextCompat.getColor(getContext(), R.color.white));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(background, paint);
    }

    private TextView createFloatingLabelTextView(int stringId, int colorId) {
        TextView textView = new TextView(getContext());
        textView.setText(stringId);
        textView.setGravity(android.view.Gravity.CENTER_VERTICAL);
        textView.setIncludeFontPadding(false);
        textView.setTextColor(ContextCompat.getColor(getContext(), colorId));
        textView.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.white));
        int horizontalPadding = (int) (0.010185f * getRefWidth());
        textView.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        textView.setTypeface(ResourcesCompat.getFont(getContext(), R.font.inter_opsz_wght));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizeInPixels(TEXT_SIZE_LABEL));
        return textView;
    }

    private void drawFloatingLabel(Canvas canvas, TextView textView, RectF rect, int colorId) {
        if (textView == null || rect.isEmpty()) {
            return;
        }
        textView.setTextColor(ContextCompat.getColor(getContext(), colorId));
        int widthSpec = MeasureSpec.makeMeasureSpec(Math.round(rect.width()), MeasureSpec.EXACTLY);
        int heightSpec = MeasureSpec.makeMeasureSpec(Math.round(rect.height()), MeasureSpec.EXACTLY);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizeInPixels(TEXT_SIZE_LABEL));
        textView.measure(widthSpec, heightSpec);
        textView.layout(0, 0, Math.round(rect.width()), Math.round(rect.height()));
        canvas.save();
        canvas.translate(rect.left, rect.top);
        textView.draw(canvas);
        canvas.restore();
    }

    private int[] measureFloatingLabel(TextView textView) {
        if (textView == null) {
            return new int[]{0, 0};
        }
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizeInPixels(TEXT_SIZE_LABEL));
        textView.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        return new int[]{textView.getMeasuredWidth() + dp(2), textView.getMeasuredHeight() + dp(2)};
    }

    private String getOtpButtonLabel() {
        if (otpRequestInProgress) {
            return getString(R.string.sending_otp);
        }
        if (otpVisible && retryCountdownSeconds > 0) {
            return getContext().getString(R.string.retry_otp_countdown, retryCountdownSeconds);
        }
        if (otpVisible) {
            return getString(R.string.retry_otp);
        }
        return getString(R.string.get_otp);
    }

    private String getConfirmButtonLabel() {
        return otpVerifyInProgress ? getString(R.string.verifying_otp) : getString(R.string.confirm);
    }

    private void addButton(String id, PixelRectF rect, String label, boolean enabled, ButtonViewAnimator.OnClickListener listener) {
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(rect.width())),
                Math.max(1, Math.round(rect.height())), Bitmap.Config.ARGB_8888);
        Canvas buttonCanvas = new Canvas(bitmap);
        Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        PixelRectF localRect = new PixelRectF(0f, 0f, bitmap.getWidth(), bitmap.getHeight());
        buttonPaint.setColor(ContextCompat.getColor(getContext(), enabled ? R.color.pinggo_action : R.color.pinggo_muted_text));
        buttonCanvas.drawRoundRect(localRect, dp(10), dp(10), buttonPaint);
        TextViewAnimator labelAnimator = new TextViewAnimator(
                getContext(), id + "_label", label, localRect, textSizePx(18),
                ContextCompat.getColor(getContext(), R.color.white), Paint.Align.CENTER,
                TextViewAnimator.FONT_INTER, TextViewAnimator.WEIGHT_BOLD, null);
        labelAnimator.onDraw(buttonCanvas);
        if (enabled && arrowBitmap != null && "next".equals(id)) {
            float size = 0.050926f * getRefWidth();
            RectF arrowRect = new RectF(bitmap.getWidth() - 0.071296f * getRefWidth() - size,
                    (bitmap.getHeight() - size) / 2f,
                    bitmap.getWidth() - 0.071296f * getRefWidth(),
                    (bitmap.getHeight() + size) / 2f);
            buttonCanvas.drawBitmap(arrowBitmap, null, arrowRect, buttonPaint);
        }
        buttonAnimators.add(new ButtonViewAnimator(enabled ? listener : null, id, bitmap, rect));
    }

    private void focusField(int field) {
        activeField = field;
        requestFocus();
        InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.viewClicked(this);
            inputMethodManager.restartInput(this);
        }
        post(this::showKeyboard);
        invalidate();
    }

    private void showKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void focusCountryField() {
        if (activeField != FIELD_COUNTRY) {
            countryQuery = countryName == null ? "" : countryName;
        }
        focusField(FIELD_COUNTRY);
        showCountryPopup(countryQuery);
    }

    private void toggleCountryPopup() {
        if (countryPopupWindow != null && countryPopupWindow.isShowing()) {
            countryPopupWindow.dismiss();
            return;
        }
        activeField = FIELD_NONE;
        countryQuery = "";
        hideKeyboard();
        showCountryPopup("");
    }

    private void showCountryPopup(String query) {
        buildLayout();
        if (countryPopupWindow != null && countryPopupWindow.isShowing()) {
            refreshCountryPopup();
            return;
        }
        countryPopupOpen = true;
        invalidate();

        int popupWidth = Math.round(countryRect.width());
        int popupHeight = Math.max(dp(72), Math.round(cardRect.bottom - countryRect.bottom));
        LinearLayout popupLayout = new LinearLayout(getContext());
        popupLayout.setOrientation(LinearLayout.VERTICAL);
        popupLayout.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.white));

        ScrollView scrollView = new ScrollView(getContext());
        scrollView.setFillViewport(false);
        scrollView.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.white));

        LinearLayout listLayout = new LinearLayout(getContext());
        listLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listLayout, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        popupLayout.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        List<CCPCountry> countries = CCPCountry.getLibraryMasterCountryList(
                getContext(),
                countryCodePicker.getLanguageToApply()
        );
        populateCountryRows(listLayout, countries, query);

        countryPopupWindow = new PopupWindow(popupLayout, popupWidth, popupHeight, false);
        countryPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        countryPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        countryPopupWindow.setOutsideTouchable(false);
        countryPopupWindow.setBackgroundDrawable(ContextCompat.getDrawable(getContext(), R.drawable.bg_pinggo_input));
        countryPopupWindow.setElevation(dp(8));
        countryPopupWindow.setOnDismissListener(() -> {
            countryPopupOpen = false;
            if (activeField == FIELD_COUNTRY) {
                activeField = FIELD_NONE;
                countryQuery = "";
            }
            invalidate();
        });

        int[] location = new int[2];
        getLocationOnScreen(location);
        countryPopupWindow.showAtLocation(
                this,
                Gravity.NO_GRAVITY,
                location[0] + Math.round(countryRect.left),
                location[1] + Math.round(countryRect.bottom)
        );
        invalidate();
    }

    private void refreshCountryPopup() {
        if (countryPopupWindow == null || !countryPopupWindow.isShowing()) {
            showCountryPopup(countryQuery);
            return;
        }
        View contentView = countryPopupWindow.getContentView();
        if (!(contentView instanceof LinearLayout)) {
            return;
        }
        LinearLayout popupLayout = (LinearLayout) contentView;
        if (popupLayout.getChildCount() == 0 || !(popupLayout.getChildAt(0) instanceof ScrollView)) {
            return;
        }
        ScrollView scrollView = (ScrollView) popupLayout.getChildAt(0);
        if (scrollView.getChildCount() == 0 || !(scrollView.getChildAt(0) instanceof LinearLayout)) {
            return;
        }
        LinearLayout listLayout = (LinearLayout) scrollView.getChildAt(0);
        populateCountryRows(listLayout, CCPCountry.getLibraryMasterCountryList(
                getContext(),
                countryCodePicker.getLanguageToApply()
        ), countryQuery);
    }

    private void populateCountryRows(LinearLayout listLayout, List<CCPCountry> countries, String query) {
        listLayout.removeAllViews();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.US);
        for (CCPCountry country : countries) {
            if (normalizedQuery.isEmpty() || countryMatchesQuery(country, normalizedQuery)) {
                listLayout.addView(createCountryPopupRow(country));
            }
        }
    }

    private boolean countryMatchesQuery(CCPCountry country, String query) {
        return safeLower(country.getName()).contains(query)
                || safeLower(country.getEnglishName()).contains(query)
                || safeLower(country.getNameCode()).contains(query)
                || ("+" + country.getPhoneCode()).contains(query)
                || country.getPhoneCode().contains(query);
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private View createCountryPopupRow(CCPCountry country) {
        float w = getRefWidth();
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (0.035648f * w), 0, (int) (0.035648f * w), 0);
        row.setBackgroundResource(android.R.drawable.list_selector_background);

        ImageView flagView = new ImageView(getContext());
        flagView.setImageResource(country.getFlagID());
        LinearLayout.LayoutParams flagParams = new LinearLayout.LayoutParams(
                (int) (0.081481f * w),
                (int) (0.05f * w)
        );
        row.addView(flagView, flagParams);

        TextView nameView = new TextView(getContext());
        nameView.setText(country.getName());
        nameView.setTextColor(ContextCompat.getColor(getContext(), R.color.primary_text));
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizeInPixels(TEXT_SIZE_INPUT));
        nameView.setSingleLine(true);
        nameView.setTypeface(ResourcesCompat.getFont(getContext(), R.font.inter_opsz_wght));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = (int) (0.045833f * w);
        row.addView(nameView, nameParams);

        TextView codeView = new TextView(getContext());
        codeView.setText("+" + country.getPhoneCode());
        codeView.setTextColor(ContextCompat.getColor(getContext(), R.color.secondary_text));
        codeView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizeInPixels(13));
        codeView.setTypeface(ResourcesCompat.getFont(getContext(), R.font.inter_opsz_wght));
        row.addView(codeView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        row.setOnClickListener(v -> {
            boolean shouldHideKeyboard = activeField == FIELD_COUNTRY;
            countryCodePicker.setCountryForNameCode(country.getNameCode());
            syncSelectedCountry();
            countryQuery = "";
            activeField = FIELD_NONE;
            if (countryPopupWindow != null) {
                countryPopupWindow.dismiss();
            }
            if (shouldHideKeyboard) {
                hideKeyboard();
            }
            invalidate();
        });

        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) getCountryFieldHeight(w)
        ));
        return row;
    }

    private void syncSelectedCountry() {
        if (countryCodePicker == null) {
            return;
        }
        countryName = countryCodePicker.getSelectedCountryName();
        countryCode = countryCodePicker.getSelectedCountryCodeWithPlus();
        selectedFlagBitmap = bitmapFromDrawable(countryCodePicker.getSelectedCountryFlagResourceId());
        updateFormattedPhoneNumber();
    }

    private void hideKeyboard() {
        hideKeyboardOnly();
        clearFocus();
    }

    private void hideKeyboardOnly() {
        InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }

    private void appendInput(String value, boolean composing) {
        if (activeField == FIELD_COUNTRY) {
            String sanitized = sanitizeCountryQuery(value);
            if (sanitized.isEmpty()) {
                return;
            }
            countryQuery = composing ? sanitized : countryQuery + sanitized;
            refreshCountryPopup();
            invalidate();
            return;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return;
        }
        if (activeField == FIELD_OTP) {
            otp = (otp + digits);
            if (otp.length() > 6) {
                otp = otp.substring(0, 6);
            }
        } else {
            String nextPhoneNumber = phoneNumber + digits;
            int maxLength = getMaxNationalNumberLength();
            if (nextPhoneNumber.length() > maxLength) {
                nextPhoneNumber = nextPhoneNumber.substring(0, maxLength);
            }
            phoneNumber = nextPhoneNumber;
            updateFormattedPhoneNumber();
        }
        invalidate();
    }

    private String sanitizeCountryQuery(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\r\\n]", "");
    }

    private int digitFromKeyCode(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return keyCode - KeyEvent.KEYCODE_0;
        }
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            return keyCode - KeyEvent.KEYCODE_NUMPAD_0;
        }
        return -1;
    }

    private void deleteLastInputCharacter() {
        if (activeField == FIELD_COUNTRY && !countryQuery.isEmpty()) {
            countryQuery = countryQuery.substring(0, countryQuery.length() - 1);
            refreshCountryPopup();
        } else if (activeField == FIELD_OTP && !otp.isEmpty()) {
            otp = otp.substring(0, otp.length() - 1);
        } else if (activeField == FIELD_PHONE && !phoneNumber.isEmpty()) {
            phoneNumber = phoneNumber.substring(0, phoneNumber.length() - 1);
            updateFormattedPhoneNumber();
        }
        invalidate();
    }

    private void updateFormattedPhoneNumber() {
        if (phoneNumber.isEmpty()) {
            formattedPhoneNumber = "";
            return;
        }
        String region = countryCodePicker == null ? "IN" : countryCodePicker.getSelectedCountryNameCode();
        try {
            AsYouTypeFormatter formatter = phoneNumberUtil.getAsYouTypeFormatter(region);
            String formatted = "";
            for (int i = 0; i < phoneNumber.length(); i++) {
                formatted = formatter.inputDigit(phoneNumber.charAt(i));
            }
            formattedPhoneNumber = formatted == null || formatted.isEmpty() ? phoneNumber : formatted;
        } catch (Exception ignored) {
            formattedPhoneNumber = formatFallback(phoneNumber);
        }
    }

    private int getMaxNationalNumberLength() {
        String region = countryCodePicker == null ? "IN" : countryCodePicker.getSelectedCountryNameCode();
        try {
            Phonenumber.PhoneNumber exampleNumber = phoneNumberUtil.getExampleNumber(region);
            if (exampleNumber != null) {
                return Math.max(4, String.valueOf(exampleNumber.getNationalNumber()).length());
            }
        } catch (Exception ignored) {
        }
        return 15;
    }

    private String formatFallback(String digits) {
        if (digits.length() <= 5) {
            return digits;
        }
        return digits.substring(0, 5) + " " + digits.substring(5);
    }

    private Bitmap bitmapFromDrawable(int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(getContext(), drawableId);
        if (drawable == null) {
            return null;
        }
        int width = Math.max(1, drawable.getIntrinsicWidth());
        int height = Math.max(1, drawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private String getString(int stringId) {
        return getContext().getString(stringId);
    }

    private float textHeight(int textSize) {
        Paint.FontMetrics metrics = new Paint().getFontMetrics();
        paint.setTextSize(textSizeInPixels(textSize));
        metrics = paint.getFontMetrics();
        return metrics.descent - metrics.ascent;
    }

    private float textSizeInPixels(int textSize) {
        switch (textSize) {
            case 20:
                return 0.050926f * getRefWidth();
            case 18:
                return 0.045833f * getRefWidth();
            case 14:
                return 0.035648f * getRefWidth();
            case 13:
                return 0.033102f * getRefWidth();
            case 12:
                return 0.030556f * getRefWidth();
            default:
                return 0.033102f * getRefWidth();
        }
    }

    private int textSizePx(int textSize) {
        switch (textSize) {
            case 20:
                return 55;
            case 18:
                return 50;
            case 14:
                return 38;
            case 13:
                return 36;
            case 12:
                return 33;
            default:
                return 36;
        }
    }

    private int getRefWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private float getCountryFieldHeight(float width) {
        return 0.162963f * width * FIELD_HEIGHT_SCALE;
    }

    private float getPhoneFieldHeight(float width) {
        return 0.168056f * width * FIELD_HEIGHT_SCALE;
    }

    private float getOtpFieldHeight(float width) {
        return 0.142593f * width * FIELD_HEIGHT_SCALE;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int getShadowExtraHeight() {
        return dp(18);
    }

    public interface AnimatorClickListener {
        void onClick();
    }
}
