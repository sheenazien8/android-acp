# Agent Control Implementation

## Protocol
- [x] Add `SessionConfig.kt` with `ConfigOption`, `AgentCommand`, `UsageInfo`, `SessionControlState`, and parser
- [x] Extend `AcpMethods.kt` with `session/set_config_option` and new update kinds
- [x] Extend `AcpEvent.kt` with control events and parser branches
- [x] Update `AcpClient.kt` to advertise `configOptions` capability and return config options on new/load session

## Repository
- [x] Add `sessionControls` StateFlow to `AcpRepository`
- [x] Capture config options from `createSession` and `openSession`
- [x] Route `config_option_update`, `available_commands_update`, `usage_update`, and `current_mode_update` to controls
- [x] Add `setModel` / `setConfigOption` and clear controls on disconnect

## UI
- [x] Add `TokenFormat.kt` for compact token counts
- [x] Add `CommandSuggestions.kt` for slash-command filtering
- [x] Add `SessionControlBar.kt` with model chip and usage bar
- [x] Add `ModelPickerSheet.kt`
- [x] Add `CommandSuggestionMenu.kt`
- [x] Wire controls into `ChatInputBar`, `ChatScreen`, and `ChatViewModel`

## Tests
- [x] Update `AcpEventParserTest` for new event kinds
- [x] Update `AcpClientTest` for `SessionCreated` result
- [x] Add `SessionConfigTest`
- [x] Add `TokenFormatTest`
- [x] Add `CommandSuggestionsTest`
- [ ] Repository control-state integration test (blocked by no Room test harness; covered by parser/client tests)

## Verification
- [x] `:app:compileDebugKotlin` passes
- [x] `:app:testDebugUnitTest` passes
- [x] `:app:lintDebug` passes
