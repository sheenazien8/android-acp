package com.lakasir.acp.acp

/**
 * Single source of truth for ACP wire names.
 * Verified against agentclientprotocol.com (protocol version 1). Update here if the schema changes.
 */
object AcpMethods {
    const val PROTOCOL_VERSION = 1
    const val ENDPOINT_PATH = "/acp"

    const val INITIALIZE = "initialize"
    const val SESSION_NEW = "session/new"
    const val SESSION_LOAD = "session/load"
    const val SESSION_PROMPT = "session/prompt"
    const val SESSION_CANCEL = "session/cancel"

    const val SESSION_UPDATE = "session/update"
    const val SESSION_REQUEST_PERMISSION = "session/request_permission"
}

object SessionUpdateKind {
    const val AGENT_MESSAGE_CHUNK = "agent_message_chunk"
    const val AGENT_THOUGHT_CHUNK = "agent_thought_chunk"
    const val USER_MESSAGE_CHUNK = "user_message_chunk"
    const val TOOL_CALL = "tool_call"
    const val TOOL_CALL_UPDATE = "tool_call_update"
    const val PLAN = "plan"
}

object ToolCallStatus {
    const val PENDING = "pending"
    const val IN_PROGRESS = "in_progress"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
}

object PermissionOptionKind {
    const val ALLOW_ONCE = "allow_once"
    const val ALLOW_ALWAYS = "allow_always"
    const val REJECT_ONCE = "reject_once"
    const val REJECT_ALWAYS = "reject_always"
}

object JsonRpcErrorCode {
    const val PARSE_ERROR = -32700
    const val METHOD_NOT_FOUND = -32601
}
