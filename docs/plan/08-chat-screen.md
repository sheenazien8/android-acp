# 08 — Chat Screen

## Context
- files: @app/src/main/java/com/lakasir/acp/ui/chat/ (new)
- Depends on 02 and 05

## Goals
- `ChatViewModel(sessionId)`: messages Flow from Room, busy state, connection state, input text, send/cancel; calls `openSession` on enter
- `ChatScreen` (`Scaffold`):
  - `TopAppBar`: session title, `ConnectionStatusIndicator`, back
  - log-style `LazyColumn` with stable `key = message.id` and `contentType = message.type`: each row has a thin left border colored by role (user = accent, agent = onSurfaceVariant, tool/system = outline, error = error); flat, small radius
  - agent text: streaming appended chunks, subtle fade on the newest chunk only; long-press to copy
  - thought chunks: dimmed, collapsible
  - `ToolCallBlock`: header row (title/kind + `AssistChip`-style status: running/done/error), collapsible body in mono with input and truncated output; `AnimatedVisibility` expand + rotating chevron, header `contentDescription` "Expand/Collapse"; expanded state via `rememberSaveable(toolCallId)`
  - `DiffBlock`: file path header + lines with `+`/`-` backgrounds using `AcpTheme.extended.diffAdded/Removed`, horizontal scroll for long lines
  - thinking indicator row while busy (`LinearProgressIndicator` or 3-dot), with `liveRegion` semantics
  - bottom input bar with `imePadding()` + `navigationBarsPadding()`: multiline `OutlinedTextField` (text via `rememberSaveable`), Send `FilledIconButton` (accent, `contentDescription = "Send"`); Cancel `OutlinedIconButton` replaces Send while busy
  - auto-scroll to bottom on new content only if already near bottom; "jump to latest" small FAB otherwise
- Route `Chat(sessionId)` read with `savedStateHandle.toRoute()`
- Components split into `ui/chat/components/` (MessageRow, ToolCallBlock, DiffBlock, ChatInputBar) each with `@Preview` in dark + light

## Notes
- Load from Room first (instant), then attach live session
- Input disabled when disconnected, with a short hint
- No auto entry animations for every row — only for user actions and streaming text

## Testing
- `./gradlew assembleDebug`
- Unit test the line diff helper

## Tools / Skills
- Skill `mobile-android-design` → `references/compose-components.md` (LazyColumn, AnimatedVisibility, loading states, text fields)
- Bash: `./gradlew`

## Implementation
- `ui/chat/ChatViewModel.kt`: takes `sessionId` directly (keyed `viewModel`, so it works both as a route and as the tablet detail pane); `items` mapped off the main thread; `isBusy`; `InputAvailability` (Ready / Offline / Resuming / NotResumable); calls `openSession` whenever this session's profile becomes connected; `send`, `cancel`
- `ui/chat/ChatItem.kt`: UI model per message type + `ChatItemMapper`, which reuses already-decoded items when the row didn't change (Room re-emits the whole list on every chunk)
- `ui/chat/LineDiff.kt`: LCS line diff, falls back to "all removed + all added" above 400k cells
- `ui/chat/TextSegments.kt`: splits agent text on ``` fences (an unclosed fence while streaming still renders as code)
- `ui/chat/ChatScreen.kt`: `TopAppBar` (title, status indicator, back hidden in the tablet pane), `LazyColumn` with `reverseLayout` so streaming stays pinned to the bottom unless the user has scrolled up, stable keys + `contentType`, thinking row while busy, "Jump to latest" small FAB, input bar as `bottomBar`; dark + light previews
- `ui/chat/components/`: `LogRow` (role-colored left border + lowercase role label), `AgentText` (prose + `CodeBox` segments, selectable; newest chunk fades in only on the streaming row), `CodeBox`, `ToolCallBlock` (chevron rotates, `AnimatedVisibility`, expanded state saved per `toolCallId`, starts expanded when it has diffs, TalkBack expand/collapse actions), `ToolStatusLabel` (pending / running / done / error), `DiffBlock` (+/- counts, tinted lines, horizontal scroll, 300-line cap), `ThoughtBlock` (collapsed to 2 lines), `PlanBlock`, `PermissionRecordRow`, `ErrorRow`, `SystemNoteRow`, `ThinkingIndicator` (3 dots, polite live region), `ChatInputBar` (`imePadding` + `navigationBarsPadding`, Send `FilledIconButton`, Cancel `OutlinedIconButton` while busy, hint when input is disabled)
- `AppNavHost`: Chat route and tablet detail pane both use `ChatScreen`
- Changes from plan:
  - Copying uses `SelectionContainer` (long-press → select → copy) instead of a custom long-press handler
  - Auto-scroll uses `reverseLayout` rather than manual scroll-on-change
- Tests: `LineDiffTest`, `TextSegmentsTest`, `ChatItemMapperTest`; 52 total, all pass
- Verified: `./gradlew assembleDebug testDebugUnitTest` OK; installed over adb on a Pixel 7a (Android 15). The app launches with no crash, but the phone was locked so I couldn't check the UI on screen
