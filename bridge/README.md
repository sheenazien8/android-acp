# lakasir-acp-bridge

WebSocket bridge between the Android app and an ACP agent that speaks JSON-RPC over stdio.
It replaces `websocat ... cmd:<agent>` and adds the `_lakasir/*` workspace extension methods
used by the app's workspace sidebar: files (list, read, write, create, rename, delete) and git
(status, diff, log, show, stage, unstage, commit; no push/pull/checkout).

```sh
cd bridge && npm install
node src/server.js --port 8767 -- pi-acp
```

Options:

- `--host` (default `0.0.0.0`), `--port` (default `8767`), `--path` (default `/acp`)
- `--allowed-roots /home/me/code:/srv/repos` or `LAKASIR_ALLOWED_ROOTS`: only allow workspace `cwd`s under these folders

Each WebSocket client gets its own agent process. Messages whose `method` starts with `_lakasir/`
are answered by the bridge and never reach the agent; everything else is piped through unchanged.

Every workspace path is relative to the `cwd` sent with the request and must stay inside it
(`..`, absolute paths and symlinks leading outside are rejected with `-32011`).

**Security:** there is no authentication. Anyone who can reach the port can read and edit files
under the allowed roots. Run it on a trusted network only, and set `--allowed-roots`.

Error codes: `-32010` conflict (file changed since it was read), `-32011` outside workspace,
`-32012` not found, `-32013` already exists, `-32000` other I/O errors, `-32020` nothing staged,
`-32021` git error (stderr in `data`), `-32022` not a git repository.

Git runs as `git -C <cwd>` with an argument list (never a shell), `GIT_TERMINAL_PROMPT=0`, and a
20 s timeout (60 s for commit, so hooks can run). Status, log and diffs are limited to the `cwd`
subtree, with paths relative to `cwd`. Commits use the machine's git identity.

Tests: `npm test`
