# 马飞 2.0

马飞是连接 Jellyfin 与飞牛影视的媒体客户端。本开发线采用 Kotlin Multiplatform / Compose Multiplatform，仓库根目录就是活动工程；HarmonyOS 通过 CPF Compose 薄宿主承载共享界面。

[华为应用市场下载](https://appgallery.huawei.com/app/detail?id=cn.xiaobai.mafei)（已发布版本与本开发线的验证状态分别管理）。

## 从这里开始

- [应用架构与修改入口](composeApp/ARCHITECTURE.md)：按需求定位状态所有者、路由绑定与测试。
- [应用运行与验证](composeApp/README.md)：平台入口、常用命令和会话边界。
- [开发约束](AGENTS.md)：共享 UI、苹果风格、导航、Git 署名和任务持续执行规则。
- [本轮重构验收记录](composeApp/REFACTOR_ACCEPTANCE.md)：自动测试、设备结果和已知问题。
- [HarmonyOS 宿主](harmonyApp/README.md)、[iOS 宿主](iosApp/README.md)：各端环境与打包要求。

当前开发线包含账号恢复/切换、媒体浏览、搜索、详情和播放接入。功能存在不等于所有平台均已验收；尤其播放仍有已记录的 Android 模拟器解码失败与鸿蒙完整工作区包黑屏问题。追剧入口目前保持关闭。

## 目录与职责

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

`App.kt` 只装配根流程，业务请求由对应控制器和 Repository 负责；页面绑定不能取得整个运行时。
`harmonyApp/` 只负责 HAP、Ability 生命周期、N-API、资源及系统桥接，不新增 ArkUI 业务页面。
[旧版参考](legacy/harmony-arkts-reference) 默认只读，不参与根 Gradle 构建。

目标平台为 HarmonyOS ARM64 手机/平板/PC、Android ARM64、iOS/iPadOS ARM64 和 Apple Silicon macOS。x86/x64、Intel Mac、Windows、Linux 不在首发目标中；目标矩阵不代表逐端验收完成。

## 构建与检查

使用仓库 Gradle Wrapper（8.14.3），安装 JDK 17 或更新的兼容版本及 Android SDK 37，并在本机配置 SDK 路径。CPF Kotlin/Compose 的固定版本见 [版本目录](gradle/libs.versions.toml)，不要单独升级其中一项。iOS 需要 Xcode；HarmonyOS 需要 DevEco Studio 及本地 MFPlayer SDK。

在仓库根目录运行：

```bash
./gradlew :check
node --test scripts/*.test.mjs scripts/*.test.cjs
./gradlew :composeApp:desktopTest :composeApp:testDebugUnitTest
./gradlew :composeApp:assembleDebug
```

`:check` 包含架构、依赖、应用职责及 UI 约束门禁，不等于全模块行为测试或设备验收。修改特定模块时还需运行该模块的相关测试。

HarmonyOS 先运行 `./gradlew :composeApp:publishDebugBinariesToHarmonyApp`，再按[宿主说明](harmonyApp/README.md)打包。`libkn.so` 与 `libkn_api.h` 必须来自同一次链接，不能拼接不同构建产物。

## 本地资料与许可证

签名资料、凭据、个人设备日志及未获授权分发的依赖不得加入提交。MFPlayer 是独立的闭源本地 SDK，不随当前源码树分发，也不属于本仓库开源许可；合法持有 SDK 的开发者按[本地前提](harmonyApp/README.md#local-prerequisites)配置。

除单独声明外，代码按 [GPL-3.0-or-later](LICENSE) 发布。第三方组件遵循各自许可证。
