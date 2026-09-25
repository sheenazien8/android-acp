# App icon and Play store assets

## Context
- files: @app/src/main/AndroidManifest.xml
- files: @app/src/main/res/drawable/ic_notification.xml
- files: @app/src/main/res/values/strings.xml
- No `android:icon` / `android:roundIcon` and no `mipmap-*` folders; the launcher shows the default icon
- The Play listing needs a 512×512 icon, a 1024×500 feature graphic, and 2–8 phone screenshots

## Goals
1. Adaptive launcher icon: `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` with a vector foreground, solid brand background, and monochrome layer (themed icons)
2. Wire `android:icon` and `android:roundIcon` in the manifest
3. Export a 512×512 PNG for Play from the same vector
4. Feature graphic 1024×500 and screenshots (Connections, Sessions with switcher, Chat with tool call + diff, Files, Git, permission dialog) in `docs/play/assets/`
5. Store listing text in `docs/play/listing.md`: app name (≤30 chars), short description (≤80), full description (≤4000), category (Developer tools / Productivity), contact email

## Assumptions
- The icon is a simple vector mark in the fixed teal accent; no designer asset exists yet

## Notes
- Keep the foreground inside the 66dp safe zone of the 108dp canvas
- Screenshots should use the demo mode from plan 35 so no private hostnames or code appear

## Testing
- Device: launcher icon renders on Pixel launcher with themed icons on and off
- `./gradlew :app:lintDebug` (no `MissingApplicationIcon`)

## Tools / Skills
- Skill `mobile-android-design`
- Bash: `adb shell screencap` for screenshots

## Implementation
<!-- Write you've done in here -->
