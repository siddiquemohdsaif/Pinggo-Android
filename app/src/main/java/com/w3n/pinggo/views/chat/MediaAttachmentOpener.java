package com.w3n.pinggo.views.chat;

import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.w3n.pinggo.data.local.MessageEntity;
import com.w3n.pinggo.data.local.TransferEntity;
import com.w3n.pinggo.data.repository.ChatRepository;
import java.io.File;

/** Open downloaded attachments using a read-only content URI and their MIME type. */
public final class MediaAttachmentOpener {
  private final AppCompatActivity activity;
  private final ChatRepository repository;
  private MessageEntity pending;

  public MediaAttachmentOpener(AppCompatActivity activity, ChatRepository repository, String chatId) {
    this.activity = activity;
    this.repository = repository;
    repository.observeTransfers(chatId).observe(activity, transfers -> {
      if (pending == null || transfers == null) return;
      for (TransferEntity transfer : transfers) {
        if (transfer == null || !java.util.Objects.equals(pending.attachmentId, transfer.attachmentId)) continue;
        if ("completed".equalsIgnoreCase(transfer.status) && transfer.localUri != null) {
          MessageEntity message = pending;
          pending = null;
          show(message, Uri.parse(transfer.localUri));
          break;
        }
        if ("failed".equalsIgnoreCase(transfer.status)) {
          pending = null;
          Toast.makeText(activity, "Unable to download attachment. Tap to retry.", Toast.LENGTH_SHORT).show();
          break;
        }
      }
    });
  }

  public void open(MessageEntity message) {
    pending = message;
    repository.downloadAttachment(message, new ChatRepository.DownloadCallback() {
      @Override public void onAvailable(Uri uri) {
        if (pending != message) return;
        pending = null;
        show(message, uri);
      }
      @Override public void onQueued() {
        if (pending == message && alive())
          Toast.makeText(activity, "Downloading attachment…", Toast.LENGTH_SHORT).show();
      }
      @Override public void onError(String error) {
        if (pending != message) return;
        pending = null;
        if (alive()) Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
      }
    });
  }

  private boolean alive() { return !activity.isFinishing() && !activity.isDestroyed(); }
  private void show(MessageEntity message, Uri uri) {
    if (!alive()) return;
    try {
      if ("file".equalsIgnoreCase(uri.getScheme()))
        uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", new File(uri.getPath()));
      String mime = message.attachmentMimeType;
      if (mime == null || mime.trim().isEmpty())
        mime = "image".equals(message.messageType) ? "image/*"
            : "video".equals(message.messageType) ? "video/*" : "application/octet-stream";
      activity.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
    } catch (RuntimeException unavailable) {
      Toast.makeText(activity, "No app available to open this attachment.", Toast.LENGTH_SHORT).show();
    }
  }
}
