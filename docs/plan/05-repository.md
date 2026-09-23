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
<!-- Write you've done in here -->
