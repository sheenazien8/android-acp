package com.lakasir.acp.data

import com.lakasir.acp.data.local.MessageEntity
import com.lakasir.acp.data.local.MessageRole
import com.lakasir.acp.data.local.MessageType
import com.lakasir.acp.data.repository.AcpRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpRepositoryRulesTest {

    private fun message(role: MessageRole, type: MessageType) =
        MessageEntity(id = 1, sessionId = 1, role = role, type = type, content = "x", timestamp = 0)

    @Test
    fun `chunks append to the previous agent message of the same type`() {
        assertTrue(AcpRepository.canAppend(message(MessageRole.AGENT, MessageType.TEXT), MessageRole.AGENT, MessageType.TEXT))
    }

    @Test
    fun `chunks start a new row after a different message`() {
        assertFalse(AcpRepository.canAppend(null, MessageRole.AGENT, MessageType.TEXT))
        assertFalse(AcpRepository.canAppend(message(MessageRole.USER, MessageType.TEXT), MessageRole.AGENT, MessageType.TEXT))
        assertFalse(AcpRepository.canAppend(message(MessageRole.TOOL, MessageType.TOOL_CALL), MessageRole.AGENT, MessageType.TEXT))
        assertFalse(AcpRepository.canAppend(message(MessageRole.AGENT, MessageType.THOUGHT), MessageRole.AGENT, MessageType.TEXT))
    }

    @Test
    fun `backoff doubles and caps at thirty seconds`() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (1..7).map(AcpRepository::backoffMs))
    }
}
