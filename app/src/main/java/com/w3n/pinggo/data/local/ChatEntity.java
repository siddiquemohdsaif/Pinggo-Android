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
        this.isOnline = isOnline;
        this.lastSeen = lastSeen;
        this.updatedAt = updatedAt;
    }
}
