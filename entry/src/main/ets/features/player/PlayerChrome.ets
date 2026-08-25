import { audio } from '@kit.AudioKit'
import { batteryInfo } from '@kit.BasicServicesKit'
import { AppStorageV2 } from '@kit.ArkUI'
import { AVCastPicker } from '@kit.AVSessionKit'
import {
  PlayerContentMode,
  PlayerController,
  PlayerEvent,
  PlayerEventListener,
  PlayerEventType,
  PlayerState,
  PlayerSurface
} from 'mfplayer'
import { MediaPlaybackStream } from '../../media/playback/MediaPlaybackModels'
import { QualityOption } from '../../player/qualityoptions/QualityOption'
import { QueueItem } from '../../entity/QueueItem'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import { AppUIState } from '../../entity/AppUIState'
import { PlayerSeekBar } from './PlayerSeekBar'
import { ImageKnifeComponent, ImageKnifeOption } from '@ohos/imageknifepro'
import { createMediaImageKnifeOption } from '../../media/catalog/MediaImageKnifeAdapter'
import { PlaybackInfoCard } from '../../player/ui/PlaybackInfoCard'
import { PlaybackInfoRuntime, PlayerMenus } from '../../player/ui/PlayerMenus'

const PLAYER_AUTO_HIDE_DELAY_MS: number = 4000
const PLAYER_CLOCK_INTERVAL_MS: number = 30000
const PLAYER_PLAYBACK_INFO_REFRESH_INTERVAL_MS: number = 1000
const PLAYER_LONG_PRESS_SPEED: number = 3
const PLAYER_MIN_BRIGHTNESS: number = 0.05
const PLAYER_CONTROL_MASK_COLOR: string = '#3D000000'
const PLAYER_FULLSCREEN_CONTROL_MASK_COLOR: string = '#52000000'
const PLAYER_CONTROL_TRANSITION_DURATION_MS: number = 180
const PLAYER_CONTROL_TRANSITION_OFFSET_VP: number = 24
const PLAYER_TECHNICAL_INFO_FONT_SIZE: number = 10
const PLAYER_TECHNICAL_INFO_LINE_HEIGHT: number = 14
const PLAYER_GESTURE_FEEDBACK_SURFACE_COLOR: string = '#66000000'
const PLAYER_GESTURE_SEEK_SURFACE_COLOR: string = '#85000000'
const PLAYER_GESTURE_PROGRESS_TRACK_COLOR: string = '#66FFFFFF'
const PLAYER_MENU_TEXT_COLOR: string = '#E6FFFFFF'
const PLAYER_MENU_SECONDARY_TEXT_COLOR: string = '#99FFFFFF'
const PLAYER_MENU_DIVIDER_COLOR: string = '#24FFFFFF'
const PLAYER_MENU_CELL_COLOR: string = '#26000000'

@Builder
function PlayerCastPickerIcon() {
  SymbolGlyph($r('sys.symbol.wireless_projection'))
    .fontSize(22)
    .fontColor([Color.White])
    .renderingStrategy(SymbolRenderingStrategy.SINGLE)
}
const PLAYER_MENU_SELECTED_CELL_COLOR: string = '#3DFFFFFF'
const PLAYER_MENU_GRADIENT_MIDDLE_COLOR: string = '#8A000000'
const PLAYER_MENU_GRADIENT_END_COLOR: string = '#DC000000'
const PLAYER_MENU_CLOSE_DURATION_MS: number = 200
const PLAYER_MENU_CLOSE_OFFSET_VP: number = 24
const PLAYER_MENU_OPTION_EXIT_DURATION_MS: number = 130
const PLAYER_MENU_OPTION_EXIT_STAGGER_MS: number = 28
const PLAYER_MENU_OPTION_EXIT_OFFSET_VP: number = 12
const PLAYER_MENU_SPEED_WIDTH: number = 138
const PLAYER_MENU_QUALITY_WIDTH: number = 176
const PLAYER_MENU_TRACK_WIDTH: number = 220
const PLAYER_MENU_MORE_WIDTH: number = 270
const PLAYER_MENU_EPISODE_WIDTH: number = 330
const PLAYER_EPISODE_LIST_MAX_COUNT: number = 12
const PLAYER_EPISODE_GRID_COLUMN_COUNT: number = 5
const PLAYER_EPISODE_ENTRANCE_DURATION_MS: number = 180
const PLAYER_EPISODE_ROW_ENTRANCE_STAGGER_MS: number = 50
const PLAYER_EPISODE_ENTRANCE_FRAME_DELAY_MS: number = 16
const PLAYER_EPISODE_GRID_CELL_HEIGHT_VP: number = 42
const PLAYER_EPISODE_GRID_ROW_GAP_VP: number = 8
const PLAYER_EPISODE_GRID_BOTTOM_PADDING_VP: number = 16

class PlayerContentModeOption {
  readonly label: string
  readonly mode: PlayerContentMode

  constructor(label: string, mode: PlayerContentMode) {
    this.label = label
    this.mode = mode
  }
}

const PLAYER_CONTENT_MODE_OPTIONS: PlayerContentModeOption[] = [
  new PlayerContentModeOption('适应', PlayerContentMode.Fit),
  new PlayerContentModeOption('填满', PlayerContentMode.Fill),
  new PlayerContentModeOption('原始比例', PlayerContentMode.Original),
  new PlayerContentModeOption('16:9', PlayerContentMode.Ratio16x9),
  new PlayerContentModeOption('4:3', PlayerContentMode.Ratio4x3),
  new PlayerContentModeOption('21:9', PlayerContentMode.Ratio21x9)
]

enum PlayerChromePanel {
  None,
  More,
  AspectRatio,
  Speed,
  Quality,
  Audio,
  Subtitle,
  Episode
}

enum PlayerGestureFeedback {
  None,
  Seek,
  Brightness,
  Volume,
  Speed
}

function queueImageOption(item: QueueItem): ImageKnifeOption {
  const option = createMediaImageKnifeOption(item.imageRef, item.image)
  option.objectFit = ImageFit.Cover
  option.border = {
    radius: 8
  }
  return option
}

@ComponentV2
struct PlayerChromeButton {
  @Require @Param symbol: Resource
  @Param label: string = ''
  @Param diameter: number = 44
  @Param iconSize: number = 22
  @Param isEnabled: boolean = true
  @Param nativeSurface: boolean = true
  @Param systemMaterialAvailable: boolean = false
  @Param immersive: boolean = false
  @Param transparentBackground: boolean = false
  @Require @Param action: () => void

  private useImmersiveMaterial(): boolean {
    return this.immersive && this.nativeSurface
  }

  private materialAvailable(): boolean {
    return this.useImmersiveMaterial() && this.systemMaterialAvailable
  }

  build() {
    Button({ type: ButtonType.Circle, stateEffect: true }) {
      SymbolGlyph(this.symbol)
        .fontSize(this.iconSize)
        .fontColor([Color.White])
    }
    .width(this.diameter)
    .height(this.diameter)
    .enabled(this.isEnabled)
    .opacity(this.isEnabled ? 1 : 0.42)
    .backgroundColor(this.transparentBackground || this.materialAvailable() ?
      Color.Transparent : $r('app.color.player_control_surface'))
    .backgroundBlurStyle(BlurStyle.NONE)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.AdaptiveInteractive,
      this.transparentBackground ? false : this.useImmersiveMaterial(),
      this.transparentBackground ? false : this.materialAvailable()
    ))
    .hitTestBehavior(HitTestMode.BLOCK_HIERARCHY)
    .accessibilityText(this.label)
    .onClick((): void => this.action())
  }
}

@ComponentV2
struct PlayerChromeOptionRow {
  @Require @Param text: string
  @Param selected: boolean = false
  @Param entering: boolean = false
  @Param exiting: boolean = false
  @Require @Param action: () => void

  build() {
    Row() {
      Text(this.text)
        .fontSize(14)
        .fontWeight(this.selected ? FontWeight.Medium : FontWeight.Regular)
        .fontColor(this.selected ? $r('app.color.color_main') : PLAYER_MENU_TEXT_COLOR)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .layoutWeight(1)
    }
    .width('100%')
    .height(44)
    .padding({ left: 12, right: 12 })
    .alignItems(VerticalAlign.Center)
    .backgroundColor(Color.Transparent)
    .opacity(this.entering || this.exiting ? 0 : 1)
    .translate({ y: this.entering || this.exiting ? PLAYER_MENU_OPTION_EXIT_OFFSET_VP : 0 })
    .animation({ duration: PLAYER_MENU_OPTION_EXIT_DURATION_MS, curve: Curve.EaseOut })
    .onClick((): void => this.action())
  }
}

@ComponentV2
struct PlayerChromeSegmentOption {
  @Require @Param text: string
  @Param selected: boolean = false
  @Require @Param action: () => void

  build() {
    Text(this.text)
      .fontSize(15)
      .fontWeight(this.selected ? FontWeight.Medium : FontWeight.Regular)
      .fontColor(this.selected ? $r('app.color.color_main') : PLAYER_MENU_SECONDARY_TEXT_COLOR)
      .maxLines(1)
      .textOverflow({ overflow: TextOverflow.Ellipsis })
      .padding({ left: 8, right: 8, top: 10, bottom: 10 })
      .backgroundColor(Color.Transparent)
      .animation({ duration: PLAYER_CONTROL_TRANSITION_DURATION_MS, curve: Curve.EaseOut })
      .onClick((): void => this.action())
  }
}

@ComponentV2
struct PlayerChromeEpisodeGridCell {
  @Require @Param text: string
  @Param selected: boolean = false
  @Param entranceVisible: boolean = true
  @Require @Param action: () => void

  build() {
    Button({ type: ButtonType.Normal, stateEffect: true }) {
      Text(this.text)
        .fontSize(15)
        .fontWeight(this.selected ? FontWeight.Medium : FontWeight.Regular)
        .fontColor(this.selected ? $r('app.color.color_main') : PLAYER_MENU_TEXT_COLOR)
    }
    .width('100%')
    .height(42)
    .padding(0)
    .backgroundColor(this.selected ? PLAYER_MENU_SELECTED_CELL_COLOR : PLAYER_MENU_CELL_COLOR)
    .border({
      width: this.selected ? 1 : 0,
      color: this.selected ? $r('app.color.color_main') : Color.Transparent
    })
    .borderRadius(6)
    .opacity(this.entranceVisible ? 1 : 0)
    .translate({ y: this.entranceVisible ? 0 : 14 })
    .animation({
      duration: PLAYER_EPISODE_ENTRANCE_DURATION_MS,
      curve: Curve.EaseOut
    })
    .onClick((): void => this.action())
  }
}

@ComponentV2
struct PlayerChromeEpisodeRow {
  @Require @Param item: QueueItem
  @Require @Param episodeIndex: number
  @Param selected: boolean = false
  @Param entranceVisible: boolean = true
  @Require @Param action: () => void

  private episodeTitle(): string {
    const playIndex = this.item.playIndex > 0 ? this.item.playIndex : this.episodeIndex + 1
    return `第 ${playIndex} 集`
  }

  build() {
    Row({ space: 12 }) {
      ImageKnifeComponent({
        imageKnifeOption: queueImageOption(this.item)
      })
        .width(112)
        .aspectRatio(16 / 9)

      Column({ space: 8 }) {
        Text(this.episodeTitle())
          .fontColor(this.selected ? $r('app.color.color_main') : PLAYER_MENU_TEXT_COLOR)
          .fontSize(15)
          .fontWeight(FontWeight.Medium)

        if (this.item.name !== this.episodeTitle()) {
          Text(this.item.name)
            .fontColor(PLAYER_MENU_SECONDARY_TEXT_COLOR)
            .fontSize(13)
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
        }

        Row({ space: 12 }) {
          Text(this.item.durationText.length > 0 ? this.item.durationText : '时长未知')
            .fontColor('#B3FFFFFF')
            .fontSize(11)
          Text(this.item.played ? '已观看' : '未观看')
            .fontColor(this.item.played ? $r('app.color.color_main') : '#B3FFFFFF')
            .fontSize(11)
        }
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
    .padding(8)
    .backgroundColor('rgba(255,255,255,0.08)')
    .borderRadius(12)
    .opacity(this.entranceVisible ? 1 : 0)
    .translate({ y: this.entranceVisible ? 0 : 14 })
    .animation({
      duration: PLAYER_EPISODE_ENTRANCE_DURATION_MS,
      curve: Curve.EaseOut
    })
    .onClick((): void => this.action())
  }
}

@ComponentV2
export struct PlayerChrome {
  @Require @Param controller: PlayerController
  @Param title: string = ''
  @Param isFullscreen: boolean = false
  @Param isLive: boolean = false
  @Param safeTop: number = 0
  @Param safeBottom: number = 0
  @Param nativeSurface: boolean = true
  @Param systemMaterialAvailable: boolean = false
  @Param qualityOptions: QualityOption[] = []
  @Param audioStreams: MediaPlaybackStream[] = []
  @Param subtitleStreams: MediaPlaybackStream[] = []
  @Param playList: QueueItem[] = []
  @Param selectedBitrate?: number = undefined
  @Param selectedAudioStream?: MediaPlaybackStream = undefined
  @Param selectedSubtitleStream?: MediaPlaybackStream = undefined
  @Param videoStream?: MediaPlaybackStream = undefined
  @Param currentPlayId: string = ''
  @Param hasPrevious: boolean = false
  @Param hasNext: boolean = false
  @Param isCastPlaying: boolean = false
  @Param menuDismissRequest: number = 0
  @Param playerMenus?: PlayerMenus = undefined
  @Param onVideoSurfaceControllerReady: (controller: XComponentController) => void =
    (_controller: XComponentController): void => {
    }
  @Require @Param onBack: () => void
  @Param onRetry: () => void = (): void => {
  }
  @Param onControlsVisibleChanged: (visible: boolean) => void = (): void => {
  }
  @Param onPanelVisibilityChanged: (visible: boolean) => void = (): void => {
  }
  @Param onFullscreenToggle: () => void = (): void => {
  }
  @Param onPrevious: () => void = (): void => {
  }
  @Param onNext: () => void = (): void => {
  }
  @Param onQualitySelected: (option: QualityOption) => void = (): void => {
  }
  @Param onAudioSelected: (stream: MediaPlaybackStream) => void = (): void => {
  }
  @Param onSubtitleSelected: (stream: MediaPlaybackStream) => void = (): void => {
  }
  @Param onEpisodeSelected: (index: number) => void = (): void => {
  }
  @Param onStopCast: () => void = (): void => {
  }
  @Param onPictureInPicture: () => void = (): void => {
  }
  @Local controlsVisible: boolean = true
  @Local locked: boolean = false
  @Local panel: PlayerChromePanel = PlayerChromePanel.None
  @Local panelEntering: boolean = false
  @Local compactOptionsEntering: boolean = false
  @Local episodeVisibleRow: number = -1
  @Local episodeViewportRowCount: number = 0
  @Local panelClosing: boolean = false
  @Local panelEnterStep: number = -1
  @Local panelExitStep: number = -1
  @Local feedback: PlayerGestureFeedback = PlayerGestureFeedback.None
  @Local positionMs: number = 0
  @Local durationMs: number = 0
  @Local bufferedMs: number = 0
  @Local sliderPositionMs: number = 0
  @Local draggingSlider: boolean = false
  @Local buffering: boolean = false
  @Local errorMessage: string = ''
  @Local playing: boolean = false
  @Local playbackSpeed: number = 1
  @Local decoder: string = ''
  @Local feedbackValue: string = ''
  @Local feedbackProgress: number = 0
  @Local clockText: string = ''
  @Local batteryPercent: number = 0
  @Local contentMode: PlayerContentMode = PlayerContentMode.Fit
  @Local playbackInfoVisible: boolean = false
  private widthVp: number = 0
  private heightVp: number = 0
  private gestureStartX: number = 0
  private gestureStartY: number = 0
  private gestureStartPositionMs: number = 0
  private gestureStartBrightness: number = 0.5
  private gestureStartVolume: number = 0
  private maxVolume: number = 1
  private speedBeforeLongPress: number = 1
  private autoHideTimer: number = -1
  private panelEnterTimer: number = -1
  private panelCloseTimer: number = -1
  private episodeRowTimer: number = -1
  private playbackInfoTimer: number = -1
  private openPlaybackInfoAfterPanelClose: boolean = false
  private clockTimer: number = -1
  private audioManager?: audio.AudioManager
  private appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!
  private readonly verticalGesture: PanGestureOptions = new PanGestureOptions({ direction: PanDirection.Vertical })
  private readonly horizontalGesture: PanGestureOptions = new PanGestureOptions({ direction: PanDirection.Horizontal })
  private readonly playerListener: PlayerEventListener = {
    onPlayerEvent: (event: PlayerEvent): void => {
      if (event.type === PlayerEventType.ERROR) {
        this.errorMessage = event.errorMessage.length > 0 ? event.errorMessage : '视频播放失败'
      }
      this.decoder = event.decoder
      this.syncControllerState(event)
      if (this.playbackInfoVisible && event.type !== PlayerEventType.PROGRESS_CHANGED) {
        this.refreshPlaybackInfo()
      }
    }
  }

  aboutToAppear(): void {
    this.controller.addListener(this.playerListener)
    this.initializeDeviceControls()
    this.updateSystemStatus()
    try {
      this.decoder = this.controller.getHardwareDecoder()
    } catch (_error) {
      this.decoder = ''
    }
    this.clockTimer = setInterval((): void => this.updateSystemStatus(), PLAYER_CLOCK_INTERVAL_MS)
    this.syncControllerState()
    this.showControlsTemporarily()
  }

  aboutToDisappear(): void {
    this.controller.removeListener(this.playerListener)
    this.clearAutoHideTimer()
    this.clearPanelEnterTimer()
    this.clearPanelCloseTimer()
    this.clearEpisodeRowTimer()
    this.stopPlaybackInfoRefresh()
    this.openPlaybackInfoAfterPanelClose = false
    this.playbackInfoVisible = false
    if (this.playerMenus) {
      this.playerMenus.isShowPlayBackInfo = false
    }
    this.onPanelVisibilityChanged(false)
    if (this.clockTimer >= 0) {
      clearInterval(this.clockTimer)
      this.clockTimer = -1
    }
  }

  @Monitor('menuDismissRequest')
  onMenuDismissRequestChanged(): void {
    if (this.playbackInfoVisible) {
      this.closePlaybackInfo()
    } else if (this.panel !== PlayerChromePanel.None) {
      this.closePanel()
    }
  }

  private initializeDeviceControls(): void {
    try {
      this.audioManager = audio.getAudioManager()
      const groupManager = this.audioManager.getVolumeManager()
        .getVolumeGroupManagerSync(audio.DEFAULT_VOLUME_GROUP_ID)
      this.maxVolume = groupManager.getMaxVolumeSync(audio.AudioVolumeType.MEDIA)
      this.gestureStartVolume = groupManager.getVolumeSync(audio.AudioVolumeType.MEDIA)
    } catch (_error) {
      this.audioManager = undefined
    }
    try {
      const brightness = this.appUIState.windowClass?.getWindowProperties().brightness ?? -1
      this.gestureStartBrightness = brightness >= 0 ? brightness : 0.5
    } catch (_error) {
      this.gestureStartBrightness = 0.5
    }
  }

  private updateSystemStatus(): void {
    const now = new Date()
    const hours = now.getHours().toString().padStart(2, '0')
    const minutes = now.getMinutes().toString().padStart(2, '0')
    this.clockText = `${hours}:${minutes}`
    this.batteryPercent = Math.max(0, Math.min(100, batteryInfo.batterySOC))
  }

  private currentBatterySymbol(): Resource {
    if (batteryInfo.chargingStatus === batteryInfo.BatteryChargeState.ENABLE) {
      return $r('sys.symbol.statusbar_battery_bolt_fill')
    }
    if (this.batteryPercent >= 100) {
      return $r('sys.symbol.statusbar_battery_100percent')
    }
    if (this.batteryPercent >= 95) {
      return $r('sys.symbol.statusbar_battery_95percent')
    }
    if (this.batteryPercent >= 90) {
      return $r('sys.symbol.statusbar_battery_90percent')
    }
    if (this.batteryPercent >= 80) {
      return $r('sys.symbol.statusbar_battery_80percent')
    }
    if (this.batteryPercent >= 70) {
      return $r('sys.symbol.statusbar_battery_70percent')
    }
    if (this.batteryPercent >= 60) {
      return $r('sys.symbol.statusbar_battery_60percent')
    }
    if (this.batteryPercent >= 50) {
      return $r('sys.symbol.statusbar_battery_50percent')
    }
    if (this.batteryPercent >= 40) {
      return $r('sys.symbol.statusbar_battery_40percent')
    }
    if (this.batteryPercent >= 30) {
      return $r('sys.symbol.statusbar_battery_30percent')
    }
    if (this.batteryPercent >= 20) {
      return $r('sys.symbol.statusbar_battery_20percent')
    }
    if (this.batteryPercent >= 10) {
      return $r('sys.symbol.statusbar_battery_10percent')
    }
    return $r('sys.symbol.statusbar_battery_5percent')
  }

  private landscapeStartInset(base: number): number {
    return base + (this.isFullscreen ? Math.max(0, this.appUIState.safeLeft) : 0)
  }

  private landscapeEndInset(base: number): number {
    return base + (this.isFullscreen ? Math.max(0, this.appUIState.safeRight) : 0)
  }

  private syncControllerState(event?: PlayerEvent): void {
    const state: PlayerState = event?.state ?? this.controller.getState()
    const positionMs: number = Math.max(0, event?.positionMs ?? this.controller.getCurrentPositionMs())
    const durationMs: number = Math.max(0, event?.durationMs ?? this.controller.getDurationMs())
    const bufferedMs: number = Math.max(0, event?.bufferedPositionMs ?? this.controller.getBufferedPositionMs())
    const timelineReset: boolean = state === PlayerState.IDLE || state === PlayerState.INITIALIZING ||
      state === PlayerState.LOADING || state === PlayerState.RELEASED
    // 加载中的查询可能暂时给出 0；除非进入新媒体生命周期或 UI 本身也在 0，
    // 否则不允许用这个瞬态值冲掉已经显示的播放位置。
    const shouldApplyPosition: boolean = timelineReset || positionMs > 0 || this.positionMs <= 0
    if (shouldApplyPosition) {
      this.positionMs = positionMs
    }
    if (timelineReset || durationMs > 0 || this.durationMs <= 0) {
      this.durationMs = durationMs
    }
    this.bufferedMs = Math.max(this.positionMs, bufferedMs)
    if (!this.draggingSlider && shouldApplyPosition) {
      this.sliderPositionMs = this.positionMs
    }
    this.buffering = event?.isBuffering ?? this.controller.isBuffering()
    this.playing = state === PlayerState.PLAYING && (event?.playWhenReady ?? this.controller.isPlayWhenReady())
    this.playbackSpeed = event?.playbackSpeed ?? this.controller.getPlaybackSpeed()
    if (state !== PlayerState.ERROR) {
      this.errorMessage = ''
    } else if (this.errorMessage.length === 0) {
      this.errorMessage = '视频播放失败'
    }
  }

  private clearAutoHideTimer(): void {
    if (this.autoHideTimer >= 0) {
      clearTimeout(this.autoHideTimer)
      this.autoHideTimer = -1
    }
  }

  private clearPanelCloseTimer(): void {
    if (this.panelCloseTimer >= 0) {
      clearTimeout(this.panelCloseTimer)
      this.panelCloseTimer = -1
    }
  }

  private clearPanelEnterTimer(): void {
    if (this.panelEnterTimer >= 0) {
      clearTimeout(this.panelEnterTimer)
      this.panelEnterTimer = -1
    }
  }

  private clearEpisodeRowTimer(): void {
    if (this.episodeRowTimer >= 0) {
      clearTimeout(this.episodeRowTimer)
      this.episodeRowTimer = -1
    }
  }

  private setControlsVisible(visible: boolean): void {
    if (visible && !this.controlsVisible) {
      this.syncControllerState()
    }
    this.controlsVisible = visible
    this.onControlsVisibleChanged(visible)
  }

  private showControlsTemporarily(): void {
    if (this.locked) {
      return
    }
    this.setControlsVisible(true)
    this.clearAutoHideTimer()
    if (!this.draggingSlider && this.panel === PlayerChromePanel.None) {
      this.autoHideTimer = setTimeout((): void => {
        this.setControlsVisible(false)
        this.autoHideTimer = -1
      }, PLAYER_AUTO_HIDE_DELAY_MS)
    }
  }

  private toggleControls(): void {
    if (this.locked) {
      this.setControlsVisible(true)
      return
    }
    if (this.controlsVisible) {
      this.clearAutoHideTimer()
      this.setControlsVisible(false)
    } else {
      this.showControlsTemporarily()
    }
  }

  private togglePlayback(): void {
    if (this.playing) {
      this.controller.pause()
    } else {
      this.controller.play()
    }
    this.syncControllerState()
    this.showControlsTemporarily()
  }

  private seekTo(positionMs: number): void {
    const target = Math.max(0, Math.min(this.durationMs, positionMs))
    this.controller.seekTo(target, false)
    this.positionMs = target
    this.sliderPositionMs = target
  }

  private onSliderChanged(value: number, mode: SliderChangeMode): void {
    this.sliderPositionMs = value
    if (mode === SliderChangeMode.Begin || mode === SliderChangeMode.Moving) {
      this.draggingSlider = true
      this.clearAutoHideTimer()
      this.feedback = PlayerGestureFeedback.Seek
      this.feedbackProgress = this.durationMs > 0 ? value / this.durationMs : 0
      this.feedbackValue = `${this.formatTime(value)} / ${this.formatTime(this.durationMs)}`
      return
    }
    this.draggingSlider = false
    this.feedback = PlayerGestureFeedback.None
    this.seekTo(value)
    this.showControlsTemporarily()
  }

  private onDoubleTap(): void {
    if (this.locked) {
      return
    }
    this.togglePlayback()
  }

  private onHorizontalStart(event: GestureEvent | undefined): void {
    if (this.locked || this.durationMs <= 0 || !event?.fingerList?.[0]) {
      return
    }
    this.gestureStartX = event.fingerList[0].localX
    this.gestureStartPositionMs = this.positionMs
    this.clearAutoHideTimer()
  }

  private onHorizontalUpdate(event: GestureEvent | undefined): void {
    if (this.locked || this.durationMs <= 0 || this.widthVp <= 0 || !event?.fingerList?.[0]) {
      return
    }
    const deltaX = event.fingerList[0].localX - this.gestureStartX
    const target = Math.max(0, Math.min(this.durationMs,
      this.gestureStartPositionMs + deltaX * this.durationMs / this.widthVp))
    this.sliderPositionMs = target
    this.feedback = PlayerGestureFeedback.Seek
    this.feedbackProgress = target / this.durationMs
    this.feedbackValue = `${this.formatTime(target)} / ${this.formatTime(this.durationMs)}`
  }

  private onHorizontalEnd(): void {
    if (this.feedback !== PlayerGestureFeedback.Seek) {
      return
    }
    this.seekTo(this.sliderPositionMs)
    this.feedback = PlayerGestureFeedback.None
    this.showControlsTemporarily()
  }

  private onVerticalStart(event: GestureEvent | undefined): void {
    if (this.locked || !event?.fingerList?.[0]) {
      return
    }
    this.gestureStartX = event.fingerList[0].localX
    this.gestureStartY = event.fingerList[0].localY
    try {
      if (this.audioManager) {
        this.gestureStartVolume = this.audioManager.getVolumeManager()
          .getVolumeGroupManagerSync(audio.DEFAULT_VOLUME_GROUP_ID)
          .getVolumeSync(audio.AudioVolumeType.MEDIA)
      }
    } catch (_error) {
    }
  }

  private onVerticalUpdate(event: GestureEvent | undefined): void {
    if (this.locked || this.heightVp <= 0 || !event?.fingerList?.[0]) {
      return
    }
    const percent = (this.gestureStartY - event.fingerList[0].localY) / this.heightVp
    if (this.gestureStartX < this.widthVp / 2) {
      const value = Math.max(PLAYER_MIN_BRIGHTNESS, Math.min(1, this.gestureStartBrightness + percent))
      this.feedback = PlayerGestureFeedback.Brightness
      this.feedbackProgress = value
      try {
        const windowClass = this.appUIState.windowClass
        if (windowClass) {
          void windowClass.setWindowBrightness(value).catch((): void => {
          })
        }
      } catch (_error) {
      }
    } else if (this.audioManager) {
      const value = Math.max(0, Math.min(this.maxVolume,
        this.gestureStartVolume + this.maxVolume * percent))
      this.feedback = PlayerGestureFeedback.Volume
      this.feedbackProgress = this.maxVolume > 0 ? value / this.maxVolume : 0
      this.audioManager.setVolume(audio.AudioVolumeType.MEDIA, value, (): void => {
      })
    }
  }

  private onVerticalEnd(): void {
    if (this.feedback === PlayerGestureFeedback.Brightness || this.feedback === PlayerGestureFeedback.Volume) {
      this.feedback = PlayerGestureFeedback.None
    }
  }

  private onLongPressStart(): void {
    if (this.locked || !this.playing) {
      return
    }
    this.speedBeforeLongPress = this.controller.getPlaybackSpeed()
    this.controller.setPlaybackSpeed(PLAYER_LONG_PRESS_SPEED)
    this.feedback = PlayerGestureFeedback.Speed
    this.feedbackValue = `${PLAYER_LONG_PRESS_SPEED}X 快进中`
  }

  private onLongPressEnd(): void {
    if (this.feedback !== PlayerGestureFeedback.Speed) {
      return
    }
    this.controller.setPlaybackSpeed(this.speedBeforeLongPress)
    this.feedback = PlayerGestureFeedback.None
  }

  private toggleLock(): void {
    this.locked = !this.locked
    this.clearPanelEnterTimer()
    this.clearPanelCloseTimer()
    this.clearEpisodeRowTimer()
    this.panel = PlayerChromePanel.None
    this.panelEntering = false
    this.compactOptionsEntering = false
    this.episodeVisibleRow = -1
    this.episodeViewportRowCount = 0
    this.panelClosing = false
    this.panelEnterStep = -1
    this.panelExitStep = -1
    this.onPanelVisibilityChanged(false)
    this.setControlsVisible(true)
    if (!this.locked) {
      this.showControlsTemporarily()
    }
  }

  private openPanel(panel: PlayerChromePanel): void {
    this.clearPanelEnterTimer()
    this.clearPanelCloseTimer()
    this.clearEpisodeRowTimer()
    this.panelEntering = false
    this.compactOptionsEntering = false
    this.episodeVisibleRow = -1
    this.episodeViewportRowCount = 0
    this.panelClosing = false
    this.panelEnterStep = -1
    this.panelExitStep = -1
    this.panelEntering = true
    this.compactOptionsEntering = panel === PlayerChromePanel.More || panel === PlayerChromePanel.AspectRatio ||
      panel === PlayerChromePanel.Speed || panel === PlayerChromePanel.Quality ||
      panel === PlayerChromePanel.Audio || panel === PlayerChromePanel.Subtitle
    this.panelEnterStep = this.compactOptionsEntering ? 0 : -1
    this.panel = panel
    this.panelEnterTimer = setTimeout((): void => {
      this.panelEntering = false
      if (!this.compactOptionsEntering) {
        this.panelEnterTimer = -1
        return
      }
      this.panelEnterTimer = setTimeout((): void => this.advanceCompactPanelEnter(), 16)
    }, 16)
    this.clearAutoHideTimer()
    // 菜单接管画面时不与原控制层叠加，避免顶部和底部控件同时出现。
    this.setControlsVisible(false)
    this.onPanelVisibilityChanged(true)
  }

  private closePanel(): void {
    if (this.panel === PlayerChromePanel.None || this.panelClosing) {
      return
    }
    this.clearAutoHideTimer()
    this.clearPanelEnterTimer()
    this.panelEntering = false
    this.compactOptionsEntering = false
    this.panelClosing = true
    this.panelEnterStep = -1
    this.panelExitStep = 0
    if (this.isCompactOptionPanel()) {
      this.advanceCompactPanelExit()
      return
    }
    if (this.panel === PlayerChromePanel.Episode) {
      this.startEpisodeItemsExit()
      return
    }
    this.panelCloseTimer = setTimeout((): void => this.finishPanelClose(), PLAYER_MENU_CLOSE_DURATION_MS)
  }

  private finishPanelClose(): void {
    const shouldOpenPlaybackInfo = this.openPlaybackInfoAfterPanelClose
    this.openPlaybackInfoAfterPanelClose = false
    this.clearPanelEnterTimer()
    this.clearEpisodeRowTimer()
    this.panel = PlayerChromePanel.None
    this.panelEntering = false
    this.compactOptionsEntering = false
    this.episodeVisibleRow = -1
    this.episodeViewportRowCount = 0
    this.panelClosing = false
    this.panelEnterStep = -1
    this.panelExitStep = -1
    this.panelCloseTimer = -1
    if (shouldOpenPlaybackInfo) {
      this.openPlaybackInfo()
      return
    }
    this.onPanelVisibilityChanged(false)
    this.showControlsTemporarily()
  }

  private advanceCompactPanelExit(): void {
    const count = this.compactOptionCount()
    if (count <= 0) {
      this.finishPanelClose()
      return
    }
    this.panelExitStep += 1
    if (this.panelExitStep < count) {
      this.panelCloseTimer = setTimeout((): void => this.advanceCompactPanelExit(),
        PLAYER_MENU_OPTION_EXIT_STAGGER_MS)
      return
    }
    this.panelCloseTimer = setTimeout((): void => this.finishPanelClose(), PLAYER_MENU_OPTION_EXIT_DURATION_MS)
  }

  private advanceCompactPanelEnter(): void {
    const count = this.compactOptionCount()
    if (count <= 0) {
      this.finishCompactPanelEnter()
      return
    }
    this.panelEnterStep += 1
    if (this.panelEnterStep < count) {
      this.panelEnterTimer = setTimeout((): void => this.advanceCompactPanelEnter(),
        PLAYER_MENU_OPTION_EXIT_STAGGER_MS)
      return
    }
    this.panelEnterTimer = setTimeout((): void => this.finishCompactPanelEnter(),
      PLAYER_MENU_OPTION_EXIT_DURATION_MS)
  }

  private finishCompactPanelEnter(): void {
    this.compactOptionsEntering = false
    this.panelEnterStep = -1
    this.panelEnterTimer = -1
  }

  private selectSpeed(speed: number): void {
    this.controller.setPlaybackSpeed(speed)
    this.playbackSpeed = speed
    this.closePanel()
  }

  private requestPlaybackInfo(): void {
    if (!this.playerMenus) {
      return
    }
    if (this.panel !== PlayerChromePanel.None) {
      this.openPlaybackInfoAfterPanelClose = true
      this.closePanel()
      return
    }
    this.openPlaybackInfo()
  }

  private openPlaybackInfo(): void {
    if (!this.playerMenus) {
      return
    }
    this.refreshPlaybackInfo()
    this.startPlaybackInfoRefresh()
    this.playerMenus.isShowPlayBackInfo = true
    this.playbackInfoVisible = true
    this.clearAutoHideTimer()
    this.setControlsVisible(false)
    this.onPanelVisibilityChanged(true)
  }

  private closePlaybackInfo(): void {
    this.openPlaybackInfoAfterPanelClose = false
    this.stopPlaybackInfoRefresh()
    this.playbackInfoVisible = false
    if (this.playerMenus) {
      this.playerMenus.isShowPlayBackInfo = false
    }
    this.onPanelVisibilityChanged(false)
    this.showControlsTemporarily()
  }

  private startPlaybackInfoRefresh(): void {
    this.stopPlaybackInfoRefresh()
    this.playbackInfoTimer = setInterval((): void => this.refreshPlaybackInfo(),
      PLAYER_PLAYBACK_INFO_REFRESH_INTERVAL_MS)
  }

  private stopPlaybackInfoRefresh(): void {
    if (this.playbackInfoTimer >= 0) {
      clearInterval(this.playbackInfoTimer)
      this.playbackInfoTimer = -1
    }
  }

  private refreshPlaybackInfo(): void {
    if (!this.playerMenus) {
      return
    }
    const runtimeInfo: PlaybackInfoRuntime = new PlaybackInfoRuntime()
    try {
      runtimeInfo.decodeMode = this.controller.getDecodeMode()
      runtimeInfo.decoder = this.controller.getHardwareDecoder()
      runtimeInfo.videoWidth = Math.max(0, this.controller.getVideoWidth())
      runtimeInfo.videoHeight = Math.max(0, this.controller.getVideoHeight())
      const videoInfo = this.controller.getVideoInfo()
      runtimeInfo.videoCodec = videoInfo.codec
      runtimeInfo.videoCodecProfile = videoInfo.codecProfile
      runtimeInfo.videoPixelFormat = videoInfo.pixelFormat
      runtimeInfo.videoPrimaries = videoInfo.primaries
      runtimeInfo.videoTransfer = videoInfo.transfer
      runtimeInfo.outputPrimaries = videoInfo.outputPrimaries
      runtimeInfo.outputTransfer = videoInfo.outputTransfer
      runtimeInfo.dynamicRange = this.dynamicRangeLabel()
    } catch (_error) {
      runtimeInfo.decoder = this.decoder
      runtimeInfo.dynamicRange = this.dynamicRangeLabel()
    }
    try {
      const stats = this.controller.getPlaybackStats()
      runtimeInfo.renderer = stats.currentVo ?? ''
      runtimeInfo.videoBitrate = Math.max(0, stats.videoBitrate)
      runtimeInfo.audioBitrate = Math.max(0, stats.audioBitrate)
      runtimeInfo.audioSampleRate = Math.max(0, stats.audioSampleRate)
      runtimeInfo.audioChannels = Math.max(0, stats.audioChannels)
      runtimeInfo.videoDropCount = Math.max(0, stats.voDropCount)
      runtimeInfo.decoderDropCount = Math.max(0, stats.decoderDropCount)
      runtimeInfo.delayedFrameCount = Math.max(0, stats.delayedCount)
    } catch (_error) {
    }
    try {
      runtimeInfo.networkSource = this.controller.isNetworkSource()
      if (runtimeInfo.networkSource) {
        runtimeInfo.downloadSpeedBps = Math.max(0, this.controller.getDownloadSpeedBps())
        runtimeInfo.bufferedDurationSec = Math.max(0, this.controller.getBufferedDurationSec())
      }
    } catch (_error) {
    }
    try {
      const diagnostics = this.controller.getMultiConnectionDiagnostics()
      if (diagnostics !== undefined && !diagnostics.downgraded) {
        runtimeInfo.multiConnectionEnabled = diagnostics.configuredConnections > 1
        runtimeInfo.multiConnectionConfigured = Math.max(0, diagnostics.configuredConnections)
        runtimeInfo.multiConnectionActive = Math.max(0, diagnostics.activeRequests)
        runtimeInfo.multiConnectionAggregateSpeedBps = Math.max(0, diagnostics.aggregateSpeedBps)
        if (runtimeInfo.multiConnectionAggregateSpeedBps > 0) {
          runtimeInfo.downloadSpeedBps = runtimeInfo.multiConnectionAggregateSpeedBps
        }
      }
    } catch (_error) {
    }
    this.playerMenus.refreshPlaybackInfo(runtimeInfo, this.videoStream, this.selectedAudioStream,
      this.selectedSubtitleStream)
  }

  private selectContentMode(mode: PlayerContentMode): void {
    this.contentMode = mode
    this.closePanel()
  }

  private contentModeLabel(): string {
    for (const option of PLAYER_CONTENT_MODE_OPTIONS) {
      if (option.mode === this.contentMode) {
        return option.label
      }

    }
    return '适应'
  }

  private formatTime(valueMs: number): string {
    const totalSeconds = Math.max(0, Math.floor(valueMs / 1000))
    const hours = Math.floor(totalSeconds / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const seconds = totalSeconds % 60
    if (hours > 0) {
      return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString()
        .padStart(2, '0')}`
    }
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
  }

  private formatBitrate(bitrate: number): string {
    return bitrate >= 1000000 ? `${(bitrate / 1000000).toFixed(1)} Mbps` : `${Math.round(bitrate / 1000)} Kbps`
  }

  private actualDecodeLabel(): string {
    const decoder: string = this.decoder.trim().toLowerCase()
    if (decoder.length === 0 || decoder === 'unknown') {
      return ''
    }
    if (decoder === 'no') {
      return '软解'
    }
    return '硬解'
  }

  private videoCodecLabel(): string {
    let codec: string = ''
    try {
      codec = this.controller.getVideoInfo().codec.trim()
    } catch (_error) {
    }
    if (codec.length === 0) {
      codec = this.videoStream?.Codec?.trim() ?? ''
    }
    return codec.length > 0 ? codec.toUpperCase() : ''
  }

  private videoResolutionLabel(): string {
    let width: number = 0
    let height: number = 0
    try {
      width = Math.max(0, this.controller.getVideoWidth())
      height = Math.max(0, this.controller.getVideoHeight())
    } catch (_error) {
    }
    if (width <= 0 || height <= 0) {
      width = Math.max(0, this.videoStream?.Width ?? 0)
      height = Math.max(0, this.videoStream?.Height ?? 0)
    }
    return width > 0 && height > 0 ? `${width}×${height}` : ''
  }

  private videoFrameRateLabel(): string {
    const frameRate: number = this.videoStream?.RealFrameRate ?? this.videoStream?.AverageFrameRate ??
      this.videoStream?.ReferenceFrameRate ?? 0
    if (frameRate <= 0) {
      return ''
    }
    const rounded: number = Math.round(frameRate)
    const text: string = Math.abs(frameRate - rounded) < 0.01 ? `${rounded}` : frameRate.toFixed(2)
    return `${text} fps`
  }

  private dynamicRangeLabel(): string {
    let actualHdr: boolean = false
    let actualDolbyVisionProfile: number = 0
    let actualTransfer: string = ''
    try {
      const videoInfo = this.controller.getVideoInfo()
      actualHdr = videoInfo.hdr
      actualDolbyVisionProfile = videoInfo.dolbyVisionProfile
      actualTransfer = videoInfo.transfer
    } catch (_error) {
    }
    const sourceRange: string = `${this.videoStream?.VideoRangeType ?? ''} ${this.videoStream?.ColorTransfer ?? ''} ` +
      `${this.videoStream?.VideoDoViTitle ?? ''}`
    const range: string = `${sourceRange} ${actualTransfer}`.trim().toUpperCase()
    const dolbyVisionProfile: number = actualDolbyVisionProfile > 0 ? actualDolbyVisionProfile :
      (this.videoStream?.DvProfile ?? 0)
    if (dolbyVisionProfile > 0 ||
      range.indexOf('DOVI') >= 0 || range.indexOf('DOLBY VISION') >= 0) {
      return dolbyVisionProfile > 0 ? `Dolby Vision P${dolbyVisionProfile}` : 'Dolby Vision'
    }
    if (range.indexOf('VIVID') >= 0) {
      return 'HDR Vivid'
    }
    if (range.indexOf('HDR10PLUS') >= 0 || range.indexOf('HDR10+') >= 0) {
      return 'HDR10+'
    }
    if (range.indexOf('HDR10') >= 0) {
      return 'HDR10'
    }
    if (range.indexOf('HLG') >= 0) {
      return 'HLG'
    }
    if (actualHdr || range.indexOf('HDR') >= 0 || range.indexOf('PQ') >= 0 || range.indexOf('2084') >= 0) {
      return 'HDR'
    }
    return this.videoStream === undefined && !actualHdr ? '' : 'SDR'
  }

  private downloadSpeedLabel(): string {
    try {
      if (!this.controller.isNetworkSource()) {
        return ''
      }
      const bytesPerSecond: number = this.controller.getDownloadSpeedBps()
      if (bytesPerSecond <= 0) {
        return ''
      }
      if (bytesPerSecond >= 1024 * 1024) {
        return `${(bytesPerSecond / 1024 / 1024).toFixed(1)} MB/s`
      }
      return `${Math.max(1, Math.round(bytesPerSecond / 1024))} KB/s`
    } catch (_error) {
      return ''
    }
  }

  private technicalInfoText(): string {
    const items: string[] = []
    const decode: string = this.actualDecodeLabel()
    const codec: string = this.videoCodecLabel()
    const resolution: string = this.videoResolutionLabel()
    const frameRate: string = this.videoFrameRateLabel()
    const bitrate: number = this.videoStream?.BitRate ?? 0
    const dynamicRange: string = this.dynamicRangeLabel()
    const downloadSpeed: string = this.downloadSpeedLabel()
    if (decode.length > 0) {
      items.push(decode)
    }
    if (codec.length > 0) {
      items.push(codec)
    }
    if (resolution.length > 0) {
      items.push(resolution)
    }
    if (frameRate.length > 0) {
      items.push(frameRate)
    }
    if (bitrate > 0) {
      items.push(this.formatBitrate(bitrate))
    }
    if (dynamicRange.length > 0) {
      items.push(dynamicRange)
    }
    if (downloadSpeed.length > 0) {
      items.push(`↓ ${downloadSpeed}`)
    }
    return items.join('  ·  ')
  }

  private hasTechnicalInfo(): boolean {
    return this.technicalInfoText().length > 0
  }

  private fullscreenBottomControlsHeight(): number {
    return this.hasTechnicalInfo() ? 84 : 68
  }

  private fullscreenBottomMaskHeight(): number {
    return this.fullscreenBottomControlsHeight() + 54
  }

  private currentPanelTitle(): string {
    switch (this.panel) {
      case PlayerChromePanel.More:
        return '画面设置'
      case PlayerChromePanel.AspectRatio:
        return '画面比例'
      case PlayerChromePanel.Speed:
        return '播放速度'
      case PlayerChromePanel.Quality:
        return '清晰度'
      case PlayerChromePanel.Audio:
        return '音频'
      case PlayerChromePanel.Subtitle:
        return '字幕'
      case PlayerChromePanel.Episode:
        return '选集'
      default:
        return ''
    }
  }

  private shouldUseEpisodeGrid(): boolean {
    return this.playList.length > PLAYER_EPISODE_LIST_MAX_COUNT
  }

  private currentEpisodeIndex(): number {
    return Math.max(0, this.playList.findIndex((item: QueueItem): boolean => item.id === this.currentPlayId))
  }

  private shouldAnimateEpisodeItem(index: number): boolean {
    const animatedRowCount = this.episodeAnimatedRowCount()
    return animatedRowCount > 0 && this.episodeAnimationRow(index) < animatedRowCount
  }

  private episodeAnimationRow(index: number): number {
    if (this.shouldUseEpisodeGrid()) {
      return Math.floor(index / PLAYER_EPISODE_GRID_COLUMN_COUNT)
    }
    return Math.max(0, index - this.currentEpisodeIndex())
  }

  private episodeTotalRowCount(): number {
    if (this.shouldUseEpisodeGrid()) {
      return Math.ceil(this.playList.length / PLAYER_EPISODE_GRID_COLUMN_COUNT)
    }
    return Math.max(0, this.playList.length - this.currentEpisodeIndex())
  }

  private episodeAnimatedRowCount(): number {
    const totalRowCount = this.episodeTotalRowCount()
    if (!this.shouldUseEpisodeGrid()) {
      return totalRowCount
    }
    return Math.min(totalRowCount, this.episodeViewportRowCount)
  }

  private episodeRowStaggerMs(): number {
    return PLAYER_EPISODE_ROW_ENTRANCE_STAGGER_MS
  }

  private isEpisodeItemEntranceVisible(index: number): boolean {
    if (this.shouldUseEpisodeGrid() && this.episodeViewportRowCount <= 0) {
      return false
    }
    return !this.shouldAnimateEpisodeItem(index) || this.episodeVisibleRow >= this.episodeAnimationRow(index)
  }

  private updateEpisodeViewport(area: Area): void {
    const height = Number(area.height)
    if (height <= 0) {
      return
    }
    const contentHeight = Math.max(0, height - PLAYER_EPISODE_GRID_BOTTOM_PADDING_VP)
    this.episodeViewportRowCount = Math.max(1,
      Math.ceil((contentHeight + PLAYER_EPISODE_GRID_ROW_GAP_VP) /
        (PLAYER_EPISODE_GRID_CELL_HEIGHT_VP + PLAYER_EPISODE_GRID_ROW_GAP_VP)))
    this.startEpisodeItemsEntrance()
  }

  private startEpisodeItemsEntrance(): void {
    if (this.panel !== PlayerChromePanel.Episode || this.panelClosing || this.episodeVisibleRow >= 0 ||
      this.episodeRowTimer >= 0) {
      return
    }
    if (this.episodeAnimatedRowCount() <= 0) {
      return
    }
    this.episodeRowTimer = setTimeout((): void => {
      this.episodeRowTimer = -1
      if (this.panel === PlayerChromePanel.Episode && !this.panelClosing) {
        this.episodeVisibleRow = 0
        this.advanceEpisodeItemsEntrance()
      }
    }, PLAYER_EPISODE_ENTRANCE_FRAME_DELAY_MS)
  }

  private advanceEpisodeItemsEntrance(): void {
    if (this.panel !== PlayerChromePanel.Episode || this.panelClosing ||
      this.episodeVisibleRow >= this.episodeAnimatedRowCount() - 1) {
      this.episodeRowTimer = -1
      return
    }
    this.episodeRowTimer = setTimeout((): void => {
      this.episodeRowTimer = -1
      if (this.panel === PlayerChromePanel.Episode && !this.panelClosing) {
        this.episodeVisibleRow += 1
        this.advanceEpisodeItemsEntrance()
      }
    }, this.episodeRowStaggerMs())
  }

  private startEpisodeItemsExit(): void {
    this.clearEpisodeRowTimer()
    this.advanceEpisodeItemsExit()
  }

  private advanceEpisodeItemsExit(): void {
    if (this.panel !== PlayerChromePanel.Episode) {
      return
    }
    this.episodeVisibleRow -= 1
    if (this.episodeVisibleRow < 0) {
      this.panelCloseTimer = setTimeout((): void => this.finishPanelClose(), PLAYER_EPISODE_ENTRANCE_DURATION_MS)
      return
    }
    this.episodeRowTimer = setTimeout((): void => {
      this.episodeRowTimer = -1
      this.advanceEpisodeItemsExit()
    }, this.episodeRowStaggerMs())
  }

  private isCompactOptionPanel(): boolean {
    return this.panel === PlayerChromePanel.More || this.panel === PlayerChromePanel.AspectRatio ||
      this.panel === PlayerChromePanel.Speed || this.panel === PlayerChromePanel.Quality ||
      this.panel === PlayerChromePanel.Audio || this.panel === PlayerChromePanel.Subtitle
  }

  private compactOptionCount(): number {
    switch (this.panel) {
      case PlayerChromePanel.More:
        return this.playerMenus ? 2 : 1
      case PlayerChromePanel.AspectRatio:
        return PLAYER_CONTENT_MODE_OPTIONS.length
      case PlayerChromePanel.Speed:
        return 6
      case PlayerChromePanel.Quality:
        return this.qualityOptions.length
      case PlayerChromePanel.Audio:
        return this.audioStreams.length
      case PlayerChromePanel.Subtitle:
        return this.subtitleStreams.length
      default:
        return 0
    }
  }

  private isCompactOptionExiting(index: number, count: number): boolean {
    return this.panelClosing && this.panelExitStep > count - index - 1
  }

  private isCompactOptionEntering(index: number, count: number): boolean {
    return this.compactOptionsEntering && this.panelEnterStep <= count - index - 1
  }

  private shouldShowPanelTitle(): boolean {
    return this.panel === PlayerChromePanel.Episode
  }

  private shouldDismissPanelContentAsWhole(): boolean {
    return this.panelClosing && !this.isCompactOptionPanel() && this.panel !== PlayerChromePanel.Episode
  }

  private panelWidth(): number | string {
    if (!this.isFullscreen) {
      return '84%'
    }
    let contentWidth = PLAYER_MENU_MORE_WIDTH
    switch (this.panel) {
      case PlayerChromePanel.Speed:
        contentWidth = PLAYER_MENU_SPEED_WIDTH
        break
      case PlayerChromePanel.Quality:
        contentWidth = PLAYER_MENU_QUALITY_WIDTH
        break
      case PlayerChromePanel.AspectRatio:
        contentWidth = PLAYER_MENU_QUALITY_WIDTH
        break
      case PlayerChromePanel.Audio:
      case PlayerChromePanel.Subtitle:
        contentWidth = PLAYER_MENU_TRACK_WIDTH
        break
      case PlayerChromePanel.Episode:
        contentWidth = PLAYER_MENU_EPISODE_WIDTH
        break
      default:
        break
    }
    return contentWidth + this.landscapeEndInset(0)
  }

  build() {
    Stack({ alignContent: Alignment.Center }) {
      PlayerSurface({
        controller: this.controller,
        contentMode: this.contentMode,
        onVideoSurfaceControllerReady: (controller: XComponentController): void =>
          this.onVideoSurfaceControllerReady(controller)
      })
        .width('100%')
        .height('100%')

      Stack()
        .width('100%')
        .height('100%')
        .gesture(GestureGroup(GestureMode.Exclusive,
          TapGesture({ count: 2 }).onAction((): void => this.onDoubleTap()),
          TapGesture({ count: 1 }).onAction((): void => this.toggleControls()),
          PanGesture(this.verticalGesture)
            .onActionStart((event: GestureEvent | undefined): void => this.onVerticalStart(event))
            .onActionUpdate((event: GestureEvent | undefined): void => this.onVerticalUpdate(event))
            .onActionEnd((): void => this.onVerticalEnd()),
          PanGesture(this.horizontalGesture)
            .onActionStart((event: GestureEvent | undefined): void => this.onHorizontalStart(event))
            .onActionUpdate((event: GestureEvent | undefined): void => this.onHorizontalUpdate(event))
            .onActionEnd((): void => this.onHorizontalEnd()),
          LongPressGesture({ repeat: false })
            .onAction((): void => this.onLongPressStart())
            .onActionEnd((): void => this.onLongPressEnd())
        ))

      if (!this.isFullscreen && !this.locked && this.panel === PlayerChromePanel.None) {
        RelativeContainer() {
          PlayerChromeButton({
            symbol: $r('sys.symbol.chevron_backward'),
            label: '返回',
            diameter: 44,
            iconSize: 22,
            nativeSurface: this.nativeSurface,
            systemMaterialAvailable: this.systemMaterialAvailable,
            immersive: true,
            action: (): void => this.onBack()
          })
            .margin({ left: 12, top: 10 + this.safeTop })
            .alignRules({
              top: { anchor: '__container__', align: VerticalAlign.Top },
              left: { anchor: '__container__', align: HorizontalAlign.Start }
            })
            .id('player_chrome_portrait_back')
        }
        .width('100%')
        .height('100%')
        // 仅让返回按钮本身命中；其余区域必须继续交给下方的视频手势层。
        .hitTestBehavior(HitTestMode.None)
      }

      RelativeContainer() {
        if (this.controlsVisible && !this.locked) {
          Column()
            .height((this.isFullscreen ? 96 : 88) + this.safeTop)
            .linearGradient({
              angle: 0,
              colors: [
                [Color.Transparent, 0],
                [this.isFullscreen ? PLAYER_FULLSCREEN_CONTROL_MASK_COLOR : PLAYER_CONTROL_MASK_COLOR, 1]
              ]
            })
            .hitTestBehavior(HitTestMode.None)
            .alignRules({
              top: { anchor: '__container__', align: VerticalAlign.Top },
              left: { anchor: '__container__', align: HorizontalAlign.Start },
              right: { anchor: '__container__', align: HorizontalAlign.End }
            })
            .transition(
              TransitionEffect.OPACITY
                .combine(TransitionEffect.translate({ y: -PLAYER_CONTROL_TRANSITION_OFFSET_VP }))
                .animation({
                  duration: PLAYER_CONTROL_TRANSITION_DURATION_MS,
                  curve: Curve.EaseInOut
                })
            )
            .id('player_chrome_top_mask')

          Column()
            .height((this.isFullscreen ? this.fullscreenBottomMaskHeight() : 106) + this.safeBottom)
            .linearGradient({
              angle: 180,
              colors: [
                [Color.Transparent, 0],
                [this.isFullscreen ? PLAYER_FULLSCREEN_CONTROL_MASK_COLOR : PLAYER_CONTROL_MASK_COLOR, 1]
              ]
            })
            .hitTestBehavior(HitTestMode.None)
            .alignRules({
              bottom: { anchor: '__container__', align: VerticalAlign.Bottom },
              left: { anchor: '__container__', align: HorizontalAlign.Start },
              right: { anchor: '__container__', align: HorizontalAlign.End }
            })
            .transition(
              TransitionEffect.OPACITY
                .combine(TransitionEffect.translate({ y: PLAYER_CONTROL_TRANSITION_OFFSET_VP }))
                .animation({
                  duration: PLAYER_CONTROL_TRANSITION_DURATION_MS,
                  curve: Curve.EaseInOut
                })
            )
            .id('player_chrome_bottom_mask')

          Row({ space: this.isFullscreen ? 10 : 10 }) {
            if (this.isFullscreen) {
              PlayerChromeButton({
                symbol: $r('sys.symbol.chevron_backward'),
                label: '返回',
                diameter: 44,
                iconSize: 22,
                nativeSurface: this.nativeSurface,
                systemMaterialAvailable: this.systemMaterialAvailable,
                immersive: true,
                action: (): void => this.onBack()
              })
            }
            Text(this.title)
              .fontSize(16)
              .fontWeight(FontWeight.Medium)
              .fontColor(Color.White)
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
              .layoutWeight(1)
            Row({ space: 8 }) {
              PlayerChromeButton({
                symbol: $r('sys.symbol.smal_window_playback'),
                label: '画中画',
                diameter: 44,
                iconSize: 22,
                nativeSurface: this.nativeSurface,
                systemMaterialAvailable: this.systemMaterialAvailable,
                immersive: true,
                action: (): void => {
                  this.onPictureInPicture()
                }
              })
              // 使用固定的自定义图标承载系统投屏选择器，避免系统图标在设备状态更新时短暂闪空。
              Stack({ alignContent: Alignment.Center }) {
                AVCastPicker({
                  sessionType: 'video',
                  customPicker: PlayerCastPickerIcon
                })
                  .width(24)
                  .height(24)
                  .accessibilityText('投屏')
              }
              .width(44)
              .height(44)
              .backgroundColor(this.nativeSurface && this.systemMaterialAvailable ?
                Color.Transparent : $r('app.color.player_control_surface'))
              .backgroundBlurStyle(BlurStyle.NONE)
              .attributeModifier(AppThemeSurfaceResolver.modifier(
                AppThemeMaterialRole.AdaptiveInteractive,
                this.nativeSurface,
                this.nativeSurface && this.systemMaterialAvailable
              ))
              .borderRadius(22)
              PlayerChromeButton({
                symbol: $r('sys.symbol.dot_grid_2x2'),
                label: '更多设置',
                diameter: 44,
                iconSize: 22,
                nativeSurface: this.nativeSurface,
                systemMaterialAvailable: this.systemMaterialAvailable,
                immersive: true,
                action: (): void => this.openPanel(PlayerChromePanel.More)
              })
            }

            if (this.isFullscreen) {
              Row({ space: 6 }) {
                SymbolGlyph(this.currentBatterySymbol())
                  .fontSize(16)
                  .fontColor([Color.White])
                Text(this.clockText)
                  .fontSize(13)
                  .fontColor(Color.White)
              }
            }
          }
          .height(44)
          .padding({
            left: this.isFullscreen ? this.landscapeStartInset(20) : 68,
            right: this.isFullscreen ? this.landscapeEndInset(20) : 12
          })
          .margin({ top: (this.isFullscreen ? 16 : 10) + this.safeTop })
          .alignRules({
            top: { anchor: '__container__', align: VerticalAlign.Top },
            left: { anchor: '__container__', align: HorizontalAlign.Start },
            right: { anchor: '__container__', align: HorizontalAlign.End }
          })
          .transition(
            TransitionEffect.OPACITY
              .combine(TransitionEffect.translate({ y: -PLAYER_CONTROL_TRANSITION_OFFSET_VP }))
              .animation({
                duration: PLAYER_CONTROL_TRANSITION_DURATION_MS,
                curve: Curve.EaseInOut
              })
          )
          // 左侧为竖屏常驻返回按钮预留的空白区域必须继续命中下层按钮。
          .hitTestBehavior(HitTestMode.None)
          .id('player_chrome_top_controls')

          if (this.isFullscreen) {
            PlayerChromeButton({
              symbol: $r('sys.symbol.lock_open'),
              label: '锁定',
              diameter: 44,
              iconSize: 22,
              nativeSurface: this.nativeSurface,
              systemMaterialAvailable: this.systemMaterialAvailable,
              action: (): void => this.toggleLock()
            })
              .margin({ left: this.landscapeStartInset(20) })
              .alignRules({
                center: { anchor: '__container__', align: VerticalAlign.Center },
                left: { anchor: '__container__', align: HorizontalAlign.Start }
              })
              .id('player_chrome_lock')
          }

          Column({ space: this.isFullscreen ? 2 : 0 }) {
            if (this.isFullscreen && this.hasTechnicalInfo()) {
              Text(this.technicalInfoText())
                .width('100%')
                .height(PLAYER_TECHNICAL_INFO_LINE_HEIGHT)
                .fontSize(PLAYER_TECHNICAL_INFO_FONT_SIZE)
                .fontColor(Color.White)
                .opacity(0.82)
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
            }

            if (this.isFullscreen) {
              if (!this.isLive) {
                Row({ space: 8 }) {
                  Text(this.formatTime(this.draggingSlider ? this.sliderPositionMs : this.positionMs))
                    .fontSize(11)
                    .fontColor(Color.White)
                  PlayerSeekBar({
                    value: this.sliderPositionMs,
                    bufferedValue: this.durationMs > 0 ? this.bufferedMs : 0,
                    total: Math.max(1, this.durationMs),
                    isEnabled: this.durationMs > 0,
                    trackHeight: 4,
                    thumbDiameter: 14,
                    onSeekStart: (value: number): void => this.onSliderChanged(value, SliderChangeMode.Begin),
                    onSeekChanging: (value: number): void => this.onSliderChanged(value, SliderChangeMode.Moving),
                    onSeekEnd: (value: number): void => this.onSliderChanged(value, SliderChangeMode.End)
                  })
                    .width('100%')
                    .height(28)
                    .layoutWeight(1)
                  Text(this.formatTime(this.durationMs))
                    .fontSize(11)
                    .fontColor(Color.White)
                }
                .width('100%')
                .height(28)
              } else {
                Row({ space: 6 }) {
                  Circle().width(7).height(7).fill($r('app.color.main_red_color'))
                  Text('直播')
                    .fontSize(11)
                    .fontColor(Color.White)
                }
                .width('100%')
                .height(28)
              }
              Row({ space: 4 }) {
                PlayerChromeButton({
                  symbol: this.playing ? $r('sys.symbol.pause_fill') : $r('sys.symbol.play_fill'),
                  label: this.playing ? '暂停' : '播放',
                  diameter: 30,
                  iconSize: 16,
                  nativeSurface: this.nativeSurface,
                  systemMaterialAvailable: this.systemMaterialAvailable,
                  transparentBackground: true,
                  action: (): void => this.togglePlayback()
                })
                if (this.hasNext) {
                  PlayerChromeButton({
                    symbol: $r('sys.symbol.forward_end_fill'),
                    label: '下一集',
                    diameter: 30,
                    iconSize: 16,
                    nativeSurface: this.nativeSurface,
                    systemMaterialAvailable: this.systemMaterialAvailable,
                    transparentBackground: true,
                    action: (): void => this.onNext()
                  })
                }
                Blank().layoutWeight(1)
                if (this.audioStreams.length > 1) {
                  Button('音轨')
                    .height(36)
                    .padding({ left: 8, right: 8 })
                    .fontSize(13)
                    .fontColor(Color.White)
                    .backgroundColor(Color.Transparent)
                    .borderRadius(18)
                    .onClick((): void => this.openPanel(PlayerChromePanel.Audio))
                }
                if (this.subtitleStreams.length > 0) {
                  Button('字幕')
                    .height(36)
                    .padding({ left: 8, right: 8 })
                    .fontSize(13)
                    .fontColor(Color.White)
                    .backgroundColor(Color.Transparent)
                    .borderRadius(18)
                    .onClick((): void => this.openPanel(PlayerChromePanel.Subtitle))
                }
                if (this.playList.length > 1) {
                  Button('选集')
                    .height(36)
                    .padding({ left: 8, right: 8 })
                    .fontSize(13)
                    .fontColor(Color.White)
                    .backgroundColor(Color.Transparent)
                    .borderRadius(18)
                    .onClick((): void => this.openPanel(PlayerChromePanel.Episode))
                }
                if (this.qualityOptions.length > 0) {
                  Button('清晰度')
                    .height(36)
                    .padding({ left: 8, right: 8 })
                    .fontSize(13)
                    .fontColor(Color.White)
                    .backgroundColor(Color.Transparent)
                    .borderRadius(18)
                    .onClick((): void => this.openPanel(PlayerChromePanel.Quality))
                }
                Button(this.playbackSpeed === 1 ? '倍速' : `${this.playbackSpeed}x`)
                  .height(36)
                  .padding({ left: 8, right: 8 })
                  .fontSize(13)
                  .fontColor(Color.White)
                  .backgroundColor(Color.Transparent)
                  .borderRadius(18)
                  .onClick((): void => this.openPanel(PlayerChromePanel.Speed))
              }
              .width('100%')
              .height(36)
            }

            if (!this.isFullscreen) {
              Row({ space: 8 }) {
                PlayerChromeButton({
                  symbol: this.playing ? $r('sys.symbol.pause_fill') : $r('sys.symbol.play_fill'),
                  label: this.playing ? '暂停' : '播放',
                  diameter: 46,
                  iconSize: 24,
                  nativeSurface: this.nativeSurface,
                  systemMaterialAvailable: this.systemMaterialAvailable,
                  transparentBackground: false,
                  action: (): void => this.togglePlayback()
                })
                if (!this.isLive) {
                  Text(this.formatTime(this.draggingSlider ? this.sliderPositionMs : this.positionMs))
                    .fontSize(12)
                    .fontColor(Color.White)
                  PlayerSeekBar({
                    value: this.sliderPositionMs,
                    bufferedValue: this.durationMs > 0 ? this.bufferedMs : 0,
                    total: Math.max(1, this.durationMs),
                    isEnabled: this.durationMs > 0,
                    trackHeight: 4,
                    thumbDiameter: 16,
                    onSeekStart: (value: number): void => this.onSliderChanged(value, SliderChangeMode.Begin),
                    onSeekChanging: (value: number): void => this.onSliderChanged(value, SliderChangeMode.Moving),
                    onSeekEnd: (value: number): void => this.onSliderChanged(value, SliderChangeMode.End)
                  })
                    .width('100%')
                    .height(32)
                    .layoutWeight(1)
                  Text(this.formatTime(this.durationMs))
                    .fontSize(12)
                    .fontColor(Color.White)
                } else {
                  Row({ space: 6 }) {
                    Circle().width(7).height(7).fill($r('app.color.main_red_color'))
                    Text('直播')
                      .fontSize(13)
                      .fontColor(Color.White)
                  }
                  .layoutWeight(1)
                }
                PlayerChromeButton({
                  symbol: $r('sys.symbol.full_screen'),
                  label: '全屏',
                  diameter: 40,
                  iconSize: 20,
                  nativeSurface: this.nativeSurface,
                  systemMaterialAvailable: this.systemMaterialAvailable,
                  transparentBackground: false,
                  action: (): void => this.onFullscreenToggle()
                })
              }
              .width('100%')
              .height(46)
            }
          }
          .width('100%')
          .height(this.isFullscreen ? this.fullscreenBottomControlsHeight() : 46)
          .padding({
            left: this.isFullscreen ? this.landscapeStartInset(20) : 8,
            right: this.isFullscreen ? this.landscapeEndInset(20) : 8
          })
          .margin({
            bottom: 14 + this.safeBottom
          })
          .alignRules({
            bottom: { anchor: '__container__', align: VerticalAlign.Bottom },
            left: { anchor: '__container__', align: HorizontalAlign.Start },
            right: { anchor: '__container__', align: HorizontalAlign.End }
          })
          .transition(
            TransitionEffect.OPACITY
              .combine(TransitionEffect.translate({ y: PLAYER_CONTROL_TRANSITION_OFFSET_VP }))
              .animation({
                duration: PLAYER_CONTROL_TRANSITION_DURATION_MS,
                curve: Curve.EaseInOut
              })
          )
          .id('player_chrome_bottom_controls')

        }
      }
      .width('100%')
      .height('100%')
      .hitTestBehavior(HitTestMode.None)
      .transition(TransitionEffect.IDENTITY)

      if (this.locked) {
        RelativeContainer() {
          PlayerChromeButton({
            symbol: $r('sys.symbol.lock_fill'),
            label: '解锁',
            diameter: 52,
            iconSize: 24,
            nativeSurface: this.nativeSurface,
            systemMaterialAvailable: this.systemMaterialAvailable,
            action: (): void => this.toggleLock()
          })
            .margin({ left: this.landscapeStartInset(this.isFullscreen ? 20 : 24) })
            .alignRules({
              center: { anchor: '__container__', align: VerticalAlign.Center },
              left: { anchor: '__container__', align: HorizontalAlign.Start }
            })
            .id('player_chrome_unlock')
        }
        .width('100%')
        .height('100%')
      }

      if (this.feedback === PlayerGestureFeedback.Brightness || this.feedback === PlayerGestureFeedback.Volume) {
        RelativeContainer() {
          Row({ space: 10 }) {
            SymbolGlyph(this.feedback === PlayerGestureFeedback.Brightness ? $r('sys.symbol.sun_max') :
              $r('sys.symbol.speaker_wave_3'))
              .fontSize(20)
              .fontColor([Color.White])
            Stack({ alignContent: Alignment.Start }) {
              Row()
                .width('100%')
                .height(4)
                .backgroundColor(PLAYER_GESTURE_PROGRESS_TRACK_COLOR)
                .borderRadius(2)
              Row()
                .width(`${Math.max(0, Math.min(1, this.feedbackProgress)) * 100}%`)
                .height(4)
                .backgroundColor(Color.White)
                .borderRadius(2)
            }
            .width(128)
            .height(4)
          }
          .height(42)
          .padding({ left: 14, right: 16 })
          .borderRadius(21)
          .backgroundColor(PLAYER_GESTURE_FEEDBACK_SURFACE_COLOR)
          .alignRules({
            top: { anchor: '__container__', align: VerticalAlign.Top },
            middle: { anchor: '__container__', align: HorizontalAlign.Center }
          })
          .margin({ top: (this.isFullscreen ? 60 : 32) + this.safeTop })
          .id('player_chrome_gesture_level_feedback')
        }
        .width('100%')
        .height('100%')
        .hitTestBehavior(HitTestMode.None)
      }

      if (this.feedback === PlayerGestureFeedback.Speed) {
        RelativeContainer() {
          Row({ space: 6 }) {
            Row({ space: 1 }) {
              SymbolGlyph($r('sys.symbol.play_fill'))
                .fontSize(14)
                .fontColor([Color.White])
              SymbolGlyph($r('sys.symbol.play_fill'))
                .fontSize(14)
                .fontColor([Color.White])
            }
            Text(this.feedbackValue)
              .fontSize(16)
              .fontWeight(FontWeight.Medium)
              .fontColor(Color.White)
          }
          .height(34)
          .padding({ left: 12, right: 14 })
          .borderRadius(17)
          .backgroundColor(PLAYER_GESTURE_FEEDBACK_SURFACE_COLOR)
          .alignRules({
            top: { anchor: '__container__', align: VerticalAlign.Top },
            middle: { anchor: '__container__', align: HorizontalAlign.Center }
          })
          .margin({ top: (this.isFullscreen ? 60 : 32) + this.safeTop })
          .id('player_chrome_gesture_speed_feedback')
        }
        .width('100%')
        .height('100%')
        .hitTestBehavior(HitTestMode.None)
      }

      if (this.feedback === PlayerGestureFeedback.Seek) {
        Column({ space: 8 }) {
          Text(this.feedbackValue)
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor(Color.White)
          Stack({ alignContent: Alignment.Start }) {
            Row()
              .width('100%')
              .height(3)
              .backgroundColor(PLAYER_GESTURE_PROGRESS_TRACK_COLOR)
              .borderRadius(2)
            Row()
              .width(`${Math.max(0, Math.min(1, this.feedbackProgress)) * 100}%`)
              .height(3)
              .backgroundColor($r('app.color.color_main'))
              .borderRadius(2)
          }
          .width(146)
          .height(3)
        }
        .padding({ left: 14, right: 14, top: 11, bottom: 11 })
        .borderRadius(12)
        .backgroundColor(PLAYER_GESTURE_SEEK_SURFACE_COLOR)
        .hitTestBehavior(HitTestMode.None)
      }

      if (this.buffering && this.errorMessage.length === 0) {
        LoadingProgress()
          .width(40)
          .height(40)
          .color(Color.White)
      }

      if (this.errorMessage.length > 0) {
        Column({ space: 14 }) {
          SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
            .fontSize(34)
            .fontColor([Color.White])
          Text(this.errorMessage)
            .fontSize(15)
            .fontColor(Color.White)
          Button('重试')
            .height(40)
            .fontColor(Color.White)
            .backgroundColor($r('app.color.color_main'))
            .borderRadius(20)
            .onClick((): void => this.onRetry())
        }
        .padding(22)
        .borderRadius(20)
        .backgroundColor($r('app.color.player_feedback_surface'))
      }

      if (this.panel !== PlayerChromePanel.None) {
        Row() {
          Blank()
            .layoutWeight(1)
            .height('100%')
            .onClick((): void => this.closePanel())
          Column({ space: 0 }) {
            if (this.shouldShowPanelTitle()) {
              Text(this.currentPanelTitle())
                .width('100%')
                .height(38)
                .padding({ left: 16, right: 16 })
                .fontSize(18)
                .fontWeight(FontWeight.Medium)
                .fontColor(PLAYER_MENU_TEXT_COLOR)
                .opacity(this.panelClosing && this.episodeVisibleRow < 0 ? 0 : 1)
                .translate({ y: this.panelClosing && this.episodeVisibleRow < 0 ? 12 : 0 })
                .animation({ duration: PLAYER_EPISODE_ENTRANCE_DURATION_MS, curve: Curve.EaseInOut })
            }

            if (this.panel === PlayerChromePanel.Episode) {
              if (this.shouldUseEpisodeGrid()) {
                Grid() {
                  ForEach(this.playList, (item: QueueItem, index: number) => {
                    GridItem() {
                      PlayerChromeEpisodeGridCell({
                        text: item.playIndex > 0 ? `${item.playIndex}` : `${index + 1}`,
                        selected: item.id === this.currentPlayId,
                        entranceVisible: this.isEpisodeItemEntranceVisible(index),
                        action: (): void => {
                          this.onEpisodeSelected(index)
                          this.closePanel()
                        }
                      })
                    }
                  })
                }
                .columnsTemplate('1fr 1fr 1fr 1fr 1fr')
                .columnsGap(8)
                .rowsGap(8)
                .width('100%')
                .layoutWeight(1)
                .padding({ left: 16, right: 16, bottom: 16 })
                // 选集面板保留滚动手势，但不显示右侧滚动指示器。
                .scrollBar(BarState.Off)
                .onAppear((): void => this.startEpisodeItemsEntrance())
                .onAreaChange((_oldValue: Area, newValue: Area): void => this.updateEpisodeViewport(newValue))
                .opacity(this.shouldDismissPanelContentAsWhole() ? 0 : 1)
                .translate({ y: this.shouldDismissPanelContentAsWhole() ? PLAYER_MENU_CLOSE_OFFSET_VP : 0 })
                .animation({ duration: PLAYER_EPISODE_ENTRANCE_DURATION_MS, curve: Curve.EaseInOut })
              } else {
                List({
                  space: 8,
                  initialIndex: Math.max(0, this.playList.findIndex((item: QueueItem): boolean => item.id === this.currentPlayId))
                }) {
                  ForEach(this.playList, (item: QueueItem, index: number) => {
                    ListItem() {
                      PlayerChromeEpisodeRow({
                        item: item,
                        episodeIndex: index,
                        selected: item.id === this.currentPlayId,
                        entranceVisible: this.isEpisodeItemEntranceVisible(index),
                        action: (): void => {
                          this.onEpisodeSelected(index)
                          this.closePanel()
                        }
                      })
                    }
                  })
                }
                .width('100%')
                .layoutWeight(1)
                .padding({ left: 16, right: 16, bottom: 16 })
                // 选集面板保留滚动手势，但不显示右侧滚动指示器。
                .scrollBar(BarState.Off)
                .onAppear((): void => this.startEpisodeItemsEntrance())
                .opacity(this.shouldDismissPanelContentAsWhole() ? 0 : 1)
                .translate({ y: this.shouldDismissPanelContentAsWhole() ? PLAYER_MENU_CLOSE_OFFSET_VP : 0 })
                .animation({ duration: PLAYER_EPISODE_ENTRANCE_DURATION_MS, curve: Curve.EaseInOut })
              }
            } else {
              Scroll() {
                Column({ space: 0 }) {
                  if (this.panel === PlayerChromePanel.More) {
                    PlayerChromeOptionRow({
                      text: `画面比例 · ${this.contentModeLabel()}`,
                      entering: this.isCompactOptionEntering(0, this.compactOptionCount()),
                      exiting: this.isCompactOptionExiting(0, this.compactOptionCount()),
                      action: (): void => this.openPanel(PlayerChromePanel.AspectRatio)
                    })

                    if (this.playerMenus) {
                      Divider()
                        .color(PLAYER_MENU_DIVIDER_COLOR)
                        .strokeWidth(1)

                      PlayerChromeOptionRow({
                        text: '播放信息',
                        entering: this.isCompactOptionEntering(1, this.compactOptionCount()),
                        exiting: this.isCompactOptionExiting(1, this.compactOptionCount()),
                        action: (): void => this.requestPlaybackInfo()
                      })
                    }
                  } else if (this.panel === PlayerChromePanel.AspectRatio) {
                    ForEach(PLAYER_CONTENT_MODE_OPTIONS, (option: PlayerContentModeOption, index: number) => {
                      PlayerChromeOptionRow({
                        text: option.label,
                        selected: this.contentMode === option.mode,
                        entering: this.isCompactOptionEntering(index, PLAYER_CONTENT_MODE_OPTIONS.length),
                        exiting: this.isCompactOptionExiting(index, PLAYER_CONTENT_MODE_OPTIONS.length),
                        action: (): void => this.selectContentMode(option.mode)
                      })
                    }, (option: PlayerContentModeOption): string => option.mode)
                  } else if (this.panel === PlayerChromePanel.Speed) {
                    ForEach([3, 2, 1.5, 1.25, 1, 0.75], (speed: number, index: number) => {
                      PlayerChromeOptionRow({
                        text: `${speed}x`,
                        selected: Math.abs(this.playbackSpeed - speed) < 0.01,
                        entering: this.isCompactOptionEntering(index, 6),
                        exiting: this.isCompactOptionExiting(index, 6),
                        action: (): void => this.selectSpeed(speed)
                      })
                    })
                  } else if (this.panel === PlayerChromePanel.Quality) {
                    ForEach(this.qualityOptions, (option: QualityOption, index: number) => {
                      PlayerChromeOptionRow({
                        text: `${option.maxHeight}p  ${this.formatBitrate(option.bitrate)}`,
                        selected: this.selectedBitrate === option.bitrate,
                        entering: this.isCompactOptionEntering(index, this.qualityOptions.length),
                        exiting: this.isCompactOptionExiting(index, this.qualityOptions.length),
                        action: (): void => {
                          this.closePanel()
                          this.onQualitySelected(option)
                        }
                      })
                    })
                  } else if (this.panel === PlayerChromePanel.Audio) {
                    ForEach(this.audioStreams, (stream: MediaPlaybackStream, index: number) => {
                      PlayerChromeOptionRow({
                        text: stream.DisplayTitle ?? stream.Title ?? `音轨 ${stream.Index ?? ''}`,
                        selected: this.selectedAudioStream?.Index === stream.Index,
                        entering: this.isCompactOptionEntering(index, this.audioStreams.length),
                        exiting: this.isCompactOptionExiting(index, this.audioStreams.length),
                        action: (): void => {
                          this.closePanel()
                          this.onAudioSelected(stream)
                        }
                      })
                    })
                  } else if (this.panel === PlayerChromePanel.Subtitle) {
                    ForEach(this.subtitleStreams, (stream: MediaPlaybackStream, index: number) => {
                      PlayerChromeOptionRow({
                        text: stream.DisplayTitle ?? stream.Title ?? `字幕 ${stream.Index ?? ''}`,
                        selected: this.selectedSubtitleStream?.Index === stream.Index,
                        entering: this.isCompactOptionEntering(index, this.subtitleStreams.length),
                        exiting: this.isCompactOptionExiting(index, this.subtitleStreams.length),
                        action: (): void => {
                          this.closePanel()
                          this.onSubtitleSelected(stream)
                        }
                      })
                    })
                  }
                }
                .width('100%')
                .padding({ left: 16, right: 16, bottom: 16 })
              }
              .layoutWeight(1)
              .width('100%')
              // 清晰度、音轨、字幕等长菜单不显示右侧滚动指示器。
              .scrollBar(BarState.Off)
              .opacity(this.shouldDismissPanelContentAsWhole() ? 0 : 1)
              .translate({ y: this.shouldDismissPanelContentAsWhole() ? PLAYER_MENU_CLOSE_OFFSET_VP : 0 })
              .animation({ duration: PLAYER_MENU_CLOSE_DURATION_MS, curve: Curve.EaseInOut })
            }
          }
          .width(this.panelWidth())
          .height('100%')
          .opacity(this.panelEntering ? 0 : 1)
          .translate({ y: this.panelEntering ? PLAYER_MENU_CLOSE_OFFSET_VP : 0 })
          .animation({ duration: PLAYER_CONTROL_TRANSITION_DURATION_MS, curve: Curve.EaseOut })
          .padding({
            top: this.isFullscreen ? 18 : 8,
            right: this.isFullscreen ? this.landscapeEndInset(0) : 0,
            bottom: this.isFullscreen ? 18 : 8
          })
          // 菜单直接叠加在画面上，仅用暗色渐变保证文本可读性，不使用系统沉浸材质。
          .linearGradient({
            angle: 90,
            colors: [
              [Color.Transparent, 0],
              [PLAYER_MENU_GRADIENT_MIDDLE_COLOR, 0.42],
              [PLAYER_MENU_GRADIENT_END_COLOR, 1]
            ]
          })
          .transition(
            TransitionEffect.OPACITY.animation({
              duration: PLAYER_CONTROL_TRANSITION_DURATION_MS,
              curve: Curve.EaseInOut
            })
          )
        }
        .width('100%')
        .height('100%')
        .backgroundColor(Color.Transparent)
        .transition(
          TransitionEffect.OPACITY.animation({
            duration: PLAYER_CONTROL_TRANSITION_DURATION_MS,
            curve: Curve.EaseInOut
          })
        )
      }

      if (this.playbackInfoVisible && this.playerMenus) {
        PlaybackInfoCard({
          menus: this.playerMenus,
          onDismiss: (): void => this.closePlaybackInfo()
        })
      }

      if (this.isCastPlaying) {
        Stack({ alignContent: Alignment.Center }) {
          Button('退出投屏')
            .height(40)
            .padding({ left: 20, right: 20 })
            .fontColor(Color.White)
            .backgroundColor($r('app.color.color_main'))
            .borderRadius(20)
            .onClick((): void => this.onStopCast())
        }
        .width('100%')
        .height('100%')
        .backgroundColor(Color.Black)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Color.Black)
    .onAreaChange((_oldValue: Area, newValue: Area): void => {
      this.widthVp = Number(newValue.width)
      this.heightVp = Number(newValue.height)
    })
  }
}
