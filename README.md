# MaFei HarmonyOS 工程

`ohosApp` 是 MaFei 的 HarmonyOS 应用工程，负责服务器连接、媒体浏览、详情展示、播放界面和系统集成。

## 开发环境

- DevEco Studio（HarmonyOS API 26）
- API 24 及以上设备
- 通过 DevEco Studio/ohpm 管理公开依赖

签名配置只应在本机维护。仓库中的 `build-profile.json5` 不包含任何证书、Profile、口令或本机路径。

## 播放器

应用目前通过 `MFPlayer` 完成正式播放。公开源码只包含与播放器交互的 ArkTS 接入层；闭源 SDK 二进制不在仓库中。拥有合法 SDK 的内部开发环境请阅读 [`entry/libs/README.md`](entry/libs/README.md)。

## 开源边界

- 可公开：应用页面、主题、数据层、服务接口适配和测试代码。
- 不公开：签名材料、私有播放器 SDK、内部工作文档与用户/服务器数据。

详细的公开构建与提交规则见根目录的 [`docs/README.md`](../docs/README.md)。
