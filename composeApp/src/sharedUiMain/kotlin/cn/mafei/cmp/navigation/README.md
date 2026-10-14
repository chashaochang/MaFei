# 应用导航边界

这是 `App.kt` 分阶段重构的第一批：独立路由定义与壳层输入映射。
保持现有页面、导航栈、状态生命周期和业务调用顺序。

## 查找入口

| 内容 | 唯一入口 |
| --- | --- |
| 未登录路由及参数 | `SignedOutRoutes.kt` |
| 浏览、详情、账号路由及参数 | `BrowseRoutes.kt` |
| 路由归属、选中项、手机整页与二级内容分类 | `BrowseShellMapping.kt` |
| 底部、侧栏、顶部文字入口列表 | `browseNavigationItems` |
| 最近媒体库入口的账号级保留 | `../LibraryTabSelection.kt` |
| 栈的创建与变更、返回前后的数据恢复 | `../App.kt`（后续批次） |
| 窗口布局、侧栏宽度、材质、实际绘制 | `common/ui` 的 `MaFeiAdaptiveShell` |

本目录保留 `cn.mafei.cmp` package 和已有路由类名，避免改变 kotlinx.serialization
默认的路由类型标识。类型为应用模块内的 `internal`，不向 Feature 暴露应用实现。

## 依赖和状态归属

- 路由只携带导航参数。账号重认证参数继续只使用非秘密元数据和凭据引用。
- 映射是纯函数；不读取 Repository、Provider、ApplicationRuntime 或浏览器状态，
  不启动协程、不保存窗口尺寸、不改变路由栈。
- 新增路由必须在 `shellContext()` 的穷尽分支中明确归属；不能增加默认分支把
  未分类的路由自动当作账号页面。相应导航行为在 `BrowseShellMappingTest` 中验证。
- 当前高亮由当前路由的来源决定；保留的媒体库快捷入口是另一份状态。
  `rememberLibraryTabId` 的现有 server/account key 与调用位置保持不变。
- 搜索由壳层的图标动作展示；不能因为共用首页内容宿主而选中首页。
- `secondaryContent` 是传给壳层的路由分类，不代表壳层已经满足全部视觉契约。
- 账号浮层和播放器生命周期继续由原宿主管理，不把完整应用状态传入映射层。

## 验证

```bash
./gradlew :composeApp:desktopTest --tests 'cn.mafei.cmp.BrowseShellMappingTest' :composeApp:testDebugUnitTest --tests 'cn.mafei.cmp.BrowseShellMappingTest'
./gradlew verifyDependencyBoundaries verifyArchitecture
```

测试覆盖媒体库来源保留、非媒体库来源隔离、搜索分类、保留入口与高亮独立、
导航项顺序、手机/宽屏导航可用性、账号浮层以及已有路由序列化兼容。
这些是数据和行为契约测试，不代表设备渲染、动效或视觉验收。

## 后续迁移与已知差异

下一批按职责迁移页面连接和状态控制器；返回栈、媒体加载恢复和播放会话应分别迁移，
每批先覆盖相关生命周期行为，再独立提交。不得把剩余入口整体搬入另一个大类。

2026-10-15 检查发现：旧平板导航文档内部存在相反规则，`MaFeiAdaptiveShell` 的
收起态二级页面展示仍保留“顶栏加返回按钮”的旧实现，与当前根 `AGENTS.md` 的
“隐藏一级顶栏、保留紧凑栏”要求不一致。以根 `AGENTS.md` 为产品依据；该展示差异
需要独立修正并做 UI 验收，本批未改动或验收该展示，也未为旧展示规则新增测试。
