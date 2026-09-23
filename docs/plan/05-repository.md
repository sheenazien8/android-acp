# 05 — Repository & Connection Lifecycle

## Context
- files: @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt (new), @app/src/main/java/com/lakasir/acp/di/AppContainer.kt
- Depends on 03 (AcpClient) and 04 (Room)

## Goals
- `AcpRepository` (app-scoped, single instance):
  - `connectionState: StateFlow<ConnectionState>` (Disconnected / Connecting / Connected(agentInfo) / Error(msg)), `activeProfileId`
  - `connect(profile)`: open WS → `initialize` → update `lastConnectedAt`
  - `disconnect()`: stops reconnect loop
  - auto-reconnect with exponential backoff (1s → 30s cap) while "should be connected"
  - profile CRUD passthrough to DAO
  - `createSession(profileId)`: `session/new` with profile `cwd` → insert `SessionEntity`
  - `openSession(localId)`: if not attached and agent supports `loadSession`, call `session/load` and ignore replayed history (Room already has it)
  - `sendPrompt(localId, text)`: insert user message → `session/prompt` → on `stopReason` mark turn ended; errors saved as `error` messages
  - `cancel(localId)`
  - collects all `AcpEvent`s and writes to Room (chunk merge, tool call upsert, diff, unknown → system with `rawJson`)
  - `busySessions: StateFlow<Set<Long>>` for thinking indicator / cancel button
  - `pendingPermission: StateFlow<PermissionRequest?>` + `answerPermission(optionId?)`
- Register in `AppContainer`

## Notes
- UI observes Room only (single source of truth); repository never hands raw WS frames to UI
- On disconnect: clear busy sessions, dismiss pending permission (answer cancelled if still connected)
- Session title defaults to first user prompt (truncated) once sent

## Testing
- `./gradlew assembleDebug testDebugUnitTest`
- Unit test the chunk-merge / tool-upsert mapping as a pure function

## Tools / Skills
- Bash: `./gradlew`

## Implementation
- `data/model/MessagePayloads.kt`: JSON payloads stored in `MessageEntity.content` — `ToolCallState` (with `merge()`, where a new `content` list replaces the old one, as ACP specifies), `DiffState`, `PlanItem` + `PlanCodec`, `PermissionRecord`
- `data/repository/ConnectionState.kt`: `ConnectionState` (Disconnected / Connecting(attempt) / Connected(agent) / Error(message, retryInMs)), `PendingPermission`
- `data/repository/AcpRepository.kt`:
  - profile CRUD; deleting the active profile disconnects
  - `connect(profile)` runs a loop: open WS → `initialize` → wait for drop → back off 1s, 2s, 4s … capped at 30s → retry; `disconnect()` stops it
  - `createSession`, `openSession` (`session/load` only if the agent supports it; replayed updates are ignored while loading because Room already has them), `deleteSession`
  - `sendPrompt` runs in the app scope, so leaving the chat screen doesn't cancel the turn; stores the user message, sets the title from the first prompt, records non-`end_turn` stop reasons and errors as messages
  - `cancel` answers any pending permission for that session with `cancelled`, then sends `session/cancel`
  - event persistence: chunks append to the last same-type agent row, tool calls upsert by `toolCallId`, plan updates replace the last plan row, unknown kinds stored as SYSTEM with `rawJson`, `user_message_chunk` ignored (the app stores its own)
  - `busySessions`, `attachedSessions` (sessions that can receive prompts on this connection), `pendingPermissions` (a queue, in case several arrive) + `answerPermission`
  - on disconnect: busy/attached/pending cleared; in-flight prompts fail with "Connection lost" and that is saved as an error row
- `AppContainer`: app scope (`SupervisorJob + Dispatchers.Default`), `AcpClient` over `OkHttpAcpTransport`, `AcpRepository`
- `MessageDao.get(id)` added
- Tests: `MessagePayloadsTest`, `AcpRepositoryRulesTest` (append rule, backoff); 38 total, all pass
- Verified: `./gradlew assembleDebug testDebugUnitTest` OK
