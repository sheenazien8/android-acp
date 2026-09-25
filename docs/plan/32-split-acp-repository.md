# Split AcpRepository

## Context
- files: @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt (491 lines)
- files: @app/src/main/java/com/lakasir/acp/di/AppContainer.kt
- files: @app/src/test/java/com/lakasir/acp/data/
- One class owns: connection lifecycle + retry/backoff, per-profile clients, ACP event → DB mapping, permission queue, auto mode, session controls (model/thinking/commands), session CRUD

## Goals
Split into focused classes with the same public behavior:
1. `ConnectionManager`: `connect/disconnect/retryNow`, `connectionStates`, clients per profile, backoff
2. `SessionEventHandler`: applies `AcpEvent`s to Room (text append/streaming buffer from plan 28, tool calls, permissions record), `completedTurns`
3. `PermissionQueue`: pending permissions, auto mode answers
4. `SessionControls`: config options, commands, `setConfigOption`
5. `AcpRepository` stays as a thin facade used by ViewModels, so UI code does not change

## Notes
- Do this after plan 28 so the streaming buffer lands in `SessionEventHandler` directly
- Move tests with the code; no behavior change, so all existing tests must pass unchanged or with import changes only
- Keep manual DI in `AppContainer`

## Testing
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
- Device regression run: connect, prompt, tool call, permission, auto mode, model switch, reconnect after Wi-Fi toggle

## Tools / Skills
- Bash: `./gradlew`

## Implementation
<!-- Write you've done in here -->
