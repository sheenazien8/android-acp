# 01 — Project Setup

## Context
- files: none yet (greenfield); see @docs/plan/00-overview.md
- JDK 17, Android SDK 34–36, Gradle 8.10.2 cached locally

## Goals
- Gradle Kotlin DSL: `settings.gradle.kts`, root `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, Gradle wrapper 8.10.2
- Single `app` module, namespace `com.lakasir.acp`, minSdk 26, compile/targetSdk 35, Kotlin 2.x with Compose compiler plugin, kotlinx.serialization plugin, KSP for Room
- Dependencies: Compose BOM + Material 3, `material3-window-size-class`, `material-icons-extended` (or core icons only if size matters), Navigation Compose 2.8+ (type-safe `@Serializable` routes), Lifecycle ViewModel/Runtime Compose, OkHttp, kotlinx.serialization-json, kotlinx.coroutines, Room (runtime, ktx, compiler), JUnit + coroutines-test
- `MainActivity` calls `enableEdgeToEdge()`; `windowSoftInputMode="adjustResize"` so the chat input can use `imePadding()`
- `AndroidManifest.xml`: `INTERNET` permission, `usesCleartextTraffic="true"`, `AcpApp` application class, `MainActivity`
- `AcpApp` holding `di/AppContainer` (empty skeleton, filled in later plans)
- `MainActivity` with an empty `NavHost` placeholder using type-safe routes (`@Serializable object Connections`, `data class Sessions(profileId)`, `data class Chat(sessionId)`) in `ui/navigation/Routes.kt`
- `.gitignore`, `local.properties` pointing at `~/android-sdk`

## Assumptions
- Versions picked from what is already in the local Gradle cache where possible, to build offline-friendly

## Notes
- `usesCleartextTraffic` is required for `ws://`; flagged as TODO for production (TLS)
- Room schema export dir `app/schemas` configured via KSP arg

## Testing
- `./gradlew assembleDebug` succeeds

## Tools / Skills
- Bash: `./gradlew assembleDebug`

## Implementation
<!-- Write you've done in here -->
