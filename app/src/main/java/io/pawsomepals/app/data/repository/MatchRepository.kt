package io.pawsomepals.app.data.repository

import android.annotation.SuppressLint
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.MatchStatus
import io.pawsomepals.app.data.model.MatchType
import io.pawsomepals.app.data.model.Swipe
import io.pawsomepals.app.service.MatchingService
import io.pawsomepals.app.utils.GeoFireUtils
import io.pawsomepals.app.utils.GeoLocation
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

    @SuppressLint("RestrictedApi")
    private suspend fun processLike(swipe: Swipe): Result<Unit> {
        return try {
            Log.d(TAG, """
            Processing like:
            - Swiper ID: ${swipe.swiperId}
            - Swiped ID: ${swipe.swipedId}
            - Super Like: ${swipe.superLike}
            - Score: ${swipe.compatibilityScore}
        """.trimIndent())

            // Record like in realtime database
            val likeRef = realtimeDb.reference
                .child(COLLECTION_LIKES)
                .child(swipe.swiperId)
                .child(swipe.swipedId)

            likeRef.setValue(
                mapOf(
                    "timestamp" to System.currentTimeMillis(),
                    "superLike" to swipe.superLike,
                    "compatibilityScore" to swipe.compatibilityScore
                )
            ).await()

            Log.d(TAG, "Like recorded at path: ${likeRef.path}")

            // Check for mutual match
            val mutualLike = checkMutualLike(swipe.swiperId, swipe.swipedId)
            Log.d(TAG, """
            Mutual like check:
            - Result: ${mutualLike.getOrNull()}
            - Success: ${mutualLike.isSuccess}
            - Error: ${mutualLike.exceptionOrNull()?.message}
        """.trimIndent())

            if (mutualLike.getOrNull() == true) {
                Log.d(TAG, "Creating match from swipe")
                val matchResult = createMatchFromSwipe(swipe)
                Log.d(TAG, "Match creation result: $matchResult")
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
    suspend fun hasUserSwipedProfile(userId: String, profileId: String): Result<Boolean> {
        return try {
            // Check in likes collection
            val likeRef = realtimeDb.reference
                .child(COLLECTION_LIKES)
                .child(userId)
                .child(profileId)
                .get()
                .await()

            // Check in dislikes collection
            val dislikeRef = realtimeDb.reference
                .child(COLLECTION_DISLIKES)
                .child(userId)
                .child(profileId)
                .get()
                .await()

            Result.success(likeRef.exists() || dislikeRef.exists())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking swipe status", e)
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

    suspend fun getSuggestedPlaydateLocations(
        match: Match,
        maxDistance: Double = 10.0
    ): Result<List<DogFriendlyLocation>> {
        return try {
            val centerLat = ((match.dog1Latitude ?: 0.0) + (match.dog2Latitude ?: 0.0)) / 2
            val centerLng = ((match.dog1Longitude ?: 0.0) + (match.dog2Longitude ?: 0.0)) / 2

            val locationsRef = firestore.collection("dog_friendly_locations")
            val geoHash = GeoFireUtils.getGeoHashForLocation(
                GeoLocation(centerLat, centerLng)
            )

            val bounds = GeoFireUtils.getGeoHashQueryBounds(
                GeoLocation(centerLat, centerLng),
                maxDistance * 1000
            )

            val locations = bounds.flatMap { bound ->
                locationsRef
                    .orderBy("geoHash")
                    .startAt(bound.startHash)
                    .endAt(bound.endHash)
                    .get()
                    .await()
                    .toObjects<DogFriendlyLocation>()
            }

            Result.success(locations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun createMatch(match: Match): Result<Unit> {
        return try {
            // Validate match before creation
            if (match.user1Id == match.user2Id || match.dog1Id == match.dog2Id) {
                return Result.failure(IllegalArgumentException("Cannot create match between same user or dog"))
            }

            // Check if match already exists (in either direction)
            val existingMatch = getMatchByDogs(match.dog1Id, match.dog2Id).getOrNull()
            if (existingMatch != null) {
                return Result.failure(IllegalStateException("Match already exists"))
            }

            val matchData = match.toMap() + mapOf(
                "expiryDate" to (System.currentTimeMillis() + match.matchType.getExpiryDuration()),
                "geoHash" to GeoFireUtils.getGeoHashForLocation(
                    GeoLocation(match.dog1Latitude ?: 0.0, match.dog1Longitude ?: 0.0)
                )
            )

            firestore.collection(COLLECTION_MATCHES)
                .document(match.id)
                .set(matchData)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    // Match Queries
    suspend fun getActiveMatches(userId: String): Flow<Result<List<Match>>> = flow {
        try {
            // We need to check both user1Id and user2Id
            val matches1 = firestore.collection("matches")
                .whereEqualTo("status", "ACTIVE")
                .whereEqualTo("user1Id", userId)
                .get()
                .await()

            val matches2 = firestore.collection("matches")
                .whereEqualTo("status", "ACTIVE")
                .whereEqualTo("user2Id", userId)
                .get()
                .await()

            val allMatches = (matches1.toObjects<Match>() + matches2.toObjects<Match>())
                .distinctBy { it.id } // Remove any duplicates
                .filter { match ->
                    // Additional validation
                    match.user1Id != match.user2Id && // Different users
                            match.dog1Id != match.dog2Id      // Different dogs
                }

            emit(Result.success(allMatches))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun getNearbyMatches(
        latitude: Double,
        longitude: Double,
        radius: Double
    ): Flow<List<Match>> = flow {
        try {
            val center = GeoLocation(latitude, longitude)
            val geoHash = GeoFireUtils.getGeoHashForLocation(center)
            val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radius * 1000)

            val matches = bounds.flatMap { bound ->
                firestore.collection(COLLECTION_MATCHES)
                    .orderBy("geoHash")
                    .startAt(bound.startHash)
                    .endAt(bound.endHash)
                    .get()
                    .await()
                    .toObjects<Match>()
            }

            emit(matches)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }


    fun getPendingMatches(): Flow<Result<List<Match>>> = flow {
        try {
            val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

            // Check both sides of pending matches
            val pending1 = firestore.collection("matches")
                .whereEqualTo("status", MatchStatus.PENDING.name)
                .whereEqualTo("user1Id", userId)
                .get()
                .await()

            val pending2 = firestore.collection("matches")
                .whereEqualTo("status", MatchStatus.PENDING.name)
                .whereEqualTo("user2Id", userId)
                .get()
                .await()

            val allPending = (pending1.toObjects<Match>() + pending2.toObjects<Match>())
                .distinctBy { it.id }
                .filter { match ->
                    match.user1Id != match.user2Id &&
                            match.dog1Id != match.dog2Id
                }

            emit(Result.success(allPending))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
    // Match Status Updates
    suspend fun updateMatchStatus(matchId: String, newStatus: MatchStatus): Result<Unit> {
        return try {
            firestore.collection(COLLECTION_MATCHES)
                .document(matchId)
                .update(mapOf(
                    "status" to newStatus.name,
                    "lastUpdated" to System.currentTimeMillis()
                ))
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
            // Check if dog2 liked dog1
            val likeRef = realtimeDb.reference
                .child(COLLECTION_LIKES)
                .child(dog2Id)
                .child(dog1Id)
                .get()
                .await()

            // Check if dog1 liked dog2
            val reverseLikeRef = realtimeDb.reference
                .child(COLLECTION_LIKES)
                .child(dog1Id)
                .child(dog2Id)
                .get()
                .await()

            // Both must exist for a mutual match
            Result.success(likeRef.exists() && reverseLikeRef.exists())  // Fixed to AND
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