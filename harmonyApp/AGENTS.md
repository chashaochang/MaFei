# HarmonyOS 薄宿主

## CMP 与 HarmonyOS 边界

1. 活动应用以根目录 Kotlin Multiplatform / Compose Multiplatform 工程为准。
2. `harmonyApp/` 必须保持 CPF Compose 薄宿主，只负责 HAP、Ability 生命周期、N-API、资源和系统能力桥接；不得把业务页面重新实现为 ArkUI。
3. `legacy/harmony-arkts-reference/` 是旧版行为、接口和数据库兼容参考，默认只读，不参与根 Gradle 构建。
4. `libkn.so` 与 `libkn_api.h` 必须来自同一次 `link*SharedOhosArm64` 产物，HarmonyOS 只打包 `arm64-v8a`。
5. `AppScope/app.json5` 保留正式包名；Hvigor 在 Debug 构建时自动追加 `.dev`。本地 `development` 签名配置必须匹配开发包，Release 保留产品原签名。使用运行脚本或产物检查核对 HAP 包名与 `debug` 标志后再安装；不得卸载正式版来解决安装失败。配置步骤见 [开发包说明](DEVELOPMENT.md)。

涉及可见界面时先读 [唯一 UI 规范](../common/ui/UI_POLICY.md)；不得把业务页面重新实现为 ArkUI。构建与桥接入口见 [README](README.md)。
