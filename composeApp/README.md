# Application runtime / session startup

The four platform entry points now create one process-scoped `ApplicationRuntime`:
real platform HTTP, `SecureCredentialStore`, ordinary `SettingsStorage`, SDK factory,
connection catalog, session persistence and media repository. Activity/controller recreation
reuses that root; there is not a second writer per screen. Platform entry points must call
`ProcessApplication` on their main thread. Desktop drains the root before exiting;
mobile roots live with their application process (page hide/rotation is not logout).

`App` starts recovery and provides `LocalApplicationRuntime` to shared Compose content.
The existing interface-laboratory page shows the startup status; this is **not** the finished
connect/home UI and an installed SDK session is **not** verified authentication with a server.

## Recovery and logout

- `ApplicationSessions.start()` loads the current committed credential group and its
  `SavedConnection`, creates a fresh real Jellyfin/Feiniu SDK and adapter, installs the
  credentials, opens a new media-cache generation and observes authoritative credentials.
  No unauthenticated network request is sent on fresh startup.
- `switchAccount()` cancels and joins the old observer before clearing its SDK and media
  cache. The prior account's saved credentials remain available. Old adapters and handles
  cannot send/publish late responses or log out a new account.
- `activate(connection, binding)` is the future connect/login flow's admission point.
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

Remaining: connect/login UI and actual authentication flow; migration Reader/Sink must
populate this catalog/index before startup; actual player runtime/Surface and account-change
player teardown; signed Mac Keychain host; real-device/server/upgrade acceptance. There is
no OAuth integration and no claim that Stage 1 or the product is fully accepted.
