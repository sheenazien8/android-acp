# UI tests and accessibility pass

## Context
- files: @app/src/main/java/com/lakasir/acp/ui/
- files: @app/src/test/java/com/lakasir/acp/acp/AcpClientTest.kt (fake transport pattern)
- 23 JVM test files, no instrumented or Compose UI tests
- Most controls have `contentDescription`; chat blocks, diff views and the permission dialog have not been checked with TalkBack

## Goals
1. Compose UI tests (`androidTest`, `createAndroidComposeRule`) with a fake `AcpClientFactory` in `AppContainer`:
   - Add profile → connect → sessions list shows "Connected"
   - New session → send prompt → streamed text appears → tool call block → permission dialog → allow
   - Connection switcher drawer switches profiles
2. Accessibility:
   - TalkBack pass on Chat, permission dialog, Files, Git; fix missing labels and merge semantics on rows
   - Touch targets ≥48dp (status chips, attachment chips, file tree rows)
   - Font scale 200%: no clipped top bars or input bar
3. Play Console pre-launch report: review its accessibility warnings after the first internal upload

## Notes
- `AppContainer` needs a test hook to swap the client factory (constructor param or `AcpApp` override in a test application class)
- Shares the `androidTest` setup with plan 31

## Testing
- `./gradlew :app:connectedDebugAndroidTest`
- Manual TalkBack and font-scale run on a device

## Tools / Skills
- Skill `mobile-android-design`
- Bash: `./gradlew`, `adb shell settings put system font_scale 2.0`

## Implementation
<!-- Write you've done in here -->
