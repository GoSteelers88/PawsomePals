package com.example.pawsomepals.data.repository

import com.example.pawsomepals.data.model.Match
import com.example.pawsomepals.data.model.DogProfile
import com.example.pawsomepals.service.MatchingService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    private val userId: String
        get() = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

    suspend fun addLike(likedProfileId: String) {
        val likeRef = realtimeDb.getReference("likes/$userId/$likedProfileId")
        likeRef.setValue(true).await()
        checkForMatch(likedProfileId)
    }

    suspend fun removeMatch(matchId: String) {
        firestore.collection("matches").document(matchId).delete().await()
    }

    suspend fun addDislike(dislikedProfileId: String) {
        val dislikeRef = realtimeDb.getReference("dislikes/$userId/$dislikedProfileId")
        dislikeRef.setValue(true).await()
    }

    private suspend fun checkForMatch(likedProfileId: String) {
        val otherUserLikeRef = realtimeDb.getReference("likes/$likedProfileId/$userId")
        val otherUserLike = otherUserLikeRef.get().await()

        if (otherUserLike.exists()) {
            // It's a match!
            createMatch(likedProfileId)
        }
    }

    private suspend fun createMatch(matchedUserId: String) {
        val matchId = firestore.collection("matches").document().id
        val match = Match(id = matchId, user1Id = userId, user2Id = matchedUserId)
        firestore.collection("matches").document(matchId).set(match).await()
    }

    suspend fun getUserMatches(): List<DogProfile> {
        val matchesQuery = firestore.collection("matches")
            .whereEqualTo("user1Id", userId)
            .get()
            .await()

        val matchedUserIds = matchesQuery.documents.mapNotNull { it.getString("user2Id") }

        return matchedUserIds.mapNotNull { dogProfileRepository.getDogProfileById(it) }
    }

    suspend fun isMatch(otherProfileId: String): Boolean {
        val currentUserProfile = dogProfileRepository.getCurrentUserDogProfile(userId)
        val otherProfile = dogProfileRepository.getDogProfileById(otherProfileId)

        if (currentUserProfile != null && otherProfile != null) {
            return matchingService.isMatch(currentUserProfile, otherProfile)
        }
        return false
    }


    fun getRecentMatches(limit: Int = 10): Flow<List<DogProfile>> = flow {
        val matchesQuery = firestore.collection("matches")
            .whereEqualTo("user1Id", userId)
            .orderBy("timestamp")
            .limit(limit.toLong())
            .get()
            .await()

        val matchedUserIds = matchesQuery.documents.mapNotNull { it.getString("user2Id") }
        val matchedProfiles = matchedUserIds.mapNotNull { dogProfileRepository.getDogProfileById(it) }
        emit(matchedProfiles)
    }

    suspend fun getCompatibilityScore(otherProfileId: String): Double {
        val currentUserProfile = dogProfileRepository.getCurrentUserDogProfile(userId)
        val otherProfile = dogProfileRepository.getDogProfileById(otherProfileId)

        if (currentUserProfile != null && otherProfile != null) {
            return matchingService.getCompatibilityScore(currentUserProfile, otherProfile)
        }
        return 0.0
    }
}


