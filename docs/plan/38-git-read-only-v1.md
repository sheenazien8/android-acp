# Read-only Git for v1

## Context
- files: @app/src/main/java/com/lakasir/acp/ui/workspace/git/GitPane.kt
- files: @app/src/main/java/com/lakasir/acp/ui/workspace/git/GitViewModel.kt
- files: @app/build.gradle.kts
- The Git tab supports stage/unstage (per file and all) and commit (`CommitBox`), plus status, diffs and history

## Goals
1. `BuildConfig.GIT_WRITE` flag (true in debug, false in release for v1)
2. When false: hide stage/unstage actions and the commit box; keep status, diff, history
3. Keep the bridge methods; only the UI is gated

## Assumptions
- Commit/stage return in v1.1 after real-user feedback; the flag makes that a one-line change

## Notes
- The agent can still run git itself through its own tools; this only removes the app's direct write buttons

## Testing
- `./gradlew :app:assembleRelease` build shows no stage buttons or commit box; debug still has them
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

## Tools / Skills
- Bash: `./gradlew`

## Implementation
<!-- Write you've done in here -->
