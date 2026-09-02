package com.w3n.pinggo.views.chat;

/** Internal value objects shared by chat-message measurement, caching, and binding. */
final class ChatMessageAdapterModels {
  private ChatMessageAdapterModels() {}

  static final class LocationRenderTiming {
    final String traceId;
    final long pressedElapsedMs;
    final long pressedWallMs;

    LocationRenderTiming(String traceId, long pressedElapsedMs, long pressedWallMs) {
      this.traceId = traceId == null ? "" : traceId;
      this.pressedElapsedMs = pressedElapsedMs;
      this.pressedWallMs = pressedWallMs;
    }
  }

  static final class MessageRenderModel {
    final String displayText;
    final String formattedTime;
    final String repliedMessageId;
    final boolean forwarded;
    final boolean pinned;
    final boolean deleted;
    final boolean showDelivery;
    final boolean own;
    final String mediaType;
    final String attachmentName;
    final String attachmentSource;
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
        String mediaType,
        String attachmentName,
        String attachmentSource,
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
      this.mediaType = mediaType;
      this.attachmentName = attachmentName;
      this.attachmentSource = attachmentSource;
      this.sourceSignature = sourceSignature;
      this.presentationSignature = sourceSignature;
      this.stableMessageId = stableMessageId;
      this.contentVersion = contentVersion;
    }
  }

  static final class MetricKey {
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

  static final class MessageMetrics {
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
