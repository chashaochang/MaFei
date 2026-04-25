# MaFei

MaFei 是一个基于 Kotlin Multiplatform 和 Compose Multiplatform 的跨端视频播放器项目，目标覆盖 Android 和 iOS，并逐步扩展到平板、电视等场景。

当前仓库已经从官方 KMP 模板切换为项目自己的业务骨架，先搭通以下核心页面与导航：

- 登录/启动页
- 首页
- 更新页
- 详情页
- 播放页

目前使用的是静态假数据，目的是先把产品结构、导航关系和页面骨架跑通，方便后续继续接入：

- Jellyfin 服务器发现与登录
- 首页继续观看和追更列表
- 详情页的继续播放、从头播放、选季选集
- 播放页控制层、设置面板和失败回退

### 技术栈

- [Compose Multiplatform](https://jb.gg/compose)
- [Compose Navigation](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-navigation-routing.html)
- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Gradle](https://gradle.org/)

### 下一步建议

- 接入真实 Jellyfin API 和鉴权流程
- 将首页、详情、播放页替换为真实服务端数据
- 增加媒体库、搜索、设置等模块
- 引入播放器内核与进度同步逻辑
