package com.w3n.wavestream.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PresenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PresenceEntity presence);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<PresenceEntity> presence);

    @Query("SELECT * FROM presence WHERE userId = :userId LIMIT 1")
    LiveData<PresenceEntity> observePresence(String userId);
}
