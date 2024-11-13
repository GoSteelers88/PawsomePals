package io.pawsomepals.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrationHelper {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add any necessary changes for version 1 to 2
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add any necessary changes for version 2 to 3
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add any necessary changes for version 3 to 4
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add any necessary changes for version 4 to 5
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add any necessary changes for version 5 to 6
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add any necessary changes for version 6 to 7
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add any necessary changes for version 7 to 8
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add any necessary changes for version 8 to 9
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add any necessary changes for version 9 to 10
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `questions` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `userId` TEXT NOT NULL,
                    `question` TEXT NOT NULL,
                    `answer` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL
                )
            """)
        }
    }
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Handle settings table
            database.execSQL("DROP TABLE IF EXISTS settings")
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `settings` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `agePreferenceMin` INTEGER NOT NULL,
                    `agePreferenceMax` INTEGER NOT NULL,
                    `profileVisibility` INTEGER NOT NULL,
                    `breedPreferences` TEXT NOT NULL,
                    `playdateRemindersNotification` INTEGER NOT NULL,
                    `newMatchesNotification` INTEGER NOT NULL,
                    `notificationsEnabled` INTEGER NOT NULL,
                    `maxDistance` INTEGER NOT NULL,
                    `locationSharing` INTEGER NOT NULL,
                    `languageCode` TEXT NOT NULL,
                    `appUpdatesNotification` INTEGER NOT NULL,
                    `messagesNotification` INTEGER NOT NULL,
                    `privacyLevel` TEXT NOT NULL,
                    `darkModeEnabled` INTEGER NOT NULL,
                    `dataUsage` INTEGER NOT NULL
                )
            """)
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Handle photos table
            database.execSQL("DROP TABLE IF EXISTS photos")
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `photos` (
                    `id` TEXT PRIMARY KEY NOT NULL,
                    `ownerId` TEXT NOT NULL,
                    `url` TEXT NOT NULL,
                    `description` TEXT,
                    `uploadDate` INTEGER NOT NULL,
                    `isUserPhoto` INTEGER NOT NULL
                )
            """)
        }
    }



    fun getAllMigrations(): Array<Migration> {
        return arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12
        )
    }
}