# MaFei 开发约束

## ArkUI 组件规则

1. 普通 UI、新增页面片段和可复用内容必须使用 `@ComponentV2` 独立组件，通过 `@Param`、`@Event` 和可追踪状态传递数据与事件。
2. 只有 ArkUI 或第三方组件明确要求 `CustomBuilder`/Builder 插槽时，才允许保留薄 `@Builder` 适配壳，例如 `bindSheet`、自定义 `bindMenu`、`openCustomDialog`、`ListItemGroup.header/footer`、第三方 Grid/List Item、HDS `tabBar`、`AVCastPicker.customPicker` 和自定义 Refresh 内容。
3. 每个允许的适配壳必须紧邻 `// CustomBuilderAdapter: <唯一 ID>` 标记，且 ID 与文件路径登记在 `scripts/legacy_builder_baseline.json`。适配壳内部只负责挂载现有 `@ComponentV2` 或框架要求的最小内容，不承载页面业务和大段布局。
4. 禁止新增 `@BuilderParam`、`CustomBuilder` 类型和 `wrapBuilder`。禁止用 Builder 参数、闭包或普通字段保存断点、方向、主题、登录态等运行时状态；这些状态必须来自 `@Trace`、`@Local` 或 `@Param` 的实时数据源。
5. 修改相关区域时，优先组件化普通 Builder；但不得删除框架插槽必须的 Builder 适配层，除非同时改掉该插槽用法。
6. 提交前必须运行 `node scripts/verify_no_new_builders.mjs`。门禁失败时不得通过新增无依据白名单或提高全局数量绕过；新增例外必须能指向具体框架插槽。

## 自动化验证

模拟器或真机自动化点击前，必须先读取 UI 布局树中目标控件的真实 `bounds`，点击有效区域中心或明确的内部安全点。禁止根据截图或肉眼位置估算坐标，也禁止在未核对布局树时把误点判断为交互问题。
