package io.pawsomepals.app.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.pawsomepals.app.auth.GoogleAuthManager
import io.pawsomepals.app.data.managers.CalendarManager
import io.pawsomepals.app.data.managers.CalendarManager.CalendarAuthState
import io.pawsomepals.app.data.managers.CalendarManager.CalendarState
import io.pawsomepals.app.data.model.Playdate
import io.pawsomepals.app.data.model.PlaydateStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calendarManager: CalendarManager,
    private val googleAuthManager: GoogleAuthManager,
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "CalendarService"
        private const val ONE_HOUR_IN_MILLIS = 60 * 60 * 1000L
    }

    private val _calendarState = MutableStateFlow<CalendarState>(CalendarState.Idle)
    val calendarState: StateFlow<CalendarState> = _calendarState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val coroutineContext = Dispatchers.IO + SupervisorJob()

    sealed class CalendarResult<out T> {
        data class Success<T>(val data: T) : CalendarResult<T>()
        data class Error(val exception: Exception) : CalendarResult<Nothing>()
        object Loading : CalendarResult<Nothing>()
    }

    // Authentication methods
    suspend fun beginCalendarAuth(): CalendarResult<Intent> = withContext(coroutineContext) {
        try {
            _calendarState.value = CalendarState.Loading
            val signInResult = googleAuthManager.getSignInIntent()
            signInResult.fold(
                onSuccess = { intent ->
                    CalendarResult.Success(intent)
                },
                onFailure = { error ->
                    CalendarResult.Error(error as Exception)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to begin calendar auth", e)
            _calendarState.value = CalendarState.Error("Failed to begin calendar auth: ${e.message}")
            CalendarResult.Error(e)
        }
    }

    // In CalendarService.kt
    suspend fun getUserAvailability(userId: String): List<Long> {
        return try {
            val availableTimes = mutableListOf<Long>()
            observeCalendarEvents(
                startTime = System.currentTimeMillis(),
                endTime = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000)
            ).collect { result ->
                when (result) {
                    is CalendarResult.Success -> {
                        availableTimes.addAll(
                            result.data
                                .filter { event -> event.title == "Available for Playdate" }
                                .map { event -> event.startTime }
                        )
                    }
                    else -> {} // Handle other cases if needed
                }
            }
            availableTimes
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user availability", e)
            emptyList()
        }
    }


    suspend fun handleAuthResult(data: Intent?): CalendarResult<Unit> = withContext(coroutineContext) {
        if (data == null) {
            return@withContext CalendarResult.Error(Exception("No sign in data received"))
        }

        try {
            _calendarState.value = CalendarState.Loading
            val result = googleAuthManager.handleSignInResult(data)
            result.fold(
                onSuccess = { token ->
                    val firebaseResult = googleAuthManager.firebaseAuthWithGoogle(token)
                    firebaseResult.fold(
                        onSuccess = { user ->
                            calendarManager.initializeWithToken(token)
                            _calendarState.value = CalendarState.Success(Unit)
                            CalendarResult.Success(Unit)
                        },
                        onFailure = { error ->
                            throw error as Exception
                        }
                    )
                },
                onFailure = { error ->
                    throw error as Exception
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle auth result", e)
            _calendarState.value = CalendarState.Error("Auth failed: ${e.message}")
            CalendarResult.Error(e)
        }
    }



    // Calendar event operations
    suspend fun addPlaydateEvent(playdate: Playdate): CalendarResult<String> = try {
        val managerEvent = createCalendarEventFromPlaydate(playdate)
        when (val result = calendarManager.createEvent(managerEvent)) {
            is CalendarManager.CalendarResult.Success -> CalendarResult.Success(result.data)
            is CalendarManager.CalendarResult.Error -> CalendarResult.Error(Exception(result.message))
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to add playdate event", e)
        CalendarResult.Error(e)
    }

    suspend fun deleteCalendarEvent(eventId: String): CalendarResult<Unit> = try {
        calendarManager.deleteEvent(eventId)
        CalendarResult.Success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to delete calendar event", e)
        CalendarResult.Error(e)
    }

    suspend fun updateCalendarEventStatus(eventId: String, status: String): CalendarResult<Unit> = try {
        calendarManager.updateEventStatus(eventId, status)
        CalendarResult.Success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to update calendar event status", e)
        CalendarResult.Error(e)
    }

    private fun createCalendarEventFromPlaydate(playdate: Playdate): CalendarManager.CalendarEvent {
        return CalendarManager.CalendarEvent(
            id = playdate.id,
            title = "Playdate",
            description = "Playdate at ${playdate.location}",
            startTime = playdate.scheduledTime,
            endTime = playdate.scheduledTime + ONE_HOUR_IN_MILLIS,
            location = playdate.location,
            participants = listOf(playdate.dog1Id, playdate.dog2Id),
            status = playdate.status,
            reminders = emptyList()
        )
    }
    fun observeCalendarEvents(
        startTime: Long,
        endTime: Long
    ): Flow<CalendarResult<List<CalendarManager.CalendarEvent>>> = flow {
        emit(CalendarResult.Loading)

        try {
            val events = calendarManager.getEvents(startTime, endTime)
            events.collect { managerEvents ->
                val serviceEvents = managerEvents.map { managerEvent ->
                    CalendarManager.CalendarEvent(
                        id = managerEvent.id,
                        title = managerEvent.title,
                        description = managerEvent.description,
                        startTime = managerEvent.startTime,
                        endTime = managerEvent.endTime,
                        location = managerEvent.location,
                        participants = managerEvent.participants,
                        status = managerEvent.status,
                        reminders = managerEvent.reminders ?: emptyList()
                    )
                }
                emit(CalendarResult.Success(serviceEvents))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error observing calendar events", e)
            emit(CalendarResult.Error(e))
        }
    }
    suspend fun syncPlaydateWithCalendar(playdate: Playdate): CalendarResult<String> {
        return try {
            val event = CalendarManager.CalendarEvent(
                id = playdate.id,
                title = "Playdate",
                description = "Playdate at ${playdate.location}",
                startTime = playdate.scheduledTime,
                endTime = playdate.scheduledTime + ONE_HOUR_IN_MILLIS,
                location = playdate.location,
                participants = listOf(playdate.dog1Id, playdate.dog2Id),
                status = playdate.status
            )

            when (val result = calendarManager.createEvent(event)) {
                is CalendarManager.CalendarResult.Success -> CalendarResult.Success(result.data)
                is CalendarManager.CalendarResult.Error -> CalendarResult.Error(Exception(result.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync playdate with calendar", e)
            CalendarResult.Error(e)
        }
    }
    suspend fun updatePlaydateEvent(
        playdateId: String,
        title: String,
        description: String,
        startTime: Long,
        endTime: Long,
        location: String,
        participants: List<String>,
        status: PlaydateStatus
    ): CalendarResult<Unit> {
        return try {
            val event = CalendarManager.CalendarEvent(
                id = playdateId,
                title = title,
                description = description,
                startTime = startTime,
                endTime = endTime,
                location = location,
                participants = participants,
                status = status,
                reminders = emptyList()
            )

            when (val result = calendarManager.updateEvent(event)) {
                is CalendarManager.CalendarResult.Success -> CalendarResult.Success(Unit)
                is CalendarManager.CalendarResult.Error -> CalendarResult.Error(Exception(result.message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update playdate event", e)
            CalendarResult.Error(e)
        }
    }
    private suspend fun updatePlaydateStatus(playdateId: String, status: PlaydateStatus) {
        try {
            firestore.collection("playdates")
                .document(playdateId)
                .update("status", status.name)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating playdate status", e)
            throw e
        }
    }
    

    fun checkAuthStatus(): CalendarAuthState = calendarManager.calendarAuthState.value

    class UnauthorizedException(message: String) : Exception(message)
    class CalendarException(message: String, cause: Throwable? = null) : Exception(message, cause)
}