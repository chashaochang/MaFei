# MaFei 反馈组件与 Material UI 收口设计

## 目标

统一加载、刷新、进度和错误反馈的视觉实现，拒绝生产页面直接渲染 Material 预置控件。保留现有业务布局、数据流和交互语义，只替换反馈组件的视觉出口。

## 视觉边界

- 加载反馈使用轻量液态弧线/点阵动画，不使用 `CircularProgressIndicator`。
- 确定性进度使用项目自绘的细圆角轨道，不使用 `LinearProgressIndicator`。
- 错误反馈使用透明或轻玻璃底、小面积语义色和项目交互按钮，不使用 `errorContainer` 红色整块。
- 下拉刷新保留当前手势和列表滚动语义，替换 Material 刷新指示器。
- `LiquidButton`、`LiquidAlertDialog`、`LiquidBottomTabs`、`LiquidFloatingTabs`、`LiquidActionBar` 等已有玻璃组件继续复用，不重新设计。
- `MaterialTheme`、颜色方案、排版和 `Text` 可以继续作为 token 来源；禁止使用 Material 默认容器和控件外观。

## 共享组件

### `MaFeiLoadingIndicator`

提供 `Page`、`Inline`、`Button` 三种尺寸/布局语义。

- `Page`：首次加载时居中显示；已有内容的后台刷新不遮挡页面。
- `Inline`：卡片、列表项、详情局部内容使用。
- `Button`：按钮内部使用，不产生 Material 圆环或默认按钮背景。

### `MaFeiProgressLine`

承载播放进度、观看进度和确定性任务进度。轨道、进度色、圆角和高度由 MaFei token 控制。

### `MaFeiCarouselIndicator`

专门承载首页轮播图进度。固定在轮播图底部，不跟列表滚动；当前页进度完成后再切换页面，并从零开始下一页。

### `MaFeiPullRefresh`

替换 `PullToRefreshBox` 的视觉层，保留原有刷新触发时机、列表滚动和取消语义。指示器使用液态弧线/水滴动效，不使用 Material 指示器。

### `MaFeiInlineNotice`

统一错误、局部警告和可重试状态。

- 默认透明/玻璃容器。
- 红色只用于图标、短边缘或少量文字强调。
- 操作使用 `MaFeiTextAction` 或 `MaFeiGlassAction`。
- 进出场使用淡入和轻微位移动画。

### 首页静默刷新按钮

首页单独使用 `MaFeiRefreshIndicatorButton`：

- 位于搜索按钮左侧。
- 与搜索按钮尺寸、圆角、玻璃材质和内边距完全一致。
- 不可点击、不产生波纹，只表达“首页正在后台刷新”。
- 首次加载不显示；已有内容进行静默刷新时显示。
- 始终保留布局槽位，只改变透明度，避免搜索按钮横向跳动。
- 刷新完成后淡出；刷新失败后停止动画，转交局部错误提示或 Toast。
- 固定在顶部操作栏，不随 Floating Tabs 或列表内容滚动。

## 迁移范围

### 第一批：共享出口

- `common/ui/LegacyStateViews.kt`
- `common/ui/MediaCard.kt`
- `common/ui/SavedAccountList.kt`

移除共享层中的 Material 圆环、Material 线性进度和 `errorContainer` 错误块。

### 第二批：业务页面

- 首页、媒体库、收藏、搜索
- 影片详情、季详情、人物详情
- 连接、账号历史、服务器发现
- 设置和追剧
- 会话恢复页

只替换反馈组件，不修改各页面的数据请求、路由、列表结构和播放逻辑。

### 第三批：容器和导入收口

- `MaFeiPanel` 从 `OutlinedCard` 改为项目自绘容器。
- 移除生产 UI 中的 `material3.*` 通配符导入。
- 可见的 Material `Surface`/`Card` 改为项目容器；播放器宿主的原生视频 Surface 不在范围内。

## 门禁

新增生产 UI 静态检查，禁止 `sharedUiMain` 直接使用：

`CircularProgressIndicator`、`LinearProgressIndicator`、`PullToRefreshBox`、`Button`、`TextButton`、`OutlinedButton`、`Card`、`OutlinedCard`、`AlertDialog`、`Snackbar`、`FilterChip`、`Switch`、`Slider`。

允许 token 来源：`MaterialTheme`、`Text`、`Typography`、`ColorScheme`、`LocalContentColor`。诊断用 `FoundationProbeScreen` 和 Android 播放器承载 Surface 单独列入排除清单。

## 验收标准

1. 生产 `sharedUiMain` 无新增 Material 原生反馈控件。
2. 页面首次加载、局部加载、后台刷新三种状态视觉不同且不互相遮挡。
3. 首页刷新时搜索按钮位置稳定，刷新按钮与搜索按钮尺寸一致。
4. 错误提示不再出现大面积 Material 红块，重试操作仍可用。
5. 播放进度、观看进度和轮播进度仍准确，不被误替换成不确定加载动画。
6. Android、鸿蒙和 Desktop 编译通过，现有 UI/交互测试通过。
