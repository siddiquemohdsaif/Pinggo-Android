package com.w3n.pinggo.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.component.FigmaConfig;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.R;

/** Reusable AAR-native header for media overlays. */
public final class NativeMediaTopBarView extends View {
  public static final int BACKGROUND_COLOR = 0x40000000;

  private final FigmaConfig figma = new FigmaConfig(1080f);
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer chrome = layers.addLayer("media_header");
  private final Listener listener;
  private final String phone;
  private final String subtitle;
  private final boolean showMore;
  private final Bitmap shade = color(BACKGROUND_COLOR);
  private final Bitmap transparent = color(Color.TRANSPARENT);
  private final Bitmap back = BitmapFactory.decodeResource(getResources(), R.drawable.conversation_back);
  private final Bitmap forward = BitmapFactory.decodeResource(getResources(), R.drawable.conversation_selection_forward);
  private final Bitmap more = BitmapFactory.decodeResource(getResources(), R.drawable.home_overflow_dots);
  private int topInset;

  public NativeMediaTopBarView(@NonNull Context context, String phone, Listener listener) {
    this(context, phone, "", false, listener);
  }
  public NativeMediaTopBarView(@NonNull Context context, String phone, String subtitle,
      boolean showMore, Listener listener) {
    super(context); this.phone = phone == null ? "" : phone;
    this.subtitle = subtitle == null ? "" : subtitle; this.showMore = showMore;
    this.listener = listener; setClickable(true);
  }
  public static int contentHeightPx(Context context) {
    float width = Math.max(1, context.getResources().getDisplayMetrics().widthPixels);
    return Math.round(170f * new FigmaConfig(1080f).getScale(width));
  }

  public void setTopInset(int value) { topInset=Math.max(0,value); if(getWidth()>0) build(); }
  @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);build();}
  private void build() {
    int width = getWidth(), height = getHeight();
    if (width <= 0 || height <= 0) return;
    chrome.clear();
    float scale = figma.getScale(width);
    float top = topInset;
    chrome.add(new Image.Builder(getContext(), "header_shade", shade,
        new RectF(0, 0, width, height)).setScaleType(Image.ScaleType.FIT_XY));
    iconButton("media_back", back,
        new RectF(51f * scale, top + 60f * scale, 102f * scale, top + 111f * scale),
        new RectF(25f * scale, top + 34f * scale, 128f * scale, top + 137f * scale),
        id -> listener.onBack());
    float textRight = showMore ? 795f * scale : width - 170f * scale;
    chrome.add(new Text.Builder(getContext(), "media_phone", phone,
        new RectF(165f * scale, top + 42f * scale, textRight, top + 91f * scale))
        .setFont(NativeFonts.INTER)
        .setFontVariations(FontVariation.MEDIUM)
        .setTextSizePx(38f * scale)
        .setTextColor(Color.WHITE)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER)
        .setMaxLines(1));
    if (!subtitle.isEmpty()) {
      chrome.add(new Text.Builder(getContext(), "media_time", subtitle,
          new RectF(165f * scale, top + 95f * scale, textRight, top + 139f * scale))
          .setFont(NativeFonts.INTER)
          .setFontVariations(FontVariation.REGULAR)
          .setTextSizePx(31f * scale)
          .setTextColor(0xFFE8EDF0)
          .setVerticalAlignment(Text.VerticalAlignment.CENTER)
          .setMaxLines(1));
    }
    // MessageSelectionHeaderComponent spaces adjacent action centers by 110 Figma units.
    float forwardCenter = showMore ? 906f : 1000f;
    iconButton("media_forward", forward,
        new RectF((forwardCenter - 25.5f) * scale, top + 60f * scale,
            (forwardCenter + 25.5f) * scale, top + 111f * scale),
        new RectF((forwardCenter - 49f) * scale, top + 35f * scale,
            (forwardCenter + 49f) * scale, top + 136f * scale),
        id -> listener.onForward());
    if (showMore) {
      iconButton("media_more", more,
          new RectF(1000f * scale, top + 55f * scale,
              1032f * scale, top + 112f * scale),
          new RectF(972f * scale, top + 27f * scale,
              1060f * scale, top + 140f * scale),
          id -> listener.onMore(this));
    }
    invalidate();
  }

  private void iconButton(String id, Bitmap icon, RectF iconBounds, RectF touchBounds,
      Button.OnClickListener click) {
    chrome.add(new Image.Builder(getContext(), id + "_icon", icon, iconBounds)
        .setScaleType(Image.ScaleType.FIT_CENTER));
    chrome.add(new Button.Builder(getContext(), id + "_touch", transparent, "", touchBounds)
        .setImageScaleType(Image.ScaleType.FIT_XY)
        .setCornerRadiusPx(0)
        .setRippleEnabled(true).setWaitForRippleBeforeClick(true)
        .setRippleColor(0x16019CC4)
        .setOnClickListener(click));
  }
  @Override protected void onDraw(@NonNull Canvas c){super.onDraw(c);layers.draw(c);}
  @Override public boolean onTouchEvent(MotionEvent e){return layers.onTouchEvent(e)||super.onTouchEvent(e);}
  public void release(){layers.release();recycle(shade,transparent,back,forward,more);}
  private float px(float v){return figma.toRuntime(v,Math.max(1,getResources().getDisplayMetrics().widthPixels));}
  private float sp(float v){return v*getResources().getDisplayMetrics().scaledDensity;}
  private static Bitmap color(int v){Bitmap b=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);b.eraseColor(v);return b;}
  private static void recycle(Bitmap...a){for(Bitmap b:a)if(b!=null&&!b.isRecycled())b.recycle();}
  public interface Listener{void onBack();void onForward();default void onMore(View anchor) {}}
}
