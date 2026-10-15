# 应用架构与修改入口

本文描述 App 入口重构后的当前结构。全局设计、交互、Git 和持续执行规则以根 `AGENTS.md` 为准。
目标是让一次需求只触及明确的状态所有者、路由绑定和已有展示组件，并由行为测试约束结果。

## 从需求定位实现

以下共享 UI 路径以 `src/sharedUiMain/kotlin/cn/mafei/cmp/` 为基准。

| 要改变的行为 | 首先查看 | 边界 |
| --- | --- | --- |
| 根流程选择、主题、平台回调 | `App.kt` | 只装配，不添加页面、路由栈或业务请求 |
| 启动、恢复失败、退出重试 | `startup/ApplicationStartup.kt` | 应用级生命周期 |
| 玻璃强度、轮播、渲染方式偏好保存 | `startup/ApplicationPreferences.kt` | 保存任务不随设置页退出而取消 |
| 未登录的账号历史、删除与预填 | `account/SignedOutAccountState.kt` | `AccountAccess` 是唯一账号访问边界 |
| 已登录的账号切换、连接、账号子栈 | `account/AccountFlowState.kt` | 跟随账号会话销毁 |
| 登录页和历史双栏布局 | `account/SignedOutFlow.kt` | 只绑定状态、事件和布局 |
| 账号浮层、全屏宿主 | `account/AccountSurface.kt` | 与内容栈独立，响应尺寸变化 |
| 设置、连接策略、管理、网页入口 | `settings/*EntryBinding.kt`、`account/*EntryBinding.kt` | 显式能力、Store、事件；没有运行时服务定位器 |
| 路由参数、来源、壳层选中项 | `navigation/BrowseRoutes.kt`、`BrowseShellMapping.kt` | 类型与纯映射，不访问数据 |
| 浏览返回、季快照恢复、详情预取 | `navigation/BrowseNavigator.kt` | 命令操作 `BrowseNavigationState`，不拥有页面 |
| 搜索取消、提交历史 | `browse/BrowseSearchState.kt` | 仅成功提交记录历史；替代请求取消前一个 |
| 选择与删除确认 | `browse/BrowseSelectionState.kt`、`BrowseDeleteConfirmation.kt` | 多入口使用同一待删状态 |
| 页面绑定 | `browse/entries/*EntryBinding.kt` | Feature 提供展示，绑定提供状态、事件与导航 |
| 浏览会话装配 | `browse/BrowseApplication.kt` | 创建所有者、注册路由、连接壳层；不写请求算法或 Feature 页面 |
| 数据加载的触发与生命周期 | `browse/BrowseDataLifecycle.kt`、`BrowseCatalogs.kt` | 在动画路由之上持有，避免转场重建请求与缓存 |
| 起播、队列、清晰度意图 | `playback/BrowsePlayerState.kt` | 账号级播放意图，不持有浏览/账号导航栈 |
| 全屏、宿主回调、自动下一集 | `playback/PlayerLifecycle.kt` | 进度与字幕变化不通知整个浏览壳层 |
| 完整播放器和 Mini Player | `playback/BrowsePlayerHost.kt`、`BrowsePlayerOverlay.kt` | 复用已有 Player UI 与原生 Surface |

浏览数据实现位于 `feature/home/src/commonMain/kotlin/cn/mafei/cmp/feature/home/`：
`MediaBrowser.kt` 是兼容门面，`browser/` 内 8 个控制器分别拥有目录、搜索、收藏、详情、播放选择、
人物、历史和媒体操作。具体职责与状态归属见该目录的 [README](../feature/home/src/commonMain/kotlin/cn/mafei/cmp/feature/home/browser/README.md)。

## 依赖与状态生命周期

```text
平台入口 → App → 启动 / 未登录 / BrowseApplication
                                  ├─ 账号状态 → 账号与设置绑定 → Feature UI
                                  ├─ 导航状态/命令 → 浏览绑定 → Feature UI
                                  ├─ 浏览控制器 → MediaRepository → Adapter → Provider
                                  └─ 播放意图/生命周期 → SessionPlaybackRuntime → PlayerController
```

- App 仍在 `key(session)` 内装配浏览流程。切换账号替换所有浏览、账号和播放意图对象；窗口大小变化不重建它们。
- `BrowseNavigationState` 持有浏览栈和初始媒体快照；账号栈留在 `AccountFlowState`。播放器呈现不清空浏览栈。
- 首页网格保持原来的会话内状态，其余列表保留原来的 saveable 状态；最近媒体库入口继续按 server/account 保存。
- `BrowserContext` 只有一份可观察媒体读模型和账号认证错误通道。请求序号、分页累加器、历史补全缓存、季快照归各控制器。
- 收藏、已观看和删除经 `BrowserMediaFacts` 更新所有已展示副本；控制器之间只传递必要事实或只读回调，不互相持有实例。
- 页面转场可能同时保留前后两页。账号根动作继续检查当前账号路由；返回详情时继续延迟清理旧文档并校验栈，季/集恢复保持原有顺序。
- 账号凭据由现有 `ApplicationSessions` 与存储边界管理，不进入导航参数。取消异常继续传播，不把取消显示成网络错误。
- 壳层仅订阅播放器 phase/resource 等路由相关信息。播放进度由播放器视图自己订阅；画中画与原生 Surface 的既有工作区改动保持单独归属。

绑定函数参数较多时应先判断是否存在清晰的同领域接口。不得引入一个包含所有 Runtime、导航、页面与播放器变量的全局上下文来缩短签名。

## 新增需求的执行方式

1. 写清进入条件、用户事件、预期页面/数据变化、错误与返回行为；明确不变项。视觉目标按根 `AGENTS.md` 的参考和验收规则处理。
2. 在上表定位唯一状态所有者。复用 Feature UI 和共享组件；业务规则放在控制器/Repository，绑定只连接数据与事件。
3. 新路由保持类型化参数，补全纯壳层映射；现有序列化类型名和 package 不因目录迁移改变。
4. 测试可观察行为：请求竞争、取消、失败回滚、跨列表同步、账号切换、返回恢复。编译检查不能代替这些行为，也不能代替视觉验收。
5. 执行对应平台编译与下方门禁。独立变更验证后提交；完整计划还有剩余项时继续执行。

## 自动门禁与验证

`verifyAppBoundaries` 已接入 `verifyArchitecture`，后者也属于根 `check`。
门禁拒绝根入口混入页面/请求、纯路由依赖运行时、EntryBinding 获取整个 Runtime、
浏览门面重新加入请求实现，以及控制器另建可观察媒体副本或互相持有具体实例。
入口与控制器另有代码规模预算，辅助发现职责集中；预算不能替代依赖和行为审查。

```bash
./gradlew verifyAppBoundaries verifyDependencyBoundaries verifyArchitecture
./gradlew :composeApp:desktopTest :composeApp:testDebugUnitTest
./gradlew :composeApp:compileKotlinIosArm64 :composeApp:compileKotlinOhosArm64
```

定向回归入口：`BrowseShellMappingTest`、`SignedOutAccountStateTest`、`BrowseSearchStateTest`、
`MediaBrowserTest`、`PlayerShellObservationTest`、`ApplicationRuntimePlaybackTest`、`PlayerPreferencesTest`。
边界脚本本身的负例测试使用 `node --test scripts/verify-app-boundaries.test.mjs`。

本次范围是行为保持的结构重构，不是视觉重做、发布或平台升级。源码/自动测试、设备运行与视觉验收要分开报告。
已发现的旧宽屏二级页收起态展示与根导航契约的差异见 [导航文档](src/sharedUiMain/kotlin/cn/mafei/cmp/navigation/README.md)，
该既有展示差异不在本次结构迁移中修正或宣称验收。

## 本轮验证记录（2026-10-15）

- 重构及验收修复已形成独立提交。`App.kt` 为 130 行，`MediaBrowser.kt` 为 102 行。
- `currentGlassStyle()` 与已有播放器测试使用的 `GlassStyleScope` 已分别通过 `a762a5c`、`9faeca3` 纳入提交，独立检出不再依赖临时补丁。
- 纯提交源码通过应用 Desktop 211 项、Android JVM 192 项测试、iOS/OHOS Kotlin 编译与架构门禁；播放器触摸回归 16 项、首页反馈回归 5 项通过。
- 设备回归发现并修复播放器返回按钮被顶部悬停层遮挡的问题；原有追剧关闭开关已纳入提交，首页普通连接错误改为标准提示框。
- 最终安装包来自包含原有未提交改动的完整工作区。纯提交验证与完整工作区安装验证是两种证据，不能互相替代。不得用隔离基线包覆盖用户工作版本后，遗漏恢复完整工作区包。
- 构建、设备检查、片源限制和安装包指纹见 [重构验收记录](REFACTOR_ACCEPTANCE.md)。本轮未发布或推送远端，不声明全平台视觉验收完成。
