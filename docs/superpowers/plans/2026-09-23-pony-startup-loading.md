# Pony Startup Loading Animation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the startup linear progress bar with a branded, animated pony loading scene while preserving the existing runtime startup state machine.

**Architecture:** Keep startup state ownership in `App`; add one file-private `PonyStartupLoading` composable in the same shared UI source file. It will reuse `MaFeiLegacyDrawables.appIcon`, draw a small non-glass track, and drive translation/bob/scale from remembered Compose infinite transitions. Delay the existing page handoff briefly only when startup completes so the pony can fade out.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform shared UI, Compose animation-core, Compose Canvas, existing MaFei theme and drawable resources.

---

### Task 1: Add the startup pony component

**Files:**
- Modify: `composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/App.kt` imports and private composables near `StaticAppCanvas`.

- [ ] **Step 1: Add animation and drawing imports** for `Canvas`, `graphicsLayer`, `LocalDensity`, `animateFloat`, `infiniteRepeatable`, `LinearEasing`, `RepeatMode`, and `rememberInfiniteTransition`, retaining existing imports.

- [ ] **Step 2: Implement `PonyStartupLoading(exiting: Boolean)`** with:
  - `rememberInfiniteTransition(label = "pony-startup")` for a reversible 1.4 second horizontal motion and a phase-shifted bob/scale.
  - A `Canvas` rail with a low-alpha rounded track and moving highlight dot.
  - `Image(painterResource(MaFeiLegacyDrawables.appIcon))` sized for the transparent logo bounds, positioned with `graphicsLayer` using density-converted translation.
  - `animateFloatAsState` or a transition-controlled alpha/translation when `exiting` is true, so the pony leaves smoothly.
  - Existing text `正在启动…`, centered below the animation and no `LinearProgressIndicator`.

- [ ] **Step 3: Replace only the startup call site** in `App`: keep `showStartupProgress` and render `PonyStartupLoading` instead of `SessionMessage(..., busy = true)`; leave other `SessionMessage` usages unchanged.

### Task 2: Preserve a short completion exit

**Files:**
- Modify: `composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/App.kt` startup state variables and `LaunchedEffect(runtime)`.

- [ ] **Step 1: Add a runtime-scoped `startupExiting` state initialized to `false`.

- [ ] **Step 2: In the existing `finally`, set `startupExiting = true`, delay 220ms, then set `starting = false`; rethrow cancellation as currently done so cancellation does not alter error behavior.

- [ ] **Step 3: Pass `startupExiting` to `PonyStartupLoading`; ensure the startup canvas still switches to the existing signed-out/ready/error branches after the delay.

### Task 3: Validate the focused change

**Files:**
- No new test source required; validation targets existing shared UI and startup tests.

- [ ] **Step 1: Run the focused Compose app desktop test suite or compile task available in the repository:** `./gradlew :composeApp:compileKotlinDesktop`.

- [ ] **Step 2: Run the startup regression tests:** `./gradlew :composeApp:jvmTest --tests 'cn.mafei.cmp.session.MigrationStartupTest'`.

- [ ] **Step 3: Inspect the diff and status**, confirming only `App.kt` and the design/plan docs are staged or modified, and that existing untracked user files remain untouched.

- [ ] **Step 4: Record visual verification expectations** for phone and wide layouts, including initial static frame, looping movement, and exit fade; report if a simulator is unavailable.
