# Connection switcher sidebar

## Context
- Switching between bridges means going back to the Connections screen and picking another profile
- Sessions screen: @app/src/main/java/com/lakasir/acp/ui/sessions/SessionsScreen.kt
- Sessions view model: @app/src/main/java/com/lakasir/acp/ui/sessions/SessionsViewModel.kt
- Navigation: @app/src/main/java/com/lakasir/acp/ui/navigation/AppNavHost.kt
- Chat screen already uses an end-side `ModalNavigationDrawer` for the workspace sidebar (plan 14)

## Goals
1. Start-side modal drawer on the Sessions screen listing every connection profile (name, address, status dot); the current one is highlighted with the teal left border
2. The top bar navigation icon becomes a menu button that opens the drawer; drawer gestures only while open (same as the chat sidebar)
3. Tapping another profile replaces the current Sessions entry (`popUpTo<Connections>`), so Back still returns to Connections
4. A "Manage connections" row at the bottom goes back to the Connections screen
5. `SessionsViewModel` exposes `profiles` and `connectionStates` for the drawer

## Assumptions
- Switching only navigates; it does not auto-connect (same as tapping a card on the Connections screen)
- `launchSingleTop` is not used for the switch because it would keep the old entry's view model and `profileId`
- The drawer is only on the Sessions screen (phone and expanded layouts); the chat screen keeps its end-side workspace sidebar

## Testing
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
- Device: open a connection, open the drawer, switch to another profile, Back returns to Connections

## Implementation
- `ConnectionSwitcher.kt`: drawer content with profile rows (name, `host:port`, status indicator), current profile highlighted, "Manage connections" footer
- `SessionsScreen` wraps both phone and expanded layouts in a start-side `ModalNavigationDrawer` (flat 320dp sheet, gestures only while open, Back closes it); the top bar back arrow is replaced by a menu button
- `SessionsViewModel` exposes `profiles` and `connectionStates`
- `AppNavHost` switches with `navigate(Sessions(id)) { popUpTo<Connections>() }`
- `assembleDebug`, `testDebugUnitTest`, `lintDebug` pass; device smoke test not run (no device attached)
