package com.w3n.pinggo.views.chat;

import android.graphics.Bitmap;
import com.w3n.pinggo.data.local.MessageEntity;

/** Immutable dependencies used by {@link ChatMessageAdapter} while rendering message rows. */
final class ChatMessageAdapterConfig {
  interface AttachmentStateProvider {
    int attachmentState(MessageEntity message);
    long downloadedBytes(MessageEntity message);
    long totalBytes(MessageEntity message);
  }

  interface MediaMetricsListener {
    void onMediaMetricsChanged();
  }

  interface AudioPlaybackListener {
    void onAudioPlaybackToggle(String messageId);
  }

  interface ReplyNavigationListener {
    void onReplyTargetSelected(String messageId);
  }

  interface MessageClickListener {
    void onMessageClick(MessageEntity message);
  }

  interface MessageLongClickListener {
    void onMessageLongClick(MessageEntity message);
  }

  final Bitmap transparent;
  final Bitmap selectionBackground;
  final Bitmap messageSendingIcon;
  final Bitmap messageSentIcon;
  final Bitmap messageDeliveredIcon;
  final Bitmap messageReadIcon;
  final Bitmap messagePinnedIcon;
  final Bitmap documentIcon;
  final Bitmap deletedMessageIcon;
  final Bitmap forwardedMessageIcon;
  final Bitmap callPhoneIncomingIcon;
  final Bitmap callPhoneOutgoingIcon;
  final Bitmap callPhoneMissedIcon;
  final Bitmap callVideoIncomingIcon;
  final Bitmap callVideoOutgoingIcon;
  final Bitmap callVideoMissedIcon;
  final AttachmentStateProvider attachmentStateProvider;
  final MediaMetricsListener mediaMetricsListener;
  final AudioPlaybackListener audioPlaybackListener;
  final ReplyNavigationListener replyNavigationListener;
  final MessageClickListener messageClickListener;
  final MessageLongClickListener messageLongClickListener;
  final ChatPerformanceProfiler profiler;
  final String opponentName;

  ChatMessageAdapterConfig(
      Bitmap transparent,
      Bitmap selectionBackground,
      Bitmap messageSendingIcon,
      Bitmap messageSentIcon,
      Bitmap messageDeliveredIcon,
      Bitmap messageReadIcon,
      Bitmap messagePinnedIcon,
      Bitmap documentIcon,
      Bitmap deletedMessageIcon,
      Bitmap forwardedMessageIcon,
      Bitmap callPhoneIncomingIcon,
      Bitmap callPhoneOutgoingIcon,
      Bitmap callPhoneMissedIcon,
      Bitmap callVideoIncomingIcon,
      Bitmap callVideoOutgoingIcon,
      Bitmap callVideoMissedIcon,
      AttachmentStateProvider attachmentStateProvider,
      MediaMetricsListener mediaMetricsListener,
      AudioPlaybackListener audioPlaybackListener,
      ReplyNavigationListener replyNavigationListener,
      MessageClickListener messageClickListener,
      MessageLongClickListener messageLongClickListener,
      ChatPerformanceProfiler profiler,
      String opponentName) {
    this.transparent = transparent;
    this.selectionBackground = selectionBackground;
    this.messageSendingIcon = messageSendingIcon;
    this.messageSentIcon = messageSentIcon;
    this.messageDeliveredIcon = messageDeliveredIcon;
    this.messageReadIcon = messageReadIcon;
    this.messagePinnedIcon = messagePinnedIcon;
    this.documentIcon = documentIcon;
    this.deletedMessageIcon = deletedMessageIcon;
    this.forwardedMessageIcon = forwardedMessageIcon;
    this.callPhoneIncomingIcon = callPhoneIncomingIcon;
    this.callPhoneOutgoingIcon = callPhoneOutgoingIcon;
    this.callPhoneMissedIcon = callPhoneMissedIcon;
    this.callVideoIncomingIcon = callVideoIncomingIcon;
    this.callVideoOutgoingIcon = callVideoOutgoingIcon;
    this.callVideoMissedIcon = callVideoMissedIcon;
    this.attachmentStateProvider = attachmentStateProvider;
    this.mediaMetricsListener = mediaMetricsListener;
    this.audioPlaybackListener = audioPlaybackListener;
    this.replyNavigationListener = replyNavigationListener;
    this.messageClickListener = messageClickListener;
    this.messageLongClickListener = messageLongClickListener;
    this.profiler = profiler;
    this.opponentName = opponentName == null ? "" : opponentName;
  }
}
