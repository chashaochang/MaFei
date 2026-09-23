# MaFei 2.0 开发约束

## 液态玻璃 UI 前置门禁

1. 修改 `common/ui`、`common/design-system`、`common/glass`、`feature/**` 或 `composeApp` 中任何可见界面前，必须先阅读根目录 [`SKILL.md`](SKILL.md)。
2. 液态玻璃不是“低透明度 Surface + 圆角 + 描边”。需要同时满足实时背景采样、光学材质、组件角色、交互形变和内容可读性。缺少任一项时不得在说明或提交中称为液态玻璃。
3. 禁止把页面 section、列表、海报网格、状态提示和普通内容容器全部改成玻璃卡片。玻璃只用于导航、浮层、播放器、焦点控制和确有层级意义的悬浮表面。
4. 禁止用 `AppGlassCard`、`MaFeiSurface` 或 Material3 `Surface` 批量包裹页面来代替产品设计。先确定信息层级、媒体主视觉、滚动节奏和交互，再选择材质。
5. 新增或重做交互玻璃组件时，优先复用已验证的 Liquid Button、Toggle、Slider、Bottom Tabs 和 Mini Player 模式；不得自行用静态透明背景仿制。
6. UI 完成声明必须包含手机和宽屏截图、浅色和深色、静止和按压/拖动状态。仅编译通过不算视觉验收。

## Git Agent 独立署名

1. 用户要求今后 Codex、GLM 与 Pony Ma 分开署名；Author 和 Committer 都必须明确设置，不能只写 Co-authored-by：Codex 使用 `Codex <codex@mafei.invalid>`，GLM 使用 `GLM <glm@mafei.invalid>`，Pony Ma 使用 `Pony Ma <ponyma@mafei.invalid>`。这些是项目内身份，不是官方 GitHub bot 账号。
2. 多 Agent 共享工作区，禁止为切换身份修改全局或仓库共享 `user.name` / `user.email`。每次提交使用命令级 `GIT_AUTHOR_NAME`、`GIT_AUTHOR_EMAIL`、`GIT_COMMITTER_NAME`、`GIT_COMMITTER_EMAIL`，并显式传 `git commit --author='对应名称 <对应邮箱>'`，避免 amend 继承错误作者。
3. 只暂存自己负责的明确路径，保留他人改动；提交前重读 HEAD/status，提交后检查 `git log -1 --format='%h Author: %an <%ae> Committer: %cn <%ce>'`。
4. 已确认 CMP 历史已按用户授权改写，原历史有备份；旧冻结 SHA 使用 `docs/agent-git-identities-2026-09-08.md` 映射，不得恢复旧提交链或推送备份分支。进一步改写历史、删除备份或强推必须另获用户授权。

## CMP 与 HarmonyOS 边界

1. 活动应用以根目录 Kotlin Multiplatform / Compose Multiplatform 工程为准。
2. `harmonyApp/` 必须保持 CPF Compose 薄宿主，只负责 HAP、Ability 生命周期、N-API、资源和系统能力桥接；不得把业务页面重新实现为 ArkUI。
3. `legacy/harmony-arkts-reference/` 是旧版行为、接口和数据库兼容参考，默认只读，不参与根 Gradle 构建。
4. `libkn.so` 与 `libkn_api.h` 必须来自同一次 `link*SharedOhosArm64` 产物，HarmonyOS 只打包 `arm64-v8a`。

## 共享 UI 单一来源

1. 同一业务实体或同一交互模式在两个及以上页面出现时，列表、卡片、空状态、确认反馈和动画必须只有一个展示实现；页面只负责提供状态、事件和导航。
2. 跨 Feature 复用的展示模型与组件放入 `common/ui` 或经评审确定的专用共享模块。禁止为了让单个 Feature 自闭环而复制另一个 Feature 的布局、动画或分组逻辑。
3. 新建页面组件前必须先检索现有实现。已有组件不能满足需求时应扩展其明确的可配置边界，不得复制后局部修改形成第二套实现。
4. 允许页面保留不同的业务外壳，例如标题栏、背景、路由和权限判断；业务外壳不得重新实现共享内容区。
5. Review 必须检查重复实现与状态分叉；同一交互只在其中一个入口生效，视为未完成，不能以单端编译通过验收。

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
