# First-run setup and demo mode

## Context
- files: @app/src/main/java/com/lakasir/acp/ui/connection/ConnectionScreen.kt
- files: @app/src/main/java/com/lakasir/acp/acp/AcpClientFactory.kt
- files: @app/src/main/java/com/lakasir/acp/acp/AcpTransport.kt
- files: @bridge/README.md
- A fresh install shows "No bridges yet" with one sentence; a Play reviewer without a bridge cannot use anything, which risks a "minimum functionality" rejection

## Goals
1. First-run screen (shown when there are no profiles): what the app does, the bridge install command (`npx lakasir-acp-bridge --tls auto --token <secret> -- <agent>`), copy button, link to the README, "Add connection" and "Try demo" buttons
2. Demo mode: a built-in `DemoTransport` implementing `AcpTransport` that plays a scripted session
   - `initialize`, `session/new`, a prompt reply with streamed markdown, one tool call with a diff, one permission request, model/thinking config options, slash commands
   - Workspace methods answer from a small in-memory file tree and git status
   - Shown as a normal profile named "Demo" with a "Demo" badge; can be deleted
3. Use demo mode for Play screenshots (plan 24) and reviewer access notes

## Notes
- Demo profile uses a reserved scheme (`demo://`) so `AcpClientFactory` picks the fake transport; nothing goes over the network
- Keep scripted content in one Kotlin file of JSON strings so it is easy to update
- Reviewer notes in the Play Console "App access" section: "Tap Try demo; no account needed"

## Testing
- Unit: demo transport answers every request id and emits the scripted updates in order
- Device: fresh install → Try demo → full chat flow, Files and Git tabs work offline
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

## Tools / Skills
- Skill `mobile-android-design`
- Bash: `./gradlew`

## Implementation
<!-- Write you've done in here -->
