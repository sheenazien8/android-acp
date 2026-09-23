# 08 — Chat Screen

## Context
- files: @app/src/main/java/com/lakasir/acp/ui/chat/ (new)
- Depends on 02 and 05

## Goals
- `ChatViewModel(sessionId)`: messages Flow from Room, busy state, connection state, input text, send/cancel; calls `openSession` on enter
- `ChatScreen`:
  - log-style `LazyColumn`: each row has a thin left border colored by role (user = accent, agent = text secondary, tool/system = muted, error = error color); flat, small radius
  - agent text: streaming appended chunks, subtle per-chunk fade on the newest text only
  - thought chunks: dimmed, collapsible
  - `ToolCallBlock`: header (title/kind + status chip running/done/error), collapsible body in mono with input and truncated output; expand animation
  - `DiffBlock`: file path header + lines with `+`/`-` backgrounds using diff tokens (simple line diff of oldText/newText)
  - thinking indicator row while busy
  - bottom input: multiline text field + Send (accent); Cancel replaces Send while busy
  - auto-scroll to bottom on new content if already near bottom
- Nav route `chat/{sessionId}`

## Notes
- Load from Room first (instant), then attach live session
- Input disabled when disconnected, with a short hint
- No auto entry animations for every row — only for user actions and streaming text

## Testing
- `./gradlew assembleDebug`
- Unit test the line diff helper

## Tools / Skills
- Bash: `./gradlew`

## Implementation
<!-- Write you've done in here -->
