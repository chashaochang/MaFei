# Hover Glass and Person Cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make hover feedback on liquid-glass buttons visibly responsive and give cast/person cards the same dark overlay and blue-title affordance as media cards.

**Architecture:** Keep the existing glass material and interaction pipeline. Adjust the shared hover visual once in the glass button component, and add a reusable person-card hover layer in the detail UI so every cast entry follows one contract without changing navigation or data loading.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, MaFei vendor Liquid Glass, existing `mafeiInteractive` interaction modifier.

---

### Task 1: Strengthen shared LiquidButton hover feedback

**Files:**
- Inspect/modify: `common/glass/src/sharedUiMain/kotlin/cn/mafei/cmp/common/glass/components/LiquidButton.kt`
- Test/build: shared glass and desktop UI compilation

- [ ] Locate the existing pointer-hover/focus visual state and preserve its pressed, drag, backdrop, lens, blur, and hit-target behavior.
- [ ] Add only a restrained hover elevation: a visible but low-alpha highlight plus a small uniform scale while hovered/focused, using the component's existing animation state rather than a second gesture system.
- [ ] Keep idle appearance unchanged and do not add a white opaque surface or Material button.
- [ ] Compile `:common:glass:compileKotlinDesktop` and run `:common:ui:desktopTest`.

### Task 2: Add shared person-card hover treatment

**Files:**
- Inspect/modify: `feature/detail/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/detail/DetailScreen.kt`
- Inspect/modify if the section is shared by season detail: `feature/detail/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/detail/SeasonDetailScreen.kt`

- [ ] Reuse the existing person-card interaction source and click callback.
- [ ] While hovered/focused, draw a black translucent overlay clipped to the avatar shape and render the person name in the card's existing text area with `MaterialTheme.colorScheme.primary`.
- [ ] Keep the entire card clickable to `onPersonSelected`; do not invent a separate action button or change the person data route.
- [ ] Keep non-hover layout, avatar size, spacing, and typography unchanged.

### Task 3: Verify and commit

**Files:**
- No additional production files.

- [ ] Run `git diff --check`.
- [ ] Run `./gradlew :common:glass:compileKotlinDesktop :feature:detail:compileKotlinDesktop :common:ui:desktopTest :feature:home:desktopTest --no-daemon`.
- [ ] Run Android Debug and Harmony OHOS Arm64 compilation if the shared component has no platform-specific branch.
- [ ] Commit with Codex identity and body metadata:
  `fix(ui): improve hover feedback for glass and person cards`

