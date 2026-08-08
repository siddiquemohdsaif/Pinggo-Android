package com.w3n.wavestream.modals;

public class Chat {
    private final String chatId;
    private final String phoneNumber;
    private final String profilePhotoUrl;
    private final String localProfilePhotoPath;
    private boolean isOnline;
    private long lastSeen;

    public Chat(String contactName) {
        this("", contactName, null, null,false,0);
    }

    public Chat(String chatId, String phoneNumber, String profilePhotoUrl, boolean isOnline, long lastSeen) {
        this(chatId, phoneNumber, profilePhotoUrl, null,isOnline,lastSeen);
    }

    public Chat(String chatId, String phoneNumber, String profilePhotoUrl, String localProfilePhotoPath, boolean isOnline, long lastSeen) {
        this.chatId = chatId;
        this.phoneNumber = phoneNumber;
        this.profilePhotoUrl = profilePhotoUrl;
        this.localProfilePhotoPath = localProfilePhotoPath;
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

    public boolean isOnline() {
        return isOnline;
    }

    public long getLastSeen() {
        return lastSeen;
    }
}
