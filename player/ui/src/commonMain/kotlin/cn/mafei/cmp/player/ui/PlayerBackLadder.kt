package cn.mafei.cmp.player.ui

/**
 * Legacy `PlayerPage.onBackPress` order, as a pure decision so the priority is
 * unit-testable instead of living only inside a composable lambda:
 *
 * 1. 打开的面板/菜单 → 关闭（含「播放信息」卡，旧版把它也算作菜单可见）
 * 2. 全屏 → 退出全屏
 * 3. 否则离开播放页
 *
 * [PlayerBackStep.ExitFullscreen] 只描述「退全屏」这一步：它**必须**请求退出
 * (`requestFullscreen(false)`)，不能是「再切一次」—— 曾经接线成永远请求 true，
 * 结果全屏里侧滑返回被消费掉却什么都不发生。
 */
enum class PlayerBackStep { ClosePanel, ClosePlaybackInfo, ExitFullscreen, LeavePlayer }

fun playerBackStep(
    /** 侧栏面板（更多/倍速/音轨/字幕/选集/清晰度/画面比例）是否打开。 */
    panelOpen: Boolean,
    /** 「播放信息」卡是否可见。 */
    playbackInfoVisible: Boolean,
    /** 当前画面是否为全屏（本组件按窗口横竖判定）。 */
    fullscreen: Boolean,
    /** 宿主是否提供全屏切换；不提供时这一步跳过，直接离开播放页。 */
    fullscreenToggleAvailable: Boolean,
): PlayerBackStep = when {
    panelOpen -> PlayerBackStep.ClosePanel
    playbackInfoVisible -> PlayerBackStep.ClosePlaybackInfo
    fullscreen && fullscreenToggleAvailable -> PlayerBackStep.ExitFullscreen
    else -> PlayerBackStep.LeavePlayer
}
