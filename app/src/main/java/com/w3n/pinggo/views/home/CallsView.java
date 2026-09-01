package com.w3n.pinggo.views.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.modals.CallLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Scrollable call list implemented with native-views-release.aar components. */
public final class CallsView extends View {
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
    private static final int PRIMARY = 0xFF000E1A;
    private static final int SECONDARY = 0xFF687382;
    private static final int ACCENT = 0xFF019CC4;

    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final ZLayer listLayer = layers.addLayer("call_list");
    private final ZLayer stateLayer = layers.addLayer("call_state");
    private final CallAdapter adapter = new CallAdapter();
    private final OnCallClickListener clickListener;
    private final Bitmap dividerBitmap = colorBitmap(0xFFE5EAF0);
    private ComponentList<CallLog> list;
    private Text emptyText;

    public CallsView(Context context, OnCallClickListener clickListener) {
        super(context);
        this.clickListener = clickListener;
        setClickable(true);
    }

    public void submitCalls(List<CallLog> calls) {
        adapter.submit(calls);
        updateVisibility();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width <= 0 || height <= 0) return;
        listLayer.clear();
        stateLayer.clear();
        list = listLayer.add(new ComponentList.Builder<CallLog>(getContext(), "call_component_list",
                new RectF(0, 0, width, height)).setOrientation(ComponentList.Orientation.VERTICAL)
                .setItemSize(px(209f)).setPaddingPx(px(33f), px(11f), px(33f), px(264f))
                .setAdapter(adapter).setClipToBounds(true).setOverscrollEnabled(false)
                .setOnItemClickListener((componentList, call, position) ->
                        clickListener.onCallClick(call)));
        emptyText = stateLayer.add(new Text.Builder(getContext(), "empty_calls", "No calls found.",
                new RectF(px(55f), px(77f), width - px(55f), px(308f)))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
                .setTextSizePx(sp(16)).setTextColor(SECONDARY).setAlignment(Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER));
        updateVisibility();
    }

    private void updateVisibility() {
        if (list == null || emptyText == null) return;
        boolean empty = adapter.getItemCount() == 0;
        list.setVisible(!empty).setEnabled(!empty);
        emptyText.setVisible(empty);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        return layers.onTouchEvent(event) || super.onTouchEvent(event);
    }

    public void release() {
        layers.release();
        if (!dividerBitmap.isRecycled()) dividerBitmap.recycle();
    }

    private final class CallAdapter extends ComponentList.Adapter<CallLog> {
        private final List<CallLog> calls = new ArrayList<>();
        void submit(List<CallLog> values) {
            calls.clear();
            if (values != null) calls.addAll(values);
            notifyDataSetChanged();
        }
        @Override public int getItemCount() { return calls.size(); }
        @Override public CallLog getItem(int position) { return calls.get(position); }
        @Override public long getItemId(int position) {
            CallLog call = calls.get(position);
            String key = call.getContactName() + '|' + call.getFullCalledDateTime();
            return key.hashCode();
        }
        @Override public void onCreateItem(ComponentList.Item item, int type) {
            ComponentList.ItemScope scope = item.getScope();
            float width = scope.width();
            float height = scope.height();
            ZLayer row = item.addLayer("row");
            row.add(new Image.Builder(getContext(), scope.id("avatar"), avatar("?"),
                    new RectF(px(22f), px(27.5f), px(176f), px(181.5f)))
                    .setScaleType(Image.ScaleType.CENTER_CROP));
            row.add(rowText(scope.id("name"), new RectF(px(220f), px(19.25f), width - px(159.5f), px(110f)),
                    sp(17), PRIMARY, FontVariation.SEMI_BOLD));
            row.add(rowText(scope.id("time"), new RectF(px(220f), px(104.5f), width - px(159.5f), px(187f)),
                    sp(14), SECONDARY, FontVariation.REGULAR));
            row.add(new Text.Builder(getContext(), scope.id("type"), "",
                    new RectF(width - px(154f), 0, width - px(22f), height)).useDefaultFont()
                    .setTextSizePx(sp(22)).setTextColor(PRIMARY).setAlignment(Text.Alignment.CENTER)
                    .setVerticalAlignment(Text.VerticalAlignment.CENTER));
            row.add(new Image.Builder(getContext(), scope.id("divider"), dividerBitmap,
                    new RectF(px(220f), height - px(2.75f), width, height))
                    .setScaleType(Image.ScaleType.FIT_XY));
        }
        @Override public void onBindItem(ComponentList.Item item, CallLog call, int position) {
            item.find("avatar", Image.class).setBitmap(avatar(call.getContactName()));
            item.find("name", Text.class).setText(call.getContactName());
            item.find("time", Text.class).setText(call.getCalledTime());
            item.find("type", Text.class).setText(call.isVideoCall() ? "▣" : "☎");
            item.find("divider", Image.class).setVisible(position < calls.size() - 1);
        }
    }

    private Text.Builder rowText(String id, RectF bounds, float size, int color,
                                 FontVariation variation) {
        return new Text.Builder(getContext(), id, "", bounds).setFont(NativeFonts.INTER)
                .setFontVariations(variation).setTextSizePx(size).setTextColor(color)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER).setWrapEnabled(false);
    }

    private Bitmap avatar(String value) {
        int size = Math.max(1, Math.round(px(154f)));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFFD9F1F7);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        String label = value == null || value.trim().isEmpty() ? "?"
                : value.trim().substring(0, 1).toUpperCase(Locale.US);
        paint.setColor(ACCENT);
        paint.setTextSize(size * .42f);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(label, size / 2f,
                size / 2f - (metrics.ascent + metrics.descent) / 2f, paint);
        return bitmap;
    }

    private float px(float value) {
    return figmaConfig.toRuntime(value, Math.max(1, getResources().getDisplayMetrics().widthPixels));
  }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
    private static Bitmap colorBitmap(int color) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }

    public interface OnCallClickListener { void onCallClick(CallLog callLog); }
}
