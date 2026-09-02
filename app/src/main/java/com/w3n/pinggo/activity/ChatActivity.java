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
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.provider.Settings;
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
import com.w3n.pinggo.data.cache.MediaPreviewCache;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.views.chat.ChatView;
import com.w3n.pinggo.views.chat.ChatViewListener;
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
import java.io.IOException;

public class ChatActivity extends AppCompatActivity implements ChatViewListener {
  private static final String TESTING_TAG = "PARVEZ_TESTING";
  private static final String LOCATION_PERF_TAG = "PingGoLocationPerf";
  private static final long LOCATION_CACHE_MAX_AGE_MS = 30_000L;
  private static final long LOCATION_FALLBACK_MAX_AGE_MS = 120_000L;
  private static final float LOCATION_ACCEPTABLE_ACCURACY_M = 100f;
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
  private ChatView chatView;
  private String selectedAttachmentType;
  private Uri selectedAttachmentUri;
  private final ActivityResultLauncher<String[]> attachmentPicker =
      registerForActivityResult(
          new ActivityResultContracts.OpenMultipleDocuments(), this::onAttachmentsPicked);
  private final ActivityResultLauncher<Intent> cameraCapture =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
            Intent data = result.getData();
            handleSelectedMediaResult(data);
          });
  private final ActivityResultLauncher<Intent> attachmentPreview =
      registerForActivityResult(
          new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
            handleSelectedMediaResult(result.getData());
          });

  private void handleSelectedMediaResult(Intent data) {
            if (data == null) return;
            List<Uri> uris = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
              for (int index = 0; index < clip.getItemCount(); index++) {
                Uri uri = clip.getItemAt(index).getUri();
                if (uri != null && !uris.contains(uri)) uris.add(uri);
              }
            }
            if (uris.isEmpty() && data.getData() != null) uris.add(data.getData());
            if (uris.isEmpty()) return;
            ArrayList<String> types = data.getStringArrayListExtra(
                CameraCaptureActivity.EXTRA_MEDIA_TYPES);
            if (types == null || types.size() != uris.size()) {
              String fallback = data.getStringExtra(CameraCaptureActivity.EXTRA_MEDIA_TYPE);
              if (fallback == null) fallback = "Image";
              types = new ArrayList<>(Collections.nCopies(uris.size(), fallback));
            }
            sendSelectedAttachments(uris, types);
  }
  private final ActivityResultLauncher<String[]> locationPermission =
      registerForActivityResult(
          new ActivityResultContracts.RequestMultiplePermissions(), this::onLocationPermission);
  private final ActivityResultLauncher<String> audioRecordingPermission =
      registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        if (granted) startAudioRecorder();
        else Toast.makeText(this, "Microphone permission is required.",
            Toast.LENGTH_SHORT).show();
      });
  private final ActivityResultLauncher<String> storagePermission =
      registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        if (granted && pendingDownloadMessage != null) startAttachmentDownload(pendingDownloadMessage);
        else if (!granted) Toast.makeText(this, "Storage permission is required.", Toast.LENGTH_SHORT).show();
        pendingDownloadMessage = null;
      });
  private final ActivityResultLauncher<Intent> allFilesAccess =
      registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && !Environment.isExternalStorageManager()) {
          Toast.makeText(this,
              "All files access is required to store media in /storage/emulated/0/PingGo.",
              Toast.LENGTH_LONG).show();
        }
      });
  private final Set<String> pendingSeen = new HashSet<>();
  private final Map<String, Integer> attachmentStates = new ConcurrentHashMap<>();
  private final ExecutorService messagePreparationExecutor =
      Executors.newSingleThreadExecutor();
  private ChatPerformanceProfiler profiler;
  private ChatRepository repository;
  private NativePromptDialogView promptDialog;
  private ConversationMenuDialogView conversationMenuDialog;
  private String chatId, currentUser, receiverId, replyingId, editingId;
  private String profilePhotoPath;
  private boolean typingStarted, peerTyping, locationPending, attachmentSending;
  private MediaRecorder audioRecorder;
  private File recordedAudioFile;
  private long audioRecordingStartedAt;
  private final List<Integer> audioRecordingSamples = new ArrayList<>();
  private final Runnable updateAudioRecordingTime = new Runnable() {
    @Override public void run() {
      if (audioRecorder == null || chatView == null) return;
      int amplitude = 0;
      try {
        amplitude = Math.max(0, audioRecorder.getMaxAmplitude());
        audioRecordingSamples.add(amplitude);
      } catch (RuntimeException ignored) {
      }
      chatView.updateAudioRecordingState(
          SystemClock.elapsedRealtime() - audioRecordingStartedAt, amplitude);
      typingHandler.postDelayed(this, 80L);
    }
  };
  private MediaPlayer audioPlayer;
  private String playingAudioMessageId = "";
  private String pendingAudioPlaybackMessageId = "";
  private long playingAudioDurationMs;
  private boolean audioPlaybackPrepared;
  private final Runnable updateAudioPlayback = new Runnable() {
    @Override public void run() {
      MediaPlayer player = audioPlayer;
      if (player == null || chatView == null || !audioPlaybackPrepared) return;
      try {
        chatView.setAudioPlaybackState(playingAudioMessageId, true,
            player.getCurrentPosition(), playingAudioDurationMs);
        typingHandler.postDelayed(this, 250L);
      } catch (IllegalStateException error) {
        stopAudioPlayback();
      }
    }
  };
  private LocationManager locationManager;
  private LocationListener networkLocationListener;
  private LocationListener gpsLocationListener;
  private CancellationSignal networkLocationCancellation;
  private CancellationSignal gpsLocationCancellation;
  private Location bestPendingLocation;
  private long locationPressedElapsedMs;
  private long locationPressedWallMs;
  private String locationTraceId = "";
  private PresenceEntity latestPresence;
  private List<MessageEntity> latestMessages = Collections.emptyList();
  private List<MessageEntity> availableMessages = Collections.emptyList();
  private LiveData<List<MessageEntity>> messageSource;
  // Room can deliver the whole network page immediately. The custom list receives only one
  // viewport first, then progressively larger suffixes so its synchronous layout never blocks
  // the first useful frame on all 50 variable-height rows.
  private int messageLimit = ChatRepository.MESSAGE_PAGE_SIZE;
  private int renderedMessageCount;
  private int replyTargetLoadGeneration;
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
        Location fallback = bestPendingLocation;
        if (fallback != null && locationAgeMs(fallback) <= LOCATION_FALLBACK_MAX_AGE_MS) {
          logLocationPerf(
              "timeout_fallback",
              locationDetails(fallback) + " limitMs=15000");
          completeLocation(fallback, "timeout_fallback");
        } else {
          locationPending = false;
          removeLocationUpdates();
          logLocationPerf("timeout", "limitMs=15000");
          Toast.makeText(this, "Unable to get current location.", Toast.LENGTH_SHORT).show();
        }
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
    ensurePingGoStorageAccess();
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
            if (chatView != null && chatView.dismissAttachmentPanel()) return;
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

  private void ensurePingGoStorageAccess() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (Environment.isExternalStorageManager()) return;
      try {
        allFilesAccess.launch(new Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:" + getPackageName())));
      } catch (RuntimeException unavailable) {
        allFilesAccess.launch(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
      }
      return;
    }
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
        && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
      storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }
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
    if (audioRecorder != null) finishAudioRecording(false);
    pendingAudioPlaybackMessageId = "";
    stopAudioPlayback();
    saveMessageSessionState();
    if (repository != null && chatId != null) repository.clearActiveChat(chatId);
    super.onStop();
  }

  public void setFloatingCallInset(int insetPx) {
    if (chatView != null) chatView.setFloatingCallInset(insetPx);
  }

  private void messages(List<MessageEntity> values) {
    List<MessageEntity> snapshot = values == null
        ? Collections.emptyList() : new ArrayList<>(values);
    int generation = ++replyTargetLoadGeneration;
    repository.loadReplyTargets(chatId, snapshot, replyTargets -> {
      if (generation != replyTargetLoadGeneration || isFinishing() || isDestroyed()) return;
      messagesHydrated(snapshot, replyTargets);
    });
  }

  private void messagesHydrated(
      List<MessageEntity> values, List<MessageEntity> replyTargets) {
    availableMessages = values;
    if (!pendingAudioPlaybackMessageId.isEmpty()) {
      MessageEntity pendingAudio = findMessageByKey(pendingAudioPlaybackMessageId);
      if (pendingAudio != null && pendingAudio.attachmentLocalUri != null) {
        Uri pendingUri = Uri.parse(pendingAudio.attachmentLocalUri);
        if (canRead(pendingUri)) {
          String pendingKey = pendingAudioPlaybackMessageId;
          pendingAudioPlaybackMessageId = "";
          typingHandler.post(() -> startAudioPlayback(pendingKey, pendingUri));
        }
      }
    }
    if (chatView != null) {
      chatView.indexReplyTargets(availableMessages);
      if (replyTargets != null && !replyTargets.isEmpty()) {
        chatView.indexReplyTargets(replyTargets);
      }
      List<MessageEntity> pinnedMessages = new ArrayList<>();
      for (MessageEntity message : availableMessages) {
        if (message != null && message.pinned) pinnedMessages.add(message);
      }
      pinnedMessages.sort((first, second) -> Long.compare(
          second.pinnedAt == null ? second.sentTime : second.pinnedAt,
          first.pinnedAt == null ? first.sentTime : first.pinnedAt));
      chatView.setPinnedMessages(pinnedMessages);
    }
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
    prepareAndRenderAvailableMessages("room");
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
    submitPreparedMessages(renderedMessagesSnapshot(), phase);
  }

  /** Measures attachment orientation and final row heights before the list sees this data set. */
  private void prepareAndRenderAvailableMessages(String phase) {
    List<MessageEntity> messagesToPrepare = renderedMessagesSnapshot();
    int generation = ++messagePreparationGeneration;
    if (messagesToPrepare.isEmpty()) {
      messagePreparationRunning = false;
      submitPreparedMessages(messagesToPrepare, phase);
      updateOlderMessagesState();
      return;
    }
    ChatView view = chatView;
    if (view == null) return;
    float availableWidth = view.getMessageLayoutWidth();
    messagePreparationRunning = true;
    updateOlderMessagesState();
    messagePreparationExecutor.execute(() -> {
      long preparationStartedNanos = SystemClock.elapsedRealtimeNanos();
      ChatView.PreparedMessages prepared = view.prepareMessages(messagesToPrepare, availableWidth);
      profiler.operation(
          "prepare_visible",
          preparationStartedNanos,
          "count=" + messagesToPrepare.size());
      typingHandler.post(() -> {
        ChatView currentView = chatView;
        if (currentView == null || generation != messagePreparationGeneration) return;
        Runnable applyPrepared = () -> {
          if (chatView == null || generation != messagePreparationGeneration) return;
          messagePreparationRunning = false;
          submitPreparedMessages(messagesToPrepare, phase, prepared);
          updateOlderMessagesState();
          scheduleProgressiveRender();
        };
        if (!currentView.deferUntilMessageScrollIdle(applyPrepared)) applyPrepared.run();
      });
    });
  }

  /** Keeps every pinned target addressable while progressively revealing the recent timeline. */
  private List<MessageEntity> renderedMessagesSnapshot() {
    if (availableMessages.isEmpty()) return Collections.emptyList();
    int firstRendered = Math.max(0, availableMessages.size() - renderedMessageCount);
    List<MessageEntity> result = new ArrayList<>();
    for (int index = 0; index < availableMessages.size(); index++) {
      MessageEntity message = availableMessages.get(index);
      if (index >= firstRendered || message.pinned) result.add(message);
    }
    return result;
  }

  private void submitPreparedMessages(List<MessageEntity> preparedMessages, String phase) {
    submitPreparedMessages(preparedMessages, phase, null);
  }

  private void submitPreparedMessages(
      List<MessageEntity> preparedMessages,
      String phase,
      ChatView.PreparedMessages backgroundPreparation) {
    long operationStartedNanos = SystemClock.elapsedRealtimeNanos();
    latestMessages = preparedMessages;
    long renderStarted = System.nanoTime();
    boolean changed = backgroundPreparation == null
        ? chatView.submitMessages(latestMessages)
        : chatView.submitPreparedMessages(backgroundPreparation);
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
    List<MessageEntity> targetMessages = new ArrayList<>();
    for (int index = 0; index < snapshot.size(); index++) {
      MessageEntity message = snapshot.get(index);
      if (index >= firstToPrepare || message.pinned) targetMessages.add(message);
    }
    float availableWidth = chatView.getMessageLayoutWidth();
    messagePreparationRunning = true;
    updateOlderMessagesState();
    messagePreparationExecutor.execute(() -> {
      long preparationStartedNanos = SystemClock.elapsedRealtimeNanos();
      ChatView view = chatView;
      if (view == null) return;
      ChatView.PreparedMessages prepared =
          view.prepareMessages(targetMessages, availableWidth);
      profiler.operation(
          "prepare_chunk",
          preparationStartedNanos,
          "count=" + (preparedEnd - firstToPrepare)
              + " targetRendered=" + targetRenderedCount);
      typingHandler.post(() -> {
        ChatView currentView = chatView;
        if (currentView == null || generation != messagePreparationGeneration) return;
        Runnable applyPrepared = () -> {
          if (chatView == null || generation != messagePreparationGeneration) return;
          availableMessages = snapshot;
          renderedMessageCount = targetRenderedCount;
          messagePreparationRunning = renderedMessageCount < snapshot.size();
          submitPreparedMessages(targetMessages, "background", prepared);
          updateOlderMessagesState();
          if (messagePreparationRunning) scheduleProgressiveRender();
        };
        if (!currentView.deferUntilMessageScrollIdle(applyPrepared)) applyPrepared.run();
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
    chatView.scrollToBottom();
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

  private void sendSelectedAttachments(List<Uri> uris, List<String> types) {
    if (uris == null || uris.isEmpty() || attachmentSending) return;
    if (chatId == null || chatId.isEmpty() || receiverId.isEmpty()) {
      Toast.makeText(this, "Chat information missing.", Toast.LENGTH_SHORT).show();
      return;
    }
    attachmentSending = true;
    selectedAttachmentUri = null;
    selectedAttachmentType = null;
    chatView.clearAttachmentPreview();
    chatView.scrollToBottom();
    uploadCameraAttachment(
        new ArrayList<>(uris), new ArrayList<>(types), 0,
        chatView.getDraft(), replyingId);
  }

  private void uploadCameraAttachment(
      List<Uri> uris, List<String> types, int index, String caption, String replyId) {
    if (index >= uris.size()) {
      attachmentSending = false;
      finishComposeAction();
      return;
    }
    String type = index < types.size() && types.get(index) != null
        ? types.get(index).toLowerCase() : "image";
    repository.uploadAndSendAttachment(
        chatId, receiverId, index == 0 ? caption : "", index == 0 ? replyId : null,
        uris.get(index), type, new ChatRepository.AttachmentCallback() {
          @Override public void onSent() {
            uploadCameraAttachment(uris, types, index + 1, caption, replyId);
          }

          @Override public void onError(String message) {
            Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
            uploadCameraAttachment(uris, types, index + 1, caption, replyId);
          }
        });
  }

  @Override
  public void onReplyTargetRequested(String messageId) {
    if (messageId == null || messageId.trim().isEmpty()) return;
    for (int index = 0; index < availableMessages.size(); index++) {
      MessageEntity message = availableMessages.get(index);
      if (!messageId.equals(message.messageId)) continue;
      int requiredCount = availableMessages.size() - index;
      if (renderedMessageCount < requiredCount) {
        renderedMessageCount = requiredCount;
        prepareAndRenderAvailableMessages("reply_target");
      }
      return;
    }
    if (isProgressiveRendering()) {
      renderedMessageCount = availableMessages.size();
      prepareAndRenderAvailableMessages("reply_target_expand");
      return;
    }
    onLoadOlderMessages();
  }

  @Override
  public void onAudioRecordingStart() {
    if (attachmentSending || audioRecorder != null) return;
    if (chatId == null || chatId.isEmpty() || receiverId.isEmpty()) {
      Toast.makeText(this, "Chat information missing.", Toast.LENGTH_SHORT).show();
      return;
    }
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED) {
      startAudioRecorder();
    } else {
      audioRecordingPermission.launch(Manifest.permission.RECORD_AUDIO);
    }
  }

  private void startAudioRecorder() {
    if (audioRecorder != null) return;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        && !Environment.isExternalStorageManager()) {
      ensurePingGoStorageAccess();
      Toast.makeText(this,
          "Allow all files access, then tap the microphone again.", Toast.LENGTH_LONG).show();
      return;
    }
    File audioDirectory = new File(Environment.getExternalStorageDirectory(), "PingGo/Audio");
    if ((!audioDirectory.exists() && !audioDirectory.mkdirs()) || !audioDirectory.isDirectory()) {
      Toast.makeText(this, "Unable to create the PingGo audio folder.",
          Toast.LENGTH_SHORT).show();
      return;
    }
    File output = new File(audioDirectory,
        "AUD_" + System.currentTimeMillis() + ".m4a");
    MediaRecorder recorder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ? new MediaRecorder(this) : new MediaRecorder();
    try {
      recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
      recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
      recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
      recorder.setAudioSamplingRate(44_100);
      recorder.setAudioEncodingBitRate(128_000);
      recorder.setOutputFile(output.getAbsolutePath());
      recorder.prepare();
      recorder.start();
      audioRecorder = recorder;
      recordedAudioFile = output;
      audioRecordingStartedAt = SystemClock.elapsedRealtime();
      audioRecordingSamples.clear();
      stopTyping.run();
      chatView.startAudioRecording();
      typingHandler.post(updateAudioRecordingTime);
    } catch (IOException | RuntimeException error) {
      try { recorder.release(); } catch (RuntimeException ignored) {}
      if (output.exists()) output.delete();
      Toast.makeText(this, "Unable to start audio recording.", Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onAudioRecordingSend() {
    finishAudioRecording(true);
  }

  @Override
  public void onAudioRecordingCancel() {
    finishAudioRecording(false);
  }

  private void finishAudioRecording(boolean sendRecording) {
    MediaRecorder recorder = audioRecorder;
    File output = recordedAudioFile;
    long duration = audioRecordingStartedAt == 0L
        ? 0L : SystemClock.elapsedRealtime() - audioRecordingStartedAt;
    List<Integer> amplitudeSamples = new ArrayList<>(audioRecordingSamples);
    audioRecordingSamples.clear();
    audioRecorder = null;
    recordedAudioFile = null;
    audioRecordingStartedAt = 0L;
    typingHandler.removeCallbacks(updateAudioRecordingTime);
    if (recorder == null) {
      if (chatView != null) chatView.stopAudioRecording();
      return;
    }
    boolean stopped = false;
    try {
      recorder.stop();
      stopped = true;
    } catch (RuntimeException ignored) {
    } finally {
      try { recorder.release(); } catch (RuntimeException ignored) {}
    }
    if (chatView != null) chatView.stopAudioRecording();
    if (!sendRecording || !stopped || duration < 500L || output == null || !output.isFile()) {
      if (output != null && output.exists()) output.delete();
      if (sendRecording) Toast.makeText(this, "Record a longer audio message.",
          Toast.LENGTH_SHORT).show();
      return;
    }
    attachmentSending = true;
    Uri audioUri = FileProvider.getUriForFile(
        this, getPackageName() + ".files", output);
    chatView.scrollToBottom();
    repository.uploadAndSendAttachment(
        chatId, receiverId, buildAudioMetadata(duration, amplitudeSamples),
        replyingId, audioUri, "audio",
        new ChatRepository.AttachmentCallback() {
          @Override public void onSent() {
            attachmentSending = false;
            finishComposeAction();
          }

          @Override public void onError(String message) {
            attachmentSending = false;
            Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
          }
        });
  }

  @Override
  public void onAudioPlaybackToggle(String messageId) {
    String key = messageId == null ? "" : messageId;
    if (key.isEmpty()) return;
    if (key.equals(playingAudioMessageId) && audioPlayer != null) {
      stopAudioPlayback();
      return;
    }
    MessageEntity message = findMessageByKey(key);
    if (message == null) {
      Toast.makeText(this, "This audio message does not exist.", Toast.LENGTH_SHORT).show();
      return;
    }
    Uri local = message.attachmentLocalUri == null
        ? null : Uri.parse(message.attachmentLocalUri);
    if (local != null && canRead(local)) {
      startAudioPlayback(key, local);
      return;
    }
    if (message.attachmentUrl == null || message.attachmentUrl.trim().isEmpty()) {
      Toast.makeText(this, "This file does not exist.", Toast.LENGTH_SHORT).show();
      return;
    }
    if (attachmentStates.getOrDefault(
        attachmentKey(message), ATTACHMENT_DOWNLOAD_REQUIRED) == ATTACHMENT_DOWNLOADING) return;
    attachmentStates.put(attachmentKey(message), ATTACHMENT_DOWNLOADING);
    pendingAudioPlaybackMessageId = key;
    refreshAttachmentRows();
    repository.downloadAttachment(message, new ChatRepository.DownloadCallback() {
      @Override public void onAvailable(Uri uri) {
        pendingAudioPlaybackMessageId = "";
        attachmentStates.put(attachmentKey(message), ATTACHMENT_AVAILABLE);
        refreshAttachmentRows();
        startAudioPlayback(key, uri);
      }

      @Override public void onQueued() {
        attachmentStates.put(attachmentKey(message), ATTACHMENT_DOWNLOADING);
        refreshAttachmentRows();
      }

      @Override public void onError(String error) {
        pendingAudioPlaybackMessageId = "";
        attachmentStates.put(attachmentKey(message), ATTACHMENT_DOWNLOAD_REQUIRED);
        refreshAttachmentRows();
        Toast.makeText(ChatActivity.this,
            error == null || error.trim().isEmpty() ? "This file does not exist." : error,
            Toast.LENGTH_SHORT).show();
      }
    });
  }

  private void startAudioPlayback(String messageId, Uri uri) {
    pendingAudioPlaybackMessageId = "";
    stopAudioPlayback();
    MediaPlayer player = new MediaPlayer();
    audioPlayer = player;
    playingAudioMessageId = messageId;
    playingAudioDurationMs = 0L;
    audioPlaybackPrepared = false;
    chatView.setAudioPlaybackState(messageId, true, 0L, 0L);
    try {
      player.setAudioAttributes(new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build());
      player.setDataSource(this, uri);
      player.setOnPreparedListener(prepared -> {
        if (prepared != audioPlayer) return;
        audioPlaybackPrepared = true;
        playingAudioDurationMs = Math.max(0, prepared.getDuration());
        prepared.start();
        chatView.setAudioPlaybackState(messageId, true, 0L, playingAudioDurationMs);
        typingHandler.post(updateAudioPlayback);
      });
      player.setOnCompletionListener(completed -> stopAudioPlayback());
      player.setOnErrorListener((failed, what, extra) -> {
        if (failed == audioPlayer) {
          Toast.makeText(this, "Unable to play this audio message.",
              Toast.LENGTH_SHORT).show();
          stopAudioPlayback();
        }
        return true;
      });
      player.prepareAsync();
    } catch (IOException | RuntimeException error) {
      Toast.makeText(this, "Unable to play this audio message.", Toast.LENGTH_SHORT).show();
      stopAudioPlayback();
    }
  }

  private void stopAudioPlayback() {
    MediaPlayer player = audioPlayer;
    String previousId = playingAudioMessageId;
    long previousDuration = playingAudioDurationMs;
    audioPlayer = null;
    playingAudioMessageId = "";
    playingAudioDurationMs = 0L;
    audioPlaybackPrepared = false;
    typingHandler.removeCallbacks(updateAudioPlayback);
    if (player != null) {
      try { player.stop(); } catch (RuntimeException ignored) {}
      try { player.release(); } catch (RuntimeException ignored) {}
    }
    if (chatView != null && !previousId.isEmpty()) {
      chatView.setAudioPlaybackState(previousId, false, 0L, previousDuration);
    }
  }

  private MessageEntity findMessageByKey(String key) {
    for (MessageEntity message : availableMessages) {
      if (message != null
          && (key.equals(message.messageId) || key.equals(message.clientMessageId))) return message;
    }
    for (MessageEntity message : latestMessages) {
      if (message != null
          && (key.equals(message.messageId) || key.equals(message.clientMessageId))) return message;
    }
    return null;
  }

  private static String formatAudioDuration(long milliseconds) {
    long seconds = Math.max(0L, milliseconds / 1000L);
    return String.format(java.util.Locale.US,
        "%d:%02d", seconds / 60L, seconds % 60L);
  }

  private static String buildAudioMetadata(long durationMs, List<Integer> samples) {
    final int barCount = 22;
    int[] bars = new int[barCount];
    int maximum = 0;
    if (samples != null && !samples.isEmpty()) {
      for (int bar = 0; bar < barCount; bar++) {
        int start = bar * samples.size() / barCount;
        int end = Math.max(start + 1, (bar + 1) * samples.size() / barCount);
        int peak = 0;
        for (int index = start; index < end && index < samples.size(); index++) {
          peak = Math.max(peak, samples.get(index));
        }
        bars[bar] = peak;
        maximum = Math.max(maximum, peak);
      }
    }
    StringBuilder metadata = new StringBuilder(formatAudioDuration(durationMs));
    metadata.append("|waveform=");
    for (int index = 0; index < bars.length; index++) {
      if (index > 0) metadata.append(',');
      float normalized = maximum <= 0 ? .16f : (float) Math.sqrt(bars[index] / (float) maximum);
      int level = Math.max(12, Math.min(100, Math.round(normalized * 100f)));
      metadata.append(level);
    }
    return metadata.toString();
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
    chatView.scrollToBottom();
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
      locationPressedElapsedMs = SystemClock.elapsedRealtime();
      locationPressedWallMs = System.currentTimeMillis();
      locationTraceId = "location_" + locationPressedWallMs;
      logLocationPerf("press", "wallTimeMs=" + locationPressedWallMs);
      requestLocationPermission();
    }
  }

  @Override
  public void onCameraSelected() {
    if (attachmentSending || audioRecorder != null) return;
    startActivityForResultCamera();
  }

  private void startActivityForResultCamera() {
    cameraCapture.launch(new Intent(this, CameraCaptureActivity.class));
  }

  private void onAttachmentsPicked(List<Uri> picked) {
    if (picked == null || picked.isEmpty()) return;
    String pickerType = selectedAttachmentType;
    ArrayList<Uri> uris = new ArrayList<>();
    ArrayList<String> types = new ArrayList<>();
    for (Uri uri : picked) {
      if (uri == null) continue;
      try {
        getContentResolver().takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } catch (SecurityException ignored) {
      }
      uris.add(uri);
      types.add(selectedPreviewType(uri, pickerType));
    }
    if (uris.isEmpty()) return;
    Intent preview = new Intent(this, SelectedMediaPreviewActivity.class);
    preview.putParcelableArrayListExtra(SelectedMediaPreviewActivity.EXTRA_URIS, uris);
    preview.putStringArrayListExtra(SelectedMediaPreviewActivity.EXTRA_TYPES, types);
    preview.putExtra(SelectedMediaPreviewActivity.EXTRA_CAPTURED, false);
    preview.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    attachmentPreview.launch(preview);
  }

  private String selectedPreviewType(Uri uri, String pickerType) {
    // The entry point is authoritative. Files must remain file messages even when their
    // underlying MIME type is image/* or video/*.
    if ("File".equals(pickerType)) return "File";
    if ("Image".equals(pickerType)) return "Image";
    if ("Video".equals(pickerType)) return "Video";
    String mime = getContentResolver().getType(uri);
    if (mime != null && mime.startsWith("image/")) return "Image";
    if (mime != null && mime.startsWith("video/")) return "Video";
    return "File";
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
      logLocationPerf("permission_ready", "fine=" + fine + " coarse=" + coarse);
      requestCurrentLocation();
      return;
    }
    logLocationPerf("permission_prompt", "fine=false coarse=false");
    locationPermission.launch(
        new String[] {
          Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        });
  }

  private void onLocationPermission(Map<String, Boolean> result) {
    boolean granted =
        Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
            || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
    logLocationPerf("permission_result", "granted=" + granted);
    if (granted) requestCurrentLocation();
    else Toast.makeText(this, "Location permission is required.", Toast.LENGTH_SHORT).show();
  }

  private void requestCurrentLocation() {
    locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
    boolean fine =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
    boolean coarse =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
    boolean networkEnabled = coarse || fine;
    networkEnabled = networkEnabled && isLocationProviderEnabled(LocationManager.NETWORK_PROVIDER);
    boolean gpsEnabled = fine && isLocationProviderEnabled(LocationManager.GPS_PROVIDER);
    if (!networkEnabled && !gpsEnabled) {
      logLocationPerf("provider_unavailable", "");
      Toast.makeText(this, "Turn on location services and try again.", Toast.LENGTH_SHORT).show();
      return;
    }
    locationPending = true;
    bestPendingLocation = null;
    typingHandler.removeCallbacks(locationTimeout);
    typingHandler.postDelayed(locationTimeout, 15_000);

    Location cached = bestCachedLocation(networkEnabled, gpsEnabled);
    if (cached != null) {
      long ageMs = locationAgeMs(cached);
      logLocationPerf(
          "cached_candidate",
          locationDetails(cached)
              + " accepted=" + isAcceptableLocation(cached, LOCATION_CACHE_MAX_AGE_MS));
      rememberLocationCandidate(cached);
      if (isAcceptableLocation(cached, LOCATION_CACHE_MAX_AGE_MS)) {
        completeLocation(cached, "cached");
        return;
      }
    }

    logLocationPerf(
        "location_request",
        "network=" + networkEnabled + " gps=" + gpsEnabled);
    if (networkEnabled) requestLocationFromProvider(LocationManager.NETWORK_PROVIDER);
    if (gpsEnabled) requestLocationFromProvider(LocationManager.GPS_PROVIDER);
  }

  private boolean isLocationProviderEnabled(String provider) {
    try {
      return locationManager != null && locationManager.isProviderEnabled(provider);
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private Location bestCachedLocation(boolean networkEnabled, boolean gpsEnabled) {
    Location best = null;
    if (networkEnabled) best = betterLocation(
        best, lastKnownLocation(LocationManager.NETWORK_PROVIDER));
    if (gpsEnabled) best = betterLocation(
        best, lastKnownLocation(LocationManager.GPS_PROVIDER));
    return best;
  }

  private Location lastKnownLocation(String provider) {
    try {
      return locationManager == null ? null : locationManager.getLastKnownLocation(provider);
    } catch (SecurityException | IllegalArgumentException ignored) {
      return null;
    }
  }

  private void requestLocationFromProvider(String provider) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        CancellationSignal cancellation = new CancellationSignal();
        if (LocationManager.NETWORK_PROVIDER.equals(provider)) {
          networkLocationCancellation = cancellation;
        } else {
          gpsLocationCancellation = cancellation;
        }
        locationManager.getCurrentLocation(
            provider,
            cancellation,
            getMainExecutor(),
            location -> onLocationCandidate(location, provider));
      } else {
        LocationListener listener = new LocationListener() {
          @Override public void onLocationChanged(Location location) {
            onLocationCandidate(location, provider);
          }
          @Override public void onStatusChanged(String value, int status, Bundle extras) {}
          @Override public void onProviderEnabled(String value) {}
          @Override public void onProviderDisabled(String value) {}
        };
        if (LocationManager.NETWORK_PROVIDER.equals(provider)) {
          networkLocationListener = listener;
        } else {
          gpsLocationListener = listener;
        }
        locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper());
      }
    } catch (SecurityException | IllegalArgumentException error) {
      logLocationPerf(
          "provider_request_error",
          "provider=" + provider + " message=" + error.getMessage());
    }
  }

  private void onLocationCandidate(Location location, String requestedProvider) {
    if (!locationPending) return;
    if (location == null) {
      logLocationPerf("location_null", "provider=" + requestedProvider);
      return;
    }
    rememberLocationCandidate(location);
    logLocationPerf(
        "location_candidate",
        "requestedProvider=" + requestedProvider + " " + locationDetails(location)
            + " accepted=" + isAcceptableLocation(location, LOCATION_CACHE_MAX_AGE_MS));
    if (isAcceptableLocation(location, LOCATION_CACHE_MAX_AGE_MS)) {
      completeLocation(location, requestedProvider);
    }
  }

  private void completeLocation(Location location, String source) {
    if (!locationPending) return;
    locationPending = false;
    typingHandler.removeCallbacks(locationTimeout);
    removeLocationUpdates();
    logLocationPerf("location_ready", "source=" + source + " " + locationDetails(location));
    if (chatId == null || chatId.isEmpty() || receiverId.isEmpty()) {
      Toast.makeText(this, "Chat information missing.", Toast.LENGTH_SHORT).show();
      return;
    }
    long sendStartedMs = SystemClock.elapsedRealtime();
    String clientMessageId = repository.sendLocation(
        chatId,
        receiverId,
        location.getLatitude(),
        location.getLongitude(),
        location.getAccuracy(),
        replyingId);
    long sendCallMs = SystemClock.elapsedRealtime() - sendStartedMs;
    chatView.trackLocationRender(
        clientMessageId, locationTraceId, locationPressedElapsedMs, locationPressedWallMs);
    logLocationPerf(
        "send",
        "clientMessageId=" + clientMessageId + " repositoryCallMs=" + sendCallMs);
    finishComposeAction();
    Toast.makeText(this, "Current location sent.", Toast.LENGTH_SHORT).show();
  }

  private void rememberLocationCandidate(Location candidate) {
    if (candidate == null || locationAgeMs(candidate) > LOCATION_FALLBACK_MAX_AGE_MS) return;
    bestPendingLocation = betterLocation(bestPendingLocation, candidate);
  }

  private Location betterLocation(Location first, Location second) {
    if (first == null) return second;
    if (second == null) return first;
    boolean firstAcceptable = isAcceptableLocation(first, LOCATION_CACHE_MAX_AGE_MS);
    boolean secondAcceptable = isAcceptableLocation(second, LOCATION_CACHE_MAX_AGE_MS);
    if (firstAcceptable != secondAcceptable) return secondAcceptable ? second : first;
    if (second.getAccuracy() + 5f < first.getAccuracy()) return second;
    return locationAgeMs(second) < locationAgeMs(first) ? second : first;
  }

  private boolean isAcceptableLocation(Location location, long maximumAgeMs) {
    return location != null
        && locationAgeMs(location) <= maximumAgeMs
        && location.hasAccuracy()
        && location.getAccuracy() <= LOCATION_ACCEPTABLE_ACCURACY_M;
  }

  private long locationAgeMs(Location location) {
    if (location == null) return Long.MAX_VALUE;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
        && location.getElapsedRealtimeNanos() > 0L) {
      return Math.max(0L, SystemClock.elapsedRealtime()
          - location.getElapsedRealtimeNanos() / 1_000_000L);
    }
    return Math.max(0L, System.currentTimeMillis() - location.getTime());
  }

  private String locationDetails(Location location) {
    return "provider=" + location.getProvider()
        + " accuracyM=" + location.getAccuracy()
        + " fixAgeMs=" + locationAgeMs(location);
  }

  private void removeLocationUpdates() {
    if (networkLocationCancellation != null) networkLocationCancellation.cancel();
    if (gpsLocationCancellation != null) gpsLocationCancellation.cancel();
    networkLocationCancellation = null;
    gpsLocationCancellation = null;
    removeLocationListener(networkLocationListener);
    removeLocationListener(gpsLocationListener);
    networkLocationListener = null;
    gpsLocationListener = null;
    bestPendingLocation = null;
  }

  private void removeLocationListener(LocationListener listener) {
    if (locationManager == null || listener == null) return;
    try {
      locationManager.removeUpdates(listener);
    } catch (SecurityException ignored) {
      // Permission may have been revoked while the request was active.
    }
  }

  private void logLocationPerf(String event, String details) {
    long now = SystemClock.elapsedRealtime();
    long elapsed = locationPressedElapsedMs <= 0L ? -1L : now - locationPressedElapsedMs;
    Log.i(
        LOCATION_PERF_TAG,
        "trace=" + locationTraceId
            + " event=" + event
            + " elapsedMs=" + elapsed
            + (details == null || details.isEmpty() ? "" : " " + details));
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
    if ("audio".equals(type)) {
      // Voice messages are owned by the in-chat player. Never let their row click fall
      // through to the generic attachment chooser.
      String audioMessageId = message.messageId;
      if (audioMessageId == null || audioMessageId.trim().isEmpty()) {
        audioMessageId = message.clientMessageId;
      }
      onAudioPlaybackToggle(audioMessageId);
      return;
    }
    if ("location".equals(type) && message.latitude != null && message.longitude != null) {
      String coordinates = message.latitude + "," + message.longitude;
      openLocationChooser(coordinates);
      return;
    }
    if (!("image".equals(type) || "video".equals(type) || "file".equals(type))) return;
    String fileMediaType = "file".equals(type) ? attachmentMediaType(message) : null;
    if ("image".equals(type) || "video".equals(type) || fileMediaType != null) {
      String previewType = fileMediaType == null ? type : fileMediaType;
      String source = message.attachmentLocalUri;
      if (source == null || source.trim().isEmpty() || !canRead(Uri.parse(source))) {
        source = message.attachmentUrl;
      }
      if (source == null || source.trim().isEmpty()) {
        Toast.makeText(this, "This file does not exist.", Toast.LENGTH_SHORT).show();
        return;
      }
      String mediaType = "video".equals(previewType)
          ? MediaPreviewCache.TYPE_VIDEO : MediaPreviewCache.TYPE_IMAGE;
      if (!MediaPreviewCache.isMediaReady(this, source, mediaType)) {
        if (!"file".equals(type)) {
          Toast.makeText(this, "This file does not exist.", Toast.LENGTH_SHORT).show();
          return;
        }
        String remoteSource = source;
        MediaPreviewCache.resolveMedia(this, remoteSource, mediaType,
            new MediaPreviewCache.Callback<Uri>() {
              @Override public void onSuccess(Uri local) {
                openMediaPreview(local.toString(), previewType, message);
              }

              @Override public void onError() {
                Toast.makeText(ChatActivity.this,
                    "This file does not exist.", Toast.LENGTH_SHORT).show();
              }
            });
        return;
      } else {
        openMediaPreview(source, previewType, message);
        return;
      }
    }
    boolean own = currentUser.equals(normalize(message.senderId));
    if (own) {
      Uri local = message.attachmentLocalUri == null ? null : Uri.parse(message.attachmentLocalUri);
      if (local == null || !canRead(local)) {
        Toast.makeText(this, "File no longer available.", Toast.LENGTH_SHORT).show();
        return;
      }
      openAttachment(message, local);
      return;
    }
    if (message.attachmentLocalUri != null && canRead(Uri.parse(message.attachmentLocalUri))) {
      openAttachment(message, Uri.parse(message.attachmentLocalUri));
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
    chatView.showReply(message);
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

  private void openLocationChooser(String coordinates) {
    try {
      Uri target = Uri.parse("geo:" + coordinates + "?q=" + Uri.encode(coordinates));
      Intent mapIntent = new Intent(Intent.ACTION_VIEW, target);
      startActivity(Intent.createChooser(mapIntent, "Open location with"));
    } catch (RuntimeException error) {
      Toast.makeText(this, "No map app is available.", Toast.LENGTH_SHORT).show();
    }
  }

  private void startAttachmentDownload(MessageEntity message) {
    repository.downloadAttachment(message, new ChatRepository.DownloadCallback() {
      @Override public void onAvailable(Uri uri) {
        attachmentStates.put(attachmentKey(message), ATTACHMENT_AVAILABLE);
        refreshAttachmentRows();
        openAttachment(message, uri);
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

  private void openAttachment(MessageEntity message, Uri uri) {
    String previewType = attachmentMediaType(message);
    if (previewType != null) {
      openMediaPreview(uri.toString(), previewType, message);
      return;
    }
    if ("file".equals(uri.getScheme())) {
      uri = FileProvider.getUriForFile(this, getPackageName() + ".files", new File(uri.getPath()));
    }
    try {
      Intent intent = new Intent(Intent.ACTION_VIEW, uri);
      String mime = message.attachmentMimeType;
      intent.setDataAndType(uri, mime == null || mime.isEmpty() ? "*/*" : mime);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(Intent.createChooser(intent, "Open with"));
    } catch (RuntimeException error) {
      Toast.makeText(this, "No app can open this message.", Toast.LENGTH_SHORT).show();
    }
  }

  private void openMediaPreview(String source, String mediaType, MessageEntity message) {
    Intent preview = new Intent(this,
        "video".equals(mediaType) ? VideoPreviewActivity.class : ImagePreviewActivity.class);
    preview.putExtra(ImagePreviewActivity.EXTRA_URI, source);
    preview.putExtra(ImagePreviewActivity.EXTRA_PHONE_NUMBER, receiverId);
    preview.putExtra(ImagePreviewActivity.EXTRA_CHAT_ID, chatId);
    preview.putExtra(ImagePreviewActivity.EXTRA_MESSAGE_ID,
        message == null ? null : message.messageId);
    startActivity(preview);
  }

  private static String attachmentMediaType(MessageEntity message) {
    if (message == null) return null;
    String mime = message.attachmentMimeType == null
        ? "" : message.attachmentMimeType.toLowerCase(java.util.Locale.US);
    if (mime.startsWith("image/")) return "image";
    if (mime.startsWith("video/")) return "video";
    String name = message.attachmentName == null
        ? "" : message.attachmentName.toLowerCase(java.util.Locale.US);
    if (name.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp|heic|heif)$")) return "image";
    if (name.matches(".*\\.(mp4|m4v|mov|webm|mkv|avi|3gp)$")) return "video";
    return null;
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
      chatView.showReply(message);
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
    if (audioRecorder != null) {
      finishAudioRecording(false);
      return;
    }
    if (chatView != null && chatView.dismissAttachmentPanel()) return;
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
    finishAudioRecording(false);
    stopAudioPlayback();
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
