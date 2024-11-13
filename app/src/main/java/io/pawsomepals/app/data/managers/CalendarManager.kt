package io.pawsomepals.app.data.managers

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pawsomepals.app.data.dao.PlaydateDao
import io.pawsomepals.app.data.model.CalendarTimeSlot
import io.pawsomepals.app.data.model.PlaydateStatus
import io.pawsomepals.app.data.model.PlaydateWithDetails
import io.pawsomepals.app.data.preferences.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class CalendarManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val playdateDao: PlaydateDao,
    private val userPreferences: UserPreferences  // Changed from SharedPreferences
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // State management
    private val _calendarState = MutableStateFlow<CalendarState>(CalendarState.Idle)
    val calendarState: StateFlow<CalendarState> = _calendarState.asStateFlow()

    private val _calendarAuthState = MutableStateFlow<CalendarAuthState>(CalendarAuthState.NotInitialized)
    val calendarAuthState: StateFlow<CalendarAuthState> = _calendarAuthState.asStateFlow()

    // Data models
    sealed class CalendarState {
        object Idle : CalendarState()
        object Loading : CalendarState()
        data class Error(val message: String) : CalendarState()
        data class Success<T>(val data: T) : CalendarState()
    }

    sealed class CalendarAuthState {
        object NotInitialized : CalendarAuthState()
        object NotAuthenticated : CalendarAuthState()
        object RequiresPermissions : CalendarAuthState()
        data class Authenticated(
            val provider: CalendarProvider,
            val accountInfo: CalendarAccountInfo? = null
        ) : CalendarAuthState()
        data class Error(val type: ErrorType, val message: String) : CalendarAuthState()
    }

    data class CalendarAccountInfo(
        val accountId: String,
        val email: String?,
        val displayName: String?,
        val photoUrl: String?,
        val lastSyncTimestamp: Long?,
        val permissions: Set<CalendarPermission>
    )

    data class CalendarEvent(
        val id: String,
        val title: String,
        val description: String,
        val startTime: Long,
        val endTime: Long,
        val location: String?,
        val participants: List<String>,
        val status: PlaydateStatus,
        val reminders: List<Reminder> = emptyList()
    )

    data class Reminder(
        val time: Long,
        val type: ReminderType
    )

    data class TimeSlot(
        val id: String,
        val userId: String,
        val startTime: String,
        val endTime: String,
        val dayOfWeek: Int,
        val date: Long = 0,
        val isAvailable: Boolean = true,
        val existingPlaydate: PlaydateWithDetails? = null
    )
    // Sealed classes for results
    sealed class CalendarResult<out T> {
        data class Success<T>(val data: T) : CalendarResult<T>()
        data class Error(val message: String) : CalendarResult<Nothing>()
    }

    // Enums
    enum class CalendarProvider {
        GOOGLE_CALENDAR,
        DEVICE_CALENDAR,
        CUSTOM_CALENDAR
    }

    enum class CalendarPermission {
        READ_CALENDAR,
        WRITE_CALENDAR,
        READ_EVENTS,
        WRITE_EVENTS,
        MANAGE_SHARING
    }

    enum class ErrorType {
        PERMISSION_DENIED,
        AUTHENTICATION_FAILED,
        TOKEN_EXPIRED,
        NETWORK_ERROR,
        SYNC_FAILED,
        UNKNOWN
    }

    enum class ReminderType {
        NOTIFICATION,
        EMAIL
    }

    init {
        initializeFromStoredState()
    }

    private fun initializeFromStoredState() {
        scope.launch {
            try {
                // Use UserPreferences' calendarSyncFlow instead of SharedPreferences
                val isCalendarEnabled = userPreferences.calendarSyncFlow.first()
                if (isCalendarEnabled) {
                    // Additional calendar initialization if needed
                    _calendarAuthState.value = CalendarAuthState.NotAuthenticated
                } else {
                    _calendarAuthState.value = CalendarAuthState.NotInitialized
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing calendar state", e)
                handleAuthError(e)
            }
        }
    }


    suspend fun initializeWithToken(token: String) {
        try {
            _calendarState.value = CalendarState.Loading

            // Store calendar sync preference using UserPreferences
            userPreferences.setCalendarSync(true)

            // Initialize state
            initializeWithStoredCredentials(token, CalendarProvider.GOOGLE_CALENDAR)

            _calendarState.value = CalendarState.Success(Unit)
        } catch (e: Exception) {
            handleAuthError(e)
        }
    }


    private suspend fun initializeWithStoredCredentials(token: String, provider: CalendarProvider) {
        try {
            val accountInfo = getCurrentAccountInfo(token)
            _calendarAuthState.value = CalendarAuthState.Authenticated(provider, accountInfo)
        } catch (e: Exception) {
            handleAuthError(e)
        }
    }

    suspend fun getEvents(
        startDate: Long,
        endDate: Long,
        filter: EventFilter = EventFilter()
    ): Flow<List<CalendarEvent>> = callbackFlow {
        when (_calendarAuthState.value) {
            is CalendarAuthState.Authenticated -> {
                val events = fetchEventsFromFirestore(startDate, endDate, filter)
                trySend(events)
            }
            else -> {
                trySend(emptyList())
            }
        }
        awaitClose()
    }

    suspend fun createEvent(event: CalendarEvent): CalendarResult<String> {
        return try {
            requireAuthenticated()

            val eventRef = firestore.collection("calendar_events").document()
            eventRef.set(event).await()

            CalendarResult.Success(eventRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating calendar event", e)
            CalendarResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun deleteEvent(eventId: String): CalendarResult<Unit> {
        return try {
            requireAuthenticated()

            firestore.collection("calendar_events")
                .document(eventId)
                .delete()
                .await()

            CalendarResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting calendar event", e)
            CalendarResult.Error(e.message ?: "Unknown error")
        }
    }


    suspend fun updateEventStatus(eventId: String, status: String): CalendarResult<Unit> {
        return try {
            requireAuthenticated()

            firestore.collection("calendar_events")
                .document(eventId)
                .update("status", status)
                .await()

            CalendarResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating calendar event status", e)
            CalendarResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getAvailableTimeSlots(
        startDate: Long,
        endDate: Long,
        duration: Int = 60
    ): List<CalendarTimeSlot> {
        return try {
            requireAuthenticated()
            // Implementation for getting available time slots
            emptyList() // Placeholder
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available time slots", e)
            emptyList()
        }
    }

    private suspend fun getCurrentAccountInfo(token: String): CalendarAccountInfo {
        // Implement token decoding and account info extraction
        return CalendarAccountInfo(
            accountId = "placeholder",
            email = null,
            displayName = null,
            photoUrl = null,
            lastSyncTimestamp = System.currentTimeMillis(),
            permissions = setOf(CalendarPermission.READ_CALENDAR, CalendarPermission.WRITE_CALENDAR)
        )
    }

    private suspend fun fetchEventsFromFirestore(
        startDate: Long,
        endDate: Long,
        filter: EventFilter
    ): List<CalendarEvent> {
        // Implement Firestore query
        return emptyList() // Placeholder
    }

    private fun handleAuthError(e: Exception) {
        Log.e(TAG, "Calendar auth error", e)
        _calendarState.value = CalendarState.Error(e.message ?: "Unknown error")
        _calendarAuthState.value = CalendarAuthState.Error(
            type = when (e) {
                is SecurityException -> ErrorType.PERMISSION_DENIED
                else -> ErrorType.UNKNOWN
            },
            message = e.message ?: "Unknown error"
        )
    }
    private suspend fun requireAuthenticated() {
        if (_calendarAuthState.value !is CalendarAuthState.Authenticated) {
            throw SecurityException("Calendar not authenticated")
        }
    }

    suspend fun updateEvent(event: CalendarEvent): CalendarResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                requireAuthenticated()

                // Validate event data
                if (event.id.isBlank()) {
                    return@withContext CalendarResult.Error("Event ID cannot be empty")
                }

                // Create update payload
                val updateData = mapOf(
                    "title" to event.title,
                    "description" to event.description,
                    "startTime" to event.startTime,
                    "endTime" to event.endTime,
                    "location" to event.location,
                    "participants" to event.participants,
                    "status" to event.status.name,
                    "lastModified" to System.currentTimeMillis(),
                    "reminders" to event.reminders
                )

                // Perform update with retry mechanism
                var attempts = 0
                val maxAttempts = 3
                var lastException: Exception? = null

                while (attempts < maxAttempts) {
                    try {
                        firestore.collection("calendar_events")
                            .document(event.id)
                            .update(updateData)
                            .await()

                        return@withContext CalendarResult.Success(Unit)
                    } catch (e: Exception) {
                        lastException = e
                        attempts++
                        if (attempts < maxAttempts) {
                            delay(1000L * attempts)
                        }
                    }
                }

                return@withContext CalendarResult.Error("Failed to update event after $maxAttempts attempts: ${lastException?.message}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext CalendarResult.Error("Unexpected error updating event: ${e.message}")
            }
        }
    }


    data class EventFilter(
        val status: PlaydateStatus? = null,
        val participantId: String? = null,
        val location: String? = null
    )

    companion object {
        private const val TAG = "CalendarManager"
        private const val PREF_CALENDAR_TOKEN = "calendar_token"
        private const val PREF_CALENDAR_PROVIDER = "calendar_provider"
    }
}