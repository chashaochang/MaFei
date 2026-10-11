# 马飞 2.0

马飞 2.0 是基于 Kotlin Multiplatform 与 Compose Multiplatform 的跨平台媒体客户端。仓库根目录现在就是活动 Gradle/CMP 工程，不再使用额外的 `cmp/` 外层目录。

## 首发平台

- HarmonyOS ARM64：手机、平板与 HarmonyOS PC
- Android ARM64：手机与平板
- iOS / iPadOS ARM64
- Apple Silicon macOS：Compose Desktop/JVM

x64、x86、Intel Mac、Windows 与 Linux 不在首发目标中。

## 目录结构

```text
common/       公共基础、设计系统、Glass 与复用 UI
core/         领域、存储、序列化与安全凭据契约
providers/    Jellyfin 与飞牛独立 SDK
data/         Provider 适配、Repository 与旧数据迁移
player/       跨平台播放器统一 API、核心、UI 与平台 runtime
platform/     网络、安全存储等平台实现
feature/      按业务域拆分的 Compose Feature
composeApp/   Android、iOS、macOS、HarmonyOS 共享应用入口
harmonyApp/   CPF Compose 的 HarmonyOS ARM64 薄宿主
iosApp/       iOS 原生宿主
legacy/       旧 HarmonyOS ArkTS 行为与数据兼容参考
```

`harmonyApp/` 只负责 HAP、Ability 生命周期、N-API、资源和系统能力桥接，不承载 ArkTS 业务页面。当前 ArkTS 版本已归档到 [`legacy/harmony-arkts-reference`](legacy/harmony-arkts-reference)，用于核对产品行为、旧 RDB/Preferences 和覆盖升级兼容逻辑。

## 常用验证

```bash
./gradlew projects
./gradlew verifyArchitecture
./gradlew :composeApp:compileDebugKotlinAndroid
```

HarmonyOS 构建必须先由 Gradle 发布同一次链接产生的 `libkn.so` 与 `libkn_api.h`，再由 `harmonyApp` 运行 Hvigor/HAP 构建。

## 开发分工

- UI/CMP：`common`、`feature`、`composeApp`、`harmonyApp`
- 非 UI：`core`、`providers`、`data`、`player`、`platform`
- 根 Gradle 与跨模块依赖由集成方统一维护

详细架构、HarmonyOS 可行性和非 UI 交接约束位于本地 `docs/superpowers/`。
