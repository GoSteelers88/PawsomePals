package com.example.pawsomepals.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.pawsomepals.data.dao.PlaydateDao
import com.example.pawsomepals.data.model.PlaydateRequest
import com.example.pawsomepals.data.model.RequestStatus
import com.example.pawsomepals.data.model.Timeslot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaydateRepository @Inject constructor(
    private val playdateDao: PlaydateDao,
    private val userRepository: UserRepository
) {

    fun getAvailableTimeslots(): Flow<List<Timeslot>> {
        return playdateDao.getAllTimeslots()
    }

    suspend fun createTimeslot(startTime: String, endTime: String, dayOfWeek: Int) {
        val timeslot = Timeslot(startTime = startTime, endTime = endTime, dayOfWeek = dayOfWeek)
        playdateDao.insertTimeslot(timeslot)
    }

    fun getPlaydateRequests(): Flow<List<PlaydateRequest>> {
        return playdateDao.getAllPlaydateRequests()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun createPlaydateRequest(receiverId: String, timeslots: List<LocalDate>): PlaydateRequest? {
        val currentUserId = userRepository.getCurrentUserId() ?: return null

        val playdateRequest = PlaydateRequest(
            requesterId = currentUserId,
            receiverId = receiverId,
            suggestedTimeslots = timeslots.map { it.atStartOfDay().toEpochSecond(ZoneOffset.UTC) },
            status = RequestStatus.PENDING
        )
        val id = playdateDao.insertPlaydateRequest(playdateRequest)
        return playdateRequest.copy(id = id.toInt())
    }

    suspend fun updatePlaydateRequestStatus(requestId: Int, status: RequestStatus) {
        val requests = playdateDao.getAllPlaydateRequests().first()
        val request = requests.find { it.id == requestId }
        request?.let { foundRequest ->
            val updatedRequest = foundRequest.copy(status = status)
            playdateDao.updatePlaydateRequest(updatedRequest)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getPlaydatesForDateRange(startDate: LocalDate, endDate: LocalDate): List<PlaydateRequest> {
        val startTimestamp = startDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val endTimestamp = endDate.atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC)
        return playdateDao.getPlaydatesForDateRange(startTimestamp, endTimestamp)
    }
}