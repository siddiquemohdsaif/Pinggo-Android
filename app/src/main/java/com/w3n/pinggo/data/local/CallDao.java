package com.w3n.pinggo.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface CallDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<CallEntity> calls);

    @Query("SELECT c.* FROM calls c WHERE c.ownerId = :ownerId AND NOT EXISTS ("
            + "SELECT 1 FROM calls newer WHERE newer.ownerId = c.ownerId "
            + "AND newer.chatId = c.chatId AND (newer.endedAt > c.endedAt "
            + "OR (newer.endedAt = c.endedAt AND newer.callId > c.callId))) "
            + "ORDER BY c.endedAt DESC, c.callId DESC")
    LiveData<List<CallEntity>> observeLatestCalls(String ownerId);

    @Query("SELECT * FROM calls WHERE ownerId = :ownerId AND chatId = :chatId "
            + "ORDER BY endedAt DESC, callId DESC LIMIT :limit")
    LiveData<List<CallEntity>> observeCallHistory(String ownerId, String chatId, int limit);
}
