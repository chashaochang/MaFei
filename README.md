# MaFei

MaFei 是一个 Jellyfin 第三方客户端项目。

当前仓库以 HarmonyOS 版本为主，同时保留了 `2.0` 跨平台重构所需的 Kotlin Multiplatform 基础工程。

应用商店：

- 华为应用市场  
  https://appgallery.huawei.com/app/detail?id=cn.xiaobai.mafei

## 特性

当前 `ohosApp` 已实现的主要能力：

- Jellyfin 服务器连接、切换与局域网发现
- 首页、详情页、媒体浏览
- AVPlayer / mpv 双引擎播放
- 播放进度记录与续播
- 字幕、音轨、清晰度切换
- 倍速、长按三倍速、锁屏、投屏
- 平板 / PC 形态适配

## 当前状态

- `1.x`
  已发布的 HarmonyOS 客户端，当前以维护和整理为主
- `2.0`
  规划中的跨平台重构版本，目标是用 Kotlin Multiplatform 共享逻辑层

## 仓库结构

- `ohosApp/`
  HarmonyOS 客户端主工程
- `composeApp/`
  Kotlin Multiplatform / Compose Multiplatform 基础工程
- `iosApp/`
  iOS 宿主工程
- `docs/`
  技术调研与设计文档
- `ci/`
  构建和辅助脚本

## 开发

当前仓库不是单一技术栈，按目录分别构建：

- `ohosApp/`
  使用 DevEco Studio / hvigor
- `composeApp/`
  使用 Gradle
- `iosApp/`
  使用 Xcode

如果你只是想查看当前可用版本，请优先关注 `ohosApp/`。

## 路线

`2.0` 的目标不是共享 UI，而是优先共享逻辑层，包括：

- Jellyfin 数据模型与映射
- 播放源决策
- 直连 / 转码 / 回退策略
- 字幕与音轨默认选择
- 续播与播放状态同步
- 错误分类与提示生成

针对 `KMP + OHOS` 的适配调研见：

- [docs/kmp-ohos-adaptation.md](/Users/machunjiang/MaFei/docs/kmp-ohos-adaptation.md)

## 文档

- HarmonyOS 现有实现说明：
  [ohosApp/README.md](/Users/machunjiang/MaFei/ohosApp/README.md)
- KMP 适配 OHOS 调研：
  [docs/kmp-ohos-adaptation.md](/Users/machunjiang/MaFei/docs/kmp-ohos-adaptation.md)

## License

本仓库按 `GPL-3.0-or-later` 发布。

第三方代码和已有文件头中的许可证声明继续按各自原始声明保留。
