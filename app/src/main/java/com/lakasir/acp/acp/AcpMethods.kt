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
    const val SESSION_SET_CONFIG_OPTION = "session/set_config_option"

    const val SESSION_UPDATE = "session/update"
    const val SESSION_REQUEST_PERMISSION = "session/request_permission"
}

object SessionConfigCategory {
    const val MODEL = "model"
    const val MODE = "mode"
    const val THOUGHT_LEVEL = "thought_level"
    const val MODEL_CONFIG = "model_config"
}

object SessionConfigType {
    const val SELECT = "select"
    const val BOOLEAN = "boolean"
}

object SessionConfigIds {
    const val MODEL = "model"
    const val THOUGHT_LEVEL = "thought_level"
}

object SessionUpdateKind {
    const val AGENT_MESSAGE_CHUNK = "agent_message_chunk"
    const val AGENT_THOUGHT_CHUNK = "agent_thought_chunk"
    const val USER_MESSAGE_CHUNK = "user_message_chunk"
    const val TOOL_CALL = "tool_call"
    const val TOOL_CALL_UPDATE = "tool_call_update"
    const val PLAN = "plan"
    const val CONFIG_OPTION_UPDATE = "config_option_update"
    const val AVAILABLE_COMMANDS_UPDATE = "available_commands_update"
    const val USAGE_UPDATE = "usage_update"
    const val CURRENT_MODE_UPDATE = "current_mode_update"
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

object PromptBlockType {
    const val TEXT = "text"
    const val RESOURCE_LINK = "resource_link"
}

/**
 * Workspace extension methods answered by the lakasir bridge itself (never forwarded to the agent).
 * ACP reserves the leading underscore for extensions. Paths are relative to `cwd`.
 */
object LakasirMethods {
    const val HELLO = "_lakasir/hello"
    const val FS_LIST = "_lakasir/fs/list"
    const val FS_READ = "_lakasir/fs/read"
    const val FS_WRITE = "_lakasir/fs/write"
    const val FS_CREATE = "_lakasir/fs/create"
    const val FS_RENAME = "_lakasir/fs/rename"
    const val FS_DELETE = "_lakasir/fs/delete"
    const val GIT_STATUS = "_lakasir/git/status"
    const val GIT_DIFF = "_lakasir/git/diff"
    const val GIT_LOG = "_lakasir/git/log"
    const val GIT_SHOW = "_lakasir/git/show"
    const val GIT_STAGE = "_lakasir/git/stage"
    const val GIT_UNSTAGE = "_lakasir/git/unstage"
    const val GIT_COMMIT = "_lakasir/git/commit"
}

object LakasirErrorCode {
    const val CONFLICT = -32010
    const val OUTSIDE_WORKSPACE = -32011
    const val NOT_FOUND = -32012
    const val ALREADY_EXISTS = -32013
    const val NOTHING_STAGED = -32020
    const val GIT_ERROR = -32021
    const val NOT_A_REPO = -32022
}

object FsEntryType {
    const val FILE = "file"
    const val DIR = "dir"
}
