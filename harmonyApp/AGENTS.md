# HarmonyOS 薄宿主

## CMP 与 HarmonyOS 边界

1. 活动应用以根目录 Kotlin Multiplatform / Compose Multiplatform 工程为准。
2. `harmonyApp/` 必须保持 CPF Compose 薄宿主，只负责 HAP、Ability 生命周期、N-API、资源和系统能力桥接；不得把业务页面重新实现为 ArkUI。
3. `legacy/harmony-arkts-reference/` 是旧版行为、接口和数据库兼容参考，默认只读，不参与根 Gradle 构建。
4. `libkn.so` 与 `libkn_api.h` 必须来自同一次 `link*SharedOhosArm64` 产物，HarmonyOS 只打包 `arm64-v8a`。

涉及可见界面时先读 [唯一 UI 规范](../common/ui/UI_POLICY.md)；不得把业务页面重新实现为 ArkUI。构建与桥接入口见 [README](README.md)。
