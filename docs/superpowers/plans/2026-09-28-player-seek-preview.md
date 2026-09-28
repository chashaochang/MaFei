# 播放器进度预览 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore real Jellyfin Trickplay frame previews while dragging the shared CMP seek bar, with a safe no-preview fallback.

**Architecture:** Provider adapters own metadata lookup, authentication, tile URL construction and sheet caching. The repository forwards the account-scoped call. App maps provider bytes to a player API preview value; player UI debounces requests and crops the decoded sheet above the existing seek feedback.

**Tech Stack:** Kotlin Multiplatform, kotlinx.serialization, Compose Multiplatform, existing `HttpTransport`, Jellyfin 10.10 Trickplay endpoint.

---

### Task 1: Freeze preview contracts and math

**Files:**
- Modify: `data/adapter-contract/src/commonMain/kotlin/cn/mafei/cmp/data/adapter_contract/MediaProviderAdapter.kt`
- Create: `data/adapter-contract/src/commonMain/kotlin/cn/mafei/cmp/data/adapter_contract/PlaybackPreview.kt`
- Create: `data/adapter-contract/src/commonTest/kotlin/cn/mafei/cmp/data/adapter_contract/PlaybackPreviewTest.kt`

- [ ] Add `PlaybackPreviewFrame` and a default nullable `previewFrame` method.
- [ ] Add pure `PlaybackPreviewMath.locate` with interval/tile-width/tile-height/count validation.
- [ ] Test first/last frame, sheet rollover, clamping, invalid metadata and negative position.
- [ ] Run `:data:adapter-contract:desktopTest` and `:data:adapter-contract:testDebugUnitTest`.

### Task 2: Add Jellyfin Trickplay wire and transport support

**Files:**
- Modify: `providers/jellyfin/wire/src/commonMain/kotlin/cn/mafei/cmp/providers/jellyfin/wire/ItemDtos.kt`
- Modify: `providers/jellyfin/wire/src/commonMain/kotlin/cn/mafei/cmp/providers/jellyfin/wire/WireEnums.kt`
- Modify: `providers/jellyfin/transport/src/commonMain/kotlin/cn/mafei/cmp/providers/jellyfin/transport/JellyfinRequests.kt`
- Modify: `providers/jellyfin/catalog/src/commonMain/kotlin/cn/mafei/cmp/providers/jellyfin/catalog/JellyfinCatalogApi.kt`
- Modify: `providers/jellyfin/catalog/src/commonMain/kotlin/cn/mafei/cmp/providers/jellyfin/catalog/JellyfinCatalogApi.kt`
- Modify: `providers/jellyfin/transport/src/commonTest/kotlin/cn/mafei/cmp/providers/jellyfin/transport/JellyfinRequestsTest.kt`

- [ ] Model `Trickplay` as source-id → width → info (`Width`, `Height`, `Interval`, `ThumbnailCount`, `TileWidth`, `TileHeight`).
- [ ] Add `ItemField.Trickplay` and include it in the playback/detail field set where item metadata is fetched.
- [ ] Build `GET /Videos/{itemId}/Trickplay/{width}/{index}.jpg?MediaSourceId=...` against the configured origin.
- [ ] Test exact path/query encoding and `fields=Trickplay`.

### Task 3: Implement Jellyfin adapter and repository forwarding

**Files:**
- Modify: `data/adapter-jellyfin/src/commonMain/kotlin/cn/mafei/cmp/data/adapter_jellyfin/JellyfinMediaProviderAdapter.kt`
- Modify: `data/repositories/src/commonMain/kotlin/cn/mafei/cmp/data/repositories/MediaRepository.kt`
- Modify: relevant adapter/repository test fakes and tests.

- [ ] Cache Trickplay manifests and tile sheets by item/source/width/index.
- [ ] Use the prepared source id when available; otherwise choose the first usable source manifest.
- [ ] Fetch bytes through `facade.client.execute`, preserving cancellation and translating server errors through the existing Jellyfin adapter path.
- [ ] Return null for missing/invalid metadata or 404 and never expose a raw stream URL.
- [ ] Test cache hit, sheet coordinate, missing manifest and failed tile fallback.

### Task 4: Connect player API, App and CMP seek feedback

**Files:**
- Modify: `player/api/src/commonMain/kotlin/cn/mafei/cmp/player/api/PlayerApi.kt`
- Modify: `player/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/player/ui/NowPlayingScreen.kt`
- Modify: `composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/App.kt`

- [ ] Add a Provider-free `PlayerSeekPreviewFrame` carrying encoded tile bytes and crop rectangle.
- [ ] Pass an optional suspend preview loader through `BoundNowPlayingScreen` to `NowPlayingScreen`.
- [ ] During drag, debounce and cancel stale requests; decode off the UI thread and crop the current tile above the time feedback.
- [ ] Clear preview on release and keep the existing feedback when loader returns null.
- [ ] Test pure UI state helpers and compile Desktop/Android/OHOS.

### Task 5: Verification and commit

- [ ] Run targeted adapter/player tests, `git diff --check`, `:composeApp:compileKotlinDesktop`, `:composeApp:compileDebugKotlinAndroid`, and `:composeApp:compileKotlinOhosArm64`.
- [ ] Review the diff for no Material preview container, no token-bearing URL persistence, and no changes under `legacy/`.
- [ ] Commit only preview files with Codex identity and required Agent/Model/Reasoning trailers.
