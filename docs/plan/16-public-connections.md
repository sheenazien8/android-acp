# 16 — Public (Internet-Reachable) Connections

## Context

- files: @app/src/main/java/com/lakasir/acp/data/local/Entities.kt
- files: @app/src/main/java/com/lakasir/acp/data/local/Migrations.kt
- files: @app/src/main/java/com/lakasir/acp/data/local/AppDatabase.kt
- files: @app/src/main/java/com/lakasir/acp/acp/AcpClient.kt
- files: @app/src/main/java/com/lakasir/acp/acp/AcpTransport.kt
- files: @app/src/main/java/com/lakasir/acp/acp/AcpClientFactory.kt
- files: @app/src/main/java/com/lakasir/acp/acp/AcpMethods.kt
- files: @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt
- files: @app/src/main/java/com/lakasir/acp/ui/connection/ProfileForm.kt
- files: @app/src/main/java/com/lakasir/acp/ui/connection/ProfileFormSheet.kt
- files: @app/src/main/java/com/lakasir/acp/ui/connection/ConnectionScreen.kt
- files: @app/src/main/AndroidManifest.xml
- files: @bridge/src/server.js
- files: @bridge/README.md

Current state (plan 15 is the last implemented plan, DB version 2):

- A `ConnectionProfileEntity` stores only `host`, `port`, `cwd`. There is no scheme, path or credential.
- `AcpClient.connect(host, port)` hardcodes the URL: `transport.connect("ws://$host:$port${AcpMethods.ENDPOINT_PATH}")` — cleartext `ws://` on the fixed `/acp` path, no request headers.
- `AcpTransport.connect(url)` builds a bare `Request` with no headers; `OkHttpAcpTransport` uses one shared `defaultClient()`.
- `AndroidManifest.xml` sets `android:usesCleartextTraffic="true"` for all builds.
- The bridge has no authentication (`--host`, `--port`, `--path`, `--allowed-roots` only) and its own README says "run it on a trusted network only".
- Plan doc `00-overview.md` already lists "TLS/auth" as out-of-scope TODO, and `AcpMethods.ENDPOINT_PATH` ("/acp") is the single place the path lives.

So today the app can only talk to a bridge on the same trusted LAN. Connecting to a bridge that is reachable from the public internet needs: a real URL shape (`wss://host:port/path`), a credential the bridge can check, and no blanket cleartext permission in release builds.

## Goals

Allow a profile to target a publicly reachable bridge and connect to it safely.

1. **Endpoint in the profile** — new profile fields: `scheme` (`ws` | `wss`), `path` (default `/acp`), `authToken` (optional), `allowInsecureTls` (default `false`). Persisted with `MIGRATION_2_3`; DB version 2 → 3; existing profiles keep today's behavior (`ws`, `/acp`, no token).
2. **Single URL builder** — one testable mapper (`AcpEndpoint`) that turns a profile into the handshake URL and headers, replacing the hardcoded string in `AcpClient`.
3. **Bearer auth on the handshake** — token sent as `Authorization: Bearer <token>`; token never rendered in the UI or logged.
4. **TLS** — `wss` works out of the box against a normal certificate; a per-profile opt-in "allow self-signed" builds a trusting client, used only for that profile.
5. **Cleartext restricted** — `usesCleartextTraffic` removed from the main manifest; cleartext allowed only in debug builds and only for `ws` profiles.
6. **Readable auth errors** — a `401`/`403` handshake rejection surfaces as "Invalid or missing token" instead of a generic socket failure.
7. **Bridge-side token** — `--token` / `LAKASIR_TOKEN` option; reject unauthenticated upgrades with `401` before any agent process is spawned.
8. **Docs** — bridge README security section rewritten (token + TLS/reverse proxy + `--allowed-roots`), and `00-overview.md` gains row 16.

## Assumptions

Interpretation of "connect to public connections" (user did not specify; the question tool was unavailable):

- **Assumed:** "public connections" = connection profiles for bridges that are reachable outside the LAN — i.e. the app must support a full endpoint URL (`wss://`, custom path) and a token, and the bridge must be able to require that token. This matches the codebase's own TODO ("TLS/auth" in `00-overview.md`) and the bridge README's "trusted network only" warning.
- **Assumed:** TLS terminates either at the bridge (`--tls-cert/--tls-key`, optional, thin) or at a reverse proxy / tunnel (Caddy, nginx, Cloudflare Tunnel). The plan implements token auth in the bridge and documents the proxy path; embedding a certificate manager in the bridge is out of scope.
- **Assumed:** one token per bridge instance, shared by all its clients. Per-user accounts / device pairing / OAuth are out of scope.
- **Assumed:** the token is stored in Room like the rest of the profile (MVP simplicity). The backup-exposure caveat is called out in Notes; `EncryptedSharedPreferences` is a follow-up, not this plan.
- **Assumed:** "public" does not mean a curated directory of community ACP servers, publishing a profile for other users, or a tunnel-helper wizard. Those are separate features; see Notes for how this plan stays compatible with them.

## Goals → Files

| Change | File |
|---|---|
| New fields + entity mapping | @app/src/main/java/com/lakasir/acp/data/local/Entities.kt |
| `MIGRATION_2_3`, `ALL` | @app/src/main/java/com/lakasir/acp/data/local/Migrations.kt |
| `version = 3` | @app/src/main/java/com/lakasir/acp/data/local/AppDatabase.kt |
| New `AcpEndpoint` value object | @app/src/main/java/com/lakasir/acp/acp/AcpEndpoint.kt (new) |
| `connect(endpoint)` | @app/src/main/java/com/lakasir/acp/acp/AcpClient.kt |
| `connect(url, headers)` + failure-code mapping | @app/src/main/java/com/lakasir/acp/acp/AcpTransport.kt |
| Per-profile OkHttpClient (insecure-TLS opt-in) | @app/src/main/java/com/lakasir/acp/acp/AcpClientFactory.kt |
| `client.connect(AcpEndpoint(profile))` | @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt |
| Form state + validation | @app/src/main/java/com/lakasir/acp/ui/connection/ProfileForm.kt |
| Form fields | @app/src/main/java/com/lakasir/acp/ui/connection/ProfileFormSheet.kt |
| Row URL display + lock icon | @app/src/main/java/com/lakasir/acp/ui/connection/ConnectionScreen.kt |
| Network security config | @app/src/main/res/xml/network_security_config.xml (new), @app/src/debug/AndroidManifest.xml (new), @app/src/main/AndroidManifest.xml |
| `--token` + upgrade rejection | @bridge/src/server.js, @bridge/README.md |

## Notes

- **URL composition rules** (keep in `AcpEndpoint`, unit-tested):
  - `path`: trim, `ifEmpty { AcpMethods.ENDPOINT_PATH }`, ensure leading `/`, drop trailing `/`.
  - `host`: trim; wrap bare IPv6 literals in `[...]` (the form already rejects `://` and spaces for host, so those stay rejected).
  - port: always written explicitly (`ws://h:80/acp`, `wss://h:443/acp`) — simpler and unambiguous than default-port elision.
  - Build with `HttpUrl.Builder`/`Request.Builder` so OkHttp validates the URL; a malformed host must fail at validation time, not at connect time.
- **Headers**: send `Authorization: Bearer <token>` only when the token is non-empty after trim. Never put the token in the URL (it would leak into logs and `ConnectionState.Error` strings).
- **Insecure TLS**: build the permissive client lazily and cache it once (`HttpClient`-style single instance), never as the default. Never log or surface the certificate error text verbatim beyond a short "TLS handshake failed" message. Do not set `usesCleartextTraffic` for this.
- **Backoff/reconnect is unchanged** — `AcpRepository.runConnection` already retries; it just needs the endpoint (including the token) from the profile each attempt, so a token edited in the form takes effect on the next connect.
- **Failure mapping**: `WebSocketListener.onFailure` currently ignores `response`. Map `response?.code == 401 || 403` → `"Invalid or missing token"`, `503` → `"Bridge is not ready"`, else keep the existing reason. This is what `ConnectionState.Error.message` shows inline under the row.
- **Cleartext policy**: put `<base-config cleartextTrafficPermitted="false">` plus a per-domain debug override in `network_security_config.xml`, apply it from the main manifest, and delete `usesCleartextTraffic` from `main`. Release builds then physically cannot dial `ws://`; the form should warn (not block) when `scheme == ws` and a release build is detected via `BuildConfig.DEBUG`.
- **Token at rest**: Room is unencrypted, and `android:allowBackup="true"` currently includes it in cloud backups. Either switch to `android:allowBackup="false"` + `dataExtractionRules` excluding the DB, or store only the token in `EncryptedSharedPreferences`. Decide in implementation; document the choice in the Implementation section.
- **Bridge security is not optional here**: once a bridge is public, `_lakasir/*` lets anyone with the token read and write files under the allowed roots. Keep `--allowed-roots` mandatory in the docs, and consider a startup warning when `--host 0.0.0.0` is combined with no `--token`.
- **Compat with future work**: an `AcpEndpoint` value object is exactly the seam needed for a later curated "public servers" directory (seed profiles) or a tunnel helper (auto-filled tunnel URL) — no rework required.
- **Migration test**: add the profile columns with `NOT NULL DEFAULT` values so the existing `ALTER TABLE`-based migration path keeps working on installs already at version 2, and add an assertion alongside the existing migration test for `MIGRATION_1_2`.

## Testing

- `./gradlew assembleDebug testDebugUnitTest` (project rule: every plan ends green).
- New JVM tests:
  - `acp/AcpEndpointTest.kt` — default path, missing/leading/trailing slashes, `wss` + custom path, IPv6 host bracket wrapping, no token → no header, token → `Authorization: Bearer …`, token never present in the built URL.
  - `acp/AcpTransportTest.kt` (extend `FakeTransport` usage) — `connect(url, headers)` passes headers through; `401`/`403` response maps to the token message.
  - `ui/connection/ProfileFormTest.kt` — new cases: path must start with `/`, empty path falls back to `/acp`, token is trimmed and optional, `allowInsecureTls` round-trips through `from()`/`toEntity()`.
  - `data/` migration assertion next to the existing auto-approve one for `MIGRATION_2_3`.
- Bridge: `cd bridge && npm test` — add cases for a missing token, a wrong token (rejected with `401`, no agent spawned) and the correct token (existing echo-agent test passes with the token set). Existing tests must stay green when no `--token` is configured.
- Manual, end-to-end:
  1. Run the bridge publicly (`--token …`, `--allowed-roots …`) behind a reverse proxy/tunnel with a real certificate.
  2. Add a profile with `wss`, the public host, path and token → connects, Sessions screen appears.
  3. Wrong token → inline "Invalid or missing token", no crash, retry backoff visible.
  4. Self-signed `wss` without `allowInsecureTls` → TLS error; enabling it → connects.
  5. Toggle airplane mode → existing reconnect behavior still observed.
  6. Existing LAN profile (migrated) still connects over `ws://` in a debug build.

## Tools / Skills

- Skill `mobile-android-design` → `references/compose-components.md` (text fields, `PasswordVisualTransformation`, segmented button), `references/material3-theming.md` for the new form controls.
- Bash: `./gradlew assembleDebug testDebugUnitTest`, `cd bridge && npm test`.
- No database MCP needed; the schema change is a self-contained Room migration.

## Implementation
<!-- Write you've done in here -->
