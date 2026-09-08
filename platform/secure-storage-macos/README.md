# macOS ARM64 Keychain store — 06D

`MacOSSecureCredentialStore(namespace = "default")` implements the frozen
`SecureCredentialStore` on macOS Apple Silicon JVM Desktop. It consumes the
existing `SecureSecretCodec` inside Keychain, not in plaintext files/settings.
No external dependency, `security` CLI, JNA or JDK preview/FFM API is required.

## Layout

- `src/desktopMain/.../MacOSSecureCredentialStore.kt`: provider-neutral contract,
  status mapping, bounded upsert retry, stable scoped aliases, coroutine admission.
- `src/desktopMain/.../NativeKeychain.kt`: JNI bridge and bundled native loader.
- `src/nativeMain/objc/MaFeiKeychain.m`: Objective-C JNI → Security.framework.
- `src/desktopTest/`: fake-backed contracts, all-entrypoint JNI shape validation,
  separately opt-in real Keychain test.
- This module's `build.gradle.kts`: native compile, resources/JAR packaging and
  integration-test opt-in. No root Gradle/host files were modified by this package.

## Production decisions

1. **macOS file-based Keychain** (user default, normally login), explicitly
   selected with `kSecUseDataProtectionKeychain=false`, `kSecClassGenericPassword`, exact
   service `cn.mafei.credentials.v1/<namespace>` and exact account/alias. Native
   code independently rejects foreign services. No global Keychain enumeration,
   search-list edits, default-keychain changes or broad native delete endpoint.
2. No iCloud synchronization or Data Protection-only accessibility attributes.
   Writes use the system default Keychain; reads use its search list with the exact
   service/account filter. The system's default per-application ACL is retained;
   no all-applications trusted ACL is installed. Normal unlock/access prompts are
   permitted. Denial is an error, never an empty successful read or plaintext fallback.
3. This is an **explicit user-approved backend change (2026-09-09)**, not runtime
   fallback on entitlement errors. Ad-hoc JVM hosts can use this backend; rebuilds
   or a changed host identity may trigger system access prompts. Stable signing
   remains recommended for distribution. A final app with hardened runtime needs the
   native library signed appropriately for its host/library-validation policy.
4. `overwrite=false` invokes atomic `SecItemAdd`, never read-then-add as a
   correctness guarantee. Upsert uses Update → Add on missing → Update on a racing
   duplicate, bounded to eight iterations with cancellation checks; it never
   deletes the old item to overwrite it.
5. Coroutine work runs on Dispatchers.IO. Per-exact-service shared mutexes give
   same-process instances a shared order; wait admission is cancellable. Native
   synchronous commits cannot be rolled back by a cancellation racing completion.
   deleteAll snapshots exact-service aliases and deletes only requested prefixes;
   it is not a cross-item/cross-process transaction.
6. Keychain read missing is null; locked/not-available is BackendUnavailable;
   auth/entitlement denial is AccessDenied; decode/invalid data/I/O is StorageFailure;
   unsupported is Unsupported. `OSStatus` is retained in sanitized error text;
   raw backend exception causes are not attached because their text can contain
   credential material.
   No corrupt or denied read becomes an empty successful result. `exists` reads
   and validates the value rather than treating locked metadata as usable credentials.
7. JVM encoded buffers are cleared after every native write/read; native mutable
   input data uses macOS `memset_s` before release. Security.framework owns returned
   immutable CFData; it is promptly released but no claim is made of forcibly
   clearing memory owned by that framework. JNI result `toString()` redacts data.
8. Native resources are bundled at
   `native/macos-arm64/libmafei_keychain.dylib`. Only that trusted bundled binary is
   extracted to an owner-private temporary directory for System.load; secrets are
   never extracted. No cwd/java.library.path fallback is performed.

## Building

The build host must be macOS ARM64 with Xcode command-line tools and a full JDK
with `include/jni.h`. If an IDE's JBR omits headers, the module discovers an already
installed JDK 21 using `/usr/libexec/java_home -v 21`; nothing is downloaded. Override
with `-PmafeiKeychainJniJdk=/absolute/path/to/full-jdk` when needed. No user-specific
JDK path is hardcoded in this module.

```bash
./gradlew :platform:secure-storage-macos:desktopTest \
  :platform:secure-storage-macos:desktopJar verifyArchitecture
```

`compileKeychainNative` invokes clang for **arm64 only**, minimum macOS 11, with
Foundation/Security frameworks, ARC, `-Wall -Wextra -Werror`.
The resulting dylib is included in the Desktop JAR. Configuration cache works;
native builds track source/JDK location and require no root script mutation.

## Current evidence — 2026-09-08

- Test files were written first; the initial red attempt was blocked by missing
  IDE-JBR JNI headers and a Gradle task configuration-cache issue, **not** a proven
  behavioral red test. Both build issues were then fixed.
- Desktop unit/bridge tests **9/9 passed**: eight fake-backed contract tests and one
  real loaded-JNI test covering all five typed entrypoints with a foreign service
  rejected *before* SecItem is called. This proves JNI loading, names and result
  signatures, not successful Keychain storage.
- Native ARM64 dylib compilation, Desktop JAR packaging, verifyArchitecture and
  `git diff --check` passed. `file` confirms Mach-O 64-bit arm64; `nm` confirms all
  five JNI exports.
- Logs: `/private/tmp/mafei-06d-macos-green.log`, build bootstrap failure details
  `/private/tmp/mafei-06d-macos-red.log`.
- Mainline then added an independent error-privacy regression which exposed raw
  backend causes leaking through wrapped errors. The wrapper now drops those
  causes while preserving classification; final rerun **10/10 passed**, including
  that mainline test. The mainline-authored test was preserved, not edited.

### Historical Data Protection probe (superseded by explicit backend change)

The separately enabled probe targeted only fresh synthetic namespace
`integration.ec878408-3dde-461e-81b7-f08010fa3639` and alias `synthetic/token`.
The real JNI add reached Security.framework and returned **OSStatus -34018
(MissingEntitlement)**, correctly reported as AccessDenied. The development JVM
therefore **created no test credential**, and no unrelated namespace was queried
or modified. No unlock/signing/security-setting workaround was attempted.

Log: `/private/tmp/mafei-06d-macos-native.log`.

Run the opt-in synthetic round-trip on the selected file-based backend:

```bash
./gradlew :platform:secure-storage-macos:desktopTest \
  --tests '*MacOSKeychainNativeIntegrationTest' -PmafeiKeychainIntegration=true
```

Each invocation generates its own new synthetic namespace. It adds, reads,
atomically checks duplicate, updates, lists and deletes only the exact test alias.
The normal test task excludes this probe. Gradle JVM acceptance does not replace
packaged application login/restart acceptance because Keychain ACLs bind to hosts.

### File-based Keychain verification — 2026-09-09

User authorized choosing the working system backend. The existing real synthetic
round-trip now **passes**, including add/read/duplicate/update/list/delete and
cleanup; all **11/11** module tests and **56/56** application Desktop tests passed.
Mac packaging and architecture verification passed. No real account password/token
was used by the synthetic test. Log: `/private/tmp/mafei-login-keychain-green.log`.
Application login/restart acceptance is tracked in `composeApp/README.md`.

## Remaining integration gates

- Distribution signing/library-validation acceptance for the Apple Silicon host.
- Packaged-host login/restart; locked/login-after-reboot,
  access-denied and update/restore acceptance. Locking the user's real Keychain was
  intentionally not used for testing; locked/error behavior has fake-backed tests.
- No architecture expansion to Intel, Windows/Linux, iOS or HarmonyOS; no claims
  about those platforms follow from this Desktop module.
