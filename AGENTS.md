# MaFei 2.0 开发约束

## CMP 与 HarmonyOS 边界

1. 活动应用以根目录 Kotlin Multiplatform / Compose Multiplatform 工程为准。
2. `harmonyApp/` 必须保持 CPF Compose 薄宿主，只负责 HAP、Ability 生命周期、N-API、资源和系统能力桥接；不得把业务页面重新实现为 ArkUI。
3. `legacy/harmony-arkts-reference/` 是旧版行为、接口和数据库兼容参考，默认只读，不参与根 Gradle 构建。
4. `libkn.so` 与 `libkn_api.h` 必须来自同一次 `link*SharedOhosArm64` 产物，HarmonyOS 只打包 `arm64-v8a`。

## 旧 ArkUI 参考代码规则

只有在明确修正旧版参考或迁移夹具时才允许修改 `legacy/harmony-arkts-reference/`：

1. 普通 UI、页面片段和可复用内容使用 `@ComponentV2` 独立组件，通过 `@Param`、`@Event` 和可追踪状态传递数据与事件。
2. 只有 ArkUI 或第三方组件明确要求 `CustomBuilder`/Builder 插槽时，才允许保留带 `CustomBuilderAdapter` ID 的薄 `@Builder` 适配壳。
3. 禁止新增 `@BuilderParam`、`CustomBuilder` 类型和 `wrapBuilder`，也禁止使用 Builder 保存运行时状态或承载大段业务布局。
4. 修改旧 ArkUI 参考后必须运行：

```bash
node legacy/harmony-arkts-reference/scripts/verify_no_new_builders.mjs
```

门禁失败时不得通过无依据白名单或提高全局数量绕过。

## 自动化验证

模拟器或真机自动化点击前，必须先读取 UI 布局树中目标控件的真实 `bounds`，点击有效区域中心或明确的内部安全点。禁止根据截图或肉眼位置估算坐标，也禁止在未核对布局树时把误点判断为交互问题。
