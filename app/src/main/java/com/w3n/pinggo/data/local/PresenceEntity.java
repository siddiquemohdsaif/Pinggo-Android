package com.w3n.pinggo.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "presence")
public class PresenceEntity {
    @PrimaryKey
    @NonNull
    public String userId;
    public boolean isOnline;
    public Long lastSeen;
    public long updatedAt;

    public PresenceEntity(@NonNull String userId, boolean isOnline, Long lastSeen, long updatedAt) {
        this.userId = userId;
        this.isOnline = isOnline;
        this.lastSeen = lastSeen;
        this.updatedAt = updatedAt;
    }
}
