# MaFei 公开开发说明

## 环境

- DevEco Studio（支持 HarmonyOS API 26）
- 兼容 API 24 及以上设备
- Node.js / ohpm（由 DevEco Studio 的 HarmonyOS 工具链提供）

## 打开与构建

1. 使用 DevEco Studio 打开仓库中的 `ohosApp/` 目录。
2. 执行依赖同步；公开依赖会由 ohpm 下载。
3. 在 DevEco Studio 中为本机创建调试签名。不要把任何证书、Profile、口令或绝对路径写回仓库。
4. 选择 `default` 产品构建并安装到 API 24 以上的设备。

`ohosApp/build-profile.json5` 是不含签名材料的公共模板。需要发布构建时，请在本机单独维护签名配置，例如使用已被忽略的 `ohosApp/build-profile.private.json5` 作为私有备份。

## 私有播放器依赖

应用侧播放器接入依赖 `MFPlayer`，但其 SDK 不随本仓库发布。拥有合法 SDK 的内部开发环境可将二进制放到 `ohosApp/entry/libs/mfplayer.har` 后重新同步依赖；该路径已被 Git 忽略。

没有该依赖时，仍可阅读、修改和评审公开的 ArkTS、页面、数据层与主题代码，但不能生成包含正式播放器的可运行安装包。

## 提交前检查

- 不提交签名文件、证书、密钥、账号、Token、真实服务器地址或脱敏不足的日志。
- 不提交私有 HAR/HSP/HAP、内部设计稿、工作记录或未授权第三方资产。
- 修改 `oh-package.json5` 后同步检查 `oh-package-lock.json5`，确保没有指向本机路径的依赖记录。
