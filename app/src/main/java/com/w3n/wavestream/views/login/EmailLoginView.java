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
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

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
import com.ogfa.nativeviews.textfield.TextField;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.wavestream.R;

/** Native-view implementation of the email step in the login flow. */
public class EmailLoginView extends View {
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
    private final Bitmap illustrationBitmap;
    private final Bitmap backBitmap;
    private final Bitmap arrowBitmap;
    private final Bitmap whiteBitmap = colorBitmap(Color.WHITE);
    private final Bitmap dividerBitmap = colorBitmap(0xFFE3E7EC);
    private final Bitmap buttonBitmap = createButtonBackground();
    private final Bitmap googleButtonBitmap = createGoogleButtonBackground();
    private final Bitmap googleLogoBitmap;
    private final String fullPhoneNumber;

    private ZLayer loginCardContent;
    private TextField emailField;
    private Text emailErrorText;
    private Text emailLabelText;
    private String savedEmail = "";
    private OnNextListener nextListener;
    private OnBackListener backListener;
    private int statusBarInset;
    private int navigationBarInset;
    private int keyboardInset;
    private boolean keyboardVisible;

    public EmailLoginView(Context context, @NonNull String fullPhoneNumber) {
        super(context);
        this.fullPhoneNumber = fullPhoneNumber;
        setBackgroundColor(Color.WHITE);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        backgroundBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.pinggo_login_background);
        illustrationBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.pinggo_email_illustration);
        backBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_email_back);
        arrowBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_pinggo_arrow);
        googleLogoBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.google_logo);
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

    public String getEmail() {
        return emailField == null ? savedEmail : emailField.getText().trim();
    }

    public String getFullPhoneNumber() {
        return fullPhoneNumber;
    }

    public void setOnNextListener(OnNextListener listener) {
        nextListener = listener;
    }

    public void setOnBackListener(OnBackListener listener) {
        backListener = listener;
    }

    private void buildScreen() {
        if (emailField != null) savedEmail = emailField.getText();
        backgroundLayer.clear();
        foregroundLayer.clear();
        loginCardLayer.clear();
        loginCardContent = null;
        emailField = null;
        emailErrorText = null;
        emailLabelText = null;

        backgroundLayer.add(new Image.Builder(getContext(), "login_background", backgroundBitmap,
                position(0f, 0f), new Size(designUnits(getWidth()),designUnits(getHeight())))
                .setScaleType(Image.ScaleType.CENTER_CROP));
        addTopContent();
        addLegalNotice();
        addLoginCard();
        updateKeyboardTranslation();
        invalidate();
    }

    private void addTopContent() {
        float topOffset = designUnits(statusBarInset);
        foregroundLayer.add(new Button.Builder(getContext(), "back_button", backBitmap,
                position(87f, 85f + topOffset), new Size(64f, 64f))
                .setImageScaleType(Image.ScaleType.FIT_CENTER)
                .setRippleEnabled(true)
                .setRippleColor(0x22000000)
                .setOnClickListener(id -> {
                    if (backListener != null) backListener.onBack();
                }));
        foregroundLayer.add(new Image.Builder(getContext(), "email_illustration",
                illustrationBitmap, position(306f, 320f + topOffset), new Size(594f, 396f))
                .horizontalCenter(true)
                .setScaleType(Image.ScaleType.FIT_XY));
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
        Card card = loginCardLayer.add(new Card.Builder(getContext(), "email_card",
                cardPosition, new Size(940.563f, 1076.197f))
                .setBackgroundColor(Color.WHITE)
                .setCornerRadius(63.380f)
                .horizontalCenter(true)
                .setDropShadow(new DropShadow(0f, 5.070f, 35.493f, 5.070f,
                        Color.argb(13, 0, 0, 0))));
        loginCardContent = card.getContentLayer();
        loginCardContent.setTouchPolicy(ZLayer.TouchPolicy.BLOCK_BELOW);

        addCardText(card, "card_title", getString(R.string.enter_your_email),
                66f, 780f, 86f, 57.042f,
                PRIMARY_TEXT_COLOR, FontVariation.BOLD, 1);
        addCardText(card, "card_description", getString(R.string.email_description),
                162f, 760f, 112f, 31.690f,
                MUTED_TEXT_COLOR, FontVariation.REGULAR, 2);
        addEmailField(card);
        addSendCodeButton(card);
        addDivider(card);
        addGoogleButton(card);
    }

    private void addEmailField(Card card) {
        emailField = new TextField.Builder(getContext(), "email_field",
                cardPosition(card, 63.380f, 365f), new Size(813.803f, 147.042f))
                .setHint(getString(R.string.email_example_hint))
                .setText(savedEmail)
                .setMaxLength(254)
                .setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
                .setImeOptions(EditorInfo.IME_ACTION_NEXT)
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSize(36.761f)
                .setTextColor(PRIMARY_TEXT_COLOR)
                .setHintColor(0xFF757575)
                .setCursorColor(ACCENT_COLOR)
                .setCursorWidth(5.070f)
                .setSelectionColor(0x443B9CFF)
                .setBackgroundColor(Color.WHITE, Color.WHITE)
                .setStrokeColor(ACCENT_COLOR, ACCENT_COLOR)
                .setStrokeWidth(4.437f)
                .setCornerRadius(25.352f)
                .setPadding(38.028f, 22.817f)
                .setOnTextChangedListener((id, text) -> clearEmailError())
                .setOnFocusChangedListener((id, focused) -> {
                    updateFocusedFieldAppearance();
                    post(this::updateKeyboardTranslation);
                })
                .build(this);
        loginCardContent.add(emailField);
        emailLabelText = addFieldLabel(card, "email_label", getString(R.string.email_address),
                98.873f, 347.254f, 205f, ACCENT_COLOR);
        emailErrorText = new Text.Builder(getContext(), "email_error", "",
                cardPosition(card, 68.451f, 515f), new Size(798.592f, 40f))
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSize(24.085f)
                .setTextColor(0xFFD32F2F)
                .setAlignment(Text.Alignment.START)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .build(this);
        emailErrorText.setVisible(false);
        loginCardContent.add(emailErrorText);
    }

    private void addSendCodeButton(Card card) {
        loginCardContent.add(new Button.Builder(getContext(), "next_button", buttonBitmap,
                getString(R.string.send_code), cardPosition(card, 63.380f, 575f),
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
                .setOnClickListener(id -> handleNext()));
        addImage(card, "next_arrow", arrowBitmap,
                706.056f, 620.633f, 48.169f, 48.169f);
    }

    private void addDivider(Card card) {
        addImage(card, "divider_left", dividerBitmap, 63.380f, 785f, 360f, 2.535f);
        addImage(card, "divider_right", dividerBitmap, 517f, 785f, 360f, 2.535f);
        addCardText(card, "divider_or", getString(R.string.or),
                754f, 80f, 62f, 31.690f,
                MUTED_TEXT_COLOR, FontVariation.BOLD, 1);
    }

    private void addGoogleButton(Card card) {
        loginCardContent.add(new Button.Builder(getContext(), "google_button", googleButtonBitmap,
                getString(R.string.continue_with_google), cardPosition(card, 63.380f, 843f),
                new Size(813.803f, 139.437f))
                .setImageScaleType(Image.ScaleType.FIT_XY)
                .setCornerRadius(27.887f)
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.BOLD)
                .setTextSize(36f)
                .setTextColor(0xFF001B48)
                .setRippleEnabled(true)
                .setRippleColor(0x16019CC4));
        addImage(card, "google_logo", googleLogoBitmap,
                174f, 881f, 64f, 64f);
    }

    private void handleNext() {
        String email = getEmail();
        if (email.isEmpty()) {
            showEmailError(getString(R.string.email_required));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showEmailError(getString(R.string.invalid_email));
            return;
        }
        clearEmailError();
        if (nextListener != null) nextListener.onNext(fullPhoneNumber, email);
    }

    private void showEmailError(String message) {
        if (emailErrorText != null) emailErrorText.setText(message).setVisible(true);
        if (emailField != null) {
            emailField.requestFocus();
            emailField.setSelection(emailField.getText().length());
        }
        invalidate();
    }

    private void clearEmailError() {
        if (emailErrorText != null) emailErrorText.setVisible(false);
    }

    private Text addFieldLabel(Card card, String id, String label, float left, float top,
                               float width, int color) {
        addImage(card, id + "_background", whiteBitmap,
                left - 10.141f, top + 5.070f, width, 35.493f);
        Text labelText = new Text.Builder(getContext(), id, label,
                cardPosition(card, left, top), new Size(width, 48.169f))
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSize(29.155f)
                .setTextColor(color)
                .setAlignment(Text.Alignment.START)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setWrapEnabled(false)
                .build(this);
        loginCardContent.add(labelText);
        return labelText;
    }

    private void addImage(Card card, String id, Bitmap bitmap, float left, float top,
                          float width, float height) {
        loginCardContent.add(new Image.Builder(getContext(), id, bitmap,
                cardPosition(card, left, top), new Size(width, height))
                .setScaleType(Image.ScaleType.FIT_XY));
    }

    private void addCardText(Card card, String id, String value, float top, float width,
                             float height, float textSize, int color,
                             FontVariation variation, int maxLines) {
        loginCardContent.add(new Text.Builder(getContext(), id, value,
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

    private void addCenteredText(String id, String value, float top, float width,
                                 float height, float textSize, int color,
                                 FontVariation variation, int maxLines) {
        foregroundLayer.add(new Text.Builder(getContext(), id, value,
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
        if (start >= 0) {
            text.setSpan(new ForegroundColorSpan(ACCENT_COLOR), start,
                    start + phrase.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private float designUnits(int pixels) {
        return getWidth() <= 0 ? 0f : pixels / figmaConfig.getScale(getWidth());
    }

    private void updateKeyboardTranslation() {
        if (loginCardContent == null) return;
        float translation = 0f;
        if (keyboardVisible && keyboardInset > 0 && emailField != null
                && emailField.isFocused()) {
            float gap = 30.423f * figmaConfig.getScale(getWidth());
            translation = Math.min(0f, getHeight() - keyboardInset - gap
                    - emailField.getBounds().bottom);
        }
        loginCardLayer.setTranslationY(translation);
        invalidate();
    }

    private void updateFocusedFieldAppearance() {
        if (emailLabelText != null && emailField != null) {
            emailLabelText.setTextColor(ACCENT_COLOR);
        }
        invalidate();
    }

    private static Bitmap colorBitmap(int color) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }

    private static Bitmap createButtonBackground() {
        Bitmap bitmap = Bitmap.createBitmap(642, 110, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0f, 0f, bitmap.getWidth(), 0f,
                0xFF05A7D5, 0xFF019BC5, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(0f, 0f, bitmap.getWidth(), bitmap.getHeight(),
                22f, 22f, paint);
        return bitmap;
    }

    private static Bitmap createGoogleButtonBackground() {
        Bitmap bitmap = Bitmap.createBitmap(642, 110, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(2f, 2f, bitmap.getWidth() - 2f, bitmap.getHeight() - 2f,
                20f, 20f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0xFFE0E5EC);
        canvas.drawRoundRect(2f, 2f, bitmap.getWidth() - 2f, bitmap.getHeight() - 2f,
                20f, 20f, paint);
        return bitmap;
    }

    private String getString(int stringId) {
        return getContext().getString(stringId);
    }

    private String getString(int stringId, Object... formatArgs) {
        return getContext().getString(stringId, formatArgs);
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
        void onNext(String fullPhoneNumber, String email);
    }

    public interface OnBackListener {
        void onBack();
    }
}
