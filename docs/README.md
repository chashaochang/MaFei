# 文档归属与搜索

新增长期架构说明放 `docs/architecture/`，确认后的产品/接口规格放 `docs/specs/`，可复查的验证记录放 `docs/validation/`，历史设计参考放 `docs/archive/`。以上目录和本索引入库；不要放凭据、服务器私密信息、临时日志或设备转储。

已有 43 份被跟踪的历史文档保持版本控制，不因 ignore 变化删除。`docs/superpowers/` 等过程目录及其他本地资料继续忽略；不要用 `git add -f docs` 打包整个目录。新文档默认使用上述明确目录。

根 `.ignore` 隔离旧 ArkTS 与归档参考；普通 `rg` 从活动 CMP 源码开始。需要旧版行为时显式运行 `rg --no-ignore PATTERN legacy/harmony-arkts-reference/`；需要归档参考时同样显式指定 `docs/archive/`。忽略只影响搜索，不修改 Git 跟踪或构建依赖。

`output/`、`outputs/`、`tmp/`、`generated-images/` 和 `mafei-layout.json` 是本地生成内容。产品资源放所属模块 `src` 资源目录。`runscript/` 是受维护的跨平台构建/运行入口，继续入库。

应用重构的历史验收见 [验证历史](validation/VALIDATION_HISTORY.md)。历史 SHA 处理见 [署名记录](agent-git-identities-2026-09-08.md) 和 [SDK 清理](../composeApp/HISTORY_CLEANUP.md)。
