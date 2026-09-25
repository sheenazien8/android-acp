# Streaming write batching and chat paging

## Context
- files: @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt
- files: @app/src/main/java/com/lakasir/acp/data/local/Daos.kt
- files: @app/src/main/java/com/lakasir/acp/ui/chat/ChatViewModel.kt
- files: @app/src/main/java/com/lakasir/acp/ui/chat/ChatItem.kt
- files: @app/src/main/java/com/lakasir/acp/ui/chat/ChatScreen.kt
- Every `agent_message_chunk` / `agent_thought_chunk` calls `messageDao.appendContent` (`AcpRepository.kt:406`)
- Each write invalidates `messages`, so `observeBySession` re-queries the whole session and `ChatItemMapper` walks all rows (it caches unchanged items, but still compares every entity)
- Cost per chunk grows with session length; long sessions stream slowly and use more battery

## Goals
1. Buffer streaming text in memory per session:
   - `StreamingBuffer` holds the open message id + pending text
   - Flush to Room every 300ms, on type/role change, on tool call, and on turn end / cancel / disconnect
   - Expose the in-flight text as a `StateFlow<Map<Long, StreamingText>>` so the UI shows chunks immediately
2. `ChatViewModel.items` = `combine(persisted items, streaming overlay)`; the overlay replaces the text of the last agent item
3. Load chat history in pages:
   - DAO `observeRecent(sessionId, limit)` ordered by id DESC, starting with 200 rows
   - Load 200 more when the list scrolls near the oldest item (reversed `LazyColumn`)
4. Keep `ChatItemMapper` caching

## Notes
- The flush must happen before `completedTurns` emits, so the notification and resume logic see the full text
- On process death between flushes up to 300ms of text is lost; the agent's `session/load` replay covers it
- Paging keeps `reverseLayout`; key items by message id so scroll position holds when older pages load
- Consider Paging 3 only if the simple limit approach is not enough

## Testing
- Unit: buffer flushes on timer, on type change, and on turn end (`kotlinx-coroutines-test` virtual time)
- Unit: overlay merge puts streamed text into the last agent item
- Device: a session with 2,000+ messages streams smoothly (compare frame times with `adb shell dumpsys gfxinfo`) and scrolls back through history
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

## Tools / Skills
- Bash: `./gradlew`, `adb`

## Implementation
<!-- Write you've done in here -->
