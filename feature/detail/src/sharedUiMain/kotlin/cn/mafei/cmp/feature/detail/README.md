# 详情 UI 定位

| 行为 | 文件 |
| --- | --- |
| 详情入口、页面保存状态和模态分派 | `DetailScreen.kt` |
| 页面布局、氛围背景、标题栏、拉伸与加载状态 | `DetailScaffold.kt` |
| 主视觉、媒体标签与基础元信息 | `DetailHero.kt` |
| 播放、收藏、追剧与已观看操作展示 | `DetailPlaybackActions.kt` |
| 简介、季列表、演职员与外链 | `DetailContentSections.kt` |
| 片源、音轨、字幕入口及选择浮层 | `DetailPlaybackSelector.kt` |
| 媒体技术信息摘要与信息浮层 | `DetailMediaInformation.kt` |

这些文件保持同一 package；现有跨详情页面的共享函数仍是唯一实现。
状态、副作用、点击事件和玻璃采样的所属层级保持不变。
