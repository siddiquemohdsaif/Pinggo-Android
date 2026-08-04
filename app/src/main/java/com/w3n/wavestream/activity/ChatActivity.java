package com.w3n.wavestream.activity;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.w3n.wavestream.R;
import com.w3n.wavestream.views.ContactAvatarView;
import com.w3n.wavestream.views.animator.WaveAnimatorView;
import com.w3n.wavestream.views.animator.dialog.CustomViewDialog;
import com.w3n.wavestream.views.animator.dialog.MessageBubbleDialog;
import com.w3n.wavestream.views.animator.scroll.ScrollPositionAnimator;

public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_CHAT_NAME = "com.w3n.wavestream.EXTRA_CHAT_NAME";

    private LinearLayout messagesContainer;
    private ScrollPositionAnimator scrollPositionAnimator;
    private WaveAnimatorView waveAnimatorView;
    private EditText messageEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        String chatName = getIntent().getStringExtra(EXTRA_CHAT_NAME);
        if (chatName == null || chatName.trim().isEmpty()) {
            chatName = getString(R.string.chat);
        }

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        FrameLayout profileContainer = findViewById(R.id.chatProfileContainer);
        ContactAvatarView avatarView = new ContactAvatarView(this, chatName);
        profileContainer.addView(avatarView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView chatNameTextView = findViewById(R.id.chatNameTextView);
        chatNameTextView.setText(chatName);

        messagesContainer = findViewById(R.id.messagesContainer);
        messageEditText = findViewById(R.id.messageEditText);
        ScrollView messagesScrollView = findViewById(R.id.messagesScrollView);
        scrollPositionAnimator = new ScrollPositionAnimator(messagesScrollView);
        waveAnimatorView = findViewById(R.id.waveAnimatorView);

        findViewById(R.id.sendButton).setOnClickListener(v -> sendCurrentMessage());
        findViewById(R.id.chatMoreButton).setOnClickListener(v -> showCanvasInfoDialog());

        waveAnimatorView.post(this::showTypingIndicator);
        messagesScrollView.post(() -> scrollPositionAnimator.scrollAnimateToPosition(100f));
    }

    private void sendCurrentMessage() {
        String message = messageEditText.getText().toString().trim();
        if (message.isEmpty()) {
            Toast.makeText(this, R.string.message, Toast.LENGTH_SHORT).show();
            return;
        }

        TextView messageView = new TextView(this);
        messageView.setText(message);
        messageView.setTextColor(getColor(R.color.white));
        messageView.setTextSize(16f);
        messageView.setBackgroundResource(R.drawable.bg_message_outgoing);
        messageView.setPadding(dp(14), dp(10), dp(14), dp(10));
        messageView.setMaxWidth(dp(280));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.END;
        params.topMargin = dp(10);
        messagesContainer.addView(messageView, params);
        messageEditText.setText("");
        hideTypingIndicator();

        messagesContainer.post(() -> scrollPositionAnimator.scrollAnimateToPosition(100f));
        pulseSendButton();
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
