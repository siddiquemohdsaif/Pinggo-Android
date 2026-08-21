package com.w3n.pinggo.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                MessageEntity.class,
                ChatEntity.class,
                PresenceEntity.class,
                TransferEntity.class
        },
        version = 6,
        exportSchema = false
)
public abstract class PingGoDatabase extends RoomDatabase {
    private static volatile PingGoDatabase instance;

    public abstract MessageDao messageDao();

    public abstract ChatDao chatDao();

    public abstract PresenceDao presenceDao();
    public abstract TransferDao transferDao();

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `transfers` (`transferId` TEXT NOT NULL, `clientMessageId` TEXT, `attachmentId` TEXT, `direction` TEXT, `chatId` TEXT, `senderId` TEXT, `receiverId` TEXT, `kind` TEXT, `caption` TEXT, `repliedMessageId` TEXT, `fileName` TEXT, `mimeType` TEXT, `sourceUri` TEXT, `localUri` TEXT, `remoteUrl` TEXT, `stagedPath` TEXT, `uploadId` TEXT, `fileHash` TEXT, `totalSize` INTEGER NOT NULL, `transferredBytes` INTEGER NOT NULL, `status` TEXT, `error` TEXT, `updatedTime` INTEGER NOT NULL, PRIMARY KEY(`transferId`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfers_clientMessageId` ON `transfers` (`clientMessageId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfers_attachmentId` ON `transfers` (`attachmentId`)");
        }
    };
    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `attachmentSha256` TEXT");
        }
    };

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
                        .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
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
