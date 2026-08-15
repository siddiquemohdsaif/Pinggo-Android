package com.w3n.pinggo.views.login;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
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
import com.w3n.pinggo.R;

/** Native-view implementation of the WhatsApp verification-method screen. */
public class WhatsappLoginView extends View {
    private static final float REFERENCE_WIDTH = 1080f;
    private static final int ACCENT_COLOR = 0xFF019CC4;
    private static final int PRIMARY_TEXT_COLOR = 0xFF000E1A;
    private static final int MUTED_TEXT_COLOR = 0xFF656565;

    private final FigmaConfig figmaConfig = new FigmaConfig(REFERENCE_WIDTH);
    private final ZLayerGroup layerGroup = new ZLayerGroup(this);
    private final ZLayer backgroundLayer = layerGroup.addLayer("background");
    private final ZLayer foregroundLayer = layerGroup.addLayer("foreground");
    private final ZLayer cardLayer = layerGroup.addLayer("whatsapp_card");
    private final Bitmap backgroundBitmap;
    private final Bitmap illustrationBitmap;
    private final Bitmap whatsappLogoBitmap;
    private final Bitmap backBitmap;
    private final Bitmap arrowBitmap;
    private final Bitmap actionButtonBitmap = createActionButtonBackground();
    private final Bitmap methodBackgroundBitmap = createOutlinedBackground();
    private final Bitmap recommendedBackgroundBitmap = createRecommendedBackground();
    private final Bitmap transparentBitmap = colorBitmap(Color.TRANSPARENT);
    private final String fullPhoneNumber;

    private ZLayer cardContent;
    private OnBackListener backListener;
    private OnSendCodeListener sendCodeListener;
    private OnTryAnotherMethodListener tryAnotherMethodListener;
    private int statusBarInset;
    private int navigationBarInset;

    public WhatsappLoginView(Context context, @NonNull String fullPhoneNumber) {
        super(context);
        this.fullPhoneNumber = fullPhoneNumber;
        setBackgroundColor(Color.WHITE);
        setClickable(true);
        setFocusable(true);
        backgroundBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.pinggo_login_background_2);
        illustrationBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.pinggo_whatsapp_illustration);
        whatsappLogoBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.whatsapp_logo);
        backBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_email_back);
        arrowBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_pinggo_arrow);
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

    public void setOnSendCodeListener(OnSendCodeListener listener) {
        sendCodeListener = listener;
    }

    public void setOnTryAnotherMethodListener(OnTryAnotherMethodListener listener) {
        tryAnotherMethodListener = listener;
    }

    private void buildScreen() {
        backgroundLayer.clear();
        foregroundLayer.clear();
        cardLayer.clear();
        cardContent = null;

        backgroundLayer.add(new Image.Builder(getContext(), "login_background", backgroundBitmap,
                position(0f, 0f), new Size(designUnits(getWidth()),designUnits(getHeight())))
                .setScaleType(Image.ScaleType.CENTER_CROP));
        addBackButton();
        addIllustration();
        addLegalNotice();
        addVerificationCard();
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
        foregroundLayer.add(new Image.Builder(getContext(), "whatsapp_illustration",
                illustrationBitmap, position(0f, 333f+ designUnits(statusBarInset)), new Size(720f, 508f))
                .horizontalCenter(true)
                .setScaleType(Image.ScaleType.FIT_XY));
    }

    private void addVerificationCard() {
        Position cardPosition = new Position(this, figmaConfig,
                Position.HorizontalMarginFrom.LEFT, Position.VerticalMarginFrom.BOTTOM,
                0f, 206.620f + designUnits(navigationBarInset));
        Card card = cardLayer.add(new Card.Builder(getContext(), "verify_phone_card",
                cardPosition, new Size(940.563f, 900f))
                .setBackgroundColor(Color.WHITE)
                .setCornerRadius(63.380f)
                .horizontalCenter(true)
                .setDropShadow(new DropShadow(0f, 5.070f, 35.493f, 5.070f,
                        Color.argb(13, 0, 0, 0))));
        cardContent = card.getContentLayer();
        cardContent.setTouchPolicy(ZLayer.TouchPolicy.BLOCK_BELOW);

        addCardText(card, "title", getString(R.string.verify_your_phone_number),
                70f, 820f, 80f, 50f, PRIMARY_TEXT_COLOR, FontVariation.BOLD, 1);
        addCardText(card, "description",
                getString(R.string.whatsapp_verification_description, fullPhoneNumber),
                163f, 820f, 120f, 33f, MUTED_TEXT_COLOR, FontVariation.REGULAR, 2);
        addMethodRow(card);
        addSendButton(card);
        addAlternativeMethod(card);
    }

    private void addMethodRow(Card card) {
        cardContent.add(new Button.Builder(getContext(), "whatsapp_method",
                methodBackgroundBitmap, cardPosition(card, 63.380f, 315f),
                new Size(813.803f, 180f))
                .setImageScaleType(Image.ScaleType.FIT_XY)
                .setCornerRadius(27.887f)
                .setRippleEnabled(true)
                .setRippleColor(0x1600C853)
                .setOnClickListener(id -> {
                    if (sendCodeListener != null) sendCodeListener.onSendCode(fullPhoneNumber);
                }));
        addImage(card, "whatsapp_logo", whatsappLogoBitmap,
                110f, 350f, 100f, 100f);
        addCardTextAt(card, "whatsapp_label", getString(R.string.whatsapp),
                260f, 350f, 280f, 100f, 44f,
                PRIMARY_TEXT_COLOR, FontVariation.BOLD, Text.Alignment.START);
        addImage(card, "recommended_background", recommendedBackgroundBitmap,
                560f, 360f, 250f, 80f);
        addCardTextAt(card, "recommended", getString(R.string.recommended),
                560f, 360f, 250f, 80f, 31f,
                0xFF169B36, FontVariation.REGULAR, Text.Alignment.CENTER);
    }

    private void addSendButton(Card card) {
        cardContent.add(new Button.Builder(getContext(), "send_whatsapp_code", actionButtonBitmap,
                getString(R.string.send_code_on_whatsapp), cardPosition(card, 63.380f, 575f),
                new Size(813.803f, 139.437f))
                .setImageScaleType(Image.ScaleType.FIT_XY)
                .setCornerRadius(27.887f)
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSize(38f)
                .setTextColor(Color.WHITE)
                .setRippleEnabled(true)
                .setRippleColor(0x33FFFFFF)
                .setOnClickListener(id -> {
                    if (sendCodeListener != null) sendCodeListener.onSendCode(fullPhoneNumber);
                }));
        addImage(card, "next_arrow", arrowBitmap,
                706.056f, 620.633f, 48.169f, 48.169f);
    }

    private void addAlternativeMethod(Card card) {
        addCardTextAt(card, "alternative_prefix", getString(R.string.cant_use_whatsapp),
                145f, 756f, 420f, 70f, 31f,
                MUTED_TEXT_COLOR, FontVariation.REGULAR, Text.Alignment.START);
        cardContent.add(new Button.Builder(getContext(), "try_another_method",
                transparentBitmap, getString(R.string.try_another_method),
                cardPosition(card, 470f, 756f),
                new Size(330f, 70f))
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSize(31f)
                .setTextColor(ACCENT_COLOR)
                .setRippleEnabled(true)
                .setRippleColor(0x16019CC4)
                .setOnClickListener(id -> {
                    if (tryAnotherMethodListener != null) {
                        tryAnotherMethodListener.onTryAnotherMethod();
                    }
                }));
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

    private void addCardTextAt(Card card, String id, String value, float left, float top,
                               float width, float height, float textSize, int color,
                               FontVariation variation, Text.Alignment alignment) {
        cardContent.add(new Text.Builder(getContext(), id, value,
                cardPosition(card, left, top), new Size(width, height))
                .setFont(NativeFonts.INTER)
                .setFontVariations(variation)
                .setTextSize(textSize)
                .setTextColor(color)
                .setAlignment(alignment)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setWrapEnabled(false));
    }

    private void addImage(Card card, String id, Bitmap bitmap, float left, float top,
                          float width, float height) {
        cardContent.add(new Image.Builder(getContext(), id, bitmap,
                cardPosition(card, left, top), new Size(width, height))
                .setScaleType(Image.ScaleType.FIT_XY));
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

    private static Bitmap createActionButtonBackground() {
        Bitmap bitmap = Bitmap.createBitmap(642, 110, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0f, 0f, bitmap.getWidth(), 0f,
                0xFF05A7D5, 0xFF019BC5, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(0f, 0f, bitmap.getWidth(), bitmap.getHeight(),
                22f, 22f, paint);
        return bitmap;
    }

    private static Bitmap createOutlinedBackground() {
        Bitmap bitmap = Bitmap.createBitmap(642, 142, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(2f, 2f, bitmap.getWidth() - 2f, bitmap.getHeight() - 2f,
                20f, 20f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0xFFDDE3EA);
        canvas.drawRoundRect(2f, 2f, bitmap.getWidth() - 2f, bitmap.getHeight() - 2f,
                20f, 20f, paint);
        return bitmap;
    }

    private static Bitmap createRecommendedBackground() {
        Bitmap bitmap = Bitmap.createBitmap(250, 80, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFFDDF8E4);
        canvas.drawRoundRect(0f, 0f, bitmap.getWidth(), bitmap.getHeight(),
                18f, 18f, paint);
        return bitmap;
    }

    private static Bitmap colorBitmap(int color) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
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

    public interface OnSendCodeListener {
        void onSendCode(String fullPhoneNumber);
    }

    public interface OnTryAnotherMethodListener {
        void onTryAnotherMethod();
    }
}
