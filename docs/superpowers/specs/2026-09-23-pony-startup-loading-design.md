# 小马启动加载动画设计

## 目标

替换项目启动时 `SessionMessage` 中普通的 `LinearProgressIndicator`，让 MaFei Logo 小马成为启动阶段的主视觉。动画表达“正在启动”，但不伪装成有可靠百分比的真实进度。

## 交互与视觉

- 启动仍沿用现有 `App` 的 `starting` 状态和 450ms 延迟，不改变 `ApplicationRuntime.start()`、会话恢复或错误处理。
- 延迟结束后显示透明背景的小马 Logo，沿一条很短的水平圆角轨道循环移动。
- 小马移动时有轻微上下起伏和缩放呼吸，运动节奏连续、低刺激；背景保持现有深色启动画布，不增加玻璃卡片或额外面板。
- 启动完成后，小马沿当前方向完成一次短暂的淡出/位移，随后由现有页面接管。
- 启动文案保留“正在启动…”，但不再显示普通线性进度条。

## 组件边界

在 `composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/App.kt` 增加文件私有的 `PonyStartupLoading` Composable，并在 `starting && showStartupProgress` 分支调用它。组件只负责绘制 Logo、轨道、文案和动画，不读取或修改会话状态。

Logo 复用 `common/ui` 已暴露的 `MaFeiLegacyDrawables.appIcon`，避免复制资源或依赖 feature 模块。动画使用 Compose 跨平台的无限过渡和 `graphicsLayer`，位移转换为像素时通过 `LocalDensity` 完成。

## 数据流与生命周期

`App` 继续在 `LaunchedEffect(runtime)` 中启动运行时。`showStartupProgress` 只控制启动动画何时出现；`starting=false` 时启动画布被现有页面分支替换，动画自动离开组合并取消过渡。运行时异常仍走现有错误变量和会话流程。

## 验证

- 编译 `composeApp` 共享 UI 目标，确认资源引用、动画 API 和跨平台源码集均可编译。
- 运行现有 Compose App 启动流程，确认动画只出现在启动阶段，登录页、会话恢复和错误态行为不变。
- 视觉检查手机窄屏和宽屏启动画布，检查静止首帧、循环运动以及启动完成时的退出状态。
