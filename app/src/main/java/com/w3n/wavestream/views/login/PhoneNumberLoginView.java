package com.w3n.wavestream.views.login;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
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
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.textfield.TextField;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.hbb20.CCPCountry;
import com.hbb20.CountryCodePicker;
import com.w3n.wavestream.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;

public class PhoneNumberLoginView extends View {
    private static final float REFERENCE_WIDTH = 1080f;
    private static final int ACCENT_COLOR = 0xFF019CC4;
    private static final int PRIMARY_TEXT_COLOR = 0xFF000E1A;
    private static final int MUTED_TEXT_COLOR = 0xFF7B8493;
    private static final int INPUT_STROKE_COLOR = 0xFFDDE3EA;
    private static final float COUNTRY_CARD_WIDTH = 806f;
    private static final float COUNTRY_CARD_HEIGHT = 787f;
    private static final float COUNTRY_EMPTY_HEIGHT = 150f;
    private static final float COUNTRY_ROW_HEIGHT_DP = 58f;
    private static final float COUNTRY_ROW_PADDING_DP = 16f;
    private static final float COUNTRY_FLAG_WIDTH_DP = 38f;
    private static final float COUNTRY_FLAG_HEIGHT_DP = 26f;
    private static final float COUNTRY_NAME_TEXT_SP = 14f;
    private static final float COUNTRY_CODE_TEXT_SP = 12f;
    private static final int COUNTRY_DISPLAY_NAME_LIMIT = 25;
    private static final float COUNTRY_CARD_BOTTOM_RADIUS = 50f;
    private static final float COUNTRY_DIVIDER_WIDTH = 670f;
    private static final float COUNTRY_DIVIDER_WEIGHT = 2f;
    private static final int COUNTRY_DIVIDER_COLOR = 0xFFE5E8EC;
    private static final float COUNTRY_SHADOW_OFFSET_Y = 4f;
    private static final float COUNTRY_SHADOW_BLUR = 28f;
    private static final float COUNTRY_SHADOW_SPREAD = 4f;
    private static final int COUNTRY_SHADOW_COLOR = 0x0D000000;

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
    private final Bitmap countryDividerBitmap = colorBitmap(COUNTRY_DIVIDER_COLOR);
    private final Bitmap buttonBitmap;
    private final Bitmap transparentBitmap = colorBitmap(Color.TRANSPARENT);
    private final SparseArray<Bitmap> countryFlagBitmaps = new SparseArray<>();

    private ZLayer loginCardContent;
    private TextField countryField;
    private TextField phoneNumberField;
    private Image countryFlagImage;
    private Image countryDropdownImage;
    private Button countryDropdownButton;
    private Text phoneErrorText;
    private Text phoneCountryCodeText;
    private Text countryLabelText;
    private Text phoneLabelText;
    private final CountryCodePicker countryCodePicker;
    private final PhoneNumberUtil phoneNumberUtil;
    private final List<CountrySearchItem> countrySearchIndex = new ArrayList<>();
    private Card loginCard;
    private PopupWindow countryPopupWindow;
    private CountryAdapter countryAdapter;
    private CountryCardLayout countryPopupCardLayout;
    private CountryListView countryListView;
    private TextView countryEmptyText;
    private Runnable pendingCountryFilter;
    private String selectedCountryName = "India";
    private String selectedCountryCode = "+91";
    private String selectedRegionCode = "IN";
    private String selectedPhoneHint = "98765 43210";
    private int selectedPhoneMaxLength = 10;
    private String savedPhoneNumber = "";
    private boolean settingCountryText;
    private float contentTranslationY;
    private OnNextListener nextListener;
    private int statusBarInset;
    private int navigationBarInset;
    private int keyboardInset;
    private boolean keyboardVisible;

    public PhoneNumberLoginView(Context context) {
        super(context);
        setBackgroundColor(Color.WHITE);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        backgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pinggo_login_background);
        logoBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pinggo_logo);
        lockBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_pinggo_lock);
        arrowBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_pinggo_arrow);
        buttonBitmap = createButtonBackground(arrowBitmap);
        selectedFlagBitmap = createIndiaFlag();
        dropdownDownBitmap = drawableToBitmap(R.drawable.country_dropdown_down);
        dropdownUpBitmap = drawableToBitmap(R.drawable.country_dropdown_up);
        countryCodePicker = new CountryCodePicker(context);
        countryCodePicker.setDefaultCountryUsingNameCode("IN");
        countryCodePicker.resetToDefaultCountry();
        phoneNumberUtil = PhoneNumberUtil.createInstance(context);
        buildCountrySearchIndex();
        syncSelectedCountry();
        syncPhoneInputRules();
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

    public void setOnNextListener(OnNextListener listener) {
        nextListener = listener;
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
        backgroundLayer.clear();
        foregroundLayer.clear();
        loginCardLayer.clear();
        loginCard = null;
        loginCardContent = null;
        countryField = null;
        phoneNumberField = null;
        backgroundLayer.add(new Image.Builder(getContext(), "login_background", backgroundBitmap,
                position(0f, 0f), new Size(designUnits(getWidth()),designUnits(getHeight()))).setScaleType(Image.ScaleType.CENTER_CROP));
        addHeader();
        addLegalNotice();
        addLoginCard();
        updateKeyboardTranslation();
        Log.d("WaveLayout",
                "view=" + getWidth() + "x" + getHeight()
                        + ", scale=" + figmaConfig.getScale(getWidth())
                        + ", status=" + statusBarInset
                        + ", navigation=" + navigationBarInset);
        invalidate();
    }

    private void addHeader() {
        float topOffset = designUnits(statusBarInset);
        foregroundLayer.add(new Image.Builder(getContext(), "pinggo_logo", logoBitmap,
                position(413.239f, 241.549f + topOffset), new Size(253.521f, 253.521f))
                .setScaleType(Image.ScaleType.FIT_XY));
        addCenteredText("brand_name", getString(R.string.pinggo_brand),
                490f + topOffset, 532.394f, 133.099f, 114.085f,
                ACCENT_COLOR, FontVariation.BOLD, 1);
        addCenteredText("primary_tagline", getString(R.string.pinggo_tagline),
                654.789f + topOffset, 545.070f, 45.634f, 35.493f,
                0xFF323232, FontVariation.REGULAR, 1);
        addCenteredText("secondary_tagline", getString(R.string.pinggo_security),
                718.169f + topOffset, 456.338f, 45.634f, 35.493f,
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
        loginCard = loginCardLayer.add(new Card.Builder(getContext(), "phone_number_card",
                cardPosition, new Size(940.563f, 1076.197f))
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
                MUTED_TEXT_COLOR, FontVariation.MEDIUM, 2);
        addCountryField(loginCard);
        addPhoneField(loginCard);
        addSecurityMessage(loginCard);
        addNextButton(loginCard);
    }

    private void addCountryField(Card card) {
        countryField = new TextField.Builder(getContext(), "country_field",
                cardPosition(card, 63.380f, 314.366f), new Size(813.803f, 140.704f))
                .setText(selectedCountryDisplayName())
                .setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
                .setImeOptions(EditorInfo.IME_ACTION_DONE
                        | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.MEDIUM)
                .setTextSize(36.761f)
                .setTextColor(PRIMARY_TEXT_COLOR)
                .setCursorColor(ACCENT_COLOR)
                .setCursorWidth(5.070f)
                .setSelectionColor(0x443B9CFF)
                .setBackgroundColor(Color.WHITE, Color.WHITE)
                .setStrokeColor(INPUT_STROKE_COLOR, ACCENT_COLOR)
                .setStrokeWidth(2.535f)
                .setCornerRadius(25.352f)
                .setPadding(144.366f, 22.817f)
                .setOnTextChangedListener((id, text) -> {
                    if (!settingCountryText && countryField != null && countryField.isFocused()) {
                        scheduleCountryFilter(text);
                    }
                })
                .setOnFocusChangedListener((id, focused) -> {
                    if (focused) {
                        settingCountryText = true;
                        // The ellipsis is presentation-only. Restore the complete
                        // country name before the user edits or filters it.
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
                cardPosition(card, 113.944f, 366.338f), new Size(55.775f, 36.761f))
                .setScaleType(Image.ScaleType.FIT_XY).build(this);
        loginCardContent.add(countryFlagImage);
        countryDropdownImage = new Image.Builder(getContext(), "country_dropdown_image",
                dropdownDownBitmap, cardPosition(card, 783.922f, 374.085f),
                new Size(33f, 20f))
                .setScaleType(Image.ScaleType.FIT_XY)
                .build(this);
        loginCardContent.add(countryDropdownImage);
        countryDropdownButton = new Button.Builder(getContext(), "country_dropdown",
                transparentBitmap, cardPosition(card, 749.718f, 332.113f),
                new Size(101.408f, 103.944f))
                .setImageScaleType(Image.ScaleType.FIT_XY)
                .setRippleEnabled(true)
                .setRippleColor(0x22019CC4)
                .setOnClickListener(id -> openUnfilteredCountryPopup())
                .build(this);
        loginCardContent.add(countryDropdownButton);
        countryLabelText = addFieldLabel(card, "country_label", getString(R.string.country),
                104.873f, 292.620f, 135f, MUTED_TEXT_COLOR);
    }

    private void addPhoneField(Card card) {
        phoneNumberField = new TextField.Builder(getContext(), "phone_number_field",
                cardPosition(card, 63.380f, 524.789f), new Size(813.803f, 147.042f))
                .setHint(selectedPhoneHint)
                .setText(savedPhoneNumber)
                .setMaxLength(selectedPhoneMaxLength)
                .setInputType(InputType.TYPE_CLASS_PHONE)
                .setImeOptions(EditorInfo.IME_ACTION_DONE)
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.MEDIUM)
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
                cardPosition(card, 72.676f, 553.944f), new Size(128.873f, 86.197f))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
                .setTextSize(36.761f).setTextColor(PRIMARY_TEXT_COLOR)
                .setAlignment(Text.Alignment.CENTER).setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setWrapEnabled(false)
                .build(this);
        loginCardContent.add(phoneCountryCodeText);
        addImage(card, "phone_divider", dividerBitmap,
                215.493f, 567.887f, 2.535f, 55.775f);
        phoneLabelText = addFieldLabel(card, "phone_number_label", getString(R.string.phone_number),
                104.873f, 503.042f, 223.169f, MUTED_TEXT_COLOR);
        phoneErrorText = addErrorText(card, "phone_error", 68.451f, 673.099f);
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
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
                .setTextSize(29.155f).setTextColor(color)
                .setAlignment(Text.Alignment.START).setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setWrapEnabled(false)
                .build(this);
        loginCardContent.add(labelText);
        return labelText;
    }

    private void addSecurityMessage(Card card) {
        addImage(card, "security_lock", lockBitmap,
                141.972f, 723.803f, 31.690f, 36.761f);
        loginCardContent.add(new Text.Builder(getContext(), "security_message",
                getString(R.string.phone_safe_message),
                cardPosition(card, 191.408f, 712.394f),
                new Size(646.479f, 60.845f))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
                .setTextSize(27.887f).setTextColor(MUTED_TEXT_COLOR)
                .setAlignment(Text.Alignment.START).setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setWrapEnabled(false));
    }

    private void addNextButton(Card card) {
        loginCardContent.add(new Button.Builder(getContext(), "next_button", buttonBitmap,
                getString(R.string.next),
                cardPosition(card, 63.380f, 823.944f),
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
    }

    private void addImage(Card card, String id, Bitmap bitmap, float left, float top,
                          float width, float height) {
        loginCardContent.add(new Image.Builder(getContext(), id, bitmap,
                cardPosition(card, left, top), new Size(width, height))
                .setScaleType(Image.ScaleType.FIT_XY));
    }

    private void handlePrimaryAction() {
        if (getPhoneNumber().isEmpty()) {
            showPhoneError(getString(R.string.phone_required));
            return;
        }
        if (!isValidPhoneNumber()) {
            showPhoneError(getString(R.string.invalid_phone));
            return;
        }
        clearPhoneError();
        if (nextListener != null) nextListener.onNext(getFullPhoneNumber());
    }

    private void openUnfilteredCountryPopup() {
        if (countryPopupWindow != null && countryPopupWindow.isShowing()) {
            countryPopupWindow.dismiss();
            return;
        }
        if (countryField != null) {
            settingCountryText = true;
            countryField.setText(selectedCountryDisplayName());
            settingCountryText = false;
            countryField.clearFocus();
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

        float scale = figmaConfig.getScale(getWidth());
        int cardWidth = Math.round(COUNTRY_CARD_WIDTH * scale);
        int shadowLeft = Math.round((COUNTRY_SHADOW_BLUR + COUNTRY_SHADOW_SPREAD) * scale);
        int shadowTop = shadowLeft;
        int shadowRight = shadowLeft;
        int shadowBottom = Math.round((COUNTRY_SHADOW_BLUR + COUNTRY_SHADOW_SPREAD
                + COUNTRY_SHADOW_OFFSET_Y) * scale);

        countryPopupCardLayout = new CountryCardLayout(getContext(),
                COUNTRY_CARD_BOTTOM_RADIUS * scale,
                COUNTRY_SHADOW_OFFSET_Y * scale,
                COUNTRY_SHADOW_BLUR * scale,
                COUNTRY_SHADOW_SPREAD * scale);
        countryPopupCardLayout.setPadding(shadowLeft, shadowTop, shadowRight, shadowBottom);

        countryAdapter = new CountryAdapter();
        countryListView = new CountryListView(getContext(), countryAdapter);
        countryPopupCardLayout.addView(countryListView);

        countryEmptyText = new TextView(getContext());
        countryEmptyText.setText("No matched country found");
        countryEmptyText.setTextColor(MUTED_TEXT_COLOR);
        countryEmptyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        countryEmptyText.setGravity(Gravity.CENTER);
        countryEmptyText.setTypeface(ResourcesCompat.getFont(getContext(), R.font.inter_opsz_wght));
        countryEmptyText.setVisibility(View.GONE);
        countryPopupCardLayout.addView(countryEmptyText);

        countryAdapter.submitQuery(query);
        int cardHeight = desiredCountryCardHeight(countryAdapter.getItemCount());
        updateCountryCardChildren(cardWidth, cardHeight, countryAdapter.getItemCount());

        countryPopupWindow = new PopupWindow(countryPopupCardLayout,
                cardWidth + shadowLeft + shadowRight,
                cardHeight + shadowTop + shadowBottom, false);
        // Always keep this popup below the IME window in Z-order. The keyboard
        // is therefore allowed to cover any overlapping portion of the dialog.
        countryPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        // Keep the popup at its content height. With INPUT_METHOD_NEEDED the IME
        // remains above this window and can obscure the overlapping list portion.
        countryPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        countryPopupWindow.setOutsideTouchable(false);
        countryPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        countryPopupWindow.setOnDismissListener(() -> {
            if (countryDropdownImage != null) countryDropdownImage.setBitmap(dropdownDownBitmap);
            countryPopupWindow = null;
            countryAdapter = null;
            countryPopupCardLayout = null;
            countryListView = null;
            countryEmptyText = null;
            if (pendingCountryFilter != null) removeCallbacks(pendingCountryFilter);
            pendingCountryFilter = null;
            if (countryField != null) {
                // The editable value is only a search query until a row is selected.
                // Dismissing the picker restores the last committed country.
                settingCountryText = true;
                countryField.setText(selectedCountryDisplayName());
                countryField.setSelection(countryField.getText().length());
                settingCountryText = false;
                countryField.clearFocus();
            }
            updateFocusedFieldAppearance();
            invalidate();
        });
        if (countryDropdownImage != null) countryDropdownImage.setBitmap(dropdownUpBitmap);
        updateCountryPopupPosition();
        updateFocusedFieldAppearance();
    }

    private void refreshCountryPopup(String query) {
        if (countryAdapter != null) countryAdapter.submitQuery(query);
    }

    private void scheduleCountryFilter(String query) {
        if (pendingCountryFilter != null) removeCallbacks(pendingCountryFilter);
        String stableQuery = query == null ? "" : query;
        pendingCountryFilter = () -> {
            pendingCountryFilter = null;
            if (countryField == null || !countryField.isFocused()
                    || !stableQuery.equals(countryField.getText())) return;
            showCountryPopup(stableQuery);
        };
        // Do not resize another window while the IME is still applying composing text.
        postDelayed(pendingCountryFilter, 80L);
    }

    private int desiredCountryCardHeight(int matchCount) {
        float scale = figmaConfig.getScale(getWidth());
        int maximumHeight = Math.round(COUNTRY_CARD_HEIGHT * scale);
        int contentHeight = matchCount == 0
                ? Math.round(COUNTRY_EMPTY_HEIGHT * scale)
                : Math.round(matchCount * dpValue(COUNTRY_ROW_HEIGHT_DP));
        return Math.max(1, Math.min(maximumHeight, contentHeight));
    }

    private void updateCountryCardChildren(int cardWidth, int cardHeight, int matchCount) {
        if (countryListView == null || countryEmptyText == null) return;
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(cardWidth, cardHeight);
        countryListView.setLayoutParams(contentParams);
        countryEmptyText.setLayoutParams(new FrameLayout.LayoutParams(cardWidth, cardHeight));
        countryListView.setVisibility(matchCount == 0 ? View.GONE : View.VISIBLE);
        countryEmptyText.setVisibility(matchCount == 0 ? View.VISIBLE : View.GONE);
    }

    private void resizeCountryPopup(int matchCount) {
        if (getWidth() <= 0 || countryPopupCardLayout == null) return;
        float scale = figmaConfig.getScale(getWidth());
        int cardWidth = Math.round(COUNTRY_CARD_WIDTH * scale);
        int cardHeight = desiredCountryCardHeight(matchCount);
        int shadowTop = Math.round((COUNTRY_SHADOW_BLUR + COUNTRY_SHADOW_SPREAD) * scale);
        int shadowBottom = Math.round((COUNTRY_SHADOW_BLUR + COUNTRY_SHADOW_SPREAD
                + COUNTRY_SHADOW_OFFSET_Y) * scale);
        updateCountryCardChildren(cardWidth, cardHeight, matchCount);
        countryPopupCardLayout.requestLayout();
        if (countryPopupWindow != null && countryPopupWindow.isShowing()) {
            countryPopupWindow.update(countryPopupWindow.getWidth(),
                    cardHeight + shadowTop + shadowBottom);
        }
    }

    private void buildCountrySearchIndex() {
        countrySearchIndex.clear();
        List<CCPCountry> countries = CCPCountry.getLibraryMasterCountryList(
                getContext(), countryCodePicker.getLanguageToApply());
        for (CCPCountry country : countries) {
            countrySearchIndex.add(new CountrySearchItem(country));
        }
    }

    private void selectCountry(CCPCountry country) {
        countryCodePicker.setCountryForNameCode(country.getNameCode());
        syncSelectedCountry();
        syncPhoneInputRules();
        settingCountryText = true;
        if (countryField != null) {
            countryField.setText(selectedCountryDisplayName());
            countryField.clearFocus();
        }
        settingCountryText = false;
        if (countryFlagImage != null) countryFlagImage.setBitmap(selectedFlagBitmap);
        if (phoneCountryCodeText != null) phoneCountryCodeText.setText(selectedCountryCode);
        if (phoneNumberField != null) {
            phoneNumberField.setHint(selectedPhoneHint);
            phoneNumberField.setMaxLength(selectedPhoneMaxLength);
        }
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

    private void syncPhoneInputRules() {
        Phonenumber.PhoneNumber example = phoneNumberUtil.getExampleNumberForType(
                selectedRegionCode, PhoneNumberUtil.PhoneNumberType.MOBILE);
        if (example == null) example = phoneNumberUtil.getExampleNumber(selectedRegionCode);

        if (example == null) {
            selectedPhoneHint = "Phone number";
            selectedPhoneMaxLength = Math.max(1, 15 - selectedCountryCode.length() + 1);
            return;
        }

        String nationalDigits = phoneNumberUtil.getNationalSignificantNumber(example);
        selectedPhoneMaxLength = Math.max(1, nationalDigits.length());

        String internationalExample = phoneNumberUtil.format(
                example, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
        String callingCode = "+" + example.getCountryCode();
        selectedPhoneHint = internationalExample.startsWith(callingCode)
                ? internationalExample.substring(callingCode.length()).trim()
                : nationalDigits;
    }

    private String selectedCountryDisplayName() {
        if (selectedCountryName == null) return "";
        if (selectedCountryName.length() <= COUNTRY_DISPLAY_NAME_LIMIT) {
            return selectedCountryName;
        }
        return selectedCountryName.substring(0, COUNTRY_DISPLAY_NAME_LIMIT).trim() + " ...";
    }

    private void updateCountryPopupPosition() {
        if (countryPopupWindow == null || countryPopupWindow.isShowing() || countryField == null) return;
        RectF bounds = countryField.getBounds();
        int[] location = new int[2];
        getLocationOnScreen(location);
        countryPopupWindow.showAtLocation(this, Gravity.NO_GRAVITY,
                countryPopupX(location[0], bounds), countryPopupY(location[1], bounds));
    }

    private int countryPopupX(int viewLeft, RectF fieldBounds) {
        float scale = figmaConfig.getScale(getWidth());
        float cardWidth = COUNTRY_CARD_WIDTH * scale;
        float shadowLeft = (COUNTRY_SHADOW_BLUR + COUNTRY_SHADOW_SPREAD) * scale;
        return viewLeft + Math.round(fieldBounds.left
                + (fieldBounds.width() - cardWidth) / 2f - shadowLeft);
    }

    private int countryPopupY(int viewTop, RectF fieldBounds) {
        float scale = figmaConfig.getScale(getWidth());
        float shadowTop = (COUNTRY_SHADOW_BLUR + COUNTRY_SHADOW_SPREAD) * scale;
        return viewTop + Math.round(fieldBounds.bottom + contentTranslationY - shadowTop);
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

    private float dpValue(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float spValue(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
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
            float contentBottom = focusedField.getBounds().bottom;
            if (focusedField == countryField && countryPopupWindow != null
                    && countryPopupWindow.isShowing() && phoneNumberField != null) {
                contentBottom = phoneNumberField.getBounds().bottom;
            }
            translation = Math.min(0f, getHeight() - keyboardInset - gap
                    - contentBottom);
        }
        contentTranslationY = translation;
        loginCardLayer.setTranslationY(translation);
        if (countryPopupWindow != null && countryPopupWindow.isShowing() && countryField != null) {
            if (countryAdapter != null) resizeCountryPopup(countryAdapter.getItemCount());
            RectF bounds = countryField.getBounds();
            int[] location = new int[2];
            getLocationOnScreen(location);
            countryPopupWindow.update(countryPopupX(location[0], bounds),
                    countryPopupY(location[1], bounds), -1, -1);
        }
        invalidate();
    }

    private void updateFocusedFieldAppearance() {
        boolean countryActive = countryField != null && countryField.isFocused()
                || countryPopupWindow != null && countryPopupWindow.isShowing();
        boolean phoneActive = !countryActive && phoneNumberField != null && phoneNumberField.isFocused();
        if (countryLabelText != null) {
            countryLabelText.setTextColor(countryActive ? ACCENT_COLOR : MUTED_TEXT_COLOR);
        }
        if (phoneLabelText != null) {
            phoneLabelText.setTextColor(phoneActive ? ACCENT_COLOR : MUTED_TEXT_COLOR);
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

    private static Bitmap createButtonBackground(Bitmap arrow) {
        Bitmap bitmap = Bitmap.createBitmap(642, 110, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0f, 0f, bitmap.getWidth(), 0f,
                0xFF05A7D5, 0xFF019BC5, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(0f, 0f, bitmap.getWidth(), bitmap.getHeight(), 22f, 22f, paint);
        if (arrow != null && !arrow.isRecycled()) {
            paint.setShader(null);
            paint.setFilterBitmap(true);
            canvas.drawBitmap(arrow, null, new RectF(557f, 36f, 595f, 74f), paint);
        }
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

    private Bitmap countryFlagBitmap(int drawableId) {
        Bitmap bitmap = countryFlagBitmaps.get(drawableId);
        if (bitmap == null) {
            bitmap = drawableToBitmap(drawableId);
            countryFlagBitmaps.put(drawableId, bitmap);
        }
        return bitmap;
    }

    private String getString(int stringId) {
        return getContext().getString(stringId);
    }

    private final class CountryAdapter extends ComponentList.Adapter<CCPCountry> {
        private final List<CCPCountry> visibleCountries = new ArrayList<>();
        private final Paint codeMeasurePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CountryAdapter() {
            codeMeasurePaint.setTextSize(spValue(COUNTRY_CODE_TEXT_SP));
        }

        void submitQuery(String query) {
            String normalizedQuery = query == null ? ""
                    : query.trim().toLowerCase(Locale.US);
            visibleCountries.clear();
            for (CountrySearchItem item : countrySearchIndex) {
                if (normalizedQuery.isEmpty() || item.searchableText.contains(normalizedQuery)) {
                    visibleCountries.add(item.country);
                }
            }
            notifyDataSetChanged();
            resizeCountryPopup(visibleCountries.size());
        }

        @Override
        public CCPCountry getItem(int position) {
            return visibleCountries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return visibleCountries.get(position).getNameCode().hashCode();
        }

        @Override
        public int getItemCount() {
            return visibleCountries.size();
        }

        @Override
        public void onCreateItem(ComponentList.Item item, int viewType) {
            ComponentList.ItemScope scope = item.getScope();
            RectF bounds = scope.getBounds();
            float rowWidth = bounds.width();
            float rowHeight = bounds.height();
            float padding = dpValue(COUNTRY_ROW_PADDING_DP);
            float flagWidth = dpValue(COUNTRY_FLAG_WIDTH_DP);
            float flagHeight = dpValue(COUNTRY_FLAG_HEIGHT_DP);
            float flagTop = (rowHeight - flagHeight) / 2f;
            float codeWidth = dpValue(92f);
            float dividerWidth = Math.min(rowWidth,
                    COUNTRY_DIVIDER_WIDTH * figmaConfig.getScale(getWidth()));
            float dividerWeight = COUNTRY_DIVIDER_WEIGHT * figmaConfig.getScale(getWidth());
            float dividerLeft = (rowWidth - dividerWidth) / 2f;

            ZLayer content = item.addLayer("content");
            content.add(new Image.Builder(getContext(), scope.id("flag"),
                    selectedFlagBitmap,
                    new RectF(padding, flagTop, padding + flagWidth, flagTop + flagHeight))
                    .setScaleType(Image.ScaleType.FIT_XY));
            content.add(new Text.Builder(getContext(), scope.id("name"), "",
                    new RectF(padding + flagWidth + dpValue(COUNTRY_ROW_PADDING_DP), 0f,
                            rowWidth - padding - codeWidth, rowHeight))
                    .setFont(NativeFonts.INTER)
                    .setFontVariations(FontVariation.REGULAR)
                    .setTextSizePx(spValue(COUNTRY_NAME_TEXT_SP))
                    .setTextColor(PRIMARY_TEXT_COLOR)
                    .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                    .setWrapEnabled(false));
            content.add(new Text.Builder(getContext(), scope.id("code"), "",
                    new RectF(rowWidth - padding - codeWidth, 0f,
                            rowWidth - padding, rowHeight))
                    .useDefaultFont()
                    .setTextSizePx(spValue(COUNTRY_CODE_TEXT_SP))
                    .setTextColor(MUTED_TEXT_COLOR)
                    .setAlignment(Text.Alignment.END)
                    .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                    .setWrapEnabled(false));
            content.add(new Image.Builder(getContext(), scope.id("divider"),
                    countryDividerBitmap,
                    new RectF(dividerLeft, rowHeight - dividerWeight,
                            dividerLeft + dividerWidth, rowHeight))
                    .setScaleType(Image.ScaleType.FIT_XY));
        }

        @Override
        public void onBindItem(ComponentList.Item item, CCPCountry country, int position) {
            RectF rowBounds = item.getScope().getBounds();
            float padding = dpValue(COUNTRY_ROW_PADDING_DP);
            float nameLeft = padding + dpValue(COUNTRY_FLAG_WIDTH_DP)
                    + dpValue(COUNTRY_ROW_PADDING_DP);
            String callingCode = "+" + country.getPhoneCode();
            float codeWidth = (float) Math.ceil(codeMeasurePaint.measureText(callingCode));
            float codeLeft = rowBounds.width() - padding - codeWidth;

            item.find("flag", Image.class).setBitmap(countryFlagBitmap(country.getFlagID()));
            item.find("name", Text.class)
                    .setRegion(new RectF(nameLeft, 0f, codeLeft, rowBounds.height()))
                    .setText(country.getName());
            item.find("code", Text.class)
                    .setRegion(new RectF(codeLeft, 0f,
                            rowBounds.width() - padding, rowBounds.height()))
                    .setText(callingCode);
            item.find("divider", Image.class).setVisible(position < visibleCountries.size() - 1);
        }
    }

    private static final class CountrySearchItem {
        final CCPCountry country;
        final String searchableText;

        CountrySearchItem(CCPCountry country) {
            this.country = country;
            String phoneCode = country.getPhoneCode() == null ? "" : country.getPhoneCode();
            searchableText = lower(country.getName()) + '\n'
                    + lower(country.getEnglishName()) + '\n'
                    + lower(country.getNameCode()) + '\n'
                    + phoneCode + '\n'
                    + '+' + phoneCode;
        }
    }

    private final class CountryListView extends View {
        private final ZLayerGroup nativeListLayers = new ZLayerGroup(this);
        private final ZLayer nativeListLayer = nativeListLayers.addLayer("countries");
        private final CountryAdapter adapter;
        private final int touchSlop;
        private float gestureDownY;
        private boolean keyboardDismissedForGesture;

        CountryListView(Context context, CountryAdapter adapter) {
            super(context);
            this.adapter = adapter;
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            setClickable(true);
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            if (width <= 0 || height <= 0) return;
            nativeListLayer.clear();
            nativeListLayer.add(new ComponentList.Builder<CCPCountry>(getContext(),
                    "country_native_list", new RectF(0f, 0f, width, height))
                    .setOrientation(ComponentList.Orientation.VERTICAL)
                    // Explicit RectF list bounds use runtime-pixel dimensions (scale = 1).
                    .setItemSize(dpValue(COUNTRY_ROW_HEIGHT_DP))
                    .setItemSpacing(0f)
                    .setAdapter(adapter)
                    .setOverscrollEnabled(false)
                    .setClipToBounds(true)
                    .setOnItemClickListener((list, country, position) -> selectCountry(country)));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            nativeListLayers.draw(canvas);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    gestureDownY = event.getY();
                    keyboardDismissedForGesture = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!keyboardDismissedForGesture && keyboardVisible
                            && Math.abs(event.getY() - gestureDownY) > touchSlop) {
                        keyboardDismissedForGesture = true;
                        hideKeyboard();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    keyboardDismissedForGesture = false;
                    break;
                default:
                    break;
            }
            return nativeListLayers.onTouchEvent(event) || super.onTouchEvent(event);
        }
    }

    private static final class CountryCardLayout extends FrameLayout {
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path cardPath = new Path();
        private final float bottomRadius;
        private final float shadowSpread;

        CountryCardLayout(Context context, float bottomRadius, float shadowOffsetY,
                          float shadowBlur, float shadowSpread) {
            super(context);
            this.bottomRadius = bottomRadius;
            this.shadowSpread = shadowSpread;
            shadowPaint.setColor(COUNTRY_SHADOW_COLOR);
            shadowPaint.setStyle(Paint.Style.FILL);
            shadowPaint.setShadowLayer(shadowBlur, 0f, shadowOffsetY, COUNTRY_SHADOW_COLOR);
            cardPaint.setColor(Color.WHITE);
            cardPaint.setStyle(Paint.Style.FILL);
            setWillNotDraw(false);
            setClipChildren(false);
            setClipToPadding(false);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            RectF cardBounds = cardBounds();
            RectF shadowBounds = new RectF(cardBounds);
            shadowBounds.inset(-shadowSpread, -shadowSpread);
            Path shadowPath = bottomRoundedPath(shadowBounds, bottomRadius + shadowSpread);
            canvas.drawPath(shadowPath, shadowPaint);
            cardPath.set(bottomRoundedPath(cardBounds, bottomRadius));
            canvas.drawPath(cardPath, cardPaint);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            int save = canvas.save();
            canvas.clipPath(cardPath);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(save);
        }

        private RectF cardBounds() {
            return new RectF(getPaddingLeft(), getPaddingTop(),
                    getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        }

        private static Path bottomRoundedPath(RectF bounds, float radius) {
            Path path = new Path();
            float[] radii = {0f, 0f, 0f, 0f, radius, radius, radius, radius};
            path.addRoundRect(bounds, radii, Path.Direction.CW);
            return path;
        }
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

    public interface OnNextListener {
        void onNext(String fullPhoneNumber);
    }
}
