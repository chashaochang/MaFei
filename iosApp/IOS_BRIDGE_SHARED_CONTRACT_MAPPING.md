# iOS Bridge to Shared Contract Mapping

Scope:
- iOS shell only (`iosApp/**`)
- For KMP integration planning and testing alignment

Bridge payload source:
- Key: `cn.xiaobai.mafei.kmp.bridge.session`
- Schema: `KMPBridgeContext` (v2)

Session/token storage policy:
- `rememberSession = true`: token -> Keychain, metadata -> UserDefaults
- `rememberSession = false`: token/metadata are not persisted (memory-only session)

## Field Mapping

| iOS bridge field | Shared contract field (suggested) | Status | Notes |
| --- | --- | --- | --- |
| `schemaVersion` | `authContext.schemaVersion` | Ready | Current value is `2`. |
| `authState` (`authenticated/unauthenticated`) | `authContext.state` | Ready | Direct enum mapping. |
| `session.baseURL` | `authContext.server.baseUrl` | Ready | Required when authenticated. |
| `session.userId` | `authContext.user.id` | Ready | Required when authenticated. |
| `session.username` | `authContext.user.username` | Ready | Required when authenticated. |
| `session.serverId` | `authContext.server.id` | Ready | SDK serverID preferred; missing时使用 deterministic derived serverId。 |
| `updatedAt` | `authContext.updatedAt` | Ready | ISO8601 date. |
| `source` | `authContext.source` | Ready | Current producer: `ios-swift-shell`. |
| `accessToken` (Keychain) | `secureAuth.accessToken` | Not downflowed by design | Must be provided by a native secure channel, not UserDefaults bridge. |

## What Is Already Available

- Auth state and identity context required for KMP bootstrap:
  - `state`
  - `server.baseUrl`
  - `user.id`
  - `user.username`
- Versioning and provenance:
  - `schemaVersion`
  - `source`
  - `updatedAt`

## Native Secure Access Path (Implemented in iOS Shell)

- `JellyfinSessionStore.withSecureAccessToken { token in ... }`
  - Controlled token access callback for future native->shared handoff.
  - Does not place token into UserDefaults bridge payload.
- `JellyfinSessionStore.secureAuthRuntimeSnapshot()`
  - Read-only diagnostic snapshot for shell/container health.
  - Includes rememberSession persistence checks, token availability/source and bridge/session consistency signals.
- Secure token source resolution order:
  - active in-memory session token
  - Keychain token fallback (only when `rememberSession = true` and session is active)
  - unavailable (must re-authenticate)

## baseUrl Normalization Rule (Implemented)

- Implemented in `JellyfinAuthService.normalizeBaseURL`.
- Input normalization:
  - Trim whitespace.
  - Auto-prepend `https://` when scheme missing.
  - Accept only `http` / `https`.
  - Lowercase scheme + host.
  - Remove trailing `/` from path (root path becomes empty).
  - Remove query/fragment/userinfo from persisted base URL.
- `probe` / `login` / stored session all use this normalized value.

## serverId Source Strategy (Implemented)

- Preferred source: `AuthenticationResult.serverID` from Jellyfin Swift SDK.
- Fallback when SDK serverID is absent/empty:
  - Deterministic ID derived from normalized base URL:
  - `derived-` + first 12 bytes of `SHA256(lowercasedBaseURL)` as hex.
- This avoids null server identity while keeping one stable serverId per normalized base URL.

## Missing / Outside Current Bridge

- Secure token fields are intentionally not present in bridge payload:
  - `accessToken`
  - Any refresh token / secret material
- App-specific domain bootstrap data (home/feed/media data) is not part of this bridge.

## Cannot Be Downflowed in Plain Bridge

- Secrets in UserDefaults bridge:
  - Access token
  - Refresh token
  - Any credential/secret that can grant API access

These must remain in Keychain and be exposed to shared layer only through a secure native boundary.
