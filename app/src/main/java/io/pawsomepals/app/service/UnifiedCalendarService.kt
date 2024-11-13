package io.pawsomepals.app.service

import android.content.Context
import io.pawsomepals.app.data.managers.CalendarManager
import io.pawsomepals.app.data.model.Playdate
import io.pawsomepals.app.data.model.PlaydateStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedCalendarService @Inject constructor(
    private val calendarManager: CalendarManager,
    @ApplicationContext private val context: Context
) {
    sealed class CalendarResult<out T> {
        data class Success<T>(val data: T) : CalendarResult<T>()
        data class Error(val exception: Exception) : CalendarResult<Nothing>()
        object Loading : CalendarResult<Nothing>()
    }

    suspend fun syncPlaydateWithCalendar(playdate: Playdate): CalendarResult<String> {
        return try {
            val event = CalendarManager.CalendarEvent(
                id = playdate.id,
                title = "Playdate",
                description = "Dog playdate at ${playdate.location}",
                startTime = playdate.scheduledTime,
                endTime = playdate.scheduledTime + (60 * 60 * 1000), // 1 hour duration
                location = playdate.location,
                participants = listOf(playdate.dog1Id, playdate.dog2Id),
                status = playdate.status,
                reminders = listOf(
                    CalendarManager.Reminder(
                        playdate.scheduledTime - (30 * 60 * 1000), // 30 min before
                        CalendarManager.ReminderType.NOTIFICATION
                    )
                )
            )

            when (val result = calendarManager.createEvent(event)) {
                is CalendarManager.CalendarResult.Success -> CalendarResult.Success(result.data)
                is CalendarManager.CalendarResult.Error -> CalendarResult.Error(Exception(result.message))
            }
        } catch (e: Exception) {
            CalendarResult.Error(e)
        }
    }

    suspend fun observeCalendarEvents(startTime: Long, endTime: Long): Flow<CalendarResult<List<CalendarManager.CalendarEvent>>> {
        return calendarManager.getEvents(startTime, endTime)
            .map { events -> CalendarResult.Success(events) }
    }

    suspend fun updateCalendarEvent(playdateId: String, status: PlaydateStatus): CalendarResult<Unit> {
        return try {
            when (val result = calendarManager.updateEventStatus(playdateId, status.name)) {
                is CalendarManager.CalendarResult.Success -> CalendarResult.Success(Unit)
                is CalendarManager.CalendarResult.Error -> CalendarResult.Error(Exception(result.message))
            }
        } catch (e: Exception) {
            CalendarResult.Error(e)
        }
    }

    suspend fun deleteCalendarEvent(eventId: String): CalendarResult<Unit> {
        return try {
            when (val result = calendarManager.deleteEvent(eventId)) {
                is CalendarManager.CalendarResult.Success -> CalendarResult.Success(Unit)
                is CalendarManager.CalendarResult.Error -> CalendarResult.Error(Exception(result.message))
            }
        } catch (e: Exception) {
            CalendarResult.Error(e)
        }
    }
}