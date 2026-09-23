# 09 — Permission Dialog

## Context
- files: @app/src/main/java/com/lakasir/acp/ui/chat/PermissionDialog.kt (new), @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt
- Depends on 05 (`pendingPermission`) and 08 (chat screen)

## Goals
- Modal dialog shown when `pendingPermission` belongs to the open session (or globally from any screen, with the session title)
- Shows tool call title/kind, raw input in mono, and one button per agent-provided option (Allow / Allow Always / Deny mapped from `allow_once` / `allow_always` / `reject_*`)
- Button → `answerPermission(optionId)` → JSON-RPC response `{outcome:{outcome:"selected", optionId}}`
- Dismiss without choice → `{outcome:{outcome:"cancelled"}}`
- Record the request and the chosen option as a `permission_request` message in Room

## Notes
- Options come from the agent; don't hardcode ids — only map `kind` to button style (accent for allow, error for reject)
- If connection drops while dialog open → dismiss

## Testing
- `./gradlew assembleDebug testDebugUnitTest`
- Unit test response JSON for selected/cancelled
- Manual end-to-end against bridge

## Tools / Skills
- Bash: `./gradlew`

## Implementation
<!-- Write you've done in here -->
