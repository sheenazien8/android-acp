package com.lakasir.acp.acp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter

class AcpSession(
    val sessionId: String,
    private val client: AcpClient,
) {
    val events: Flow<AcpEvent> = client.events.filter { it.sessionId == sessionId }

    suspend fun prompt(text: String): String? = client.prompt(sessionId, text)

    fun cancel(): Boolean = client.cancel(sessionId)
}
