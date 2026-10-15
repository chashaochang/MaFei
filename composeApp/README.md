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

从仓库根目录执行：

```bash
./gradlew :check
node --test scripts/*.test.mjs scripts/*.test.cjs
./gradlew :composeApp:desktopTest :composeApp:testDebugUnitTest
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:compileKotlinIosArm64 :composeApp:compileKotlinOhosArm64
```

macOS 开发运行：`./gradlew :composeApp:run`；本机包：`./gradlew :composeApp:createDistributable`。默认凭据后端为系统 Keychain。`-Pmafei.desktopDevelopmentStorage=true` 是需明确选择的本地开发明文后端，不能用于发布；其历史依据见 [验证历史](VALIDATION_HISTORY.md)。

HarmonyOS 链接、HAP 打包、SDK 和签名要求见 [宿主说明](../harmonyApp/README.md)。iOS 的 Xcode 宿主说明见 [iosApp](../iosApp/README.md)。Kotlin 编译成功不代表 HAP/Xcode 打包、设备播放或视觉验收完成。

## 验证状态

[2026-10-15 重构验收](REFACTOR_ACCEPTANCE.md)区分了纯提交源码和包含原有未提交改动的设备安装包。当前仍有播放与宽屏导航展示的已知限制，不能据此声明全产品无问题。

9 月的启动、登录、Keychain 和早期浏览阶段记录完整保存在 [VALIDATION_HISTORY.md](VALIDATION_HISTORY.md)，不再作为当前功能清单。
