package com.w3n.wavestream.activity;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.w3n.wavestream.R;
import com.w3n.wavestream.views.ContactAvatarView;

public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_CHAT_NAME = "com.w3n.wavestream.EXTRA_CHAT_NAME";

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
    }
}
