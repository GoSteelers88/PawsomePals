package com.example.pawsomepals.data

import android.content.Context
import android.net.Uri
import android.net.http.NetworkException
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.RequiresExtension
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.example.pawsomepals.data.dao.DogDao
import com.example.pawsomepals.data.model.User
import com.example.pawsomepals.data.model.Dog
import com.example.pawsomepals.data.model.QuestionnaireResponse
import com.example.pawsomepals.data.repository.QuestionRepository
import com.example.pawsomepals.utils.ImageHandler
import com.example.pawsomepals.utils.NetworkUtils
import com.example.pawsomepals.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

@Singleton
class DataManager @Inject constructor(
    private val appDatabase: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    @ApplicationContext private val context: Context,
    private val imageHandler: ImageHandler,
    private val networkUtils: NetworkUtils,

    private val questionRepository: QuestionRepository,

    ) {
    private val userDao = appDatabase.userDao()
    private val dogDao = appDatabase.dogDao()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Add these new properties
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncAttempts = 0

    // Auth state management
    private val _authState = MutableStateFlow<AuthViewModel.AuthState>(AuthViewModel.AuthState.Initial)
    val authState = _authState.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float>(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()


    // Add these helper functions
    private suspend fun ensureAuthenticated() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e("DataManager", "No authenticated user during ensureAuthenticated check")
            throw AuthenticationException("No authenticated user")
        }
    }

    private fun calculateBackoffDelay(): Long {
        return minOf(
            5 * 60 * 1000L, // Max 5 minutes
            (1000L * (1 shl minOf(syncAttempts++, 5))) // Exponential backoff with max of 32 seconds
        )
    }


    private var syncJob: Job? = null

    init {
        initializeAuthStateListener()

        // Start periodic cleanup of temp files
        scope.launch {
            while (isActive) {
                try {
                    cleanupTempFiles()
                    delay(30 * 60 * 1000) // Run cleanup every 30 minutes
                } catch (e: Exception) {
                    Log.e("DataManager", "Error in periodic cleanup", e)
                }
            }
        }
    }

    private fun startPeriodicSync() {
        Log.d("DataManager", "Starting periodic sync")
        stopPeriodicSync()

        syncJob = scope.launch {
            while (isActive) {
                try {
                    ensureAuthenticated()
                    syncWithFirestore()
                    delay(5 * 60 * 1000) // 5 minutes delay
                } catch (e: Exception) {
                    when (e) {
                        is AuthenticationException -> {
                            Log.e("DataManager", "Authentication error during sync", e)
                            break
                        }
                        else -> {
                            Log.e("DataManager", "Error during periodic sync", e)
                            delay(calculateBackoffDelay())
                        }
                    }
                }
            }
        }
    }

    // Add this extension function for DogDao
    suspend fun DogDao.getAllDogsByOwnerId(ownerId: String): List<Dog> {
        return withContext(Dispatchers.IO) {
            // Replace this with your actual query implementation
            // This is just an example - adjust according to your DAO structure
            getDogByOwnerId(ownerId)?.let { listOf(it) } ?: emptyList()
        }
    }

    private fun stopPeriodicSync() {
        Log.d("DataManager", "Stopping periodic sync")
        syncJob?.cancel()
        syncJob = null
    }

    private fun initializeAuthStateListener() {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                Log.d("DataManager", "User authenticated: ${user.uid}")
                _authState.value = AuthViewModel.AuthState.Authenticated
                startPeriodicSync()
            } else {
                Log.d("DataManager", "No authenticated user")
                _authState.value = AuthViewModel.AuthState.Unauthenticated
                stopPeriodicSync()
            }
        }
    }

    suspend fun syncWithFirestore() {
        withContext(Dispatchers.IO) {
            try {
                ensureAuthenticated()
                val userId = auth.currentUser!!.uid
                Log.d("DataManager", "Starting sync for user: $userId")

                // Sync user data
                val userData = getUserFromFirestore(userId)
                userData?.let {
                    saveUserToLocalDatabase(it)
                    Log.d("DataManager", "User data synced successfully")
                }

                // Sync dogs
                val firestoreDogs = getDogsFromFirestore(userId)
                syncDogsWithLocal(firestoreDogs, userId)

                syncAttempts = 0
                Log.d("DataManager", "Full sync completed successfully")
            } catch (e: Exception) {
                Log.e("DataManager", "Error during sync with Firestore", e)
                throw e
            }
        }
    }
    private suspend fun syncDogsWithLocal(firestoreDogs: List<Dog>, userId: String) {
        // Get the user's local dogs using the existing DAO method
        val localDog = dogDao.getDogByOwnerId(userId)

        // Update or insert each Firestore dog
        firestoreDogs.forEach { firestoreDog ->
            dogDao.insertDog(firestoreDog)
            Log.d("DataManager", "Synced dog: ${firestoreDog.id}")
        }

        // If there's a local dog that's not in Firestore, delete it
        localDog?.let { local ->
            if (!firestoreDogs.any { it.id == local.id }) {
                dogDao.deleteDogById(local.id)
                Log.d("DataManager", "Removed local dog: ${local.id}")
            }
        }
    }

    private suspend fun syncLocalDogsWithFirestore(firestoreDogs: List<Dog>, userId: String) {
        // Get all local dogs for this user
        val localDogs = dogDao.getAllDogsByOwnerId(userId)

        // Update or insert Firestore dogs
        firestoreDogs.forEach { firestoreDog ->
            dogDao.insertDog(firestoreDog)
        }

        // Delete local dogs that no longer exist in Firestore
        localDogs.forEach { localDog ->
            if (!firestoreDogs.any { it.id == localDog.id }) {
                dogDao.deleteDogById(localDog.id)
            }
        }
    }


    suspend fun clearAllLocalData() {
        withContext(Dispatchers.IO) {
            appDatabase.clearAllTables()
            context.getSharedPreferences("YourAppPrefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            Log.d("DataManager", "All local data cleared")
        }
    }

    suspend fun syncWithFirebase() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            try {
                val userData = getUserFromFirestore(currentUser.uid)
                if (userData != null) {
                    saveUserToLocalDatabase(userData)
                    Log.d("DataManager", "User data synced successfully")
                } else {
                    Log.w("DataManager", "No user data found in Firestore")
                }

                val dogData = getDogFromFirestore(currentUser.uid)
                if (dogData != null) {
                    saveDogToLocalDatabase(dogData)
                    Log.d("DataManager", "Dog data synced successfully")
                } else {
                    Log.w("DataManager", "No dog data found in Firestore")
                }
            } catch (e: Exception) {
                Log.e("DataManager", "Error syncing with Firebase: ${e.message}", e)
                throw e
            }
        } else {
            Log.e("DataManager", "No authenticated user found")
            throw IllegalStateException("No authenticated user found")
        }
    }

    private suspend fun getUserFromFirestore(userId: String): User? {
        return try {
            firestore.collection("users").document(userId).get().await().toObject(User::class.java)
        } catch (e: Exception) {
            Log.e("DataManager", "Error fetching user data from Firestore", e)
            null
        }
    }

    private suspend fun getDogFromFirestore(ownerId: String): Dog? {
        return try {
            firestore.collection("dogs")
                .whereEqualTo("ownerId", ownerId)
                .get()
                .await()
                .documents
                .firstOrNull()
                ?.toObject(Dog::class.java)
        } catch (e: Exception) {
            Log.e("DataManager", "Error fetching dog data from Firestore", e)
            null
        }
    }

    private suspend fun saveUserToLocalDatabase(user: User) {
        withContext(Dispatchers.IO) {
            userDao.insertUser(user)
        }
    }

    private suspend fun saveDogToLocalDatabase(dog: Dog) {
        withContext(Dispatchers.IO) {
            dogDao.insertDog(dog)
        }
    }

    suspend fun updateUserProfile(user: User) {
        try {
            val currentUser = auth.currentUser
            if (currentUser != null && currentUser.uid == user.id) {
                firestore.collection("users").document(user.id).set(user).await()
                userDao.updateUser(user)
                Log.d("DataManager", "User profile updated successfully: ${user.id}")
            } else {
                Log.e("DataManager", "Unauthorized attempt to update user profile")
                throw IllegalStateException("Unauthorized attempt to update user profile")
            }
        } catch (e: Exception) {
            Log.e("DataManager", "Error updating user profile: ${e.message}", e)
            throw e
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 7)
    suspend fun updateUserProfilePicture(uri: Uri, userId: String): String {
        return try {
            if (!networkUtils.isNetworkAvailable()) {
                throw NetworkException("No internet connection available")
            }

            val currentUser = auth.currentUser
            if (currentUser != null && currentUser.uid == userId) {
                _uploadProgress.value = 0f

                Log.d("DataManager", "Starting image compression")
                val compressedFile = imageHandler.compressImage(uri)

                val imageRef = storage.reference.child("user_images/$userId/${UUID.randomUUID()}")

                Log.d("DataManager", "Starting upload")
                val uploadTask = imageRef.putFile(compressedFile)
                    .addOnProgressListener { taskSnapshot ->
                        val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                        _uploadProgress.value = progress.toFloat()
                    }.await()

                val downloadUrl = uploadTask.storage.downloadUrl.await().toString()

                // Update Firestore and local DB
                if (networkUtils.isNetworkAvailable()) {
                    firestore.collection("users").document(userId)
                        .update("profilePictureUrl", downloadUrl).await()
                }

                val existingUser = userDao.getUserById(userId)
                existingUser?.let {
                    val updatedUser = it.copy(profilePictureUrl = downloadUrl)
                    userDao.updateUser(updatedUser)
                }

                // Cleanup
                try {
                    compressedFile.delete()
                } catch (e: Exception) {
                    Log.e("DataManager", "Error deleting temp file", e)
                }

                _uploadProgress.value = 100f
                downloadUrl
            } else {
                throw IllegalStateException("Unauthorized attempt to update profile picture")
            }
        } catch (e: Exception) {
            _uploadProgress.value = 0f
            Log.e("DataManager", "Error updating user profile picture", e)
            throw e
        }
    }


    private suspend fun compressImage(uri: Uri): File {
        return withContext(Dispatchers.IO) {
            var inputStream: java.io.InputStream? = null
            var outputStream: java.io.FileOutputStream? = null

            try {
                // Create output file first
                val outputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")

                // Setup bitmap options for initial decode
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

                // First pass - just decode bounds
                inputStream = context.contentResolver.openInputStream(uri)
                android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()

                // Calculate best sample size
                options.apply {
                    inJustDecodeBounds = false
                    inSampleSize = calculateInSampleSize(options, 1024, 1024) // Target size
                }

                // Second pass - decode with calculated sample size
                inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream, null, options)

                if (bitmap == null) {
                    throw IllegalStateException("Failed to decode image")
                }

                // Compress and save
                outputStream = FileOutputStream(outputFile)
                val success = bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)

                if (!success) {
                    throw IllegalStateException("Failed to compress image")
                }

                outputFile

            } catch (e: Exception) {
                Log.e("DataManager", "Error compressing image", e)
                throw e
            } finally {
                try {
                    inputStream?.close()
                    outputStream?.apply {
                        flush()
                        close()
                    }
                } catch (e: Exception) {
                    Log.e("DataManager", "Error closing streams", e)
                }
            }
        }
    }

    private fun calculateInSampleSize(
        options: android.graphics.BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }


    suspend fun createOrUpdateDogProfile(dog: Dog) {
        try {
            Log.d("DataManager", "Starting createOrUpdateDogProfile process")
            Log.d(
                "DataManager",
                "Initial dog data: id=${dog.id}, ownerId=${dog.ownerId}, name=${dog.name}"
            )

            val currentUser = auth.currentUser
            Log.d("DataManager", "Current authenticated user: ${currentUser?.uid}")

            if (currentUser == null) {
                Log.e("DataManager", "Authentication error: No current user found")
                throw IllegalStateException("No authenticated user found")
            }

            if (currentUser.uid != dog.ownerId) {
                Log.e(
                    "DataManager",
                    "Authorization error: Current user (${currentUser.uid}) does not match dog owner (${dog.ownerId})"
                )
                throw IllegalStateException("User not authorized to modify this dog profile")
            }

            val dogRef = if (dog.id.isBlank()) {
                Log.d("DataManager", "Creating new document reference for new dog")
                firestore.collection("dogs").document()
            } else {
                Log.d("DataManager", "Using existing document reference: ${dog.id}")
                firestore.collection("dogs").document(dog.id)
            }

            val dogToSave = if (dog.id.isBlank()) {
                Log.d("DataManager", "Generating new dog ID: ${dogRef.id}")
                dog.copy(id = dogRef.id)
            } else {
                Log.d("DataManager", "Using existing dog ID: ${dog.id}")
                dog
            }

            Log.d("DataManager", "Attempting to save dog to Firestore: ${dogToSave.id}")
            try {
                dogRef.set(dogToSave).await()
                Log.d("DataManager", "Successfully saved dog to Firestore: ${dogToSave.id}")
            } catch (e: Exception) {
                Log.e("DataManager", "Firestore save failed for dog ${dogToSave.id}", e)
                throw e
            }

            Log.d("DataManager", "Attempting to save dog to local database: ${dogToSave.id}")
            try {
                dogDao.insertDog(dogToSave)
                Log.d("DataManager", "Successfully saved dog to local database: ${dogToSave.id}")
            } catch (e: Exception) {
                Log.e("DataManager", "Local database save failed for dog ${dogToSave.id}", e)
                throw e
            }

            Log.d(
                "DataManager",
                "Dog profile created/updated successfully. Final state: id=${dogToSave.id}, name=${dogToSave.name}"
            )

        } catch (e: Exception) {
            Log.e("DataManager", "Critical error in createOrUpdateDogProfile", e)
            Log.e("DataManager", "Error details - Dog: id=${dog.id}, name=${dog.name}")
            Log.e("DataManager", "Stack trace: ${e.stackTraceToString()}")
            throw e
        }
    }

    suspend fun updateDogProfilePicture(index: Int, uri: Uri, dogId: String): String {
        return try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                val imageRef =
                    storage.reference.child("dog_images/${currentUser.uid}/${UUID.randomUUID()}")
                val uploadTask = imageRef.putFile(uri).await()
                val downloadUrl = uploadTask.storage.downloadUrl.await().toString()

                val dog = dogDao.getDogById(dogId)
                dog?.let {
                    val updatedPhotoUrls = it.photoUrls.toMutableList()
                    if (index < updatedPhotoUrls.size) {
                        updatedPhotoUrls[index] = downloadUrl
                    } else {
                        updatedPhotoUrls.add(downloadUrl)
                    }
                    val updatedDog = it.copy(photoUrls = updatedPhotoUrls)
                    createOrUpdateDogProfile(updatedDog)
                }

                downloadUrl
            } else {
                Log.e("DataManager", "Unauthorized attempt to update dog profile picture")
                throw IllegalStateException("Unauthorized attempt to update dog profile picture")
            }
        } catch (e: Exception) {
            Log.e("DataManager", "Error updating dog profile picture", e)
            throw e
        }
    }

    suspend fun saveQuestionnaireResponses(
        userId: String,
        dogId: String,
        responses: Map<String, String>
    ) {
        try {
            // Create a document ID that combines userId and dogId
            val documentId = "${userId}_${dogId}"

            firestore.collection("questionnaires")
                .document(documentId)
                .set(responses)
                .await()

            Log.d("DataManager", "Questionnaire responses saved successfully for dog: $dogId")

            // After saving to Firestore, update the dog profile with selected responses
            val dogRef = firestore.collection("dogs").document(dogId)
            val dogSnapshot = dogRef.get().await()

            if (dogSnapshot.exists()) {
                val updateMap = mutableMapOf<String, Any>()

                // Map specific questionnaire responses to dog profile fields
                responses["energyLevel"]?.let { updateMap["energyLevel"] = it }
                responses["friendliness"]?.let { updateMap["friendliness"] = it }
                responses["trainability"]?.let { updateMap["trainability"] = it }
                responses["friendlyWithDogs"]?.let { updateMap["friendlyWithDogs"] = it }
                responses["friendlyWithChildren"]?.let { updateMap["friendlyWithChildren"] = it }
                responses["friendlyWithStrangers"]?.let { updateMap["friendlyWithStrangers"] = it }
                responses["exerciseNeeds"]?.let { updateMap["exerciseNeeds"] = it }
                responses["groomingNeeds"]?.let { updateMap["groomingNeeds"] = it }
                responses["specialNeeds"]?.let { updateMap["specialNeeds"] = it }
                responses["isSpayedNeutered"]?.let { updateMap["isSpayedNeutered"] = it }

                if (updateMap.isNotEmpty()) {
                    dogRef.update(updateMap).await()

                    // Update local database
                    val updatedDog = dogSnapshot.toObject(Dog::class.java)?.copy(
                        energyLevel = responses["energyLevel"]
                            ?: dogSnapshot.getString("energyLevel") ?: "",
                        friendliness = responses["friendliness"]
                            ?: dogSnapshot.getString("friendliness") ?: "",
                        trainability = responses["trainability"]
                            ?: dogSnapshot.getString("trainability"),
                        friendlyWithDogs = responses["friendlyWithDogs"]
                            ?: dogSnapshot.getString("friendlyWithDogs"),
                        friendlyWithChildren = responses["friendlyWithChildren"]
                            ?: dogSnapshot.getString("friendlyWithChildren"),
                        friendlyWithStrangers = responses["friendlyWithStrangers"]
                            ?: dogSnapshot.getString("friendlyWithStrangers"),
                        exerciseNeeds = responses["exerciseNeeds"]
                            ?: dogSnapshot.getString("exerciseNeeds"),
                        groomingNeeds = responses["groomingNeeds"]
                            ?: dogSnapshot.getString("groomingNeeds"),
                        specialNeeds = responses["specialNeeds"]
                            ?: dogSnapshot.getString("specialNeeds"),
                        isSpayedNeutered = responses["isSpayedNeutered"]
                            ?: dogSnapshot.getString("isSpayedNeutered")
                    )

                    updatedDog?.let {
                        dogDao.insertDog(it)
                        Log.d("DataManager", "Saving questionnaire responses for dog: $dogId")
                        Log.d("DataManager", "Responses: $responses")
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("DataManager", "Error saving questionnaire responses", e)
            throw e
        }
    }


    suspend fun getQuestionnaireResponses(userId: String, dogId: String): Map<String, String>? {
        return try {
            val document = firestore.collection("questionnaires")
                .document("${userId}_${dogId}")
                .get()
                .await()

            if (document.exists()) {
                val responses = document.data
                // Convert the responses to our required Map<String, String> format
                responses?.mapValues { it.value.toString() }
            } else {
                Log.d("DataManager", "No questionnaire found for user: $userId and dog: $dogId")
                null
            }
        } catch (e: Exception) {
            Log.e("DataManager", "Error fetching questionnaire responses", e)
            null
        }
    }


    suspend fun updateDogLocation(dogId: String, latitude: Double, longitude: Double) {
        try {
            val dog = dogDao.getDogById(dogId)
            if (dog != null) {
                val updatedDog = dog.copy(latitude = latitude, longitude = longitude)
                createOrUpdateDogProfile(updatedDog)
                Log.d("DataManager", "Dog location updated successfully: $dogId")
            } else {
                Log.e("DataManager", "Dog not found")
                throw IllegalStateException("Dog not found")
            }
        } catch (e: Exception) {
            Log.e("DataManager", "Error updating dog location", e)
            throw e
        }
    }


    fun observeQuestionnaireResponses(userId: String, dogId: String): Flow<Map<String, String>> =
        callbackFlow {
            val documentId = "${userId}_${dogId}"

            val listenerRegistration = firestore.collection("questionnaires")
                .document(documentId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        close(e)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val responses =
                            snapshot.data?.mapValues { it.value.toString() } ?: emptyMap()
                        trySendBlocking(responses)
                    } else {
                        trySendBlocking(emptyMap())
                    }
                }

            awaitClose { listenerRegistration.remove() }
        }


    fun observeUserProfile(userId: String): Flow<User?> = callbackFlow {
        val listenerRegistration = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    trySendBlocking(user)
                    user?.let {
                        coroutineScope.launch {
                            userDao.insertUser(it)
                        }
                    }
                } else {
                    trySendBlocking(null)
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    fun observeDogProfile(ownerId: String): Flow<Dog?> = callbackFlow {
        val listenerRegistration = firestore.collection("dogs")
            .whereEqualTo("ownerId", ownerId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val dog = snapshot.documents.firstOrNull()?.toObject(Dog::class.java)
                    trySendBlocking(dog)
                    dog?.let {
                        coroutineScope.launch {
                            dogDao.insertDog(it)
                        }
                    }
                } else {
                    trySendBlocking(null)
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    fun observeUserDogs(userId: String): Flow<List<Dog>> = callbackFlow {
        val listenerRegistration = firestore.collection("dogs")
            .whereEqualTo("ownerId", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val dogs = snapshot.toObjects(Dog::class.java)
                    trySendBlocking(dogs)
                    coroutineScope.launch {
                        dogs.forEach { dogDao.insertDog(it) }
                    }
                } else {
                    trySendBlocking(emptyList())
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    suspend fun deleteDogProfile(dogId: String) {
        try {
            val currentUser = auth.currentUser
            val dog = dogDao.getDogById(dogId)
            if (currentUser != null && dog != null && currentUser.uid == dog.ownerId) {
                firestore.collection("dogs").document(dogId).delete().await()
                dogDao.deleteDogById(dogId)
                Log.d("DataManager", "Dog profile deleted successfully: $dogId")
            } else {
                Log.e("DataManager", "Unauthorized attempt to delete dog profile")
                throw IllegalStateException("Unauthorized attempt to delete dog profile")
            }
        } catch (e: Exception) {
            Log.e("DataManager", "Error deleting dog profile", e)
            throw e
        }
    }

    private suspend fun getDogsFromFirestore(ownerId: String): List<Dog> {
        return try {
            firestore.collection("dogs")
                .whereEqualTo("ownerId", ownerId)
                .get()
                .await()
                .toObjects(Dog::class.java)
        } catch (e: Exception) {
            Log.e("DataManager", "Error fetching dog data from Firestore", e)
            emptyList()
        }
    }

    suspend fun searchDogProfiles(query: String): List<Dog> {
        return try {
            firestore.collection("dogs")
                .whereGreaterThanOrEqualTo("name", query)
                .whereLessThanOrEqualTo("name", query + '\uf8ff')
                .get()
                .await()
                .toObjects(Dog::class.java)
        } catch (e: Exception) {
            Log.e("DataManager", "Error searching dog profiles", e)
            emptyList()
        }
    }

    suspend fun syncDogProfile(ownerId: String) {
        try {
            val dogFromFirestore = getDogFromFirestore(ownerId)
            if (dogFromFirestore != null) {
                val localDog = dogDao.getDogByOwnerId(ownerId)
                if (localDog == null || localDog != dogFromFirestore) {
                    saveDogToLocalDatabase(dogFromFirestore)
                }
                Log.d("DataManager", "Dog profile synced successfully: ${dogFromFirestore.id}")
            } else {
                val localDog = dogDao.getDogByOwnerId(ownerId)
                if (localDog != null) {
                    dogDao.deleteDogById(localDog.id)
                    Log.d(
                        "DataManager",
                        "Local dog profile removed as it doesn't exist in Firestore"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DataManager", "Error syncing dog profile", e)
        }
    }


    suspend fun getLatestUserProfile(userId: String): User? {
        return try {
            val firestoreUser = getUserFromFirestore(userId)
            val localUser = userDao.getUserById(userId)

            when {
                firestoreUser != null -> {
                    if (firestoreUser != localUser) {
                        saveUserToLocalDatabase(firestoreUser)
                    }
                    firestoreUser
                }

                localUser != null -> localUser
                else -> null
            }
        } catch (e: Exception) {
            Log.e("DataManager", "Error fetching latest user profile", e)
            null
        }
    }

    suspend fun getUserDogs(userId: String): List<Dog> {
        return try {
            val dogs = firestore.collection("dogs")
                .whereEqualTo("ownerId", userId)
                .get()
                .await()
                .toObjects(Dog::class.java)

            // Update local database with Firestore data
            val localDog = dogDao.getDogByOwnerId(userId)
            if (localDog != null) {
                val firestoreDog = dogs.firstOrNull { it.id == localDog.id }
                if (firestoreDog != null) {
                    dogDao.insertDog(firestoreDog)
                } else {
                    dogDao.deleteDogById(localDog.id)
                }
            }

            dogs.forEach { dog ->
                dogDao.insertDog(dog)
            }

            dogs
        } catch (e: Exception) {
            Log.e("DataManager", "Error fetching from Firestore, using local data", e)
            // Fallback to local data only if Firestore fails
            listOfNotNull(dogDao.getDogByOwnerId(userId))
        }
    }


    suspend fun getLatestDogProfile(ownerId: String): Dog? {
        return try {
            val firestoreDog = getDogFromFirestore(ownerId)
            val localDog = dogDao.getDogByOwnerId(ownerId)

            when {
                firestoreDog != null -> {
                    if (firestoreDog != localDog) {
                        saveDogToLocalDatabase(firestoreDog)
                    }
                    firestoreDog
                }

                localDog != null -> localDog
                else -> null
            }
        } catch (e: Exception) {
            Log.e("DataManager", "Error fetching latest dog profile", e)
            null
        }
    }

    suspend fun checkAndResolveInconsistencies(userId: String) {
        try {
            val firestoreUser = getUserFromFirestore(userId)
            val localUser = userDao.getUserById(userId)
            val firestoreDog = getDogFromFirestore(userId)
            val localDog = dogDao.getDogByOwnerId(userId)

            if (firestoreUser != localUser) {
                firestoreUser?.let { saveUserToLocalDatabase(it) }
                Log.d("DataManager", "User data inconsistency resolved for user: $userId")
            }

            if (firestoreDog != localDog) {
                firestoreDog?.let { saveDogToLocalDatabase(it) }
                Log.d("DataManager", "Dog data inconsistency resolved for user: $userId")
            }

            Log.d("DataManager", "Data consistency check completed for user: $userId")
        } catch (e: Exception) {
            Log.e("DataManager", "Error checking and resolving data inconsistencies", e)
        }
    }

    fun getOutputFileUri(context: Context): Uri {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val imageFile = File.createTempFile(
                "PROFILE_${timeStamp}_",
                ".jpg",
                storageDir
            ).apply {
                // Ensure the file is deleted when the app closes
                deleteOnExit()
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
        } catch (e: Exception) {
            Log.e("DataManager", "Error creating image file", e)
            throw e
        }
    }


    fun generateDogId(): String {
        return UUID.randomUUID().toString()
    }

    private fun cleanupTempFiles() {
        scope.launch(Dispatchers.IO) {  // Using the existing scope instead of viewModelScope
            try {
                context.cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("compressed_") && file.isFile) {
                        try {
                            if (file.exists()) {
                                file.delete()
                                Log.d("DataManager", "Deleted temp file: ${file.name}")
                            }
                        } catch (e: Exception) {
                            Log.e("DataManager", "Error deleting file: ${file.name}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DataManager", "Error cleaning up temp files", e)
            }
        }
    }


    fun cleanup() {
        Log.d("DataManager", "Cleaning up DataManager resources")
        stopPeriodicSync()
        scope.cancel()
        coroutineScope.cancel()
    }



    class AuthenticationException(message: String) : Exception(message)
}


