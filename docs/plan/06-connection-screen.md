# 06 — Connection Screen

## Context
- files: @app/src/main/java/com/lakasir/acp/ui/connection/ (new), @app/src/main/java/com/lakasir/acp/MainActivity.kt
- Depends on 02 (theme) and 05 (repository)

## Goals
- `ConnectionViewModel` (StateFlow): profiles list, connection state, form state, validation errors
- `ConnectionScreen` (`Scaffold` + `TopAppBar`):
  - `LazyColumn` of saved profiles (name, `host:port` in mono, cwd, last connected); active one marked by accent left border
  - add/edit form in a `ModalBottomSheet`: `OutlinedTextField`s for name, host, port (`KeyboardType.Number`), cwd; `supportingText` + `isError` for validation (non-empty host, port 1–65535, absolute cwd); form state via `rememberSaveable`
  - Connect / Disconnect as `FilledTonalButton` / `OutlinedButton` per row; delete via `SwipeToDismissBox` with confirm
  - add profile via `FloatingActionButton` with `contentDescription = "Add connection"`
  - after a successful connect → navigate to Sessions for that profile
  - empty state when no profiles
- Shared `ui/components/ConnectionStatusIndicator` (8dp colored dot + short label, `semantics { stateDescription }` for TalkBack) used in every top bar
- Start destination `Connections` (type-safe route)

## Notes
- Error states shown inline under the profile row, not as a big banner
- Only one active connection: connecting to another profile disconnects the current one
- All icon-only buttons get a `contentDescription`; touch targets ≥ 48dp
- Content width capped (~640dp) on expanded `WindowSizeClass` so rows don't stretch on tablets

## Testing
- `./gradlew assembleDebug`
- Manual: add profile, connect to unreachable host → Error shown, reconnect attempts visible

## Tools / Skills
- Skill `mobile-android-design` → `references/compose-components.md` (text fields, bottom sheet, swipe to dismiss), `references/android-navigation.md` (type-safe routes)
- Bash: `./gradlew assembleDebug`

## Implementation
<!-- Write you've done in here -->
