package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Owns chat-message presentation, measurement, row binding, and bubble bitmap caching. */
final class ChatMessageAdapter extends ComponentList.Adapter<MessageEntity> {
  private static final int ACCENT = 0xFF019CC4;
  private static final int SECONDARY = 0xFF687382;
  private static final int INCOMING_BUBBLE = 0xFFFEFEFE;
  private static final int INCOMING_BUBBLE_BORDER = 0xFFE4EBF3;
  private static final int OUTGOING_BUBBLE = 0xFFE2F6FE;
  private static final int OUTGOING_BUBBLE_BORDER = 0xFFD6EBF4;
  private static final int MESSAGE_TEXT_COLOR = 0xFF131D2F;
  private static final int MESSAGE_TIME_COLOR = 0xFF5C6B85;
  private static final float MESSAGE_TEXT_SIZE_PX = 34f;
  private static final float MESSAGE_TOP_PADDING_PX = 20f;
  private static final float MESSAGE_BOTTOM_PADDING_PX = 20f;
  private static final float MESSAGE_HORIZONTAL_PADDING_PX = 25f;
  private static final float MESSAGE_LINE_SPACING_PX = 10f;
  private static final float MESSAGE_TIME_SIZE_PX = 24f;
  private static final float MESSAGE_TEXT_META_GAP_PX = 20f;
  private static final float MESSAGE_TEXT_WIDTH_SAFETY_PX = 2f;
  private static final float MESSAGE_TIME_TICK_GAP_PX = 10f;
  private static final float MESSAGE_META_RIGHT_PX = 20f;
  private static final float MESSAGE_TIME_BOTTOM_PX = 15f;
  private static final float MESSAGE_TIME_RECT_EXTRA_PX = 2f;
  private static final float MESSAGE_TICK_WIDTH_PX = 40f;
  private static final float MESSAGE_TICK_HEIGHT_PX = 26f;
  private static final float MESSAGE_TICK_BOTTOM_PX = 15f;
  private static final float MESSAGE_ROW_GAP_DP = 7f;
  private static final float MESSAGE_ROW_HALF_GAP_DP = MESSAGE_ROW_GAP_DP / 2f;
  private static final float MESSAGE_TAIL_WIDTH_DP = 4f;
  private static final float MESSAGE_TAIL_HEIGHT_DP = 7f;

  interface AttachmentStateProvider {
    int attachmentState(MessageEntity message);
  }

  private final Context context;
  private final String currentUser;
  private final Bitmap transparent;
  private final Bitmap messageSentIcon;
  private final Bitmap messageDeliveredIcon;
  private final Bitmap messageReadIcon;
  private final AttachmentStateProvider attachmentStateProvider;
  private final Typeface messageTypeface;
  private final SimpleDateFormat messageTimeFormatter;
  private final List<MessageEntity> messages = new ArrayList<>();
  private final Map<String, String> texts = new HashMap<>();
  private final Map<String, Bitmap> bubbleShapes = new HashMap<>();

  ChatMessageAdapter(
      Context context,
      String currentUser,
      Bitmap transparent,
      Bitmap messageSentIcon,
      Bitmap messageDeliveredIcon,
      Bitmap messageReadIcon,
      AttachmentStateProvider attachmentStateProvider) {
    this.context = context;
    this.currentUser = normalize(currentUser);
    this.transparent = transparent;
    this.messageSentIcon = messageSentIcon;
    this.messageDeliveredIcon = messageDeliveredIcon;
    this.messageReadIcon = messageReadIcon;
    this.attachmentStateProvider = attachmentStateProvider;
    messageTypeface = NativeFonts.load(context, NativeFonts.INTER);
    messageTimeFormatter = new SimpleDateFormat("h:mm a", Locale.getDefault());
  }

  void submit(List<MessageEntity> values) {
    messages.clear();
    texts.clear();
    if (values != null) {
      messages.addAll(values);
      for (MessageEntity message : values) {
        texts.put(message.messageId, displayMessage(message));
      }
    }
    notifyDataSetChanged();
  }

  String messageIdAt(int position) {
    if (position < 0 || position >= messages.size()) return null;
    return messages.get(position).messageId;
  }

  int indexOfMessage(String messageId) {
    if (messageId == null) return -1;
    for (int index = 0; index < messages.size(); index++) {
      if (messageId.equals(messages.get(index).messageId)) return index;
    }
    return -1;
  }

  float rowHeight(MessageEntity message, float availableWidth) {
    return metrics(message, availableWidth).bubbleHeight + dp(MESSAGE_ROW_GAP_DP);
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
    return isOwn(messages.get(position)) ? 1 : 0;
  }

  @Override
  public long getItemId(int position) {
    String id = messages.get(position).messageId;
    return id == null ? position : id.hashCode();
  }

  @Override
  public void onCreateItem(ComponentList.Item item, int type) {
    ComponentList.ItemScope scope = item.getScope();
    float width = scope.width();
    float height = scope.height();
    float bubbleWidth = width * .66f;
    float left = type == 1 ? width - bubbleWidth : 0f;
    float right = type == 1 ? width : bubbleWidth;
    float tailWidth = dp(MESSAGE_TAIL_WIDTH_DP);
    float bodyLeft = type == 1 ? left : left + tailWidth;
    float bodyRight = type == 1 ? right - tailWidth : right;
    ZLayer row = item.addLayer("row");
    row.add(
        new Image.Builder(
                context,
                scope.id("bubble"),
                transparent,
                new RectF(
                    left,
                    dp(MESSAGE_ROW_HALF_GAP_DP),
                    right,
                    height - dp(MESSAGE_ROW_HALF_GAP_DP)))
            .setScaleType(Image.ScaleType.FIT_XY));
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
  }

  @Override
  public void onBindItem(ComponentList.Item item, MessageEntity message, int position) {
    boolean own = isOwn(message);
    float rowWidth = item.getScope().width();
    float rowHeight = item.getScope().height();
    MessageMetrics metrics = metrics(message, rowWidth);
    float width = metrics.bubbleWidth;
    float left = own ? rowWidth - width : 0f;
    float right = own ? rowWidth : width;
    float tailWidth = dp(MESSAGE_TAIL_WIDTH_DP);
    float bodyLeft = own ? left : left + tailWidth;
    float bodyRight = own ? right - tailWidth : right;
    float bubbleTop = dp(MESSAGE_ROW_HALF_GAP_DP);
    float bubbleBottom = rowHeight - dp(MESSAGE_ROW_HALF_GAP_DP);
    boolean hasReply = message.repliedMessageId != null && !message.repliedMessageId.isEmpty();
    int bubblePixelWidth = Math.max(1, Math.round(right - left));
    int bubblePixelHeight = Math.max(1, Math.round(bubbleBottom - bubbleTop));
    item.find("bubble", Image.class)
        .setBitmap(bubbleShape(own, bubblePixelWidth, bubblePixelHeight))
        .setRegion(new RectF(left, bubbleTop, right, bubbleBottom));

    String replied = message.repliedMessageId == null ? "" : texts.get(message.repliedMessageId);
    item.find("reply", Text.class)
        .setText(replied == null ? "Reply" : replied)
        .setRegion(
            positiveRect(
                bodyLeft + MESSAGE_HORIZONTAL_PADDING_PX,
                bubbleTop + MESSAGE_TOP_PADDING_PX,
                bodyRight - MESSAGE_HORIZONTAL_PADDING_PX,
                bubbleTop + MESSAGE_TOP_PADDING_PX + metrics.replyHeight))
        .setVisible(hasReply);

    float messageTop = bubbleTop + MESSAGE_TOP_PADDING_PX + metrics.replyHeight;
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
        .setText(displayMessage(message));

    float contentLeft = bodyLeft + MESSAGE_HORIZONTAL_PADDING_PX;
    float metadataRight = bodyRight - MESSAGE_META_RIGHT_PX;
    float tickRight = metadataRight;
    float tickLeft = tickRight - MESSAGE_TICK_WIDTH_PX;
    float timeRight = own ? tickLeft - MESSAGE_TIME_TICK_GAP_PX : metadataRight;
    Text time = item.find("time", Text.class);
    time.setRegion(positiveRect(contentLeft, bubbleTop, timeRight, bubbleBottom))
        .setText(formatMessageTime(message.sentTime));
    float measuredTimeWidth = Math.max(1f, time.getMeasuredTextWidth());
    float measuredTimeHeight = Math.max(1f, time.getMeasuredTextHeight());
    float timeLeft = timeRight - measuredTimeWidth;
    float timeBottom = bubbleBottom - MESSAGE_TIME_BOTTOM_PX;
    time.setRegion(
        positiveRect(
            timeLeft - MESSAGE_TIME_RECT_EXTRA_PX,
            timeBottom - measuredTimeHeight,
            timeLeft + measuredTimeWidth,
            timeBottom));
    item.find("delivery", Image.class)
        .setBitmap(messageStatusIcon(message))
        .setRegion(
            positiveRect(
                tickLeft,
                bubbleBottom - MESSAGE_TICK_BOTTOM_PX - MESSAGE_TICK_HEIGHT_PX,
                tickRight,
                bubbleBottom - MESSAGE_TICK_BOTTOM_PX))
        .setVisible(own);
  }

  void release() {
    for (Bitmap bitmap : bubbleShapes.values()) {
      if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }
    bubbleShapes.clear();
  }

  private MessageMetrics metrics(MessageEntity message, float availableWidth) {
    boolean own = isOwn(message);
    String value = displayMessage(message);
    String time = formatMessageTime(message.sentTime);
    float tailWidth = dp(MESSAGE_TAIL_WIDTH_DP);
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    paint.setTypeface(messageTypeface);
    paint.setTextSize(MESSAGE_TEXT_SIZE_PX);
    float longestLine = 0f;
    for (String line : value.split("\\n", -1)) {
      longestLine = Math.max(longestLine, paint.measureText(line));
    }
    Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    timePaint.setTypeface(messageTypeface);
    timePaint.setTextSize(MESSAGE_TIME_SIZE_PX);
    float timeWidth = timePaint.measureText(time) + MESSAGE_TIME_RECT_EXTRA_PX;
    float metadataWidth =
        timeWidth + (own ? MESSAGE_TIME_TICK_GAP_PX + MESSAGE_TICK_WIDTH_PX : 0f);
    float minimumBodyWidth = 170f;
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
        message.repliedMessageId == null || message.repliedMessageId.isEmpty() ? 0f : dp(22);
    float messageTop = MESSAGE_TOP_PADDING_PX + replyHeight;
    float metadataTop =
        messageTop
            + (metadataInline
                ? layout.getLineTop(lastLine)
                : layout.getHeight() + MESSAGE_LINE_SPACING_PX);
    float contentBottom =
        Math.max(messageTop + layout.getHeight(), metadataTop + metadataHeight);
    float bubbleHeight = contentBottom + MESSAGE_BOTTOM_PADDING_PX;
    return new MessageMetrics(
        bubbleWidth,
        bubbleHeight,
        renderedTextWidth,
        layout.getHeight(),
        replyHeight,
        metadataInline);
  }

  private String displayMessage(MessageEntity message) {
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
        int state = attachmentStateProvider.attachmentState(message);
        if (state == 1) availability = "\n↓ Download";
        else if (state == 2) availability = "\n◷ Downloading";
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
    long milliseconds = sentTime < 100_000_000_000L ? sentTime * 1000L : sentTime;
    return messageTimeFormatter.format(new Date(milliseconds));
  }

  private Bitmap messageStatusIcon(MessageEntity message) {
    if (message.readTime != null || "seen".equals(message.status)) return messageReadIcon;
    if (message.deliveredTime != null || "delivered".equals(message.status)) {
      return messageDeliveredIcon;
    }
    return messageSentIcon;
  }

  private String statusSuffix(MessageEntity message) {
    if (!isOwn(message)) return "";
    if ("sending".equals(message.status)) return "\n◷ Sending";
    if ("failed".equals(message.status)) return "\nFailed • Tap to resend";
    return "";
  }

  private Bitmap bubbleShape(boolean own, int width, int height) {
    String key = (own ? "own:" : "opposite:") + width + "x" + height;
    Bitmap cached = bubbleShapes.get(key);
    if (cached != null && !cached.isRecycled()) return cached;
    Bitmap created = createBubbleShape(width, height, own);
    bubbleShapes.put(key, created);
    return created;
  }

  private Bitmap createBubbleShape(int width, int height, boolean own) {
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);
    float strokeHalf = 1f;
    float tailWidth = dp(MESSAGE_TAIL_WIDTH_DP);
    float radius = Math.min(dp(16), Math.max(1f, (height - 2f) / 2f));
    float top = strokeHalf;
    float bottom = height - strokeHalf;
    float left = own ? strokeHalf : tailWidth + strokeHalf;
    float right = own ? width - tailWidth - strokeHalf : width - strokeHalf;
    float tailHeight = dp(MESSAGE_TAIL_HEIGHT_DP);
    float tailBottom = top + tailHeight;
    Path path = new Path();
    if (own) {
      path.moveTo(left + radius, top);
      path.lineTo(right, top);
      path.cubicTo(
          right + tailWidth * .30f,
          top + tailHeight * .06f,
          width - strokeHalf,
          top + tailHeight * .12f,
          width - strokeHalf,
          top + tailHeight * .28f);
      path.cubicTo(
          width - strokeHalf,
          top + tailHeight * .48f,
          right + tailWidth * .22f,
          top + tailHeight * .78f,
          right,
          tailBottom);
      path.lineTo(right, bottom - radius);
      path.quadTo(right, bottom, right - radius, bottom);
      path.lineTo(left + radius, bottom);
      path.quadTo(left, bottom, left, bottom - radius);
      path.lineTo(left, top + radius);
      path.quadTo(left, top, left + radius, top);
    } else {
      path.moveTo(left, top);
      path.cubicTo(
          left - tailWidth * .30f,
          top + tailHeight * .06f,
          strokeHalf,
          top + tailHeight * .12f,
          strokeHalf,
          top + tailHeight * .28f);
      path.cubicTo(
          strokeHalf,
          top + tailHeight * .48f,
          left - tailWidth * .22f,
          top + tailHeight * .78f,
          left,
          tailBottom);
      path.lineTo(left, bottom - radius);
      path.quadTo(left, bottom, left + radius, bottom);
      path.lineTo(right - radius, bottom);
      path.quadTo(right, bottom, right, bottom - radius);
      path.lineTo(right, top + radius);
      path.quadTo(right, top, right - radius, top);
      path.lineTo(left, top);
    }
    path.close();
    Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    fill.setStyle(Paint.Style.FILL);
    fill.setColor(own ? OUTGOING_BUBBLE : INCOMING_BUBBLE);
    canvas.drawPath(path, fill);
    Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    border.setStyle(Paint.Style.STROKE);
    border.setStrokeWidth(3f);
    border.setStrokeJoin(Paint.Join.ROUND);
    border.setStrokeCap(Paint.Cap.ROUND);
    border.setColor(own ? OUTGOING_BUBBLE_BORDER : INCOMING_BUBBLE_BORDER);
    canvas.drawPath(path, border);
    return bitmap;
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

  private static final class MessageMetrics {
    final float bubbleWidth;
    final float bubbleHeight;
    final float renderedTextWidth;
    final float textHeight;
    final float replyHeight;
    final boolean metadataInline;

    MessageMetrics(
        float bubbleWidth,
        float bubbleHeight,
        float renderedTextWidth,
        float textHeight,
        float replyHeight,
        boolean metadataInline) {
      this.bubbleWidth = bubbleWidth;
      this.bubbleHeight = bubbleHeight;
      this.renderedTextWidth = renderedTextWidth;
      this.textHeight = textHeight;
      this.replyHeight = replyHeight;
      this.metadataInline = metadataInline;
    }
  }
}
