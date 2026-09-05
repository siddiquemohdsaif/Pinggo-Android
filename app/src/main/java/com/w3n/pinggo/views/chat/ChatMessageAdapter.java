package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Looper;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.Layout;
import android.text.TextPaint;
import android.util.Log;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
import com.w3n.pinggo.data.cache.MediaPreviewCache;
import com.w3n.pinggo.data.local.MessageEntity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import com.w3n.pinggo.views.chat.ChatMessageAdapterModels.LocationRenderTiming;
import com.w3n.pinggo.views.chat.ChatMessageAdapterModels.MessageMetrics;
import com.w3n.pinggo.views.chat.ChatMessageAdapterModels.MessageRenderModel;
import com.w3n.pinggo.views.chat.ChatMessageAdapterModels.MetricKey;

/** Owns chat-message presentation, cached measurement, row diffing, and binding. */
final class ChatMessageAdapter extends ComponentList.Adapter<MessageEntity> {
  private static final String LOCATION_PERF_TAG = "PingGoLocationPerf";
  private final com.ogfa.nativeviews.component.FigmaConfig figmaConfig =
      new com.ogfa.nativeviews.component.FigmaConfig(1080f);
  private static final float MESSAGE_SCALE = 1.15f;
  private static final float ATTACHMENT_SCALE = 1.15f;
  private static final int ACCENT = 0xFF019CC4;
  private static final int SECONDARY = 0xFF687382;
  private static final int MESSAGE_TEXT_COLOR = 0xFF131D2F;
  private static final int MESSAGE_TIME_COLOR = 0xFF5C6B85;
  private static final float MESSAGE_TEXT_SIZE_PX = 34f * MESSAGE_SCALE;
  private static final float MESSAGE_TOP_PADDING_PX = 20f * MESSAGE_SCALE;
  private static final float MESSAGE_BOTTOM_PADDING_PX = 20f * MESSAGE_SCALE;
  private static final float MESSAGE_HORIZONTAL_PADDING_PX = 25f * MESSAGE_SCALE;
  private static final float MESSAGE_LINE_SPACING_PX = 10f * MESSAGE_SCALE;
  private static final float MESSAGE_TIME_SIZE_PX = 24f * MESSAGE_SCALE;
  private static final float MESSAGE_TEXT_META_GAP_PX = 20f * MESSAGE_SCALE;
  private static final float MESSAGE_TEXT_WIDTH_SAFETY_PX = 2f;
  private static final float MESSAGE_TIME_TICK_GAP_PX = 10f * MESSAGE_SCALE;
  private static final float MESSAGE_META_RIGHT_PX = 20f * MESSAGE_SCALE;
  private static final float MESSAGE_TIME_BOTTOM_PX = 15f * MESSAGE_SCALE;
  private static final float MESSAGE_TIME_RECT_EXTRA_PX = 2f;
  private static final float MESSAGE_TICK_WIDTH_PX = 40f * MESSAGE_SCALE;
  private static final float MESSAGE_TICK_HEIGHT_PX = 26f * MESSAGE_SCALE;
  private static final float MESSAGE_TICK_BOTTOM_PX = 15f * MESSAGE_SCALE;
  private static final float MESSAGE_PIN_GAP_PX = 10f * MESSAGE_SCALE;
  private static final float MESSAGE_ROW_GAP_PX = 19.25f;
  private static final float MESSAGE_ROW_HALF_GAP_PX = MESSAGE_ROW_GAP_PX / 2f;
  private static final float MESSAGE_TAIL_WIDTH_PX = 11f;
  private static final float MESSAGE_ROW_HORIZONTAL_INSET_PX = 33f;
  private static final float IMAGE_BUBBLE_WIDTH_PX = 620f;
  private static final float IMAGE_BUBBLE_HEIGHT_PX = 460f;
  private static final float VIDEO_BUBBLE_WIDTH_PX = 620f;
  private static final float VIDEO_BUBBLE_HEIGHT_PX = 260f;
  private static final float PORTRAIT_MEDIA_WIDTH_PX = 460f;
  private static final float PORTRAIT_MEDIA_HEIGHT_PX = 620f;
  private static final float FILE_BUBBLE_WIDTH_PX = 596f;
  private static final float FILE_BUBBLE_HEIGHT_PX = 127f + 7f;
  private static final float LOCATION_BUBBLE_WIDTH_PX = 620f;
  private static final float LOCATION_BUBBLE_HEIGHT_PX = 300f;
  private static final float AUDIO_BUBBLE_WIDTH_PX = 620f;
  private static final float AUDIO_BUBBLE_HEIGHT_PX = 150f;
  private static final float FILE_TITLE_TEXT_SIZE_PX = 34f;
  private static final float FILE_TITLE_LINE_SPACING_PX = 4f;
  private static final float FILE_TITLE_WIDTH_PX = 438f;
  private static final float MEDIA_PADDING_PX = 12f;
  private static final float NOTICE_PADDING_PX = 15f;
  private static final float NOTICE_LEFT_PX = 30f;
  private static final float NOTICE_ICON_SIZE_PX = 32f;
  private static final float NOTICE_TEXT_GAP_PX = 5f;
  private static final float NOTICE_TEXT_SIZE_PX = MESSAGE_TEXT_SIZE_PX * .90f;
  private static final float FORWARDED_PANEL_INSET_PX = 12f;
  private static final float FORWARDED_LABEL_BOTTOM_GAP_PX = 5f;
  private static final float FORWARDED_INNER_TOP_GAP_PX = 10f;
  private static final float FORWARDED_EXTRA_HEIGHT_PX =
      FORWARDED_LABEL_BOTTOM_GAP_PX + FORWARDED_INNER_TOP_GAP_PX;
  private static final float FORWARDED_HEADER_HEIGHT_PX =
      NOTICE_PADDING_PX + NOTICE_ICON_SIZE_PX + NOTICE_PADDING_PX
          + FORWARDED_EXTRA_HEIGHT_PX;
  private static final float FORWARDED_ATTACHMENT_HEADER_HEIGHT_PX =
      NOTICE_PADDING_PX + NOTICE_ICON_SIZE_PX + NOTICE_PADDING_PX
          + FORWARDED_LABEL_BOTTOM_GAP_PX;
  private static final float REPLY_BOX_INSET_PX = 12f;
  private static final float REPLY_TEXT_LEFT_PX = 27f;
  private static final float REPLY_TEXT_RIGHT_PX = 18f;
  private static final float REPLY_TEXT_TOP_PX = 13f;
  private static final float REPLY_TEXT_BOTTOM_PX = 13f;
  private static final float REPLY_SENDER_TEXT_SIZE_PX = 32f * .90f;
  private static final float REPLY_MESSAGE_TEXT_SIZE_PX = MESSAGE_TEXT_SIZE_PX * .90f * .90f;
  private static final float REPLY_SENDER_MESSAGE_GAP_PX = 5f;
  private static final float REPLY_MESSAGE_GAP_PX = 12f;
  private static final float REPLY_IMAGE_LANDSCAPE_HEIGHT_PX = 529f;
  private static final float REPLY_VIDEO_LANDSCAPE_HEIGHT_PX = 299f;
  private static final float REPLY_PORTRAIT_MEDIA_HEIGHT_PX = 713f;
  private static final float REPLY_FULL_ATTACHMENT_HEIGHT_PX = 146f;
  private static final float REPLY_LOCATION_HEIGHT_PX = 345f;
  private static final float REPLY_LANDSCAPE_MEDIA_WIDTH_PX = 713f;
  private static final float REPLY_FULL_ATTACHMENT_WIDTH_PX = 685f;
  private static final float REPLY_PORTRAIT_MEDIA_WIDTH_PX = 529f;

  private final Context context;
  private final String currentUser;
  private final Bitmap transparent;
  private final Bitmap selectionBackground;
  private final Bitmap messageSendingIcon;
  private final Bitmap messageSentIcon;
  private final Bitmap messageDeliveredIcon;
  private final Bitmap messageReadIcon;
  private final Bitmap messagePinnedIcon;
  private final Bitmap documentIcon;
  private final Bitmap deletedMessageIcon;
  private final Bitmap forwardedMessageIcon;
  private final Bitmap callPhoneIncomingIcon;
  private final Bitmap callPhoneOutgoingIcon;
  private final Bitmap callPhoneMissedIcon;
  private final Bitmap callVideoIncomingIcon;
  private final Bitmap callVideoOutgoingIcon;
  private final Bitmap callVideoMissedIcon;
  private final ChatMessageAdapterConfig.AttachmentStateProvider attachmentStateProvider;
  private final ChatMessageAdapterConfig.MediaMetricsListener mediaMetricsListener;
  private final ChatMessageAdapterConfig.AudioPlaybackListener audioPlaybackListener;
  private final ChatMessageAdapterConfig.ReplyNavigationListener replyNavigationListener;
  private final ChatMessageAdapterConfig.MessageClickListener messageClickListener;
  private final ChatMessageAdapterConfig.MessageLongClickListener messageLongClickListener;
  private final ChatPerformanceProfiler profiler;
  private final String opponentName;
  private final Set<String> selectedMessageIds;
  private final Typeface messageTypeface;
  private final SimpleDateFormat messageTimeFormatter;
  private final SimpleDateFormat messageDateFormatter;
  private final ThreadLocal<MeasurementTools> measurementTools;
  private final List<MessageEntity> messages = new ArrayList<>();
  private final List<String> presentationSignatures = new ArrayList<>();
  private final List<String> dateLabels = new ArrayList<>();
  private final Map<String, String> replySenders = new ConcurrentHashMap<>();
  private final Map<String, ReplyContent> replyContents = new ConcurrentHashMap<>();
  private final Map<String, Boolean> mediaPortraits = new ConcurrentHashMap<>();
  private final Map<String, Long> audioDurations = new ConcurrentHashMap<>();
  private final Map<String, LocationRenderTiming> locationRenderTimings =
      new ConcurrentHashMap<>();
  private Bitmap chatProfile;
  private String playingAudioMessageId = "";
  private String searchQuery = "";
  private String pendingNavigationRippleMessageId = "";
  private long playingAudioProgressMs;
  private long playingAudioDurationMs;
  private volatile boolean scrolling;
  private final Set<String> mediaPrefetches = ConcurrentHashMap.newKeySet();
  private final Map<ComponentList.Item, String> boundRows = new WeakHashMap<>();
  private final Map<String, MessageRenderModel> renderModelCache =
      new LinkedHashMap<String, MessageRenderModel>(64, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, MessageRenderModel> eldest) {
          return size() > 512;
        }
      };
  private final Map<MetricKey, MessageMetrics> metricsCache =
      new LinkedHashMap<MetricKey, MessageMetrics>(64, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<MetricKey, MessageMetrics> eldest) {
          return size() > 512;
        }
      };

  ChatMessageAdapter(
      Context context,
      String currentUser,
      ChatMessageAdapterConfig config,
      Set<String> selectedMessageIds) {
    this.context = context;
    this.currentUser = normalize(currentUser);
    this.transparent = config.transparent;
    this.selectionBackground = config.selectionBackground;
    this.messageSendingIcon = config.messageSendingIcon;
    this.messageSentIcon = config.messageSentIcon;
    this.messageDeliveredIcon = config.messageDeliveredIcon;
    this.messageReadIcon = config.messageReadIcon;
    this.messagePinnedIcon = config.messagePinnedIcon;
    this.documentIcon = config.documentIcon;
    this.deletedMessageIcon = config.deletedMessageIcon;
    this.forwardedMessageIcon = config.forwardedMessageIcon;
    this.callPhoneIncomingIcon = config.callPhoneIncomingIcon;
    this.callPhoneOutgoingIcon = config.callPhoneOutgoingIcon;
    this.callPhoneMissedIcon = config.callPhoneMissedIcon;
    this.callVideoIncomingIcon = config.callVideoIncomingIcon;
    this.callVideoOutgoingIcon = config.callVideoOutgoingIcon;
    this.callVideoMissedIcon = config.callVideoMissedIcon;
    this.attachmentStateProvider = config.attachmentStateProvider;
    this.mediaMetricsListener = config.mediaMetricsListener;
    this.audioPlaybackListener = config.audioPlaybackListener;
    this.replyNavigationListener = config.replyNavigationListener;
    this.messageClickListener = config.messageClickListener;
    this.messageLongClickListener = config.messageLongClickListener;
    this.profiler = config.profiler;
    this.opponentName = config.opponentName;
    this.selectedMessageIds = selectedMessageIds;
    messageTypeface = NativeFonts.load(context, NativeFonts.INTER);
    messageTimeFormatter = new SimpleDateFormat("h:mm a", Locale.getDefault());
    messageDateFormatter = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
    measurementTools =
        new ThreadLocal<MeasurementTools>() {
          @Override
          protected MeasurementTools initialValue() {
            return new MeasurementTools(messageTypeface, sp(11));
          }
        };
  }

  void setChatProfile(Bitmap profile) {
    chatProfile = profile;
  }

  void setScrolling(boolean value) { scrolling = value; }

  /** Warms only three rows in the current scroll direction. */
  void prefetchMediaAround(int firstVisible, int lastVisible, int direction, float availableWidth) {
    if (messages.isEmpty() || firstVisible < 0 || lastVisible < 0) return;
    // Include the visible range so rows that first appeared during a paused fling recover.
    int start = direction < 0 ? Math.max(0, firstVisible - 3) : firstVisible;
    int end = direction < 0 ? lastVisible : Math.min(messages.size() - 1, lastVisible + 3);
    for (int position = start; position <= end; position++) {
      MessageEntity message = messages.get(position);
      MessageRenderModel model = renderModel(message);
      if (!("image".equals(model.mediaType) || "video".equals(model.mediaType))) continue;
      MessageMetrics measured = metrics(model, availableWidth);
      boolean video = "video".equals(model.mediaType);
      String source = model.attachmentSource;
      float baseWidth = Boolean.TRUE.equals(mediaPortraits.get(source))
          ? PORTRAIT_MEDIA_WIDTH_PX : video ? VIDEO_BUBBLE_WIDTH_PX : IMAGE_BUBBLE_WIDTH_PX;
      float contentScale = measured.bubbleWidth / baseWidth;
      int width = Math.max(64, Math.round(
          measured.bubbleWidth - 2f * MEDIA_PADDING_PX * contentScale));
      int height = Math.max(64, Math.round(measured.attachmentHeight
          - 2f * MEDIA_PADDING_PX * contentScale));
      String requestKey = source + '|' + video + '|' + width + 'x' + height;
      MediaPreviewCache.Thumbnail cached =
          MediaPreviewCache.memoryThumbnail(source, video, width, height);
      if (cached != null) {
        if (!scrolling && position >= firstVisible && position <= lastVisible) {
          notifyItemChanged(position);
        }
        continue;
      }
      if (!mediaPrefetches.add(requestKey)) continue;
      String messageId = messageKey(message, position);
      MediaPreviewCache.loadThumbnail(context, source, video, width, height,
          new MediaPreviewCache.Callback<MediaPreviewCache.Thumbnail>() {
            @Override public void onSuccess(MediaPreviewCache.Thumbnail value) {
              mediaPrefetches.remove(requestKey);
              if (scrolling) return;
              int current = indexOfMessage(messageId);
              if (current >= 0) notifyItemChanged(current);
            }
            @Override public void onError() { mediaPrefetches.remove(requestKey); }
          });
    }
  }

  void trackLocationRender(
      String clientMessageId,
      String traceId,
      long pressedElapsedMs,
      long pressedWallMs) {
    if (clientMessageId == null || clientMessageId.isEmpty() || pressedElapsedMs <= 0L) return;
    locationRenderTimings.put(
        clientMessageId,
        new LocationRenderTiming(traceId, pressedElapsedMs, pressedWallMs));
  }

  void setAudioPlaybackState(
      String messageId, boolean playing, long progressMs, long durationMs) {
    String key = messageId == null ? "" : messageId;
    String previousKey = playingAudioMessageId;
    if (durationMs > 0L && !key.isEmpty()) audioDurations.put(key, durationMs);
    playingAudioMessageId = playing ? key : "";
    playingAudioProgressMs = playing ? Math.max(0L, progressMs) : 0L;
    playingAudioDurationMs = Math.max(0L, durationMs);
    int previousPosition = indexOfMessage(previousKey);
    int nextPosition = indexOfMessage(key);
    if (previousPosition >= 0) notifyItemChanged(previousPosition);
    if (nextPosition >= 0 && nextPosition != previousPosition) notifyItemChanged(nextPosition);
  }

  static final class PreparedSubmission {
    final List<MessageEntity> messages;
    final Map<String, String> replySenders;
    final Map<String, ReplyContent> replyContents;
    final List<String> signatures;
    final List<String> dateLabels;

    PreparedSubmission(
        List<MessageEntity> messages,
        Map<String, String> replySenders,
        Map<String, ReplyContent> replyContents,
        List<String> signatures,
        List<String> dateLabels) {
      this.messages = messages;
      this.replySenders = replySenders;
      this.replyContents = replyContents;
      this.signatures = signatures;
      this.dateLabels = dateLabels;
    }
  }

  /** Performs full-list formatting and signature generation without touching ComponentList. */
  PreparedSubmission prepareSubmission(List<MessageEntity> values) {
    List<MessageEntity> nextMessages =
        values == null ? new ArrayList<>() : new ArrayList<>(values);
    Map<String, String> nextReplySenders = new HashMap<>();
    Map<String, ReplyContent> nextReplyContents = new HashMap<>();
    List<MessageRenderModel> nextModels = new ArrayList<>(nextMessages.size());
    List<String> nextSignatures = new ArrayList<>(nextMessages.size());
    List<String> nextDateLabels = buildDateLabels(nextMessages);
    for (MessageEntity message : nextMessages) {
      MessageRenderModel model = renderModel(message);
      nextModels.add(model);
      if (message.messageId != null) {
        nextReplyContents.put(message.messageId, replyContent(message, model));
        nextReplySenders.put(message.messageId, String.valueOf(message.senderId));
      }
    }
    for (int index = 0; index < nextModels.size(); index++) {
      MessageRenderModel model = nextModels.get(index);
      ReplyContent repliedContent = model.repliedMessageId == null
          ? null : nextReplyContents.containsKey(model.repliedMessageId)
              ? nextReplyContents.get(model.repliedMessageId)
              : replyContents.get(model.repliedMessageId);
      String repliedSender = model.repliedMessageId == null
          ? null : nextReplySenders.containsKey(model.repliedMessageId)
              ? nextReplySenders.get(model.repliedMessageId)
              : replySenders.get(model.repliedMessageId);
      nextSignatures.add(model.presentationSignature + '\u0001'
          + (repliedContent == null ? "" : repliedContent.signature)
          + '\u0001' + String.valueOf(repliedSender)
          + '\u0001' + nextDateLabels.get(index));
    }
    return new PreparedSubmission(
        nextMessages, nextReplySenders, nextReplyContents, nextSignatures, nextDateLabels);
  }

  boolean submit(List<MessageEntity> values) {
    return applyPreparedSubmission(prepareSubmission(values));
  }

  /** Applies prepared data and emits AAR list notifications; must run on the UI thread. */
  boolean applyPreparedSubmission(PreparedSubmission prepared) {
    long submitStartedNanos = SystemClock.elapsedRealtimeNanos();
    int oldCount = messages.size();
    int insertedCount = 0, changedCount = 0, removedCount = 0, movedCount = 0;
    List<MessageEntity> nextMessages = prepared.messages;
    Map<String, String> nextReplySenders = prepared.replySenders;
    Map<String, ReplyContent> nextReplyContents = prepared.replyContents;
    List<String> nextSignatures = prepared.signatures;
    List<String> nextDateLabels = prepared.dateLabels;
    boolean changed = !presentationSignatures.equals(nextSignatures);
    replyContents.putAll(nextReplyContents);
    replySenders.putAll(nextReplySenders);
    if (!changed) {
      // Keep the freshest Room entities even when their presentation is unchanged.
      messages.clear();
      messages.addAll(nextMessages);
      dateLabels.clear();
      dateLabels.addAll(nextDateLabels);
      return false;
    }
    boolean structureChanged = oldCount != nextMessages.size();
    if (!structureChanged) {
      for (int index = 0; index < oldCount; index++) {
        if (!messageKey(messages.get(index), index)
            .equals(messageKey(nextMessages.get(index), index))) {
          structureChanged = true;
          break;
        }
      }
    }
    if (structureChanged) {
      insertedCount = Math.max(0, nextMessages.size() - oldCount);
      removedCount = Math.max(0, oldCount - nextMessages.size());
      if (insertedCount == 0 && removedCount == 0) movedCount = nextMessages.size();
      int existingStart = insertedCount > 0 ? existingSequenceStart(nextMessages) : -1;
      int changedStart = Integer.MAX_VALUE;
      int changedEnd = -1;
      if (existingStart >= 0) {
        for (int oldIndex = 0; oldIndex < oldCount; oldIndex++) {
          int nextIndex = existingStart + oldIndex;
          if (!presentationSignatures.get(oldIndex).equals(nextSignatures.get(nextIndex))) {
            changedStart = Math.min(changedStart, nextIndex);
            changedEnd = Math.max(changedEnd, nextIndex);
            changedCount++;
          }
        }
      }
      messages.clear();
      messages.addAll(nextMessages);
      presentationSignatures.clear();
      presentationSignatures.addAll(nextSignatures);
      dateLabels.clear();
      dateLabels.addAll(nextDateLabels);
      if (existingStart >= 0) {
        // Retain and re-key visible holders when a history page is prepended or a new message is
        // appended. This avoids reconstructing every visible Text component for an unchanged row.
        int insertedRangeStart = existingStart == 0 ? oldCount : 0;
        notifyItemRangeInserted(insertedRangeStart, insertedCount);
        if (changedEnd >= changedStart) {
          notifyItemRangeChanged(changedStart, changedEnd - changedStart + 1);
        }
      } else {
        // Apply an uncommon reorder/removal as one rebuild instead of one rebuild per row.
        notifyDataSetChanged();
      }
    } else {
      for (int index = 0; index < nextMessages.size(); index++) {
        String previousSignature = presentationSignatures.set(index, nextSignatures.get(index));
        messages.set(index, nextMessages.get(index));
        dateLabels.set(index, nextDateLabels.get(index));
        if (!previousSignature.equals(nextSignatures.get(index))) {
          notifyItemChanged(index);
          changedCount++;
        }
      }
    }
    if (profiler != null) {
      profiler.adapterSubmit(
          SystemClock.elapsedRealtimeNanos() - submitStartedNanos,
          oldCount,
          nextMessages.size(),
          insertedCount,
          changedCount,
          removedCount,
          movedCount);
    }
    return changed;
  }

  String messageIdAt(int position) {
    if (position < 0 || position >= messages.size()) return null;
    return messageKey(messages.get(position), position);
  }

  int indexOfMessage(String messageId) {
    if (messageId == null) return -1;
    for (int index = 0; index < messages.size(); index++) {
      if (messageId.equals(messageKey(messages.get(index), index))) return index;
    }
    return -1;
  }

  void rippleMessage(String messageId) {
    int position = indexOfMessage(messageId);
    if (position < 0) return;
    pendingNavigationRippleMessageId = messageId;
    notifyItemChanged(position);
  }

  float contentStartAt(int position, float availableWidth) {
    float start = 0f;
    for (int index = 0; index < Math.min(position, messages.size()); index++) {
      start += rowHeight(messages.get(index), index, availableWidth);
    }
    return start;
  }

  float rowHeight(MessageEntity message, int position, float availableWidth) {
    float scale = figmaScale();
    float dateHeight = hasDateLabel(position) ? DateNotifierComponent.blockHeight(scale) : 0f;
    if (isReportEvent(message)) return Math.max(1f, dateHeight);
    return dateHeight + metrics(message, availableWidth).bubbleHeight + px(MESSAGE_ROW_GAP_PX);
  }

  void prepareMetrics(List<MessageEntity> values, float availableWidth) {
    indexReplyTargets(values);
    for (MessageEntity message : values) {
      if (Thread.currentThread().isInterrupted()) return;
      MessageRenderModel model = renderModel(message);
      if ("image".equals(model.mediaType) || "video".equals(model.mediaType)) {
        boolean video = "video".equals(model.mediaType);
        Boolean portrait = attachmentPortrait(message);
        if (portrait == null) {
          portrait = MediaPreviewCache.prepareOrientation(
              context, model.attachmentSource, video);
        }
        if (portrait != null) {
          Boolean previous = mediaPortraits.put(model.attachmentSource, portrait);
          if (previous == null || previous != portrait) {
            synchronized (metricsCache) { metricsCache.clear(); }
          }
        }
      }
      metrics(model, availableWidth);
    }
  }

  void indexReplyTargets(List<MessageEntity> values) {
    boolean changed = false;
    for (MessageEntity message : values) {
      if (message.messageId == null) continue;
      MessageRenderModel model = renderModel(message);
      ReplyContent preview = replyContent(message, model);
      String sender = String.valueOf(message.senderId);
      ReplyContent oldPreview = replyContents.put(message.messageId, preview);
      String oldSender = replySenders.put(message.messageId, sender);
      changed |= oldPreview == null || !preview.signature.equals(oldPreview.signature)
          || !sender.equals(oldSender);
    }
    if (changed) synchronized (metricsCache) { metricsCache.clear(); }
  }

  ReplyContent replyPreviewContent(MessageEntity message) {
    if (message == null) return ReplyContent.text("Message unavailable");
    return replyContent(message, renderModel(message));
  }

  String replyPreviewSender(MessageEntity message) {
    return replySenderLabel(message == null ? null : message.senderId);
  }

  float replyPreviewHeight(String sender, ReplyContent content, float boxWidth) {
    return Math.max(1f,
        replyBlockHeight(sender, content, Math.max(1f, boxWidth)) - REPLY_MESSAGE_GAP_PX);
  }

  @Override
  public int getItemCount() {
    return messages.size();
  }

  @Override
  public MessageEntity getItem(int position) {
    return messages.get(position);
  }

  @Override
  public int getItemViewType(int position) {
    return renderModel(messages.get(position)).own ? 1 : 0;
  }

  @Override
  public long getItemId(int position) {
    return stableId(messageKey(messages.get(position), position));
  }

  @Override
  public void onCreateItem(ComponentList.Item item, int type) {
    ComponentList.ItemScope scope = item.getScope();
    float width = scope.width();
    float height = scope.height();
    float horizontalInset = px(MESSAGE_ROW_HORIZONTAL_INSET_PX);
    float bubbleWidth = (width - horizontalInset * 2f) * .66f;
    float left = type == 1 ? width - horizontalInset - bubbleWidth : horizontalInset;
    float right = type == 1 ? width - horizontalInset : horizontalInset + bubbleWidth;
    float tailWidth = px(MESSAGE_TAIL_WIDTH_PX);
    float bodyLeft = type == 1 ? left : left + tailWidth;
    float bodyRight = type == 1 ? right - tailWidth : right;
    ZLayer row = item.addLayer("row");
    row.add(
        new Image.Builder(
                context, scope.id("selection_background"), selectionBackground,
                new RectF(0f, 0f, width, height))
            .setScaleType(Image.ScaleType.FIT_XY));
    row.add(
        new DateNotifierComponent(
            scope.id("date_notifier"),
            messageTypeface,
            figmaScale()));
    row.add(
        new MessageBubbleComponent(
            scope.id("bubble"),
            new RectF(
                left,
                px(MESSAGE_ROW_HALF_GAP_PX),
                right,
                height - px(MESSAGE_ROW_HALF_GAP_PX)),
            figmaScale(),
            type == 1));
    row.add(new MediaPreviewComponent(
        scope.id("media_preview"), context,
        figmaScale(),
        this::onMediaOrientationAvailable));
    row.add(new FilePreviewComponent(
        scope.id("file_preview"), documentIcon, messageTypeface,
        figmaScale()));
    row.add(new LocationPreviewComponent(
        scope.id("location_preview"), messageTypeface,
        figmaScale()));
    row.add(new CallPreviewComponent(
        scope.id("call_preview"), messageTypeface,
        figmaScale()));
    row.add(new ForwardedMessagePanelComponent(
        scope.id("forwarded_panel"),
        figmaScale()));
    row.add(new MessageRowRippleComponent(scope.id("message_ripple")));
    // ZLayer hit-tests in reverse component order. Keep the audio control above the
    // row-wide ripple; it returns false outside play/stop so bubble gestures still work.
    row.add(new AudioMessageComponent(
        scope.id("audio_preview"), id -> {
          if (audioPlaybackListener != null) audioPlaybackListener.onAudioPlaybackToggle(id);
        }));
    row.add(new ReplyPreviewComponent(
        context, scope.id("reply_preview"), documentIcon, chatProfile, messageTypeface,
        REPLY_SENDER_TEXT_SIZE_PX, REPLY_MESSAGE_TEXT_SIZE_PX,
        replyNavigationListener == null ? null
            : replyNavigationListener::onReplyTargetSelected));
    row.add(new MessageNoticeComponent(
        scope.id("forwarded_notice"), forwardedMessageIcon, messageTypeface,
        NOTICE_TEXT_SIZE_PX));
    row.add(new MessageNoticeComponent(
        scope.id("deleted_notice"), deletedMessageIcon, messageTypeface,
        NOTICE_TEXT_SIZE_PX));
    row.add(new PreparedMessageTextComponent(scope.id("message_text")));
    row.add(new MessageMetadataComponent(scope.id("message_metadata")));
  }

  @Override
  public void onBindItem(ComponentList.Item item, MessageEntity message, int position) {
    long bindStartedNanos = SystemClock.elapsedRealtimeNanos();
    MessageRenderModel model = renderModel(message);
    Log.d("PingGoMessageTrace", "stage=row_rendered"
        + " chatId=" + message.chatId
        + " messageId=" + message.messageId
        + " clientMessageId=" + message.clientMessageId
        + " messageType=" + message.messageType
        + " mediaType=" + model.mediaType
        + " position=" + position);
    boolean own = model.own;
    float rowWidth = item.getScope().width();
    float rowHeight = item.getScope().height();
    String dateLabel = position >= 0 && position < dateLabels.size() ? dateLabels.get(position) : "";
    String messageKey = messageKey(message, position);
    // Native Text.setText is expensive. Re-keyed AAR holders can retain an unchanged message
    // after insertions, so avoid rebinding every child when its complete visual state is stable.
    if (!isMedia(message)) {
      boolean audioRow = "audio".equals(model.mediaType);
      String bindSignature = model.presentationSignature + '\u0001' + dateLabel + '\u0001'
          + selectedMessageIds.contains(messageKey) + '\u0001'
          + (audioRow && messageKey.equals(playingAudioMessageId)) + '\u0001'
          + (audioRow ? playingAudioProgressMs : 0L) + '\u0001'
          + Float.floatToIntBits(rowWidth) + '\u0001' + Float.floatToIntBits(rowHeight);
      String previousSignature = boundRows.put(item, bindSignature);
      if (bindSignature.equals(previousSignature)) {
        if (profiler != null) profiler.rowReused(position, message.messageId);
        return;
      }
    } else {
      boundRows.remove(item);
    }
    float dateOffset = dateLabel.isEmpty()
        ? 0f
        : DateNotifierComponent.blockHeight(
            figmaScale());
    item.find("date_notifier", DateNotifierComponent.class).bind(dateLabel, rowWidth);
    if (isReportEvent(message)) {
      item.find("selection_background", Image.class).setVisible(false);
      item.find("bubble", MessageBubbleComponent.class).hide();
      item.find("media_preview", MediaPreviewComponent.class).hide();
      item.find("file_preview", FilePreviewComponent.class).hide();
      item.find("location_preview", LocationPreviewComponent.class).hide();
      item.find("audio_preview", AudioMessageComponent.class).hide();
      item.find("call_preview", CallPreviewComponent.class).hide();
      item.find("forwarded_panel", ForwardedMessagePanelComponent.class).hide();
      item.find("reply_preview", ReplyPreviewComponent.class).hide();
      item.find("forwarded_notice", MessageNoticeComponent.class).hide();
      item.find("deleted_notice", MessageNoticeComponent.class).hide();
      item.find("message_text", PreparedMessageTextComponent.class)
          .bind(new RectF(), null, false).setHighlight("");
      item.find("message_metadata", MessageMetadataComponent.class).hide();
      item.find("message_ripple", MessageRowRippleComponent.class).bind(
          new RectF(), new RectF(), 0f, null, null);
      return;
    }
    item.find("selection_background", Image.class)
        .setRegion(positiveRect(0f, dateOffset, rowWidth, rowHeight))
        .setVisible(selectedMessageIds.contains(messageKey(message, position)));
    float horizontalInset = px(MESSAGE_ROW_HORIZONTAL_INSET_PX);
    MessageMetrics metrics = metrics(model, Math.max(1f, rowWidth - horizontalInset * 2f));
    float width = metrics.bubbleWidth;
    float left = own ? rowWidth - horizontalInset - width : horizontalInset;
    float right = own ? rowWidth - horizontalInset : horizontalInset + width;
    float tailWidth = px(MESSAGE_TAIL_WIDTH_PX);
    float bodyLeft = own ? left : left + tailWidth;
    float bodyRight = own ? right - tailWidth : right;
    float bubbleTop = dateOffset + px(MESSAGE_ROW_HALF_GAP_PX);
    float bubbleBottom = rowHeight - px(MESSAGE_ROW_HALF_GAP_PX);
    boolean hasReply = !model.deleted
        && message.repliedMessageId != null && !message.repliedMessageId.isEmpty();
    boolean media = isMedia(message);
    boolean file = isFile(message);
    boolean location = isLocation(message);
    boolean audio = isAudio(message);
    boolean call = isCall(message);
    boolean attachmentPreview = media || file || location || audio;
    String mediaSource = attachmentSource(message);
    float attachmentContentScale = 1f;
    if (file || call) {
      attachmentContentScale = width / FILE_BUBBLE_WIDTH_PX;
    } else if (audio) {
      attachmentContentScale = width / AUDIO_BUBBLE_WIDTH_PX;
    } else if (location) {
      attachmentContentScale = width / LOCATION_BUBBLE_WIDTH_PX;
    } else if (media) {
      float baseWidth = Boolean.TRUE.equals(mediaPortraits.get(mediaSource))
          ? PORTRAIT_MEDIA_WIDTH_PX : IMAGE_BUBBLE_WIDTH_PX;
      attachmentContentScale = width / baseWidth;
    }
    boolean tailLess = (attachmentPreview && !audio) || model.forwarded || hasReply || call;
    if (tailLess) {
      bodyLeft = left;
      bodyRight = right;
    }
    MessageBubbleComponent bubble = item.find("bubble", MessageBubbleComponent.class);
    bubble.show()
        .setOutgoing(own)
        .setTailEnabled(!tailLess)
        .setShapeScale(attachmentContentScale)
        .setRegion(left, bubbleTop, right, bubbleBottom);

    float headingTop = bubbleTop
        + (model.forwarded || model.deleted || hasReply
            ? NOTICE_PADDING_PX : MESSAGE_TOP_PADDING_PX);
    float attachmentTop = (attachmentPreview || call) && (model.forwarded || hasReply)
        ? headingTop + metrics.forwardedHeight + metrics.replyHeight : bubbleTop;
    boolean hasAttachmentCaption = hasAttachmentCaption(model);
    float attachmentBottom = hasAttachmentCaption
        ? attachmentTop + metrics.attachmentHeight : bubbleBottom;
    RectF clickRegion = new RectF(left, bubbleTop, right, bubbleBottom);
    if (media) {
      clickRegion.set(
          left + MEDIA_PADDING_PX * attachmentContentScale,
          attachmentTop + MEDIA_PADDING_PX * attachmentContentScale,
          right - MEDIA_PADDING_PX * attachmentContentScale,
          attachmentBottom - MEDIA_PADDING_PX * attachmentContentScale);
    } else if (file || location) {
      float inset = MEDIA_PADDING_PX * attachmentContentScale;
      clickRegion.set(left + inset, attachmentTop + inset,
          right - inset, attachmentBottom - inset);
    }
    bubble
        .setHitRegion(positiveRect(
            clickRegion.left, clickRegion.top, clickRegion.right, clickRegion.bottom))
        .setClickAction(messageClickListener == null
            ? null : () -> messageClickListener.onMessageClick(message))
        .setLongClickAction(messageLongClickListener == null
            ? null : () -> messageLongClickListener.onMessageLongClick(message));
    MessageRowRippleComponent messageRipple =
        item.find("message_ripple", MessageRowRippleComponent.class).bind(
        positiveRect(0f, bubbleTop, rowWidth, bubbleBottom),
        positiveRect(clickRegion.left, clickRegion.top, clickRegion.right, clickRegion.bottom),
        0f,
        messageClickListener == null ? null : () -> messageClickListener.onMessageClick(message),
        messageLongClickListener == null
            ? null : () -> messageLongClickListener.onMessageLongClick(message));
    if (messageKey(message, position).equals(pendingNavigationRippleMessageId)) {
      pendingNavigationRippleMessageId = "";
      messageRipple.pulse();
    }
    long setupFinishedNanos = SystemClock.elapsedRealtimeNanos();
    MediaPreviewComponent preview = item.find("media_preview", MediaPreviewComponent.class);
    if (media) {
      String source = mediaSource;
      preview.bind(
          positiveRect(left + MEDIA_PADDING_PX * attachmentContentScale,
              attachmentTop + MEDIA_PADDING_PX * attachmentContentScale,
              right - MEDIA_PADDING_PX * attachmentContentScale,
              attachmentBottom - MEDIA_PADDING_PX * attachmentContentScale),
          source == null ? "" : source,
          "video".equals(message.messageType),
          attachmentContentScale,
          message.attachmentDurationMs == null ? 0L : message.attachmentDurationMs,
          message.attachmentSize == null ? 0L : message.attachmentSize);
    } else {
      preview.hide();
    }
    FilePreviewComponent filePreview = item.find("file_preview", FilePreviewComponent.class);
    if (file) {
      String size = message.attachmentSize == null ? "" : formatSize(message.attachmentSize);
      String subtitle = fileType(message);
      if (!size.isEmpty()) subtitle += "  .  " + size;
      int attachmentState = attachmentStateProvider.attachmentState(message);
      boolean downloading = attachmentState == 2;
      android.util.Log.d("PingGoAttachmentTransfer", "stage=file_row_bound chatId="
          + message.chatId + " messageId=" + message.messageId
          + " attachmentId=" + message.attachmentId + " downloading=" + downloading
          + " transferredBytes=" + attachmentStateProvider.downloadedBytes(message)
          + " totalSize=" + attachmentStateProvider.totalBytes(message));
      filePreview.bind(new RectF(left, attachmentTop, right, attachmentBottom),
          message.attachmentName, subtitle, attachmentState,
          attachmentStateProvider.downloadedBytes(message),
          attachmentStateProvider.totalBytes(message));
    } else {
      filePreview.hide();
    }
    LocationPreviewComponent locationPreview =
        item.find("location_preview", LocationPreviewComponent.class);
    if (location) {
      locationPreview.bind(new RectF(left, attachmentTop, right, bubbleBottom),
          message.latitude, message.longitude);
    } else {
      locationPreview.hide();
    }
    AudioMessageComponent audioPreview =
        item.find("audio_preview", AudioMessageComponent.class);
    if (audio) {
      String key = messageKey(message, position);
      boolean playing = key.equals(playingAudioMessageId);
      long duration = playing && playingAudioDurationMs > 0L
          ? playingAudioDurationMs
          : audioDurations.getOrDefault(key, parseAudioDuration(message.text));
      audioPreview.bind(new RectF(bodyLeft, attachmentTop, bodyRight, bubbleBottom),
          chatProfile, key, playing,
          playing ? playingAudioProgressMs : 0L, duration,
          parseAudioWaveform(message.text));
    } else {
      audioPreview.hide();
    }
    long attachmentFinishedNanos = SystemClock.elapsedRealtimeNanos();

    MessageNoticeComponent forwardedNotice =
        item.find("forwarded_notice", MessageNoticeComponent.class);
    if (model.forwarded) {
      forwardedNotice.bind(
          positiveRect(bodyLeft + NOTICE_LEFT_PX, headingTop,
              bodyRight - NOTICE_PADDING_PX, headingTop + NOTICE_ICON_SIZE_PX),
          "Forwarded", NOTICE_ICON_SIZE_PX, NOTICE_TEXT_GAP_PX);
    } else {
      forwardedNotice.hide();
    }

    ReplyContent replied = message.repliedMessageId == null
        ? null : replyContents.get(message.repliedMessageId);
    if (replied == null) replied = ReplyContent.text("Message unavailable");
    String repliedSender = message.repliedMessageId == null
        ? "" : replySenders.get(message.repliedMessageId);
    ReplyPreviewComponent replyPreview = item.find("reply_preview", ReplyPreviewComponent.class);
    if (hasReply) {
      float replyTop = headingTop + metrics.forwardedHeight;
      replyPreview.setCornerRadius(px(44f) * attachmentContentScale).bind(
          positiveRect(bodyLeft + REPLY_BOX_INSET_PX, replyTop,
              bodyRight - REPLY_BOX_INSET_PX,
              replyTop + metrics.replyHeight - REPLY_MESSAGE_GAP_PX),
          replySenderLabel(repliedSender), replied, message.repliedMessageId);
    } else {
      replyPreview.hide();
    }
    long replyFinishedNanos = SystemClock.elapsedRealtimeNanos();

    float messageTop = hasAttachmentCaption
        ? attachmentBottom + MESSAGE_TOP_PADDING_PX
        : headingTop + metrics.forwardedHeight + metrics.replyHeight;
    CallPreviewComponent callPreview = item.find("call_preview", CallPreviewComponent.class);
    if (call) {
      callPreview.bind(new RectF(left, attachmentTop, right, bubbleBottom),
          callIcon(message, own), model.displayText, model.forwarded);
    } else {
      callPreview.hide();
    }
    ForwardedMessagePanelComponent forwardedPanel =
        item.find("forwarded_panel", ForwardedMessagePanelComponent.class);
    if (model.forwarded && !attachmentPreview && !call) {
      forwardedPanel.bind(positiveRect(
          bodyLeft + FORWARDED_PANEL_INSET_PX,
          messageTop - FORWARDED_PANEL_INSET_PX - FORWARDED_INNER_TOP_GAP_PX,
          bodyRight - FORWARDED_PANEL_INSET_PX,
          bubbleBottom - FORWARDED_PANEL_INSET_PX));
    } else {
      forwardedPanel.hide();
    }
    float textLeft = bodyLeft + MESSAGE_HORIZONTAL_PADDING_PX;
    float fullTextRight = bodyRight - MESSAGE_HORIZONTAL_PADDING_PX;
    float textRight =
        metrics.metadataInline
            ? fullTextRight
            : Math.min(
                fullTextRight,
                textLeft + metrics.renderedTextWidth + MESSAGE_TEXT_WIDTH_SAFETY_PX);
    float textBottom = messageTop + metrics.textHeight;
    boolean showMessageText = (!attachmentPreview || hasAttachmentCaption)
        && !model.deleted && !call;
    item.find("message_text", PreparedMessageTextComponent.class).bind(
        positiveRect(textLeft, messageTop, textRight, textBottom),
        metrics.messageLayout, showMessageText).setHighlight(searchQuery);
    long textFinishedNanos = SystemClock.elapsedRealtimeNanos();

    float contentLeft = bodyLeft + MESSAGE_HORIZONTAL_PADDING_PX;
    float metadataRight = bodyRight - MESSAGE_META_RIGHT_PX;
    if (attachmentPreview || model.forwarded || call) metadataRight -= 10f;
    float tickRight = metadataRight;
    float tickLeft = tickRight - MESSAGE_TICK_WIDTH_PX;
    float pinRight = model.showDelivery
        ? tickLeft - MESSAGE_PIN_GAP_PX : metadataRight;
    float pinLeft = pinRight - MESSAGE_TICK_WIDTH_PX;
    float timeRight = model.pinned
        ? pinLeft - MESSAGE_PIN_GAP_PX
        : model.showDelivery
            ? tickLeft - MESSAGE_TIME_TICK_GAP_PX : metadataRight;
    float timeLeft = timeRight - metrics.timeWidth;
    float timeBottom = bubbleBottom
        - ((attachmentPreview || call) && !hasAttachmentCaption
            ? (MEDIA_PADDING_PX + 8f) * attachmentContentScale : MESSAGE_TIME_BOTTOM_PX);
    RectF timeBounds = positiveRect(timeLeft - MESSAGE_TIME_RECT_EXTRA_PX,
        timeBottom - metrics.timeHeight, timeRight, timeBottom);
    MessageNoticeComponent deletedNotice =
        item.find("deleted_notice", MessageNoticeComponent.class);
    if (model.deleted) {
      deletedNotice.bind(
          positiveRect(bodyLeft + NOTICE_LEFT_PX, headingTop,
              bodyRight - NOTICE_PADDING_PX, headingTop + metrics.textHeight),
          "This Message was deleted", NOTICE_ICON_SIZE_PX, NOTICE_TEXT_GAP_PX);
    } else {
      deletedNotice.hide();
    }
    float metadataBottom = bubbleBottom - (hasAttachmentCaption
        ? MESSAGE_TICK_BOTTOM_PX : MESSAGE_TICK_BOTTOM_PX * attachmentContentScale);
    RectF deliveryBounds = positiveRect(tickLeft,
        metadataBottom - MESSAGE_TICK_HEIGHT_PX, tickRight, metadataBottom);
    RectF pinnedBounds = positiveRect(pinLeft,
        metadataBottom - MESSAGE_TICK_HEIGHT_PX, pinRight, metadataBottom);
    item.find("message_metadata", MessageMetadataComponent.class).bind(
        timeBounds, metrics.timeLayout, messageStatusIcon(message), deliveryBounds,
        model.showDelivery, messagePinnedIcon, pinnedBounds, model.pinned,
        media && !hasAttachmentCaption);
    if (profiler != null) {
      long bindFinishedNanos = SystemClock.elapsedRealtimeNanos();
      profiler.rowBindSections(position, message.messageId,
          setupFinishedNanos - bindStartedNanos,
          attachmentFinishedNanos - setupFinishedNanos,
          replyFinishedNanos - attachmentFinishedNanos,
          textFinishedNanos - replyFinishedNanos,
          bindFinishedNanos - textFinishedNanos);
      profiler.rowBound(
          bindFinishedNanos - bindStartedNanos,
          position,
          message.messageId);
    }
    logLocationRendered(message);
  }

  void release() {
    synchronized (renderModelCache) {
      renderModelCache.clear();
    }
    synchronized (metricsCache) {
      metricsCache.clear();
    }
    replySenders.clear();
    replyContents.clear();
    mediaPrefetches.clear();
    boundRows.clear();
    measurementTools.remove();
  }

  void refreshMeasuredRows() {
    notifyDataSetChanged();
  }

  private MessageMetrics metrics(MessageEntity message, float availableWidth) {
    return metrics(renderModel(message), availableWidth);
  }

  private MessageMetrics metrics(MessageRenderModel model, float availableWidth) {
    boolean own = model.own;
    boolean call = isCallType(model.mediaType);
    String value = model.displayText;
    String time = model.formattedTime;
    ReplyContent replyValue = model.repliedMessageId == null
        ? null : replyContents.get(model.repliedMessageId);
    if (replyValue == null) replyValue = ReplyContent.text("Message unavailable");
    String replySender = model.repliedMessageId == null
        ? "" : replySenderLabel(replySenders.get(model.repliedMessageId));
    MetricKey cacheKey = new MetricKey(
        model.stableMessageId,
        model.contentVersion ^ stableId(replyValue.signature) ^ stableId(replySender),
        Float.floatToIntBits(availableWidth));
    MessageMetrics cached;
    synchronized (metricsCache) {
      cached = metricsCache.get(cacheKey);
    }
    if (cached != null) return cached;
    long metricStartedNanos = SystemClock.elapsedRealtimeNanos();
    if (call) {
      float desiredWidth = FILE_BUBBLE_WIDTH_PX * ATTACHMENT_SCALE;
      float desiredHeight = FILE_BUBBLE_HEIGHT_PX * ATTACHMENT_SCALE;
      float scale = Math.min(1f, availableWidth / desiredWidth);
      float finalWidth = desiredWidth * scale;
      float replyHeight = model.repliedMessageId == null || model.repliedMessageId.isEmpty()
          ? 0f : replyBlockHeight(replySender, replyValue,
              finalWidth - REPLY_BOX_INSET_PX * 2f);
      float headerHeight = model.forwarded
          ? FORWARDED_ATTACHMENT_HEADER_HEIGHT_PX
          : replyHeight > 0f ? NOTICE_PADDING_PX : 0f;
      Paint callTimePaint = new Paint(measurementTools.get().timePaint);
      Paint.FontMetrics timeFont = callTimePaint.getFontMetrics();
      MessageMetrics callMetrics = new MessageMetrics(
          finalWidth,
          desiredHeight * scale + headerHeight + replyHeight,
          1f,
          1f,
          model.forwarded
              ? NOTICE_ICON_SIZE_PX + NOTICE_PADDING_PX + FORWARDED_LABEL_BOTTOM_GAP_PX
              : 0f,
          replyHeight,
          callTimePaint.measureText(model.formattedTime) + MESSAGE_TIME_RECT_EXTRA_PX,
          (float) Math.ceil(timeFont.descent - timeFont.ascent),
          true);
      callMetrics.timeLayout = timeLayout(model.formattedTime, callMetrics.timeWidth);
      synchronized (metricsCache) { metricsCache.put(cacheKey, callMetrics); }
      return callMetrics;
    }
    if (model.mediaType != null && !call) {
      if ("image".equals(model.mediaType) || "video".equals(model.mediaType)) {
        Boolean preparedPortrait = mediaPortraits.get(model.attachmentSource);
        if (preparedPortrait == null) {
          preparedPortrait = MediaPreviewCache.cachedPortrait(
              model.attachmentSource, "video".equals(model.mediaType));
          if (preparedPortrait != null) {
            mediaPortraits.put(model.attachmentSource, preparedPortrait);
          }
        }
      }
      float desiredWidth = "audio".equals(model.mediaType)
          ? AUDIO_BUBBLE_WIDTH_PX
          : isFileType(model.mediaType)
          ? FILE_BUBBLE_WIDTH_PX
          : "location".equals(model.mediaType) ? LOCATION_BUBBLE_WIDTH_PX
          : "video".equals(model.mediaType) ? VIDEO_BUBBLE_WIDTH_PX : IMAGE_BUBBLE_WIDTH_PX;
      float desiredHeight = "audio".equals(model.mediaType)
          ? AUDIO_BUBBLE_HEIGHT_PX
          : isFileType(model.mediaType)
          ? FILE_BUBBLE_HEIGHT_PX
          : "location".equals(model.mediaType) ? LOCATION_BUBBLE_HEIGHT_PX
          : "video".equals(model.mediaType) ? VIDEO_BUBBLE_HEIGHT_PX : IMAGE_BUBBLE_HEIGHT_PX;
      if (isFileType(model.mediaType)) {
        desiredHeight += fileTitleExtraHeight(model.attachmentName);
      } else if (Boolean.TRUE.equals(mediaPortraits.get(model.attachmentSource))) {
        desiredWidth = PORTRAIT_MEDIA_WIDTH_PX;
        desiredHeight = PORTRAIT_MEDIA_HEIGHT_PX;
      }
      desiredWidth *= ATTACHMENT_SCALE;
      desiredHeight *= ATTACHMENT_SCALE;
      float scale = Math.min(1f, availableWidth / desiredWidth);
      float finalWidth = desiredWidth * scale;
      float attachmentHeight = desiredHeight * scale;
      float replyHeight = model.repliedMessageId == null || model.repliedMessageId.isEmpty()
          ? 0f : replyBlockHeight(replySender, replyValue,
              finalWidth - REPLY_BOX_INSET_PX * 2f);
      float headerHeight = model.forwarded
          ? FORWARDED_HEADER_HEIGHT_PX
          : replyHeight > 0f ? NOTICE_PADDING_PX : 0f;
      Paint mediaTimePaint = new Paint(measurementTools.get().timePaint);
      Paint.FontMetrics timeFont = mediaTimePaint.getFontMetrics();
      boolean hasCaption = hasAttachmentCaption(model);
      StaticLayout captionLayout = null;
      float captionHeight = 0f;
      if (hasCaption) {
        int captionWidth = Math.max(1, Math.round(
            finalWidth - MESSAGE_HORIZONTAL_PADDING_PX * 2f));
        captionLayout = StaticLayout.Builder.obtain(model.displayText, 0,
                model.displayText.length(), measurementTools.get().messagePaint, captionWidth)
            .setIncludePad(false)
            .setLineSpacing(MESSAGE_LINE_SPACING_PX, 1f)
            .build();
        captionHeight = MESSAGE_TOP_PADDING_PX + captionLayout.getHeight()
            + MESSAGE_LINE_SPACING_PX
            + Math.max(MESSAGE_TICK_HEIGHT_PX,
                (float) Math.ceil(timeFont.descent - timeFont.ascent))
            + MESSAGE_BOTTOM_PADDING_PX;
      }
      MessageMetrics mediaMetrics = new MessageMetrics(
          finalWidth,
          attachmentHeight + headerHeight + replyHeight + captionHeight,
          hasCaption ? finalWidth - MESSAGE_HORIZONTAL_PADDING_PX * 2f : 1f,
          hasCaption ? captionLayout.getHeight() : 1f,
          model.forwarded
              ? NOTICE_ICON_SIZE_PX + NOTICE_PADDING_PX + FORWARDED_EXTRA_HEIGHT_PX
              : 0f,
          replyHeight,
          mediaTimePaint.measureText(model.formattedTime)
              + MESSAGE_TIME_RECT_EXTRA_PX,
          (float) Math.ceil(timeFont.descent - timeFont.ascent),
          !hasCaption);
      // Forwarded attachment rows already reserve their leading notice padding separately.
      mediaMetrics.attachmentHeight = Math.max(1f,
          attachmentHeight - (model.forwarded ? NOTICE_PADDING_PX : 0f));
      mediaMetrics.messageLayout = captionLayout;
      mediaMetrics.timeLayout = timeLayout(
          model.formattedTime, mediaMetrics.timeWidth, !hasCaption);
      synchronized (metricsCache) { metricsCache.put(cacheKey, mediaMetrics); }
      return mediaMetrics;
    }
    boolean hasReply = !model.deleted
        && model.repliedMessageId != null && !model.repliedMessageId.isEmpty();
    float tailWidth = model.forwarded || hasReply ? 0f : px(MESSAGE_TAIL_WIDTH_PX);
    MeasurementTools tools = measurementTools.get();
    TextPaint paint = tools.messagePaint;
    Paint timePaint = tools.timePaint;
    float timeWidth = timePaint.measureText(time) + MESSAGE_TIME_RECT_EXTRA_PX;
    float metadataWidth = timeWidth
        + (model.showDelivery ? MESSAGE_TIME_TICK_GAP_PX + MESSAGE_TICK_WIDTH_PX : 0f)
        + (model.pinned ? MESSAGE_PIN_GAP_PX + MESSAGE_TICK_WIDTH_PX : 0f);
    Paint.FontMetrics timeFont = timePaint.getFontMetrics();
    float metadataHeight = Math.max(
        MESSAGE_TICK_HEIGHT_PX, (float) Math.ceil(timeFont.descent - timeFont.ascent));
    if (model.deleted) {
      Paint noticePaint = tools.noticePaint;
      Paint.FontMetrics noticeFont = noticePaint.getFontMetrics();
      float noticeHeight = Math.max(NOTICE_ICON_SIZE_PX,
          (float) Math.ceil(noticeFont.descent - noticeFont.ascent));
      float noticeWidth = NOTICE_ICON_SIZE_PX + NOTICE_TEXT_GAP_PX
          + noticePaint.measureText("This Message was deleted");
      float minimumBodyWidth = 170f * MESSAGE_SCALE;
      float maximumBubbleWidth = availableWidth * .80f;
      float bodyWidth = Math.max(minimumBodyWidth,
          Math.max(NOTICE_LEFT_PX + noticeWidth + NOTICE_PADDING_PX,
              NOTICE_PADDING_PX + metadataWidth + MESSAGE_META_RIGHT_PX));
      float bubbleWidth = Math.min(maximumBubbleWidth, tailWidth + bodyWidth);
      float bubbleHeight = NOTICE_PADDING_PX + noticeHeight + NOTICE_PADDING_PX
          + metadataHeight + MESSAGE_TIME_BOTTOM_PX;
      MessageMetrics deletedMetrics = new MessageMetrics(
          bubbleWidth, bubbleHeight, noticeWidth, noticeHeight,
          0f, 0f, timeWidth, metadataHeight, false);
      deletedMetrics.timeLayout = timeLayout(model.formattedTime, deletedMetrics.timeWidth);
      synchronized (metricsCache) { metricsCache.put(cacheKey, deletedMetrics); }
      return deletedMetrics;
    }
    float longestLine = longestLineWidth(value, paint);
    if (model.forwarded) {
      longestLine = Math.max(longestLine,
          NOTICE_ICON_SIZE_PX + NOTICE_TEXT_GAP_PX
              + tools.noticePaint.measureText("Forwarded"));
    }
    float replyDesiredBodyWidth = 0f;
    if (hasReply) {
      float replyContentWidth = Math.max(
          replyPreferredContentWidth(replyValue, tools),
          tools.replySenderPaint.measureText(replySender));
      replyDesiredBodyWidth = REPLY_BOX_INSET_PX * 2f + REPLY_TEXT_LEFT_PX
          + replyContentWidth + REPLY_TEXT_RIGHT_PX;
    }
    float minimumBodyWidth = 170f * MESSAGE_SCALE;
    float maximumBubbleWidth = availableWidth * .80f;
    float desiredBodyWidth =
        Math.max(
            minimumBodyWidth,
            MESSAGE_HORIZONTAL_PADDING_PX
                + longestLine
                + MESSAGE_TEXT_META_GAP_PX
                + metadataWidth
                + MESSAGE_META_RIGHT_PX);
    if (model.forwarded) {
      float forwardedWidth = NOTICE_ICON_SIZE_PX + NOTICE_TEXT_GAP_PX
          + tools.noticePaint.measureText("Forwarded");
      desiredBodyWidth = Math.max(desiredBodyWidth,
          NOTICE_LEFT_PX + forwardedWidth + NOTICE_PADDING_PX);
    }
    desiredBodyWidth = Math.max(desiredBodyWidth, replyDesiredBodyWidth);
    float bubbleWidth =
        Math.max(
            tailWidth + minimumBodyWidth,
            Math.min(maximumBubbleWidth, tailWidth + desiredBodyWidth));
    float bodyWidth = bubbleWidth - tailWidth;
    float textWidth = Math.max(1f,
        bodyWidth - MESSAGE_HORIZONTAL_PADDING_PX * 2f);
    StaticLayout layout =
        StaticLayout.Builder.obtain(
                value, 0, value.length(), paint, Math.max(1, Math.round(textWidth)))
            .setIncludePad(false)
            .setLineSpacing(MESSAGE_LINE_SPACING_PX, 1f)
            .build();
    int lastLine = Math.max(0, layout.getLineCount() - 1);
    float lastLineWidth = layout.getLineWidth(lastLine);
    float renderedTextWidth = 1f;
    for (int line = 0; line < layout.getLineCount(); line++) {
      renderedTextWidth = Math.max(renderedTextWidth, layout.getLineWidth(line));
    }
    float inlineWidth = bodyWidth - MESSAGE_HORIZONTAL_PADDING_PX
        - MESSAGE_META_RIGHT_PX;
    boolean metadataInline =
        lastLineWidth + MESSAGE_TEXT_META_GAP_PX + metadataWidth <= inlineWidth;
    if (!metadataInline) {
      float stackedTextWidth =
          MESSAGE_HORIZONTAL_PADDING_PX * 2f
              + renderedTextWidth
              + MESSAGE_TEXT_WIDTH_SAFETY_PX;
      float stackedMetadataWidth =
          MESSAGE_HORIZONTAL_PADDING_PX + metadataWidth + MESSAGE_META_RIGHT_PX;
      bodyWidth =
          Math.max(minimumBodyWidth,
              Math.max(replyDesiredBodyWidth, Math.max(stackedTextWidth, stackedMetadataWidth)));
      bubbleWidth = Math.min(maximumBubbleWidth, tailWidth + bodyWidth);
    }
    float replyHeight = hasReply
        ? replyBlockHeight(replySender, replyValue,
            bodyWidth - REPLY_BOX_INSET_PX * 2f)
        : 0f;
    float forwardedHeight = model.forwarded
        ? NOTICE_ICON_SIZE_PX + NOTICE_PADDING_PX + FORWARDED_EXTRA_HEIGHT_PX : 0f;
    float messageTop = (model.forwarded || hasReply
            ? NOTICE_PADDING_PX : MESSAGE_TOP_PADDING_PX)
        + forwardedHeight + replyHeight;
    float metadataTop =
        messageTop
            + (metadataInline
                ? layout.getLineTop(lastLine)
                : layout.getHeight() + MESSAGE_LINE_SPACING_PX);
    float contentBottom =
        Math.max(
            messageTop + layout.getHeight(), metadataTop + metadataHeight);
    float bubbleHeight = contentBottom + MESSAGE_BOTTOM_PADDING_PX;
    MessageMetrics result = new MessageMetrics(
        bubbleWidth,
        bubbleHeight,
        renderedTextWidth,
        layout.getHeight(),
        forwardedHeight,
        replyHeight,
        timeWidth,
        metadataHeight,
        metadataInline);
    result.messageLayout = layout;
    result.timeLayout = timeLayout(model.formattedTime, result.timeWidth);
    synchronized (metricsCache) {
      metricsCache.put(cacheKey, result);
    }
    if (profiler != null) {
      profiler.metricPrepared(
          SystemClock.elapsedRealtimeNanos() - metricStartedNanos,
          String.valueOf(model.stableMessageId),
          Looper.myLooper() != Looper.getMainLooper());
    }
    return result;
  }

  private StaticLayout timeLayout(String value, float width) {
    return timeLayout(value, width, false);
  }

  private StaticLayout timeLayout(String value, float width, boolean overMedia) {
    TextPaint paint = new TextPaint(measurementTools.get().timePaint);
    if (overMedia) paint.setColor(0xFFFFFFFF);
    return StaticLayout.Builder.obtain(value, 0, value.length(), paint,
            Math.max(1, Math.round(width)))
        .setIncludePad(false)
        .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
        .setMaxLines(1)
        .build();
  }

  private MessageRenderModel renderModel(MessageEntity message) {
    int attachmentState = visualAttachmentState(message);
    String sourceSignature = renderSourceSignature(message, attachmentState);
    String cacheKey = messageKey(message, -1);
    synchronized (renderModelCache) {
      MessageRenderModel cached = renderModelCache.get(cacheKey);
      if (cached != null && cached.sourceSignature.equals(sourceSignature)) return cached;
    }
    String displayed = displayMessage(message, attachmentState);
    String formattedTime = formatMessageTime(message.sentTime);
    boolean deleted = isDeletedMessage(message);
    boolean own = isOwn(message);
    MessageRenderModel created = new MessageRenderModel(
        displayed,
        formattedTime,
        message.repliedMessageId,
        !deleted && message.forwardedFrom != null && !message.forwardedFrom.isEmpty(),
        !deleted && message.pinned,
        deleted,
        own && !deleted,
        own,
        mediaType(message),
        message.attachmentName,
        attachmentSource(message),
        sourceSignature,
        stableId(cacheKey),
        stableId(displayed + '\u0001' + formattedTime + '\u0001'
            + String.valueOf(message.repliedMessageId) + '\u0001'
            + String.valueOf(message.forwardedFrom) + '\u0001' + message.pinned
            + '\u0001' + deleted + '\u0001' + own
            + '\u0001' + sourceSignature));
    synchronized (renderModelCache) {
      renderModelCache.put(cacheKey, created);
    }
    return created;
  }

  private int visualAttachmentState(MessageEntity message) {
    String type = message.messageType == null ? "text" : message.messageType;
    if (!("image".equals(type) || "video".equals(type)
        || "audio".equals(type) || "file".equals(type))) return -1;
    return attachmentStateProvider.attachmentState(message);
  }

  private String renderSourceSignature(MessageEntity message, int attachmentState) {
    return messageKey(message, -1) + '\u0001'
        + String.valueOf(message.senderId) + '\u0001'
        + String.valueOf(message.repliedMessageId) + '\u0001'
        + message.sentTime + '\u0001'
        + String.valueOf(message.status) + '\u0001'
        + String.valueOf(message.deliveredTime) + '\u0001'
        + String.valueOf(message.readTime) + '\u0001'
        + String.valueOf(message.text) + '\u0001'
        + String.valueOf(message.messageType) + '\u0001'
        + String.valueOf(message.attachmentName) + '\u0001'
        + String.valueOf(message.attachmentSize) + '\u0001'
        + String.valueOf(message.attachmentWidth) + '\u0001'
        + String.valueOf(message.attachmentHeight) + '\u0001'
        + String.valueOf(message.attachmentOrientation) + '\u0001'
        + String.valueOf(message.attachmentDurationMs) + '\u0001'
        + String.valueOf(message.attachmentUrl) + '\u0001'
        + String.valueOf(message.attachmentLocalUri) + '\u0001'
        + String.valueOf(message.latitude) + '\u0001'
        + String.valueOf(message.longitude) + '\u0001'
        + String.valueOf(message.forwardedFrom) + '\u0001'
        + message.pinned + '\u0001' + String.valueOf(message.pinnedBy) + '\u0001'
        + String.valueOf(message.deletedText) + '\u0001'
        + attachmentState + '\u0001'
        + attachmentStateProvider.downloadedBytes(message) + '\u0001'
        + attachmentStateProvider.totalBytes(message);
  }

  private String displayMessage(MessageEntity message, int attachmentState) {
    String type = message.messageType == null ? "text" : message.messageType;
    String displayed;
    if ("location".equals(type) && message.latitude != null && message.longitude != null) {
      displayed =
          "[LOCATION]\n"
              + formatCoordinate(message.latitude)
              + ", "
              + formatCoordinate(message.longitude)
              + "\nTap to open map";
    } else if ("image".equals(type) || "video".equals(type) || "file".equals(type)) {
      displayed = message.text == null ? "" : message.text.trim();
    } else if ("audio".equals(type)) {
      displayed = "";
    } else if (isCallType(type)) {
      displayed = stripCallLabel(message.text);
    } else {
      displayed = message.text == null ? "" : message.text;
    }
    return displayed + statusSuffix(message);
  }

  private static String stripCallLabel(String value) {
    if (value == null) return "";
    return value.replace("[Voice Call]", "")
        .replace("[Video Call]", "")
        .replace('\n', ' ')
        .trim()
        .replaceAll("\\s+", " ");
  }

  private String formatMessageTime(long sentTime) {
    if (sentTime <= 0L) return "";
    long milliseconds = timestampMillis(sentTime);
    synchronized (messageTimeFormatter) {
      return messageTimeFormatter.format(new Date(milliseconds));
    }
  }

  private List<String> buildDateLabels(List<MessageEntity> values) {
    List<String> labels = new ArrayList<>(values.size());
    long now = System.currentTimeMillis();
    long previousTimestamp = -1L;
    for (MessageEntity message : values) {
      if (isReportEvent(message)) {
        boolean own = currentUser.equals(normalize(message.senderId));
        String peer = opponentName.isEmpty() ? "this contact" : opponentName;
        boolean blockEvent = "chat_block".equalsIgnoreCase(message.messageType);
        boolean unblockEvent = "chat_unblock".equalsIgnoreCase(message.messageType);
        labels.add(blockEvent
            ? own ? "You blocked " + peer : peer + " blocked you"
            : unblockEvent
                ? own ? "You unblocked " + peer : peer + " unblocked you"
                : own ? "You reported " + peer : peer + " reported you");
        long reportTimestamp = timestampMillis(message.sentTime);
        if (reportTimestamp > 0L) previousTimestamp = reportTimestamp;
        continue;
      }
      long timestamp = timestampMillis(message.sentTime);
      if (timestamp <= 0L || (previousTimestamp > 0L && sameLocalDay(previousTimestamp, timestamp))) {
        labels.add("");
      } else {
        labels.add(formatDateLabel(timestamp, now));
      }
      if (timestamp > 0L) previousTimestamp = timestamp;
    }
    return labels;
  }

  private static boolean isReportEvent(MessageEntity message) {
    if (message == null || message.messageType == null) return false;
    String type = message.messageType.trim().toLowerCase(Locale.US);
    return "report".equals(type) || "chat_report".equals(type)
        || "chat_block".equals(type) || "chat_unblock".equals(type);
  }

  /** Returns the first match and invalidates bound-row signatures for highlight-only changes. */
  int setSearchQuery(String value) {
    String next = value == null ? "" : value.trim().toLowerCase(Locale.US);
    if (!searchQuery.equals(next)) {
      searchQuery = next;
      boundRows.clear();
      notifyDataSetChanged();
    }
    if (next.isEmpty()) return -1;
    for (int index = 0; index < messages.size(); index++) {
      String text = messages.get(index).text;
      if (text != null && text.toLowerCase(Locale.US).contains(next)) return index;
    }
    return -1;
  }

  List<Integer> matchingPositions(String value) {
    String query = value == null ? "" : value.trim().toLowerCase(Locale.US);
    List<Integer> matches = new ArrayList<>();
    if (query.isEmpty()) return matches;
    for (int index = 0; index < messages.size(); index++) {
      if (isReportEvent(messages.get(index))) continue;
      String text = messages.get(index).text;
      if (text != null && text.toLowerCase(Locale.US).contains(query)) matches.add(index);
    }
    return matches;
  }

  private String formatDateLabel(long timestamp, long now) {
    if (sameLocalDay(timestamp, now)) return "Today";
    Calendar yesterday = Calendar.getInstance();
    yesterday.setTimeInMillis(now);
    yesterday.add(Calendar.DAY_OF_YEAR, -1);
    if (sameLocalDay(timestamp, yesterday.getTimeInMillis())) return "Yesterday";
    synchronized (messageDateFormatter) {
      return messageDateFormatter.format(new Date(timestamp));
    }
  }

  private boolean hasDateLabel(int position) {
    return position >= 0 && position < dateLabels.size() && !dateLabels.get(position).isEmpty();
  }

  private static boolean sameLocalDay(long firstTimestamp, long secondTimestamp) {
    Calendar first = Calendar.getInstance();
    Calendar second = Calendar.getInstance();
    first.setTimeInMillis(firstTimestamp);
    second.setTimeInMillis(secondTimestamp);
    return first.get(Calendar.ERA) == second.get(Calendar.ERA)
        && first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
        && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
  }

  private static long timestampMillis(long timestamp) {
    if (timestamp <= 0L) return timestamp;
    return timestamp < 100_000_000_000L ? timestamp * 1000L : timestamp;
  }

  private Bitmap messageStatusIcon(MessageEntity message) {
    if (message.readTime != null || "seen".equals(message.status)) return messageReadIcon;
    if (message.deliveredTime != null || "delivered".equals(message.status)) {
      return messageDeliveredIcon;
    }
    if ("sent".equals(message.status)) return messageSentIcon;
    return messageSendingIcon;
  }

  private String statusSuffix(MessageEntity message) {
    if (!isOwn(message)) return "";
    if ("failed".equals(message.status)) return "\nFailed • Tap to resend";
    return "";
  }

  private Text.Builder textBuilder(
      String id, String value, RectF region, float size, int color, FontVariation variation) {
    return new Text.Builder(context, id, value, region)
        .setFont(NativeFonts.INTER)
        .setFontVariations(variation)
        .setTextSizePx(size)
        .setTextColor(color)
        .setVerticalAlignment(Text.VerticalAlignment.CENTER)
        .setMaxLines(4);
  }

  private boolean isOwn(MessageEntity message) {
    return currentUser.equals(normalize(message.senderId));
  }

  private static boolean isMedia(MessageEntity message) {
    String type = mediaType(message);
    return "image".equals(type) || "video".equals(type);
  }

  private static boolean isFile(MessageEntity message) {
    return isFileType(mediaType(message));
  }

  private static boolean isFileType(String type) {
    return "file".equals(type);
  }

  private static boolean isAudio(MessageEntity message) {
    return "audio".equals(mediaType(message));
  }

  private static boolean isLocation(MessageEntity message) {
    return "location".equals(mediaType(message));
  }

  private static boolean isCall(MessageEntity message) {
    return isCallType(mediaType(message));
  }

  private static boolean isCallType(String type) {
    return "voice_call".equals(type) || "video_call".equals(type);
  }

  private static String mediaType(MessageEntity message) {
    if (message == null || isDeletedMessage(message)) return null;
    String type = message.messageType;
    if ("location".equals(type) && message.latitude != null && message.longitude != null) {
      return "location";
    }
    return "image".equals(type) || "video".equals(type) || "audio".equals(type)
        || "file".equals(type)
        || "voice_call".equals(type) || "video_call".equals(type) ? type : null;
  }

  private static Boolean attachmentPortrait(MessageEntity message) {
    if (message == null) return null;
    if (message.attachmentWidth != null && message.attachmentWidth > 0
        && message.attachmentHeight != null && message.attachmentHeight > 0) {
      return message.attachmentHeight > message.attachmentWidth;
    }
    if ("portrait".equalsIgnoreCase(message.attachmentOrientation)) return true;
    if ("landscape".equalsIgnoreCase(message.attachmentOrientation)) return false;
    return null;
  }

  private Bitmap callIcon(MessageEntity message, boolean own) {
    boolean video = "video_call".equals(message.messageType);
    String label = message.text == null ? "" : message.text.toLowerCase(Locale.US);
    boolean missed = label.contains("missed");
    boolean didntConnect = label.contains("didn't connect") || label.contains("did not connect");
    if (video) {
      if (missed) return callVideoMissedIcon;
      if (didntConnect || !own) return callVideoIncomingIcon;
      return callVideoOutgoingIcon;
    }
    if (missed) return callPhoneMissedIcon;
    if (didntConnect || !own) return callPhoneIncomingIcon;
    return callPhoneOutgoingIcon;
  }

  private static String fileType(MessageEntity message) {
    String name = message.attachmentName;
    if (name != null) {
      int dot = name.lastIndexOf('.');
      if (dot >= 0 && dot < name.length() - 1) {
        return name.substring(dot + 1).toUpperCase(Locale.US);
      }
    }
    String mime = message.attachmentMimeType;
    if (mime != null) {
      int slash = mime.lastIndexOf('/');
      if (slash >= 0 && slash < mime.length() - 1) {
        return mime.substring(slash + 1).toUpperCase(Locale.US);
      }
    }
    return "FILE";
  }

  private static String attachmentSource(MessageEntity message) {
    if (message == null) return "";
    String local = message.attachmentLocalUri;
    if (local != null && !local.trim().isEmpty()) return local;
    return message.attachmentUrl == null ? "" : message.attachmentUrl;
  }

  private void onMediaOrientationAvailable(String source, boolean portrait) {
    if (source == null || source.isEmpty()) return;
    Boolean previous = mediaPortraits.put(source, portrait);
    if (previous != null && previous == portrait) return;
    synchronized (metricsCache) { metricsCache.clear(); }
    if (mediaMetricsListener != null) mediaMetricsListener.onMediaMetricsChanged();
  }

  private static boolean isDeletedMessage(MessageEntity message) {
    return message != null && (message.deletedText != null
        || "This Message was deleted".equals(message.text));
  }

  private int indexOfMessageKeyFrom(String key, int startIndex) {
    for (int index = Math.max(0, startIndex); index < messages.size(); index++) {
      if (key.equals(messageKey(messages.get(index), index))) return index;
    }
    return -1;
  }

  private int existingSequenceStart(List<MessageEntity> nextMessages) {
    int inserted = nextMessages.size() - messages.size();
    if (inserted <= 0) return -1;
    for (int start = 0; start <= inserted; start++) {
      boolean sameExistingSequence = true;
      for (int oldIndex = 0; oldIndex < messages.size(); oldIndex++) {
        if (!messageKey(messages.get(oldIndex), oldIndex)
            .equals(messageKey(nextMessages.get(start + oldIndex), start + oldIndex))) {
          sameExistingSequence = false;
          break;
        }
      }
      if (sameExistingSequence) return start;
    }
    return -1;
  }

  private static String messageKey(MessageEntity message, int fallbackPosition) {
    if (message.messageId != null && !message.messageId.isEmpty()) return message.messageId;
    if (message.clientMessageId != null && !message.clientMessageId.isEmpty()) {
      return message.clientMessageId;
    }
    return "position:" + fallbackPosition;
  }

  private static long stableId(String value) {
    long hash = 0xcbf29ce484222325L;
    for (int index = 0; index < value.length(); index++) {
      hash ^= value.charAt(index);
      hash *= 0x100000001b3L;
    }
    return hash;
  }

  private String formatSize(long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024f);
    return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
  }

  private static long parseAudioDuration(String value) {
    if (value == null) return 0L;
    String duration = value.trim();
    int metadataStart = duration.indexOf('|');
    if (metadataStart >= 0) duration = duration.substring(0, metadataStart).trim();
    int separator = duration.indexOf(':');
    if (separator <= 0 || separator >= duration.length() - 1) return 0L;
    try {
      long minutes = Long.parseLong(duration.substring(0, separator));
      long seconds = Long.parseLong(duration.substring(separator + 1));
      if (minutes < 0L || seconds < 0L || seconds > 59L) return 0L;
      return (minutes * 60L + seconds) * 1000L;
    } catch (NumberFormatException ignored) {
      return 0L;
    }
  }

  private static float[] parseAudioWaveform(String value) {
    if (value == null) return null;
    String marker = "|waveform=";
    int start = value.indexOf(marker);
    if (start < 0) return null;
    String encoded = value.substring(start + marker.length()).trim();
    if (encoded.isEmpty()) return null;
    String[] levels = encoded.split(",");
    if (levels.length < 2 || levels.length > 64) return null;
    float[] waveform = new float[levels.length];
    try {
      for (int index = 0; index < levels.length; index++) {
        int level = Integer.parseInt(levels[index].trim());
        waveform[index] = Math.max(.08f, Math.min(1f, level / 100f));
      }
      return waveform;
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private String formatCoordinate(double value) {
    return String.format(Locale.US, "%.6f", value);
  }

  private String capitalize(String value) {
    return value.substring(0, 1).toUpperCase(Locale.US) + value.substring(1);
  }

  private String captionSuffix(String caption) {
    return caption == null || caption.trim().isEmpty() ? "" : " — " + caption.trim();
  }

  private static boolean hasAttachmentCaption(MessageRenderModel model) {
    if (model == null || model.displayText == null || model.displayText.trim().isEmpty()) {
      return false;
    }
    return "image".equals(model.mediaType) || "video".equals(model.mediaType)
        || "file".equals(model.mediaType);
  }

  private static String normalize(String value) {
    if (value == null) return "";
    String normalized = value.trim();
    if (normalized.startsWith("<plus>")) normalized = normalized.substring(6);
    return normalized.startsWith("+") ? normalized.substring(1) : normalized;
  }

  private static RectF positiveRect(float left, float top, float right, float bottom) {
    if (Float.isNaN(left)
        || Float.isInfinite(left)
        || Float.isNaN(top)
        || Float.isInfinite(top)
        || Float.isNaN(right)
        || Float.isInfinite(right)
        || Float.isNaN(bottom)
        || Float.isInfinite(bottom)) {
      return new RectF(0f, 0f, 1f, 1f);
    }
    return new RectF(left, top, Math.max(left + 1f, right), Math.max(top + 1f, bottom));
  }

  private float px(float value) {
    return figmaConfig.toRuntime(value,
        Math.max(1, context.getResources().getDisplayMetrics().widthPixels));
  }

  private float figmaScale() {
    return figmaConfig.getScale(
        Math.max(1, context.getResources().getDisplayMetrics().widthPixels));
  }

  private void logLocationRendered(MessageEntity message) {
    if (!isLocation(message)) return;
    String clientId = message.clientMessageId == null || message.clientMessageId.isEmpty()
        ? message.messageId : message.clientMessageId;
    LocationRenderTiming timing = locationRenderTimings.remove(clientId);
    if (timing == null && message.messageId != null) {
      timing = locationRenderTimings.remove(message.messageId);
    }
    if (timing == null) return;
    long elapsedMs = SystemClock.elapsedRealtime() - timing.pressedElapsedMs;
    Log.i(
        LOCATION_PERF_TAG,
        "trace=" + timing.traceId
            + " event=render"
            + " elapsedMs=" + elapsedMs
            + " pressedWallTimeMs=" + timing.pressedWallMs
            + " clientMessageId=" + clientId);
  }

  private float sp(float value) {
    return value * context.getResources().getDisplayMetrics().scaledDensity;
  }

  private static float longestLineWidth(String value, TextPaint paint) {
    float longest = 0f;
    int lineStart = 0;
    for (int index = 0; index <= value.length(); index++) {
      if (index == value.length() || value.charAt(index) == '\n') {
        longest = Math.max(longest, paint.measureText(value, lineStart, index));
        lineStart = index + 1;
      }
    }
    return longest;
  }

  private float fileTitleExtraHeight(String title) {
    String value = title == null || title.trim().isEmpty() ? "File" : title.trim();
    TextPaint paint = measurementTools.get().fileTitlePaint;
    StaticLayout wrapped = StaticLayout.Builder.obtain(
            value, 0, value.length(), paint, Math.round(FILE_TITLE_WIDTH_PX))
        .setIncludePad(false)
        .setLineSpacing(FILE_TITLE_LINE_SPACING_PX, 1f)
        .build();
    StaticLayout single = StaticLayout.Builder.obtain(
            "Ag", 0, 2, paint, Math.round(FILE_TITLE_WIDTH_PX))
        .setIncludePad(false)
        .setLineSpacing(FILE_TITLE_LINE_SPACING_PX, 1f)
        .build();
    return Math.max(0f, wrapped.getHeight() - single.getHeight());
  }

  private float replyBlockHeight(String sender, ReplyContent content, float boxWidth) {
    MeasurementTools tools = measurementTools.get();
    int contentWidth = Math.max(1,
        Math.round(boxWidth - REPLY_TEXT_LEFT_PX - REPLY_TEXT_RIGHT_PX));
    String senderValue = sender == null || sender.isEmpty() ? "Unknown" : sender;
    StaticLayout senderLayout = StaticLayout.Builder.obtain(
            senderValue, 0, senderValue.length(), tools.replySenderPaint, contentWidth)
        .setIncludePad(false)
        .setMaxLines(1)
        .build();
    float contentHeight;
    if (content != null && content.isMedia()) {
      boolean portrait = Boolean.TRUE.equals(mediaPortraits.get(content.source));
      contentHeight = portrait ? REPLY_PORTRAIT_MEDIA_HEIGHT_PX
          : ReplyContent.VIDEO.equals(content.type)
              ? REPLY_VIDEO_LANDSCAPE_HEIGHT_PX : REPLY_IMAGE_LANDSCAPE_HEIGHT_PX;
    } else if (content != null && ReplyContent.LOCATION.equals(content.type)) {
      contentHeight = REPLY_LOCATION_HEIGHT_PX;
    } else if (content != null && (ReplyContent.AUDIO.equals(content.type)
        || ReplyContent.FILE.equals(content.type) || content.isCall())) {
      contentHeight = REPLY_FULL_ATTACHMENT_HEIGHT_PX;
    } else {
      String messageValue = content == null || content.text.isEmpty()
          ? "Message unavailable" : content.text;
      StaticLayout messageLayout = StaticLayout.Builder.obtain(
              messageValue, 0, messageValue.length(), tools.replyMessagePaint, contentWidth)
          .setIncludePad(false)
          .build();
      contentHeight = messageLayout.getHeight();
    }
    if (content != null && (content.isMedia()
        || ReplyContent.AUDIO.equals(content.type)
        || ReplyContent.FILE.equals(content.type)
        || ReplyContent.LOCATION.equals(content.type) || content.isCall())) {
      float preferredWidth = replyPreferredContentWidth(content, tools);
      contentHeight *= Math.min(1f, contentWidth / Math.max(1f, preferredWidth));
    }
    float boxHeight = REPLY_TEXT_TOP_PX + senderLayout.getHeight()
        + REPLY_SENDER_MESSAGE_GAP_PX + contentHeight + REPLY_TEXT_BOTTOM_PX;
    return boxHeight + REPLY_MESSAGE_GAP_PX;
  }

  private float replyPreferredContentWidth(
      ReplyContent content, MeasurementTools tools) {
    if (content != null && content.isMedia()) {
      return Boolean.TRUE.equals(mediaPortraits.get(content.source))
          ? REPLY_PORTRAIT_MEDIA_WIDTH_PX : REPLY_LANDSCAPE_MEDIA_WIDTH_PX;
    }
    if (content != null && (ReplyContent.AUDIO.equals(content.type)
        || ReplyContent.FILE.equals(content.type) || ReplyContent.LOCATION.equals(content.type)
        || content.isCall())) {
      return REPLY_FULL_ATTACHMENT_WIDTH_PX;
    }
    String value = content == null || content.text.isEmpty()
        ? "Message unavailable" : content.text;
    return longestLineWidth(value, tools.replyMessagePaint);
  }

  private String replySenderLabel(String senderId) {
    String value = senderId == null || "null".equals(senderId) ? "" : senderId.trim();
    if (currentUser.equals(normalize(value))) return "You";
    if (value.startsWith("<plus>")) value = "+" + value.substring(6);
    return value.isEmpty() ? "Unknown" : value;
  }

  private ReplyContent replyContent(MessageEntity message, MessageRenderModel model) {
    if (model.deleted) return ReplyContent.text("This Message was deleted");
    if ("image".equals(model.mediaType) || "video".equals(model.mediaType)) {
      return ReplyContent.media(model.mediaType, model.attachmentSource);
    }
    if ("audio".equals(model.mediaType)) {
      return ReplyContent.audio(parseAudioDuration(message.text),
          parseAudioWaveform(message.text));
    }
    if ("file".equals(model.mediaType)) {
      String title = message.attachmentName == null || message.attachmentName.trim().isEmpty()
          ? "File" : message.attachmentName.trim();
      String subtitle = fileType(message);
      if (message.attachmentSize != null) subtitle += "  .  " + formatSize(message.attachmentSize);
      return ReplyContent.file(title, subtitle);
    }
    if ("location".equals(model.mediaType)
        && message.latitude != null && message.longitude != null) {
      return ReplyContent.location(message.latitude, message.longitude);
    }
    if ("voice_call".equals(model.mediaType) || "video_call".equals(model.mediaType)) {
      return ReplyContent.call(model.mediaType, model.displayText, callIcon(message, model.own));
    }
    if (model.displayText != null && !model.displayText.trim().isEmpty()) {
      return ReplyContent.text(model.displayText.trim());
    }
    return ReplyContent.text("Message unavailable");
  }

  private static final class MeasurementTools {
    final TextPaint messagePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    final TextPaint replyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    final TextPaint replySenderPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    final TextPaint replyMessagePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    final TextPaint fileTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    final Paint noticePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    MeasurementTools(Typeface typeface, float replyTextSize) {
      messagePaint.setTypeface(typeface);
      messagePaint.setTextSize(MESSAGE_TEXT_SIZE_PX);
      messagePaint.setColor(MESSAGE_TEXT_COLOR);
      replyPaint.setTypeface(typeface);
      replyPaint.setTextSize(replyTextSize);
      replySenderPaint.setTypeface(
          android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
              ? Typeface.create(typeface, 600, false)
              : Typeface.create(typeface, Typeface.BOLD));
      replySenderPaint.setTextSize(REPLY_SENDER_TEXT_SIZE_PX);
      replyMessagePaint.setTypeface(
          android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
              ? Typeface.create(typeface, 100, false)
              : Typeface.create(typeface, Typeface.NORMAL));
      replyMessagePaint.setTextSize(REPLY_MESSAGE_TEXT_SIZE_PX);
      fileTitlePaint.setTypeface(typeface);
      fileTitlePaint.setTextSize(FILE_TITLE_TEXT_SIZE_PX);
      noticePaint.setTypeface(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
          ? Typeface.create(typeface, 100, false)
          : Typeface.create(typeface, Typeface.NORMAL));
      noticePaint.setTextSkewX(-0.22f);
      noticePaint.setTextSize(NOTICE_TEXT_SIZE_PX);
      timePaint.setTypeface(typeface);
      timePaint.setTextSize(MESSAGE_TIME_SIZE_PX);
      timePaint.setColor(MESSAGE_TIME_COLOR);
    }
  }

}
