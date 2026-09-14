package com.w3n.pinggo.views.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.core.content.ContextCompat;

import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.progress.Progress;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.R;
import com.w3n.pinggo.Util.PhoneNumberFormatter;
import com.w3n.pinggo.modals.CallLog;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Scrollable call list implemented with native-views-release.aar components. */
public final class CallsView extends View {
    private static final String TESTING_TAG = "PARVEZ_TESTING";
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
    private static final int PRIMARY = 0xFF000E1A;
    private static final int SECONDARY = 0xFF687382;
    private static final int ACCENT = 0xFF019CC4;
    private static final int PAGINATION_PREFETCH_REMAINING = 10;

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
    private final Bitmap selectionBackgroundBitmap = drawableBitmap(R.drawable.chat_selection_background);
    private final Bitmap selectionCheckBitmap = drawableBitmap(R.drawable.chat_selection_check);
    private final Map<String, Bitmap> avatarCache = new ConcurrentHashMap<>();
    private final Set<String> avatarLoads = ConcurrentHashMap.newKeySet();
    private final ExecutorService avatarExecutor = Executors.newFixedThreadPool(2);
    private final Set<String> selectedCallIds = new java.util.LinkedHashSet<>();
    private OnSelectionChangedListener selectionChangedListener;
    private ComponentList<CallLog> list;
    private Text emptyText;
    private float paginationGestureStartY;
    private boolean paginationGestureMovedUp;
    private boolean paginationRequestedForGesture;
    private final Runnable paginationAfterFling = this::loadNextPageIfNeeded;

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
        prefetchAvatars(adapter.all);
        updateVisibility();
        post(this::loadAllPagesForSearch);
    }

    public void filter(String query) {
        adapter.filter(query);
        updateVisibility();
        post(this::loadAllPagesForSearch);
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        selectionChangedListener = listener;
    }

    public boolean clearSelection() {
        if (selectedCallIds.isEmpty()) return false;
        Set<String> previousSelection = new java.util.LinkedHashSet<>(selectedCallIds);
        selectedCallIds.clear();
        for (String callId : previousSelection) adapter.notifyCallChanged(callId);
        notifySelectionChanged();
        return true;
    }

    private boolean isSelecting() { return !selectedCallIds.isEmpty(); }

    private void toggleSelection(CallLog call) {
        String id = call.getCallId();
        if (id == null || id.trim().isEmpty()) return;
        if (!selectedCallIds.add(id)) selectedCallIds.remove(id);
        adapter.notifyCallChanged(id);
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (selectionChangedListener == null) return;
        List<CallLog> selected = new ArrayList<>();
        for (CallLog call : adapter.all) {
            if (selectedCallIds.contains(call.getCallId())) selected.add(call);
        }
        selectionChangedListener.onSelectionChanged(selected);
    }

    public void setPaginationLoading(boolean loading) {
        boolean paginationStarted = adapter.setPaginationLoading(loading && adapter.callCount() > 0);
        updateVisibility();
        if (paginationStarted && adapter.query.isEmpty()) {
            post(() -> {
                if (list != null && adapter.isPaginationLoading()) {
                    list.scrollToPosition(adapter.getItemCount() - 1);
                }
            });
        }
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
                .setPaddingPx(0, 0, 0, 0)
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
        boolean empty = adapter.callCount() == 0;
        list.setVisible(!empty).setEnabled(!empty);
        emptyText.setVisible(empty);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); layers.draw(canvas); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            removeCallbacks(paginationAfterFling);
            paginationGestureStartY = event.getY();
            paginationGestureMovedUp = false;
            paginationRequestedForGesture = false;
        } else if (action == MotionEvent.ACTION_MOVE
                && event.getY() - paginationGestureStartY
                < -ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
            paginationGestureMovedUp = true;
        }
        boolean handled = layers.onTouchEvent(event);
        if (handled && paginationGestureMovedUp && !paginationRequestedForGesture) {
            if (action == MotionEvent.ACTION_MOVE) post(paginationAfterFling);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                postDelayed(paginationAfterFling, 120L);
                postDelayed(paginationAfterFling, 450L);
                postDelayed(paginationAfterFling, 900L);
            }
        }
        return handled || super.onTouchEvent(event);
    }

    private void loadNextPageIfNeeded() {
        int callCount = adapter.callCount();
        int prefetchPosition = Math.max(0, callCount - PAGINATION_PREFETCH_REMAINING);
        if (!paginationRequestedForGesture && list != null && callCount > 0
                && list.getLastVisiblePosition() >= prefetchPosition
                && loadMoreListener != null) {
            paginationRequestedForGesture = true;
            Log.d(TESTING_TAG, "call_scroll phase=pagination_requested firstVisible="
                    + list.getFirstVisiblePosition() + " lastVisible="
                    + list.getLastVisiblePosition() + " calls=" + callCount
                    + " prefetchPosition=" + prefetchPosition);
            loadMoreListener.run();
        }
    }

    public void release() {
        removeCallbacks(paginationAfterFling);
        layers.release();
        if (!dividerBitmap.isRecycled()) dividerBitmap.recycle();
        recycle(phoneIncomingBitmap, phoneOutgoingBitmap, phoneMissedBitmap,
                videoIncomingBitmap, videoOutgoingBitmap, videoMissedBitmap,
                selectionBackgroundBitmap, selectionCheckBitmap);
        for (Bitmap bitmap : avatarCache.values()) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        avatarCache.clear();
        avatarLoads.clear();
        avatarExecutor.shutdownNow();
    }

    private final class CallAdapter extends ComponentList.Adapter<CallLog> {
        private static final int TYPE_CALL = 0;
        private static final int TYPE_PAGINATION = 1;
        private final List<CallLog> all = new ArrayList<>();
        private final List<CallLog> calls = new ArrayList<>();
        private final Map<ComponentList.Item, CallBindingState> rowBindings =
                new IdentityHashMap<>();
        private String query = "";
        private boolean paginationLoading;
        boolean setPaginationLoading(boolean loading) {
            if (paginationLoading == loading) return false;
            int footerPosition = calls.size();
            paginationLoading = loading;
            if (loading) notifyItemInserted(footerPosition);
            else notifyItemRemoved(footerPosition);
            return loading;
        }
        boolean isPaginationLoading() { return paginationLoading; }
        int callCount() { return calls.size(); }
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
                if (query.isEmpty() || matchesQuery(call)) calls.add(call);
            }
            if (calls.isEmpty()) paginationLoading = false;
            notifyDataSetChanged();
        }
        private boolean matchesQuery(CallLog call) {
            return containsQuery(call.getContactName())
                    || phoneMatchesQuery(call.getPhoneNumber())
                    || containsQuery(call.getCalledTime())
                    || containsQuery(call.getFullCalledDateTime())
                    || containsQuery(call.getDuration())
                    || containsQuery(call.isVideoCall() ? "video call" : "voice call")
                    || containsQuery(call.isConference() ? "conference call" : "")
                    || containsQuery(call.isMissed() ? "missed call" : "")
                    || containsQuery(call.isOutgoing() ? "outgoing call" : "incoming call");
        }
        private boolean containsQuery(String value) {
            return value != null && value.toLowerCase(Locale.US).contains(query);
        }
        private boolean phoneMatchesQuery(String value) {
            if (value == null || !isPhoneLikeQuery(query)) return false;
            String queryDigits = PhoneNumberFormatter.digitsOnly(query);
            String phoneDigits = PhoneNumberFormatter.digitsOnly(value);
            return !queryDigits.isEmpty() && phoneDigits.contains(queryDigits);
        }
        private boolean isPhoneLikeQuery(String value) {
            if (value == null || value.isEmpty()) return false;
            for (int index = 0; index < value.length(); index++) {
                if (Character.isLetter(value.charAt(index))) return false;
            }
            return true;
        }
        @Override public int getItemCount() { return calls.size() + (paginationLoading ? 1 : 0); }
        @Override public CallLog getItem(int position) {
            return position < calls.size() ? calls.get(position) : calls.get(calls.size() - 1);
        }
        @Override public int getItemViewType(int position) {
            return position < calls.size() ? TYPE_CALL : TYPE_PAGINATION;
        }
        void notifyCallChanged(String callId) {
            if (callId == null) return;
            Iterator<Map.Entry<ComponentList.Item, CallBindingState>> iterator =
                    rowBindings.entrySet().iterator();
            while (iterator.hasNext()) {
                if (Objects.equals(iterator.next().getValue().callId, callId)) iterator.remove();
            }
            for (int index = 0; index < calls.size(); index++) {
                if (Objects.equals(calls.get(index).getCallId(), callId)) notifyItemChanged(index);
            }
        }
        void notifyAvatarChanged(String avatarKey) {
            Iterator<Map.Entry<ComponentList.Item, CallBindingState>> iterator =
                    rowBindings.entrySet().iterator();
            while (iterator.hasNext()) {
                if (Objects.equals(iterator.next().getValue().avatarKey, avatarKey)) {
                    iterator.remove();
                }
            }
            for (int position = 0; position < calls.size(); position++) {
                if (Objects.equals(avatarKeyFor(calls.get(position)), avatarKey)) {
                    notifyItemChanged(position);
                }
            }
        }
        @Override public long getItemId(int position) {
            if (position >= calls.size()) return Long.MIN_VALUE;
            return callKey(calls.get(position)).hashCode();
        }
        @Override public void onCreateItem(ComponentList.Item item, int type) {
            rowBindings.remove(item);
            ComponentList.ItemScope scope = item.getScope();
            float width = scope.width();
            float height = scope.height();
            float scale = figmaConfig.getScale(getWidth());
            if (type == TYPE_PAGINATION) {
                float progressSize = 44f * scale;
                ZLayer footer = item.addLayer("pagination_footer");
                footer.add(new Progress.Builder(getContext(), scope.id("progress"),
                        new RectF((width - progressSize) / 2f,
                                (height - progressSize) / 2f,
                                (width + progressSize) / 2f,
                                (height + progressSize) / 2f))
                        .setStyle(Progress.Style.CIRCULAR)
                        .setMode(Progress.Mode.INDETERMINATE)
                        .setProgressColor(ACCENT)
                        .setTrackColor(0x22019CC4)
                        .setThickness(6f)
                        .setIndeterminateDuration(850L));
                return;
            }
            ZLayer row = item.addLayer("row");
            row.add(new Image.Builder(getContext(), scope.id("selection_background"),
                    selectionBackgroundBitmap, new RectF(0, 0, width, height))
                    .setScaleType(Image.ScaleType.FIT_XY));
            row.add(new ChatRowRippleComponent(scope.id("row_ripple"),
                    new RectF(0f, 0f, width, height)));
            row.add(new Image.Builder(getContext(), scope.id("avatar"), cachedAvatar("?"),
                    new RectF(50f * scale, 27f * scale, 182f * scale, 159f * scale))
                    .setScaleType(Image.ScaleType.CENTER_CROP));
            row.add(new Image.Builder(getContext(), scope.id("selection_check"),
                    selectionCheckBitmap,
                    new RectF(128f * scale, 111f * scale, 184f * scale, 167f * scale))
                    .setScaleType(Image.ScaleType.FIT_XY));
            row.add(rowText(scope.id("name"), new RectF(220f * scale, 14f * scale,
                    width - 210f * scale, 108f * scale), 36f * scale, PRIMARY,
                    FontVariation.MEDIUM).setWrapEnabled(true));
            row.add(rowText(scope.id("details"), new RectF(220f * scale, 110f * scale,
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
            if (getItemViewType(position) == TYPE_PAGINATION) return;
            boolean selected = selectedCallIds.contains(call.getCallId());
            CallBindingState previousBinding = rowBindings.get(item);
            if (previousBinding != null && previousBinding.matches(call, position, selected)) {
                if (previousBinding.restoreAvatar(item)) return;
                rowBindings.remove(item);
            }
            item.find("selection_background", Image.class).setVisible(selected);
            item.find("selection_check", Image.class).setVisible(selected);
            item.find("row_ripple", ChatRowRippleComponent.class).bind(
                    new RectF(0f, 0f, item.getScope().width(), item.getScope().height()),
                    () -> { if (isSelecting()) toggleSelection(call); else clickListener.onCallClick(call); },
                    () -> toggleSelection(call));
            bindAvatar(item, call);
            item.find("name", Text.class).setText(call.getContactName());
            String duration = call.getDuration() == null ? "" : call.getDuration().trim();
            String details = call.getCalledTime() == null ? "" : call.getCalledTime().trim();
            if (call.isConference()) {
                String type = call.isGroupCall()
                        ? (call.isVideoCall() ? "Group video call" : "Group voice call")
                        : (call.isVideoCall() ? "Conference video call" : "Conference voice call");
                details = type + (details.isEmpty() ? "" : " · " + details);
            }
            if (!duration.isEmpty()) details += (details.isEmpty() ? "" : " · ") + duration;
            item.find("details", Text.class).setText(details);
            item.find("type", Image.class)
                    .setBitmap(callIcon(call));
            float scale = figmaConfig.getScale(getWidth());
            item.find("call_ripple", ChatRowRippleComponent.class).bind(
                    new RectF(item.getScope().width() - 176f * scale, 20f * scale,
                            item.getScope().width(), 165f * scale),
                    () -> { if (isSelecting()) toggleSelection(call);
                        else callStartListener.onCallStart(call, call.isVideoCall()); },
                    () -> toggleSelection(call));
            item.find("divider", Image.class).setVisible(position < calls.size() - 1);
            rowBindings.put(item, new CallBindingState(item, call, position, selected));
        }

        @Override public void onItemRecycled(ComponentList.Item item) {
            rowBindings.remove(item);
        }

        private final class CallBindingState {
            private final String callId;
            private final String key;
            private final int contentHash;
            private final int position;
            private final boolean selected;
            private final Bitmap avatarBitmap;
            private final String avatarKey;

            private CallBindingState(ComponentList.Item item, CallLog call, int position,
                                     boolean selected) {
                this.callId = call.getCallId();
                this.key = callKey(call);
                this.contentHash = callContentHash(call);
                this.position = position;
                this.selected = selected;
                this.avatarBitmap = item.find("avatar", Image.class).getBitmap();
                this.avatarKey = avatarKeyFor(call);
            }

            private boolean matches(CallLog call, int nextPosition, boolean nextSelected) {
                return position == nextPosition && selected == nextSelected
                        && Objects.equals(key, callKey(call))
                        && contentHash == callContentHash(call);
            }

            private boolean restoreAvatar(ComponentList.Item item) {
                if (avatarBitmap == null || avatarBitmap.isRecycled()) return false;
                Image avatarImage = item.find("avatar", Image.class);
                boolean bitmapChanged = avatarImage.getBitmap() != avatarBitmap;
                boolean visibilityChanged = !avatarImage.isVisible();
                if (bitmapChanged) avatarImage.setBitmap(avatarBitmap);
                if (visibilityChanged) avatarImage.setVisible(true);
                if (bitmapChanged || visibilityChanged) {
                    Log.d(TESTING_TAG, "call_scroll phase=avatar_restored key=" + key
                            + " position=" + position);
                }
                return true;
            }
        }
    }

    private void bindAvatar(ComponentList.Item item, CallLog call) {
        String path = resolveAvatarPath(call);
        int size = Math.max(1, Math.round(px(132f)));
        String cacheKey = avatarCacheKey(path, size);
        Bitmap cached = avatarCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            item.find("avatar", Image.class).setBitmap(cached);
            return;
        }
        item.find("avatar", Image.class).setBitmap(cachedAvatar(call.getContactName()));
        if (path == null || path.trim().isEmpty()) return;
        queueAvatarLoad(callKey(call), path, size, cacheKey);
    }

    private void prefetchAvatars(List<CallLog> calls) {
        if (calls == null || calls.isEmpty()) return;
        int size = Math.max(1, Math.round(px(132f)));
        int queued = 0;
        for (CallLog call : calls) {
            String path = resolveAvatarPath(call);
            if (path == null || path.trim().isEmpty()) continue;
            String cacheKey = avatarCacheKey(path, size);
            Bitmap cached = avatarCache.get(cacheKey);
            if (cached != null && !cached.isRecycled()) continue;
            if (queueAvatarLoad(callKey(call), path, size, cacheKey)) queued++;
        }
        if (queued > 0) {
            Log.d(TESTING_TAG, "call_avatar phase=prefetch_queued count=" + queued
                    + " totalCalls=" + calls.size());
        }
    }

    private String resolveAvatarPath(CallLog call) {
        String path = call.getLocalProfilePhotoPath();
        if ((path == null || path.trim().isEmpty()) && !call.isGroupCall()) {
            path = ChatProfilePhotoStore.getLocalPath(getContext(), call.getPhoneNumber());
        }
        return path;
    }

    private String avatarCacheKey(String path, int size) {
        return (path == null ? "" : path) + "@" + size;
    }

    private boolean queueAvatarLoad(String key, String imagePath, int size, String cacheKey) {
        if (!avatarLoads.add(cacheKey)) return false;
        avatarExecutor.execute(() -> {
            Bitmap source = BitmapFactory.decodeFile(imagePath);
            Bitmap cropped = source == null ? null : circleCrop(source, size);
            if (source != null && source != cropped && !source.isRecycled()) source.recycle();
            if (cropped != null) avatarCache.put(cacheKey, cropped);
            avatarLoads.remove(cacheKey);
            if (cropped != null) post(() -> {
                adapter.notifyAvatarChanged(cacheKey);
            });
            Log.d(TESTING_TAG, "call_avatar phase=decode_complete success="
                    + (cropped != null) + " key=" + key);
        });
        return true;
    }

    private String callKey(CallLog call) {
        if (call == null) return "";
        if (call.getCallId() != null && !call.getCallId().isEmpty()) {
            return "call:" + call.getCallId();
        }
        if (call.getMessageId() != null && !call.getMessageId().isEmpty()) {
            return "message:" + call.getMessageId();
        }
        return "fallback:" + call.getChatId() + '|' + call.getPhoneNumber() + '|'
                + call.getFullCalledDateTime();
    }

    private int callContentHash(CallLog call) {
        return Objects.hash(callKey(call), call.getContactName(), call.getCalledTime(),
                call.getFullCalledDateTime(), call.getDuration(), call.isVideoCall(),
                call.isOutgoing(), call.isMissed(), call.isConference(),
                call.getLocalProfilePhotoPath());
    }

    private String avatarKeyFor(CallLog call) {
        return avatarCacheKey(resolveAvatarPath(call), Math.max(1, Math.round(px(132f))));
    }

    private static Bitmap circleCrop(Bitmap source, int size) {
        Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float scale = Math.max(size / (float) source.getWidth(), size / (float) source.getHeight());
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((size - source.getWidth() * scale) / 2f,
                (size - source.getHeight() * scale) / 2f);
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        return result;
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
    public interface OnSelectionChangedListener {
        void onSelectionChanged(List<CallLog> selectedCalls);
    }
    public interface OnCallStartListener {
        void onCallStart(CallLog callLog, boolean video);
    }
}
