package io.pawsomepals.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import io.pawsomepals.app.data.dao.DogDao
import io.pawsomepals.app.data.dao.UserDao
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.DogProfile
import io.pawsomepals.app.data.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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


    private val _currentUserFlow = MutableStateFlow<User?>(null)
    val currentUserFlow: StateFlow<User?> = _currentUserFlow.asStateFlow()

    init {
        // Set up auth state listener
        auth.addAuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            CoroutineScope(Dispatchers.IO).launch {
                if (firebaseUser != null) {
                    refreshCurrentUser()
                } else {
                    _currentUserFlow.value = null
                    currentUser = null
                }
            }
        }
    }
    private suspend fun refreshCurrentUser() {
        withContext(Dispatchers.IO) {
            val firebaseUser = auth.currentUser ?: return@withContext

            try {
                // Try local DB first
                var user = userDao.getUserById(firebaseUser.uid)

                // If not in local DB, try Firestore
                if (user == null) {
                    user = firestore.collection("users")
                        .document(firebaseUser.uid)
                        .get()
                        .await()
                        .toObject(User::class.java)
                        ?.also {
                            userDao.insertUser(it)
                        }
                }

                currentUser = user
                _currentUserFlow.value = user
            } catch (e: Exception) {
                Log.e("UserRepository", "Error refreshing current user", e)
            }
        }
    }

    fun getUserProfile(userId: String): Flow<User?> = flow {
        emit(getUserById(userId))
    }

    suspend fun getCurrentUser(): User? {
        return withContext(Dispatchers.IO) {
            currentUser?.let { return@withContext it }

            val firebaseUser = auth.currentUser ?: return@withContext null

            // Try to get from local DB first
            var user = userDao.getUserById(firebaseUser.uid)

            // If not in local DB, try Firestore
            if (user == null) {
                user = firestore.collection("users")
                    .document(firebaseUser.uid)
                    .get()
                    .await()
                    .toObject(User::class.java)
                    ?.also {
                        userDao.insertUser(it)
                    }
            }

            // Update current user and flow
            currentUser = user
            _currentUserFlow.value = user

            user
        }
    }

    suspend fun insertUser(user: User) {
        withContext(Dispatchers.IO) {
            try {
                // Enhanced validation
                if (user.id.isBlank()) {
                    Log.e("UserRepository", "Attempted to insert user with blank ID")
                    throw IllegalArgumentException("User ID cannot be blank")
                }

                // Insert into local DB first
                userDao.insertUser(user)

                // Then update Firestore
                firestore.collection("users")
                    .document(user.id)
                    .set(user)
                    .await()

                Log.d("UserRepository", "User inserted successfully: ${user.id}")
            } catch (e: Exception) {
                Log.e("UserRepository", "Error inserting user", e)
                throw e
            }
        }
    }
    suspend fun createUser(firebaseUser: FirebaseUser): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                val userRef = firestore.collection("users").document(firebaseUser.uid)
                val snapshot = userRef.get().await()

                val user = if (!snapshot.exists()) {
                    // Generate a unique username based on display name or email
                    val baseUsername = (firebaseUser.displayName
                        ?: firebaseUser.email?.substringBefore("@")
                        ?: "user").toLowerCase()
                    var uniqueUsername = baseUsername
                    var counter = 1

                    // Keep trying until we find a unique username
                    while (!isUsernameAvailable(uniqueUsername)) {
                        uniqueUsername = "${baseUsername}${counter++}"
                    }

                    val newUser = User(
                        id = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        username = uniqueUsername,
                        lastLoginTime = System.currentTimeMillis()
                    )
                    userRef.set(newUser).await()
                    userDao.insertUser(newUser)
                    newUser
                } else {
                    val existingUser = snapshot.toObject(User::class.java)!!
                    userDao.insertUser(existingUser)
                    existingUser
                }
                Result.success(user)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    suspend fun updateUserQuestionnaireStatus(userId: String, completed: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val user = getUserById(userId) ?: return@withContext
                val updatedUser = user.copy(hasCompletedQuestionnaire = completed)
                updateUser(updatedUser)
            } catch (e: Exception) {
                Log.e("UserRepository", "Error updating questionnaire status", e)
                throw e
            }
        }
    }
    suspend fun updateUserTermsStatus(userId: String, accepted: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val user = getUserById(userId) ?: return@withContext
                val updatedUser = user.copy(hasAcceptedTerms = accepted)
                updateUser(updatedUser)
            } catch (e: Exception) {
                Log.e("UserRepository", "Error updating terms status", e)
                throw e
            }
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



    suspend fun getNextDogProfile(): Dog? {
        return withContext(Dispatchers.IO) {
            val currentUserId = currentUser?.id ?: return@withContext null
            dogDao.getNextUnseenDogProfile(currentUserId)
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
            try {
                // Try local DB first
                var user = userDao.getUserById(id)

                // If not in local DB, try Firestore
                if (user == null) {
                    Log.d("UserRepository", "User $id not found in local DB, checking Firestore")
                    user = firestore.collection("users")
                        .document(id)
                        .get()
                        .await()
                        .toObject(User::class.java)
                        ?.also {
                            // Cache in local DB
                            userDao.insertUser(it)
                            Log.d("UserRepository", "Cached user $id from Firestore to local DB")
                        }
                }

                if (user == null) {
                    Log.d("UserRepository", "User $id not found in either local DB or Firestore")
                }

                user
            } catch (e: Exception) {
                Log.e("UserRepository", "Error getting user $id", e)
                null
            }
        }
    }
    suspend fun updateUsername(userId: String, newUsername: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isValidUsername(newUsername)) {
                    return@withContext Result.failure(IllegalArgumentException("Invalid username format"))
                }

                if (!isUsernameAvailable(newUsername)) {
                    return@withContext Result.failure(IllegalArgumentException("Username already taken"))
                }

                val user = getUserById(userId) ?: return@withContext Result.failure(
                    IllegalStateException("User not found")
                )

                val updatedUser = user.copy(username = newUsername)
                updateUser(updatedUser)

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("UserRepository", "Error updating username", e)
                Result.failure(e)
            }
        }
    }
    fun isValidUsername(username: String): Boolean {
        // Username requirements:
        // - 3-30 characters
        // - Only letters, numbers, and underscores
        // - Must start with a letter
        val usernamePattern = "^[a-zA-Z][a-zA-Z0-9_]{2,29}$"
        return username.matches(usernamePattern.toRegex())
    }
    suspend fun searchByUsername(prefix: String, limit: Int = 10): List<User> {
        return withContext(Dispatchers.IO) {
            try {
                firestore.collection("users")
                    .orderBy("username")
                    .startAt(prefix)
                    .endAt(prefix + '\uf8ff')
                    .limit(limit.toLong())
                    .get()
                    .await()
                    .toObjects(User::class.java)
            } catch (e: Exception) {
                Log.e("UserRepository", "Error searching usernames", e)
                emptyList()
            }
        }
    }
    suspend fun isUsernameAvailable(username: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection("users")
                    .whereEqualTo("username", username)
                    .get()
                    .await()

                snapshot.isEmpty
            } catch (e: Exception) {
                Log.e("UserRepository", "Error checking username availability", e)
                throw e
            }
        }
    }

    suspend fun getUserByUsername(username: String): User? {
        return withContext(Dispatchers.IO) {
            userDao.getUserByUsername(username)
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
            try {
                if (user.id.isBlank()) {
                    throw IllegalArgumentException("User ID cannot be blank")
                }

                // Update Firestore
                firestore.collection("users")
                    .document(user.id)
                    .set(user)
                    .await()

                // Update local DB
                userDao.updateUser(user)

                // Update current user if needed
                if (user.id == currentUser?.id) {
                    currentUser = user
                    _currentUserFlow.value = user
                }

                Log.d("UserRepository", "User updated successfully")
            } catch (e: Exception) {
                Log.e("UserRepository", "Error updating user", e)
                throw e
            }
        }
    }
    fun isUserSignedIn(): Boolean {
        return auth.currentUser != null
    }

    fun getCurrentUserFlow(): Flow<User?> = currentUserFlow




    fun isUserSetupComplete(): Flow<Boolean> = flow {
        withContext(Dispatchers.IO) {
            try {
                val user = getCurrentUser()
                if (user != null) {
                    emit(user.hasAcceptedTerms && user.hasCompletedQuestionnaire)
                } else {
                    emit(false)
                }
            } catch (e: Exception) {
                Log.e("UserRepository", "Error checking user setup status", e)
                emit(false)
            }
        }
    }

    suspend fun isUserSetupComplete(userId: String): Boolean {
        return try {
            val user = getUserById(userId)
            user?.let { it.hasAcceptedTerms && it.hasCompletedQuestionnaire } ?: false
        } catch (e: Exception) {
            Log.e("UserRepository", "Error checking user setup status", e)
            false
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
            isSpayedNeutered = dog.isSpayedNeutered,  // Fixed: directly use the Boolean value
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

    // In UserRepository.kt, update the dogProfileToDog function:
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
            profilePictureUrl = dogProfile.profilePictureUrl,
            isSpayedNeutered = dogProfile.isSpayedNeutered ?: false,
            friendlyWithDogs = dogProfile.friendlyWithDogs,
            friendlyWithChildren = dogProfile.friendlyWithChildren,
            friendlyWithStrangers = null,
            specialNeeds = dogProfile.specialNeeds,
            favoriteToy = dogProfile.favoriteToy,
            preferredActivities = dogProfile.preferredActivities,
            walkFrequency = dogProfile.walkFrequency,
            favoriteTreat = dogProfile.favoriteTreat,
            trainingCertifications = dogProfile.trainingCertifications,
            trainability = null,
            exerciseNeeds = null,
            groomingNeeds = null,
            weight = null,
            latitude = dogProfile.latitude,
            longitude = dogProfile.longitude,
            profileComplete = false,
            photoUrls = List(6) { null },
            achievements = emptyList()
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
    // In UserRepository.kt


    fun getCurrentUserId(): String? = auth.currentUser?.uid




    suspend fun getCurrentUserDog(): Dog? {
        return withContext(Dispatchers.IO) {
            currentUser?.id?.let { userId ->
                getDogProfileByOwnerId(userId)
            }
        }
    }

    suspend fun getUserState(userId: String): UserState {
        return withContext(Dispatchers.IO) {
            val user = getUserById(userId)
            when {
                user == null -> UserState.NOT_FOUND
                !user.hasAcceptedTerms -> UserState.NEEDS_TERMS
                !user.hasCompletedQuestionnaire -> UserState.NEEDS_QUESTIONNAIRE
                else -> UserState.COMPLETE
            }
        }
    }



    enum class UserState {
        NOT_FOUND,
        NEEDS_TERMS,
        NEEDS_QUESTIONNAIRE,
        COMPLETE
    }
}