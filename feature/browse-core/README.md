# 账号级浏览状态与请求

此模块拥有首页/媒体库、搜索、收藏、详情、人物、历史、播放选择和媒体操作控制器，以及唯一媒体读模型。它是纯共享 Kotlin 模块，不依赖 Compose、Feature 页面或平台宿主。查找搜索逻辑从这里的 `browser/SearchBrowser.kt` 开始，`feature/search` 负责展示。

`MediaBrowser` 继续作为兼容门面。为保持调用 API，本轮迁移保留 `cn.mafei.cmp.feature.home` 包名和类名；只有一个实现，原 `feature/home` 不保留副本。目录归属以本模块为准。状态所有者表见 [browser/README](src/commonMain/kotlin/cn/mafei/cmp/feature/home/browser/README.md)。

`feature/home` 仅保留首页展示、Hero 文案与首页导航展示规则，并通过 API 依赖兼容现有调用者；应用显式依赖本模块。新增请求/分页/重试/缓存修改在本模块实现，不放回首页 Screen。

测试与源码同模块：`commonTest` 包含队列与首页最新查询规则，`jvmTest` 的 `MediaBrowserTest` 覆盖请求竞争、分页、缓存、返回恢复与失败行为，并同时运行于 Desktop 与 Android JVM。它使用 JVM 同步集合，不能直接当作 Native 运行测试。

```bash
./gradlew :feature:browse-core:desktopTest :feature:browse-core:testDebugUnitTest
./gradlew verifyAppBoundaries verifyDependencyBoundaries
```
