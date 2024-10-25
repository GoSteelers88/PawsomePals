package com.example.pawsomepals.data.repository

import android.util.Log
import com.example.pawsomepals.data.model.Dog
import com.example.pawsomepals.data.model.ResultWrapper
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DogProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userRepository: UserRepository
) {
    companion object {
        private const val TAG = "DogProfileRepository"
        private const val COLLECTION_DOGS = "dogProfiles"
        private const val COLLECTION_LIKES = "likes"
        private const val COLLECTION_DISLIKES = "dislikes"
        private const val SUBCOLLECTION_LIKED_PROFILES = "likedProfiles"
        private const val SUBCOLLECTION_DISLIKED_PROFILES = "dislikedProfiles"
    }

    private val dogProfilesCollection = firestore.collection(COLLECTION_DOGS)

    // Create new dog profile
    suspend fun createDogProfile(dog: Dog): ResultWrapper<Dog> {
        return try {
            val newDogRef = dogProfilesCollection.document()
            val dogWithId = dog.copy(id = newDogRef.id)
            newDogRef.set(dogWithId).await()
            Log.d(TAG, "Created dog profile with ID: ${dogWithId.id}")
            ResultWrapper.Success(dogWithId)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating dog profile", e)
            ResultWrapper.Error(e)
        }
    }
    // Update existing dog profile
    suspend fun updateDogProfile(dog: Dog): Result<Unit> {
        return try {
            dogProfilesCollection.document(dog.id).set(dog).await()
            Log.d(TAG, "Updated dog profile with ID: ${dog.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating dog profile: ${dog.id}", e)
            Result.failure(e)
        }
    }

    // Get dog profile as Flow
    fun getDogProfile(dogId: String): Flow<Dog?> = flow {
        try {
            val snapshot = dogProfilesCollection.document(dogId).get().await()
            val dog = snapshot.toObject(Dog::class.java)
            emit(dog)
            Log.d(TAG, "Retrieved dog profile: ${dog?.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting dog profile: $dogId", e)
            throw e
        }
    }.catch { e ->
        Log.e(TAG, "Error in dog profile flow: $dogId", e)
        throw e
    }

    // Get dog profile directly (suspend function)
    suspend fun getDogProfileById(dogId: String): Result<Dog?> {
        return try {
            val snapshot = dogProfilesCollection.document(dogId).get().await()
            val dog = snapshot.toObject(Dog::class.java)
            Log.d(TAG, "Retrieved dog profile by ID: $dogId")
            Result.success(dog)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting dog profile by ID: $dogId", e)
            Result.failure(e)
        }
    }

    // Get user's dog profiles
    fun getDogProfilesByOwner(ownerId: String): Flow<List<Dog>> = flow {
        try {
            val snapshot = dogProfilesCollection
                .whereEqualTo("ownerId", ownerId)
                .get()
                .await()
            val dogs = snapshot.toObjects(Dog::class.java)
            emit(dogs)
            Log.d(TAG, "Retrieved ${dogs.size} dogs for owner: $ownerId")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting dogs for owner: $ownerId", e)
            throw e
        }
    }

    // Update dog photo URLs
    suspend fun updateDogPhotoUrls(dogId: String, photoUrls: List<String?>): Result<Unit> {
        return try {
            val updates = mapOf("photoUrls" to photoUrls)
            dogProfilesCollection.document(dogId).update(updates).await()
            Log.d(TAG, "Updated photo URLs for dog: $dogId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating photo URLs for dog: $dogId", e)
            Result.failure(e)
        }
    }

    // Create or update dog profile
    suspend fun createOrUpdateDogProfile(dog: Dog): Result<Unit> {
        return try {
            dogProfilesCollection.document(dog.id).set(dog).await()
            Log.d(TAG, "Created/Updated dog profile: ${dog.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating/updating dog profile: ${dog.id}", e)
            Result.failure(e)
        }
    }

    // Delete dog profile
    suspend fun deleteDogProfile(dogId: String): Result<Unit> {
        return try {
            dogProfilesCollection.document(dogId).delete().await()
            Log.d(TAG, "Deleted dog profile: $dogId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting dog profile: $dogId", e)
            Result.failure(e)
        }
    }

    // Update dog location
    suspend fun updateDogLocation(dogId: String, latitude: Double, longitude: Double): Result<Unit> {
        return try {
            val updates = mapOf(
                "latitude" to latitude,
                "longitude" to longitude
            )
            dogProfilesCollection.document(dogId).update(updates).await()
            Log.d(TAG, "Updated location for dog: $dogId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating location for dog: $dogId", e)
            Result.failure(e)
        }
    }

    // Get profiles for swiping (excluding current user's dogs and already swiped profiles)
    suspend fun getSwipingProfiles(): Result<List<Dog>> {
        return try {
            val currentUserId = userRepository.getCurrentUserId() ?: throw IllegalStateException("No current user")

            val snapshot = dogProfilesCollection
                .whereNotEqualTo("ownerId", currentUserId)
                .get()
                .await()

            val allDogs = snapshot.toObjects(Dog::class.java)
            val filteredDogs = allDogs.filter { dog ->
                !hasBeenSwiped(currentUserId, dog.id).getOrDefault(false)
            }

            Log.d(TAG, "Retrieved ${filteredDogs.size} dogs for swiping")
            Result.success(filteredDogs)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting swiping profiles", e)
            Result.failure(e)
        }
    }

    // Check if a profile has been swiped
    private suspend fun hasBeenSwiped(userId: String, profileId: String): Result<Boolean> {
        return try {
            val likeSnapshot = firestore.collection(COLLECTION_LIKES)
                .document(userId)
                .collection(SUBCOLLECTION_LIKED_PROFILES)
                .document(profileId)
                .get()
                .await()

            val dislikeSnapshot = firestore.collection(COLLECTION_DISLIKES)
                .document(userId)
                .collection(SUBCOLLECTION_DISLIKED_PROFILES)
                .document(profileId)
                .get()
                .await()

            Result.success(likeSnapshot.exists() || dislikeSnapshot.exists())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking swipe status for profile: $profileId", e)
            Result.failure(e)
        }
    }

    // Search dog profiles
    suspend fun searchDogProfiles(query: String): ResultWrapper<List<Dog>> {
        return try {
            val snapshot = dogProfilesCollection
                .whereGreaterThanOrEqualTo("name", query)
                .whereLessThanOrEqualTo("name", query + "\uf8ff")
                .get()
                .await()

            val dogs = snapshot.toObjects(Dog::class.java)
            Log.d(TAG, "Found ${dogs.size} dogs matching query: $query")
            ResultWrapper.Success(dogs)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching dog profiles", e)
            ResultWrapper.Error(e)
        }
    }

    // Get current user's dog profile
    suspend fun getCurrentUserDogProfile(userId: String): Result<Dog?> {
        return try {
            val snapshot = dogProfilesCollection
                .whereEqualTo("ownerId", userId)
                .limit(1)
                .get()
                .await()

            val dog = snapshot.documents.firstOrNull()?.toObject(Dog::class.java)
            Log.d(TAG, "Retrieved current user's dog profile: ${dog?.id}")
            Result.success(dog)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user's dog profile", e)
            Result.failure(e)
        }
    }
}