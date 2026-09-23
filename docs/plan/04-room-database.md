# 04 — Room Database

## Context
- files: @app/src/main/java/com/lakasir/acp/data/local/ (new)
- Depends on 01

## Goals
- Entities
  - `ConnectionProfileEntity`: `id`, `name`, `host`, `port`, `cwd`, `createdAt`, `lastConnectedAt?`
  - `SessionEntity`: `id`, `remoteSessionId`, `connectionProfileId` (FK cascade, indexed), `title`, `createdAt`, `updatedAt`, `status` (active/closed/error)
  - `MessageEntity`: `id`, `sessionId` (FK cascade, indexed), `role` (user/agent/system/tool), `type` (text/thought/tool_call/diff/permission_request/error), `content`, `toolCallId?` (indexed, for upsert), `timestamp`, `rawJson?`
- Enums stored via `TypeConverters`
- DAOs with `Flow` queries: `ConnectionProfileDao`, `SessionDao` (by profile, ordered by `updatedAt desc`), `MessageDao` (by session ordered by `timestamp, id`; `lastMessage(sessionId)`; `findByToolCallId`)
- `AppDatabase` version 1, `exportSchema = true`, `Migrations.kt` with an empty `ALL` array ready for v2
- Wire DB + DAOs into `AppContainer`

## Notes
- Tool call rows are updated in place by `toolCallId`; `content` holds serialized tool state JSON (title, kind, status, text output, diffs)
- Streaming chunks are appended to the last agent message of the current turn, not one row per chunk

## Testing
- `./gradlew assembleDebug` (KSP generates DAOs; schema JSON written to `app/schemas`)

## Tools / Skills
- Bash: `./gradlew assembleDebug`

## Implementation
- `data/local/Entities.kt`: `ConnectionProfileEntity`, `SessionEntity` (FK cascade, indexes on `connectionProfileId` and `remoteSessionId`), `MessageEntity` (FK cascade, indexes on `sessionId` and `(sessionId, toolCallId)`), `SessionSummary` (session + last text preview)
- Enums: `SessionStatus` (ACTIVE/CLOSED/ERROR), `MessageRole` (USER/AGENT/SYSTEM/TOOL), `MessageType` (TEXT/THOUGHT/TOOL_CALL/PLAN/PERMISSION_REQUEST/ERROR)
- `data/local/Daos.kt`: `ConnectionProfileDao`, `SessionDao` (`observeSummaries` with a preview subquery), `MessageDao` (`appendContent` in SQL for streaming chunks, `findToolCall`, `last`)
- `data/local/AppDatabase.kt` v1 with `exportSchema = true`; `Migrations.kt` with empty `ALL`; schema at `app/schemas/.../1.json`
- `AppContainer.database` (lazy)
- Changes from plan:
  - No custom TypeConverters: Room stores enums by name out of the box
  - No separate `tool_result` / `diff` types: a tool call is one `TOOL_CALL` row updated in place, with its output and diffs in `content`; added `THOUGHT` and `PLAN`
- Verified: `./gradlew assembleDebug` OK, schema JSON generated
