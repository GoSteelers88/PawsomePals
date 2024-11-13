package io.pawsomepals.app.viewmodel


import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pawsomepals.app.data.DataManager
import io.pawsomepals.app.data.model.Chat
import io.pawsomepals.app.data.model.CompatibilityPrompt
import io.pawsomepals.app.data.model.Message
import io.pawsomepals.app.data.model.MessageType
import io.pawsomepals.app.data.model.Notification
import io.pawsomepals.app.data.model.NotificationType
import io.pawsomepals.app.data.model.Playdate
import io.pawsomepals.app.data.model.PlaydateMood
import io.pawsomepals.app.data.model.PlaydateStatus
import io.pawsomepals.app.data.model.SafetyChecklist
import io.pawsomepals.app.data.model.User
import io.pawsomepals.app.data.preferences.UserPreferences
import io.pawsomepals.app.data.repository.ChatRepository
import io.pawsomepals.app.data.repository.NotificationRepository
import io.pawsomepals.app.data.repository.UserRepository
import io.pawsomepals.app.service.AchievementService
import io.pawsomepals.app.service.AnalyticsService
import io.pawsomepals.app.service.CalendarService
import io.pawsomepals.app.utils.DateUtils
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val auth: FirebaseAuth  // Add this

) : ViewModel() {

    // UI State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

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


    // Safety & Compatibility
    private val _isFirstMeeting = MutableStateFlow(true)
    val isFirstMeeting: StateFlow<Boolean> = _isFirstMeeting

    private val _safetyChecklist = MutableStateFlow(SafetyChecklist())
    val safetyChecklist: StateFlow<SafetyChecklist> = _safetyChecklist

    private val _compatibilityPrompts = MutableStateFlow<List<CompatibilityPrompt>>(emptyList())
    val compatibilityPrompts: StateFlow<List<CompatibilityPrompt>> = _compatibilityPrompts

    private var currentChatId: String? = null

    init {
        viewModelScope.launch {
            _isUserLoggedIn.value = userRepository.isUserLoggedIn()
            if (_isUserLoggedIn.value) {
                loadChats()
            }
        }
    }

    // Chat List Management
    private fun loadChats() {
        viewModelScope.launch {
            try {
                chatRepository.getAllChats().collect { chatsList ->
                    _chats.value = chatsList
                }
            } catch (e: Exception) {
                handleError("Error loading chats", e)
            }
        }
    }

    fun getCurrentUserId(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
    }

    fun loadChatMessages(chatId: String) {
        currentChatId = chatId
        viewModelScope.launch {
            try {
                chatRepository.getMessages(chatId).collect { messageList ->
                    _messages.value = messageList
                }
            } catch (e: Exception) {
                handleError("Error loading messages", e)
            }
        }
    }

    // Message Handling
    fun sendMessage(content: String, mediaUri: Uri? = null) {
        val chatId = currentChatId ?: return
        viewModelScope.launch {
            try {
                val senderId = userRepository.getCurrentUserId() ?: return@launch

                if (mediaUri != null) {
                    sendMediaMessage(chatId, senderId, mediaUri)
                } else {
                    chatRepository.sendMessage(
                        chatId = chatId,
                        senderId = senderId,
                        content = content
                    )
                }
            } catch (e: Exception) {
                handleError("Error sending message", e)
            }
        }
    }

    private suspend fun sendMediaMessage(chatId: String, senderId: String, mediaUri: Uri) {
        _isMediaUploading.value = true
        try {
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
