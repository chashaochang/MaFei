# 2026-10-08 滚动性能修复与验证

范围：首页、影片/季详情的滚动与玻璃绘制。不修改布局、媒体数据规则或登录数据。

## 已确认并修复

- 默认 `LayerBackdrop` 先直接绘制内容、再记录同一内容；三层嵌套使叶子绘制执行 8 次。现在每帧记录一次，页面与玻璃消费者共享同一实时 display list。自定义 recorder 保留可见内容与采样内容不同的语义。
- 首页把 FloatingTabs 的逐像素坐标读在页面组合阶段；改为在 placement 阶段读取，仅可见/吸顶布尔变化触发组合。
- 影片/季详情的滚动折叠进度带动整个内容重组；现在 Hero 变换在 graphicsLayer 内读取，标题栏通过共享 `DetailScrollActionBar` 隔离重组。

## 回归与测量方法

- `BackdropRecordingTest`：三层记录次数先红（8，期望 1）再绿；实时颜色更新、遮盖源后的采样、自定义 recorder 行为均通过。
- 桌面单测：common:ui、common:media、feature:home、feature:detail。
- 构建：Android assembleDebug、OHOS publishDebug/ReleaseBinariesToHarmonyApp、HAP assembleHap、verifyArchitecture。
- `verify-harmony-artifacts.mjs` 新增第四参数 `debug|release`，检查 libkn/header、Hvigor 中间产物和 HAP 产物来自同一次链接。默认仍为 debug。
- 真机：62Q0225B10002147，cn.xiaobai.mafei，1280×2832。仅覆盖安装；没有卸载、清数据、切换账号或退出登录。
- 每次自动操作前读取 `uitest dumpLayout`，确认列表实际 bounds。首页 List 为 `[0,137][1280,2832]`；脚本在有效区域执行两次向上和两次向下滑动。
- `hitrace --trace_begin -b 32768 app ace graphic`，滑动后立即 trace_finish；用 `scripts/analyze-harmony-frame-trace.mjs <trace> <pid>` 统计。

测量是 **应用 buffer 提交间隔与应用绘制回调耗时，不是最终屏幕 FPS**。操作间停顿、触底/触顶停帧也可能形成长间隔，不能把所有长间隔都认定为掉帧。系统刷新率会动态变化，不以倒数直接宣传帧率。

## 中间结果（不能作为最终完成结论）

| 首页采样 | 间隔 P95 | 绘制回调均值 | 重组均值 |
| --- | ---: | ---: | ---: |
| 修复后 Debug，预热 | 30.84ms | 14.87ms | 0.77ms |
| 相同代码 Native Release，预热 | 25.20ms | 13.41ms | 0.26ms |

Release 降低了 Kotlin 代码开销，但绘制仍然占主要时间，因此没有将换 Release 包视为卡顿已解决。图像解码已在后台完成，无证据支持重写图片缓存。

本地 trace：`/private/tmp/mafei-perf-before-scroll.ftrace`、`mafei-perf-warm-scroll.ftrace`、`mafei-perf-release-warm-scroll.ftrace`。原始 trace 仅本地保留，不提交用户媒体信息。

## 未保留的实验

CPF 1.9.2-1.0.0 的 `ComposeArkUIViewControllerConfiguration.skiaParallelRendering` 默认关闭。独立线程实验减轻了主线程回调耗时，但本机提交间隔 P95 反而约 50ms，已撤回，不能把较低的主线程耗时当成整体更流畅。

## 静态封面背景缓存

首页及详情氛围图使用共享 `CachedBlurredArtwork`：封面、窗口尺寸或模糊半径变化时，将居中裁剪和等效半径的模糊结果一次性栅格化，长边上限 512px，随后复用位图。清晰 Hero、封面切换 Crossfade、亮度与蒙层保持原逻辑；底栏和 FloatingTabs 仍实时采样背景加滚动内容。未启用全局离屏快照缓存。

补充几何测试覆盖手机/宽屏裁剪、缩放半径、小目标不放大；像素测试覆盖实际模糊、封面更新及窗口尺寸更新。软件 snapshot 不设置 layer.clip：部分 Skia 实现会留下零尺寸初始裁剪，像素测试已复现；最终输出位图自身提供尺寸边界。

全量 composeApp 桌面测试曾出现 PlayerCapabilities 构造器 NoSuchMethodError（已有播放器 API 改动后的过期测试字节码）；只重编译 composeApp 的测试任务后 144 项恢复通过，没有为此修改播放器业务代码。

新增静态背景缓存后，同一设备/同一首页往返滑动脚本，Debug 预热结果：

| 指标 | 缓存前 Debug | 缓存后 Debug |
| --- | ---: | ---: |
| 提交间隔均值 | 22.78ms | 13.79ms |
| 提交间隔 P50 | 22.25ms | 11.30ms |
| 提交间隔 P95 | 30.84ms | 22.80ms |
| 绘制回调均值 | 14.87ms | 9.38ms |
| 本次采样 >25ms 间隔数量/样本量 | 32/264 | 9/426 |

缓存后影片详情本机采样：绘制回调均值 7.88ms、P95 15.13ms；已操作上下滚动并正常返回首页。详情采样影片不同，不将其与旧详情数字作严格 A/B 百分比对比。

新增 trace：`/private/tmp/mafei-perf-cached-warm-scroll.ftrace`、`mafei-perf-cached-detail.ftrace`。本轮桌面测试合计 227 项通过（UI 17、media 10、home 32、detail 24、composeApp 144）。Android assembleDebug、OHOS Debug 编译/HAP/产物一致性检查通过，包已覆盖安装，后续补充优化编译包的实际结果。

性能验收必须标注 Native 构建模式。使用 `:composeApp:publishReleaseBinariesToHarmonyApp` 可生成优化后的 libkn；HAP 是否 Debug 与 Native 是否 Release 是两个独立选项。本轮保持原签名及包名，不通过重新安装清空账号。

## 最终安装包

Native Release（`linkReleaseSharedOhosArm64`，LLVM -O3）+ 原签名 Debug HAP。`verify-harmony-artifacts.mjs ... release` 验证通过后覆盖安装，最终 PID 64476。

同一首页滑动脚本最终结果：提交间隔均值 12.53ms、P50 11.26ms、P95 22.54ms；绘制回调均值 8.52ms、P95 13.45ms；重组均值 0.20ms。原始修改前采样对应提交均值 18.61ms、绘制回调均值 14.28ms。系统刷新率动态调整，数字是本机短场景观测，不等同于所有机型的 FPS 保证。

最终两轮约 6 秒滚动采样均无 >50ms 的提交间隔；仍有少量 >25ms 间隔，并记录到首次使用效果的 shader 编译（最长约 16ms）。因此结论是主要持续滚动热点已消除、流畅度显著改善，而非“零掉帧”。

最终 trace：`/private/tmp/mafei-perf-final-first-scroll.ftrace`、`mafei-perf-final-warm-scroll.ftrace`。只补取了一张手机画面核对真实背景和玻璃仍在显示，没有抓取电脑桌面、没有启动或置顶 Mac/Java 窗口；这不代表完整多形态视觉验收矩阵已通过。

最终 Release 影片详情往返滚动通过：绘制回调均值 7.02ms、P95 13.29ms，重组均值 0.33ms；trace 为 `/private/tmp/mafei-perf-final-detail.ftrace`。提交间隔最大约 792ms 含触底/触顶后没有内容变化的停帧，不能作为单帧执行耗时；对应绘制回调最大为 20.31ms。已通过页面返回按钮回到首页，交付时停留在首页。

手机/宽屏、浅色/深色的完整视觉矩阵、Android/iOS/Mac 真机性能不因本轮单台鸿蒙设备通过而自动视为完成。

## 第二轮：材质导出复用

上一轮优化已单独提交为 `8f34f19`，其他播放器、ISO 及设置改动未纳入。

`DrawBackdropNode` 的可见材质与 `exportedBackdrop` 原先分别录制同一背景。现在每次绘制只录制一次，两条路径复用当帧的 display list；导出仍只包含背景、表面与前景装饰，不包含子控件。未修改模糊、折射、透明度、动画或组件布局，也未缓存动态玻璃为静态位图。

新增三个回归：

- 导出路径背景录制次数先红（2 次，期望 1 次），修改后通过。
- 导出不包含子控件，保持背景实时更新及 behind/surface/front 遮挡顺序。
- 非零模糊、窗口尺寸及模糊半径变更后，导出与可见材质逐像素一致。

五个模块桌面测试合计 230 项通过，`verifyArchitecture` 通过。Android assembleDebug、OHOS Native Release（8m47s）和 HAP（7s）均构建通过；`verify-harmony-artifacts.mjs ... release` 检查同次产物通过。已使用 `hdc install -r` 覆盖安装，保留原包名、签名和账号数据。

构建期间手机曾锁屏，`aa start` 返回 10106102；未绕过锁屏。安装后再次启动成功，PID 22419，读取首页布局树确认列表 bounds 后完成两轮相同滑动路径采样，没有截图。

预热采样绘制回调均值 8.37ms、P95 12.46ms；提交间隔均值 12.44ms、P95 18.38ms、最大 37.47ms；461 个间隔中 6 个超过 25ms，0 个超过 50ms。首轮仍观测到约 17.48ms 的 shader 加载/编译，绘制回调最大 47.03ms。相比上一轮预热均值 8.52ms、提交 P95 22.54ms，本次数据有改善，但短场景、轮播内容及系统调度有波动，不能把全部变化归因于此项改动，更不能把录制次数减半解读为帧率提升一倍。

本地 trace：`/private/tmp/mafei-perf-export-first.ftrace`、`mafei-perf-export-warm.ftrace`。首次 GPU shader 编译慢帧尚未解决；已有的 shader 实例缓存不等同于 GPU 管线已经预热。

详情页已操作上下滚动并通过页面返回按钮回首页：绘制均值 6.85ms、P95 13.91ms、最大 20.53ms。trace 为 `/private/tmp/mafei-perf-export-detail.ftrace`；约 720ms 的最大提交间隔包含触顶/触底后的静止段，不作为单帧执行耗时。不同影片不做严格 A/B 比较。此次未截图，不声明完整视觉矩阵验收。
