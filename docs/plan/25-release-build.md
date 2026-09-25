# Release build: R8, signing, AAB, versioning

## Context
- files: @app/build.gradle.kts
- files: @app/proguard-rules.pro
- files: @gradle.properties
- `isMinifyEnabled = false`, no `signingConfig`, `versionCode = 1` hard-coded
- Libraries: kotlinx.serialization, Room, OkHttp, Navigation type-safe routes (`@Serializable` route classes)

## Goals
1. `release { isMinifyEnabled = true; isShrinkResources = true }`
2. Keep rules for: `@Serializable` classes used as navigation routes and protocol payloads (kotlinx plugin ships most rules; verify `Routes.kt` and `acp/*` models survive), Room entities are handled by Room
3. Signing: upload keystore outside the repo; read path/passwords from `~/.gradle/gradle.properties` or env vars (`LAKASIR_UPLOAD_STORE_FILE`, ...); no secrets in git
4. `versionCode` from env/CI (`LAKASIR_VERSION_CODE`, default 1) and `versionName` from the latest git tag
5. Build the bundle with `./gradlew :app:bundleRelease`; enroll in Play App Signing when creating the app
6. Document the release steps in `docs/play/release.md`

## Notes
- Test the minified build on a device, not only the debug build: R8 bugs show up as `SerializationException` or navigation crashes at runtime
- Keep `mapping.txt` for every uploaded build (Play Console deobfuscation)
- `debug` keeps cleartext config; make sure `src/debug/res/xml/network_security_config.xml` is not merged into release

## Testing
- `./gradlew :app:bundleRelease :app:assembleRelease`
- Install `assembleRelease` on a device, run through connect → session → prompt → tool call → permission → files → git
- `bundletool build-apks --mode=universal` to check the AAB installs

## Tools / Skills
- Bash: `./gradlew`, `keytool`, `bundletool`

## Implementation
<!-- Write you've done in here -->
