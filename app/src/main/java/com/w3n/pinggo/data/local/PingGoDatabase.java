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
        version = 16,
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
    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `lastMessageTime` INTEGER NOT NULL DEFAULT 0");
        }
    };
    private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `unreadCount` INTEGER NOT NULL DEFAULT 0");
        }
    };
    private static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `notificationMuted` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0");
        }
    };
    private static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `lastMessageSenderId` TEXT");
        }
    };
    private static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `lastMessageDeliveredTime` INTEGER");
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `lastMessageReadTime` INTEGER");
        }
    };
    private static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `lastMessageType` TEXT");
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `lastMessageAttachmentName` TEXT");
        }
    };
    private static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `pinnedAt` INTEGER");
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `forwardedFrom` TEXT");
        }
    };
    private static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `deletedText` TEXT");
        }
    };
    private static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `invisible` INTEGER NOT NULL DEFAULT 0");
        }
    };
    private static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `lastMessageId` TEXT");
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `lastMessageStatus` TEXT");
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
                        .addMigrations(MIGRATION_4_5, MIGRATION_5_6,
                                MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                                MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                                MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
                                MIGRATION_15_16)
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
