package com.example.pawsomepals.data.repository

import android.util.Log
import com.example.pawsomepals.data.dao.DogDao
import com.example.pawsomepals.data.dao.UserDao
import com.example.pawsomepals.data.model.Dog
import com.example.pawsomepals.data.model.DogProfile
import com.example.pawsomepals.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

class UserRepository(
    private val userDao: UserDao,
    private val dogDao: DogDao,
    private val firestore: FirebaseFirestore
) {
    private var currentUser: User? = null

    suspend fun getCurrentUser(): User? {
        return currentUser ?: userDao.getLastLoggedInUser()?.also { currentUser = it }
    }

    fun getUserProfile(userId: String): Flow<User?> = flow {
        emit(getUserById(userId))
    }

    fun getCurrentUserId(): String? {
        return currentUser?.id
    }

    suspend fun insertUser(user: User) {
        withContext(Dispatchers.IO) {
            userDao.insertUser(user)
            firestore.collection("users").document(user.id).set(user).await()
        }
    }

    suspend fun getDogProfileByOwnerId(ownerId: String): DogProfile? {
        return withContext(Dispatchers.IO) {
            val localDog = dogDao.getDogByOwnerId(ownerId)
            if (localDog != null) {
                dogToDogProfile(localDog)
            } else {
                val remoteDog = firestore.collection("dogs")
                    .whereEqualTo("ownerId", ownerId)
                    .get()
                    .await()
                    .documents
                    .firstOrNull()
                    ?.toObject(Dog::class.java)
                remoteDog?.let {
                    dogDao.insertDog(it)
                    dogToDogProfile(it)
                }
            }
        }
    }

    suspend fun getDogProfileById(profileId: String): DogProfile? {
        return withContext(Dispatchers.IO) {
            val localDog = dogDao.getDogById(profileId)
            if (localDog != null) {
                dogToDogProfile(localDog)
            } else {
                val remoteDog = firestore.collection("dogs")
                    .document(profileId)
                    .get()
                    .await()
                    .toObject(Dog::class.java)
                remoteDog?.let {
                    dogDao.insertDog(it)
                    dogToDogProfile(it)
                }
            }
        }
    }

    fun signInWithEmailAndPassword(email: String, password: String) {
        Log.d("FirebaseAuth", "Attempting to sign in with email: $email")
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FirebaseAuth", "signInWithEmail:success")
                    // Handle successful login
                } else {
                    Log.e("FirebaseAuth", "signInWithEmail:failure", task.exception)
                    // Handle failed login
                    val errorMessage = task.exception?.message ?: "Unknown error occurred"
                    Log.e("FirebaseAuth", "Error details: $errorMessage")
                }
            }
    }

    suspend fun getNextDogProfile(): DogProfile? {
        return withContext(Dispatchers.IO) {
            val currentUserId = currentUser?.id ?: return@withContext null
            dogDao.getNextUnseenDogProfile(currentUserId)?.let { dogToDogProfile(it) }
        }
    }

    suspend fun loginUser(email: String, password: String): User? {
        return withContext(Dispatchers.IO) {
            val localUser = userDao.getUserByEmail(email)
            val firebaseUser = firestore.collection("users")
                .whereEqualTo("email", email)
                .get()
                .await()
                .documents
                .firstOrNull()
                ?.toObject(User::class.java)

            when {
                firebaseUser != null && firebaseUser.password == password -> {
                    if (localUser == null || localUser != firebaseUser) {
                        userDao.insertUser(firebaseUser)
                    }
                    currentUser = firebaseUser
                    firebaseUser
                }
                localUser != null && localUser.password == password -> {
                    currentUser = localUser
                    // Sync local user to Firestore
                    firestore.collection("users").document(localUser.id).set(localUser).await()
                    localUser
                }
                else -> null
            }
        }
    }

    suspend fun userExists(email: String): Boolean {
        return withContext(Dispatchers.IO) {
            val user = userDao.getUserByEmail(email)
            Log.d("UserRepository", "Checking if user exists: $email, Result: ${user != null}")
            user != null
        }
    }

    suspend fun getUserByEmail(email: String): User? {
        return withContext(Dispatchers.IO) {
            userDao.getUserByEmail(email)
        }
    }

    suspend fun getUserById(id: String): User? {
        return withContext(Dispatchers.IO) {
            userDao.getUserById(id)
        }
    }

    suspend fun getUserByUsername(username: String): User? {
        return withContext(Dispatchers.IO) {
            userDao.getUserByUsername(username)
        }
    }

    suspend fun registerUser(username: String, email: String, password: String, petName: String?) {
        withContext(Dispatchers.IO) {
            val userId = UUID.randomUUID().toString()
            val user = User(
                id = userId,
                username = username,
                email = email,
                password = password,
                petName = petName,
                hasAcceptedTerms = false,
                hasCompletedQuestionnaire = false
            )
            insertUser(user)

            if (!petName.isNullOrBlank()) {
                val dogId = UUID.randomUUID().toString()
                val dog = Dog(
                    id = dogId,
                    ownerId = userId,
                    name = petName,
                    // ... other dog properties
                )
                insertDog(dog)
            }
        }
    }

    suspend fun updateUser(user: User) {
        withContext(Dispatchers.IO) {
            userDao.updateUser(user)
            firestore.collection("users").document(user.id).set(user).await()
            if (user.id == currentUser?.id) {
                currentUser = user
            }
        }
    }

    suspend fun insertDog(dog: Dog) {
        withContext(Dispatchers.IO) {
            dogDao.insertDog(dog)
            firestore.collection("dogs").document(dog.id).set(dog).await()
        }
    }

    suspend fun updateDog(dog: Dog) {
        withContext(Dispatchers.IO) {
            dogDao.updateDog(dog)
            firestore.collection("dogs").document(dog.id).set(dog).await()
        }
    }

    fun generateDogId(): String {
        return UUID.randomUUID().toString()
    }

    fun dogToDogProfile(dog: Dog): DogProfile {
        return DogProfile(
            id = dog.id,
            ownerId = dog.ownerId,
            name = dog.name,
            breed = dog.breed,
            age = dog.age,
            gender = dog.gender,
            size = dog.size,
            energyLevel = dog.energyLevel,
            friendliness = dog.friendliness,
            isSpayedNeutered = dog.isSpayedNeutered?.toBoolean(),
            friendlyWithDogs = dog.friendlyWithDogs,
            friendlyWithChildren = dog.friendlyWithChildren,
            specialNeeds = dog.specialNeeds,
            favoriteToy = dog.favoriteToy,
            preferredActivities = dog.preferredActivities,
            walkFrequency = dog.walkFrequency,
            favoriteTreat = dog.favoriteTreat,
            trainingCertifications = dog.trainingCertifications,
            bio = null, // Or provide a default value if needed
            profilePictureUrl = dog.profilePictureUrl,
            latitude = dog.latitude,
            longitude = dog.longitude
        )
    }

    fun dogProfileToDog(dogProfile: DogProfile): Dog {
        return Dog(
            id = dogProfile.id,
            ownerId = dogProfile.ownerId,
            name = dogProfile.name,
            breed = dogProfile.breed,
            age = dogProfile.age,
            gender = dogProfile.gender,
            size = dogProfile.size,
            energyLevel = dogProfile.energyLevel,
            friendliness = dogProfile.friendliness,
            isSpayedNeutered = dogProfile.isSpayedNeutered?.toString(),
            friendlyWithDogs = dogProfile.friendlyWithDogs,
            friendlyWithChildren = dogProfile.friendlyWithChildren,
            specialNeeds = dogProfile.specialNeeds,
            favoriteToy = dogProfile.favoriteToy,
            preferredActivities = dogProfile.preferredActivities,
            walkFrequency = dogProfile.walkFrequency,
            favoriteTreat = dogProfile.favoriteTreat,
            trainingCertifications = dogProfile.trainingCertifications,
            profilePictureUrl = dogProfile.profilePictureUrl,
            latitude = dogProfile.latitude,
            longitude = dogProfile.longitude
        )
    }

    suspend fun updateDogProfile(dogProfile: DogProfile) {
        withContext(Dispatchers.IO) {
            val dog = dogProfileToDog(dogProfile)
            updateDog(dog)
        }
    }

    suspend fun createOrUpdateDogProfile(dogProfile: DogProfile) {
        withContext(Dispatchers.IO) {
            val dog = dogProfileToDog(dogProfile)
            if (dogDao.getDogById(dog.id) != null) {
                updateDog(dog)
            } else {
                insertDog(dog)
            }
        }
    }

    fun getUserFlow(userId: String): Flow<User?> = flow {
        emit(getUserById(userId))
    }

    suspend fun updateSubscription(userId: String, endDate: LocalDate?) {
        withContext(Dispatchers.IO) {
            val user = getUserById(userId)
            user?.let {
                it.subscriptionEndDate = endDate
                updateUser(it)
            }
        }
    }

    suspend fun incrementDailyQuestionCount(userId: String) {
        withContext(Dispatchers.IO) {
            val user = getUserById(userId)
            user?.let {
                it.dailyQuestionCount++
                updateUser(it)
            }
        }
    }

    suspend fun resetAllDailyQuestionCounts() {
        withContext(Dispatchers.IO) {
            userDao.resetAllDailyQuestionCounts()
            // Also update Firestore
            firestore.collection("users").get().await().documents.forEach { document ->
                document.reference.update("dailyQuestionCount", 0)
            }
        }
    }

    suspend fun updateUserQuestionnaireStatus(userId: String, completed: Boolean) {
        withContext(Dispatchers.IO) {
            userDao.updateUserQuestionnaireStatus(userId, completed)
            firestore.collection("users").document(userId).update("hasCompletedQuestionnaire", completed).await()
        }
    }

    suspend fun getCurrentUserDog(): DogProfile? {
        return withContext(Dispatchers.IO) {
            currentUser?.id?.let { userId ->
                getDogProfileByOwnerId(userId)
            }
        }
    }

    suspend fun logout() {
        currentUser = null
        // You might want to clear any session data or update last logged in time here
    }
}