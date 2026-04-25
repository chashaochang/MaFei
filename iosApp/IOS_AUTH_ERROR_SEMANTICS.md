# iOS Auth Error Semantics (M1)

Scope:
- iOS shell only (`iosApp/**`)
- Authentication and server probe phases

## Stable Error Contract

Auth/probe failures are mapped to:
- `code`: stable contract error code
- `retryable`: whether retry action is meaningful
- `action`: suggested next UI action (`Retry` / `EditServer` / `ReLogin`)

Current implementation entry:
- `JellyfinAuthServiceError.map(_:)`

## Mapping Table

| Source condition | code | retryable | action |
| --- | --- | --- | --- |
| URL normalize/validation failed | `InvalidUrl` | `false` | `EditServer` |
| `URLError.notConnectedToInternet/cannotFindHost/cannotConnectToHost/timedOut/...` | `NetworkUnreachable` | `true` | `Retry` |
| `URLError.secureConnectionFailed/serverCertificateUntrusted/...` | `TlsCertificateError` | `false` | `EditServer` |
| HTTP `401` | `AuthFailed` | `false` | `ReLogin` |
| HTTP `403` | `Forbidden` | `false` | `ReLogin` |
| HTTP `5xx` | `ServerError` | `true` | `Retry` |
| Unknown/unclassified failures | `Unknown` | `true` | `Retry` |

## Runtime Visibility

- `ServerConfigView` shows mapped probe error summary (`code/retryable/action`).
- `LoginView` shows mapped sign-in error summary (`code/retryable/action`).
- UI action hints are exposed as buttons when applicable:
  - Retry test connection / retry sign in
  - Edit server (back to server config)
