package com.w3n.pinggo.modals;

public class Chat {
    private final String chatId;
    private final String phoneNumber;
    private final String profilePhotoUrl;
    private final String localProfilePhotoPath;
    private final String lastMessage;
    private final long lastMessageTime;
    private final boolean lastMessageOutgoing;
    private final Long lastMessageDeliveredTime;
    private final Long lastMessageReadTime;
    private final String lastMessageType;
    private final String lastMessageAttachmentName;
    private final int unreadCount;
    private final boolean pinned;
    private final long notificationMuted;
    private final boolean archived;
    private boolean isOnline;
    private long lastSeen;

    public Chat(String contactName) {
        this("", contactName, null, null, "", 0, 0, false, 0, false, false, 0);
    }

    public Chat(String chatId, String phoneNumber, String profilePhotoUrl, boolean isOnline, long lastSeen) {
        this(chatId, phoneNumber, profilePhotoUrl, null, "", 0, 0,
                false, 0, false, isOnline, lastSeen);
    }

    public Chat(String chatId, String phoneNumber, String profilePhotoUrl, String localProfilePhotoPath, boolean isOnline, long lastSeen) {
        this(chatId, phoneNumber, profilePhotoUrl, localProfilePhotoPath, "", 0, 0,
                false, 0, false, isOnline, lastSeen);
    }

    public Chat(String chatId, String phoneNumber, String profilePhotoUrl,
                String localProfilePhotoPath, String lastMessage, boolean isOnline, long lastSeen) {
        this(chatId, phoneNumber, profilePhotoUrl, localProfilePhotoPath,
                lastMessage, 0, 0, false, 0, false, isOnline, lastSeen);
    }

    public Chat(String chatId, String phoneNumber, String profilePhotoUrl,
                String localProfilePhotoPath, String lastMessage, long lastMessageTime,
                boolean isOnline, long lastSeen) {
        this(chatId, phoneNumber, profilePhotoUrl, localProfilePhotoPath,
                lastMessage, lastMessageTime, 0, false, 0, false, isOnline, lastSeen);
    }

    public Chat(String chatId, String phoneNumber, String profilePhotoUrl,
                String localProfilePhotoPath, String lastMessage, long lastMessageTime,
                int unreadCount, boolean isOnline, long lastSeen) {
        this(chatId, phoneNumber, profilePhotoUrl, localProfilePhotoPath, lastMessage,
                lastMessageTime, unreadCount, false, 0, false, isOnline, lastSeen);
    }

    public Chat(String chatId, String phoneNumber, String profilePhotoUrl,
                String localProfilePhotoPath, String lastMessage, long lastMessageTime,
                int unreadCount, boolean pinned, long notificationMuted, boolean archived,
                boolean isOnline, long lastSeen) {
        this(chatId, phoneNumber, profilePhotoUrl, localProfilePhotoPath, lastMessage,
                lastMessageTime, false, unreadCount, pinned, notificationMuted, archived,
                isOnline, lastSeen);
    }

    public Chat(String chatId, String phoneNumber, String profilePhotoUrl,
                String localProfilePhotoPath, String lastMessage, long lastMessageTime,
                boolean lastMessageOutgoing, int unreadCount, boolean pinned,
                long notificationMuted, boolean archived, boolean isOnline, long lastSeen) {
        this(chatId, phoneNumber, profilePhotoUrl, localProfilePhotoPath, lastMessage,
                lastMessageTime, lastMessageOutgoing, null, null, "text", "", unreadCount, pinned,
                notificationMuted, archived, isOnline, lastSeen);
    }

    public Chat(String chatId, String phoneNumber, String profilePhotoUrl,
                String localProfilePhotoPath, String lastMessage, long lastMessageTime,
                boolean lastMessageOutgoing, Long lastMessageDeliveredTime,
                Long lastMessageReadTime, String lastMessageType,
                String lastMessageAttachmentName, int unreadCount, boolean pinned,
                long notificationMuted, boolean archived, boolean isOnline, long lastSeen) {
        this.chatId = chatId;
        this.phoneNumber = phoneNumber;
        this.profilePhotoUrl = profilePhotoUrl;
        this.localProfilePhotoPath = localProfilePhotoPath;
        this.lastMessage = lastMessage;
        this.lastMessageTime = lastMessageTime;
        this.lastMessageOutgoing = lastMessageOutgoing;
        this.lastMessageDeliveredTime = lastMessageDeliveredTime;
        this.lastMessageReadTime = lastMessageReadTime;
        this.lastMessageType = lastMessageType == null ? "text" : lastMessageType;
        this.lastMessageAttachmentName = lastMessageAttachmentName;
        this.unreadCount = unreadCount;
        this.pinned = pinned;
        this.notificationMuted = notificationMuted;
        this.archived = archived;
        this.isOnline = isOnline;
        this.lastSeen = lastSeen;
    }

    public String getChatId() {
        return chatId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }

    public String getLocalProfilePhotoPath() {
        return localProfilePhotoPath;
    }

    public String getContactName() {
        return phoneNumber;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public long getLastMessageTime() {
        return lastMessageTime;
    }
    public boolean isLastMessageOutgoing() { return lastMessageOutgoing; }
    public Long getLastMessageDeliveredTime() { return lastMessageDeliveredTime; }
    public Long getLastMessageReadTime() { return lastMessageReadTime; }
    public String getLastMessageType() { return lastMessageType; }
    public String getLastMessageAttachmentName() { return lastMessageAttachmentName; }

    public int getUnreadCount() {
        return unreadCount;
    }

    public boolean isPinned() { return pinned; }
    public boolean isArchived() { return archived; }
    public boolean isMuted() {
        return notificationMuted == -1 || notificationMuted > System.currentTimeMillis();
    }

    public boolean isOnline() {
        return isOnline;
    }

    public long getLastSeen() {
        return lastSeen;
    }
}
