package io.pawsomepals.app.coordinator

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.firebase.auth.FirebaseAuth
import io.pawsomepals.app.data.model.*
import io.pawsomepals.app.data.repository.DogProfileRepository
import io.pawsomepals.app.data.repository.MatchRepository
import io.pawsomepals.app.data.repository.PlaydateRepository
import io.pawsomepals.app.data.repository.UserRepository
import io.pawsomepals.app.notification.NotificationManager
import io.pawsomepals.app.service.location.LocationSuggestionService
import kotlinx.coroutines.flow.*
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaydateCoordinator @Inject constructor(
    private val matchRepository: MatchRepository,
    private val playdateRepository: PlaydateRepository,
    private val locationSuggestionService: LocationSuggestionService,
    private val notificationManager: NotificationManager,
    private val userRepository: UserRepository,
    private val dogProfileRepository: DogProfileRepository, // Add this

    private val auth: FirebaseAuth
) {
    sealed class CoordinationResult {
        data class Success(val playdate: Playdate) : CoordinationResult()
        data class Error(val error: PlaydateError) : CoordinationResult()
        object InProgress : CoordinationResult()
    }

    private suspend fun Match.getOtherDog(currentDogId: String, dogProfileRepository: DogProfileRepository): Dog {
        val otherDogId = if (dog1Id == currentDogId) dog2Id else dog1Id
        return dogProfileRepository.getDogById(otherDogId).getOrNull()
            ?: throw IllegalStateException("Other dog not found")
    }
    interface LocationSuggestionService {
        suspend fun validateLocation(location: DogFriendlyLocation)
    }

    // Add these missing functions to NotificationManager
    interface NotificationManager {
        suspend fun sendPlaydateRequestNotification(requestId: String, dogName: String)
        interface NotificationManager {
            suspend fun sendMatchNotification(
                userId: String,
                title: String,
                message: String,
                data: Map<String, String>
            )
        }    }


    private fun PlaydateRequest.toPlaydate(): Playdate {
        return Playdate(
            id = id,
            matchId = matchId,
            dog1Id = requesterId,
            dog2Id = receiverId,
            scheduledTime = suggestedTimeslots.firstOrNull() ?: 0L,
            location = selectedLocationId ?: "",
            status = PlaydateStatus.PENDING,
            createdBy = requesterId
        )
    }

    sealed class PlaydateError {
        data class MatchNotFound(val matchId: String) : PlaydateError()
        data class InvalidMatch(val reason: String) : PlaydateError()
        data class LocationError(val reason: String) : PlaydateError()
        data class SchedulingError(val reason: String) : PlaydateError()
        data class ValidationError(val reason: String) : PlaydateError()
        data class AuthenticationError(val reason: String) : PlaydateError()
    }

    data class PlaydateContext(
        val matchDetails: Match.MatchWithDetails,
        val selectedLocation: DogFriendlyLocation? = null,
        val proposedTime: LocalDateTime? = null,
        val status: PlaydateStatus = PlaydateStatus.PENDING,
        val participants: Set<String> = emptySet()
    )

    private val _coordinationState = MutableStateFlow<PlaydateContext?>(null)
    val coordinationState: StateFlow<PlaydateContext?> = _coordinationState.asStateFlow()

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun initiatePlaydateFromMatch(matchId: String): Flow<CoordinationResult> = flow {
        emit(CoordinationResult.InProgress)

        try {
            val currentUserId = auth.currentUser?.uid
                ?: throw IllegalStateException("No authenticated user")

            // Get match details
            val matchResult = matchRepository.getMatchById(matchId)
            val match = matchResult.getOrNull()
                ?: throw IllegalStateException("Match not found: $matchId")

            // Validate match status
            if (match.status != MatchStatus.ACTIVE) {
                throw IllegalStateException("Match is not active")
            }

            // Get current user's dog
            val currentDog = userRepository.getCurrentUserDog()
                ?: throw IllegalStateException("Current user's dog not found")

            // Create match details
            val matchDetails = Match.MatchWithDetails(
                match = match,
                otherDog = match.getOtherDog(currentDog.id, dogProfileRepository), // Use dogProfileRepository instead of userRepository
                distanceAway = match.locationDistance?.let { "${it.toInt()} km" } ?: "Unknown"
            )

            // Update coordination state
            _coordinationState.value = PlaydateContext(
                matchDetails = matchDetails,
                participants = setOf(match.user1Id, match.user2Id)
            )

            emit(CoordinationResult.Success(
                Playdate(
                    id = matchId,
                    matchId = matchId,
                    dog1Id = match.dog1Id,
                    dog2Id = match.dog2Id,
                    status = PlaydateStatus.PENDING,
                    createdBy = currentUserId
                )
            ))

        } catch (e: Exception) {
            emit(CoordinationResult.Error(PlaydateError.InvalidMatch(e.message ?: "Unknown error")))
            _coordinationState.value = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun proposePlaydateTime(
        dateTime: LocalDateTime
    ): Flow<CoordinationResult> = flow {
        emit(CoordinationResult.InProgress)

        try {
            val context = _coordinationState.value
                ?: throw IllegalStateException("No active playdate coordination")

            // Validate proposed time
            validateProposedTime(dateTime, context.participants)

            // Update context with proposed time
            _coordinationState.value = context.copy(proposedTime = dateTime)

            // Create or update playdate request
            if (context.selectedLocation != null) {
                val request = createPlaydateRequest(context, dateTime)
                emit(CoordinationResult.Success(request))
            } else {
                emit(CoordinationResult.Success(
                    createPartialPlaydate(context.matchDetails.match, dateTime)
                ))
            }

        } catch (e: Exception) {
            emit(CoordinationResult.Error(PlaydateError.SchedulingError(e.message ?: "Unknown error")))
        }
    }

    suspend fun proposePlaydateLocation(
        location: DogFriendlyLocation
    ): Flow<CoordinationResult> = flow {
        emit(CoordinationResult.InProgress)

        try {
            val context = _coordinationState.value
                ?: throw IllegalStateException("No active playdate coordination")

            // Validate location
            validateLocation(location, context.participants)

            // Update context with selected location
            _coordinationState.value = context.copy(selectedLocation = location)

            // Create or update playdate request if we have both time and location
            if (context.proposedTime != null) {
                val request = createPlaydateRequest(context, context.proposedTime)
                emit(CoordinationResult.Success(request))
            } else {
                emit(CoordinationResult.Success(
                    createPartialPlaydate(context.matchDetails.match, location = location)
                ))
            }

        } catch (e: Exception) {
            emit(CoordinationResult.Error(PlaydateError.LocationError(e.message ?: "Unknown error")))
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun finalizePlaydate(): Flow<CoordinationResult> = flow {
        emit(CoordinationResult.InProgress)

        try {
            val context = _coordinationState.value
                ?: throw IllegalStateException("No active playdate coordination")

            // Validate we have all required information
            if (context.selectedLocation == null || context.proposedTime == null) {
                throw IllegalStateException("Missing required playdate details")
            }

            // Create final playdate request
            val request = createPlaydateRequest(context, context.proposedTime)

            // Send notifications
            notificationManager.sendPlaydateRequestNotification(
                request.id,
                context.matchDetails.otherDog.name
            )

            // Clear coordination state
            _coordinationState.value = null

            emit(CoordinationResult.Success(request))

        } catch (e: Exception) {
            emit(CoordinationResult.Error(PlaydateError.ValidationError(e.message ?: "Unknown error")))
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun validateProposedTime(
        dateTime: LocalDateTime,
        participants: Set<String>
    ) {
        // Check if time is in the future
        if (dateTime.isBefore(LocalDateTime.now())) {
            throw IllegalArgumentException("Proposed time must be in the future")
        }

        // Check availability for all participants
        participants.forEach { userId ->
            val availability = playdateRepository.getUserAvailabilityForWeek(userId)
            // Implement availability checking logic
        }
    }

    private suspend fun validateLocation(
        location: DogFriendlyLocation,
        participants: Set<String>
    ) {
        // Verify location exists and is valid
        locationSuggestionService.validateLocation(location)

        // Check if location is accessible to all participants
        participants.forEach { userId ->
            // Implement distance/accessibility checking logic
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun createPlaydateRequest(
        context: PlaydateContext,
        dateTime: LocalDateTime
    ): Playdate {
        val location = context.selectedLocation
            ?: throw IllegalStateException("Location required for playdate request")

        return playdateRepository.createPlaydateRequest(
            match = context.matchDetails,
            location = location,
            dateTime = dateTime
        ).toPlaydate()
    }

    private fun createPartialPlaydate(
        match: Match,
        dateTime: LocalDateTime? = null,
        location: DogFriendlyLocation? = null
    ): Playdate {
        return Playdate(
            id = match.id,
            matchId = match.id,
            dog1Id = match.dog1Id,
            dog2Id = match.dog2Id,
            scheduledTime = dateTime?.toEpochSecond(ZoneOffset.UTC)?.times(1000) ?: 0L,
            location = location?.placeId ?: "",
            status = PlaydateStatus.PENDING,
            createdBy = auth.currentUser?.uid ?: throw IllegalStateException("No authenticated user")
        )
    }

    fun cancelCoordination() {
        _coordinationState.value = null
    }
}