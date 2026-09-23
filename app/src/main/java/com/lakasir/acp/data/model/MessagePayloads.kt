package com.lakasir.acp.data.model

import com.lakasir.acp.acp.AcpJson
import com.lakasir.acp.acp.PlanEntry
import com.lakasir.acp.acp.ToolCallInfo
import com.lakasir.acp.acp.ToolContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class DiffState(val path: String, val oldText: String? = null, val newText: String)

@Serializable
data class ToolCallState(
    val toolCallId: String,
    val title: String? = null,
    val kind: String? = null,
    val status: String? = null,
    val input: String? = null,
    val output: String = "",
    val diffs: List<DiffState> = emptyList(),
) {
    val displayTitle: String get() = title ?: kind ?: "Tool call"

    fun merge(info: ToolCallInfo): ToolCallState {
        val content = info.content
        val textOutput = content?.filterIsInstance<ToolContent.Text>()?.joinToString("\n") { it.text }
        return copy(
            title = info.title ?: title,
            kind = info.kind ?: kind,
            status = info.status ?: status,
            input = info.rawInput?.toString() ?: input,
            output = when {
                !textOutput.isNullOrEmpty() -> textOutput
                info.rawOutput != null -> info.rawOutput.toString()
                content != null -> ""
                else -> output
            },
            diffs = content?.filterIsInstance<ToolContent.Diff>()?.map { DiffState(it.path, it.oldText, it.newText) } ?: diffs,
        )
    }

    fun encode(): String = AcpJson.encodeToString(serializer(), this)

    companion object {
        fun from(info: ToolCallInfo): ToolCallState = ToolCallState(info.toolCallId).merge(info)
        fun decode(json: String): ToolCallState? = runCatching { AcpJson.decodeFromString(serializer(), json) }.getOrNull()
    }
}

@Serializable
data class PlanItem(val content: String, val status: String? = null, val priority: String? = null)

object PlanCodec {
    private val serializer = ListSerializer(PlanItem.serializer())

    fun encode(entries: List<PlanEntry>): String =
        AcpJson.encodeToString(serializer, entries.map { PlanItem(it.content, it.status, it.priority) })

    fun decode(json: String): List<PlanItem> = runCatching { AcpJson.decodeFromString(serializer, json) }.getOrDefault(emptyList())
}

@Serializable
data class PermissionRecord(
    val title: String,
    val kind: String? = null,
    val input: String? = null,
    val options: List<String> = emptyList(),
    val choice: String? = null,
    val auto: Boolean = false,
) {
    fun encode(): String = AcpJson.encodeToString(serializer(), this)

    companion object {
        fun decode(json: String): PermissionRecord? = runCatching { AcpJson.decodeFromString(serializer(), json) }.getOrNull()
    }
}
