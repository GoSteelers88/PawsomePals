package com.example.pawsomepals.data.dao

import androidx.room.*
import com.example.pawsomepals.data.model.Chat
import com.example.pawsomepals.data.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats WHERE user1Id = :userId OR user2Id = :userId")
    fun getChatsByUser(userId: String): Flow<List<Chat>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: Chat)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC")
    fun getMessagesByChatId(chatId: String): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)
}