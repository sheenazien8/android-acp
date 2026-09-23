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
<!-- Write you've done in here -->
