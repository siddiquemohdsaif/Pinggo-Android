package com.w3n.pinggo.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.data.local.MessageEntity;
import com.w3n.pinggo.data.local.PresenceEntity;
import com.w3n.pinggo.data.repository.ChatRepository;
import com.w3n.pinggo.views.chat.ChatView;
import com.w3n.pinggo.views.common.NativePromptDialogView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChatActivity extends AppCompatActivity implements ChatView.Listener {
  public static final String EXTRA_CHAT_NAME = "com.w3n.pinggo.EXTRA_CHAT_NAME",
      EXTRA_CHAT_ID = "com.w3n.pinggo.EXTRA_CHAT_ID",
      EXTRA_PROFILE_PHOTO_URL = "com.w3n.pinggo.EXTRA_PROFILE_PHOTO_URL",
      EXTRA_LOCAL_PROFILE_PHOTO_PATH = "com.w3n.pinggo.EXTRA_LOCAL_PROFILE_PHOTO_PATH";
  private final Handler typingHandler = new Handler(Looper.getMainLooper());
  private final Set<String> pendingSeen = new HashSet<>();
  private ChatView chatView;
  private ChatRepository repository;
  private NativePromptDialogView promptDialog;
  private String chatId, currentUser, receiverId, replyingId, editingId;
  private boolean typingStarted, peerTyping;
  private PresenceEntity latestPresence;
  private final Runnable stopTyping =
      () -> {
        if (typingStarted && repository != null && !receiverId.isEmpty()) {
          repository.sendTyping(chatId, receiverId, false);
          typingStarted = false;
        }
      };

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    EdgeToEdge.enable(this);
    String name = getIntent().getStringExtra(EXTRA_CHAT_NAME);
    if (name == null || name.trim().isEmpty()) name = "Chat";
    chatId = getIntent().getStringExtra(EXTRA_CHAT_ID);
    currentUser = normalize(LoginStateManager.getInstance().getUID(this));
    receiverId = receiver();
    chatView =
        new ChatView(
            this,
            name,
            currentUser,
            getIntent().getStringExtra(EXTRA_LOCAL_PROFILE_PHOTO_PATH),
            this);
    setContentView(chatView);
    ViewCompat.setOnApplyWindowInsetsListener(
        chatView,
        (v, i) -> {
          Insets bars = i.getInsets(WindowInsetsCompat.Type.systemBars()),
              ime = i.getInsets(WindowInsetsCompat.Type.ime());
          chatView.setInsets(
              bars.top, bars.bottom, ime.bottom, i.isVisible(WindowInsetsCompat.Type.ime()));
          return i;
        });
    ViewCompat.requestApplyInsets(chatView);
    repository = ChatRepository.getInstance(this);
    repository.setEventListener(
        new ChatRepository.EventListener() {
          @Override
          public void onTyping(String eventChat, String user, boolean typing) {
            if (chatId != null && chatId.equals(eventChat)) {
              peerTyping = typing;
              if (typing) chatView.setPresence("typing...");
              else renderPresence(latestPresence);
            }
          }

          @Override
          public void onSocketError(String error) {
            Toast.makeText(ChatActivity.this, error, Toast.LENGTH_SHORT).show();
          }
        });
    repository.connect();
    observe();
  }

  private void observe() {
    if (chatId == null || chatId.trim().isEmpty()) {
      chatView.showStatus("Chat id missing.");
      return;
    }
    if (currentUser.isEmpty()) {
      chatView.showStatus("Login data missing.");
      return;
    }
    repository.observeMessages(chatId).observe(this, this::messages);
    if (!receiverId.isEmpty())
      repository.observePresence(receiverId).observe(this, this::renderPresence);
    repository.hydrateChat(chatId, LoginStateManager.getInstance().getUID(this));
    repository.syncAfterReconnect(LoginStateManager.getInstance().getUID(this));
    if (!receiverId.isEmpty()) repository.syncPresence(Collections.singletonList(receiverId));
  }

  private void messages(List<MessageEntity> values) {
    chatView.submitMessages(values);
    if (values == null) return;
    List<String> unseen = new ArrayList<>();
    for (MessageEntity m : values) {
      boolean incoming = !currentUser.equals(normalize(m.senderId));
      if (incoming && m.readTime == null && !pendingSeen.contains(m.messageId))
        unseen.add(m.messageId);
      else if (m.readTime != null) pendingSeen.remove(m.messageId);
    }
    if (!unseen.isEmpty()) {
      pendingSeen.addAll(unseen);
      repository.markSeen(chatId, unseen);
    }
  }

  private void renderPresence(PresenceEntity p) {
    latestPresence = p;
    if (peerTyping) return;
    if (p == null) {
      chatView.setPresence("");
      return;
    }
    if (p.isOnline) {
      chatView.setPresence("online");
      return;
    }
    chatView.setPresence(
        p.lastSeen != null && p.lastSeen > 0
            ? "last seen "
                + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(p.lastSeen))
            : "");
  }

  @Override
  public void onSend() {
    String text = chatView.getDraft();
    if (text.isEmpty()) {
      Toast.makeText(this, "Message required.", Toast.LENGTH_SHORT).show();
      return;
    }
    if (editingId != null) {
      repository.editMessage(chatId, editingId, text);
      editingId = null;
    } else {
      if (chatId == null || chatId.isEmpty() || receiverId.isEmpty()) {
        Toast.makeText(this, "Chat information missing.", Toast.LENGTH_SHORT).show();
        return;
      }
      repository.sendMessage(chatId, receiverId, text, replyingId);
    }
    stopTyping.run();
    replyingId = null;
    chatView.clearReply();
    chatView.clearDraft();
  }

  @Override
  public void onTypingChanged(String text) {
    if (repository == null || chatId == null || receiverId.isEmpty()) return;
    typingHandler.removeCallbacks(stopTyping);
    if (text != null && !text.isEmpty()) {
      if (!typingStarted) {
        repository.sendTyping(chatId, receiverId, true);
        typingStarted = true;
      }
      typingHandler.postDelayed(stopTyping, 2000);
    } else stopTyping.run();
  }

  @Override
  public void onMessageLongPress(MessageEntity message) {
    boolean own = currentUser.equals(normalize(message.senderId));
    List<String> actions = new ArrayList<>();
    actions.add("Reply");
    if (own) actions.add("Edit");
    actions.add("Delete");
    showPrompt(NativePromptDialogView.actions(this, actions,
        which -> handleAction(actions.get(which), message, own), this::removePrompt));
  }

  private void handleAction(String action, MessageEntity message, boolean own) {
    if (message.messageId == null || message.messageId.trim().isEmpty()) {
      Toast.makeText(this, "Message id missing.", Toast.LENGTH_SHORT).show();
      return;
    }
    if ("Reply".equals(action)) {
      replyingId = message.messageId;
      editingId = null;
      chatView.showReply(message.text);
      return;
    }
    if ("Edit".equals(action)) {
      editingId = message.messageId;
      replyingId = null;
      chatView.clearReply();
      chatView.setDraft(message.text);
      return;
    }
    if (own) repository.deleteOwnMessage(chatId, message.messageId);
    else repository.deleteOpponentMessage(chatId, message.messageId);
    if (message.messageId.equals(editingId)) {
      editingId = null;
      chatView.clearDraft();
    }
    if (message.messageId.equals(replyingId)) {
      replyingId = null;
      chatView.clearReply();
    }
  }

  @Override
  public void onBack() {
    finish();
  }

  @Override
  public void onMore() {
    showPrompt(NativePromptDialogView.message(this, "Chat",
        "Messages, presence, reply, edit and delete are connected.", this::removePrompt));
  }

  private void showPrompt(NativePromptDialogView prompt) {
    removePrompt();
    promptDialog = prompt;
    ((ViewGroup) findViewById(android.R.id.content)).addView(prompt,
        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
  }

  private void removePrompt() {
    NativePromptDialogView current = promptDialog;
    promptDialog = null;
    if (current == null) return;
    if (current.getParent() instanceof ViewGroup) {
      ((ViewGroup) current.getParent()).removeView(current);
    }
    current.release();
  }

  private String receiver() {
    if (chatId == null) return "";
    for (String value : chatId.split("_")) {
      String n = normalize(value);
      if (!n.equals(currentUser)) return n;
    }
    return "";
  }

  private static String normalize(String v) {
    if (v == null) return "";
    String n = v.trim();
    if (n.startsWith("<plus>")) n = n.substring(6);
    return n.startsWith("+") ? n.substring(1) : n;
  }

  @Override
  protected void onDestroy() {
    removePrompt();
    stopTyping.run();
    typingHandler.removeCallbacksAndMessages(null);
    if (repository != null) repository.setEventListener(null);
    if (chatView != null) chatView.release();
    chatView = null;
    super.onDestroy();
  }
}
