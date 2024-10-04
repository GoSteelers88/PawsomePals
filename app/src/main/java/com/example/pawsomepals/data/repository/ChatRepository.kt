package com.example.pawsomepals.data.repository

import com.example.pawsomepals.data.model.Chat
import com.example.pawsomepals.data.model.Message
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val database: FirebaseDatabase
) {
    private val chatsRef = database.getReference("chats")
    private val messagesRef = database.getReference("messages")

    fun getChats(userId: String): Flow<List<Chat>> = callbackFlow {
        val listener = chatsRef.orderByChild("user1Id").equalTo(userId).addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val chats = snapshot.children.mapNotNull { it.getValue<Chat>() }
                    trySend(chats)
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    close(error.toException())
                }
            }
        )

        awaitClose { chatsRef.removeEventListener(listener) }
    }

    fun getMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val listener = messagesRef.child(chatId).orderByChild("timestamp").addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val messages = snapshot.children.mapNotNull { it.getValue<Message>() }
                    trySend(messages)
                }

                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            }
        )

        awaitClose { messagesRef.child(chatId).removeEventListener(listener) }
    }

    suspend fun sendMessage(chatId: String, senderId: String, content: String) {
        val message = Message(
            chatId = chatId,
            senderId = senderId,
            content = content
        )
        messagesRef.child(chatId).push().setValue(message).await()

        // Update last message in chat
        chatsRef.child(chatId).updateChildren(
            mapOf(
                "lastMessageTimestamp" to message.timestamp,
                "lastMessagePreview" to message.content
            )
        ).await()
    }

    suspend fun createChat(user1Id: String, user2Id: String): String {
        val chat = Chat(user1Id = user1Id, user2Id = user2Id)
        val chatRef = chatsRef.push()
        chatRef.setValue(chat).await()
        return chatRef.key ?: throw IllegalStateException("Failed to create chat")
    }
}