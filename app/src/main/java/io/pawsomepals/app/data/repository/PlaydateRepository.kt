package io.pawsomepals.app.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.firebase.firestore.FirebaseFirestore
import io.pawsomepals.app.data.dao.PlaydateDao
import io.pawsomepals.app.data.managers.CalendarManager
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.Playdate
import io.pawsomepals.app.data.model.PlaydateRequest
import io.pawsomepals.app.data.model.PlaydateStatus
import io.pawsomepals.app.data.model.PlaydateWithDetails
import io.pawsomepals.app.data.model.RequestStatus
import io.pawsomepals.app.data.model.TimeslotEntity
import io.pawsomepals.app.data.model.UserAvailability
import io.pawsomepals.app.notification.NotificationManager
import io.pawsomepals.app.service.CalendarService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaydateRepository @Inject constructor(
    private val playdateDao: PlaydateDao,
    private val userRepository: UserRepository,
    private val calendarService: CalendarService,
    private val firestore: FirebaseFirestore,
    private val notificationManager: NotificationManager
) {
    data class PlaydateStats(
        val weeklyPlaydates: Int = 0,
        val availableHours: Int = 0,
        val completedPlaydates: Int = 0
    )

    // --- Base Methods ---
    suspend fun getUnscheduledMatches(): Flow<List<Match>> = flow {
        try {
            val userId = userRepository.getCurrentUserId() ?: return@flow
            val matches = firestore.collection("matches")
                .whereEqualTo("status", "MATCHED")
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .toObjects(Match::class.java)

            val playdates = playdateDao.getAllPlaydates().first()
            val scheduledMatchIds = playdates.map { it.matchId }.toSet()
            val unscheduledMatches = matches.filterNot { match ->
                scheduledMatchIds.contains(match.id)
            }
            emit(unscheduledMatches)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    suspend fun getConfirmedPlaydates(): Flow<List<PlaydateWithDetails>> = flow {
        try {
            val userId = userRepository.getCurrentUserId() ?: return@flow

            // Changed to use the Dao directly and map results
            playdateDao.getPlaydatesByStatus(PlaydateStatus.SCHEDULED)
                .collect { playdatesWithRequests ->
                    val confirmedPlaydates = playdatesWithRequests.map { playdateWithRequests ->
                        val playdate = playdateWithRequests.playdate
                        val otherDogId =
                            if (playdate.dog1Id == userId) playdate.dog2Id else playdate.dog1Id
                        val otherDog = userRepository.getDogProfileById(otherDogId)
                        val location = if (playdate.location.isNotEmpty()) {
                            getLocationDetails(playdate.location)
                        } else {
                            createDefaultLocation()
                        }

                        PlaydateWithDetails(
                            playdate = playdate,
                            otherDog = otherDog
                                ?: throw IllegalStateException("Other dog not found"),
                            location = location,
                            status = playdate.status
                        )
                    }
                    emit(confirmedPlaydates)
                }
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    // --- Request Related Methods ---
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun createPlaydateRequest(
        match: Match.MatchWithDetails,
        location: DogFriendlyLocation,
        dateTime: LocalDateTime
    ): PlaydateRequest {
        return PlaydateRequest(
            id = UUID.randomUUID().toString(),
            matchId = match.match.id,
            requesterId = match.match.user1Id,
            receiverId = match.match.user2Id,
            suggestedTimeslots = listOf(
                dateTime.toEpochSecond(ZoneOffset.UTC)
            ),
            selectedLocationId = location.placeId,
            status = RequestStatus.PENDING,
            lastUpdated = System.currentTimeMillis()
        ).also { request ->
            playdateDao.insertPlaydateRequest(request)
            firestore.collection("playdate_requests")
                .document(request.id)
                .set(request)
                .await()
        }
    }
    suspend fun updatePlaydateRequestStatus(requestId: String, status: RequestStatus) {
        try {
            // First update Firestore
            firestore.collection("playdate_requests")
                .document(requestId)
                .update("status", status.name)
                .await()

            // Then update local database
            val request = playdateDao.getPlaydateRequestById(requestId)
            request?.let { foundRequest ->
                val updatedRequest = foundRequest.copy(status = status)
                playdateDao.updatePlaydateRequest(updatedRequest)

                if (status == RequestStatus.ACCEPTED) {
                    createConfirmedPlaydate(updatedRequest)
                }
            }
        } catch (e: Exception) {
            // Fallback to local update if Firestore fails
            val request = playdateDao.getPlaydateRequestById(requestId)
            request?.let { foundRequest ->
                val updatedRequest = foundRequest.copy(status = status)
                playdateDao.updatePlaydateRequest(updatedRequest)
            }
        }
    }

    suspend fun getPlaydateRequestsForUser(userId: String): Flow<Pair<List<PlaydateRequest>, List<PlaydateRequest>>> =
        flow {
            try {
                val requests = playdateDao.getAllPlaydateRequests().first()
                val incoming = requests.filter {
                    it.receiverId == userId && it.status == RequestStatus.PENDING
                }
                val outgoing = requests.filter {
                    it.requesterId == userId && it.status == RequestStatus.PENDING
                }
                emit(Pair(incoming, outgoing))
            } catch (e: Exception) {
                emit(Pair(emptyList(), emptyList()))
            }
        }

    fun getPlaydateRequests(): Flow<List<PlaydateRequest>> {
        return playdateDao.getAllPlaydateRequests()
    }

    // --- Timeslot Related Methods ---
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun createTimeslot(startTime: String, endTime: String, dayOfWeek: Int) {
        val timeslotEntity = TimeslotEntity(
            startTime = startTime,
            endTime = endTime,
            dayOfWeek = dayOfWeek,
            date = LocalDate.now().toEpochDay()
        )

        playdateDao.insertTimeslot(timeslotEntity)

        val userId = userRepository.getCurrentUserId() ?: return
        firestore.collection("users")
            .document(userId)
            .collection("recurring_timeslots")
            .add(timeslotEntity)
            .await()
    }

    suspend fun getAvailableTimeslotsForDay(dayOfWeek: Int, userId: String): List<TimeslotEntity> {
        return try {
            val firestoreTimeslots = firestore.collection("users")
                .document(userId)
                .collection("recurring_timeslots")
                .whereEqualTo("dayOfWeek", dayOfWeek)
                .get()
                .await()
                .toObjects(TimeslotEntity::class.java)

            firestoreTimeslots.forEach { timeslot ->
                playdateDao.insertTimeslot(timeslot)
            }
            firestoreTimeslots
        } catch (e: Exception) {
            playdateDao.getAllTimeslots().first().filter { it.dayOfWeek == dayOfWeek }
        }
    }

    // --- Playdate Related Methods ---
    @RequiresApi(Build.VERSION_CODES.O)
    fun getPlaydatesByStatus(status: PlaydateStatus): Flow<List<PlaydateWithDetails>> = flow {
        try {
            val userId = userRepository.getCurrentUserId() ?: return@flow
            val dogId = getDogIdForUser(userId) ?: userId

            // Changed from first() to collect()
            playdateDao.getPlaydatesByStatus(status, dogId).collect { playdatesWithRequests ->
                val playdatesWithDetails = playdatesWithRequests.map { playdateWithRequests ->
                    val playdate = playdateWithRequests.playdate
                    val otherDogId =
                        if (playdate.dog1Id == userId) playdate.dog2Id else playdate.dog1Id
                    val otherDog = userRepository.getDogProfileById(otherDogId)
                    val location = getLocationDetails(playdate.location)

                    PlaydateWithDetails(
                        playdate = playdate,
                        otherDog = otherDog ?: throw IllegalStateException("Other dog not found"),
                        location = location,
                        status = playdate.status
                    )
                }
                emit(playdatesWithDetails)
            }
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    suspend fun getPlaydateById(playdateId: String): PlaydateWithDetails? {
        return try {
            val currentUserId = userRepository.getCurrentUserId() ?: return null
            val playdate = playdateDao.getPlaydateById(playdateId) ?: return null
            val otherDogId =
                if (playdate.dog1Id == currentUserId) playdate.dog2Id else playdate.dog1Id
            val otherDog = userRepository.getDogProfileById(otherDogId) ?: return null
            val location = if (playdate.location.isNotEmpty()) {
                try {
                    val locationDoc = firestore.collection("locations")
                        .document(playdate.location)
                        .get()
                        .await()
                    if (locationDoc.exists()) {
                        locationDoc.toObject(DogFriendlyLocation::class.java)
                    } else createDefaultLocation()
                } catch (e: Exception) {
                    createDefaultLocation()
                }
            } else createDefaultLocation()

            PlaydateWithDetails(
                playdate = playdate,
                otherDog = otherDog,
                location = location ?: createDefaultLocation(),
                status = playdate.status
            )
        } catch (e: Exception) {
            null
        }
    }

    // --- Helper Methods ---
    private suspend fun createConfirmedPlaydate(request: PlaydateRequest) {
        val playdate = Playdate(
            id = UUID.randomUUID().toString(),
            matchId = request.matchId,
            dog1Id = request.requesterId,
            dog2Id = request.receiverId,
            scheduledTime = request.suggestedTimeslots.first(),
            status = PlaydateStatus.SCHEDULED,  // Changed from CONFIRMED
            location = request.selectedLocationId,  // Changed from location
            createdBy = request.requesterId
        )

        playdateDao.insertPlaydate(playdate)

        if (calendarService.checkAuthStatus() is CalendarManager.CalendarAuthState.Authenticated) {
            calendarService.addPlaydateEvent(playdate)
        }
    }

    private suspend fun getLocationDetails(locationId: String): DogFriendlyLocation {
        return try {
            firestore.collection("locations")
                .document(locationId)
                .get()
                .await()
                .toObject(DogFriendlyLocation::class.java) ?: createDefaultLocation()
        } catch (e: Exception) {
            createDefaultLocation()
        }
    }

    private fun createDefaultLocation() = DogFriendlyLocation(
        placeId = "",
        name = "Location not set",
        address = "",
        latitude = 0.0,
        longitude = 0.0,
        placeTypes = emptyList(),
        rating = null,
        userRatingsTotal = null,
        phoneNumber = null,
        websiteUri = null
    )

    private suspend fun getDogIdForUser(userId: String): String? {
        return try {
            firestore.collection("users")
                .document(userId)
                .collection("dogs")
                .limit(1)
                .get()
                .await()
                .documents
                .firstOrNull()
                ?.id
        } catch (e: Exception) {
            null
        }
    }

    // Add these methods inside the PlaydateRepository class

    // --- Calendar & Availability Methods ---
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getUserAvailabilityForWeek(userId: String): Flow<Map<DayOfWeek, List<UserAvailability>>> =
        flow {
            try {
                val availability = mutableMapOf<DayOfWeek, MutableList<UserAvailability>>()

                // Get timeslots for each day of the week
                DayOfWeek.values().forEach { day ->
                    val timeslots = getAvailableTimeslotsForDay(day.value, userId)
                    val userAvailabilities = timeslots.map { timeslot ->
                        val startTime = LocalTime.parse(timeslot.startTime)
                        val endTime = LocalTime.parse(timeslot.endTime)

                        UserAvailability(
                            userId = userId,
                            dayOfWeek = day,
                            startHour = startTime.hour,
                            startMinute = startTime.minute,
                            endHour = endTime.hour,
                            endMinute = endTime.minute
                        )
                    }
                    availability[day] = userAvailabilities.toMutableList()
                }
                emit(availability)
            } catch (e: Exception) {
                emit(emptyMap())
            }
        }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun syncTimeslotsWithCalendar(userId: String) {
        val timeslots = playdateDao.getAllTimeslots().first()
        timeslots.forEach { timeslot ->
            val playdate = Playdate(
                id = UUID.randomUUID().toString(),
                scheduledTime = LocalTime.parse(timeslot.startTime).toSecondOfDay() * 1000L,
                status = PlaydateStatus.NONE,
                dog1Id = userId,
                dog2Id = "",
                createdBy = userId,
                location = ""
            )
            calendarService.addPlaydateEvent(playdate)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun convertCalendarSelectionToTimeslots(
        selectedDate: LocalDate,
        selectedTimes: List<String>
    ): List<TimeslotEntity> {
        return selectedTimes.map { timeString ->
            TimeslotEntity(
                startTime = timeString,
                endTime = LocalTime.parse(timeString)
                    .plusHours(1)
                    .format(DateTimeFormatter.ofPattern("HH:mm")),
                dayOfWeek = selectedDate.dayOfWeek.value,
                date = selectedDate.toEpochDay()
            )
        }
    }

    suspend fun saveRecurringTimeslot(
        startTime: String,
        endTime: String,
        dayOfWeek: Int,
        userId: String = userRepository.getCurrentUserId() ?: ""
    ) {
        val timeslotEntity = TimeslotEntity(
            startTime = startTime,
            endTime = endTime,
            dayOfWeek = dayOfWeek
        )

        firestore.collection("users")
            .document(userId)
            .collection("recurring_timeslots")
            .add(timeslotEntity)
            .await()

        playdateDao.insertTimeslot(timeslotEntity)
    }

    suspend fun saveUserAvailability(
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        isRecurring: Boolean = true
    ) {
        val userId = userRepository.getCurrentUserId() ?: return
        val timeslotEntity = TimeslotEntity(
            startTime = startTime,
            endTime = endTime,
            dayOfWeek = dayOfWeek
        )

        firestore.collection("users")
            .document(userId)
            .collection("recurring_timeslots")
            .add(timeslotEntity)
            .await()

        playdateDao.insertTimeslot(timeslotEntity)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun checkTimeslotConflicts(
        startTime: String,
        endTime: String,
        dayOfWeek: Int,
        userId: String
    ): List<PlaydateRequest> {
        val startTimeObj = LocalTime.parse(startTime)
        val endTimeObj = LocalTime.parse(endTime)

        return playdateDao.getAllPlaydateRequests().first().filter { request ->
            (request.requesterId == userId || request.receiverId == userId) &&
                    request.suggestedTimeslots.any { timestamp ->
                        val requestDate = LocalDate.ofEpochDay(timestamp)
                        requestDate.dayOfWeek.value == dayOfWeek
                    }
        }
    }

    suspend fun updateTimeslot(
        timeslotId: Int,
        startTime: String,
        endTime: String,
        dayOfWeek: Int,
        userId: String
    ) {
        val timeslotEntity = TimeslotEntity(
            id = timeslotId,
            startTime = startTime,
            endTime = endTime,
            dayOfWeek = dayOfWeek
        )

        firestore.collection("users")
            .document(userId)
            .collection("recurring_timeslots")
            .whereEqualTo("id", timeslotId)
            .get()
            .await()
            .documents
            .firstOrNull()?.reference?.set(timeslotEntity)
            ?.await()

        playdateDao.updateTimeslot(timeslotEntity)
    }

    suspend fun deleteTimeslot(timeslotId: Int, userId: String) {
        val timeslot = playdateDao.getAllTimeslots().first().find { it.id == timeslotId } ?: return

        firestore.collection("users")
            .document(userId)
            .collection("recurring_timeslots")
            .whereEqualTo("id", timeslotId)
            .get()
            .await()
            .documents
            .forEach { document ->
                document.reference.delete().await()
            }

        playdateDao.deleteTimeslot(timeslot)
    }

    // --- Stats Related Methods ---
    suspend fun getPlaydateStats(): PlaydateStats {
        val userId = userRepository.getCurrentUserId() ?: return PlaydateStats()
        val dogId = getDogIdForUser(userId) ?: userId

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        val weekStart = calendar.timeInMillis
        calendar.add(Calendar.WEEK_OF_YEAR, 1)
        val weekEnd = calendar.timeInMillis

        return try {
            getLocalStats(userId, weekStart, weekEnd)
        } catch (e: Exception) {
            getFirestoreStats(userId, weekStart, weekEnd)
        }
    }

    private suspend fun getLocalStats(
        userId: String,
        weekStart: Long,
        weekEnd: Long
    ): PlaydateStats {
        val weeklyPlaydates = playdateDao.getPlaydatesCountForWeek(userId, weekStart, weekEnd)
        val completedPlaydates = playdateDao.getCompletedPlaydatesCount(userId)
        val availableHours = playdateDao.getAvailableHoursForDay(
            Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        )?.toInt() ?: 0

        return PlaydateStats(
            weeklyPlaydates = weeklyPlaydates,
            availableHours = availableHours,
            completedPlaydates = completedPlaydates
        )
    }

    private suspend fun getFirestoreStats(
        userId: String,
        weekStart: Long,
        weekEnd: Long
    ): PlaydateStats {
        val weeklyPlaydates = firestore.collection("playdates")
            .whereGreaterThanOrEqualTo("scheduledTime", weekStart)
            .whereLessThanOrEqualTo("scheduledTime", weekEnd)
            .whereEqualTo("dog1Id", userId)
            .get()
            .await()
            .size()

        val completedPlaydates = firestore.collection("playdates")
            .whereEqualTo("status", PlaydateStatus.COMPLETED)
            .whereEqualTo("dog1Id", userId)
            .get()
            .await()
            .size()

        val availableHours = getAvailableHoursFromFirestore(userId)

        return PlaydateStats(
            weeklyPlaydates = weeklyPlaydates,
            availableHours = availableHours,
            completedPlaydates = completedPlaydates
        )
    }

    private suspend fun getAvailableHoursFromFirestore(userId: String): Int {
        return firestore.collection("users")
            .document(userId)
            .collection("recurring_timeslots")
            .get()
            .await()
            .documents
            .sumOf { doc ->
                val startTime = doc.getString("startTime")?.split(":")?.get(0)?.toIntOrNull() ?: 0
                val endTime = doc.getString("endTime")?.split(":")?.get(0)?.toIntOrNull() ?: 0
                endTime - startTime
            }
    }

    // --- Date Range Methods ---
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getPlaydatesForDateRange(startDate: LocalDate, endDate: LocalDate): List<Playdate> {
        val startTimestamp = startDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val endTimestamp = endDate.atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC)
        return playdateDao.getPlaydatesForDateRange(startTimestamp, endTimestamp)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun initializeTimeslotData(userId: String, dogId: String) {
        try {
            val firestoreTimeslots = firestore.collection("users")
                .document(userId)
                .collection("recurring_timeslots")
                .get()
                .await()
                .toObjects(TimeslotEntity::class.java)

            firestoreTimeslots.forEach { timeslot ->
                playdateDao.insertTimeslot(timeslot)
            }

            if (calendarService.checkAuthStatus() is CalendarManager.CalendarAuthState.Authenticated) {
                syncTimeslotsWithCalendar(userId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getPlaydatesForCurrentWeek(): Int {
        return try {
            val today = LocalDate.now()
            val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

            val userId = userRepository.getCurrentUserId() ?: return 0

            // Query Firestore for this week's playdates
            val playdates = firestore.collection("playdates")
                .whereEqualTo("dog1Id", userId)
                .whereGreaterThanOrEqualTo(
                    "scheduledTime",
                    startOfWeek.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
                )
                .whereLessThanOrEqualTo(
                    "scheduledTime",
                    endOfWeek.atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC)
                )
                .get()
                .await()
                .documents.size

            // Also check playdates where user is dog2
            val additionalPlaydates = firestore.collection("playdates")
                .whereEqualTo("dog2Id", userId)
                .whereGreaterThanOrEqualTo(
                    "scheduledTime",
                    startOfWeek.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
                )
                .whereLessThanOrEqualTo(
                    "scheduledTime",
                    endOfWeek.atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC)
                )
                .get()
                .await()
                .documents.size

            playdates + additionalPlaydates
        } catch (e: Exception) {
            // Fallback to local database if Firestore fails
            val startTime = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay().toEpochSecond(ZoneOffset.UTC)
            val endTime = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC)
            playdateDao.getPlaydatesForDateRange(startTime, endTime).size
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getAvailableHours(): Int {
        return try {
            val userId = userRepository.getCurrentUserId() ?: return 0

            val timeslots = firestore.collection("users")
                .document(userId)
                .collection("recurring_timeslots")
                .get()
                .await()
                .toObjects(TimeslotEntity::class.java)

            timeslots.sumOf { timeslot ->
                val startTime = LocalTime.parse(timeslot.startTime)
                val endTime = LocalTime.parse(timeslot.endTime)
                ChronoUnit.HOURS.between(startTime, endTime).toInt()
            }
        } catch (e: Exception) {
            playdateDao.getAllTimeslots().first().sumOf { timeslot ->
                val startTime = LocalTime.parse(timeslot.startTime)
                val endTime = LocalTime.parse(timeslot.endTime)
                ChronoUnit.HOURS.between(startTime, endTime).toInt()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getCompletedPlaydatesCount(): Int {
        return try {
            val userId = userRepository.getCurrentUserId() ?: return 0

            val completedAsDog1 = firestore.collection("playdates")
                .whereEqualTo("dog1Id", userId)
                .whereEqualTo("status", PlaydateStatus.COMPLETED)
                .get()
                .await()
                .documents.size

            val completedAsDog2 = firestore.collection("playdates")
                .whereEqualTo("dog2Id", userId)
                .whereEqualTo("status", PlaydateStatus.COMPLETED)
                .get()
                .await()
                .documents.size

            completedAsDog1 + completedAsDog2
        } catch (e: Exception) {
            playdateDao.getPlaydatesByStatus(PlaydateStatus.COMPLETED).first().size
        }
    }

    suspend fun handlePlaydateRequest(
        requestId: String,
        response: RequestStatus,
        proposedChanges: PlaydateRequest? = null
    ) {
        try {
            val request = playdateDao.getPlaydateRequestById(requestId)
                ?: throw IllegalStateException("Request not found")

            when (response) {
                RequestStatus.ACCEPTED -> {
                    val updatedRequest = request.copy(status = RequestStatus.ACCEPTED)
                    playdateDao.updatePlaydateRequest(updatedRequest)
                    createConfirmedPlaydate(updatedRequest)
                }

                RequestStatus.DECLINED -> {
                    val updatedRequest = request.copy(status = RequestStatus.DECLINED)
                    playdateDao.updatePlaydateRequest(updatedRequest)
                }

                RequestStatus.PENDING -> {
                    proposedChanges?.let { changes ->
                        playdateDao.insertPlaydateRequest(changes)
                    }
                }

                else -> {} // Handle other states if needed
            }
        } catch (e: Exception) {
            throw e
        }
    }
}