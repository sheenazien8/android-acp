# File Manager (chat sidebar + bridge extension methods)

## Context
- files:
  - @app/src/main/java/com/lakasir/acp/acp/AcpClient.kt
  - @app/src/main/java/com/lakasir/acp/acp/AcpMethods.kt
  - @app/src/main/java/com/lakasir/acp/acp/AcpException.kt
  - @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt
  - @app/src/main/java/com/lakasir/acp/data/local/Entities.kt (`MessageEntity.rawJson`)
  - @app/src/main/java/com/lakasir/acp/data/model/MessagePayloads.kt
  - @app/src/main/java/com/lakasir/acp/di/AppContainer.kt
  - @app/src/main/java/com/lakasir/acp/ui/chat/ChatScreen.kt
  - @app/src/main/java/com/lakasir/acp/ui/chat/ChatViewModel.kt
  - @app/src/main/java/com/lakasir/acp/ui/chat/components/ChatInputBar.kt
  - @app/src/main/java/com/lakasir/acp/ui/chat/components/CodeBox.kt
  - Bridge (WebSocket ↔ `claude-agent-acp` stdio): **not in this repo**, see Assumptions
- Current state:
  - The project files live on the bridge machine under the profile's `cwd`; the phone has no copy
  - ACP has no client→agent file methods. `fs/*` and `terminal/*` are agent→client, and the client advertises them as `false` in `initialize`
  - `AcpClient.request(method, params, timeoutMs)` is generic, so any extra JSON-RPC method can be sent over the same socket; an `ErrorResponse` with `-32601` becomes `AcpException.Rpc`
  - `AcpRepository` owns one `AcpClient` per connected profile (`clients: Map<profileId, AcpClient>`, private)
  - `AcpClient.prompt` sends only a single `text` block; the user row is stored as `MessageType.TEXT` with `content = text` and `rawJson = null`
  - Chat screen: `Scaffold` + `TopAppBar` (back, title, auto-mode toggle), message list, `ChatInputBar`. No drawer (overview listed "no drawer" as a deliberate deviation; this plan changes that for the chat screen only)

## Goals
- **Bridge extension protocol** (ACP `_`-prefixed extension methods, same WebSocket, handled by the bridge and never forwarded to the agent):
  - `_lakasir/hello` → `{version: 1, fs: true, git: bool}` (feature detection; `-32601` = bridge without extensions)
  - `_lakasir/fs/list` `{cwd, path}` → `{entries: [{name, path, type: "file"|"dir"|"symlink", size, mtime}]}` (dirs first, then by name; `.git` hidden)
  - `_lakasir/fs/read` `{cwd, path, maxBytes?}` → `{path, text?, binary: bool, truncated: bool, size, mtime}` (default limit 512 KB, UTF-8; binary = NUL byte in the first 8 KB)
  - `_lakasir/fs/write` `{cwd, path, text, expectedMtime?}` → `{mtime}`; error `-32010` "conflict" when `expectedMtime` differs from the file on disk
  - `_lakasir/fs/create` `{cwd, path, type: "file"|"dir"}`, `_lakasir/fs/rename` `{cwd, from, to}`, `_lakasir/fs/delete` `{cwd, path}` (a dir is deleted recursively only with `recursive: true`)
  - Every `path` is **relative to `cwd`**; the bridge resolves `realpath` and rejects anything outside `cwd` (`..`, absolute paths, symlink escape) with `-32011` "outside workspace"
  - Wire names live in `AcpMethods.kt` as `object LakasirMethods` plus `LakasirErrorCode`
- **Bridge implementation** of the methods above (in the bridge repo), with the path guard and a unit test for the guard
- **App, data layer**:
  - `acp/WorkspaceApi.kt`: typed wrapper over `AcpClient.request` (`hello`, `list`, `read`, `write`, `create`, `rename`, `delete`) with `@Serializable` DTOs `FsEntry`, `FsFile`, `BridgeFeatures`
  - `AcpRepository.workspace(profileId): WorkspaceApi?` (the only new public accessor; `clients` stays private) and `bridgeFeatures: StateFlow<Map<Long, BridgeFeatures?>>`, filled by calling `_lakasir/hello` once after `initialize` (`null` on `-32601`, no error surfaced)
  - `data/repository/WorkspaceRepository.kt`: resolves session → profile → `cwd`, maps `AcpException` to user-facing messages, wired in `AppContainer`
- **App, sidebar**:
  - Chat top bar: "Workspace" icon button (folder icon, 48dp) that opens an **end-side** `ModalNavigationDrawer` (width `min(360dp, 85% of the screen)`)
  - Sidebar header: `cwd` (monospace, middle-ellipsized), refresh button, and `SecondaryTabRow` tabs **Files** | **Git** (Git tab is a "Coming in plan 15" placeholder until plan 15, hidden if `git = false`)
  - If `bridgeFeatures` is `null`: sidebar shows an `EmptyState` "This bridge doesn't support file access" + a short hint to update the bridge
  - Disconnected profile: sidebar shows the same disconnected state as the chat input
- **App, Files tab** (`ui/workspace/`):
  - Lazy tree with expand/collapse; each folder's children load lazily and are cached in the ViewModel; breadcrumb not needed (tree shows depth by 12dp indent)
  - Row: type icon, name, size (files); long-press → menu: **Attach to prompt**, **Rename**, **Delete**, **New file / New folder** (on dirs), **Copy path**
  - Tap a file → full-screen **file viewer** route `FileViewer(sessionId, path)`: monospace, line numbers, horizontal scroll, reuses `CodeBox` styling; binary/truncated → notice instead of content
  - Viewer **Edit** action → editor mode (`BasicTextField`, monospace, no highlighting), Save with `expectedMtime`; on conflict show a dialog: "Reload" (discard edits) or "Overwrite" (save without `expectedMtime`); unsaved-changes guard on back
  - Delete and overwrite ask for confirmation; rename/create use a small text-field dialog with name validation (no `/`, not empty)
- **App, attach to prompt**:
  - `ChatViewModel.attachments: StateFlow<List<String>>` (relative paths, de-duplicated, max 10)
  - `ChatInputBar` shows attached files as removable `InputChip`s above the text field; send is enabled with attachments even when the text is empty
  - `AcpClient.prompt(sessionId, text, attachments)` sends one `text` block (if not blank) plus one `resource_link` block per file: `{type: "resource_link", uri: "file://<cwd>/<path>", name: <file name>}`
  - User row keeps `content = text`; `rawJson = UserPromptPayload(attachments).encode()` (new `@Serializable` class in `MessagePayloads.kt`, default empty list, so old rows decode fine). The user bubble shows the attachment chips (read-only)
- **Auto refresh**: when a `tool_call_update` for the open session reaches `completed` with `kind` `edit`, `delete` or `move`, or carries diffs, the sidebar reloads the expanded folders that contain affected paths (whole tree if paths are unknown), debounced 500 ms

## Assumptions
- Transport is **ACP extension methods on the existing WebSocket** (user's choice 1a). The bridge intercepts `_lakasir/*` and answers them itself
- The bridge source is outside this repo; its location/language is **to be confirmed** before starting. The contract above is written so it can be implemented in any bridge (Node is likely since `claude-agent-acp` is an npm package)
- The workspace root is the **profile `cwd`** (sessions use the profile `cwd` in `session/new`/`session/load`); `cwd` is sent in every call so one bridge can serve several profiles
- Full file management (browse, view, edit, create, rename, delete) plus "attach to prompt" (user said both)
- UI is a **sidebar inside the chat screen** (user's choice), end side, modal on phones. A permanent side panel for wide screens (≥ 840dp) is left as a follow-up
- No local caching in Room; the tree is fetched live and lives only in the ViewModel
- Text files only for view/edit; images and other binaries show "Binary file (N KB)" with no preview

## Notes
- Name the extension prefix `_lakasir/` (ACP reserves leading `_` for extensions, so no clash with future spec methods)
- The bridge must **not** forward `_lakasir/*` to the agent, and must not let agent traffic see them. Responses use the request `id` from the app; there's no clash with ids of requests the bridge forwards, because the app's `nextId` is shared across both
- Security: the bridge has no auth (LAN only, overview "Out of scope: TLS/auth"). File write/delete makes that worse: anyone on the LAN who can open the socket can edit files under `cwd`. Keep the realpath guard strict, refuse to follow symlinks out of `cwd`, and never allow `cwd` itself to be deleted or renamed. Mention this in the Implementation section
- The guard is relative to the `cwd` the app sends, so a client can still pick any `cwd`. Add an optional bridge setting `LAKASIR_ALLOWED_ROOTS` (colon-separated); when set, a `cwd` outside those roots gets `-32011`
- `fs/read` limits: 512 KB default, the viewer shows "Truncated at 512 KB" and disables Edit for truncated files (saving would drop the tail)
- `-32601` from `hello` must not be logged to `protocolErrors`; it's the normal "old bridge" path
- Request timeouts: `list`/`read`/`write` use the default 30 s; large directories (e.g. `node_modules`) are listed as-is, only one level at a time, capped at 2000 entries with `truncated: true`
- The drawer must not steal the chat list's horizontal gestures: set `gesturesEnabled = drawerState.isOpen` so it opens only from the button
- End-side drawer in Compose: wrap `ModalNavigationDrawer` in `CompositionLocalProvider(LocalLayoutDirection provides Rtl)` and restore `Ltr` for the drawer and content
- `resource_link` is part of the ACP baseline (every agent must accept `text` and `resource_link` prompt blocks), so no capability check is needed. `claude-agent-acp` turns it into an `@file` mention
- Paths shown to the user and stored in `UserPromptPayload` are relative; only the wire `uri` is absolute
- Plan 13 auto mode is unrelated, but auto refresh is most useful with it (the agent edits files without prompts)
- UI follows `mobile-android-design` with the overview deviations (flat blocks, 4dp radius, brand palette); 48dp targets; icons have `contentDescription`

## Testing
- `./gradlew :app:compileDebugKotlin testDebugUnitTest lintDebug`
- Unit tests:
  - `WorkspaceApi` DTO parsing: list entries, read with `binary`/`truncated`, missing optional fields
  - `AcpClient.prompt` builds `text` + `resource_link` blocks (text omitted when blank, `uri` built from `cwd` + relative path, no `//`)
  - `UserPromptPayload` round trip + `rawJson = null` decodes to no attachments
  - Error mapping: `-32601` → features `null`; `-32010` → conflict; `-32011` → "outside workspace" message
  - Attachment list rules: de-dup, max 10, remove
- Bridge unit tests: path guard rejects `../x`, `/etc/passwd`, symlink to outside, allows nested paths and `.`
- Manual against a real bridge: open sidebar, expand folders, open a file, edit + save, edit on the machine meanwhile → conflict dialog, create/rename/delete, attach two files and send a prompt (agent reads them), agent edits a file → tree refreshes. Old bridge → "doesn't support file access" state

## Tools / Skills
- Skill `mobile-android-design` (navigation drawer, tabs, chips, dialogs, text editing)
- Bash: `./gradlew`, bridge test runner (to be confirmed)
- WebFetch agentclientprotocol.com (extension methods, `resource_link` content block) to re-check wire shapes before coding

## Implementation
- Bridge: the running bridge was `websocat --restrict-uri /acp ws-l:0.0.0.0:8767 cmd:pi-acp`, a plain pipe that can't answer extension methods. Added **`bridge/`** to this repo (Node ≥ 20, only dependency `ws`), a drop-in replacement: `node bridge/src/server.js --port 8767 -- pi-acp`
  - `src/server.js`: one agent process per WebSocket client, newline-delimited JSON in both directions; messages whose `method` starts with `_lakasir/` are answered by the bridge and never reach the agent; `--host`, `--port`, `--path`, `--allowed-roots` (or `LAKASIR_ALLOWED_ROOTS`)
  - `src/workspace.js`: path guard (`resolveRoot` + `resolveInside`: realpath of the nearest existing ancestor must stay inside `cwd`, dangling/escaping symlinks rejected, root can't be deleted/renamed) and the six `fs/*` handlers; `src/extensions.js`: dispatch + JSON-RPC error mapping; `src/errors.js`: codes
  - Extra error codes beyond the plan: `-32012` not found, `-32013` already exists, `-32000` other I/O errors
  - `bridge/README.md` documents usage and the no-auth risk; `bridge/node_modules/` added to `.gitignore`
  - 18 `node --test` tests (guard, handlers, dispatch, and an end-to-end WebSocket test with a fake echo agent)
- App, protocol: `LakasirMethods`, `LakasirErrorCode`, `FsEntryType`, `PromptBlockType` in `AcpMethods.kt`; `acp/WorkspaceApi.kt` (DTOs + typed calls; `hello` returns `null` on any `AcpException`, 5 s timeout); `AcpClient.prompt(sessionId, text, links)` + `ResourceLink.forWorkspaceFile` (percent-encoded `file://` URI)
- App, data:
  - `AcpRepository`: `bridgeFeatures` (filled by `hello` right after `initialize`, cleared on disconnect), `workspace(localSessionId)`, `workspaceChanges` (emitted when a tool call reaches `completed` with kind `edit`/`delete`/`move`/`execute` or has diffs), `sendPrompt(..., attachments)` storing `UserPromptPayload` in `rawJson`
  - `WorkspaceRepository` (in `AppContainer`): `availability(sessionId)` (Offline / Checking / Unsupported / Ready), `run { }` with error mapping, and the per-session pending attachment list (max 10)
- App, UI (`ui/workspace/`):
  - `WorkspaceSidebar` in an end-side `ModalNavigationDrawer` (RTL wrapper, `min(360dp, 85%)`, flat `RectangleShape`, gestures only while open, back closes it), opened from a folder icon in the chat top bar
  - Lazy tree (`FileTree.kt`: `FolderState`, `TreeRow`, `flattenTree`, `WorkspacePaths` helpers), row tap = expand/open, long-press or the ⋮ button = menu (Open, Attach to prompt, New file/folder, Copy path, Rename, Delete), header + menu for root create and refresh, snackbars for results
  - `FileViewerScreen` (route `FileViewer(sessionId, path)`): line-numbered viewer, truncated banner, binary notice, editor (`BasicTextField`), Save with `expectedMtime`, conflict dialog (Overwrite / Reload), discard-changes guard, Attach action
  - `ChatInputBar` shows removable `InputChip`s; send is enabled with only attachments; the user row lists attached paths
  - Added after review: **markdown preview** for `.md`/`.markdown`/`.mdx` files, a Render/Source action in the viewer top bar (source by default). `ui/markdown/` has a small dependency-free renderer: `MarkdownParser` (headings, paragraphs, bullet/ordered/task lists with nesting, quotes, fenced code, tables, rules, YAML front matter) and `MarkdownInline` (bold, italic, strikethrough, inline code, `http(s)`/`mailto` links as `LinkAnnotation.Url`, images as `[image: alt]`), rendered by `MarkdownView` with the chat `CodeBox` for code
- Changes from plan:
  - The bridge lives in this repo (`bridge/`) instead of a separate bridge repo, because the existing bridge was websocat
  - `fs/list` entries use `type: "file" | "dir" | "other"` + `symlink: Boolean` instead of `type: "symlink"`, so links to folders inside the workspace expand like folders; links that escape it show as `other` and can't be opened
  - No Files | Git tab row yet: the bridge reports `git: false`, so the tab would never show. Plan 15 adds the tabs
  - Viewer wraps long lines instead of scrolling horizontally (more readable on a phone, no shared horizontal scroll between lazy rows)
  - Auto refresh reloads every expanded folder (tool calls don't carry reliable paths) and also triggers on `execute` tool calls (shell commands often change files)
  - Pending attachments live in `WorkspaceRepository` (not `ChatViewModel`) so the sidebar and the file viewer screen can both add to them; `ChatViewModel.attachments` reads from there
  - `AcpRepository.workspace` takes a session id rather than a profile id (it resolves the profile and `cwd` itself)
- Verified:
  - `./gradlew :app:compileDebugKotlin testDebugUnitTest lintDebug assembleDebug` pass: 98 unit tests (30 new: `WorkspaceApiTest`, `WorkspaceRulesTest`, `FileTreeTest`, `MarkdownTest`), lint 0 errors and no new warnings
  - `cd bridge && npm test`: 18 pass
  - Smoke test: the new bridge on a spare port with the real `pi-acp` agent: `initialize` went through to the agent, `_lakasir/hello`, `fs/list` and `fs/read` were answered by the bridge, and `../` was rejected with `-32011`
  - On device (Pixel 7a): the sidebar first showed "File access not available" because the profile pointed at the old websocat port (8090); pointing it at the new bridge fixed it
