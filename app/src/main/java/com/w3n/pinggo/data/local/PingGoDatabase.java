package com.w3n.pinggo.data.local;

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
public abstract class PingGoDatabase extends RoomDatabase {
    private static volatile PingGoDatabase instance;

    public abstract MessageDao messageDao();

    public abstract ChatDao chatDao();

    public abstract PresenceDao presenceDao();

    public static PingGoDatabase getInstance(Context context) {
        if (instance != null) {
            return instance;
        }
        synchronized (PingGoDatabase.class) {
            if (instance == null) {
                instance = Room.databaseBuilder(
                        context.getApplicationContext(),
                        PingGoDatabase.class,
                        "pinggo.db"
                )
                        .fallbackToDestructiveMigration()
                        .build();
            }
        }
        return instance;
    }

    public static void clearAllLocalData(Context context) {
        PingGoDatabase database = getInstance(context);
        database.clearAllTables();
    }
}
