# Foreground service type and lifetime

## Context
- files: @app/src/main/AndroidManifest.xml
- files: @app/src/main/java/com/lakasir/acp/service/AcpConnectionService.kt
- files: @app/src/main/java/com/lakasir/acp/service/ConnectionNotifications.kt
- files: @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt
- The service declares `foregroundServiceType="remoteMessaging"` and runs while any profile is active, even when the agent is idle
- Play Console requires a declaration (description + video) for each FGS type; `remoteMessaging` is meant for moving text messages between a user's devices, so it is likely to be rejected
- There is no `onTimeout` handling (Android 15 time limits for `dataSync`/`mediaProcessing`)

## Goals
1. Switch to `specialUse`:
   - Manifest: `FOREGROUND_SERVICE_SPECIAL_USE` permission, `foregroundServiceType="specialUse"`, and a `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="..."/>` with a one-line reason
   - Code: `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on API 34+
2. Only keep the service in the foreground while work is running:
   - Start it when a prompt starts or a permission request is pending
   - Stop it (`stopForeground` + `stopSelf`) after the last busy session finishes and no permission is pending, with a short grace period (e.g. 30s) so turn-complete notifications still post
   - Idle connections stay open while the app is visible; when the app goes to background with nothing running, the connection may drop and reconnects on return (resume with `session/load`)
3. Override `onTimeout(startId, fgsType)` to stop gracefully (defensive; `specialUse` has no limit today)
4. Write the Play Console declaration text and record a short screen video script in `docs/play/fgs-declaration.md`

## Assumptions
- `specialUse` is the best fit because no standard type covers "keep a user-started agent task alive"; `dataSync` would add a 6h/24h cap on Android 15
- Background-only idle connections are not needed for v1; the user cares about running tasks, permission prompts and completion notifications

## Notes
- `repository.busySessions` and `repository.pendingPermissions` already exist; drive start/stop from `combine` of the two
- `START_NOT_STICKY` stays
- Starting an FGS from the background is restricted on Android 12+; prompts are started from the UI, so start the service at prompt time while the app is visible
- Notification permission denial must not crash the service; the FGS notification is still required

## Testing
- Unit: start/stop decision function (busy set + pending permissions + grace timer) with `kotlinx-coroutines-test`
- Device: send a prompt, background the app, confirm the notification stays until the turn ends, then disappears after the grace period; approve a permission from the notification
- `adb shell dumpsys activity services com.lakasir.acp` shows `types=0x40000000` (specialUse)
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

## Tools / Skills
- Bash: `./gradlew`, `adb`

## Implementation
<!-- Write you've done in here -->
