package com.example.pawsomepals.data.repository

import android.util.Log
import com.example.pawsomepals.data.model.Match
import com.example.pawsomepals.data.model.Dog
import com.example.pawsomepals.data.model.ResultWrapper
import com.example.pawsomepals.data.model.Swipe
import com.example.pawsomepals.service.MatchingService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatchRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val realtimeDb: FirebaseDatabase,
    private val auth: FirebaseAuth,
    private val matchingService: MatchingService,
    private val dogProfileRepository: DogProfileRepository
) {
    companion object {
        private const val TAG = "MatchRepository"
        private const val COLLECTION_MATCHES = "matches"
        private const val COLLECTION_LIKES = "likes"
        private const val COLLECTION_DISLIKES = "dislikes"
    }

    private val userId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

    suspend fun addLike(likedProfileId: String): ResultWrapper<Unit> {
        return try {
            Log.d(TAG, "Adding like from $userId to $likedProfileId")
            val likeRef = realtimeDb.getReference("$COLLECTION_LIKES/$userId/$likedProfileId")
            likeRef.setValue(true).await()
            checkForMatch(likedProfileId)
            ResultWrapper.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding like", e)
            ResultWrapper.Error(e)
        }
    }

    suspend fun addDislike(dislikedProfileId: String): ResultWrapper<Unit> {
        return try {
            Log.d(TAG, "Adding dislike from $userId to $dislikedProfileId")
            val dislikeRef = realtimeDb.getReference("$COLLECTION_DISLIKES/$userId/$dislikedProfileId")
            dislikeRef.setValue(true).await()
            ResultWrapper.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding dislike", e)
            ResultWrapper.Error(e)
        }
    }
    // Add these new methods to MatchRepository

    suspend fun addSwipe(swipe: Swipe) {
        try {
            if (swipe.isLike) {
                addLike(swipe.swipedDogId)
            } else {
                addDislike(swipe.swipedDogId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding swipe", e)
            throw e
        }
    }

    suspend fun createMatch(match: Match) {
        try {
            firestore.collection(COLLECTION_MATCHES)
                .document(match.id)
                .set(match)
                .await()
            Log.d(TAG, "Created match: ${match.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating match", e)
            throw e
        }
    }

    fun getActiveMatches(): Flow<List<Match>> = flow {
        try {
            val matchesQuery = firestore.collection(COLLECTION_MATCHES)
                .whereEqualTo("user1Id", userId)
                .whereEqualTo("status", "ACTIVE")
                .get()
                .await()

            val matches = matchesQuery.toObjects(Match::class.java)
            emit(matches)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active matches", e)
            emit(emptyList())
        }
    }

    suspend fun removeMatch(matchId: String): ResultWrapper<Unit> {
        return try {
            firestore.collection(COLLECTION_MATCHES).document(matchId).delete().await()
            ResultWrapper.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing match", e)
            ResultWrapper.Error(e)
        }
    }

    suspend fun isMatch(otherProfileId: String): ResultWrapper<Boolean> {
        return try {
            val currentDogResult = dogProfileRepository.getDogProfile(userId)
                .map { it.getOrNull() }
                .firstOrNull()

            val otherDogResult = dogProfileRepository.getDogProfile(otherProfileId)
                .map { it.getOrNull() }
                .firstOrNull()

            if (currentDogResult != null && otherDogResult != null) {
                ResultWrapper.Success(matchingService.isMatch(currentDogResult, otherDogResult))
            } else {
                ResultWrapper.Error(Exception("Could not find one or both dog profiles"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking match compatibility", e)
            ResultWrapper.Error(e)
        }
    }

    fun getUserMatches(): Flow<List<Dog>> = flow {
        try {
            val matchesQuery = firestore.collection(COLLECTION_MATCHES)
                .whereEqualTo("user1Id", userId)
                .get()
                .await()

            val matchedDogs = mutableListOf<Dog>()

            for (doc in matchesQuery.documents) {
                val matchedUserId = doc.getString("user2Id")
                if (matchedUserId != null) {
                    dogProfileRepository.getDogProfile(matchedUserId)
                        .map { result -> result.getOrNull() }
                        .firstOrNull()
                        ?.let { dog ->
                            if (dog != null) {
                                matchedDogs.add(dog)
                            }
                        }
                }
            }
            emit(matchedDogs)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user matches", e)
            emit(emptyList())
        }
    }

    fun getRecentMatches(limit: Int = 10): Flow<List<Dog>> = flow {
        try {
            val matchesQuery = firestore.collection(COLLECTION_MATCHES)
                .whereEqualTo("user1Id", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val matchedDogs = mutableListOf<Dog>()

            for (doc in matchesQuery.documents) {
                val matchedUserId = doc.getString("user2Id")
                if (matchedUserId != null) {
                    dogProfileRepository.getDogProfile(matchedUserId)
                        .map { result -> result.getOrNull() }
                        .firstOrNull()
                        ?.let { dog ->
                            if (dog != null) {
                                matchedDogs.add(dog)
                            }
                        }
                }
            }
            emit(matchedDogs)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent matches", e)
            emit(emptyList())
        }
    }

    private suspend fun checkForMatch(likedProfileId: String): ResultWrapper<Unit> {
        return try {
            val otherUserLikeRef = realtimeDb.getReference("$COLLECTION_LIKES/$likedProfileId/$userId")
            val otherUserLike = otherUserLikeRef.get().await()

            if (otherUserLike.exists()) {
                createMatch(likedProfileId)
            }
            ResultWrapper.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for match", e)
            ResultWrapper.Error(e)
        }
    }

    private suspend fun createMatch(matchedUserId: String): ResultWrapper<Match> {
        return try {
            val matchId = firestore.collection(COLLECTION_MATCHES).document().id
            val match = Match(
                id = matchId,
                user1Id = userId,
                user2Id = matchedUserId,
                timestamp = System.currentTimeMillis()
            )
            firestore.collection(COLLECTION_MATCHES).document(matchId).set(match).await()
            ResultWrapper.Success(match)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating match", e)
            ResultWrapper.Error(e)
        }
    }

    suspend fun getCompatibilityScore(otherProfileId: String): ResultWrapper<Double> {
        return try {
            val currentDogResult = dogProfileRepository.getDogProfile(userId)
                .map { it.getOrNull() }
                .firstOrNull()

            val otherDogResult = dogProfileRepository.getDogProfile(otherProfileId)
                .map { it.getOrNull() }
                .firstOrNull()

            if (currentDogResult != null && otherDogResult != null) {
                ResultWrapper.Success(matchingService.getCompatibilityScore(currentDogResult, otherDogResult))
            } else {
                ResultWrapper.Error(Exception("Could not find one or both dog profiles"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating compatibility score", e)
            ResultWrapper.Error(e)
        }
    }

    suspend fun hasLiked(profileId: String): ResultWrapper<Boolean> {
        return try {
            val likeRef = realtimeDb.getReference("$COLLECTION_LIKES/$userId/$profileId")
            val snapshot = likeRef.get().await()
            ResultWrapper.Success(snapshot.exists())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking like status", e)
            ResultWrapper.Error(e)
        }
    }

    suspend fun hasDisliked(profileId: String): ResultWrapper<Boolean> {
        return try {
            val dislikeRef = realtimeDb.getReference("$COLLECTION_DISLIKES/$userId/$profileId")
            val snapshot = dislikeRef.get().await()
            ResultWrapper.Success(snapshot.exists())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking dislike status", e)
            ResultWrapper.Error(e)
        }
    }
}