package com.w3n.wavestream.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.w3n.wavestream.R;
import com.w3n.wavestream.modals.CallLog;
import com.w3n.wavestream.modals.Chat;
import com.w3n.wavestream.views.CallsView;
import com.w3n.wavestream.views.ChatsView;

public class HomeActivity extends AppCompatActivity {
    private TextView topTitleTextView;
    private View searchEditText;
    private FrameLayout contentFrameLayout;
    private ExtendedFloatingActionButton newChatFab;
    private ExtendedFloatingActionButton makeCallFab;
    private ChatsView chatsView;
    private CallsView callsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        topTitleTextView = findViewById(R.id.topTitleTextView);
        searchEditText = findViewById(R.id.searchEditText);
        contentFrameLayout = findViewById(R.id.contentFrameLayout);
        newChatFab = findViewById(R.id.newChatFab);
        makeCallFab = findViewById(R.id.makeCallFab);
        chatsView = new ChatsView(this, this::openChat);
        callsView = new CallsView(this, this::openCallLog);

        findViewById(R.id.overflowButton).setOnClickListener(this::showOverflowMenu);
        findViewById(R.id.bottomNavigationView).setOnApplyWindowInsetsListener(null);
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.navigation_chats) {
                showChats();
                return true;
            } else if (item.getItemId() == R.id.navigation_calls) {
                showCalls();
                return true;
            }
            return false;
        });

        newChatFab.setOnClickListener(v -> Toast.makeText(this, R.string.new_chat, Toast.LENGTH_SHORT).show());
        makeCallFab.setOnClickListener(v -> Toast.makeText(this, R.string.make_call, Toast.LENGTH_SHORT).show());
        showChats();
    }

    private void showChats() {
        topTitleTextView.setText(R.string.app_name);
        searchEditText.setVisibility(View.VISIBLE);
        findViewById(R.id.overflowButton).setVisibility(View.VISIBLE);
        showContent(chatsView);
        newChatFab.setVisibility(View.VISIBLE);
        makeCallFab.setVisibility(View.GONE);
    }

    private void showCalls() {
        topTitleTextView.setText(R.string.call);
        searchEditText.setVisibility(View.GONE);
        findViewById(R.id.overflowButton).setVisibility(View.GONE);
        showContent(callsView);
        newChatFab.setVisibility(View.GONE);
        makeCallFab.setVisibility(View.VISIBLE);
    }

    private void showOverflowMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenu().add(R.string.settings);
        popupMenu.setOnMenuItemClickListener(item -> {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        });
        popupMenu.show();
    }

    private void showContent(View contentView) {
        contentFrameLayout.removeAllViews();
        contentFrameLayout.addView(contentView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
    }

    private void openChat(Chat chat) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, chat.getContactName());
        startActivity(intent);
    }

    private void openCallLog(CallLog callLog) {
        Intent intent = new Intent(this, CallDetailActivity.class);
        intent.putExtra(CallDetailActivity.EXTRA_CONTACT_NAME, callLog.getContactName());
        intent.putExtra(CallDetailActivity.EXTRA_CALLED_TIME, callLog.getCalledTime());
        intent.putExtra(CallDetailActivity.EXTRA_FULL_CALLED_DATE_TIME, callLog.getFullCalledDateTime());
        intent.putExtra(CallDetailActivity.EXTRA_DURATION, callLog.getDuration());
        intent.putExtra(CallDetailActivity.EXTRA_IS_VIDEO_CALL, callLog.isVideoCall());
        startActivity(intent);
    }
}
