package com.example.pawsomepals.data.dao


import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pawsomepals.data.model.Question
import com.example.pawsomepals.data.model.QuestionnaireResponse

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

    @Query("SELECT * FROM questionnaire_responses WHERE userId = :userId")
    suspend fun getQuestionnaireResponse(userId: String): QuestionnaireResponse?

    @Query("SELECT * FROM questionnaire_responses WHERE userId = :userId AND dogId = :dogId")
    suspend fun getQuestionnaireResponse(userId: String, dogId: String): QuestionnaireResponse

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionnaireResponse(response: QuestionnaireResponse)
}