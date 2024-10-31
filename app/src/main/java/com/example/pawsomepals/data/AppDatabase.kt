package com.example.pawsomepals.data


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.pawsomepals.data.dao.ChatDao
import com.example.pawsomepals.data.dao.DogDao
import com.example.pawsomepals.data.dao.PhotoDao
import com.example.pawsomepals.data.dao.PlaydateDao
import com.example.pawsomepals.data.dao.QuestionDao
import com.example.pawsomepals.data.dao.RatingDao
import com.example.pawsomepals.data.dao.SettingsDao
import com.example.pawsomepals.data.dao.SwipeDao
import com.example.pawsomepals.data.dao.UserDao
import com.example.pawsomepals.data.model.Chat
import com.example.pawsomepals.data.model.Dog
import com.example.pawsomepals.data.model.IntListConverter
import com.example.pawsomepals.data.model.LocalDateConverter
import com.example.pawsomepals.data.model.LongListConverter
import com.example.pawsomepals.data.model.Message
import com.example.pawsomepals.data.model.PhotoEntity
import com.example.pawsomepals.data.model.PlaydateRequest
import com.example.pawsomepals.data.model.Question
import com.example.pawsomepals.data.model.Rating
import com.example.pawsomepals.data.model.Settings
import com.example.pawsomepals.data.model.Swipe
import com.example.pawsomepals.data.model.Timeslot
import com.example.pawsomepals.data.model.User

@Database(
    entities = [
        User::class,
        Dog::class,
        Swipe::class,
        Timeslot::class,
        PlaydateRequest::class,
        Question::class,
        Settings::class,
        PhotoEntity::class,
        Chat::class,
        Message::class,
        Rating::class,

    ],
    version = 24,
    exportSchema = true
)
@TypeConverters(IntListConverter::class, LongListConverter::class, LocalDateConverter::class, Converters::class)
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pawsome_pals_database"
                )
                    .addMigrations(*DatabaseMigrationHelper.getAllMigrations())
                    .addMigrations(DatabaseMigrationFallback.fallbackMigration(context))
                    .addCallback(DatabaseMigrationFallback(context))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}