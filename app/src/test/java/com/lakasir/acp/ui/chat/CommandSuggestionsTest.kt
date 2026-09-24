package com.lakasir.acp.ui.chat

import com.lakasir.acp.acp.AgentCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandSuggestionsTest {

    private val commands = listOf(
        AgentCommand("todo", "Track a task", "What should I track?"),
        AgentCommand("test", "Run tests", null),
        AgentCommand("commit", "Create a commit", "commit message"),
    )

    @Test
    fun `slash prefix activates suggestions`() {
        assertTrue(CommandSuggestions.isActive("/t"))
        assertFalse(CommandSuggestions.isActive("/"))
        assertFalse(CommandSuggestions.isActive("t"))
        assertFalse(CommandSuggestions.isActive("/todo done"))
    }

    @Test
    fun `filters commands by prefix`() {
        assertEquals(listOf("todo", "test"), CommandSuggestions.filter("/t", commands).map { it.name })
        assertEquals(listOf("commit"), CommandSuggestions.filter("/co", commands).map { it.name })
        assertTrue(CommandSuggestions.filter("/z", commands).isEmpty())
    }

    @Test
    fun `caps results at six`() {
        val many = (1..10).map { AgentCommand("cmd$it", "desc$it", null) }
        assertEquals(6, CommandSuggestions.filter("/cmd", many).size)
    }

    @Test
    fun `apply inserts command and trailing space`() {
        assertEquals("/todo ", CommandSuggestions.apply("/t", "todo"))
    }

    @Test
    fun `hint shown when command is selected`() {
        assertEquals("What should I track?", CommandSuggestions.hint("/todo ", commands))
        assertEquals("What should I track?", CommandSuggestions.hint("/todo", commands))
        assertNull(CommandSuggestions.hint("/test", commands))
        assertNull(CommandSuggestions.hint("/t", commands))
    }
}
