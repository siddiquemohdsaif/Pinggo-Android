package com.w3n.pinggo.views.settings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

/** AAR-native profile settings surface. */
public final class SettingsView extends View {
  private static final int PRIMARY = 0xFF000E1A, SECONDARY = 0xFF687382, ACCENT = 0xFF019CC4;
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer bg = layers.addLayer("background"), content = layers.addLayer("content");
  private final Listener listener;
  private final Bitmap white = color(Color.WHITE),
      line = color(0xFFE5EAF0),
      accent = color(ACCENT),
      danger = color(0xFFCF3344);
  private Bitmap profileBitmap;
  private Image profileImage;
  private Text nameValue, phoneValue;
  private Button logout;
  private int topInset, bottomInset;

  public SettingsView(Context c, Listener l) {
    super(c);
    listener = l;
    setBackgroundColor(0xFFF7F9FB);
    setClickable(true);
  }

  public void setInsets(int t, int b) {
    topInset = Math.max(0, t);
    bottomInset = Math.max(0, b);
    if (getWidth() > 0) build();
  }

  public void setValues(String name, String phone) {
    if (nameValue != null) nameValue.setText(value(name));
    if (phoneValue != null) phoneValue.setText(value(phone));
    invalidate();
  }

  public void setProfilePhoto(Bitmap bitmap) {
    profileBitmap = bitmap;
    if (profileImage != null) profileImage.setBitmap(bitmap == null ? placeholder() : bitmap);
    invalidate();
  }

  public void setLoading(boolean loading) {
    if (logout != null) logout.setEnabled(!loading).setAlpha(loading ? .55f : 1f);
    invalidate();
  }

  @Override
  protected void onSizeChanged(int w, int h, int ow, int oh) {
    super.onSizeChanged(w, h, ow, oh);
    if (w > 0 && h > 0) build();
  }

  private void build() {
    bg.clear();
    content.clear();
    float w = getWidth(), top = topInset + dp(10);
    bg.add(
        new Image.Builder(getContext(), "bg", white, new RectF(0, 0, w, getHeight()))
            .setScaleType(Image.ScaleType.FIT_XY));
    button(
        "back",
        white,
        "‹",
        new RectF(dp(8), top, dp(56), top + dp(48)),
        PRIMARY,
        id -> listener.onBack());
    text(
        "title",
        "Settings",
        new RectF(dp(64), top, w - dp(20), top + dp(48)),
        sp(24),
        PRIMARY,
        FontVariation.BOLD);
    float photoTop = top + dp(78);
    profileImage =
        content.add(
            new Image.Builder(
                    getContext(),
                    "photo",
                    profileBitmap == null ? placeholder() : profileBitmap,
                    new RectF(w / 2 - dp(70), photoTop, w / 2 + dp(70), photoTop + dp(140)))
                .setScaleType(Image.ScaleType.CENTER_CROP)
                .setOnClickListener(id -> listener.onPhoto()));
    button(
        "edit_photo",
        accent,
        "Edit photo",
        new RectF(w / 2 - dp(62), photoTop + dp(150), w / 2 + dp(62), photoTop + dp(196)),
        Color.WHITE,
        id -> listener.onPhoto());
    float row = photoTop + dp(228);
    nameValue = addRow("name", "Name", row, id -> listener.onName());
    row += dp(82);
    phoneValue = addRow("phone", "Phone number", row, id -> listener.onPhone());
    logout =
        button(
            "logout",
            danger,
            "Log out",
            new RectF(
                dp(24),
                getHeight() - bottomInset - dp(76),
                w - dp(24),
                getHeight() - bottomInset - dp(20)),
            Color.WHITE,
            id -> listener.onLogout());
    invalidate();
  }

  private Text addRow(String id, String label, float top, Button.OnClickListener click) {
    float w = getWidth();
    button(id, white, "", new RectF(dp(16), top, w - dp(16), top + dp(72)), PRIMARY, click);
    text(
        id + "_label",
        label,
        new RectF(dp(28), top + dp(8), w - dp(40), top + dp(32)),
        sp(13),
        SECONDARY,
        FontVariation.REGULAR);
    Text value =
        text(
            id + "_value",
            "-",
            new RectF(dp(28), top + dp(31), w - dp(50), top + dp(66)),
            sp(17),
            PRIMARY,
            FontVariation.SEMI_BOLD);
    content.add(
        new Image.Builder(
                getContext(),
                id + "_line",
                line,
                new RectF(dp(28), top + dp(71), w - dp(28), top + dp(72)))
            .setScaleType(Image.ScaleType.FIT_XY));
    return value;
  }

  private Text text(String id, String v, RectF r, float sz, int c, FontVariation f) {
    return content.add(
        new Text.Builder(getContext(), id, v, r)
            .setFont(NativeFonts.INTER)
            .setFontVariations(f)
            .setTextSizePx(sz)
            .setTextColor(c)
            .setVerticalAlignment(Text.VerticalAlignment.CENTER)
            .setMaxLines(1));
  }

  private Button button(
      String id, Bitmap b, String label, RectF r, int c, Button.OnClickListener l) {
    return content.add(
        new Button.Builder(getContext(), id, b, label, r)
            .setImageScaleType(Image.ScaleType.FIT_XY)
            .setCornerRadiusPx(dp(16))
            .setFont(NativeFonts.INTER)
            .setFontVariations(FontVariation.SEMI_BOLD)
            .setTextSizePx(sp(16))
            .setTextColor(c)
            .setRippleEnabled(true)
            .setRippleColor(0x22019CC4)
            .setOnClickListener(l));
  }

  private Bitmap placeholder() {
    int s = Math.round(dp(140));
    Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    Paint p = new Paint(1);
    p.setColor(0xFFD9F1F7);
    c.drawCircle(s / 2f, s / 2f, s / 2f, p);
    p.setColor(ACCENT);
    p.setTextSize(s * .42f);
    p.setTextAlign(Paint.Align.CENTER);
    Paint.FontMetrics m = p.getFontMetrics();
    c.drawText("☺", s / 2f, s / 2f - (m.ascent + m.descent) / 2, p);
    return b;
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    layers.draw(c);
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    return layers.onTouchEvent(e) || super.onTouchEvent(e);
  }

  public void release() {
    layers.release();
    recycle(white, line, accent, danger);
  }

  private String value(String s) {
    return s == null || s.trim().isEmpty() ? "-" : s;
  }

  private float dp(float v) {
    return v * getResources().getDisplayMetrics().density;
  }

  private float sp(float v) {
    return v * getResources().getDisplayMetrics().scaledDensity;
  }

  private static Bitmap color(int c) {
    Bitmap b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    b.eraseColor(c);
    return b;
  }

  private static void recycle(Bitmap... bs) {
    for (Bitmap b : bs) if (b != null && !b.isRecycled()) b.recycle();
  }

  public interface Listener {
    void onBack();

    void onPhoto();

    void onName();

    void onPhone();

    void onLogout();
  }
}
