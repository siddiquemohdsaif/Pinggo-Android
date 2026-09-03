package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import com.ogfa.nativeviews.button.Button;
import com.ogfa.nativeviews.component.Component;
import com.ogfa.nativeviews.component.ComponentHost;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.progress.Progress;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.textfield.TextField;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.ogfa.nativeviews.zlayer.ZLayerGroup;
import com.w3n.pinggo.R;
import com.w3n.pinggo.data.cache.MediaPreviewCache;
import com.w3n.pinggo.data.local.MessageEntity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** AAR-native conversation screen and message composer. */
public final class ChatView extends View {
  /** Opaque full-list render data prepared away from the UI thread. */
  public static final class PreparedMessages {
    private final ChatMessageAdapter.PreparedSubmission submission;

    private PreparedMessages(ChatMessageAdapter.PreparedSubmission submission) {
      this.submission = submission;
    }
  }
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
  private static final int PRIMARY = 0xFF000E1A, SECONDARY = 0xFF687382, ACCENT = 0xFF019CC4;
  private static final int MAX_VISIBLE_COMPOSER_LINES = 7;
  private final ZLayerGroup layers = new ZLayerGroup(this);
  private final ZLayer bg = layers.addLayer("background"),
      content = layers.addLayer("content"),
      overlay = layers.addLayer("overlay"),
      selectionOverlay = layers.addLayer("message_selection");
  private final ChatViewListener listener;
  private final ChatPerformanceProfiler profiler;
  private final ChatMessageAdapter adapter;
  private final PinnedMessageAdapter pinnedAdapter;
  private final PinnedMessageTabView pinnedMessageTab;
  private final MessageSelectionHeaderComponent selectionHeader;
  private final ChatHeaderComponent chatHeader;
  private final TextPaint composerMeasurePaint =
      new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
  private final Bitmap white = color(Color.WHITE),
      chatStatusBarBackground = color(0xFFF9FBFE),
      transparent = color(Color.TRANSPARENT),
      divider = color(0xFFE5EAF0),
      accent = color(ACCENT),
      attachmentOption = color(0xFFEAF7FA),
      conversationBackground = resourceBitmap(R.drawable.conversation_background),
      headerBackground = resourceBitmap(R.drawable.conversation_header_background),
      microphoneIcon = resourceBitmap(R.drawable.conversation_microphone),
      sendIcon = resourceBitmap(R.drawable.conversation_send),
      emojiIcon = resourceBitmap(R.drawable.conversation_emoji),
      attachmentIcon = resourceBitmap(R.drawable.conversation_attachment),
      cameraIcon = resourceBitmap(R.drawable.conversation_camera),
      backIcon = drawableBitmap(R.drawable.conversation_back),
      voiceCallIcon = resourceBitmap(R.drawable.conversation_voice_call),
      videoCallIcon = resourceBitmap(R.drawable.conversation_video_call),
      moreIcon = resourceBitmap(R.drawable.home_overflow_dots),
      selectionBackground = resourceBitmap(R.drawable.chat_selection_background),
      selectionStatusBarBackground = color(0xFFE9EDF0),
      messageSelectionBackground = color(0x40A9B3BB),
      selectionReplyIcon = namedDrawableBitmap("conversation_selection_reply"),
      selectionCopyIcon = namedDrawableBitmap("conversation_selection_copy"),
      selectionForwardIcon = namedDrawableBitmap("conversation_selection_forward"),
      selectionPinIcon = namedDrawableBitmap("conversation_selection_pin"),
      selectionUnpinIcon = namedDrawableBitmap("conversation_selection_unpin"),
      selectionDeleteIcon = namedDrawableBitmap("conversation_selection_delete"),
      messageSendingIcon = resourceBitmap(R.drawable.chat_status_sending_image),
      messageSentIcon = resourceBitmap(R.drawable.chat_status_sent),
      messageDeliveredIcon = resourceBitmap(R.drawable.chat_status_delivered),
      messageReadIcon = resourceBitmap(R.drawable.chat_status_read),
      documentIcon = resourceBitmap(R.drawable.chat_document),
      deletedMessageIcon = drawableBitmap(R.drawable.chat_message_deleted),
      forwardedMessageIcon = drawableBitmap(R.drawable.chat_message_forwarded),
      callPhoneIncomingIcon = resourceBitmap(R.drawable.chat_phone_incoming),
      callPhoneOutgoingIcon = resourceBitmap(R.drawable.chat_phone_outgoing),
      callPhoneMissedIcon = resourceBitmap(R.drawable.chat_phone_missed),
      callVideoIncomingIcon = resourceBitmap(R.drawable.chat_video_incoming),
      callVideoOutgoingIcon = resourceBitmap(R.drawable.chat_video_outgoing),
      callVideoMissedIcon = resourceBitmap(R.drawable.chat_video_missed);
  private final String chatName, currentUser;
  private final ChatSelectionController selection = new ChatSelectionController();
  private final Bitmap profile;
  private ComponentList<MessageEntity> list;
  private Text status, olderStatus, replyText, searchMatchCount;
  private ComposerReplyPreviewComponent composerReplyPreview;
  private Image olderLoadingBackground, attachmentPreviewBackground;
  private Progress olderProgress;
  private TextField input;
  private TextField searchInput;
  private Button send, attachmentPreviewRemove, attachmentPreviewSend;
  private Image composerActionIcon;
  private Text audioRecordingTime;
  private AudioRecordingComponent audioRecordingWaveform;
  private Text attachmentPreviewTextComponent;
  private ComposerBackgroundComponent composerBackground;
  private int topInset, bottomInset, imeInset;
  private int floatingCallInset;
  private boolean imeVisible;
  private boolean searchVisible;
  private boolean contactBlocked;
  private boolean keepKeyboardAfterSend;
  private boolean forceBottomOnNextMessageSubmission;
  private boolean directComposerSendGesture;
  private boolean directMessageListGesture;
  private String searchDraft = "";
  private Runnable searchDismissAction;
  private final List<Integer> searchMatches = new ArrayList<>();
  private int searchMatchIndex = -1;
  private final ChatComposerState composer = new ChatComposerState();
  private boolean loadingOlderMessages, canLoadOlderMessages = true;
  private String pendingPinnedScrollMessageId;
  private String pendingReplyScrollMessageId;
  private String statusValue = "Loading messages...";
  private final List<MessageEntity> pinnedMessages = new ArrayList<>();
  private int pinnedMessageIndex;
  private float headerBottom, baseListBottom;
  private float renderedComposerHeight = -1f;
  private boolean composerResizePosted;
  private float loadingGestureStartY;
  private boolean loadingGestureBlocked;
  private boolean olderLoadRequestedForGesture;
  private boolean profileScrollCandidate;
  private boolean profileScrollStarted;
  private float profileGestureStartY;
  private int lastPrefetchFirst = -1;
  private boolean messageScrollActive;
  private float lastIdleProbeOffset;
  private int stableIdleProbes;
  private long idleProbeDeadlineMs;
  private Runnable pendingScrollIdleAction;
  private final Runnable finishScrollProfile = this::finishScrollProfile;
  private final Runnable probeScrollIdle = new Runnable() {
    @Override public void run() {
      if (!messageScrollActive || list == null) return;
      float offset = list.getScrollOffset();
      if (Math.abs(offset - lastIdleProbeOffset) < .5f) stableIdleProbes++;
      else stableIdleProbes = 0;
      lastIdleProbeOffset = offset;
      if (stableIdleProbes >= 2 || SystemClock.uptimeMillis() >= idleProbeDeadlineMs) {
        finishScrollProfile();
      } else {
        postDelayed(this, 32L);
      }
    }
  };

  public ChatView(
      Context c,
      String name,
      String currentUser,
      String photoPath,
      ChatViewListener l,
      ChatPerformanceProfiler profiler) {
    super(c);
    chatName = name;
    this.currentUser = normalize(currentUser);
    listener = l;
    this.profiler = profiler;
    composerMeasurePaint.setTextSize(sp(16));
    composerMeasurePaint.setTypeface(ResourcesCompat.getFont(c, NativeFonts.INTER));
    pinnedAdapter = new PinnedMessageAdapter(
        c, this.currentUser, white, divider, selectionPinIcon);
    pinnedMessageTab = new PinnedMessageTabView(
        c, pinnedAdapter, white, divider, transparent,
        this::scrollToPinnedMessage, this::movePinnedMessage);
    selectionHeader = new MessageSelectionHeaderComponent(
        c, listener, this::clearMessageSelection, pinnedAdapter::isPinnedByCurrentUser,
        selectionBackground, selectionStatusBarBackground, transparent, backIcon,
        selectionReplyIcon, selectionCopyIcon, selectionForwardIcon, selectionPinIcon,
        selectionUnpinIcon, selectionDeleteIcon);
    adapter =
        new ChatMessageAdapter(
            c,
            this.currentUser,
            new ChatMessageAdapterConfig(
                transparent,
                messageSelectionBackground,
                messageSendingIcon,
                messageSentIcon,
                messageDeliveredIcon,
                messageReadIcon,
                selectionPinIcon,
                documentIcon,
                deletedMessageIcon,
                forwardedMessageIcon,
                callPhoneIncomingIcon,
                callPhoneOutgoingIcon,
                callPhoneMissedIcon,
                callVideoIncomingIcon,
                callVideoOutgoingIcon,
                callVideoMissedIcon,
                listener::attachmentState,
                this::onMediaMetricsChanged,
                listener::onAudioPlaybackToggle,
                this::scrollToMessageId,
                this::handleMessageClick,
                this::toggleMessageSelection,
                profiler,
                name),
            selection.ids());
    profile = ChatProfileBitmap.load(c, photoPath, name, Math.round(px(132f)), ACCENT);
    adapter.setChatProfile(profile);
    chatHeader = new ChatHeaderComponent(
        c, chatName, listener, chatStatusBarBackground, headerBackground, transparent,
        profile, backIcon,
        voiceCallIcon, videoCallIcon, moreIcon);
    setBackgroundColor(0xFFF7F9FB);
    setClickable(true);
    setFocusableInTouchMode(true);
  }

  public void setInsets(int top, int bottom, int ime, boolean visible) {
    int nextTop = Math.max(0, top);
    int nextBottom = Math.max(0, bottom);
    boolean structureChanged = topInset != nextTop || bottomInset != nextBottom;
    topInset = nextTop;
    bottomInset = nextBottom;
    imeInset = Math.max(0, ime);
    imeVisible = visible;
    if (getWidth() <= 0) return;
    if (structureChanged || input == null || list == null) build();
    else applyKeyboardInsets();
  }

  public void setFloatingCallInset(int inset) {
    int next = Math.max(0, inset);
    if (floatingCallInset == next) return;
    floatingCallInset = next;
    if (getWidth() > 0 && getHeight() > 0) build();
  }

  public void setPresence(String value) {
    chatHeader.setPresence(value);
    invalidate();
  }

  public void showStatus(String value) {
    statusValue = value == null ? "" : value;
    adapter.submit(new ArrayList<>());
    if (status != null) status.setText(statusValue).setVisible(!statusValue.isEmpty());
    if (list != null) list.setVisible(false);
    invalidate();
  }

  public boolean submitMessages(List<MessageEntity> values) {
    return submitMessagesInternal(values, null);
  }

  private boolean submitMessagesInternal(
      List<MessageEntity> values, ChatMessageAdapter.PreparedSubmission prepared) {
    int oldCount = adapter.getItemCount();
    int firstVisible = list == null ? -1 : list.getFirstVisiblePosition();
    int lastVisible = list == null ? -1 : list.getLastVisiblePosition();
    float availableWidth = getMessageLayoutWidth();
    float oldScrollOffset = list == null ? 0f : list.getScrollOffset();
    float oldAnchorStart = firstVisible < 0
        ? 0f : adapter.contentStartAt(firstVisible, availableWidth);
    float anchorPixelOffset = oldScrollOffset - oldAnchorStart;
    String anchorId = adapter.messageIdAt(firstVisible);
    String oldFirstId = adapter.messageIdAt(0);
    String oldLastId = adapter.messageIdAt(oldCount - 1);
    boolean wasNearBottom = oldCount == 0 || lastVisible >= oldCount - 2;
    boolean changed = prepared == null
        ? adapter.submit(values) : adapter.applyPreparedSubmission(prepared);
    boolean selectionChanged = selection.retainLoaded(adapter);
    if (selectionChanged) refreshMessageSelectionHeader();
    boolean empty = adapter.getItemCount() == 0;
    statusValue = "";
    if (status != null) status.setText("").setVisible(false);
    if (list != null) {
      list.setVisible(!empty).setEnabled(!empty);
      if (!empty && changed) {
        boolean prepended = oldFirstId != null && adapter.indexOfMessage(oldFirstId) > 0;
        String newLastId = adapter.messageIdAt(adapter.getItemCount() - 1);
        boolean appended = oldLastId != null && !oldLastId.equals(newLastId);
        int anchorPosition = adapter.indexOfMessage(anchorId);
        if (oldCount == 0 || (wasNearBottom && appended && !prepended)) {
          int newestPosition = adapter.getItemCount() - 1;
          // The data refresh already binds the visible range. Do not recycle and bind it again
          // when the entire incremental window already fits on screen.
          if (list.getLastVisiblePosition() < newestPosition) {
            list.scrollToPosition(newestPosition);
          }
        } else if (anchorPosition >= 0) {
          float desiredOffset = adapter.contentStartAt(anchorPosition, availableWidth)
              + anchorPixelOffset;
          float delta = desiredOffset - list.getScrollOffset();
          if (Math.abs(delta) >= .5f) list.scrollBy(0f, delta);
          if (prepended && profiler != null) {
            profiler.paginationAnchor(
                anchorId, firstVisible, anchorPosition, anchorPixelOffset, delta);
          }
        }
      }
      if (pendingPinnedScrollMessageId != null) {
        int pinnedPosition = adapter.indexOfMessage(pendingPinnedScrollMessageId);
        if (pinnedPosition >= 0) {
          adapter.rippleMessage(pendingPinnedScrollMessageId);
          list.stopScroll();
          list.scrollToPosition(pinnedPosition);
          pendingPinnedScrollMessageId = null;
        }
      }
      if (pendingReplyScrollMessageId != null) {
        int replyPosition = adapter.indexOfMessage(pendingReplyScrollMessageId);
        if (replyPosition >= 0) {
          adapter.rippleMessage(pendingReplyScrollMessageId);
          list.stopScroll();
          list.scrollToPosition(replyPosition);
          pendingReplyScrollMessageId = null;
        } else {
          String requestedId = pendingReplyScrollMessageId;
          post(() -> {
            if (requestedId.equals(pendingReplyScrollMessageId)) {
              listener.onReplyTargetRequested(requestedId);
            }
          });
        }
      }
    }
    if (changed && list != null && !MediaPreviewCache.isDecodingPaused()) {
      adapter.prefetchMediaAround(
          list.getFirstVisiblePosition(), list.getLastVisiblePosition(), 1, availableWidth);
    }
    if (searchVisible && changed) refreshSearchMatches(false);
    if (changed || selectionChanged) invalidate();
    return changed;
  }

  public float getMessageLayoutWidth() {
    return Math.max(1f, getWidth() - px(66f));
  }

  /** Keeps only the newest prepared update and applies it as soon as list motion becomes idle. */
  public boolean deferUntilMessageScrollIdle(Runnable action) {
    if (!messageScrollActive || action == null) return false;
    pendingScrollIdleAction = action;
    return true;
  }

  /** Keeps the composer workflow anchored to the newest message. */
  public void scrollToBottom() {
    forceBottomOnNextMessageSubmission = true;
    if (list == null || adapter.getItemCount() == 0) return;
    list.stopScroll();
    list.scrollToPosition(adapter.getItemCount() - 1);
    invalidate();
  }

  /** Keeps the composer focused after send without restarting an already visible IME. */
  public void restoreComposerAfterSend() {
    if (!keepKeyboardAfterSend) return;
    keepKeyboardAfterSend = false;
    post(() -> {
      if (input == null || contactBlocked) return;
      if (input.isFocused() && imeVisible) return;
      requestFocus();
      input.requestFocus();
      InputMethodManager keyboard =
          (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
      if (keyboard != null) {
        keyboard.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
      }
    });
  }

  private void dispatchComposerSend() {
    // Some component hosts transfer focus to the send button before its click callback.
    // IME visibility preserves the user's pre-tap composer state in that case.
    keepKeyboardAfterSend = input != null && (input.isFocused() || imeVisible);
    forceBottomOnNextMessageSubmission = true;
    listener.onSend();
  }

  private boolean isInsideDisplayedSendButton(float x, float y) {
    if (send == null || !send.isVisible() || !send.isEnabled()) return false;
    RectF bounds = new RectF(send.getBounds());
    if (imeVisible) bounds.offset(0f, -Math.max(0, imeInset - bottomInset));
    return bounds.contains(x, y);
  }

  private void onMediaMetricsChanged() {
    if (list == null || adapter.getItemCount() == 0) {
      adapter.refreshMeasuredRows();
      return;
    }
    int firstVisible = list.getFirstVisiblePosition();
    int lastVisible = list.getLastVisiblePosition();
    boolean keepBottom = !list.canScrollForward()
        || lastVisible >= adapter.getItemCount() - 1;
    float availableWidth = getMessageLayoutWidth();
    float oldOffset = list.getScrollOffset();
    float oldAnchorStart = firstVisible < 0
        ? 0f : adapter.contentStartAt(firstVisible, availableWidth);
    float anchorPixelOffset = oldOffset - oldAnchorStart;
    String anchorId = adapter.messageIdAt(firstVisible);

    adapter.refreshMeasuredRows();
    if (keepBottom) {
      list.scrollToPosition(adapter.getItemCount() - 1);
    } else {
      int anchorPosition = adapter.indexOfMessage(anchorId);
      if (anchorPosition >= 0) {
        float desiredOffset = adapter.contentStartAt(anchorPosition, availableWidth)
            + anchorPixelOffset;
        list.scrollBy(0f, desiredOffset - list.getScrollOffset());
      }
    }
    invalidate();
  }

  public void prepareMessageMetrics(List<MessageEntity> values, float availableWidth) {
    adapter.prepareMetrics(values, availableWidth);
  }

  /** Builds render models, dates, signatures, and row metrics without mutating the AAR list. */
  public PreparedMessages prepareMessages(List<MessageEntity> values, float availableWidth) {
    adapter.prepareMetrics(values, availableWidth);
    return new PreparedMessages(adapter.prepareSubmission(values));
  }

  /** Applies an off-thread preparation result. Call only from the main thread. */
  public boolean submitPreparedMessages(PreparedMessages prepared) {
    if (prepared == null) return false;
    return submitMessages(prepared.submission);
  }

  private boolean submitMessages(ChatMessageAdapter.PreparedSubmission prepared) {
    return submitMessagesInternal(null, prepared);
  }

  public void indexReplyTargets(List<MessageEntity> values) {
    adapter.indexReplyTargets(values);
  }

  public void trackLocationRender(
      String clientMessageId,
      String traceId,
      long pressedElapsedMs,
      long pressedWallMs) {
    adapter.trackLocationRender(
        clientMessageId, traceId, pressedElapsedMs, pressedWallMs);
  }

  /** Displays one active pin while retaining the full collection for navigation. */
  public void setPinnedMessages(List<MessageEntity> values) {
    String previousPinsSignature = pinnedMessagesSignature(pinnedMessages);
    String activeId = pinnedMessages.isEmpty() || pinnedMessageIndex >= pinnedMessages.size()
        ? "" : pinnedMessageKey(pinnedMessages.get(pinnedMessageIndex));
    pinnedMessages.clear();
    if (values != null) pinnedMessages.addAll(values);
    pinnedMessages.sort((first, second) -> {
      long firstSent = first == null ? 0L : first.sentTime;
      long secondSent = second == null ? 0L : second.sentTime;
      int byTime = Long.compare(firstSent, secondSent);
      return byTime != 0 ? byTime
          : pinnedMessageKey(first).compareTo(pinnedMessageKey(second));
    });
    pinnedMessageIndex = Math.max(0, pinnedMessages.size() - 1);
    if (!activeId.isEmpty()) {
      for (int index = 0; index < pinnedMessages.size(); index++) {
        if (activeId.equals(pinnedMessageKey(pinnedMessages.get(index)))) {
          pinnedMessageIndex = index;
          break;
        }
      }
    }
    boolean changed = pinnedAdapter.submit(activePinnedMessages());
    boolean collectionChanged = !previousPinsSignature.equals(
        pinnedMessagesSignature(pinnedMessages));
    if (!changed && !collectionChanged) return;
    if (getWidth() > 0 && getHeight() > 0) build();
    else invalidate();
  }

  private List<MessageEntity> activePinnedMessages() {
    if (pinnedMessages.isEmpty()) return Collections.emptyList();
    pinnedMessageIndex = Math.max(0, Math.min(pinnedMessageIndex, pinnedMessages.size() - 1));
    return Collections.singletonList(pinnedMessages.get(pinnedMessageIndex));
  }

  private static String pinnedMessageKey(MessageEntity message) {
    if (message == null) return "";
    if (message.messageId != null && !message.messageId.trim().isEmpty()) return message.messageId;
    return message.clientMessageId == null ? "" : message.clientMessageId;
  }

  private static String pinnedMessagesSignature(List<MessageEntity> messages) {
    StringBuilder value = new StringBuilder();
    for (MessageEntity message : messages) {
      value.append(pinnedMessageKey(message)).append('\u0001')
          .append(message == null ? 0L : message.sentTime).append('\u0001')
          .append(message == null ? null : message.pinnedAt).append('\u0001')
          .append(message == null ? null : message.pinnedBy).append('\u0002');
    }
    return value.toString();
  }

  private void movePinnedMessage(int direction) {
    if (pinnedMessages.isEmpty()) return;
    int count = pinnedMessages.size();
    pinnedMessageIndex = (pinnedMessageIndex + direction + count) % count;
    MessageEntity active = pinnedMessages.get(pinnedMessageIndex);
    pinnedAdapter.submit(Collections.singletonList(active));
    pinnedMessageTab.updateCount(pinnedMessageIndex, count);
    pinnedMessageTab.scrollToOnlyRow();
    scrollToPinnedMessage(active);
    invalidate();
  }

  public void setOlderMessagesState(boolean loading, boolean canLoad) {
    loadingOlderMessages = loading;
    canLoadOlderMessages = canLoad;
    updateOlderLoadingChrome();
  }

  public String getDraft() {
    return input == null ? composer.draft : input.getText().trim();
  }

  public void clearDraft() {
    composer.draft = "";
    if (input != null) input.clear();
    updateComposerActionIcon();
  }

  public void showReply(String value) {
    composer.replyPreview = value == null ? "" : value;
    composer.replySender = "";
    composer.replyTargetId = "";
    composer.replyContent = ReplyContent.text(composer.replyPreview);
    updateReply();
  }

  public void showReply(MessageEntity message) {
    composer.replySender = adapter.replyPreviewSender(message);
    composer.replyTargetId = message == null ? "" : message.messageId;
    composer.replyContent = message == null ? null : adapter.replyPreviewContent(message);
    composer.replyPreview = composer.replyContent == null ? ""
        : ReplyContent.TEXT.equals(composer.replyContent.type)
            ? composer.replyContent.text : "attachment";
    updateReply();
  }

  public void clearReply() {
    boolean hadReply = composer.hasReply();
    composer.clearReply();
    if (!hadReply) return;
    if (replyText != null) replyText.setVisible(false);
    if (composerReplyPreview != null) composerReplyPreview.hide();
    if (input != null && list != null && renderedComposerHeight >= 0f) {
      applyComposerHeight(renderedComposerHeight);
    } else {
      updateReply();
    }
    invalidate();
  }

  public boolean isSelectingMessages() {
    return selection.isSelecting();
  }

  public boolean clearMessageSelection() {
    if (!selection.isSelecting()) return false;
    List<String> previousIds = selection.clear();
    for (String id : previousIds) {
      int position = adapter.indexOfMessage(id);
      if (position >= 0) adapter.notifyItemChanged(position);
    }
    selectionOverlay.clear();
    listener.onMessageSelectionChanged(false);
    invalidate();
    return true;
  }

  private void toggleMessageSelection(MessageEntity message) {
    if (!selection.toggle(message)) return;
    String id = ChatSelectionController.idOf(message);
    int position = adapter.indexOfMessage(id);
    if (position >= 0) adapter.notifyItemChanged(position);
    refreshMessageSelectionHeader();
  }

  private void handleMessageClick(MessageEntity message) {
    if (isSelectingMessages()) toggleMessageSelection(message);
    else listener.onMessageClick(message);
  }

  private void refreshMessageSelectionHeader() {
    selectionOverlay.clear();
    if (isSelectingMessages() && getWidth() > 0) {
      selectionHeader.build(selectionOverlay, selectedMessages(), getWidth(),
          topInset + floatingCallInset, getWidth() / 1080f);
    }
    listener.onMessageSelectionChanged(isSelectingMessages());
    invalidate();
  }

  private List<MessageEntity> selectedMessages() {
    return selection.selectedMessages(adapter);
  }

  public void showAttachmentPreview(String type, String name) {
    composer.attachmentType = type == null ? "File" : type;
    composer.attachmentName = name == null || name.trim().isEmpty() ? "Attachment" : name;
    if (getWidth() > 0) build();
  }

  public void clearAttachmentPreview() {
    composer.clearAttachment();
    if (getWidth() > 0) build();
  }

  public boolean dismissAttachmentPanel() {
    if (!composer.attachmentPanelVisible) return false;
    if (input != null) composer.draft = input.getText();
    composer.attachmentPanelVisible = false;
    build();
    return true;
  }

  public void setDraft(String value) {
    composer.draft = value == null ? "" : value;
    if (input != null) {
      input.setText(composer.draft);
      input.setSelection(composer.draft.length());
      input.requestFocus();
    }
    updateComposerActionIcon();
  }

  private void updateReply() {
    if (getWidth() > 0 && getHeight() > 0) {
      build();
    } else {
      invalidate();
    }
  }

  private float composerReplyHeight(float scale) {
    if (!composer.hasReply()) return 0f;
    ReplyContent content = composer.replyContent == null
        ? ReplyContent.text(composer.replyPreview) : composer.replyContent;
    float boxWidth = composerReplyWidth();
    return adapter.replyPreviewHeight(composer.replySender, content, boxWidth);
  }

  private float composerReplyWidth() {
    if (!composer.hasReply()) return 0f;
    return Math.max(1f, getWidth() - px(88f));
  }

  private void toggleAttachmentPanel() {
    composer.attachmentPanelVisible = !composer.attachmentPanelVisible;
    build();
  }

  private void selectAttachment(String type) {
    composer.attachmentPanelVisible = false;
    build();
    listener.onAttachmentSelected(type);
  }

  @Override
  protected void onSizeChanged(int w, int h, int ow, int oh) {
    super.onSizeChanged(w, h, ow, oh);
    if (w > 0 && h > 0) build();
  }

  private void build() {
    int previousCount = adapter.getItemCount();
    int previousFirstVisible = list == null ? -1 : list.getFirstVisiblePosition();
    int previousLastVisible = list == null ? -1 : list.getLastVisiblePosition();
    String previousAnchorId = adapter.messageIdAt(previousFirstVisible);
    boolean wasNearBottom = previousCount == 0 || previousLastVisible >= previousCount - 2;
    if (input != null) composer.draft = input.getText();
    if (searchInput != null) searchDraft = searchInput.getText();
    bg.clear();
    content.clear();
    overlay.clear();
    selectionOverlay.clear();
    composerActionIcon = null;
    input = null;
    searchInput = null;
    searchMatchCount = null;
    audioRecordingTime = null;
    audioRecordingWaveform = null;
    composerReplyPreview = null;
    attachmentPreviewBackground = null;
    attachmentPreviewTextComponent = null;
    attachmentPreviewRemove = null;
    attachmentPreviewSend = null;
    float attachmentPanelHeight = composer.attachmentPanelVisible ? px(308f) : 0;
    float w = getWidth();
    float scale = w / 1080f;
    float top = topInset + floatingCallInset;
    float screenBottom = getHeight() - bottomInset;
    float attachmentPanelTop = screenBottom - attachmentPanelHeight;
    float composerBottom = attachmentPanelTop - 40f * scale;
    float composerHeight = composer.recording
        ? 134f * scale : composerHeightForText(composer.draft, scale);
    float composerTop = composerBottom - composerHeight;
    float microphoneBottom = attachmentPanelTop - 40f * scale;
    float microphoneTop = microphoneBottom - 134f * scale;
    renderedComposerHeight = composerHeight;
    headerBottom = top + 170f * scale;
    bg.add(
        new Image.Builder(
                getContext(), "conversation_background", conversationBackground,
                new RectF(0, headerBottom, w, getHeight()))
            .setScaleType(Image.ScaleType.FIT_XY));
    headerBottom = chatHeader.build(bg, content, w, top, scale);
    if (searchVisible) {
      float searchTop = headerBottom;
      float searchBottom = searchTop + 132f * scale;
      content.add(new Image.Builder(getContext(), "search_tab_background", white,
          new RectF(0f, searchTop, w, searchBottom)).setScaleType(Image.ScaleType.FIT_XY));
      content.add(new Image.Builder(getContext(), "search_tab_divider", divider,
          new RectF(0f, searchBottom - px(2.75f), w, searchBottom))
          .setScaleType(Image.ScaleType.FIT_XY));
      searchInput = content.add(new TextField.Builder(getContext(), "message_search",
          new RectF(44f * scale, searchTop + 17f * scale,
              590f * scale, searchBottom - 17f * scale))
          .setText(searchDraft)
          .setHint("Search messages")
          .setInputType(InputType.TYPE_CLASS_TEXT)
          .setImeOptions(EditorInfo.IME_ACTION_SEARCH)
          .setFont(NativeFonts.INTER)
          .setFontVariations(FontVariation.REGULAR)
          .setTextSizePx(38f * scale)
          .setTextColor(PRIMARY)
          .setHintColor(SECONDARY)
          .setCursorColor(ACCENT)
          .setBackgroundColor(0xFFF3F6F8, Color.WHITE)
          .setStrokeColor(0xFFD8E1E8, ACCENT)
          .setCornerRadiusPx(42f * scale)
          .setPaddingPx(32f * scale, 16f * scale)
          .setOnTextChangedListener((id, value) -> {
            searchDraft = value == null ? "" : value;
            searchMessages(searchDraft);
          }));
      searchMatchCount = text(content, "search_match_count", "0/0",
          new RectF(600f * scale, searchTop + 17f * scale,
              716f * scale, searchBottom - 17f * scale),
          31f * scale, SECONDARY, FontVariation.MEDIUM, Text.Alignment.CENTER);
      button(content, "previous_search_match", transparent, "↑",
          new RectF(720f * scale, searchTop + 17f * scale,
              824f * scale, searchBottom - 17f * scale), PRIMARY,
          id -> moveSearchMatch(-1));
      button(content, "next_search_match", transparent, "↓",
          new RectF(828f * scale, searchTop + 17f * scale,
              932f * scale, searchBottom - 17f * scale), PRIMARY,
          id -> moveSearchMatch(1));
      button(content, "close_search", transparent, "×",
          new RectF(936f * scale, searchTop + 17f * scale,
              1058f * scale, searchBottom - 17f * scale), PRIMARY,
          id -> dismissSearch());
      refreshSearchMatches(false);
      headerBottom = searchBottom;
    }
    float pinnedHeight = pinnedTabHeight();
    if (pinnedHeight > 0f) {
      pinnedMessageTab.build(
          content, headerBottom, w, pinnedMessageIndex, pinnedMessages.size());
    }
    float replyHeight = composerReplyHeight(scale);
    float previewHeight = composer.hasAttachment() ? px(198f) : 0f;
    float messageComposerGap = 20f;
    float listBottom = composerTop - replyHeight - previewHeight - messageComposerGap;
    baseListBottom = listBottom;
    float messageListTop = messageListTop();
    ComponentList.Builder<MessageEntity> messageListBuilder =
            new ComponentList.Builder<MessageEntity>(
                    getContext(), "messages", new RectF(0, messageListTop, w, listBottom))
                .setOrientation(ComponentList.Orientation.VERTICAL)
                .setItemSizeProvider(
                    (message, position) -> adapter.rowHeight(message, position, w - px(66f)))
                .setPaddingPx(0, px(22f), 0, px(33f))
                .setAdapter(adapter)
                .setClipToBounds(true)
                .setScrollBarEnabled(true)
                .setOverscrollEnabled(false)
                .setOnItemLongClickListener(
                    (componentList, message, position) -> {
                      toggleMessageSelection(message);
                      return true;
                    })
                .setOnItemClickListener(
                    (componentList, message, position) -> {
                      // The row-wide fallback exists only for multi-selection. Normal media/file
                      // actions must remain scoped to the bubble or its inner preview bounds.
                      if (isSelectingMessages()) toggleMessageSelection(message);
                    });
    list = messageListBuilder.build(this);
    content.add(new TimedMessageListComponent(list));
    status =
        text(
            content,
            "status",
            statusValue,
            new RectF(px(55f), messageListTop + px(55f), w - px(55f), messageListTop + px(330f)),
            sp(16),
            SECONDARY,
            FontVariation.REGULAR,
            Text.Alignment.CENTER);
    float olderLoadingHeight = olderLoadingHeight();
    float olderLoadingTop = headerBottom + pinnedTabHeight();
    olderLoadingBackground =
        overlay.add(
            new Image.Builder(
                    getContext(), "older_loading_background", white,
                    new RectF(0, olderLoadingTop, w, olderLoadingTop + olderLoadingHeight))
                .setScaleType(Image.ScaleType.FIT_XY));
    float olderProgressSize = px(49.5f);
    float olderGroupWidth = px(599.5f);
    float olderGroupLeft = (w - olderGroupWidth) / 2f;
    float olderProgressTop = olderLoadingTop + (olderLoadingHeight - olderProgressSize) / 2f;
    olderProgress =
        overlay.add(
            new Progress.Builder(
                    getContext(), "older_message_progress",
                    new RectF(olderGroupLeft, olderProgressTop,
                        olderGroupLeft + olderProgressSize,
                        olderProgressTop + olderProgressSize))
                .setStyle(Progress.Style.CIRCULAR)
                .setMode(Progress.Mode.INDETERMINATE)
                .setProgressColor(ACCENT)
                .setTrackColor(0x22019CC4)
                .setThickness(px(5.5f))
                .setIndeterminateDuration(850L));
    olderStatus =
        text(
            overlay,
            "older_status",
            loadingOlderMessages ? "Loading older messages..." : "",
            new RectF(olderGroupLeft + olderProgressSize + px(27.5f), olderLoadingTop,
                olderGroupLeft + olderGroupWidth, olderLoadingTop + olderLoadingHeight),
            sp(13),
            SECONDARY,
            FontVariation.MEDIUM,
            Text.Alignment.START);
    updateOlderLoadingChrome();
    float replyTop = !composer.hasReply()
        ? composerTop - previewHeight
        : listBottom + messageComposerGap;
    replyText =
        text(
            overlay,
            "reply",
            composer.replyPreview,
            new RectF(px(44f), replyTop, w - px(44f), replyTop + Math.max(px(2.75f), replyHeight)),
            sp(13),
            SECONDARY,
            FontVariation.REGULAR,
            Text.Alignment.START);
    replyText.setVisible(composer.hasReply());
    if (composer.replyContent != null) {
      Typeface typeface = ResourcesCompat.getFont(getContext(), NativeFonts.INTER);
      if (typeface == null) typeface = Typeface.DEFAULT;
      composerReplyPreview = overlay.add(new ComposerReplyPreviewComponent(
          getContext(), "composer_reply_preview", documentIcon, profile, typeface,
          28.8f, 31.671f, this::scrollToMessageId));
      float composerReplyWidth = composerReplyWidth();
      composerReplyPreview.bind(
          new RectF(px(44f), replyTop, px(44f) + composerReplyWidth,
              replyTop + replyHeight),
          composer.replySender, composer.replyContent, composer.replyTargetId);
      replyText.setVisible(false);
    }
    if (composer.hasAttachment()) {
      float previewTop = composerTop - previewHeight;
      attachmentPreviewBackground = overlay.add(
          new Image.Builder(
                  getContext(), "attachment_preview_bg", white,
                  new RectF(0, previewTop, w, composerTop))
              .setScaleType(Image.ScaleType.FIT_XY));
      attachmentPreviewTextComponent = text(
          overlay,
          "attachment_preview_text",
          composer.attachmentType + "\n" + composer.attachmentName,
          new RectF(px(44f), previewTop + px(22f), w - px(363f), composerTop - px(22f)),
          sp(13),
          PRIMARY,
          FontVariation.SEMI_BOLD,
          Text.Alignment.START);
      attachmentPreviewRemove = button(
          overlay,
          "attachment_preview_remove",
          white,
          "×",
          new RectF(w - px(352f), previewTop + px(33f), w - px(225.5f), composerTop - px(33f)),
          SECONDARY,
          id -> {
            clearAttachmentPreview();
            listener.onAttachmentPreviewRemoved();
          });
      attachmentPreviewSend = button(
          overlay,
          "attachment_preview_send",
          accent,
          "Send",
          new RectF(w - px(214.5f), previewTop + px(33f), w - px(22f), composerTop - px(33f)),
          Color.WHITE,
          id -> dispatchComposerSend());
    }
    composerBackground = overlay.add(
        new ComposerBackgroundComponent(
            "composer_background",
            new RectF(30f * scale, composerTop, 898f * scale, composerBottom),
            64f * scale,
            3f * scale));
    if (composer.recording) {
      iconButton(
          overlay,
          "recording_cancel",
          selectionDeleteIcon,
          new RectF(65f * scale, composerBottom - 94f * scale,
              122f * scale, composerBottom - 37f * scale),
          new RectF(43f * scale, composerBottom - 116f * scale,
              144f * scale, composerBottom - 15f * scale),
          id -> listener.onAudioRecordingCancel());
      audioRecordingWaveform = overlay.add(
          new AudioRecordingComponent("audio_recording_waveform")
              .bind(new RectF(155f * scale, composerTop + 8f * scale,
                  860f * scale, composerBottom - 8f * scale), composer.recordingElapsedMs));
      audioRecordingTime = text(
          overlay,
          "audio_recording_time",
          formatRecordingElapsed(composer.recordingElapsedMs),
          new RectF(180f * scale, composerTop + 8f * scale,
              300f * scale, composerBottom - 8f * scale),
          34f * scale,
          PRIMARY,
          FontVariation.REGULAR,
          Text.Alignment.START);
    } else {
      input =
        overlay.add(
            new TextField.Builder(
                    getContext(),
                    "composer",
                    new RectF(155f * scale, composerTop + 8f * scale,
                        885f * scale, composerBottom - 8f * scale))
                .setText(composer.draft)
                .setHint("Message")
                .setMaxLength(4000)
                .setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE)
                .setImeOptions(EditorInfo.IME_ACTION_NONE
                    | EditorInfo.IME_FLAG_NO_ENTER_ACTION)
                .setFont(NativeFonts.INTER)
                .setFontVariations(FontVariation.REGULAR)
                .setTextSizePx(sp(16))
                .setTextColor(PRIMARY)
                .setHintColor(SECONDARY)
                .setCursorColor(ACCENT)
                .setBackgroundColor(Color.TRANSPARENT, Color.TRANSPARENT)
                .setStrokeColor(Color.TRANSPARENT, Color.TRANSPARENT)
                .setStrokeWidthPx(0)
                .setCornerRadiusPx(0)
                .setPaddingPx(0, px(22f))
                .setOnTextChangedListener(
                    (id, value) -> {
                      composer.draft = value;
                      listener.onTypingChanged(value);
                      updateComposerActionIcon();
                      scheduleComposerResize();
                    }));
      input.setMultilineBottomEndInsetPx(189f * scale);
      iconButton(
        overlay,
        "emoji",
        emojiIcon,
        new RectF(68f * scale, composerBottom - 94f * scale,
            123f * scale, composerBottom - 39f * scale),
        new RectF(46f * scale, composerBottom - 116f * scale,
            145f * scale, composerBottom - 17f * scale),
        id -> openEmojiKeyboard());
      iconButton(
        overlay,
        "attachment",
        attachmentIcon,
        new RectF(712f * scale, composerBottom - 94f * scale,
            767f * scale, composerBottom - 39f * scale),
        new RectF(690f * scale, composerBottom - 116f * scale,
            789f * scale, composerBottom - 17f * scale),
        id -> toggleAttachmentPanel());
      iconButton(
        overlay,
        "camera",
        cameraIcon,
        new RectF(805f * scale, composerBottom - 94f * scale,
            860f * scale, composerBottom - 39f * scale),
        new RectF(783f * scale, composerBottom - 116f * scale,
            882f * scale, composerBottom - 17f * scale),
        id -> listener.onCameraSelected());
    }
    composerActionIcon =
        overlay.add(
            new Image.Builder(
                    getContext(), "send_icon",
                    composer.recording || hasComposerContent() ? sendIcon : microphoneIcon,
                    new RectF(916f * scale, microphoneTop,
                        1050f * scale, microphoneBottom))
                .setScaleType(Image.ScaleType.FIT_CENTER));
    send =
        overlay.add(
            new Button.Builder(
                    getContext(), "send_touch", transparent, "",
                    new RectF(894f * scale, microphoneTop - 12f * scale,
                        1072f * scale, microphoneBottom + 12f * scale))
                .setImageScaleType(Image.ScaleType.FIT_XY)
                .setCornerRadiusPx(0)
                .setRippleEnabled(true)
                .setRippleColor(0x16019CC4)
                .setOnClickListener(id -> {
                  if (composer.recording) listener.onAudioRecordingSend();
                  else if (hasComposerContent()) dispatchComposerSend();
                  else listener.onAudioRecordingStart();
                }));
    if (composer.attachmentPanelVisible) {
      overlay.add(
          new Image.Builder(
                  getContext(),
                  "attachment_panel_bg",
                  white,
                  new RectF(0, attachmentPanelTop, w, screenBottom))
              .setScaleType(Image.ScaleType.FIT_XY));
      overlay.add(
          new Image.Builder(
                  getContext(),
                  "attachment_panel_line",
                  divider,
                  new RectF(0, attachmentPanelTop, w, attachmentPanelTop + px(2.75f)))
              .setScaleType(Image.ScaleType.FIT_XY));
      String[] attachmentTypes = {"Image", "Video", "File", "Location"};
      float gap = px(22f), side = px(27.5f), optionWidth = (w - side * 2 - gap * 3) / 4f;
      for (int i = 0; i < attachmentTypes.length; i++) {
        String type = attachmentTypes[i];
        float left = side + i * (optionWidth + gap);
        button(
            overlay,
            "attachment_" + type.toLowerCase(Locale.US),
            attachmentOption,
            type,
            new RectF(left, attachmentPanelTop + px(44f), left + optionWidth, screenBottom - px(44f)),
            ACCENT,
            id -> selectAttachment(type));
      }
    }
    if (contactBlocked) {
      overlay.add(new Image.Builder(getContext(), "blocked_composer_background", white,
          new RectF(0f, composerTop - px(12f), w, screenBottom))
          .setScaleType(Image.ScaleType.FIT_XY));
      float blockedGap = 22f * scale;
      float blockedSide = 44f * scale;
      float blockedWidth = (w - blockedSide * 2f - blockedGap) / 2f;
      button(overlay, "blocked_delete_chat", attachmentOption, "Delete chat",
          new RectF(blockedSide, composerTop, blockedSide + blockedWidth, composerBottom),
          0xFFD32F2F, id -> listener.onBlockedDeleteChat());
      button(overlay, "blocked_unblock", accent, "Unblock",
          new RectF(blockedSide + blockedWidth + blockedGap, composerTop,
              w - blockedSide, composerBottom),
          Color.WHITE, id -> listener.onBlockedUnblock());
    }
    if (isSelectingMessages()) {
      selectionHeader.build(selectionOverlay, selectedMessages(), w, top, scale);
    }
    boolean empty = adapter.getItemCount() == 0;
    list.setVisible(!empty);
    status.setVisible(empty && !statusValue.isEmpty());
    if (!empty) {
      int anchorPosition = adapter.indexOfMessage(previousAnchorId);
      if (forceBottomOnNextMessageSubmission || wasNearBottom || anchorPosition < 0) {
        list.scrollToPosition(adapter.getItemCount() - 1);
        forceBottomOnNextMessageSubmission = false;
      }
      else list.scrollToPosition(anchorPosition);
    }
    applyKeyboardInsets();
    invalidate();
  }


  private void applyKeyboardInsets() {
    if (list == null || getWidth() <= 0) return;
    float shift = imeVisible ? -Math.max(0, imeInset - bottomInset) : 0;
    overlay.setTranslationY(shift);
    float listBottom = Math.max(headerBottom + px(2.75f), baseListBottom + shift);
    RectF nextBounds = new RectF(0, messageListTop(), getWidth(), listBottom);
    RectF currentBounds = list.getBounds();
    if (!sameBounds(currentBounds, nextBounds)) {
      int itemCount = adapter.getItemCount();
      int lastVisible = list.getLastVisiblePosition();
      boolean wasNearBottom = itemCount == 0 || lastVisible >= itemCount - 2;
      list.setRegion(nextBounds);
      // Resizing the viewport does not change ComponentList's scroll offset. Keep a
      // conversation that was at the bottom pinned to its newest message when the IME
      // reduces the available height; otherwise the final rows remain below the keyboard.
      if (wasNearBottom && itemCount > 0) list.scrollToPosition(itemCount - 1);
    }
    invalidate();
  }

  private float composerHeightForText(String value, float scale) {
    float minimumHeight = 134f * scale;
    String measuredValue = value == null || value.isEmpty() ? "Message" : value;
    int textWidth = Math.max(1, (int) Math.floor(730f * scale));
    int bottomEndInset = Math.max(0, Math.round(189f * scale));
    StaticLayout layout = composerTextLayout(measuredValue, textWidth, bottomEndInset);
    float visibleTextHeight = layout.getHeight();
    if (layout.getLineCount() > MAX_VISIBLE_COMPOSER_LINES) {
      visibleTextHeight = layout.getLineBottom(MAX_VISIBLE_COMPOSER_LINES - 1);
    }
    float desiredHeight =
        (float) Math.ceil(visibleTextHeight + px(44f) + 16f * scale);

    float top = topInset + floatingCallInset;
    float screenBottom = getHeight() - bottomInset;
    float attachmentPanelHeight = composer.attachmentPanelVisible ? px(308f) : 0f;
    float composerBottom = screenBottom - attachmentPanelHeight - 40f * scale;
    float headerEdge = top + 170f * scale;
    float composerChromeAbove = px(198f) + 20f;
    composerChromeAbove += composerReplyHeight(scale);
    if (composer.hasAttachment()) composerChromeAbove += px(198f);
    float maximumHeight = Math.max(
        minimumHeight, composerBottom - headerEdge - composerChromeAbove);
    return Math.min(maximumHeight, Math.max(minimumHeight, desiredHeight));
  }

  private StaticLayout composerTextLayout(String value, int width, int bottomEndInset) {
    StaticLayout layout =
        StaticLayout.Builder.obtain(value, 0, value.length(), composerMeasurePaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build();
    if (bottomEndInset <= 0) return layout;
    int lineCount = Math.max(1, layout.getLineCount());
    int[] rightIndents = new int[lineCount];
    rightIndents[lineCount - 1] = Math.min(width - 1, bottomEndInset);
    return StaticLayout.Builder.obtain(value, 0, value.length(), composerMeasurePaint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setIndents(null, rightIndents)
        .build();
  }

  private void scheduleComposerResize() {
    if (getWidth() <= 0 || getHeight() <= 0) return;
    float desiredHeight = composerHeightForText(composer.draft, getWidth() / 1080f);
    if (Math.abs(desiredHeight - renderedComposerHeight) < .5f) {
      invalidate();
      return;
    }
    if (input != null && composerBackground != null && list != null) {
      applyComposerHeight(desiredHeight);
      invalidate();
      return;
    }
    if (composerResizePosted) return;
    composerResizePosted = true;
    post(
        () -> {
          composerResizePosted = false;
          if (input == null || getWidth() <= 0 || getHeight() <= 0) return;
          composer.draft = input.getText();
          float currentDesiredHeight =
              composerHeightForText(composer.draft, getWidth() / 1080f);
          if (Math.abs(currentDesiredHeight - renderedComposerHeight) < .5f) return;
          applyComposerHeight(currentDesiredHeight);
        });
  }

  private boolean hasComposerContent() {
    return !composer.draft.isEmpty() || composer.hasAttachment();
  }

  private void updateComposerActionIcon() {
    if (composerActionIcon != null) {
      composerActionIcon.setBitmap(composer.recording || hasComposerContent()
          ? sendIcon : microphoneIcon);
      invalidate();
    }
  }

  public void startAudioRecording() {
    if (composer.recording) return;
    composer.recording = true;
    composer.recordingElapsedMs = 0L;
    composer.attachmentPanelVisible = false;
    InputMethodManager keyboard =
        (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    if (keyboard != null) keyboard.hideSoftInputFromWindow(getWindowToken(), 0);
    build();
  }

  public void updateAudioRecordingState(long elapsedMs, int amplitude) {
    composer.recordingElapsedMs = Math.max(0L, elapsedMs);
    if (audioRecordingTime != null) {
      audioRecordingTime.setText(formatRecordingElapsed(composer.recordingElapsedMs));
    }
    if (audioRecordingWaveform != null) {
      audioRecordingWaveform.setRecordingSample(composer.recordingElapsedMs, amplitude);
    }
    invalidate();
  }

  public void stopAudioRecording() {
    if (!composer.recording) return;
    composer.recording = false;
    composer.recordingElapsedMs = 0L;
    build();
  }

  public void setAudioPlaybackState(
      String messageId, boolean playing, long progressMs, long durationMs) {
    adapter.setAudioPlaybackState(messageId, playing, progressMs, durationMs);
    invalidate();
  }

  private static String formatRecordingElapsed(long elapsedMs) {
    long totalSeconds = Math.max(0L, elapsedMs / 1000L);
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L);
  }

  private void applyComposerHeight(float composerHeight) {
    if (input == null || composerBackground == null || list == null) return;
    float width = getWidth();
    float scale = width / 1080f;
    float screenBottom = getHeight() - bottomInset;
    float attachmentPanelHeight = composer.attachmentPanelVisible ? px(308f) : 0f;
    float attachmentPanelTop = screenBottom - attachmentPanelHeight;
    float composerBottom = attachmentPanelTop - 40f * scale;
    float composerTop = composerBottom - composerHeight;
    float replyHeight = composerReplyHeight(scale);
    float previewHeight = composer.hasAttachment() ? px(198f) : 0f;
    float listBottom = composerTop - replyHeight - previewHeight - 20f;

    renderedComposerHeight = composerHeight;
    composerBackground.setBounds(
        new RectF(30f * scale, composerTop, 898f * scale, composerBottom));
    input.setRegion(
            new RectF(155f * scale, composerTop + 8f * scale,
                885f * scale, composerBottom - 8f * scale))
        .setMultilineBottomEndInsetPx(189f * scale);

    baseListBottom = listBottom;
    if (replyText != null && composer.hasReply()) {
      float replyTop = listBottom + 20f;
      replyText.setRegion(
          new RectF(px(44f), replyTop, width - px(44f), replyTop + replyHeight));
    }
    if (composerReplyPreview != null && composer.replyContent != null) {
      float replyTop = listBottom + 20f;
      float composerReplyWidth = composerReplyWidth();
      composerReplyPreview.bind(
          new RectF(px(44f), replyTop, px(44f) + composerReplyWidth,
              replyTop + replyHeight),
          composer.replySender, composer.replyContent, composer.replyTargetId);
    }
    if (attachmentPreviewBackground != null) {
      float previewTop = composerTop - previewHeight;
      attachmentPreviewBackground.setRegion(new RectF(0, previewTop, width, composerTop));
      attachmentPreviewTextComponent.setRegion(
          new RectF(px(44f), previewTop + px(22f), width - px(363f), composerTop - px(22f)));
      attachmentPreviewRemove.setRegion(
          new RectF(width - px(352f), previewTop + px(33f),
              width - px(225.5f), composerTop - px(33f)));
      attachmentPreviewSend.setRegion(
          new RectF(width - px(214.5f), previewTop + px(33f),
              width - px(22f), composerTop - px(33f)));
    }
    applyKeyboardInsets();
  }

  private void openEmojiKeyboard() {
    if (composer.attachmentPanelVisible) {
      if (input != null) composer.draft = input.getText();
      composer.attachmentPanelVisible = false;
      build();
    }
    if (input == null) return;
    requestFocus();
    input.requestFocus();
    post(() -> {
      if (input == null) return;
      input.requestFocus();
      InputMethodManager keyboard =
          (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
      if (keyboard != null) {
        keyboard.restartInput(this);
        keyboard.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
      }
    });
  }

  private static boolean sameBounds(RectF first, RectF second) {
    return Math.abs(first.left - second.left) < .5f
        && Math.abs(first.top - second.top) < .5f
        && Math.abs(first.right - second.right) < .5f
        && Math.abs(first.bottom - second.bottom) < .5f;
  }

  private void updateOlderLoadingChrome() {
    boolean visible = shouldShowOlderLoading();
    if (visible && list != null) list.stopScroll();
    if (olderLoadingBackground != null) olderLoadingBackground.setVisible(visible);
    if (olderProgress != null) olderProgress.setVisible(visible);
    if (olderStatus != null) {
      olderStatus.setText(visible ? "Loading older messages..." : "").setVisible(visible);
    }
    if (list != null) applyKeyboardInsets();
    else invalidate();
  }

  private boolean shouldShowOlderLoading() {
    // Near-top prefetch must not stop or shift the list while the gesture/fling is active.
    return loadingOlderMessages && !messageScrollActive && adapter.getItemCount() > 0;
  }

  private float messageListTop() {
    return headerBottom + pinnedTabHeight()
        + (shouldShowOlderLoading() ? olderLoadingHeight() : 0f);
  }

  private float pinnedTabHeight() {
    return pinnedMessageTab.height(!pinnedMessages.isEmpty());
  }

  private void scrollToPinnedMessage(MessageEntity message) {
    if (list == null || message == null) return;
    String messageId = pinnedMessageKey(message);
    int position = adapter.indexOfMessage(messageId);
    if (position < 0) {
      pendingPinnedScrollMessageId = messageId;
      return;
    }
    pendingPinnedScrollMessageId = null;
    adapter.rippleMessage(messageId);
    list.stopScroll();
    list.scrollToPosition(position);
    invalidate();
  }

  private void scrollToMessageId(String messageId) {
    if (list == null || messageId == null || messageId.trim().isEmpty()) return;
    int position = adapter.indexOfMessage(messageId);
    if (position < 0) {
      pendingReplyScrollMessageId = messageId;
      listener.onReplyTargetRequested(messageId);
      return;
    }
    pendingReplyScrollMessageId = null;
    adapter.rippleMessage(messageId);
    list.stopScroll();
    list.scrollToPosition(position);
    invalidate();
  }

  /** Applies live highlighting and moves to the first currently loaded match. */
  public boolean searchMessages(String query) {
    if (list == null) return false;
    adapter.setSearchQuery(query);
    refreshSearchMatches(true);
    return !searchMatches.isEmpty();
  }

  private void refreshSearchMatches(boolean selectFirst) {
    searchMatches.clear();
    searchMatches.addAll(adapter.matchingPositions(searchDraft));
    if (searchMatches.isEmpty()) searchMatchIndex = -1;
    else if (selectFirst || searchMatchIndex < 0 || searchMatchIndex >= searchMatches.size()) {
      searchMatchIndex = 0;
    }
    updateSearchMatchCount();
    if (selectFirst && searchMatchIndex >= 0) scrollToSearchMatch();
  }

  private void moveSearchMatch(int direction) {
    refreshSearchMatches(false);
    if (searchMatches.isEmpty()) return;
    searchMatchIndex = (searchMatchIndex + direction + searchMatches.size())
        % searchMatches.size();
    updateSearchMatchCount();
    scrollToSearchMatch();
  }

  private void scrollToSearchMatch() {
    if (list == null || searchMatchIndex < 0 || searchMatchIndex >= searchMatches.size()) return;
    list.stopScroll();
    list.scrollToPosition(searchMatches.get(searchMatchIndex));
    invalidate();
  }

  private void updateSearchMatchCount() {
    if (searchMatchCount == null) return;
    searchMatchCount.setText(searchMatches.isEmpty()
        ? (searchDraft.trim().isEmpty() ? "0/0" : "0/0")
        : (searchMatchIndex + 1) + "/" + searchMatches.size());
  }

  public void showSearch(Runnable onDismiss) {
    searchVisible = true;
    searchDismissAction = onDismiss;
    if (getWidth() > 0 && getHeight() > 0) build();
    if (searchInput != null) {
      searchInput.requestFocus();
      searchInput.setSelection(searchDraft.length());
    }
  }

  public boolean dismissSearch() {
    if (!searchVisible) return false;
    searchVisible = false;
    searchDraft = "";
    adapter.setSearchQuery("");
    Runnable dismissed = searchDismissAction;
    searchDismissAction = null;
    if (getWidth() > 0 && getHeight() > 0) build();
    if (dismissed != null) dismissed.run();
    return true;
  }

  public void setContactBlocked(boolean blocked) {
    if (contactBlocked == blocked) return;
    contactBlocked = blocked;
    if (blocked) {
      composer.draft = "";
      composer.clearReply();
      composer.clearAttachment();
      composer.attachmentPanelVisible = false;
    }
    if (getWidth() > 0 && getHeight() > 0) build();
  }

  private float olderLoadingHeight() {
    return px(132f);
  }


  private Text.Builder textBuilder(String id, String v, RectF r, float sz, int c, FontVariation f) {
    return new Text.Builder(getContext(), id, v, r)
        .setFont(NativeFonts.INTER)
        .setFontVariations(f)
        .setTextSizePx(sz)
        .setTextColor(c)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER)
        .setMaxLines(4);
  }

  private Text text(
      ZLayer l, String id, String v, RectF r, float sz, int c, FontVariation f, Text.Alignment a) {
    return l.add(textBuilder(id, v, r, sz, c, f).setAlignment(a));
  }

  private Button button(
      ZLayer l, String id, Bitmap b, String label, RectF r, int c, Button.OnClickListener click) {
    return l.add(
        new Button.Builder(getContext(), id, b, label, r)
            .setImageScaleType(Image.ScaleType.FIT_XY)
            .setCornerRadiusPx(px(44f))
            .setFont(NativeFonts.INTER)
            .setFontVariations(FontVariation.SEMI_BOLD)
            .setTextSizePx(sp(18))
            .setTextColor(c)
            .setRippleEnabled(true)
            .setRippleColor(0x22019CC4)
            .setOnClickListener(click));
  }

  private Button iconButton(
      ZLayer layer, String id, Bitmap icon, RectF iconBounds, RectF touchBounds,
      Button.OnClickListener click) {
    layer.add(
        new Image.Builder(getContext(), id + "_icon", icon, iconBounds)
            .setScaleType(Image.ScaleType.FIT_CENTER));
    return layer.add(
        new Button.Builder(getContext(), id + "_touch", transparent, "", touchBounds)
            .setImageScaleType(Image.ScaleType.FIT_XY)
            .setCornerRadiusPx(0)
            .setRippleEnabled(true)
            .setRippleColor(0x16019CC4)
            .setOnClickListener(click));
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    long drawStartedNanos = SystemClock.elapsedRealtimeNanos();
    layers.draw(c);
    if (profiler != null) {
      int first = list == null ? -1 : list.getFirstVisiblePosition();
      int last = list == null ? -1 : list.getLastVisiblePosition();
      int count = adapter.getItemCount();
      profiler.viewDraw(SystemClock.elapsedRealtimeNanos() - drawStartedNanos, count, first, last);
      profiler.scrollProgress(first, last, count);
    }
  }

  /** Transparent timing wrapper around the AAR list's draw call. */
  private final class TimedMessageListComponent implements Component {
    private final ComponentList<MessageEntity> delegate;

    TimedMessageListComponent(ComponentList<MessageEntity> delegate) {
      this.delegate = delegate;
    }

    @Override public String getId() { return delegate.getId(); }
    @Override public RectF getBounds() { return delegate.getBounds(); }
    @Override public boolean isVisible() { return delegate.isVisible(); }
    @Override public boolean isEnabled() { return delegate.isEnabled(); }
    @Override public boolean onTouchEvent(MotionEvent event) { return delegate.onTouchEvent(event); }
    @Override public void attach(ComponentHost host) { delegate.attach(host); }
    @Override public void release() { delegate.release(); }

    @Override public void draw(Canvas canvas) {
      long startedNanos = SystemClock.elapsedRealtimeNanos();
      delegate.draw(canvas);
      if (profiler != null) {
        profiler.messageListDraw(SystemClock.elapsedRealtimeNanos() - startedNanos);
      }
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    int action = e.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN
        && input != null
        && (input.isFocused() || imeVisible)
        && hasComposerContent()
        && isInsideDisplayedSendButton(e.getX(), e.getY())) {
      // Do not pass this gesture to ZLayerGroup: its TextField routing clears focus before
      // the sibling Button receives the click, making the first send tap dismiss the IME.
      directComposerSendGesture = true;
      keepKeyboardAfterSend = true;
      return true;
    }
    if (directComposerSendGesture) {
      if (action == MotionEvent.ACTION_UP) {
        boolean sendMessage = isInsideDisplayedSendButton(e.getX(), e.getY());
        directComposerSendGesture = false;
        if (sendMessage) dispatchComposerSend();
        return true;
      }
      if (action == MotionEvent.ACTION_CANCEL) directComposerSendGesture = false;
      return true;
    }
    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
      // ZLayerGroup otherwise gives the gesture to its TextField coordinator, which clears
      // composer focus before the list can scroll. Route this gesture straight to the list.
      directMessageListGesture = list != null && input != null
          && (input.isFocused() || imeVisible)
          && list.getBounds().contains(e.getX(), e.getY());
      loadingGestureStartY = e.getY();
      loadingGestureBlocked = false;
      olderLoadRequestedForGesture = false;
      profileGestureStartY = e.getY();
      profileScrollCandidate = list != null && list.getBounds().contains(e.getX(), e.getY());
      if (list != null) {
        lastPrefetchFirst = list.getFirstVisiblePosition();
        int last = list.getLastVisiblePosition();
        adapter.prefetchMediaAround(lastPrefetchFirst, last, -1, getMessageLayoutWidth());
        adapter.prefetchMediaAround(lastPrefetchFirst, last, 1, getMessageLayoutWidth());
      }
      profileScrollStarted = false;
      removeCallbacks(finishScrollProfile);
      removeCallbacks(probeScrollIdle);
    } else if (e.getActionMasked() == MotionEvent.ACTION_MOVE
        && !loadingGestureBlocked && shouldShowOlderLoading()
        && e.getY() - loadingGestureStartY
            > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
      MotionEvent cancel = MotionEvent.obtain(e);
      cancel.setAction(MotionEvent.ACTION_CANCEL);
      if (directMessageListGesture && list != null) list.onTouchEvent(cancel);
      else layers.onTouchEvent(cancel);
      cancel.recycle();
      loadingGestureBlocked = true;
    }
    if (e.getActionMasked() == MotionEvent.ACTION_MOVE && profileScrollCandidate
        && Math.abs(e.getY() - profileGestureStartY)
            > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
      if (!profileScrollStarted && profiler != null) {
        profiler.scrollStart(
            list.getFirstVisiblePosition(), list.getLastVisiblePosition(), adapter.getItemCount());
      }
      profileScrollStarted = true;
      messageScrollActive = true;
      adapter.setScrolling(true);
      // Comparison mode: allow newly visible media rows to start their thumbnail work while
      // the gesture/fling is active. MediaPreviewComponent still checks the memory cache first,
      // so already prepared previews remain allocation-free on the bind path.
      MediaPreviewCache.setDecodingPaused(false);
      removeCallbacks(finishScrollProfile);
      removeCallbacks(probeScrollIdle);
    } else if ((e.getActionMasked() == MotionEvent.ACTION_UP
        || e.getActionMasked() == MotionEvent.ACTION_CANCEL) && profileScrollStarted) {
      removeCallbacks(finishScrollProfile);
      removeCallbacks(probeScrollIdle);
      lastIdleProbeOffset = list == null ? 0f : list.getScrollOffset();
      stableIdleProbes = 0;
      idleProbeDeadlineMs = SystemClock.uptimeMillis() + 350L;
      postDelayed(probeScrollIdle, 32L);
      profileScrollCandidate = false;
      profileScrollStarted = false;
    }
    if (loadingGestureBlocked) {
      if (e.getActionMasked() == MotionEvent.ACTION_UP
          || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
        loadingGestureBlocked = false;
        directMessageListGesture = false;
      }
      return true;
    }
    boolean handled = directMessageListGesture && list != null
        ? list.onTouchEvent(e) : layers.onTouchEvent(e);
    if (handled) loadOlderMessagesIfNeeded();
    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      directMessageListGesture = false;
    }
    return handled || super.onTouchEvent(e);
  }

  private void loadOlderMessagesIfNeeded() {
    if (list == null || adapter.getItemCount() == 0
        || olderLoadRequestedForGesture || loadingOlderMessages || !canLoadOlderMessages) return;
    // Begin before position zero so Room/network latency is hidden by the remaining rows.
    if (list.getFirstVisiblePosition() <= 5) {
      olderLoadRequestedForGesture = true;
      listener.onLoadOlderMessages();
    }
  }

  private void finishScrollProfile() {
    if (!messageScrollActive && pendingScrollIdleAction == null) return;
    messageScrollActive = false;
    removeCallbacks(probeScrollIdle);
    adapter.setScrolling(false);
    MediaPreviewCache.setDecodingPaused(false);
    updateOlderLoadingChrome();
    if (list == null) return;
    int first = list.getFirstVisiblePosition();
    int last = list.getLastVisiblePosition();
    int direction = lastPrefetchFirst < 0 || first >= lastPrefetchFirst ? 1 : -1;
    lastPrefetchFirst = first;
    adapter.prefetchMediaAround(first, last, direction, getMessageLayoutWidth());
    if (profiler != null) profiler.scrollEnd(first, last, adapter.getItemCount());
    Runnable pending = pendingScrollIdleAction;
    pendingScrollIdleAction = null;
    if (pending != null) pending.run();
  }

  @Override
  public boolean onCheckIsTextEditor() {
    return layers.onCheckIsTextEditor();
  }

  @Override
  public InputConnection onCreateInputConnection(EditorInfo a) {
    InputConnection x = layers.onCreateInputConnection(a);
    return x != null ? x : super.onCreateInputConnection(a);
  }

  @Override
  public boolean onKeyDown(int k, KeyEvent e) {
    return layers.onKeyDown(k, e) || super.onKeyDown(k, e);
  }

  public void release() {
    removeCallbacks(finishScrollProfile);
    removeCallbacks(probeScrollIdle);
    finishScrollProfile();
    MediaPreviewCache.setDecodingPaused(false);
    layers.release();
    adapter.release();
    recycle(
        white, transparent, divider, accent, attachmentOption, profile,
        conversationBackground, headerBackground, microphoneIcon, sendIcon, emojiIcon,
        attachmentIcon, cameraIcon, backIcon, voiceCallIcon, videoCallIcon, moreIcon,
        selectionBackground, selectionStatusBarBackground, messageSelectionBackground,
        selectionReplyIcon,
        selectionCopyIcon, selectionForwardIcon, selectionPinIcon, selectionUnpinIcon,
        selectionDeleteIcon,
        messageSendingIcon, messageSentIcon, messageDeliveredIcon, messageReadIcon,
        documentIcon, deletedMessageIcon, forwardedMessageIcon,
        callPhoneIncomingIcon, callPhoneOutgoingIcon, callPhoneMissedIcon,
        callVideoIncomingIcon, callVideoOutgoingIcon, callVideoMissedIcon);
  }

  private static String normalize(String v) {
    if (v == null) return "";
    String n = v.trim();
    if (n.startsWith("<plus>")) n = n.substring(6);
    return n.startsWith("+") ? n.substring(1) : n;
  }

  private float px(float v) {
    return figmaConfig.toRuntime(v, Math.max(1, getResources().getDisplayMetrics().widthPixels));
  }

  private float sp(float v) {
    return v * getResources().getDisplayMetrics().scaledDensity;
  }

  private static Bitmap color(int c) {
    Bitmap b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    b.eraseColor(c);
    return b;
  }

  private Bitmap resourceBitmap(int resource) {
    return BitmapFactory.decodeResource(getResources(), resource);
  }

  private Bitmap namedDrawableBitmap(String name) {
    int resource = getResources().getIdentifier(name, "drawable", getContext().getPackageName());
    return resource == 0 ? color(Color.TRANSPARENT) : resourceBitmap(resource);
  }

  private Bitmap drawableBitmap(int resource) {
    android.graphics.drawable.Drawable drawable = ContextCompat.getDrawable(getContext(), resource);
    if (drawable == null) return transparent;
    int width = Math.max(1, drawable.getIntrinsicWidth());
    int height = Math.max(1, drawable.getIntrinsicHeight());
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    drawable.setBounds(0, 0, width, height);
    drawable.draw(canvas);
    return bitmap;
  }

  private static void recycle(Bitmap... bs) {
    for (Bitmap b : bs) if (b != null && !b.isRecycled()) b.recycle();
  }

}
