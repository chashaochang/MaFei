# 自适应壳层定位

| 行为 | 文件 |
| --- | --- |
| 响应式布局、背景捕获和导航呈现装配 | `MaFeiAdaptiveShell.kt` |
| 导航输入模型、上下文与共享尺寸 | `MaFeiNavigationChrome.kt` |
| 窗口级模态门户和宿主状态 | `MaFeiWindowModalHost.kt` |
| 侧栏内容、导航行和收起形变 | `MaFeiSidebar.kt` |
| 收起态顶部入口、手势与顶部模糊 | `MaFeiTopTabs.kt` |
| 底部导航与共享导航图标 | `MaFeiBottomNavigation.kt` |

路由、账号、媒体库选择及滚动状态由调用方持有。壳层不接管这些状态。
产品导航契约与已知展示差异见应用 `navigation/README.md`；此次文件拆分保持现有行为，未修正或验收既有展示差异。
