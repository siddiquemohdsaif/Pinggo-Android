package com.w3n.pinggo.views.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.R;
import com.w3n.pinggo.modals.CallLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private final OnCallStartListener callStartListener;
    private final Runnable loadMoreListener;
    private final Bitmap dividerBitmap = colorBitmap(0xFFE5EAF0);
    private final Bitmap phoneIncomingBitmap = drawableBitmap(R.drawable.chat_phone_incoming);
    private final Bitmap phoneOutgoingBitmap = drawableBitmap(R.drawable.chat_phone_outgoing);
    private final Bitmap phoneMissedBitmap = drawableBitmap(R.drawable.chat_phone_missed);
    private final Bitmap videoIncomingBitmap = drawableBitmap(R.drawable.chat_video_incoming);
    private final Bitmap videoOutgoingBitmap = drawableBitmap(R.drawable.chat_video_outgoing);
    private final Bitmap videoMissedBitmap = drawableBitmap(R.drawable.chat_video_missed);
    private final Map<String, Bitmap> avatarCache = new HashMap<>();
    private ComponentList<CallLog> list;
    private Text emptyText;

    public CallsView(Context context, OnCallClickListener clickListener,
                     OnCallStartListener callStartListener, Runnable loadMoreListener) {
        super(context);
        this.clickListener = clickListener;
        this.callStartListener = callStartListener;
        this.loadMoreListener = loadMoreListener;
        setClickable(true);
    }

    public void submitCalls(List<CallLog> calls) {
        adapter.submit(calls);
        updateVisibility();
        post(this::loadAllPagesForSearch);
    }

    public void filter(String query) {
        adapter.filter(query);
        updateVisibility();
        post(this::loadAllPagesForSearch);
    }

    private void loadAllPagesForSearch() {
        if (!adapter.query.isEmpty() && loadMoreListener != null) loadMoreListener.run();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width <= 0 || height <= 0) return;
        listLayer.clear();
        stateLayer.clear();
        list = listLayer.add(new ComponentList.Builder<CallLog>(getContext(), "call_component_list",
                new RectF(0, 0, width, height)).setOrientation(ComponentList.Orientation.VERTICAL)
                .setItemSize(185f * figmaConfig.getScale(width))
                .setPaddingPx(0, 0, 0, 155f * figmaConfig.getScale(width))
                .setAdapter(adapter).setClipToBounds(true).setScrollEnabled(true)
                .setOverscrollEnabled(false)
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
        boolean handled = layers.onTouchEvent(event);
        if (handled) post(this::loadNextPageIfNeeded);
        return handled || super.onTouchEvent(event);
    }

    private void loadNextPageIfNeeded() {
        if (list != null && adapter.getItemCount() > 0
                && list.getLastVisiblePosition() >= adapter.getItemCount() - 3
                && loadMoreListener != null) loadMoreListener.run();
    }

    public void release() {
        layers.release();
        if (!dividerBitmap.isRecycled()) dividerBitmap.recycle();
        recycle(phoneIncomingBitmap, phoneOutgoingBitmap, phoneMissedBitmap,
                videoIncomingBitmap, videoOutgoingBitmap, videoMissedBitmap);
        for (Bitmap bitmap : avatarCache.values()) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        avatarCache.clear();
    }

    private final class CallAdapter extends ComponentList.Adapter<CallLog> {
        private final List<CallLog> all = new ArrayList<>();
        private final List<CallLog> calls = new ArrayList<>();
        private String query = "";
        void submit(List<CallLog> values) {
            all.clear();
            if (values != null) all.addAll(values);
            applyFilter();
        }
        void filter(String value) {
            query = value == null ? "" : value.trim().toLowerCase(Locale.US);
            applyFilter();
        }
        private void applyFilter() {
            calls.clear();
            for (CallLog call : all) {
                String name = call.getContactName() == null ? "" : call.getContactName();
                String phone = call.getPhoneNumber() == null ? "" : call.getPhoneNumber();
                if (query.isEmpty() || name.toLowerCase(Locale.US).contains(query)
                        || phone.toLowerCase(Locale.US).contains(query)) calls.add(call);
            }
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
            float scale = figmaConfig.getScale(getWidth());
            ZLayer row = item.addLayer("row");
            row.add(new ChatRowRippleComponent(scope.id("row_ripple"),
                    new RectF(0f, 0f, width, height)));
            row.add(new Image.Builder(getContext(), scope.id("avatar"), cachedAvatar("?"),
                    new RectF(50f * scale, 27f * scale, 182f * scale, 159f * scale))
                    .setScaleType(Image.ScaleType.CENTER_CROP));
            row.add(rowText(scope.id("name"), new RectF(220f * scale, 38f * scale,
                    width - 210f * scale, 92f * scale), 42f * scale, PRIMARY,
                    FontVariation.MEDIUM));
            row.add(rowText(scope.id("details"), new RectF(220f * scale, 103f * scale,
                    width - 210f * scale, 157f * scale), 38f * scale, SECONDARY,
                    FontVariation.REGULAR));
            row.add(new Image.Builder(getContext(), scope.id("type"), phoneOutgoingBitmap,
                    new RectF(width - 132f * scale, 57f * scale,
                            width - 52f * scale, 137f * scale))
                    .setScaleType(Image.ScaleType.FIT_CENTER));
            row.add(new ChatRowRippleComponent(scope.id("call_ripple"),
                    new RectF(width - 176f * scale, 20f * scale, width, 165f * scale)));
            row.add(new Image.Builder(getContext(), scope.id("divider"), dividerBitmap,
                    new RectF(220f * scale, height - Math.max(1f, scale), width, height))
                    .setScaleType(Image.ScaleType.FIT_XY));
        }
        @Override public void onBindItem(ComponentList.Item item, CallLog call, int position) {
            item.find("row_ripple", ChatRowRippleComponent.class).bind(
                    new RectF(0f, 0f, item.getScope().width(), item.getScope().height()),
                    () -> clickListener.onCallClick(call), null);
            item.find("avatar", Image.class).setBitmap(cachedAvatar(call.getContactName()));
            item.find("name", Text.class).setText(call.getContactName());
            String duration = call.getDuration() == null ? "" : call.getDuration().trim();
            String details = call.getCalledTime() == null ? "" : call.getCalledTime().trim();
            if (!duration.isEmpty()) details += (details.isEmpty() ? "" : " · ") + duration;
            item.find("details", Text.class).setText(details);
            item.find("type", Image.class)
                    .setBitmap(callIcon(call));
            float scale = figmaConfig.getScale(getWidth());
            item.find("call_ripple", ChatRowRippleComponent.class).bind(
                    new RectF(item.getScope().width() - 176f * scale, 20f * scale,
                            item.getScope().width(), 165f * scale),
                    () -> callStartListener.onCallStart(call, call.isVideoCall()), null);
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
        int size = Math.max(1, Math.round(px(132f)));
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

    private Bitmap cachedAvatar(String value) {
        String key = value == null ? "" : value.trim().toLowerCase(Locale.US);
        Bitmap cached = avatarCache.get(key);
        if (cached != null && !cached.isRecycled()) return cached;
        Bitmap created = avatar(value);
        avatarCache.put(key, created);
        return created;
    }

    private Bitmap callIcon(CallLog call) {
        int direction = call.getIconDirection();
        if (call.isVideoCall()) {
            return direction == CallLog.ICON_MISSED ? videoMissedBitmap
                    : direction == CallLog.ICON_OUTGOING
                    ? videoOutgoingBitmap : videoIncomingBitmap;
        }
        return direction == CallLog.ICON_MISSED ? phoneMissedBitmap
                : direction == CallLog.ICON_OUTGOING
                ? phoneOutgoingBitmap : phoneIncomingBitmap;
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

    private Bitmap drawableBitmap(int resource) {
        Drawable drawable = ContextCompat.getDrawable(getContext(), resource);
        if (drawable == null) return colorBitmap(Color.TRANSPARENT);
        int width = Math.max(1, drawable.getIntrinsicWidth());
        int height = Math.max(1, drawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        return bitmap;
    }

    private static void recycle(Bitmap... bitmaps) {
        for (Bitmap bitmap : bitmaps) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    public interface OnCallClickListener { void onCallClick(CallLog callLog); }
    public interface OnCallStartListener {
        void onCallStart(CallLog callLog, boolean video);
    }
}
