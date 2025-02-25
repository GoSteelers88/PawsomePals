package io.pawsomepals.app



import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.MatchStatus
import io.pawsomepals.app.data.model.MessageType
import io.pawsomepals.app.data.repository.ChatRepository
import io.pawsomepals.app.data.repository.DogProfileRepository
import io.pawsomepals.app.data.repository.MatchRepository
import io.pawsomepals.app.notification.NotificationManager
import kotlinx.coroutines.tasks.await
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
    // Add this sealed class back
    sealed class MatchChatError {
        data class MatchNotFound(val matchId: String) : MatchChatError()
        data class ChatCreationFailed(val reason: String) : MatchChatError()
        data class DogProfileNotFound(val userId: String) : MatchChatError()
        object InvalidMatchStatus : MatchChatError()
    }



    suspend fun initiateChatFromMatch(matchId: String): CoordinationResult {
        return try {
            Log.d("MatchToChatCoordinator", "Starting chat initiation for match: $matchId")

            // 1. Validate match exists and is active
            val match = matchRepository.getMatchById(matchId).getOrNull()
            if (match == null) {
                Log.e("MatchToChatCoordinator", "Match not found: $matchId")
                return CoordinationResult.Error(MatchChatError.MatchNotFound(matchId))
            }

            Log.d("MatchToChatCoordinator", "Match found: ${match.id}, status: ${match.status}")

            if (match.status != MatchStatus.ACTIVE) {
                Log.e("MatchToChatCoordinator", "Invalid match status: ${match.status}")
                return CoordinationResult.Error(MatchChatError.InvalidMatchStatus)
            }

            // 2. Get dog profiles with logging
            val dog1 = dogProfileRepository.getDogById(match.dog1Id).getOrNull()
            val dog2 = dogProfileRepository.getDogById(match.dog2Id).getOrNull()

            Log.d("MatchToChatCoordinator", "Dog profiles - Dog1: ${dog1?.id}, Dog2: ${dog2?.id}")

            if (dog1 == null) {
                Log.e("MatchToChatCoordinator", "Dog1 not found: ${match.dog1Id}")
                return CoordinationResult.Error(MatchChatError.DogProfileNotFound(match.user1Id))
            }
            if (dog2 == null) {
                Log.e("MatchToChatCoordinator", "Dog2 not found: ${match.dog2Id}")
                return CoordinationResult.Error(MatchChatError.DogProfileNotFound(match.user2Id))
            }

            // 3. Create or get existing chat with logging
            Log.d("MatchToChatCoordinator", "Getting or creating chat for match: ${match.id}")
            val chatId = getOrCreateChat(match)
            Log.d("MatchToChatCoordinator", "Chat ID obtained: $chatId")

            // 4. Send welcome message if chat exists
            if (chatId.isNotEmpty()) {
                Log.d("MatchToChatCoordinator", "Sending welcome message to chat: $chatId")
                sendWelcomeMessage(chatId, match, dog1, dog2)
            } else {
                Log.e("MatchToChatCoordinator", "Empty chat ID returned")
                return CoordinationResult.Error(MatchChatError.ChatCreationFailed("Empty chat ID"))
            }

            // 5. Update match with chat reference
            Log.d("MatchToChatCoordinator", "Updating match with chat reference")
            updateMatchWithChat(match.id, chatId)

            Log.d("MatchToChatCoordinator", "Chat initiation complete. ChatId: $chatId")
            CoordinationResult.Success(chatId)
        } catch (e: Exception) {
            Log.e("MatchToChatCoordinator", "Error initiating chat", e)
            CoordinationResult.Error(MatchChatError.ChatCreationFailed(e.message ?: "Unknown error"))
        }
    }



    private suspend fun getOrCreateChat(match: Match): String {
        return try {
            Log.d("MatchToChatCoordinator", "Checking for existing chat for match: ${match.id}")
            val existingChat = chatRepository.findChatForMatch(match.id)

            if (existingChat != null) {
                Log.d("MatchToChatCoordinator", "Found existing chat: ${existingChat.id}")
                return existingChat.id
            }

            Log.d("MatchToChatCoordinator", "Creating new chat for match: ${match.id}")
            val chatId = chatRepository.createChat(
                user1Id = match.user1Id,
                user2Id = match.user2Id,
                matchId = match.id,
                dog1Id = match.dog1Id,  // Add these
                dog2Id = match.dog2Id   // Add these
            )

            if (chatId.isEmpty()) {
                Log.e("MatchToChatCoordinator", "Empty chat ID returned from creation")
                throw IllegalStateException("Failed to create chat - empty ID returned")
            }

            Log.d("MatchToChatCoordinator", "Created new chat: $chatId")
            chatId
        } catch (e: Exception) {
            Log.e("MatchToChatCoordinator", "Error in getOrCreateChat", e)
            throw e
        }
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

    private suspend fun sendWelcomeMessage(chatId: String, match: Match, dog1: Dog, dog2: Dog) {
        try {
            Log.d("MatchToChatCoordinator", "Generating welcome message for chat: $chatId")
            val welcomeMessage = generateWelcomeMessage(match, dog1, dog2)

            Log.d("MatchToChatCoordinator", "Sending welcome message to chat: $chatId")
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
            Log.d("MatchToChatCoordinator", "Welcome message sent successfully")

            notifyUsersOfMatch(match, dog1, dog2)
        } catch (e: Exception) {
            Log.e("MatchToChatCoordinator", "Error sending welcome message", e)
            throw e
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