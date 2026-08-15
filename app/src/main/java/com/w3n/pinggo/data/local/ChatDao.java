package com.w3n.pinggo.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ChatEntity chat);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<ChatEntity> chats);

    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    LiveData<List<ChatEntity>> observeChats();
}
