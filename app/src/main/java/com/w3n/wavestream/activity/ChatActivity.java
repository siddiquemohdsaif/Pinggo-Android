package com.w3n.wavestream.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.wavestream.R;
import com.w3n.wavestream.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.wavestream.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.wavestream.views.ContactAvatarView;
import com.w3n.wavestream.views.animator.WaveAnimatorView;
import com.w3n.wavestream.views.animator.dialog.CustomViewDialog;
import com.w3n.wavestream.views.animator.dialog.MessageBubbleDialog;
import com.w3n.wavestream.views.animator.scroll.ScrollPositionAnimator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_CHAT_NAME = "com.w3n.wavestream.EXTRA_CHAT_NAME";
    public static final String EXTRA_CHAT_ID = "com.w3n.wavestream.EXTRA_CHAT_ID";
    public static final String EXTRA_PROFILE_PHOTO_URL = "com.w3n.wavestream.EXTRA_PROFILE_PHOTO_URL";
    public static final String EXTRA_LOCAL_PROFILE_PHOTO_PATH = "com.w3n.wavestream.EXTRA_LOCAL_PROFILE_PHOTO_PATH";
    private static final String STATUS_TAG = "status";

    private LinearLayout messagesContainer;
    private ScrollPositionAnimator scrollPositionAnimator;
    private WaveAnimatorView waveAnimatorView;
    private EditText messageEditText;
    private View replyPreviewContainer;
    private TextView replyPreviewMessageTextView;
    private String chatId;
    private String currentPhoneNumber;
    private final Map<String, String> messageTextById = new java.util.HashMap<>();
    private String editingMessageId;
    private TextView editingMessageView;
    private String replyingMessageId;
    private String replyingMessageText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        String chatName = getIntent().getStringExtra(EXTRA_CHAT_NAME);
        if (chatName == null || chatName.trim().isEmpty()) {
            chatName = getString(R.string.chat);
        }
        chatId = getIntent().getStringExtra(EXTRA_CHAT_ID);
        currentPhoneNumber = getCurrentPhoneNumber();

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        FrameLayout profileContainer = findViewById(R.id.chatProfileContainer);
        ContactAvatarView avatarView = new ContactAvatarView(this, chatName);
        profileContainer.addView(avatarView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        showLocalProfilePhoto(profileContainer, getIntent().getStringExtra(EXTRA_LOCAL_PROFILE_PHOTO_PATH));

        TextView chatNameTextView = findViewById(R.id.chatNameTextView);
        chatNameTextView.setText(chatName);

        messagesContainer = findViewById(R.id.messagesContainer);
        messageEditText = findViewById(R.id.messageEditText);
        replyPreviewContainer = findViewById(R.id.replyPreviewContainer);
        replyPreviewMessageTextView = findViewById(R.id.replyPreviewMessageTextView);
        ScrollView messagesScrollView = findViewById(R.id.messagesScrollView);
        scrollPositionAnimator = new ScrollPositionAnimator(messagesScrollView);
        waveAnimatorView = findViewById(R.id.waveAnimatorView);

        findViewById(R.id.sendButton).setOnClickListener(v -> sendCurrentMessage());
        findViewById(R.id.chatMoreButton).setOnClickListener(v -> showCanvasInfoDialog());

        showStatus("Loading messages...");
        loadChat();
    }

    private void loadChat() {
        if (chatId == null || chatId.trim().isEmpty()) {
            showStatus("Chat id missing.");
            return;
        }

        if (currentPhoneNumber == null || currentPhoneNumber.trim().isEmpty()) {
            showStatus("Login data missing.");
            return;
        }

        AppFunctionManager.getInstance().getChat(chatId, currentPhoneNumber, new AppFunctionManager.Callback() {
            @Override
            public void onSuccess(Object object) {
                runOnUiThread(() -> renderMessages(parseMessages(object)));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> showStatus(error));
            }
        });
    }

    private void renderMessages(List<JsonObject> messages) {
        messagesContainer.removeAllViews();
        messagesContainer.setTag(null);
        messageTextById.clear();
        if (messages.isEmpty()) {
            showStatus("No messages found.");
            return;
        }

        for (JsonObject message : messages) {
            messageTextById.put(getString(message, "id"), getString(message, "text"));
        }

        for (JsonObject message : messages) {
            boolean outgoing = currentPhoneNumber.equals(normalizePhoneNumber(getString(message, "senderId")));
            addMessageBubble(
                    getString(message, "id"),
                    getString(message, "text"),
                    getString(message, "repliedMessageId"),
                    outgoing,
                    outgoing
            );
        }

        messagesContainer.post(() -> scrollPositionAnimator.scrollAnimateToPosition(100f));
    }

    private List<JsonObject> parseMessages(Object object) {
        List<JsonObject> messages = new ArrayList<>();
        if (!(object instanceof JsonObject)) {
            return messages;
        }

        JsonObject response = (JsonObject) object;
        JsonElement chatElement = response.get("chat");
        if (chatElement == null || chatElement.isJsonNull() || !chatElement.isJsonObject()) {
            return messages;
        }

        JsonObject chat = chatElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : chat.entrySet()) {
            JsonElement element = entry.getValue();
            if (element != null && element.isJsonObject() && element.getAsJsonObject().has("text")) {
                JsonObject message = element.getAsJsonObject();
                if ("gone".equals(getString(message, "visible"))) {
                    continue;
                }
                if (getString(message, "id").isEmpty()) {
                    message.addProperty("id", entry.getKey());
                }
                messages.add(message);
            }
        }

        messages.sort(Comparator.comparingLong(message -> getLong(message, "sentTime")));
        return messages;
    }

    private void sendCurrentMessage() {
        String message = messageEditText.getText().toString().trim();
        if (message.isEmpty()) {
            Toast.makeText(this, R.string.message, Toast.LENGTH_SHORT).show();
            return;
        }

        if (editingMessageId != null) {
            sendEditedMessage(message);
            return;
        }

        if (chatId == null || chatId.trim().isEmpty()) {
            Toast.makeText(this, "Chat id missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        String receiverId = getReceiverId();
        if (receiverId.isEmpty()) {
            Toast.makeText(this, "Receiver missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        findViewById(R.id.sendButton).setEnabled(false);
        hideTypingIndicator();

        AppFunctionManager.Callback sendCallback = new AppFunctionManager.Callback() {
            @Override
            public void onSuccess(Object object) {
                runOnUiThread(() -> {
                    String repliedMessageId = replyingMessageId;
                    String newMessageId = getResponseMessageId(object);
                    messageTextById.put(newMessageId, message);
                    addMessageBubble(newMessageId, message, repliedMessageId, true, true);
                    messageEditText.setText("");
                    clearReplyMode();
                    hideTypingIndicator();
                    findViewById(R.id.sendButton).setEnabled(true);
                    messagesContainer.post(() -> scrollPositionAnimator.scrollAnimateToPosition(100f));
                    pulseSendButton();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    findViewById(R.id.sendButton).setEnabled(true);
                    Toast.makeText(ChatActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }
        };

        if (replyingMessageId != null) {
            AppFunctionManager.getInstance().replyMessage(
                    chatId,
                    currentPhoneNumber,
                    receiverId,
                    message,
                    replyingMessageId,
                    sendCallback
            );
            return;
        }

        AppFunctionManager.getInstance().addMessage(
                chatId,
                currentPhoneNumber,
                receiverId,
                message,
                sendCallback
        );
    }

    private void sendEditedMessage(String message) {
        if (editingMessageId == null) {
            return;
        }

        findViewById(R.id.sendButton).setEnabled(false);
        AppFunctionManager.getInstance().editMessage(
                chatId,
                editingMessageId,
                currentPhoneNumber,
                message,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        runOnUiThread(() -> {
                            if (editingMessageView != null) {
                                editingMessageView.setText(message);
                            }
                            clearEditMode();
                            findViewById(R.id.sendButton).setEnabled(true);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            findViewById(R.id.sendButton).setEnabled(true);
                            Toast.makeText(ChatActivity.this, error, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
        );
    }

    private void addMessageBubble(String messageId, String message, String repliedMessageId, boolean outgoing, boolean ownMessage) {
        if (STATUS_TAG.equals(messagesContainer.getTag())) {
            messagesContainer.removeAllViews();
            messagesContainer.setTag(null);
        }

        LinearLayout bubbleContainer = new LinearLayout(this);
        bubbleContainer.setOrientation(LinearLayout.VERTICAL);
        bubbleContainer.setGravity(outgoing ? Gravity.END : Gravity.START);

        if (repliedMessageId != null && !repliedMessageId.trim().isEmpty()) {
            TextView replyTextView = new TextView(this);
            String replyPreview = messageTextById.get(repliedMessageId);
            replyTextView.setText(replyPreview == null || replyPreview.trim().isEmpty() ? "Reply" : replyPreview);
            replyTextView.setTextColor(getColor(R.color.secondary_text));
            replyTextView.setTextSize(12f);
            replyTextView.setMaxWidth(dp(260));
            replyTextView.setSingleLine(true);
            replyTextView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            replyTextView.setPadding(dp(10), 0, dp(10), dp(4));
            bubbleContainer.addView(replyTextView);
        }

        TextView messageView = new TextView(this);
        messageView.setText(message);
        messageView.setTextColor(getColor(outgoing ? R.color.white : R.color.primary_text));
        messageView.setTextSize(16f);
        messageView.setBackgroundResource(outgoing ? R.drawable.bg_message_outgoing : R.drawable.bg_message_incoming);
        messageView.setPadding(dp(14), dp(10), dp(14), dp(10));
        messageView.setMaxWidth(dp(280));
        messageView.setTag(bubbleContainer);
        messageView.setOnLongClickListener(v -> {
            showMessageMenu(v, messageId, messageView, ownMessage);
            return true;
        });
        bubbleContainer.addView(messageView);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = outgoing ? Gravity.END : Gravity.START;
        params.topMargin = dp(10);
        messagesContainer.addView(bubbleContainer, params);
    }

    private void showMessageMenu(View anchor, String messageId, TextView messageView, boolean ownMessage) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenu().add("Reply");
        if (ownMessage) {
            popupMenu.getMenu().add("Edit");
        }
        popupMenu.getMenu().add("Delete");
        popupMenu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("Reply".equals(title)) {
                startReply(messageId, messageView.getText().toString());
                return true;
            }
            if ("Edit".equals(title)) {
                startEdit(messageId, messageView);
                return true;
            }
            if ("Delete".equals(title)) {
                deleteMessage(messageId, messageView, ownMessage);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void startReply(String messageId, String message) {
        if (messageId == null || messageId.trim().isEmpty()) {
            Toast.makeText(this, "Message id missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        replyingMessageId = messageId;
        replyingMessageText = message;
        replyPreviewMessageTextView.setText(message);
        replyPreviewContainer.setVisibility(View.VISIBLE);
        messageEditText.requestFocus();
    }

    private void startEdit(String messageId, TextView messageView) {
        if (messageId == null || messageId.trim().isEmpty()) {
            Toast.makeText(this, "Message id missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        editingMessageId = messageId;
        editingMessageView = messageView;
        messageEditText.setText(messageView.getText());
        messageEditText.setSelection(messageEditText.length());
        messageEditText.requestFocus();
    }

    private void deleteMessage(String messageId, TextView messageView, boolean ownMessage) {
        if (!ownMessage) {
            deleteOpponentMessage(messageId, messageView);
            return;
        }

        if (messageId == null || messageId.trim().isEmpty()) {
            Toast.makeText(this, "Message id missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        AppFunctionManager.getInstance().deleteMessage(
                chatId,
                messageId,
                currentPhoneNumber,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        runOnUiThread(() -> {
                            messageView.setText("This Message was deleted");
                            clearEditModeIfNeeded(messageId);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> Toast.makeText(ChatActivity.this, error, Toast.LENGTH_SHORT).show());
                    }
                }
        );
    }

    private void deleteOpponentMessage(String messageId, TextView messageView) {
        if (messageId == null || messageId.trim().isEmpty()) {
            Toast.makeText(this, "Message id missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        AppFunctionManager.getInstance().deleteOpponentMessage(
                chatId,
                messageId,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        runOnUiThread(() -> removeMessageBubble(messageView));
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> Toast.makeText(ChatActivity.this, error, Toast.LENGTH_SHORT).show());
                    }
                }
        );
    }

    private void removeMessageBubble(TextView messageView) {
        Object tag = messageView.getTag();
        if (tag instanceof View) {
            messagesContainer.removeView((View) tag);
            return;
        }
        messagesContainer.removeView(messageView);
    }

    private void clearEditModeIfNeeded(String messageId) {
        if (messageId != null && messageId.equals(editingMessageId)) {
            clearEditMode();
        }
    }

    private void clearEditMode() {
        editingMessageId = null;
        editingMessageView = null;
        messageEditText.setText("");
        if (replyingMessageId == null) {
            messageEditText.setHint(R.string.message);
        }
    }

    private void clearReplyMode() {
        replyingMessageId = null;
        replyingMessageText = null;
        replyPreviewContainer.setVisibility(View.GONE);
        replyPreviewMessageTextView.setText("");
        if (editingMessageId == null) {
            messageEditText.setHint(R.string.message);
        }
    }

    private void pulseSendButton() {
        android.view.View sendButton = findViewById(R.id.sendButton);
        if (sendButton.getWidth() == 0 || sendButton.getHeight() == 0) {
            return;
        }

        Bitmap bitmap = Bitmap.createBitmap(sendButton.getWidth(), sendButton.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        sendButton.draw(canvas);
        RectF rectF = new RectF(sendButton.getLeft(), sendButton.getTop(), sendButton.getRight(), sendButton.getBottom());
        waveAnimatorView.pulseBitmap("sendPulse", bitmap, rectF);
    }

    private void showTypingIndicator() {
        android.view.View composer = findViewById(R.id.messageComposer);
        if (composer.getTop() == 0) {
            return;
        }
        RectF dotsRect = new RectF(dp(28), composer.getTop() - dp(48), dp(112), composer.getTop() - dp(12));
        waveAnimatorView.showTypingDots(dotsRect);
    }

    private void hideTypingIndicator() {
        waveAnimatorView.hideTypingDots();
    }

    private void showStatus(String message) {
        messagesContainer.removeAllViews();
        messagesContainer.setTag(STATUS_TAG);
        TextView statusTextView = new TextView(this);
        statusTextView.setText(message);
        statusTextView.setTextColor(getColor(R.color.secondary_text));
        statusTextView.setTextSize(16);
        statusTextView.setGravity(Gravity.CENTER);
        statusTextView.setPadding(dp(16), dp(32), dp(16), dp(32));
        messagesContainer.addView(statusTextView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private String getCurrentPhoneNumber() {
        String uid = LoginStateManager.getInstance().getUID(this);
        return normalizePhoneNumber(uid);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return "";
        }
        if (phoneNumber.startsWith("<plus>")) {
            return phoneNumber.substring("<plus>".length());
        }
        if (phoneNumber.startsWith("+")) {
            return phoneNumber.substring(1);
        }
        return phoneNumber;
    }

    private String getReceiverId() {
        if (chatId == null) {
            return "";
        }

        String[] phoneNumbers = chatId.split("_");
        for (String phoneNumber : phoneNumbers) {
            String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
            if (!normalizedPhoneNumber.equals(currentPhoneNumber)) {
                return normalizedPhoneNumber;
            }
        }
        return "";
    }

    private String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.getAsString();
    }

    private long getLong(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return 0L;
        }
        try {
            return element.getAsLong();
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String getResponseMessageId(Object object) {
        if (!(object instanceof JsonObject)) {
            return "";
        }

        JsonObject response = (JsonObject) object;
        String messageId = getString(response, "messageID");
        if (!messageId.isEmpty()) {
            return messageId;
        }
        return getString(response, "messageId");
    }

    private void showCanvasInfoDialog() {
        CustomViewDialog.addDialog(
                waveAnimatorView.getDialogs(),
                new MessageBubbleDialog("Wave animator ready"),
                waveAnimatorView,
                true,
                "chat_info",
                id -> waveAnimatorView.invalidate()
        );
    }

    private void showLocalProfilePhoto(FrameLayout profileContainer, String localProfilePhotoPath) {
        if (localProfilePhotoPath == null || localProfilePhotoPath.trim().isEmpty()) {
            return;
        }

        Bitmap bitmap = BitmapFactory.decodeFile(localProfilePhotoPath);
        if (bitmap == null) {
            return;
        }

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setClipToOutline(true);
        profileContainer.removeAllViews();
        profileContainer.addView(imageView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
