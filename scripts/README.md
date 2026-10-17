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
