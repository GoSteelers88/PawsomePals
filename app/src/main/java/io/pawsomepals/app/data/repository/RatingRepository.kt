package io.pawsomepals.app.data.repository

import io.pawsomepals.app.data.model.Rating
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RatingRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val ratingsCollection = firestore.collection("ratings")

    suspend fun submitRating(rating: Rating): Rating {
        val newRatingId = UUID.randomUUID().toString()
        val newRating = rating.copy(id = newRatingId)
        ratingsCollection.document(newRatingId).set(newRating).await()
        return newRating
    }

    fun getRating(ratingId: String): Flow<Rating?> = flow {
        val snapshot = ratingsCollection.document(ratingId).get().await()
        emit(snapshot.toObject(Rating::class.java))
    }

    suspend fun updateRating(rating: Rating) {
        ratingsCollection.document(rating.id).set(rating).await()
    }

    suspend fun deleteRating(ratingId: String) {
        ratingsCollection.document(ratingId).delete().await()
    }

    fun getUserRatings(userId: String): Flow<List<Rating>> = flow {
        val snapshot = ratingsCollection.whereEqualTo("userId", userId).get().await()
        val ratings = snapshot.toObjects(Rating::class.java)
        emit(ratings)
    }

    fun getRaterRatings(raterId: String): Flow<List<Rating>> = flow {
        val snapshot = ratingsCollection.whereEqualTo("raterId", raterId).get().await()
        val ratings = snapshot.toObjects(Rating::class.java)
        emit(ratings)
    }

    suspend fun getAverageRating(userId: String): Double {
        val snapshot = ratingsCollection.whereEqualTo("userId", userId).get().await()
        val ratings = snapshot.toObjects(Rating::class.java)
        return if (ratings.isNotEmpty()) {
            ratings.map { it.score }.average()
        } else {
            0.0
        }
    }
}