package com.lakasir.acp.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "connection_profiles")
data class ConnectionProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val cwd: String,
    val createdAt: Long,
    val lastConnectedAt: Long? = null,
)

enum class SessionStatus { ACTIVE, CLOSED, ERROR }

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ConnectionProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["connectionProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("connectionProfileId"), Index("remoteSessionId")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteSessionId: String,
    val connectionProfileId: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: SessionStatus = SessionStatus.ACTIVE,
)

enum class MessageRole { USER, AGENT, SYSTEM, TOOL }

enum class MessageType { TEXT, THOUGHT, TOOL_CALL, PLAN, PERMISSION_REQUEST, ERROR }

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index(value = ["sessionId", "toolCallId"])],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: MessageRole,
    val type: MessageType,
    val content: String,
    val toolCallId: String? = null,
    val timestamp: Long,
    val rawJson: String? = null,
)

data class SessionSummary(
    @Embedded val session: SessionEntity,
    @ColumnInfo(name = "preview") val preview: String?,
)
