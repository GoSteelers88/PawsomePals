package com.example.pawsomepals.data.repository

import com.example.pawsomepals.data.model.Dog
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

    suspend fun createDogProfile(dog: Dog): Dog {
        val newDogRef = dogProfilesCollection.document()
        dog.id = newDogRef.id
        newDogRef.set(dog).await()
        return dog
    }

    suspend fun updateDogPhotoUrls(dogId: String, photoUrls: List<String?>) {
        val updates = mapOf(
            "photoUrls" to photoUrls
        )
        dogProfilesCollection.document(dogId).update(updates).await()
    }

    fun getDogProfile(dogId: String): Flow<Dog?> = flow {
        emit(getDogProfileById(dogId))
    }

    suspend fun getDogProfileById(dogId: String): Dog? {
        val snapshot = dogProfilesCollection.document(dogId).get().await()
        return snapshot.toObject(Dog::class.java)
    }

    suspend fun getCurrentUserDogProfile(userId: String): Dog? {
        val snapshot = dogProfilesCollection.whereEqualTo("ownerId", userId).limit(1).get().await()
        return snapshot.documents.firstOrNull()?.toObject(Dog::class.java)
    }

    suspend fun updateDogProfile(dog: Dog) {
        createOrUpdateDogProfile(dog)
    }

    suspend fun createOrUpdateDogProfile(dog: Dog) {
        dogProfilesCollection.document(dog.id).set(dog).await()
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

    suspend fun getDogProfilesByOwner(ownerId: String): List<Dog> {
        val snapshot = dogProfilesCollection.whereEqualTo("ownerId", ownerId).get().await()
        return snapshot.toObjects(Dog::class.java)
    }

    suspend fun getSwipingProfiles(): List<Dog> {
        val currentUserId = userRepository.getCurrentUserId() ?: return emptyList()

        val snapshot = firestore.collection("dogProfiles")
            .whereNotEqualTo("ownerId", currentUserId) // Exclude current user's profile
            .get()
            .await()

        return snapshot.toObjects(Dog::class.java)
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

    suspend fun searchDogProfiles(query: String): List<Dog> {
        val snapshot = dogProfilesCollection
            .whereGreaterThanOrEqualTo("name", query)
            .whereLessThanOrEqualTo("name", query + "\uf8ff")
            .get()
            .await()
        return snapshot.toObjects(Dog::class.java)
    }
}