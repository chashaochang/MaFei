# 播放器 UI 定位

| 行为 | 文件 |
| --- | --- |
| 播放器装配、控件显隐、焦点、手势状态和视频采样 | `NowPlayingScreen.kt` |
| 窗口标题栏、顶部与底部操作栏、按钮 | `PlayerChromeBars.kt` |
| 时间轴、拖动进度与 seek 控件 | `PlayerTimeline.kt` |
| 设置菜单、轨道/速度/画面模式选项及菜单动效 | `PlayerPanels.kt` |
| 选集列表和网格 | `PlayerEpisodePanel.kt` |
| 播放技术信息卡 | `PlayerPlaybackInfoCard.kt` |
| Seek 预览、音量/亮度/倍速反馈与错误展示 | `PlayerGestureOverlay.kt` |
| 播放器图形绘制 | `PlayerChromeGlyphs.kt` |

`commonMain/PlayerGestureFeedback.kt` 仍负责反馈的纯模型，展示实现位于 `PlayerGestureOverlay.kt`。
`NowPlayingScreen` 保留现有状态、副作用、焦点和输入协调；它仍较长，后续应以明确的状态边界拆分，不能只为行数创建全局参数包。
