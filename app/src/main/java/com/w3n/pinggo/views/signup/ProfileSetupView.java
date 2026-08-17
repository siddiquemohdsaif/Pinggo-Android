package com.w3n.pinggo.views.signup;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;

import com.ogfa.nativeviews.button.Button;
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
import com.w3n.pinggo.R;

/** AAR-native, 1080-wide Figma implementation of signup profile setup. */
public final class ProfileSetupView extends View {
    private static final float FIGMA_WIDTH = 1080f;
    private static final int ACCENT = 0xFF019CC4;
    private static final int PRIMARY = 0xFF000E1A;
    private static final int BODY = 0xFF3B3F4C;

    private final FigmaConfig figma = new FigmaConfig(FIGMA_WIDTH);
    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer background = layers.addLayer("background");
    private final ZLayer cardLayer = layers.addLayer("card");
    private final ZLayer foreground = layers.addLayer("foreground");
    private final Bitmap backgroundBitmap;
    private final Bitmap backBitmap;
    private final Bitmap lockBitmap;
    private final Bitmap cardBitmap;
    private final Bitmap transparent = solid(Color.TRANSPARENT);
    private final Bitmap white = solid(Color.WHITE);
    private final Bitmap nextBitmap;
    private final Bitmap avatarBitmap;
    private final Bitmap cameraBitmap;

    private ZLayer cardContent;
    private TextField nameField;
    private Text nameError;
    private Bitmap profilePhoto;
    private String savedName = "";
    private int topInset;
    private int keyboardInset;
    private boolean keyboardVisible;
    private OnBackListener backListener;
    private OnPhotoListener photoListener;
    private OnNextListener nextListener;

    public ProfileSetupView(Context context) {
        super(context);
        setBackgroundColor(Color.WHITE);
        setClickable(true);
        setFocusableInTouchMode(true);
        backgroundBitmap = BitmapFactory.decodeResource(
                getResources(), R.drawable.pinggo_login_background_2);
        backBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_email_back);
        lockBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_pinggo_lock);
        cardBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.signup_profile_card);
        nextBitmap = makeNext(BitmapFactory.decodeResource(
                getResources(), R.drawable.ic_pinggo_arrow));
        avatarBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.signup_avatar);
        cameraBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.signup_camera);
    }

    public void setOnBackListener(OnBackListener listener) { backListener = listener; }
    public void setOnPhotoListener(OnPhotoListener listener) { photoListener = listener; }
    public void setOnNextListener(OnNextListener listener) { nextListener = listener; }

    public void clearListeners() {
        backListener = null;
        photoListener = null;
        nextListener = null;
    }

    public void setProfilePhoto(Bitmap photo) {
        profilePhoto = photo;
        if (getWidth() > 0) build();
    }

    public void setInsets(int statusBar, int navigationBar, int ime, boolean imeVisible) {
        int nextTop = Math.max(0, statusBar);
        boolean rebuild = topInset != nextTop;
        topInset = nextTop;
        keyboardInset = Math.max(0, ime);
        keyboardVisible = imeVisible;
        if (rebuild && getWidth() > 0) build(); else moveForKeyboard();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) build();
    }

    private void build() {
        if (nameField != null) savedName = nameField.getText();
        background.clear();
        cardLayer.clear();
        foreground.clear();
        nameField = null;
        nameError = null;
        float offset = units(topInset);
        background.add(new Image.Builder(getContext(), "background", backgroundBitmap,
                pos(0f, 0f), new Size(units(getWidth()), units(getHeight())))
                .setScaleType(Image.ScaleType.CENTER_CROP));
        addCard(offset);
        addHeader(offset);
        moveForKeyboard();
        invalidate();
    }

    private void addHeader(float offset) {
        foreground.add(new Button.Builder(getContext(), "back_button", backBitmap,
                pos(87f, 95f + offset), new Size(64f, 64f))
                .setImageScaleType(Image.ScaleType.FIT_CENTER)
                .setRippleEnabled(true)
                .setRippleColor(0x22000000)
                .setOnClickListener(id -> {
                    if (backListener != null) backListener.onBack();
                }));
        centered("title", string(R.string.set_up_profile), 380f + offset,
                620f, 82f, 57f, PRIMARY, FontVariation.SEMI_BOLD, 1);
        centered("subtitle", string(R.string.profile_setup_description), 472f + offset,
                650f, 114f, 38f, BODY, FontVariation.REGULAR, 2);
    }



    private void addCard(float offset) {
        cardLayer.add(new Image.Builder(getContext(), "profile_card", cardBitmap,
                pos(59f, 760f + offset), new Size(961f, 1132f))
                .setScaleType(Image.ScaleType.FIT_XY));
        cardContent = cardLayer;
        addAvatar(offset);
        addName(offset);
        image("lock", lockBitmap, 167f, 1453f + offset, 32f, 38f);
        cardContent.add(text("visibility", string(R.string.profile_name_visibility),
                pos(217f, 1436f + offset), 730f, 76f, 29f, 0xFF656565,
                FontVariation.REGULAR, Text.Alignment.START, 2));
        cardContent.add(new Button.Builder(getContext(), "next", nextBitmap,
                string(R.string.next), pos(132f, 1552f + offset), new Size(816f, 140f))
                .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadius(28f)
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.BOLD)
                .setTextSize(38f).setTextColor(Color.WHITE).setRippleEnabled(true)
                .setRippleColor(0x33FFFFFF).setRippleDuration(320L)
                .setRippleOrigin(Button.RippleOrigin.TOUCH)
                .setOnClickListener(id -> submit()));
        cardContent.add(text("change_later", string(R.string.profile_change_later),
                pos(145f, 1738f + offset), 790f, 70f, 29f, BODY,
                FontVariation.REGULAR, Text.Alignment.CENTER, 1));
    }

    private void addAvatar(float offset) {
        Bitmap displayedAvatar = profilePhoto == null ? avatarBitmap : makeAvatar(profilePhoto);
        cardLayer.add(new Image.Builder(getContext(), "avatar", displayedAvatar,
                pos(340f, 769f + offset), new Size(400f, 400f))
                .setScaleType(Image.ScaleType.FIT_XY));
        cardLayer.add(new Button.Builder(getContext(), "avatar_touch", transparent,
                pos(340f, 769f + offset), new Size(400f, 400f))
                .setImageScaleType(Image.ScaleType.FIT_XY).setRippleEnabled(true)
                .setRippleColor(0x22019CC4).setOnClickListener(id -> selectPhoto()));
        cardLayer.add(new Button.Builder(getContext(), "camera", cameraBitmap,
                pos(622f, 1050f + offset), new Size(112f, 112f))
                .setImageScaleType(Image.ScaleType.FIT_XY).setRippleEnabled(true)
                .setRippleColor(0x33FFFFFF).setOnClickListener(id -> selectPhoto()));
    }

    private void selectPhoto() {
        hideKeyboard();
        if (photoListener != null) photoListener.onPhotoClick();
    }
    private void addName(float offset) {
        nameField = new TextField.Builder(getContext(), "name", pos(132f, 1269f + offset),
                new Size(816f, 144f)).setHint(string(R.string.name_hint)).setText(savedName)
                .setMaxLength(80).setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_WORDS)
                .setImeOptions(EditorInfo.IME_ACTION_DONE).setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR).setTextSize(37f)
                .setTextColor(PRIMARY).setHintColor(0xFFB9BDC5).setCursorColor(ACCENT)
                .setCursorWidth(5f).setSelectionColor(0x443B9CFF)
                .setBackgroundColor(Color.WHITE, Color.WHITE).setStrokeColor(ACCENT, ACCENT)
                .setStrokeWidth(4f).setCornerRadius(25f).setPadding(44f, 22f)
                .setOnTextChangedListener((id, value) -> clearError())
                .setOnFocusChangedListener((id, focused) -> post(this::moveForKeyboard))
                .setOnEditorActionListener((id, action) -> {
                    if (action != EditorInfo.IME_ACTION_DONE) return false;
                    submit();
                    return true;
                }).build(this);
        cardContent.add(nameField);
        image("label_bg", white, 167f, 1257f + offset, 126f, 40f);
        cardContent.add(text("label", string(R.string.name), pos(179f, 1246f + offset),
                110f, 54f, 30f, ACCENT, FontVariation.REGULAR, Text.Alignment.START, 1));
        nameError = text("error", "", pos(137f, 1413f + offset), 800f, 38f,
                24f, 0xFFD32F2F, FontVariation.REGULAR, Text.Alignment.START, 1);
        nameError.setVisible(false);
        cardContent.add(nameError);
    }

    private void submit() {
        String name = nameField == null ? savedName.trim() : nameField.getText().trim();
        if (name.isEmpty()) {
            nameError.setText(string(R.string.name_required)).setVisible(true);
            nameField.requestFocus();
            invalidate();
            return;
        }
        clearError();
        hideKeyboard();
        if (nextListener != null) nextListener.onNext(name);
    }

    private void clearError() {
        if (nameError != null) nameError.setVisible(false);
        invalidate();
    }

    private void hideKeyboard() {
        if (nameField != null) nameField.clearFocus();
        InputMethodManager input = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (input != null) input.hideSoftInputFromWindow(getWindowToken(), 0);
        moveForKeyboard();
    }

    private void moveForKeyboard() {
        if (cardContent == null) return;
        float y = 0f;
        if (keyboardVisible && keyboardInset > 0 && nameField != null && nameField.isFocused()) {
            y = Math.min(0f, getHeight() - keyboardInset
                    - 30f * figma.getScale(getWidth()) - nameField.getBounds().bottom);
        }
        cardLayer.setTranslationY(y);
        invalidate();
    }

    private void centered(String id, String value, float top, float width, float height,
                          float size, int color, FontVariation weight, int maxLines) {
        foreground.add(new Text.Builder(getContext(), id, value, pos(0f, top),
                new Size(width, height)).setFont(NativeFonts.INTER)
                .setFontVariations(weight).setTextSize(size).setTextColor(color)
                .setAlignment(Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .horizontalCenter(true).setMaxLines(maxLines));
    }

    private Text text(String id, String value, Position position, float width, float height,
                      float size, int color, FontVariation weight,
                      Text.Alignment alignment, int maxLines) {
        return new Text.Builder(getContext(), id, value, position, new Size(width, height))
                .setFont(NativeFonts.INTER).setFontVariations(weight).setTextSize(size)
                .setTextColor(color).setAlignment(alignment)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(maxLines)
                .build(this);
    }

    private void image(String id, Bitmap bitmap, float left, float top,
                       float width, float height) {
        cardContent.add(new Image.Builder(getContext(), id, bitmap,
                pos(left, top), new Size(width, height))
                .setScaleType(Image.ScaleType.FIT_XY));
    }

    private Position pos(float left, float top) {
        return new Position(this, figma, Position.HorizontalMarginFrom.LEFT,
                Position.VerticalMarginFrom.TOP, left, top);
    }

    private float units(int pixels) {
        return getWidth() == 0 ? 0f : pixels / figma.getScale(getWidth());
    }

    private String string(int id) { return getContext().getString(id); }

    private static Bitmap solid(int color) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }

    private static Bitmap makeNext(Bitmap arrow) {
        Bitmap bitmap = Bitmap.createBitmap(816, 140, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(new LinearGradient(0f, 0f, 816f, 0f,
                0xFF05A7D5, 0xFF019BC5, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(0f, 0f, 816f, 140f, 28f, 28f, paint);
        paint.setShader(null);
        if (arrow != null) canvas.drawBitmap(arrow, null, new RectF(710f, 45f, 760f, 95f), paint);
        return bitmap;
    }

    private static Bitmap makeAvatar(Bitmap photo) {
        Bitmap bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(0xFF009FCD);
        paint.setPathEffect(new DashPathEffect(new float[]{11f, 10f}, 0f));
        canvas.drawCircle(200f, 200f, 197f, paint);
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setShadowLayer(9f, 0f, 5f, 0x22000000);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(200f, 200f, 174f, paint);
        paint.clearShadowLayer();
        if (photo == null || photo.isRecycled()) {
            paint.setColor(0xFFA9CCE8);
            canvas.drawCircle(200f, 200f, 168f, paint);
            paint.setColor(0xFFE9F3FD);
            canvas.drawCircle(200f, 151f, 61f, paint);
            canvas.drawOval(new RectF(77f, 251f, 323f, 394f), paint);
        } else {
            BitmapShader shader = new BitmapShader(photo, Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP);
            float scale = Math.max(336f / photo.getWidth(), 336f / photo.getHeight());
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate(200f - photo.getWidth() * scale / 2f,
                    200f - photo.getHeight() * scale / 2f);
            shader.setLocalMatrix(matrix);
            paint.setShader(shader);
            canvas.drawCircle(200f, 200f, 168f, paint);
            paint.setShader(null);
        }
        return bitmap;
    }



    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        layers.draw(canvas);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        return layers.onTouchEvent(event) || super.onTouchEvent(event);
    }
    @Override public boolean onCheckIsTextEditor() { return layers.onCheckIsTextEditor(); }
    @Override public InputConnection onCreateInputConnection(EditorInfo attrs) {
        InputConnection result = layers.onCreateInputConnection(attrs);
        return result != null ? result : super.onCreateInputConnection(attrs);
    }
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        return layers.onKeyDown(code, event) || super.onKeyDown(code, event);
    }

    public interface OnBackListener { void onBack(); }
    public interface OnPhotoListener { void onPhotoClick(); }
    public interface OnNextListener { void onNext(String name); }
}
