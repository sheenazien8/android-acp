# Session CRUD

## Context
- files:
  - @app/src/main/java/com/lakasir/acp/data/local/Daos.kt
  - @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt
  - @app/src/main/java/com/lakasir/acp/ui/sessions/SessionsViewModel.kt
  - @app/src/main/java/com/lakasir/acp/ui/sessions/SessionsScreen.kt
  - @app/src/main/java/com/lakasir/acp/ui/chat/ChatViewModel.kt
  - @app/src/main/java/com/lakasir/acp/ui/chat/ChatScreen.kt
  - @app/src/main/java/com/lakasir/acp/ui/navigation/AppNavHost.kt
- Current state of session CRUD:
  - Create: "New session" FAB → `AcpRepository.createSession` (`session/new`), only while connected
  - Read: list (`observeSummaries`) + open in Chat (`session/load` if supported)
  - Update: **missing**. Title is set automatically from the first prompt (first line, 60 chars) while it is still `DEFAULT_TITLE`; the user cannot rename it
  - Delete: swipe-to-delete on the Sessions list with a confirm dialog (local only); not reachable from the Chat screen, and swipe is not discoverable
- ACP v1 has no rename/delete method for sessions, so update/delete are local-only (Room)

## Goals
- Repository: `renameSession(id, title)` (trimmed, non-blank, max 60 chars) via existing `SessionDao.updateTitle`; also `touch` is not changed by rename
- Sessions screen:
  - Row long-press / trailing overflow `IconButton` (`MoreVert`) → `DropdownMenu` with "Rename" and "Delete"
  - Keep swipe-to-delete as a shortcut, sharing the same confirm dialog
  - Rename dialog: `AlertDialog` with a single-line `OutlinedTextField` prefilled with the current title, Save disabled when blank, `imeAction = Done`
- Chat screen:
  - Top bar overflow menu with "Rename" and "Delete"
  - Delete confirms, then navigates back (compact) or clears the selection (expanded list-detail)
- Shared UI: extract `RenameSessionDialog` and `DeleteSessionDialog` into `ui/sessions/SessionDialogs.kt` and use them from both screens
- Unit test for the title normalization (pure function)

## Assumptions
- "CRUD" means filling the missing Update (rename) and making Delete reachable from Chat, since Create/Read already exist. Assumed because the request did not list specific operations
- Rename/delete stay local; nothing is sent to the agent
- Deleting a session that is busy first calls `cancel()` so the agent stops the turn, then deletes locally
- A manually renamed session is never overwritten by the auto-title (auto-title only applies while the title equals `DEFAULT_TITLE`, which already holds unless the user renames it back to "New session")

## Notes
- Deleting the session open in Chat: `ChatViewModel.session` becomes `null`; navigate back before/after delete so the screen does not render an empty session
- In the expanded layout, the Chat detail pane gets `onBack = null`; it needs an `onDeleted` callback so `SessionsScreen` can reset `selectedId`
- Pending permission requests of a deleted session must be dropped from `_pendingPermissions` (answer with cancel) so the global dialog doesn't point to a missing session
- Session row overflow button must be a 48dp touch target with `contentDescription` (mobile-android-design skill)
- `rememberSaveable` for the rename text field and the open dialog state

## Testing
- `./gradlew :app:compileDebugKotlin testDebugUnitTest`
- Unit test: `SessionTitle.normalize` (trim, blank → null, truncate to 60)
- Manual: rename from list and from chat; delete from list (menu + swipe) and from chat (compact + expanded); delete a busy session

## Tools / Skills
- Skill `mobile-android-design` (dialogs, menus, touch targets)
- Bash: `./gradlew`

## Implementation
- `AcpRepository`:
  - `renameSession(id, title)` writes the normalized title via `SessionDao.updateTitle`
  - `normalizeTitle` (companion, next to `canAppend` / `backoffMs`) + `MAX_TITLE_LENGTH = 60`; the auto-title from the first prompt now uses it too
  - `deleteSession` cancels the turn if the session is busy (which also answers its pending permissions as cancelled), otherwise cancels pending permissions directly, then drops it from busy/attached and deletes the row
  - `sendPrompt` re-reads the session after the prompt returns and stops if it was deleted, so no "Stopped: cancelled" / error row is inserted into a deleted session (was an FK crash risk with the old swipe-delete too), and no turn notification is sent for it
- `ui/sessions/SessionActions.kt`: shared `RenameSessionDialog` (auto-focused, text preselected, saveable, Save disabled when blank, IME Done saves, input capped at 60), `DeleteSessionDialog`, and `SessionMenuButton` (`MoreVert` → `DropdownMenu` with Rename / Delete)
- Sessions screen: each row has an overflow menu and long-press opens it (`combinedClickable` with a long-click label); swipe-to-delete kept and shares the confirm dialog; dialog targets are stored as ids in `rememberSaveable`; the expanded list-detail selection is cleared when the selected session disappears (covers delete from the list or from the detail pane)
- Chat screen: top bar overflow menu with Rename / Delete; `ChatScreen` gets an `onDeleted` callback, `ChatViewModel.delete` calls it after the delete finishes (compact pops back; expanded passes `{}` and relies on the selection clearing)
- Changes from plan: title normalization lives in `AcpRepository`'s companion instead of a separate `SessionTitle` object, and the shared file is `SessionActions.kt` instead of `SessionDialogs.kt`, because it also holds the menu
- Tests: 3 new cases in `AcpRepositoryRulesTest` (trim/first line, blank rejected, max length)
- Verified: `./gradlew :app:compileDebugKotlin testDebugUnitTest lintDebug` pass (64 unit tests). Manual device testing not done yet
