package com.w3n.pinggo.views.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.component.FigmaConfig;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.R;

import java.util.EnumMap;
import java.util.Map;

/** Three-tab bottom navigation matching the final Frame 4250 design. */
public final class BottomNavigationView extends View {
    private static final float FIGMA_WIDTH = 1080f;
    private static final float FIGMA_HEIGHT = 205f;
    private static final int SURFACE = 0xFFFFFFFF;
    private static final int ACCENT = 0xFF019CC4;
    private static final int SECONDARY = 0xFF687382;
    private static final int PILL = 0xFFE8F6FA;
    public enum Tab { CHATS, CALLS, MEET }

    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final FigmaConfig figmaConfig = new FigmaConfig(FIGMA_WIDTH);
    private final ZLayer layer = layers.addLayer("bottom_navigation");
    private final Listener listener;
    private final Bitmap background = resourceBitmap(R.drawable.bottom_nav_background);
    private final Bitmap selected = resourceBitmap(R.drawable.bottom_nav_selected_pill);
    private final Bitmap transparent = bitmap(Color.TRANSPARENT);
    private final Bitmap badge = resourceBitmap(R.drawable.bottom_nav_badge);
    private final Bitmap chatActive = resourceBitmap(R.drawable.bottom_nav_chat_active);
    private final Bitmap chatInactive = resourceBitmap(R.drawable.bottom_nav_chat_inactive);
    private final Bitmap callActive = resourceBitmap(R.drawable.bottom_nav_calls_active);
    private final Bitmap callInactive = resourceBitmap(R.drawable.bottom_nav_calls_inactive);
    private final Bitmap meetActive = resourceBitmap(R.drawable.bottom_nav_meet_active);
    private final Bitmap meetInactive = resourceBitmap(R.drawable.bottom_nav_meet_inactive);
    private final Map<Tab, Image> pills = new EnumMap<>(Tab.class);
    private final Map<Tab, Image> icons = new EnumMap<>(Tab.class);
    private final Map<Tab, Text> labels = new EnumMap<>(Tab.class);
    private Image chatBadge;
    private Text chatBadgeText;
    private Tab selectedTab = Tab.CHATS;
    private int totalUnread;

    public BottomNavigationView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setClickable(true);
    }

    public void setSelectedTab(Tab tab) {
        if (tab == null || selectedTab == tab) return;
        selectedTab = tab;
        updateSelection();
    }

    public void setTotalUnread(int value) {
        value = Math.max(0, value);
        if (totalUnread == value) return;
        totalUnread = value;
        updateSelection();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        layer.clear();
        pills.clear();
        icons.clear();
        labels.clear();
        chatBadge = null;
        chatBadgeText = null;
        float scale = figmaConfig.getScale(width);
        float barHeight = Math.min(FIGMA_HEIGHT * scale, height);
        layer.add(new Image.Builder(getContext(), "background", background,
                new RectF(0, 0, width, barHeight)).setScaleType(Image.ScaleType.FIT_XY));
        float tabWidth = width / 3f;
        addTab(Tab.CHATS, "chats", 0, tabWidth, 23f, chatActive, chatInactive, R.string.chats,
                selectedTab == Tab.CHATS, id -> listener.onChatsSelected());
        addTab(Tab.CALLS, "calls", tabWidth, tabWidth * 2, 0f, callActive, callInactive, R.string.calls,
                selectedTab == Tab.CALLS, id -> listener.onCallsSelected());
        addTab(Tab.MEET, "meet", tabWidth * 2, width, -21f, meetActive, meetInactive, R.string.meet,
                selectedTab == Tab.MEET, id -> listener.onMeetSelected());
        updateSelection();
    }

    private void addTab(Tab tab, String id, float left, float right, float horizontalOffset,
                        Bitmap activeIcon,
                        Bitmap inactiveIcon, int labelResource, boolean active,
                        Button.OnClickListener click) {
        float scale = figmaConfig.getScale(getWidth());
        float offset = horizontalOffset * scale;
        float positionedLeft = left + offset;
        float positionedRight = right + offset;
        float center = (positionedLeft + positionedRight) / 2f;
        Image pill = layer.add(new Image.Builder(getContext(), id + "_pill", selected,
                new RectF(center - 76f * scale, 32f * scale,
                        center + 76f * scale, 113f * scale))
                .setScaleType(Image.ScaleType.FIT_XY).setVisible(active));
        pills.put(tab, pill);
        Bitmap icon = active ? activeIcon : inactiveIcon;
        icons.put(tab, layer.add(new Image.Builder(getContext(), id + "_icon", icon,
                new RectF(center - 32f * scale, 40f * scale,
                        center + 32f * scale, 104f * scale))
                .setScaleType(Image.ScaleType.FIT_CENTER)));
        labels.put(tab, layer.add(new Text.Builder(getContext(), id + "_label",
                getResources().getString(labelResource),
                new RectF(positionedLeft, 121f * scale, positionedRight, 185f * scale))
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.MEDIUM).setTextSizePx(34f * scale)
                .setTextColor(active ? ACCENT : SECONDARY).setAlignment(Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)));
        if (tab == Tab.CHATS) addChatBadge(center);
        // Keep the invisible interaction region within its original screen third.
        // Native Button converts these bounds to Position margins and rejects a
        // negative left margin (the Chats visual offset is -23 Figma pixels).
        layer.add(new Button.Builder(getContext(), id + "_touch", transparent, "",
                new RectF(left, 0, right,
                        Math.min(FIGMA_HEIGHT * scale, getHeight())))
                .setImageScaleType(Image.ScaleType.FIT_XY)
//                .setRippleEnabled(true)
//                .setRippleColor(0x16019CC4)
                .setOnClickListener(click));
    }

    private void addChatBadge(float center) {
        float scale = figmaConfig.getScale(getWidth());
        chatBadge = layer.add(new Image.Builder(getContext(), "chat_badge", badge,
                new RectF(center + 28f * scale, 23f * scale,
                        center + 82f * scale, 61f * scale))
                .setScaleType(Image.ScaleType.FIT_XY).setVisible(totalUnread > 0));
        String label = totalUnread > 99 ? "99+" : String.valueOf(totalUnread);
        chatBadgeText = layer.add(new Text.Builder(getContext(), "chat_badge_text", label,
                new RectF(center + 28f * scale, 23f * scale,
                        center + 82f * scale, 61f * scale))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
                .setTextSizePx(26f * scale).setTextColor(Color.WHITE).setAlignment(Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER));
        chatBadgeText.setVisible(totalUnread > 0);
    }

    private void updateSelection() {
        for (Tab tab : Tab.values()) {
            boolean active = tab == selectedTab;
            Image pill = pills.get(tab);
            Image icon = icons.get(tab);
            Text label = labels.get(tab);
            if (pill != null) pill.setVisible(active);
            if (icon != null) icon.setBitmap(iconFor(tab, active));
            if (label != null) label.setTextColor(active ? ACCENT : SECONDARY);
        }
        if (chatBadge != null) chatBadge.setVisible(totalUnread > 0);
        if (chatBadgeText != null) {
            chatBadgeText.setText(totalUnread > 99 ? "99+" : String.valueOf(totalUnread))
                    .setVisible(totalUnread > 0);
        }
        invalidate();
    }

    private Bitmap iconFor(Tab tab, boolean active) {
        switch (tab) {
            case CALLS: return active ? callActive : callInactive;
            case MEET: return active ? meetActive : meetInactive;
            case CHATS:
            default: return active ? chatActive : chatInactive;
        }
    }

    @Override protected void onDraw(Canvas canvas) { layers.draw(canvas); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        return layers.onTouchEvent(event) || super.onTouchEvent(event);
    }
    public void release() {
        layers.release();
        for (Bitmap value : new Bitmap[]{background, selected, transparent, badge,
                chatActive, chatInactive, callActive, callInactive, meetActive, meetInactive}) {
            if (!value.isRecycled()) value.recycle();
        }
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
    public int contentHeightForWidth(int width) {
        return Math.max(1, Math.round(FIGMA_HEIGHT * figmaConfig.getScale(width)));
    }
    private static Bitmap bitmap(int color) {
        Bitmap value = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        value.eraseColor(color);
        return value;
    }
    private Bitmap resourceBitmap(int resource) {
        return BitmapFactory.decodeResource(getResources(), resource);
    }
    public interface Listener {
        void onChatsSelected();
        void onCallsSelected();
        void onMeetSelected();
    }
}
