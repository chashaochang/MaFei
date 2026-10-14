# Application runtime / login and browsing

## 当前架构与导航代码入口

新增功能或修改行为先查看[应用架构与修改入口](ARCHITECTURE.md)，按状态所有者定位实现。
路由定义与壳层映射见[应用导航边界](src/sharedUiMain/kotlin/cn/mafei/cmp/navigation/README.md)。
下方带日期的记录是历史验证结果，不能代替当前代码的验收。

## Login/home completed increment — 2026-09-09

The packaged Mac application now completes **form login → real library/artwork →
restart restore → local logout**. The remaining runtime failure after the Keychain
change was **missing `Dispatchers.Main`**, not another proven Keychain failure.
Compose's own UI dispatcher did not install a kotlinx-coroutines Main provider;
starting the session observer failed. Desktop now explicitly includes the Swing
provider, with an actual AWT-thread regression test (red before dependency, green
after). This corrects the earlier incomplete diagnosis below.

### Temporary Mac credential policy (explicit user approval)

For local development only, build/run with:

```bash
./gradlew :composeApp:createDistributable -Pmafei.desktopDevelopmentStorage=true
```

The resulting `.app` carries `-Dmafei.desktop.developmentStorage=true` and a
development title. Token/Cookie values are **unencrypted**, under the Mac user's
`Library/Application Support/cn.mafei.cmp/credentials-development-v1/sessions.json`,
with owner-only directory/file permissions (0700/0600) and atomic replacement.
Passwords and arbitrary raw secrets are rejected. This is not a secure-storage
claim or implicit Keychain fallback. Default builds keep Keychain; DMG/PKG/release
packaging is blocked with this flag. Other platforms are unchanged. The backend is
single-process only; do not run two writers against the same profile.

### UI and media behavior

- Compact glass navigation, account/server context, and secondary laboratory menu.
- Wide login uses a two-column welcome/form layout; narrow windows use a scrollable
  single column. Password visibility, keyboard focus/Return submit, loading/error and
  HTTP warning are present; submitted passwords are cleared from composition.
- Home has adaptive poster grids, horizontally scrolling library selection,
  progress/played/favorite markers, retry/loading/skeleton/empty/stale states and
  opaque cursor pagination. Library type filters now apply on **all pages**, so
  Series libraries do not mix in Seasons/Episodes (a real-server finding with a
  regression test that failed before the fix). There are no invented recommendation
  or continue-watching feeds.
- Artwork uses existing platform transport, same-origin configured-server checks,
  header credentials (never token query synthesis), same-origin redirects, 4 MiB
  response cap and 12s deadline. Images decode off the UI thread; four concurrent
  loads and a 24 MiB account/screen-scoped decoded memory cache. Invalid images show
  a placeholder. No disk image cache, third-party image SDK or new module.
- Detail receives the same artwork renderer. Playback remains out of this increment.

### Actual acceptance

- Packaged SDK/ApplicationRuntime against the user's Jellyfin **10.11.11**: login,
  all **10** libraries, sampled artwork in each library, and new-runtime restoration
  passed. The primary movie library contains **9** movies after type filtering.
- Actual Mac window: restored normal profile into home; isolated clean profile
  completed keyboard form entry/Return submit into home; subsequent process restart
  restored it; keyboard logout returned to login and left **0** credential entries
  in that isolated profile. The normal profile is retained for the user.
- Actual dark/light desktop home and wide login inspected; **390dp** home and login
  inspected (two-column posters, one-column form with scrolling). No coordinate
  clicks were guessed: the form/theme/logout acceptance used keyboard focus.
- Desktop **68/68**, Android JVM **60/60** tests passed. iOS ARM64/OHOS source
  compilation and `verifyArchitecture` passed; these are not device acceptance.
- Logs: `/private/tmp/mafei-login-home-final.log`,
  `/private/tmp/mafei-library-types-red.log`, `/private/tmp/mafei-main-dispatcher-red.log`,
  `/private/tmp/mafei-main-dispatcher-green.log`, `/private/tmp/mafei-login-home-fixed-runtime.log`.
  No server passwords or tokens belong in this repository or logs.

For window-size checks the Desktop entry honors bounded optional JVM properties
`mafei.desktop.windowWidth` / `mafei.desktop.windowHeight` (dp); defaults 1180×780.
These are Mac layout checks, not iOS/Android/OHOS keyboard, inset or device acceptance.
The Native migration bridge, player, series/episode drilldown, account management,
and secure Mac distribution/signing remain separate pending work.

## Mac login failure follow-up (2026-09-09)

**Latest: explicit file-based Keychain selection.** User approved using the working
system backend. Mac now uses the system default file-based Keychain (normally login),
with normal per-host ACL/prompts and no plaintext fallback. Its real synthetic
add/read/duplicate/update/list/delete test passed and cleaned its test entry;
macOS storage module **11/11**, application Desktop **56/56**, architecture gate
and fresh Mac packaging passed. The newly built application was launched.
Automated AX value-setting was rejected as not settable, so actual UI login and
restart restoration still need manual acceptance; do not equate synthetic Gradle
JVM acceptance with packaged-host ACL acceptance. Signing remains a distribution
concern, not a prerequisite for the selected development backend.
Log: `/private/tmp/mafei-login-keychain-green.log`.

### Previous diagnosis and error-stage correction

The live test server (Jellyfin 10.11.11) accepted authentication using the packaged
Mac SDK; library enumeration and sampled directory detail worked. Its diagnostic
session was logged out. No server credentials belong in this repository.

The Mac package was ad-hoc signed; the machine had **0 valid code-signing
identities**. Data Protection Keychain synthetic writes returned **-34018**.
This required Apple signing/provisioning or an explicitly approved login-Keychain
backend change, now made above. The error-stage correction alone did not fix storage.

`ConnectionSaveException` now distinguishes failed post-authentication admission
from rejected authentication. UI text identifies denied/unavailable secure storage,
without retaining backend messages/causes. Admitted cancellation still keeps the
committed SDK. Two new regression cases failed before the fix; final Desktop
**56/56**, Android JVM **54/54**, iOS ARM64/OHOS compilation, architecture gate and
Mac packaging passed. No new runtime/Keychain round-trip acceptance is claimed.
Logs: `/private/tmp/mafei-connect-stage-red.log`, `/private/tmp/mafei-connect-stage-green.log`.

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
