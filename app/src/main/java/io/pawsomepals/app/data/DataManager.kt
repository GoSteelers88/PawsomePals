package io.pawsomepals.app.data

import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pawsomepals.app.auth.AuthStateManager
import io.pawsomepals.app.auth.GoogleAuthManager
import io.pawsomepals.app.data.dao.DogDao
import io.pawsomepals.app.data.managers.CalendarManager
import io.pawsomepals.app.data.model.Achievement
import io.pawsomepals.app.data.model.CalendarSyncStatus
import io.pawsomepals.app.data.model.Chat
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.MatchStatus
import io.pawsomepals.app.data.model.Message
import io.pawsomepals.app.data.model.MessageStatus
import io.pawsomepals.app.data.model.MessageType
import io.pawsomepals.app.data.model.Playdate
import io.pawsomepals.app.data.model.PlaydateCalendarInfo
import io.pawsomepals.app.data.model.PlaydateMood
import io.pawsomepals.app.data.model.PlaydateStatus
import io.pawsomepals.app.data.model.Swipe
import io.pawsomepals.app.data.model.User
import io.pawsomepals.app.data.repository.PhotoRepository
import io.pawsomepals.app.service.CalendarService
import io.pawsomepals.app.service.MatchingService
import io.pawsomepals.app.service.location.LocationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class DataManager @Inject constructor(
    private val appDatabase: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    @ApplicationContext private val context: Context,
    private val locationService: LocationService,  // Add this
    private val matchingService: MatchingService,
    private val calendarService: CalendarService,
    private val googleAuthManager: GoogleAuthManager,
    private val authStateManager: AuthStateManager,
    private val photoRepository: PhotoRepository,



) {

    private val calendarDao = appDatabase.playdateCalendarDao()  // Add this
    private val achievementDao = appDatabase.achievementDao()  // Add this line

    private val userDao = appDatabase.userDao()
    private val dogDao = appDatabase.dogDao()
    private val matchDao = appDatabase.matchDao()
    private val messageDao = appDatabase.messageDao()
    private val swipeDao = appDatabase.swipeDao()
    private val _authState = MutableStateFlow<AuthStateManager.AuthState>(AuthStateManager.AuthState.Initial)
    private var cleanupJob: Job? = null
    private var syncJob: Job? = null
    private val syncMutex = Mutex()
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())



    // Add these new properties
    private var syncAttempts = 0
    private var currentSyncJob: Job? = null
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    private val oneTapClient: SignInClient = Identity.getSignInClient(context)

    private val matchCollection = firestore.collection("matches")
    private val swipeCollection = firestore.collection("swipes")
    private val dogCollection = firestore.collection("dogs")


    //Calendar Functions
    private val _calendarSyncState = MutableStateFlow<CalendarSyncState>(CalendarSyncState.Idle)
    val calendarSyncState: StateFlow<CalendarSyncState> = _calendarSyncState.asStateFlow()


    sealed class CalendarSyncState {
        object Idle : CalendarSyncState()
        object Syncing : CalendarSyncState()
        data class Error(val message: String) : CalendarSyncState()
        object Complete : CalendarSyncState()
    }

    companion object {
        private const val ONE_HOUR_IN_MILLIS = 60 * 60 * 1000L  // 1 hour in milliseconds
    }


    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())



    private val _uploadProgress = MutableStateFlow<Float>(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    // Constants for chat media
    private val CHAT_MEDIA_MAX_SIZE = 5 * 1024 * 1024 // 5MB
    private val CHAT_IMAGE_MAX_DIMENSION = 1280 // pixels
    private val CHAT_MEDIA_PATH = "chat_media"


    private fun calculateBackoffDelay(): Long {
        return minOf(
            5 * 60 * 1000L, // Max 5 minutes
            (1000L * (1 shl minOf(syncAttempts++, 5))) // Exponential backoff with max of 32 seconds
        )
    }



    init {
        // Enable offline persistence
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        firestore.firestoreSettings = settings

        scope.launch {
            authStateManager.authState.collect { state ->
                _authState.value = state

                when (state) {
                    is AuthStateManager.AuthState.Authenticated -> {
                        Log.d("DataManager", "Authenticated state detected")
                        _isSyncing.value = true
                        try {
                            stopPeriodicSync() // Ensure any existing sync is stopped
                            syncWithFirestore() // Initial sync
                            startPeriodicSync() // Start periodic sync
                            Log.d("DataManager", "Initial sync and periodic sync started")
                        } catch (e: Exception) {
                            Log.e("DataManager", "Error during initial sync", e)
                        } finally {
                            _isSyncing.value = false
                        }
                    }

                    is AuthStateManager.AuthState.Unauthenticated,
                    is AuthStateManager.AuthState.Error -> {
                        Log.d("DataManager", "Unauthenticated/Error state detected")
                        stopPeriodicSync()
                        scope.launch {
                            try {
                                cleanupOnSignOut()
                                Log.d("DataManager", "Cleanup completed")
                            } catch (e: Exception) {
                                Log.e("DataManager", "Error during cleanup", e)
                            }
                        }
                    }

                    else -> {
                        Log.d("DataManager", "Other auth state detected: $state")
                    }
                }
            }
        }
    }

    // In DataManager.kt
    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<FirebaseUser> {
        return withContext(Dispatchers.IO) {
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user = result.user ?: throw IllegalStateException("Failed to get user after login")
                Result.success(user)
            } catch (e: Exception) {
                Log.e("DataManager", "Login failed", e)
                Result.failure(e)
            }
        }
    }


    private suspend fun ensureAuthenticated() = withContext(Dispatchers.IO) {
        val supervisor = SupervisorJob()
        supervisorScope {
            repeat(3) { attempt ->
                auth.currentUser?.let {
                    Log.d(TAG, "Authenticated on attempt ${attempt + 1}")
                    return@supervisorScope
                }
                Log.d(TAG, "Retry attempt ${attempt + 1}")
                delay(1000L * (attempt + 1))
            }
            throw AuthenticationException("Failed to authenticate after retries")
        }
    }

    private suspend fun syncUserData(userId: String) {
        withContext(Dispatchers.IO) {
            val firestoreSnapshot = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            if (firestoreSnapshot.exists()) {
                val user = firestoreSnapshot.toObject(User::class.java)
                user?.let {
                    userDao.insertUser(it)
                    Log.d("DataManager", "User data synced")
                }
            }
        }
    }




    //Calendar Implementation
    suspend fun syncPlaydateWithCalendar(playdate: Playdate) {
        try {
            _calendarSyncState.value = CalendarSyncState.Syncing

            val dog1 = dogDao.getDogById(playdate.dog1Id)
            val dog2 = dogDao.getDogById(playdate.dog2Id)

            if (dog1 == null || dog2 == null) {
                _calendarSyncState.value = CalendarSyncState.Error("Unable to find dog information")
                return
            }

            when (val result = calendarService.addPlaydateEvent(playdate)) {
                is CalendarService.CalendarResult.Success -> {
                    updatePlaydateCalendarInfo(
                        PlaydateCalendarInfo(
                            playdateId = playdate.id,
                            calendarEventId = result.data,
                            lastSyncTimestamp = System.currentTimeMillis(),
                            syncStatus = CalendarSyncStatus.SYNCED
                        )
                    )
                    _calendarSyncState.value = CalendarSyncState.Complete
                }
                is CalendarService.CalendarResult.Error -> {
                    handleCalendarSyncFailure(playdate.id, result.exception)
                }
                is CalendarService.CalendarResult.Loading -> {
                    _calendarSyncState.value = CalendarSyncState.Syncing
                }
            }
        } catch (e: Exception) {
            handleCalendarSyncFailure(playdate.id, e)
        }
    }



    private suspend fun updatePlaydateCalendarInfo(calendarInfo: PlaydateCalendarInfo) {
        withContext(Dispatchers.IO) {
            calendarDao.insert(calendarInfo)

            // Update Firestore
            firestore.collection("playdate_calendar_sync")
                .document(calendarInfo.playdateId.toString())
                .set(calendarInfo)
                .await()
        }
    }

    private suspend fun handleCalendarSyncFailure(playdateId: String, error: Exception) {
        Log.e("DataManager", "Calendar sync failed for playdate: $playdateId", error)
        _calendarSyncState.value = CalendarSyncState.Error(error.message ?: "Unknown error")

        updatePlaydateCalendarInfo(
            PlaydateCalendarInfo(
                playdateId = playdateId,
                calendarEventId = null,
                lastSyncTimestamp = System.currentTimeMillis(),
                syncStatus = CalendarSyncStatus.FAILED
            )
        )
    }


    suspend fun updatePlaydateStatus(playdateId: String, newStatus: PlaydateStatus) {
        withContext(Dispatchers.IO) {
            try {
                val playdate = appDatabase.playdateDao().getPlaydateById(playdateId)
                    ?: throw IllegalArgumentException("Playdate not found")

                // Update playdate status
                appDatabase.playdateDao().updatePlaydateStatus(
                    playdateId = playdateId,
                    newStatus = newStatus,
                    timestamp = System.currentTimeMillis()
                )

                // Update Firestore
                firestore.collection("playdates")
                    .document(playdateId)
                    .update(mapOf("status" to newStatus.name))
                    .await()

                // Update calendar event if status is significant
                if (newStatus in listOf(PlaydateStatus.CANCELLED, PlaydateStatus.COMPLETED)) {
                    updateCalendarEventStatus(playdateId, newStatus)
                }
            } catch (e: Exception) {
                Log.e("DataManager", "Error updating playdate status", e)
                throw e
            }
        }
    }



    suspend fun deletePlaydateCalendarEvent(playdate: Playdate) {
        try {
            val calendarInfo = calendarDao.getCalendarInfoForPlaydate(playdate.id)
            calendarInfo?.calendarEventId?.let { eventId ->
                when (val result = calendarService.deleteCalendarEvent(eventId)) {
                    is CalendarService.CalendarResult.Success -> {
                        calendarDao.updateCalendarInfo(
                            playdateId = playdate.id,
                            calendarEventId = null,
                            timestamp = System.currentTimeMillis(),
                            status = CalendarSyncStatus.DELETED
                        )
                    }
                    is CalendarService.CalendarResult.Error -> {
                        Log.e("DataManager", "Failed to delete calendar event", result.exception)
                        calendarDao.updateCalendarInfo(
                            playdateId = playdate.id,
                            calendarEventId = eventId,
                            timestamp = System.currentTimeMillis(),
                            status = CalendarSyncStatus.FAILED
                        )
                    }
                    is CalendarService.CalendarResult.Loading -> {
                        // Handle loading state if needed
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DataManager", "Error deleting calendar event", e)
            throw e
        }
    }
       private suspend fun updateCalendarEventStatus(playdateId: String, status: PlaydateStatus) {
        try {
            val calendarInfo = calendarDao.getCalendarInfoForPlaydate(playdateId)
                ?: return

            when (status) {
                PlaydateStatus.CANCELLED -> {
                    calendarService.deleteCalendarEvent(calendarInfo.calendarEventId!!)
                    calendarDao.updateCalendarInfo(
                        playdateId = playdateId,
                        calendarEventId = null,
                        timestamp = System.currentTimeMillis(),
                        status = CalendarSyncStatus.DELETED,
                        failureCount = 0,
                        error = null
                    )
                }
                PlaydateStatus.COMPLETED -> {
                    calendarService.updateCalendarEventStatus(
                        calendarInfo.calendarEventId!!,
                        "Completed Playdate"
                    )
                }
                else -> { /* Other statuses don't require calendar updates */ }
            }
        } catch (e: Exception) {
            Log.e("DataManager", "Error updating calendar event status", e)
        }
    }

    fun checkCalendarAuthStatus(): CalendarManager.CalendarAuthState {
        return calendarService.checkAuthStatus()
    }

    suspend fun getGoogleSignInIntent(): Intent? {  // Changed return type to Intent?
        return try {
            googleAuthManager.getSignInIntent().fold(
                onSuccess = { intent -> intent },  // Return Intent directly
                onFailure = {
                    Log.e("DataManager", "Error getting sign in intent", it)
                    null
                }
            )
        } catch (e: Exception) {
            Log.e("DataManager", "Error getting sign in intent", e)
            null
        }
    }
    suspend fun handleCalendarSignInResult(data: Intent?) {
        try {
            calendarService.handleAuthResult(data)
        } catch (e: Exception) {
            Log.e("DataManager", "Error handling calendar sign-in", e)
            throw e
        }
    }


    suspend fun initializeChatForMatch(match: Match): String {
        try {
            ensureAuthenticated()
            val chat = Chat(
                id = UUID.randomUUID().toString(),
                user1Id = match.user1Id,
                user2Id = match.user2Id,
                dog1Id = match.dog1Id,
                dog2Id = match.dog2Id,
                matchId = match.id,
                participants = listOf(match.user1Id, match.user2Id),
                created = System.currentTimeMillis()
            )

            // Save to Firestore
            firestore.collection("chats")
                .document(chat.id)
                .set(chat)
                .await()

            // Update match with chat reference
            firestore.collection("matches")
                .document(match.id)
                .update("chatId", chat.id)
                .await()

            // Save to local database
            appDatabase.chatDao().insertChat(chat)

            return chat.id
        } catch (e: Exception) {
            Log.e("DataManager", "Error initializing chat for match", e)
            throw e
        }
    }

    suspend fun sendMessage(chatId: String, content: String, type: MessageType = MessageType.TEXT, metadata: Map<String, Any>? = null) {
        try {
            ensureAuthenticated()
            val currentUser = auth.currentUser!!

            val message = Message(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                senderId = currentUser.uid,
                content = content,
                timestamp = System.currentTimeMillis(),
                type = type,
                metadata = metadata,
                status = MessageStatus.SENT
            )

            // Save to Firestore
            firestore.collection("messages")
                .document(message.id)
                .set(message)
                .await()

            // Update chat's last message
            firestore.collection("chats")
                .document(chatId)
                .update(mapOf(
                    "lastMessageTimestamp" to message.timestamp,
                    "lastMessagePreview" to message.content,
                    "lastMessageType" to type.name,
                    "hasUnreadMessages" to true
                ))
                .await()

            // Save to local database
            appDatabase.messageDao().insertMessage(message)

        } catch (e: Exception) {
            Log.e("DataManager", "Error sending message", e)
            throw e
        }
    }

    fun observeChatMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val listener = firestore.collection("messages")
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val messages = snapshot.toObjects(Message::class.java)
                    trySendBlocking(messages)

                    // Launch in scope passed to callbackFlow
                    launch {
                        messages.forEach {
                            messageDao.insertMessage(it)
                        }
                    }
                }
            }

        awaitClose { listener.remove() }
    }


    fun observeUserChats(userId: String): Flow<List<Chat>> = callbackFlow {
        val listener = firestore.collection("chats")
            .whereArrayContains("participants", userId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val chats = snapshot.toObjects(Chat::class.java)
                    trySendBlocking(chats)

                    // Launch in scope passed to callbackFlow
                    launch {
                        chats.forEach {
                            appDatabase.chatDao().insertChat(it)
                        }
                    }
                }
            }

        awaitClose { listener.remove() }
    }


    suspend fun saveAchievement(achievement: Achievement) {
        try {
            ensureAuthenticated()

            // Save to Firestore
            firestore.collection("achievements")
                .document(achievement.id)
                .set(achievement)
                .await()

            // Save to local database
            withContext(Dispatchers.IO) {
                appDatabase.achievementDao().insertAchievement(achievement)
            }

            Log.d("DataManager", "Achievement saved successfully: ${achievement.id}")
        } catch (e: Exception) {
            Log.e("DataManager", "Error saving achievement", e)
            throw e
        }
    }

    suspend fun markMessagesAsRead(chatId: String) {
        try {
            ensureAuthenticated()
            val currentUser = auth.currentUser!!

            // Update Firestore
            val unreadMessages = firestore.collection("messages")
                .whereEqualTo("chatId", chatId)
                .whereNotEqualTo("senderId", currentUser.uid)
                .whereEqualTo("status", MessageStatus.DELIVERED.name)
                .get()
                .await()

            val batch = firestore.batch()
            unreadMessages.documents.forEach { doc ->
                batch.update(doc.reference, "status", MessageStatus.READ.name)
            }
            batch.commit().await()

            // Update local database
            appDatabase.messageDao().markMessagesAsRead(chatId, currentUser.uid)

            // Update chat unread status
            firestore.collection("chats")
                .document(chatId)
                .update("hasUnreadMessages", false)
                .await()

        } catch (e: Exception) {
            Log.e("DataManager", "Error marking messages as read", e)
            throw e
        }
    }
    fun startSync() {
        syncJob?.cancel()
        syncJob = managerScope.launch {
            try {
                authStateManager.authState.collect { state ->
                    when (state) {
                        is AuthStateManager.AuthState.Authenticated -> {
                            syncMutex.withLock {
                                _isSyncing.value = true
                                try {
                                    syncWithFirestore()
                                    startPeriodicSync()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.e("DataManager", "Sync error", e)
                                } finally {
                                    _isSyncing.value = false
                                }
                            }
                        }
                        else -> stopPeriodicSync()
                    }
                }
            } catch (e: CancellationException) {
                Log.d("DataManager", "Sync cancelled normally")
            }
        }
    }


    fun startPeriodicSync() {
        scope.launch {
            while (isActive) {
                try {
                    delay(5 * 60 * 1000)
                    syncMutex.withLock {
                        syncWithFirestore()
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e("DataManager", "Periodic sync error", e)
                    delay(calculateBackoffDelay())
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

    fun stopPeriodicSync() {
        Log.d("DataManager", "Stopping periodic sync")
        syncJob?.cancel()
        syncJob = null
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

    private suspend fun syncDogsWithFirestore() {
        withContext(Dispatchers.IO) {
            try {
                val firestoreDogs = firestore.collection("dogs")
                    .get()
                    .await()
                    .toObjects(Dog::class.java)

                firestoreDogs.forEach { dog ->
                    dogDao.insertDog(dog)
                }
            } catch (e: Exception) {
                Log.e("DataManager", "Error syncing dogs", e)
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

    suspend fun syncWithFirestore() = withContext(Dispatchers.IO) {
        coroutineScope {
            val currentUser = auth.currentUser ?: throw IllegalStateException("No authenticated user")

            supervisorScope {
                launch { syncUserData(currentUser.uid) }
                launch { syncDogData(currentUser.uid) }
            }
        }
    }

    private suspend fun syncDogData(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                // Get dogs from Firestore
                val firestoreDogs = firestore.collection("dogs")
                    .whereEqualTo("ownerId", userId)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { it.toObject(Dog::class.java) }

                // Get local dogs
                val localDogs = dogDao.getAllDogsByOwnerId(userId)

                // Update or insert Firestore dogs to local DB
                firestoreDogs.forEach { firestoreDog ->
                    dogDao.insertDog(firestoreDog)
                    Log.d("DataManager", "Synced dog from Firestore: ${firestoreDog.id}")
                }

                // Remove local dogs that don't exist in Firestore
                localDogs.forEach { localDog ->
                    if (!firestoreDogs.any { it.id == localDog.id }) {
                        dogDao.deleteDogById(localDog.id)
                        Log.d("DataManager", "Removed local dog: ${localDog.id}")
                    }
                }

                Log.d("DataManager", "Dog sync completed successfully")
            } catch (e: Exception) {
                Log.e("DataManager", "Error syncing dog data", e)
                throw e
            }
        }
    }

    suspend fun processSwipe(currentDogId: String, swipedDogId: String, isLike: Boolean) {
        try {
            ensureAuthenticated()
            val currentUser = auth.currentUser!!

            // Create swipe record
            val swipe = Swipe(
                swiperId = currentUser.uid,
                swipedId = swipedDogId,
                swiperDogId = currentDogId,
                swipedDogId = swipedDogId,
                isLike = isLike,
                timestamp = System.currentTimeMillis()
            )

            // Save swipe to Firestore first
            val swipeRef = swipeCollection.document()
            swipe.id = swipeRef.id
            swipeRef.set(swipe).await()

            // If it's a like, check for match
            if (isLike) {
                checkForMatchOnline(swipe)
            }

            // Update local database after successful online operation
            swipeDao.insertSwipe(swipe)

        } catch (e: Exception) {
            Log.e("DataManager", "Error processing swipe", e)
            throw e
        }
    }
    private suspend fun checkForMatchOnline(newSwipe: Swipe) {
        try {
            // Check for reciprocal swipe in Firestore
            val reciprocalSwipes = swipeCollection
                .whereEqualTo("swiperId", newSwipe.swipedId)
                .whereEqualTo("swipedId", newSwipe.swiperId)
                .whereEqualTo("isLike", true)
                .get()
                .await()

            if (!reciprocalSwipes.isEmpty) {
                // We have a match!
                val reciprocalSwipe = reciprocalSwipes.documents.first().toObject(Swipe::class.java)!!
                createMatchOnline(newSwipe, reciprocalSwipe)
            }
        } catch (e: Exception) {
            Log.e("DataManager", "Error checking for match online", e)
            throw e
        }
    }

    private suspend fun createMatchOnline(swipe1: Swipe, swipe2: Swipe) {
        try {
            val dog1 = dogCollection.document(swipe1.swiperDogId).get().await().toObject(Dog::class.java)
            val dog2 = dogCollection.document(swipe2.swiperDogId).get().await().toObject(Dog::class.java)

            if (dog1 == null || dog2 == null) {
                Log.e("DataManager", "Unable to find dogs for match creation")
                return
            }

            // Calculate compatibility using MatchingService
            val matchResult = matchingService.calculateMatch(dog1, dog2)

            // Create match document with proper constructor
            val match = Match(
                id = UUID.randomUUID().toString(),
                user1Id = swipe1.swiperId,
                user2Id = swipe2.swiperId,
                dog1Id = swipe1.swiperDogId,
                dog2Id = swipe2.swiperDogId,
                compatibilityScore = matchResult.compatibilityScore,
                matchReasons = matchResult.reasons,
                status = MatchStatus.PENDING,
                timestamp = System.currentTimeMillis()
            )

            // Save to Firestore
            matchCollection.document(match.id).set(match).await()

            // Create chat room and send notifications
            createChatRoomForMatch(match)
            sendMatchNotifications(match)

            // Update local database
            matchDao.insertMatch(match)

        } catch (e: Exception) {
            Log.e("DataManager", "Error creating match online", e)
            throw e
        }
    }

    // Get potential matches from online source
    suspend fun getOnlinePotentialMatches(dogId: String, limit: Int = 20): List<Dog> {
        try {
            ensureAuthenticated()
            Log.d(TAG, "Getting potential matches for dog: $dogId")

            val currentDog = dogCollection.document(dogId).get().await().toObject(Dog::class.java)
            Log.d(TAG, "Current dog: ${currentDog?.id}, owner: ${currentDog?.ownerId}")

            val allDogs = dogCollection
                .get()
                .await()
                .toObjects(Dog::class.java)
            Log.d(TAG, "Total dogs in database: ${allDogs.size}")

            val swipedProfiles = swipeCollection
                .whereEqualTo("swiperId", currentDog?.ownerId)
                .get()
                .await()
                .toObjects(Swipe::class.java)
                .map { it.swipedDogId }
            Log.d(TAG, "Already swiped dogs: ${swipedProfiles.size}")

            val potentialMatches = dogCollection
                .whereNotEqualTo("id", dogId)
                .whereNotEqualTo("ownerId", currentDog?.ownerId)
                .get()
                .await()
                .toObjects(Dog::class.java)
                .filter { dog ->
                    val isValidMatch = !swipedProfiles.contains(dog.id) &&
                            isWithinMatchingDistance(currentDog!!, dog)
                    Log.d(TAG, """
                    Evaluating dog ${dog.id}:
                    - Owner: ${dog.ownerId}
                    - Already swiped: ${swipedProfiles.contains(dog.id)}
                    - Within distance: ${currentDog?.let { isWithinMatchingDistance(it, dog) }}
                    - Is valid match: $isValidMatch
                """.trimIndent())
                    isValidMatch
                }
                .take(limit)

            Log.d(TAG, "Found ${potentialMatches.size} potential matches")
            return potentialMatches
        } catch (e: Exception) {
            Log.e(TAG, "Error getting online potential matches", e)
            throw e
        }
    }
    // Get active matches from online source
    fun observeOnlineMatches(dogId: String): Flow<List<Match>> = callbackFlow {
        val listenerRegistration = matchCollection
            .whereEqualTo("status", MatchStatus.ACTIVE)
            .whereArrayContains("dogIds", dogId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val matches = snapshot.toObjects(Match::class.java)
                    trySendBlocking(matches)

                    launch {
                        matches.forEach { matchDao.insertMatch(it) }
                    }
                }
            }

        awaitClose { listenerRegistration.remove() }
    }

    // Handle match responses
    suspend fun respondToMatch(matchId: String, accepted: Boolean) {
        try {
            ensureAuthenticated()
            val match = matchCollection.document(matchId).get().await().toObject(Match::class.java)
                ?: throw IllegalStateException("Match not found")

            val newStatus = if (accepted) MatchStatus.ACTIVE else MatchStatus.DECLINED

            // Update Firestore
            matchCollection.document(matchId)
                .update(
                    mapOf(
                        "status" to newStatus,
                        "lastInteractionTimestamp" to System.currentTimeMillis()
                    )
                )
                .await()

            // Handle match acceptance side effects
            when {
                accepted -> {
                    createChatRoomForMatch(match)
                    sendMatchNotifications(match)
                }
            }

            // Update local database after successful online operation
            val localMatch = matchDao.getMatchById(matchId)
            if (localMatch != null) {
                matchDao.updateMatch(localMatch.copy(status = newStatus))
            }

        } catch (e: Exception) {
            Log.e("DataManager", "Error responding to match", e)
            throw e
        }
    }

    private fun isWithinMatchingDistance(dog1: Dog, dog2: Dog): Boolean {
        return try {
            val lat1 = dog1.latitude ?: return false
            val lon1 = dog1.longitude ?: return false
            val lat2 = dog2.latitude ?: return false
            val lon2 = dog2.longitude ?: return false

            val distance = locationService.calculateDistance(lat1, lon1, lat2, lon2)
            distance <= MatchingService.MAX_DISTANCE

        } catch (e: Exception) {
            Log.e("DataManager", "Error calculating match distance", e)
            false
        }
    }




    private suspend fun getUserFromFirestore(userId: String): User? {
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            if (snapshot.exists()) {
                snapshot.toObject(User::class.java)
            } else {
                null
            }
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
            try {
                userDao.insertUser(user)
                Log.d("DataManager", "User saved to local database: ${user.id}")
            } catch (e: Exception) {
                Log.e("DataManager", "Error saving user to local database", e)
            }
        }
    }

    private suspend fun saveDogToLocalDatabase(dog: Dog) {
        withContext(Dispatchers.IO) {
            try {
                // First check if user exists
                val user = userDao.getUserById(dog.ownerId) ?: run {
                    // If not, fetch and save user from Firestore
                    val firestoreUser = getUserFromFirestore(dog.ownerId)
                        ?: return@withContext // Skip if user not found
                    userDao.insertUser(firestoreUser)
                    firestoreUser
                }

                // Now safe to insert dog
                dogDao.insertDog(dog)
            } catch (e: Exception) {
                Log.e("DataManager", "Error saving dog", e)
            }
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


    private suspend fun syncExistingUserDogs(existingUser: User) {
        val existingDogs = firestore.collection("dogs")
            .whereEqualTo("ownerId", existingUser.id)
            .get()
            .await()
            .toObjects(Dog::class.java)

        existingDogs.forEach { dog ->
            dogDao.insertDog(dog)
        }
        Log.d("UserRepository", "Existing user synced: ${existingUser.id}")
    }
    private suspend fun createInitialDogProfile(userId: String, petName: String, user: User) {
        val dogId = UUID.randomUUID().toString()
        val dog = Dog(
            id = dogId,
            ownerId = userId,
            name = petName,
            // Add other required Dog fields with default values
        )

        // Save dog to Firestore
        firestore.collection("dogs")
            .document(dog.id)
            .set(dog)
            .await()

        // Save dog locally
        dogDao.insertDog(dog)

        // Update user's dogIds
        val updatedUser = user.copy(dogIds = listOf(dogId))
        firestore.collection("users")
            .document(userId)
            .set(updatedUser)
            .await()

        userDao.updateUser(updatedUser)
    }


    private suspend fun createChatRoomForMatch(match: Match) {
        try {
            // Get dog profiles for both users
            val dog1 = dogDao.getDogById(match.dog1Id)
            val dog2 = dogDao.getDogById(match.dog2Id)

            if (dog1 == null || dog2 == null) {
                Log.e("DataManager", "Unable to find dogs for chat room creation")
                return
            }

            // Create new chat
            val chat = Chat(
                id = UUID.randomUUID().toString(),
                user1Id = match.user1Id,
                user2Id = match.user2Id,
                dog1Id = match.dog1Id,
                dog2Id = match.dog2Id,
                matchId = match.id,
                participants = listOf(match.user1Id, match.user2Id),
                created = System.currentTimeMillis()
            )

            // Save chat to Firestore
            firestore.collection("chats")
                .document(chat.id)
                .set(chat)
                .await()

            // Update match with chat reference
            firestore.collection("matches")
                .document(match.id)
                .update("chatId", chat.id)
                .await()

            // Save to local database
            appDatabase.chatDao().insertChat(chat)

            // Send welcome message
            sendMessage(
                chatId = chat.id,
                content = generateWelcomeMessage(match, dog1, dog2),
                type = MessageType.SYSTEM,
                metadata = mapOf(
                    "matchId" to match.id,
                    "compatibilityScore" to match.compatibilityScore.toString()
                )
            )

            Log.d("DataManager", "Chat room created for match: ${match.id}")
        } catch (e: Exception) {
            Log.e("DataManager", "Error creating chat room for match", e)
        }
    }

    private fun generateWelcomeMessage(match: Match, dog1: Dog, dog2: Dog): String {
        val matchReasons = match.matchReasons.take(3).joinToString(", ")
        return """
        🎉 It's a match! ${dog1.name} and ${dog2.name} seem perfect for a playdate!
        
        Compatibility Score: ${(match.compatibilityScore * 100).toInt()}%
        Top Match Reasons: $matchReasons
        
        Start chatting to plan your first playdate! 🐾
    """.trimIndent()
    }

    private suspend fun sendMatchNotifications(match: Match) {
        try {
            val dog1 = dogDao.getDogById(match.dog1Id)
            val dog2 = dogDao.getDogById(match.dog2Id)

            if (dog1 == null || dog2 == null) {
                Log.e("DataManager", "Unable to find dogs for notifications")
                return
            }

            // Create notification data
            val notification = hashMapOf(
                "type" to "NEW_MATCH",
                "matchId" to match.id,
                "timestamp" to System.currentTimeMillis(),
                "dog1" to mapOf(
                    "id" to dog1.id,
                    "name" to dog1.name,
                    "ownerId" to dog1.ownerId
                ),
                "dog2" to mapOf(
                    "id" to dog2.id,
                    "name" to dog2.name,
                    "ownerId" to dog2.ownerId
                ),
                "compatibilityScore" to match.compatibilityScore
            )

            // Save notification to Firestore
            firestore.collection("notifications")
                .add(notification)
                .await()

            // Send FCM notifications to both users
            val user1Notification = hashMapOf(
                "to" to "/topics/user_${match.user1Id}",
                "notification" to mapOf(
                    "title" to "New Match! 🎉",
                    "body" to "Your dog matched with ${dog2.name}!"
                ),
                "data" to mapOf(
                    "type" to "match",
                    "matchId" to match.id
                )
            )

            val user2Notification = hashMapOf(
                "to" to "/topics/user_${match.user2Id}",
                "notification" to mapOf(
                    "title" to "New Match! 🎉",
                    "body" to "Your dog matched with ${dog1.name}!"
                ),
                "data" to mapOf(
                    "type" to "match",
                    "matchId" to match.id
                )
            )

            // Send notifications using Firebase Cloud Messaging
            // This should be handled by your notification service

            Log.d("DataManager", "Match notifications sent for match: ${match.id}")
        } catch (e: Exception) {
            Log.e("DataManager", "Error sending match notifications", e)
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
        withContext(Dispatchers.IO) {
            try {
                val currentUser = auth.currentUser ?: run {
                    delay(500) // Give Firebase time to initialize
                    auth.currentUser
                } ?: throw IllegalStateException("No authenticated user")

                val dogRef = if (dog.id.isBlank()) {
                    firestore.collection("dogs").document()
                        .also { dog.id = it.id }
                } else {
                    firestore.collection("dogs").document(dog.id)
                }

                // Save to Firestore first
                dogRef.set(dog).await()

                // Then use the safe transaction for local DB
                try {
                    appDatabase.dogDao().insertDogWithOwnerCheck(dog, appDatabase.userDao())
                } catch (e: Exception) {
                    Log.e("DataManager", "Local DB save failed - continuing", e)
                    // Don't throw - Firestore is source of truth
                }

                Log.d("DataManager", "Dog profile created/updated: ${dog.id}")
            } catch (e: Exception) {
                Log.e("DataManager", "Error creating/updating dog profile", e)
                throw e
            }
        }
    }

    suspend fun updateDogProfilePicture(index: Int, uri: Uri, dogId: String): String {
        return try {
            val currentUser = auth.currentUser ?: throw IllegalStateException("User not authenticated")
            val imageRef = storage.reference
                .child("dog_images")
                .child(currentUser.uid)
                .child(dogId)
                .child("photo_${index}_${System.currentTimeMillis()}.jpg")

            val uploadTask = imageRef.putFile(uri).await()
            val downloadUrl = uploadTask.storage.downloadUrl.await().toString()

            downloadUrl
        } catch (e: Exception) {
            Log.e("DataManager", "Error updating dog profile picture", e)
            throw e
        }
    }
    // Upload chat media with progress tracking
    suspend fun uploadChatMedia(chatId: String, uri: Uri): String {
        try {
            ensureAuthenticated()
            val currentUser = auth.currentUser!!

            // Compress image if needed
            val compressedUri = compressChatImage(uri)

            // Create storage reference with organized path
            val fileName = "chat_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"
            val mediaRef = storage.reference
                .child(CHAT_MEDIA_PATH)
                .child(chatId)
                .child(fileName)

            // Upload with progress tracking
            val uploadTask = mediaRef.putFile(compressedUri)

            uploadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                _uploadProgress.value = progress.toFloat() / 100f
            }

            val uploadResult = uploadTask.await()
            return uploadResult.storage.downloadUrl.await().toString()

        } catch (e: Exception) {
            Log.e("DataManager", "Error uploading chat media", e)
            throw e
        } finally {
            _uploadProgress.value = 0f
        }
    }
    suspend fun compressChatImage(uri: Uri): Uri {
        return withContext(Dispatchers.IO) {
            photoRepository.compressImage(uri, CHAT_IMAGE_MAX_DIMENSION)
        }
    }
    private fun calculateScaleFactor(width: Int, height: Int, maxDimension: Int): Int {
        var scale = 1
        while ((width / scale) > maxDimension || (height / scale) > maxDimension) {
            scale *= 2
        }
        return scale
    }

    // Delete chat media
    suspend fun deleteChatMedia(chatId: String, mediaUrl: String) {
        try {
            val storageRef = storage.getReferenceFromUrl(mediaUrl)
            storageRef.delete().await()
        } catch (e: Exception) {
            Log.e("DataManager", "Error deleting chat media", e)
            throw e
        }
    }
    suspend fun saveQuestionnaireResponses(userId: String, dogId: String, responses: Map<String, String>) {
        try {
            val documentId = "${userId}_${dogId}"
            // First save raw responses to questionnaires collection
            firestore.collection("questionnaires")
                .document(documentId)
                .set(responses)
                .await()

            Log.d("DataManager", "Questionnaire responses saved: $dogId")

            val dogRef = firestore.collection("dogs").document(dogId)
            val dogSnapshot = dogRef.get().await()

            if (!dogSnapshot.exists()) return

            val updateMap = mutableMapOf<String, Any>()

            // Handle regular string fields
            val stringFields = listOf(
                "energyLevel", "friendliness", "trainability", "friendlyWithDogs",
                "friendlyWithChildren", "friendlyWithStrangers", "exerciseNeeds",
                "groomingNeeds", "specialNeeds"
            )

            stringFields.forEach { field ->
                responses[field]?.let { updateMap[field] = it }
            }

            // Handle isSpayedNeutered separately - store as Boolean in Firestore
            responses["isSpayedNeutered"]?.let { value ->
                // Convert string to Boolean before storing
                val booleanValue = when (value.toLowerCase()) {
                    "true", "yes", "1" -> true
                    "false", "no", "0" -> false
                    else -> null  // Handle invalid values
                }
                // Only update if we got a valid boolean value
                booleanValue?.let {
                    updateMap["isSpayedNeutered"] = it
                }
            }

            if (updateMap.isNotEmpty()) {
                // Update Firestore
                dogRef.update(updateMap).await()

                // Update local database
                val existingDog = dogSnapshot.toObject(Dog::class.java)
                existingDog?.let { dog ->
                    val updatedDog = dog.copy(
                        energyLevel = responses["energyLevel"] ?: dog.energyLevel,
                        friendliness = responses["friendliness"] ?: dog.friendliness,
                        trainability = responses["trainability"] ?: dog.trainability,
                        friendlyWithDogs = responses["friendlyWithDogs"] ?: dog.friendlyWithDogs,
                        friendlyWithChildren = responses["friendlyWithChildren"] ?: dog.friendlyWithChildren,
                        friendlyWithStrangers = responses["friendlyWithStrangers"] ?: dog.friendlyWithStrangers,
                        exerciseNeeds = responses["exerciseNeeds"] ?: dog.exerciseNeeds,
                        groomingNeeds = responses["groomingNeeds"] ?: dog.groomingNeeds,
                        specialNeeds = responses["specialNeeds"] ?: dog.specialNeeds,
                        // Convert to Boolean properly
                        isSpayedNeutered = responses["isSpayedNeutered"]?.let { value ->
                            when (value.toLowerCase()) {
                                "true", "yes", "1" -> true
                                "false", "no", "0" -> false
                                else -> dog.isSpayedNeutered  // Keep existing value if invalid
                            }
                        } ?: dog.isSpayedNeutered  // Keep existing value if not provided
                    )
                    dogDao.insertDog(updatedDog)
                    Log.d("DataManager", "Dog profile updated: $dogId")
                }
            }
        } catch (e: Exception) {
            Log.e("DataManager", "Error saving questionnaire", e)
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
                        launch {
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
                        launch {
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

                    launch {
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

    fun getOutputFileUri(): Uri {
        return photoRepository.getOutputFileUri(context)
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

    suspend fun cleanupOnSignOut() = withContext(Dispatchers.IO) {
        try {
            when {
                _isSyncing.value -> delay(1000)
            }

            currentSyncJob?.cancel()
            syncJob?.cancel()

            when (auth.currentUser) {
                null -> {
                    appDatabase.clearAllTables()
                    context.getSharedPreferences("YourAppPrefs", Context.MODE_PRIVATE)
                        .edit()
                        .clear()
                        .apply()
                }
                else -> { /* No cleanup needed when user exists */ }
            }
        } catch (e: Exception) {
            Log.e("DataManager", "Error during cleanup", e)
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun updatePlaydateMood(playdateId: String, mood: PlaydateMood) {
        firestore.collection("playdates")
            .document(playdateId)
            .update("mood", mood.name)
            .await()
    }
    suspend fun updatePlaydate(playdate: Playdate) {
        withContext(Dispatchers.IO) {
            firestore.collection("playdates")
                .document(playdate.id)
                .set(playdate)
                .await()
        }
    }

    suspend fun updateCalendarEvent(playdate: Playdate) {
        withContext(Dispatchers.IO) {
            calendarService.updatePlaydateEvent(  // Change to use a known method
                playdate.id,
                "Playdate",
                "Playdate at ${playdate.location}",
                playdate.scheduledTime,
                playdate.scheduledTime + ONE_HOUR_IN_MILLIS,
                playdate.location,
                listOf(playdate.dog1Id, playdate.dog2Id),
                playdate.status
            )
        }
    }


    suspend fun cleanup() {
        withContext(Dispatchers.IO) {
            stopPeriodicSync()
            FirebaseAuth.getInstance().signOut()
            auth.signOut()
            appDatabase.clearAllTables()
            context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }



    class AuthenticationException(message: String) : Exception(message)
}


