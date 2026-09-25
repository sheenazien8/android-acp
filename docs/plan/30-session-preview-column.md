# Store the session preview instead of computing it

## Context
- files: @app/src/main/java/com/lakasir/acp/data/local/Daos.kt
- files: @app/src/main/java/com/lakasir/acp/data/local/Entities.kt
- files: @app/src/main/java/com/lakasir/acp/data/local/Migrations.kt
- files: @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt
- `SessionDao.observeSummaries` runs a correlated subquery on `messages` per session to get the last TEXT message as preview
- The query also re-runs on every `messages` write (every streaming flush), because Room observes both tables

## Goals
1. Add `sessions.lastPreview TEXT` (next Room version; combine with plan 21's migration if done together)
2. Update it when a turn ends (last agent text, trimmed to ~200 chars) and when a user prompt is sent
3. Migration backfills `lastPreview` with the existing subquery once
4. `observeSummaries` reads only from `sessions`, so message writes no longer re-trigger the sessions list

## Notes
- `SessionSummary` can become just `SessionEntity`; update `SessionsScreen` previews
- Order of plan versions: whichever of 21/30 ships first takes v4

## Testing
- Unit: migration SQL matches the new schema (plan 31 schema test)
- Device: preview updates after a turn; sessions list does not flicker while a reply streams
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

## Tools / Skills
- Bash: `./gradlew`

## Implementation
<!-- Write you've done in here -->
