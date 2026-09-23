package com.lakasir.acp.service

import com.lakasir.acp.acp.AgentInfo
import com.lakasir.acp.data.repository.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionSummaryTest {

    private val connected = ConnectionState.Connected(1, AgentInfo(1, "agent", "1.0", loadSession = false))

    @Test
    fun `all connected`() {
        assertEquals("1 connected", ConnectionNotifications.summary(setOf(1), mapOf(1L to connected)))
    }

    @Test
    fun `reconnecting profiles without state are counted`() {
        assertEquals("2 reconnecting", ConnectionNotifications.summary(setOf(1, 2), emptyMap()))
    }

    @Test
    fun `mixed states`() {
        val states = mapOf(
            1L to connected,
            2L to ConnectionState.Error(2, "closed", 1_000),
            3L to ConnectionState.Connecting(3, 0),
        )
        assertEquals("1 connected · 2 reconnecting", ConnectionNotifications.summary(setOf(1, 2, 3), states))
    }

    @Test
    fun `inactive profiles are ignored`() {
        assertEquals("1 connected", ConnectionNotifications.summary(setOf(1), mapOf(1L to connected, 2L to connected.copy(profileId = 2))))
    }
}
