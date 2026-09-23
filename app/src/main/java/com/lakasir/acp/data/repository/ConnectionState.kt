package com.lakasir.acp.data.repository

import com.lakasir.acp.acp.AcpEvent
import com.lakasir.acp.acp.AgentInfo

sealed interface ConnectionState {
    val profileId: Long?

    data object Disconnected : ConnectionState {
        override val profileId: Long? = null
    }

    data class Connecting(override val profileId: Long, val attempt: Int) : ConnectionState
    data class Connected(override val profileId: Long, val agent: AgentInfo) : ConnectionState
    data class Error(override val profileId: Long, val message: String, val retryInMs: Long) : ConnectionState
}

data class PendingPermission(
    val localSessionId: Long,
    val sessionTitle: String,
    val messageId: Long,
    val request: AcpEvent.PermissionRequest,
)
