# Stage 1：窗口切换滚动锚点与真实鼠标验收

日期：2026-09-07。接续 [特效熔断记录](2026-09-07-glass-fallback.md)。

## 复现与修复

旧 Probe 在 430×760 单栏滚到样本 11–12 后，调整为 1100×800 双栏，列表跳回顶部；材质和命中计数仍保留。

本轮仅修改 `FoundationProbeScreen.kt`：将 `rememberLazyListState` 提升到布局分支外，两种布局共用同一状态；控制区、对照区、样本使用稳定 item key，避免窄屏控制区移出列表时按索引错配。没有修改命中策略、Provider、Data、Player、共享 Gradle 或 Harmony 宿主。

使用 adaptive 技能指导窗口变化时的状态保持验证；使用 harmony-kmp-cmp 技能区分共享编译与宿主/真机验收。未新增导航或测试框架依赖。

## 构建证据（B）

隔离快照 `/private/tmp/mafei-window-anchor.iwlKWu`：基于 `82e8700` 加本轮 Probe 修改，不含 GLM 同期修复。日志 `/private/tmp/mafei-window-anchor-build.log`，26 秒成功：

```text
:composeApp:assembleDebug
:composeApp:createDistributable
:composeApp:compileKotlinIosArm64
:composeApp:compileKotlinIosSimulatorArm64
:composeApp:compileKotlinOhosArm64
:composeApp:desktopTest                  1 项，0 失败，本轮执行
:common:glass:desktopTest                6 项，0 失败，FROM-CACHE
:common:design-system:desktopTest        3 项，0 失败，FROM-CACHE
verifyArchitecture                      UP-TO-DATE
```

这不是 Compose UI 自动化单测；滚动回归证据来自下述新包运行。仍有既有编译器参数警告。本轮没有重新链接 OHOS 动态库或打包 HAP。

## Mac ARM 运行证据（E）

启动并核对进程路径：`/private/tmp/mafei-window-anchor.iwlKWu/composeApp/build/compose/binaries/main/app/MaFei.app/Contents/MacOS/MaFei`，取证 PID 89315。旧同名探针退出后按 PID 查询 UI 树。

- 430×760：原生滚轮可滚到列表末尾，样本 12 在底栏上方完整显示。
- 从末尾调整为 1100×800：仍在样本 10–12 附近，没有回到顶部。
- 再调整回 430×760：仍在样本 10–12 附近，材质选择、命中计数保留。
- 宽度改变会改变条目高度，因此不承诺逐像素偏移相同，也不承诺调整后仍恰好位于列表末端；需要继续滚动才能再次到底。
- 对新包发送 CoreGraphics 原生鼠标 move/down/up：Full 窄屏计数 0→1；Reduced 窄屏 1→2；Opaque 宽屏 2→3，回到窄屏仍为 3。
- 旧熔断探针同样补验原生鼠标：实体回退后宽屏 1→2、窄屏 2→3。该项只作为上轮熔断证据补充，不冒充新包故障注入。

所有点击坐标均先由实时 AX 布局树获取真实 bounds，再取中心。上轮 System Events `click at` 返回底层列表节点的现象未作为产品 Bug；本轮真实鼠标路径成功，不需要修改产品命中逻辑。未用截图比例估算点击位置。

证据（临时目录，非永久归档）：

- `/private/tmp/mafei-anchor-narrow-tree.log`
- `/private/tmp/mafei-anchor-wide-tree.log`
- `/private/tmp/mafei-anchor-opaque-tree.log`
- `/private/tmp/mafei-anchor-return-tree.log`
- `/private/tmp/mafei-window-anchor-before.png`：旧包调整窗口后回到顶部。
- `/private/tmp/mafei-anchor-narrow-end.png`：新包窄屏末端。
- `/private/tmp/mafei-anchor-wide-after.png`：新包双栏保持样本位置。
- `/private/tmp/mafei-anchor-return-narrow.png`：返回窄屏保持样本位置和计数。

## 验收边界

此次通过的是 Mac 样本窗口的功能滚动、原生鼠标命中及布局切换状态保持；没有测量 FPS、帧时间或全尺寸覆盖率。Android/iOS/Harmony 运行、Harmony 真机窗口/旋转/进程恢复、完整键盘焦点和 Player Surface 仍 Pending。Stage 1 未整体通过，不启动完整 Home 迁移，也不据此冻结 GLM 工作包。
