# 2026-09-08：Codex 并行开发与主线审查

后续进度见 [平台 06 集成](2026-09-08-platform-06-integration.md)：HTTP 与 Android 安全存储已交付源码实现；本文下方的 06 框架状态是本批结束时的历史记录。

本轮按用户授权，由 Codex 子 Agent 接手原 GLM 非 UI 工作，主线审查、修正、定向复验后逐批提交。各 Agent 不自行提交，也不修改共享 Gradle；共享文件和集成由主线维护。以下均为本地提交，未推送。

## 已合入范围

| 批次 | 提交 | 本轮证据和准确边界 |
| --- | --- | --- |
| 04C 飞牛 Adapter 复审 | `001a452` | 修正短页游标、收藏排序、非法日期、跨账号协商/上报；Adapter JVM 双目标各 44 项，Playback SDK 各 4 项；真实飞牛仍 Pending |
| 04D 媒体 Repository | `4fbc2e7` | 会话代次、TTL/失败回退、分页合并和 UseCase；原各 24 项，此轮与 04E 联合回归 |
| 04E 会话凭证写序 | `9146f67` | 独立账号凭证保留、权威快照写序、退出清理、取消准入、身份复核后 Ready；本批各 19 项 |
| 04F 迁移核心 | `b4f76da` | 完整旧来源清单、快照、校验、幂等凭证/事务回执恢复；各 30 项；实际 Reader/Sink 未实现 |
| 05A 播放器核心 | `390ec2b` | Controller/Runtime 契约、确定性状态机、队列/续播/上报、生命周期/取消；各 35 项；所有真实引擎和 Surface 接线仍 Pending |

提交 Author/Committer 均为 `Codex <codex@mafei.invalid>`。GLM 原提交保留其身份；未再次改写历史。

## 主线独立复验

- `/private/tmp/mafei-data-final-independent.log`：Repository Desktop/Android 各 **43/43**（04D 24 + 04E 19），migration 各 **30/30**；四份结果均本轮实际执行、无 skipped。Native 编译门禁通过，本次命中缓存；子 Agent 已另行记录实际编译，不称 Native 测试运行。
- `/private/tmp/mafei-player-mainline-independent.log`：player core/facade Desktop/Android 各 **35/35**，本轮实际执行。composeApp Android、iOS device/simulator ARM64、OHOS ARM64 源码集成编译通过；不代表 Native 链接、HAP 或引擎运行。
- `node --test scripts/verify-cmp-foundation.test.mjs`：**7/7**。架构门禁和 `git diff --check` 通过。
- 初次联合 Android 构建遇到正在开发的 PlayerStateMachine 跨模块 smart-cast 编译错误；没有把这次失败归因于迁移/宿主。播放器修正后完整重建通过，见下一节。

## 主线 UI/宿主回归（不是新业务页面）

使用 Android CLI 技能完成开发包安装、布局树读取及截图；继续使用项目现有的 UIAutomator bounds 脚本，不根据截图估算点击。

1. `/private/tmp/mafei-mainline-probe-rebuild.log`：根工程 `:composeApp:assembleDebug` 成功。
2. 安装 `/Users/machunjiang/MaFei/composeApp/build/outputs/apk/debug/composeApp-debug.apk` 到 `emulator-5554`（Android ARM64 开发模拟器），实际启动 `cn.xiaobai.mafei.cmp.dev/cn.mafei.cmp.MainActivity`；不清除应用数据。
3. `/private/tmp/mafei-android-current-layout.json`：Android CLI 当前布局树。
4. `/private/tmp/mafei-android-current-smoke.log`：主题双向切换、收展、Full/Reduced/Opaque 三档真实命中、横竖屏命中与状态保持、系统返回关弹层、模拟特效熔断为实体、重新请求 Full 不绕过熔断、前后台保持命中 5 和熔断状态全部通过。每次 tap 重新读取 UIAutomator bounds；旋转设置在 finally 恢复。
5. `/private/tmp/mafei-android-current-frame.png` 已实际查看：资源文本、深色系统栏、实体底栏、命中 5 和 Opaque 状态可见。此项不是性能/全尺寸/生产视觉验收。
6. 此 APK 构建先于最后的纯播放器核心回归补丁；Probe 不接真实播放器，不用它证明最终 PlayerStateMachine 的设备运行。最终播放器以独立源码/单测证据为准。
7. 本轮 `hdc list targets` 仍为 `[Empty]`。没有 HarmonyOS 安装、首帧、真实网络、真实安全存储或覆盖安装证据；Stage 1 不宣布通过。iOS/Mac 本轮未新增 UI 运行验收。

## 剩余工作与接线次序

- **06A HTTP**：主线已准备 `platform:http-ktor` 共享模块及 Android/Mac 依赖接线，只是框架，不是 Transport 已实现。共享超时/取消/读体/重定向实现与本地服务器合同测试待交付；不得将空模块当网络可用。仅使用已锁定 CPF Ktor 依赖，没有新增版本坐标。
- **06B 安全存储**：Keystore/Keychain/OHOS 安全桥接尚未实现；禁止明文兜底。
- **05B 引擎**：Android/iOS/OHOS/Mac runtime、Surface、系统媒体会话与真实播放未实现；05A Fake 结果不能代替。
- **Data/Player 组装**：权威 SessionFlow 到凭证源、MediaRepository 会话生命周期、Provider Ready 到 generation、飞牛过期资源逐项重协商、有序上报队列均未接应用。
- **迁移集成**：真实 OHOS Reader 必须覆盖多账号库、独立凭证偏好、当前选择、追剧、片源偏好和首页；真实 Sink 必须提供启动排他租约及业务记录与 receipt 同一事务。覆盖安装验证单独验收。
- **主线 UI**：继续 Stage 1 运行门禁及 Player Surface probe；不以底层核心完成为由越过门禁迁移完整 Home。

`gradle.properties` 和 `runscript/` 保留原状，未纳入本轮提交。临时日志不是永久 CI 归档，可能由系统清理。
