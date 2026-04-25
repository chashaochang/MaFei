import { media } from "@kit.MediaKit";
import { image } from '@kit.ImageKit'
import { AVCastPicker, avSession } from "@kit.AVSessionKit";
import { audio } from "@kit.AudioKit";
import { BusinessError, emitter } from "@kit.BasicServicesKit";
import { settings } from "@kit.BasicServicesKit";
import { window, PiPWindow, display } from "@kit.ArkUI";
import { common } from "@kit.AbilityKit";
import avSessionManager from "../../utils/AVSessionManager";
import { Constants } from "../../utils/Constants";
import { PlayStatus, PlayerStatus } from "./PlayStatus";
import { BaseItemKind, MediaStream } from "sdk/src/main/ets/generated-client/models";
import { QualityOption } from "../../player/qualityoptions/QualityOption";
import { OneMoreUtil } from "../../utils/OneMoreUtil";
import { PlayerPageUIState } from "./PlayerPageUIState";
import { PlayerPageViewModel } from "./PlayerPageViewModel";
import { SmartDialog } from "ohos_smart_dialog";
// 弹幕功能暂不上线，先注释引用，后续恢复时取消注释即可。
// import { DanmakuViewV2 } from "@ohos/danmakuflamemaster";
import { QueueItem } from "../../entity/QueueItem";
import { PlayerEngineType } from "../../entity/PlayerEngineType";
import { AppPreference } from "../../data/AppPreference";
import { ImageKnifeComponent } from "@ohos/imageknifepro";
import { VideoFramePreviewHelper } from './VideoFramePreviewHelper'
import { PlaybackInfoCard } from "../../player/ui/PlaybackInfoCard";

const PROGRESS_MAX_VALUE: number = 100;
const SEEK_SYNC_TOLERANCE_MS: number = 1500;
const SEEK_SYNC_TIMEOUT_MS: number = 1800;
const SEEK_PREVIEW_DEBOUNCE_MS: number = 70;

let avTouchStartX: number = 0
let avTouchStartY: number = 0
let avChangeWidth: number = 0
let avChangeHeight: number = 0
let avUiTime: number = 0
let avUiLockTime: number = 0

@Extend(Stack)
function menuItemStyle() {
  .borderRadius(8)
  .backgroundColor('rgba(255,255,255,0.2)')
  .width('100%')
  .layoutWeight(1)
}

@ComponentV2
export struct AVPlayerView {
  @Param @Require videoUrl: string

  @Monitor('videoUrl')
  onUrlChange() {
    this.resetSeekPreviewState()
    void this.seekPreviewHelper.release()
    this.stop()
    this.startPlay()
    this.prepareSeekPreview()
  }

  @Monitor('ui.subtitles')
  onSubtitleChanged() {
    if (this.ui.subtitles.length > 0) {
      this.startSubTitle()
    } else {
      this.stopSubTitle()
    }
  }

  changeSystemBar() {
    if (!this.vm.appUIState.isFullScreen && this.showControls) {
      this.vm.appUIState.windowClass?.setWindowSystemBarEnable(['status', 'navigation'])
    } else {
      this.vm.appUIState.windowClass?.setWindowSystemBarEnable([])
    }
  }

  @Monitor('showControls')
  onControlVisibilityChanged() {
    this.changeSystemBar()
  }

  @Monitor('vm.appUIState.isFullScreen')
  onScreenChanged() {
    this.changeSystemBar()
    if (this.vm.appUIState.isFullScreen) {
      if (OneMoreUtil.isNormalPhone()) {
        if (canIUse('SystemCapability.Window.SessionManager')) {
          this.vm.appUIState.windowClass?.setPreferredOrientation(window.Orientation.USER_ROTATION_LANDSCAPE).catch()
        }
      }
    } else {
      if (OneMoreUtil.isNormalPhone()) {
        SmartDialog.dismiss()
        if (canIUse('SystemCapability.Window.SessionManager')) {
          this.vm.appUIState.windowClass?.setPreferredOrientation(window.Orientation.USER_ROTATION_PORTRAIT)
        }
      }
    }
  }

  @Monitor('lock')
  onLockChanged() {
    if (this.lock) {
      if (OneMoreUtil.isNormalPhone()) {
        this.vm.appUIState.windowClass?.setPreferredOrientation(window.Orientation.LANDSCAPE)
      } else if (OneMoreUtil.isBigScreen()) {
        switch (display.getDefaultDisplaySync().orientation) {
          case display.Orientation.PORTRAIT:
            this.vm.appUIState.windowClass?.setPreferredOrientation(window.Orientation.PORTRAIT)
            break
          case display.Orientation.LANDSCAPE:
            this.vm.appUIState.windowClass?.setPreferredOrientation(window.Orientation.LANDSCAPE_INVERTED)
            break
          case display.Orientation.PORTRAIT_INVERTED:
            this.vm.appUIState.windowClass?.setPreferredOrientation(window.Orientation.PORTRAIT_INVERTED)
            break
          case display.Orientation.LANDSCAPE_INVERTED:
            this.vm.appUIState.windowClass?.setPreferredOrientation(window.Orientation.LANDSCAPE)
            break
        }
      }
    } else {
      if (OneMoreUtil.isBigScreen()) {
        this.vm.appUIState.windowClass?.setPreferredOrientation(window.Orientation.AUTO_ROTATION)
      }
      this.onScreenChanged()
    }
  }

  @Monitor('ui')
  onNewInstance() {
    this.stop()
  }

  @Monitor('ui.playerStatus')
  onPlayerStateChanged() {
    this.sendAVSessionState(this.ui.playerStatus)
    this.updateWindowKeepScreenOn(this.ui.playerStatus === PlayerStatus.PLAYING)
  }

  @Monitor('ui.isCastPlaying')
  onCastChanged() {
    if (this.ui.isCastPlaying) {
      this.vm.appUIState.isFullScreen = false
    }
  }

  @Param @Require onNext: () => void
  @Param @Require onAudioTrackSelected: (stream: MediaStream) => void
  @Param @Require onSubtitleSelected: (stream: MediaStream) => void
  @Param @Require onQualitySelected: (option: QualityOption) => void
  @Param @Require onEpisodeChange: (index: number) => void
  @Param @Require stopCast: () => void

  @Local progressValue: number = 0
  @Local currentTime: string = "00:00"
  @Local totalTime: string = "00:00"
  @Local loadingVisible: Visibility = Visibility.None
  @Local replayVisible: Visibility = Visibility.None
  @Local slideEnable: boolean = true
  @Local showControls: boolean = true
  @Local duration: number = 0
  @Local currentPosition: number = 0
  @Local videoAspectRatio: number = 16 / 9
  @Local videoWidth: string = '100%'
  private videoParentAspectRatio: number = 16 / 9
  @Local fullShowLock: boolean = false
  @Local lock: boolean = false
  @Local playSpeed: media.PlaybackSpeed = media.PlaybackSpeed.SPEED_FORWARD_1_00_X
  @Local showVolumeUi: boolean = false
  @Local showBrightnessUi: boolean = false
  @Local showSeekProgressUi: boolean = false
  @Local mSeekTimePosition: number = 0
  @Local mSeekCurrentTime: string = "00:00"
  @Local mSeekDirectionRes: Resource = $r('app.media.video_forward_icon')
  @Local seekPreviewPixelMap?: image.PixelMap
  @Local mCurrentVolumeValue: number = 0
  @Local mMaxVolumeValue: number = 20
  @Local mCurrentBrightness: number = 10
  @Local hintMsgVisibility: boolean = false
  @Local mContext: object | undefined = undefined
  @Local mFirst: boolean = true

  private avPlayer: media.AVPlayer | null = null
  pipController?: PiPWindow.PiPController
  private isVerticalDrag: boolean = false
  private isHorizontalDrag: boolean = false
  private isLoadNotSeek: boolean = false
  private updateSubtitleTimer: number = -1
  sec: number = 3
  private mAudioManager: audio.AudioManager | null = null
  private panOptionBrightAndVolume: PanGestureOptions = new PanGestureOptions({ direction: PanDirection.Vertical })
  private panOptionSeek: PanGestureOptions = new PanGestureOptions({ direction: PanDirection.Horizontal })
  private surfaceId: string = ''
  private xComponentController: XComponentController = new XComponentController()
  private keepScreenOnState?: boolean
  private isSeekingWithSlider: boolean = false
  private pendingSeekTargetMs: number = -1
  private pendingSeekDeadlineMs: number = 0
  private seekPreviewHelper: VideoFramePreviewHelper = new VideoFramePreviewHelper()
  private seekPreviewTimer: number = -1
  private seekPreviewRequestToken: number = 0
  private seekPreviewLoading: boolean = false
  private queuedSeekPreviewPositionMs: number = -1

  @Consumer() ui: PlayerPageUIState = new PlayerPageUIState(this.getUIContext())
  @Consumer() vm: PlayerPageViewModel = new PlayerPageViewModel(this.ui)

  aboutToAppear() {
    try {
      this.mAudioManager = audio.getAudioManager()
      const volumeManager = this.mAudioManager.getVolumeManager()
      const groupid = audio.DEFAULT_VOLUME_GROUP_ID
      const audioVolumeGroupManager = volumeManager.getVolumeGroupManagerSync(groupid)
      this.mMaxVolumeValue = audioVolumeGroupManager.getMaxVolumeSync(audio.AudioVolumeType.MEDIA)
      this.mCurrentVolumeValue = audioVolumeGroupManager.getVolumeSync(audio.AudioVolumeType.MEDIA)
    } catch (err) {
    }
    try {
      if (canIUse('SystemCapability.Applications.Settings.Core')) {
        this.mCurrentBrightness = Number(settings.getValueSync(getContext(this), settings.display.SCREEN_BRIGHTNESS_STATUS, '10'))
      }
    } catch (err) {
    }
    emitter.on(Constants.PLAYER_EVENT, async (data) => {
      if (data.data && data.data['eventName']) {
        let name = data.data['eventName'] as string
        switch (name) {
          case 'onPageShow':
            if (this.ui.isCastPlaying) {
              return
            }
            this.onLockChanged()
            if (this.mContext && !this.mFirst) {
              this.startPlayOrResumePlay()
            }
            break
          case 'onPageHide':
            this.pause()
            break
          case 'Pause':
            this.pause()
            break
          case 'Play':
          case 'Resume':
            this.startPlayOrResumePlay()
            break
          case 'Stop':
            this.stop()
            break
          case 'Destroy':
            this.stop()
            break
          case 'Seek':
            let ms = data.data['args'] as number | undefined
            if (ms !== undefined) {
              this.seekTo(ms)
            }
            break
          case 'setVolume':
            let volume = data.data['args'] as number | undefined
            if (volume !== undefined && this.mAudioManager) {
              this.mAudioManager.setVolume(audio.AudioVolumeType.MEDIA, volume, (err: BusinessError) => {
              })
            }
            break
          case 'Speed':
            let speed = data.data['args'] as string | undefined
            if (speed) {
              this.playSpeed = this.speedFromString(speed)
              try {
                this.avPlayer?.setSpeed(this.playSpeed)
              } catch (err) {
              }
            }
            break
        }
      }
    })
    let context = getContext(this) as common.UIAbilityContext
    context.eventHub.on('onForeground', () => {
      if (canIUse('SystemCapability.Window.SessionManager')) {
        this.pipController?.stopPiP()
      }
    })
  }

  aboutToDisappear() {
    this.resetSeekPreviewState()
    void this.seekPreviewHelper.release()
    this.updateWindowKeepScreenOn(false)
    this.stopSubTitle()
    emitter.off(Constants.PLAYER_EVENT.eventId)
    let context = getContext(this) as common.UIAbilityContext
    context.eventHub.off('onForeground')
    this.pipController?.stopPiP()
    this.stop()
  }

  private updateWindowKeepScreenOn(shouldKeepOn: boolean): void {
    const windowClass = this.vm.appUIState.windowClass
    if (!windowClass) {
      return
    }
    if (this.keepScreenOnState === shouldKeepOn) {
      return
    }
    this.keepScreenOnState = shouldKeepOn
    windowClass.setWindowKeepScreenOn(shouldKeepOn).catch((err: BusinessError) => {
      this.keepScreenOnState = undefined
      console.error(`[AVPlayer] setWindowKeepScreenOn(${shouldKeepOn}) failed: ${JSON.stringify(err)}`)
    })
  }

  private completionNum(num: number): string | number {
    if (num < 10) {
      return '0' + num
    } else {
      return num
    }
  }

  private stringForTime(timeMs: number): string {
    let totalSeconds: number | string = (timeMs / 1000)
    let newSeconds: number | string = totalSeconds % 60
    let minutes: number | string = (totalSeconds / 60) % 60
    let hours: number | string = totalSeconds / 3600
    hours = this.completionNum(Math.floor(Math.floor((hours as number) * 100) / 100))
    minutes = this.completionNum(Math.floor(Math.floor((minutes as number) * 100) / 100))
    newSeconds = Math.floor(Math.floor((newSeconds as number) * 100) / 100)
    newSeconds = this.completionNum(newSeconds)
    if ((hours as number) > 0) {
      return hours + ":" + minutes + ":" + newSeconds
    } else {
      return minutes + ":" + newSeconds
    }
  }

  private updateSubtitles() {
    if (this.ui.subtitles.length === 0) {
      return
    }
    const currentTime = this.currentPosition
    let currentSubtitle: string = ''
    let nextUpdateTime: number = Number.MAX_VALUE

    for (const subtitle of this.ui.subtitles) {
      if (currentTime >= subtitle.startTime && currentTime < subtitle.endTime) {
        currentSubtitle = subtitle.text
        nextUpdateTime = subtitle.endTime
        break
      } else if (subtitle.startTime > currentTime) {
        nextUpdateTime = subtitle.startTime
        break
      }
    }

    this.ui.currentSubtitle = currentSubtitle

    if (nextUpdateTime !== Number.MAX_VALUE) {
      const delay = nextUpdateTime - currentTime
      this.updateSubtitleTimer = setTimeout(() => {
        this.updateSubtitles()
      }, delay)
    }
  }

  private startSubTitle() {
    if (this.ui.subtitles.length === 0) {
      return
    }
    this.updateSubtitles()
  }

  private stopSubTitle() {
    if (this.updateSubtitleTimer !== -1) {
      clearTimeout(this.updateSubtitleTimer)
      this.updateSubtitleTimer = -1
    }
    this.ui.currentSubtitle = ''
  }

  private seekToSubtitle() {
    if (this.ui.subtitles.length === 0) {
      return
    }
    if (this.updateSubtitleTimer !== -1) {
      clearTimeout(this.updateSubtitleTimer)
    }
    this.updateSubtitles()
  }

  private playSpeedToNumber(): number {
    switch (this.playSpeed) {
      case media.PlaybackSpeed.SPEED_FORWARD_2_00_X:
        return 2
      case media.PlaybackSpeed.SPEED_FORWARD_1_75_X:
        return 1.75
      case media.PlaybackSpeed.SPEED_FORWARD_1_25_X:
        return 1.25
      case media.PlaybackSpeed.SPEED_FORWARD_1_00_X:
        return 1
      case media.PlaybackSpeed.SPEED_FORWARD_0_75_X:
        return 0.75
      default:
        return 1
    }
  }

  sendAVSessionState(status: number) {
    let playbackState: avSession.AVPlaybackState = {
      speed: this.playSpeedToNumber(),
      position: {
        elapsedTime: this.currentPosition,
        updateTime: Date.now()
      },
    }
    switch (status) {
      case PlayerStatus.IDLE:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_IDLE
        break
      case PlayerStatus.INITIALIZED:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_INITIAL
        break
      case PlayerStatus.PREPARED:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_PREPARE
        break
      case PlayerStatus.PLAYING:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_PLAY
        break
      case PlayerStatus.PAUSED:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_PAUSE
        break
      case PlayerStatus.COMPLETED:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_COMPLETED
        break
      case PlayerStatus.STOPPED:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_STOP
        break
      case PlayerStatus.RELEASED:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_RELEASED
        break
      case PlayerStatus.ERROR:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_ERROR
        break
    }
    avSessionManager.getSession()?.setAVPlaybackState(playbackState).then(() => {
    }).catch((err: BusinessError) => {
    })
  }

  private timeCountdown() {
    clearTimeout(avUiTime)
    if (!this.lock) {
      this.showControls = true
    }
    if (this.vm.appUIState.isFullScreen) {
      this.fullShowLock = true
    }
    this.sec = 3
    avUiTime = setInterval(() => {
      if (this.sec <= 0) {
        clearTimeout(avUiTime)
        this.showControls = false
        this.fullShowLock = false
      } else {
        this.sec--
      }
    }, 1000)
  }

  private timeLockCountdown() {
    clearTimeout(avUiLockTime)
    this.fullShowLock = true
    this.sec = 3
    avUiLockTime = setInterval(() => {
      if (this.sec <= 0) {
        clearTimeout(avUiLockTime)
        this.fullShowLock = false
      } else {
        this.sec--
      }
    }, 1000)
  }

  private startOrStop() {
    if (!this.avPlayer) {
      return
    }
    if (this.ui.CONTROL_PlayStatus == PlayStatus.PLAY) {
      this.pause()
    } else {
      try {
        this.avPlayer.play()
      } catch (err) {
      }
    }
  }

  private formatBitrate(bitrate: number): string {
    const BITRATE_MEGA_BIT = 1_000_000
    const BITRATE_KILO_BIT = 1_000
    let value: number
    let unit: string
    if (bitrate > BITRATE_MEGA_BIT) {
      value = bitrate / BITRATE_MEGA_BIT
      unit = " Mbps"
    } else if (bitrate > BITRATE_KILO_BIT) {
      value = bitrate / BITRATE_KILO_BIT
      unit = " kbps"
    } else {
      value = bitrate
      unit = " bps"
    }
    let formatted = value.toFixed(2)
    if (formatted.endsWith('.00')) {
      formatted = formatted.substring(0, formatted.length - 3)
    } else if (formatted.endsWith('0') && formatted.includes('.')) {
      formatted = formatted.substring(0, formatted.length - 1)
    }
    return formatted + unit
  }

  private formatTime(ms: number): string {
    const totalSeconds = Math.floor(ms / 1000)
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
  }

  private setVideoWH(videoW: number, videoH: number): void {
    if (!videoW || !videoH || videoH === 0) {
      return
    }
    const va = videoW / videoH
    this.videoAspectRatio = va
    const vpa = this.videoParentAspectRatio
    if (vpa > 0 && vpa > va) {
      this.videoWidth = (videoW / (videoH * vpa)) * 100 + '%'
    } else {
      this.videoWidth = '100%'
    }
  }

  private logAvPlayer(message: string): void {
    console.info(`[AVPlayer] ${message}`)
  }

  private logAvSourceDiagnostics(reason: string): void {
    const source = this.vm.mediaSourceOrNull
    const videoStream = source?.selectedVideoStream
    this.logAvPlayer(
      `${reason}: container=${source?.sourceInfo.Container ?? 'unknown'}, codec=${videoStream?.Codec ?? 'unknown'}, ` +
      `range=${source?.selectedVideoRangeType ?? 'unknown'}, colorTransfer=${videoStream?.ColorTransfer ?? 'unknown'}, ` +
      `colorPrimaries=${videoStream?.ColorPrimaries ?? 'unknown'}, bitDepth=${videoStream?.BitDepth ?? 'unknown'}, ` +
      `pixelFormat=${videoStream?.PixelFormat ?? 'unknown'}, size=${videoStream?.Width ?? 0}x${videoStream?.Height ?? 0}`
    )
  }

  private setAVPlayerCallbacks(player: media.AVPlayer) {
    player.on('stateChange', async (state: string) => {
      this.logAvPlayer(`state=${state}, width=${player.width ?? 0}, height=${player.height ?? 0}, duration=${player.duration ?? 0}`)
      switch (state) {
        case 'initialized':
          this.ui.playerStatus = PlayerStatus.INITIALIZED
          if (this.surfaceId) {
            player.surfaceId = this.surfaceId
            player.prepare()
          }
          break
        case 'prepared':
          this.ui.playerStatus = PlayerStatus.PREPARED
          this.logAvSourceDiagnostics('prepared')
          // 用真实视频宽高更新显示区域，避免画面被拉伸变形
          if (player.width && player.height) {
            this.setVideoWH(player.width, player.height)
          }
          this.duration = player.duration
          this.totalTime = this.formatTime(this.duration)
          this.slideEnable = true
          this.applyInitialSeekIfNeeded()
          player.play()
          break
        case 'playing':
          this.ui.CONTROL_PlayStatus = PlayStatus.PLAY
          this.ui.playerStatus = PlayerStatus.PLAYING
          this.loadingVisible = Visibility.None
          this.logAvSourceDiagnostics('playing')
          // 部分流在 prepared 时拿不到宽高，这里再兜底一次
          if (player.width && player.height) {
            this.setVideoWH(player.width, player.height)
          }
          if (this.ui.subtitles.length > 0) {
            this.startSubTitle()
          }
          // 弹幕功能暂不上线，先注释播放时启动弹幕。
          // if (this.ui.danmuModel.isPrepared()) {
          //   this.ui.danmuModel.start()
          // }
          break
        case 'paused':
          this.ui.CONTROL_PlayStatus = PlayStatus.PAUSE
          this.ui.playerStatus = PlayerStatus.PAUSED
          break
        case 'completed':
          this.ui.CONTROL_PlayStatus = PlayStatus.DONE
          this.ui.playerStatus = PlayerStatus.COMPLETED
          this.ui.playbackPositionMs = this.duration
          this.replayVisible = Visibility.Visible
          break
        case 'stopped':
          this.ui.playerStatus = PlayerStatus.STOPPED
          break
        case 'released':
          this.ui.playerStatus = PlayerStatus.RELEASED
          break
        default:
          break
      }
    })

    player.on('timeUpdate', (time: number) => {
      if (this.shouldIgnorePlaybackProgress(time)) {
        return
      }
      this.currentPosition = time
      this.ui.playbackPositionMs = time
      this.currentTime = this.formatTime(time)
      if (this.duration > 0) {
        this.progressValue = (time / this.duration) * 100
      }
    })

    player.on('durationUpdate', (duration: number) => {
      this.duration = duration
      this.totalTime = this.formatTime(duration)
    })

    player.on('error', () => {
      this.logAvSourceDiagnostics('error')
      this.ui.playerStatus = PlayerStatus.ERROR
      this.loadingVisible = Visibility.None
      this.replayVisible = Visibility.Visible
    })
  }

  private applyInitialSeekIfNeeded(): void {
    const initialSeekMs = this.ui.initSeek
    if (initialSeekMs <= 0) {
      return
    }
    this.seekTo(initialSeekMs)
    this.currentPosition = initialSeekMs
    this.ui.playbackPositionMs = initialSeekMs
    this.currentTime = this.formatTime(initialSeekMs)
    if (this.duration > 0) {
      this.progressValue = Math.min(PROGRESS_MAX_VALUE, (initialSeekMs / this.duration) * PROGRESS_MAX_VALUE)
    }
    this.ui.initSeek = 0
    this.logAvPlayer(`applied initial seek: ${initialSeekMs}ms`)
  }

  private async initAVPlayer() {
    if (!this.videoUrl || !this.surfaceId) {
      return
    }
    try {
      this.avPlayer = await media.createAVPlayer()
      this.setAVPlayerCallbacks(this.avPlayer)
      this.avPlayer.url = this.videoUrl
    } catch (err) {
      this.loadingVisible = Visibility.None
      this.replayVisible = Visibility.Visible
    }
  }

  private startPlay() {
    if (!this.videoUrl) {
      return
    }
    this.prepareSeekPreview()
    this.logAvSourceDiagnostics('startPlay')
    this.ui.playerStatus = PlayerStatus.IDLE
    this.loadingVisible = Visibility.Visible
    this.replayVisible = Visibility.None
    if (this.surfaceId) {
      this.initAVPlayer()
    }
  }

  private pause() {
    this.avPlayer?.pause()
    this.ui.CONTROL_PlayStatus = PlayStatus.PAUSE
    this.ui.playerStatus = PlayerStatus.PAUSED
  }

  private stop() {
    if (this.avPlayer) {
      this.avPlayer.stop()
      this.avPlayer.release()
      this.avPlayer = null
    }
    this.ui.CONTROL_PlayStatus = PlayStatus.INIT
    this.ui.playerStatus = PlayerStatus.RELEASED
    this.loadingVisible = Visibility.None
  }

  private startPlayOrResumePlay() {
    if (this.avPlayer && this.ui.CONTROL_PlayStatus === PlayStatus.PAUSE) {
      try {
        this.avPlayer.play()
      } catch (err) {
      }
    } else if (this.videoUrl) {
      this.startPlay()
    }
  }

  private seekTo(ms: number) {
    if (this.avPlayer) {
      try {
        this.avPlayer.seek(ms, media.SeekMode.SEEK_CLOSEST)
        this.pendingSeekTargetMs = ms
        this.pendingSeekDeadlineMs = Date.now() + SEEK_SYNC_TIMEOUT_MS
      } catch (err) {
      }
    }
  }

  private shouldIgnorePlaybackProgress(positionMs: number): boolean {
    if (this.isSeekingWithSlider) {
      return true
    }
    if (this.pendingSeekTargetMs < 0) {
      return false
    }
    if (Date.now() > this.pendingSeekDeadlineMs ||
      Math.abs(positionMs - this.pendingSeekTargetMs) <= SEEK_SYNC_TOLERANCE_MS) {
      this.pendingSeekTargetMs = -1
      this.pendingSeekDeadlineMs = 0
      return false
    }
    return true
  }

  private updateSeekPreview(value: number): number {
    const seekMs = (value / PROGRESS_MAX_VALUE) * this.duration
    this.currentPosition = seekMs
    this.ui.playbackPositionMs = seekMs
    this.currentTime = this.formatTime(seekMs)
    this.progressValue = value
    this.mSeekCurrentTime = this.stringForTime(seekMs)
    this.mSeekTimePosition = value
    return seekMs
  }

  private handleSliderSeek(value: number, mode: SliderChangeMode): void {
    if (!this.avPlayer || this.duration <= 0) {
      return
    }
    const seekMs = this.updateSeekPreview(value)
    if (mode === SliderChangeMode.Begin) {
      this.isSeekingWithSlider = true
      this.showSeekProgressUi = true
      this.scheduleSeekPreviewFrame(seekMs, true)
      return
    }
    if (mode === SliderChangeMode.Moving) {
      this.showSeekProgressUi = true
      this.scheduleSeekPreviewFrame(seekMs)
      return
    }
    this.isSeekingWithSlider = false
    this.showSeekProgressUi = false
    this.seekTo(seekMs)
    this.seekToSubtitle()
    this.resetSeekPreviewState()
  }

  private scheduleSeekPreviewFrame(positionMs: number, immediate: boolean = false): void {
    this.clearSeekPreviewTimer()
    if (immediate) {
      this.requestSeekPreviewFrame(positionMs)
      return
    }
    this.seekPreviewTimer = setTimeout(() => {
      this.seekPreviewTimer = -1
      this.requestSeekPreviewFrame(positionMs)
    }, SEEK_PREVIEW_DEBOUNCE_MS)
  }

  private clearSeekPreviewTimer(): void {
    if (this.seekPreviewTimer >= 0) {
      clearTimeout(this.seekPreviewTimer)
      this.seekPreviewTimer = -1
    }
  }

  private requestSeekPreviewFrame(positionMs: number): void {
    if (this.seekPreviewLoading) {
      this.queuedSeekPreviewPositionMs = positionMs
      return
    }
    const requestToken = ++this.seekPreviewRequestToken
    this.seekPreviewLoading = true
    void this.loadSeekPreviewFrame(positionMs, requestToken)
  }

  private async loadSeekPreviewFrame(positionMs: number, requestToken: number): Promise<void> {
    try {
      const pixelMap = await this.seekPreviewHelper.fetchFrameByTime(this.videoUrl, positionMs)
      if (requestToken !== this.seekPreviewRequestToken) {
        pixelMap?.release()
        return
      }
      if (!pixelMap) {
        return
      }
      this.setSeekPreviewPixelMap(pixelMap)
    } finally {
      this.seekPreviewLoading = false
      const nextPositionMs = this.queuedSeekPreviewPositionMs
      this.queuedSeekPreviewPositionMs = -1
      if (nextPositionMs >= 0) {
        this.requestSeekPreviewFrame(nextPositionMs)
      }
    }
  }

  private setSeekPreviewPixelMap(pixelMap?: image.PixelMap): void {
    const previousPixelMap = this.seekPreviewPixelMap
    this.seekPreviewPixelMap = pixelMap
    if (previousPixelMap && previousPixelMap !== pixelMap) {
      previousPixelMap.release()
    }
  }

  private resetSeekPreviewState(): void {
    this.clearSeekPreviewTimer()
    this.seekPreviewRequestToken++
    this.seekPreviewLoading = false
    this.queuedSeekPreviewPositionMs = -1
    this.setSeekPreviewPixelMap(undefined)
  }

  private prepareSeekPreview(): void {
    if (this.videoUrl.length === 0) {
      return
    }
    void this.seekPreviewHelper.prepare(this.videoUrl)
  }

  private speedFromString(s: string): media.PlaybackSpeed {
    switch (s) {
      case '3f':
      case '2f':
        return media.PlaybackSpeed.SPEED_FORWARD_2_00_X
      case '1.5f':
        return media.PlaybackSpeed.SPEED_FORWARD_1_75_X
      case '1.25f':
        return media.PlaybackSpeed.SPEED_FORWARD_1_25_X
      case '1f':
        return media.PlaybackSpeed.SPEED_FORWARD_1_00_X
      case '0.75f':
        return media.PlaybackSpeed.SPEED_FORWARD_0_75_X
      default:
        return this.playSpeed
    }
  }

  async createPipController() {
    if (canIUse('SystemCapability.Window.SessionManager')) {
      this.pipController = await PiPWindow.create({
        context: getContext(this),
        componentController: this.xComponentController,
        navigationId: Constants.NAVIGATION_ID,
        templateType: PiPWindow.PiPTemplateType.VIDEO_PLAY
      })
      this.pipController.on('stateChange', (state: PiPWindow.PiPState, reason: string) => {
        this.onPipStateChange(state, reason)
      })
      this.pipController.on('controlPanelActionEvent', (event: PiPWindow.PiPActionEventType, status?: number) => {
        this.onPipActionEvent(event, status)
      })
    }
  }

  onPipStateChange(state: PiPWindow.PiPState, reason: string) {
    if (canIUse('SystemCapability.Window.SessionManager')) {
      switch (state) {
        case PiPWindow.PiPState.STARTED:
          this.hintMsgVisibility = true
          this.startPlayOrResumePlay()
          break
        case PiPWindow.PiPState.ABOUT_TO_STOP:
          this.startPlayOrResumePlay()
          break
        case PiPWindow.PiPState.STOPPED:
          this.hintMsgVisibility = false
          break
        default:
          break
      }
    }
  }

  onPipActionEvent(event: PiPWindow.PiPActionEventType, status: number | undefined) {
    switch (event) {
      case 'playbackStateChanged':
        if (status === 0) {
          this.pause()
        } else {
          this.startPlayOrResumePlay()
        }
        break
      default:
        break
    }
  }

  async startPip() {
    if (!this.pipController) {
      await this.createPipController()
    }
    if (!this.pipController) {
      return
    }
    await this.pipController.startPiP()
  }

  async stopPip() {
    if (!this.pipController) {
      return
    }
    await this.pipController.stopPiP()
  }

  @Builder
  audioMenu() {
    Menu() {
      ForEach(this.ui.audioStreams, (item: MediaStream) => {
        MenuItem({
          content: (item.DisplayTitle ?? '') as string
        }).selectIcon(true)
          .selected(this.ui.selectedAudioStream == item)
          .onClick(() => {
            this.onAudioTrackSelected(item)
          })
      })
    }
  }

  @Builder
  subtitleMenu() {
    Menu() {
      ForEach(this.ui.subtitleStreams, (item: MediaStream) => {
        MenuItem({
          content: (item.DisplayTitle ?? '') as string
        }).selectIcon(true)
          .selected(this.ui.selectedSubtitleStream == item)
          .onClick(() => {
            this.onSubtitleSelected(item)
          })
      })
    }
  }

  @Builder
  qualityMenu() {
    Menu() {
      ForEach(this.ui.qualityOptions, (option: QualityOption) => {
        MenuItem({
          content: `${option.maxHeight}p - ${this.formatBitrate(option.bitrate)}`
        }).selectIcon(true)
          .selected(this.ui.selectedBitrate == option.bitrate)
          .onClick(() => {
            this.onQualitySelected(option)
          })
      })
    }
  }

  @Builder
  dialogEpisode(_this: AVPlayerView) {
    Column({ space: 10 }) {
      List({ space: 8, initialIndex: _this.ui.playList.findIndex(e => e.id == _this.ui.currentPlayId) }) {
        ForEach(_this.ui.playList, (item: QueueItem, index: number) => {
          ListItem() {
            Row({ space: 12 }) {
              ImageKnifeComponent({
                imageKnifeOption: {
                  loadSrc: item.image,
                  objectFit: ImageFit.Cover,
                  border: {
                    radius: 8
                  }
                }
              })
                .width(112)
                .aspectRatio(16 / 9)

              Column({ space: 8 }) {
                Text(`第 ${item.playIndex} 集`)
                  .fontColor(_this.ui.currentPlayId == item.id ? $r('app.color.color_main') : Color.White)
                  .fontSize(15)
                  .fontWeight(FontWeight.Medium)

                if (item.name != `第 ${item.playIndex} 集`) {
                  Text(item.name)
                    .fontColor('#D9FFFFFF')
                    .fontSize(13)
                    .maxLines(2)
                    .ellipsisMode(EllipsisMode.END)
                    .textOverflow({ overflow: TextOverflow.Ellipsis })
                }

                Row({ space: 12 }) {
                  Text(item.durationText.length > 0 ? item.durationText : '时长未知')
                    .fontColor('#B3FFFFFF')
                    .fontSize(11)
                  Text(item.played ? '已观看' : '未观看')
                    .fontColor(item.played ? $r('app.color.color_main') : '#B3FFFFFF')
                    .fontSize(11)
                }
              }
              .layoutWeight(1)
              .alignItems(HorizontalAlign.Start)
            }
            .width('100%')
            .padding(8)
            .backgroundColor('rgba(255,255,255,0.08)')
            .borderWidth(1)
            .borderColor(_this.ui.currentPlayId == item.id ? $r('app.color.color_main') : 'rgba(255,255,255,0.18)')
            .borderRadius(12)
            .onClick(() => {
              _this.onEpisodeChange(index)
              SmartDialog.dismiss()
            })
          }
        })
      }.width('100%').height('100%')
    }.height('100%').padding(20).backgroundColor('rgba(0,0,0,0.8)')
    .width(340)
  }

  @Builder
  dialogSpeed(_this: AVPlayerView) {
    Column({ space: 10 }) {
      Stack() {
        Text('2.0X')
          .fontColor(_this.playSpeed === media.PlaybackSpeed.SPEED_FORWARD_2_00_X ? $r('app.color.color_main') : Color.White)
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
      }.menuItemStyle().onClick(() => {
        _this.playSpeed = media.PlaybackSpeed.SPEED_FORWARD_2_00_X
        try {
          _this.avPlayer?.setSpeed(media.PlaybackSpeed.SPEED_FORWARD_2_00_X)
        } catch (err) {
        }
        SmartDialog.dismiss()
      })
      Stack() {
        Text('1.75X')
          .fontColor(_this.playSpeed === media.PlaybackSpeed.SPEED_FORWARD_1_75_X ? $r('app.color.color_main') : Color.White)
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
      }.menuItemStyle().onClick(() => {
        _this.playSpeed = media.PlaybackSpeed.SPEED_FORWARD_1_75_X
        try {
          _this.avPlayer?.setSpeed(media.PlaybackSpeed.SPEED_FORWARD_1_75_X)
        } catch (err) {
        }
        SmartDialog.dismiss()
      })
      Stack() {
        Text('1.25X')
          .fontColor(_this.playSpeed === media.PlaybackSpeed.SPEED_FORWARD_1_25_X ? $r('app.color.color_main') : Color.White)
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
      }.menuItemStyle().onClick(() => {
        _this.playSpeed = media.PlaybackSpeed.SPEED_FORWARD_1_25_X
        try {
          _this.avPlayer?.setSpeed(media.PlaybackSpeed.SPEED_FORWARD_1_25_X)
        } catch (err) {
        }
        SmartDialog.dismiss()
      })
      Stack() {
        Text('1.0X')
          .fontColor(_this.playSpeed === media.PlaybackSpeed.SPEED_FORWARD_1_00_X ? $r('app.color.color_main') : Color.White)
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
      }.menuItemStyle().onClick(() => {
        _this.playSpeed = media.PlaybackSpeed.SPEED_FORWARD_1_00_X
        try {
          _this.avPlayer?.setSpeed(media.PlaybackSpeed.SPEED_FORWARD_1_00_X)
        } catch (err) {
        }
        SmartDialog.dismiss()
      })
      Stack() {
        Text('0.75X')
          .fontColor(_this.playSpeed === media.PlaybackSpeed.SPEED_FORWARD_0_75_X ? $r('app.color.color_main') : Color.White)
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
      }.menuItemStyle().onClick(() => {
        _this.playSpeed = media.PlaybackSpeed.SPEED_FORWARD_0_75_X
        try {
          _this.avPlayer?.setSpeed(media.PlaybackSpeed.SPEED_FORWARD_0_75_X)
        } catch (err) {
        }
        SmartDialog.dismiss()
      })
    }.height('100%').padding(10).backgroundColor('rgba(0,0,0,0.8)')
    .width(200)
  }

  @Builder
  topControlView() {
    Row() {
      Image($r('app.media.ic_back'))
        .width(44)
        .height(44)
        .padding(12)
        .margin({ left: 16 })
        .onClick(() => {
          this.handleTopBackClick()
        })
      Text(this.ui.title)
        .fontColor(Color.White)
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .margin({ left: 4 })
        .layoutWeight(1)
        .textOverflow({ overflow: TextOverflow.MARQUEE })
      Text(this.getReportButtonText())
        .fontColor(this.ui.isContentReported ? 'rgba(255,255,255,0.72)' : Color.White)
        .fontSize(12)
        .fontWeight(FontWeight.Medium)
        .padding({ left: 10, right: 10, top: 6, bottom: 6 })
        .backgroundColor(this.ui.isContentReported ? 'rgba(255,255,255,0.08)' : 'rgba(255,255,255,0.14)')
        .borderRadius(999)
        .margin({ right: 8 })
        .onClick(() => {
          this.vm.showReportComposer()
        })
        .bindSheet($$this.ui.isReportSheetVisible, this.reportSheetBuilder(), {
          height: 360,
          preferType: SheetType.BOTTOM,
          title: { title: '内容举报' },
          onDisappear: () => {
            this.ui.isReportSheetVisible = false
          }
        })
      Text(this.getEngineBadgeLabel())
        .fontColor(Color.White)
        .fontSize(10)
        .fontWeight(FontWeight.Medium)
        .padding({ left: 8, right: 8, top: 4, bottom: 4 })
        .backgroundColor('rgba(255,255,255,0.18)')
        .borderRadius(999)
        .margin({ right: 12 })
      AVCastPicker({
        normalColor: Color.White,
      })
        .width(24)
        .height(24)
        .margin({ right: 32 })
        .visibility(this.ui.isCastPlaying ? Visibility.None : Visibility.Visible)
    }
    .height(this.vm.appUIState.isFullScreen ? 76 : 56 + this.vm.appUIState.safeTop)
    .width('100%')
    .justifyContent(FlexAlign.Start)
    .width('100%')
    .padding({
      top: this.vm.appUIState.isFullScreen ? 10 : this.vm.appUIState.safeTop,
      left: this.vm.appUIState.isFullScreen ? this.vm.appUIState.safeTop : 0,
      right: this.vm.appUIState.isFullScreen ? this.vm.appUIState.safeTop : 0,
    })
    .linearGradient({
      angle: 0,
      colors: [
        ['rgba(26, 30, 35, 0.0)', 0],
        ['rgba(26, 30, 35, 0.2)', 0.3],
        ['rgba(26, 30, 35, 0.4)', 0.6],
        ['rgba(26, 30, 35, 0.8)', 1]
      ]
    })
    .visibility(OneMoreUtil.isPc() ? Visibility.None : Visibility.Visible)
  }

  private getReportButtonText(): string {
    if (this.ui.isReportSubmitting) {
      return '提交中'
    }
    if (this.ui.isContentReported) {
      return '已举报'
    }
    return '举报'
  }

  @Builder
  private reportSheetBuilder(): void {
    Column({ space: 12 }) {
      Text('请填写举报原因，最多 100 字。')
        .fontSize(13)
        .fontColor('rgba(255,255,255,0.72)')
        .width('100%')
      TextInput({
        text: this.ui.reportContentDraft,
        placeholder: '请输入举报内容',
      })
        .onChange((value: string) => {
          this.vm.updateReportContentDraft(value)
        })
        .maxLines(4)
        .height(120)
        .align(Alignment.TopStart)
        .padding(12)
        .fontSize(14)
        .fontColor(Color.White)
        .placeholderColor('rgba(255,255,255,0.35)')
        .backgroundColor('rgba(255,255,255,0.08)')
        .borderRadius(12)
      Text(`${this.vm.getReportDescriptionLength()}/${this.vm.getReportDescriptionMaxLength()}`)
        .fontSize(12)
        .fontColor('rgba(255,255,255,0.52)')
        .width('100%')
        .textAlign(TextAlign.End)
      Row({ space: 12 }) {
        Row() {
          Text('取消')
            .fontSize(14)
            .fontColor(Color.White)
            .fontWeight(FontWeight.Medium)
        }
        .layoutWeight(1)
        .height(44)
        .borderRadius(22)
        .backgroundColor('rgba(255,255,255,0.12)')
        .justifyContent(FlexAlign.Center)
        .onClick(() => {
          this.vm.hideReportComposer(false)
        })
        Row() {
          Text(this.ui.isReportSubmitting ? '提交中' : '确认举报')
            .fontSize(14)
            .fontColor(this.vm.canSubmitReport() ? '#111111' : 'rgba(17,17,17,0.45)')
            .fontWeight(FontWeight.Bold)
        }
        .layoutWeight(1)
        .height(44)
        .borderRadius(22)
        .backgroundColor(this.vm.canSubmitReport() ? '#FFFFFF' : 'rgba(255,255,255,0.35)')
        .justifyContent(FlexAlign.Center)
        .onClick(() => {
          if (!this.vm.canSubmitReport()) {
            if (!this.ui.isReportSubmitting) {
              this.vm.submitContentReport()
            }
            return
          }
          this.vm.submitContentReport()
        })
      }
      .width('100%')
    }
    .padding({ left: 16, right: 16, top: 8, bottom: 24 + this.vm.appUIState.safeBottom })
    .backgroundColor('#1A1A1A')
  }

  private handleTopBackClick(): void {
    if (this.vm.appUIState.isFullScreen) {
      this.vm.appUIState.isFullScreen = false
      return
    }
    emitter.emit(Constants.PLAYER_PAGE_EVENT, {
      data: {
        eventName: 'onBackPressed'
      }
    })
  }

  private getEngineBadgeLabel(): string {
    if (this.ui.effectivePlayerEngine === PlayerEngineType.MPV) {
      const useHardwareDecode = this.ui.forceMpvHardwareDecode || AppPreference.getInstance().getMpvUseHardwareDecode()
      return useHardwareDecode ? 'MPV 硬解' : 'MPV 软解'
    }
    if (this.ui.effectivePlayerEngine === PlayerEngineType.AV) {
      return 'AV'
    }
    return '自动'
  }

  @Builder
  bottomControlView() {
    Column() {
      Flex({ direction: FlexDirection.Row, alignItems: ItemAlign.Center, justifyContent: FlexAlign.Start }) {
        Text(this.currentTime)
          .fontColor(Color.White)
          .fontSize(13)
          .fontWeight(FontWeight.Bold)
          .margin({ left: 16, right: 8 })
        Slider({
          value: this.progressValue,
          min: 0,
          max: PROGRESS_MAX_VALUE,
          step: 1,
          style: SliderStyle.OutSet
        })
          .layoutWeight(1)
          .blockColor(Color.White)
          .trackColor(Color.Gray)
          .selectedColor($r('app.color.color_main'))
          .showTips(true)
          .enabled(this.slideEnable)
          .onChange((value: number, mode: SliderChangeMode) => {
            this.handleSliderSeek(value, mode)
          })
        Text(this.totalTime)
          .fontColor(Color.White)
          .fontSize(13)
          .fontWeight(FontWeight.Bold)
          .margin({ left: 8, right: 8 })
        Image($r('app.media.ic_full'))
          .width(48)
          .height(48)
          .padding(12)
          .onClick(() => {
            this.vm.appUIState.isFullScreen = !this.vm.appUIState.isFullScreen
          })
      }.width('100%')

      Row() {
        Image(this.ui.CONTROL_PlayStatus == PlayStatus.PLAY ? $r('app.media.ic_pause') : $r('app.media.ic_play'))
          .width(44)
          .height(44)
          .padding(12)
          .onClick(() => {
            if (this.ui.CONTROL_PlayStatus == PlayStatus.PLAY) {
              this.pause()
            } else {
              try {
                this.avPlayer?.play()
              } catch (err) {
              }
            }
          })
        if (this.ui.playerMenus?.hasNext) {
          Image($r('app.media.ic_next'))
            .width(44)
            .height(44)
            .padding(12)
            .onClick(() => {
              this.onNext()
            })
        }
        Button() {
          Image($r("app.media.ic_audio"))
            .width(44)
            .height(44)
            .padding(12)
        }.backgroundColor(Color.Transparent).bindMenu(this.audioMenu())

        Button() {
          Image($r("app.media.ic_subtitle"))
            .width(44)
            .height(44)
            .padding(12)
        }.backgroundColor(Color.Transparent).bindMenu(this.subtitleMenu())

        Button() {
          Image($r("app.media.ic_info"))
            .width(44)
            .height(44)
            .padding(10)
        }.backgroundColor(Color.Transparent)
        .onClick(() => {
          if (this.ui.playerMenus) {
            this.ui.playerMenus.isShowPlayBackInfo = true
          }
        })

        // 弹幕功能暂不上线，先注释播放器工具栏入口。
        // Button() {
        //   Image(this.ui.danmuOpen ? $r('app.media.danmu_open') : $r('app.media.danmu_close'))
        //     .width(44)
        //     .height(44)
        //     .padding(10)
        // }
        // .backgroundColor(Color.Transparent)
        // .visibility(this.ui.enableDanmu ? Visibility.Visible : Visibility.None)
        // .onClick(() => {
        //   this.ui.danmuOpen = !this.ui.danmuOpen
        //   if (this.ui.danmuOpen) {
        //     this.ui.danmuModel.show()
        //   } else {
        //     this.ui.danmuModel.hide()
        //   }
        // })

        Blank().layoutWeight(1)
        if (this.ui.mediaType == BaseItemKind.Episode) {
          Text('选集')
            .fontColor(Color.White)
            .fontSize(16)
            .height('100%')
            .padding({ left: 16, right: 16 })
            .onClick(() => {
              SmartDialog.show({
                builder: this.dialogEpisode,
                builderArgs: this,
                alignment: Alignment.End,
              })
            })
        }
        Button() {
          Text('质量')
            .fontColor(Color.White)
            .fontSize(16)
            .height('100%')
            .padding({ left: 16, right: 16 })
        }.backgroundColor(Color.Transparent).bindMenu(this.qualityMenu())

        Text('倍速')
          .fontColor(Color.White)
          .fontSize(16)
          .height('100%')
          .padding({ left: 16, right: 16 })
          .onClick(() => {
            SmartDialog.show({
              builder: this.dialogSpeed,
              builderArgs: this,
              alignment: Alignment.End,
            })
          })
        if (OneMoreUtil.isPc()) {
          Image(this.vm.appUIState.isFullScreen ? $r('app.media.ic_out_fullscreen') : $r('app.media.ic_fullscreen'))
            .width(44)
            .height(44)
            .padding(12)
            .onClick(() => {
              this.vm.appUIState.isFullScreen = !this.vm.appUIState.isFullScreen
              if (this.vm.appUIState.isFullScreen) {
                OneMoreUtil.maximize()
              } else {
                OneMoreUtil.recover()
              }
            })
        }
      }.height(44).padding({ left: 8, right: 8 })
      .visibility(
        this.vm.appUIState.isFullScreen || (OneMoreUtil.isBigScreen() && !OneMoreUtil.isPc()) ?
          Visibility.Visible : Visibility.None
      )
    }.padding({
      bottom: this.showControls && !this.vm.appUIState.isFullScreen ? this.vm.appUIState.safeBottom : 10,
      left: this.vm.appUIState.isFullScreen ? this.vm.appUIState.safeTop : 0,
      right: this.vm.appUIState.isFullScreen ? this.vm.appUIState.safeTop : 0,
    })
  }

  @Builder
  VolumeUi() {
    Column() {
      Progress({
        value: Number((this.mCurrentVolumeValue / this.mMaxVolumeValue * 100).toFixed(0)),
        total: 100,
        type: ProgressType.Linear
      })
        .style({ strokeWidth: 10, enableSmoothEffect: false })
        .width(100)
        .rotate({ angle: -90 })
        .margin({ top: 20, left: -20 })
        .alignSelf(ItemAlign.Center)
        .backgroundColor(Color.White)
      Image($r('app.media.video_volume_icon'))
        .objectFit(ImageFit.Auto)
        .margin({ top: 50, left: -15 })
        .width(30)
        .height(30)
        .alignSelf(ItemAlign.Center)
        .borderStyle(BorderStyle.Dashed)
    }.visibility(this.showVolumeUi ? Visibility.Visible : Visibility.Hidden)
    .alignSelf(ItemAlign.Center)
  }

  @Builder
  BrightnessUi() {
    Column() {
      Image($r('app.media.video_brightness_6_white_36dp'))
        .objectFit(ImageFit.Auto)
        .width(30)
        .height(30)
        .alignSelf(ItemAlign.Center)
      Text((this.mCurrentBrightness / 255 * 100).toFixed(0) + '%')
        .fontColor(Color.White)
        .textAlign(TextAlign.Center)
        .width(80)
    }.visibility(this.showBrightnessUi ? Visibility.Visible : Visibility.Hidden)
    .width(80)
    .alignSelf(ItemAlign.Center)
  }

  @Builder
  SeekProgressUi() {
    Column({ space: 10 }) {
      if (this.seekPreviewPixelMap) {
        Stack() {
          Image(this.seekPreviewPixelMap)
            .objectFit(ImageFit.Contain)
            .width('100%')
            .height('100%')
        }
          .width(172)
          .height(104)
          .backgroundColor('#091018')
          .borderRadius(14)
          .clip(true)
      }

      Row() {
        Text(this.mSeekCurrentTime)
          .fontColor($r('app.color.color_main'))
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
        Blank()
          .layoutWeight(1)
        Text(this.totalTime)
          .fontColor('#D7DEE8')
          .fontSize(13)
      }
        .width('100%')

      Row({ space: 8 }) {
        Text('松手后跳转')
          .fontColor('#AEB8C4')
          .fontSize(11)

        Blank()
          .layoutWeight(1)

        Text(`${Math.round(this.mSeekTimePosition)}%`)
          .fontColor('#8E9AA8')
          .fontSize(11)
      }
        .width('100%')
    }
    .visibility(this.showSeekProgressUi ? Visibility.Visible : Visibility.Hidden)
    .width(220)
    .padding({ top: 14, bottom: 14, left: 16, right: 16 })
    .backgroundColor('#CC11161D')
    .borderRadius(20)
    .border({ width: 1, color: 'rgba(255,255,255,0.10)' })
    .alignItems(HorizontalAlign.Center)
    .alignSelf(ItemAlign.Center)
  }

  build() {
    Stack({ alignContent: Alignment.Center }) {
      Text('当前视频正在画中画播放')
        .fontColor(Color.Gray)
        .margin({ bottom: 30 })
        .visibility(this.hintMsgVisibility ? Visibility.Visible : Visibility.Hidden)
      Column() {
        XComponent({
          id: 'avplayer_xcomponent',
          type: XComponentType.SURFACE,
          controller: this.xComponentController
        })
          .onLoad(async () => {
            this.mContext = getContext(this)
            this.surfaceId = this.xComponentController.getXComponentSurfaceId()
            if (this.videoUrl) {
              this.startPlay()
            }
            setTimeout(() => {
              this.mFirst = false
            }, 300)
            if (canIUse('SystemCapability.Window.SessionManager')) {
              if (!this.pipController) {
                await this.createPipController()
              }
            }
          })
          .onDestroy(() => {
            this.stop()
          })
          .width('100%')
          .height('100%')
          .id('avplayer_xcomponent')
          .onClick(() => {
            this.timeCountdown()
          })
      }
      .width(this.videoWidth)
      .aspectRatio(this.videoAspectRatio)

      if (this.ui.subtitles.length > 0) {
        Stack() {
          Text(this.ui.currentSubtitle)
            .fontSize(this.vm.appUIState.isFullScreen ? 21 : 13)
            .fontColor(Color.White)
            .width('100%')
            .padding(20)
            .textAlign(TextAlign.Center)
            .textShadow({ radius: 2 })
            .hitTestBehavior(HitTestMode.None)
        }.height('100%').alignContent(Alignment.Bottom)
      }

      // 弹幕功能暂不上线，先注释弹幕渲染层。
      // DanmakuViewV2({ model: this.ui.danmuModel })
      //   .padding({
      //     top: this.vm.appUIState.isFullScreen ? 10 : this.vm.appUIState.safeTop + 10,
      //   })
      //   .width('100%')
      //   .height('100%')
      //   .opacity(0.6)

      Flex({ alignItems: ItemAlign.Center, justifyContent: FlexAlign.SpaceBetween }) {
        this.VolumeUi()
        this.SeekProgressUi()
        this.BrightnessUi()
      }.width('100%')
      .height('100%')
      .padding({ left: 36, right: 36 })

      Column() {
        this.topControlView()
        Blank().layoutWeight(1)
        this.bottomControlView()
      }.height('100%')
      .visibility(this.showControls ? Visibility.Visible : Visibility.Hidden)

      if (this.vm.appUIState.isFullScreen &&
        this.fullShowLock &&
        this.showControls &&
        !this.showVolumeUi &&
        !this.showBrightnessUi &&
        !this.showSeekProgressUi &&
        !OneMoreUtil.isPc()) {
        Stack() {
          Image(this.lock ? $r('app.media.lock') : $r('app.media.unlock'))
            .width(20)
            .height(20)
            .objectFit(ImageFit.Contain)
            .margin({ right: 60 })
            .onClick(() => {
              this.lock = !this.lock
              if (this.lock) {
                this.showControls = false
              }
            })
        }.width('100%')
        .alignContent(Alignment.End)
      }

      if (this.ui.playerMenus?.isShowPlayBackInfo) {
        PlaybackInfoCard({
          menus: this.ui.playerMenus,
          onDismiss: () => {
            if (this.ui.playerMenus) {
              this.ui.playerMenus.isShowPlayBackInfo = false
            }
          }
        })
      }

      Stack() {
        Button('退出投屏')
          .onClick(() => {
            this.stopCast()
          })
      }.width('100%')
      .height('100%')
      .backgroundColor(Color.Black)
      .visibility(this.ui.isCastPlaying ? Visibility.Visible : Visibility.Hidden)

      Image($r('app.media.icon_replay'))
        .objectFit(ImageFit.Auto)
        .width('120px')
        .height('120px')
        .visibility(this.replayVisible)
        .onClick(() => {
          if (this.avPlayer) {
            try {
              this.avPlayer.seek(0, media.SeekMode.SEEK_CLOSEST)
              this.avPlayer.play()
              this.replayVisible = Visibility.None
            } catch (err) {
              this.stop()
              this.startPlay()
            }
          } else {
            this.startPlay()
          }
        })

      Image($r('app.media.icon_load'))
        .objectFit(ImageFit.Auto)
        .width('120px')
        .height('120px')
        .visibility(this.loadingVisible)
        .border({ width: 0 })
        .borderStyle(BorderStyle.Dashed)

      Stack({ alignContent: Alignment.Top }) {
        Row() {
          Image($r('app.media.ic_speed')).height(20)
          Text('正在倍速播放')
            .fontSize(14)
            .fontColor(Color.White)
        }
        .margin({ top: 20 })
        .padding(4)
        .backgroundColor(Color.Black)
        .borderRadius(4)
        .opacity(0.4)
      }
      .width('100%')
      .height('100%')
      .visibility(this.ui.isFastSpeed ? Visibility.Visible : Visibility.None)
    }
    .width('100%')
    .height('100%')
    .alignContent(Alignment.Center)
    .backgroundColor(Color.Black)
    .clip(true)
    .onHover((isHover: boolean) => {
      if (OneMoreUtil.isPc()) {
        if (isHover) {
          if (this.loadingVisible == Visibility.Visible) {
            return
          }
          if (!this.showControls) {
            this.timeCountdown()
          }
        } else {
          clearTimeout(avUiTime)
          this.showControls = false
          this.fullShowLock = false
        }
      }
    })
    .onAreaChange((_oldValue: Area, newValue: Area) => {
      avChangeWidth = newValue.width as number
      avChangeHeight = newValue.height as number
      if (avChangeHeight && avChangeHeight > 0) {
        this.videoParentAspectRatio = avChangeWidth / avChangeHeight
        if (this.avPlayer && this.avPlayer.width && this.avPlayer.height) {
          this.setVideoWH(this.avPlayer.width, this.avPlayer.height)
        }
      }
      if (this.vm.appUIState.isFullScreen) {
        this.fullShowLock = true
      }
      // 弹幕功能暂不上线，先注释弹幕层尺寸同步。
      // this.ui.danmuModel.setWidth(this.getUIContext().vp2px(avChangeWidth))
      // this.ui.danmuModel.setHeight(this.getUIContext().vp2px(avChangeHeight))
    })
    .gesture(GestureGroup(GestureMode.Exclusive,
      TapGesture({ count: 2 }).onAction(() => {
        if (OneMoreUtil.isPc()) {
          return
        }
        if (this.lock) {
          return
        }
        if (this.loadingVisible == Visibility.Visible) {
          return
        }
        if (!this.avPlayer) {
          return
        }
        if (this.ui.CONTROL_PlayStatus == PlayStatus.PLAY) {
          this.pause()
        } else {
          try {
            this.avPlayer.play()
          } catch (err) {
          }
        }
      }),
      TapGesture({ count: 1 }).onAction(() => {
        if (this.lock) {
          this.timeLockCountdown()
          return
        }
        if (this.loadingVisible == Visibility.Visible) {
          return
        }
        if (OneMoreUtil.isPc()) {
          this.startOrStop()
        } else {
          if (this.showControls) {
            this.showControls = false
          } else {
            this.timeCountdown()
          }
        }
      }),
      PanGesture(this.panOptionBrightAndVolume)
        .onActionStart((event: GestureEvent | undefined) => {
          if (OneMoreUtil.isPc()) {
            return
          }
          if (this.lock) {
            return
          }
          if (this.isHorizontalDrag) {
            return
          }
          if (event && event.fingerList && event.fingerList[0]) {
            avTouchStartX = event.fingerList[0].localX
            avTouchStartY = event.fingerList[0].localY
          }
        })
        .onActionUpdate((event: GestureEvent | undefined) => {
          if (OneMoreUtil.isPc()) {
            return
          }
          if (this.lock) {
            return
          }
          if (this.isHorizontalDrag) {
            return
          }
          if (this.ui.isFastSpeed) {
            return
          }
          this.isVerticalDrag = true
          if (event && event.fingerList && event.fingerList[0] && avChangeHeight > 0) {
            let touchY = event.fingerList[0].localY
            let deltaY = touchY - avTouchStartY
            let percent = (-deltaY / avChangeHeight)
            let width = avChangeWidth
            if (avTouchStartX >= (width / 2)) {
              this.showVolumeUi = true
              let playerVolumeValue = this.mCurrentVolumeValue
              playerVolumeValue += this.mMaxVolumeValue * percent
              if (playerVolumeValue < 0) {
                playerVolumeValue = 0
              }
              if (playerVolumeValue > this.mMaxVolumeValue) {
                playerVolumeValue = this.mMaxVolumeValue
              }
              this.mCurrentVolumeValue = playerVolumeValue
              if (this.mAudioManager) {
                this.mAudioManager.setVolume(audio.AudioVolumeType.MEDIA, playerVolumeValue, (err: BusinessError) => {
                })
              }
            } else {
              this.showBrightnessUi = true
              let brightness = this.mCurrentBrightness
              brightness += 255 * percent
              if (brightness < 0) {
                brightness = 5
              }
              if (brightness > 255) {
                brightness = 255
              }
              let finalValue = brightness / 255
              if (this.vm.appUIState.windowClass) {
                this.vm.appUIState.windowClass.setWindowBrightness(finalValue)
              }
              this.mCurrentBrightness = brightness
            }
            avTouchStartY = touchY
          }
        })
        .onActionEnd(() => {
          if (OneMoreUtil.isPc()) {
            return
          }
          this.showVolumeUi = false
          this.showBrightnessUi = false
          this.isVerticalDrag = false
        }),
      PanGesture(this.panOptionSeek)
        .onActionStart((event: GestureEvent | undefined) => {
          if (OneMoreUtil.isPc()) {
            return
          }
          if (!this.avPlayer) {
            return
          }
          if (this.lock) {
            return
          }
          if (this.loadingVisible == Visibility.Visible) {
            this.isLoadNotSeek = true
            return
          }
          if (this.ui.isFastSpeed) {
            return
          }
          if (this.isVerticalDrag) {
            return
          }
          if (event && event.fingerList && event.fingerList[0]) {
            this.showControls = false
            avTouchStartX = event.fingerList[0].localX
            this.mSeekTimePosition = this.progressValue
          }
        })
        .onActionUpdate((event: GestureEvent | undefined) => {
          if (OneMoreUtil.isPc()) {
            return
          }
          if (!this.avPlayer) {
            return
          }
          if (this.lock) {
            return
          }
          if (this.isLoadNotSeek) {
            return
          }
          if (this.isVerticalDrag) {
            return
          }
          if (this.ui.isFastSpeed) {
            return
          }
          this.isHorizontalDrag = true
          if (event && event.fingerList && event.fingerList[0] && avChangeWidth > 0) {
            this.showSeekProgressUi = true
            let touchX = event.fingerList[0].localX
            let deltaX = touchX - avTouchStartX
            if (deltaX > 0) {
              this.mSeekDirectionRes = $r('app.media.video_forward_icon')
            } else {
              this.mSeekDirectionRes = $r('app.media.video_backward_icon')
            }
            let position = this.currentPosition
            let totalTimeDuration = this.duration
            let seekTimePosition = Number(position + (deltaX * totalTimeDuration / avChangeWidth))
            if (seekTimePosition < 0) {
              seekTimePosition = 0
            }
            if (seekTimePosition > totalTimeDuration) {
              seekTimePosition = totalTimeDuration
            }
            this.mSeekCurrentTime = this.stringForTime(seekTimePosition)
            this.mSeekTimePosition = Number((seekTimePosition / totalTimeDuration).toFixed(2)) * 100
            this.scheduleSeekPreviewFrame(seekTimePosition, !this.seekPreviewPixelMap)
          }
        })
        .onActionEnd(() => {
          if (OneMoreUtil.isPc()) {
            return
          }
          if (!this.avPlayer) {
            return
          }
          if (this.isLoadNotSeek) {
            this.isLoadNotSeek = false
            return
          }
          if (this.ui.isFastSpeed) {
            return
          }
          if (this.isHorizontalDrag && this.avPlayer) {
            this.progressValue = this.mSeekTimePosition
            this.showControls = false
            let seekMs = this.mSeekTimePosition * (this.duration / 100)
            this.seekTo(seekMs)
            this.currentPosition = seekMs
            this.currentTime = this.formatTime(seekMs)
            this.seekToSubtitle()
            // 弹幕功能暂不上线，先注释拖动进度时的弹幕同步。
            // if (this.ui.danmuModel.isPrepared()) {
            //   this.ui.danmuModel.seekTo(seekMs)
            // }
          }
          this.isHorizontalDrag = false
          this.showSeekProgressUi = false
          this.resetSeekPreviewState()
        }),
      LongPressGesture()
        .onAction(() => {
          if (OneMoreUtil.isPc()) {
            return
          }
          if (this.ui.isCastPlaying) {
            return
          }
          try {
            this.avPlayer?.setSpeed(media.PlaybackSpeed.SPEED_FORWARD_2_00_X)
            this.ui.isFastSpeed = true
          } catch (err) {
          }
        })
        .onActionEnd(() => {
          if (OneMoreUtil.isPc()) {
            return
          }
          if (this.ui.isFastSpeed) {
            try {
              this.avPlayer?.setSpeed(this.playSpeed)
            } catch (err) {
            }
            this.ui.isFastSpeed = false
          }
        })
    ))
  }
}
