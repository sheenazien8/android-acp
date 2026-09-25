# Encrypt the bridge auth token at rest

## Context
- files: @app/src/main/java/com/lakasir/acp/data/local/Entities.kt
- files: @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt
- files: @app/src/main/java/com/lakasir/acp/ui/connection/ProfileForm.kt
- files: @app/src/main/java/com/lakasir/acp/di/AppContainer.kt
- `connection_profiles.authToken` is stored as plain text in Room
- `allowBackup="false"` and data extraction rules already keep the DB out of backups

## Goals
1. `TokenCipher` using an AES-256-GCM key in the Android Keystore (`KeyGenParameterSpec`, no user auth required)
2. Store `authToken` as `base64(iv + ciphertext)`; decrypt only when building the connection request
3. One-time migration on app start: encrypt any existing plain tokens (mark with a `v1:` prefix to tell formats apart)
4. If the key is lost (e.g. device restore), treat the token as missing and ask the user to re-enter it in the profile form

## Assumptions
- Using the platform Keystore directly instead of `EncryptedSharedPreferences` because that library is deprecated

## Notes
- Keep `ConnectionProfileEntity.toString()` masking the token
- `TokenCipher` behind an interface so JVM tests can use a fake

## Testing
- Unit: encode/decode round trip with the fake cipher; `v1:` prefix detection; plain → encrypted migration
- Device: existing profile still connects after upgrade; DB file (`adb shell run-as ... sqlite3`) shows no plain token
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

## Tools / Skills
- Bash: `./gradlew`, `adb`

## Implementation
<!-- Write you've done in here -->
