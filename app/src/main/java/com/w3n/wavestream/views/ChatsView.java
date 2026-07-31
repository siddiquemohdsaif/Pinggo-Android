package com.w3n.wavestream.views;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.w3n.wavestream.R;
import com.w3n.wavestream.modals.Chat;
import com.w3n.wavestream.utils.ChatsData;

public class ChatsView extends ScrollView {
    public interface OnChatClickListener {
        void onChatClick(Chat chat);
    }

    private final OnChatClickListener onChatClickListener;

    public ChatsView(Context context, OnChatClickListener onChatClickListener) {
        super(context);
        this.onChatClickListener = onChatClickListener;
        setFillViewport(true);
        addView(createChatList(), new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
    }

    private View createChatList() {
        LinearLayout listContainer = new LinearLayout(getContext());
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(16), dp(8), dp(16), dp(96));

        for (Chat chat : ChatsData.getChats()) {
            listContainer.addView(createChatRow(chat));
        }

        return listContainer;
    }

    private View createChatRow(Chat chat) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setOnClickListener(v -> onChatClickListener.onChatClick(chat));

        ContactAvatarView profileIcon = new ContactAvatarView(getContext(), chat.getContactName());
        row.addView(profileIcon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView nameTextView = new TextView(getContext());
        nameTextView.setText(chat.getContactName());
        nameTextView.setTextColor(getContext().getColor(R.color.primary_text));
        nameTextView.setTextSize(18);
        nameTextView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nameTextView.setPadding(dp(16), 0, 0, 0);
        row.addView(nameTextView, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        return row;
    }
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
