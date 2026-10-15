# Codex GLM-05A：统一播放器非 UI 核心

状态：05A 核心经主线审查与独立回归通过。不是完整播放器验收。

主线终审（2026-09-08）：后台切源/显式 play 的恢复意图与暂停即时上报问题已修正；主线独立执行 Desktop/Android 各 35 项通过。日志 `/private/tmp/mafei-player-mainline-independent.log` 同时记录 composeApp Android、iOS device/simulator ARM64、OHOS ARM64 源码集成编译通过及架构门禁通过。Native 编译不替代 Native 运行；下面 runtime/Surface/上报接线限制继续有效。

## 本批边界

- 仅 `player/api`、`player/core`、`player/testkit`、`player/facade`。
- 主线增加已有 coroutines 坐标到 api，并把 testkit 接给 facade commonTest；没有新库坐标。
- 没有修改 `runtime-*`、`player/ui`、平台宿主或 Legacy；没有 Git 提交。

## 可消费接口

`PlayerController` 提供 `load/play/pause/seek/stop/release/speed/selectTrack`、Surface 挂载解绑、前后台和按 generation 取消准备。`StateFlow<PlayerState>` 是状态入口。

`DefaultPlayerController(runtime, ownerScope, nowMs, reportSink)` 串行化状态机和引擎命令；Runtime 的 `bind` 接收线程安全事件入口，`execute` 必须是非阻塞的原生命令入队。同步 native callback 不重入状态机，也不死锁。Owner scope 取消自动释放；显式 release 幂等且不可取消。

`PlayableResource` 只接受上层已经完成信任校验的资源，防御复制 headers，日志字符串不打印 URL/headers。Generation 和 Surface handle 均使用引用身份；旧 generation 的 ready/error/progress/ended 全部忽略，旧 Surface detach 不能拆掉新 Surface。

命令返回表示提交，不等于原生播放确认：load 只进入 Loading，Ready/Playing/Paused 由 Runtime 回调确认。续播 seek 先于 autoplay，重复 Ready 不重复 seek/play。暂停或退后台后到达的 Playing 被反向 pause，晚到 buffering 不覆盖暂停。

## 队列和上报

核心支持已准备资源列表、指定起播 index、续播、自动下一项，最后一项进入 Ended。队列中的 URL 不得持久化，调用方必须保证使用时尚有效。飞牛过期播放链接的逐项重新协商、Data Ready 与 generation 的归属映射属于数据/播放器集成批次，当前不能直接把长队列永久缓存后使用。

Start 在首个 Playing 确认发出；Progress 默认 10 秒节流，但原生 Paused / 恢复 Playing 确认立即发送暂停状态更新，不等待后续进度。缓冲和进度不能在原生 Playing 确认前擅自解除暂停。Stop 在换资源/停止/结束/错误/释放时强制发送。上报带 generation 和 resourceId，不解析播放 URL，不依赖 Provider DTO。

`reportSink` 必须非阻塞，将事件交给数据层自己的有序上报队列；默认空 sink 明确关闭 Provider 上报，不能当作服务器已收到。Sink 异常累计到 `reportDeliveryFailures`，不破坏播放状态。原生命令异常累计到 `runtimeCommandFailures`；Released 表示控制器关闭，不证明失败的原生释放已经成功。

## 验证证据

- 最初测试 API 尚不存在时，编译失败记录于 `/private/tmp/mafei-player05-red.log`，不称为行为红灯。
- 晚到 buffering 覆盖 Paused：真实失败 1/20，记录于 `/private/tmp/mafei-player05-behavior-red.log`；修复后通过。
- 主线终审补充后台先于 load、后台显式 play、后台自动下一项、暂停/恢复即时上报：5 个真实行为失败，证据 `/private/tmp/mafei-player05-review-red.log`；修复后通过，autoplay=false / 用户 pause 的不恢复对照保留。
- Core 26 个 commonTest、Facade 9 个 commonTest；Desktop 和 Android JVM 分别执行。
- iOS ARM64 / OHOS ARM64 的 api/core/facade/testkit 编译验证；不是 Native 测试运行，也不是原生引擎播放。
- 最终命令与结果：`/private/tmp/mafei-player05-final.log`。

## Pending

| 项目 | 状态 |
| --- | --- |
| 纯 Kotlin 状态机、Controller、Fake | Implemented；JVM 双目标测试 |
| iOS/OHOS 共享四模块 | Compile-only |
| Android/iOS/OHOS/Mac 真实引擎 | Pending；本批未实现 runtime adapter |
| Surface/UIView/窗口注册表和宿主接线 | Pending |
| Provider Ready 转资源、逐项过期重协商、播放上报队列 | Pending 集成 |
| 系统媒体会话、画中画、后台音频 | Pending |
| 真机播放、Native 并发、网络和解码表现 | Pending；不得使用 Fake 结果替代 |

运行时桥接必须遵守 generation 与命令顺序，使用自己的平台线程；不能回调假 Ready/Playing 来绕过门禁。
