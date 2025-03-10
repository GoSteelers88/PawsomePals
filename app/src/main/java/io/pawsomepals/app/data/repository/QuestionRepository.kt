package io.pawsomepals.app.data.repository

import io.pawsomepals.app.data.dao.QuestionDao
import io.pawsomepals.app.data.model.Question
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class QuestionRepository(
    private val questionDao: QuestionDao,
    private val firestore: FirebaseFirestore
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

            // Save to Firestore
            firestore.collection("questions").document(questionId).set(newQuestion).await()
        }
    }

    suspend fun getQuestionsByUser(userId: String): List<Question> {
        return withContext(Dispatchers.IO) {
            // First, try to get questions from local database
            var questions = questionDao.getQuestionsByUser(userId)

            // If local database is empty, fetch from Firestore
            if (questions.isEmpty()) {
                questions = firestore.collection("questions")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                    .toObjects(Question::class.java)

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
            firestore.collection("questions").document(questionId).delete().await()
        }
    }
}