package com.example.pawsomepals.data.repository

import com.example.pawsomepals.data.model.DogProfile
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DogProfileRepository @Inject constructor(
    private val firebaseDatabase: FirebaseDatabase
) {
    private val dogProfilesRef = firebaseDatabase.getReference("dogProfiles")

    suspend fun createDogProfile(dogProfile: DogProfile): DogProfile {
        val newDogRef = dogProfilesRef.push()
        dogProfile.id = newDogRef.key ?: throw IllegalStateException("Failed to generate new dog ID")
        newDogRef.setValue(dogProfile).await()
        return dogProfile
    }

    fun getDogProfile(dogId: String): Flow<DogProfile?> = flow {
        emit(getDogProfileById(dogId))
    }

    private suspend fun getDogProfileById(dogId: String): DogProfile? {
        val snapshot = dogProfilesRef.child(dogId).get().await()
        return snapshot.getValue(DogProfile::class.java)
    }

    suspend fun updateDogProfile(dogProfile: DogProfile) {
        createOrUpdateDogProfile(dogProfile)
    }

    private suspend fun createOrUpdateDogProfile(dogProfile: DogProfile) {
        dogProfilesRef.child(dogProfile.id).setValue(dogProfile).await()
    }

    suspend fun deleteDogProfile(dogId: String) {
        dogProfilesRef.child(dogId).removeValue().await()
    }

    suspend fun updateDogLocation(dogId: String, latitude: Double, longitude: Double) {
        val updates = mapOf(
            "latitude" to latitude,
            "longitude" to longitude
        )
        dogProfilesRef.child(dogId).updateChildren(updates).await()
    }

    suspend fun getDogProfilesByOwner(ownerId: String): List<DogProfile> {
        val snapshot = dogProfilesRef.orderByChild("ownerId").equalTo(ownerId).get().await()
        return snapshot.children.mapNotNull { it.getValue(DogProfile::class.java) }
    }

    suspend fun searchDogProfiles(query: String): List<DogProfile> {
        val snapshot = dogProfilesRef.orderByChild("name").startAt(query).endAt(query + "\uf8ff").get().await()
        return snapshot.children.mapNotNull { it.getValue(DogProfile::class.java) }
    }
}