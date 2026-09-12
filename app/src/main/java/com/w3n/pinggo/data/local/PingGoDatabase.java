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
                CallEntity.class,
                PresenceEntity.class,
                TransferEntity.class
        },
        version = 27,
        exportSchema = false
)
public abstract class PingGoDatabase extends RoomDatabase {
    private static volatile PingGoDatabase instance;

    public abstract MessageDao messageDao();

    public abstract ChatDao chatDao();
    public abstract CallDao callDao();

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
    private static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `pinnedBy` TEXT");
        }
    };
    private static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `attachmentWidth` INTEGER");
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `attachmentHeight` INTEGER");
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `attachmentOrientation` TEXT");
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `attachmentDurationMs` INTEGER");
        }
    };
    private static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `transfers` ADD COLUMN `messageId` TEXT");
        }
    };
    private static final Migration MIGRATION_20_21 = new Migration(20, 21) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `calls` (`ownerId` TEXT NOT NULL, `callId` TEXT NOT NULL, `messageId` TEXT, `chatId` TEXT, `callerId` TEXT, `receiverId` TEXT, `mediaType` TEXT, `status` TEXT, `terminationReason` TEXT, `createdAt` INTEGER NOT NULL, `ringingAt` INTEGER, `connectedAt` INTEGER, `endedAt` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, PRIMARY KEY(`ownerId`, `callId`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_calls_ownerId_endedAt` ON `calls` (`ownerId`, `endedAt`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_calls_ownerId_chatId_endedAt` ON `calls` (`ownerId`, `chatId`, `endedAt`)");
        }
    };
    private static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `isGroup` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `groupDescription` TEXT");
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `groupMemberCount` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `ownGroupRole` TEXT");
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `membershipVersion` INTEGER NOT NULL DEFAULT 0");
        }
    };
    private static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `groupEventType` TEXT");
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `groupEventActorId` TEXT");
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `groupEventTargetIds` TEXT");
        }
    };
    private static final Migration MIGRATION_23_24 = new Migration(23, 24) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `groupReceiptsJson` TEXT");
        }
    };
    private static final Migration MIGRATION_24_25 = new Migration(24, 25) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `calls` ADD COLUMN `conference` INTEGER NOT NULL DEFAULT 0");
        }
    };
    private static final Migration MIGRATION_25_26 = new Migration(25, 26) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `calls` ADD COLUMN `participantIdsJson` TEXT");
        }
    };
    private static final Migration MIGRATION_26_27 = new Migration(26, 27) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `chats` ADD COLUMN `lastCallParticipantIdsJson` TEXT");
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `callParticipantIdsJson` TEXT");
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
                                MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
                                MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22,
                                MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25,
                                MIGRATION_25_26, MIGRATION_26_27)
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
