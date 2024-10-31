package com.example.pawsomepals.data.dao


import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pawsomepals.data.model.Question

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE userId = :userId")
    suspend fun getQuestionsByUser(userId: String): List<Question>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: Question)

    @Query("SELECT COUNT(*) FROM questions WHERE userId = :userId AND timestamp >= :startOfDay")
    suspend fun getDailyQuestionCount(userId: String, startOfDay: Long): Int

    @Query("DELETE FROM questions WHERE id = :questionId")
    suspend fun deleteQuestion(questionId: String)


}