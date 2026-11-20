# HTTP transport

`:platform:http` provides `createPlatformHttpTransport()` for the app's platform
entry points. Providers continue to depend only on `:core:http-contract`.

| Source | Responsibility |
| --- | --- |
| `commonMain` | Shared Ktor transport: deadlines, cancellation, redirects and bounded reads |
| `sharedJvmMain` | CIO, compiled by `androidMain` and `desktopMain` |
| `iosMain` | [Darwin and Set-Cookie compatibility](README-ios.md) |
| `ohosMain` | [HTTP-only Curl and the IPv6 fallback](README-ohos.md) |

CIO and Darwin client dependencies are scoped to their respective targets.
Only OHOS consumes the libcurl cinterop artifact. The Curl source and license
retain their existing [provenance](third-party/NOTICE.md).

```bash
./gradlew :platform:http:testDebugUnitTest :platform:http:desktopTest \
  :platform:http:iosSimulatorArm64Test
./gradlew :platform:http:linkDebugTestOhosArm64 -Pkotlin.native.cacheKind.ohosArm64=none
```

The OHOS test executable needs the cache override with CPF 1.3.0 to avoid
unresolved symbols from cached native libraries. This does not change the app
build settings or execute tests on a device.
