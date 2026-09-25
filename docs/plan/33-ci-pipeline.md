# CI pipeline

## Context
- files: @app/build.gradle.kts
- files: @bridge/package.json
- No CI; checks run by hand (`assembleDebug testDebugUnitTest lintDebug`, `node --test`)

## Goals
1. `.github/workflows/ci.yml` on push and PR:
   - JDK 17, Gradle cache, `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
   - Node 20, `cd bridge && npm ci && npm test`
2. `release.yml` on tag `v*`: `bundleRelease` with signing secrets from GitHub Actions secrets, `versionCode` from `github.run_number`, upload AAB + `mapping.txt` as artifacts
3. Optional later: upload to the Play internal track with `r0adkll/upload-google-play` (needs a service account JSON)

## Notes
- Keystore stored as base64 secret, decoded to a temp file in the job
- Lint baseline: fail on new issues only if lint currently has warnings

## Testing
- Push a branch and see both jobs pass; push a test tag and download the AAB artifact

## Tools / Skills
- Bash: `gh workflow run`, `gh run watch`

## Implementation
<!-- Write you've done in here -->
