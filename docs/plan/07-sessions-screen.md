# 07 — Sessions Screen

## Context
- files: @app/src/main/java/com/lakasir/acp/ui/sessions/ (new)
- Depends on 02 and 05

## Goals
- `SessionsViewModel(profileId)`: sessions Flow from Room, connection state, `createSession()` with loading/error state
- `SessionsScreen` (`Scaffold`):
  - `TopAppBar` with back navigation, profile name, `ConnectionStatusIndicator`
  - `LazyColumn` of `ListItem`s (headline = title, supporting = last message preview, trailing = relative time); busy sessions show a small `CircularProgressIndicator`; stable `key = session.id`
  - "New Session" `ExtendedFloatingActionButton` (disabled when not connected); `LinearProgressIndicator` while creating; errors via `SnackbarHost`
  - tap row → Chat; delete local session via `SwipeToDismissBox`
  - empty state when no sessions
- Route `Sessions(profileId)` read with `savedStateHandle.toRoute()`
- Adaptive: on expanded `WindowSizeClass` (tablet/foldable) show list + chat side by side (list-detail); compact keeps separate screens

## Notes
- No "01/02/03" numbering; order by `updatedAt desc`
- Past sessions remain viewable while disconnected (read from Room)

## Testing
- `./gradlew assembleDebug`

## Tools / Skills
- Skill `mobile-android-design` → `references/compose-components.md` (lists, loading states), `references/material3-theming.md` (window size classes)
- Bash: `./gradlew assembleDebug`

## Implementation
- `ui/sessions/SessionsViewModel.kt`: reads `Sessions(profileId)` via `savedStateHandle.toRoute()`; profile, session summaries, connection state, busy set, `creating`, one-shot `error` and `createdSession`; `createSession`, `connect`, `deleteSession`
- `ui/sessions/SessionsScreen.kt`:
  - `TopAppBar` with back, profile name + `host:port` in mono, status indicator scoped to this profile
  - `LinearProgressIndicator` while creating; errors in a `Snackbar`
  - "New session" `ExtendedFloatingActionButton`, shown only when this profile is connected
  - when this profile isn't active: a one-line "Offline. History is read-only." row with a Connect `TextButton` (not a big banner)
  - `ListItem` rows: title, 2-line preview of the last text message, relative time, small spinner while busy; stable keys; dividers; swipe-to-delete with a dialog; empty state
  - Adaptive: on `WindowWidthSizeClass.Expanded`, the list is 360dp wide next to a detail pane (selected row gets an accent border); compact navigates to `Chat`
- `MainActivity` computes `calculateWindowSizeClass(this)` and passes it to `AppNavHost`
- `AppNavHost` wraps everything in a background `Surface`; Chat and the detail pane are placeholders until plan 08
- Changes from plan: "New session" is hidden rather than disabled when offline, because `ExtendedFloatingActionButton` has no disabled state; the offline row tells the user to connect instead
- Verified: `./gradlew assembleDebug` OK
