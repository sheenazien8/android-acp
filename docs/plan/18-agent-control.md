# 18 — Agent Control (Model Switching, Context Usage, Commands)

## Context

- files: @app/src/main/java/com/lakasir/acp/acp/AcpMethods.kt
- files: @app/src/main/java/com/lakasir/acp/acp/AcpEvent.kt
- files: @app/src/main/java/com/lakasir/acp/acp/AcpClient.kt
- files: @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt
- files: @app/src/main/java/com/lakasir/acp/ui/chat/ChatViewModel.kt
- files: @app/src/main/java/com/lakasir/acp/ui/chat/ChatScreen.kt
- files: @app/src/main/java/com/lakasir/acp/ui/chat/components/ChatInputBar.kt
- files: @docs/plan/00-overview.md

Current state (plans 01–16 implemented, plan 17 is a separate offline-indicator fix):

- `AcpClient.newSession(cwd)` returns only `sessionId`; the `configOptions` and `modes` fields of the `session/new` response are discarded. `loadSession(id, cwd)` discards the response entirely.
- `AcpEventParser` handles `agent_message_chunk`, `agent_thought_chunk`, `user_message_chunk`, `tool_call`, `tool_call_update`, `plan`. Every other `sessionUpdate` becomes `AcpEvent.Unknown`, which `AcpRepository.persist` writes as a **visible** `SYSTEM`/`TEXT` message. So `config_option_update`, `available_commands_update`, `usage_update`, `current_mode_update` currently show up as raw system notes.
- `AcpRepository` keeps per-session runtime state in memory (`busySessions`, `attachedSessions`, `pendingPermissions`), keyed by local session id; Room stores only profiles, sessions and messages. `clearProfileConnectionState` resets the in-memory sets on disconnect.
- The chat bottom bar is `ChatInputBar` only (`OutlinedTextField` + send/cancel button). The top bar is crowded already (connection indicator, auto-mode toggle, workspace, session menu).
- The bridge (@bridge/src/server.js) forwards every non-`_lakasir/*` method to the agent verbatim, so new ACP methods need **no bridge change**.

ACP v1 wire facts (verified in @node_modules/@agentclientprotocol/sdk/schema/schema.json and the claude-agent-acp adapter):

- **Model** — there is no `session/set_model` in protocol v1. The model is a session **config option** with `id: "model"` and `category: "model"`, `type: "select"`, `currentValue` (a value id) and `options` (flat list or grouped list). Returned in `session/new` and `session/load` responses as `configOptions` and pushed via the `config_option_update` session update. Switched with `session/set_config_option` params `{sessionId, configId, value}` → response `{configOptions}`.
- **Context tokens** — the `usage_update` session update: `{used, size, cost?: {amount, currency}}`; `used`/`size` are required. `PromptResponse` may also carry `usage` (unstable, not needed here).
- **Commands** — the `available_commands_update` session update: `{availableCommands: [{name, description, input?: {hint}}]}`. It replaces the whole set each time. There is no execute-command method; a command is run by sending `/name args` as a normal `session/prompt`.
- **Modes** — `current_mode_update` and `session/set_mode` exist but are out of scope (see Assumptions); the adapter also exposes modes as config options with `category: "mode"`.

## Goals

1. **Capture session control state** — parse `configOptions` from `session/new` and `session/load`, and the `config_option_update`, `available_commands_update`, `usage_update` session updates into typed models.
2. **Stop the noise** — those four update kinds no longer persist as system messages.
3. **In-memory per-session state** — `AcpRepository` exposes `sessionControls: StateFlow<Map<Long, SessionControlState>>` keyed by local session id, populated on create/load, updated by events, cleared on disconnect. No Room change (DB stays version 3).
4. **Model switching** — a model chip in the chat bottom area shows the current model and opens a picker sheet; selecting calls `session/set_config_option` and applies the returned `configOptions`.
5. **Context token info** — a compact `used / size` indicator (with a thin progress bar) driven by `usage_update`, placed next to the model chip.
6. **Commands support** — typing `/` shows a filtered list of the agent's available commands above the input; tapping inserts `/name ` and shows the command's input hint. Sending is unchanged (`/name args` goes through `session/prompt`).
7. **Docs** — add row 18 to @docs/plan/00-overview.md and move "model/config options, commands, usage" out of the out-of-scope TODO.

## Assumptions

The `question` tool was unavailable in this session, so the following were inferred (documented per the plan skill):

- **Scope is exactly the three listed features.** Session modes (`session/set_mode`) and generic config options (reasoning `effort`, `thought_level`, boolean toggles) are **not** implemented. `current_mode_update` is still parsed so it does not become a system note, but it is ignored by the repository. The config-option plumbing is generic (`setConfigOption(configId, value)`), so adding modes or effort later is a UI-only follow-up.
- **"Commands support" = agent-provided slash commands** from `available_commands_update`, surfaced as an input autocomplete. No app-local commands (`/clear`, `/cancel`) are added; the existing Stop button already covers cancel.
- **Model switching uses config options, not `session/set_model`**, because v1 has no such method and the installed adapter uses config options.
- **State is in-memory only.** Usage/commands/models are per-connection and repopulated on `session/load`, so persisting them would add a migration for little value. Restarting the app shows the controls again only once a session is attached.
- **`clientCapabilities.session.configOptions = {}`** is advertised in `initialize` (spec-correct for select options; `boolean` stays unadvertised since boolean options are out of scope).

## Goals → Files

| Change | File |
|---|---|
| New method name, update kinds, config category/type constants | @app/src/main/java/com/lakasir/acp/acp/AcpMethods.kt |
| `ConfigOption`, `ConfigChoice`, `AgentCommand`, `UsageInfo`, `SessionControlState` + parser | @app/src/main/java/com/lakasir/acp/acp/SessionConfig.kt (new) |
| New events + parser branches for the four update kinds | @app/src/main/java/com/lakasir/acp/acp/AcpEvent.kt |
| `newSession`/`loadSession` return config options; `setConfigOption`; advertise client capability | @app/src/main/java/com/lakasir/acp/acp/AcpClient.kt |
| `sessionControls` StateFlow, capture on create/load, route events, `setModel`/`setConfigOption`, clear on disconnect | @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt |
| Expose controls/model/usage/commands, `setModel` | @app/src/main/java/com/lakasir/acp/ui/chat/ChatViewModel.kt |
| Pure `TokenFormat` helper | @app/src/main/java/com/lakasir/acp/ui/chat/TokenFormat.kt (new) |
| Pure command-suggestion/filter/insert helper | @app/src/main/java/com/lakasir/acp/ui/chat/CommandSuggestions.kt (new) |
| Control bar (model chip + usage) and picker | @app/src/main/java/com/lakasir/acp/ui/chat/components/SessionControlBar.kt (new) |
| Model picker bottom sheet | @app/src/main/java/com/lakasir/acp/ui/chat/components/ModelPickerSheet.kt (new) |
| `/` suggestions list | @app/src/main/java/com/lakasir/acp/ui/chat/components/CommandSuggestions.kt (new) |
| Suggestion + command-hint params | @app/src/main/java/com/lakasir/acp/ui/chat/components/ChatInputBar.kt |
| Wire the bottom bar and sheet | @app/src/main/java/com/lakasir/acp/ui/chat/ChatScreen.kt |
| Row 18 + TODO cleanup | @docs/plan/00-overview.md |

## Notes

### Protocol layer (`SessionConfig.kt`, `AcpMethods.kt`, `AcpEvent.kt`)

- Constants to add in `AcpMethods`: `SESSION_SET_CONFIG_OPTION = "session/set_config_option"`; `SessionUpdateKind.CONFIG_OPTION_UPDATE` (`"config_option_update"`), `AVAILABLE_COMMANDS_UPDATE` (`"available_commands_update"`), `USAGE_UPDATE` (`"usage_update"`), `CURRENT_MODE_UPDATE` (`"current_mode_update"`); `SessionConfigCategory.MODEL = "model"` (+ `MODE`, `THOUGHT_LEVEL`, `MODEL_CONFIG`); `SessionConfigType.SELECT = "select"`, `BOOLEAN = "boolean"`; `SessionConfigIds.MODEL = "model"` as the fallback id.
- `SessionConfig.kt` models (no inline comments):

  ```kotlin
  data class ConfigChoice(val value: String, val name: String, val description: String? = null)
  data class ConfigOption(
      val id: String,
      val name: String,
      val description: String?,
      val category: String?,
      val type: String,
      val currentValue: String?,
      val currentBoolean: Boolean?,
      val choices: List<ConfigChoice>,
  )
  data class AgentCommand(val name: String, val description: String, val hint: String? = null)
  data class UsageInfo(val used: Long, val size: Long, val costAmount: Double?, val costCurrency: String?)
  data class SessionControlState(
      val configOptions: List<ConfigOption> = emptyList(),
      val commands: List<AgentCommand> = emptyList(),
      val usage: UsageInfo? = null,
  ) {
      val model: ConfigOption?
          get() = configOptions.firstOrNull { it.category == SessionConfigCategory.MODEL }
              ?: configOptions.firstOrNull { it.id == SessionConfigIds.MODEL }
  }
  ```

- Parser rules:
  - Select `options` may be a **flat array of option objects** or an **array of groups** (`{group, name, options:[...]}`). Flatten both into `choices`, skip entries without `value`.
  - A select without `currentValue` (or a non-string one) is still listed but has no current selection; render it disabled in the sheet.
  - Boolean options: parse `currentValue` as bool into `currentBoolean`; they are not advertised and not rendered (skip in UI), but parsing them must not throw.
  - `usage_update`: require numeric `used` and `size`, else return `null` and drop the update. `cost` is optional (`amount` double + `currency` string).
  - `available_commands_update`: require `name`; `description` may be missing (fall back to `""`); `input` is optional and only `{hint}` matters.
  - Unknown/extra keys are ignored; malformed entries are skipped, never thrown.
- `AcpEvent` additions: `ConfigOptionUpdated(sessionId, options, raw)`, `CommandsUpdated(sessionId, commands, raw)`, `UsageUpdated(sessionId, usage, raw)`, `CurrentModeUpdate(sessionId, modeId, raw)`. All carry `raw` like existing events.
- `AcpEventParser.parseSessionUpdate` branches: parse the three useful updates; `CURRENT_MODE_UPDATE` → `CurrentModeUpdate`. Everything else stays `Unknown` (so genuinely unknown updates remain visible, unchanged behavior).

### Client (`AcpClient.kt`)

- Add `data class SessionCreated(val sessionId: String, val configOptions: List<ConfigOption>)`. `newSession(cwd)` returns it (parse `configOptions` from the result object; missing/null → empty). `loadSession(sessionId, cwd)` returns `List<ConfigOption>` from its response.
- `setConfigOption(sessionId, configId, value): List<ConfigOption>` sends `{sessionId, configId, value}` (string variant, no `type`) and returns the parsed `configOptions` from the response.
- `initialize` adds `clientCapabilities.session = { configOptions: {} }`.
- Both signature changes require updating `AcpRepository` and @app/src/test/java/com/lakasir/acp/acp/AcpClientTest.kt.

### Repository (`AcpRepository.kt`)

- New `private val _sessionControls = MutableStateFlow<Map<Long, SessionControlState>>(emptyMap())` + public `sessionControls`, following the existing `_busySessions` pattern. Helpers: `private fun mergeControls(localId, transform)`.
- `createSession`: insert the session, then store `SessionControlState(configOptions = created.configOptions)`.
- `openSession`: on a successful `session/load`, update the session's `configOptions` from the response.
- `handleEvent` / `persist`: `ConfigOptionUpdated` replaces `configOptions`; `CommandsUpdated` replaces `commands`; `UsageUpdated` replaces `usage`; `CurrentModeUpdate` → `Unit`. None of these insert messages.
- `setConfigOption(localId, configId, value)`: resolve session → client; on success update `configOptions` from the response; catch `AcpException` and insert a `SYSTEM`/`ERROR` message (`"Could not change setting: ${e.message}"`). `setModel(localId, value)` resolves the model option id (category `model`, else `model`) and delegates.
- `clearProfileConnectionState`: also `_sessionControls.update { it - affected }` for the profile's sessions (same as busy/attached). Controls are therefore absent while offline and reappear after `openSession` re-loads.
- Guard: if the profile is not `Connected`/attached, `setConfigOption` is a no-op (the UI hides controls in that state anyway).

### UI (`ChatScreen.kt`, `ChatViewModel.kt`, new components)

- `ChatViewModel` additions:
  - `controls: StateFlow<SessionControlState>` = `repository.sessionControls.map { it[sessionId] ?: SessionControlState() }`.
  - `model`, `commands`, `usage` derived from `controls`.
  - `setModel(value)` → `repository.setModel(sessionId, value)`.
  - No new persistence; nothing changes in `InputAvailability`.
- `ChatScaffold.bottomBar` becomes a `Column` with, top to bottom: `CommandSuggestions` (only when active), `SessionControlBar`, then the existing `ChatInputBar`. Keep `ChatInputBar`'s own divider; `SessionControlBar` draws its own top divider so the two blocks stay visually separated.
- `SessionControlBar(controls, isBusy, onOpenModelPicker)`:
  - Model chip (`AssistChip`/`SuggestionChip` with a small leading icon and a dropdown chevron) showing the current choice's `name` (fall back to the option `name`). Hidden when there is no model option. Disabled while `isBusy` (switch is only allowed between turns).
  - `ContextUsageIndicator(usage)` next to it: `TokenFormat.compact(used) / TokenFormat.compact(size)` plus a thin `LinearProgressIndicator` (fraction clamped to `0f..1f`); tint switches to `error` at ≥ 80%. Hidden when `usage == null`. `contentDescription` gives exact numbers.
  - If neither model nor usage exists, render nothing (no empty bar / divider).
- `ModelPickerSheet(current, choices, busy, onSelect, onDismiss)`: `ModalBottomSheet` like @app/src/main/java/com/lakasir/acp/ui/connection/ProfileFormSheet.kt. Radio rows (`name` + optional `description`) with the current value checked; tapping calls `onSelect(value)` and closes. Show a disabled state / inline progress while the request is in flight. The sheet is driven by `showModelPicker` in `ChatScaffold`, so keep it out of `ChatContent`'s parameter list if possible (or thread one boolean + callback).
- `CommandSuggestions`: active only when `input` starts with `/` **and** the first token has no whitespace after it. Filter `commands` by prefix (case-insensitive), show up to 6 rows in a `Surface`/`Card` (each: `/name` in monospace + description, `hint` as supporting line). Tapping calls `onPick(name)`; `ChatScreen` sets `input = "/$name "` and refocuses the text field via a `FocusRequester`. Hidden when there are no commands or no match.
- `ChatInputBar` gains `suggestions: List<AgentCommand> = emptyList()`, `onPickSuggestion: (String) -> Unit = {}`, and `commandHint: String?` (shown as supporting text when the input is exactly `/name ` for a command whose `hint` is set). Reuse the existing `hint` slot only for availability; do not overload it.
- Pure helpers, unit-testable and UI-free:
  - `TokenFormat.compact(Long): String` → `1200` → `"1.2k"`, `200000` → `"200k"`, `< 1000` unchanged.
  - `CommandSuggestions.filter(input, commands)` → `List<AgentCommand>`; `CommandSuggestions.isActive(input)` → `Boolean`.
- `send` is unchanged: a `/name args` string goes through `sendPrompt` and thus `session/prompt`. Note it in the input placeholder copy only if needed (keep "Message the agent").

### Edge cases

- Agents without config options / commands / usage: the control bar hides itself; the screen looks exactly like today apart from unknown-update notes being gone.
- `configOptions` can be `null`/omitted on `session/new` for older agents — treat as empty.
- Model switch while offline/not attached: chip hidden, no request possible.
- Model switch failure: keeps the old selection, surfaces a system error message.
- `available_commands_update` is a full replacement, not a delta.
- On reconnect the agent may re-emit `available_commands_update` after `session/load`; if it does not, the command list stays empty until it does (acceptable).
- Do not log config values or the auth token; `ConfigOption` may contain model ids only, but keep logging off.
- Follow the project rules: no inline comments, one commit for this plan, English identifiers.

## Testing

- `./gradlew assembleDebug testDebugUnitTest` (project rule: every plan ends green).
- New JVM tests:
  - `acp/SessionConfigTest.kt` — `configOptions` with a flat select, a grouped select, a boolean, a missing `currentValue`, and unknown fields; `usage_update` (with/without `cost`, missing `used`/`size`); `available_commands_update` (with/without `input.hint`); model lookup by `category` and by fallback `id`.
  - `acp/AcpEventParserTest.kt` (extend) — the four new `sessionUpdate` kinds map to the new events with correct fields; `current_mode_update` → `CurrentModeUpdate`; an unknown kind still → `Unknown`.
  - `acp/AcpClientTest.kt` (extend) — `newSession` returns `sessionId` + `configOptions`; `loadSession` returns `configOptions`; `setConfigOption` sends `{sessionId, configId, value}` and parses the response; `initialize` params include `clientCapabilities.session.configOptions`.
  - `data/AcpRepositoryRulesTest.kt` (extend) or a new repository test with a fake `AcpClientFactory` — controls populated on create; events replace options/commands/usage; controls cleared on disconnect; `setModel` resolves the option id and applies the response; failure inserts an error and keeps the old value.
  - `ui/chat/TokenFormatTest.kt` — boundaries (999, 1000, 1_500, 200_000, 1_000_000).
  - `ui/chat/CommandSuggestionsTest.kt` — inactive without `/`, inactive after a space, prefix filtering, case-insensitivity, empty command list.
- Manual, end-to-end against the bridge running `claude-agent-acp`:
  1. Open a session → the model chip shows the agent's current model; the usage indicator appears after the first turn.
  2. Open the picker, switch models → chip updates from the `config_option_update`/response; send a prompt and confirm the agent uses the new model.
  3. Send a couple of prompts → usage grows; verify formatting and the ≥ 80% warning color.
  4. Type `/` → command list appears; pick one → input becomes `/name ` with the hint; send it and confirm the agent runs it.
  5. Disconnect / kill the bridge → the control bar disappears; reconnect → it comes back after the session resumes.
  6. Existing flows (permission dialog, auto mode, workspace sidebar, git) unchanged.

## Tools / Skills

- Skill `mobile-android-design` → `references/compose-components.md` (chips, `ModalBottomSheet`, `LinearProgressIndicator`, `LazyColumn` for the picker/suggestions), `references/material3-theming.md` for the control-bar typography/colors.
- Bash: `./gradlew assembleDebug testDebugUnitTest`.
- No database MCP needed (no Room change). Re-check @node_modules/@agentclientprotocol/sdk/schema/schema.json if a field name is in doubt; the bridge needs no change.

## Implementation
<!-- Write you've done in here -->
