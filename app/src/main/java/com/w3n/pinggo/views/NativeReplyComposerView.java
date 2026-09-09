package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.textfield.TextField;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.R;

/** AAR-native reply field and send action used by media previews. */
final class NativeReplyComposerView extends View {
  interface Listener { void onSend(String text); }
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer layer = layers.addLayer("reply_composer");
  private final Bitmap fieldBackground = colorBitmap(0xFF1D2A31);
  private final Bitmap sendBackground = colorBitmap(0xFF019CC4);
  private final Bitmap sendIcon;
  private final Listener listener;
  private TextField field;
  private int bottomInset;

  NativeReplyComposerView(Context context, Listener listener) {
    super(context); this.listener = listener;
    sendIcon = BitmapFactory.decodeResource(getResources(), R.drawable.conversation_send);
    setClickable(true); setFocusableInTouchMode(true); setBackgroundColor(0x66000000);
  }

  void setBottomInset(int value) { bottomInset = Math.max(0, value); rebuild(); }
  private void rebuild() { if (getWidth() > 0 && getHeight() > 0) build(getWidth(), getHeight()); }
  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    build(width, height);
  }
  private void build(int width, int height) {
    layer.clear(); float density = getResources().getDisplayMetrics().density;
    float top = 8f * density;
    float bottom = Math.max(top + 1, height - bottomInset - 8f * density);
    field = layer.add(new TextField.Builder(getContext(), "reply_field",
        new RectF(12f * density, top, width - 12f * density, bottom))
        .setHint("Reply").setTextColor(Color.WHITE).setHintColor(0xFF9EA8AE)
        .setTextSizePx(17f * getResources().getDisplayMetrics().scaledDensity)
        .setBackgroundColor(0xFF1D2A31, 0xFF1D2A31).setStrokeColor(0xFF1D2A31, 0xFF019CC4)
        .setCornerRadiusPx(28f * density).setPaddingPx(18f * density, 8f * density)
        .setImeOptions(EditorInfo.IME_ACTION_SEND)
        .setOnEditorActionListener((id, action) -> {
          if (action != EditorInfo.IME_ACTION_SEND) return false; send(); return true;
        }));
    float size = 48f * density;
    float right = width - 16f * density;
    layer.add(new Button.Builder(getContext(), "send", sendBackground, "",
        new RectF(right - size, 12f * density, right, 12f * density + size))
        .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(26f * density)
        .setRippleEnabled(true).setOnClickListener(id -> send()));
    layer.add(new Image.Builder(getContext(), "send_icon", sendIcon,
        new RectF(right - size + 13f * density, 25f * density,
            right - 13f * density, 47f * density)).setScaleType(Image.ScaleType.FIT_CENTER));
  }
  private void send() {
    String text = field == null ? "" : field.getText().trim();
    if (text.isEmpty()) return;
    listener.onSend(text); field.setText("");
  }
  @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
  @Override public boolean onTouchEvent(MotionEvent event) { return layers.onTouchEvent(event) || super.onTouchEvent(event); }
  @Override public boolean onCheckIsTextEditor() { return layers.onCheckIsTextEditor(); }
  @Override public InputConnection onCreateInputConnection(EditorInfo attrs) {
    InputConnection value = layers.onCreateInputConnection(attrs);
    return value == null ? super.onCreateInputConnection(attrs) : value;
  }
  @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
    return layers.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
  }
  void release() { layers.release(); fieldBackground.recycle(); sendBackground.recycle(); sendIcon.recycle(); }
  private static Bitmap colorBitmap(int color) { Bitmap b=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888); b.eraseColor(color); return b; }
}
