# MaFei

MaFei 是一款 HarmonyOS 媒体客户端，提供服务器连接、媒体浏览、详情展示、播放界面和系统集成能力。

本分支的仓库根目录就是 HarmonyOS 工程，可直接用 DevEco Studio 打开；不再保留额外的 `ohosApp/` 外层目录或内部文档目录。

## 开发环境

- DevEco Studio（HarmonyOS API 26）
- API 24 及以上设备
- 通过 DevEco Studio / ohpm 管理公开依赖

签名配置仅在本机维护。仓库中的 `build-profile.json5` 不包含证书、Profile、口令或本机路径。

## 开发约束

- 普通 UI 必须拆为 `@ComponentV2` 独立组件；只有 ArkUI 或第三方控件明确要求 Builder 插槽时，允许保留带 `CustomBuilderAdapter` ID 的薄适配壳。
- 禁止 `@BuilderParam`、`CustomBuilder` 类型和 `wrapBuilder`，也禁止用 Builder 承载业务状态或大段页面布局。完整规则见 [`AGENTS.md`](AGENTS.md)。
- 提交前运行 `node scripts/verify_no_new_builders.mjs`，确认 Builder 适配壳与审查白名单精确一致。

## 播放器依赖

项目的正式播放能力依赖本地 `MFPlayer` SDK。公开源码仅包含 ArkTS 接入层，不包含 SDK 二进制；具备该依赖的开发环境可按 [`entry/libs/README.md`](entry/libs/README.md) 放置本地文件后再同步依赖。

## 公开边界

- 包含：应用页面、主题、数据层、服务接口适配和测试代码。
- 不包含：签名材料、播放器 SDK、内部工作文档与用户或服务器数据。
