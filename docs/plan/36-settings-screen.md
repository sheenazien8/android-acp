# Settings screen

## Context
- files: @app/src/main/java/com/lakasir/acp/ui/navigation/AppNavHost.kt
- files: @app/src/main/java/com/lakasir/acp/ui/navigation/Routes.kt
- files: @app/src/main/java/com/lakasir/acp/ui/connection/ConnectionScreen.kt
- files: @app/src/main/java/com/lakasir/acp/ui/sessions/ConnectionSwitcher.kt
- No settings screen; the Play listing needs a reachable privacy policy and users need a way to delete data

## Goals
1. `Settings` route, opened from a gear icon on the Connections top bar and from the connection switcher footer
2. Sections:
   - Data: storage used, "Clear all history" (plan 29), per-profile clear lives in the profile sheet
   - Notifications: shortcut to system notification settings
   - About: version name/code, privacy policy link (plan 26), bridge setup link, open-source licenses (static list or `play-services-oss-licenses` alternative: a simple generated list)
3. Confirmation dialog for destructive actions

## Notes
- Keep it one simple `LazyColumn` of `ListItem`s, same flat style as the rest of the app
- Licenses: a hand-written list of the few dependencies is enough for v1

## Testing
- Device: every row works; clear history empties sessions; links open the browser
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

## Tools / Skills
- Skill `mobile-android-design`

## Implementation
<!-- Write you've done in here -->
