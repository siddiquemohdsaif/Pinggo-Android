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

    @Query("SELECT * FROM calls WHERE ownerId = :ownerId "
            + "ORDER BY endedAt DESC, callId DESC")
    LiveData<List<CallEntity>> observeCalls(String ownerId);

    @Query("SELECT * FROM calls WHERE ownerId = :ownerId AND chatId = :chatId "
            + "ORDER BY endedAt DESC, callId DESC LIMIT :limit")
    LiveData<List<CallEntity>> observeCallHistory(String ownerId, String chatId, int limit);
}
