package com.example.pawsomepals.data.repository

import com.example.pawsomepals.data.model.DogProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DogProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository
) {
    private val dogProfilesCollection = firestore.collection("dogProfiles")

    suspend fun createDogProfile(dogProfile: DogProfile): DogProfile {
        val newDogRef = dogProfilesCollection.document()
        dogProfile.id = newDogRef.id
        newDogRef.set(dogProfile).await()
        return dogProfile
    }

    fun getDogProfile(dogId: String): Flow<DogProfile?> = flow {
        emit(getDogProfileById(dogId))
    }

    suspend fun getDogProfileById(dogId: String): DogProfile? {
        val snapshot = dogProfilesCollection.document(dogId).get().await()
        return snapshot.toObject(DogProfile::class.java)
    }

    suspend fun getCurrentUserDogProfile(userId: String): DogProfile? {
        val snapshot = dogProfilesCollection.whereEqualTo("ownerId", userId).limit(1).get().await()
        return snapshot.documents.firstOrNull()?.toObject(DogProfile::class.java)
    }

    suspend fun updateDogProfile(dogProfile: DogProfile) {
        createOrUpdateDogProfile(dogProfile)
    }

    suspend fun createOrUpdateDogProfile(dogProfile: DogProfile) {
        dogProfilesCollection.document(dogProfile.id).set(dogProfile).await()
    }

    suspend fun deleteDogProfile(dogId: String) {
        dogProfilesCollection.document(dogId).delete().await()
    }

    suspend fun updateDogLocation(dogId: String, latitude: Double, longitude: Double) {
        val updates = mapOf(
            "latitude" to latitude,
            "longitude" to longitude
        )
        dogProfilesCollection.document(dogId).update(updates).await()
    }


    suspend fun getDogProfilesByOwner(ownerId: String): List<DogProfile> {
        val snapshot = dogProfilesCollection.whereEqualTo("ownerId", ownerId).get().await()
        return snapshot.toObjects(DogProfile::class.java)
    }
    suspend fun getSwipingProfiles(): List<DogProfile> {
        val currentUserId = userRepository.getCurrentUserId() ?: return emptyList()

        val snapshot = firestore.collection("dogProfiles")
            .whereNotEqualTo("ownerId", currentUserId) // Exclude current user's profile
            .get()
            .await()

        return snapshot.toObjects(DogProfile::class.java)
            .filter { !hasBeenSwiped(currentUserId, it.id) } // Filter out already swiped profiles
    }

    private suspend fun hasBeenSwiped(userId: String, profileId: String): Boolean {
        val likeSnapshot = firestore.collection("likes")
            .document(userId)
            .collection("likedProfiles")
            .document(profileId)
            .get()
            .await()

        val dislikeSnapshot = firestore.collection("dislikes")
            .document(userId)
            .collection("dislikedProfiles")
            .document(profileId)
            .get()
            .await()

        return likeSnapshot.exists() || dislikeSnapshot.exists()
    }

    suspend fun searchDogProfiles(query: String): List<DogProfile> {
        val snapshot = dogProfilesCollection
            .whereGreaterThanOrEqualTo("name", query)
            .whereLessThanOrEqualTo("name", query + "\uf8ff")
            .get()
            .await()
        return snapshot.toObjects(DogProfile::class.java)
    }

}