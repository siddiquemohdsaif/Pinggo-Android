package com.w3n.pinggo.views.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.Typeface;

/** Full-width quoted-message preview displayed directly above the message composer. */
final class ComposerReplyPreviewComponent extends ReplyPreviewComponent {
  ComposerReplyPreviewComponent(
      Context context, String id, Bitmap documentIcon, Bitmap audioAvatar,
      Typeface typeface, float senderTextSize, float messageTextSize,
      ClickListener clickListener) {
    super(context, id, documentIcon, audioAvatar, typeface,
        senderTextSize, messageTextSize, clickListener);
  }

  @Override
  ComposerReplyPreviewComponent bind(
      RectF region, String sender, ReplyContent content, String repliedMessageId) {
    super.bind(region, sender, content, repliedMessageId);
    return this;
  }
}
