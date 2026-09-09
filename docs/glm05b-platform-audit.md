# GLM-05b 平台基础设施审计与 Provider→Player 闭环

日期：2026-09-09。覆盖：四端 HTTP Transport、四端 SecureCredentialStore、Apple 自定义 Header 结论、迁移接入状态。

## 1. HTTP Transport（四端）

全部平台复用 `platform/http-ktor` 的统一引擎包装（`createKtorHttpTransport`），**协议逻辑零复制**：

| 平台 | 模块 | 实现 | 状态 |
| --- | --- | --- | --- |
| Android | http-android | `CioHttpTransport` 直用 | Implemented and runtime-tested（JVM 单测） |
| Apple Silicon macOS | http-macos | `CioHttpTransport` 直用 | Implemented and runtime-tested |
| iOS ARM64 | http-ios | Ktor Darwin 引擎 + `DarwinCookieTransport`（cookie 镜像） | Implemented but compile-only（Loopback 测试需模拟器） |
| OHOS ARM64 | http-ohos | Native curl 处理器 + Ktor 组合 | Implemented but compile-only（Loopback/Native 集成测试需设备） |

共享策略（http-ktor 统一注入，各平台不重复实现）：单一外层超时（默认 30s，引擎内部超时禁用避免双重计时）；**关闭原生自动重定向与凭据存储、禁用自动重放**（重定向策略归 core:http-contract）；错误按 core:http-contract 分类（`HttpTimeoutException`/`HttpTransportException(kind)`）。已知限制：**响应体大小上限未在传输层统一强制**（各 Provider 端依赖 JSON 解码与固定 URL 信任面，P1 补齐点已记录）。

## 2. SecureCredentialStore（四端）

| 平台 | 模块 | 后端 | 状态 |
| --- | --- | --- | --- |
| Android | secure-storage-android | EncryptedCredentialFiles（Keystore 主密钥加密文件，非明文） | Implemented（单测覆盖加密文件层；Keystore 解密运行验证 Pending） |
| Apple Silicon macOS | secure-storage-macos | Security.framework Keychain（JNI dylib，真实链接测试通过） | Implemented and runtime-tested |
| iOS | secure-storage-ios | Security.framework Keychain（SecItem） | Implemented but compile-only（集成测试需模拟器/设备） |
| OHOS | secure-storage-ohos | Asset Store + NativeGCM + 长凭证 Vault（宿主 N-API） | Implemented but compile-only（Native 集成测试需设备） |

共同契约：core:secure-credential-api 的 `SecureCredentialStore`；toString/日志不含秘密（fake/测试断言覆盖）；无明文文件 fallback 伪装（Android 文件层显式命名 Encrypted，密钥来自 Keystore）。并发/取消边界由上层 SessionRepository 的 mutex + NonCancellable 语义处理（已有测试）。

## 3. Apple 自定义 Header 结论（AVPlayer）

调研结论：**AVFoundation 公开 API 不支持为 AVURLAsset/AVPlayer 的 HTTP(S) 请求注入自定义 Header**。公开且可发布的唯一路径是 `AVAssetResourceLoaderDelegate`：把播放 URL 改写为自定义 scheme，由代理拦截并改回 https、用 URLSession 手动转发并注入 Header、把响应数据回喂成 AVAssetResourceLoadingRequest 流。该方案：

- 属公开 API，无审核风险；
- 但需要自管 HTTP 生命周期（重定向、Range、Cookie、TLS 信任、取消、缓冲）并处理流式回喂，工程量与出错面大；
- “URL 能传给 AVPlayer”不构成支持——Header 必须证明真的进入了媒体请求，而当前无真机验证窗口。

**决定**：P0 不实现。macOS/iOS runtime 对非空 headers 的行为是**显式失败**（macOS：load 抛 `UnsupportedOperationException` → `Failed(UnsupportedMedia)`；iOS：bridge 契约要求同等行为）。受影响资源说明：

- **Jellyfin**：P0 流 URL 自带 `api_key` 查询参数（direct stream/transcoding URL 均含），Header 缺失**不影响** Jellyfin 播放。
- **飞牛**：play_link 依赖 `Authorization` + `Trim-MC-token` Cookie（且 relay 路由还要 `mode=relay`）——**Header 不支持的端上飞牛播放不可用**，即 macOS/iOS 的飞牛视频播放为此待办的真实阻塞点，恢复前用户会收到明确的 UnsupportedMedia 失败。

状态：**Pending（需 AVAssetResourceLoaderDelegate 实现与真机验证）**，未冒充 Implemented。

## 4. 迁移接入状态

`data:migration` 的引擎/计划器/快照模型已完整并测试（幂等 journal、receipt、源数据变化检测 `SourceChanged`、凭证 create-if-absent 与冲突拒绝、完成标记后不再写）。数据覆盖：Jellyfin 服务器与用户 token、飞牛 token/cookie/地址路由、账号选择、播放偏好、追剧、首页快照、installationId。

**Pending（平台窗口）**：各平台 `LegacySnapshotReader` 的真实读取实现（旧 App 的 RDB（SQLite 文件，桌面端需 SQLite 驱动）与 OHOS Preferences，需要宿主/设备文件访问证据）；OHOS 需要宿主 N-API Reader。公共接口与校验、接线层已就绪，宿主 Reader 到位后即可运行完整迁移。旧数据仅作为读取依据，`legacy/**` 未修改。

## 5. Provider→Player 闭环（本批新增）

`data/repositories/playback/ProviderPlaybackCoordinator`（+10 项契约测试，全部通过）：

- `preparePlayback` → `PlayableResource` 转换保留 URL/headers/媒体 id/字幕；`reportingContext` 留在内存中的 Ready 值内，只用于原 adapter 上报，不持久化、不从 URL 反解析。
- 上报经 `reportSink` 注入 `DefaultPlayerController`，Start/Progress(paused)/Stop 原样翻译，取消传播，失败进入 `reportFailures`（可观察）而不打断状态机。
- 过期/认证失败（Authentication/Authorization）触发**同一 adapter 实例**的重新协商（限 `maxRenegotiationsPerGeneration`，默认 1 次）；协商结果仅当绑定仍为当前会话时才加载；Stop/Release/快速切集后的旧结果被丢弃；非认证类 5xx 只记录不重试、不切主机。
- Release 后 coordinator 关闭上报通道并取消 worker，后续报告为无操作。
