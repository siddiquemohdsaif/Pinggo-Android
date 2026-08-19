package com.w3n.pinggo.views.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;

import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;

/** Android host view containing one centered AAR-native Text component. */
public final class NativeMessageView extends View {
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer content = layers.addLayer("message");
  private final String message;
  private final int color;
  private final float textSizeSp;
  private boolean built;

  public NativeMessageView(Context context, String message, int color, float textSizeSp) {
    super(context);
    this.message = message;
    this.color = color;
    this.textSizeSp = textSizeSp;
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
    super.onSizeChanged(width, height, oldWidth, oldHeight);
    if (built || width <= 0 || height <= 0) return;
    built = true;
    content.add(new Text.Builder(getContext(), "message_text", message,
        new RectF(0, 0, width, height))
        .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
        .setTextSizePx(textSizeSp * getResources().getDisplayMetrics().scaledDensity)
        .setTextColor(color).setAlignment(Text.Alignment.CENTER)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(2));
  }

  @Override protected void onDraw(@NonNull Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
  public void release() { layers.release(); }
}
