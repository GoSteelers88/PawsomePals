package com.example.pawsomepals.data.repository

import android.util.Log
import com.example.pawsomepals.data.dao.DogDao
import com.example.pawsomepals.data.dao.UserDao
import com.example.pawsomepals.data.model.Dog
import com.example.pawsomepals.data.model.DogProfile
import com.example.pawsomepals.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID


class UserRepository(
    private val userDao: UserDao,
    private val dogDao: DogDao,
    private val firebaseRef: DatabaseReference
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
        userDao.insertUser(user)
        firebaseRef.child("users").child(user.id).setValue(user).await()
    }

    suspend fun getDogProfileByOwnerId(ownerId: String): DogProfile? {
        return withContext(Dispatchers.IO) {
            dogDao.getDogByOwnerId(ownerId)?.let { dogToDogProfile(it) }
                ?: firebaseRef.child("dogs").orderByChild("ownerId").equalTo(ownerId)
                    .get().await().children.firstOrNull()?.getValue(Dog::class.java)?.let { dogToDogProfile(it) }
        }
    }
    suspend fun getDogProfileById(profileId: String): DogProfile? {
        return withContext(Dispatchers.IO) {
            dogDao.getDogById(profileId)?.let { dogToDogProfile(it) }
                ?: firebaseRef.child("dogs").child(profileId)
                    .get().await().getValue(Dog::class.java)?.let { dogToDogProfile(it) }
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
            val firebaseUser = firebaseRef.child("users").orderByChild("email").equalTo(email)
                .get().await().children.firstOrNull()?.getValue(User::class.java)

            if (firebaseUser != null && firebaseUser.password == password) {
                if (localUser == null || localUser != firebaseUser) {
                    userDao.insertUser(firebaseUser)
                }
                currentUser = firebaseUser
                firebaseUser
            } else if (localUser != null && localUser.password == password) {
                currentUser = localUser
                // Sync local user to Firebase
                firebaseRef.child("users").child(localUser.id).setValue(localUser).await()
                localUser
            } else {
                null
            }
        }
    }

    suspend fun userExists(email: String): Boolean {
        return withContext(IO) {
            val user = userDao.getUserByEmail(email)
            Log.d("UserRepository", "Checking if user exists: $email, Result: ${user != null}")
            user != null
        }
    }

    suspend fun getUserByEmail(email: String): User? {
        return withContext(IO) {
            userDao.getUserByEmail(email)
        }
    }

    suspend fun getUserById(id: String): User? {
        return withContext(IO) {
            userDao.getUserById(id)
        }
    }

    suspend fun getUserByUsername(username: String): User? {
        return withContext(IO) {
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
            userDao.insertUser(user)
            firebaseRef.child("users").child(userId).setValue(user).await()

            if (!petName.isNullOrBlank()) {
                val dogId = UUID.randomUUID().toString()
                val dog = Dog(
                    id = dogId,
                    ownerId = userId,
                    name = petName,
                    // ... other dog properties
                )
                dogDao.insertDog(dog)  // Use the instance variable dogDao here
                firebaseRef.child("dogs").child(dogId).setValue(dog).await()
            }
        }
    }

    suspend fun updateUser(user: User) {
        withContext(IO) {
            userDao.updateUser(user)
            firebaseRef.child("users").child(user.id).setValue(user).await()
            if (user.id == currentUser?.id) {
                currentUser = user
            }
        }
    }

    suspend fun insertDog(dog: Dog) {
        withContext(Dispatchers.IO) {
            dogDao.insertDog(dog)
            firebaseRef.child("dogs").child(dog.id).setValue(dog).await()
        }
    }

    suspend fun updateDog(dog: Dog) {
        withContext(Dispatchers.IO) {
            dogDao.updateDog(dog)
            firebaseRef.child("dogs").child(dog.id).setValue(dog).await()
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
            dogDao.updateDog(dog)
            firebaseRef.child("dogs").child(dog.id).setValue(dog).await()
        }
    }



    suspend fun createOrUpdateDogProfile(dogProfile: DogProfile) {
        withContext(Dispatchers.IO) {
            val dog = dogProfileToDog(dogProfile)
            if (dogDao.getDogById(dog.id) != null) {
                dogDao.updateDog(dog)
            } else {
                dogDao.insertDog(dog)
            }
            firebaseRef.child("dogs").child(dog.id).setValue(dog).await()
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
            // Also update Firebase if needed
        }
    }
    suspend fun updateUserQuestionnaireStatus(userId: String, completed: Boolean) {
        userDao.updateUserQuestionnaireStatus(userId, completed)
        firebaseRef.child("users").child(userId).child("hasCompletedQuestionnaire").setValue(completed)
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