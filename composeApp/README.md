# 共享应用入口

`composeApp` 装配启动、账号、浏览、设置和播放流程。Feature 拥有页面展示，浏览控制器拥有请求和数据状态；`App.kt` 仅选择根流程和注入平台回调。

修改前先读 [架构与职责表](ARCHITECTURE.md)，再看 [导航边界](src/sharedUiMain/kotlin/cn/mafei/cmp/navigation/README.md)及 [浏览控制器](../feature/home/src/commonMain/kotlin/cn/mafei/cmp/feature/home/browser/README.md)。全局规则以根 [AGENTS.md](../AGENTS.md) 为准。

## 平台入口

以下路径相对本目录；Kotlin package 为 `cn.mafei.cmp`。

| 平台 | 入口 | 宿主职责 |
| --- | --- | --- |
| Android | `src/androidMain/kotlin/cn/mafei/cmp/MainActivity.kt` | Activity、系统回调、原生播放 Surface |
| HarmonyOS | `src/ohosMain/kotlin/cn/mafei/cmp/MainArkUIViewController.kt` | 对接 CPF Compose 和 N-API 薄宿主 |
| iOS | `src/iosMain/kotlin/cn/mafei/cmp/MainViewController.kt` | UIKit/Compose 控制器及原生能力 |
| macOS | `src/desktopMain/kotlin/cn/mafei/cmp/Main.kt` | Desktop 窗口、生命周期和本机能力 |
| 共享 | `src/sharedUiMain/kotlin/cn/mafei/cmp/App.kt` | 启动/未登录/浏览流程装配 |

进程级 `ApplicationRuntime` 持有会话、存储和服务入口。恢复保存的账号不等于服务器认证仍有效；账号级错误使用独立反馈，保留媒体内容和导航。切换账号重建其浏览与播放状态，窗口尺寸变化不重建账号会话。

## 常用命令

### 环境准备

使用仓库 Gradle Wrapper（8.14.3），安装 JDK 17 或更新的兼容版本及 Android SDK 37，并在本机配置 SDK 路径。CPF Kotlin/Compose 的固定版本见[版本目录](../gradle/libs.versions.toml)，不要单独升级其中一项。iOS 需要 Xcode；HarmonyOS 需要 DevEco Studio 及本地 MFPlayer SDK。

从仓库根目录执行：

```bash
./gradlew :check
node --test scripts/*.test.mjs scripts/*.test.cjs
./gradlew :composeApp:desktopTest :composeApp:testDebugUnitTest
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:compileKotlinIosArm64 :composeApp:compileKotlinOhosArm64
```

`:check` 包含架构、依赖、应用职责及 UI 约束门禁，不等于全模块行为测试或设备验收。修改特定模块时还需运行该模块的相关测试。

macOS 开发运行：`./gradlew :composeApp:run`；本机包：`./gradlew :composeApp:createDistributable`。默认凭据后端为系统 Keychain。`-Pmafei.desktopDevelopmentStorage=true` 是需明确选择的本地开发明文后端，不能用于发布；其历史依据见 [验证历史](VALIDATION_HISTORY.md)。

HarmonyOS 先运行 `./gradlew :composeApp:publishDebugBinariesToHarmonyApp`，再按[宿主说明](../harmonyApp/README.md)配置 SDK、签名并打包 HAP。`libkn.so` 与 `libkn_api.h` 必须来自同一次链接，不能拼接不同构建产物。iOS 的 Xcode 宿主说明见 [iosApp](../iosApp/README.md)。Kotlin 编译成功不代表 HAP/Xcode 打包、设备播放或视觉验收完成。

## 仓库目录与职责

仓库根目录是活动 KMP/CMP 工程：

```text
common/       公共基础、设计系统、Glass 与复用 UI
core/         领域、存储、序列化与安全凭据契约
providers/    媒体服务 SDK
data/         Provider 适配、Repository 与旧数据迁移
player/       统一播放器 API、核心、UI 与平台 runtime
platform/     网络、安全存储等平台实现
feature/      按业务域拆分的 Compose Feature
composeApp/   Android、iOS、macOS、HarmonyOS 应用装配
harmonyApp/   CPF Compose 的 HarmonyOS ARM64 薄宿主
iosApp/       iOS 原生宿主
legacy/       旧 HarmonyOS ArkTS 行为与数据兼容参考
```

`App.kt` 只装配根流程，业务请求由对应控制器和 Repository 负责；页面绑定不能取得整个运行时。`harmonyApp/` 只负责 HAP、Ability 生命周期、N-API、资源及系统桥接，不新增 ArkUI 业务页面。[旧版参考](../legacy/harmony-arkts-reference)默认只读，不参与根 Gradle 构建。

目标平台为 HarmonyOS ARM64 手机/平板/PC、Android ARM64、iOS/iPadOS ARM64 和 Apple Silicon macOS。x86/x64、Intel Mac、Windows、Linux 不在首发目标中；目标矩阵不代表逐端验收完成。

## 本地资料与依赖

签名资料、凭据、个人设备日志及未获授权分发的依赖不得加入提交。MFPlayer 是独立的闭源本地 SDK，不随当前源码树分发，也不属于本仓库开源许可；合法持有 SDK 的开发者按[本地前提](../harmonyApp/README.md#local-prerequisites)配置。

历史清理范围、提交号映射与本地备份边界见[历史清理记录](HISTORY_CLEANUP.md)。

## 验证状态

[2026-10-15 重构验收](REFACTOR_ACCEPTANCE.md)区分了纯提交源码和包含原有未提交改动的设备安装包。当前仍有播放与宽屏导航展示的已知限制，不能据此声明全产品无问题。

其中记录了 Android 模拟器解码失败与鸿蒙完整工作区包黑屏问题；追剧入口目前保持关闭。根 README 的截图仅展示已安装开发包的界面，不替代播放或逐端验收。

9 月的启动、登录、Keychain 和早期浏览阶段记录完整保存在 [VALIDATION_HISTORY.md](VALIDATION_HISTORY.md)，不再作为当前功能清单。
