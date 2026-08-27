package com.w3n.pinggo.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "chats")
public class ChatEntity {
    @PrimaryKey
    @NonNull
    public String chatId;
    public String contactName;
    public String otherUserId;
    public String profilePhotoUrl;
    public String localProfilePhotoPath;
    public String lastMessage;
    public long lastMessageTime;
    public String lastMessageSenderId;
    public Long lastMessageDeliveredTime;
    public Long lastMessageReadTime;
    public String lastMessageType;
    public String lastMessageAttachmentName;
    public int unreadCount;
    public boolean pinned;
    public long notificationMuted;
    public boolean archived;
    public boolean isOnline;
    public long lastSeen;
    public long updatedAt;

    public ChatEntity(
            @NonNull String chatId,
            String contactName,
            String otherUserId,
            String profilePhotoUrl,
            String localProfilePhotoPath,
            String lastMessage,
            long lastMessageTime,
            String lastMessageSenderId,
            Long lastMessageDeliveredTime,
            Long lastMessageReadTime,
            String lastMessageType,
            String lastMessageAttachmentName,
            int unreadCount,
            boolean pinned,
            long notificationMuted,
            boolean archived,
            boolean isOnline,
            long lastSeen,
            long updatedAt
    ) {
        this.chatId = chatId;
        this.contactName = contactName;
        this.otherUserId = otherUserId;
        this.profilePhotoUrl = profilePhotoUrl;
        this.localProfilePhotoPath = localProfilePhotoPath;
        this.lastMessage = lastMessage;
        this.lastMessageTime = lastMessageTime;
        this.lastMessageSenderId = lastMessageSenderId;
        this.lastMessageDeliveredTime = lastMessageDeliveredTime;
        this.lastMessageReadTime = lastMessageReadTime;
        this.lastMessageType = lastMessageType;
        this.lastMessageAttachmentName = lastMessageAttachmentName;
        this.unreadCount = unreadCount;
        this.pinned = pinned;
        this.notificationMuted = notificationMuted;
        this.archived = archived;
        this.isOnline = isOnline;
        this.lastSeen = lastSeen;
        this.updatedAt = updatedAt;
    }
}
