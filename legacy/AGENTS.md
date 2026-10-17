# 旧版参考规则

本目录默认只读，不属于活动应用；只有明确修正旧版参考或迁移夹具时才能修改。搜索使用 `rg --no-ignore 关键词 legacy/harmony-arkts-reference/`，不要移除根 `.ignore` 的隔离。

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
