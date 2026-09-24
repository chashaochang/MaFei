# MaFei Feedback Components Implementation Plan

> **For agentic workers:** This plan is executed inline in the current session, task-by-task, with a focused test/build checkpoint after each batch.

**Goal:** Replace production Material feedback widgets with shared MaFei loading, progress, refresh, and error components while preserving existing screen layout, data flow, and interaction semantics.

**Architecture:** Put all visual feedback primitives in `common:ui`. Feature screens keep owning state and callbacks, but render only the shared components. The home screen gets a fixed-slot, non-interactive glass refresh indicator beside search. A source gate rejects new production Material widgets after migration.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Compose animation/foundation primitives, existing `common:glass` and `common:design-system` tokens, Gradle verification task, desktop Compose tests.

---

### Task 1: Add shared non-Material feedback primitives

**Files:**
- Create: `common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/MaFeiFeedbackComponents.kt`
- Modify: `common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/LegacyStateViews.kt`
- Test: `common/ui/src/desktopTest/kotlin/cn/mafei/cmp/common/ui/MaFeiFeedbackComponentsTest.kt`

- [ ] **Step 1: Add public component contracts**

Create these public types in `MaFeiFeedbackComponents.kt`:

```kotlin
enum class MaFeiLoadingStyle { Page, Inline, Button }

@Composable
fun MaFeiLoadingIndicator(
    modifier: Modifier = Modifier,
    style: MaFeiLoadingStyle = MaFeiLoadingStyle.Inline,
    tint: Color = MaterialTheme.colorScheme.primary,
)

@Composable
fun MaFeiProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.Black.copy(alpha = .34f),
    progressColor: Color = MaterialTheme.colorScheme.primary,
)

@Composable
fun MaFeiInlineNotice(
    message: String,
    modifier: Modifier = Modifier,
    kind: MaFeiNoticeKind = MaFeiNoticeKind.Error,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
)

enum class MaFeiNoticeKind { Error, Warning, Info }
```

The implementation uses only Canvas, Box, Row, drawBehind and Compose animation primitives. It must not import or call `CircularProgressIndicator`, `LinearProgressIndicator`, `Surface`, `Card`, `Button`, `TextButton`, or `OutlinedButton`.

- [ ] **Step 2: Implement loading animation without layout changes**

Use an `Animatable` or `rememberInfiniteTransition` phase for a short arc and two soft dots. `Page` uses a 42.dp visual, `Inline` 24.dp, and `Button` 16.dp. The `Button` variant has no hit target and no click indication.

- [ ] **Step 3: Implement determinate progress and inline notices**

`MaFeiProgressLine` draws a clipped rounded track and clamped progress fill. `MaFeiInlineNotice` uses a transparent/glass-compatible background, a 2.dp semantic accent, safe text color, and `mafeiInteractive` only when an action is supplied. Red is limited to the accent/icon for `Error`; there is no `errorContainer` usage.

- [ ] **Step 4: Route the existing full-screen loader through the shared component**

Change `LegacyLoadingView` to render `MaFeiLoadingIndicator(style = MaFeiLoadingStyle.Page)` while preserving its delayed reveal and full-size layout.

- [ ] **Step 5: Add a focused desktop rendering test**

In `MaFeiFeedbackComponentsTest.kt`, render each loading style and `MaFeiInlineNotice` under `MaFeiTheme`; assert nodes are present by content description/test tags and that an action invokes its callback. Do not assert Material implementation details.

- [ ] **Step 6: Run the focused test**

Run:

```bash
./gradlew :common:ui:desktopTest --tests '*MaFeiFeedbackComponentsTest' --no-daemon
```

Expected: the new test class passes.

- [ ] **Step 7: Commit the shared primitives**

```bash
git add common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/MaFeiFeedbackComponents.kt \\
  common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/LegacyStateViews.kt \\
  common/ui/src/desktopTest/kotlin/cn/mafei/cmp/common/ui/MaFeiFeedbackComponentsTest.kt
git commit -m "feat(ui): add non-material feedback primitives"
```

### Task 2: Migrate shared card and account feedback

**Files:**
- Modify: `common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/MediaCard.kt`
- Modify: `common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/SavedAccountList.kt`
- Test: `common/ui/src/desktopTest/kotlin/cn/mafei/cmp/common/ui/SavedAccountListTest.kt`

- [ ] **Step 1: Replace card progress and error block**

Replace the card's `LinearProgressIndicator` with `MaFeiProgressLine`. Replace `InlineProblem`'s `Surface(errorContainer)` and `TextButton` with `MaFeiInlineNotice` and its action callback. Keep the existing message and retry semantics unchanged.

- [ ] **Step 2: Replace saved-account busy spinner**

Replace the account-row `CircularProgressIndicator` with `MaFeiLoadingIndicator(style = MaFeiLoadingStyle.Inline)`. Keep delete-dissolve and row enabled/disabled state untouched.

- [ ] **Step 3: Run shared UI regression tests**

Run:

```bash
./gradlew :common:ui:desktopTest --no-daemon
```

Expected: all existing saved-account and dialog interaction tests pass.

- [ ] **Step 4: Commit the shared migration**

```bash
git add common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/MediaCard.kt \\
  common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/SavedAccountList.kt
git commit -m "refactor(ui): migrate shared card feedback"
```

### Task 3: Migrate the home refresh and progress behavior

**Files:**
- Modify: `feature/home/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/home/HomeScreen.kt`
- Modify: `feature/home/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/home/LibraryIndexScreen.kt`
- Modify: `common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/MaFeiActionBar.kt` only if the fixed-slot button needs a shared action-bar slot
- Test: existing `feature/home/src/desktopTest` tests plus a focused home indicator test if the module already contains UI tests

- [ ] **Step 1: Add the fixed-slot refresh indicator beside search**

Render a non-clickable `MaFeiRefreshIndicatorButton` in the same action row as search. Reserve its measured width at all times and animate only alpha/contents. Show it only when existing home content is present and refresh is active. Give it no `Role.Button`, no click handler, and a state description of `正在刷新首页`.

- [ ] **Step 2: Replace home indeterminate and determinate indicators**

Replace home `CircularProgressIndicator` calls with the appropriate `MaFeiLoadingIndicator` style. Replace carousel and playback/section progress lines with `MaFeiCarouselIndicator` or `MaFeiProgressLine`; do not change their state sources or timing logic.

- [ ] **Step 3: Replace home retry action and pull-to-refresh visual**

Replace the home `TextButton` retry with `MaFeiTextAction` or `MaFeiInlineNotice`. Replace the `PullToRefreshBox` wrapper with `MaFeiPullRefresh`, preserving the existing list state, refresh callback, and nested scroll behavior.

- [ ] **Step 4: Migrate the library index refresh wrapper**

Apply the same `MaFeiPullRefresh` wrapper to `LibraryIndexScreen` without changing content padding or route behavior.

- [ ] **Step 5: Verify home desktop behavior**

Run:

```bash
./gradlew :feature:home:desktopTest :composeApp:compileKotlinDesktop --no-daemon
```

Expected: build succeeds and existing home interaction tests pass.

- [ ] **Step 6: Commit the home batch**

```bash
git add feature/home/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/home/HomeScreen.kt \\
  feature/home/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/home/LibraryIndexScreen.kt \\
  common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/MaFeiActionBar.kt
git commit -m "refactor(home): use custom refresh and progress feedback"
```

### Task 4: Migrate feature pages in bounded batches

**Files:**
- Modify: `feature/media/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/media/VideoListScreen.kt`
- Modify: `feature/favorite/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/favorite/FavoriteScreen.kt`
- Modify: `feature/search/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/search/SearchScreen.kt`
- Modify: `feature/detail/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/detail/DetailScreen.kt`
- Modify: `feature/detail/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/detail/PersonDetailScreen.kt`
- Modify: `feature/detail/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/detail/SeasonDetailScreen.kt`
- Modify: `feature/connect/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/connect/ConnectScreen.kt`
- Modify: `feature/connect/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/connect/AccountHistoryScreen.kt`
- Modify: `feature/connect/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/connect/ServerDiscoveryScreen.kt`
- Modify: `feature/settings/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/settings/MineScreen.kt`
- Modify: `feature/settings/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/settings/SettingsScreens.kt`
- Modify: `feature/chasing/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/chasing/ChasingScreen.kt`
- Modify: `composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/App.kt`

- [ ] **Step 1: Replace page and inline loading indicators**

For every listed page, replace only the direct Material indicator call with `MaFeiLoadingIndicator` and preserve surrounding layout, state checks, and callbacks.

- [ ] **Step 2: Replace progress lines by semantic role**

Use `MaFeiProgressLine` for playback/episode progress and background task progress. Use `MaFeiLoadingIndicator` for indeterminate loading. Do not convert one into the other.

- [ ] **Step 3: Replace remaining visible Material retry/error blocks**

Use `MaFeiInlineNotice` for page-local errors and `MaFeiTextAction` or `MaFeiGlassAction` for existing actions. Keep existing user-facing strings and retry callbacks.

- [ ] **Step 4: Replace five pull-refresh wrappers**

Migrate `VideoListScreen`, `FavoriteScreen`, and `ChasingScreen` to `MaFeiPullRefresh`; home and library were migrated in Task 3. Preserve each page's list state and content padding.

- [ ] **Step 5: Compile and test each bounded group**

Run:

```bash
./gradlew :feature:media:desktopTest :feature:favorite:desktopTest :feature:search:desktopTest --no-daemon
./gradlew :feature:detail:desktopTest :feature:connect:desktopTest :feature:settings:desktopTest --no-daemon
./gradlew :composeApp:desktopTest --no-daemon
```

Expected: each command completes successfully.

- [ ] **Step 6: Commit feature migrations**

```bash
git add feature/media feature/favorite feature/search feature/detail feature/connect \\
  feature/settings feature/chasing composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/App.kt
git commit -m "refactor(ui): migrate feature feedback components"
```

### Task 5: Remove visible stock containers and add the Material UI gate

**Files:**
- Modify: `common/design-system/src/sharedUiMain/kotlin/cn/mafei/cmp/common/design_system/MaFeiTheme.kt`
- Modify: `common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/MediaCard.kt`
- Modify: `common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/MaFeiAdaptiveShell.kt`
- Modify: `composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/App.kt`
- Create: `scripts/verify-no-native-material-ui.mjs`
- Modify: `build.gradle.kts`

- [ ] **Step 1: Replace visible stock containers**

Use `Box`/ `Column` with `MaFeiShape` and existing glass/design-system helpers for visible panels, status chips, and error surfaces. Keep root layout Surface calls only where they provide a background without Material widget styling. Do not touch Android player host Surface.

- [ ] **Step 2: Add the source gate**

Create `scripts/verify-no-native-material-ui.mjs`. Scan only production `sharedUiMain` Kotlin sources under `common`, `feature`, and `composeApp`; ignore `FoundationProbeScreen.kt`. Fail on these symbols/imports:

```text
CircularProgressIndicator, LinearProgressIndicator, PullToRefreshBox,
Button, TextButton, OutlinedButton, Card, OutlinedCard, AlertDialog,
Snackbar, FilterChip, Switch, Slider, import androidx.compose.material3.*
```

The output must list relative file paths and line numbers for every offender.

- [ ] **Step 3: Register the Gradle verification task**

Add a root `verifyNoNativeMaterialUi` Exec task that runs the Node script, then make `check` depend on `verifyArchitecture`, `verifyNoSvgResources`, and `verifyNoNativeMaterialUi`.

- [ ] **Step 4: Run the gate and fix imports**

Run:

```bash
./gradlew verifyNoNativeMaterialUi --no-daemon
```

Expected: PASS with no production Material widget offenders. Remove wildcard imports from production UI files while retaining explicitly allowed token imports.

- [ ] **Step 5: Commit the gate**

```bash
git add common/design-system common/ui composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/App.kt \\
  scripts/verify-no-native-material-ui.mjs build.gradle.kts
git commit -m "build(ui): forbid native material widgets"
```

### Task 6: Cross-target verification and final review

**Files:**
- No new source files; review all files changed by Tasks 1–5.

- [ ] **Step 1: Run formatting and source checks**

```bash
git diff --check
./gradlew verifyArchitecture verifyNoSvgResources verifyNoNativeMaterialUi --no-daemon
```

- [ ] **Step 2: Run desktop regression tests**

```bash
./gradlew :common:ui:desktopTest :feature:home:desktopTest :feature:detail:desktopTest \\
  :feature:connect:desktopTest :composeApp:desktopTest --no-daemon
```

- [ ] **Step 3: Compile Android and Harmony targets**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinOhosArm64 --no-daemon
```

Expected: both targets compile without platform-specific Material fallbacks.

- [ ] **Step 4: Review the final diff**

Confirm that only feedback rendering changed: no route, provider, playback, persistence, or image-fetching behavior changed. Confirm the home refresh button remains fixed beside search and is non-interactive.

- [ ] **Step 5: Report pre-existing worktree changes separately**

Run:

```bash
git status --short
git log -1 --format='%h %an <%ae> %s'
```

Do not stage unrelated pre-existing changes.
