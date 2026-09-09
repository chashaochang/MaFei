# GLM-05 播放器四端 Runtime 说明

日期：2026-09-09。基线：GLM-04C 后主线（含 04B 冻结 `fb32976`）。范围：四端 `player/runtime-*`；common 层（player:api / core / facade / testkit）此前已完成并保持原样，**本批无接口变更**（`PlayerSurfaceHandle` 维持 opaque final 类——平台对象通过各 runtime 的 registry 注册，正是 API 注释预设的形态）。

## 平台状态总览（不夸大）

| 平台 | 模块 | 引擎 | 状态 |
| --- | --- | --- | --- |
| Android | runtime-android | `android.media.MediaPlayer`（零新依赖） | **Implemented**（翻译层 JVM 单测 11/11 全绿；`SystemMediaPlayerEngine` 真实现编译通过；真实解码/渲染/后台行为待真机 = **Pending**） |
| Apple Silicon macOS | runtime-macos | AVPlayer（JNI dylib，ObjC 桥） | **Implemented**（翻译层 10/10 全绿 + **真实 JNI 链接验证 1/1**：dylib 编译、加载、AVPlayer 创建/销毁、查询面全部真实执行）。视频合成到 Compose 桌面窗口属 UI 层（Codex）——音频已完整，视频输出 **Pending** |
| iOS | runtime-ios | AVPlayer（bridge 模式） | **Compile-only**：直接 KMP 绑定被工具链阻断（证据见下），交付接口 + 翻译层 + 宿主接线说明；iosTest 已编写，执行需 iOS 模拟器窗口 |
| HarmonyOS | runtime-ohos | native AVPlayer（bridge 模式） | **Pending**（按任务要求）：接口 + 翻译层 + 宿主接线说明已交付；真实引擎与 Surface 证据待宿主窗口 |

翻译层四端同构：命令翻译、generation 过滤（引用相等）、轮询驱动事件（全部源自引擎回调/查询，**无任何伪造 ready/playing/progress**）、Release 幂等且解绑 sink、Stop 先停旧资源再 Load、非法 SelectTrack 诚实抛 `UnsupportedPlayerCapability`。

## 工具链缺口证据（iOS 直接绑定不可编译）

Kotlin 发行版 `2.2.21-1.0.0` 的 ios_arm64 `platform.AVFoundation` klib 导出 `AVPlayer` 类型壳但**无任何成员**（`play`/`pause`/`seekToTime`/`rate`/`currentItem`/`timeControlStatus`/`replaceCurrentItemWithPlayerItem` 全部 unresolved），协议 `AVMediaPlayback`/`AVPlayerItems` 也无法解析；对照实验 `platform.UIKit`/`platform.Foundation` 类型可解析。最小探针复现步骤见 git 历史。因此在恢复完整 klib（升级官方发行版或补 cinterop def）之前，iOS 走 bridge 注入模式。

## OHOS / iOS 宿主接线说明

1. 宿主实现 `OhosAvPlayerBridge` / `IosAvPlayerBridge`：每个 handle 一个 native AVPlayer 会话；`loadUrl` 设置媒体源；查询方法直读引擎真实状态（OHOS: `AVPlayerState`/`duration`/`currentTime`; iOS: `timeControlStatus`/`AVPlayerItemStatus`/`loadedTimeRanges`）。
2. Surface：宿主创建 XComponent（OHOS）/ CALayer（iOS）后调用 `OhosSurfaceRegistry.register(surfaceId)` / `IosSurfaceRegistry.register(layer)` 取 opaque handle，经 controller `attachSurface(handle)` 下发；runtime 只向 bridge 传 surfaceId/layer。
3. 宿主以主循环定时器实现 `*PollScheduler`（250ms 建议），驱动 `runtime.poll()`。
4. 事件经 `runtime.bind { }` 回调进 `DefaultPlayerController`（common 层负责 generation 过滤与播放上报编排）。

## 决策记录

1. **轮询驱动而非回调驱动**：四端统一从引擎真实状态推导事件，规避各引擎回调线程差异；状态机（common）天然过滤重复/过期事件。
2. **generation 纪律**：`PlaybackGeneration` 引用相等比对在 common 状态机完成；runtime 层再以 engine session/handle 隔离旧回调（Android session 计数、macOS/iOS/OHOS 单 handle + generation 快照），双保险。
3. **Android headers**：`setDataSource(context, uri, headers)` 支持；**macOS/iOS P0 不支持自定义 HTTP header**（AVURLAsset 需 resource-loader 代理）——KDoc/Pending 注明，不得静默假装生效。
4. **Surface 注册表**：`PlayerSurfaceHandle` 不可继承（final），Android/macOS/iOS/OHOS 各自 registry 映射真实 Surface/AVPlayerLayer/CALayer/surfaceId。
5. **速度 API**：Android 公共 SDK jar 无 `setPlaybackSpeed`，用 `PlaybackParams.setSpeed`（start 与 setPlaybackSpeed 两处同步）；macOS 用 `AVPlayer.rate`。
6. **禁改清单遵守**：未触碰 player/ui、composeApp、feature、common/ui、design-system、harmonyApp、gradle.properties、output/、runscript/。

## 验证（实际执行）

- `:player:core:desktopTest` 26/26、`:player:facade:desktopTest` 9/9（common 基线，未改动）。
- `:player:runtime-android:testDebugUnitTest` 11/11（fake engine 翻译层：load/ready、旧 session 丢弃、poll 派生、错误映射、外来 generation 忽略、surface 重挂、Release 幂等解绑 sink、SelectTrack 拒绝）。
- `:player:runtime-macos:desktopTest` 11/11（10 翻译层 + 1 真实 JNI：dylib 编译加载、AVPlayer 创建/销毁/查询真实执行）。
- 编译：runtime-android `compileDebugKotlinAndroid`、runtime-ios `compileKotlinIosArm64`、runtime-ohos `compileKotlinOhosArm64`、runtime-macos `compileKotlinDesktop` 全部通过；`verifyArchitecture`、`git diff --check` 通过。
- **未运行**：iOS 模拟器测试（iosTest 已编写）、OHOS 模拟器测试、Android 真机/模拟器播放、macOS 音频实播——全部如实列为 Pending，不以"编译通过"冒充真实验证。
