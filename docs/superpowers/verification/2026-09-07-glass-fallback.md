# Stage 1：Glass 特效熔断与 Mac 交互分项验收

日期：2026-09-07。接续 `2026-09-07-glass-components.md`。

UI 实现提交：`9360e6e`。GLM 同期的 `de01b62` 已保留，但不属于本轮 UI 隔离构建验收范围。

## 实现范围

- `common:glass` 新增 `GlassEffectGuard`：可捕获的 blur/lens 创建或配置异常触发一次性熔断，后续不再调用特效构造。取消和 fatal Error 原样抛出，不吞掉业务错误。
- `GlassRenderHealth` 由 Shell/window 持有，非进程全局黑名单；同一窗口内的卡片和底栏共享故障状态。没有环境提供者的独立 GlassSurface 也有本地回退。
- 失败时清除本次尚未完成的 RenderEffect/padding，同帧 tint 使用 alpha=1；重组后改为实体背景分支。形状、布局、content 及事件处理没有替换。
- 主题、材质档位、减少透明度偏好不负责清除熔断；新的渲染生命周期有独立状态。
- Probe 增加“模拟特效熔断”按钮及证据边界说明。按钮上报故障信号，不故意让生产 GPU 崩溃。
- 没有修改 vendor、共享 Gradle、Provider、Data、Player 或平台宿主；GLM 正在修改的飞牛文件保持原状。

本轮使用 harmony-kmp-cmp 技能约束 CPF/Skia 与薄宿主边界，验证在隔离快照完成，未更换后端或依赖坐标。

## TDD / 构建（B）

快照：`/private/tmp/mafei-glass-fallback.PLA1ev`，基于 e203f5e + 本轮五个 UI 源码/测试文件，不包含 GLM 同期未提交修复。

先增加 4 项 guard 测试；首次运行因 GlassEffectGuard 尚不存在而编译失败（红灯），实现后联合验证 29 秒通过：

```text
:common:glass:desktopTest                   6 项，0 失败（4 新 + 2 既有）
:common:glass:testDebugUnitTest             6 项，0 失败
:common:design-system:desktopTest           3 项，0 失败
:composeApp:assembleDebug
:composeApp:createDistributable
:composeApp:compileKotlinIosArm64
:composeApp:compileKotlinIosSimulatorArm64
:composeApp:compileKotlinOhosArm64
verifyArchitecture
```

日志：`/private/tmp/mafei-glass-fallback-red.log`、`/private/tmp/mafei-glass-fallback-green.log`。

本轮未重链/发布 libkn.so、未重打 HAP；OHOS 证据限于 KLIB 编译，不能沿用上轮 HAP 冒充本轮新包。

## Mac 运行分项（E）

启动本轮产物：

`/private/tmp/mafei-glass-fallback.PLA1ev/composeApp/build/compose/binaries/main/app/MaFei.app`

`file` 确认 launcher 为 Mach-O arm64，进程路径核对为上述新包。最初旧、新包同名导致辅助功能选择歧义，关闭已验证路径的旧探针进程后，仅使用新包取证；早期旧窗口输出不用于本轮验收。

已观察：

1. 800dp 单栏首帧 → 将窗口设置为 1100×800 后显示 `1100 dp · 双栏`，控制区与内容分列。
2. 故障前 Full/Reduced 可见背景纹理，Opaque 为实体背景；三档同时可见。只证明本窗口视觉差异，不量化折射质量或性能。
3. 按 UI 树的真实 bounds 点击模拟熔断后，三张卡和底栏均显示 Opaque，截图确认实体背景且文字可读。
4. 三张卡片的 bounds 在熔断前后分别保持 `{408,269,228,136}`、`{648,269,228,136}`、`{888,269,228,136}`；底栏 `{396,776,732,72}` 和命中按钮 `{1035,792,74,40}` 也保持不变。控制区说明文本变长不属于材质几何变化。
5. 熔断后弹层正常显示，文字/关闭按钮可见；Escape 关闭后主页面和熔断状态保留。
6. 从深色切到浅色，三张卡/底栏仍为 Opaque，背景及文字保持可读，没有自动重试玻璃特效。
7. 对底栏命中按钮执行准确节点的 AXPress 后，可访问性 description 从“命中 · 0”变成“命中 · 1”，证明按钮事件可运行。

自动化证据注意：所有坐标操作前均读取了当前 UI 树。底栏按钮的 System Events `click at` 虽使用其真实中心，却返回底层列表节点；因此改用直接 AXPress 区分事件与自动化命中。**不把 AXPress 通过当成实际鼠标命中通过，也未据此修改业务命中策略。** 后续需用原生鼠标事件/手动操作验收该浮层的真实命中。

采样 bounds（x,y,width,height）与中心：

- 模拟熔断按钮 `{112,657,240,40}` → `{232,677}`。
- 弹层按钮 `{112,597,240,40}` → `{232,617}`。
- 主题按钮 `{1070,112,58,40}` → `{1099,132}`。

截图（2200×1600，来自实际窗口区域，不用截图推算点击位置）：

- `/private/tmp/mafei-glass-fallback-before.png`：三档正常材质。
- `/private/tmp/mafei-glass-fallback-after.png`：熔断后实体回退。
- `/private/tmp/mafei-glass-fallback-interaction.png`：熔断后模态弹层。
- `/private/tmp/mafei-glass-fallback-light.png`：切换浅色后仍保留熔断。

## 尚未通过的范围

- 真实 GPU/驱动故障、原生崩溃、graphicsLayer 分配/绑定失败、Backdrop 捕获失败不在本次异常捕获边界内，不能宣称所有渲染故障都能恢复。
- 单测注入的是 Kotlin 特效异常；Mac 模拟按钮验证故障信号后的 UI 路径，不是驱动故障注入。
- Mac 实际鼠标浮层命中、长列表滚动性能、完整键盘焦点遍历仍待验证。
- Android/iOS/Harmony 新包运行、Harmony 真机旋转/窗口/进程恢复、Player 测试帧和系统无障碍偏好接线仍 Pending。
- Stage 1 未整体通过，不启动完整 Home 迁移。
