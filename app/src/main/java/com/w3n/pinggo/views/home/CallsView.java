package com.w3n.pinggo.views.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.LruCache;
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
import com.w3n.pinggo.contacts.DeviceContactResolver;
import com.w3n.pinggo.modals.CallLog;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.w3n.pinggo.data.cache.ProfileBitmapCache;
import com.w3n.pinggo.data.repository.ChatRepository;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Scrollable call list implemented with native-views-release.aar components. */
public final class CallsView extends View {
    private static final String TESTING_TAG = "PARVEZ_TESTING";
    private static final String PAGINATION_TAG = "CallPagination";
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
    private OnProfilePhotoClickListener profilePhotoClickListener;
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
    private final LruCache<String, Bitmap> conferenceCollageCache = new LruCache<>(32);
    private final Set<String> selectedCallIds = new java.util.LinkedHashSet<>();
    private final Set<String> requestedProfiles = new java.util.HashSet<>();
    private final Set<String> requestedConferenceProfiles = new java.util.HashSet<>();
    private final Map<String, String> loadedProfiles = new java.util.HashMap<>();
    private OnSelectionChangedListener selectionChangedListener;
    private ComponentList<CallLog> list;
    private Text emptyText;
    private float paginationGestureStartY;
    private boolean paginationGestureMovedUp;
    private boolean paginationRequestedForGesture;
    private boolean canLoadMore = true;
    private long lastThresholdLogAtMs;
    private boolean lastLoggedThresholdReached;
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
        int incomingCount = calls == null ? 0 : calls.size();
        int previousStoredCount = adapter.all.size();
        Log.i(PAGINATION_TAG, "stage=view_submit_start incoming=" + incomingCount
                + " previous=" + adapter.callCount() + " loading="
                + adapter.isPaginationLoading());
        adapter.submit(calls);
        if (calls != null && incomingCount > previousStoredCount) {
            List<CallLog> appendedCalls = new ArrayList<>(calls.subList(
                    Math.min(previousStoredCount, incomingCount), incomingCount));
            // Keep bitmap fallback creation and decode scheduling outside the list-update frame.
            postOnAnimation(() -> prefetchAvatars(appendedCalls));
        }
        updateVisibility();
        Log.i(PAGINATION_TAG, "stage=view_submit_complete rendered=" + adapter.callCount()
                + " itemCount=" + adapter.getItemCount() + " firstVisible="
                + (list == null ? -1 : list.getFirstVisiblePosition()) + " lastVisible="
                + (list == null ? -1 : list.getLastVisiblePosition()));
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
    public void setOnProfilePhotoClickListener(OnProfilePhotoClickListener listener) {
        profilePhotoClickListener = listener;
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
        Log.i(PAGINATION_TAG, "stage=loading_state requested=" + loading
                + " applied=" + adapter.isPaginationLoading() + " started="
                + paginationStarted + " rendered=" + adapter.callCount());
        updateVisibility();
        // Do not force the viewport to the loading footer. Pagination is normally
        // requested during a fling; jumping to the newly inserted footer interrupts
        // that fling and makes the list appear frozen until the request completes.
        // The footer remains part of the adapter and becomes visible naturally.
    }

    public void setCanLoadMore(boolean canLoadMore) {
        if (this.canLoadMore == canLoadMore) return;
        this.canLoadMore = canLoadMore;
        if (!canLoadMore) {
            removeCallbacks(paginationAfterFling);
            paginationRequestedForGesture = true;
        }
        Log.i(PAGINATION_TAG, "stage=has_more_state canLoadMore=" + canLoadMore
                + " rendered=" + adapter.callCount());
    }

    private void loadAllPagesForSearch() {
        if (canLoadMore && !adapter.query.isEmpty() && loadMoreListener != null)
            loadMoreListener.run();
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
        if (!canLoadMore) return;
        int callCount = adapter.callCount();
        int prefetchPosition = Math.max(0, callCount - PAGINATION_PREFETCH_REMAINING);
        int firstVisible = list == null ? -1 : list.getFirstVisiblePosition();
        int lastVisible = list == null ? -1 : list.getLastVisiblePosition();
        int remaining = lastVisible < 0 ? callCount : Math.max(0, callCount - 1 - lastVisible);
        boolean thresholdReached = list != null && callCount > 0
                && lastVisible >= prefetchPosition;
        long now = android.os.SystemClock.elapsedRealtime();
        if (thresholdReached != lastLoggedThresholdReached
                || now - lastThresholdLogAtMs >= 500L) {
            lastThresholdLogAtMs = now;
            lastLoggedThresholdReached = thresholdReached;
            Log.i(PAGINATION_TAG, "stage=threshold_check rendered=" + callCount
                    + " firstVisible=" + firstVisible + " lastVisible=" + lastVisible
                    + " remaining=" + remaining + " prefetchRemaining="
                    + PAGINATION_PREFETCH_REMAINING + " prefetchPosition=" + prefetchPosition
                    + " thresholdReached=" + thresholdReached + " alreadyRequested="
                    + paginationRequestedForGesture);
        }
        if (!paginationRequestedForGesture && thresholdReached && loadMoreListener != null) {
            paginationRequestedForGesture = true;
            Log.d(TESTING_TAG, "call_scroll phase=pagination_requested firstVisible="
                    + firstVisible + " lastVisible=" + lastVisible + " calls=" + callCount
                    + " prefetchPosition=" + prefetchPosition);
            Log.i(PAGINATION_TAG, "stage=listener_dispatch reason=prefetch_threshold"
                    + " rendered=" + callCount + " remaining=" + remaining);
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
        conferenceCollageCache.evictAll();
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
            if (canAppend(values)) {
                int previousCount = all.size();
                int addedCount = values.size() - previousCount;
                if (addedCount == 0) return;
                for (int index = previousCount; index < values.size(); index++) {
                    CallLog call = values.get(index);
                    all.add(call);
                    calls.add(call);
                }
                notifyItemRangeInserted(previousCount, addedCount);
                Log.i(PAGINATION_TAG, "stage=adapter_append previous=" + previousCount
                        + " added=" + addedCount + " total=" + calls.size());
                return;
            }
            all.clear();
            if (values != null) all.addAll(values);
            applyFilter();
        }
        private boolean canAppend(List<CallLog> values) {
            if (!query.isEmpty() || values == null || values.size() < all.size()
                    || calls.size() != all.size()) return false;
            for (int index = 0; index < all.size(); index++) {
                // HomeActivity deliberately reuses immutable prepared rows from its cache.
                // Reference equality makes pagination validation O(previous rows) without
                // recalculating hashes or touching profile storage.
                if (values.get(index) != all.get(index)) return false;
            }
            return true;
        }
        void filter(String value) {
            query = value == null ? "" : value.trim().toLowerCase(Locale.US);
            applyFilter();
        }
        private void applyFilter() {
            List<CallLog> updated = new ArrayList<>();
            for (CallLog call : all) {
                if (query.isEmpty() || matchesQuery(call)) updated.add(call);
            }
            boolean sameOrder = calls.size() == updated.size();
            for (int i = 0; sameOrder && i < calls.size(); i++) {
                sameOrder = Objects.equals(callKey(calls.get(i)), callKey(updated.get(i)));
            }
            if (!sameOrder) {
                calls.clear();
                calls.addAll(updated);
                if (calls.isEmpty()) paginationLoading = false;
                rowBindings.clear();
                notifyDataSetChanged();
                return;
            }
            for (int target = 0; target < updated.size(); target++) {
                CallLog next = updated.get(target);
                int existing = indexOfCallFrom(callKey(next), target);
                if (existing < 0) {
                    calls.add(target, next);
                    notifyItemInserted(target);
                } else {
                    if (existing != target) {
                        CallLog moved = calls.remove(existing);
                        calls.add(target, moved);
                        notifyItemMoved(existing, target);
                    }
                    CallLog previous = calls.set(target, next);
                    if (callContentHash(previous) != callContentHash(next)) notifyItemChanged(target);
                }
            }
            for (int index = calls.size() - 1; index >= updated.size(); index--) {
                calls.remove(index);
                notifyItemRemoved(index);
            }
            if (calls.isEmpty() && paginationLoading) {
                paginationLoading = false;
                notifyItemRemoved(0);
            }
        }
        private int indexOfCallFrom(String key, int start) {
            for (int index = Math.max(0, start); index < calls.size(); index++)
                if (Objects.equals(callKey(calls.get(index)), key)) return index;
            return -1;
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
        void notifyConferenceParticipantChanged(String participantId) {
            rowBindings.clear();
            for (int position = 0; position < calls.size(); position++) {
                CallLog call = calls.get(position);
                if (usesConferenceCollage(call)
                        && call.getParticipantIds().contains(participantId)) {
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
            Bitmap fallbackAvatar = ProfileBitmapCache.get().request(
                    null, "?", Math.max(1, Math.round(132f * scale)), ACCENT, null);
            row.add(new Image.Builder(getContext(), scope.id("avatar"), fallbackAvatar,
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
            Image avatarImage = item.find("avatar", Image.class);
            avatarImage.setOnClickListener(id -> {
                if (isSelecting()) toggleSelection(call);
                else if (profilePhotoClickListener != null)
                    profilePhotoClickListener.onProfilePhotoClick(
                            call, avatarImage.getBitmap(), resolveAvatarPath(call));
            });
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
        if (usesConferenceCollage(call)) {
            bindConferenceAvatar(item, call);
            return;
        }
        item.find("avatar", Image.class).setVisible(true);
        String path = resolveAvatarPath(call);
        if ((path == null || path.trim().isEmpty()) && !call.isGroupCall())
            loadMissingProfile(call);
        int size = Math.max(1, Math.round(px(132f)));
        String cacheKey = avatarCacheKey(path, size);
        Bitmap avatar = ProfileBitmapCache.get().request(path, call.getContactName(), size,
                ACCENT, () -> adapter.notifyAvatarChanged(cacheKey));
        item.find("avatar", Image.class).setBitmap(avatar);
    }

    private void bindConferenceAvatar(ComponentList.Item item, CallLog call) {
        int size = Math.max(1, Math.round(px(132f)));
        for (String participantId : call.getParticipantIds()) {
            String path = participantPhotoPath(participantId);
            if (path == null || path.trim().isEmpty())
                loadMissingConferenceProfile(participantId);
        }
        String collageKey = conferenceCollageKey(call, size);
        Bitmap collage = conferenceCollageCache.get(collageKey);
        if (collage == null || collage.isRecycled()) {
            collage = buildConferenceCollage(call, size, collageKey);
            conferenceCollageCache.put(collageKey, collage);
        }
        item.find("avatar", Image.class).setBitmap(collage).setVisible(true);
    }

    private Bitmap buildConferenceCollage(CallLog call, int size, String collageKey) {
        List<String> participants = call.getParticipantIds();
        int total = participants.size();
        int photoCount = total > 4 ? 3 : Math.min(4, total);
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Path circle = new Path();
        circle.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(circle);
        canvas.drawColor(0xFFF1E8DC);

        int panelCount = total > 4 ? 4 : photoCount;
        RectF[] panels = collagePanels(panelCount, size);
        for (int index = 0; index < photoCount; index++) {
            String participantId = participants.get(index);
            Bitmap photo = ProfileBitmapCache.get().requestSquare(
                    participantPhotoPath(participantId),
                    DeviceContactResolver.cachedNameOrPhone(participantId), size, ACCENT,
                    () -> {
                        conferenceCollageCache.remove(collageKey);
                        adapter.notifyAvatarChanged(avatarKeyFor(call));
                    });
            drawCenterCrop(canvas, photo, panels[index], paint);
        }
        if (total > 4) drawOverflowPanel(canvas, panels[3], total - 3, paint);
        drawCollageDividers(canvas, total > 4 ? 4 : photoCount, size, paint);
        canvas.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, size * .018f));
        paint.setColor(0xFFF1E8DC);
        canvas.drawCircle(size / 2f, size / 2f,
                size / 2f - paint.getStrokeWidth() / 2f, paint);
        return output;
    }

    private static RectF[] collagePanels(int count, int size) {
        float half = size / 2f;
        if (count == 2) return new RectF[] {
                new RectF(0f, 0f, half, size), new RectF(half, 0f, size, size)
        };
        if (count == 3) return new RectF[] {
                new RectF(0f, 0f, half, size), new RectF(half, 0f, size, half),
                new RectF(half, half, size, size)
        };
        return new RectF[] {
                new RectF(0f, 0f, half, half), new RectF(half, 0f, size, half),
                new RectF(0f, half, half, size), new RectF(half, half, size, size)
        };
    }

    private static void drawCenterCrop(
            Canvas canvas, Bitmap bitmap, RectF destination, Paint paint) {
        if (bitmap == null || bitmap.isRecycled()
                || destination.width() <= 0f || destination.height() <= 0f) return;
        float sourceAspect = bitmap.getWidth() / (float) bitmap.getHeight();
        float destinationAspect = destination.width() / destination.height();
        int left = 0;
        int top = 0;
        int right = bitmap.getWidth();
        int bottom = bitmap.getHeight();
        if (sourceAspect > destinationAspect) {
            int croppedWidth = Math.max(1,
                    Math.round(bitmap.getHeight() * destinationAspect));
            left = (bitmap.getWidth() - croppedWidth) / 2;
            right = left + croppedWidth;
        } else if (sourceAspect < destinationAspect) {
            int croppedHeight = Math.max(1,
                    Math.round(bitmap.getWidth() / destinationAspect));
            top = (bitmap.getHeight() - croppedHeight) / 2;
            bottom = top + croppedHeight;
        }
        canvas.drawBitmap(bitmap, new Rect(left, top, right, bottom), destination, paint);
    }

    private static void drawOverflowPanel(Canvas canvas, RectF panel, int overflow, Paint paint) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFD8C8B5);
        canvas.drawRect(panel, paint);
        paint.setColor(0xFF285565);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(panel.width() * .38f);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText("+" + overflow, panel.centerX(),
                panel.centerY() - (metrics.ascent + metrics.descent) / 2f, paint);
        paint.setTypeface(null);
    }

    private static void drawCollageDividers(Canvas canvas, int count, int size, Paint paint) {
        float half = size / 2f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, size * .025f));
        paint.setColor(0xFFF1E8DC);
        canvas.drawLine(half, 0f, half, size, paint);
        if (count == 3) canvas.drawLine(half, half, size, half, paint);
        else if (count >= 4) canvas.drawLine(0f, half, size, half, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void prefetchAvatars(List<CallLog> calls) {
        if (calls == null || calls.isEmpty()) return;
        int size = Math.max(1, Math.round(px(132f)));
        for (CallLog call : calls) {
            if (usesConferenceCollage(call)) {
                int count = Math.min(call.getParticipantIds().size(),
                        call.getParticipantIds().size() > 4 ? 3 : 4);
                for (int index = 0; index < count; index++) {
                    String participantId = call.getParticipantIds().get(index);
                    ProfileBitmapCache.get().requestSquare(participantPhotoPath(participantId),
                            DeviceContactResolver.cachedNameOrPhone(participantId),
                            size, ACCENT, null);
                }
                continue;
            }
            String path = resolveAvatarPath(call);
            if (path == null || path.trim().isEmpty()) continue;
            ProfileBitmapCache.get().request(path, call.getContactName(), size, ACCENT, null);
        }
    }

    private String resolveAvatarPath(CallLog call) {
        String loaded = loadedProfiles.get(call.getPhoneNumber());
        if (loaded != null) return loaded;
        String path = call.getLocalProfilePhotoPath();
        if ((path == null || path.trim().isEmpty()) && !call.isGroupCall()) {
            path = ChatProfilePhotoStore.getLocalPath(getContext(), call.getPhoneNumber());
        }
        return path;
    }

    private void loadMissingProfile(CallLog call) {
        String phone = call.getPhoneNumber();
        if (phone == null || phone.trim().isEmpty() || !requestedProfiles.add(phone)) return;
        String oldKey = avatarKeyFor(call);
        ChatRepository.getInstance(getContext()).loadCallProfilePhoto(
                call.getChatId(), phone, path -> {
                    if (path == null) return;
                    loadedProfiles.put(phone, path);
                    // Drop saved fallback bindings, then rebind using the shared bitmap cache.
                    adapter.notifyAvatarChanged(oldKey);
                    adapter.notifyAvatarChanged(avatarKeyFor(call));
                    Log.d(TESTING_TAG, "call_profile phase=loaded source=shared_store");
                });
    }

    private boolean usesConferenceCollage(CallLog call) {
        return call != null && call.isConference() && call.getParticipantIds().size() > 1;
    }

    private String participantPhotoPath(String participantId) {
        String loaded = loadedProfiles.get(participantId);
        return loaded != null ? loaded
                : ChatProfilePhotoStore.getLocalPath(getContext(), participantId);
    }

    private void loadMissingConferenceProfile(String participantId) {
        if (participantId == null || participantId.trim().isEmpty()
                || !requestedConferenceProfiles.add(participantId)) return;
        ChatRepository.getInstance(getContext()).loadUserProfilePhoto(participantId, path -> {
            if (path == null || path.trim().isEmpty()) return;
            loadedProfiles.put(participantId, path);
            conferenceCollageCache.evictAll();
            adapter.notifyConferenceParticipantChanged(participantId);
            Log.d(TESTING_TAG, "call_profile phase=conference_participant_loaded");
        });
    }

    private String avatarCacheKey(String path, int size) {
        return (path == null ? "" : path) + "@" + size;
    }

    private String conferenceCollageKey(CallLog call, int size) {
        StringBuilder key = new StringBuilder("conference@").append(size);
        for (String participantId : call.getParticipantIds()) {
            key.append('|').append(participantId).append(':')
                    .append(participantPhotoPath(participantId));
        }
        return key.toString();
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
                call.getLocalProfilePhotoPath(), call.getParticipantIds());
    }

    private String avatarKeyFor(CallLog call) {
        if (usesConferenceCollage(call))
            return "conference|" + android.text.TextUtils.join(",", call.getParticipantIds());
        return avatarCacheKey(resolveAvatarPath(call), Math.max(1, Math.round(px(132f))));
    }

    private Text.Builder rowText(String id, RectF bounds, float size, int color,
                                 FontVariation variation) {
        return new Text.Builder(getContext(), id, "", bounds).setFont(ListFonts.inter(getContext(), variation))
                .clearFontVariations().setTextSizePx(size).setTextColor(color)
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
    public interface OnProfilePhotoClickListener {
        void onProfilePhotoClick(CallLog callLog, Bitmap fallback, String originalSource);
    }
    public interface OnSelectionChangedListener {
        void onSelectionChanged(List<CallLog> selectedCalls);
    }
    public interface OnCallStartListener {
        void onCallStart(CallLog callLog, boolean video);
    }
}
