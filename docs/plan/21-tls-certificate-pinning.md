# TLS certificate pinning (replace trust-all TLS)

## Context
- files: @app/src/main/java/com/lakasir/acp/acp/AcpClientFactory.kt
- files: @app/src/main/java/com/lakasir/acp/acp/AcpEndpoint.kt
- files: @app/src/main/java/com/lakasir/acp/data/local/Entities.kt
- files: @app/src/main/java/com/lakasir/acp/data/local/Migrations.kt
- files: @app/src/main/java/com/lakasir/acp/ui/connection/ProfileForm.kt
- files: @app/src/main/java/com/lakasir/acp/ui/connection/ProfileFormSheet.kt
- files: @app/src/main/res/xml/network_security_config.xml
- files: @bridge/src/server.js
- `allowInsecureTls` builds an OkHttp client with a trust-all `X509TrustManager` and `hostnameVerifier { _, _ -> true }` (`AcpClientFactory.kt:23-34`)
- Google Play rejects apps with unsafe TrustManager / HostnameVerifier implementations, so this is a publish blocker
- Release builds block cleartext (`cleartextTrafficPermitted="false"`), so LAN users need `wss` anyway
- The bridge already supports `--tls-cert/--tls-key` but the user must create the cert by hand

## Goals
1. Replace "allow self-signed" with trust-on-first-use pinning:
   - New nullable column `connection_profiles.certSha256` (Room v4, `MIGRATION_3_4`); drop `allowInsecureTls` usage (keep the column, stop reading it)
   - If the system trust store validates the cert, connect normally (public CA, no pin needed)
   - If it fails and no pin is stored, surface `ConnectionState.Error` with a new `UntrustedCertificate(sha256)` reason; the UI shows a dialog with the SHA-256 fingerprint and "Trust this certificate" / "Cancel"
   - On trust, store the pin and reconnect; afterwards accept only a leaf cert whose SHA-256 matches the pin
   - A pin mismatch never auto-prompts again silently: show "Certificate changed" with the new and old fingerprints
2. Implement the check with a delegating `X509TrustManager` that first calls the platform trust manager, then falls back to the pin; hostname verification uses the default verifier unless the cert is pinned (IP hosts on LAN)
3. Bridge: `--tls auto` generates a self-signed cert + key once into `~/.config/lakasir-acp-bridge/` (via `openssl req -x509 ...`) and prints its SHA-256 fingerprint at startup so the user can compare it with the dialog
4. Profile form: remove the "Allow self-signed certificate" switch; show the pinned fingerprint (short form) with a "Forget certificate" action
5. Release stays `wss`-only; plain `ws://` works only in debug builds (decision: option (a) from the Play readiness analysis)

## Assumptions
- Chose option (a): require `wss` in release and make self-signed TLS painless with pinning, because the user accepted the recommendation
- Node has no built-in X.509 generator; shelling out to `openssl` is simpler than adding the `selfsigned` package. If `openssl` is missing, the bridge prints how to pass `--tls-cert/--tls-key`

## Notes
- The fingerprint must be taken from the leaf certificate (`chain[0]`), hex uppercase with colons for display
- OkHttp's `CertificatePinner` pins public keys and still requires the chain to validate, so it does not work for self-signed; use the custom trust manager
- Keep `@SuppressLint` out; the new trust manager must actually validate, so lint's `TrustAllX509TrustManager` check stays green
- Existing profiles with `allowInsecureTls = true` will hit the first-use dialog once after upgrade; that is expected
- Add the migration to the schema test from plan 31

## Testing
- Unit: trust manager accepts a CA-valid chain, rejects an unknown self-signed chain without a pin, accepts it with the matching pin, rejects it with a different pin
- Unit: `MIGRATION_3_4` adds `certSha256` (schema test)
- Bridge: `node --test` for `--tls auto` argument parsing and fingerprint formatting
- Device: bridge with `--tls auto`, connect, compare fingerprint, trust, reconnect without prompt; regenerate cert, see "Certificate changed"
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

## Tools / Skills
- Skill `mobile-android-design` for the trust dialog
- Bash: `./gradlew`, `openssl x509 -fingerprint -sha256`

## Implementation
<!-- Write you've done in here -->
