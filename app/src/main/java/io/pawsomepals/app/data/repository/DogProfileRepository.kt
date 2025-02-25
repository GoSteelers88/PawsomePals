package io.pawsomepals.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import com.google.gson.Gson
import io.pawsomepals.app.data.dao.DogDao
import io.pawsomepals.app.data.dao.UserDao
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.utils.DogIdGenerator
import io.pawsomepals.app.utils.GeoFireUtils
import io.pawsomepals.app.utils.GeoLocation
import io.pawsomepals.app.utils.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DogProfileRepository @Inject constructor(
    private val userDao: UserDao,
    private val dogDao: DogDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val userRepository: UserRepository
) {
    companion object {
        private const val TAG = "DogProfileRepository"
        private const val COLLECTION_DOGS = "dogs"
        private const val COLLECTION_LIKES = "likes"
        private const val COLLECTION_DISLIKES = "dislikes"
        private const val SUBCOLLECTION_LIKED_PROFILES = "likedProfiles"
        private const val SUBCOLLECTION_DISLIKED_PROFILES = "dislikedProfiles"
    }

    private val dogProfilesCollection = firestore.collection(COLLECTION_DOGS)

    suspend fun createDogProfile(dog: Dog): Result<Dog> {
        return try {
            val dogId = if (dog.id.isBlank()) {
                DogIdGenerator.generate(dog.ownerId)
            } else {
                dog.id
            }

            val dogWithId = dog.copy(id = dogId)
            dogProfilesCollection.document(dogId)
                .set(dogWithId)
                .await()

            Log.d(TAG, "Created dog profile with ID: $dogId")
            Result.success(dogWithId)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating dog profile", e)
            Result.failure(e)
        }
    }

    suspend fun validateDogId(dogId: String): Result<Boolean> {
        return try {
            if (!DogIdGenerator.isValid(dogId)) return Result.success(false)
            val doc = dogProfilesCollection.document(dogId).get().await()
            Result.success(doc.exists())
        } catch (e: Exception) {
            Log.e(TAG, "Error validating dog ID: $dogId", e)
            Result.failure(e)
        }
    }

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

    fun getDogProfile(dogId: String): Flow<Result<Dog?>> = flow {
        try {
            val snapshot = dogProfilesCollection.document(dogId).get().await()
            val dog = snapshot.toObject(Dog::class.java)
            Log.d(TAG, "Retrieved dog profile: ${dog?.id}")
            emit(Result.success(dog)) // emit must be the last expression
        } catch (e: Exception) {
            Log.e(TAG, "Error getting dog profile: $dogId", e)
            emit(Result.failure(e)) // emit must be the last expression
        }
    }.catch { e ->
        Log.e(TAG, "Error in dog profile flow: $dogId", e)
        emit(Result.failure(e)) // emit must be the last expression
    }
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

    fun getDogProfilesByOwner(ownerId: String): Flow<Result<List<Dog>>> = flow {
        try {
            val snapshot = dogProfilesCollection
                .whereEqualTo("ownerId", ownerId)
                .get()
                .await()
            val dogs = snapshot.toObjects(Dog::class.java)
            emit(Result.success(dogs))
            Log.d(TAG, "Retrieved ${dogs.size} dogs for owner: $ownerId")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting dogs for owner: $ownerId", e)
            emit(Result.failure(e))
        }
    }

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

    suspend fun createOrUpdateDogProfile(dog: Dog): Result<Unit> {
        return try {
            // Log the dog object before saving
            Log.d(TAG, "Attempting to save dog profile: ${Gson().toJson(dog)}")

            // Convert dog to map to ensure all fields are properly serialized
            val dogMap = dog.toMap()
            Log.d(TAG, "Dog as map: $dogMap")

            // Save to Firestore
            dogProfilesCollection.document(dog.id)
                .set(dogMap)
                .await()

            // Verify the save by reading back
            val savedDog = dogProfilesCollection.document(dog.id)
                .get()
                .await()
                .toObject(Dog::class.java)

            Log.d(TAG, "Verified saved dog: ${Gson().toJson(savedDog)}")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating/updating dog profile: ${dog.id}", e)
            Result.failure(e)
        }
    }

    private fun Dog.toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "ownerId" to ownerId,
            "name" to name,
            "breed" to breed,
            "age" to age,
            "gender" to gender,
            "size" to size,
            "energyLevel" to energyLevel,
            "friendliness" to friendliness,
            "profilePictureUrl" to profilePictureUrl,
            "isSpayedNeutered" to isSpayedNeutered,
            "friendlyWithDogs" to friendlyWithDogs,
            "friendlyWithChildren" to friendlyWithChildren,
            "specialNeeds" to specialNeeds,
            "favoriteToy" to favoriteToy,
            "preferredActivities" to preferredActivities,
            "walkFrequency" to walkFrequency,
            "favoriteTreat" to favoriteTreat,
            "trainingCertifications" to trainingCertifications,
            "latitude" to latitude,
            "longitude" to longitude,
            "photoUrls" to photoUrls,
            "trainability" to trainability,
            "friendlyWithStrangers" to friendlyWithStrangers,
            "exerciseNeeds" to exerciseNeeds,
            "groomingNeeds" to groomingNeeds,
            "weight" to weight,
            "achievements" to achievements
        )
    }
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
    suspend fun getDogById(dogId: String): Result<Dog> {
        return try {
            Log.d(TAG, "🔍 Fetching dog by ID: $dogId")
            val snapshot = dogProfilesCollection.document(dogId).get().await()
            val dog = snapshot.toObject(Dog::class.java)
            if (dog != null) {
                Log.d(TAG, "✅ Successfully retrieved dog: ${dog.id}, name: ${dog.name}")
                Result.success(dog)
            } else {
                Log.e(TAG, "❌ Dog not found for ID: $dogId")
                Result.failure(NoSuchElementException("Dog not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting dog by ID: $dogId", e)
            Result.failure(e)
        }
    }

    suspend fun getDogsByLocation(
        latitude: Double,
        longitude: Double,
        radius: Double
    ): Result<List<Dog>> {
        return try {
            Log.d(TAG, "🔍 Starting location query: lat=$latitude, lng=$longitude, radius=$radius")

            val geoHash = GeoFireUtils.getGeoHashForLocation(
                GeoLocation(latitude, longitude)
            )
            Log.d(TAG, "📍 Generated geoHash: $geoHash")

            val bounds = GeoFireUtils.getGeoHashQueryBounds(
                GeoLocation(latitude, longitude),
                radius * 1000
            )
            Log.d(TAG, "🗺️ Got ${bounds.size} query bounds")

            val tasks = bounds.map { bound ->
                dogProfilesCollection
                    .orderBy("geoHash")
                    .startAt(bound.startHash)
                    .endAt(bound.endHash)
                    .get()
            }

            val results = tasks.map { it.await() }
            val matchingDogs = results.flatMap { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val dog = doc.toObject(Dog::class.java)
                    if (dog?.latitude != null && dog.longitude != null) {
                        val distance = GeoFireUtils.getDistanceBetween(
                            GeoLocation(latitude, longitude),
                            GeoLocation(dog.latitude!!, dog.longitude!!)
                        )

                        if (distance <= radius * 1000) {
                            Log.d(TAG, "✅ Found matching dog within radius: ${dog.id}, name: ${dog.name}, distance: ${distance/1000}km")
                            dog
                        } else {
                            Log.d(TAG, "⚠️ Dog outside radius: ${dog.id}, distance: ${distance/1000}km")
                            null
                        }
                    } else {
                        Log.w(TAG, "⚠️ Dog missing location data: ${dog?.id}")
                        null
                    }
                }
            }

            Log.d(TAG, "📊 Found total ${matchingDogs.size} dogs within radius")
            Result.success(matchingDogs)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting dogs by location", e)
            Result.failure(e)
        }
    }
    // Modify existing getSwipingProfiles to include location
    suspend fun getSwipingProfiles(
        batchSize: Int = 20,
        location: Location? = null,
        radius: Double = 50.0
    ): Result<List<Dog>> {
        return try {
            val currentUserId = auth.currentUser?.uid
            Log.d(TAG, "Getting swiping profiles for user: $currentUserId")

            if (currentUserId == null) {
                Log.d(TAG, "No authenticated user found")
                return Result.success(emptyList())
            }

            // If location is available, use location-based query
            if (location != null) {
                Log.d(TAG, "Using location-based query: lat=${location.latitude}, lng=${location.longitude}")
                return getDogsByLocation(location.latitude, location.longitude, radius)
            }

            Log.d(TAG, "Using regular query")
            // Fallback to regular query if no location
            val snapshot = dogProfilesCollection
                .whereNotEqualTo("ownerId", currentUserId)
                .limit(batchSize.toLong())
                .get()  // Remove Source.SERVER
                .await()

            val dogs = snapshot.toObjects<Dog>()
            Log.d(TAG, "Found ${dogs.size} dogs, ids: ${dogs.map { it.id }}")
            Result.success(dogs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get swiping profiles", e)
            Result.failure(e)
        }
    }



    suspend fun searchDogProfiles(query: String): Result<List<Dog>> {
        return try {
            val snapshot = dogProfilesCollection
                .whereGreaterThanOrEqualTo("name", query)
                .whereLessThanOrEqualTo("name", query + "\uf8ff")
                .get()
                .await()

            val dogs = snapshot.toObjects(Dog::class.java)
            Log.d(TAG, "Found ${dogs.size} dogs matching query: $query")
            Result.success(dogs)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching dog profiles", e)
            Result.failure(e)
        }
    }

    suspend fun getCurrentUserDogProfile(userId: String): Result<Dog?> {
        return try {
            Log.d(TAG, "🔍 Fetching current user's dog profile for userId: $userId")
            val snapshot = dogProfilesCollection
                .whereEqualTo("ownerId", userId)
                .limit(1)
                .get()
                .await()

            val dog = snapshot.documents.firstOrNull()?.toObject(Dog::class.java)
            if (dog != null) {
                Log.d(TAG, "✅ Found current user's dog: ${dog.id}, name: ${dog.name}")
            } else {
                Log.w(TAG, "⚠️ No dog profile found for user: $userId")
            }
            Result.success(dog)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting current user's dog profile", e)
            Result.failure(e)
        }
    }
}