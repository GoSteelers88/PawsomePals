package io.pawsomepals.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.pawsomepals.app.data.dao.DogDao
import io.pawsomepals.app.data.dao.UserDao
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.DogProfile
import io.pawsomepals.app.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Singleton

@Singleton
class UserRepository(
    private val userDao: UserDao,
    private val dogDao: DogDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    // Add this

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
    suspend fun getCurrentUserActiveDog(): Dog? {
        return withContext(Dispatchers.IO) {
            val user = getCurrentUser() ?: return@withContext null

            // First try to get the dog from user's dogIds
            user.dogIds.firstOrNull()?.let { dogId ->
                getDogProfileById(dogId)
            } ?: getDogProfileByOwnerId(user.id) // Fallback to owner ID lookup
        }
    }

    suspend fun getDogForChat(chatId: String): Dog? {
        return withContext(Dispatchers.IO) {
            val user = getCurrentUser() ?: return@withContext null

            // Get the chat participants' dogs
            val chatDogs = firestore.collection("chats")
                .document(chatId)
                .get()
                .await()
                .data
                ?.let { chatData ->
                    val participants = chatData["participants"] as? List<String>
                    participants?.mapNotNull { userId ->
                        if (userId == user.id) {
                            getCurrentUserActiveDog()
                        } else {
                            getDogProfileByOwnerId(userId)
                        }
                    }
                } ?: emptyList()

            // Return the current user's dog from the chat
            chatDogs.find { it.ownerId == user.id }
        }
    }


    suspend fun getDogProfileByOwnerId(ownerId: String): Dog? {
        return withContext(Dispatchers.IO) {
            val localDog = dogDao.getDogByOwnerId(ownerId)
            localDog ?: firestore.collection("dogs")
                .whereEqualTo("ownerId", ownerId)
                .get()
                .await()
                .documents
                .firstOrNull()
                ?.toObject(Dog::class.java)
                ?.also { dogDao.insertDog(it) }
        }
    }
    suspend fun createUserInFirestore(user: User) {
        withContext(Dispatchers.IO) {
            try {
                firestore.collection("users")
                    .document(user.id)
                    .set(user)
                    .await()
                Log.d("UserRepository", "User saved to Firestore successfully: ${user.id}")
            } catch (e: Exception) {
                Log.e("UserRepository", "Error saving user to Firestore", e)
                throw e
            }
        }
    }

    suspend fun getDogProfileById(profileId: String): Dog? {
        return withContext(Dispatchers.IO) {
            val localDog = dogDao.getDogById(profileId)
            localDog ?: firestore.collection("dogs")
                .document(profileId)
                .get()
                .await()
                .toObject(Dog::class.java)
                ?.also { dogDao.insertDog(it) }
        }
    }
    suspend fun updateDogPhotoUrls(dogId: String, photoUrls: List<String?>) {
        withContext(Dispatchers.IO) {
            val dog = dogDao.getDogById(dogId)
            dog?.let {
                val updatedDog = it.copy(photoUrls = photoUrls)
                updateDog(updatedDog)
            }
        }
    }
    suspend fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
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

    suspend fun getNextDogProfile(): Dog? {
        return withContext(Dispatchers.IO) {
            val currentUserId = currentUser?.id ?: return@withContext null
            dogDao.getNextUnseenDogProfile(currentUserId)
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
            // Create user without petName
            val user = User(
                id = userId,
                username = username,
                email = email,
                password = password,
                dogIds = emptyList(),  // Initialize empty dog list
                hasAcceptedTerms = false,
                hasCompletedQuestionnaire = false
            )
            insertUser(user)

            // If petName provided, create initial dog and update user's dogIds
            if (!petName.isNullOrBlank()) {
                val dogId = UUID.randomUUID().toString()
                val dog = Dog(
                    id = dogId,
                    ownerId = userId,
                    name = petName
                )
                insertDog(dog)

                // Update user with new dogId
                val updatedUser = user.copy(dogIds = listOf(dogId))
                updateUser(updatedUser)
            }
        }
    }
    suspend fun addDogToUser(userId: String, dogId: String) {
        withContext(Dispatchers.IO) {
            val user = getUserById(userId) ?: return@withContext
            val updatedUser = user.copy(
                dogIds = user.dogIds + dogId
            )
            updateUser(updatedUser)
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
    suspend fun isUserSetupComplete(userId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val user = getUserById(userId)
            user?.hasAcceptedTerms == true && user.hasCompletedQuestionnaire == true
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

    suspend fun updateDogProfile(dog: Dog) {
        withContext(Dispatchers.IO) {
            updateDog(dog)
        }
    }

    suspend fun createOrUpdateDogProfile(dog: Dog) {
        withContext(Dispatchers.IO) {
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

    suspend fun getCurrentUserDog(): Dog? {
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