# MaFei

MaFei 是一款 HarmonyOS 媒体客户端，提供服务器连接、媒体浏览、详情展示、播放界面和系统集成能力。

本分支的仓库根目录就是 HarmonyOS 工程，可直接用 DevEco Studio 打开；不再保留额外的 `ohosApp/` 外层目录或内部文档目录。

## 开发环境

- DevEco Studio（HarmonyOS API 26）
- API 24 及以上设备
- 通过 DevEco Studio / ohpm 管理公开依赖

签名配置仅在本机维护。仓库中的 `build-profile.json5` 不包含证书、Profile、口令或本机路径。

## 播放器依赖

项目的正式播放能力依赖本地 `MFPlayer` SDK。公开源码仅包含 ArkTS 接入层，不包含 SDK 二进制；具备该依赖的开发环境可按 [`entry/libs/README.md`](entry/libs/README.md) 放置本地文件后再同步依赖。

## 公开边界

- 包含：应用页面、主题、数据层、服务接口适配和测试代码。
- 不包含：签名材料、播放器 SDK、内部工作文档与用户或服务器数据。
