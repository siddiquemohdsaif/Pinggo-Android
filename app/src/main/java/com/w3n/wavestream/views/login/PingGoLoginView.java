package com.w3n.wavestream.views.login;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

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
import com.ogfa.nativeviews.textfield.TextField;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.hbb20.CCPCountry;
import com.hbb20.CountryCodePicker;
import com.w3n.wavestream.R;

import java.util.List;
import java.util.Locale;

import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;

public class PingGoLoginView extends View {
    private static final float REFERENCE_WIDTH = 1080f;
    private static final int ACCENT_COLOR = 0xFF019CC4;
    private static final int PRIMARY_TEXT_COLOR = 0xFF000E1A;
    private static final int MUTED_TEXT_COLOR = 0xFF7B8493;
    private static final int INPUT_STROKE_COLOR = 0xFFDDE3EA;

    private final FigmaConfig figmaConfig = new FigmaConfig(REFERENCE_WIDTH);
    private final ZLayerGroup layerGroup = new ZLayerGroup(this);
    private final ZLayer backgroundLayer = layerGroup.addLayer("background");
    private final ZLayer foregroundLayer = layerGroup.addLayer("foreground");
    private final ZLayer loginCardLayer = layerGroup.addLayer("login_card");
    private final Bitmap backgroundBitmap;
    private final Bitmap logoBitmap;
    private final Bitmap lockBitmap;
    private final Bitmap arrowBitmap;
    private Bitmap selectedFlagBitmap;
    private final Bitmap dropdownDownBitmap;
    private final Bitmap dropdownUpBitmap;
    private final Bitmap whiteBitmap = colorBitmap(Color.WHITE);
    private final Bitmap dividerBitmap = colorBitmap(INPUT_STROKE_COLOR);
    private final Bitmap buttonBitmap = createButtonBackground();

    private ZLayer loginCardContent;
    private TextField countryField;
    private TextField phoneNumberField;
    private TextField otpField;
    private Image countryFlagImage;
    private Button countryDropdownButton;
    private Text phoneErrorText;
    private Text otpErrorText;
    private Text phoneCountryCodeText;
    private Text countryLabelText;
    private Text phoneLabelText;
    private Text otpLabelText;
    private final CountryCodePicker countryCodePicker;
    private final PhoneNumberUtil phoneNumberUtil;
    private PopupWindow countryPopupWindow;
    private String selectedCountryName = "India";
    private String selectedCountryCode = "+91";
    private String selectedRegionCode = "IN";
    private String savedPhoneNumber = "";
    private String savedOtp = "";
    private boolean settingCountryText;
    private boolean openingUnfilteredCountryPopup;
    private boolean otpVisible;
    private boolean otpRequestInProgress;
    private boolean otpVerifyInProgress;
    private float contentTranslationY;
    private OnRequestOtpListener requestOtpListener;
    private OnConfirmOtpListener confirmOtpListener;
    private int statusBarInset;
    private int navigationBarInset;
    private int keyboardInset;
    private boolean keyboardVisible;

    public PingGoLoginView(Context context) {
        super(context);
        setBackgroundColor(Color.WHITE);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        backgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pinggo_login_background);
        logoBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pinggo_logo);
        lockBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_pinggo_lock);
        arrowBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_pinggo_arrow);
        selectedFlagBitmap = createIndiaFlag();
        dropdownDownBitmap = drawableToBitmap(R.drawable.ic_dropdown_down);
        dropdownUpBitmap = drawableToBitmap(R.drawable.ic_dropdown_up);
        countryCodePicker = new CountryCodePicker(context);
        countryCodePicker.setDefaultCountryUsingNameCode("IN");
        countryCodePicker.resetToDefaultCountry();
        phoneNumberUtil = PhoneNumberUtil.createInstance(context);
        syncSelectedCountry();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && height > 0) buildScreen();
    }

    public void setInsets(int statusBarInset, int navigationBarInset, int keyboardInset,
                          boolean keyboardVisible) {
        int safeTop = Math.max(0, statusBarInset);
        int safeBottom = Math.max(0, navigationBarInset);
        boolean safeInsetsChanged = this.statusBarInset != safeTop
                || this.navigationBarInset != safeBottom;
        this.statusBarInset = safeTop;
        this.navigationBarInset = safeBottom;
        this.keyboardInset = Math.max(0, keyboardInset);
        this.keyboardVisible = keyboardVisible;
        if (safeInsetsChanged && getWidth() > 0 && getHeight() > 0) buildScreen();
        else updateKeyboardTranslation();
    }

    public String getPhoneNumber() {
        return phoneNumberField == null ? "" : phoneNumberField.getText().trim();
    }

    public String getFullPhoneNumber() {
        return selectedCountryCode + getPhoneNumber().replaceAll("\\s+", "");
    }

    public boolean isValidPhoneNumber() {
        try {
            Phonenumber.PhoneNumber parsedNumber = phoneNumberUtil.parse(
                    getFullPhoneNumber(), selectedRegionCode);
            return phoneNumberUtil.isValidNumberForRegion(parsedNumber, selectedRegionCode);
        } catch (Exception ignored) {
            return false;
        }
    }

    public String getOtp() {
        return otpField == null ? "" : otpField.getText().trim();
    }

    public void setOnRequestOtpListener(OnRequestOtpListener listener) {
        requestOtpListener = listener;
    }

    public void setOnConfirmOtpListener(OnConfirmOtpListener listener) {
        confirmOtpListener = listener;
    }

    public void setOtpRequestInProgress(boolean inProgress) {
        otpRequestInProgress = inProgress;
    }

    public void setOtpVerifyInProgress(boolean inProgress) {
        otpVerifyInProgress = inProgress;
    }

    public void showPhoneError(String message) {
        if (phoneErrorText != null) {
            phoneErrorText.setText(message).setVisible(true);
        }
        if (phoneNumberField != null) {
            phoneNumberField.requestFocus();
            phoneNumberField.setSelection(phoneNumberField.getText().length());
        }
        invalidate();
    }

    public void clearPhoneError() {
        if (phoneErrorText != null) phoneErrorText.setVisible(false);
    }

    public void showOtpError(String message) {
        if (otpErrorText != null) otpErrorText.setText(message).setVisible(true);
        if (otpField != null) otpField.requestFocus();
        invalidate();
    }

    public void showOtpFields() {
        savedPhoneNumber = getPhoneNumber();
        otpVisible = true;
        buildScreen();
        post(() -> {
            if (otpField != null) otpField.requestFocus();
        });
    }

    public boolean handleCountryOutsideTap(float rawX, float rawY) {
        if (countryPopupWindow == null || !countryPopupWindow.isShowing() || countryField == null) {
            return false;
        }
        RectF bounds = countryField.getBounds();
        int[] location = new int[2];
        getLocationOnScreen(location);
        bounds.offset(location[0], location[1] + contentTranslationY);
        if (bounds.contains(rawX, rawY)) return false;
        if (keyboardVisible && countryField.isFocused()) {
            hideKeyboard();
            return true;
        }
        countryPopupWindow.dismiss();
        return true;
    }

    private void buildScreen() {
        if (phoneNumberField != null) savedPhoneNumber = phoneNumberField.getText();
        if (otpField != null) savedOtp = otpField.getText();
        backgroundLayer.clear();
        foregroundLayer.clear();
        loginCardLayer.clear();
        loginCardContent = null;
        countryField = null;
        phoneNumberField = null;
        otpField = null;
        backgroundLayer.add(new Image.Builder(getContext(), "login_background", backgroundBitmap,
                position(0f, 0f), new Size(getWidth(), getHeight())).setScaleType(Image.ScaleType.CENTER_CROP));
        addHeader();
        addLegalNotice();
        addLoginCard();
        updateKeyboardTranslation();
        invalidate();
    }

    private void addHeader() {
        float topOffset = designUnits(statusBarInset);
        foregroundLayer.add(new Image.Builder(getContext(), "pinggo_logo", logoBitmap,
                position(413.239f, 291.549f + topOffset), new Size(253.521f, 253.521f))
                .setScaleType(Image.ScaleType.FIT_XY));
        addCenteredText("brand_name", getString(R.string.pinggo_brand),
                540f + topOffset, 532.394f, 133.099f, 114.085f,
                ACCENT_COLOR, FontVariation.BOLD, 1);
        addCenteredText("primary_tagline", getString(R.string.pinggo_tagline),
                704.789f + topOffset, 545.070f, 45.634f, 35.493f,
                0xFF323232, FontVariation.REGULAR, 1);
        addCenteredText("secondary_tagline", getString(R.string.pinggo_security),
                768.169f + topOffset, 456.338f, 45.634f, 35.493f,
                ACCENT_COLOR, FontVariation.REGULAR, 1);
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
                .setLetterSpacingPercent(0f)
                .setTextColor(0xFF656565)
                .setAlignment(Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .horizontalCenter(true)
                .setMaxLines(2));
    }

    private void addLoginCard() {
        Position cardPosition = new Position(this, figmaConfig,
                Position.HorizontalMarginFrom.LEFT, Position.VerticalMarginFrom.BOTTOM,
                0f, 206.620f + designUnits(navigationBarInset));
        float cardHeight = otpVisible ? 1286.620f : 1076.197f;
        Card loginCard = loginCardLayer.add(new Card.Builder(getContext(), "phone_number_card",
                cardPosition, new Size(940.563f, cardHeight))
                .setBackgroundColor(Color.WHITE)
                .setCornerRadius(63.380f)
                .horizontalCenter(true)
                .setDropShadow(new DropShadow(0f, 5.070f, 35.493f, 5.070f,
                        Color.argb(13, 0, 0, 0))));
        loginCardContent = loginCard.getContentLayer();
        loginCardContent.setTouchPolicy(ZLayer.TouchPolicy.BLOCK_BELOW);
        addCardText(loginCard, "card_title", getString(R.string.enter_phone_number),
                64.648f, 773.239f, 86.197f, 57.042f,
                PRIMARY_TEXT_COLOR, FontVariation.BOLD, 1);
        addCardText(loginCard, "card_description", getString(R.string.verification_code_hint),
                162.254f, 722.535f, 103.944f, 31.690f,
                MUTED_TEXT_COLOR, FontVariation.REGULAR, 2);
        addCountryField(loginCard);
        addPhoneField(loginCard);
        if (otpVisible) addOtpField(loginCard);
        addSecurityMessage(loginCard);
        addNextButton(loginCard);
    }

    private void addCountryField(Card card) {
        countryField = new TextField.Builder(getContext(), "country_field",
                cardPosition(card, 63.380f, 314.366f), new Size(813.803f, 140.704f))
                .setText(selectedCountryName)
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSize(36.761f)
                .setTextColor(PRIMARY_TEXT_COLOR)
                .setBackgroundColor(Color.WHITE, Color.WHITE)
                .setStrokeColor(INPUT_STROKE_COLOR, ACCENT_COLOR)
                .setStrokeWidth(2.535f)
                .setCornerRadius(25.352f)
                .setPadding(134.366f, 22.817f)
                .setOnTextChangedListener((id, text) -> {
                    if (!settingCountryText && countryField != null && countryField.isFocused()) {
                        showCountryPopup(text);
                        refreshCountryPopup(text);
                    }
                })
                .setOnFocusChangedListener((id, focused) -> {
                    if (focused && !openingUnfilteredCountryPopup) {
                        settingCountryText = true;
                        countryField.setText(selectedCountryName);
                        countryField.setSelection(countryField.getText().length());
                        settingCountryText = false;
                        showCountryPopup(countryField.getText());
                    }
                    updateFocusedFieldAppearance();
                    post(this::updateKeyboardTranslation);
                })
                .build(this);
        loginCardContent.add(countryField);
        countryFlagImage = new Image.Builder(getContext(), "country_flag", selectedFlagBitmap,
                cardPosition(card, 103.944f, 366.338f), new Size(55.775f, 36.761f))
                .setScaleType(Image.ScaleType.FIT_XY).build(this);
        loginCardContent.add(countryFlagImage);
        countryDropdownButton = new Button.Builder(getContext(), "country_dropdown",
                dropdownDownBitmap, cardPosition(card, 699.718f, 332.113f),
                new Size(101.408f, 103.944f))
                .setImageScaleType(Image.ScaleType.FIT_CENTER)
                .setRippleEnabled(true)
                .setRippleColor(0x22019CC4)
                .setOnClickListener(id -> openUnfilteredCountryPopup())
                .build(this);
        loginCardContent.add(countryDropdownButton);
        countryLabelText = addFieldLabel(card, "country_label", getString(R.string.country),
                98.873f, 296.620f, 159.718f, MUTED_TEXT_COLOR);
    }

    private void addPhoneField(Card card) {
        phoneNumberField = new TextField.Builder(getContext(), "phone_number_field",
                cardPosition(card, 63.380f, 524.789f), new Size(813.803f, 147.042f))
                .setHint("98765 43210")
                .setText(savedPhoneNumber)
                .setMaxLength(14)
                .setInputType(InputType.TYPE_CLASS_PHONE)
                .setImeOptions(EditorInfo.IME_ACTION_DONE)
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSize(36.761f)
                .setTextColor(PRIMARY_TEXT_COLOR)
                .setHintColor(0xFF757575)
                .setCursorColor(ACCENT_COLOR)
                .setCursorWidth(5.070f)
                .setSelectionColor(0x443B9CFF)
                .setBackgroundColor(Color.WHITE, Color.WHITE)
                .setStrokeColor(INPUT_STROKE_COLOR, ACCENT_COLOR)
                .setStrokeWidth(4.437f)
                .setCornerRadius(25.352f)
                .setPadding(196.479f, 22.817f)
                .setOnFocusChangedListener((id, focused) -> {
                    updateFocusedFieldAppearance();
                    post(this::updateKeyboardTranslation);
                })
                .setOnTextChangedListener((id, text) -> clearPhoneError())
                .build(this);
        loginCardContent.add(phoneNumberField);
        phoneCountryCodeText = new Text.Builder(getContext(), "phone_country_code", selectedCountryCode,
                cardPosition(card, 102.676f, 553.944f), new Size(98.873f, 86.197f))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
                .setTextSize(36.761f).setTextColor(PRIMARY_TEXT_COLOR)
                .setAlignment(Text.Alignment.START).setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setWrapEnabled(false)
                .build(this);
        loginCardContent.add(phoneCountryCodeText);
        addImage(card, "phone_divider", dividerBitmap,
                215.493f, 567.887f, 2.535f, 55.775f);
        phoneLabelText = addFieldLabel(card, "phone_number_label", getString(R.string.phone_number),
                98.873f, 507.042f, 228.169f, MUTED_TEXT_COLOR);
        phoneErrorText = addErrorText(card, "phone_error", 68.451f, 673.099f);
    }

    private void addOtpField(Card card) {
        otpField = new TextField.Builder(getContext(), "otp_field",
                cardPosition(card, 63.380f, 714.930f), new Size(813.803f, 147.042f))
                .setHint(getString(R.string.enter_otp))
                .setText(savedOtp)
                .setMaxLength(6)
                .setInputType(InputType.TYPE_CLASS_NUMBER)
                .setImeOptions(EditorInfo.IME_ACTION_DONE)
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSize(36.761f)
                .setTextColor(PRIMARY_TEXT_COLOR)
                .setHintColor(0xFF757575)
                .setCursorColor(ACCENT_COLOR)
                .setCursorWidth(5.070f)
                .setBackgroundColor(Color.WHITE, Color.WHITE)
                .setStrokeColor(INPUT_STROKE_COLOR, ACCENT_COLOR)
                .setStrokeWidth(4.437f)
                .setCornerRadius(25.352f)
                .setPadding(38.028f, 22.817f)
                .setOnTextChangedListener((id, text) -> {
                    if (otpErrorText != null) otpErrorText.setVisible(false);
                })
                .setOnFocusChangedListener((id, focused) -> {
                    updateFocusedFieldAppearance();
                    post(this::updateKeyboardTranslation);
                })
                .build(this);
        loginCardContent.add(otpField);
        otpLabelText = addFieldLabel(card, "otp_label", getString(R.string.enter_otp),
                98.873f, 697.183f, 177.465f, MUTED_TEXT_COLOR);
        otpErrorText = addErrorText(card, "otp_error", 68.451f, 863.239f);
    }

    private Text addErrorText(Card card, String id, float left, float top) {
        Text errorText = new Text.Builder(getContext(), id, "",
                cardPosition(card, left, top), new Size(798.592f, 32.958f))
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSize(24.085f)
                .setTextColor(0xFFD32F2F)
                .setAlignment(Text.Alignment.START)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setWrapEnabled(false)
                .build(this);
        errorText.setVisible(false);
        loginCardContent.add(errorText);
        return errorText;
    }

    private Text addFieldLabel(Card card, String id, String label, float left, float top,
                               float width, int color) {
        addImage(card, id + "_background", whiteBitmap,
                left - 10.141f, top + 5.070f, width, 35.493f);
        Text labelText = new Text.Builder(getContext(), id, label,
                cardPosition(card, left, top), new Size(width, 48.169f))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
                .setTextSize(29.155f).setTextColor(color)
                .setAlignment(Text.Alignment.START).setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setWrapEnabled(false)
                .build(this);
        loginCardContent.add(labelText);
        return labelText;
    }

    private void addSecurityMessage(Card card) {
        float topOffset = otpVisible ? 210.423f : 0f;
        addImage(card, "security_lock", lockBitmap,
                141.972f, 723.803f + topOffset, 31.690f, 36.761f);
        loginCardContent.add(new Text.Builder(getContext(), "security_message",
                getString(R.string.phone_safe_message),
                cardPosition(card, 191.408f, 712.394f + topOffset),
                new Size(646.479f, 60.845f))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
                .setTextSize(27.887f).setTextColor(MUTED_TEXT_COLOR)
                .setAlignment(Text.Alignment.START).setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setWrapEnabled(false));
    }

    private void addNextButton(Card card) {
        float topOffset = otpVisible ? 210.423f : 0f;
        loginCardContent.add(new Button.Builder(getContext(), "next_button", buttonBitmap,
                otpVisible ? getString(R.string.confirm) : getString(R.string.next),
                cardPosition(card, 63.380f, 823.944f + topOffset),
                new Size(813.803f, 139.437f))
                .setImageScaleType(Image.ScaleType.FIT_XY)
                .setCornerRadius(27.887f)
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.BOLD)
                .setTextSize(38.028f)
                .setTextColor(Color.WHITE)
                .setRippleEnabled(true)
                .setRippleColor(0x33FFFFFF)
                .setRippleDuration(320L)
                .setRippleOrigin(Button.RippleOrigin.TOUCH)
                .setOnClickListener(id -> handlePrimaryAction()));
        addImage(card, "next_arrow", arrowBitmap,
                706.056f, 869.577f + topOffset, 48.169f, 48.169f);
    }

    private void addImage(Card card, String id, Bitmap bitmap, float left, float top,
                          float width, float height) {
        loginCardContent.add(new Image.Builder(getContext(), id, bitmap,
                cardPosition(card, left, top), new Size(width, height))
                .setScaleType(Image.ScaleType.FIT_XY));
    }

    private void handlePrimaryAction() {
        if (otpVisible) {
            String otp = getOtp();
            if (otp.length() != 6) {
                showOtpError(getString(R.string.invalid_otp));
                return;
            }
            if (!otpVerifyInProgress && confirmOtpListener != null) {
                confirmOtpListener.onConfirmOtp(otp);
            }
            return;
        }

        if (getPhoneNumber().isEmpty()) {
            showPhoneError(getString(R.string.phone_required));
            return;
        }
        if (!isValidPhoneNumber()) {
            showPhoneError(getString(R.string.invalid_phone));
            return;
        }
        clearPhoneError();
        if (!otpRequestInProgress && requestOtpListener != null) {
            requestOtpListener.onRequestOtp(getFullPhoneNumber());
        }
    }

    private void openUnfilteredCountryPopup() {
        if (countryPopupWindow != null && countryPopupWindow.isShowing()) {
            countryPopupWindow.dismiss();
            return;
        }
        if (countryField != null) {
            settingCountryText = true;
            countryField.setText(selectedCountryName);
            settingCountryText = false;
            openingUnfilteredCountryPopup = true;
            countryField.requestFocus();
            openingUnfilteredCountryPopup = false;
        }
        hideKeyboard();
        showCountryPopup("");
    }

    private void showCountryPopup(String query) {
        if (countryField == null) return;
        if (countryPopupWindow != null && countryPopupWindow.isShowing()) {
            refreshCountryPopup(query);
            return;
        }

        LinearLayout popupLayout = new LinearLayout(getContext());
        popupLayout.setOrientation(LinearLayout.VERTICAL);
        popupLayout.setBackgroundColor(Color.WHITE);
        ScrollView scrollView = new ScrollView(getContext());
        LinearLayout countryList = new LinearLayout(getContext());
        countryList.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(countryList, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        popupLayout.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        populateCountryRows(countryList, query);

        RectF bounds = countryField.getBounds();
        int popupWidth = Math.round(bounds.width());
        int popupHeight = Math.min(dp(420), Math.max(dp(180),
                getHeight() - Math.round(bounds.bottom + contentTranslationY) - keyboardInset));
        countryPopupWindow = new PopupWindow(popupLayout, popupWidth, popupHeight, false);
        countryPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        countryPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        countryPopupWindow.setOutsideTouchable(false);
        countryPopupWindow.setBackgroundDrawable(
                ContextCompat.getDrawable(getContext(), R.drawable.bg_pinggo_input));
        countryPopupWindow.setElevation(dp(8));
        countryPopupWindow.setOnDismissListener(() -> {
            if (countryDropdownButton != null) countryDropdownButton.setBitmap(dropdownDownBitmap);
            countryPopupWindow = null;
            if (countryField != null) countryField.clearFocus();
            updateFocusedFieldAppearance();
            invalidate();
        });
        if (countryDropdownButton != null) countryDropdownButton.setBitmap(dropdownUpBitmap);
        updateCountryPopupPosition();
        updateFocusedFieldAppearance();
    }

    private void refreshCountryPopup(String query) {
        if (countryPopupWindow == null || !countryPopupWindow.isShowing()) return;
        View content = countryPopupWindow.getContentView();
        if (!(content instanceof LinearLayout)) return;
        LinearLayout popupLayout = (LinearLayout) content;
        if (popupLayout.getChildCount() == 0 || !(popupLayout.getChildAt(0) instanceof ScrollView)) return;
        ScrollView scrollView = (ScrollView) popupLayout.getChildAt(0);
        if (scrollView.getChildCount() == 0 || !(scrollView.getChildAt(0) instanceof LinearLayout)) return;
        populateCountryRows((LinearLayout) scrollView.getChildAt(0), query);
    }

    private void populateCountryRows(LinearLayout countryList, String query) {
        countryList.removeAllViews();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.US);
        List<CCPCountry> countries = CCPCountry.getLibraryMasterCountryList(
                getContext(), countryCodePicker.getLanguageToApply());
        for (CCPCountry country : countries) {
            if (matchesCountry(country, normalizedQuery)) {
                countryList.addView(createCountryRow(country));
            }
        }
    }

    private boolean matchesCountry(CCPCountry country, String query) {
        return query.isEmpty()
                || lower(country.getName()).contains(query)
                || lower(country.getEnglishName()).contains(query)
                || lower(country.getNameCode()).contains(query)
                || country.getPhoneCode().contains(query)
                || ("+" + country.getPhoneCode()).contains(query);
    }

    private View createCountryRow(CCPCountry country) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        ImageView flag = new ImageView(getContext());
        flag.setImageResource(country.getFlagID());
        row.addView(flag, new LinearLayout.LayoutParams(dp(38), dp(26)));
        TextView name = new TextView(getContext());
        name.setText(country.getName());
        name.setTextColor(PRIMARY_TEXT_COLOR);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        name.setTypeface(ResourcesCompat.getFont(getContext(), R.font.inter_opsz_wght));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = dp(16);
        row.addView(name, nameParams);
        TextView code = new TextView(getContext());
        code.setText("+" + country.getPhoneCode());
        code.setTextColor(MUTED_TEXT_COLOR);
        code.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        row.addView(code);
        row.setOnClickListener(view -> selectCountry(country));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));
        return row;
    }

    private void selectCountry(CCPCountry country) {
        countryCodePicker.setCountryForNameCode(country.getNameCode());
        syncSelectedCountry();
        settingCountryText = true;
        if (countryField != null) {
            countryField.setText(selectedCountryName);
            countryField.clearFocus();
        }
        settingCountryText = false;
        if (countryFlagImage != null) countryFlagImage.setBitmap(selectedFlagBitmap);
        if (phoneCountryCodeText != null) phoneCountryCodeText.setText(selectedCountryCode);
        if (countryPopupWindow != null) countryPopupWindow.dismiss();
        hideKeyboard();
        invalidate();
    }

    private void syncSelectedCountry() {
        selectedCountryName = countryCodePicker.getSelectedCountryName();
        selectedCountryCode = countryCodePicker.getSelectedCountryCodeWithPlus();
        selectedRegionCode = countryCodePicker.getSelectedCountryNameCode();
        selectedFlagBitmap = drawableToBitmap(countryCodePicker.getSelectedCountryFlagResourceId());
    }

    private void updateCountryPopupPosition() {
        if (countryPopupWindow == null || countryPopupWindow.isShowing() || countryField == null) return;
        RectF bounds = countryField.getBounds();
        int[] location = new int[2];
        getLocationOnScreen(location);
        countryPopupWindow.showAtLocation(this, Gravity.NO_GRAVITY,
                location[0] + Math.round(bounds.left),
                location[1] + Math.round(bounds.bottom + contentTranslationY));
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private void hideKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void addCenteredText(String id, CharSequence value, float top, float width,
                                 float height, float textSize, int color,
                                 FontVariation variation, int maxLines) {
        foregroundLayer.add(new Text.Builder(getContext(), id, value, position(0f, top),
                new Size(width, height))
                .setFont(NativeFonts.INTER).setFontVariations(variation)
                .setTextSize(textSize).setTextColor(color)
                .setAlignment(Text.Alignment.CENTER).setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .horizontalCenter(true).setMaxLines(maxLines));
    }

    private void addCardText(Card card, String id, CharSequence value, float top, float width,
                             float height, float textSize, int color, FontVariation variation,
                             int maxLines) {
        loginCardContent.add(new Text.Builder(getContext(), id, value,
                cardPosition(card, (940.563f - width) / 2f, top), new Size(width, height))
                .setFont(NativeFonts.INTER).setFontVariations(variation)
                .setTextSize(textSize).setTextColor(color)
                .setAlignment(Text.Alignment.CENTER).setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setMaxLines(maxLines));
    }

    private Position cardPosition(Card card, float left, float top) {
        float scale = figmaConfig.getScale(getWidth());
        RectF bounds = card.getBounds();
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
        if (start >= 0) text.setSpan(new ForegroundColorSpan(ACCENT_COLOR), start,
                start + phrase.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private float designUnits(int pixels) {
        return getWidth() <= 0 ? 0f : pixels / figmaConfig.getScale(getWidth());
    }

    private void updateKeyboardTranslation() {
        if (loginCardContent == null) return;
        float translation = 0f;
        TextField focusedField = layerGroup.getFocusedTextField();
        if (keyboardVisible && keyboardInset > 0 && focusedField != null) {
            float gap = 30.423f * figmaConfig.getScale(getWidth());
            translation = Math.min(0f, getHeight() - keyboardInset - gap
                    - focusedField.getBounds().bottom);
        }
        contentTranslationY = translation;
        loginCardContent.setTranslationY(translation);
        if (countryPopupWindow != null && countryPopupWindow.isShowing() && countryField != null) {
            RectF bounds = countryField.getBounds();
            int[] location = new int[2];
            getLocationOnScreen(location);
            int availableHeight = getHeight() - keyboardInset
                    - Math.round(bounds.bottom + contentTranslationY);
            int popupHeight = Math.min(dp(420), Math.max(dp(180), availableHeight));
            countryPopupWindow.update(location[0] + Math.round(bounds.left),
                    location[1] + Math.round(bounds.bottom + contentTranslationY),
                    Math.round(bounds.width()), popupHeight);
        }
        invalidate();
    }

    private void updateFocusedFieldAppearance() {
        boolean countryActive = countryField != null && countryField.isFocused()
                || countryPopupWindow != null && countryPopupWindow.isShowing();
        boolean phoneActive = !countryActive && phoneNumberField != null && phoneNumberField.isFocused();
        boolean otpActive = !countryActive && otpField != null && otpField.isFocused();
        if (countryLabelText != null) {
            countryLabelText.setTextColor(countryActive ? ACCENT_COLOR : MUTED_TEXT_COLOR);
        }
        if (phoneLabelText != null) {
            phoneLabelText.setTextColor(phoneActive ? ACCENT_COLOR : MUTED_TEXT_COLOR);
        }
        if (otpLabelText != null) {
            otpLabelText.setTextColor(otpActive ? ACCENT_COLOR : MUTED_TEXT_COLOR);
        }
        invalidate();
    }

    private static Bitmap colorBitmap(int color) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }

    private static Bitmap createIndiaFlag() {
        Bitmap bitmap = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFFFF9933); canvas.drawRect(0f, 0f, 48f, 10.67f, paint);
        paint.setColor(Color.WHITE); canvas.drawRect(0f, 10.67f, 48f, 21.34f, paint);
        paint.setColor(0xFF138808); canvas.drawRect(0f, 21.34f, 48f, 32f, paint);
        paint.setColor(0xFF000080); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
        canvas.drawCircle(24f, 16f, 4.2f, paint);
        return bitmap;
    }

    private static Bitmap createButtonBackground() {
        Bitmap bitmap = Bitmap.createBitmap(642, 110, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0f, 0f, bitmap.getWidth(), 0f,
                0xFF05A7D5, 0xFF019BC5, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(0f, 0f, bitmap.getWidth(), bitmap.getHeight(), 22f, 22f, paint);
        return bitmap;
    }

    private Bitmap drawableToBitmap(int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(getContext(), drawableId);
        if (drawable == null) return colorBitmap(Color.TRANSPARENT);
        int width = Math.max(1, drawable.getIntrinsicWidth());
        int height = Math.max(1, drawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        return bitmap;
    }

    private String getString(int stringId) {
        return getContext().getString(stringId);
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
    public boolean onCheckIsTextEditor() {
        return layerGroup.onCheckIsTextEditor();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection connection = layerGroup.onCreateInputConnection(outAttrs);
        return connection != null ? connection : super.onCreateInputConnection(outAttrs);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return layerGroup.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    public interface OnRequestOtpListener {
        void onRequestOtp(String fullPhoneNumber);
    }

    public interface OnConfirmOtpListener {
        void onConfirmOtp(String otp);
    }
}
