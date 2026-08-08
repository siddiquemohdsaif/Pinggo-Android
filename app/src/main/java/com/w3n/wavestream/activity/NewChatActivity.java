package com.w3n.wavestream.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.wavestream.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.wavestream.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.w3n.wavestream.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.wavestream.R;
import com.w3n.wavestream.views.ContactAvatarView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class NewChatActivity extends AppCompatActivity {
    private static final int CONTACTS_PERMISSION_REQUEST = 42;

    private LinearLayout listContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        LinearLayout root = new LinearLayout(this);
        root.setId(View.generateViewId());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.screen_background));
        setContentView(root);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        root.addView(createToolbar());
        ScrollView scrollView = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(16), dp(8), dp(16), dp(24));
        scrollView.addView(listContainer, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        loadContactsWithPermission();
    }

    private View createToolbar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setPadding(dp(8), dp(10), dp(16), dp(10));

        ImageButton backButton = new ImageButton(this);
        backButton.setImageResource(android.R.drawable.ic_menu_revert);
        backButton.setBackgroundResource(android.R.drawable.list_selector_background);
        backButton.setContentDescription("Back");
        backButton.setOnClickListener(v -> finish());
        toolbar.addView(backButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = new TextView(this);
        title.setText("New Chat");
        title.setTextColor(getColor(R.color.primary_text));
        title.setTextSize(24);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));
        return toolbar;
    }

    private void loadContactsWithPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            discoverContacts();
            return;
        }
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.READ_CONTACTS},
                CONTACTS_PERMISSION_REQUEST
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CONTACTS_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            discoverContacts();
            return;
        }
        showStatus("Contacts permission is required to discover chats.");
    }

    private void discoverContacts() {
        showStatus("Loading contacts...");
        List<String> contacts = readPhoneContacts();
        if (contacts.isEmpty()) {
            showStatus("No contacts found.");
            return;
        }

        AppFunctionManager.getInstance().discoverContacts(
                getCurrentPhoneNumber(),
                contacts,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        runOnUiThread(() -> renderDiscovery(object));
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> showStatus(error));
                    }
                }
        );
    }

    private List<String> readPhoneContacts() {
        Set<String> contacts = new LinkedHashSet<>();
        String ownPhoneNumber = normalizePhoneNumber(getCurrentPhoneNumber());
        String[] projection = new String[]{
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " ASC"
        )) {
            if (cursor == null) {
                return new ArrayList<>();
            }
            int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            while (cursor.moveToNext()) {
                String number = normalizePhoneNumber(cursor.getString(numberIndex));
                if (!number.isEmpty() && !number.equals(ownPhoneNumber)) {
                    contacts.add(number);
                }
            }
        }
        return new ArrayList<>(contacts);
    }

    private void renderDiscovery(Object object) {
        listContainer.removeAllViews();
        if (!(object instanceof JsonObject)) {
            showStatus("Unable to load contacts.");
            return;
        }

        JsonArray contacts = ((JsonObject) object).getAsJsonArray("contacts");
        if (contacts == null || contacts.size() == 0) {
            showStatus("No contacts found.");
            return;
        }

        List<JsonObject> foundContacts = new ArrayList<>();
        List<String> inviteContacts = new ArrayList<>();

        String ownPhoneNumber = normalizePhoneNumber(getCurrentPhoneNumber());
        for (JsonElement element : contacts) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject contact = element.getAsJsonObject();
            String phoneNumber = normalizePhoneNumber(getString(contact, "phoneNumber"));
            if (phoneNumber.isEmpty() || phoneNumber.equals(ownPhoneNumber)) {
                continue;
            }
            if (getBoolean(contact, "found")) {
                foundContacts.add(contact);
            } else {
                inviteContacts.add(phoneNumber);
            }
        }

        if (foundContacts.isEmpty() && inviteContacts.isEmpty()) {
            showStatus("No contacts found.");
            return;
        }

        for (JsonObject contact : foundContacts) {
            listContainer.addView(createFoundRow(contact));
        }

        if (!inviteContacts.isEmpty()) {
            listContainer.addView(createDivider("Invite"));
            for (String phoneNumber : inviteContacts) {
                listContainer.addView(createInviteRow(phoneNumber));
            }
        }
    }

    private View createDivider(String text) {
        TextView divider = new TextView(this);
        divider.setText(text);
        divider.setTextColor(getColor(R.color.secondary_text));
        divider.setTextSize(14);
        divider.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        divider.setGravity(Gravity.CENTER_VERTICAL);
        divider.setPadding(dp(4), dp(20), dp(4), dp(8));
        return divider;
    }

    private View createFoundRow(JsonObject contact) {
        String phoneNumber = getString(contact, "phoneNumber");
        String chatId = getString(contact, "chatId");
        String profilePhotoUrl = getString(contact, "profilePhotoUrl");

        LinearLayout row = createBaseRow();
        row.setOnClickListener(v -> openChat(phoneNumber, chatId, profilePhotoUrl));

        row.addView(createAvatar(phoneNumber));
        row.addView(createTextBlock(phoneNumber, "Tap to chat"), new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));
        return row;
    }

    private View createInviteRow(String phoneNumber) {
        LinearLayout row = createBaseRow();
        row.addView(createAvatar(phoneNumber));
        row.addView(createTextBlock(phoneNumber, "Not on WaveStream"), new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        TextView inviteButton = new TextView(this);
        inviteButton.setText("Invite");
        inviteButton.setTextColor(getColor(R.color.send_button));
        inviteButton.setTextSize(15);
        inviteButton.setGravity(Gravity.CENTER);
        inviteButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        inviteButton.setPadding(dp(12), dp(8), dp(12), dp(8));
        inviteButton.setOnClickListener(v -> inviteContact(phoneNumber));
        row.addView(inviteButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return row;
    }

    private LinearLayout createBaseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), dp(10), dp(4), dp(10));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        return row;
    }

    private View createAvatar(String phoneNumber) {
        FrameLayout avatarContainer = new FrameLayout(this);
        ContactAvatarView avatarView = new ContactAvatarView(this, phoneNumber);
        avatarContainer.addView(avatarView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        avatarContainer.setLayoutParams(new LinearLayout.LayoutParams(dp(52), dp(52)));
        return avatarContainer;
    }

    private LinearLayout createTextBlock(String title, String subtitle) {
        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setGravity(Gravity.CENTER_VERTICAL);
        textBlock.setPadding(dp(16), 0, dp(8), 0);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(getColor(R.color.primary_text));
        titleView.setTextSize(18);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textBlock.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(getColor(R.color.secondary_text));
        subtitleView.setTextSize(14);
        textBlock.addView(subtitleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return textBlock;
    }

    private void openChat(String phoneNumber, String chatId, String profilePhotoUrl) {
        if (chatId == null || chatId.trim().isEmpty()) {
            chatId = buildChatId(getCurrentPhoneNumber(), phoneNumber);
        }
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, phoneNumber);
        intent.putExtra(ChatActivity.EXTRA_CHAT_ID, chatId);
        intent.putExtra(ChatActivity.EXTRA_PROFILE_PHOTO_URL, profilePhotoUrl);
        intent.putExtra(
                ChatActivity.EXTRA_LOCAL_PROFILE_PHOTO_PATH,
                ChatProfilePhotoStore.getLocalPath(this, phoneNumber)
        );
        startActivity(intent);
    }

    private void inviteContact(String phoneNumber) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + Uri.encode(phoneNumber)));
        intent.putExtra("sms_body", "Join me on WaveStream.");
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No SMS app found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showStatus(String message) {
        listContainer.removeAllViews();
        TextView statusTextView = new TextView(this);
        statusTextView.setText(message);
        statusTextView.setTextColor(getColor(R.color.secondary_text));
        statusTextView.setTextSize(16);
        statusTextView.setGravity(Gravity.CENTER);
        statusTextView.setPadding(dp(16), dp(32), dp(16), dp(32));
        listContainer.addView(statusTextView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private String getCurrentPhoneNumber() {
        String uid = LoginStateManager.getInstance().getUID(this);
        if (uid == null) {
            return "";
        }
        if (uid.startsWith("<plus>")) {
            return "+" + uid.substring("<plus>".length());
        }
        return uid.startsWith("+") ? uid : "+" + uid;
    }

    private String buildChatId(String currentPhoneNumber, String otherPhoneNumber) {
        String current = normalizePhoneNumber(currentPhoneNumber);
        String other = normalizePhoneNumber(otherPhoneNumber);
        return current + "_" + other;
    }

    private String normalizePhoneNumber(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^0-9]", "");
    }

    private String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.getAsString();
    }

    private boolean getBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
