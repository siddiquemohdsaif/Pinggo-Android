package com.w3n.pinggo.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.R;
import com.w3n.pinggo.data.local.ChatEntity;
import com.w3n.pinggo.data.local.CallEntity;
import com.w3n.pinggo.data.repository.CallRepository;
import com.w3n.pinggo.contacts.DeviceContactResolver;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.modals.CallLog;
import com.w3n.pinggo.modals.Chat;
import com.w3n.pinggo.views.common.ExitAppController;
import com.w3n.pinggo.views.home.HomeMenuDialogView;
import com.w3n.pinggo.views.home.HomeView;

import java.util.ArrayList;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hosts the AAR-native home surface and owns lifecycle, data, and navigation.
 */
public class HomeActivity extends PingGoActivity implements HomeView.Listener {
    private static final String TESTING_TAG = "PARVEZ_TESTING";
    private static final String CALL_PAGINATION_TAG = "CallPagination";
    private static final int SELECTION_STATUS_BAR_COLOR = 0xFFE9EDF0;
    private HomeView homeView;
    private HomeMenuDialogView homeMenuDialog;
    private ChatRepository repository;
    private List<ChatEntity> latestChatEntities = new ArrayList<>();
    private CallRepository callRepository;
    private List<CallEntity> latestCallEntities = new ArrayList<>();
    private String nextCallCursor;
    private boolean callListHasMore;
    private boolean callListLoading;
    private int callListGeneration;
    private int callListVisibleLimit = 20;
    private boolean callPaginationRevealPending;
    private final ExecutorService callRowExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService chatRowExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, CachedCallRow> callRowCache = new ConcurrentHashMap<>();
    private int callRowBuildGeneration;
    private int chatRowBuildGeneration;
    private int contactNameGeneration;
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
            });
    private final ActivityResultLauncher<String> contactsPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted)
                    warmDeviceContacts();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        homeView = new HomeView(this, this);
        setContentView(homeView);
        ExitAppController.install(this, null);
        ViewGroup content = findViewById(android.R.id.content);
        boolean companionDevice = LoginStateManager.getInstance().isCompanionDevice(this);
        homeMenuDialog = new HomeMenuDialogView(this, new HomeMenuDialogView.Listener() {
            @Override
            public void onNewChat() {
                HomeActivity.this.onNewChat();
            }

            @Override
            public void onNewGroup() {
                HomeActivity.this.onNewGroup();
            }

            @Override
            public void onLinkedDevices() {
                if (LoginStateManager.getInstance().isCompanionDevice(HomeActivity.this)) return;
                startActivity(new Intent(HomeActivity.this, LinkedDevicesActivity.class));
            }

            @Override
            public void onSettings() {
                openSettings();
            }
        }, !companionDevice);
        content.addView(homeMenuDialog, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (homeMenuDialog != null && homeMenuDialog.dismissIfShowing())
                    return;
                if (homeView != null && homeView.dismissProfilePhotoPreview())
                    return;
                if (homeView != null && homeView.clearSelections())
                    return;

                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
        ViewCompat.setOnApplyWindowInsetsListener(homeView, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            homeView.setInsets(bars.top, bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(homeView);
        loadChats();
        requestContactsPermission();
        requestNotificationPermission();
    }

    private void requestContactsPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
            warmDeviceContacts();
        else
            contactsPermission.launch(Manifest.permission.READ_CONTACTS);
    }

    private void warmDeviceContacts() {
        DeviceContactResolver.warmUp(this, () -> {
            if (homeView == null)
                return;
            contactNameGeneration++;
            submitChatsAsync(latestChatEntities);
            submitCachedCalls(latestCallEntities);
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void loadChats() {
        long loadStartedAt = SystemClock.elapsedRealtime();
        Log.d(TESTING_TAG, "home_chat_progress phase=loading_started");
        homeView.showChatLoading();
        repository = ChatRepository.getInstance(this);
        // Covers a fresh login completed after Application.onCreate().
        repository.connect();
        repository.observeChats().observe(this, entities -> {
            long renderStartedNanos = SystemClock.elapsedRealtimeNanos();
            int count = entities == null ? 0 : entities.size();
            Log.d(TESTING_TAG, "home_chat_render phase=room_observed count=" + count
                    + " loadElapsedMs=" + (SystemClock.elapsedRealtime() - loadStartedAt));
            latestChatEntities = entities == null ? new ArrayList<>() : entities;
            submitChatsAsync(entities);
            Log.d(TESTING_TAG, "home_chat_render phase=conversion_scheduled count=" + count
                    + " durationMs="
                    + ((SystemClock.elapsedRealtimeNanos() - renderStartedNanos) / 1_000_000L));
            submitCachedCalls(latestCallEntities);
            repository.acknowledgePendingIncomingDeliveries();
        });
        String uid = LoginStateManager.getInstance().getUID(this);
        if (uid != null && !uid.trim().isEmpty()) {
            Log.d(TESTING_TAG, "home_chat_progress phase=initial_request accountReady=true");
            repository.ensureChatListLoaded(normalizeAccountId(uid));
            callRepository = CallRepository.getInstance(this);
            callRepository.observeCalls(uid).observe(this, calls -> {
                latestCallEntities = calls == null ? new ArrayList<>() : calls;
                Log.i(CALL_PAGINATION_TAG, "stage=cache_observed cached="
                        + latestCallEntities.size() + " visibleLimit=" + callListVisibleLimit
                        + " revealPending=" + callPaginationRevealPending);
                submitCachedCalls(latestCallEntities);
            });
        }
    }

    @Override
    public void onOpenChat(Chat chat) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, chat.getContactName());
        intent.putExtra(ChatActivity.EXTRA_CHAT_ID, chat.getChatId());
        intent.putExtra(ChatActivity.EXTRA_PROFILE_PHOTO_URL, chat.getProfilePhotoUrl());
        String localPath = chat.getLocalProfilePhotoPath();
        boolean groupChat = chat.getChatId() != null && chat.getChatId().startsWith("grp_");
        if (!groupChat && (localPath == null || localPath.trim().isEmpty())) {
            localPath = ChatProfilePhotoStore.getLocalPath(this, chat.getPhoneNumber());
        }
        intent.putExtra(ChatActivity.EXTRA_LOCAL_PROFILE_PHOTO_PATH, localPath);
        intent.putExtra(ChatActivity.EXTRA_OPEN_REQUEST_NANOS, SystemClock.elapsedRealtimeNanos());
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repository == null)
            return;
        refreshServerCalls();
        repository.acknowledgePendingIncomingDeliveries();
        repository.setEventListener(new ChatRepository.EventListener() {
            @Override
            public void onTyping(String chatId, String userId, boolean typing) {
                if (homeView != null)
                    homeView.setChatTyping(chatId, typing);
            }

            @Override
            public void onSocketError(String error) {
            }

            @Override
            public void onTotalUnread(int totalUnread) {
                if (homeView != null)
                    homeView.setTotalUnread(totalUnread);
            }

            @Override
            public void onCallsChanged() {
                refreshServerCalls();
            }
        });
    }

    private void refreshServerCalls() {
        callListGeneration++;
        callListLoading = false;
        nextCallCursor = null;
        callListHasMore = true;
        // Keep already revealed history visible while refreshing the first server page.
        callListVisibleLimit = Math.max(20, callListVisibleLimit);
        callPaginationRevealPending = false;
        Log.i(CALL_PAGINATION_TAG, "stage=refresh_reset generation=" + callListGeneration
                + " visibleLimit=" + callListVisibleLimit + " cached="
                + latestCallEntities.size());
        if (homeView != null) {
            homeView.setCallsPaginationLoading(false);
            homeView.setCallsCanLoadMore(true);
            submitCachedCalls(latestCallEntities);
        }
        loadServerCalls();
    }

    private void loadServerCalls() {
        if (callListLoading || callPaginationRevealPending || !callListHasMore) {
            Log.i(CALL_PAGINATION_TAG, "stage=request_skipped loading=" + callListLoading
                    + " revealPending=" + callPaginationRevealPending + " hasMore="
                    + callListHasMore + " cursorPresent="
                    + (nextCallCursor != null && !nextCallCursor.isEmpty()));
            return;
        }
        String uid = LoginStateManager.getInstance().getUID(this);
        if (uid == null || uid.trim().isEmpty()) {
            Log.i(CALL_PAGINATION_TAG, "stage=request_skipped reason=missing_user");
            return;
        }
        callListLoading = true;
        final int requestGeneration = callListGeneration;
        final String requestedCursor = nextCallCursor;
        final boolean pagination = requestedCursor != null && !requestedCursor.isEmpty();
        final long requestStartedAt = SystemClock.elapsedRealtime();
        Log.i(CALL_PAGINATION_TAG, "stage=api_request_start generation=" + requestGeneration
                + " kind=" + (pagination ? "next_page" : "initial") + " pageSize=20"
                + " cursorPresent=" + pagination + " cached=" + latestCallEntities.size()
                + " visibleLimit=" + callListVisibleLimit);
        if (pagination && homeView != null)
            homeView.setCallsPaginationLoading(true);
        AppFunctionManager.getInstance().getCallList(uid, 20, requestedCursor,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        if (requestGeneration != callListGeneration) {
                            Log.i(CALL_PAGINATION_TAG, "stage=api_response_ignored generation="
                                    + requestGeneration + " currentGeneration=" + callListGeneration);
                            return;
                        }
                        callListLoading = false;
                        if (!(object instanceof JsonObject)) {
                            Log.i(CALL_PAGINATION_TAG, "stage=api_response_invalid kind="
                                    + (pagination ? "next_page" : "initial") + " elapsedMs="
                                    + (SystemClock.elapsedRealtime() - requestStartedAt));
                            callPaginationRevealPending = false;
                            if (homeView != null) homeView.setCallsPaginationLoading(false);
                            return;
                        }
                        JsonObject response = (JsonObject) object;
                        JsonArray values = response.getAsJsonArray("calls");
                        if (values == null)
                            values = new JsonArray();
                        if (pagination && values.size() > 0) {
                            callListVisibleLimit += values.size();
                            callPaginationRevealPending = true;
                        }
                        if (callRepository != null)
                            callRepository.cachePage(uid, values);
                        nextCallCursor = jsonString(response, "nextCursor");
                        callListHasMore = response.has("hasMore")
                                && response.get("hasMore").getAsBoolean()
                                && !nextCallCursor.isEmpty();
                        if (homeView != null)
                            homeView.setCallsCanLoadMore(callListHasMore);
                        Log.i(CALL_PAGINATION_TAG, "stage=api_response_success kind="
                                + (pagination ? "next_page" : "initial") + " received="
                                + values.size() + " elapsedMs="
                                + (SystemClock.elapsedRealtime() - requestStartedAt)
                                + " visibleLimit=" + callListVisibleLimit + " hasMore="
                                + callListHasMore + " nextCursorPresent="
                                + !nextCallCursor.isEmpty() + " revealPending="
                                + callPaginationRevealPending);
                        if (pagination && values.size() == 0 && homeView != null)
                            homeView.setCallsPaginationLoading(false);
                    }

                    @Override
                    public void onError(String error) {
                        if (requestGeneration == callListGeneration)
                            callListLoading = false;
                        callPaginationRevealPending = false;
                        if (homeView != null) homeView.setCallsPaginationLoading(false);
                        Log.e(CALL_PAGINATION_TAG, "stage=api_response_error kind="
                                + (pagination ? "next_page" : "initial") + " elapsedMs="
                                + (SystemClock.elapsedRealtime() - requestStartedAt)
                                + " error=" + error);
                    }
                });
    }

    @Override
    public void onLoadMoreCalls() {
        Log.i(CALL_PAGINATION_TAG, "stage=activity_load_more_callback cached="
                + latestCallEntities.size() + " visibleLimit=" + callListVisibleLimit
                + " loading=" + callListLoading + " revealPending="
                + callPaginationRevealPending + " hasMore=" + callListHasMore);
        loadServerCalls();
    }

    private void submitCachedCalls(List<CallEntity> values) {
        if (callRowExecutor.isShutdown()) return;
        final List<CallEntity> callSnapshot = values == null
                ? new ArrayList<>() : new ArrayList<>(values);
        final List<ChatEntity> chatSnapshot = new ArrayList<>(latestChatEntities);
        final String ownId = normalizeAccountId(LoginStateManager.getInstance().getUID(this));
        final int visibleLimit = callListVisibleLimit;
        final int namesGeneration = contactNameGeneration;
        final int buildGeneration = ++callRowBuildGeneration;
        final long scheduledAt = SystemClock.elapsedRealtime();
        final android.content.Context appContext = getApplicationContext();
        Log.i(CALL_PAGINATION_TAG, "stage=render_scheduled generation=" + buildGeneration
                + " cached=" + callSnapshot.size() + " visibleLimit=" + visibleLimit);
        callRowExecutor.execute(() -> {
            long buildStartedAt = SystemClock.elapsedRealtime();
            List<CallLog> calls = new ArrayList<>();
            Map<String, ChatEntity> chatsById = new HashMap<>();
            for (ChatEntity chat : chatSnapshot) chatsById.put(chat.chatId, chat);
            DateFormat rowTime = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
            DateFormat fullTime = DateFormat.getDateTimeInstance(
                    DateFormat.LONG, DateFormat.SHORT, Locale.getDefault());
            int visibleCount = Math.min(visibleLimit, callSnapshot.size());
            int reusedCount = 0;
            int convertedCount = 0;
            Log.i(CALL_PAGINATION_TAG, "stage=render_prepare generation=" + buildGeneration
                    + " thread=" + Thread.currentThread().getName() + " cached="
                    + callSnapshot.size() + " visibleLimit=" + visibleLimit
                    + " targetRendered=" + visibleCount);
            for (int index = 0; index < visibleCount; index++) {
                CallEntity call = callSnapshot.get(index);
                String chatId = call.chatId == null ? "" : call.chatId;
                String callerId = call.callerId == null ? "" : call.callerId;
                String receiverId = call.receiverId == null ? "" : call.receiverId;
                String otherId = ownId.equals(normalizeAccountId(callerId))
                        ? receiverId : callerId;
                ChatEntity chat = chatsById.get(chatId);
                String rowKey = call.callId == null || call.callId.isEmpty()
                        ? (call.messageId == null ? "row:" + index : "message:" + call.messageId)
                        : "call:" + call.callId;
                int rowFingerprint = Objects.hash(call.messageId, chatId, callerId, receiverId,
                        call.mediaType, call.status, call.terminationReason, call.createdAt,
                        call.ringingAt, call.connectedAt, call.endedAt, call.durationSeconds,
                        call.conference, call.participantIdsJson, ownId, namesGeneration,
                        chat == null ? null : chat.contactName,
                        chat == null ? null : chat.localProfilePhotoPath,
                        chat != null && chat.isGroup);
                CachedCallRow cachedRow = callRowCache.get(rowKey);
                if (cachedRow != null && cachedRow.fingerprint == rowFingerprint) {
                    calls.add(cachedRow.call);
                    reusedCount++;
                    continue;
                }
                String contact = DeviceContactResolver.cachedNameOrPhone(otherId);
                if (call.conference) {
                    contact = chat != null && chat.isGroup && chat.contactName != null
                            && !chat.contactName.trim().isEmpty()
                            ? chat.contactName.trim() : "Conference call";
                    if (chat == null || !chat.isGroup) {
                        String names = conferenceParticipantNames(call.participantIdsJson, ownId);
                        if (!names.isEmpty()) contact += "\n" + names;
                    }
                }
                long endedAt = call.endedAt;
                boolean outgoing = ownId.equals(normalizeAccountId(callerId));
                boolean missed = call.connectedAt == null || call.connectedAt <= 0;
                Date date = new Date(endedAt > 0 ? endedAt : call.createdAt);
                String profilePath = chat == null ? null : chat.localProfilePhotoPath;
                if ((profilePath == null || profilePath.trim().isEmpty())
                        && !chatId.startsWith("grp_")) {
                    profilePath = ChatProfilePhotoStore.getLocalPath(appContext, otherId);
                }
                List<String> participantIds = call.conference
                        ? conferenceParticipantIds(call.participantIdsJson, ownId, otherId)
                        : java.util.Collections.emptyList();
                for (String participantId : participantIds)
                    ChatProfilePhotoStore.getLocalPath(appContext, participantId);
                CallLog prepared = new CallLog(chatId, call.callId, call.messageId, otherId,
                        contact, rowTime.format(date), fullTime.format(date),
                        formatCallDuration(call.durationSeconds),
                        "video".equals(call.mediaType), outgoing, missed, call.conference,
                        profilePath, participantIds);
                calls.add(prepared);
                callRowCache.put(rowKey, new CachedCallRow(rowFingerprint, prepared));
                convertedCount++;
            }
            long builtAt = SystemClock.elapsedRealtime();
            Log.i(CALL_PAGINATION_TAG, "stage=render_built generation=" + buildGeneration
                    + " rendered=" + calls.size() + " backgroundDurationMs="
                    + (builtAt - buildStartedAt) + " reused=" + reusedCount
                    + " converted=" + convertedCount);
            runOnUiThread(() -> {
                if (buildGeneration != callRowBuildGeneration || homeView == null) {
                    Log.i(CALL_PAGINATION_TAG, "stage=render_discarded generation="
                            + buildGeneration + " currentGeneration=" + callRowBuildGeneration);
                    return;
                }
                long submitStartedAt = SystemClock.elapsedRealtime();
                homeView.submitCalls(calls);
                Log.i(CALL_PAGINATION_TAG, "stage=render_submitted generation="
                        + buildGeneration + " rendered=" + calls.size() + " cached="
                        + callSnapshot.size() + " uiDurationMs="
                        + (SystemClock.elapsedRealtime() - submitStartedAt)
                        + " totalDurationMs="
                        + (SystemClock.elapsedRealtime() - scheduledAt));
                if (callPaginationRevealPending) {
                    callPaginationRevealPending = false;
                    homeView.setCallsPaginationLoading(false);
                    Log.i(CALL_PAGINATION_TAG,
                            "stage=cache_reveal_complete renderedLimit=" + visibleLimit);
                }
            });
        });
    }

    private static String jsonString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static String conferenceParticipantNames(String json, String ownId) {
        if (json == null || json.trim().isEmpty()) return "";
        try {
            JsonArray ids = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
            List<String> names = new ArrayList<>();
            for (JsonElement item : ids) {
                String id = normalizeAccountId(item.getAsString());
                if (!id.isEmpty() && !id.equals(ownId))
                    names.add(DeviceContactResolver.cachedNameOrPhone(id));
            }
            return android.text.TextUtils.join(", ", names);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static List<String> conferenceParticipantIds(String json, String ownId,
                                                          String fallbackOtherId) {
        java.util.LinkedHashSet<String> participants = new java.util.LinkedHashSet<>();
        if (json != null && !json.trim().isEmpty()) {
            try {
                JsonArray ids = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
                for (JsonElement item : ids) {
                    String id = normalizeAccountId(item.getAsString());
                    if (!id.isEmpty() && !id.equals(ownId)) participants.add(id);
                }
            } catch (RuntimeException ignored) {
                // The direct-call peer below remains a safe visual fallback.
            }
        }
        String fallback = normalizeAccountId(fallbackOtherId);
        if (!fallback.isEmpty() && !fallback.equals(ownId)) participants.add(fallback);
        return new ArrayList<>(participants);
    }

    private static long jsonLong(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? 0L : value.getAsLong();
    }

    private static String formatCallDuration(long seconds) {
        if (seconds <= 0)
            return "0 sec";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remaining = seconds % 60;
        if (hours > 0)
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, remaining);
        return String.format(Locale.getDefault(), "%d:%02d", minutes, remaining);
    }

    @Override
    public void onOpenCall(CallLog callLog) {
        Intent intent = new Intent(this, CallDetailActivity.class);
        intent.putExtra(CallDetailActivity.EXTRA_CHAT_ID, callLog.getChatId());
        intent.putExtra(CallDetailActivity.EXTRA_PHONE_NUMBER, callLog.getPhoneNumber());
        intent.putExtra(CallDetailActivity.EXTRA_CONTACT_NAME, callLog.getContactName());
        intent.putExtra(CallDetailActivity.EXTRA_CALLED_TIME, callLog.getCalledTime());
        intent.putExtra(CallDetailActivity.EXTRA_FULL_CALLED_DATE_TIME,
                callLog.getFullCalledDateTime());
        intent.putExtra(CallDetailActivity.EXTRA_DURATION, callLog.getDuration());
        intent.putExtra(CallDetailActivity.EXTRA_IS_VIDEO_CALL, callLog.isVideoCall());
        intent.putExtra(CallDetailActivity.EXTRA_IS_CONFERENCE, callLog.isConference());
        intent.putExtra(CallDetailActivity.EXTRA_IS_OUTGOING, callLog.isOutgoing());
        intent.putExtra(CallDetailActivity.EXTRA_IS_MISSED, callLog.isMissed());
        intent.putExtra(CallDetailActivity.EXTRA_PROFILE_PATH,
                callLog.getLocalProfilePhotoPath());
        intent.putStringArrayListExtra(CallDetailActivity.EXTRA_PARTICIPANT_IDS,
                new ArrayList<>(callLog.getParticipantIds()));
        startActivity(intent);
    }

    @Override
    public void onStartCall(CallLog callLog, boolean video) {
        openCall(callLog.getChatId(), callLog.getPhoneNumber(), "", video);
    }

    private void openCall(String chatId, String phoneNumber, String profilePath, boolean video) {
        Intent intent = new Intent(this, video ? VideoCallActivity.class : VoiceCallActivity.class);
        intent.putExtra(VoiceCallActivity.EXTRA_CALL_CHAT_ID, chatId);
        intent.putExtra(VoiceCallActivity.EXTRA_CALL_ID, java.util.UUID.randomUUID().toString());
        intent.putExtra(VoiceCallActivity.EXTRA_CALLER_ID, phoneNumber);
        intent.putExtra(VoiceCallActivity.EXTRA_PHONE_NUMBER,
                DeviceContactResolver.cachedNameOrPhone(phoneNumber));
        intent.putExtra(VoiceCallActivity.EXTRA_PROFILE_PATH, profilePath);
        startActivity(intent);
    }

    @Override
    public void onNewChat() {
        startActivity(new Intent(this, NewChatActivity.class));
    }

    @Override
    public void onNewGroup() {
        Intent intent = new Intent(this, NewChatActivity.class);
        intent.putExtra(NewChatActivity.EXTRA_CREATE_GROUP, true);
        startActivity(intent);
    }

    @Override
    public void onMakeCall() {
        Intent intent = new Intent(this, NewChatActivity.class);
        intent.putExtra(NewChatActivity.EXTRA_SHOW_CHAT_LIST, true);
        startActivity(intent);
    }

    @Override
    public void onOpenMenuDialog() {
        if (homeMenuDialog != null)
            homeMenuDialog.show();
    }

    @Override
    public void onBulkGroup(List<Chat> chats) {
        ArrayList<String> preselectedMembers = new ArrayList<>();
        if (chats != null) {
            for (Chat chat : chats) {
                if (chat == null || chat.getChatId() == null
                        || chat.getChatId().startsWith("grp_"))
                    continue;
                // Chat is a presentation model: getPhoneNumber() contains the resolved
                // contact label on the home screen. Group membership needs the canonical
                // account id kept by the Room entity instead.
                String memberId = "";
                for (ChatEntity entity : latestChatEntities) {
                    if (entity != null && chat.getChatId().equals(entity.chatId)) {
                        memberId = normalizeAccountId(entity.otherUserId);
                        break;
                    }
                }
                if (!memberId.isEmpty() && !preselectedMembers.contains(memberId))
                    preselectedMembers.add(memberId);
            }
        }
        if (preselectedMembers.isEmpty()) {
            Toast.makeText(this, "Select at least one direct chat for the group",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, NewChatActivity.class);
        intent.putExtra(NewChatActivity.EXTRA_CREATE_GROUP, true);
        intent.putStringArrayListExtra(
                NewChatActivity.EXTRA_PRESELECTED_MEMBER_IDS, preselectedMembers);
        startActivity(intent);
        if (homeView != null)
            homeView.clearChatSelection();
    }

    @Override
    public void onBulkPin(List<Chat> chats) {
        boolean allPinned = !chats.isEmpty();
        for (Chat chat : chats)
            allPinned &= chat.isPinned();
        applyBulkSetting(chats, "pin", allPinned ? 0 : 1,
                allPinned ? "Chats unpinned" : "Chats pinned", false);
    }

    @Override
    public void onBulkMute(List<Chat> chats) {
        boolean allMuted = !chats.isEmpty();
        for (Chat chat : chats)
            allMuted &= chat.isMuted();
        applyBulkSetting(chats, "mute", allMuted ? 0 : -1,
                allMuted ? "Chats unmuted" : "Chats muted", false);
    }

    @Override
    public void onBulkDelete(List<Chat> chats) {
        applyBulkSetting(chats, "delete", 1, "Chats deleted", true);
    }

    @Override
    public void onChatSelectionChanged(boolean selected) {
        getWindow().setStatusBarColor(selected
                ? SELECTION_STATUS_BAR_COLOR
                : APP_SYSTEM_BAR_COLOR);
    }

    @Override
    public void onCallSelectionChanged(boolean selected) {
        getWindow().setStatusBarColor(selected
                ? SELECTION_STATUS_BAR_COLOR : APP_SYSTEM_BAR_COLOR);
    }

    @Override
    public void onBulkDeleteCalls(List<CallLog> calls) {
        if (calls == null || calls.isEmpty()) return;
        List<String> callIds = new ArrayList<>();
        for (CallLog call : calls) {
            if (call != null && call.getCallId() != null && !call.getCallId().trim().isEmpty())
                callIds.add(call.getCallId());
        }
        if (callIds.isEmpty()) return;
        if (homeView != null) homeView.clearCallSelection();
        AppFunctionManager.getInstance().deleteCallLogs(callIds,
                new AppFunctionManager.Callback() {
                    @Override public void onSuccess(Object object) {
                        String uid = LoginStateManager.getInstance().getUID(HomeActivity.this);
                        if (callRepository != null) callRepository.deleteCachedCalls(uid, callIds);
                        if (object instanceof JsonObject && repository != null) {
                            JsonArray deleted = ((JsonObject) object).getAsJsonArray("deleted");
                            if (deleted != null) for (JsonElement element : deleted) {
                                if (!element.isJsonObject()) continue;
                                JsonElement message = element.getAsJsonObject().get("message");
                                if (message != null && message.isJsonObject())
                                    repository.cacheServerMessage(message.getAsJsonObject());
                            }
                        }
                        Toast.makeText(HomeActivity.this, "Call log deleted",
                                Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onError(String error) {
                        Toast.makeText(HomeActivity.this, "Call log could not be deleted",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void applyBulkSetting(List<Chat> chats, String setting, long value,
            String successMessage, boolean deleteLocal) {
        if (repository == null || chats == null || chats.isEmpty())
            return;
        if (homeView != null)
            homeView.clearChatSelection();
        List<String> chatIds = new ArrayList<>();
        for (Chat chat : chats) {
            if (chat.getChatId() != null && !chat.getChatId().trim().isEmpty()) {
                chatIds.add(chat.getChatId());
            }
        }
        repository.updateChatSettings(chatIds, setting, value,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        if (deleteLocal) {
                            for (String chatId : chatIds)
                                repository.deleteLocalChat(chatId);
                        }
                        finishBulkOperation(successMessage);
                    }

                    @Override
                    public void onError(String error) {
                        finishBulkOperation("Chats could not be updated");
                    }

                    private void finishBulkOperation(String message) {
                        Toast.makeText(HomeActivity.this, message, Toast.LENGTH_SHORT).show();
                        String uid = LoginStateManager.getInstance().getUID(HomeActivity.this);
                        if (uid != null)
                            repository.refreshChatList(normalizeAccountId(uid));
                    }
                });
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void submitChatsAsync(List<ChatEntity> entities) {
        if (chatRowExecutor.isShutdown()) return;
        final List<ChatEntity> snapshot = entities == null
                ? new ArrayList<>() : new ArrayList<>(entities);
        final int generation = ++chatRowBuildGeneration;
        final android.content.Context appContext = getApplicationContext();
        chatRowExecutor.execute(() -> {
            long startedAt = SystemClock.elapsedRealtime();
            String ownId = normalizeAccountId(
                    LoginStateManager.getInstance().getUID(appContext));
            List<Chat> rows = toChats(snapshot, ownId, appContext);
            long preparedAt = SystemClock.elapsedRealtime();
            Log.d(TESTING_TAG, "home_chat_render phase=conversion_finished generation="
                    + generation + " count=" + rows.size() + " backgroundDurationMs="
                    + (preparedAt - startedAt));
            runOnUiThread(() -> {
                if (generation != chatRowBuildGeneration || homeView == null) return;
                long submitStartedAt = SystemClock.elapsedRealtime();
                homeView.submitChats(rows);
                Log.d(TESTING_TAG, "home_chat_render phase=view_submitted generation="
                        + generation + " count=" + rows.size() + " uiDurationMs="
                        + (SystemClock.elapsedRealtime() - submitStartedAt));
            });
        });
    }

    private List<Chat> toChats(List<ChatEntity> entities, String ownId,
                               android.content.Context appContext) {
        List<Chat> chats = new ArrayList<>();
        if (entities == null)
            return chats;
        for (ChatEntity entity : entities) {
            String contact = entity.isGroup
                    ? safeGroupName(entity.contactName)
                    : DeviceContactResolver.cachedNameOrPhone(entity.otherUserId);
            String localPath = entity.localProfilePhotoPath;
            if (!entity.isGroup && (localPath == null || localPath.isEmpty())) {
                localPath = ChatProfilePhotoStore.getLocalPath(appContext, entity.otherUserId);
            }
            chats.add(new Chat(entity.chatId, contact, entity.profilePhotoUrl, localPath,
                    homeMessagePreview(entity, ownId), entity.lastMessageTime,
                    normalizeAccountId(entity.lastMessageSenderId).equals(
                            ownId),
                    entity.lastMessageDeliveredTime, entity.lastMessageReadTime,
                    entity.lastMessageStatus,
                    entity.lastMessageType, entity.lastMessageAttachmentName,
                    entity.unreadCount,
                    entity.pinned, entity.notificationMuted, entity.archived,
                    entity.isOnline, entity.lastSeen));
        }
        return chats;
    }

    private static String safeGroupName(String value) {
        return value == null || value.trim().isEmpty() ? "Group" : value.trim();
    }

    private String homeMessagePreview(ChatEntity entity, String ownNumber) {
        String text = entity.lastMessage;
        if (text == null)
            return null;
        String type = entity.lastMessageType == null ? "" : entity.lastMessageType;
        String action = "chat_report".equalsIgnoreCase(type) ? "reported"
                : "chat_block".equalsIgnoreCase(type) ? "blocked"
                        : "chat_unblock".equalsIgnoreCase(type) ? "unblocked" : "";
        if (action.isEmpty())
            return text;
        String normalizedText = text.trim();
        String[] participants = normalizedText.split(" " + action + " ", 2);
        if (!ownNumber.isEmpty() && participants.length == 2)
            return (ownNumber.equals(normalizeAccountId(participants[0])) ? "You" : participants[0])
                    + " " + action + " "
                    + (ownNumber.equals(normalizeAccountId(participants[1]))
                            ? "You"
                            : participants[1]);
        return normalizedText;
    }

    private static String normalizeAccountId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("<plus>")) {
            return normalized.substring("<plus>".length());
        }
        return normalized.startsWith("+") ? normalized.substring(1) : normalized;
    }

    private static final class CachedCallRow {
        final int fingerprint;
        final CallLog call;

        CachedCallRow(int fingerprint, CallLog call) {
            this.fingerprint = fingerprint;
            this.call = call;
        }
    }

    @Override
    protected void onDestroy() {
        callRowBuildGeneration++;
        chatRowBuildGeneration++;
        callRowExecutor.shutdownNow();
        chatRowExecutor.shutdownNow();
        callRowCache.clear();
        if (homeView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(homeView, null);
            homeView.release();
            homeView = null;
        }
        if (homeMenuDialog != null) {
            homeMenuDialog.release();
            homeMenuDialog = null;
        }
        super.onDestroy();
    }
}
