# iOS Bridge Context (Phase 4)

- Producer: `iosApp` Swift login shell
- Storage: `UserDefaults.standard`
- Key: `cn.xiaobai.mafei.kmp.bridge.session`

JSON shape (token is never written here):

```json
{
  "schemaVersion": 2,
  "authState": "unauthenticated | authenticated",
  "session": {
    "baseURL": "https://example.com",
    "userId": "user-id",
    "username": "name",
    "serverId": "optional-server-id"
  },
  "updatedAt": "ISO8601",
  "source": "ios-swift-shell"
}
```

Notes:
- Access token remains in iOS Keychain.
- `rememberSession=true`: token+metadata are persisted; `rememberSession=false`: no token/session metadata persistence across app restarts.
- Sign out writes a logged-out context (`authState = unauthenticated`, `session = null`).
- Swift container keeps a minimal state machine: `restoring -> unauthenticated -> authenticated`.
- While authenticated, bridge context is re-checked at startup, app foreground, and manual "Refresh Session Context".
- iOS shell exposes a controlled secure token path via `JellyfinSessionStore.withSecureAccessToken`.
- Runtime diagnostics are available via `JellyfinSessionStore.secureAuthRuntimeSnapshot()`.
- `serverId` strategy: prefer SDK-provided serverID; fallback to deterministic derived ID from normalized base URL hash.
- KMP runtime handoff path: see [IOS_KMP_RUNTIME_BRIDGE.md](/Users/machunjiang/MaFei/iosApp/IOS_KMP_RUNTIME_BRIDGE.md).
- Smoke validation steps: see [IOS_AUTH_SMOKE_CHECKLIST.md](/Users/machunjiang/MaFei/iosApp/IOS_AUTH_SMOKE_CHECKLIST.md).
- Shared contract mapping: see [IOS_BRIDGE_SHARED_CONTRACT_MAPPING.md](/Users/machunjiang/MaFei/iosApp/IOS_BRIDGE_SHARED_CONTRACT_MAPPING.md).
