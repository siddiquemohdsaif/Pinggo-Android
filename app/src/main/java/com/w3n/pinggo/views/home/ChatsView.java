package com.w3n.pinggo.views.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.BitmapShader;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;
import android.util.LruCache;
import android.view.Choreographer;

import androidx.lifecycle.Observer;
import androidx.core.content.ContextCompat;

import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.component.FigmaConfig;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.progress.Progress;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.R;
import com.w3n.pinggo.activity.NewChatActivity;
import com.w3n.pinggo.data.local.ChatEntity;
import com.w3n.pinggo.data.repository.ChatListState;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.modals.Chat;

import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Scrollable chat list implemented with native-views-release.aar components. */
public final class ChatsView extends View {
    private static final String PERF_TAG = "ChatsViewPerf";
    private static final float FIGMA_WIDTH = 1080f;
    private static final int PRIMARY = 0xFF000E1A;
    private static final int SECONDARY = 0xFF687382;
    private static final int UNREAD_PREVIEW = 0xFF855C5C;
    private static final int ACCENT = 0xFF019CC4;

    private final ZLayerGroup layers = new ZLayerGroup(this);
    private final FigmaConfig figmaConfig = new FigmaConfig(FIGMA_WIDTH);
    private final ZLayer listLayer = layers.addLayer("chat_list");
    private final ZLayer stateLayer = layers.addLayer("chat_state");
    private final ChatAdapter adapter = new ChatAdapter();
    private final ChatRepository repository;
    private final OnChatClickListener clickListener;
    private OnSelectionChangedListener selectionChangedListener;
    private final Set<String> selectedChatIds = new LinkedHashSet<>();
    private Runnable newChatListener;
    private Runnable newGroupListener;
    private final Bitmap dividerBitmap = colorBitmap(0xFFE5EAF0);
    private final Bitmap actionBitmap = colorBitmap(ACCENT);
    private final Bitmap whiteBitmap = colorBitmap(Color.WHITE);
    private final Bitmap emptyTransparentBitmap = colorBitmap(Color.TRANSPARENT);
    private final Bitmap floatingActionBitmap = BitmapFactory.decodeResource(
            getResources(), R.drawable.chat_floating_action);
    private final Bitmap emptyIllustrationBitmap = BitmapFactory.decodeResource(
            getResources(), R.drawable.chats_empty_illustration);
    private final Bitmap emptyStartChatIconBitmap = resourceBitmap(
            R.drawable.chats_empty_start_icon);
    private final Bitmap unreadBadgeBitmap = resourceBitmap(R.drawable.chat_unread_badge);
    private final Bitmap selectionBackgroundBitmap = resourceBitmap(
            R.drawable.chat_selection_background);
    private final Bitmap selectionCheckBitmap = resourceBitmap(R.drawable.chat_selection_check);
    private final Bitmap pinnedBitmap = resourceBitmap(R.drawable.chat_pinned);
    private final Bitmap mutedBitmap = resourceBitmap(R.drawable.chat_muted);
    private final Bitmap sendingBitmap = drawableBitmap(R.drawable.chat_status_sending_image);
    private final Bitmap deliveredBitmap = resourceBitmap(R.drawable.chat_status_delivered);
    private final Bitmap sentBitmap = resourceBitmap(R.drawable.chat_status_sent);
    private final Bitmap readBitmap = resourceBitmap(R.drawable.chat_status_read);
    private final Bitmap pictureBitmap = resourceBitmap(R.drawable.chat_picture);
    private final Bitmap videoBitmap = resourceBitmap(R.drawable.chat_video_neutral);
    private final Bitmap documentBitmap = resourceBitmap(R.drawable.chat_document);
    private final Bitmap locationBitmap = resourceBitmap(R.drawable.chat_location);
    private final Bitmap phoneIncomingBitmap = resourceBitmap(R.drawable.chat_phone_incoming);
    private final Bitmap phoneOutgoingBitmap = resourceBitmap(R.drawable.chat_phone_outgoing);
    private final Bitmap phoneMissedBitmap = resourceBitmap(R.drawable.chat_phone_missed);
    private final Bitmap videoIncomingBitmap = resourceBitmap(R.drawable.chat_video_incoming);
    private final Bitmap videoOutgoingBitmap = resourceBitmap(R.drawable.chat_video_outgoing);
    private final Bitmap videoMissedBitmap = resourceBitmap(R.drawable.chat_video_missed);
    private final Observer<List<ChatEntity>> observer = entities -> post(() -> submit(toChats(entities)));
    private final Observer<ChatListState> listStateObserver = state -> post(() -> {
        chatListState = state == null ? ChatListState.initial() : state;
        updateVisibility();
    });
    private ComponentList<Chat> list;
    private Text status;
    private Image emptyIllustration;
    private Text emptyTitle;
    private Text emptyDescription;
    private Button emptyStartChat;
    private Image emptyStartChatIcon;
    private Text emptyStartChatLabel;
    private Button emptyCreateGroup;
    private Button floatingAction;
    private Text refreshErrorText;
    private Image paginationBackground;
    private Progress initialProgress;
    private Text initialLoadingText;
    private Progress paginationProgress;
    private Text paginationLoadingText;
    private ChatListState chatListState = ChatListState.initial();
    private boolean loaded;
    private boolean observing;
    private String statusMessage = "Loading chats...";
    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Long> typingBaselines = new HashMap<>();
    private final Map<String, Runnable> typingTimeouts = new HashMap<>();
    private final ExecutorService avatarExecutor = Executors.newFixedThreadPool(2);
    private final Set<String> avatarLoads = Collections.synchronizedSet(new LinkedHashSet<>());
    // Do not recycle on eviction: a visible native Image may still hold the bitmap.
    private final LruCache<String, Bitmap> avatarCache = new LruCache<>(48);
    private final Paint ellipsizePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SimpleDateFormat timeFormatter =
            new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private final SimpleDateFormat dateFormatter =
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private final Calendar messageCalendar = Calendar.getInstance();
    private final Calendar todayCalendar = Calendar.getInstance();
    private final Date reusableDate = new Date();
    private boolean frameProfilerRunning;
    private float loadingGestureStartY;
    private boolean loadingGestureBlocked;
    private long previousFrameNanos;
    private final Choreographer.FrameCallback frameProfiler =
            new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            if (!frameProfilerRunning) return;
            if (previousFrameNanos != 0L) {
                long frameMs = (frameTimeNanos - previousFrameNanos) / 1_000_000L;
                if (frameMs >= 24L) {
                    Log.w(PERF_TAG, "slowFrame=" + frameMs + "ms firstVisible="
                            + (list == null ? -1 : list.getFirstVisiblePosition())
                            + " lastVisible=" + (list == null ? -1 : list.getLastVisiblePosition())
                            + " items=" + adapter.getItemCount());
                }
            }
            previousFrameNanos = frameTimeNanos;
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public ChatsView(Context context, OnChatClickListener clickListener) {
        super(context);
        this.clickListener = clickListener;
        repository = ChatRepository.getInstance(context);
        setClickable(true);
    }

    public void loadChats() {
        if (loaded) return;
        loaded = true;
        showStatus("Loading chats...");
        String phone = currentPhoneNumber();
        if (phone.isEmpty()) {
            showStatus("Login data missing.");
            return;
        }
        startObserving();
        repository.ensureChatListLoaded(phone);
    }

    public void submit(List<Chat> chats) {
        adapter.submit(chats, adapter.query);
        showStatus(adapter.getItemCount() == 0 ? getResources().getString(
                R.string.start_new_conversation) : "");
        post(this::loadNextPageIfNeeded);
    }

    public void showLoading() { showStatus("Loading chats..."); }
    public void filter(String query) {
        adapter.filter(query);
        showStatus(adapter.getItemCount() == 0
                ? (adapter.hasChats() ? "No matching conversations"
                : getResources().getString(R.string.start_new_conversation)) : "");
    }
    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        selectionChangedListener = listener;
    }
    public boolean isSelecting() { return !selectedChatIds.isEmpty(); }
    public boolean clearSelection() {
        if (selectedChatIds.isEmpty()) return false;
        List<String> previouslySelected = new ArrayList<>(selectedChatIds);
        selectedChatIds.clear();
        for (String chatId : previouslySelected) adapter.notifyChatChanged(chatId);
        updateVisibility();
        notifySelectionChanged();
        return true;
    }
    public void setOnNewChatListener(Runnable listener) { newChatListener = listener; }
    public void setOnNewGroupListener(Runnable listener) { newGroupListener = listener; }
    public void setTyping(String chatId, boolean typing) {
        if (chatId == null || chatId.trim().isEmpty()) return;
        Runnable previous = typingTimeouts.remove(chatId);
        if (previous != null) typingHandler.removeCallbacks(previous);
        if (!typing) {
            typingBaselines.remove(chatId);
            adapter.notifyChatChanged(chatId);
            return;
        }
        typingBaselines.put(chatId, adapter.findLastMessageTime(chatId));
        Runnable timeout = () -> {
            typingBaselines.remove(chatId);
            typingTimeouts.remove(chatId);
            adapter.notifyChatChanged(chatId);
        };
        typingTimeouts.put(chatId, timeout);
        typingHandler.postDelayed(timeout, 30_000L);
        adapter.notifyChatChanged(chatId);
    }

    private void startObserving() {
        if (observing) return;
        observing = true;
        repository.observeChats().observeForever(observer);
        repository.observeChatListState().observeForever(listStateObserver);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isDebugBuild() && !frameProfilerRunning) {
            frameProfilerRunning = true;
            previousFrameNanos = 0L;
            Choreographer.getInstance().postFrameCallback(frameProfiler);
        }
        if (loaded && !currentPhoneNumber().isEmpty()) startObserving();
    }

    @Override protected void onDetachedFromWindow() {
        frameProfilerRunning = false;
        Choreographer.getInstance().removeFrameCallback(frameProfiler);
        if (observing) {
            repository.observeChats().removeObserver(observer);
            repository.observeChatListState().removeObserver(listStateObserver);
            observing = false;
        }
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width <= 0 || height <= 0) return;
        listLayer.clear();
        stateLayer.clear();
        list = listLayer.add(new ComponentList.Builder<Chat>(getContext(), "chat_component_list",
                new RectF(0, 0, width, height)).setOrientation(ComponentList.Orientation.VERTICAL)
                .setItemSize(185f * figmaConfig.getScale(width))
                .setPaddingPx(0, 0, 0, 155f * figmaConfig.getScale(width))
                .setAdapter(adapter).setClipToBounds(true).setScrollEnabled(true)
                .setOverscrollEnabled(false)
                .setOnItemLongClickListener((componentList, chat, position) -> {
                    toggleSelection(chat);
                    return true;
                })
                .setOnItemClickListener((componentList, chat, position) -> {
                    if (isSelecting()) toggleSelection(chat);
                    else clickListener.onChatClick(chat);
                }));
        status = stateLayer.add(new Text.Builder(getContext(), "chat_status", statusMessage,
                new RectF(dp(20), dp(28), width - dp(20), dp(112)))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.REGULAR)
                .setTextSizePx(sp(16)).setTextColor(SECONDARY).setAlignment(Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(2));
        float emptyScale = figmaConfig.getScale(width);
        emptyIllustration = stateLayer.add(new Image.Builder(getContext(),
                "chats_empty_illustration", emptyIllustrationBitmap,
                new RectF(255f * emptyScale, 245f * emptyScale,
                        825f * emptyScale, 815f * emptyScale))
                .setScaleType(Image.ScaleType.FIT_CENTER));
        emptyTitle = stateLayer.add(new Text.Builder(getContext(), "empty_title",
                "Start your first conversation",
                new RectF(100f * emptyScale, 800f * emptyScale,
                        width - 100f * emptyScale, 875f * emptyScale))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.SEMI_BOLD)
                .setTextSizePx(46f * emptyScale).setTextColor(PRIMARY)
                .setAlignment(Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(1));
        emptyDescription = stateLayer.add(new Text.Builder(getContext(), "empty_description",
                "Chat with friends, family, or teammates\non PingGo.",
                new RectF(110f * emptyScale, 905f * emptyScale,
                        width - 110f * emptyScale, 1025f * emptyScale))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
                .setTextSizePx(38f * emptyScale).setTextColor(SECONDARY)
                .setAlignment(Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER).setMaxLines(2));
        emptyStartChat = stateLayer.add(new Button.Builder(getContext(), "empty_start_chat",
                actionBitmap, "",
                new RectF(210f * emptyScale, 1100f * emptyScale,
                        width - 210f * emptyScale, 1220f * emptyScale))
                .setImageScaleType(Image.ScaleType.FIT_XY).setCornerRadiusPx(24f * emptyScale)
                .setRippleEnabled(true).setRippleColor(0x33FFFFFF)
                .setOnClickListener(id -> openNewChat()));
        float startLabelWidth = 420f * emptyScale;
        // Native Text uses less visual width than its no-wrap bounds. This Figma
        // position centers the rendered icon + label, not the reserved text box.
        float startGroupLeft = 360f * emptyScale;
        emptyStartChatIcon = stateLayer.add(new Image.Builder(getContext(),
                "empty_start_chat_icon", emptyStartChatIconBitmap,
                new RectF(startGroupLeft, 1137f * emptyScale,
                        startGroupLeft + 46f * emptyScale, 1183f * emptyScale))
                .setScaleType(Image.ScaleType.FIT_CENTER));
        float startLabelLeft = startGroupLeft + 70f * emptyScale;
        emptyStartChatLabel = stateLayer.add(new Text.Builder(getContext(),
                "empty_start_chat_label", "Start a new chat",
                new RectF(startLabelLeft, 1125f * emptyScale,
                        startLabelLeft + startLabelWidth, 1195f * emptyScale))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.SEMI_BOLD)
                .setTextSizePx(37f * emptyScale).setTextColor(Color.WHITE)
                .setAlignment(Text.Alignment.START)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setWrapEnabled(false).setMaxLines(1));
        emptyCreateGroup = stateLayer.add(new Button.Builder(getContext(), "empty_create_group",
                emptyTransparentBitmap, "Create a group",
                new RectF(300f * emptyScale, 1275f * emptyScale,
                        width - 300f * emptyScale, 1365f * emptyScale))
                .setImageScaleType(Image.ScaleType.FIT_XY)
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.SEMI_BOLD)
                .setTextSizePx(37f * emptyScale).setTextColor(ACCENT)
                .setRippleEnabled(true).setRippleColor(0x18019CC4)
                .setOnClickListener(id -> {
                    if (newGroupListener != null) newGroupListener.run();
                }));
        float scale = figmaConfig.getScale(width);
        float actionSize = 155f * scale;
        float actionRightMargin = 40f * scale;
        float actionBottomMargin = 46f * scale;
        RectF actionBounds = new RectF(width - actionSize - actionRightMargin,
                height - actionSize - actionBottomMargin,
                width - actionRightMargin, height - actionBottomMargin);
        floatingAction = stateLayer.add(new Button.Builder(getContext(), "new_chat_floating",
                floatingActionBitmap, "",
                actionBounds)
                .setImageScaleType(Image.ScaleType.FIT_CENTER).setRippleEnabled(true)
                .setRippleColor(0x22FFFFFF).setOnClickListener(id -> openNewChat()));
        float initialProgressSize = 72f * scale;
        initialProgress = stateLayer.add(new Progress.Builder(getContext(),
                "chat_initial_progress",
                new RectF((width - initialProgressSize) / 2f,
                        (height - initialProgressSize) / 2f,
                        (width + initialProgressSize) / 2f,
                        (height + initialProgressSize) / 2f))
                .setStyle(Progress.Style.CIRCULAR)
                .setMode(Progress.Mode.INDETERMINATE)
                .setProgressColor(ACCENT)
                .setTrackColor(0x22019CC4)
                .setThickness(7f)
                .setIndeterminateDuration(850L)
                .setVisible(false));
        initialLoadingText = stateLayer.add(new Text.Builder(getContext(),
                "chat_initial_loading_text", "Loading chats...",
                new RectF(dp(20), (height + initialProgressSize) / 2f + dp(10),
                        width - dp(20), (height + initialProgressSize) / 2f + dp(54)))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
                .setTextSizePx(sp(14)).setTextColor(SECONDARY)
                .setAlignment(Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setMaxLines(1));
        initialLoadingText.setVisible(false);
        float footerHeight = paginationFooterHeight();
        float footerTop = height - footerHeight;
        paginationBackground = stateLayer.add(new Image.Builder(getContext(),
                "chat_page_background", whiteBitmap,
                new RectF(0, footerTop, width, height))
                .setScaleType(Image.ScaleType.FIT_XY));
        paginationBackground.setVisible(false);
        float progressSize = 44f * scale;
        float loadingGroupWidth = 330f * scale;
        float loadingGroupLeft = (width - loadingGroupWidth) / 2f;
        float progressTop = footerTop + (footerHeight - progressSize) / 2f;
        paginationProgress = stateLayer.add(new Progress.Builder(getContext(),
                "chat_page_progress",
                new RectF(loadingGroupLeft, progressTop,
                        loadingGroupLeft + progressSize, progressTop + progressSize))
                .setStyle(Progress.Style.CIRCULAR)
                .setMode(Progress.Mode.INDETERMINATE)
                .setProgressColor(ACCENT)
                .setTrackColor(0x22019CC4)
                .setThickness(6f)
                .setIndeterminateDuration(850L)
                .setVisible(false));
        paginationLoadingText = stateLayer.add(new Text.Builder(getContext(),
                "chat_page_loading_text", "Loading chats...",
                new RectF(loadingGroupLeft + progressSize + 24f * scale, footerTop,
                        loadingGroupLeft + loadingGroupWidth, height))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
                .setTextSizePx(sp(14)).setTextColor(SECONDARY)
                .setAlignment(Text.Alignment.START)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setMaxLines(1));
        paginationLoadingText.setVisible(false);
        refreshErrorText = stateLayer.add(new Text.Builder(getContext(),
                "chat_refresh_error", "Couldn't refresh chats. Showing saved chats.",
                new RectF(dp(20), height - dp(64), width - dp(20), height - dp(12)))
                .setFont(NativeFonts.INTER).setFontVariations(FontVariation.MEDIUM)
                .setTextSizePx(sp(13)).setTextColor(UNREAD_PREVIEW)
                .setAlignment(Text.Alignment.CENTER)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER)
                .setMaxLines(1));
        refreshErrorText.setVisible(false);
        updateVisibility();
    }

    private void showStatus(String message) {
        statusMessage = message == null ? "" : message;
        updateVisibility();
    }

    private void updateVisibility() {
        if (list == null || status == null) return;
        boolean hasChats = adapter.getItemCount() > 0;
        ChatListState.Status state = chatListState.getStatus();
        boolean awaitingCachedRows = !adapter.hasChats()
                && chatListState.getCachedChatCount() > 0;
        boolean initialLoading = !hasChats
                && (state == ChatListState.Status.INITIAL_CACHE_LOADING
                || awaitingCachedRows);
        boolean empty = !hasChats && !adapter.hasChats()
                && state == ChatListState.Status.EMPTY;
        boolean errorWithoutCache = !hasChats
                && state == ChatListState.Status.ERROR_WITHOUT_CACHE;
        boolean errorWithCache = adapter.hasChats()
                && state == ChatListState.Status.ERROR_WITH_CACHE;
        boolean showingProgress = state == ChatListState.Status.REFRESHING
                || state == ChatListState.Status.PAGINATING;
        float listBottom = getHeight() - (hasChats && showingProgress
                ? paginationFooterHeight() : 0f);
        if (hasChats && showingProgress) list.stopScroll();
        list.setRegion(new RectF(0, 0, getWidth(), Math.max(0, listBottom)));
        list.setVisible(hasChats).setEnabled(hasChats);
        status.setText(errorWithoutCache
                        ? "Couldn't refresh chats. Check your connection and try again."
                        : statusMessage)
                .setVisible(!hasChats && !empty && !initialLoading);
        emptyIllustration.setVisible(empty);
        emptyTitle.setVisible(empty);
        emptyDescription.setVisible(empty);
        emptyStartChat.setVisible(empty).setEnabled(empty);
        emptyStartChatIcon.setVisible(empty);
        emptyStartChatLabel.setVisible(empty);
        emptyCreateGroup.setVisible(empty).setEnabled(empty);
        floatingAction.setVisible(!isSelecting()).setEnabled(!isSelecting());
        if (initialProgress != null) {
            initialProgress.setVisible(initialLoading);
        }
        if (initialLoadingText != null) {
            initialLoadingText.setVisible(initialLoading);
        }
        if (paginationBackground != null) {
            paginationBackground.setVisible(hasChats && showingProgress);
        }
        if (paginationProgress != null) {
            paginationProgress.setVisible(hasChats && showingProgress);
        }
        if (paginationLoadingText != null) {
            paginationLoadingText.setVisible(hasChats && showingProgress);
        }
        if (refreshErrorText != null) {
            refreshErrorText.setVisible(errorWithCache);
        }
        invalidate();
    }

    private void toggleSelection(Chat chat) {
        if (chat == null || chat.getChatId() == null) return;
        if (!selectedChatIds.add(chat.getChatId())) selectedChatIds.remove(chat.getChatId());
        adapter.notifyChatChanged(chat.getChatId());
        updateVisibility();
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (selectionChangedListener == null) return;
        List<Chat> selected = new ArrayList<>();
        for (Chat chat : adapter.all) {
            if (selectedChatIds.contains(chat.getChatId())) selected.add(chat);
        }
        selectionChangedListener.onSelectionChanged(selected);
    }

    private void openNewChat() {
        if (newChatListener != null) newChatListener.run();
        else getContext().startActivity(new Intent(getContext(), NewChatActivity.class));
    }

    @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            loadingGestureStartY = event.getY();
            loadingGestureBlocked = false;
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                && !loadingGestureBlocked && isChatPageLoading()
                && event.getY() - loadingGestureStartY
                < -ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            layers.onTouchEvent(cancel);
            cancel.recycle();
            loadingGestureBlocked = true;
        }
        if (loadingGestureBlocked) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                loadingGestureBlocked = false;
            }
            return true;
        }
        boolean handled = layers.onTouchEvent(event);
        if (handled) post(this::loadNextPageIfNeeded);
        return handled || super.onTouchEvent(event);
    }

    private boolean isChatPageLoading() {
        ChatListState.Status state = chatListState.getStatus();
        return adapter.getItemCount() > 0
                && (state == ChatListState.Status.REFRESHING
                || state == ChatListState.Status.PAGINATING);
    }

    private float paginationFooterHeight() {
        return 112f * figmaConfig.getScale(Math.max(1, getWidth()));
    }

    private void loadNextPageIfNeeded() {
        if (list == null || adapter.getItemCount() == 0) return;
        if (list.getLastVisiblePosition() >= adapter.getItemCount() - 3) {
            if (isDebugBuild()) {
                Log.d(PERF_TAG, "paginationCheck lastVisible=" + list.getLastVisiblePosition()
                        + " items=" + adapter.getItemCount());
            }
            repository.loadNextChatListPage();
        }
    }

    public void release() {
        typingHandler.removeCallbacksAndMessages(null);
        typingBaselines.clear();
        typingTimeouts.clear();
        if (observing) {
            repository.observeChats().removeObserver(observer);
            repository.observeChatListState().removeObserver(listStateObserver);
        }
        observing = false;
        avatarExecutor.shutdownNow();
        avatarLoads.clear();
        avatarCache.evictAll();
        layers.release();
        recycle(dividerBitmap, actionBitmap, whiteBitmap, emptyTransparentBitmap,
                floatingActionBitmap,
                emptyIllustrationBitmap,
                emptyStartChatIconBitmap,
                unreadBadgeBitmap,
                selectionBackgroundBitmap, selectionCheckBitmap,
                pinnedBitmap, mutedBitmap, sendingBitmap, deliveredBitmap, sentBitmap, readBitmap);
        recycle(pictureBitmap, videoBitmap, documentBitmap, locationBitmap);
        recycle(phoneIncomingBitmap, phoneOutgoingBitmap, phoneMissedBitmap,
                videoIncomingBitmap, videoOutgoingBitmap, videoMissedBitmap);
    }

    private final class ChatAdapter extends ComponentList.Adapter<Chat> {
        private final List<Chat> all = new ArrayList<>();
        private final List<Chat> chats = new ArrayList<>();
        private String query = "";
        void submit(List<Chat> values, String currentQuery) {
            if (values != null) {
                for (Chat chat : values) {
                    Long baseline = typingBaselines.get(chat.getChatId());
                    if (baseline != null && chat.getLastMessageTime() > baseline) {
                        typingBaselines.remove(chat.getChatId());
                        Runnable timeout = typingTimeouts.remove(chat.getChatId());
                        if (timeout != null) typingHandler.removeCallbacks(timeout);
                    }
                }
            }
            all.clear();
            if (values != null) all.addAll(values);
            query = normalizeQuery(currentQuery);
            applyVisibleDiff(filteredChats());
        }
        void filter(String value) {
            query = normalizeQuery(value);
            applyVisibleDiff(filteredChats());
        }
        private String normalizeQuery(String value) {
            return value == null ? "" : value.trim().toLowerCase(Locale.US);
        }
        private List<Chat> filteredChats() {
            List<Chat> filtered = new ArrayList<>();
            for (Chat chat : all) {
                String name = chat.getContactName() == null ? "" : chat.getContactName();
                if (query.isEmpty() || name.toLowerCase(Locale.US).contains(query)) {
                    filtered.add(chat);
                }
            }
            return filtered;
        }
        private void applyVisibleDiff(List<Chat> updated) {
            String previousLastChatId = chats.isEmpty()
                    ? null : chats.get(chats.size() - 1).getChatId();
            boolean structureChanged = false;
            for (int targetIndex = 0; targetIndex < updated.size(); targetIndex++) {
                Chat target = updated.get(targetIndex);
                int existingIndex = indexOfChatFrom(target.getChatId(), targetIndex);
                if (existingIndex < 0) {
                    chats.add(targetIndex, target);
                    notifyItemInserted(targetIndex);
                    structureChanged = true;
                    continue;
                }
                if (existingIndex != targetIndex) {
                    Chat moved = chats.remove(existingIndex);
                    chats.add(targetIndex, moved);
                    notifyItemMoved(existingIndex, targetIndex);
                    structureChanged = true;
                }
                Chat previous = chats.set(targetIndex, target);
                if (!sameChatContent(previous, target)) notifyItemChanged(targetIndex);
            }
            for (int index = chats.size() - 1; index >= updated.size(); index--) {
                chats.remove(index);
                notifyItemRemoved(index);
                structureChanged = true;
            }
            if (structureChanged) {
                notifyChatChanged(previousLastChatId);
                String currentLastChatId = chats.isEmpty()
                        ? null : chats.get(chats.size() - 1).getChatId();
                if (!Objects.equals(previousLastChatId, currentLastChatId)) {
                    notifyChatChanged(currentLastChatId);
                }
            }
        }
        private int indexOfChatFrom(String chatId, int startIndex) {
            for (int index = Math.max(0, startIndex); index < chats.size(); index++) {
                if (Objects.equals(chats.get(index).getChatId(), chatId)) return index;
            }
            return -1;
        }
        private boolean sameChatContent(Chat first, Chat second) {
            return Objects.equals(first.getChatId(), second.getChatId())
                    && Objects.equals(first.getContactName(), second.getContactName())
                    && Objects.equals(first.getProfilePhotoUrl(), second.getProfilePhotoUrl())
                    && Objects.equals(first.getLocalProfilePhotoPath(),
                    second.getLocalProfilePhotoPath())
                    && Objects.equals(first.getLastMessage(), second.getLastMessage())
                    && first.getLastMessageTime() == second.getLastMessageTime()
                    && first.isLastMessageOutgoing() == second.isLastMessageOutgoing()
                    && Objects.equals(first.getLastMessageDeliveredTime(),
                    second.getLastMessageDeliveredTime())
                    && Objects.equals(first.getLastMessageReadTime(),
                    second.getLastMessageReadTime())
                    && Objects.equals(first.getLastMessageStatus(),
                    second.getLastMessageStatus())
                    && Objects.equals(first.getLastMessageType(), second.getLastMessageType())
                    && Objects.equals(first.getLastMessageAttachmentName(),
                    second.getLastMessageAttachmentName())
                    && first.getUnreadCount() == second.getUnreadCount()
                    && first.isPinned() == second.isPinned()
                    && first.isMuted() == second.isMuted()
                    && first.isArchived() == second.isArchived()
                    && first.isOnline() == second.isOnline()
                    && first.getLastSeen() == second.getLastSeen();
        }
        boolean hasChats() { return !all.isEmpty(); }
        long findLastMessageTime(String chatId) {
            for (Chat chat : all) {
                if (chat.getChatId().equals(chatId)) return chat.getLastMessageTime();
            }
            return 0L;
        }
        int indexOfChat(String chatId) {
            for (int index = 0; index < chats.size(); index++) {
                if (chats.get(index).getChatId().equals(chatId)) return index;
            }
            return -1;
        }
        void notifyChatChanged(String chatId) {
            int position = indexOfChat(chatId);
            if (position >= 0) notifyItemChanged(position);
        }
        @Override public int getItemCount() { return chats.size(); }
        @Override public Chat getItem(int position) { return chats.get(position); }
        @Override public long getItemId(int position) {
            String id = chats.get(position).getChatId();
            return id == null || id.isEmpty() ? position : id.hashCode();
        }
        @Override public void onCreateItem(ComponentList.Item item, int type) {
            ComponentList.ItemScope scope = item.getScope();
            float width = scope.width();
            float height = scope.height();
            float scale = figmaConfig.getScale(getWidth());
            ZLayer row = item.addLayer("row");
            row.add(new Image.Builder(getContext(), scope.id("selection_background"),
                    selectionBackgroundBitmap, new RectF(0, 0, width, height))
                    .setScaleType(Image.ScaleType.FIT_XY));
            row.add(new Image.Builder(getContext(), scope.id("avatar"), avatar("?"),
                    new RectF(50f * scale, 27f * scale, 182f * scale, 159f * scale))
                    .setScaleType(Image.ScaleType.CENTER_CROP));
            row.add(new Image.Builder(getContext(), scope.id("selection_check"),
                    selectionCheckBitmap,
                    new RectF(128f * scale, 111f * scale, 184f * scale, 167f * scale))
                    .setScaleType(Image.ScaleType.FIT_XY));
            row.add(rowText(scope.id("name"), new RectF(220f * scale, 38f * scale,
                    width - 230f * scale, 92f * scale), 42f * scale, PRIMARY,
                    FontVariation.MEDIUM));
            row.add(rowText(scope.id("time"), new RectF(width - 300f * scale, 45f * scale,
                    width - 42f * scale, 86f * scale), 30f * scale, SECONDARY,
                    FontVariation.REGULAR).setAlignment(Text.Alignment.END));
            row.add(new Image.Builder(getContext(), scope.id("unread_badge"), unreadBadgeBitmap,
                    new RectF(width - 98f * scale, 91f * scale,
                            width - 42f * scale, 147f * scale))
                    .setScaleType(Image.ScaleType.FIT_XY));
            row.add(new Image.Builder(getContext(), scope.id("unread_badge_with_state"), unreadBadgeBitmap,
                    new RectF(width - 98f * scale, 87f * scale,
                            width - 42f * scale, 143f * scale))
                    .setScaleType(Image.ScaleType.FIT_XY));
            row.add(rowText(scope.id("unread"), new RectF(width - 98f * scale, 91f * scale,
                    width - 42f * scale, 147f * scale), 26f * scale, Color.WHITE,
                    FontVariation.SEMI_BOLD).setAlignment(Text.Alignment.CENTER));
            row.add(rowText(scope.id("unread_with_state"),
                    new RectF(width - 98f * scale, 87f * scale,
                            width - 42f * scale, 143f * scale), 26f * scale, Color.WHITE,
                    FontVariation.SEMI_BOLD).setAlignment(Text.Alignment.CENTER));
            row.add(new Image.Builder(getContext(), scope.id("state"), pinnedBitmap,
                    new RectF(width - 74f * scale, 103f * scale,
                            width - 42f * scale, 135f * scale))
                    .setScaleType(Image.ScaleType.FIT_CENTER));
            row.add(new Image.Builder(getContext(), scope.id("state_with_unread"), pinnedBitmap,
                    new RectF(width - 175f * scale, 103f * scale,
                            width - 143f * scale, 135f * scale))
                    .setScaleType(Image.ScaleType.FIT_CENTER));
            row.add(new Image.Builder(getContext(), scope.id("second_state"), mutedBitmap,
                    new RectF(width - 132f * scale, 103f * scale,
                            width - 100f * scale, 135f * scale))
                    .setScaleType(Image.ScaleType.FIT_CENTER));
            row.add(new Image.Builder(getContext(), scope.id("second_state_with_unread"),
                    mutedBitmap, new RectF(width - 233f * scale, 103f * scale,
                            width - 201f * scale, 135f * scale))
                    .setScaleType(Image.ScaleType.FIT_CENTER));
            row.add(new Image.Builder(getContext(), scope.id("message_status"), deliveredBitmap,
                    new RectF(220f * scale, 115f * scale, 265f * scale, 143f * scale))
                    .setScaleType(Image.ScaleType.FIT_CENTER));
            row.add(rowText(scope.id("message"), new RectF(280f * scale, 103f * scale,
                    width - 210f * scale, 157f * scale), 38f * scale, SECONDARY,
                    FontVariation.REGULAR));
            row.add(rowText(scope.id("received_message"),
                    new RectF(220f * scale, 103f * scale,
                            width - 210f * scale, 157f * scale), 38f * scale, SECONDARY,
                    FontVariation.REGULAR));
            row.add(new Image.Builder(getContext(), scope.id("sent_square_media"), pictureBitmap,
                    new RectF(281f * scale, 114f * scale, 311f * scale, 144f * scale))
                    .setScaleType(Image.ScaleType.FIT_XY));
            row.add(new Image.Builder(getContext(), scope.id("received_square_media"), pictureBitmap,
                    new RectF(220f * scale, 114f * scale, 250f * scale, 144f * scale))
                    .setScaleType(Image.ScaleType.FIT_XY));
            row.add(new Image.Builder(getContext(), scope.id("sent_video_media"), videoBitmap,
                    new RectF(281f * scale, 118f * scale, 315f * scale, 141f * scale))
                    .setScaleType(Image.ScaleType.FIT_XY));
            row.add(new Image.Builder(getContext(), scope.id("received_video_media"), videoBitmap,
                    new RectF(220f * scale, 118f * scale, 254f * scale, 141f * scale))
                    .setScaleType(Image.ScaleType.FIT_XY));
            row.add(rowText(scope.id("sent_square_text"),
                    new RectF(327f * scale, 103f * scale, width - 210f * scale, 157f * scale),
                    38f * scale, SECONDARY, FontVariation.REGULAR));
            row.add(rowText(scope.id("sent_video_text"),
                    new RectF(331f * scale, 103f * scale, width - 210f * scale, 157f * scale),
                    38f * scale, SECONDARY, FontVariation.REGULAR));
            row.add(rowText(scope.id("received_square_text"),
                    new RectF(266f * scale, 103f * scale, width - 210f * scale, 157f * scale),
                    38f * scale, SECONDARY, FontVariation.REGULAR));
            row.add(rowText(scope.id("received_video_text"),
                    new RectF(270f * scale, 103f * scale, width - 210f * scale, 157f * scale),
                    38f * scale, SECONDARY, FontVariation.REGULAR));
            row.add(new Image.Builder(getContext(), scope.id("voice_call_icon"), phoneIncomingBitmap,
                    new RectF(220f * scale, 114f * scale, 250f * scale, 144f * scale))
                    .setScaleType(Image.ScaleType.FIT_XY));
            row.add(new Image.Builder(getContext(), scope.id("video_call_icon"), videoIncomingBitmap,
                    new RectF(220f * scale, 118f * scale, 254f * scale, 141f * scale))
                    .setScaleType(Image.ScaleType.FIT_XY));
            row.add(rowText(scope.id("voice_call_text"),
                    new RectF(266f * scale, 103f * scale, width - 210f * scale, 157f * scale),
                    38f * scale, SECONDARY, FontVariation.REGULAR));
            row.add(rowText(scope.id("video_call_text"),
                    new RectF(270f * scale, 103f * scale, width - 210f * scale, 157f * scale),
                    38f * scale, SECONDARY, FontVariation.REGULAR));
            row.add(new Image.Builder(getContext(), scope.id("divider"), dividerBitmap,
                    new RectF(220f * scale, height - Math.max(1f, scale), width, height))
                    .setScaleType(Image.ScaleType.FIT_XY));
        }
        @Override public void onBindItem(ComponentList.Item item, Chat chat, int position) {
            long bindStarted = SystemClock.elapsedRealtimeNanos();
            Trace.beginSection("ChatsView.bindRow");
            bindAvatar(item, chat);
            boolean selected = selectedChatIds.contains(chat.getChatId());
            item.find("selection_background", Image.class).setVisible(selected);
            item.find("selection_check", Image.class).setVisible(selected);
            float scale = figmaConfig.getScale(getWidth());
            item.find("name", Text.class).setText(ellipsize(chat.getContactName(),
                    630f * scale, 42f * scale));
            item.find("time", Text.class).setText(lastMessageTime(chat));
            boolean unread = chat.getUnreadCount() > 0;
            int previewColor = unread ? UNREAD_PREVIEW : SECONDARY;
            boolean hasState = chat.isPinned() || chat.isMuted();
            item.find("unread_badge", Image.class).setVisible(false);
            item.find("unread_badge_with_state", Image.class).setVisible(false);
            item.find("unread", Text.class).setVisible(false);
            item.find("unread_with_state", Text.class).setVisible(false);
            item.find("state", Image.class).setVisible(false);
            item.find("state_with_unread", Image.class).setVisible(false);
            item.find("second_state", Image.class).setVisible(false);
            item.find("second_state_with_unread", Image.class).setVisible(false);
            if (unread) {
                String badgeId = hasState ? "unread_badge_with_state" : "unread_badge";
                String textId = hasState ? "unread_with_state" : "unread";
                item.find(badgeId, Image.class).setVisible(true);
                item.find(textId, Text.class).setText(unreadCount(chat)).setVisible(true);
            }
            if (hasState) {
                String stateId = unread ? "state_with_unread" : "state";
                item.find(stateId, Image.class)
                        .setBitmap(chat.isPinned() ? pinnedBitmap : mutedBitmap)
                        .setVisible(true);
                if (chat.isPinned() && chat.isMuted()) {
                    String secondStateId = unread
                            ? "second_state_with_unread" : "second_state";
                    item.find(secondStateId, Image.class)
                            .setBitmap(mutedBitmap)
                            .setVisible(true);
                }
            }
            hideMessageComponents(item);
            if (typingBaselines.containsKey(chat.getChatId())) {
                item.find("received_message", Text.class).setText("typing...")
                        .setTextColor(0xFF009FC8).setVisible(true);
                item.find("divider", Image.class).setVisible(position < chats.size() - 1);
                finishBindProfile(bindStarted, position, chat.getChatId());
                return;
            }
            boolean hasMessage = chat.getLastMessage() != null && !chat.getLastMessage().trim().isEmpty();
            boolean received = !chat.isLastMessageOutgoing();
            String type = chat.getLastMessageType() == null ? "text" : chat.getLastMessageType();
            boolean video = "video".equals(type);
            boolean squareMedia = "image".equals(type) || "file".equals(type)
                    || "location".equals(type);
            boolean voiceCall = "voice_call".equals(type);
            boolean videoCall = "video_call".equals(type);
            boolean call = voiceCall || videoCall;
            String preview = lastMessage(chat);
            if (hasMessage && !received && !call) {
                String status = chat.getLastMessageStatus();
                Bitmap receipt = chat.getLastMessageReadTime() != null || "seen".equals(status)
                        ? readBitmap
                        : chat.getLastMessageDeliveredTime() != null || "delivered".equals(status)
                        ? deliveredBitmap
                        : chat.getLastMessageTime() <= 0 || "sending".equals(status)
                        || "failed".equals(status) ? sendingBitmap : sentBitmap;
                item.find("message_status", Image.class).setBitmap(receipt).setVisible(true);
            }
            if (call) {
                boolean missed = preview.toLowerCase(Locale.US).contains("missed");
                boolean didntConnect = preview.toLowerCase(Locale.US).contains("didn't connect");
                String iconId = voiceCall ? "voice_call_icon" : "video_call_icon";
                String textId = voiceCall ? "voice_call_text" : "video_call_text";
                Bitmap icon = voiceCall
                        ? (missed ? phoneMissedBitmap : didntConnect ? phoneIncomingBitmap
                        : received ? phoneIncomingBitmap : phoneOutgoingBitmap)
                        : (missed ? videoMissedBitmap : didntConnect ? videoIncomingBitmap
                        : received ? videoIncomingBitmap : videoOutgoingBitmap);
                item.find(iconId, Image.class).setBitmap(icon).setVisible(true);
                item.find(textId, Text.class)
                        .setText(ellipsize(formatCallPreview(preview, videoCall),
                                voiceCall ? 604f * scale : 600f * scale, 38f * scale))
                        .setTextColor(previewColor).setVisible(true);
            } else if (video || squareMedia) {
                String mediaName = chat.getLastMessageAttachmentName();
                if (mediaName == null || mediaName.trim().isEmpty()) mediaName = preview;
                String direction = received ? "received_" : "sent_";
                String kind = video ? "video" : "square";
                String iconId = direction + kind + "_media";
                String textId = direction + kind + "_text";
                if (squareMedia) {
                    Bitmap icon = "image".equals(type) ? pictureBitmap
                            : "location".equals(type) ? locationBitmap : documentBitmap;
                    item.find(iconId, Image.class).setBitmap(icon);
                }
                item.find(iconId, Image.class).setVisible(true);
                float available = received ? (video ? 600f : 604f) : (video ? 539f : 543f);
                item.find(textId, Text.class).setText(ellipsize(mediaName,
                        available * scale, 38f * scale))
                        .setTextColor(previewColor).setVisible(true);
            } else {
                String textId = received ? "received_message" : "message";
                float available = received ? 650f : 590f;
                item.find(textId, Text.class).setText(ellipsize(preview,
                        available * scale, 38f * scale))
                        .setTextColor(previewColor).setVisible(true);
            }
            item.find("divider", Image.class).setVisible(position < chats.size() - 1);
            finishBindProfile(bindStarted, position, chat.getChatId());
        }

        private void hideMessageComponents(ComponentList.Item item) {
            item.find("message_status", Image.class).setVisible(false);
            item.find("message", Text.class).setVisible(false);
            item.find("received_message", Text.class).setVisible(false);
            item.find("sent_square_media", Image.class).setVisible(false);
            item.find("received_square_media", Image.class).setVisible(false);
            item.find("sent_video_media", Image.class).setVisible(false);
            item.find("received_video_media", Image.class).setVisible(false);
            item.find("sent_square_text", Text.class).setVisible(false);
            item.find("sent_video_text", Text.class).setVisible(false);
            item.find("received_square_text", Text.class).setVisible(false);
            item.find("received_video_text", Text.class).setVisible(false);
            item.find("voice_call_icon", Image.class).setVisible(false);
            item.find("video_call_icon", Image.class).setVisible(false);
            item.find("voice_call_text", Text.class).setVisible(false);
            item.find("video_call_text", Text.class).setVisible(false);
        }

    }

    private Text.Builder rowText(String id, RectF bounds, float size, int color,
                                 FontVariation variation) {
        return new Text.Builder(getContext(), id, "", bounds).setFont(NativeFonts.INTER)
                .setFontVariations(variation).setTextSizePx(size).setTextColor(color)
                .setVerticalAlignment(Text.VerticalAlignment.CENTER).setWrapEnabled(false)
                .setMaxLines(1);
    }

    private String ellipsize(String value, float maxWidth, float textSize) {
        String text = value == null ? "" : value.replace('\n', ' ').trim();
        ellipsizePaint.setTextSize(textSize);
        // Native Text renders Inter with wider metrics than Android's default Paint.
        // Reserve additional width so the pre-truncated value cannot wrap when bound.
        float safeWidth = maxWidth * 0.72f;
        if (ellipsizePaint.measureText(text) <= safeWidth) return text;
        String suffix = "...";
        float available = Math.max(0f, safeWidth - ellipsizePaint.measureText(suffix));
        int low = 0;
        int high = text.length();
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (ellipsizePaint.measureText(text, 0, middle) <= available) low = middle;
            else high = middle - 1;
        }
        return text.substring(0, low).trim() + suffix;
    }

    private String formatCallPreview(String value, boolean video) {
        String label = video ? "Video Call" : "Voice Call";
        String text = value == null ? "" : value.trim();
        text = text.replace("[Voice Call]", "").replace("[Video Call]", "").trim();
        if (text.isEmpty()) return label;
        if (text.equalsIgnoreCase("missed")) return label + " (Missed)";
        if (text.equalsIgnoreCase("didn't connect")) return label + " (Didn't connect)";
        if (text.startsWith("(") && text.endsWith(")")) return label + text;
        return label + " (" + text + ")";
    }

    private void bindAvatar(ComponentList.Item item, Chat chat) {
        long started = SystemClock.elapsedRealtimeNanos();
        String path = chat.getLocalProfilePhotoPath();
        if (path == null || path.trim().isEmpty()) {
            path = ChatProfilePhotoStore.getLocalPath(getContext(), chat.getPhoneNumber());
        }
        int size = avatarPixelSize();
        final String cacheKey = (path == null ? "" : path) + "@" + size;
        Bitmap cached = avatarCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            item.find("avatar", Image.class).setBitmap(cached);
            logAvatarBind(started, "cache", chat.getChatId());
            return;
        }
        String placeholderKey = "placeholder:" + chat.getContactName() + "@" + size;
        Bitmap placeholder = avatarCache.get(placeholderKey);
        if (placeholder == null || placeholder.isRecycled()) {
            placeholder = avatar(chat.getContactName());
            avatarCache.put(placeholderKey, placeholder);
        }
        item.find("avatar", Image.class).setBitmap(placeholder);
        if (path == null || path.trim().isEmpty() || !avatarLoads.add(cacheKey)) {
            logAvatarBind(started, "placeholder", chat.getChatId());
            return;
        }
        final String imagePath = path;
        final String chatId = chat.getChatId();
        avatarExecutor.execute(() -> {
            long decodeStarted = SystemClock.elapsedRealtimeNanos();
            Trace.beginSection("ChatsView.decodeAvatar");
            Bitmap source = BitmapFactory.decodeFile(imagePath);
            Bitmap cropped = source == null ? null : circleCrop(source, size);
            if (source != null && source != cropped && !source.isRecycled()) source.recycle();
            if (cropped != null) avatarCache.put(cacheKey, cropped);
            avatarLoads.remove(cacheKey);
            Trace.endSection();
            long decodeMs = (SystemClock.elapsedRealtimeNanos() - decodeStarted) / 1_000_000L;
            if (isDebugBuild()) {
                Log.d(PERF_TAG, "avatarDecode=" + decodeMs + "ms success="
                        + (cropped != null) + " chat=" + chatId);
            }
            if (cropped != null) post(() -> {
                int currentPosition = adapter.indexOfChat(chatId);
                if (currentPosition >= 0) adapter.notifyItemChanged(currentPosition);
            });
        });
        logAvatarBind(started, "queued", chat.getChatId());
    }

    private void logAvatarBind(long started, String source, String chatId) {
        long elapsedMicros = (SystemClock.elapsedRealtimeNanos() - started) / 1_000L;
        if (isDebugBuild() && elapsedMicros >= 500L) {
            Log.d(PERF_TAG, "avatarBind=" + elapsedMicros + "us source=" + source
                    + " chat=" + chatId);
        }
    }

    private void finishBindProfile(long started, int position, String chatId) {
        Trace.endSection();
        long elapsedMicros = (SystemClock.elapsedRealtimeNanos() - started) / 1_000L;
        if (isDebugBuild() && elapsedMicros >= 1_000L) {
            Log.d(PERF_TAG, "rowBind=" + elapsedMicros + "us position=" + position
                    + " chat=" + chatId);
        }
    }

    private int avatarPixelSize() {
        return Math.max(1, Math.round(132f * figmaConfig.getScale(getWidth())));
    }

    private Bitmap circleCrop(Bitmap source, int size) {
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        float scale = Math.max(size / (float) source.getWidth(), size / (float) source.getHeight());
        matrix.setScale(scale, scale);
        matrix.postTranslate((size - source.getWidth() * scale) / 2f,
                (size - source.getHeight() * scale) / 2f);
        BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        return output;
    }

    private Bitmap avatar(String value) {
        int size = Math.max(1, Math.round(dp(56)));
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

    private String lastMessage(Chat chat) {
        String message = chat.getLastMessage();
        return message == null || message.trim().isEmpty()
                ? "Start conversation" : message.trim();
    }

    private String lastMessageTime(Chat chat) {
        long timestamp = chat.getLastMessageTime();
        if (timestamp <= 0) return "";
        messageCalendar.setTimeInMillis(timestamp);
        todayCalendar.setTimeInMillis(System.currentTimeMillis());
        boolean sameDate = messageCalendar.get(Calendar.ERA) == todayCalendar.get(Calendar.ERA)
                && messageCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR)
                && messageCalendar.get(Calendar.DAY_OF_YEAR) == todayCalendar.get(Calendar.DAY_OF_YEAR);
        reusableDate.setTime(timestamp);
        return (sameDate ? timeFormatter : dateFormatter).format(reusableDate)
                .toLowerCase(Locale.getDefault());
    }

    private String unreadCount(Chat chat) {
        return chat.getUnreadCount() > 99 ? "99+" : String.valueOf(chat.getUnreadCount());
    }

    private List<Chat> toChats(List<ChatEntity> entities) {
        List<Chat> chats = new ArrayList<>();
        if (entities == null) return chats;
        for (ChatEntity entity : entities) {
            String name = entity.contactName == null || entity.contactName.isEmpty()
                    ? entity.otherUserId : entity.contactName;
            String path = entity.localProfilePhotoPath == null
                    || entity.localProfilePhotoPath.isEmpty()
                    ? ChatProfilePhotoStore.getLocalPath(getContext(), entity.otherUserId)
                    : entity.localProfilePhotoPath;
            chats.add(new Chat(entity.chatId, name, entity.profilePhotoUrl, path,
                    entity.lastMessage, entity.lastMessageTime,
                    normalizeId(entity.lastMessageSenderId).equals(currentPhoneNumber()),
                    entity.lastMessageDeliveredTime, entity.lastMessageReadTime,
                    entity.lastMessageStatus,
                    entity.lastMessageType, entity.lastMessageAttachmentName,
                    entity.unreadCount,
                    entity.pinned, entity.notificationMuted, entity.archived,
                    entity.isOnline, entity.lastSeen));
        }
        return chats;
    }

    private String currentPhoneNumber() {
        String uid = LoginStateManager.getInstance().getUID(getContext());
        if (uid == null) return "";
        if (uid.startsWith("<plus>")) return uid.substring("<plus>".length());
        return uid.startsWith("+") ? uid.substring(1) : uid;
    }

    private String normalizeId(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        if (normalized.startsWith("<plus>")) normalized = normalized.substring(6);
        return normalized.startsWith("+") ? normalized.substring(1) : normalized;
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
    private boolean isDebugBuild() {
        return (getContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
    private static Bitmap colorBitmap(int color) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return bitmap;
    }
    private Bitmap resourceBitmap(int resource) {
        return BitmapFactory.decodeResource(getResources(), resource);
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
    private static void recycle(Bitmap... values) {
        for (Bitmap value : values) if (value != null && !value.isRecycled()) value.recycle();
    }

    public interface OnChatClickListener { void onChatClick(Chat chat); }
    public interface OnSelectionChangedListener {
        void onSelectionChanged(List<Chat> selectedChats);
    }
}
