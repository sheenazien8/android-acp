# Target SDK 36 and dependency refresh

## Context
- files: @app/build.gradle.kts
- files: @gradle/libs.versions.toml
- files: @app/src/main/java/com/lakasir/acp/MainActivity.kt
- `compileSdk = 35`, `targetSdk = 35`, AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01
- Play requires new apps and updates to target a recent API level (API 35 until Aug 2026; very likely API 36 after 31 Aug 2026)

## Goals
1. Confirm the current required target level in the Play Console / Play policy page and record it in Notes
2. Bump `compileSdk` and `targetSdk` to 36 (SDK platform 36 is installed)
3. Bump AGP, Kotlin, KSP, Compose BOM, Navigation, Lifecycle, Room, Activity to versions that support API 36
4. Fix API 36 behavior changes:
   - Edge-to-edge is enforced; check every screen's top bar, input bar (`imePadding`) and drawers
   - Predictive back: make sure `BackHandler`s in the chat and sessions drawers still work (`enableOnBackInvokedCallback`)
   - Orientation/resizability restrictions are ignored on large screens; check the expanded layout
5. Keep `minSdk = 26`

## Notes
- Do this before plan 25 (release build) so R8 rules are checked against the final dependency versions
- Room 2.7 needs KSP2; check `room.schemaLocation` still works
- Material3 updates can change `ModalNavigationDrawer`/`ListItem` defaults; compare the Sessions and Chat screens visually

## Testing
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
- Device on Android 15/16: open every screen, rotate, use the keyboard, open both drawers, predictive back gesture

## Tools / Skills
- WebFetch for the Play target API policy page
- Skill `mobile-android-design`

## Implementation
<!-- Write you've done in here -->
