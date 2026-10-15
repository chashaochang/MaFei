# 手机端性能与连续动效 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有业务和页面信息架构的前提下，改善手机端列表性能，并为“卡片 → 详情 → 返回”接入可降级的共享海报转场。

**Architecture:** Navigation 3 的 `NavDisplay` 在外层建立一个 `SharedTransitionLayout`，通过 `sharedTransitionScope` 连接同一回退栈中的入口卡片和详情 Hero。共享海报只负责媒体封面边界，详情正文仍由现有页面动画负责；不支持共享元素的平台继续使用现有导航动画。性能修改集中在稳定 key、派生滚动状态、图片候选和缓存参数，不新增全屏采样或列表玻璃层。

**Tech Stack:** Kotlin 2.2、Compose Multiplatform 1.9、Navigation 3、`SharedTransitionScope`、`LazyVerticalGrid`、现有 `ArtworkMemoryCache`、Kotlin test/Compose desktop test。

---

### Task 1: 建立共享转场边界与降级入口

**Files:**
- Modify: `composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/App.kt:2030-2060`
- Modify: `feature/home/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/home/HomeScreen.kt:210-250`
- Modify: `feature/detail/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/detail/DetailScreen.kt:150-205`

- [ ] **Step 1: 先确认当前 NavDisplay 转场测试基线**

Run:

```bash
./gradlew :composeApp:desktopTest --no-daemon --offline --console=plain
```

Expected: `BUILD SUCCESSFUL`，记录为共享元素改动前基线。

- [ ] **Step 2: 在 BrowseContent 的 NavDisplay 外层建立共享作用域**

在现有 `NavDisplay` 外包一层 `SharedTransitionLayout`，并将作用域传入 Navigation 3：

```kotlin
SharedTransitionLayout(Modifier.fillMaxSize()) {
    val sharedScope = this
    NavDisplay(
        backStack = browseBackStack,
        sharedTransitionScope = sharedScope,
        onBack = { popBrowseRoute() },
        transitionSpec = existingForwardTransitionSpec,
        popTransitionSpec = existingPopTransitionSpec,
        predictivePopTransitionSpec = existingPredictivePopTransitionSpec,
        entryProvider = existingBrowseEntryProvider,
    )
}
```

这里的四个 `existing...` 名称表示当前 `BrowseContent` 中已经存在的同名 lambda：实现时直接保留原 lambda 体，只在 `BrowseHomeRoute`、`BrowseLibraryRoute`（如其内容使用列表入口）和 `BrowseDetailRoute` 的页面调用处传入 `sharedScope`，不能新建第二套路由表。

`SignedOutFlow` 和 `mineNavContent` 不参与第一批共享元素，避免把账号页和媒体详情共用一套作用域。

- [ ] **Step 3: 给 HomeScreen 和 DetailScreen 增加可选 shared scope 参数**

保持默认值为 null，确保不在 Navigation 3 作用域内的调用点仍可编译：

```kotlin
@OptIn(ExperimentalSharedTransitionApi::class)
sharedTransitionScope: SharedTransitionScope? = null,
```

同时保留现有 `artwork`、`backdropArtwork`、`onItem` 和详情数据参数，不改变业务回调。

- [ ] **Step 4: 编译验证边界改动**

Run:

```bash
./gradlew :composeApp:compileKotlinDesktop :feature:home:compileKotlinDesktop :feature:detail:compileKotlinDesktop --no-daemon --offline --console=plain
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/App.kt \
  feature/home/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/home/HomeScreen.kt \
  feature/detail/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/detail/DetailScreen.kt
GIT_AUTHOR_NAME='Codex' GIT_AUTHOR_EMAIL='codex@mafei.invalid' \
GIT_COMMITTER_NAME='Codex' GIT_COMMITTER_EMAIL='codex@mafei.invalid' \
git commit --author='Codex <codex@mafei.invalid>' \
  -m 'feat(motion): add navigation shared transition boundary' \
  -m 'Agent: Codex\nModel: gpt-6-sol\nReasoning: high'
```

### Task 2: 让首页/媒体库卡片与详情 Hero 共享海报

**Files:**
- Modify: `feature/home/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/home/HomeScreen.kt:2200-2245`
- Modify: `feature/detail/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/detail/DetailScreen.kt:581-640`
- Modify: `common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/LegacyMediaComponents.kt:45-120`

- [ ] **Step 1: 定义稳定媒体 key**

在 `common/ui` 增加一个纯函数，所有入口使用同一格式，避免把标题或 URL 当作共享元素身份：

```kotlin
fun mediaArtworkTransitionKey(mediaId: String): String = "mafei-media-artwork:$mediaId"
```

媒体 ID 为空时不启用共享元素，不能产生多个空 key。

- [ ] **Step 2: 为卡片海报增加可选共享元素修饰符**

将 `LegacyVideoCard` 增加可选参数，不改变现有默认外观：

```kotlin
sharedArtworkModifier: (Modifier) -> Modifier = { it },
```

将它只应用于海报内容的最外层，并保持现有 `clip(RoundedCornerShape(6.dp))` 在共享修饰符之后，确保 overlay 期间仍有圆角裁剪。

- [ ] **Step 3: 在 HomeScreen 的卡片入口传入 shared element**

在 `SharedTransitionScope` 和 `LocalNavAnimatedContentScope` 均可用、且媒体 ID 非空时：

```kotlin
val transitionModifier = with(sharedScope) {
    Modifier.sharedElement(
        rememberSharedContentState(mediaArtworkTransitionKey(item.id.value)),
        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
    )
}
```

若 scope 为 null，则传 `Modifier`，不改变普通卡片。

- [ ] **Step 4: 在 DetailHero 传入相同 key**

给 `DetailScreen` 的 `DetailHero` 增加 `sharedArtworkTransitionModifier` 参数；对 `artwork(item, artworkModifier.then(sharedModifier))` 使用同一媒体 key。Hero 只共享图片，不共享渐变蒙层、标题、Logo 或播放按钮。

- [ ] **Step 5: 加入显式边界动画**

共享元素只做封面连续移动；详情正文沿用当前 `AnimatedContent`。共享元素不可用或没有匹配入口时，保持当前 `fadeIn + slideInVertically`，不得让空 scope 访问 `LocalNavAnimatedContentScope`。

- [ ] **Step 6: 运行测试并提交**

```bash
./gradlew :common:ui:desktopTest :feature:home:desktopTest :feature:detail:desktopTest :composeApp:desktopTest --no-daemon --offline --console=plain
git diff --check
```

Expected: 所有任务成功；提交使用：

```bash
git add common/ui/src/sharedUiMain/kotlin/cn/mafei/cmp/common/ui/LegacyMediaComponents.kt \
  feature/home/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/home/HomeScreen.kt \
  feature/detail/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/detail/DetailScreen.kt
GIT_AUTHOR_NAME='Codex' GIT_AUTHOR_EMAIL='codex@mafei.invalid' \
GIT_COMMITTER_NAME='Codex' GIT_COMMITTER_EMAIL='codex@mafei.invalid' \
git commit --author='Codex <codex@mafei.invalid>' \
  -m 'feat(motion): animate media artwork into detail hero' \
  -m 'Agent: Codex\nModel: gpt-6-sol\nReasoning: high'
```

### Task 3: 降低手机滚动和异步加载的重组成本

**Files:**
- Modify: `common/media/src/sharedUiMain/kotlin/cn/mafei/cmp/common/media/MediaArtwork.kt:246-290`
- Modify: `feature/home/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/home/HomeScreen.kt:330-380, 600-700`
- Modify: `feature/detail/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/detail/DetailScreen.kt:190-320`

- [ ] **Step 1: 保持图片候选和目标尺寸稳定**

确认候选列表只在 `images` 改变时重建、目标尺寸按 bucket 记忆；禁止在每个滚动帧重新调用网络或解码。已有 `ArtworkMemoryCache` 不改缓存语义，只补稳定参数或避免重复 `BoxWithConstraints` 测量。

- [ ] **Step 2: 把滚动驱动值限制在窄重组范围**

对首页 sticky/floating tabs 和详情 Hero collapse 使用 `derivedStateOf` 或 `snapshotFlow`，避免把滚动值读取提升到页面根节点；逐帧几何变化继续放在 `graphicsLayer`，不触发整页重组。

- [ ] **Step 3: 异步内容保持旧内容直到新内容可用**

详情刷新时不把已显示 `item` 清成 null；只让 `loading` 控制局部进度线和新段落的 `DetailAnimatedVisibility`。旧请求取消后不得继续发布图片或段落动画。

- [ ] **Step 4: 运行针对性测试**

```bash
./gradlew :common:media:desktopTest :feature:home:desktopTest :feature:detail:desktopTest :composeApp:desktopTest --no-daemon --offline --console=plain
```

Expected: `BUILD SUCCESSFUL`；记录首页快速滚动、详情刷新和返回位置无回归后提交：

```bash
git add common/media/src/sharedUiMain/kotlin/cn/mafei/cmp/common/media/MediaArtwork.kt \
  feature/home/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/home/HomeScreen.kt \
  feature/detail/src/sharedUiMain/kotlin/cn/mafei/cmp/feature/detail/DetailScreen.kt
GIT_AUTHOR_NAME='Codex' GIT_AUTHOR_EMAIL='codex@mafei.invalid' \
GIT_COMMITTER_NAME='Codex' GIT_COMMITTER_EMAIL='codex@mafei.invalid' \
git commit --author='Codex <codex@mafei.invalid>' \
  -m 'perf(ui): reduce mobile scroll and async recomposition work' \
  -m 'Agent: Codex\nModel: gpt-6-sol\nReasoning: high'
```

### Task 4: 端到端回归与共享元素回退

**Files:**
- Modify: `composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/App.kt` only when a platform compile/runtime check demonstrates that the shared scope needs a guard
- Test: existing desktop tests plus Android compile/test tasks

- [ ] **Step 1: 验证普通导航仍可用**

```bash
./gradlew :composeApp:desktopTest :composeApp:testDebugUnitTest --no-daemon --offline --console=plain
```

- [ ] **Step 2: 验证平台编译**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosArm64 :composeApp:compileKotlinOhosArm64 --no-daemon --offline --console=plain
```

若某个平台无法编译共享元素 API，必须在该平台的同一处 scope 入口降级为空，并保留普通 NavDisplay 动画；不得复制一份平台专属页面。

- [ ] **Step 3: 做布局树验收**

使用自动化工具前先读取目标控件的真实 bounds；验收前进、返回、快速连续点卡片、列表位置和加载态。不得以全桌面截图代替应用窗口或布局树检查。

- [ ] **Step 4: 完成最终检查并提交平台降级修正（仅在 Step 2/3 发现问题时）**

```bash
git diff --check
git status --short
```

只提交本任务明确修改的路径，并确认：

```bash
git log -1 --format='%h Author: %an <%ae> Committer: %cn%n%B'
```
