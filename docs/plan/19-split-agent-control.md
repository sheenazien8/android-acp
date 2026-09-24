# Split agent-control features

## Context
- Plan 18 (`docs/plan/18-agent-control.md`) added three features together:
  - Model switching via session config options
  - Context-token usage row
  - Slash-command autocomplete
- All changes are currently on `master` working tree.
- User wants to drop the usage row from `master` and keep it on a separate branch (`feat/agent-context`).
- Model switch + slash-command autocomplete should stay on `master` and be committed.
- A new feature, **thinking level switch**, should be added on `master`.

Relevant files:
- @app/src/main/java/com/lakasir/acp/acp/AcpClient.kt
- @app/src/main/java/com/lakasir/acp/acp/AcpEvent.kt
- @app/src/main/java/com/lakasir/acp/acp/AcpMethods.kt
- @app/src/main/java/com/lakasir/acp/acp/AcpSession.kt
- @app/src/main/java/com/lakasir/acp/acp/SessionConfig.kt
- @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt
- @app/src/main/java/com/lakasir/acp/ui/chat/ChatScreen.kt
- @app/src/main/java/com/lakasir/acp/ui/chat/ChatViewModel.kt
- @app/src/main/java/com/lakasir/acp/ui/chat/components/ChatInputBar.kt
- @app/src/main/java/com/lakasir/acp/ui/chat/components/SessionControlBar.kt
- @app/src/main/java/com/lakasir/acp/ui/chat/components/ModelPickerSheet.kt
- @app/src/main/java/com/lakasir/acp/ui/chat/components/CommandSuggestionMenu.kt
- @app/src/main/java/com/lakasir/acp/ui/chat/CommandSuggestions.kt
- @app/src/main/java/com/lakasir/acp/ui/chat/components/ContextUsageIndicator.kt
- @app/src/main/java/com/lakasir/acp/ui/chat/TokenFormat.kt
- @app/src/test/java/com/lakasir/acp/acp/AcpClientTest.kt
- @app/src/test/java/com/lakasir/acp/acp/AcpEventParserTest.kt
- @app/src/test/java/com/lakasir/acp/acp/SessionConfigTest.kt
- @app/src/test/java/com/lakasir/acp/ui/chat/CommandSuggestionsTest.kt
- @app/src/test/java/com/lakasir/acp/ui/chat/TokenFormatTest.kt
- @app/build.gradle.kts
- @/home/sheenazien8/.nvm/versions/node/v24.19.0/lib/node_modules/pi-acp/dist/index.js (local runtime patch, not in git)

## Goals
1. Create `feat/agent-context` branch from the current working tree and commit the full Plan 18 state there as a save point.
2. On `master`, revert only the usage-row-specific changes:
   - `AcpClient.prompt()` return type and usage parsing fallback.
   - `AcpSession.prompt()` return type.
   - `AcpRepository` usage accumulation branches.
   - Usage row in `ChatScreen` bottom bar.
   - `ContextUsageIndicator.kt` and `TokenFormat.kt` files (and their tests) if no longer used.
   - `isReturnDefaultValues = true` in `app/build.gradle.kts` (only needed for temporary Android Log calls).
   - Revert local `pi-acp` patch.
3. Keep model switch + slash-command autocomplete plumbing intact.
4. Commit the cleaned `master` state.
5. Add a **thinking level switch** on `master`:
   - Discover how pi-acp exposes thinking level as a session config option.
   - Render it next to the model chip in `SessionControlBar` or as a second chip.
   - Wire `setConfigOption` to change it.
6. Verify with `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` and an on-device smoke test.

## Assumptions
- "Thinking level switch" is a session config option exposed by pi-acp (similar to the `model` option), not a custom pi slash command.
- Slash-command autocomplete is considered part of the model-switch/agent-control feature set and should stay on `master`.
- The local `pi-acp` patch is a runtime-only workaround; it should be reverted on `master` because the usage feature is dropped.

## Notes
- The usage row and model switch share `SessionControlState` / `_sessionControls`; be careful to keep the session-controls map but remove only `usage` fields from the UI/data flow.
- If `UsageInfo` / `UsageUpdated` parsing is kept for future `feat/agent-context`, make sure the master repository does not act on it (or treats it as a no-op to avoid creating visible system notes).
- `CommandSuggestions` and `AgentCommand` should remain because slash commands are kept.
- After splitting, `tasks/todo.md` and `docs/plan/18-agent-control.md` may need updates to reflect scope changes.

## Testing
- Unit tests: `./gradlew :app:testDebugUnitTest`
- Lint: `./gradlew :app:lintDebug`
- Build: `./gradlew :app:assembleDebug`
- Device: install debug APK, reconnect, verify model picker and slash commands still work, verify thinking level switch appears and changes, verify no usage row.

## Tools / Skills
- `git` for branching and reverting.
- `gradle` / Android build tools.
- `adb` for device screenshots/logcat.
- `pi-acp` local source inspection.

## Implementation
<!-- Write you've done in here -->
