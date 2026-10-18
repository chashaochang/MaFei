# Verification

Use Node 20+ and the checked-in lockfile:

```sh
npm ci --ignore-scripts
npm test
./gradlew :check --max-workers=2
```

The Node suite includes Harmony host bridge behavior under mocked platform
services. Its TypeScript 4.9.5 transpiler is a local npm development dependency,
matching the previously used DevEco copy; DevEco installation paths are not
required. `MAFEI_TYPESCRIPT` can explicitly select another local compiler for
compatibility investigation. These tests do not replace ArkTS compilation or
physical-device acceptance.

The GitHub workflow runs on Linux with Java 17 and Android SDK 37. Besides root
`:check`, it explicitly runs Android application JVM tests and the shared browser,
player, repository and adapter Desktop JVM behavior suites. Root `check` alone
does not execute every application test. CI excludes `mavenLocal()` so artifacts
must resolve from the configured public repositories.

The workflow does not build/sign HAPs, access private player SDKs, package desktop
applications, or claim iOS/Harmony/macOS device acceptance. Full desktop application
tests require an Apple Silicon Mac because their runtime includes native AVPlayer
and secure-storage bridges. Those remain separate platform validation steps.

Source-size budgets compare against the pull request base or previous push SHA.
Checkout retains history for that comparison. Locally the script defaults to
`HEAD`; an explicit `MAFEI_SIZE_BASE_REF` checks a whole branch against its base.

`./gradlew verifyFeedbackPolicy` (also required by root `:check`) runs regression
tests and scans active production Kotlin/ArkTS/TypeScript/Swift sources for removed
inline notices, obvious error banner components, and direct error text, including
nullable-lambda aliases and string interpolation. Comments, literal documentation,
tests, generated code and third-party sources are excluded. This is a lexical guard
with simple alias tracking, not type-aware or visual layout analysis; custom
renderers still need review and device validation.

Use `MaFeiErrorDialog`, `MaFeiGlassConfirmDialog` or `LegacyTransientToast` for
independent feedback; use `LegacyFailedView` only for a genuine full-page failure
without usable content. A necessary direct text renderer in a full-page failure,
the standard dialog implementation, business error history or developer diagnostic
state must have one immediately preceding comment, for example:

```kotlin
// mafei-feedback: business-history -- Displays a completed task's stored error history, not operation feedback.
Text(task.lastError)
```

The only categories are `full-page-error`, `dialog-content`, `business-history`,
and `diagnostic-state`. A specific reason is required. Each marker covers only the next rejected text call;
stale/broad markers fail, and removed inline APIs or error banners cannot be exempted.
