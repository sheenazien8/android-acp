# 17 — Fix Offline Indicator Showing While Agent Is Online

## Context

- files: @app/src/main/java/com/lakasir/acp/ui/chat/ChatViewModel.kt
- files: @app/src/main/java/com/lakasir/acp/ui/chat/ChatScreen.kt
- files: @app/src/main/java/com/lakasir/acp/ui/components/ConnectionStatusIndicator.kt
- files: @app/src/main/java/com/lakasir/acp/ui/sessions/SessionsViewModel.kt
- files: @app/src/main/java/com/lakasir/acp/data/repository/AcpRepository.kt
- files: @app/src/main/java/com/lakasir/acp/data/repository/ConnectionState.kt

Current state:

- `AcpRepository.connectionStates` is a `StateFlow<Map<Long, ConnectionState>>` keyed by profile id. It only emits when a profile's state changes.
- The chat top bar shows `ConnectionStatusIndicator(connectionState, profileId = session.connectionProfileId)`. `connectionState` comes from `ChatViewModel.connectionState`:

  ```kotlin
  val connectionState = repository.connectionStates
      .map { states ->
          val profileId = session.value?.connectionProfileId
          if (profileId != null) states[profileId] ?: Disconnected else Disconnected
      }
  ```

- **Bug:** the mapping reads `session.value` as a snapshot. It does not observe `session`.
  - When the chat screen opens, Room hasn't loaded the session yet, so `session.value` is `null`. `session` is also `WhileSubscribed`, so it may not even be collecting yet.
  - The first map runs with `null` and yields `Disconnected`, so the indicator shows **"Offline"**.
  - After the session loads, nothing re-runs the map, because `connectionStates` doesn't emit again while the connection stays `Connected`. The indicator stays "Offline" until some profile reconnects or drops.
- Other flows in the same ViewModel already do this correctly. `availability` and the `init` resume collector both `combine(repository.connectionStates, session.filterNotNull())`. That's why the input bar works ("Ready") while the top-bar indicator says "Offline". The two disagree on the same screen.
- `SessionsViewModel.connectionState` maps with a constructor `profileId` (no async lookup), so it's correct. `ConnectionScreen` uses the whole map, so it's correct too. `WorkspaceRepository` availability uses `combine`, so it's correct.

## Goals

- The chat top-bar indicator shows the real state of the session's profile ("Connected", "Connecting", "Retrying", "Offline") as soon as the session loads. It stays in sync with `connectionStates` afterwards.
- The indicator and `availability` can never disagree: "Offline" in the top bar only when input is also `Offline`.
- No regressions in the Sessions and Connections screens.

## Notes

- Fix: derive `connectionState` from `combine(repository.connectionStates, session.filterNotNull())`, the same way `availability` does:

  ```kotlin
  val connectionState: StateFlow<ConnectionState> = combine(
      repository.connectionStates,
      session.filterNotNull(),
  ) { states, session -> states[session.connectionProfileId] ?: ConnectionState.Disconnected }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionState.Disconnected)
  ```

  Optionally use `.map { it.connectionProfileId }.distinctUntilChanged()` on `session` so title or auto-mode edits don't re-emit.
- Initial value: while the session is still loading, the indicator briefly shows "Offline" (the `stateIn` initial). That's acceptable. If it flickers visibly, render nothing until `session != null` (ChatScreen already has `profileId: Long?` available for that check).
- Declaration order matters: `session` is declared above `connectionState`, so referencing it in the initializer is safe. Keep it that way.
- Search for any other `.value` snapshot reads inside flow operators (`grep -rn "\.value" ui/ | grep -E "map|combine"`). The same bug pattern could hide elsewhere.
- Don't touch `ConnectionStatusIndicator`'s `profileId` scoping. It's correct once it receives the right state.
- The working tree has uncommitted plan-16 changes (public connections / `AcpEndpoint`). Keep this fix in its own commit that touches only the chat files (one commit per plan, no Claude attribution).

## Testing

- Unit test (new `app/src/test/java/com/lakasir/acp/ui/chat/ChatViewModelConnectionStateTest.kt`, or extend an existing repository-rules test if the ViewModel is hard to build in isolation):
  - Set the connection state to `Connected` for profile 1 **before** the session flow emits. Then emit the session with `connectionProfileId = 1`. Assert `connectionState` becomes `Connected`. This fails on the current code.
  - Change the state to `Error`, then `Disconnected`. Assert the indicator state follows.
  - Assert `connectionState is Connected` ⇔ `availability != Offline` for the same inputs.
  - If the ViewModel can't be built easily with the concrete `AcpRepository`, extract the pure mapping into a small top-level function and unit-test that instead.
- Manual on device/emulator:
  1. Connect to a bridge and open a session from the Sessions list. The top bar shows "Connected" immediately and the input is enabled.
  2. Kill the app, reopen it (background service keeps the connection alive), then deep-link or navigate into a chat. The top bar shows "Connected", not "Offline".
  3. Stop the bridge. The indicator moves to "Retrying" and the input to Offline. Restart the bridge and it returns to "Connected".
- `./gradlew :app:testDebugUnitTest` passes.

## Tools / Skills

- `./gradlew :app:testDebugUnitTest`, `./gradlew :app:assembleDebug`
- `adb` + the `run` skill to launch the app and verify on the emulator
- `mobile-android-design` skill, only if the loading-state rendering of the indicator changes

## Implementation
<!-- Write you've done in here -->
