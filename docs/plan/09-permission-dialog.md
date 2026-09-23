# 09 — Permission Dialog

## Context
- files: @app/src/main/java/com/lakasir/acp/ui/permission/ (new), @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt
- Depends on 05 (`pendingPermission`) and 08 (chat screen)

## Goals
- Modal dialog shown when `pendingPermission` belongs to the open session (or globally from any screen, with the session title)
- Built on M3 `AlertDialog` (icon + title + scrollable text), not a custom modal
- Shows tool call title/kind, raw input in mono (scrollable, max height), and one button per agent-provided option (Allow / Allow Always / Deny mapped from `allow_once` / `allow_always` / `reject_*`)
- Button styles: `Button` (accent) for allow_once, `FilledTonalButton` for allow_always, `OutlinedButton` with error color for reject; buttons stacked vertically when > 2 options
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
- Skill `mobile-android-design` → `references/compose-components.md` (Alert Dialog)
- Bash: `./gradlew`

## Implementation
- `ui/permission/PermissionViewModel.kt`: shows the oldest item in the repository's `pendingPermissions` queue; `answer(pending, optionId?)`
- `ui/permission/PermissionButtonStyle.kt`: maps option `kind` to a button style (allow_once → primary, allow_always → tonal, reject_* → destructive, unknown → neutral); option ids are never hardcoded
- `ui/permission/PermissionDialog.kt`: `PermissionHost` + M3 `AlertDialog` (shield icon, tool title, "session · kind" line, raw input in a mono `CodeBox` capped at 12 lines, scrollable body up to 360dp), one full-width button per agent option stacked vertically; dark + light previews
- Answering: a button sends `{outcome:{outcome:"selected", optionId}}`; back/dismiss sends `{outcome:{outcome:"cancelled"}}`; the `PERMISSION_REQUEST` row in chat changes from "Waiting for your answer" to "Answered: <option>"
- Shown globally: `PermissionHost` sits in `AppNavHost` outside the `NavHost`, so the dialog appears on any screen with the session title
- Queue handling from plan 05: cancelling a turn answers that session's pending requests with `cancelled`; a dropped connection clears the queue, which dismisses the dialog
- Changes from plan: tapping outside the dialog is disabled (`dismissOnClickOutside = false`) so a stray tap can't cancel a tool call; only Back cancels
- Tests: `PermissionButtonStyleTest`; the selected/cancelled response JSON is covered in `AcpClientTest`; 53 total, all pass
- Verified: `./gradlew assembleDebug testDebugUnitTest` OK; installed over adb on a Pixel 7a. The Connections screen renders correctly (dark theme, empty state, status dot, FAB), and a UI dump confirmed the add-connection sheet opens with all fields. The dialog has not been exercised end to end yet, because that needs a running bridge
