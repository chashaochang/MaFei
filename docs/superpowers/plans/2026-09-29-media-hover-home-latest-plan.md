# Media Hover Actions and Home Latest by Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every media card expose consistent hover actions with live watched/favorite state, and render per-library recent additions on the large-screen home without changing the phone home structure.

**Architecture:** Keep the hover layer in `common:ui`; cards receive immutable media state and callbacks, while `MediaBrowser` owns optimistic state updates, per-media/action serialization, and rollback. Large-screen `HomeScreen` composes the existing horizontal shelf once per library and uses the existing `homeLatestByCollection` cache; phone Floating Tabs remain unchanged.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Compose interaction sources, StateFlow, existing `MediaBrowser`, existing MaFei glyphs and design tokens.

---

## File map

- Modify `common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/MediaCard.kt`: expose the shared hover overlay and add watched/favorite state inputs.
- Modify `common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/LegacyMediaComponents.kt`: pass media state to the same overlay from legacy video/base cards.
- Modify `feature/home/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/home/HomeScreen.kt`: add hover actions to resume/next-up shelves and render per-library latest shelves only in wide home layouts.
- Modify `feature/home/src/commonMain/kotlin/cn/mafei/cmp/feature/home/MediaBrowser.kt`: preload wide-home feeds and implement optimistic, per-media/action user-data mutations.
- Modify `composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/App.kt`: load latest feeds for the wide home route and pass callbacks/state to all card-bearing routes; stop using the unified latest page as the wide-home entry.
- Modify `feature/home/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/home/HomeLatestByLibraryScreen.kt`: keep the route internally consistent if it remains reachable, using the shared card state/callback contract.
- Add tests under `feature/home/src/commonTest/kotlin/cn/mafei/cmp/feature/home/MediaBrowserUserDataActionTest.kt`: verify independent optimistic mutations, rollback, and propagation to all state collections.

## Task 1: Freeze the shared hover-action contract

**Files:**
- Modify: `common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/MediaCard.kt`
- Modify: `common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/LegacyMediaComponents.kt`

- [ ] **Step 1: Extend card parameters without changing existing callers.**

Add these defaulted parameters to both `MediaCard` and `LegacyVideoCard`/`LegacyBaseItemCard`:

```kotlin
isWatched: Boolean = false,
isFavorite: Boolean = false,
```

Keep `onPlay`, `onToggleWatched`, and `onToggleFavorite` nullable so unsupported providers remain non-interactive.

- [ ] **Step 2: Make `MediaCardHoverOverlay` public and stateful.**

Change its contract to:

```kotlin
@Composable
fun MediaCardHoverOverlay(
    title: String,
    isWatched: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleWatched: (() -> Unit)?,
    onToggleFavorite: (() -> Unit)?,
    modifier: Modifier = Modifier,
)
```

Pass the two booleans from every card. Keep the existing mask, play glyph, spacing, and hover geometry. For each action use the existing MaFei glyph and theme primary color when selected; do not add a Material button or new card surface. The action label must switch between `设置已观看`/`取消已观看` and `收藏`/`取消收藏`.

- [ ] **Step 3: Add state-aware action rendering.**

Update `CardHoverAction` to accept `selected: Boolean` and render the selected action with a small primary-color circular wash plus white glyph, while the unselected action keeps the existing white glyph. The action remains disabled only when its callback is null.

- [ ] **Step 4: Wire the legacy cards.**

`LegacyVideoCard` and `LegacyBaseItemCard` must call the shared overlay with:

```kotlin
isWatched = item.userData.isPlayed,
isFavorite = item.userData.isFavorite,
```

The outer card click must keep opening details; child action clicks must continue to be consumed by their own clickable nodes.

- [ ] **Step 5: Run formatting and compile the common UI target.**

Run:

```bash
./gradlew :common:ui:desktopTest :common:ui:compileKotlinDesktop --no-daemon
```

Expected: BUILD SUCCESSFUL. Do not commit until Task 2 callers compile as well.

## Task 2: Apply the shared contract to every current card caller

**Files:**
- Modify: `feature/home/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/home/HomeScreen.kt`
- Modify: `feature/media/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/media/VideoListScreen.kt`
- Modify: `feature/favorite/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/favorite/FavoriteScreen.kt`
- Modify: `feature/search/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/search/SearchScreen.kt`
- Modify: `feature/detail/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/detail/DetailScreen.kt`
- Modify: `feature/home/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/home/HomeLatestByLibraryScreen.kt`
- Modify: `composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/App.kt`

- [ ] **Step 1: Pass state to every `LegacyVideoCard`.**

At each existing `LegacyVideoCard(item = item, ...)`, add:

```kotlin
isWatched = item.userData.isPlayed,
isFavorite = item.userData.isFavorite,
```

Do not duplicate hover UI in feature modules.

- [ ] **Step 2: Pass state to every `MediaCard`.**

At each `MediaCard` call, add the same two values. Keep callback wiring unchanged until Task 4 supplies the corrected mutation behavior.

- [ ] **Step 3: Add callbacks to the resume/next-up shelf API.**

Extend `HomeMediaShelf` with nullable callbacks:

```kotlin
onPlay: ((MediaItem) -> Unit)?,
onToggleWatched: ((MediaItem) -> Unit)?,
onToggleFavorite: ((MediaItem) -> Unit)?,
```

Inside each landscape poster `Box`, create the same `MutableInteractionSource` hover/focus state used by `MediaCard`, and render `MediaCardHoverOverlay` with the current item state. The shelf card remains landscape; only the overlay is shared.

- [ ] **Step 4: Build all shared callers.**

Run:

```bash
./gradlew :common:ui:compileKotlinDesktop :feature:home:compileKotlinDesktop :feature:media:compileKotlinDesktop :feature:favorite:compileKotlinDesktop :feature:search:compileKotlinDesktop :feature:detail:compileKotlinDesktop --no-daemon
```

Expected: BUILD SUCCESSFUL.

## Task 3: Move large-screen recent additions onto the home page

**Files:**
- Modify: `feature/home/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/home/HomeScreen.kt`
- Modify: `composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/App.kt`
- Modify: `feature/home/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/home/HomeLatestByLibraryScreen.kt` only if compilation requires its callback/state contract to be updated.

- [ ] **Step 1: Keep phone feed selection unchanged.**

Do not alter `selectedFeedKey`, `FloatingTabs`, `latestItems`, or the phone branch. The per-library shelves are only rendered when `layout.navigation` is `Sidebar` or `Rail`.

- [ ] **Step 2: Replace the wide global latest shelf with per-library shelves.**

In the `overview && !showFloatingTabs` branch, keep the resume and next-up shelves, then iterate `collections` in provider order. For each collection with a loaded or loading/error entry, call `HomeMediaShelf` using:

```kotlin
title = "最近添加 · ${collection.name}"
items = homeLatestByCollection[collection.id].orEmpty()
onItem = onItem
onPlay = onPlay
onToggleWatched = onToggleWatched
onToggleFavorite = onToggleFavorite
onOpenMore = { onBrowseCollection(collection) }
```

Use the existing loading/error/empty visual conventions; do not synthesize a merged `recentItems` row for wide home.

- [ ] **Step 3: Load all wide-home feeds, not the secondary recent route.**

In `BrowseContent`, change the existing `LaunchedEffect(currentRoute, state.collections)` condition so that it launches `browser.loadHomeLatest(collection.id)` when the current route is the home route and the layout is wide. Keep the existing selected-feed loading path for phone tabs. Avoid launching duplicate loads because `MediaBrowser.loadHomeLatest` already guards cached/loading IDs.

- [ ] **Step 4: Remove the wide-home “更多” jump to the unified latest page.**

The wide shelf’s more action must use `onBrowseCollection(collection)`. Leave the existing route only if another explicit caller still references it; do not add a new route or compatibility branch.

- [ ] **Step 5: Compile the home/app targets.**

Run:

```bash
./gradlew :feature:home:compileKotlinDesktop :composeApp:compileKotlinDesktop --no-daemon
```

Expected: BUILD SUCCESSFUL.

## Task 4: Make watched/favorite mutations per-media and immediately visible

**Files:**
- Modify: `feature/home/src/commonMain/kotlin/cn/mafei/cmp/feature/home/MediaBrowser.kt`
- Add: `feature/home/src/commonTest/kotlin/cn/mafei/cmp/feature/home/MediaBrowserUserDataActionTest.kt`

- [ ] **Step 1: Add action keys and a per-action lock.**

Add private types near the browser implementation:

```kotlin
private enum class UserDataAction { Watched, Favorite }
private data class UserDataActionKey(val id: MediaId, val action: UserDataAction)
```

Add a private `MutableSet<UserDataActionKey>` to `MediaBrowser`; it is only accessed by the browser’s serialized state dispatcher, matching the existing mutation methods.

- [ ] **Step 2: Implement optimistic list-item mutation.**

Replace the body of `toggleWatched(item)` and `toggleFavorite(item)` with a helper that:

1. Reads the current value from `state.value` when present, otherwise the passed item.
2. Computes the inverse target value.
3. Returns if the same `(mediaId, action)` is already locked.
4. Immediately applies only the changed field through `replaceUserData` to update all lists.
5. Calls `repository.setWatched` or `repository.setFavorite`.
6. On failure, restores only the changed field if that action is still locked, then exposes the existing safe error state.
7. Always removes the action key in `finally`.

Use a field-transform helper so concurrent watched and favorite operations cannot overwrite one another:

```kotlin
private fun MediaBrowserState.replaceUserData(
    id: MediaId,
    transform: (MediaUserData) -> MediaUserData,
): MediaBrowserState
```

Keep `mutateDetail` for detail-page actions, but do not use its `detailActionBusy` for list cards.

- [ ] **Step 3: Add focused fake-repository tests before running implementation tests.**

The test fixture must contain the same media ID in `items`, `homeFeed.latest`, `homeLatestByCollection`, `favoriteItems`, `searchItems`, and `historyItems`. Add tests for:

```kotlin
@Test fun favoriteUpdatesEveryPublishedCollectionImmediately()
@Test fun watchedUpdatesEveryPublishedCollectionImmediately()
@Test fun differentMediaActionsDoNotBlockEachOther()
@Test fun failedActionRollsBackOnlyItsField()
```

Each test should use a suspending fake repository with a controllable `CompletableDeferred<Unit>` so the assertion can observe the optimistic state before the request completes.

- [ ] **Step 4: Run the focused tests.**

Run:

```bash
./gradlew :feature:home:desktopTest --tests '*MediaBrowserUserDataActionTest' --no-daemon
```

Expected: all four tests pass.

## Task 5: Regression, platform compilation, and visual acceptance

**Files:**
- No new production files; only fix files from Tasks 1–4 if a regression is found.

- [ ] **Step 1: Run module tests.**

```bash
./gradlew :common:ui:desktopTest :feature:home:desktopTest :feature:media:desktopTest :feature:favorite:desktopTest :feature:search:desktopTest :feature:detail:desktopTest :composeApp:desktopTest --no-daemon
```

Expected: BUILD SUCCESSFUL with all existing and new tests passing.

- [ ] **Step 2: Compile all requested targets.**

```bash
./gradlew :composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinOhosArm64 --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Check the diff.**

```bash
git diff --check
git status --short
```

Expected: no whitespace errors; existing untracked build artifacts remain untouched.

- [ ] **Step 4: Verify desktop hover behavior.**

On the current desktop target, verify at least two cards in each of these sections: 继续观看, 接下来, one media-library latest shelf, a library list, and search. Confirm the overlay appears on every hovered card, the title turns primary, and selected watched/favorite states are visible.

- [ ] **Step 5: Verify the wide home layout.**

Resize the desktop window across the existing Sidebar/Rail breakpoints. Confirm per-library latest shelves stay on the home page, the phone Floating Tabs branch is untouched, and each shelf’s more action opens its corresponding library.

- [ ] **Step 6: Commit production changes.**

```bash
git add common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/MediaCard.kt \
  common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/LegacyMediaComponents.kt \
  feature/home/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/home/HomeScreen.kt \
  feature/home/src/commonMain/kotlin/cn/mafei/cmp/feature/home/MediaBrowser.kt \
  feature/home/src/commonTest/kotlin/cn/mafei/cmp/feature/home/MediaBrowserUserDataActionTest.kt \
  composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/App.kt
env GIT_AUTHOR_NAME='Codex' GIT_AUTHOR_EMAIL='codex@mafei.invalid' \
  GIT_COMMITTER_NAME='Codex' GIT_COMMITTER_EMAIL='codex@mafei.invalid' \
  git commit -m "fix(ui): unify media hover actions and home latest shelves" \
  -m $'Agent: Codex\nModel: gpt-6-sol\nReasoning: high'
```

