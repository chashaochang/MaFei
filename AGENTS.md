# MaFei 2.0 开发约束

## 苹果风格 UI 强制约束

1. MaFei 的产品视觉与交互以苹果 iOS / iPadOS / macOS 风格为准，适用于 Android、HarmonyOS、iOS 和桌面全部端。Compose 是实现技术，不是采用 Google Material Design 风格的理由。除用户明确批准外，不得改为 Material 风格。
2. 该约束覆盖所有可见界面，包括确认弹窗、菜单、Sheet、输入框、按钮、开关、导航、空状态和反馈；不得只把首页或底栏做成苹果风格，其他界面直接使用 Material 默认外观。
3. 禁止直接交付 Material 默认的 `AlertDialog`、`Button`、`TextField`、`DropdownMenu`、`NavigationBar` 等产品外观，包括其默认排版、操作区、配色、形状和 ripple。允许复用底层布局、状态、语义和无障碍能力，但最终呈现与交互必须遵守 MaFei 的苹果风格设计；仅换颜色、圆角或加玻璃背景不算适配。
4. 确认弹窗采用克制、紧凑、层级清楚的苹果风格：标题与正文的对齐、操作分组、分隔和按压反馈须整体设计。菜单必须贴合触发控件或采用明确的 Sheet / Popover 呈现，并按手机、平板和桌面交互适配；禁止把默认 Material 弹窗套一层玻璃后直接交付。
5. 登录失效等账号级提醒不得插入首页、列表或媒体内容流。使用独立的账号反馈入口；需要用户处理时使用符合上述风格的模态反馈，保留内容、导航与滚动状态，不擅自退出账号或跳转登录页。
6. 修改前必须确认对应的苹果交互模式、用户已认可的 MaFei 实现或用户指定的旧鸿蒙参考。优先复用共享组件，但已有组件的名字包含 `MaFei` / `Glass` 不代表外观合格；不符合本约束时应修正共享组件，不能继续传播，也不能在页面里另写一套。
7. 苹果风格不等于所有控件都使用液态玻璃。材质必须遵守下方液态玻璃门禁，以及用户选择的半透明 / 高斯模糊 / 液态玻璃风格；降级只改变材质，不得退回 Material 默认布局与交互。
8. Review 必须核对排版密度、操作层级、菜单锚点、动效、触控/键鼠反馈及各尺寸呈现。发现 Material 默认视觉或未经用户批准的重新设计，视为未完成；编译、单测通过或“复用了现有组件”都不能豁免。不得把未经用户认可的结果描述为视觉已收口。

## 液态玻璃 UI 前置门禁

1. 修改 `common/ui`、`common/design-system`、`common/glass`、`feature/**` 或 `composeApp` 中任何可见界面前，必须先阅读根目录 [`SKILL.md`](SKILL.md)。
2. 液态玻璃不是“低透明度 Surface + 圆角 + 描边”。需要同时满足实时背景采样、光学材质、组件角色、交互形变和内容可读性。缺少任一项时不得在说明或提交中称为液态玻璃。
3. 禁止把页面 section、列表、海报网格、状态提示和普通内容容器全部改成玻璃卡片。玻璃只用于导航、浮层、播放器、焦点控制和确有层级意义的悬浮表面。
4. 禁止用 `AppGlassCard`、`MaFeiSurface` 或 Material3 `Surface` 批量包裹页面来代替产品设计。先确定信息层级、媒体主视觉、滚动节奏和交互，再选择材质。
5. 新增或重做交互玻璃组件时，优先复用已验证的 Liquid Button、Toggle、Slider、Bottom Tabs 和 Mini Player 模式；不得自行用静态透明背景仿制。
6. UI 完成声明必须包含手机和宽屏截图、浅色和深色、静止和按压/拖动状态。仅编译通过不算视觉验收。
7. 确认/提示弹窗必须使用现成的标准液态玻璃提示框：页面调用 `common/ui/.../MaFeiGlassConfirmDialog.kt`，该宿主只负责遮罩、焦点、关闭和事件，展示必须委托 `common/glass/.../components/LiquidAlertDialog.kt`（源自 Liquid Glass Demo 的 `DialogContent.kt`）。禁止另写 Material 弹窗、手排按钮行，或以“苹果风格”为由再创造一套提示框。独立窗口的 `LegacyConfirmDialog` 也复用同一个面板，仅在没有同窗口 backdrop 时降级材质；不能复制布局。参考布局和光学参数不得擅自修改。

## Git Agent 独立署名

1. 用户要求今后 Codex、GLM 与 Pony Ma 分开署名；Author 和 Committer 都必须明确设置，不能只写 Co-authored-by：Codex 使用 `Codex <codex@mafei.invalid>`，GLM 使用 `GLM <glm@mafei.invalid>`，Pony Ma 使用 `Pony Ma <ponyma@mafei.invalid>`。这些是项目内身份，不是官方 GitHub bot 账号。
2. 多 Agent 共享工作区，禁止为切换身份修改全局或仓库共享 `user.name` / `user.email`。每次提交使用命令级 `GIT_AUTHOR_NAME`、`GIT_AUTHOR_EMAIL`、`GIT_COMMITTER_NAME`、`GIT_COMMITTER_EMAIL`，并显式传 `git commit --author='对应名称 <对应邮箱>'`，避免 amend 继承错误作者。
3. 只暂存自己负责的明确路径，保留他人改动；提交前重读 HEAD/status，提交后检查 `git log -1 --format='%h Author: %an <%ae> Committer: %cn <%ce>'`。
4. 每个新提交都必须在提交正文中包含以下 Git trailer，记录实际执行提交的 Agent 和模型；模型名称必须使用运行时提供的准确 ID，不得猜测或填写泛称：
   ```text
   Agent: Codex
   Model: gpt-6-sol
   Reasoning: high
   ```
   `Agent` 必须与提交署名一致；`Model` 必填；`Reasoning` 可选但建议填写。GLM、Pony Ma 提交时分别填写自身身份和实际模型 ID。
5. 已确认 CMP 历史已按用户授权改写，原历史有备份；旧冻结 SHA 使用 `docs/agent-git-identities-2026-09-08.md` 映射，不得恢复旧提交链或推送备份分支。进一步改写历史、删除备份或强推必须另获用户授权。
6. 每个独立修复或功能完成并通过相应验证后，立即单独提交，再开始下一项；不得积压到整批任务结束，也不得把无关任务或其他 Agent 的改动混入同一提交。文件中存在多项改动时按 hunk 暂存。提交信息如实区分编译、测试和真机验证结果。

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

## 平板/大屏侧栏与收起态顶部栏契约

这是 MaFei 参考 iPadOS 的固定导航规则。侧栏和顶部栏是一一对应的两种呈现，不能
各自重新编排入口，也不能把“我的”复制成顶部 Tab。

- 展开侧栏的顺序固定为：搜索、首页、收藏；其下是“我的媒体库”分组和多个二级媒体库。
- 侧栏收起且处于一级页面时，顶部栏至少包含：`首页`、`收藏`、搜索图标。
- 用户访问过具体二级媒体库后，在收藏后保留最近选择的媒体库名称；切到首页、收藏、搜索
  不移除这项，点击直接回到该媒体库。切到另一个媒体库才替换此项，切换账号则重新记录。
  从未选择二级媒体库时不显示“媒体库”占位项。保留的入口与当前高亮页是独立状态。
- 顶部栏最后一项只有搜索图标，没有“搜索”文字。搜索是动作入口，不能因为搜索路由
  被渲染在首页内容宿主中就把首页标记为选中。
- 顶部栏左侧的展开侧栏按钮是独立的壳层控制，不计入上述 Tab 顺序。
- “我的”属于账号区/独立账号动作，不进入收起态顶部 Tab。二级详情等内容栈激活时隐藏
  一级顶部栏；大屏侧栏若原本收起，则只显示 56dp 紧凑图标栏。搜索是收起态顶栏最后的
  图标动作，不属于这个二级内容栈：进入搜索后顶栏仍保留且搜索图标保持选中。
- 大屏一级页面只要显示上述顶栏，必须使用双层头部：第一层是顶栏，第二层是页面自己的
  标题/操作栏，页面内容必须从第二层下面开始布局；不得让列表或标题从顶栏下面穿过去。
- 大屏搜索页必须显示独立的“搜索”标题，输入框位于标题下方，不能用输入框替换标题。
  手机搜索页仍沿用返回按钮与输入框同一行的布局。
- 二级页面必须始终有自己的标题栏。大屏二级页可以没有返回按钮（返回由壳层/系统导航负责），
  但不能因为 `onBack == null` 而连标题栏一起省略；搜索在大屏属于一级动作，不显示返回按钮。
- 窗口尺寸变化只重算壳层形态，必须保留当前媒体库选择、路由栈和滚动状态。

实现入口：`common/ui/.../MaFeiAdaptiveShell.kt` 负责壳层呈现，
`composeApp/.../App.kt` 负责把当前二级媒体库映射到顶部第三项。修改前先对照
`docs/superpowers/specs/2026-09-02-pad-secondary-navigation-design.md` 的完整状态机。

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
