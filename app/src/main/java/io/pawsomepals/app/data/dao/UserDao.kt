package io.pawsomepals.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.pawsomepals.app.data.model.User

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: String): User?

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    @Query("SELECT * FROM users ORDER BY lastLoginTime DESC LIMIT 1")
    suspend fun getLastLoggedInUser(): User?

    @Query("UPDATE users SET dailyQuestionCount = 0")
    suspend fun resetAllDailyQuestionCounts()

    @Query("UPDATE users SET hasCompletedQuestionnaire = :completed WHERE id = :userId")
    suspend fun updateUserQuestionnaireStatus(userId: String, completed: Boolean)
}