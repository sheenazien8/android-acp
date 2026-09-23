# Background Connection

## Context
- files:
  - @app/src/main/AndroidManifest.xml
  - @app/src/main/java/com/lakasir/acp/AcpApp.kt
  - @app/src/main/java/com/lakasir/acp/MainActivity.kt
  - @app/src/main/java/com/lakasir/acp/di/AppContainer.kt
  - @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt
  - @app/src/main/java/com/lakasir/acp/data/repository/ConnectionState.kt
  - @app/build.gradle.kts
  - @gradle/libs.versions.toml
- ACP connections (one `AcpClient` per profile, plan 10) live only in `AppContainer.appScope`, so they are tied to the process lifetime.
- No `Service`, no foreground-service or notification permissions. Once the app goes to the background Android may restrict networking or kill the process, silently dropping WebSockets.
- `runConnection` retries with exponential backoff (1s → 30s) but has no way to retry immediately when the network comes back.
- Permission requests and finished agent turns are only visible inside the app UI.

## Goals
- Keep ACP connections alive while the app is in the background using a foreground service that runs exactly while at least one profile is meant to be connected.
- Persistent notification shows the connection summary (connected / reconnecting counts) and a "Disconnect all" action.
- When the app is not visible, post a notification for:
  - a new permission request (tap opens the app, where the permission dialog is shown)
  - an agent turn finishing (end of `session/prompt`)
- Retry immediately (skip the remaining backoff) when the default network becomes available.
- Request `POST_NOTIFICATIONS` on Android 13+.

## Assumptions
- Foreground service type `remoteMessaging`: `dataSync` is capped at 6h/day on Android 15 (targetSdk 35), and the app is a remote chat client, which matches `remoteMessaging`.
- Service is started only from a user action (connect from the UI), which is allowed while the app is in the foreground. It is `START_NOT_STICKY`: if the process dies, the in-memory connections are gone anyway.
- No wake lock / Wi-Fi lock. FGS processes keep network access in Doze, and OkHttp's 20s ping keeps the socket alive. Revisit if drops are observed on real devices.
- Tapping a notification opens `MainActivity` (no deep link into a specific chat); the permission dialog is already global.

## Notes
- The service must not follow `connectionStates` directly: `runConnection` briefly sets `Disconnected` between retries, which would stop the service and then fail to restart it from the background (`ForegroundServiceStartNotAllowedException`). Use a separate `activeProfileIds` set that only changes on `connect()` / `disconnect()`.
- Starting/stopping the service is driven by an observer in `AcpApp` on `activeProfileIds`, so UI code does not need to know about the service.
- App visibility via `ProcessLifecycleOwner` (`androidx.lifecycle:lifecycle-process`, same lifecycle version).
- Turn completion is exposed as a `SharedFlow<CompletedTurn>` from the repository instead of diffing `busySessions` (which also shrinks on disconnect).
- Notification summary text is a pure function so it can be unit tested (no Robolectric in the project).
- Known issue from plan 10 not handled here: event/protocolError collectors launched in `connect()` are never cancelled on `disconnect()`.

## Testing
- `./gradlew :app:compileDebugKotlin`
- `./gradlew test` — unit test for the connection summary text.
- Manual on device: connect a profile, background the app, verify the persistent notification, trigger a permission request / finish a prompt and verify notifications, toggle airplane mode and verify fast reconnect, "Disconnect all" stops the service.

## Tools / Skills
- `./gradlew :app:compileDebugKotlin`, `./gradlew test`

## Implementation
- `AcpRepository`: added `activeProfileIds` (changes only on `connect` / `disconnect`), `completedTurns` shared flow emitted at the end of `sendPrompt` (success or error), and `retryNow()` which cuts the reconnect backoff short (`withTimeoutOrNull(backoff) { retrySignal.first() }`).
- `CompletedTurn` model in `ConnectionState.kt`.
- `service/AcpConnectionService`: `remoteMessaging` foreground service, `START_NOT_STICKY`. Keeps the persistent notification in sync with the connection summary, has a "Disconnect all" action, posts permission / turn-finished notifications when the app is not visible (`ProcessLifecycleOwner`), cancels a permission notification once answered, and calls `retryNow()` from a default-network callback.
- `service/ConnectionNotifications`: channels (`connection` low, `agent` high), notification builders, and a pure `summary()` function.
- `AcpApp` starts/stops the service when `activeProfileIds` goes non-empty/empty.
- `MainActivity` requests `POST_NOTIFICATIONS` on Android 13+.
- Manifest: `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_REMOTE_MESSAGING`, service declaration. Added `lifecycle-process` dependency and `ic_notification` vector.
- `ConnectionSummaryTest` (4 tests).
- Verified: `./gradlew :app:compileDebugKotlin test lintDebug` pass (61 unit tests). Manual device testing not done yet.
