# MaFei

MaFei 是一款 HarmonyOS 本地媒体客户端，可连接 Jellyfin，并提供飞牛影视的兼容接入。

[华为应用市场下载](https://appgallery.huawei.com/app/detail?id=cn.xiaobai.mafei)

## 能力概览

- 服务器连接、账号切换与局域网发现
- 首页推荐、媒体库、搜索、收藏、详情页与播放历史
- 剧集选季选集、字幕/音轨/清晰度选择与续播
- HarmonyOS 原生主题、深浅色模式和大屏降级布局
- 投屏、画中画、媒体通知和播放进度同步
- 管理员可用的媒体库、用户、设备与活动会话管理入口

## 仓库内容

- `ohosApp/`：HarmonyOS 客户端工程
- `docs/`：面向贡献者的公开开发说明

本仓库只包含可公开的应用代码、资源和构建配置。产品规划、内部工作记录、签名资料及私有依赖均不在仓库中。

## 开发

使用 DevEco Studio 打开 [`ohosApp`](ohosApp) 目录，并按 [开发说明](docs/README.md) 配置本地环境。

项目最低兼容 API 24，目标 API 26。签名证书、Profile、密钥和任何本机路径都必须仅保留在本地，不能提交到 Git。

### 关于播放器依赖

当前开发分支的正式播放器使用 `MFPlayer`。它是独立的闭源本地依赖，SDK 二进制不随本仓库发布，也不属于本仓库的开源授权范围。公开源码保留了应用侧接入层；拥有合法 SDK 的内部开发环境可按 [`ohosApp/entry/libs/README.md`](ohosApp/entry/libs/README.md) 放置本地依赖。

## 参与贡献

欢迎通过 Issue 提交可复现的问题或改进建议。提交代码前请确认：

- 不包含服务器地址、账号、令牌、日志或截图中的个人数据；
- 不包含签名文件、密码、证书或 Profile；
- 不包含未获授权分发的二进制依赖。

## License

除单独声明外，本仓库代码按 [GPL-3.0-or-later](LICENSE) 发布。第三方依赖仍遵循其各自许可证；`MFPlayer` 不在本仓库授权范围内。
