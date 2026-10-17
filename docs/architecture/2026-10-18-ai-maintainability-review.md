# Claude 项目可维护性 review 核实与整改（2026-10-18）

范围：验证用户转述的审查结论，整改成立的搜索、规则发现、源码组织、构建和自动门禁问题。保持产品行为和已确认视觉；不借结构整理修复其他业务缺陷、升级依赖或发布。

## 核实结果

| 审查意见 | 核实与处理 |
| --- | --- |
| legacy 占比大、临时文件污染搜索 | 成立：基线 2249 个 tracked 文件中 1096 个属于 legacy。增加根 `.ignore` 隔离旧版与归档；需要参考时显式 `rg --no-ignore`。这是维护优先项，不直接等同线上 P0。其他工具是否读取 `.ignore` 取决于工具实现。 |
| outputs/tmp/generated-images/布局树未忽略 | 成立，已补 `.gitignore`。`.tmp`、diagnosis 继续本地保留，未擅自删除。 |
| output 和 runscript 都应移走 | 部分成立：四张 output 概念图按原字节和 SHA 清单移到 docs/archive；runscript 是仍在使用的运行入口，保留。 |
| 大文件不利于定位和审查 | 成立，六个重点文件按稳定职责拆分，保留 API/状态/调用顺序。首页和播放器大入口仍有相互关联的状态装配，未为行数新造全局参数容器。 |
| 根规则过长、历史与规范混杂 | 成立。根约 19KB→4KB；完整 UI 规范集中在 common/ui/UI_POLICY.md，各范围 AGENTS 只做必读索引。历史验证移入 docs/validation；署名和历史禁令仍由根索引发现。 |
| Agent 入口不一致、旧 ohosApp 路径失效 | 成立。新增 CLAUDE.md，Windsurf/OpenCode 入口指向根 AGENTS，HarmonyOS 指向真实 harmonyApp。旧私有 .deveco-flow 不再是入口依赖。 |
| SKILL 根位置不标准 | 不是功能错误，仓库显式入口可以读取根 Skill；本轮仍将唯一版本归到 common/glass/SKILL.md，并移除作者机器绝对参考路径，改显式环境变量。没有迁移成某一家工具独占的规范。 |
| docs 全量忽略但 43 份已跟踪 | 成立。保留历史跟踪文档；明确开放 architecture/specs/validation/archive 和索引，其他过程与私有材料继续忽略。 |
| feature/home 承担浏览控制器 | 成立。迁到纯 Kotlin 的 feature/browse-core，测试随实现移动；为 API 兼容保留既有 package，不保留第二份实现。 |
| 五个空模块令人误判 | 成立，逐个补充“预留、未实现”及实际入口说明。保留现有构建边界，未用目录名推断已支持能力。 |
| 应用 Route 分散 | 成立，7 个文件逐字节归入 settings、account、browse/entries，包名与类型身份保持。 |
| 根构建根据路径隐式选目标、版本重复 | 成立。74 个既有库显式选择六类 mafei.kmp.* 约定，应用保留定制装配；新增 browse-core 也显式声明。CPF 与 Desktop 的必要版本差异保留，声明集中 catalog。 |
| 检查只在本地运行 | 成立，加入 CI 和可便携安装的 Node 测试依赖；远端 runner 执行证据与本地执行分开。 |
| 全限定名/typealias 可绕过源码边界 | 不成立：原脚本扫描代码正文中的完整类型引用，原测试已有 FQN 负例。本轮补 typealias 回归，并补实际 Gradle 声明依赖图检查（含生产配置继承）。 |
| 应使用 worktree 避免共享 index | 本轮采用独立 worktree 并行，串行合流；48 个既有 tracked 脏改和未跟踪资料不混入整改提交。未盲删 index.lock，也未改写历史或推送。 |

## 结构变化

| 主文件 | 提交基线行数 | 整改提交行数 |
| --- | ---: | ---: |
| HomeScreen | 2632 | 1125 |
| DetailScreen | 2591 | 296 |
| NowPlayingScreen | 2761 | 965 |
| MaFeiAdaptiveShell | 1686 | 536 |
| FeiniuMediaProviderAdapter | 2251 | 90 |
| ApplicationRuntime | 960 | 559 |

行数采用同一 clean 提交基线；保留用户未提交修改后，首页等文件可能略有增加。四个 UI 文件共 221 个顶层声明逐个正文对照，只有文件归属、必要 private→internal 和 imports 发生变化。原脏 Home/Detail 的 133 个声明另外迁移到对应新文件，保持未提交归属。

源码规模门禁覆盖全部活动 Kotlin 生产文件，默认 800 行；原有超限文件设显式上限，并相对 Git base 检查只能下降。六个重点文件之外的既有超限也被纳入清单，未宣称全仓已经没有大文件。规则和后续拆分方法见 [源码规模门禁](source-size.md)。

浏览职责见 [browse-core](../../feature/browse-core/README.md)，Runtime 见 [session](../../composeApp/src/commonMain/kotlin/cn/mafei/cmp/session/README.md)，飞牛见 [adapter-feiniu](../../data/adapter-feiniu/README.md)，构建目标和验证入口见 [build-logic](../../build-logic/README.md)。

## 验证范围

- 纯提交源码：976 次行为测试执行、4 项 Gradle TestKit、104 项 Node 测试通过，iOS ARM64 / OHOS ARM64 Kotlin 编译通过。
- 恢复原修改的完整工作区：1030 次行为测试执行、4 项 TestKit、110 项 Node 测试通过；根 `:check`（含源码/应用/真实依赖图/规模/Material/SVG 门禁）、iOS/OHOS Kotlin 编译通过。
- 行为测试次数包含 Desktop 与 Android JVM 对同一共用测试的分别执行，不代表独立场景数。覆盖应用、browse-core、首页/详情/搜索/设置、共享 UI/玻璃、播放器、仓储及两个 Adapter；不是声称全仓所有测试任务都已运行。
- CI 工作流在本机 `CI=true`、禁用 `mavenLocal()` 条件下执行成功；GitHub 托管 runner 未触发。未执行原生链接、HAP/IPA 打包、服务器、设备或视觉验收。
- 四个 UI 文件的声明等价对照、Runtime/Feiniu 独立复核、所有规则链接与图片 SHA 核对通过。正常 `rg` 不再进入 legacy、归档或本地产物。
- 初始 48 个 tracked 脏文件等价恢复为拆分后的 53 个脏文件，另有 48 个原未跟踪文件的存在、大小和修改时间逐项一致。搜索模块原有未提交 UI 测试依赖保留，并改用相同版本的 catalog 引用。没有将这些改动放进整改提交。

| 完整工作区任务 | 通过次数 |
| --- | ---: |
| composeApp Desktop / Android JVM | 165 / 146 |
| browse-core Desktop / Android JVM | 61 / 61 |
| 首页 / 详情 / 搜索 / 设置 Desktop | 49 / 28 / 3 / 18 |
| common UI / glass Desktop | 74 / 22 |
| player UI / core Desktop | 81 / 40 |
| repositories / Jellyfin Adapter / Feiniu Adapter Desktop | 88 / 108 / 86 |
| Gradle TestKit / Node 脚本 | 4 / 110 |

当前导航文档记载的既有宽屏展示差异保持原样，不借结构拆分改变产品含义。仍有 8 个明确列出的既有文件规模例外，不能把这次整改描述为全仓文件都小于 800 行。

## 提交与工作区

代码验证基点为 `db59e3e9`，从 `0a5ffe0b` 起分职责提交并快进合回 `dev`；以下最终记录提交只调整文档。未推送、发布或改写既有历史。

原始未提交修改另存为本地安全 stash：`862d4a44c9625514100f4e8c213e8cb942c0546a`，说明为 `Codex preserve pre-existing MaFei work before 2026-10-18 maintainability integration`。工作区已经恢复这些修改，这份 stash 仅作原始快照备份，不应再次直接应用到当前工作区。


```text
c9bb56d6 chore(agent): 整理任务规则与搜索入口
84e43f9e docs(modules): 标明五个预留模块与实际实现入口
e88b6026 build(quality): 为活动源码增加只降不升的规模预算
87f40daa build: 显式声明 KMP 目标约定并集中平台版本
fc6b7694 refactor(runtime): 按生命周期拆分进程服务职责
c2153bf7 refactor(home): 按首页展示职责拆分辅助实现
672c7cc4 refactor(detail): 按详情展示职责拆分页面
102fbf11 refactor(browser): 将浏览控制器与测试迁入独立共享模块
3898fd2f build: 在实际 Gradle 依赖图执行模块边界门禁
ff174a92 refactor(feiniu): 按目录详情首页和播放拆分适配器
9ba93ba3 refactor(app): 将路由内容归入对应应用目录
b36c2b8f refactor(player): 按控件与浮层职责拆分播放器展示
5d76a83f refactor(ui): 按导航表面拆分自适应壳层
09ef42c9 build(quality): 随职责拆分下调现有文件规模预算
db59e3e9 ci: 接入可便携执行的架构与核心行为验证
```
