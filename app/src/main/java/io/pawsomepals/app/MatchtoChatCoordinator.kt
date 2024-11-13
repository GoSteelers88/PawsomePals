package io.pawsomepals.app



import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import io.pawsomepals.app.data.model.Chat
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.MatchStatus
import io.pawsomepals.app.data.model.MatchType
import io.pawsomepals.app.data.model.MessageType
import io.pawsomepals.app.data.model.PlaydateStatus
import io.pawsomepals.app.data.repository.ChatRepository
import io.pawsomepals.app.data.repository.DogProfileRepository
import io.pawsomepals.app.data.repository.MatchRepository
import io.pawsomepals.app.notification.NotificationManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatchToChatCoordinator @Inject constructor(
    private val matchRepository: MatchRepository,
    private val chatRepository: ChatRepository,
    private val dogProfileRepository: DogProfileRepository,
    private val notificationManager: NotificationManager,
    private val firestore: FirebaseFirestore
) {
    sealed class CoordinationResult {
        data class Success(val chatId: String) : CoordinationResult()
        data class Error(val error: MatchChatError) : CoordinationResult()
    }

    sealed class MatchChatError {
        data class MatchNotFound(val matchId: String) : MatchChatError()
        data class ChatCreationFailed(val reason: String) : MatchChatError()
        data class DogProfileNotFound(val userId: String) : MatchChatError()
        object InvalidMatchStatus : MatchChatError()
    }

    suspend fun initiateChatFromMatch(matchId: String): CoordinationResult {
        return try {
            // 1. Validate match exists and is active
            val match = matchRepository.getMatchById(matchId).getOrNull()
                ?: return CoordinationResult.Error(MatchChatError.MatchNotFound(matchId))

            if (match.status != MatchStatus.ACTIVE) {
                return CoordinationResult.Error(MatchChatError.InvalidMatchStatus)
            }

            // 2. Get dog profiles
            val dog1 = dogProfileRepository.getDogById(match.dog1Id).getOrNull()
                ?: return CoordinationResult.Error(MatchChatError.DogProfileNotFound(match.user1Id))
            val dog2 = dogProfileRepository.getDogById(match.dog2Id).getOrNull()
                ?: return CoordinationResult.Error(MatchChatError.DogProfileNotFound(match.user2Id))

            // 3. Create or get existing chat
            val chatId = getOrCreateChat(match)

            // 4. Send initial message if new chat
            if (chatId.isNotEmpty()) {
                sendWelcomeMessage(chatId, match, dog1, dog2)
            }

            // 5. Update match with chat reference
            updateMatchWithChat(match.id, chatId)

            CoordinationResult.Success(chatId)
        } catch (e: Exception) {
            CoordinationResult.Error(MatchChatError.ChatCreationFailed(e.message ?: "Unknown error"))
        }
    }

    private suspend fun getOrCreateChat(match: Match): String {
        return try {
            // First check if chat already exists
            val existingChat = chatRepository.findChatForMatch(match.id)
            if (existingChat != null) {
                return existingChat.id
            }

            // Create new chat if none exists
            chatRepository.createChat(
                user1Id = match.user1Id,
                user2Id = match.user2Id,
                matchId = match.id
            )
        } catch (e: Exception) {
            throw e
        }
    }

    private suspend fun sendWelcomeMessage(
        chatId: String,
        match: Match,
        dog1: Dog,
        dog2: Dog
    ) {
        val welcomeMessage = generateWelcomeMessage(match, dog1, dog2)
        chatRepository.sendMessage(
            chatId = chatId,
            senderId = "system",
            content = welcomeMessage,
            type = MessageType.SYSTEM,
            metadata = mapOf(
                "matchId" to match.id,
                "compatibilityScore" to match.compatibilityScore.toString(),
                "matchType" to match.matchType.name
            )
        )

        // Send notifications to both users
        notifyUsersOfMatch(match, dog1, dog2)
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

    private suspend fun notifyUsersOfMatch(match: Match, dog1: Dog, dog2: Dog) {
        // Notify both users
        listOf(
            Pair(match.user1Id, dog2.name),
            Pair(match.user2Id, dog1.name)
        ).forEach { (userId, otherDogName) ->
            notificationManager.sendMatchNotification(
                userId = userId,
                title = "New Match! 🎉",
                message = "Your dog matched with $otherDogName! Start chatting now!",
                data = mapOf(
                    "type" to "match",
                    "matchId" to match.id
                )
            )
        }
    }

    private suspend fun updateMatchWithChat(matchId: String, chatId: String) {
        firestore.collection("matches")
            .document(matchId)
            .update(
                mapOf(
                    "chatId" to chatId,
                    "chatCreatedAt" to System.currentTimeMillis()
                )
            ).await()
    }
}