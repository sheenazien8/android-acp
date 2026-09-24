package com.lakasir.acp.ui.chat

import com.lakasir.acp.data.local.MessageEntity
import com.lakasir.acp.data.local.MessageRole
import com.lakasir.acp.data.local.MessageType
import com.lakasir.acp.data.model.PermissionRecord
import com.lakasir.acp.data.model.PlanCodec
import com.lakasir.acp.data.model.PlanItem
import com.lakasir.acp.data.model.ToolCallState
import com.lakasir.acp.data.model.UserPromptPayload

sealed interface ChatItem {
    val id: Long

    data class UserText(override val id: Long, val text: String, val attachments: List<String> = emptyList()) : ChatItem
    data class AgentText(override val id: Long, val text: String) : ChatItem
    data class Thought(override val id: Long, val text: String) : ChatItem
    data class Tool(override val id: Long, val state: ToolCallState) : ChatItem
    data class Plan(override val id: Long, val items: List<PlanItem>) : ChatItem
    data class Permission(override val id: Long, val record: PermissionRecord) : ChatItem
    data class Error(override val id: Long, val text: String) : ChatItem
    data class SystemNote(override val id: Long, val text: String) : ChatItem

    companion object {
        fun from(message: MessageEntity): ChatItem = when (message.type) {
            MessageType.TEXT -> when (message.role) {
                MessageRole.USER -> UserText(message.id, message.content, UserPromptPayload.decode(message.rawJson).attachments)
                MessageRole.AGENT -> AgentText(message.id, message.content)
                else -> SystemNote(message.id, message.content)
            }
            MessageType.THOUGHT -> Thought(message.id, message.content)
            MessageType.TOOL_CALL -> ToolCallState.decode(message.content)
                ?.let { Tool(message.id, it) }
                ?: SystemNote(message.id, "Unreadable tool call")
            MessageType.PLAN -> Plan(message.id, PlanCodec.decode(message.content))
            MessageType.PERMISSION_REQUEST -> PermissionRecord.decode(message.content)
                ?.let { Permission(message.id, it) }
                ?: SystemNote(message.id, "Unreadable permission request")
            MessageType.ERROR -> Error(message.id, message.content)
        }
    }
}

class ChatItemMapper {
    private val cache = HashMap<Long, Pair<MessageEntity, ChatItem>>()

    fun map(messages: List<MessageEntity>): List<ChatItem> {
        val next = HashMap<Long, Pair<MessageEntity, ChatItem>>(messages.size)
        val items = messages.map { message ->
            val cached = cache[message.id]
            val item = if (cached != null && cached.first == message) cached.second else ChatItem.from(message)
            next[message.id] = message to item
            item
        }
        cache.clear()
        cache.putAll(next)
        return items
    }
}
