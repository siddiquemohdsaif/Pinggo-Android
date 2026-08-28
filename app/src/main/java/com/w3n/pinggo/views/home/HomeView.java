package com.w3n.pinggo.views.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.component.FigmaConfig;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.textfield.TextField;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.R;
import com.w3n.pinggo.views.common.NativeTextFieldImeController;
import com.w3n.pinggo.modals.CallLog;
import com.w3n.pinggo.modals.Chat;

import java.util.ArrayList;
import java.util.List;

/** Home screen rendered entirely with components from native-views-release.aar. */
public final class HomeView extends ViewGroup {
    private static final float FIGMA_WIDTH = 1080f;
    private static final int BACKGROUND = 0xFFF7F9FB;
    private static final int PRIMARY = 0xFF000E1A;
    private static final int SECONDARY = 0xFF687382;
    private static final int ACCENT = 0xFF019CC4;

    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final FigmaConfig figmaConfig = new FigmaConfig(FIGMA_WIDTH);
    private final ZLayer backgroundLayer = layers.addLayer("background");
    private final ZLayer contentLayer = layers.addLayer("content");
    private final ZLayer navigationLayer = layers.addLayer("navigation");
    private final Listener listener;
    private final Bitmap backgroundBitmap = colorBitmap(BACKGROUND);
    private final Bitmap accentBitmap = colorBitmap(ACCENT);
    private final Bitmap transparentBitmap = colorBitmap(Color.TRANSPARENT);
    private final Bitmap logoBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pinggo_logo);
    private final Bitmap searchBackgroundBitmap = BitmapFactory.decodeResource(
            getResources(), R.drawable.home_search_background);
    private final Bitmap overflowDotsBitmap = BitmapFactory.decodeResource(
            getResources(), R.drawable.home_overflow_dots);
    private final Bitmap selectionBackgroundBitmap = BitmapFactory.decodeResource(
            getResources(), R.drawable.chat_selection_background);
    private final Bitmap selectionGroupBitmap = BitmapFactory.decodeResource(
            getResources(), R.drawable.chat_selection_group);
    private final Bitmap selectionPinBitmap = BitmapFactory.decodeResource(
            getResources(), R.drawable.chat_selection_pin);
    private final Bitmap selectionMuteBitmap = BitmapFactory.decodeResource(
            getResources(), R.drawable.chat_selection_mute);
    private final Bitmap selectionDeleteBitmap = BitmapFactory.decodeResource(
            getResources(), R.drawable.chat_selection_delete);
    private final Bitmap selectionBackBitmap = drawableBitmap(R.drawable.conversation_back);

    private final ChatsView chatsView;
    private final CallsView callsView;
    private final MeetsView meetsView;
    private final BottomNavigationView bottomNavigationView;
    private Text title;
    private Image logo;
    private Image searchBackground;
    private TextField search;
    private Button overflow;
    private Image overflowDots;
    private Button action;
    private int topInset;
    private int bottomInset;
    private boolean showingChats = true;
    private boolean showingMeet;
    private List<Chat> selectedChats = new ArrayList<>();

    public HomeView(@NonNull Context context, @NonNull Listener listener) {
        super(context);
        this.listener = listener;
        setWillNotDraw(false);
        setBackgroundColor(BACKGROUND);
        setClickable(true);
        setFocusableInTouchMode(true);
        chatsView = new ChatsView(context, listener::onOpenChat);
        chatsView.setOnSelectionChangedListener(chats -> {
            selectedChats = chats == null ? new ArrayList<>() : new ArrayList<>(chats);
            if (getWidth() > 0) buildScreen();
            invalidate();
        });
        chatsView.setOnNewChatListener(listener::onNewChat);
        chatsView.setOnNewGroupListener(listener::onNewGroup);
        callsView = new CallsView(context, listener::onOpenCall);
        meetsView = new MeetsView(context);
        bottomNavigationView = new BottomNavigationView(context, new BottomNavigationView.Listener() {
            @Override public void onChatsSelected() { showChats(); }
            @Override public void onCallsSelected() { clearChatSelection(); showCalls(); }
            @Override public void onMeetSelected() { clearChatSelection(); showMeet(); }
        });
        addView(chatsView);
        addView(callsView);
        addView(meetsView);
        addView(bottomNavigationView);
    }

    public void setInsets(int top, int bottom) {
        top = Math.max(0, top);
        bottom = Math.max(0, bottom);
        if (topInset == top && bottomInset == bottom) return;
        topInset = top;
        bottomInset = bottom;
        if (getWidth() > 0) buildScreen();
    }

    public void showChatLoading() {
        chatsView.showLoading();
    }

    public void submitChats(List<Chat> chats) {
        chatsView.submit(chats);
        int unreadChats = 0;
        if (chats != null) {
            for (Chat chat : chats) {
                if (chat != null && chat.getUnreadCount() > 0) unreadChats++;
            }
        }
        bottomNavigationView.setTotalUnread(unreadChats);
    }

    public void submitCalls(List<CallLog> calls) {
        callsView.submitCalls(calls);
    }
    public void setChatTyping(String chatId, boolean typing) {
        chatsView.setTyping(chatId, typing);
    }
    public void setTotalUnread(int totalUnread) {
        bottomNavigationView.setTotalUnread(totalUnread);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
        int navHeight = bottomNavigationView.contentHeightForWidth(getMeasuredWidth()) + bottomInset;
        int chatsTop = chatsContentTop(getMeasuredWidth());
        int secondaryTop = Math.round(topInset + dp(68));
        int exactWidth = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
        chatsView.measure(exactWidth, MeasureSpec.makeMeasureSpec(
                Math.max(0, getMeasuredHeight() - navHeight - chatsTop), MeasureSpec.EXACTLY));
        int secondaryHeight = Math.max(0, getMeasuredHeight() - navHeight - secondaryTop);
        int exactSecondaryHeight = MeasureSpec.makeMeasureSpec(
                secondaryHeight, MeasureSpec.EXACTLY);
        callsView.measure(exactWidth, exactSecondaryHeight);
        meetsView.measure(exactWidth, exactSecondaryHeight);
        bottomNavigationView.measure(exactWidth, MeasureSpec.makeMeasureSpec(navHeight, MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        int navHeight = bottomNavigationView.contentHeightForWidth(width) + bottomInset;
        int chatsTop = chatsContentTop(width);
        int secondaryTop = Math.round(topInset + dp(68));
        int navTop = height - navHeight;
        chatsView.layout(0, chatsTop, width, navTop);
        callsView.layout(0, secondaryTop, width, navTop);
        meetsView.layout(0, secondaryTop, width, navTop);
        bottomNavigationView.layout(0, navTop, width, height);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && height > 0) buildScreen();
    }

    private void buildScreen() {
        // Keep the layers registered with ZLayerGroup. Clearing the group removes
        // the layers themselves, so adding components to these retained fields
        // would build an invisible tree that ZLayerGroup.draw() cannot reach.
        backgroundLayer.clear();
        contentLayer.clear();
        navigationLayer.clear();
        // Clearing a ZLayer releases its native components. Conditional header
        // components must not retain Java references to those released objects.
        title = null;
        logo = null;
        overflow = null;
        overflowDots = null;
        searchBackground = null;
        search = null;
        action = null;
        float width = getWidth();
        float height = getHeight();
        float scale = figmaConfig.getScale(Math.round(width));
        float figmaTop = topInset;
        float navTop = height - bottomNavigationView.contentHeightForWidth(Math.round(width))
                - bottomInset;
        float searchTop = figmaTop + 183f * scale;

        backgroundLayer.add(image("background", backgroundBitmap, new RectF(0, 0, width, height)));
        if (selectedChats.isEmpty()) {
            logo = contentLayer.add(image("logo", logoBitmap,
                    new RectF(52f * scale, figmaTop + 28f * scale,
                            179f * scale, figmaTop + 155f * scale))
                    .setScaleType(Image.ScaleType.FIT_CENTER));
            title = contentLayer.add(text("title", getString(R.string.app_name),
                    new RectF(199f * scale, figmaTop + 34f * scale,
                            600f * scale, figmaTop + 143f * scale), 54f * scale, 0xFF009FC8,
                    FontVariation.BOLD));
            overflowDots = contentLayer.add(image("overflow_dots", overflowDotsBitmap,
                    new RectF(996f * scale, figmaTop + 56f * scale,
                            1028f * scale, figmaTop + 113f * scale))
                    .setScaleType(Image.ScaleType.FIT_XY));
            overflow = contentLayer.add(new Button.Builder(getContext(), "overflow_touch",
                    transparentBitmap, "",
                    new RectF(940f * scale, figmaTop + 25f * scale,
                            1068f * scale, figmaTop + 145f * scale))
                    .setImageScaleType(Image.ScaleType.FIT_XY)
                    .setRippleEnabled(false)
                    .setOnClickListener(id -> listener.onOpenMenuDialog()));
        } else {
            buildSelectionHeader(width, figmaTop, scale);
        }

        searchBackground = contentLayer.add(image("search_background", searchBackgroundBitmap,
                new RectF(42f * scale, searchTop, 1039f * scale,
                        searchTop + 123f * scale)).setScaleType(Image.ScaleType.FIT_XY));
        search = contentLayer.add(new TextField.Builder(getContext(), "search",
                new RectF(42f * scale, searchTop, 1039f * scale, searchTop + 123f * scale))
                .setHint(getString(R.string.search_chats)).setInputType(InputType.TYPE_CLASS_TEXT)
                .setImeOptions(EditorInfo.IME_ACTION_DONE).setMaxLength(80)
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
                .setTextSizePx(38f * scale).setTextColor(PRIMARY).setHintColor(0xFF60708D)
                .setCursorColor(ACCENT).setCursorWidthPx(4f * scale)
                .setBackgroundColor(Color.TRANSPARENT, Color.TRANSPARENT)
                .setStrokeColor(Color.TRANSPARENT, Color.TRANSPARENT)
                .setStrokeWidthPx(0).setCornerRadiusPx(0)
                .setPaddingPx(140f * scale, 20f * scale)
                .setOnTextChangedListener((id, value) -> {
                    chatsView.filter(value);
                }).setOnEditorActionListener((id, actionId) -> {
                    if (actionId == EditorInfo.IME_ACTION_DONE) hideKeyboard();
                    return actionId == EditorInfo.IME_ACTION_DONE;
                }));

        action = addButton(contentLayer, "action", accentBitmap, getString(R.string.new_chat),
                new RectF(width - dp(156), navTop - dp(72), width - dp(20), navTop - dp(16)),
                Color.WHITE, dp(18), id -> {
                    if (showingChats) listener.onNewChat(); else listener.onMakeCall();
                });

        if (showingMeet) showMeet();
        else if (showingChats) showChats();
        else showCalls();
    }

    private void showChats() {
        showingChats = true;
        showingMeet = false;
        if (title != null) title.setText(getString(R.string.app_name));
        if (overflow != null) overflow.setVisible(selectedChats.isEmpty());
        if (overflowDots != null) overflowDots.setVisible(selectedChats.isEmpty());
        if (logo != null) logo.setVisible(selectedChats.isEmpty());
        searchBackground.setVisible(true);
        search.setVisible(true);
        chatsView.setVisibility(VISIBLE);
        callsView.setVisibility(GONE);
        meetsView.setVisibility(GONE);
        bottomNavigationView.setSelectedTab(BottomNavigationView.Tab.CHATS);
        action.setLabel(getString(R.string.new_chat));
        action.setVisible(false);
        updateChatState();
        invalidate();
    }

    private void buildSelectionHeader(float width, float top, float scale) {
        contentLayer.add(image("selection_header", selectionBackgroundBitmap,
                new RectF(0, top, width, top + 170f * scale)));
        contentLayer.add(new Image.Builder(getContext(), "selection_back",
                selectionBackBitmap,
                new RectF(50f * scale, top + 60f * scale,
                        101f * scale, top + 111f * scale))
                .setScaleType(Image.ScaleType.FIT_CENTER));
        contentLayer.add(new Button.Builder(getContext(), "selection_back_touch",
                transparentBitmap, "", new RectF(30f * scale, top + 40f * scale,
                        121f * scale, top + 131f * scale))
                .setImageScaleType(Image.ScaleType.FIT_XY).setRippleEnabled(true)
                .setOnClickListener(id -> clearChatSelection()));
        contentLayer.add(text("selection_count", String.valueOf(selectedChats.size()),
                new RectF(165f * scale, top + 57f * scale,
                        300f * scale, top + 117f * scale), 50f * scale, SECONDARY,
                FontVariation.MEDIUM));
        addSelectionButton("selection_group", selectionGroupBitmap,
                612f, 51f, top, scale,
                () -> listener.onBulkGroup(new ArrayList<>(selectedChats)));
        addSelectionButton("selection_pin", selectionPinBitmap,
                728f, 51f, top, scale,
                () -> listener.onBulkPin(new ArrayList<>(selectedChats)));
        addSelectionButton("selection_mute", selectionMuteBitmap,
                844f, 51f, top, scale,
                () -> listener.onBulkMute(new ArrayList<>(selectedChats)));
        addSelectionButton("selection_delete", selectionDeleteBitmap,
                960f, 40f, top, scale,
                () -> listener.onBulkDelete(new ArrayList<>(selectedChats)));
    }

    private void addSelectionButton(String id, Bitmap bitmap, float left, float iconWidth, float top,
                                    float scale, Runnable action) {
        contentLayer.add(new Button.Builder(getContext(), id, bitmap, "",
                new RectF(left * scale, top + 60f * scale,
                        (left + iconWidth) * scale, top + 111f * scale))
                .setImageScaleType(Image.ScaleType.FIT_CENTER).setRippleEnabled(true)
                .setRippleColor(0x18019CC4).setOnClickListener(value -> action.run()));
    }

    public boolean clearChatSelection() {
        return chatsView.clearSelection();
    }

    private void showCalls() {
        showingChats = false;
        showingMeet = false;
        if (title == null) return;
        search.clearFocus();
        hideKeyboard();
        title.setText(getString(R.string.call));
        overflow.setVisible(false);
        if (overflowDots != null) overflowDots.setVisible(false);
        logo.setVisible(false);
        searchBackground.setVisible(false);
        search.setVisible(false);
        chatsView.setVisibility(GONE);
        callsView.setVisibility(VISIBLE);
        meetsView.setVisibility(GONE);
        bottomNavigationView.setSelectedTab(BottomNavigationView.Tab.CALLS);
        action.setLabel(getString(R.string.make_call));
        action.setVisible(true);
        updateCallState();
        invalidate();
    }

    private void showMeet() {
        showingChats = false;
        showingMeet = true;
        if (title == null) return;
        search.clearFocus();
        hideKeyboard();
        title.setText(getString(R.string.meet));
        overflow.setVisible(false);
        if (overflowDots != null) overflowDots.setVisible(false);
        logo.setVisible(false);
        searchBackground.setVisible(false);
        search.setVisible(false);
        action.setVisible(false);
        chatsView.setVisibility(GONE);
        callsView.setVisibility(GONE);
        meetsView.setVisibility(VISIBLE);
        bottomNavigationView.setSelectedTab(BottomNavigationView.Tab.MEET);
        invalidate();
    }

    private void updateChatState() {
        invalidate();
    }

    private void updateCallState() {
        invalidate();
    }

    private Button addButton(ZLayer layer, String id, Bitmap bitmap, String label, RectF bounds,
                             int color, float radius, Button.OnClickListener click) {
        return layer.add(new Button.Builder(getContext(), id, bitmap, label, bounds)
                .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(radius)
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.SEMI_BOLD)
                .setTextSizePx(sp(15)).setTextColor(color).setRippleEnabled(true)
                .setRippleColor(0x22019CC4).setRippleOrigin(Button.RippleOrigin.TOUCH)
                .setOnClickListener(click));
    }

    private Image.Builder image(String id, Bitmap bitmap, RectF bounds) {
        return new Image.Builder(getContext(), id, bitmap, bounds).setScaleType(Image.ScaleType.FIT_XY);
    }

    private Text.Builder text(String id, String value, RectF bounds, float size, int color,
                              FontVariation variation) {
        return new Text.Builder(getContext(), id, value, bounds).setFont(NativeFonts.INTER)
                .setFontVariations(variation).setTextSizePx(size).setTextColor(color)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER).setWrapEnabled(false);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        backgroundLayer.draw(canvas);
    }
    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        contentLayer.draw(canvas);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        NativeTextFieldImeController.dismissOnOutsideDown(
                this, layers, contentLayer, event);
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        return layers.onTouchEvent(event) || super.onTouchEvent(event);
    }
    @Override public boolean onCheckIsTextEditor() { return layers.onCheckIsTextEditor(); }
    @Override public InputConnection onCreateInputConnection(EditorInfo attrs) {
        InputConnection result = layers.onCreateInputConnection(attrs);
        return result != null ? result : super.onCreateInputConnection(attrs);
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        return layers.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    public void release() {
        chatsView.release();
        callsView.release();
        bottomNavigationView.release();
        layers.release();
        for (Bitmap bitmap : new Bitmap[]{backgroundBitmap, accentBitmap,
                transparentBitmap, logoBitmap,
                searchBackgroundBitmap, overflowDotsBitmap, selectionBackgroundBitmap,
                selectionGroupBitmap, selectionPinBitmap, selectionMuteBitmap,
                selectionDeleteBitmap, selectionBackBitmap}) {
            if (!bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private void hideKeyboard() {
        InputMethodManager input = (InputMethodManager) getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (input != null) input.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    private String getString(int id) { return getResources().getString(id); }
    private int chatsContentTop(int width) {
        return Math.round(topInset + 331f * figmaConfig.getScale(width));
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
    private static Bitmap colorBitmap(int color) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }

    private Bitmap drawableBitmap(int resource) {
        android.graphics.drawable.Drawable drawable =
                ContextCompat.getDrawable(getContext(), resource);
        if (drawable == null) return transparentBitmap;
        int width = Math.max(1, drawable.getIntrinsicWidth());
        int height = Math.max(1, drawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        return bitmap;
    }

    public interface Listener {
        void onOpenChat(Chat chat);
        void onOpenCall(CallLog callLog);
        void onNewChat();
        void onNewGroup();
        void onMakeCall();
        void onOpenMenuDialog();
        void onBulkGroup(List<Chat> chats);
        void onBulkPin(List<Chat> chats);
        void onBulkMute(List<Chat> chats);
        void onBulkDelete(List<Chat> chats);
    }

}
