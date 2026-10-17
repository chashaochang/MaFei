# 首页 UI 定位

| 行为 | 文件 |
| --- | --- |
| 页面装配、保存状态、加载触发、滚动网格与反馈宿主 | `HomeScreen.kt` |
| 窗口级背景采样、Hero 前景和滚动淡出 | `HomeHeroBackdrop.kt` |
| 推荐轮播、分页指示、自动播放与邻图预加载顺序 | `HomeRecommendationHero.kt` |
| 媒体横向列表、条目标题与播放进度 | `HomeShelves.kt` |
| 浮动入口几何、刷新容器、排序和加载/错误反馈 | `HomeFeedControls.kt` |

共享状态仍由页面原有位置持有；辅助文件只承载原有展示或纯计算，不创建第二份状态。
`HomeScreen` 的主组合函数仍较长：保留其既有状态和副作用边界，不能为满足行数拆成全局参数包。
