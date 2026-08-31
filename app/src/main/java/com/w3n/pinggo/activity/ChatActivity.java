package com.w3n.pinggo.activity;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LiveData;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.data.local.MessageEntity;
import com.w3n.pinggo.data.local.PresenceEntity;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.views.chat.ChatView;
import com.w3n.pinggo.views.chat.ChatPerformanceProfiler;
import com.w3n.pinggo.views.chat.ConversationMenuDialogView;
import com.w3n.pinggo.call.ActiveCallRegistry;
import com.w3n.pinggo.views.common.NativePromptDialogView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.File;
import java.io.FileNotFoundException;

public class ChatActivity extends AppCompatActivity implements ChatView.Listener {
  private static final String TESTING_TAG = "PARVEZ_TESTING";
  private static final int SELECTION_STATUS_BAR_COLOR = 0xFFE9EDF0;
  private static final int DEFAULT_STATUS_BAR_COLOR = 0xFFFFFFFF;
  private static final int INITIAL_RENDER_WINDOW_SIZE = 15;
  private static final int PROGRESSIVE_RENDER_INCREMENT = ChatRepository.MESSAGE_PAGE_SIZE;
  private static final int MESSAGE_WINDOW_INCREMENT = ChatRepository.MESSAGE_PAGE_SIZE;
  private static final int ATTACHMENT_AVAILABLE = 0;
  private static final int ATTACHMENT_DOWNLOAD_REQUIRED = 1;
  private static final int ATTACHMENT_DOWNLOADING = 2;
  private MessageEntity pendingDownloadMessage;
  public static final String EXTRA_CHAT_NAME = "com.w3n.pinggo.EXTRA_CHAT_NAME",
      EXTRA_CHAT_ID = "com.w3n.pinggo.EXTRA_CHAT_ID",
      EXTRA_PROFILE_PHOTO_URL = "com.w3n.pinggo.EXTRA_PROFILE_PHOTO_URL",
      EXTRA_LOCAL_PROFILE_PHOTO_PATH = "com.w3n.pinggo.EXTRA_LOCAL_PROFILE_PHOTO_PATH",
      EXTRA_OPEN_REQUEST_NANOS = "com.w3n.pinggo.EXTRA_OPEN_REQUEST_NANOS";
  private final Handler typingHandler = new Handler(Looper.getMainLooper());
  private long lastSocketErrorToastAt;
  private final ActivityResultLauncher<String[]> attachmentPicker =
      registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onAttachmentPicked);
  private final ActivityResultLauncher<String[]> locationPermission =
      registerForActivityResult(
          new ActivityResultContracts.RequestMultiplePermissions(), this::onLocationPermission);
  private final ActivityResultLauncher<String> storagePermission =
      registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        if (granted && pendingDownloadMessage != null) startAttachmentDownload(pendingDownloadMessage);
        else if (!granted) Toast.makeText(this, "Storage permission is required.", Toast.LENGTH_SHORT).show();
        pendingDownloadMessage = null;
      });
  private final Set<String> pendingSeen = new HashSet<>();
  private final Map<String, Integer> attachmentStates = new ConcurrentHashMap<>();
  private final ExecutorService messagePreparationExecutor =
      Executors.newSingleThreadExecutor();
  private ChatView chatView;
  private ChatPerformanceProfiler profiler;
  private ChatRepository repository;
  private NativePromptDialogView promptDialog;
  private ConversationMenuDialogView conversationMenuDialog;
  private String chatId, currentUser, receiverId, replyingId, editingId;
  private String profilePhotoPath;
  private String selectedAttachmentType;
  private Uri selectedAttachmentUri;
  private boolean typingStarted, peerTyping, locationPending, attachmentSending;
  private LocationManager locationManager;
  private LocationListener locationListener;
  private PresenceEntity latestPresence;
  private List<MessageEntity> latestMessages = Collections.emptyList();
  private List<MessageEntity> availableMessages = Collections.emptyList();
  private LiveData<List<MessageEntity>> messageSource;
  // Room can deliver the whole network page immediately. The custom list receives only one
  // viewport first, then progressively larger suffixes so its synchronous layout never blocks
  // the first useful frame on all 50 variable-height rows.
  private int messageLimit = ChatRepository.MESSAGE_PAGE_SIZE;
  private int renderedMessageCount;
  private int messagePreparationGeneration;
  private boolean messagePreparationRunning;
  private boolean localPageLoading;
  private boolean localHasMore = true;
  private boolean messagePageLoading;
  private boolean messageNetworkHasMore = true;
  private boolean firstMessagePageLoaded;
  private boolean localExpansionRequested;
  private int localExpansionPreviousCount;
  private String localExpansionPreviousOldestId;
  private String nextMessageCursor;
  private final Runnable refreshTyping = new Runnable() {
    @Override public void run() {
      if (!typingStarted || repository == null || receiverId.isEmpty()) return;
      repository.sendTyping(chatId, receiverId, true);
      typingHandler.postDelayed(this, 25_000L);
    }
  };
  private final Runnable stopTyping =
      () -> {
        typingHandler.removeCallbacks(refreshTyping);
        if (typingStarted && repository != null && !receiverId.isEmpty()) {
          repository.sendTyping(chatId, receiverId, false);
          typingStarted = false;
        }
      };
  private final Runnable locationTimeout =
      () -> {
        if (!locationPending) return;
        locationPending = false;
        removeLocationUpdates();
        Toast.makeText(this, "Unable to get current location.", Toast.LENGTH_SHORT).show();
      };

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    long createStartedNanos = SystemClock.elapsedRealtimeNanos();
    EdgeToEdge.enable(this);
    String name = getIntent().getStringExtra(EXTRA_CHAT_NAME);
    if (name == null || name.trim().isEmpty()) name = "Chat";
    chatId = getIntent().getStringExtra(EXTRA_CHAT_ID);
    profiler = new ChatPerformanceProfiler(
        chatId, getIntent().getLongExtra(EXTRA_OPEN_REQUEST_NANOS, createStartedNanos));
    profiler.attach(getWindow());
    currentUser = normalize(LoginStateManager.getInstance().getUID(this));
    receiverId = receiver();
    profilePhotoPath = getIntent().getStringExtra(EXTRA_LOCAL_PROFILE_PHOTO_PATH);
    chatView =
        new ChatView(
            this,
            name,
            currentUser,
            profilePhotoPath,
            this,
            profiler);
    setContentView(chatView);
    conversationMenuDialog =
        new ConversationMenuDialogView(
            this,
            option -> { });
    ((ViewGroup) findViewById(android.R.id.content)).addView(
        conversationMenuDialog,
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    getOnBackPressedDispatcher().addCallback(
        this,
        new OnBackPressedCallback(true) {
          @Override
          public void handleOnBackPressed() {
            if (chatView != null && chatView.clearMessageSelection()) return;
            if (conversationMenuDialog != null
                && conversationMenuDialog.dismissIfShowing()) return;
            if (promptDialog != null) {
              removePrompt();
              return;
            }
            setEnabled(false);
            getOnBackPressedDispatcher().onBackPressed();
            setEnabled(true);
          }
        });
    ViewCompat.setOnApplyWindowInsetsListener(
        chatView,
        (v, i) -> {
          Insets bars = i.getInsets(WindowInsetsCompat.Type.systemBars()),
              ime = i.getInsets(WindowInsetsCompat.Type.ime());
          chatView.setInsets(
              bars.top, bars.bottom, ime.bottom, i.isVisible(WindowInsetsCompat.Type.ime()));
          return i;
        });
    ViewCompat.requestApplyInsets(chatView);
    repository = ChatRepository.getInstance(this);
    restoreMessageSessionState();
    repository.setEventListener(
        new ChatRepository.EventListener() {
          @Override
          public void onTyping(String eventChat, String user, boolean typing) {
            if (chatId != null && chatId.equals(eventChat)) {
              peerTyping = typing;
              if (typing) chatView.setPresence("typing...");
              else renderPresence(latestPresence);
            }
          }

          @Override
          public void onSocketError(String error) {
            long now = System.currentTimeMillis();
            if (now - lastSocketErrorToastAt < 15000L) return;
            lastSocketErrorToastAt = now;
            String message = error != null && error.contains("Control frames must be final")
                ? "Chat connection interrupted. Reconnecting…"
                : error;
            Toast.makeText(ChatActivity.this,
                message == null || message.trim().isEmpty()
                    ? "Chat connection interrupted." : message,
                Toast.LENGTH_SHORT).show();
          }
        });
    observe();
    profiler.activityCreated(createStartedNanos);
  }

  private void observe() {
    if (chatId == null || chatId.trim().isEmpty()) {
      chatView.showStatus("Chat id missing.");
      return;
    }
    if (currentUser.isEmpty()) {
      chatView.showStatus("Login data missing.");
      return;
    }
    observeMessageWindow();
    repository.observeTransfers(chatId).observe(this, values -> {
      Map<String, Integer> nextStates = new HashMap<>();
      if (values != null) for (com.w3n.pinggo.data.local.TransferEntity transfer : values) {
        if (!"download".equals(transfer.direction) || transfer.attachmentId == null) continue;
        if ("queued".equals(transfer.status) || "downloading".equals(transfer.status)
            || "retrying".equals(transfer.status)) {
          nextStates.put(transfer.attachmentId, ATTACHMENT_DOWNLOADING);
        } else if (transfer.localUri != null && !transfer.localUri.isEmpty()) {
          nextStates.put(transfer.attachmentId, ATTACHMENT_AVAILABLE);
        }
      }
      if (!attachmentStates.equals(nextStates)) {
        attachmentStates.clear();
        attachmentStates.putAll(nextStates);
        refreshAttachmentRows();
      }
    });
    if (!receiverId.isEmpty())
      repository.observePresence(receiverId).observe(this, this::renderPresence);
    if (!firstMessagePageLoaded) {
      loadMessagePage(null);
    } else {
      Log.d(TESTING_TAG, "message_list source=session phase=route_skipped chatId=" + chatId
          + " limit=" + messageLimit
          + " hasMore=" + messageNetworkHasMore
          + " hasNextCursor=" + (nextMessageCursor != null
          && !nextMessageCursor.isEmpty()));
    }
    if (!receiverId.isEmpty()) repository.syncPresence(Collections.singletonList(receiverId));
  }

  private void observeMessageWindow() {
    if (messageSource != null) messageSource.removeObservers(this);
    localPageLoading = true;
    Log.d(TESTING_TAG, "message_list source=room_cache phase=observe_start chatId="
        + chatId + " limit=" + messageLimit);
    messageSource = repository.observeMessages(chatId, messageLimit);
    messageSource.observe(this, this::messages);
    updateOlderMessagesState();
  }

  private void loadMessagePage(String cursor) {
    if (messagePageLoading || !messageNetworkHasMore) return;
    messagePageLoading = true;
    Log.d(TESTING_TAG, "message_list source=routes phase=activity_request chatId=" + chatId
        + " page=" + (cursor == null || cursor.isEmpty() ? "initial" : "pagination")
        + " hasCursor=" + (cursor != null && !cursor.isEmpty()));
    updateOlderMessagesState();
    repository.hydrateChatPage(
        chatId,
        LoginStateManager.getInstance().getUID(this),
        cursor,
        new ChatRepository.MessagePageCallback() {
          @Override
          public void onLoaded(String newCursor, boolean hasMore, int loadedCount) {
            firstMessagePageLoaded = true;
            nextMessageCursor = newCursor;
            messageNetworkHasMore = hasMore && newCursor != null && !newCursor.isEmpty();
            messagePageLoading = false;
            saveMessageSessionState();
            Log.d(TESTING_TAG, "message_list source=routes phase=activity_loaded chatId="
                + chatId + " loaded=" + loadedCount + " hasMore=" + hasMore
                + " hasNextCursor=" + (newCursor != null && !newCursor.isEmpty()));
            updateOlderMessagesState();
          }

          @Override
          public void onError(String message) {
            messagePageLoading = false;
            Log.e(TESTING_TAG, "message_list source=routes phase=activity_error chatId="
                + chatId + " error=" + message);
            updateOlderMessagesState();
          }
        });
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (repository != null && chatId != null) repository.setActiveChat(chatId);
  }

  @Override
  protected void onStop() {
    saveMessageSessionState();
    if (repository != null && chatId != null) repository.clearActiveChat(chatId);
    super.onStop();
  }

  public void setFloatingCallInset(int insetPx) {
    if (chatView != null) chatView.setFloatingCallInset(insetPx);
  }

  private void messages(List<MessageEntity> values) {
    availableMessages = values == null ? Collections.emptyList() : values;
    localPageLoading = false;
    localHasMore = availableMessages.size() >= messageLimit;
    boolean completedLocalExpansion = localExpansionRequested;
    boolean revealedOlderCachedMessages = completedLocalExpansion
        && availableMessages.size() > localExpansionPreviousCount
        && !sameMessageId(localExpansionPreviousOldestId,
            availableMessages.isEmpty() ? null : availableMessages.get(0).messageId);
    localExpansionRequested = false;
    if (completedLocalExpansion) {
      Log.d(TESTING_TAG, "message_list source=pagination phase=local_expansion_complete chatId="
          + chatId + " previousCount=" + localExpansionPreviousCount
          + " currentCount=" + availableMessages.size()
          + " revealedOlder=" + revealedOlderCachedMessages);
    }
    long oldestTime = availableMessages.isEmpty() ? 0L : availableMessages.get(0).sentTime;
    long newestTime = availableMessages.isEmpty()
        ? 0L : availableMessages.get(availableMessages.size() - 1).sentTime;
    Log.d(TESTING_TAG, "message_list source=room_cache phase=loaded chatId=" + chatId
        + " count=" + availableMessages.size() + " limit=" + messageLimit
        + " localHasMore=" + localHasMore + " oldestTime=" + oldestTime
        + " newestTime=" + newestTime);
    if (availableMessages.isEmpty()) renderedMessageCount = 0;
    else if (renderedMessageCount == 0) {
      renderedMessageCount = Math.min(INITIAL_RENDER_WINDOW_SIZE, availableMessages.size());
    } else {
      renderedMessageCount = Math.min(renderedMessageCount, availableMessages.size());
    }
    renderAvailableMessages("room");
    scheduleProgressiveRender();
    if (completedLocalExpansion && !revealedOlderCachedMessages
        && !messagePageLoading && messageNetworkHasMore
        && (!firstMessagePageLoaded || nextMessageCursor != null)) {
      loadMessagePage(firstMessagePageLoaded ? nextMessageCursor : null);
    } else {
      if (revealedOlderCachedMessages) saveMessageSessionState();
      updateOlderMessagesState();
    }
  }

  private void renderAvailableMessages(String phase) {
    long operationStartedNanos = SystemClock.elapsedRealtimeNanos();
    int firstRendered = Math.max(0, availableMessages.size() - renderedMessageCount);
    latestMessages = availableMessages.isEmpty()
        ? Collections.emptyList()
        : new ArrayList<>(availableMessages.subList(firstRendered, availableMessages.size()));
    long renderStarted = System.nanoTime();
    boolean changed = chatView.submitMessages(latestMessages);
    profiler.contentSubmitted(latestMessages.size());
    long renderDurationMs = (System.nanoTime() - renderStarted) / 1_000_000L;
    Log.d(TESTING_TAG, "message_list source=render phase=" + phase + "_complete chatId="
        + chatId + " available=" + availableMessages.size()
        + " rendered=" + latestMessages.size() + " changed=" + changed
        + " durationMs=" + renderDurationMs);
    profiler.operation(
        "render_submit_" + phase,
        operationStartedNanos,
        "available=" + availableMessages.size()
            + " rendered=" + latestMessages.size()
            + " changed=" + changed);
    markRenderedMessagesSeen();
  }

  private void scheduleProgressiveRender() {
    if (chatView == null || renderedMessageCount >= availableMessages.size()) {
      messagePreparationRunning = false;
      updateOlderMessagesState();
      return;
    }
    int generation = ++messagePreparationGeneration;
    List<MessageEntity> snapshot = new ArrayList<>(availableMessages);
    int targetRenderedCount = Math.min(
        snapshot.size(), renderedMessageCount + PROGRESSIVE_RENDER_INCREMENT);
    int firstToPrepare = Math.max(0, snapshot.size() - targetRenderedCount);
    int preparedEnd = Math.max(firstToPrepare, snapshot.size() - renderedMessageCount);
    List<MessageEntity> preparationChunk = new ArrayList<>(
        snapshot.subList(firstToPrepare, preparedEnd));
    float availableWidth = chatView.getMessageLayoutWidth();
    messagePreparationRunning = true;
    updateOlderMessagesState();
    messagePreparationExecutor.execute(() -> {
      long preparationStartedNanos = SystemClock.elapsedRealtimeNanos();
      ChatView view = chatView;
      if (view == null) return;
      view.prepareMessageMetrics(preparationChunk, availableWidth);
      profiler.operation(
          "prepare_chunk",
          preparationStartedNanos,
          "count=" + preparationChunk.size() + " targetRendered=" + targetRenderedCount);
      typingHandler.post(() -> {
        if (chatView == null || generation != messagePreparationGeneration) return;
        availableMessages = snapshot;
        renderedMessageCount = targetRenderedCount;
        messagePreparationRunning = renderedMessageCount < snapshot.size();
        renderAvailableMessages("background");
        updateOlderMessagesState();
        if (messagePreparationRunning) scheduleProgressiveRender();
      });
    });
  }

  private boolean isProgressiveRendering() {
    return messagePreparationRunning || renderedMessageCount < availableMessages.size();
  }

  private void markRenderedMessagesSeen() {
    List<String> unseen = new ArrayList<>();
    for (MessageEntity m : latestMessages) {
      boolean incoming = !currentUser.equals(normalize(m.senderId));
      if (incoming && m.readTime == null && !pendingSeen.contains(m.messageId))
        unseen.add(m.messageId);
      else if (m.readTime != null) pendingSeen.remove(m.messageId);
    }
    if (!unseen.isEmpty()) {
      pendingSeen.addAll(unseen);
      repository.markSeen(chatId, unseen);
    }
  }

  private void updateOlderMessagesState() {
    if (chatView == null) return;
    chatView.setOlderMessagesState(
        localPageLoading || messagePageLoading,
        !isProgressiveRendering() && (localHasMore || messageNetworkHasMore));
  }

  @Override
  public void onLoadOlderMessages() {
    Log.d(TESTING_TAG, "message_list source=pagination phase=top_reached chatId=" + chatId
        + " currentCount=" + latestMessages.size() + " currentLimit=" + messageLimit
        + " localLoading=" + localPageLoading + " localHasMore=" + localHasMore
        + " routeLoading=" + messagePageLoading
        + " routeHasMore=" + messageNetworkHasMore
        + " hasNextCursor=" + (nextMessageCursor != null && !nextMessageCursor.isEmpty()));
    if (isProgressiveRendering() || localPageLoading || messagePageLoading
        || (!localHasMore && !messageNetworkHasMore)) return;
    localExpansionRequested = true;
    localExpansionPreviousCount = availableMessages.size();
    localExpansionPreviousOldestId = availableMessages.isEmpty()
        ? null : availableMessages.get(0).messageId;
    messageLimit += MESSAGE_WINDOW_INCREMENT;
    observeMessageWindow();
  }

  private boolean sameMessageId(String first, String second) {
    return first == null ? second == null : first.equals(second);
  }

  private void restoreMessageSessionState() {
    if (repository == null || chatId == null || chatId.trim().isEmpty()) return;
    ChatRepository.MessageSessionState state = repository.getMessageSessionState(chatId);
    if (state == null) {
      Log.d(TESTING_TAG, "message_cache source=session phase=miss chatId=" + chatId);
      return;
    }
    // Reopen with one normal network page available to the progressive renderer. A previously
    // expanded history window is intentionally not submitted to ComponentList in one operation.
    messageLimit = ChatRepository.MESSAGE_PAGE_SIZE;
    firstMessagePageLoaded = state.isFirstPageLoaded();
    nextMessageCursor = state.getNextCursor();
    messageNetworkHasMore = state.hasMoreOnNetwork();
    Log.d(TESTING_TAG, "message_cache source=session phase=restored chatId=" + chatId
        + " limit=" + messageLimit
        + " firstPageLoaded=" + firstMessagePageLoaded
        + " hasMore=" + messageNetworkHasMore
        + " hasNextCursor=" + (nextMessageCursor != null && !nextMessageCursor.isEmpty()));
  }

  private void saveMessageSessionState() {
    if (repository == null || !firstMessagePageLoaded
        || chatId == null || chatId.trim().isEmpty()) return;
    repository.saveMessageSessionState(chatId, messageLimit, true,
        nextMessageCursor, messageNetworkHasMore);
  }

  private void renderPresence(PresenceEntity p) {
    latestPresence = p;
    if (peerTyping) return;
    if (p == null) {
      chatView.setPresence("");
      return;
    }
    if (p.isOnline) {
      chatView.setPresence("online");
      return;
    }
    chatView.setPresence(
        p.lastSeen != null && p.lastSeen > 0
            ? "last seen "
                + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(p.lastSeen))
            : "");
  }

  @Override
  public void onSend() {
    String text = chatView.getDraft();
    if (chatId == null || chatId.isEmpty() || receiverId.isEmpty()) {
      Toast.makeText(this, "Chat information missing.", Toast.LENGTH_SHORT).show();
      return;
    }
    if (editingId != null) {
      if (text.isEmpty()) {
        Toast.makeText(this, "Message required.", Toast.LENGTH_SHORT).show();
        return;
      }
      repository.editMessage(chatId, editingId, text);
      editingId = null;
    } else if (selectedAttachmentUri != null) {
      if (attachmentSending) return;
      attachmentSending = true;
      chatView.clearAttachmentPreview();
      repository.uploadAndSendAttachment(
          chatId,
          receiverId,
          text,
          replyingId,
          selectedAttachmentUri,
          selectedAttachmentType.toLowerCase(),
          new ChatRepository.AttachmentCallback() {
            @Override
            public void onSent() {
              attachmentSending = false;
              selectedAttachmentUri = null;
              selectedAttachmentType = null;
              finishComposeAction();
            }

            @Override
            public void onError(String message) {
              attachmentSending = false;
              selectedAttachmentUri = null;
              selectedAttachmentType = null;
              finishComposeAction();
              Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
            }
          });
      return;
    } else {
      if (text.isEmpty()) {
        Toast.makeText(this, "Message required.", Toast.LENGTH_SHORT).show();
        return;
      }
      repository.sendMessage(chatId, receiverId, text, replyingId);
    }
    finishComposeAction();
  }

  @Override
  public void onVideoCall() {
    openCall(VideoCallActivity.class);
  }

  @Override
  public void onVoiceCall() {
    openCall(VoiceCallActivity.class);
  }

  private void openCall(Class<? extends AppCompatActivity> activityClass) {
    String requestedType = activityClass == VideoCallActivity.class
        ? ActiveCallRegistry.TYPE_VIDEO : ActiveCallRegistry.TYPE_VOICE;
    ActiveCallRegistry registry = ActiveCallRegistry.getInstance();
    if (registry.matches(chatId, requestedType)) {
      registry.openExisting(this);
      return;
    }
    if (registry.hasActiveCall()) {
      String activeType = ActiveCallRegistry.TYPE_VIDEO.equals(registry.getType()) ? "Video" : "Voice";
      String state = registry.isConnected() ? " connected" : " active";
      Toast.makeText(this, activeType + " call is already" + state + ".", Toast.LENGTH_SHORT).show();
      return;
    }
    Intent intent = new Intent(this, activityClass);
    intent.putExtra(VoiceCallActivity.EXTRA_CALL_CHAT_ID, chatId);
    intent.putExtra(VoiceCallActivity.EXTRA_CALL_ID, UUID.randomUUID().toString());
    intent.putExtra(VoiceCallActivity.EXTRA_CALLER_ID, receiverId);
    intent.putExtra(VoiceCallActivity.EXTRA_PHONE_NUMBER,
        receiverId.isEmpty() ? "Unknown" : "+" + receiverId);
    intent.putExtra(VoiceCallActivity.EXTRA_PROFILE_PATH, profilePhotoPath);
    startActivity(intent);
  }

  private void finishComposeAction() {
    stopTyping.run();
    replyingId = null;
    chatView.clearReply();
    chatView.clearDraft();
  }

  @Override
  public void onAttachmentSelected(String type) {
    if ("Image".equals(type)) {
      selectedAttachmentType = type;
      attachmentPicker.launch(new String[] {"image/*"});
    } else if ("Video".equals(type)) {
      selectedAttachmentType = type;
      attachmentPicker.launch(new String[] {"video/*"});
    } else if ("File".equals(type)) {
      selectedAttachmentType = type;
      attachmentPicker.launch(new String[] {"*/*"});
    } else if ("Location".equals(type)) {
      requestLocationPermission();
    }
  }

  private void onAttachmentPicked(Uri uri) {
    if (uri == null) {
      return;
    }
    selectedAttachmentUri = uri;
    try {
      getContentResolver().takePersistableUriPermission(
          uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
    } catch (SecurityException error) {
    }
    chatView.showAttachmentPreview(selectedAttachmentType, selectedFileName(uri));
    Toast.makeText(this, selectedAttachmentType + " selected.", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onAttachmentPreviewRemoved() {
    selectedAttachmentUri = null;
    selectedAttachmentType = null;
  }

  private String selectedFileName(Uri uri) {
    try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (column >= 0) return cursor.getString(column);
      }
    } catch (RuntimeException error) {
    }
    return "Attachment";
  }

  private void requestLocationPermission() {
    boolean fine =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
    boolean coarse =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
    if (fine || coarse) {
      requestCurrentLocation();
      return;
    }
    locationPermission.launch(
        new String[] {
          Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        });
  }

  private void onLocationPermission(Map<String, Boolean> result) {
    boolean granted =
        Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
            || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
    if (granted) requestCurrentLocation();
    else Toast.makeText(this, "Location permission is required.", Toast.LENGTH_SHORT).show();
  }

  private void requestCurrentLocation() {
    locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
    String provider = chooseLocationProvider();
    if (provider == null) {
      Toast.makeText(this, "Turn on location services and try again.", Toast.LENGTH_SHORT).show();
      return;
    }
    locationPending = true;
    typingHandler.removeCallbacks(locationTimeout);
    typingHandler.postDelayed(locationTimeout, 15_000);
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        locationManager.getCurrentLocation(provider, null, getMainExecutor(), this::onLocationReady);
      } else {
        locationListener =
            new LocationListener() {
              @Override
              public void onLocationChanged(Location location) {
                onLocationReady(location);
              }

              @Override
              public void onStatusChanged(String provider, int status, Bundle extras) {}

              @Override
              public void onProviderEnabled(String provider) {}

              @Override
              public void onProviderDisabled(String provider) {}
            };
        locationManager.requestSingleUpdate(provider, locationListener, Looper.getMainLooper());
      }
    } catch (SecurityException error) {
      locationPending = false;
      typingHandler.removeCallbacks(locationTimeout);
      Toast.makeText(this, "Location permission is required.", Toast.LENGTH_SHORT).show();
    }
  }

  private String chooseLocationProvider() {
    if (locationManager == null) return null;
    boolean fine =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
    if (fine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
      return LocationManager.GPS_PROVIDER;
    }
    if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
      return LocationManager.NETWORK_PROVIDER;
    }
    return null;
  }

  private void onLocationReady(Location location) {
    if (!locationPending) return;
    locationPending = false;
    typingHandler.removeCallbacks(locationTimeout);
    removeLocationUpdates();
    if (location == null) {
      Toast.makeText(this, "Unable to get current location.", Toast.LENGTH_SHORT).show();
      return;
    }
    if (chatId == null || chatId.isEmpty() || receiverId.isEmpty()) {
      Toast.makeText(this, "Chat information missing.", Toast.LENGTH_SHORT).show();
      return;
    }
    repository.sendLocation(
        chatId,
        receiverId,
        location.getLatitude(),
        location.getLongitude(),
        location.getAccuracy(),
        replyingId);
    finishComposeAction();
    Toast.makeText(this, "Current location sent.", Toast.LENGTH_SHORT).show();
  }

  private void removeLocationUpdates() {
    if (locationManager == null || locationListener == null) return;
    try {
      locationManager.removeUpdates(locationListener);
    } catch (SecurityException ignored) {
      // Permission may have been revoked while the request was active.
    }
    locationListener = null;
  }

  @Override
  public void onTypingChanged(String text) {
    if (repository == null || chatId == null || receiverId.isEmpty()) return;
    typingHandler.removeCallbacks(stopTyping);
    if (text != null && !text.isEmpty()) {
      if (!typingStarted) {
        repository.sendTyping(chatId, receiverId, true);
        typingStarted = true;
        typingHandler.removeCallbacks(refreshTyping);
        typingHandler.postDelayed(refreshTyping, 25_000L);
      }
      typingHandler.postDelayed(stopTyping, 2000);
    } else stopTyping.run();
  }

  @Override
  public void onMessageClick(MessageEntity message) {
    if ("failed".equals(message.status)) {
      repository.resendMessage(message);
      return;
    }
    String type = message.messageType == null ? "text" : message.messageType;
    if ("location".equals(type) && message.latitude != null && message.longitude != null) {
      String coordinates = message.latitude + "," + message.longitude;
      openUri(Uri.parse("geo:" + coordinates + "?q=" + Uri.encode(coordinates)), null);
      return;
    }
    if (!("image".equals(type) || "video".equals(type) || "file".equals(type))) return;
    boolean own = currentUser.equals(normalize(message.senderId));
    if (own) {
      Uri local = message.attachmentLocalUri == null ? null : Uri.parse(message.attachmentLocalUri);
      if (local == null || !canRead(local)) {
        Toast.makeText(this, "File no longer available.", Toast.LENGTH_SHORT).show();
        return;
      }
      openUri(local, message.attachmentMimeType);
      return;
    }
    if (message.attachmentLocalUri != null && canRead(Uri.parse(message.attachmentLocalUri))) {
      openLocalAttachment(Uri.parse(message.attachmentLocalUri), message.attachmentMimeType);
      return;
    }
    if (attachmentStates.getOrDefault(
        attachmentKey(message), ATTACHMENT_DOWNLOAD_REQUIRED) == ATTACHMENT_DOWNLOADING) return;
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
        && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
      pendingDownloadMessage = message;
      storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
      return;
    }
    startAttachmentDownload(message);
  }

  @Override
  public void onReplySelected(MessageEntity message) {
    if (!hasServerMessageId(message)) {
      Toast.makeText(this, "Wait until the message is sent.", Toast.LENGTH_SHORT).show();
      return;
    }
    replyingId = message.messageId;
    editingId = null;
    chatView.clearMessageSelection();
    chatView.showReply(message.text);
  }

  @Override
  public void onCopySelected(List<MessageEntity> messages) {
    StringBuilder copied = new StringBuilder();
    for (MessageEntity message : messages) {
      String value = copyValue(message);
      if (value.isEmpty()) continue;
      if (copied.length() > 0) copied.append('\n');
      copied.append(value);
    }
    if (copied.length() == 0) {
      Toast.makeText(this, "Nothing to copy.", Toast.LENGTH_SHORT).show();
      return;
    }
    ClipboardManager clipboard =
        (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("PingGo messages", copied.toString()));
    chatView.clearMessageSelection();
    Toast.makeText(this, messages.size() == 1 ? "Message copied." : "Messages copied.",
        Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onForwardSelected(List<MessageEntity> messages) {
    ArrayList<String> messageIds = serverMessageIds(messages, true);
    if (messageIds.isEmpty()) {
      Toast.makeText(this, "Wait until the selected messages are sent.", Toast.LENGTH_SHORT).show();
      return;
    }
    Intent intent = new Intent(this, NewChatActivity.class);
    intent.putExtra(NewChatActivity.EXTRA_FORWARD_SOURCE_CHAT_ID, chatId);
    intent.putStringArrayListExtra(NewChatActivity.EXTRA_FORWARD_MESSAGE_IDS, messageIds);
    chatView.clearMessageSelection();
    startActivity(intent);
  }

  @Override
  public void onPinSelected(List<MessageEntity> messages) {
    ArrayList<String> messageIds = serverMessageIds(messages);
    if (messageIds.isEmpty()) {
      Toast.makeText(this, "Wait until the selected messages are sent.", Toast.LENGTH_SHORT).show();
      return;
    }
    repository.pinMessages(chatId, messageIds);
    chatView.clearMessageSelection();
    Toast.makeText(this, messageIds.size() == 1 ? "Message pinned." : "Messages pinned.",
        Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onUnpinSelected(List<MessageEntity> messages) {
    ArrayList<String> messageIds = serverMessageIds(messages);
    if (messageIds.isEmpty()) {
      Toast.makeText(this, "Wait until the selected messages are sent.", Toast.LENGTH_SHORT).show();
      return;
    }
    repository.unpinMessages(chatId, messageIds);
    chatView.clearMessageSelection();
    Toast.makeText(this, messageIds.size() == 1 ? "Message unpinned." : "Messages unpinned.",
        Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onDeleteSelected(List<MessageEntity> messages) {
    List<String> own = new ArrayList<>(), opponent = new ArrayList<>();
    for (MessageEntity message : messages) {
      if (!hasServerMessageId(message)) continue;
      if (currentUser.equals(normalize(message.senderId))) own.add(message.messageId);
      else opponent.add(message.messageId);
    }
    if (!own.isEmpty()) repository.deleteOwnMessages(chatId, own);
    if (!opponent.isEmpty()) repository.deleteOpponentMessages(chatId, opponent);
    chatView.clearMessageSelection();
    if (own.isEmpty() && opponent.isEmpty()) {
      Toast.makeText(this, "Wait until the selected messages are sent.", Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onMessageSelectionChanged(boolean selected) {
    getWindow().setStatusBarColor(
        selected ? SELECTION_STATUS_BAR_COLOR : DEFAULT_STATUS_BAR_COLOR);
  }

  private static boolean hasServerMessageId(MessageEntity message) {
    return message != null && message.messageId != null
        && !message.messageId.trim().isEmpty()
        && !message.messageId.startsWith("local_");
  }

  private static ArrayList<String> serverMessageIds(List<MessageEntity> messages) {
    return serverMessageIds(messages, false);
  }

  private static ArrayList<String> serverMessageIds(
      List<MessageEntity> messages, boolean skipDeleted) {
    ArrayList<String> ids = new ArrayList<>();
    for (MessageEntity message : messages) {
      if (hasServerMessageId(message) && (!skipDeleted || !isDeleted(message))) {
        ids.add(message.messageId);
      }
    }
    return ids;
  }

  private static boolean isDeleted(MessageEntity message) {
    return message != null && (message.deletedText != null
        || "This Message was deleted".equals(message.text));
  }

  private static String copyValue(MessageEntity message) {
    if (message == null) return "";
    String text = message.text == null ? "" : message.text.trim();
    if (!text.isEmpty()) return text;
    String type = message.messageType == null ? "text" : message.messageType;
    if (message.attachmentName != null && !message.attachmentName.trim().isEmpty()) {
      return message.attachmentName.trim();
    }
    if ("location".equals(type) && message.latitude != null && message.longitude != null) {
      return message.latitude + "," + message.longitude;
    }
    return "text".equals(type) ? "" : type;
  }

  private void openUri(Uri target, String mimeType) {
    try {
      Intent intent = new Intent(Intent.ACTION_VIEW, target);
      if (mimeType != null && !mimeType.isEmpty()) intent.setDataAndType(target, mimeType);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(intent);
    } catch (RuntimeException error) {
      Toast.makeText(this, "No app can open this message.", Toast.LENGTH_SHORT).show();
    }
  }

  private void startAttachmentDownload(MessageEntity message) {
    repository.downloadAttachment(message, new ChatRepository.DownloadCallback() {
      @Override public void onAvailable(Uri uri) {
        attachmentStates.put(attachmentKey(message), ATTACHMENT_AVAILABLE);
        refreshAttachmentRows();
        openLocalAttachment(uri, message.attachmentMimeType);
      }
      @Override public void onQueued() {
        attachmentStates.put(attachmentKey(message), ATTACHMENT_DOWNLOADING);
        refreshAttachmentRows();
      }
      @Override public void onError(String error) {
        Toast.makeText(ChatActivity.this, error, Toast.LENGTH_SHORT).show();
      }
    });
  }

  private void refreshAttachmentRows() {
    if (chatView != null) chatView.submitMessages(latestMessages);
  }

  private String attachmentKey(MessageEntity message) {
    return message.attachmentId != null ? message.attachmentId
        : message.messageId != null ? message.messageId : String.valueOf(message.hashCode());
  }

  private boolean canRead(Uri uri) {
    try (android.content.res.AssetFileDescriptor ignored =
             getContentResolver().openAssetFileDescriptor(uri, "r")) {
      return ignored != null;
    } catch (FileNotFoundException | SecurityException error) {
      return false;
    } catch (java.io.IOException error) {
      return false;
    }
  }

  private void openLocalAttachment(Uri uri, String mimeType) {
    if ("file".equals(uri.getScheme())) {
      uri = FileProvider.getUriForFile(this, getPackageName() + ".files", new File(uri.getPath()));
    }
    openUri(uri, mimeType == null || mimeType.isEmpty() ? "*/*" : mimeType);
  }

  @Override
  public int attachmentState(MessageEntity message) {
    if (currentUser.equals(normalize(message.senderId))) return ATTACHMENT_AVAILABLE;
    Integer state = attachmentStates.get(attachmentKey(message));
    if (state != null) return state;
    return message.attachmentLocalUri != null && !message.attachmentLocalUri.isEmpty()
        ? ATTACHMENT_AVAILABLE : ATTACHMENT_DOWNLOAD_REQUIRED;
  }

  private void handleAction(String action, MessageEntity message, boolean own) {
    if (message.messageId == null || message.messageId.trim().isEmpty()) {
      Toast.makeText(this, "Message id missing.", Toast.LENGTH_SHORT).show();
      return;
    }
    if ("Reply".equals(action)) {
      replyingId = message.messageId;
      editingId = null;
      chatView.showReply(message.text);
      return;
    }
    if ("Edit".equals(action)) {
      editingId = message.messageId;
      replyingId = null;
      chatView.clearReply();
      chatView.setDraft(message.text);
      return;
    }
    if (own) repository.deleteOwnMessage(chatId, message.messageId);
    else repository.deleteOpponentMessage(chatId, message.messageId);
    if (message.messageId.equals(editingId)) {
      editingId = null;
      chatView.clearDraft();
    }
    if (message.messageId.equals(replyingId)) {
      replyingId = null;
      chatView.clearReply();
    }
  }

  @Override
  public void onBack() {
    if (chatView != null && chatView.clearMessageSelection()) return;
    finish();
  }

  @Override
  public void onMore() {
    removePrompt();
    if (conversationMenuDialog != null) conversationMenuDialog.show();
  }

  private void showPrompt(NativePromptDialogView prompt) {
    if (conversationMenuDialog != null) conversationMenuDialog.dismissIfShowing();
    removePrompt();
    promptDialog = prompt;
    ((ViewGroup) findViewById(android.R.id.content)).addView(prompt,
        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
  }

  private void removePrompt() {
    NativePromptDialogView current = promptDialog;
    promptDialog = null;
    if (current == null) return;
    if (current.getParent() instanceof ViewGroup) {
      ((ViewGroup) current.getParent()).removeView(current);
    }
    current.release();
  }

  private String receiver() {
    if (chatId == null) return "";
    for (String value : chatId.split("_")) {
      String n = normalize(value);
      if (!n.equals(currentUser)) return n;
    }
    return "";
  }

  private static String normalize(String v) {
    if (v == null) return "";
    String n = v.trim();
    if (n.startsWith("<plus>")) n = n.substring(6);
    return n.startsWith("+") ? n.substring(1) : n;
  }

  @Override
  protected void onDestroy() {
    removePrompt();
    if (conversationMenuDialog != null) {
      conversationMenuDialog.release();
      conversationMenuDialog = null;
    }
    stopTyping.run();
    messagePreparationGeneration++;
    messagePreparationExecutor.shutdownNow();
    typingHandler.removeCallbacksAndMessages(null);
    locationPending = false;
    removeLocationUpdates();
    if (repository != null) repository.setEventListener(null);
    if (chatView != null) chatView.release();
    chatView = null;
    if (profiler != null) profiler.release();
    profiler = null;
    super.onDestroy();
  }
}
