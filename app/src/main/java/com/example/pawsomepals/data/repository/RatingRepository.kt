package com.example.pawsomepals.data.repository

import com.example.pawsomepals.data.model.Rating
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RatingRepository @Inject constructor(
    private val firebaseDatabase: FirebaseDatabase
) {
    private val ratingsRef = firebaseDatabase.getReference("ratings")

    suspend fun submitRating(rating: Rating): Rating {
        val newRatingId = UUID.randomUUID().toString()
        val newRating = rating.copy(id = newRatingId)
        ratingsRef.child(newRatingId).setValue(newRating).await()
        return newRating
    }

    fun getRating(ratingId: String): Flow<Rating?> = flow {
        val snapshot = ratingsRef.child(ratingId).get().await()
        emit(snapshot.getValue(Rating::class.java))
    }

    suspend fun updateRating(rating: Rating) {
        ratingsRef.child(rating.id).setValue(rating).await()
    }

    suspend fun deleteRating(ratingId: String) {
        ratingsRef.child(ratingId).removeValue().await()
    }

    fun getUserRatings(userId: String): Flow<List<Rating>> = flow {
        val snapshot = ratingsRef.orderByChild("userId").equalTo(userId).get().await()
        val ratings = snapshot.children.mapNotNull { it.getValue(Rating::class.java) }
        emit(ratings)
    }

    fun getRaterRatings(raterId: String): Flow<List<Rating>> = flow {
        val snapshot = ratingsRef.orderByChild("raterId").equalTo(raterId).get().await()
        val ratings = snapshot.children.mapNotNull { it.getValue(Rating::class.java) }
        emit(ratings)
    }

    suspend fun getAverageRating(userId: String): Double {
        val snapshot = ratingsRef.orderByChild("userId").equalTo(userId).get().await()
        val ratings = snapshot.children.mapNotNull { it.getValue(Rating::class.java) }
        return if (ratings.isNotEmpty()) {
            ratings.map { it.score }.average()
        } else {
            0.0
        }
    }
}