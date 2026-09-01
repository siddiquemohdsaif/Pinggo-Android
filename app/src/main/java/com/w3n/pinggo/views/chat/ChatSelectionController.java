package com.w3n.pinggo.views.chat;

import com.w3n.pinggo.data.local.MessageEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Owns selected-message identity and resolves selected entities in display order. */
final class ChatSelectionController {
  private final Set<String> ids = new LinkedHashSet<>();

  Set<String> ids() { return ids; }
  boolean isSelecting() { return !ids.isEmpty(); }
  boolean contains(MessageEntity message) { return ids.contains(idOf(message)); }

  boolean toggle(MessageEntity message) {
    String id = idOf(message);
    if (id.isEmpty()) return false;
    if (!ids.add(id)) ids.remove(id);
    return true;
  }

  List<String> clear() {
    List<String> previous = new ArrayList<>(ids);
    ids.clear();
    return previous;
  }

  boolean retainLoaded(ChatMessageAdapter adapter) {
    return ids.removeIf(messageId -> adapter.indexOfMessage(messageId) < 0);
  }

  List<MessageEntity> selectedMessages(ChatMessageAdapter adapter) {
    List<MessageEntity> selected = new ArrayList<>();
    for (int index = 0; index < adapter.getItemCount(); index++) {
      MessageEntity message = adapter.getItem(index);
      if (contains(message)) selected.add(message);
    }
    return selected;
  }

  static String idOf(MessageEntity message) {
    if (message == null) return "";
    if (message.messageId != null && !message.messageId.isEmpty()) return message.messageId;
    return message.clientMessageId == null ? "" : message.clientMessageId;
  }
}
