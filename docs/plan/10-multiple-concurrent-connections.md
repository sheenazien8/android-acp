# Multiple Concurrent Connections

## Context

Current architecture assumes a single active ACP connection at a time:
- `AcpClient` wraps one `AcpTransport` / WebSocket
- `AcpRepository` exposes one `connectionState` and one `connectJob`
- `connect(profile)` cancels any previous connection before starting a new one
- UI (`ConnectionScreen`, `SessionsScreen`, `ChatScreen`) renders a single global status and a single connect/disconnect action per profile

Relevant files:
- @app/src/main/java/com/lakasir/acp/di/AppContainer.kt
- @app/src/main/java/com/lakasir/acp/acp/AcpClient.kt
- @app/src/main/java/com/lakasir/acp/acp/AcpTransport.kt
- @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt
- @app/src/main/java/com/lakasir/acp/data/repository/ConnectionState.kt
- @app/src/main/java/com/lakasir/acp/ui/connection/ConnectionViewModel.kt
- @app/src/main/java/com/lakasir/acp/ui/connection/ConnectionScreen.kt
- @app/src/main/java/com/lakasir/acp/ui/sessions/SessionsViewModel.kt
- @app/src/main/java/com/lakasir/acp/ui/chat/ChatViewModel.kt
- @app/src/main/java/com/lakasir/acp/ui/components/ConnectionStatusIndicator.kt
- @app/src/test/java/com/lakasir/acp/acp/FakeTransport.kt
- @app/src/test/java/com/lakasir/acp/data/AcpRepositoryRulesTest.kt

## Goals

- Allow multiple connection profiles to stay connected to their ACP servers simultaneously.
- Each session routes events and prompts through the connection that owns its profile.
- Per-profile connection state replaces the single global state.
- UI shows independent connect / disconnect / status per profile.
- Existing single-connection behavior in tests continues to work.

## Assumptions

- "Multiple connections running together" means one active WebSocket per connection profile. A profile can be connected or disconnected independently; multiple profiles can be online at the same time.
- We are not adding multiple parallel connections to the *same* profile/endpoint (load balancing or redundant links). One transport per profile is sufficient.
- Existing session and message data model remains unchanged: a session is still bound to one profile.

## Notes

- `AcpClient` currently owns a single `transport` and single `events`/`protocolErrors` shared flow. To support multiple transports, we can either:
  1. Make `AcpClient` multi-transport internally, or
  2. Create one `AcpClient` instance per profile.

  Approach 2 is simpler and keeps transport/client lifecycle aligned with the profile. The repository will manage a map of profileId -> client, each with its own transport and events.

- `ConnectionState` already carries a `profileId`, so converting the repository to expose `Map<Long, ConnectionState>` or `StateFlow<Map<Long, ConnectionState>>` is a small change. UI can derive the state for a specific profile.

- `AcpRepository.busySessions`, `attachedSessions`, and `pendingPermissions` are keyed by `localSessionId` and are already global across profiles, so they can stay as-is; only the connection state map needs to become per-profile.

- `AcpRepository.handleEvent` currently needs `profileId` to resolve remote session IDs. With multiple clients we must pass the profileId along with the event (or tag events by client) so the repository knows which profile a session belongs to.

- `AcpClient.request` uses a shared `pending` map keyed by numeric id. With multiple clients, id spaces can overlap safely because each client has its own `pending` map.

- Transport and client cleanup must cancel the per-profile reconnect job and close that transport when disconnecting or deleting a profile.

- The `ConnectionStatusIndicator` already accepts an optional `profileId` to scope the state, so minimal changes are needed there.

## Testing

- `./gradlew :app:compileDebugKotlin` — passed
- `./gradlew test` — passed (57 tests)
- Cleaned up and kept concurrent client tests (`AcpClientConcurrentTest`, `AcpClientMultipleTransportTest`) that verify independent clients can share a scope without request-id collisions and that events stay isolated per transport.

## Tools / Skills

- `./gradlew test` for unit tests
- `./gradlew :app:compileDebugKotlin` for compile checks

## Implementation

- Added `AcpClientFactory` / `DefaultAcpClientFactory` so the repository can create a fresh client + transport per profile.
- Replaced the single `AcpClient` in `AcpRepository` with a map of `profileId -> AcpClient` and per-profile reconnect jobs.
- Exposed `connectionStates: StateFlow<Map<Long, ConnectionState>>` instead of a single state; every session routes to the client that owns its profile.
- Updated `ConnectionViewModel`, `SessionsViewModel`, and `ChatViewModel` to derive per-profile state from the map.
- Updated `ConnectionScreen` and `ConnectionStatusIndicator` to show an aggregate status in the app bar and per-profile status/connect/disconnect in each row.
- Cleaned up concurrent client tests (`AcpClientConcurrentTest`, `AcpClientMultipleTransportTest`) and added `AcpClientFactoryTest`.
- Verified with `./gradlew :app:compileDebugKotlin` and `./gradlew test` — both pass.
