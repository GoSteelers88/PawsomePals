package io.pawsomepals.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.pawsomepals.app.data.model.Chat
import io.pawsomepals.app.data.model.Message
import io.pawsomepals.app.data.model.MessageType
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats WHERE user1Id = :userId OR user2Id = :userId")
    fun getChatsByUser(userId: String): Flow<List<Chat>>


    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC")
    fun getMessagesByChatId(chatId: String): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Query("SELECT * FROM chats WHERE user1Id = :userId OR user2Id = :userId")
    fun getChatsForUser(userId: String): Flow<List<Chat>>

    @Query("UPDATE chats SET lastMessageTimestamp = :timestamp, lastMessagePreview = :preview, lastMessageType = :type, hasUnreadMessages = :hasUnread WHERE id = :chatId")
    suspend fun updateLastMessage(chatId: String, timestamp: Long, preview: String, type: MessageType, hasUnread: Boolean)

    @Query("UPDATE chats SET playdateStatus = :status WHERE id = :chatId")
    suspend fun updatePlaydateStatus(chatId: String, status: Chat.PlaydateStatus)

    @Query("UPDATE chats SET hasUnreadMessages = :unread WHERE id = :chatId")
    suspend fun updateUnreadStatus(chatId: String, unread: Boolean)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: Chat)


}