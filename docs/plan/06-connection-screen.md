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
- `ui/AppViewModelProvider.kt`: manual-DI `viewModelFactory` reading `AcpApp.container`
- Shared components in `ui/components/`: `ConnectionStatusIndicator` (dot + label, optional `profileId` scoping, one merged TalkBack description), `leftBorder` modifier, `SwipeToDelete` (swipe opens a confirmation instead of deleting immediately), `EmptyState`, `relativeTime`
- `ui/connection/ProfileForm.kt`: form state + validation (bare host, port 1–65535, absolute POSIX or Windows cwd; name defaults to host)
- `ui/connection/ConnectionViewModel.kt`: profiles, connection state, form, connect/disconnect/delete; opens Sessions automatically once the profile the user connected reaches `Connected`
- `ui/connection/ConnectionScreen.kt`: `TopAppBar` + status indicator, FAB "Add connection", rows with accent left border when connected, host/port/cwd in mono, inline status line (error + retry countdown / reconnect attempt / agent name / last connected), Edit icon, Connect (tonal) / Disconnect (outlined), swipe-to-delete with dialog, empty state, 640dp max width; dark + light previews
- `ui/connection/ProfileFormSheet.kt`: `ModalBottomSheet` with `OutlinedTextField`s, mono for host/port/cwd, digits-only port, errors via `supportingText`, `imePadding`
- `AppNavHost`: Connections screen wired; Sessions is a placeholder until plan 07
- Changes from plan:
  - Form state lives in the ViewModel instead of `rememberSaveable`: it survives rotation just the same and keeps validation out of the UI
  - Tapping a row opens its sessions even when offline, so past history can be read
- Tests: `ProfileFormTest` (5); 43 total, all pass
- Verified: `./gradlew assembleDebug testDebugUnitTest` OK
