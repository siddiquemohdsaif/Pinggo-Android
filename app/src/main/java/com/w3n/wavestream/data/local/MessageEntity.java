package com.w3n.wavestream.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "messages",
        indices = {
                @Index("chatId"),
                @Index("clientMessageId"),
                @Index("sentTime")
        }
)
public class MessageEntity {
    @PrimaryKey
    @NonNull
    public String messageId;
    public String clientMessageId;
    public String chatId;
    public String senderId;
    public String receiverId;
    public String text;
    public String repliedMessageId;
    public long sentTime;
    public Long deliveredTime;
    public Long readTime;
    public String status;

    public MessageEntity(
            @NonNull String messageId,
            String clientMessageId,
            String chatId,
            String senderId,
            String receiverId,
            String text,
            String repliedMessageId,
            long sentTime,
            Long deliveredTime,
            Long readTime,
            String status
    ) {
        this.messageId = messageId;
        this.clientMessageId = clientMessageId;
        this.chatId = chatId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.text = text;
        this.repliedMessageId = repliedMessageId;
        this.sentTime = sentTime;
        this.deliveredTime = deliveredTime;
        this.readTime = readTime;
        this.status = status;
    }
}
