# 07 — Sessions Screen

## Context
- files: @app/src/main/java/com/lakasir/acp/ui/sessions/ (new)
- Depends on 02 and 05

## Goals
- `SessionsViewModel(profileId)`: sessions Flow from Room, connection state, `createSession()` with loading/error state
- `SessionsScreen`:
  - top bar: profile name + `ConnectionStatusIndicator`
  - list rows: title, last message preview, relative time, status; busy sessions show a small activity mark
  - "New Session" action (disabled when not connected) → creates and navigates to chat
  - tap row → Chat
- Nav route `sessions/{profileId}`

## Notes
- No "01/02/03" numbering; order by `updatedAt desc`
- Past sessions remain viewable while disconnected (read from Room)

## Testing
- `./gradlew assembleDebug`

## Tools / Skills
- Bash: `./gradlew assembleDebug`

## Implementation
<!-- Write you've done in here -->
