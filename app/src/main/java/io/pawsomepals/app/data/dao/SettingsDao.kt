package io.pawsomepals.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.pawsomepals.app.data.model.Settings
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings LIMIT 1")
    fun getSettingsFlow(): Flow<Settings>

    @Query("SELECT * FROM settings LIMIT 1")
    suspend fun getSettings(): Settings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: Settings)

    @Update
    suspend fun updateSettings(settings: Settings)

    @Query("DELETE FROM settings")
    suspend fun deleteAllSettings()

    @Transaction
    suspend fun updateOrInsertSettings(settings: Settings) {
        val existing = getSettings()
        if (existing == null) {
            insertSettings(settings)
        } else {
            updateSettings(settings)
        }
    }
}