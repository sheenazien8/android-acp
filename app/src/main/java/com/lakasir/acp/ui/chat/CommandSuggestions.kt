package com.lakasir.acp.ui.chat

import com.lakasir.acp.acp.AgentCommand

object CommandSuggestions {

    fun isActive(input: String): Boolean {
        if (!input.startsWith("/")) return false
        val tail = input.substring(1)
        return tail.isNotEmpty() && !tail.contains(" ")
    }

    fun filter(input: String, commands: List<AgentCommand>): List<AgentCommand> {
        if (!isActive(input)) return emptyList()
        val prefix = input.substring(1).lowercase()
        return commands.filter { it.name.lowercase().startsWith(prefix) }.take(6)
    }

    fun apply(input: String, name: String): String = "/$name "

    fun hint(input: String, commands: List<AgentCommand>): String? {
        val command = commands.firstOrNull { input == "/${it.name} " || input == "/${it.name}" }
        return command?.hint
    }
}
