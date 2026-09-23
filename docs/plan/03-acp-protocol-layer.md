# 03 — ACP Protocol Layer

## Context
- files: @app/src/main/java/com/lakasir/acp/acp/ (new)
- ACP schema summary in @docs/plan/00-overview.md
- Depends on 01. Pure Kotlin, no Android UI or Room.

## Goals
- `AcpMethods.kt`: all method names and `sessionUpdate` kinds as constants (single place to adjust)
- `AcpMessage.kt`: sealed `AcpMessage` — `Request(id, method, params)`, `Response(id, result)`, `ErrorResponse(id, error{code, message, data})`, `Notification(method, params)`; `params`/`result` are `JsonElement`; `AcpMessage.parse(String)` and `encode()`
- `AcpTransport.kt`: OkHttp WebSocket wrapper
  - `connect(url)`, `close()`, `suspend send(json)`
  - `incoming: SharedFlow<String>`, `state: StateFlow<TransportState>` (Disconnected / Connecting / Connected / Failed(reason))
- `AcpEvent.kt`: typed events parsed from `session/update` — `MessageChunk`, `ThoughtChunk`, `UserChunk`, `ToolCall`, `ToolCallUpdate` (status, content incl. `Diff(path, oldText, newText)` and text), `Plan`, `Unknown(raw)`; plus `PermissionRequest(requestId, sessionId, toolCall, options)` and `TurnEnded(stopReason)`
- `AcpClient.kt`: connection orchestration
  - request id counter + `pending: Map<id, CompletableDeferred>` with per-request timeout (`withTimeout`, default 30s, prompt has no timeout)
  - `initialize()` → stores `AgentInfo`/capabilities (`loadSession`)
  - `newSession(cwd)`, `loadSession(id, cwd)`, `prompt(sessionId, text)` → `stopReason`, `cancel(sessionId)`, `respondPermission(requestId, optionId?)`
  - routes `session/update` into the matching `AcpSession`; agent→client requests: `session/request_permission` surfaced as event, others answered with `-32601`
  - fails all pending requests on disconnect
- `AcpSession.kt`: per-session handle exposing `events: Flow<AcpEvent>`

## Notes
- Unparseable frames → `AcpEvent.ProtocolError(raw, reason)`, never crash the collector
- JSON config: `ignoreUnknownKeys = true`, `explicitNulls = false`
- Transport must not block OkHttp threads; use `tryEmit` with a large buffer

## Testing
- JVM unit tests:
  - `AcpMessage` parse/encode round-trip for all 4 kinds + malformed input
  - `AcpEvent` parsing for each `sessionUpdate` kind, including diff content and unknown kind
  - `AcpClient` with a fake transport: id correlation, error response → exception, timeout, pending failed on disconnect, permission request routing
- `./gradlew testDebugUnitTest`

## Tools / Skills
- Bash: `./gradlew testDebugUnitTest`
- WebFetch agentclientprotocol.com if a field name is in doubt

## Implementation
<!-- Write you've done in here -->
