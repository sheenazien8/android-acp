# 06 — Connection Screen

## Context
- files: @app/src/main/java/com/lakasir/acp/ui/connection/ (new), @app/src/main/java/com/lakasir/acp/MainActivity.kt
- Depends on 02 (theme) and 05 (repository)

## Goals
- `ConnectionViewModel` (StateFlow): profiles list, connection state, form state, validation errors
- `ConnectionScreen`:
  - list of saved profiles (name, `host:port`, cwd, last connected); active one highlighted with accent
  - add/edit form (bottom sheet or dialog): name, host, port, cwd; validates non-empty host, port 1–65535, absolute cwd
  - Connect / Disconnect per profile; delete profile
  - after a successful connect → navigate to Sessions for that profile
- Shared `ConnectionStatusIndicator` (small colored dot + short label) used in every top bar
- Nav route `connections` as start destination

## Notes
- Error states shown inline under the profile row, not as a big banner
- Only one active connection: connecting to another profile disconnects the current one

## Testing
- `./gradlew assembleDebug`
- Manual: add profile, connect to unreachable host → Error shown, reconnect attempts visible

## Tools / Skills
- Bash: `./gradlew assembleDebug`

## Implementation
<!-- Write you've done in here -->
