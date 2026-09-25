# Migration tests

## Context
- files: @app/src/test/java/com/lakasir/acp/data/MigrationSchemaTest.kt (untracked)
- files: @app/src/main/java/com/lakasir/acp/data/local/Migrations.kt
- files: @app/src/main/java/com/lakasir/acp/data/local/AppDatabase.kt
- files: @app/schemas/
- The JVM schema test compares `MIGRATION_2_3` SQL with the exported schema JSON; it is not committed
- No test runs the real migrations on SQLite; a broken migration on Play would wipe or crash every user's app

## Goals
1. Commit `MigrationSchemaTest` and extend it to every migration (1→2, 2→3, and future ones)
2. Add an instrumented `MigrationTest` using `androidx.room:room-testing` `MigrationTestHelper`: create v1, insert rows, migrate to latest, validate schema and data
3. Add a rule to plan template notes: every new Room version ships with a migration + both tests; never `fallbackToDestructiveMigration` in release

## Notes
- `androidTest` source set does not exist yet; add `androidx.test.ext:junit`, `androidx.test:runner`, `room-testing`
- `schemas/` must be added as an `androidTest` asset dir (`sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")`)

## Testing
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:connectedDebugAndroidTest` on a device or emulator

## Tools / Skills
- Bash: `./gradlew`, `adb`

## Implementation
<!-- Write you've done in here -->
