# Privacy policy and Data safety

## Context
- files: @app/src/main/java/com/lakasir/acp/data/local/Entities.kt
- files: @app/src/main/AndroidManifest.xml
- files: @bridge/README.md
- The app stores on device: connection profiles (host, port, cwd, auth token), sessions, full message history (prompts, agent output, tool calls, raw JSON)
- Data leaves the device only to the user's own bridge; there is no analytics, ads, or third-party SDK
- Play requires a privacy policy URL and a completed Data safety form

## Goals
1. Write `docs/play/privacy-policy.md` (and host it, e.g. GitHub Pages): what is stored, where it goes (only the user-configured bridge), no third parties, how to delete (clear history / uninstall), contact
2. Write `docs/play/data-safety.md` with the answers for the Play form:
   - Data types: "App activity → other user-generated content" (prompts), "Files and docs" (workspace files viewed/edited)
   - Transmitted off device: yes, to a server the user operates; encrypted in transit: yes (`wss` only in release, plan 21)
   - Shared with third parties: no
   - User can request deletion: yes (in-app clear history, plan 29/36)
3. Add a privacy policy link in the settings screen (plan 36)

## Assumptions
- Declaring data as "collected" even though it goes to the user's own server, because Play counts any transfer off the device; this is the safe choice

## Notes
- If a crash reporting SDK is added later, update both documents (Play Console vitals need no SDK)
- Permissions to explain in the listing: notifications (turn finished, permission prompts), foreground service (plan 22)

## Testing
- Review the documents against the final release build's behavior and permission list (`aapt dump permissions`)

## Tools / Skills
- WebFetch for the current Data safety form categories

## Implementation
<!-- Write you've done in here -->
