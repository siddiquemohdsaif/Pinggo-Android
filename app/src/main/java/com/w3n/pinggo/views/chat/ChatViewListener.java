package com.w3n.pinggo.views.chat;

import com.w3n.pinggo.data.local.MessageEntity;
import java.util.List;

/** Activity-facing actions emitted by {@link ChatView}. */
public interface ChatViewListener {
  void onBack();
  void onVideoCall();
  void onVoiceCall();
  void onMore();
  void onSend();
  void onAudioRecordingStart();
  void onAudioRecordingSend();
  void onAudioRecordingCancel();
  void onAudioPlaybackToggle(String messageId);
  void onAttachmentSelected(String type);
  void onCameraSelected();
  void onAttachmentPreviewRemoved();
  void onTypingChanged(String value);
  void onMessageClick(MessageEntity message);
  void onReplySelected(MessageEntity message);
  void onCopySelected(List<MessageEntity> messages);
  void onForwardSelected(List<MessageEntity> messages);
  void onPinSelected(List<MessageEntity> messages);
  void onUnpinSelected(List<MessageEntity> messages);
  void onDeleteSelected(List<MessageEntity> messages);
  void onMessageSelectionChanged(boolean selected);
  void onLoadOlderMessages();
  void onReplyTargetRequested(String messageId);

  /** 0 = locally available, 1 = needs download, 2 = downloading. */
  int attachmentState(MessageEntity message);
}
