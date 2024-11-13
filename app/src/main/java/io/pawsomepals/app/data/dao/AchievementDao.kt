package io.pawsomepals.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.pawsomepals.app.data.model.Achievement
import io.pawsomepals.app.data.model.AchievementType
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievement(achievement: Achievement)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(achievements: List<Achievement>)

    @Delete
    suspend fun deleteAchievement(achievement: Achievement)

    @Query("SELECT * FROM achievements WHERE dogId = :dogId ORDER BY dateEarned DESC")
    fun getAchievementsForDog(dogId: String): Flow<List<Achievement>>

    @Query("SELECT * FROM achievements WHERE dogId = :dogId AND type = :type ORDER BY dateEarned DESC")
    fun getAchievementsByType(dogId: String, type: AchievementType): Flow<List<Achievement>>

    @Query("SELECT COUNT(*) FROM achievements WHERE dogId = :dogId")
    suspend fun getAchievementCount(dogId: String): Int

    @Query("SELECT * FROM achievements WHERE dogId = :dogId AND id = :achievementId")
    suspend fun getAchievement(dogId: String, achievementId: String): Achievement?

    @Query("SELECT EXISTS(SELECT 1 FROM achievements WHERE dogId = :dogId AND id = :achievementId)")
    suspend fun hasAchievement(dogId: String, achievementId: String): Boolean

    @Transaction
    suspend fun updateDogAchievements(dogId: String, newAchievements: List<Achievement>) {
        // Delete old achievements for this dog
        deleteAchievementsForDog(dogId)
        // Insert new achievements
        insertAchievements(newAchievements)
    }

    @Query("DELETE FROM achievements WHERE dogId = :dogId")
    suspend fun deleteAchievementsForDog(dogId: String)
}
