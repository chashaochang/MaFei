# MaFei 开发入口

活动应用是根 Kotlin / Compose Multiplatform 工程。`harmonyApp/` 是 CPF Compose 薄宿主；`legacy/harmony-arkts-reference/` 默认只读，不参与根构建。功能是否实现以源码与验证为准，不以空模块名判断。

## 执行与协作

- 完整计划获授权后持续推进；阶段完成、测试通过、提交都只是中间节点。维护完整计划，最终逐项核对，不缩小范围或把剩余项改称后续工作。只在目标完成、用户暂停或必要外部条件阻断时停止，先做其余可推进项。
- 限定在已授权范围，不夹带无关重构。检查真实仓库根、HEAD、status，保留他人修改。并行修改或重叠文件优先使用独立 worktree；合并前检查基线和脏改，不能盲删 `index.lock`。
- 每项独立变更经适当验证立即单独提交；只暂存负责的路径或 hunk。提交后继续完整计划。

## 模块与必读索引

依赖方向：`core → providers → data → feature/common/player → composeApp`；平台能力在 `platform/*` 和 `player/runtime-*`。浏览请求、分页、缓存属于 `feature/browse-core` 的浏览控制器；Feature Screen 负责展示。

| 修改范围 | 动手前阅读 |
| --- | --- |
| 应用流程、路由、状态所有权 | [应用架构](composeApp/ARCHITECTURE.md)，导航目录 README |
| 任意模块的可见 UI、动画、交互（含平台入口） | [唯一 UI 规范](common/ui/UI_POLICY.md)；全部平台遵循已确认的苹果风格，禁止 Material 默认产品外观 |
| 玻璃材质、采样、降级、渲染性能 | [液态玻璃 Skill](common/glass/SKILL.md) |
| HarmonyOS 宿主、打包、桥接 | [宿主规则](harmonyApp/AGENTS.md) |
| 旧版参考或迁移夹具 | [旧版规则](legacy/AGENTS.md)，显式搜索旧版路径 |
| 文档、参考与验证记录 | [文档策略](docs/README.md) |
| 构建目标、版本与 CI | [构建约定](build-logic/README.md) 与 [版本目录](gradle/libs.versions.toml) |

`App.kt` 只装配根流程；`MediaBrowser` 保持兼容门面；媒体事实只通过统一读模型发布，控制器不互持。路由显式定义参数、来源、壳层映射；绑定仅接收需要的能力、存储与事件，不获取整个 `ApplicationRuntime`。不得放宽门禁或增加无依据例外。

## 验证与证据

- 流程或边界变更：`./gradlew verifyAppBoundaries verifyDependencyBoundaries`，并运行相关行为测试。完整门禁：`./gradlew :check`；脚本测试：`node --test scripts/*.test.mjs scripts/*.test.cjs`。
- 按受影响平台执行编译/测试；应用入口示例：`:composeApp:desktopTest`、`:composeApp:testDebugUnitTest`、`:composeApp:compileKotlinIosArm64`、`:composeApp:compileKotlinOhosArm64`。
- 源码、自动测试、模拟器、真机、服务器及视觉验收分开报告。编译不能证明视觉和设备行为；UI 变更遵循完整 UI 规范的验收要求。
- 自动化点击先读取布局树真实 `bounds`，点击内部有效点；禁止凭截图比例或肉眼估坐标，也不能据误点判断交互 Bug。

## Git 署名与历史

- Author 和 Committer 均显式设置：Codex `Codex <codex@mafei.invalid>`；GLM `GLM <glm@mafei.invalid>`；Pony Ma `Pony Ma <ponyma@mafei.invalid>`。这是项目身份。
- 每次提交使用命令级 `GIT_AUTHOR_NAME/EMAIL`、`GIT_COMMITTER_NAME/EMAIL`，并传 `git commit --author='名称 <邮箱>'`。禁止修改全局或共享仓库身份配置，不能只写 Co-authored-by。
- 提交正文必须有 `Agent: 对应 Agent`、`Model: 运行时提供的准确模型 ID`；建议有 `Reasoning: 实际级别`。不能猜模型或照抄示例。提交后检查 `git log -1 --format='%h Author: %an <%ae> Committer: %cn <%ce>'`。
- 不恢复已清理的旧历史、不推送备份分支。进一步历史改写、删除备份、强推须单独授权；既有 SHA 映射见 [署名记录](docs/agent-git-identities-2026-09-08.md) 与 [私有 SDK 历史清理](composeApp/HISTORY_CLEANUP.md)。提交说明如实区分测试、编译和真机结果。
