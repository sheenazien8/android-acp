package com.lakasir.acp.acp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

sealed interface ToolContent {
    data class Text(val text: String) : ToolContent
    data class Diff(val path: String, val oldText: String?, val newText: String) : ToolContent
    data class Terminal(val terminalId: String) : ToolContent
    data class Other(val raw: JsonElement) : ToolContent
}

data class ToolCallInfo(
    val toolCallId: String,
    val title: String? = null,
    val kind: String? = null,
    val status: String? = null,
    val content: List<ToolContent>? = null,
    val rawInput: JsonElement? = null,
    val rawOutput: JsonElement? = null,
)

data class PlanEntry(val content: String, val status: String?, val priority: String?)

data class PermissionOption(val optionId: String, val name: String, val kind: String)

sealed interface AcpEvent {
    val sessionId: String
    val raw: JsonElement

    data class MessageChunk(override val sessionId: String, val text: String, override val raw: JsonElement) : AcpEvent
    data class ThoughtChunk(override val sessionId: String, val text: String, override val raw: JsonElement) : AcpEvent
    data class UserMessageChunk(override val sessionId: String, val text: String, override val raw: JsonElement) : AcpEvent
    data class ToolCallStarted(override val sessionId: String, val toolCall: ToolCallInfo, override val raw: JsonElement) : AcpEvent
    data class ToolCallUpdated(override val sessionId: String, val toolCall: ToolCallInfo, override val raw: JsonElement) : AcpEvent
    data class Plan(override val sessionId: String, val entries: List<PlanEntry>, override val raw: JsonElement) : AcpEvent
    data class Unknown(override val sessionId: String, val kind: String, override val raw: JsonElement) : AcpEvent

    data class PermissionRequest(
        override val sessionId: String,
        val requestId: JsonElement,
        val toolCall: ToolCallInfo,
        val options: List<PermissionOption>,
        override val raw: JsonElement,
    ) : AcpEvent
}

object AcpEventParser {

    fun parseSessionUpdate(params: JsonElement?): AcpEvent {
        val root = params as? JsonObject ?: throw AcpParseException("session/update params must be an object")
        val sessionId = root.string("sessionId") ?: throw AcpParseException("session/update missing sessionId")
        val update = root["update"] as? JsonObject ?: throw AcpParseException("session/update missing update")
        val kind = update.string("sessionUpdate") ?: throw AcpParseException("session/update missing sessionUpdate")

        return when (kind) {
            SessionUpdateKind.AGENT_MESSAGE_CHUNK -> AcpEvent.MessageChunk(sessionId, contentText(update["content"]), root)
            SessionUpdateKind.AGENT_THOUGHT_CHUNK -> AcpEvent.ThoughtChunk(sessionId, contentText(update["content"]), root)
            SessionUpdateKind.USER_MESSAGE_CHUNK -> AcpEvent.UserMessageChunk(sessionId, contentText(update["content"]), root)
            SessionUpdateKind.TOOL_CALL -> AcpEvent.ToolCallStarted(sessionId, toolCall(update), root)
            SessionUpdateKind.TOOL_CALL_UPDATE -> AcpEvent.ToolCallUpdated(sessionId, toolCall(update), root)
            SessionUpdateKind.PLAN -> AcpEvent.Plan(sessionId, planEntries(update["entries"]), root)
            else -> AcpEvent.Unknown(sessionId, kind, root)
        }
    }

    fun parsePermissionRequest(requestId: JsonElement, params: JsonElement?): AcpEvent.PermissionRequest {
        val root = params as? JsonObject ?: throw AcpParseException("request_permission params must be an object")
        val sessionId = root.string("sessionId") ?: throw AcpParseException("request_permission missing sessionId")
        val toolCall = root["toolCall"] as? JsonObject ?: throw AcpParseException("request_permission missing toolCall")
        val options = (root["options"] as? JsonArray).orEmpty().mapNotNull { option ->
            val obj = option as? JsonObject ?: return@mapNotNull null
            val optionId = obj.string("optionId") ?: return@mapNotNull null
            PermissionOption(optionId, obj.string("name") ?: optionId, obj.string("kind") ?: "")
        }
        return AcpEvent.PermissionRequest(sessionId, requestId, toolCall(toolCall), options, root)
    }

    fun contentText(block: JsonElement?): String {
        val obj = block as? JsonObject ?: return ""
        return when (obj.string("type")) {
            "text" -> obj.string("text").orEmpty()
            "resource_link" -> obj.string("name") ?: obj.string("uri").orEmpty()
            "resource" -> (obj["resource"] as? JsonObject)?.let { it.string("text") ?: it.string("uri") }.orEmpty()
            null -> ""
            else -> "[${obj.string("type")}]"
        }
    }

    private fun toolCall(obj: JsonObject): ToolCallInfo = ToolCallInfo(
        toolCallId = obj.string("toolCallId") ?: throw AcpParseException("tool call missing toolCallId"),
        title = obj.string("title"),
        kind = obj.string("kind"),
        status = obj.string("status"),
        content = (obj["content"] as? JsonArray)?.map(::toolContent),
        rawInput = obj["rawInput"]?.takeUnless { it is JsonNull },
        rawOutput = obj["rawOutput"]?.takeUnless { it is JsonNull },
    )

    private fun toolContent(element: JsonElement): ToolContent {
        val obj = element as? JsonObject ?: return ToolContent.Other(element)
        return when (obj.string("type")) {
            "content" -> ToolContent.Text(contentText(obj["content"]))
            "diff" -> ToolContent.Diff(
                path = obj.string("path").orEmpty(),
                oldText = obj.string("oldText"),
                newText = obj.string("newText").orEmpty(),
            )
            "terminal" -> ToolContent.Terminal(obj.string("terminalId").orEmpty())
            else -> ToolContent.Other(obj)
        }
    }

    private fun planEntries(element: JsonElement?): List<PlanEntry> =
        (element as? JsonArray).orEmpty().mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            PlanEntry(obj.string("content").orEmpty(), obj.string("status"), obj.string("priority"))
        }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
