package io.pawsomepals.app.viewmodel


import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pawsomepals.app.data.DataManager
import io.pawsomepals.app.data.model.Chat
import io.pawsomepals.app.data.model.CompatibilityPrompt
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.data.model.MatchReason
import io.pawsomepals.app.data.model.Message
import io.pawsomepals.app.data.model.MessageType
import io.pawsomepals.app.data.model.Notification
import io.pawsomepals.app.data.model.NotificationType
import io.pawsomepals.app.data.model.Playdate
import io.pawsomepals.app.data.model.PlaydateMood
import io.pawsomepals.app.data.model.PlaydateStatus
import io.pawsomepals.app.data.model.PromptType
import io.pawsomepals.app.data.model.SafetyChecklist
import io.pawsomepals.app.data.model.User
import io.pawsomepals.app.data.preferences.UserPreferences
import io.pawsomepals.app.data.repository.ChatRepository
import io.pawsomepals.app.data.repository.MatchRepository
import io.pawsomepals.app.data.repository.NotificationRepository
import io.pawsomepals.app.data.repository.UserRepository
import io.pawsomepals.app.service.AchievementService
import io.pawsomepals.app.service.AnalyticsService
import io.pawsomepals.app.service.CalendarService
import io.pawsomepals.app.utils.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject


@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val dataManager: DataManager,
    private val userPreferences: UserPreferences,
    private val calendarService: CalendarService,
    private val notificationRepository: NotificationRepository,
    private val achievementService: AchievementService,
    private val analyticsService: AnalyticsService,
    private val auth: FirebaseAuth,
    private val matchRepository: MatchRepository,
    private val firestore: FirebaseFirestore, // Add this

// Add this

) : ViewModel() {

    // UI State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _showLocationSearch = MutableStateFlow(false)
    val showLocationSearch = _showLocationSearch.asStateFlow()

    fun toggleLocationSearch() {
        _showLocationSearch.value = !_showLocationSearch.value
    }
    private val _matchId = MutableStateFlow<String?>(null)
    val matchId: StateFlow<String?> = _matchId.asStateFlow()

    private val _selectedMedia = MutableStateFlow<Uri?>(null)
    val selectedMedia: StateFlow<Uri?> = _selectedMedia.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // User & Auth State
    private val _isUserLoggedIn = MutableStateFlow(false)
    val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn

    fun getCurrentUserIdOrNull(): String? = auth.currentUser?.uid


    // Change these lines in ChatViewModel
    private val _otherUserData = MutableStateFlow<User?>(null)
    val otherUserData: StateFlow<User?> = _otherUserData

    // Chat Data
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _typingStatus = MutableStateFlow(false)
    val typingStatus: StateFlow<Boolean> = _typingStatus

    // Media Handling


    private val _isMediaUploading = MutableStateFlow(false)
    val isMediaUploading: StateFlow<Boolean> = _isMediaUploading

    val uploadProgress: StateFlow<Float> = dataManager.uploadProgress

    // Playdate Data
    private val _todaysPlaydates = MutableStateFlow<List<Playdate>>(emptyList())
    val todaysPlaydates: StateFlow<List<Playdate>> = _todaysPlaydates

    private val _chatsWithDetails = MutableStateFlow<List<Chat.ChatWithDetails>>(emptyList())
    val chatsWithDetails: StateFlow<List<Chat.ChatWithDetails>> = _chatsWithDetails.asStateFlow()

    private val _isLoadingChats = MutableStateFlow(false)
    val isLoadingChats: StateFlow<Boolean> = _isLoadingChats.asStateFlow()

    // Safety & Compatibility
    private val _isFirstMeeting = MutableStateFlow(true)
    val isFirstMeeting: StateFlow<Boolean> = _isFirstMeeting

    private val _safetyChecklist = MutableStateFlow(SafetyChecklist())
    val safetyChecklist: StateFlow<SafetyChecklist> = _safetyChecklist

    private val _compatibilityPrompts = MutableStateFlow<List<CompatibilityPrompt>>(emptyList())
    val compatibilityPrompts: StateFlow<List<CompatibilityPrompt>> = _compatibilityPrompts

    var currentChatId: String? = null

    init {
        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Initializing ChatViewModel")

                // Set initial state
                _isUserLoggedIn.value = auth.currentUser != null
                Log.d("ChatViewModel", "Initial auth state: ${auth.currentUser != null}")

                // Load chats if user is logged in
                if (_isUserLoggedIn.value) {
                    Log.d("ChatViewModel", "Loading chats for user: ${auth.currentUser?.uid}")
                    loadChats()
                }

                // Set up continuous auth state listener
                auth.addAuthStateListener { firebaseAuth ->
                    viewModelScope.launch {
                        val isLoggedIn = firebaseAuth.currentUser != null
                        Log.d("ChatViewModel", "Auth state changed. isLoggedIn: $isLoggedIn")
                        _isUserLoggedIn.value = isLoggedIn

                        if (isLoggedIn) {
                            loadChats()
                        } else {
                            _chats.value = emptyList()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error initializing ChatViewModel", e)
                handleError("Error initializing chat", e)
            }
        }
    }


    private fun generatePromptFromReason(reason: MatchReason): String {
        return when(reason) {
            MatchReason.ENERGY_LEVEL_MATCH -> "Would you like to discuss your dogs' favorite activities?"
            MatchReason.SIZE_COMPATIBILITY -> "What kind of play style does your dog prefer?"
            MatchReason.BREED_COMPATIBILITY -> "Do you have any breed-specific tips to share?"
            MatchReason.PLAY_STYLE_MATCH -> "What are your dog's favorite games and toys?"
            MatchReason.TRAINING_LEVEL_MATCH -> "How was your dog trained? Any tips to share?"
            MatchReason.TEMPERAMENT_MATCH -> "How does your dog usually interact with new friends?"
            MatchReason.SOCIAL_COMPATIBILITY -> "What social situations does your dog enjoy most?"
            MatchReason.HEALTH_COMPATIBILITY -> "How do you maintain your dog's health and wellness?"
            MatchReason.AGE_COMPATIBILITY -> "What activities suit your dog's age and energy?"
            MatchReason.LOCATION_PROXIMITY -> "Do you have any favorite local dog parks?"
        }
    }


    // Chat List Management
    private fun loadChats() {
        viewModelScope.launch {
            try {
                _isLoadingChats.value = true
                Log.d("ChatViewModel", "Starting to load chats")

                chatRepository.getAllChats().collect { chatsList ->
                    Log.d("ChatViewModel", """
                    Received chats from repository:
                    - Count: ${chatsList.size}
                    - Chat IDs: ${chatsList.map { it.id }}
                """.trimIndent())

                    val chatsWithDetails = chatsList.mapNotNull { chat ->
                        try {
                            val currentUserId = getCurrentUserIdOrNull()
                            Log.d("ChatViewModel", "Processing chat ${chat.id} with current user: $currentUserId")

                            if (currentUserId == null) {
                                Log.e("ChatViewModel", "Current user ID is null")
                                return@mapNotNull null
                            }

                            // Get match details
                            val match = matchRepository.getMatchById(chat.matchId).getOrNull()
                            Log.d("ChatViewModel", """
                            Match details for chat ${chat.id}:
                            - Match ID: ${match?.id}
                            - Match found: ${match != null}
                        """.trimIndent())

                            if (match == null) {
                                Log.e("ChatViewModel", "Match not found for chat ${chat.id}")
                                return@mapNotNull null
                            }

                            // Get other user ID
                            val otherUserId = if (match.user1Id == currentUserId) match.user2Id else match.user1Id
                            Log.d("ChatViewModel", "Other user ID determined: $otherUserId")

                            // Get other user info
                            val otherUser = userRepository.getUserById(otherUserId)
                            Log.d("ChatViewModel", "Other user found: ${otherUser != null}")

                            if (otherUser == null) {
                                Log.e("ChatViewModel", "Other user not found: $otherUserId")
                                return@mapNotNull null
                            }

                            // Get dog profile
                            val otherDog = userRepository.getDogProfileByOwnerId(otherUserId)
                            Log.d("ChatViewModel", """
                            Dog profile for user $otherUserId:
                            - Found: ${otherDog != null}
                            - Dog name: ${otherDog?.name}
                        """.trimIndent())

                            if (otherDog == null) {
                                Log.e("ChatViewModel", "Dog not found for user: $otherUserId")
                                return@mapNotNull null
                            }

                            Log.d("ChatViewModel", """
                            Successfully processed chat ${chat.id}:
                            - Other dog name: ${otherDog.name}
                            - Owner ID: ${otherDog.ownerId}
                        """.trimIndent())

                            Chat.ChatWithDetails(
                                chat = chat,
                                otherDogPhotoUrl = otherDog.profilePictureUrl,
                                otherDogName = otherDog.name,
                                isNewMatch = match.chatCreatedAt?.let { createdAt ->
                                    createdAt > System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                                } ?: false,
                                pendingPlaydate = chat.playdateStatus == Chat.PlaydateStatus.PENDING_CONFIRMATION
                            )
                        } catch (e: Exception) {
                            Log.e("ChatViewModel", "Error processing chat ${chat.id}", e)
                            e.printStackTrace()
                            null
                        }
                    }

                    _chatsWithDetails.value = chatsWithDetails
                    _isLoadingChats.value = false
                    Log.d("ChatViewModel", """
                    Chat loading completed:
                    - Initial chats: ${chatsList.size}
                    - Processed chats: ${chatsWithDetails.size}
                    - Failed processing: ${chatsList.size - chatsWithDetails.size}
                """.trimIndent())
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading chats", e)
                e.printStackTrace()
                handleError("Error loading chats", e)
                _isLoadingChats.value = false
            }
        }
    }

    fun getMatchIdForChat(chatId: String) {
        viewModelScope.launch {
            try {
                val chat = firestore.collection("chats")
                    .document(chatId)
                    .get()
                    .await()

                _matchId.value = chat.getString("matchId")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error getting match ID", e)
                _matchId.value = null
            }
        }
    }

    private suspend fun getChatsWithDetails(): List<Chat.ChatWithDetails> {
        return chats.value.map { chat ->
            val currentUserId = getCurrentUserIdOrNull() ?: return@map Chat.ChatWithDetails(chat)
            val otherDogId = chat.getOtherDogId(chat.dog1Id)
            val otherDog = userRepository.getDogProfileById(otherDogId)

            Chat.ChatWithDetails(
                chat = chat,
                otherDogPhotoUrl = otherDog?.profilePictureUrl,
                otherDogName = otherDog?.name ?: "Unknown Dog",
                isNewMatch = chat.created > System.currentTimeMillis() - (24 * 60 * 60 * 1000),
                pendingPlaydate = chat.playdateStatus == Chat.PlaydateStatus.PENDING_CONFIRMATION
            )
        }
    }

    fun sendLocationMessage(location: DogFriendlyLocation) {
        viewModelScope.launch {
            try {
                val currentUserId = userRepository.getCurrentUserId() ?: return@launch
                chatRepository.sendMessage(
                    chatId = currentChatId ?: return@launch,
                    senderId = currentUserId,
                    content = location.name,
                    type = MessageType.LOCATION,
                    metadata = mapOf(
                        "locationName" to location.name,
                        "address" to location.address,
                        "coordinates" to "${location.latitude},${location.longitude}",
                        "hasFencing" to location.hasFencing.toString(),
                        "isOffLeashAllowed" to location.isOffLeashAllowed.toString()
                    )
                )
                _showLocationSearch.value = false
            } catch (e: Exception) {
                handleError("Error sharing location", e)
            }
        }
    }

    fun getCurrentUserId(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
    }

    fun loadChatMessages(chatId: String) {
        currentChatId = chatId
        viewModelScope.launch {
            Log.d("ChatViewModel", "Starting to load messages for chatId: $chatId")
            try {
                chatRepository.getMessages(chatId).collect { messageList ->
                    Log.d("ChatViewModel", "Received ${messageList.size} messages for chat $chatId: $messageList")
                    _messages.value = messageList
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading messages", e)
            }
        }
    }
    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            try {
                chatRepository.deleteChat(chatId)
                // Chats will be automatically reloaded through the flow
            } catch (e: Exception) {
                handleError("Error deleting chat", e)
            }
        }
    }


    fun sendMessage(content: String, mediaUri: Uri? = null) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch
                val senderId = currentUser.uid
                val timestamp = System.currentTimeMillis()

                // Create message ID using same format as repository
                val messageId = "${chatId}_${timestamp}_${senderId}"

                // Create optimistic message
                val optimisticMessage = Message(
                    id = messageId,
                    chatId = chatId,
                    senderId = senderId,
                    senderName = currentUser.displayName ?: "Anonymous",
                    content = content,
                    timestamp = timestamp,
                    isFromCurrentUser = true
                )

                // Add optimistic message to UI immediately
                _messages.value = _messages.value + optimisticMessage

                if (mediaUri != null) {
                    sendMediaMessage(chatId, senderId, mediaUri)
                } else {
                    chatRepository.sendMessage(
                        chatId = chatId,
                        senderId = senderId,
                        content = content,
                        type = MessageType.TEXT
                    )
                }
            } catch (e: Exception) {
                // Remove optimistic message if send fails
                _messages.value = _messages.value.filterNot { it.id == "${chatId}_${System.currentTimeMillis()}_${auth.currentUser?.uid}" }
                handleError("Error sending message", e)
            }
        }
    }



    private fun promptTypeFromReason(reason: MatchReason): PromptType {
        return when(reason) {
            MatchReason.BREED_COMPATIBILITY -> PromptType.BREED_INFO
            MatchReason.TRAINING_LEVEL_MATCH -> PromptType.TRAINING_TIP
            MatchReason.HEALTH_COMPATIBILITY -> PromptType.HEALTH_TIP
            else -> PromptType.PLAY_STYLE
        }
    }

    private suspend fun sendMediaMessage(chatId: String, senderId: String, mediaUri: Uri) {
        _isMediaUploading.value = true
        try {
            val timestamp = System.currentTimeMillis()
            val messageId = "${chatId}_${timestamp}_${senderId}"

            val mediaUrl = dataManager.uploadChatMedia(chatId, mediaUri)
            chatRepository.sendMessage(
                chatId = chatId,
                senderId = senderId,
                content = mediaUrl,
                type = MessageType.IMAGE
            )
        } finally {
            _isMediaUploading.value = false
            clearSelectedMedia()
        }
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            try {
                if (message.type == MessageType.IMAGE) {
                    dataManager.deleteChatMedia(message.chatId, message.content)
                }
                chatRepository.deleteMessage(message.chatId, message.id)
            } catch (e: Exception) {
                handleError("Error deleting message", e)
            }
        }
    }

    // Playdate Management
    @RequiresApi(Build.VERSION_CODES.O)
    fun acceptPlaydate(playdate: Playdate) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val updatedPlaydate = playdate.copy(
                    status = PlaydateStatus.ACCEPTED,
                    metadata = playdate.metadata?.plus("acceptedAt" to System.currentTimeMillis())
                )

                updatePlaydateAndNotify(updatedPlaydate)
            } catch (e: Exception) {
                handleError("Failed to accept playdate", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun updatePlaydateAndNotify(playdate: Playdate) {
        // Update playdate
        dataManager.updatePlaydate(playdate)

        // Send message to chat
        val message = createPlaydateMessage(playdate)
        val currentUserId = userRepository.getCurrentUserId() ?: return
        chatRepository.sendMessage(
            chatId = playdate.chatId,
            senderId = currentUserId,
            content = message.content,
            type = MessageType.PLAYDATE_CONFIRMATION
        )

        // Send notification
        notificationRepository.sendPlaydateNotification(playdate)

        // Update calendar if enabled
        if (userPreferences.getCalendarSync()) {
            calendarService.addPlaydateEvent(playdate)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createPlaydateMessage(playdate: Playdate): Message {
        return Message(
            id = UUID.randomUUID().toString(),
            chatId = playdate.chatId,
            senderId = userRepository.getCurrentUserId() ?: "",
            content = generatePlaydateMessage(playdate),
            type = MessageType.PLAYDATE_CONFIRMATION,
            metadata = mapOf(
                "playdateId" to playdate.id,
                "status" to playdate.status.name
            ),
            timestamp = System.currentTimeMillis()
        )
    }

    // Safety & Compatibility Features
    private suspend fun sendSafetyConfirmation() {
        val currentUserId = userRepository.getCurrentUserId() ?: return
        val currentChatId = currentChatId ?: return

        chatRepository.sendMessage(
            chatId = currentChatId,
            senderId = currentUserId,
            content = "Safety checklist completed ✓",
            type = MessageType.SYSTEM
        )

        // Track the event
        analyticsService.trackCustomEvent(
            "safety_checklist_completed",
            mapOf("chatId" to currentChatId)
        )

        // Add a notification
        notificationRepository.addNotification(
            Notification(
                id = UUID.randomUUID().toString(),
                title = "Safety Check Complete",
                message = "All safety items have been verified for your upcoming playdate",
                type = NotificationType.SAFETY,
                metadata = mapOf("chatId" to currentChatId)
            )
        )
    }

    // Utility Functions
    private fun handleError(message: String, error: Exception) {
        Log.e("ChatViewModel", message, error)
        _error.value = "$message: ${error.message}"
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun generatePlaydateMessage(playdate: Playdate): String {
        return when (playdate.status) {
            PlaydateStatus.ACCEPTED -> "Playdate accepted for ${DateUtils.formatDateTime(playdate.scheduledTime)}"
            PlaydateStatus.CANCELLED -> "Playdate cancelled"
            else -> "Playdate status updated to ${playdate.status}"
        }
    }

    fun reschedulePlaydate(playdate: Playdate) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val updatedPlaydate = playdate.copy(
                    status = PlaydateStatus.RESCHEDULING,
                    metadata = playdate.metadata?.plus("rescheduledAt" to System.currentTimeMillis())
                )

                // Update playdate in DataManager
                dataManager.updatePlaydate(updatedPlaydate)

                // Send message to chat
                val currentUserId = userRepository.getCurrentUserId() ?: return@launch
                chatRepository.sendMessage(
                    chatId = playdate.chatId,
                    senderId = currentUserId,
                    content = "Requested to reschedule playdate",
                    type = MessageType.PLAYDATE_UPDATE
                )

                // Send notification
                notificationRepository.addNotification(
                    Notification(
                        id = UUID.randomUUID().toString(),
                        title = "Playdate Rescheduling",
                        message = "A request to reschedule the playdate has been sent",
                        type = NotificationType.PLAYDATE,
                        metadata = mapOf(
                            "playdateId" to playdate.id,
                            "status" to PlaydateStatus.RESCHEDULING.name
                        )
                    )
                )

                // Update calendar if enabled
                if (userPreferences.getCalendarSync()) {
                    calendarService.syncPlaydateWithCalendar(updatedPlaydate)
                        .also { result ->
                            when (result) {
                                is CalendarService.CalendarResult.Success -> {
                                    Log.d("ChatViewModel", "Calendar updated successfully")
                                }
                                is CalendarService.CalendarResult.Error -> {
                                    Log.e("ChatViewModel", "Failed to update calendar", result.exception)
                                }
                                is CalendarService.CalendarResult.Loading -> {
                                    Log.d("ChatViewModel", "Calendar update in progress")
                                }
                            }
                        }
                }
            } catch (e: Exception) {
                handleError("Failed to reschedule playdate", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendPlaydateMood(playdateId: String, mood: PlaydateMood) {
        viewModelScope.launch {
            try {
                val currentUserId = userRepository.getCurrentUserId() ?: return@launch

                dataManager.updatePlaydateMood(playdateId, mood)

                chatRepository.sendMessage(
                    chatId = currentChatId ?: return@launch,
                    senderId = currentUserId,
                    content = "Changed playdate mood to: ${mood.name}",
                    type = MessageType.PLAYDATE_MOOD,
                    metadata = mapOf(
                        "playdateId" to playdateId,
                        "mood" to mood.name
                    )
                )

                // Send notification
                notificationRepository.addNotification(
                    Notification(
                        id = UUID.randomUUID().toString(),
                        title = "Playdate Mood Updated",
                        message = "The mood was set to ${mood.name}",
                        type = NotificationType.PLAYDATE,
                        metadata = mapOf(
                            "playdateId" to playdateId,
                            "mood" to mood.name
                        )
                    )
                )

                analyticsService.trackCustomEvent(
                    "playdate_mood_set",
                    mapOf(
                        "playdateId" to playdateId,
                        "mood" to mood.name
                    )
                )
            } catch (e: Exception) {
                handleError("Failed to update playdate mood", e)
            }
        }
    }

    fun updateSafetyChecklist(checklist: SafetyChecklist) {
        viewModelScope.launch {
            try {
                _safetyChecklist.value = checklist
                userPreferences.saveSafetyChecklist(checklist)

                // If all items are checked, mark first meeting as complete
                if (checklist.isComplete()) {
                    _isFirstMeeting.value = false
                    sendSafetyConfirmation()

                    // Get the specific dog involved in this chat
                    val chatId = currentChatId
                    val dogForAchievement = if (chatId != null) {
                        userRepository.getDogForChat(chatId)
                    } else {
                        userRepository.getCurrentUserActiveDog()
                    }

                    // Award achievement to the specific dog
                    if (dogForAchievement != null) {
                        achievementService.unlockAchievement(
                            dogId = dogForAchievement.id,
                            achievementId = "SAFETY_CHECK_COMPLETE"
                        )
                    } else {
                        Log.e("ChatViewModel",
                            "Failed to unlock achievement: No dog found for chat $chatId")
                    }
                }
            } catch (e: Exception) {
                handleError("Failed to update safety checklist", e)
            }
        }
    }

    fun handlePromptSelection(prompt: CompatibilityPrompt) {
        viewModelScope.launch {
            try {
                val currentUserId = userRepository.getCurrentUserId() ?: return@launch

                // Send the prompt as a message
                chatRepository.sendMessage(
                    chatId = currentChatId ?: return@launch,
                    senderId = currentUserId,
                    content = prompt.message,
                    type = MessageType.COMPATIBILITY_PROMPT,
                    metadata = mapOf("promptId" to prompt.id)
                )

                // Remove the prompt from the list
                _compatibilityPrompts.value = _compatibilityPrompts.value.filter { it != prompt }

                // Track analytics
                analyticsService.trackCustomEvent(
                    "compatibility_prompt_used",
                    mapOf("promptId" to prompt.id)
                )
            } catch (e: Exception) {
                handleError("Failed to handle prompt selection", e)
            }
        }
    }

    private fun SafetyChecklist.isComplete(): Boolean {
        return vaccinationVerified &&
                sizeCompatible &&
                energyLevelMatched &&
                meetingSpotConfirmed &&
                backupContactShared
    }


    // Media Handling
    fun selectMedia(uri: Uri) {
        _selectedMedia.value = uri
    }

    fun clearSelectedMedia() {
        _selectedMedia.value = null
    }


    // Chat Status
    fun setTyping(isTyping: Boolean) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            chatRepository.updateTypingStatus(chatId, isTyping)
        }
    }

    fun markMessagesAsRead() {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            chatRepository.markMessagesAsRead(chatId)
        }
    }
}
