package io.pawsomepals.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
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
import io.pawsomepals.app.data.repository.QuestionRepository
import io.pawsomepals.app.service.CalendarService
import io.pawsomepals.app.service.MatchingService
import io.pawsomepals.app.service.location.LocationService
import io.pawsomepals.app.utils.ImageHandler
import io.pawsomepals.app.utils.NetworkUtils
import io.pawsomepals.app.viewmodel.AuthViewModel
import com.google.android.datatransport.runtime.dagger.Provides
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException


@Singleton
class DataManager @Inject constructor(
    private val appDatabase: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    @ApplicationContext private val context: Context,
    private val imageHandler: ImageHandler,
    private val networkUtils: NetworkUtils,
    private val locationService: LocationService,  // Add this
    private val matchingService: MatchingService,
    private val questionRepository: QuestionRepository,
    private val calendarService: CalendarService,
    private val googleAuthManager: GoogleAuthManager



) {
    private val calendarDao = appDatabase.playdateCalendarDao()  // Add this
    private val achievementDao = appDatabase.achievementDao()  // Add this line

    private val userDao = appDatabase.userDao()
    private val dogDao = appDatabase.dogDao()
    private val matchDao = appDatabase.matchDao()
    private val messageDao = appDatabase.messageDao()
    private val swipeDao = appDatabase.swipeDao()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Add these new properties
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncAttempts = 0
    private var currentSyncJob: Job? = null
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

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

    // Auth state management
    private val _authState = MutableStateFlow<AuthViewModel.AuthState>(AuthViewModel.AuthState.Initial)
    val authState = _authState.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float>(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    // Constants for chat media
    private val CHAT_MEDIA_MAX_SIZE = 5 * 1024 * 1024 // 5MB
    private val CHAT_IMAGE_MAX_DIMENSION = 1280 // pixels
    private val CHAT_MEDIA_PATH = "chat_media"

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

                    // Update local database
                    coroutineScope.launch {
                        messages.forEach {
                            appDatabase.messageDao().insertMessage(it)
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

                    // Update local database
                    coroutineScope.launch {
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

    private fun startPeriodicSync() {
        Log.d("DataManager", "Starting periodic sync")
        stopPeriodicSync()

        currentSyncJob = scope.launch {
            while (isActive) {
                try {
                    _isSyncing.value = true
                    syncWithFirestore()
                    delay(5 * 60 * 1000) // 5 minutes delay
                } catch (e: CancellationException) {
                    Log.d("DataManager", "Sync job cancelled normally")
                    break
                } catch (e: Exception) {
                    Log.e("DataManager", "Error during periodic sync", e)
                    delay(calculateBackoffDelay())
                } finally {
                    _isSyncing.value = false
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
                cleanupOnSignOut()
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

    suspend fun syncWithFirestore() {
        withContext(Dispatchers.IO) {
            try {
                val currentUser = auth.currentUser ?: throw IllegalStateException("No authenticated user")

                // Add retry logic for initial sync
                var attempts = 0
                val maxAttempts = 3

                while (attempts < maxAttempts) {
                    try {
                        val userData = getUserFromFirestore(currentUser.uid)
                        if (userData != null) {
                            saveUserToLocalDatabase(userData)
                            Log.d("DataManager", "User data synced successfully")
                            break
                        }
                        delay(1000L * (attempts + 1)) // Exponential backoff
                        attempts++
                    } catch (e: Exception) {
                        Log.e("DataManager", "Sync attempt ${attempts + 1} failed", e)
                        if (attempts == maxAttempts - 1) throw e
                        attempts++
                        delay(1000L * attempts)
                    }
                }

                // Only proceed with dog sync if user sync succeeded
                val dogData = getDogFromFirestore(currentUser.uid)
                if (dogData != null) {
                    saveDogToLocalDatabase(dogData)
                    Log.d("DataManager", "Dog data synced successfully")
                } else {
                    Log.d("DataManager", "No dog data found to sync")
                }

            } catch (e: Exception) {
                Log.e("DataManager", "Error in syncWithFirebase", e)
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
            val currentDog = dogCollection.document(dogId).get().await().toObject(Dog::class.java)
                ?: throw IllegalStateException("Current dog not found")

            // Get already swiped profiles
            val swipedProfiles = swipeCollection
                .whereEqualTo("swiperId", currentDog.ownerId)
                .get()
                .await()
                .toObjects(Swipe::class.java)
                .map { it.swipedDogId }

            // Query for potential matches
            val potentialMatches = dogCollection
                .whereNotEqualTo("id", dogId)
                .whereNotEqualTo("ownerId", currentDog.ownerId)
                .get()
                .await()
                .toObjects(Dog::class.java)
                .filter { dog ->
                    // Filter out already swiped profiles
                    !swipedProfiles.contains(dog.id) &&
                            // Filter by location if available
                            isWithinMatchingDistance(currentDog, dog)
                }
                .take(limit)

            return potentialMatches

        } catch (e: Exception) {
            Log.e("DataManager", "Error getting online potential matches", e)
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
                    // Update local database
                    coroutineScope.launch {
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

            // If accepted, create chat and send notifications
            if (accepted) {
                createChatRoomForMatch(match)
                sendMatchNotifications(match)
            }

            // Update local database after successful online operation
            matchDao.getMatchById(matchId)?.let { localMatch ->
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

    private suspend fun createChatRoomForMatch(match: Match) {
        // Implement chat room creation logic here
    }

    private suspend fun sendMatchNotifications(match: Match) {
        // Implement notification logic here
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
            try {
                // First ensure the user exists
                val user = userDao.getUserById(dog.ownerId)
                if (user == null) {
                    // If user doesn't exist, fetch from Firestore and save
                    val firestoreUser = getUserFromFirestore(dog.ownerId)
                    if (firestoreUser != null) {
                        userDao.insertUser(firestoreUser)
                    } else {
                        throw IllegalStateException("Cannot save dog: Owner not found in Firestore")
                    }
                }

                // Now safe to save the dog
                dogDao.insertDog(dog)
                Log.d("DataManager", "Successfully saved dog to local database: ${dog.id}")
            } catch (e: Exception) {
                Log.e("DataManager", "Error saving dog to local database", e)
                throw e
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

    @Provides
    @Singleton
    fun provideDataManager(
        appDatabase: AppDatabase,
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
        storage: FirebaseStorage,
        @ApplicationContext context: Context,
        questionRepository: QuestionRepository,
        imageHandler: ImageHandler,
        networkUtils: NetworkUtils,
        locationService: LocationService,
        matchingService: MatchingService,
        calendarService: CalendarService,    // Added
        googleAuthManager: GoogleAuthManager  // Added
    ): DataManager {
        return DataManager(
            appDatabase = appDatabase,
            firestore = firestore,
            auth = auth,
            storage = storage,
            context = context,
            questionRepository = questionRepository,
            imageHandler = imageHandler,
            networkUtils = networkUtils,
            locationService = locationService,
            matchingService = matchingService,
            calendarService = calendarService,    // Added
            googleAuthManager = googleAuthManager  // Added
        )
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
            Log.d("DataManager", "Initial dog data: id=${dog.id}, ownerId=${dog.ownerId}, name=${dog.name}")

            // Try to get current user with retry mechanism
            var currentUser = auth.currentUser
            var attempts = 0
            val maxAttempts = 3

            while (currentUser == null && attempts < maxAttempts) {
                delay(1000L * (attempts + 1))
                currentUser = auth.currentUser
                attempts++
                Log.d("DataManager", "Auth attempt $attempts, user: ${currentUser?.uid}")
            }

            // If still no user, try to directly verify from Firestore
            if (currentUser == null) {
                val userDoc = firestore.collection("users")
                    .document(dog.ownerId)
                    .get()
                    .await()

                if (!userDoc.exists()) {
                    Log.e("DataManager", "Neither auth user nor Firestore user found")
                    throw IllegalStateException("No authenticated user found")
                }

                Log.d("DataManager", "User verified through Firestore: ${dog.ownerId}")
            } else {
                Log.d("DataManager", "User verified through Firebase Auth: ${currentUser.uid}")
            }

            // At this point, we've verified the user exists either through Auth or Firestore
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

            // Save to Firestore with retry mechanism
            var saveAttempts = 0
            val maxSaveAttempts = 3
            var lastError: Exception? = null

            while (saveAttempts < maxSaveAttempts) {
                try {
                    dogRef.set(dogToSave).await()
                    Log.d("DataManager", "Successfully saved dog to Firestore: ${dogToSave.id}")
                    break
                } catch (e: Exception) {
                    lastError = e
                    saveAttempts++
                    if (saveAttempts == maxSaveAttempts) {
                        Log.e("DataManager", "Failed all attempts to save to Firestore")
                        throw e
                    }
                    delay(1000L * saveAttempts)
                }
            }

            // Save to local database
            try {
                dogDao.insertDog(dogToSave)
                Log.d("DataManager", "Successfully saved dog to local database: ${dogToSave.id}")
            } catch (e: Exception) {
                Log.e("DataManager", "Local database save failed for dog ${dogToSave.id}", e)
                // Don't throw here - we've already saved to Firestore
            }

            Log.d("DataManager", "Dog profile created/updated successfully")

        } catch (e: Exception) {
            Log.e("DataManager", "Critical error in createOrUpdateDogProfile", e)
            Log.e("DataManager", "Error details - Dog: id=${dog.id}, name=${dog.name}")
            throw e
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

            val dog = dogDao.getDogById(dogId) ?: throw IllegalStateException("Dog not found")
            val updatedPhotoUrls = dog.photoUrls.toMutableList()

            // If it's the first photo (index 0), clear the list and start fresh
            if (index == 0) {
                updatedPhotoUrls.clear()
                updatedPhotoUrls.add(downloadUrl)
            } else {
                // For additional photos, add or update at the specific index
                if (index < updatedPhotoUrls.size) {
                    updatedPhotoUrls[index] = downloadUrl
                } else {
                    updatedPhotoUrls.add(downloadUrl)
                }
            }

            val updatedDog = dog.copy(photoUrls = updatedPhotoUrls)
            createOrUpdateDogProfile(updatedDog)

            Log.d("DataManager", "Updated dog photo URLs: $updatedPhotoUrls")
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
    private suspend fun compressChatImage(uri: Uri): Uri {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()

                val scale = calculateScaleFactor(
                    options.outWidth,
                    options.outHeight,
                    CHAT_IMAGE_MAX_DIMENSION
                )

                val compressedOptions = BitmapFactory.Options().apply {
                    inSampleSize = scale
                }

                val compressedBitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, compressedOptions)
                } ?: throw IllegalStateException("Could not decode image")

                val tempFile = File(
                    context.cacheDir,
                    "compressed_chat_${System.currentTimeMillis()}.jpg"
                )

                FileOutputStream(tempFile.absolutePath).use { out ->
                    compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }

                compressedBitmap.recycle()

                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )
            } catch (e: Exception) {
                Log.e("DataManager", "Error compressing chat image", e)
                throw e
            }
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
                // Ensure the file is deleted when the io.pawsomepals.app closes
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

    private fun cleanupOnSignOut() {
        val cleanupJob = scope.launch {
            try {
                // First check if there are any pending operations
                if (_isSyncing.value) {
                    Log.d("DataManager", "Waiting for sync to complete before cleanup")
                    delay(1000) // Give sync a chance to complete
                }

                // Cancel ongoing operations
                currentSyncJob?.cancel()
                syncJob?.cancel()

                // Only clear data if we're actually signed out
                if (auth.currentUser == null) {
                    withContext(Dispatchers.IO) {
                        appDatabase.clearAllTables()
                        context.getSharedPreferences("YourAppPrefs", Context.MODE_PRIVATE)
                            .edit()
                            .clear()
                            .apply()
                    }
                    Log.d("DataManager", "Local data cleared during cleanup")
                } else {
                    Log.d("DataManager", "Skipping data clear as user is still authenticated")
                }

                Log.d("DataManager", "Cleanup completed successfully")
            } catch (e: CancellationException) {
                Log.d("DataManager", "Cleanup cancelled normally")
            } catch (e: Exception) {
                Log.e("DataManager", "Error during cleanup", e)
            } finally {
                _isSyncing.value = false
            }
        }

        // Make sure cleanup job completes or times out
        scope.launch {
            try {
                withTimeout(5000) { // 5 second timeout
                    cleanupJob.join()
                }
            } catch (e: Exception) {
                Log.w("DataManager", "Cleanup job timed out or failed", e)
                cleanupJob.cancel()
            }
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

    fun cleanup() {
        Log.d("DataManager", "Cleaning up DataManager resources")
        scope.launch {
            try {
                stopPeriodicSync()
                currentSyncJob?.cancel()

                // Cancel all coroutine jobs
                scope.coroutineContext.cancelChildren()
                coroutineScope.coroutineContext.cancelChildren()

                // Finally cancel the scopes themselves
                scope.cancel()
                coroutineScope.cancel()

                Log.d("DataManager", "Resources cleaned up successfully")
            } catch (e: Exception) {
                Log.e("DataManager", "Error during cleanup", e)
            }
        }
    }




    class AuthenticationException(message: String) : Exception(message)
}


