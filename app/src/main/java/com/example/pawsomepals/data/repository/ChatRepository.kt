package com.example.pawsomepals.data.repository

import com.example.pawsomepals.data.model.Chat
import com.example.pawsomepals.data.model.Message
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val chatsCollection = firestore.collection("chats")
    private val messagesCollection = firestore.collection("messages")

    fun getChats(userId: String): Flow<List<Chat>> = callbackFlow {
        val listener = chatsCollection
            .whereEqualTo("user1Id", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val chats = snapshot?.toObjects(Chat::class.java) ?: emptyList()
                trySend(chats)
            }

        awaitClose { listener.remove() }
    }

    fun getMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val listener = messagesCollection
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.toObjects(Message::class.java) ?: emptyList()
                trySend(messages)
            }

        awaitClose { listener.remove() }
    }

    suspend fun sendMessage(chatId: String, senderId: String, content: String) {
        val message = Message(
            chatId = chatId,
            senderId = senderId,
            content = content,
            timestamp = System.currentTimeMillis()
        )

        // Add message to Firestore
        messagesCollection.add(message).await()

        // Update last message in chat
        chatsCollection.document(chatId).update(
            mapOf(
                "lastMessageTimestamp" to message.timestamp,
                "lastMessagePreview" to message.content
            )
        ).await()
    }

    suspend fun createChat(user1Id: String, user2Id: String): String {
        val chat = Chat(user1Id = user1Id, user2Id = user2Id)
        val docRef = chatsCollection.add(chat).await()
        return docRef.id
    }
}