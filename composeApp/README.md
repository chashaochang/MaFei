# Application runtime / login and browsing

## Login and browse increment (2026-09-08)

The default shared Compose entry is now Connect → library grid → read-only detail,
not the foundation probe. The probe remains under **实验室**; native smoke scripts
enter it explicitly. Login/home/detail live in their existing Feature modules;
reusable cards and feedback live in `common:ui`. No module/dependency was added.

- `ProviderLogin` authenticates with a fresh real SDK. Jellyfin keeps its device ID
  through binding admission. Feiniu uses an explicit Direct HTTP(S) address, login
  followed by authenticated user/info, then the latest authoritative Token/Cookie.
  No OAuth, FNID resolution, discovery, relay or login retry was invented.
- `ApplicationRuntime.connect()` admits that fresh binding and protects it against
  post-admission cancellation. State includes the actual runtime session handle, so
  reopening the same saved identity still replaces the screen/cache generation.
- Each new connection currently gets a fresh local namespace. Existing-account
  reauthentication/deduplication and saved-account management UI remain pending.
- The feature browser uses the existing MediaRepository and page accumulator:
  opaque cursor append/deduplication, cache warnings, safe error messages, empty
  states, retry, library switching and stale-completion rejection. List scroll is
  retained when returning from detail; a library switch resets it.
- Password is composition-only and cleared after submit. Settings contain no
  passwords or access tokens. Android cleartext and iOS ATS policy explicitly
  permit user-supplied HTTP-only NAS addresses; the form warns HTTP sends login
  data unencrypted and recommends HTTPS. No TLS certificate checks were disabled.
  iOS local-network usage text is declared; runtime permission remains unverified.
- Uses stable adaptive grid/bounded layouts supported by the frozen CPF toolchain,
  not newer experimental Android-only adaptive APIs. The shell owns safe insets;
  connect additionally handles IME. The glass bar only captures a background sibling.

Current limitations: text-only poster placeholders, read-only metadata (no season/
episode drilldown yet), no player, explicit in-page back only (system-back/gesture
navigation remains pending), no real server/device acceptance. This is a functional
code path, not final visual design or a completed 2.0 product.

Verification for this increment: Desktop **52/52** and Android JVM **50/50** actually
executed. Includes 8 real-SDK/fake-HTTP login tests, 10 browse tests and 2 new root
integration/session-handle tests. The library-removal regression was reproduced as
one failing test, then fixed. iOS device/simulator and OHOS application compilation
passed. Final Android Debug APK assembly/signature verification and fresh OHOS
so/header publication → unsigned ARM64 HAP → artifact consistency gate passed.
Node foundation tests **7/7**, plist validation and script syntax checks passed.
`hdc list targets` was `[Empty]`; no install, visual check or server/device login
was performed. iOS plist host changes were linted, not built through Xcode in this
increment. Existing CPF linkage/generated-C warnings are not runtime acceptance.

Artifacts:

- `composeApp/build/outputs/apk/debug/composeApp-debug.apk` (Android Debug signed)
- `harmonyApp/entry/build/default/outputs/default/entry-default-unsigned.hap`

Logs: `/private/tmp/mafei-browse-red.log`, `/private/tmp/mafei-login-browse-gates.log`,
`/private/tmp/mafei-login-browse-final.log`, `/private/tmp/mafei-login-browse-delivery.log`,
`/private/tmp/mafei-login-browse-hap.log`. The final delivery run includes all latest
source changes; JNI/Asset/Keychain hardware behavior remains unverified here.

## Previous startup increment

The four platform entry points now create one process-scoped `ApplicationRuntime`:
real platform HTTP, `SecureCredentialStore`, ordinary `SettingsStorage`, SDK factory,
connection catalog, session persistence and media repository. Activity/controller recreation
reuses that root; there is not a second writer per screen. Platform entry points must call
`ProcessApplication` on their main thread. Desktop drains the root before exiting;
mobile roots live with their application process (page hide/rotation is not logout).

`App` starts recovery and provides `LocalApplicationRuntime` to shared Compose content.
An installed/restored SDK session is **not** verified authentication with a server;
the first browse request can still expose expired credentials or connectivity failure.

## Recovery and logout

- `ApplicationSessions.start()` loads the current committed credential group and its
  `SavedConnection`, creates a fresh real Jellyfin/Feiniu SDK and adapter, installs the
  credentials, opens a new media-cache generation and observes authoritative credentials.
  No unauthenticated network request is sent on fresh startup.
- `switchAccount()` cancels and joins the old observer before clearing its SDK and media
  cache. The prior account's saved credentials remain available. Old adapters and handles
  cannot send/publish late responses or log out a new account.
- `activate(connection, binding)` is the connect/login flow's admission point.
  It consumes a successfully authenticated SDK binding, not an unverified server-response
  snapshot. `DefaultProviderSessionFactory.restore` also supports constructing a new
  isolated binding from already persisted credentials; it does not perform login itself.
- `logout(handle)` revokes recovery eligibility and drains credential deletion. Failure
  is visible as NeedsAttention; `retryCleanup()` must succeed before claiming logout.
  Closing the application preserves the saved login, but clears memory/cache and closes
  its root HTTP transport. Cancellation after admitted activation may still commit; the
  published state remains authoritative and the newly installed SDK is not cleared.
- Jellyfin's binding is single-identity. It cannot silently accept another SDK login via
  `set`. Feiniu projects its versioned authoritative StateFlow directly. Binding cleanup
  never closes the shared root transport.

## Persistence / host boundaries

Connection metadata has explicit server/account identity, remote user ID and bounded,
stable ASCII credential aliases. Feiniu routes must be explicit (Local/Public/Relay/Direct),
not guessed from URLs. Token scheme: Jellyfin `MediaBrowser`, Feiniu `Bearer`.
Credential-bearing/query/fragment URLs are rejected, including avatars. A connect flow
must omit runtime signed avatar URLs (`account.copy(avatarImage = null)`) and re-fetch them.
Secrets only enter the secure store; no Token/Cookie is serialized in connection settings.

Settings use one versioned non-secret JSON document, typed string/byte entries, bounded
reads and serialized atomic edits. Corrupt/unreadable data is not replaced with empty data.
Android/iOS/OHOS write and fsync a temporary file before same-directory rename; Mac uses
NIO ATOMIC_MOVE without fallback. Cancellation is checked before commit. One root owns
one storage instance. This is atomic visibility/process-restart recovery, **not** a
cross-process transaction or a guarantee against sudden power loss of the last rename.

Harmony's thin host only passes the real application `filesDir` through N-API. Kotlin
copies the callback string synchronously. Exceptions are converted to a generic N-API
error rather than escaping C; no N-API handle is passed to persistence workers.
No ArkUI business screen and no new Gradle module were introduced.

## Verification (2026-09-08)

- Application Desktop **32/32** and Android JVM **30/30** actually executed. They cover
  real SDK/adapter + fake HTTP wiring, startup/restore/switch/logout/shutdown, late responses,
  admitted cancellation, SDK identity revocation, privacy and settings behavior.
  Desktop includes **2 actual temporary-file tests**, not real Keychain authentication.
- iOS device/simulator ARM64 and OHOS ARM64 application sources compiled. Native tests,
  iOS/Android device storage, OHOS system storage and server login were **not executed**.
- `:data:repositories:desktopTest/testDebugUnitTest` gates passed UP-TO-DATE; their previous
  56-per-target result is not counted as freshly rerun in this batch.
- Fresh OHOS so/header publication, unsigned ARM64 HAP packaging, artifact gate and
  7 Node foundation tests passed. `hdc list targets` was `[Empty]`: no install or first frame.
  Existing CPF fusion-renderer partial-linkage warnings remain unverified.
- Logs: `/private/tmp/mafei-startup-final-tests.log`,
  `/private/tmp/mafei-startup-final-gates.log`, `/private/tmp/mafei-startup-publish.log`,
  `/private/tmp/mafei-startup-hap.log`. Tests initially exposed non-Unit JUnit methods,
  JVM coroutine exception-copy expectations and macOS `/var` canonical-path fixture issues;
  the assertions retain cause-chain redaction and symlink rejection. N-API failure return
  was corrected to a nullable pointer. The avatar negative test confirmed pre-write rejection,
  not a reproduced plaintext leak.

Remaining: migration Reader/Sink must
populate this catalog/index before startup; actual player runtime/Surface and account-change
player teardown; signed Mac Keychain host; real-device/server/upgrade acceptance. There is
no OAuth integration and no claim that Stage 1 or the product is fully accepted.
