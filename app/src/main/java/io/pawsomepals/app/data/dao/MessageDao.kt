package io.pawsomepals.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.pawsomepals.app.data.model.Message
import io.pawsomepals.app.data.model.MessageStatus
import io.pawsomepals.app.data.model.MessageType
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    // Basic CRUD Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<Message>)

    @Update
    suspend fun updateMessage(message: Message)

    @Delete
    suspend fun deleteMessage(message: Message)

    // Queries for getting messages
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(chatId: String): Message?

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): Message?

    // Unread messages
    @Query("""
        SELECT COUNT(*) FROM messages 
        WHERE chatId = :chatId 
        AND senderId != :currentUserId 
        AND status != 'READ'
    """)
    fun getUnreadMessageCount(chatId: String, currentUserId: String): Flow<Int>

    @Query("""
        UPDATE messages 
        SET status = 'READ' 
        WHERE chatId = :chatId 
        AND senderId != :currentUserId 
        AND status != 'READ'
    """)
    suspend fun markMessagesAsRead(chatId: String, currentUserId: String)

    // Message status updates
    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: MessageStatus)

    @Query("UPDATE messages SET status = :status WHERE chatId = :chatId AND status = :oldStatus")
    suspend fun updateAllMessageStatus(chatId: String, oldStatus: MessageStatus, status: MessageStatus)

    // Message type specific queries
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND type = :messageType ORDER BY timestamp DESC")
    fun getMessagesByType(chatId: String, messageType: MessageType): Flow<List<Message>>

    // Playdate-related messages
    @Query("""
        SELECT * FROM messages 
        WHERE chatId = :chatId 
        AND type = 'PLAYDATE_SUGGESTION' 
        ORDER BY timestamp DESC
    """)
    fun getPlaydateMessages(chatId: String): Flow<List<Message>>

    // Location sharing messages
    @Query("""
        SELECT * FROM messages 
        WHERE chatId = :chatId 
        AND type = 'LOCATION_SHARE' 
        ORDER BY timestamp DESC LIMIT 1
    """)
    suspend fun getLastSharedLocation(chatId: String): Message?

    // Cleanup queries
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllMessagesFromChat(chatId: String)

    @Query("DELETE FROM messages WHERE timestamp < :timestamp")
    suspend fun deleteMessagesOlderThan(timestamp: Long)

    // Analytics queries
    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    suspend fun getMessageCount(chatId: String): Int

    @Query("""
        SELECT COUNT(*) FROM messages 
        WHERE chatId = :chatId 
        AND timestamp BETWEEN :startTime AND :endTime
    """)
    suspend fun getMessageCountInTimeRange(chatId: String, startTime: Long, endTime: Long): Int

    // Media messages
    @Query("""
        SELECT * FROM messages 
        WHERE chatId = :chatId 
        AND type = 'IMAGE' 
        ORDER BY timestamp DESC
    """)
    fun getMediaMessages(chatId: String): Flow<List<Message>>

    // Search messages
    @Query("""
        SELECT * FROM messages 
        WHERE chatId = :chatId 
        AND content LIKE '%' || :query || '%' 
        ORDER BY timestamp DESC
    """)
    suspend fun searchMessages(chatId: String, query: String): List<Message>

    // Get messages before/after a specific message
    @Query("""
        SELECT * FROM messages 
        WHERE chatId = :chatId 
        AND timestamp < :timestamp 
        ORDER BY timestamp DESC LIMIT :limit
    """)
    suspend fun getMessagesBefore(chatId: String, timestamp: Long, limit: Int): List<Message>

    @Query("""
        SELECT * FROM messages 
        WHERE chatId = :chatId 
        AND timestamp > :timestamp 
        ORDER BY timestamp ASC LIMIT :limit
    """)
    suspend fun getMessagesAfter(chatId: String, timestamp: Long, limit: Int): List<Message>

    // Quick reply messages
    @Query("""
        SELECT * FROM messages 
        WHERE chatId = :chatId 
        AND type = 'QUICK_REPLY' 
        ORDER BY timestamp DESC
    """)
    fun getQuickReplyMessages(chatId: String): Flow<List<Message>>

    // Dog profile share messages
    @Query("""
        SELECT * FROM messages 
        WHERE chatId = :chatId 
        AND type = 'DOG_PROFILE_SHARE' 
        ORDER BY timestamp DESC
    """)
    fun getDogProfileShares(chatId: String): Flow<List<Message>>
}
