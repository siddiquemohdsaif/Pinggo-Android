package com.w3n.pinggo.activity;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.w3n.pinggo.Database.CloudFunction.AppFunction.AppFunctionManager;
import com.w3n.pinggo.Database.CloudFunction.Utils.ChatProfilePhotoStore;
import com.w3n.pinggo.Database.CloudFunction.Utils.LoginStateManager;
import com.w3n.pinggo.R;
import com.w3n.pinggo.views.ContactAvatarView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NewChatActivity extends AppCompatActivity {
    private static final int CONTACTS_PERMISSION_REQUEST = 42;
    private static final int DISCOVER_BATCH_SIZE = 50;

    private RecyclerView contactsRecyclerView;
    private TextView statusTextView;
    private ContactAdapter contactAdapter;
    private final List<JsonObject> foundContacts = new ArrayList<>();
    private final List<String> inviteContacts = new ArrayList<>();
    private final Set<String> renderedPhoneNumbers = new LinkedHashSet<>();
    private final ExecutorService discoveryExecutor = Executors.newSingleThreadExecutor();

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

        statusTextView = new TextView(this);
        statusTextView.setTextColor(getColor(R.color.secondary_text));
        statusTextView.setTextSize(16);
        statusTextView.setGravity(Gravity.CENTER);
        statusTextView.setPadding(dp(16), dp(24), dp(16), dp(16));
        root.addView(statusTextView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        contactsRecyclerView = new RecyclerView(this);
        contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        contactsRecyclerView.setPadding(dp(16), dp(8), dp(16), dp(24));
        contactsRecyclerView.setClipToPadding(false);
        contactAdapter = new ContactAdapter();
        contactsRecyclerView.setAdapter(contactAdapter);
        root.addView(contactsRecyclerView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        loadContactsWithPermission();
    }

    @Override
    protected void onDestroy() {
        discoveryExecutor.shutdownNow();
        super.onDestroy();
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
        discoveryExecutor.execute(() -> {
            List<String> contacts = readPhoneContacts();
            runOnUiThread(() -> {
                if (contacts.isEmpty()) {
                    showStatus("No contacts found.");
                    return;
                }

                foundContacts.clear();
                inviteContacts.clear();
                renderedPhoneNumbers.clear();
                contactAdapter.clear();
                showStatus("Discovering contacts...");
            });

            if (!contacts.isEmpty()) {
                discoverNextBatch(contacts, 0);
            }
        });
    }

    private void discoverNextBatch(List<String> contacts, int start) {
        if (isClosing()) {
            return;
        }
        if (start >= contacts.size()) {
            runOnUiThread(() -> {
                if (isClosing()) {
                    return;
                }
                if (foundContacts.isEmpty() && inviteContacts.isEmpty()) {
                    showStatus("No contacts found.");
                }
            });
            return;
        }

        int end = Math.min(start + DISCOVER_BATCH_SIZE, contacts.size());
        List<String> batch = new ArrayList<>(contacts.subList(start, end));
        discoverContactsBatch(batch)
                .thenAccept(response -> {
                    runOnUiThread(() -> {
                        if (!isClosing()) {
                            appendDiscoveryBatch(response);
                        }
                    });
                    if (!discoveryExecutor.isShutdown()) {
                        discoveryExecutor.execute(() -> discoverNextBatch(contacts, end));
                    }
                })
                .exceptionally(throwable -> {
                    runOnUiThread(() -> {
                        if (isClosing()) {
                            return;
                        }
                        Toast.makeText(
                                this,
                                throwable.getMessage() == null ? "Contact discovery failed." : throwable.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show();
                    });
                    if (!discoveryExecutor.isShutdown()) {
                        discoveryExecutor.execute(() -> discoverNextBatch(contacts, end));
                    }
                    return null;
                });
    }

    private CompletableFuture<JsonObject> discoverContactsBatch(List<String> contacts) {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        AppFunctionManager.getInstance().discoverContacts(
                getCurrentPhoneNumber(),
                contacts,
                new AppFunctionManager.Callback() {
                    @Override
                    public void onSuccess(Object object) {
                        if (object instanceof JsonObject) {
                            future.complete((JsonObject) object);
                        } else {
                            future.completeExceptionally(new IllegalStateException("Unable to load contacts."));
                        }
                    }

                    @Override
                    public void onError(String error) {
                        future.completeExceptionally(new IllegalStateException(error));
                    }
                }
        );
        return future;
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

    private void appendDiscoveryBatch(JsonObject object) {
        JsonArray contacts = object.getAsJsonArray("contacts");
        if (contacts == null || contacts.size() == 0) {
            return;
        }

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
            if (!renderedPhoneNumbers.add(phoneNumber)) {
                continue;
            }
            if (getBoolean(contact, "found")) {
                foundContacts.add(contact);
            } else {
                inviteContacts.add(phoneNumber);
            }
        }

        if (foundContacts.isEmpty() && inviteContacts.isEmpty()) {
            return;
        }

        renderAccumulatedDiscovery();
    }

    private void renderAccumulatedDiscovery() {
        statusTextView.setVisibility(View.GONE);
        contactsRecyclerView.setVisibility(View.VISIBLE);
        List<ContactListItem> items = new ArrayList<>();
        for (JsonObject contact : foundContacts) {
            items.add(ContactListItem.found(contact));
        }

        if (!inviteContacts.isEmpty()) {
            items.add(ContactListItem.divider("Invite"));
            for (String phoneNumber : inviteContacts) {
                items.add(ContactListItem.invite(phoneNumber));
            }
        }
        contactAdapter.setItems(items);
    }

    private TextView createDivider(String text) {
        TextView divider = new TextView(this);
        divider.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
        ));
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
        row.addView(createTextBlock(phoneNumber, "Not on PingGo"), new LinearLayout.LayoutParams(
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
        row.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
        ));
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
        intent.putExtra("sms_body", "Join me on PingGo.");
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No SMS app found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showStatus(String message) {
        statusTextView.setText(message);
        statusTextView.setVisibility(View.VISIBLE);
        contactsRecyclerView.setVisibility(View.GONE);
    }

    private String getCurrentPhoneNumber() {
        String uid = LoginStateManager.getInstance().getUID(this);
        if (uid == null) {
            return "";
        }
        if (uid.startsWith("<plus>")) {
            return uid.substring("<plus>".length());
        }
        return uid.startsWith("+") ? uid.substring(1) : uid;
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

    private boolean isClosing() {
        return isFinishing() || isDestroyed();
    }

    private static final class ContactListItem {
        private static final int TYPE_FOUND = 1;
        private static final int TYPE_DIVIDER = 2;
        private static final int TYPE_INVITE = 3;

        private final int type;
        private final JsonObject contact;
        private final String text;

        private ContactListItem(int type, JsonObject contact, String text) {
            this.type = type;
            this.contact = contact;
            this.text = text;
        }

        private static ContactListItem found(JsonObject contact) {
            return new ContactListItem(TYPE_FOUND, contact, null);
        }

        private static ContactListItem divider(String text) {
            return new ContactListItem(TYPE_DIVIDER, null, text);
        }

        private static ContactListItem invite(String phoneNumber) {
            return new ContactListItem(TYPE_INVITE, null, phoneNumber);
        }
    }

    private final class ContactAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<ContactListItem> items = new ArrayList<>();

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == ContactListItem.TYPE_DIVIDER) {
                return new DividerViewHolder(createDivider(""));
            }
            if (viewType == ContactListItem.TYPE_FOUND) {
                return new ContactViewHolder(createBaseRow());
            }
            return new ContactViewHolder(createBaseRow());
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ContactListItem item = items.get(position);
            if (holder instanceof DividerViewHolder) {
                ((DividerViewHolder) holder).bind(item.text);
                return;
            }
            ContactViewHolder contactHolder = (ContactViewHolder) holder;
            if (item.type == ContactListItem.TYPE_FOUND) {
                contactHolder.bindFound(item.contact);
            } else {
                contactHolder.bindInvite(item.text);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private void setItems(List<ContactListItem> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        private void clear() {
            items.clear();
            notifyDataSetChanged();
        }
    }

    private final class DividerViewHolder extends RecyclerView.ViewHolder {
        private final TextView dividerTextView;

        private DividerViewHolder(@NonNull TextView itemView) {
            super(itemView);
            dividerTextView = itemView;
        }

        private void bind(String text) {
            dividerTextView.setText(text);
        }
    }

    private final class ContactViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout row;

        private ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            row = (LinearLayout) itemView;
        }

        private void bindFound(JsonObject contact) {
            row.removeAllViews();
            String phoneNumber = getString(contact, "phoneNumber");
            String chatId = getString(contact, "chatId");
            String profilePhotoUrl = getString(contact, "profilePhotoUrl");
            row.setOnClickListener(v -> openChat(phoneNumber, chatId, profilePhotoUrl));
            row.addView(createAvatar(phoneNumber));
            row.addView(createTextBlock(phoneNumber, "Tap to chat"), new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1
            ));
        }

        private void bindInvite(String phoneNumber) {
            row.removeAllViews();
            row.setOnClickListener(null);
            row.addView(createAvatar(phoneNumber));
            row.addView(createTextBlock(phoneNumber, "Not on PingGo"), new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1
            ));

            TextView inviteButton = new TextView(NewChatActivity.this);
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
        }
    }
}
