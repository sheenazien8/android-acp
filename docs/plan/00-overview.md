# Android ACP Client MVP — Overview

## Context
- files: empty directory `android-acp/` (greenfield)
- Toolchain on machine: JDK 17, Android SDK platforms 34/35/36, build-tools 36.0.0, Gradle 8.10.2 wrapper cached, Compose/Room/OkHttp/kotlinx artifacts cached
- Bridge exposes ACP JSON-RPC 2.0 over WebSocket at `ws://<host>:<port>/acp` (not in scope)
- ACP schema verified against agentclientprotocol.com (2026-09):
  - `initialize` params `{protocolVersion: 1, clientCapabilities: {fs: {readTextFile:false, writeTextFile:false}, terminal:false}, clientInfo}`
  - `session/new` params `{cwd, mcpServers: []}` → `{sessionId}`
  - `session/load` params `{sessionId, cwd, mcpServers}` (only if `agentCapabilities.loadSession`)
  - `session/prompt` params `{sessionId, prompt: [{type:"text", text}]}` → `{stopReason}`
  - `session/cancel` notification `{sessionId}`
  - `session/update` notification `{sessionId, update: {sessionUpdate: agent_message_chunk | agent_thought_chunk | user_message_chunk | tool_call | tool_call_update | plan | ...}}`
  - tool call: `toolCallId, title, kind, status (pending|in_progress|completed|failed), content[] (content | diff{path, oldText, newText} | terminal), rawInput`
  - `session/request_permission` (agent→client request) `{sessionId, toolCall, options[{optionId, name, kind: allow_once|allow_always|reject_once|reject_always}]}` → `{outcome: {outcome:"selected", optionId}}` or `{outcome:{outcome:"cancelled"}}`

## Goals
Deliver the MVP as a sequence of feature plans. Each one is built, compiled and checked before the next starts, so progress can be tracked file by file.

| # | Plan | Depends on |
|---|------|-----------|
| 01 | [Project setup](01-project-setup.md) | — |
| 02 | [Design tokens & theme](02-design-tokens.md) | 01 |
| 03 | [ACP protocol layer](03-acp-protocol-layer.md) | 01 |
| 04 | [Room database](04-room-database.md) | 01 |
| 05 | [Repository & connection lifecycle](05-repository.md) | 03, 04 |
| 06 | [Connection screen](06-connection-screen.md) | 02, 05 |
| 07 | [Sessions screen](07-sessions-screen.md) | 02, 05 |
| 08 | [Chat screen](08-chat-screen.md) | 02, 05 |
| 09 | [Permission dialog](09-permission-dialog.md) | 05, 08 |
| 10 | [Multiple concurrent connections](10-multiple-concurrent-connections.md) | 05, 06 |
| 11 | [Background connection](11-background-connection.md) | 10 |
| 12 | [Session CRUD](12-session-crud.md) | 07, 08 |
| 13 | [Auto mode](13-auto-mode.md) | 09, 12 |
| 14 | [File manager](14-file-manager.md) | 08, 10 |
| 15 | [Git support](15-git-support.md) | 14 |

## Assumptions
- `cwd` (absolute path on the bridge machine) is stored per `ConnectionProfile` (confirmed by user), because ACP `session/new` requires it
- Manual DI (no Hilt) — lighter for MVP
- Fonts: system `FontFamily.SansSerif` (Roboto) + `FontFamily.Monospace`, no bundled font files
- Package name `com.lakasir.acp`
- Code in English, no inline comments (global rule); protocol-adjustable names live only in `AcpMethods.kt` / `AcpEvent` parser
- Multiple profiles stored, and several can be connected at the same time, each with its own client (plan 10; the MVP started with one active connection)
- UI follows the project skill `mobile-android-design` (Material 3 + Compose), with these project-specific deviations:
  - Dynamic color (Material You) is **off**: the fixed brand palette and single teal accent from the spec win over wallpaper colors
  - Skill's rounded 12–16dp cards/avatars are **not** used; blocks are flat with a 4dp radius and role-colored left border (spec's "tool, not chatbot" direction)
  - No bottom nav / drawer: the app is a linear stack (Connections → Sessions → Chat). Exception (plans 14–15): the chat screen has an end-side workspace sidebar (Files | Git)
- Workspace features (plans 14–15) use `_lakasir/*` ACP extension methods answered by the bridge itself, on the same WebSocket; paths are relative to the profile `cwd`

## Notes
- Out of scope / TODO: mDNS discovery, TLS/auth (more important once plan 14 allows file writes), fs/terminal client capabilities, git push/pull/checkout, voice, agent-side session modes (`session/set_mode`)
- Background connection and notifications, first out of scope, were added in plan 11
- Every plan ends with `./gradlew assembleDebug` passing

## Testing
- `./gradlew assembleDebug testDebugUnitTest` after each feature plan
- Manual end-to-end against a LAN bridge after plan 09

## Tools / Skills
- Skill `mobile-android-design` (@.claude/skills/mobile-android-design) — load before plans 02, 06, 07, 08, 09, 12, 13, 14, 15; references: `material3-theming.md`, `android-navigation.md`, `compose-components.md`
- Bash: `./gradlew`
- WebFetch for ACP schema re-checks

## Implementation
- Plans 01–09 implemented, one commit each (see each plan's Implementation section for details and any deviations)
- Plans 10–13 implemented (multiple connections, background service + notifications, session rename/delete, auto mode) but not committed yet
- Room database is at version 2 (plan 13 adds `sessions.autoApprove` via `MIGRATION_1_2`)
- 68 JVM unit tests after plan 13, all passing; `lintDebug` passes
- Debug APK installed over adb on a Pixel 7a (Android 15); the app launches and the Connections screen renders
- Not yet verified: end-to-end against a real ACP bridge (connect → initialize → new session → prompt streaming → tool calls → permission → cancel)
