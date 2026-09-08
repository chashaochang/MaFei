# 2026-09-08 平台 06 小批集成

本轮子 Agent 分别实现 HTTP 与 Android 安全存储，主线实现共享编码、审查错误隐私、独立复验并分批提交。不是 GLM-06 全平台完成；iOS Keychain 本轮只有依赖准备，没有实现或运行证据。

## 已提交

| 提交 | 交付 | 边界 |
| --- | --- | --- |
| `0db77d8` | 共享凭证 codec + HTTP 内置错误文案脱敏 | 版本化编码不是加密；结构化 URL/response 仍敏感 |
| `054a1f7` | CIO Transport + Android/Mac 工厂 | 实际 buffered HTTP；不是 WebSocket/SSE，不代表 Native 网络通过 |
| `e04bea7` | AndroidKeyStore AES-GCM 实现 | 生产代码/AAR 已构建，JVM 使用合成密钥，不代表 AndroidKeyStore 实测 |

HTTP：总超时覆盖重定向/读体、关闭取消、显式重定向与跨源清凭证、无 Cookie jar/自动重放、有界读体、丢弃体、截断失败、重复 Cookie 和 101 关闭。修正实际观察的 CPF CIO 差异；错误保留分类但不导出未脱敏引擎 cause。

Android：非导出 Keystore 密钥、noBackupFilesDir 密文、namespace+alias AAD、跨实例/合作进程锁、原子替换与 fsync；损坏/错密钥不当缺失、禁止明文或自动软件密钥降级。不存在旧密文时才允许首次建密钥。

## 主线独立验证

- `/private/tmp/mafei-06-independent-final.log`：HTTP Desktop/Android JVM 各 **32/32** 实际通过，Native 编译及两端工厂门禁通过；共享模块不是 Native 运行验收。
- `/private/tmp/mafei-06b-independent.log`：Android 加密持久层 **14/14** 实际通过，生产源码/AAR 门禁通过（构建缓存命中）。
- `/private/tmp/mafei-secure-codec-green.log` / `mafei-security-mainline-green.log`：codec 双 JVM 各 **11/11**，iOS/OHOS 源码编译实际通过。HTTP contract Desktop **89/89**、Android **84/84**；原有 5 个仅 desktopTest 的测试限制继续保留。
- `/private/tmp/mafei-06-app-integration.log`：composeApp Android、Mac Desktop、iOS device/simulator ARM64、OHOS ARM64 源码集成编译通过；Repository 双 JVM 各 **43/43**、Jellyfin Adapter Desktop **49/49**、Feiniu Adapter Desktop **44/44** 实际执行通过。此次没有补跑 Adapter Android，不引用历史 XML 当本轮执行。
- Node 架构门禁回归 **7/7**、verifyArchitecture、diff check 通过。现 74 个模块。

TDD：HTTP 初始 20 项实际失败，后续差异测试逐项修正；codec/Android 初始是缺实现的编译红灯，不冒充行为红灯。主线异常 URL 隐私、未经脱敏 cause 两项各有实际失败再转绿记录，见对应小批说明。

## 未完成与下一步

1. Android/Mac 工厂虽有真实 Transport，应用 Composition Root 还未装配登录/Repository 真实链路。没有真实 Jellyfin/fnOS 服务器验收。
2. iOS/OHOS 平台 HTTP 工厂与运行验收未实现；共享 Ktor 的 Native 编译不能替代它们。
3. iOS/macOS Keychain、OHOS 安全存储桥接未实现；Android Keystore、OS 原子文件操作、锁屏/重启/重装需要设备测试。下一批优先 iOS Keychain 与 OHOS 桥接设计/实现，不再重复搭模块。
4. 当前真实 HTTPS/CA、自签名证书选择、系统代理、IPv6/LAN 和网络切换未验收。默认不放松 TLS，不提供 trust-all。
5. Player runtime/Surface、旧鸿蒙 Reader/事务 Sink 仍 Pending，Stage 1 未整体通过。本轮没有新增设备 UI 或 HAP 首帧证据。

详细说明：[HTTP](../plans/2026-09-08-codex-06a-http.md)、[Android 安全存储](../plans/2026-09-08-codex06b-android-secure-storage.md)、[共享编码与异常隐私](../plans/2026-09-08-codex06-secure-codec.md)。

提交 Author/Committer 都是 Codex，未推送。`gradle.properties`、`runscript/` 原有改动保留。临时日志可能被系统清理，不等同于 CI 永久归档。
