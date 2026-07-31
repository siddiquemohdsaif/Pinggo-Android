package com.w3n.wavestream.views;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.w3n.wavestream.R;
import com.w3n.wavestream.modals.CallLog;
import com.w3n.wavestream.utils.CallLogsData;

public class CallsView extends ScrollView {
    public interface OnCallClickListener {
        void onCallClick(CallLog callLog);
    }

    private final OnCallClickListener onCallClickListener;

    public CallsView(Context context, OnCallClickListener onCallClickListener) {
        super(context);
        this.onCallClickListener = onCallClickListener;
        setFillViewport(true);
        addView(createCallsList(), new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
    }

    private View createCallsList() {
        LinearLayout listContainer = new LinearLayout(getContext());
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(16), dp(8), dp(16), dp(96));

        for (CallLog callLog : CallLogsData.getCallLogs()) {
            listContainer.addView(createCallRow(callLog));
        }

        return listContainer;
    }

    private View createCallRow(CallLog callLog) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setOnClickListener(v -> onCallClickListener.onCallClick(callLog));

        ContactAvatarView profileIcon = new ContactAvatarView(getContext(), callLog.getContactName());
        row.addView(profileIcon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout textContainer = new LinearLayout(getContext());
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setPadding(dp(16), 0, 0, 0);

        TextView nameTextView = new TextView(getContext());
        nameTextView.setText(callLog.getContactName());
        nameTextView.setTextColor(getContext().getColor(R.color.primary_text));
        nameTextView.setTextSize(18);
        nameTextView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        TextView timeTextView = new TextView(getContext());
        timeTextView.setText(callLog.getCalledTime());
        timeTextView.setTextColor(getContext().getColor(R.color.secondary_text));
        timeTextView.setTextSize(14);
        timeTextView.setPadding(0, dp(3), 0, 0);

        textContainer.addView(nameTextView);
        textContainer.addView(timeTextView);
        row.addView(textContainer, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        ImageView callTypeImageView = new ImageView(getContext());
        callTypeImageView.setImageResource(callLog.isVideoCall() ? R.drawable.ic_video_call : R.drawable.ic_call);
        callTypeImageView.setColorFilter(getContext().getColor(R.color.primary_text));
        callTypeImageView.setContentDescription(getContext().getString(
                callLog.isVideoCall() ? R.string.video_call : R.string.voice_call
        ));
        row.addView(callTypeImageView, new LinearLayout.LayoutParams(dp(44), dp(44)));

        return row;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
