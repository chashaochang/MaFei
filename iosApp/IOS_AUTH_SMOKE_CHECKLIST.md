# iOS Auth + KMP Handoff Smoke Checklist

Scope:
- iOS native shell (`iosApp/**`)
- iOS-specific KMP handoff assertions (`composeApp/src/iosMain/**`)
- Error semantics reference: [IOS_AUTH_ERROR_SEMANTICS.md](/Users/machunjiang/MaFei/iosApp/IOS_AUTH_ERROR_SEMANTICS.md)

Build pre-check:
1. Run:
   - `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES xcodebuild -project /Users/machunjiang/MaFei/iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'generic/platform=iOS Simulator' build`
2. Assert:
   - Build succeeds.

Runtime smoke scenarios:
1. Fresh launch (no saved session)
   - Expect server config view first, not Compose screen.
2. Server probe + login success (`Remember session = ON`)
   - Expect transition into Compose container.
   - Open menu, verify `User` and `Server` rows match login result.
   - In diagnostics, assert `Remember session = on` and `rememberSession semantics valid = yes`.
3. Cold relaunch after remembered login
   - Expect startup restore path and direct entry to Compose container.
   - Expect KMP to land on Home route instead of showing its own empty server/login bootstrap.
4. Login success (`Remember session = OFF`)
   - Expect transition into Compose container for current process.
   - In diagnostics, assert:
     - `Remember session = off`
     - `Persisted metadata = no`
     - `Persisted secure token = no`
     - `rememberSession semantics valid = yes`
5. Cold relaunch after remember=off login
   - Expect return to unauthenticated flow (must not auto-enter Compose).
6. Bridge context check (authenticated)
   - In Compose menu, tap `Session Diagnostics`.
   - Assert snapshot fields:
     - `schemaVersion = 2`
     - `authState = authenticated`
     - `session.baseURL/userId/username` present
     - `session.serverId` is present (SDK serverID or deterministic derived ID)
     - `source = ios-swift-shell`
7. KMP runtime bridge check
   - After entering Compose, verify Home/Detail/Search placeholder content reflects active iOS server/user context.
   - Reference: [IOS_KMP_RUNTIME_BRIDGE.md](/Users/machunjiang/MaFei/iosApp/IOS_KMP_RUNTIME_BRIDGE.md)
8. Manual bridge recovery
   - In Compose menu, tap `Refresh Session Context`.
   - Expect success banner (`up to date` or `refreshed`).
9. Sign out flow
   - Tap `Sign Out`.
   - Expect return to unauthenticated flow (login/server config), not Compose.
10. Bridge context check (after sign out)
   - Reopen app shell and inspect diagnostics when available.
   - Assert logged-out context is written (`authState = unauthenticated`, `session = nil`).
11. Auth runtime health check (authenticated)
   - In Compose menu, tap `Validate Auth Runtime` (or diagnostics button).
   - Assert success banner appears and diagnostics show:
     - `Secure token available = yes`
     - `Bridge/session consistent = yes`
     - `Secure token source = in-memory session` or `keychain`
12. Error semantics: invalid server URL
   - In server config, input malformed URL then test connection.
   - Assert error summary includes:
     - `code=InvalidUrl`
     - `retryable=no`
     - `action=EditServer`
13. Error semantics: wrong password
   - Use valid server + wrong credentials to sign in.
   - Assert error summary includes:
     - `code=AuthFailed`
     - `retryable=no`
     - `action=ReLogin`
14. Error semantics: network unreachable
   - Use unreachable host or disable network then test/sign in.
   - Assert summary maps to `NetworkUnreachable` (or `TlsCertificateError` for TLS failures), with expected `retryable/action`.

Key security assertions:
- Access token is not stored in bridge context payload.
- Access token remains in iOS Keychain storage path.
