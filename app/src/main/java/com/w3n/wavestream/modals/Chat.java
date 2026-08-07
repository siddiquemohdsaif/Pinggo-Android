package com.w3n.wavestream.modals;

public class Chat {
    private final String chatId;
    private final String phoneNumber;
    private final String profilePhotoUrl;
    private final String localProfilePhotoPath;

    public Chat(String contactName) {
        this("", contactName, null, null);
    }

    public Chat(String chatId, String phoneNumber, String profilePhotoUrl) {
        this(chatId, phoneNumber, profilePhotoUrl, null);
    }

    public Chat(String chatId, String phoneNumber, String profilePhotoUrl, String localProfilePhotoPath) {
        this.chatId = chatId;
        this.phoneNumber = phoneNumber;
        this.profilePhotoUrl = profilePhotoUrl;
        this.localProfilePhotoPath = localProfilePhotoPath;
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
}
