package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Looper;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import com.ogfa.nativeviews.font.NativeFonts;
import com.ogfa.nativeviews.image.Image;
import com.ogfa.nativeviews.list.ComponentList;
import com.ogfa.nativeviews.text.FontVariation;
import com.ogfa.nativeviews.text.Text;
import com.ogfa.nativeviews.zlayer.ZLayer;
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
import java.util.concurrent.ConcurrentHashMap;

/** Owns chat-message presentation, cached measurement, row diffing, and binding. */
final class ChatMessageAdapter extends ComponentList.Adapter<MessageEntity> {
  private static final float MESSAGE_SCALE = 1.15f;
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
  private static final float MESSAGE_ROW_GAP_DP = 7f;
  private static final float MESSAGE_ROW_HALF_GAP_DP = MESSAGE_ROW_GAP_DP / 2f;
  private static final float MESSAGE_TAIL_WIDTH_DP = 4f;
  private static final float MESSAGE_ROW_HORIZONTAL_INSET_DP = 12f;

  interface AttachmentStateProvider {
    int attachmentState(MessageEntity message);
  }

  private final Context context;
  private final String currentUser;
  private final Bitmap transparent;
  private final Bitmap selectionBackground;
  private final Bitmap messageSendingIcon;
  private final Bitmap messageSentIcon;
  private final Bitmap messageDeliveredIcon;
  private final Bitmap messageReadIcon;
  private final Bitmap messagePinnedIcon;
  private final AttachmentStateProvider attachmentStateProvider;
  private final ChatPerformanceProfiler profiler;
  private final Set<String> selectedMessageIds;
  private final Typeface messageTypeface;
  private final SimpleDateFormat messageTimeFormatter;
  private final SimpleDateFormat messageDateFormatter;
  private final ThreadLocal<MeasurementTools> measurementTools;
  private final List<MessageEntity> messages = new ArrayList<>();
  private final List<String> presentationSignatures = new ArrayList<>();
  private final List<String> dateLabels = new ArrayList<>();
  private final Map<String, String> texts = new ConcurrentHashMap<>();
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
      Bitmap transparent,
      Bitmap selectionBackground,
      Bitmap messageSendingIcon,
      Bitmap messageSentIcon,
      Bitmap messageDeliveredIcon,
      Bitmap messageReadIcon,
      Bitmap messagePinnedIcon,
      AttachmentStateProvider attachmentStateProvider,
      ChatPerformanceProfiler profiler,
      Set<String> selectedMessageIds) {
    this.context = context;
    this.currentUser = normalize(currentUser);
    this.transparent = transparent;
    this.selectionBackground = selectionBackground;
    this.messageSendingIcon = messageSendingIcon;
    this.messageSentIcon = messageSentIcon;
    this.messageDeliveredIcon = messageDeliveredIcon;
    this.messageReadIcon = messageReadIcon;
    this.messagePinnedIcon = messagePinnedIcon;
    this.attachmentStateProvider = attachmentStateProvider;
    this.profiler = profiler;
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

  boolean submit(List<MessageEntity> values) {
    long submitStartedNanos = SystemClock.elapsedRealtimeNanos();
    int oldCount = messages.size();
    int insertedCount = 0, changedCount = 0, removedCount = 0, movedCount = 0;
    List<MessageEntity> nextMessages =
        values == null ? new ArrayList<>() : new ArrayList<>(values);
    Map<String, String> nextTexts = new HashMap<>();
    List<MessageRenderModel> nextModels = new ArrayList<>(nextMessages.size());
    List<String> nextSignatures = new ArrayList<>(nextMessages.size());
    List<String> nextDateLabels = buildDateLabels(nextMessages);
    for (MessageEntity message : nextMessages) {
      MessageRenderModel model = renderModel(message);
      nextModels.add(model);
      if (message.messageId != null) nextTexts.put(message.messageId, model.displayText);
    }
    for (int index = 0; index < nextModels.size(); index++) {
      MessageRenderModel model = nextModels.get(index);
      String repliedText = model.repliedMessageId == null
          ? null : nextTexts.get(model.repliedMessageId);
      nextSignatures.add(model.presentationSignature + '\u0001' + String.valueOf(repliedText)
          + '\u0001' + nextDateLabels.get(index));
    }
    boolean changed = !presentationSignatures.equals(nextSignatures);
    texts.clear();
    texts.putAll(nextTexts);
    if (!changed) {
      // Keep the freshest Room entities even when their presentation is unchanged.
      messages.clear();
      messages.addAll(nextMessages);
      dateLabels.clear();
      dateLabels.addAll(nextDateLabels);
      if (profiler != null) {
        profiler.adapterSubmit(
            SystemClock.elapsedRealtimeNanos() - submitStartedNanos,
            oldCount, nextMessages.size(), 0, 0, 0, 0);
      }
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

  float contentStartAt(int position, float availableWidth) {
    float start = 0f;
    for (int index = 0; index < Math.min(position, messages.size()); index++) {
      start += rowHeight(messages.get(index), index, availableWidth);
    }
    return start;
  }

  float rowHeight(MessageEntity message, int position, float availableWidth) {
    float density = context.getResources().getDisplayMetrics().density;
    float dateHeight = hasDateLabel(position) ? DateNotifierComponent.blockHeight(density) : 0f;
    return dateHeight + metrics(message, availableWidth).bubbleHeight + dp(MESSAGE_ROW_GAP_DP);
  }

  void prepareMetrics(List<MessageEntity> values, float availableWidth) {
    for (MessageEntity message : values) {
      if (Thread.currentThread().isInterrupted()) return;
      metrics(renderModel(message), availableWidth);
    }
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
    float horizontalInset = dp(MESSAGE_ROW_HORIZONTAL_INSET_DP);
    float bubbleWidth = (width - horizontalInset * 2f) * .66f;
    float left = type == 1 ? width - horizontalInset - bubbleWidth : horizontalInset;
    float right = type == 1 ? width - horizontalInset : horizontalInset + bubbleWidth;
    float tailWidth = dp(MESSAGE_TAIL_WIDTH_DP);
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
            context.getResources().getDisplayMetrics().density));
    row.add(
        new MessageBubbleComponent(
            scope.id("bubble"),
            new RectF(
                left,
                dp(MESSAGE_ROW_HALF_GAP_DP),
                right,
                height - dp(MESSAGE_ROW_HALF_GAP_DP)),
            context.getResources().getDisplayMetrics().density,
            type == 1));
    row.add(
        textBuilder(
            scope.id("forwarded"),
            "Forwarded",
            new RectF(bodyLeft + dp(12), dp(7), bodyRight - dp(12), dp(30)),
            sp(11),
            type == 1 ? ACCENT : SECONDARY,
            FontVariation.REGULAR));
    row.add(
        textBuilder(
            scope.id("reply"),
            "",
            new RectF(bodyLeft + dp(12), dp(7), bodyRight - dp(12), dp(30)),
            sp(11),
            type == 1 ? ACCENT : SECONDARY,
            FontVariation.REGULAR));
    row.add(
        textBuilder(
                scope.id("message"),
                "",
                new RectF(
                    bodyLeft + MESSAGE_HORIZONTAL_PADDING_PX,
                    MESSAGE_TOP_PADDING_PX,
                    bodyRight - MESSAGE_HORIZONTAL_PADDING_PX,
                    height - MESSAGE_BOTTOM_PADDING_PX),
                MESSAGE_TEXT_SIZE_PX,
                MESSAGE_TEXT_COLOR,
                FontVariation.REGULAR)
            .setLineSpacingPx(MESSAGE_LINE_SPACING_PX)
            .setVerticalAlignment(Text.VerticalAlignment.TOP)
            .setMaxLines(100));
    row.add(
        textBuilder(
                scope.id("time"),
                "",
                new RectF(0f, 0f, 1f, 1f),
                MESSAGE_TIME_SIZE_PX,
                MESSAGE_TIME_COLOR,
                FontVariation.REGULAR)
            .setAlignment(Text.Alignment.END)
            .setWrapEnabled(false)
            .setMaxLines(1));
    row.add(
        new Image.Builder(
                context, scope.id("delivery"), transparent, new RectF(0f, 0f, 1f, 1f))
            .setScaleType(Image.ScaleType.FIT_XY));
    row.add(
        new Image.Builder(
                context, scope.id("pinned"), messagePinnedIcon, new RectF(0f, 0f, 1f, 1f))
            .setScaleType(Image.ScaleType.FIT_CENTER));
  }

  @Override
  public void onBindItem(ComponentList.Item item, MessageEntity message, int position) {
    long bindStartedNanos = SystemClock.elapsedRealtimeNanos();
    MessageRenderModel model = renderModel(message);
    boolean own = model.own;
    float rowWidth = item.getScope().width();
    float rowHeight = item.getScope().height();
    String dateLabel = position >= 0 && position < dateLabels.size() ? dateLabels.get(position) : "";
    float dateOffset = dateLabel.isEmpty()
        ? 0f
        : DateNotifierComponent.blockHeight(
            context.getResources().getDisplayMetrics().density);
    item.find("date_notifier", DateNotifierComponent.class).bind(dateLabel, rowWidth);
    item.find("selection_background", Image.class)
        .setRegion(new RectF(0f, dateOffset, rowWidth, rowHeight))
        .setVisible(selectedMessageIds.contains(messageKey(message, position)));
    float horizontalInset = dp(MESSAGE_ROW_HORIZONTAL_INSET_DP);
    MessageMetrics metrics = metrics(model, Math.max(1f, rowWidth - horizontalInset * 2f));
    float width = metrics.bubbleWidth;
    float left = own ? rowWidth - horizontalInset - width : horizontalInset;
    float right = own ? rowWidth - horizontalInset : horizontalInset + width;
    float tailWidth = dp(MESSAGE_TAIL_WIDTH_DP);
    float bodyLeft = own ? left : left + tailWidth;
    float bodyRight = own ? right - tailWidth : right;
    float bubbleTop = dateOffset + dp(MESSAGE_ROW_HALF_GAP_DP);
    float bubbleBottom = rowHeight - dp(MESSAGE_ROW_HALF_GAP_DP);
    boolean hasReply = message.repliedMessageId != null && !message.repliedMessageId.isEmpty();
    item.find("bubble", MessageBubbleComponent.class)
        .setOutgoing(own)
        .setRegion(left, bubbleTop, right, bubbleBottom);

    float headingTop = bubbleTop + MESSAGE_TOP_PADDING_PX;
    item.find("forwarded", Text.class)
        .setRegion(
            positiveRect(
                bodyLeft + MESSAGE_HORIZONTAL_PADDING_PX,
                headingTop,
                bodyRight - MESSAGE_HORIZONTAL_PADDING_PX,
                headingTop + metrics.forwardedHeight))
        .setVisible(model.forwarded);

    String replied = message.repliedMessageId == null ? "" : texts.get(message.repliedMessageId);
    item.find("reply", Text.class)
        .setText(replied == null ? "Reply" : replied)
        .setRegion(
            positiveRect(
                bodyLeft + MESSAGE_HORIZONTAL_PADDING_PX,
                headingTop + metrics.forwardedHeight,
                bodyRight - MESSAGE_HORIZONTAL_PADDING_PX,
                headingTop + metrics.forwardedHeight + metrics.replyHeight))
        .setVisible(hasReply);

    float messageTop = headingTop + metrics.forwardedHeight + metrics.replyHeight;
    float textLeft = bodyLeft + MESSAGE_HORIZONTAL_PADDING_PX;
    float fullTextRight = bodyRight - MESSAGE_HORIZONTAL_PADDING_PX;
    float textRight =
        metrics.metadataInline
            ? fullTextRight
            : Math.min(
                fullTextRight,
                textLeft + metrics.renderedTextWidth + MESSAGE_TEXT_WIDTH_SAFETY_PX);
    float textBottom = messageTop + metrics.textHeight;
    item.find("message", Text.class)
        .setRegion(positiveRect(textLeft, messageTop, textRight, textBottom))
        .setText(model.displayText);

    float contentLeft = bodyLeft + MESSAGE_HORIZONTAL_PADDING_PX;
    float metadataRight = bodyRight - MESSAGE_META_RIGHT_PX;
    float tickRight = metadataRight;
    float tickLeft = tickRight - MESSAGE_TICK_WIDTH_PX;
    float pinRight = model.showDelivery ? tickLeft - MESSAGE_PIN_GAP_PX : metadataRight;
    float pinLeft = pinRight - MESSAGE_TICK_WIDTH_PX;
    float timeRight = model.pinned
        ? pinLeft - MESSAGE_PIN_GAP_PX
        : model.showDelivery ? tickLeft - MESSAGE_TIME_TICK_GAP_PX : metadataRight;
    Text time = item.find("time", Text.class);
    time.setRegion(positiveRect(contentLeft, bubbleTop, timeRight, bubbleBottom))
        .setText(model.formattedTime);
    float timeLeft = timeRight - metrics.timeWidth;
    float timeBottom = bubbleBottom - MESSAGE_TIME_BOTTOM_PX;
    time.setRegion(
        positiveRect(
            timeLeft - MESSAGE_TIME_RECT_EXTRA_PX,
            timeBottom - metrics.timeHeight,
            timeRight,
            timeBottom));
    item.find("delivery", Image.class)
        .setBitmap(messageStatusIcon(message))
        .setRegion(
            positiveRect(
                tickLeft,
                bubbleBottom - MESSAGE_TICK_BOTTOM_PX - MESSAGE_TICK_HEIGHT_PX,
                tickRight,
                bubbleBottom - MESSAGE_TICK_BOTTOM_PX))
        .setVisible(model.showDelivery);
    item.find("pinned", Image.class)
        .setBitmap(messagePinnedIcon)
        .setRegion(
            positiveRect(
                pinLeft,
                bubbleBottom - MESSAGE_TICK_BOTTOM_PX - MESSAGE_TICK_HEIGHT_PX,
                pinRight,
                bubbleBottom - MESSAGE_TICK_BOTTOM_PX))
        .setVisible(model.pinned);
    if (profiler != null) {
      profiler.rowBound(
          SystemClock.elapsedRealtimeNanos() - bindStartedNanos,
          position,
          message.messageId);
    }
  }

  void release() {
    synchronized (renderModelCache) {
      renderModelCache.clear();
    }
    synchronized (metricsCache) {
      metricsCache.clear();
    }
    measurementTools.remove();
  }

  private MessageMetrics metrics(MessageEntity message, float availableWidth) {
    return metrics(renderModel(message), availableWidth);
  }

  private MessageMetrics metrics(MessageRenderModel model, float availableWidth) {
    boolean own = model.own;
    String value = model.displayText;
    String time = model.formattedTime;
    String replyValue = model.repliedMessageId == null
        ? "" : texts.get(model.repliedMessageId);
    if (replyValue == null) replyValue = "Reply";
    MetricKey cacheKey = new MetricKey(
        model.stableMessageId,
        model.contentVersion ^ stableId(replyValue),
        Float.floatToIntBits(availableWidth));
    MessageMetrics cached;
    synchronized (metricsCache) {
      cached = metricsCache.get(cacheKey);
    }
    if (cached != null) return cached;
    long metricStartedNanos = SystemClock.elapsedRealtimeNanos();
    float tailWidth = dp(MESSAGE_TAIL_WIDTH_DP);
    MeasurementTools tools = measurementTools.get();
    TextPaint paint = tools.messagePaint;
    Paint timePaint = tools.timePaint;
    float longestLine = Math.max(
        longestLineWidth(value, paint),
        longestLineWidth(replyValue, tools.replyPaint));
    float timeWidth = timePaint.measureText(time) + MESSAGE_TIME_RECT_EXTRA_PX;
    float metadataWidth = timeWidth
        + (model.showDelivery ? MESSAGE_TIME_TICK_GAP_PX + MESSAGE_TICK_WIDTH_PX : 0f)
        + (model.pinned ? MESSAGE_PIN_GAP_PX + MESSAGE_TICK_WIDTH_PX : 0f);
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
    float bubbleWidth =
        Math.max(
            tailWidth + minimumBodyWidth,
            Math.min(maximumBubbleWidth, tailWidth + desiredBodyWidth));
    float bodyWidth = bubbleWidth - tailWidth;
    float textWidth = Math.max(1f, bodyWidth - MESSAGE_HORIZONTAL_PADDING_PX * 2f);
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
    float inlineWidth = bodyWidth - MESSAGE_HORIZONTAL_PADDING_PX - MESSAGE_META_RIGHT_PX;
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
          Math.max(minimumBodyWidth, Math.max(stackedTextWidth, stackedMetadataWidth));
      bubbleWidth = Math.min(maximumBubbleWidth, tailWidth + bodyWidth);
    }
    Paint.FontMetrics timeFont = timePaint.getFontMetrics();
    float metadataHeight =
        Math.max(
            MESSAGE_TICK_HEIGHT_PX,
            (float) Math.ceil(timeFont.descent - timeFont.ascent));
    float replyHeight =
        model.repliedMessageId == null || model.repliedMessageId.isEmpty()
            ? 0f
            : dp(22) * MESSAGE_SCALE;
    float forwardedHeight = model.forwarded ? dp(22) * MESSAGE_SCALE : 0f;
    float messageTop = MESSAGE_TOP_PADDING_PX + forwardedHeight + replyHeight;
    float metadataTop =
        messageTop
            + (metadataInline
                ? layout.getLineTop(lastLine)
                : layout.getHeight() + MESSAGE_LINE_SPACING_PX);
    float contentBottom =
        Math.max(messageTop + layout.getHeight(), metadataTop + metadataHeight);
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
        sourceSignature,
        stableId(cacheKey),
        stableId(displayed + '\u0001' + formattedTime + '\u0001'
            + String.valueOf(message.repliedMessageId) + '\u0001'
            + String.valueOf(message.forwardedFrom) + '\u0001' + message.pinned
            + '\u0001' + deleted + '\u0001' + own));
    synchronized (renderModelCache) {
      renderModelCache.put(cacheKey, created);
    }
    return created;
  }

  private int visualAttachmentState(MessageEntity message) {
    String type = message.messageType == null ? "text" : message.messageType;
    if (!("image".equals(type) || "video".equals(type) || "file".equals(type))) return -1;
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
        + String.valueOf(message.latitude) + '\u0001'
        + String.valueOf(message.longitude) + '\u0001'
        + String.valueOf(message.forwardedFrom) + '\u0001'
        + message.pinned + '\u0001' + String.valueOf(message.deletedText) + '\u0001'
        + attachmentState;
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
      String fallback = "file".equals(type) ? "File" : capitalize(type);
      String name =
          message.attachmentName == null || message.attachmentName.isEmpty()
              ? fallback
              : message.attachmentName;
      String size =
          message.attachmentSize == null ? "" : " • " + formatSize(message.attachmentSize);
      String availability = "";
      if (!"sending".equals(message.status) && !"failed".equals(message.status)) {
        if (attachmentState == 1) availability = "\n↓ Download";
        else if (attachmentState == 2) availability = "\n◷ Downloading";
      }
      displayed =
          "["
              + type.toUpperCase(Locale.US)
              + "]\n"
              + name
              + size
              + captionSuffix(message.text)
              + availability;
    } else {
      displayed = message.text == null ? "" : message.text;
    }
    return displayed + statusSuffix(message);
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

  private String formatCoordinate(double value) {
    return String.format(Locale.US, "%.6f", value);
  }

  private String capitalize(String value) {
    return value.substring(0, 1).toUpperCase(Locale.US) + value.substring(1);
  }

  private String captionSuffix(String caption) {
    return caption == null || caption.trim().isEmpty() ? "" : " — " + caption.trim();
  }

  private static String normalize(String value) {
    if (value == null) return "";
    String normalized = value.trim();
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

  private float dp(float value) {
    return value * context.getResources().getDisplayMetrics().density;
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

  private static final class MeasurementTools {
    final TextPaint messagePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    final TextPaint replyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    MeasurementTools(Typeface typeface, float replyTextSize) {
      messagePaint.setTypeface(typeface);
      messagePaint.setTextSize(MESSAGE_TEXT_SIZE_PX);
      replyPaint.setTypeface(typeface);
      replyPaint.setTextSize(replyTextSize);
      timePaint.setTypeface(typeface);
      timePaint.setTextSize(MESSAGE_TIME_SIZE_PX);
    }
  }

  private static final class MessageRenderModel {
    final String displayText;
    final String formattedTime;
    final String repliedMessageId;
    final boolean forwarded;
    final boolean pinned;
    final boolean deleted;
    final boolean showDelivery;
    final boolean own;
    final String sourceSignature;
    final String presentationSignature;
    final long stableMessageId;
    final long contentVersion;

    MessageRenderModel(
        String displayText,
        String formattedTime,
        String repliedMessageId,
        boolean forwarded,
        boolean pinned,
        boolean deleted,
        boolean showDelivery,
        boolean own,
        String sourceSignature,
        long stableMessageId,
        long contentVersion) {
      this.displayText = displayText;
      this.formattedTime = formattedTime;
      this.repliedMessageId = repliedMessageId;
      this.forwarded = forwarded;
      this.pinned = pinned;
      this.deleted = deleted;
      this.showDelivery = showDelivery;
      this.own = own;
      this.sourceSignature = sourceSignature;
      this.presentationSignature = sourceSignature;
      this.stableMessageId = stableMessageId;
      this.contentVersion = contentVersion;
    }
  }

  private static final class MetricKey {
    final long messageId;
    final long contentVersion;
    final int widthBits;

    MetricKey(long messageId, long contentVersion, int widthBits) {
      this.messageId = messageId;
      this.contentVersion = contentVersion;
      this.widthBits = widthBits;
    }

    @Override
    public boolean equals(Object value) {
      if (this == value) return true;
      if (!(value instanceof MetricKey)) return false;
      MetricKey other = (MetricKey) value;
      return messageId == other.messageId
          && contentVersion == other.contentVersion
          && widthBits == other.widthBits;
    }

    @Override
    public int hashCode() {
      long combined = messageId ^ (messageId >>> 32)
          ^ contentVersion ^ (contentVersion >>> 32);
      return 31 * (int) combined + widthBits;
    }
  }

  private static final class MessageMetrics {
    final float bubbleWidth;
    final float bubbleHeight;
    final float renderedTextWidth;
    final float textHeight;
    final float forwardedHeight;
    final float replyHeight;
    final float timeWidth;
    final float timeHeight;
    final boolean metadataInline;

    MessageMetrics(
        float bubbleWidth,
        float bubbleHeight,
        float renderedTextWidth,
        float textHeight,
        float forwardedHeight,
        float replyHeight,
        float timeWidth,
        float timeHeight,
        boolean metadataInline) {
      this.bubbleWidth = bubbleWidth;
      this.bubbleHeight = bubbleHeight;
      this.renderedTextWidth = renderedTextWidth;
      this.textHeight = textHeight;
      this.forwardedHeight = forwardedHeight;
      this.replyHeight = replyHeight;
      this.timeWidth = timeWidth;
      this.timeHeight = timeHeight;
      this.metadataInline = metadataInline;
    }
  }
}
