# Auto Mode (auto-approve permission requests)

## Context
- files:
  - @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt
  - @app/src/main/java/com/lakasir/acp/data/local/Entities.kt
  - @app/src/main/java/com/lakasir/acp/data/local/AppDatabase.kt
  - @app/src/main/java/com/lakasir/acp/data/local/Migrations.kt
  - @app/src/main/java/com/lakasir/acp/data/local/Daos.kt
  - @app/src/main/java/com/lakasir/acp/data/model/MessagePayloads.kt
  - @app/src/main/java/com/lakasir/acp/acp/AcpMethods.kt
  - @app/src/main/java/com/lakasir/acp/ui/chat/ChatViewModel.kt
  - @app/src/main/java/com/lakasir/acp/ui/chat/ChatScreen.kt
  - @app/src/main/java/com/lakasir/acp/ui/chat/components/ (`PermissionRecordRow`)
  - @app/src/main/java/com/lakasir/acp/ui/sessions/SessionsScreen.kt
- Current state:
  - Every `session/request_permission` from the agent is turned into a `PERMISSION_REQUEST` row (`AcpRepository.persist`) and pushed onto `_pendingPermissions`; `PermissionHost` (plan 09) shows a modal dialog and the turn blocks until the user answers
  - Options come from the agent (`allow_once` / `allow_always` / `reject_once` / `reject_always`, see `PermissionOptionKind`); the response is `{outcome:{outcome:"selected", optionId}}`
  - With background connections (plan 11) a long agent turn stalls whenever the phone is locked, because each tool call waits for a tap
  - `SessionEntity` has no per-session settings; DB is `version = 1` and `Migrations.ALL` is empty

## Goals
- Per-session **Auto mode** toggle: when on, the client answers permission requests itself, without showing the dialog
- Data:
  - `SessionEntity.autoApprove: Boolean = false` (column `autoApprove`, default 0)
  - Room `version = 2` + `MIGRATION_1_2` (`ALTER TABLE sessions ADD COLUMN autoApprove INTEGER NOT NULL DEFAULT 0`) registered in `Migrations.ALL`
  - `SessionDao.updateAutoApprove(id, enabled)`
- Repository:
  - `setAutoApprove(localId, enabled)`; when turned on, immediately auto-answer that session's already-queued `_pendingPermissions`
  - In `persist(PermissionRequest)`: if the session has `autoApprove`, pick an option with `autoApproveOption(options)` and call `client.respondPermission` directly instead of queueing; still insert the `PERMISSION_REQUEST` row with `choice` set and `auto = true`
  - `autoApproveOption` (pure, companion): prefer `allow_once`, then `allow_always`; returns `null` if the agent offers no allow option → fall back to the normal dialog
- `PermissionRecord.auto: Boolean = false`; `PermissionRecordRow` shows "Auto-approved: <option>" (muted, with a small bolt icon) instead of "Answered: <option>"
- UI:
  - Chat top bar: `IconToggleButton` (bolt icon, 48dp, `contentDescription` "Auto mode on/off"), accent tint when on; plus an "Auto mode" `AssistChip`/label under the title so the state is always visible
  - Enabling shows a confirm `AlertDialog` ("The agent will run tools and edit files without asking. Continue?"); disabling is immediate
  - Sessions list row: small bolt badge next to the title for sessions in auto mode
- Unit tests for `autoApproveOption` and the migration default

## Assumptions
- "Auto mode" means the **client** answers `session/request_permission` automatically. Assumed because the user did not name an agent; client-side approval works with every ACP agent, while agent-side modes (`session/set_mode`, e.g. Claude Code's `bypassPermissions`) are agent-specific
- Scope is **per session**, default **off** (current behaviour is kept for existing and new sessions). A per-profile "start new sessions in auto mode" default is left out; easy follow-up if wanted
- Auto mode approves **all** tool kinds (read, edit, execute, fetch...). No per-kind allowlist in this plan
- Auto mode picks `allow_once` rather than `allow_always`, so the agent does not remember a grant; turning auto mode off restores prompting right away

## Notes
- Answer on the same code path as the dialog (`respondPermission`) and keep the Room row, so the chat history shows what was auto-approved (audit trail)
- The auto-answer must happen after the row is inserted, inside the existing `writeMutex` block, and must not touch `_pendingPermissions`; otherwise `PermissionHost` flashes the dialog
- Read `autoApprove` from Room (`sessionDao.get`) at request time, not from a cached flow, so a toggle takes effect on the very next request
- Events during `session/load` replay: permission requests are not filtered by `loadingRemoteIds` today; auto mode must follow the same rule (a real request is answered, replay does not produce requests)
- If `respondPermission` returns `false` (socket gone), leave the row unanswered, same as a dropped dialog; `clearProfileConnectionState` already handles cleanup
- Deleting a session in auto mode: nothing extra, the column goes with the row
- Background turns: with plan 11's foreground service, auto mode lets turns finish while the app is backgrounded; the turn-finished notification is unchanged
- Plan 11 posts a notification for each new permission request while the app is hidden; it listens to `pendingPermissions`, so auto-answered requests (never queued) must not trigger it. Verify this and add a test
- Optional follow-up (not in scope): if `session/new` returns `modes.availableModes`, expose `session/set_mode` as a second, agent-side switch; add `SESSION_SET_MODE` to `AcpMethods` then
- UI follows `mobile-android-design`: 48dp touch targets, toggle state in `contentDescription`, no reliance on color only (icon + label)

## Testing
- `./gradlew :app:compileDebugKotlin testDebugUnitTest lintDebug`
- Unit tests:
  - `autoApproveOption`: allow_once preferred; only allow_always → it; only reject options → null; empty → null; unknown kinds ignored
  - Repository with `FakeTransport`: session with `autoApprove = true` → a `session/request_permission` gets a `selected` response with the allow_once id, `pendingPermissions` stays empty, row has `choice` + `auto = true`
  - Same with `autoApprove = false` → request is queued (regression)
  - Enabling auto mode while a request is queued answers it and empties the queue for that session only
  - `MIGRATION_1_2` via Room `MigrationTestHelper` (or at least schema export check) → existing sessions read `autoApprove = false`
- Manual: toggle in chat, confirm dialog, run a turn that edits a file and runs a command against the bridge, see "Auto-approved" rows and no dialog; toggle off mid-turn → next request shows the dialog

## Tools / Skills
- Skill `mobile-android-design` (toggle button, chips, dialogs, touch targets)
- Bash: `./gradlew`
- WebFetch agentclientprotocol.com if the follow-up `session/set_mode` is picked up

## Implementation
- Data: `SessionEntity.autoApprove` (`@ColumnInfo(defaultValue = "0")`), DB `version = 2`, `Migrations.MIGRATION_1_2` (`ALTER TABLE sessions ADD COLUMN autoApprove INTEGER NOT NULL DEFAULT 0`) in `Migrations.ALL`, `SessionDao.updateAutoApprove`; exported schema `app/schemas/.../2.json` matches the migration
- `PermissionRecord.auto` (default `false`, so old rows decode as manual answers)
- `AcpRepository`:
  - `autoApproveOption(options)` in the companion: allow_once, else allow_always, else `null`
  - `persist(PermissionRequest)`: for an auto-mode session with an allow option, responds via `respondPermission` first, then inserts the row with `choice` + `auto = true`; the request is never queued, so no dialog and no plan 11 notification. No allow option → queued as before. Auto mode on but the socket is gone → row stays unanswered and nothing is queued
  - `setAutoApprove(id, enabled)`: writes the flag; when enabling, answers that session's queued requests through `answerPermission(..., auto = true)`
  - `answerPermission` got an `auto` parameter (default `false`)
- Chat: `ChatViewModel.setAutoApprove`; top bar `IconToggleButton` with a filled/outlined bolt (primary tint when on, state in `contentDescription`), "Auto mode" label under the title, confirm `AlertDialog` when turning on, turning off is immediate
- `PermissionRecordRow`: small bolt + "Auto-approved: <option>" for auto answers
- Sessions list: bolt icon next to the title for sessions in auto mode
- Changes from plan:
  - The state label is a plain accent `labelSmall` under the title, not an `AssistChip`, to keep the top bar compact
  - The response is sent before the row is inserted, so the row is written once with its final state
  - Repository tests with `FakeTransport` and the `MigrationTestHelper` test were not written: the repository needs Room, and the project has no Robolectric or instrumented tests. Covered instead: `autoApproveOption` (3 cases in `AcpRepositoryRulesTest`), `PermissionRecord.auto` round trip + old-row decode (`MessagePayloadsTest`), and the exported schema checked against the migration SQL
- Verified: `./gradlew :app:compileDebugKotlin testDebugUnitTest lintDebug` pass (68 unit tests). Manual device testing against a bridge not done yet
