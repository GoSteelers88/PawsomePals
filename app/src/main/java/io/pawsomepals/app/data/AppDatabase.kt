package io.pawsomepals.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.pawsomepals.app.data.converters.Converters
import io.pawsomepals.app.data.dao.AchievementDao
import io.pawsomepals.app.data.dao.ChatDao
import io.pawsomepals.app.data.dao.DogDao
import io.pawsomepals.app.data.dao.DogFriendlyLocationDao
import io.pawsomepals.app.data.dao.MatchDao
import io.pawsomepals.app.data.dao.MessageDao
import io.pawsomepals.app.data.dao.PhotoDao
import io.pawsomepals.app.data.dao.PlaydateCalendarDao
import io.pawsomepals.app.data.dao.PlaydateDao
import io.pawsomepals.app.data.dao.QuestionDao
import io.pawsomepals.app.data.dao.RatingDao
import io.pawsomepals.app.data.dao.SettingsDao
import io.pawsomepals.app.data.dao.SwipeDao
import io.pawsomepals.app.data.dao.TimeSlotDao
import io.pawsomepals.app.data.dao.UserDao
import io.pawsomepals.app.data.model.Achievement
import io.pawsomepals.app.data.model.Chat
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.Message
import io.pawsomepals.app.data.model.PhotoEntity
import io.pawsomepals.app.data.model.Playdate
import io.pawsomepals.app.data.model.PlaydateCalendarInfo
import io.pawsomepals.app.data.model.PlaydateRequest
import io.pawsomepals.app.data.model.Question
import io.pawsomepals.app.data.model.Rating
import io.pawsomepals.app.data.model.Settings
import io.pawsomepals.app.data.model.Swipe
import io.pawsomepals.app.data.model.TimeslotEntity
import io.pawsomepals.app.data.model.User

@Database(
    entities = [
        User::class,
        Dog::class,
        Swipe::class,
        TimeslotEntity::class,
        Playdate::class,
        PlaydateRequest::class,
        PlaydateCalendarInfo::class,
        Question::class,
        Settings::class,
        PhotoEntity::class,
        Chat::class,
        Message::class,
        Rating::class,
        Match::class,
        DogFriendlyLocation::class,
        Achievement::class,

    ],
    version = 37, // Increment version
    exportSchema = true
)
@TypeConverters(Converters::class, Converters.PlaydateWithDetailsConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun playdateDao(): PlaydateDao
    abstract fun dogDao(): DogDao
    abstract fun questionDao(): QuestionDao
    abstract fun photoDao(): PhotoDao
    abstract fun chatDao(): ChatDao
    abstract fun ratingDao(): RatingDao
    abstract fun settingsDao(): SettingsDao
    abstract fun swipeDao(): SwipeDao
    abstract fun matchDao(): MatchDao
    abstract fun messageDao(): MessageDao
    abstract fun playdateCalendarDao(): PlaydateCalendarDao
    abstract fun dogFriendlyLocationDao(): DogFriendlyLocationDao
    abstract fun achievementDao(): AchievementDao
    abstract fun timeSlotDao(): TimeSlotDao
// Add this line


    companion object {
        private const val DATABASE_NAME = "pawsome_pals_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Define the destructive migration
        private val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop old tables
                database.execSQL("DROP TABLE IF EXISTS playdate_calendar_sync")

                // Recreate tables with correct schema
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS playdate_calendar_sync (
                        playdateId TEXT NOT NULL DEFAULT '' PRIMARY KEY,
                        calendarEventId TEXT DEFAULT NULL,
                        lastSyncTimestamp INTEGER NOT NULL DEFAULT 0,
                        syncStatus TEXT NOT NULL DEFAULT 'PENDING',
                        failureCount INTEGER NOT NULL DEFAULT 0,
                        lastError TEXT DEFAULT NULL,
                        FOREIGN KEY(playdateId) REFERENCES playdates(id) ON DELETE CASCADE
                    )
                """)

                // Create necessary indices
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_playdate_calendar_sync_playdateId 
                    ON playdate_calendar_sync(playdateId)
                """)
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration() // Allow destructive migration during development
                    .addMigrations(MIGRATION_31_32)   // Add the migration
                    .build()

                INSTANCE = instance
                instance
            }
        }

        fun clearInstance() {
            INSTANCE = null
        }
    }
}