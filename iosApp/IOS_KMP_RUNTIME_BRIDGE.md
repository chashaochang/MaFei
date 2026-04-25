# iOS KMP Runtime Bridge

Scope:
- Swift shell: `iosApp/**`
- KMP iOS entry: `composeApp/src/iosMain/**`

## What This Adds

- When Swift login shell enters `ComposeView`, it now passes the active session context directly into `MainViewControllerWithBridge(...)`.
- `iosMain` stores that context in an in-memory runtime bridge for the current container lifecycle.
- `AppPersistence.ios` reads the runtime bridge first and synthesizes:
  - one default `JellyfinServer`
  - one active `SessionRecord`

## iOS Integration Note

- `iosApp/ContentView.swift` invokes the Kotlin bridge entry point through Objective-C runtime lookup first, and falls back to legacy `MainViewController()` only if the bridge selector is unavailable.
- This avoids Swift compile-time breakage when Xcode caches an older `ComposeApp.framework` module interface, while still allowing the latest linked framework binary to provide the active session bridge.

This lets KMP iOS startup move past "empty server/session" and enter Home with the already authenticated native session.

## Security Boundary

- Access token is still not written into the UserDefaults bridge payload.
- Access token is handed to KMP iOS only through the active in-memory container bridge.
- Long-term persistence remains owned by Swift shell / Keychain path.

## Current Business-Step Outcome

- KMP iOS Home/Detail/Search/Playback now receive native server/user/session context.
- Current repositories are still placeholder-data based, but they are bridge-aware and expose the active iOS business context in UI-facing metadata.

## Known Limitation

- To satisfy current common startup gating without changing `commonMain`, the runtime bridge promotes the active container session into a KMP-usable startup session for the current process.
- Real long-term remember/persistence semantics are still enforced by Swift shell, not by this runtime bridge layer.
