package io.pawsomepals.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.pawsomepals.app.data.model.UserAvailability
import com.google.android.libraries.places.api.model.DayOfWeek
import kotlinx.coroutines.flow.Flow

@Dao
interface UserAvailabilityDao {
    @Query("SELECT * FROM user_availability WHERE userId = :userId")
    fun getUserAvailability(userId: String): Flow<List<UserAvailability>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAvailability(availability: UserAvailability)

    @Delete
    suspend fun deleteAvailability(availability: UserAvailability)

    @Query("DELETE FROM user_availability WHERE userId = :userId AND dayOfWeek = :dayOfWeek")
    suspend fun deleteAvailabilityForDay(userId: String, dayOfWeek: DayOfWeek)
}
