package com.lakasir.acp.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionProfileDao {
    @Query("SELECT * FROM connection_profiles ORDER BY COALESCE(lastConnectedAt, createdAt) DESC")
    fun observeAll(): Flow<List<ConnectionProfileEntity>>

    @Query("SELECT * FROM connection_profiles WHERE id = :id")
    fun observe(id: Long): Flow<ConnectionProfileEntity?>

    @Query("SELECT * FROM connection_profiles WHERE id = :id")
    suspend fun get(id: Long): ConnectionProfileEntity?

    @Insert
    suspend fun insert(profile: ConnectionProfileEntity): Long

    @Update
    suspend fun update(profile: ConnectionProfileEntity)

    @Delete
    suspend fun delete(profile: ConnectionProfileEntity)

    @Query("UPDATE connection_profiles SET lastConnectedAt = :timestamp WHERE id = :id")
    suspend fun touchLastConnected(id: Long, timestamp: Long)
}

@Dao
interface SessionDao {
    @Query(
        """
        SELECT s.*, (
            SELECT m.content FROM messages m
            WHERE m.sessionId = s.id AND m.type = 'TEXT'
            ORDER BY m.id DESC LIMIT 1
        ) AS preview
        FROM sessions s
        WHERE s.connectionProfileId = :profileId
        ORDER BY s.updatedAt DESC
        """
    )
    fun observeSummaries(profileId: Long): Flow<List<SessionSummary>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE connectionProfileId = :profileId AND remoteSessionId = :remoteSessionId LIMIT 1")
    suspend fun findByRemoteId(profileId: Long, remoteSessionId: String): SessionEntity?

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sessions SET updatedAt = :timestamp WHERE id = :id")
    suspend fun touch(id: Long, timestamp: Long)

    @Query("UPDATE sessions SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE sessions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: SessionStatus)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp, id")
    fun observeBySession(sessionId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY id DESC LIMIT 1")
    suspend fun last(sessionId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND toolCallId = :toolCallId AND type = 'TOOL_CALL' LIMIT 1")
    suspend fun findToolCall(sessionId: Long, toolCallId: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId AND role = 'USER'")
    suspend fun countUserMessages(sessionId: Long): Int

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("UPDATE messages SET content = content || :text WHERE id = :id")
    suspend fun appendContent(id: Long, text: String)
}
