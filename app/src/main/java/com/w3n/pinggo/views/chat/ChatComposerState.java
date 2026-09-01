package com.w3n.pinggo.views.chat;

/** Mutable UI state for the composer, reply, attachment panel, and recorder chrome. */
final class ChatComposerState {
  String draft = "";
  String replyPreview = "";
  String replySender = "";
  String replyTargetId = "";
  ReplyContent replyContent;
  String attachmentType = "";
  String attachmentName = "";
  boolean attachmentPanelVisible;
  boolean recording;
  long recordingElapsedMs;

  boolean hasReply() { return !replyPreview.isEmpty(); }
  boolean hasAttachment() { return !attachmentType.isEmpty(); }

  void clearReply() {
    replyPreview = "";
    replySender = "";
    replyTargetId = "";
    replyContent = null;
  }

  void clearAttachment() {
    attachmentType = "";
    attachmentName = "";
  }
}
