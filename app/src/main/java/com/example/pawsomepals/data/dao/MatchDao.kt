package com.example.pawsomepals.data.dao

import androidx.room.*
import com.example.pawsomepals.data.model.Match
import com.example.pawsomepals.data.model.MatchStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {
    // Basic CRUD Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: Match)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatches(matches: List<Match>)

    @Update
    suspend fun updateMatch(match: Match)

    @Delete
    suspend fun deleteMatch(match: Match)

    // Queries for getting matches
    @Query("SELECT * FROM matches WHERE id = :matchId")
    suspend fun getMatchById(matchId: String): Match?

    @Query("""
        SELECT * FROM matches 
        WHERE (dog1Id = :dogId OR dog2Id = :dogId) 
        AND status = :status 
        ORDER BY timestamp DESC
    """)
    fun getMatchesForDogByStatus(dogId: String, status: MatchStatus): Flow<List<Match>>

    @Query("""
        SELECT * FROM matches 
        WHERE (dog1Id = :dogId OR dog2Id = :dogId) 
        AND status IN (:statuses) 
        ORDER BY timestamp DESC
    """)
    fun getMatchesForDogByStatuses(dogId: String, statuses: List<MatchStatus>): Flow<List<Match>>

    // Active matches
    @Query("""
        SELECT * FROM matches 
        WHERE (dog1Id = :dogId OR dog2Id = :dogId) 
        AND status = 'ACTIVE' 
        ORDER BY lastInteractionTimestamp DESC
    """)
    fun getActiveMatches(dogId: String): Flow<List<Match>>

    // Pending matches
    @Query("""
        SELECT * FROM matches 
        WHERE (dog1Id = :dogId OR dog2Id = :dogId) 
        AND status = 'PENDING' 
        ORDER BY timestamp DESC
    """)
    fun getPendingMatches(dogId: String): Flow<List<Match>>

    // Get matches with unread messages
    @Query("""
        SELECT * FROM matches 
        WHERE (dog1Id = :dogId OR dog2Id = :dogId) 
        AND hasUnreadMessages = 1 
        ORDER BY lastInteractionTimestamp DESC
    """)
    fun getMatchesWithUnreadMessages(dogId: String): Flow<List<Match>>

    // Update match status
    @Query("UPDATE matches SET status = :status WHERE id = :matchId")
    suspend fun updateMatchStatus(matchId: String, status: MatchStatus)

    // Update last interaction
    @Query("""
        UPDATE matches 
        SET lastInteractionTimestamp = :timestamp 
        WHERE id = :matchId
    """)
    suspend fun updateLastInteraction(matchId: String, timestamp: Long = System.currentTimeMillis())

    // Handle expired matches
    @Query("""
        UPDATE matches 
        SET status = 'EXPIRED' 
        WHERE status = 'PENDING' 
        AND timestamp < :expiryTime
    """)
    suspend fun updateExpiredMatches(expiryTime: Long)

    // Clean up old matches
    @Query("DELETE FROM matches WHERE timestamp < :timestamp AND status IN ('EXPIRED', 'DECLINED')")
    suspend fun deleteOldMatches(timestamp: Long)

    // Statistics queries
    @Query("""
        SELECT COUNT(*) 
        FROM matches 
        WHERE (dog1Id = :dogId OR dog2Id = :dogId) 
        AND status = 'ACTIVE'
    """)
    suspend fun getActiveMatchCount(dogId: String): Int

    @Query("""
        SELECT AVG(compatibilityScore) 
        FROM matches 
        WHERE (dog1Id = :dogId OR dog2Id = :dogId) 
        AND status = 'ACTIVE'
    """)
    suspend fun getAverageCompatibilityScore(dogId: String): Double?

    // Archived matches
    @Query("""
        SELECT * FROM matches 
        WHERE (dog1Id = :dogId OR dog2Id = :dogId) 
        AND isArchived = 1 
        ORDER BY timestamp DESC
    """)
    fun getArchivedMatches(dogId: String): Flow<List<Match>>

    @Query("UPDATE matches SET isArchived = 1 WHERE id = :matchId")
    suspend fun archiveMatch(matchId: String)

    @Query("UPDATE matches SET isArchived = 0 WHERE id = :matchId")
    suspend fun unarchiveMatch(matchId: String)

    // Hidden matches
    @Query("""
        SELECT * FROM matches 
        WHERE (dog1Id = :dogId OR dog2Id = :dogId) 
        AND isHidden = 1 
        ORDER BY timestamp DESC
    """)
    fun getHiddenMatches(dogId: String): Flow<List<Match>>

    @Query("UPDATE matches SET isHidden = 1 WHERE id = :matchId")
    suspend fun hideMatch(matchId: String)

    @Query("UPDATE matches SET isHidden = 0 WHERE id = :matchId")
    suspend fun unhideMatch(matchId: String)

    // Message status
    @Query("UPDATE matches SET hasUnreadMessages = 1 WHERE id = :matchId")
    suspend fun markHasUnreadMessages(matchId: String)

    @Query("UPDATE matches SET hasUnreadMessages = 0 WHERE id = :matchId")
    suspend fun markMessagesRead(matchId: String)

    // Batch operations
    @Transaction
    suspend fun updateMatchesStatus(matchIds: List<String>, status: MatchStatus) {
        matchIds.forEach { matchId ->
            updateMatchStatus(matchId, status)
        }
    }

    // Complex queries
    @Query("""
        SELECT * FROM matches 
        WHERE (dog1Id = :dogId OR dog2Id = :dogId) 
        AND status = 'ACTIVE' 
        AND lastInteractionTimestamp > :since 
        ORDER BY lastInteractionTimestamp DESC
    """)
    fun getRecentActiveMatches(dogId: String, since: Long): Flow<List<Match>>

    @Query("""
        SELECT * FROM matches 
        WHERE (dog1Id = :dogId OR dog2Id = :dogId) 
        AND compatibilityScore >= :minScore 
        ORDER BY compatibilityScore DESC
    """)
    fun getHighCompatibilityMatches(dogId: String, minScore: Double = 0.8): Flow<List<Match>>
}