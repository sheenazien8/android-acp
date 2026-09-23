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
<!-- Write you've done in here -->
