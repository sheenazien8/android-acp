# Message storage retention and clear history

## Context
- files: @app/src/main/java/com/lakasir/acp/data/local/Entities.kt
- files: @app/src/main/java/com/lakasir/acp/data/local/Daos.kt
- files: @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt
- Every message stores the full raw ACP JSON (`rawJson`) next to the parsed content; nothing is ever deleted except by deleting a session or profile
- Tool calls with large diffs make `rawJson` the biggest part of the DB

## Goals
1. Only write `rawJson` in debug builds (`BuildConfig.DEBUG`); release stores `null`
2. Startup cleanup job: set `rawJson = NULL` for messages older than 7 days (debug) — one `UPDATE` query
3. "Clear history" per connection profile: delete all its sessions and messages (keeps the profile), from the profile edit sheet and the settings screen (plan 36)
4. Show DB size in settings (`context.getDatabasePath(...).length()`)
5. `VACUUM` after a clear

## Notes
- Check nothing in the UI reads `rawJson` in release paths (it is used for debugging tool calls)
- Clearing history while a session is busy: cancel the prompt first or disable the action while busy
- `sessions` → `messages` must cascade (verify the foreign key has `onDelete = CASCADE`)

## Testing
- Unit: DAO clear-by-profile query (Room in-memory test if Robolectric is added, otherwise a device test)
- Device: DB size drops after clear; sessions list becomes empty; the profile still connects
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

## Tools / Skills
- Bash: `./gradlew`, `adb shell run-as com.lakasir.acp ls -l databases/`

## Implementation
<!-- Write you've done in here -->
