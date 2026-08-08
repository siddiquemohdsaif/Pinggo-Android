package com.w3n.wavestream.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                MessageEntity.class,
                ChatEntity.class,
                PresenceEntity.class
        },
        version = 2,
        exportSchema = false
)
public abstract class WaveStreamDatabase extends RoomDatabase {
    private static volatile WaveStreamDatabase instance;

    public abstract MessageDao messageDao();

    public abstract ChatDao chatDao();

    public abstract PresenceDao presenceDao();

    public static WaveStreamDatabase getInstance(Context context) {
        if (instance != null) {
            return instance;
        }
        synchronized (WaveStreamDatabase.class) {
            if (instance == null) {
                instance = Room.databaseBuilder(
                        context.getApplicationContext(),
                        WaveStreamDatabase.class,
                        "wavestream.db"
                )
                        .fallbackToDestructiveMigration()
                        .build();
            }
        }
        return instance;
    }

    public static void clearAllLocalData(Context context) {
        WaveStreamDatabase database = getInstance(context);
        database.clearAllTables();
    }
}
