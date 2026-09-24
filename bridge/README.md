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
- `--token <secret>` or `LAKASIR_TOKEN` (16+ characters): clients must send `Authorization: Bearer <secret>`;
  anything else is rejected with HTTP 401 before an agent process is started
- `--tls-cert cert.pem --tls-key key.pem` (or `LAKASIR_TLS_CERT` / `LAKASIR_TLS_KEY`): serve `wss://` directly

Each WebSocket client gets its own agent process. Messages whose `method` starts with `_lakasir/`
are answered by the bridge and never reach the agent; everything else is piped through unchanged.

Every workspace path is relative to the `cwd` sent with the request and must stay inside it
(`..`, absolute paths and symlinks leading outside are rejected with `-32011`).

## Security

Whoever can connect can run the agent, and read, edit and commit files under the allowed roots.

- **LAN only:** run without a token on a trusted network, or bind to `--host 127.0.0.1` and reach it
  through an SSH tunnel. Still set `--allowed-roots`.
- **Reachable from the internet:** always set all three:
  1. `--token` with a long random secret (`openssl rand -hex 32`), entered in the app's profile
  2. TLS, either `--tls-cert/--tls-key`, or better, a reverse proxy or tunnel with a real
     certificate (Caddy, nginx, Cloudflare Tunnel) in front of `--host 127.0.0.1`. The proxy must
     pass WebSocket upgrades and the `Authorization` header through
  3. `--allowed-roots` limited to the projects you want to expose
- The bridge prints a warning at startup when it listens beyond localhost without a token or
  without allowed roots.
- In the app, use `wss://`. Release builds refuse `ws://`. "Allow self-signed certificate" skips
  certificate checks for that profile only; use it for testing, not over untrusted networks.

Example with Caddy in front:

```sh
node src/server.js --host 127.0.0.1 --port 8767 --token "$LAKASIR_TOKEN" \
  --allowed-roots /home/me/code -- claude-agent-acp
# Caddyfile:  bridge.example.com { reverse_proxy 127.0.0.1:8767 }
```

Error codes: `-32010` conflict (file changed since it was read), `-32011` outside workspace,
`-32012` not found, `-32013` already exists, `-32000` other I/O errors, `-32020` nothing staged,
`-32021` git error (stderr in `data`), `-32022` not a git repository.

Git runs as `git -C <cwd>` with an argument list (never a shell), `GIT_TERMINAL_PROMPT=0`, and a
20 s timeout (60 s for commit, so hooks can run). Status, log and diffs are limited to the `cwd`
subtree, with paths relative to `cwd`. Commits use the machine's git identity.

Tests: `npm test`
