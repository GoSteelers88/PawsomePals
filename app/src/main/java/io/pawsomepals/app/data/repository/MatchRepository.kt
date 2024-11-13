package io.pawsomepals.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.MatchStatus
import io.pawsomepals.app.data.model.MatchType
import io.pawsomepals.app.data.model.Swipe
import io.pawsomepals.app.service.MatchingService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
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
        private const val DEFAULT_BATCH_SIZE = 20
        private const val MAX_CACHED_MATCHES = 100
    }

    private val userId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

    // Core Swipe Operations
    suspend fun addSwipe(swipe: Swipe): Result<Unit> {
        return try {
            when {
                swipe.isLike -> processLike(swipe)
                else -> processDislike(swipe)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing swipe", e)
            Result.failure(e)
        }
    }

    private suspend fun processLike(swipe: Swipe): Result<Unit> {
        return try {
            // Record like in realtime database
            realtimeDb.reference
                .child(COLLECTION_LIKES)
                .child(swipe.swiperId)
                .child(swipe.swipedId)
                .setValue(
                    mapOf(
                        "timestamp" to System.currentTimeMillis(),
                        "superLike" to swipe.superLike,
                        "compatibilityScore" to swipe.compatibilityScore
                    )
                ).await()

            // Check for mutual match
            val mutualLike = checkMutualLike(swipe.swiperId, swipe.swipedId)
            if (mutualLike.getOrNull() == true) {
                createMatchFromSwipe(swipe)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing like", e)
            Result.failure(e)
        }
    }

    private suspend fun processDislike(swipe: Swipe): Result<Unit> {
        return try {
            realtimeDb.reference
                .child(COLLECTION_DISLIKES)
                .child(swipe.swiperDogId)
                .child(swipe.swipedDogId)
                .setValue(
                    mapOf(
                        "id" to swipe.id,
                        "timestamp" to swipe.timestamp,
                        "viewDuration" to swipe.viewDuration,
                        "photosViewed" to swipe.photosViewed,
                        "profileScrollDepth" to swipe.profileScrollDepth,
                        "initialLocation" to swipe.initialLocation
                    )
                ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing dislike", e)
            Result.failure(e)
        }
    }

    // Match Creation and Management
    private suspend fun createMatchFromSwipe(swipe: Swipe): Result<Unit> {
        return try {
            val currentDog = dogProfileRepository.getDogById(swipe.swiperId).getOrNull()
            val matchedDog = dogProfileRepository.getDogById(swipe.swipedId).getOrNull()

            if (currentDog == null || matchedDog == null) {
                return Result.failure(IllegalStateException("Could not find dog profiles for match"))
            }

            val matchResult = matchingService.calculateMatch(currentDog, matchedDog)
            val matchType = MatchType.fromCompatibilityScore(
                score = matchResult.compatibilityScore,
                distance = matchResult.distance,
                isSuperLike = swipe.superLike,
                isBreedMatch = currentDog.breed == matchedDog.breed
            )

            val match = Match(
                id = UUID.randomUUID().toString(),
                user1Id = currentDog.ownerId,
                user2Id = matchedDog.ownerId,
                dog1Id = currentDog.id,
                dog2Id = matchedDog.id,
                matchType = matchType,
                status = MatchStatus.PENDING,
                compatibilityScore = matchResult.compatibilityScore,
                matchReasons = matchResult.reasons,
                locationDistance = matchResult.distance,
                timestamp = System.currentTimeMillis(),
                initiatorDogId = swipe.swiperId
            )

            createMatch(match)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating match from swipe", e)
            Result.failure(e)
        }
    }

    suspend fun createMatch(match: Match): Result<Unit> {
        return try {
            val matchData = match.toMap() + mapOf(
                "expiryDate" to (System.currentTimeMillis() + match.matchType.getExpiryDuration())
            )

            firestore.collection(COLLECTION_MATCHES)
                .document(match.id)
                .set(matchData)
                .await()

            // Update match counts
            updateUserMatchCounts(match.user1Id, match.user2Id)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating match", e)
            Result.failure(e)
        }
    }

    // Match Queries
    fun getActiveMatches(limit: Int = DEFAULT_BATCH_SIZE): Flow<Result<List<Match>>> = flow {
        try {
            val matchesQuery = firestore.collection(COLLECTION_MATCHES)
                .whereEqualTo("user1Id", userId)
                .whereEqualTo("status", MatchStatus.ACTIVE.name)
                .whereGreaterThan("expiryDate", System.currentTimeMillis())
                .orderBy("expiryDate", Query.Direction.ASCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val matches = matchesQuery.toObjects(Match::class.java)
            emit(Result.success(matches))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active matches", e)
            emit(Result.failure(e))
        }
    }

    fun getPendingMatches(): Flow<Result<List<Match>>> = flow {
        try {
            val matchesQuery = firestore.collection(COLLECTION_MATCHES)
                .whereEqualTo("user2Id", userId)
                .whereEqualTo("status", MatchStatus.PENDING.name)
                .whereGreaterThan("expiryDate", System.currentTimeMillis())
                .orderBy("expiryDate", Query.Direction.ASCENDING)
                .get()
                .await()

            val matches = matchesQuery.toObjects(Match::class.java)
            emit(Result.success(matches))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pending matches", e)
            emit(Result.failure(e))
        }
    }

    // Match Status Updates
    suspend fun updateMatchStatus(matchId: String, newStatus: MatchStatus): Result<Unit> {
        return try {
            firestore.collection(COLLECTION_MATCHES)
                .document(matchId)
                .update(
                    mapOf(
                        "status" to newStatus.name,
                        "lastUpdated" to System.currentTimeMillis()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating match status", e)
            Result.failure(e)
        }
    }

    // Helper Functions
    private suspend fun checkMutualLike(dog1Id: String, dog2Id: String): Result<Boolean> {
        return try {
            val likeRef = realtimeDb.reference
                .child(COLLECTION_LIKES)
                .child(dog2Id)
                .child(dog1Id)
                .get()
                .await()

            Result.success(likeRef.exists())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking mutual like", e)
            Result.failure(e)
        }
    }

    suspend fun getMatchByUsers(user1Id: String, user2Id: String): Result<Match?> {
        return try {
            // Check both possible combinations of user IDs
            val matchQuery1 = firestore.collection(COLLECTION_MATCHES)
                .whereEqualTo("user1Id", user1Id)
                .whereEqualTo("user2Id", user2Id)
                .get()
                .await()

            if (!matchQuery1.documents.isEmpty()) {
                return Result.success(matchQuery1.documents[0].toObject(Match::class.java))
            }

            val matchQuery2 = firestore.collection(COLLECTION_MATCHES)
                .whereEqualTo("user1Id", user2Id)
                .whereEqualTo("user2Id", user1Id)
                .get()
                .await()

            if (!matchQuery2.documents.isEmpty()) {
                return Result.success(matchQuery2.documents[0].toObject(Match::class.java))
            }

            Result.success(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting match by users", e)
            Result.failure(e)
        }
    }


    suspend fun getMatchById(matchId: String): Result<Match?> {
        return try {
            val matchDoc = firestore.collection(COLLECTION_MATCHES)
                .document(matchId)
                .get()
                .await()

            if (matchDoc.exists()) {
                Result.success(matchDoc.toObject(Match::class.java))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting match by ID", e)
            Result.failure(e)
        }
    }
    suspend fun getMatchByDogs(dog1Id: String, dog2Id: String): Result<Match?> {
        return try {
            // Check both possible combinations of dog IDs
            val matchQuery1 = firestore.collection(COLLECTION_MATCHES)
                .whereEqualTo("dog1Id", dog1Id)
                .whereEqualTo("dog2Id", dog2Id)
                .get()
                .await()

            if (!matchQuery1.documents.isEmpty()) {
                return Result.success(matchQuery1.documents[0].toObject(Match::class.java))
            }

            val matchQuery2 = firestore.collection(COLLECTION_MATCHES)
                .whereEqualTo("dog1Id", dog2Id)
                .whereEqualTo("dog2Id", dog1Id)
                .get()
                .await()

            if (!matchQuery2.documents.isEmpty()) {
                return Result.success(matchQuery2.documents[0].toObject(Match::class.java))
            }

            Result.success(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting match by dogs", e)
            Result.failure(e)
        }
    }

    suspend fun getActiveMatchWithUser(otherUserId: String): Result<Match?> {
        return try {
            val currentUserId = userId // Get current user ID

            val match = getMatchByUsers(currentUserId, otherUserId).getOrNull()
            if (match != null && match.status == MatchStatus.ACTIVE &&
                !match.isExpired()) {
                Result.success(match)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active match with user", e)
            Result.failure(e)
        }
    }
    private suspend fun updateUserMatchCounts(user1Id: String, user2Id: String) {
        firestore.runTransaction { transaction ->
            val user1Ref = firestore.collection("users").document(user1Id)
            val user2Ref = firestore.collection("users").document(user2Id)

            val user1Snapshot = transaction.get(user1Ref)
            val user2Snapshot = transaction.get(user2Ref)

            val user1Count = user1Snapshot.getLong("matchCount") ?: 0
            val user2Count = user2Snapshot.getLong("matchCount") ?: 0

            transaction.update(user1Ref, "matchCount", user1Count + 1)
            transaction.update(user2Ref, "matchCount", user2Count + 1)
        }.await()
    }

    suspend fun removeMatch(matchId: String): Result<Unit> {
        return try {
            firestore.collection(COLLECTION_MATCHES)
                .document(matchId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing match", e)
            Result.failure(e)
        }
    }

    suspend fun removeSwipe(swiperId: String, swipedId: String): Result<Unit> {
        return try {
            // Remove from likes collection
            realtimeDb.reference
                .child(COLLECTION_LIKES)
                .child(swiperId)
                .child(swipedId)
                .removeValue()
                .await()

            // Remove from dislikes collection
            realtimeDb.reference
                .child(COLLECTION_DISLIKES)
                .child(swiperId)
                .child(swipedId)
                .removeValue()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing swipe", e)
            Result.failure(e)
        }
    }
    private fun Match.isExpired(): Boolean {
        val expiryDuration = this.matchType.getExpiryDuration()
        return System.currentTimeMillis() > (this.timestamp + expiryDuration)
    }

}