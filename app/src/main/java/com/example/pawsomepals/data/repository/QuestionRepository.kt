package com.example.pawsomepals.data.repository

import com.example.pawsomepals.data.dao.QuestionDao
import com.example.pawsomepals.data.model.Question
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class QuestionRepository(
    private val questionDao: QuestionDao,
    private val firebaseRef: DatabaseReference
) {
    suspend fun saveQuestion(userId: String, question: String, answer: String) {
        withContext(Dispatchers.IO) {
            val questionId = UUID.randomUUID().toString()
            val newQuestion = Question(
                id = questionId,
                userId = userId,
                question = question,
                answer = answer,
                timestamp = System.currentTimeMillis()
            )

            // Save to local database
            questionDao.insertQuestion(newQuestion)

            // Save to Firebase
            firebaseRef.child("questions").child(questionId).setValue(newQuestion).await()
        }
    }

    suspend fun getQuestionsByUser(userId: String): List<Question> {
        return withContext(Dispatchers.IO) {
            // First, try to get questions from local database
            var questions = questionDao.getQuestionsByUser(userId)

            // If local database is empty, fetch from Firebase
            if (questions.isEmpty()) {
                questions = firebaseRef.child("questions")
                    .orderByChild("userId")
                    .equalTo(userId)
                    .get()
                    .await()
                    .children
                    .mapNotNull { it.getValue(Question::class.java) }

                // Save fetched questions to local database
                questions.forEach { questionDao.insertQuestion(it) }
            }

            questions
        }
    }

    suspend fun getDailyQuestionCount(userId: String): Int {
        return withContext(Dispatchers.IO) {
            val today = System.currentTimeMillis() / (1000 * 60 * 60 * 24) // Get current day
            questionDao.getDailyQuestionCount(userId, today)
        }
    }

    suspend fun deleteQuestion(questionId: String) {
        withContext(Dispatchers.IO) {
            questionDao.deleteQuestion(questionId)
            firebaseRef.child("questions").child(questionId).removeValue().await()
        }
    }
}