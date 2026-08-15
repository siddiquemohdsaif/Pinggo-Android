package com.w3n.pinggo.activity;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.w3n.pinggo.R;
import com.w3n.pinggo.views.ContactAvatarView;

public class CallDetailActivity extends AppCompatActivity {
    public static final String EXTRA_CONTACT_NAME = "com.w3n.pinggo.EXTRA_CONTACT_NAME";
    public static final String EXTRA_CALLED_TIME = "com.w3n.pinggo.EXTRA_CALLED_TIME";
    public static final String EXTRA_FULL_CALLED_DATE_TIME = "com.w3n.pinggo.EXTRA_FULL_CALLED_DATE_TIME";
    public static final String EXTRA_DURATION = "com.w3n.pinggo.EXTRA_DURATION";
    public static final String EXTRA_IS_VIDEO_CALL = "com.w3n.pinggo.EXTRA_IS_VIDEO_CALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_call_detail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        String contactName = getIntent().getStringExtra(EXTRA_CONTACT_NAME);
        String calledTime = getIntent().getStringExtra(EXTRA_CALLED_TIME);
        String fullCalledDateTime = getIntent().getStringExtra(EXTRA_FULL_CALLED_DATE_TIME);
        String duration = getIntent().getStringExtra(EXTRA_DURATION);
        boolean isVideoCall = getIntent().getBooleanExtra(EXTRA_IS_VIDEO_CALL, false);

        if (contactName == null || contactName.trim().isEmpty()) {
            contactName = getString(R.string.call);
        }
        if (calledTime == null || calledTime.trim().isEmpty()) {
            calledTime = getString(R.string.unknown_time);
        }
        if (fullCalledDateTime == null || fullCalledDateTime.trim().isEmpty()) {
            fullCalledDateTime = calledTime;
        }
        if (duration == null || duration.trim().isEmpty()) {
            duration = getString(R.string.unknown_duration);
        }

        FrameLayout profileContainer = findViewById(R.id.profileContainer);
        ContactAvatarView avatarView = new ContactAvatarView(this, contactName);
        profileContainer.addView(avatarView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView nameTextView = findViewById(R.id.callContactNameTextView);
        TextView calledTimeTextView = findViewById(R.id.calledTimeTextView);
        TextView durationTextView = findViewById(R.id.callDurationTextView);
        ImageView callTypeImageView = findViewById(R.id.callTypeImageView);

        nameTextView.setText(contactName);
        calledTimeTextView.setText(fullCalledDateTime);
        durationTextView.setText(duration);
        callTypeImageView.setImageResource(isVideoCall ? R.drawable.ic_video_call : R.drawable.ic_call);
        callTypeImageView.setContentDescription(getString(isVideoCall ? R.string.video_call : R.string.voice_call));
    }
}
