package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.component.FigmaConfig;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import java.util.ArrayList;
import java.util.List;

/** Text-only overflow dialog used by the conversation header's three-dot action. */
public final class ConversationMenuDialogView extends View {
  private static final float FIGMA_WIDTH = 1080f;
  private static final float MENU_WIDTH = 396f;
  private static final float MENU_TOP = 154f;
  private static final float MENU_RIGHT = 40f;
  private static final float FIRST_ROW_TOP = 25f;
  private static final float ROW_WIDTH = 396f;
  private static final float ROW_HEIGHT = 101f;
  private static final float ROW_GAP = 13f;
  private static final float MENU_BOTTOM_PADDING = 25f;
  private static final float TEXT_LEFT = 50f;
  private static final float TEXT_TOP = 32f;
  private static final float DOUBLE_LINE_TEXT_TOP_ADJUSTMENT = 20f;
  private static final float TEXT_SIZE = 34f;
  private static final int TEXT_COLOR = 0xFF101C31;

  public interface Listener {
    void onOptionSelected(String option);
  }

  private static final String[] OPTIONS = {
      "Add to contacts",
      "Search",
      "Mute notifications",
      "Block",
      "Report",
      "Clear chat"
  };

  private final FigmaConfig figmaConfig = new FigmaConfig(FIGMA_WIDTH);
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer menuLayer = layers.addLayer("conversation_overflow_menu");
  private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Bitmap transparent = colorBitmap(Color.TRANSPARENT);
  private final Listener listener;
  private final RectF menuBounds = new RectF();
  private boolean muted;
  private boolean blocked;
  private boolean contactExists;

  public ConversationMenuDialogView(@NonNull Context context, @NonNull Listener listener) {
    super(context);
    this.listener = listener;
    setClickable(true);
    setFocusableInTouchMode(true);
    setLayerType(LAYER_TYPE_SOFTWARE, null);
    setVisibility(INVISIBLE);
    menuLayer.setTouchPolicy(ZLayer.TouchPolicy.MODAL);
    cardPaint.setColor(Color.WHITE);
    cardPaint.setShadowLayer(22f, 0f, 8f, 0x24000000);
  }

  public void show() {
    if (getWidth() > 0) buildMenu(getWidth());
    setVisibility(VISIBLE);
    bringToFront();
    requestFocus();
    invalidate();
  }

  public void setMuted(boolean muted) {
    if (this.muted == muted) return;
    this.muted = muted;
    if (getWidth() > 0) buildMenu(getWidth());
    invalidate();
  }

  public void setBlocked(boolean blocked) {
    if (this.blocked == blocked) return;
    this.blocked = blocked;
    if (getWidth() > 0) buildMenu(getWidth());
    invalidate();
  }

  public void setContactExists(boolean contactExists) {
    if (this.contactExists == contactExists) return;
    this.contactExists = contactExists;
    if (getWidth() > 0) buildMenu(getWidth());
    invalidate();
  }

  public boolean dismissIfShowing() {
    if (getVisibility() != VISIBLE) return false;
    setVisibility(INVISIBLE);
    return true;
  }

  @Override
  protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    if (width > 0) buildMenu(width);
  }

  private void buildMenu(int hostWidth) {
    menuLayer.clear();
    List<String> options = visibleOptions();
    float scale = figmaConfig.getScale(hostWidth);
    float width = MENU_WIDTH * scale;
    float menuHeight =
        (FIRST_ROW_TOP
            + options.size() * ROW_HEIGHT
            + Math.max(0, options.size() - 1) * ROW_GAP
            + MENU_BOTTOM_PADDING) * scale;
    float left = hostWidth - MENU_RIGHT * scale - width;
    WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(this);
    Insets statusBars = windowInsets == null
        ? Insets.NONE
        : windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
    float top = statusBars.top + MENU_TOP * scale;
    menuBounds.set(left, top, left + width, top + menuHeight);
    for (int index = 0; index < options.size(); index++) {
      addOption(index, options.get(index), scale);
    }
  }

  private List<String> visibleOptions() {
    List<String> options = new ArrayList<>(OPTIONS.length);
    for (String option : OPTIONS) {
      if (contactExists && "Add to contacts".equals(option)) continue;
      if (muted && "Mute notifications".equals(option)) option = "Unmute notifications";
      if (blocked && "Block".equals(option)) option = "Unblock";
      options.add(option);
    }
    return options;
  }

  private void addOption(int index, String option, float scale) {
    boolean doubleLine = "Unmute notifications".equals(option);
    float rowTop =
        menuBounds.top + (FIRST_ROW_TOP + index * (ROW_HEIGHT + ROW_GAP)) * scale;
    float rowRight = menuBounds.left + ROW_WIDTH * scale;
    menuLayer.add(
        new Text.Builder(
                getContext(),
                "conversation_menu_label_" + index,
                option,
                new RectF(
                    menuBounds.left + TEXT_LEFT * scale,
                    rowTop + (TEXT_TOP - (doubleLine ? DOUBLE_LINE_TEXT_TOP_ADJUSTMENT : 0f)) * scale,
                    rowRight - 20f * scale,
                    rowTop + ROW_HEIGHT * scale))
            .setFont(NativeFonts.INTER)
            .setFontVariations(FontVariation.MEDIUM)
            .setTextSizePx(TEXT_SIZE * scale)
            .setTextColor(TEXT_COLOR)
            .setAlignment(Text.Alignment.START)
            .setVerticalAlignment(Text.VerticalAlignment.TOP)
            .setMaxLines(doubleLine ? 2 : 1));
    menuLayer.add(
        new Button.Builder(
                getContext(),
                "conversation_menu_touch_" + index,
                transparent,
                "",
                new RectF(menuBounds.left, rowTop, rowRight, rowTop + ROW_HEIGHT * scale))
            .setImageScaleType(Image.ScaleType.FIT_XY)
            .setRippleEnabled(true)
            .setRippleColor(0x12019CC4)
            .setOnClickListener(value -> {
              dismissIfShowing();
              listener.onOptionSelected(option);
            }));
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);
    if (!menuBounds.isEmpty()) {
      float radius = 54f * figmaConfig.getScale(getWidth());
      canvas.drawRoundRect(menuBounds, radius, radius, cardPaint);
    }
    layers.draw(canvas);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN
        && !menuBounds.contains(event.getX(), event.getY())) {
      dismissIfShowing();
      return true;
    }
    layers.onTouchEvent(event);
    return true;
  }

  public void release() {
    layers.release();
    if (!transparent.isRecycled()) transparent.recycle();
  }

  private static Bitmap colorBitmap(int color) {
    Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(color);
    return bitmap;
  }
}
