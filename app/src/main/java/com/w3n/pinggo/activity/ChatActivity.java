package com.w3n.pinggo.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
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
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.io.FileNotFoundException;

public class ChatActivity extends AppCompatActivity implements ChatView.Listener {
  private MessageEntity pendingDownloadMessage;
  public static final String EXTRA_CHAT_NAME = "com.w3n.pinggo.EXTRA_CHAT_NAME",
      EXTRA_CHAT_ID = "com.w3n.pinggo.EXTRA_CHAT_ID",
      EXTRA_PROFILE_PHOTO_URL = "com.w3n.pinggo.EXTRA_PROFILE_PHOTO_URL",
      EXTRA_LOCAL_PROFILE_PHOTO_PATH = "com.w3n.pinggo.EXTRA_LOCAL_PROFILE_PHOTO_PATH";
  private final Handler typingHandler = new Handler(Looper.getMainLooper());
  private final ActivityResultLauncher<String[]> attachmentPicker =
      registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onAttachmentPicked);
  private final ActivityResultLauncher<String[]> locationPermission =
      registerForActivityResult(
          new ActivityResultContracts.RequestMultiplePermissions(), this::onLocationPermission);
  private final ActivityResultLauncher<String> storagePermission =
      registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        if (granted && pendingDownloadMessage != null) startAttachmentDownload(pendingDownloadMessage);
        else if (!granted) Toast.makeText(this, "Storage permission is required.", Toast.LENGTH_SHORT).show();
        pendingDownloadMessage = null;
      });
  private final Set<String> pendingSeen = new HashSet<>();
  private final Map<String, String> attachmentDownloads = new HashMap<>();
  private ChatView chatView;
  private ChatRepository repository;
  private NativePromptDialogView promptDialog;
  private String chatId, currentUser, receiverId, replyingId, editingId;
  private String selectedAttachmentType;
  private Uri selectedAttachmentUri;
  private boolean typingStarted, peerTyping, locationPending, attachmentSending;
  private LocationManager locationManager;
  private LocationListener locationListener;
  private PresenceEntity latestPresence;
  private List<MessageEntity> latestMessages = Collections.emptyList();
  private final Runnable stopTyping =
      () -> {
        if (typingStarted && repository != null && !receiverId.isEmpty()) {
          repository.sendTyping(chatId, receiverId, false);
          typingStarted = false;
        }
      };
  private final Runnable locationTimeout =
      () -> {
        if (!locationPending) return;
        locationPending = false;
        removeLocationUpdates();
        Toast.makeText(this, "Unable to get current location.", Toast.LENGTH_SHORT).show();
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
    repository.observeTransfers(chatId).observe(this, values -> {
      attachmentDownloads.clear();
      if (values != null) for (com.w3n.pinggo.data.local.TransferEntity transfer : values) {
        if ("download".equals(transfer.direction) && transfer.attachmentId != null
            && ("queued".equals(transfer.status) || "downloading".equals(transfer.status)
                || "retrying".equals(transfer.status))) {
          attachmentDownloads.put(transfer.attachmentId, transfer.status);
        }
      }
      refreshAttachmentRows();
    });
    if (!receiverId.isEmpty())
      repository.observePresence(receiverId).observe(this, this::renderPresence);
    repository.hydrateChat(chatId, LoginStateManager.getInstance().getUID(this));
    repository.syncAfterReconnect(LoginStateManager.getInstance().getUID(this));
    if (!receiverId.isEmpty()) repository.syncPresence(Collections.singletonList(receiverId));
  }

  private void messages(List<MessageEntity> values) {
    latestMessages = values == null ? Collections.emptyList() : values;
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
    if (chatId == null || chatId.isEmpty() || receiverId.isEmpty()) {
      Toast.makeText(this, "Chat information missing.", Toast.LENGTH_SHORT).show();
      return;
    }
    if (editingId != null) {
      if (text.isEmpty()) {
        Toast.makeText(this, "Message required.", Toast.LENGTH_SHORT).show();
        return;
      }
      repository.editMessage(chatId, editingId, text);
      editingId = null;
    } else if (selectedAttachmentUri != null) {
      if (attachmentSending) return;
      attachmentSending = true;
      chatView.clearAttachmentPreview();
      repository.uploadAndSendAttachment(
          chatId,
          receiverId,
          text,
          replyingId,
          selectedAttachmentUri,
          selectedAttachmentType.toLowerCase(),
          new ChatRepository.AttachmentCallback() {
            @Override
            public void onSent() {
              attachmentSending = false;
              selectedAttachmentUri = null;
              selectedAttachmentType = null;
              finishComposeAction();
            }

            @Override
            public void onError(String message) {
              attachmentSending = false;
              selectedAttachmentUri = null;
              selectedAttachmentType = null;
              finishComposeAction();
              Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
            }
          });
      return;
    } else {
      if (text.isEmpty()) {
        Toast.makeText(this, "Message required.", Toast.LENGTH_SHORT).show();
        return;
      }
      repository.sendMessage(chatId, receiverId, text, replyingId);
    }
    finishComposeAction();
  }

  private void finishComposeAction() {
    stopTyping.run();
    replyingId = null;
    chatView.clearReply();
    chatView.clearDraft();
  }

  @Override
  public void onAttachmentSelected(String type) {
    if ("Image".equals(type)) {
      selectedAttachmentType = type;
      attachmentPicker.launch(new String[] {"image/*"});
    } else if ("Video".equals(type)) {
      selectedAttachmentType = type;
      attachmentPicker.launch(new String[] {"video/*"});
    } else if ("File".equals(type)) {
      selectedAttachmentType = type;
      attachmentPicker.launch(new String[] {"*/*"});
    } else if ("Location".equals(type)) {
      requestLocationPermission();
    }
  }

  private void onAttachmentPicked(Uri uri) {
    if (uri == null) {
      return;
    }
    selectedAttachmentUri = uri;
    try {
      getContentResolver().takePersistableUriPermission(
          uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
    } catch (SecurityException error) {
    }
    chatView.showAttachmentPreview(selectedAttachmentType, selectedFileName(uri));
    Toast.makeText(this, selectedAttachmentType + " selected.", Toast.LENGTH_SHORT).show();
  }

  @Override
  public void onAttachmentPreviewRemoved() {
    selectedAttachmentUri = null;
    selectedAttachmentType = null;
  }

  private String selectedFileName(Uri uri) {
    try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (column >= 0) return cursor.getString(column);
      }
    } catch (RuntimeException error) {
    }
    return "Attachment";
  }

  private void requestLocationPermission() {
    boolean fine =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
    boolean coarse =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
    if (fine || coarse) {
      requestCurrentLocation();
      return;
    }
    locationPermission.launch(
        new String[] {
          Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        });
  }

  private void onLocationPermission(Map<String, Boolean> result) {
    boolean granted =
        Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
            || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
    if (granted) requestCurrentLocation();
    else Toast.makeText(this, "Location permission is required.", Toast.LENGTH_SHORT).show();
  }

  private void requestCurrentLocation() {
    locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
    String provider = chooseLocationProvider();
    if (provider == null) {
      Toast.makeText(this, "Turn on location services and try again.", Toast.LENGTH_SHORT).show();
      return;
    }
    locationPending = true;
    typingHandler.removeCallbacks(locationTimeout);
    typingHandler.postDelayed(locationTimeout, 15_000);
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        locationManager.getCurrentLocation(provider, null, getMainExecutor(), this::onLocationReady);
      } else {
        locationListener =
            new LocationListener() {
              @Override
              public void onLocationChanged(Location location) {
                onLocationReady(location);
              }

              @Override
              public void onStatusChanged(String provider, int status, Bundle extras) {}

              @Override
              public void onProviderEnabled(String provider) {}

              @Override
              public void onProviderDisabled(String provider) {}
            };
        locationManager.requestSingleUpdate(provider, locationListener, Looper.getMainLooper());
      }
    } catch (SecurityException error) {
      locationPending = false;
      typingHandler.removeCallbacks(locationTimeout);
      Toast.makeText(this, "Location permission is required.", Toast.LENGTH_SHORT).show();
    }
  }

  private String chooseLocationProvider() {
    if (locationManager == null) return null;
    boolean fine =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
    if (fine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
      return LocationManager.GPS_PROVIDER;
    }
    if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
      return LocationManager.NETWORK_PROVIDER;
    }
    return null;
  }

  private void onLocationReady(Location location) {
    if (!locationPending) return;
    locationPending = false;
    typingHandler.removeCallbacks(locationTimeout);
    removeLocationUpdates();
    if (location == null) {
      Toast.makeText(this, "Unable to get current location.", Toast.LENGTH_SHORT).show();
      return;
    }
    if (chatId == null || chatId.isEmpty() || receiverId.isEmpty()) {
      Toast.makeText(this, "Chat information missing.", Toast.LENGTH_SHORT).show();
      return;
    }
    repository.sendLocation(
        chatId,
        receiverId,
        location.getLatitude(),
        location.getLongitude(),
        location.getAccuracy(),
        replyingId);
    finishComposeAction();
    Toast.makeText(this, "Current location sent.", Toast.LENGTH_SHORT).show();
  }

  private void removeLocationUpdates() {
    if (locationManager == null || locationListener == null) return;
    try {
      locationManager.removeUpdates(locationListener);
    } catch (SecurityException ignored) {
      // Permission may have been revoked while the request was active.
    }
    locationListener = null;
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

  @Override
  public void onMessageClick(MessageEntity message) {
    if ("failed".equals(message.status)) {
      repository.resendMessage(message);
      return;
    }
    String type = message.messageType == null ? "text" : message.messageType;
    if ("location".equals(type) && message.latitude != null && message.longitude != null) {
      String coordinates = message.latitude + "," + message.longitude;
      openUri(Uri.parse("geo:" + coordinates + "?q=" + Uri.encode(coordinates)), null);
      return;
    }
    if (!("image".equals(type) || "video".equals(type) || "file".equals(type))) return;
    boolean own = currentUser.equals(normalize(message.senderId));
    if (own) {
      Uri local = message.attachmentLocalUri == null ? null : Uri.parse(message.attachmentLocalUri);
      if (local == null || !canRead(local)) {
        Toast.makeText(this, "File no longer available.", Toast.LENGTH_SHORT).show();
        return;
      }
      openUri(local, message.attachmentMimeType);
      return;
    }
    if (message.attachmentLocalUri != null && canRead(Uri.parse(message.attachmentLocalUri))) {
      openLocalAttachment(Uri.parse(message.attachmentLocalUri), message.attachmentMimeType);
      return;
    }
    if (attachmentDownloads.containsKey(attachmentKey(message))) return;
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
        && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
      pendingDownloadMessage = message;
      storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
      return;
    }
    startAttachmentDownload(message);
  }

  private void openUri(Uri target, String mimeType) {
    try {
      Intent intent = new Intent(Intent.ACTION_VIEW, target);
      if (mimeType != null && !mimeType.isEmpty()) intent.setDataAndType(target, mimeType);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(intent);
    } catch (RuntimeException error) {
      Toast.makeText(this, "No app can open this message.", Toast.LENGTH_SHORT).show();
    }
  }

  private void startAttachmentDownload(MessageEntity message) {
    repository.downloadAttachment(message, new ChatRepository.DownloadCallback() {
      @Override public void onAvailable(Uri uri) {
        openLocalAttachment(uri, message.attachmentMimeType);
      }
      @Override public void onQueued() {
        attachmentDownloads.put(attachmentKey(message), "queued");
        refreshAttachmentRows();
      }
      @Override public void onError(String error) {
        Toast.makeText(ChatActivity.this, error, Toast.LENGTH_SHORT).show();
      }
    });
  }

  private void refreshAttachmentRows() {
    if (chatView != null) chatView.submitMessages(latestMessages);
  }

  private String attachmentKey(MessageEntity message) {
    return message.attachmentId != null ? message.attachmentId
        : message.messageId != null ? message.messageId : String.valueOf(message.hashCode());
  }

  private boolean canRead(Uri uri) {
    try (android.content.res.AssetFileDescriptor ignored =
             getContentResolver().openAssetFileDescriptor(uri, "r")) {
      return ignored != null;
    } catch (FileNotFoundException | SecurityException error) {
      return false;
    } catch (java.io.IOException error) {
      return false;
    }
  }

  private void openLocalAttachment(Uri uri, String mimeType) {
    if ("file".equals(uri.getScheme())) {
      uri = FileProvider.getUriForFile(this, getPackageName() + ".files", new File(uri.getPath()));
    }
    openUri(uri, mimeType == null || mimeType.isEmpty() ? "*/*" : mimeType);
  }

  @Override
  public int attachmentState(MessageEntity message) {
    if (currentUser.equals(normalize(message.senderId))) return 0;
    if (attachmentDownloads.containsKey(attachmentKey(message))) return 2;
    return message.attachmentLocalUri != null && canRead(Uri.parse(message.attachmentLocalUri)) ? 0 : 1;
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
    locationPending = false;
    removeLocationUpdates();
    if (repository != null) repository.setEventListener(null);
    if (chatView != null) chatView.release();
    chatView = null;
    super.onDestroy();
  }
}
