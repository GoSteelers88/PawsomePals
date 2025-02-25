package io.pawsomepals.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.snapshots
import io.pawsomepals.app.data.dao.ChatDao
import io.pawsomepals.app.data.model.Chat
import io.pawsomepals.app.data.model.Message
import io.pawsomepals.app.data.model.MessageType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val chatDao: ChatDao,
    private val auth: FirebaseAuth,
    private val dogProfileRepository: DogProfileRepository,
    private val matchRepository: MatchRepository
) {
    companion object {
        private const val COLLECTION_CHATS = "chats"
        private const val COLLECTION_MESSAGES = "messages"
    }
    private val messagesCollection = firestore.collection(COLLECTION_MESSAGES)
    private val chatsCollection = firestore.collection(COLLECTION_CHATS)

    fun getMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        Log.d("ChatRepository", "Setting up messages listener for chatId: $chatId")

        val query = messagesCollection
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp", Query.Direction.ASCENDING)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("ChatRepository", "Error listening to messages", error)
                return@addSnapshotListener
            }

            Log.d("ChatRepository", "Message snapshot received. Size: ${snapshot?.size()}")
            snapshot?.documents?.forEach { doc ->
                Log.d("ChatRepository", "Message doc: ${doc.data}")
            }

            val messages = snapshot?.toObjects(Message::class.java) ?: emptyList()
            trySend(messages).isSuccess
        }

        awaitClose { listener.remove() }
    }
    suspend fun findChatForMatch(matchId: String): Chat? {
        return firestore.collection("chats")
            .whereEqualTo("matchId", matchId)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.toObject(Chat::class.java)
    }

    suspend fun createChat(
        user1Id: String,
        user2Id: String,
        matchId: String
    ): String {
        val chat = Chat(
            id = UUID.randomUUID().toString(),
            user1Id = user1Id,
            user2Id = user2Id,
            matchId = matchId,
            created = System.currentTimeMillis()
        )

        firestore.collection("chats")
            .document(chat.id)
            .set(chat)
            .await()

        return chat.id
    }

    suspend fun createChat(
        user1Id: String,
        user2Id: String,
        matchId: String,
        dog1Id: String,  // Add these parameters
        dog2Id: String   // Add these parameters
    ): String {
        val chat = Chat(
            id = UUID.randomUUID().toString(),
            user1Id = user1Id,
            user2Id = user2Id,
            matchId = matchId,
            dog1Id = dog1Id,  // Add these fields
            dog2Id = dog2Id,  // Add these fields
            participants = listOf(user1Id, user2Id),
            created = System.currentTimeMillis()
        )

        firestore.collection("chats")
            .document(chat.id)
            .set(chat)
            .await()

        return chat.id
    }

    fun observeChats(): Flow<List<Chat>> = flow {
        val userId = auth.currentUser?.uid ?: return@flow

        try {
            // First emit from local database
            emitAll(chatDao.getChatsForUser(userId))

            // Then set up Firestore listener
            firestore.collection(COLLECTION_CHATS)
                .whereArrayContains("participants", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener

                    snapshot?.documents?.forEach { doc ->
                        doc.toObject(Chat::class.java)?.let { chat ->
                            runBlocking {
                                chatDao.insertChat(chat)
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error observing chats", e)
        }
    }

    suspend fun updateLastMessage(
        chatId: String,
        message: Message,
        isFromCurrentUser: Boolean
    ) {
        val updates = mapOf(
            "lastMessageTimestamp" to message.timestamp,
            "lastMessagePreview" to message.content,
            "lastMessageType" to message.type,
            "hasUnreadMessages" to !isFromCurrentUser
        )

        try {
            // Update Firestore
            firestore.collection(COLLECTION_CHATS)
                .document(chatId)
                .update(updates)
                .await()

            // Update local database
            chatDao.updateLastMessage(
                chatId = chatId,
                timestamp = message.timestamp,
                preview = message.content,
                type = message.type,
                hasUnread = !isFromCurrentUser
            )
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error updating last message", e)
        }
    }
    suspend fun updatePlaydateStatus(chatId: String, status: Chat.PlaydateStatus) {
        try {
            // Update Firestore
            firestore.collection(COLLECTION_CHATS)
                .document(chatId)
                .update("playdateStatus", status.name)
                .await()

            // Update local database
            chatDao.updatePlaydateStatus(chatId, status)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error updating playdate status", e)
        }
    }
    suspend fun markChatRead(chatId: String) {
        try {
            // Update Firestore
            firestore.collection(COLLECTION_CHATS)
                .document(chatId)
                .update("hasUnreadMessages", false)
                .await()

            // Update local database
            chatDao.updateUnreadStatus(chatId, false)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error marking chat as read", e)
        }
    }



    // In ChatRepository.kt
    suspend fun sendMessage(
        chatId: String,
        senderId: String,
        content: String,
        type: MessageType = MessageType.TEXT,
        metadata: Map<String, Any>? = null
    ) {
        // Get user's display name
        val senderName = try {
            firestore.collection("users")
                .document(senderId)
                .get()
                .await()
                .getString("displayName") ?: "Anonymous"
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error fetching sender name", e)
            "Anonymous"
        }

        // Generate a timestamp-based message ID
        val timestamp = System.currentTimeMillis()
        val messageId = "${chatId}_${timestamp}_${senderId}"

        val message = Message(
            id = messageId,
            chatId = chatId,
            senderId = senderId,
            senderName = senderName,
            content = content,
            type = type,
            metadata = metadata,
            timestamp = timestamp
        )

        // Add message to Firestore using the generated ID
        messagesCollection
            .document(messageId)  // Use the specific ID instead of auto-generating
            .set(message)         // Use set instead of add
            .await()

        // Update last message in chat
        chatsCollection.document(chatId).update(
            mapOf(
                "lastMessageTimestamp" to message.timestamp,
                "lastMessagePreview" to message.content,
                "lastMessageType" to type
            )
        ).await()
    }
    // Add to ChatRepository.kt
    suspend fun deleteMessage(chatId: String, messageId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val message = messagesCollection.document(messageId).get().await()

        if (message.getString("senderId") == currentUserId) {
            messagesCollection.document(messageId).delete().await()
        }
    }

    suspend fun updateTypingStatus(chatId: String, isTyping: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return
        chatsCollection.document(chatId)
            .update("typingUsers.$currentUserId", isTyping)
            .await()
    }
    suspend fun getAllChats(): Flow<List<Chat>> {
        val currentUser = auth.currentUser?.uid
        Log.d("ChatRepository", "Getting chats - Current User ID: $currentUser")

        if (currentUser == null) {
            Log.d("ChatRepository", "No authenticated user found, returning empty flow")
            return flow { emit(emptyList()) }
        }

        return firestore.collection("chats")
            .whereArrayContains("participants", currentUser)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                if (snapshot.isEmpty) {
                    Log.d("ChatRepository", "Firestore returned empty snapshot")
                } else {
                    Log.d("ChatRepository", """
                    Firestore returned documents:
                    Count: ${snapshot.size()}
                    Documents: ${snapshot.documents.map {
                        "ID: ${it.id}, Participants: ${it.get("participants")}"
                    }}
                """.trimIndent())
                }

                val chats = snapshot.toObjects(Chat::class.java)
                Log.d("ChatRepository", "Converted to Chat objects: ${chats.size}")
                chats
            }
    }

    suspend fun markMessagesAsRead(chatId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        messagesCollection
            .whereEqualTo("chatId", chatId)
            .orderBy("senderId")
            .whereNotEqualTo("senderId", currentUserId)
            .get()
            .await()
            .documents
            .forEach { doc ->
                doc.reference.update("status", "READ")
            }
    }


    suspend fun createChat(user1Id: String, user2Id: String): String {
        val chat = Chat(
            user1Id = user1Id,
            user2Id = user2Id,
            participants = listOf(user1Id, user2Id),
            created = System.currentTimeMillis()
        )
        val docRef = chatsCollection.add(chat).await()
        return docRef.id
    }
    suspend fun deleteChat(chatId: String) {
        try {
            // Delete from Firestore
            firestore.collection(COLLECTION_CHATS)
                .document(chatId)
                .delete()
                .await()

            // Delete messages
            firestore.collection(COLLECTION_MESSAGES)
                .whereEqualTo("chatId", chatId)
                .get()
                .await()
                .documents
                .forEach { it.reference.delete().await() }

            // Delete from local database
            chatDao.deleteChat(chatId)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error deleting chat", e)
        }
    }
}
