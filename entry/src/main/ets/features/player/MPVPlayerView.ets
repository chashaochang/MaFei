import { image } from '@kit.ImageKit'
import { media } from '@kit.MediaKit'
import { audio } from '@kit.AudioKit'
import { QualityOption } from '../../player/qualityoptions/QualityOption'
import { PlayerPageUIState } from './PlayerPageUIState'
import { PlayerPageViewModel } from './PlayerPageViewModel'
import { PlayStatus, PlayerStatus } from './PlayStatus'
import { MpvNativePlayer } from 'mpvplayer'
import { AppPreference } from '../../data/AppPreference'
import { BusinessError, emitter } from '@kit.BasicServicesKit'
import { settings } from '@kit.BasicServicesKit'
import { Constants } from '../../utils/Constants'
import { AVCastPicker, avSession } from '@kit.AVSessionKit'
import avSessionManager from '../../utils/AVSessionManager'
import { common } from '@kit.AbilityKit'
import { SmartDialog } from 'ohos_smart_dialog'
import { QueueItem } from '../../entity/QueueItem'
import {
  MediaPlaybackExternalSubtitle,
  MediaPlaybackItemKind,
  MediaPlaybackRequestHeader,
  MediaPlaybackSource,
  MediaPlaybackStream,
  MediaPlaybackStreamType,
  MediaPlaybackSubtitleDeliveryMethod
} from '../../media/playback/MediaPlaybackModels'
// 弹幕功能暂不上线，先注释引用，后续恢复时取消注释即可。
// import { DanmakuViewV2 } from '@ohos/danmakuflamemaster'
import { OneMoreUtil } from '../../utils/OneMoreUtil'
import { display, window } from '@kit.ArkUI'
import { PlayerEngineType } from '../../entity/PlayerEngineType'
import { ImageKnifeComponent, ImageKnifeOption } from '@ohos/imageknifepro'
import { createMediaImageKnifeOption } from '../../media/catalog/MediaImageKnifeAdapter'
import { VideoFramePreviewHelper } from './VideoFramePreviewHelper'
import { PlaybackInfoCard } from '../../player/ui/PlaybackInfoCard'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver'
import type { MpvTrackInfo } from 'libmpvnative.so'

class NativeMediaPlaybackStream implements MediaPlaybackStream {
  readonly Type: MediaPlaybackStreamType
  readonly Index: number
  readonly DisplayTitle: string
  readonly Language?: string
  readonly Codec?: string
  readonly IsDefault: boolean
  readonly IsExternal: boolean
  readonly DeliveryMethod?: MediaPlaybackSubtitleDeliveryMethod

  constructor(track: MpvTrackInfo, isSubtitle: boolean, index: number) {
    const title = track.title?.trim()
    const language = track.lang?.trim()
    this.Type = isSubtitle ? MediaPlaybackStreamType.Subtitle : MediaPlaybackStreamType.Audio
    this.Index = track.id
    this.DisplayTitle = title && title.length > 0
      ? title
      : `${isSubtitle ? '字幕' : '音轨'} ${index + 1}${language ? ` (${language})` : ''}`
    this.Language = language
    this.Codec = track.codec?.trim()
    this.IsDefault = track.selected === true
    this.IsExternal = track.external === true
    this.DeliveryMethod = isSubtitle
      ? (this.IsExternal ? MediaPlaybackSubtitleDeliveryMethod.External : MediaPlaybackSubtitleDeliveryMethod.Embed)
      : undefined
  }
}

const PROGRESS_MAX_VALUE: number = 100
const MPV_XCOMPONENT_ID: string = 'mpvPlayerSurface'
const MPV_OSD_XCOMPONENT_ID: string = 'mpvPlayerOsdSurface'
const MPV_PROGRESS_VISUAL_TRACK_LEFT_INSET: number = 12
const MPV_PROGRESS_VISUAL_TRACK_RIGHT_INSET: number = 10
const MPV_PROGRESS_TRACK_HEIGHT: number = 4
const MPV_PROGRESS_BASE_COLOR: string = '#261F1F1F'
const MPV_PROGRESS_CACHE_COLOR: string = '#CCFFFFFF'
const MPV_PROGRESS_SLIDER_TRACK_COLOR: string = '#01000000'
const SEEK_SYNC_TOLERANCE_MS: number = 1500
const SEEK_SYNC_TIMEOUT_MS: number = 1800
const SEEK_PREVIEW_DEBOUNCE_MS: number = 70
const STARTUP_END_FILE_ERROR_CONFIRM_MS: number = 1800
const SUBTITLE_SELECTION_RETRY_DELAYS_MS: number[] = [150, 300, 600, 1000, 1500]
const SUBTITLE_SELECTION_BACKGROUND_RETRY_MS: number = 2000
let mpvTouchStartX: number = 0
let mpvTouchStartY: number = 0
let mpvChangeWidth: number = 0
let mpvChangeHeight: number = 0
let mpvUiTime: number = 0
let mpvUiLockTime: number = 0

@Extend(Stack)
function menuItemStyle() {
  .borderRadius(8)
  .backgroundColor('rgba(255,255,255,0.2)')
  .width('100%')
  .layoutWeight(1)
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
export struct MPVPlayerView {
  @Param @Require videoUrl: string
  @Param nativeTopNavigationEnabled: boolean = false
  @Param @Require onNext: () => void
  @Param @Require onAudioTrackSelected: (stream: MediaPlaybackStream) => void
  @Param @Require onSubtitleSelected: (stream: MediaPlaybackStream) => void
  @Param @Require onQualitySelected: (option: QualityOption) => void
  @Param @Require onEpisodeChange: (index: number) => void
  @Param @Require stopCast: () => void

  @Consumer() ui: PlayerPageUIState = new PlayerPageUIState(this.getUIContext())
  @Consumer() vm: PlayerPageViewModel = new PlayerPageViewModel(this.ui)

  @Local progressValue: number = 0
  @Local cacheProgressValue: number = 0
  @Local currentTime: string = '00:00'
  @Local totalTime: string = '00:00'
  @Local duration: number = 0
  @Local currentPosition: number = 0
  @Local showControls: boolean = true
  @Local initialized: boolean = false
  @Local loading: boolean = true
  @Local playbackCompleted: boolean = false
  @Local playSpeed: number = 1
  @Local videoAspectRatio: number = 16 / 9
  @Local videoWidth: string = '100%'
  @Local fullShowLock: boolean = false
  @Local lock: boolean = false
  @Local showSeekPreviewUi: boolean = false
  @Local seekPreviewTime: string = '00:00'
  @Local seekPreviewProgressValue: number = 0
  @Local seekPreviewPixelMap?: image.PixelMap
  @Local showVolumeUi: boolean = false
  @Local showBrightnessUi: boolean = false
  @Local mCurrentVolumeValue: number = 0
  @Local mMaxVolumeValue: number = 20
  @Local mCurrentBrightness: number = 10
  @Local waitingForStartupLandscapeReady: boolean = false

  private xComponentController: XComponentController = new XComponentController()
  private osdXComponentController: XComponentController = new XComponentController()
  private mpvPlayer: MpvNativePlayer = new MpvNativePlayer()
  private progressTimer: number = -1
  private diagnosticsTimer: number = -1
  private initializeTimer: number = -1
  private loadingDelayTimer: number = -1
  private startupEndFileErrorTimer: number = -1
  private subtitleBootstrapTimer: number = -1
  private sourceLoaded: boolean = false
  private xComponentReady: boolean = false
  private osdXComponentReady: boolean = false
  private initializeRequested: boolean = false
  private shouldResumeOnPageShow: boolean = false
  private pendingSubtitleSelection?: MediaPlaybackStream
  private pendingSubtitleSelectionAllowFallback: boolean = false
  private pendingSubtitleSelectionWaitingLogged: boolean = false
  private loadedExternalSubtitleUrls: string[] = []
  private osdSurfaceId: string = '0'
  private osdSurfaceWidthPx: number = 0
  private osdSurfaceHeightPx: number = 0
  private appliedOsdSurfaceKey: string = ''
  private videoParentAspectRatio: number = 16 / 9
  sec: number = 3
  private keepScreenOnState?: boolean
  private isSeekingWithSlider: boolean = false
  private isVerticalDrag: boolean = false
  private isHorizontalDrag: boolean = false
  private gestureSeekPositionMs: number = 0
  private gestureSeekStartPositionMs: number = 0
  private pendingSeekTargetMs: number = -1
  private pendingSeekDeadlineMs: number = 0
  private seekPreviewHelper: VideoFramePreviewHelper = new VideoFramePreviewHelper()
  private seekPreviewTimer: number = -1
  private seekPreviewRequestToken: number = 0
  private seekPreviewLoading: boolean = false
  private queuedSeekPreviewPositionMs: number = -1
  private mAudioManager: audio.AudioManager | null = null
  private panOptionBrightAndVolume: PanGestureOptions = new PanGestureOptions({ direction: PanDirection.Vertical })
  private panOptionSeek: PanGestureOptions = new PanGestureOptions({ direction: PanDirection.Horizontal })
  private playbackStarted: boolean = false
  private lastPlaybackProgressAt: number = 0

  @Monitor('ui.playerStatus')
  onPlayerStateChanged(): void {
    this.sendAVSessionState(this.ui.playerStatus)
    this.updateWindowKeepScreenOn(this.ui.playerStatus === PlayerStatus.PLAYING)
  }

  private changeSystemBar(): void {
    if (!this.vm.appUIState.isFullScreen && this.showControls) {
      this.vm.appUIState.windowClass?.setWindowSystemBarEnable(['status', 'navigation'])
    } else {
      this.vm.appUIState.windowClass?.setWindowSystemBarEnable([])
    }
  }

  @Monitor('showControls')
  onControlVisibilityChanged(): void {
    this.syncNativeTopNavigationVisibility()
    this.changeSystemBar()
  }

  @Monitor('vm.appUIState.isFullScreen')
  onScreenChanged(): void {
    if (this.vm.appUIState.isFullScreen) {
      this.waitingForStartupLandscapeReady = false
    }
    this.changeSystemBar()
    if (this.vm.appUIState.isFullScreen) {
      if (OneMoreUtil.shouldRequestPlayerManualRotation(this.vm.appUIState.windowClass)) {
        if (canIUse('SystemCapability.Window.SessionManager')) {
          this.vm.appUIState.windowClass?.setPreferredOrientation(window.Orientation.USER_ROTATION_LANDSCAPE).catch()
        }
      }
    } else {
      if (OneMoreUtil.isNormalPhone()) {
        SmartDialog.dismiss()
      }
      if (OneMoreUtil.shouldRequestPlayerManualRotation(this.vm.appUIState.windowClass)) {
        if (canIUse('SystemCapability.Window.SessionManager')) {
          this.vm.appUIState.windowClass?.setPreferredOrientation(window.Orientation.USER_ROTATION_PORTRAIT)
        }
      }
    }
  }

  private shouldShowLegacyTopControl(): boolean {
    return !this.nativeTopNavigationEnabled || this.vm.appUIState.isFullScreen
  }

  private syncNativeTopNavigationVisibility(): void {
    if (this.nativeTopNavigationEnabled) {
      this.ui.controlsVisible = this.showControls
    }
  }

  @Monitor('lock')
  onLockChanged(): void {
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

  @Monitor('ui.isCastPlaying')
  onCastChanged(): void {
    if (this.ui.isCastPlaying) {
      this.vm.appUIState.isFullScreen = false
    }
  }

  @Monitor('videoUrl')
  onUrlChange(): void {
    this.resetSeekPreviewState()
    void this.seekPreviewHelper.release()
    this.stopLoadingDelayTimer()
    this.sourceLoaded = false
    this.playbackCompleted = false
    this.playbackStarted = false
    this.lastPlaybackProgressAt = 0
    this.currentPosition = 0
    this.progressValue = 0
    this.cacheProgressValue = 0
    this.currentTime = '00:00'
    this.loadedExternalSubtitleUrls = []
    this.clearPendingSubtitleSelection(false)
    this.clearPlaybackHeaders()
    this.prepareSeekPreview()
    if (this.initialized) {
      this.loadCurrentVideo()
    }
  }

  aboutToAppear(): void {
    this.syncNativeTopNavigationVisibility()
    try {
      this.mAudioManager = audio.getAudioManager()
      const volumeManager = this.mAudioManager.getVolumeManager()
      const groupId = audio.DEFAULT_VOLUME_GROUP_ID
      const audioVolumeGroupManager = volumeManager.getVolumeGroupManagerSync(groupId)
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
    this.bindPlayerCallbacks()
    emitter.on(Constants.PLAYER_EVENT, async (data) => {
      if (!data.data || !data.data['eventName']) {
        return
      }
      let name = data.data['eventName'] as string
      switch (name) {
        case 'onPageShow':
          if (this.shouldResumeOnPageShow && this.sourceLoaded && !this.playbackCompleted) {
            this.resumePlayback()
          }
          break
        case 'onPageHide':
          if (this.ui.CONTROL_PlayStatus === PlayStatus.PLAY) {
            this.shouldResumeOnPageShow = true
            this.pausePlayback()
          }
          break
        case 'Pause':
          this.pausePlayback()
          break
        case 'Play':
        case 'Resume':
          this.resumePlayback()
          break
        case 'Stop':
        case 'Destroy':
          this.stopScopedPlayback()
          break
        case 'Seek':
          let ms = data.data['args'] as number | undefined
          if (ms !== undefined) {
            this.seekTo(ms)
          }
          break
        case 'Speed':
          let speed = data.data['args'] as string | undefined
          if (speed) {
            this.applyPlaybackSpeed(this.speedFromString(speed))
          }
          break
      }
    })
    let context = getContext(this) as common.UIAbilityContext
    context.eventHub.on('onForeground', () => {
      this.shouldResumeOnPageShow = false
    })
  }

  aboutToDisappear(): void {
    if (this.nativeTopNavigationEnabled) {
      this.ui.controlsVisible = false
    }
    this.resetSeekPreviewState()
    void this.seekPreviewHelper.release()
    this.updateWindowKeepScreenOn(false)
    emitter.off(Constants.PLAYER_EVENT.eventId)
    let context = getContext(this) as common.UIAbilityContext
    context.eventHub.off('onForeground')
    SmartDialog.dismiss()
    this.stopInitializeTimer()
    this.stopProgressTimer()
    this.stopDiagnosticsTimer()
    this.stopLoadingDelayTimer()
    this.stopStartupEndFileErrorTimer()
    this.stopSubtitleBootstrapTimer()
    this.clearPlaybackHeaders()
    this.mpvPlayer.release()
    this.mpvPlayer.clearOsdSurface()
    this.initialized = false
    this.sourceLoaded = false
    this.xComponentReady = false
    this.osdXComponentReady = false
    this.initializeRequested = false
    this.loadedExternalSubtitleUrls = []
    this.osdSurfaceId = '0'
    this.osdSurfaceWidthPx = 0
    this.osdSurfaceHeightPx = 0
    this.appliedOsdSurfaceKey = ''
    this.waitingForStartupLandscapeReady = false
    this.clearPendingSubtitleSelection(false)
    this.ui.playbackPositionMs = 0
    this.cacheProgressValue = 0
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
      console.error(`[MPVPlayer] setWindowKeepScreenOn(${shouldKeepOn}) failed: ${JSON.stringify(err)}`)
    })
  }

  private shouldUseHardwareDecode(): boolean {
    return this.ui.forceMpvSoftwareDecode
      ? false
      : (this.ui.forceMpvHardwareDecode || AppPreference.getInstance().getMpvUseHardwareDecode())
  }

  private shouldUseStartupLandscapeMask(): boolean {
    return OneMoreUtil.isNormalPhone() &&
      !this.ui.isCastPlaying &&
      this.vm.mediaSourceOrNull?.isIsoStaticPlaybackFallback() === true
  }

  private shouldShowStartupLandscapeLoading(): boolean {
    return this.waitingForStartupLandscapeReady &&
      !this.vm.appUIState.isFullScreen &&
      !this.playbackCompleted &&
      !this.ui.isCastPlaying
  }

  private shouldUseSurfacePlayback(): boolean {
    return this.shouldUseHardwareDecode() && !this.vm.mediaSourceOrNull?.isDolbyVisionStream()
  }

  private hasOsdSurfaceSize(): boolean {
    return this.osdSurfaceWidthPx > 0 && this.osdSurfaceHeightPx > 0
  }

  private setVideoWH(videoW: number, videoH: number): void {
    if (!videoW || !videoH || videoH === 0) {
      return
    }
    const videoAspect = videoW / videoH
    this.videoAspectRatio = videoAspect
    if (this.videoParentAspectRatio > 0 && this.videoParentAspectRatio > videoAspect) {
      this.videoWidth = (videoW / (videoH * this.videoParentAspectRatio)) * 100 + '%'
    } else {
      this.videoWidth = '100%'
    }
  }

  private updateVideoLayoutFromPlayer(): void {
    const width = this.mpvPlayer.getVideoWidth()
    const height = this.mpvPlayer.getVideoHeight()
    if (width > 0 && height > 0) {
      this.setVideoWH(width, height)
      this.markPlaybackStarted('video-size')
    }
  }

  private markPlaybackStarted(reason: string): void {
    this.stopStartupEndFileErrorTimer()
    if (!this.playbackStarted) {
      this.logMpv(`playback started via ${reason}`)
    }
    this.playbackStarted = true
    this.lastPlaybackProgressAt = Date.now()
    this.stopLoadingDelayTimer()
    if (this.loading) {
      this.loading = false
    }
  }

  private requestLoadingVisible(reason: string, force: boolean = false): void {
    if (force || !this.playbackStarted) {
      this.stopLoadingDelayTimer()
      this.loading = true
      return
    }
    this.stopLoadingDelayTimer()
    this.loadingDelayTimer = setTimeout(() => {
      this.loadingDelayTimer = -1
      if (!this.initialized || this.playbackCompleted) {
        return
      }
      const currentPosition = this.mpvPlayer.getCurrentPositionMs()
      if (currentPosition > this.currentPosition) {
        this.markPlaybackStarted('delayed-loading-progress')
        return
      }
      if (Date.now() - this.lastPlaybackProgressAt >= 1200) {
        this.logMpv(`show loading after delayed confirm: ${reason}`)
        this.loading = true
      }
    }, 800)
  }

  private updateOsdSurfaceSize(widthPx: number, heightPx: number): void {
    const normalizedWidth = Math.max(0, Math.round(widthPx))
    const normalizedHeight = Math.max(0, Math.round(heightPx))
    if (normalizedWidth <= 0 || normalizedHeight <= 0) {
      return
    }
    this.osdSurfaceWidthPx = normalizedWidth
    this.osdSurfaceHeightPx = normalizedHeight
    if (this.shouldUseSurfacePlayback()) {
      this.applyOsdSurfaceBinding()
      this.scheduleInitializePlayer()
    }
  }

  private applyOsdSurfaceBinding(force: boolean = false): boolean {
    if (!this.shouldUseSurfacePlayback()) {
      if (force) {
        this.mpvPlayer.clearOsdSurface()
      }
      this.appliedOsdSurfaceKey = ''
      return true
    }
    if (!this.osdXComponentReady || this.osdSurfaceId.length === 0 || this.osdSurfaceId === '0') {
      return false
    }
    if (this.osdSurfaceWidthPx <= 0 || this.osdSurfaceHeightPx <= 0) {
      return false
    }
    const bindingKey = `${this.osdSurfaceId}:${this.osdSurfaceWidthPx}x${this.osdSurfaceHeightPx}`
    if (!force && bindingKey === this.appliedOsdSurfaceKey) {
      return true
    }
    this.mpvPlayer.setOsdSurface(this.osdSurfaceId, this.osdSurfaceWidthPx, this.osdSurfaceHeightPx)
    this.appliedOsdSurfaceKey = bindingKey
    this.logMpv(`bind osd surface: id=${this.osdSurfaceId}, size=${this.osdSurfaceWidthPx}x${this.osdSurfaceHeightPx}`)
    return true
  }

  private bindPlayerCallbacks(): void {
    this.mpvPlayer.setListeners({
      onInitialized: () => {
        this.logMpv(`initialized with mode=${this.mpvPlayer.getCurrentModeLabel()}`)
        this.initialized = true
        this.ui.playerStatus = PlayerStatus.INITIALIZED
        this.loading = false
        this.playbackStarted = false
        this.lastPlaybackProgressAt = 0
        this.mpvPlayer.setPlaybackSpeed(this.playSpeed)
        this.loadCurrentVideo()
        this.startProgressTimer()
      },
      onEndFile: (reason: number, error: string) => {
        this.logMpv(`end file reason=${reason}, error=${error}`)
        if (reason === 0 && this.duration > 0) {
          this.stopProgressTimer()
          this.stopStartupEndFileErrorTimer()
          this.loading = false
          this.shouldResumeOnPageShow = false
          this.ui.CONTROL_PlayStatus = PlayStatus.DONE
          this.ui.playerStatus = PlayerStatus.COMPLETED
          this.playbackCompleted = true
          this.currentPosition = this.duration
          this.ui.playbackPositionMs = this.duration
          this.currentTime = this.formatTime(this.duration)
          this.progressValue = PROGRESS_MAX_VALUE
          this.cacheProgressValue = PROGRESS_MAX_VALUE
          return
        }
        if (!this.playbackStarted) {
          this.shouldResumeOnPageShow = false
          this.requestLoadingVisible(`startup-end-file-${reason}`, true)
          this.scheduleStartupEndFileError(reason, error)
          return
        }
        this.stopStartupEndFileErrorTimer()
        this.stopProgressTimer()
        this.loading = false
        this.shouldResumeOnPageShow = false
        this.ui.CONTROL_PlayStatus = PlayStatus.DONE
        this.ui.playerStatus = PlayerStatus.ERROR
        this.playbackCompleted = true
      },
      onDurationChanged: (durationMs: number) => {
        if (durationMs > 0) {
          this.duration = durationMs
          this.totalTime = this.formatTime(durationMs)
        }
      },
      onLoadingChanged: (loading: boolean) => {
        this.logMpv(`loading changed: ${loading}, started=${this.playbackStarted}`)
        if (loading) {
          this.requestLoadingVisible('on-loading-changed')
        } else {
          this.stopLoadingDelayTimer()
          this.loading = false
        }
      },
      onStartFile: () => {
        this.stopStartupEndFileErrorTimer()
        this.playbackCompleted = false
        this.playbackStarted = false
        this.lastPlaybackProgressAt = 0
        this.waitingForStartupLandscapeReady = this.shouldUseStartupLandscapeMask() &&
          !this.vm.appUIState.isFullScreen
        this.requestLoadingVisible('start-file', true)
        this.logMpv(`start file url=${this.videoUrl}`)
      },
      onFileLoaded: () => {
        this.markPlaybackStarted('file-loaded')
        this.startProgressTimer()
        this.updateVideoLayoutFromPlayer()
        this.bootstrapTracksForCurrentSource()
        this.syncNativeTracksToUi()
        if (!this.ui.playWhenReady) {
          this.pausePlayback(false)
        }
        this.logVideoDiagnostics('file-loaded')
        this.scheduleDiagnostics('delayed-file-loaded', 1500)
      },
      onTrackListChanged: () => {
        this.syncNativeTracksToUi()
        if (!this.pendingSubtitleSelection) {
          return
        }
        this.logMpv(`track-list changed, retry subtitle selection: tracks=${this.describeTracks(true)}`)
        if (this.trySelectSubtitleTrackDirect(this.pendingSubtitleSelection)) {
          this.clearPendingSubtitleSelection()
        }
      },
      onHwdecChanged: (decoder: string) => {
        this.logMpv(`hwdec changed: ${decoder}`)
      },
      onLogMessage: (level: string, prefix: string, text: string) => {
        if (level === 'error') {
          console.error(`[MPV][${prefix}] ${text.trim()}`)
          return
        }
        if (level === 'warn') {
          console.warn(`[MPV][${prefix}] ${text.trim()}`)
        }
      }
    })
  }

  private scheduleInitializePlayer(): void {
    const needsOsdSurface = this.shouldUseSurfacePlayback()
    if (this.initialized || this.initializeRequested || !this.xComponentReady ||
      (needsOsdSurface && (!this.osdXComponentReady || !this.hasOsdSurfaceSize()))) {
      return
    }
    this.initializeRequested = true
    this.loading = true
    this.stopInitializeTimer()
    // Let the loading UI paint first, then start the native create path.
    this.initializeTimer = setTimeout(() => {
      this.initializeTimer = -1
      if (!this.xComponentReady || this.initialized ||
        (this.shouldUseSurfacePlayback() && (!this.osdXComponentReady || !this.hasOsdSurfaceSize()))) {
        this.initializeRequested = false
        return
      }
      this.initializePlayer()
    }, 16)
  }

  private initializePlayer(): void {
    this.ui.playerStatus = PlayerStatus.IDLE
    this.loading = true
    this.playbackCompleted = false
    this.initializeRequested = false
    const useHardwareDecode = this.shouldUseHardwareDecode()
    const cacheSizeMb = AppPreference.getInstance().getMpvCacheSizeMb()
    const source = this.vm.mediaSourceOrNull
    const preferSurface = this.shouldUseSurfacePlayback()
    if (preferSurface && !this.applyOsdSurfaceBinding(true)) {
      this.loading = false
      this.ui.playerStatus = PlayerStatus.ERROR
      console.error('[MPV] osd surface not ready for surface mode')
      return
    }
    if (!preferSurface) {
      this.mpvPlayer.clearOsdSurface()
      this.appliedOsdSurfaceKey = ''
    }
    this.mpvPlayer.configurePlayback(useHardwareDecode, cacheSizeMb, preferSurface)
    if (this.ui.forceMpvHardwareDecode) {
      this.logMpv('auto mode selected MPV, force hardware decode on')
    }
    if (this.ui.forceMpvSoftwareDecode) {
      this.logMpv('auto mode selected MPV, force software decode on')
    }
    if (source?.isDolbyVisionStream()) {
      this.logMpv(`dolby vision detected, prefer buffer-hw for tone mapping: range=${source.selectedVideoRangeType}, dvProfile=${source.selectedVideoDvProfile ?? 'none'}`)
    }
    this.logMpv(`initialize player with ${this.mpvPlayer.getCurrentModeLabel()}, cache=${cacheSizeMb}MB`)
    if (!this.mpvPlayer.initialize()) {
      this.loading = false
      this.ui.playerStatus = PlayerStatus.ERROR
      console.error('[MPV] initialize failed')
    }
  }

  private loadCurrentVideo(): void {
    if (!this.initialized || this.videoUrl.length === 0) {
      return
    }
    this.prepareSeekPreview()
    this.sourceLoaded = true
    this.playbackCompleted = false
    this.playbackStarted = false
    this.lastPlaybackProgressAt = 0
    this.requestLoadingVisible('load-current-video', true)
    this.cacheProgressValue = 0
    this.ui.CONTROL_PlayStatus = this.ui.playWhenReady ? PlayStatus.PLAY : PlayStatus.PAUSE
    this.ui.playerStatus = this.ui.playWhenReady ? PlayerStatus.PLAYING : PlayerStatus.PAUSED
    this.startProgressTimer()
    const startPositionSeconds = this.ui.initSeek > 0 ? this.ui.initSeek / 1000 : 0
    this.logMpv(`load video with mode=${this.mpvPlayer.getCurrentModeLabel()}`)
    this.applyPlaybackHeaders()
    this.mpvPlayer.load(this.videoUrl, startPositionSeconds)
    if (this.ui.initSeek > 0) {
      this.currentPosition = this.ui.initSeek
      this.currentTime = this.formatTime(this.ui.initSeek)
      this.ui.playbackPositionMs = this.ui.initSeek
      this.ui.initSeek = 0
    }
    if (!this.ui.playWhenReady) {
      this.mpvPlayer.pause()
    }
    this.scheduleDiagnostics('after-load', 2500)
  }

  private togglePlayback(): void {
    if (!this.initialized || !this.sourceLoaded) {
      return
    }
    if (this.ui.CONTROL_PlayStatus === PlayStatus.PLAY) {
      this.pausePlayback()
    } else {
      if (this.playbackCompleted) {
        this.ui.initSeek = 0
        this.loadCurrentVideo()
        return
      }
      this.resumePlayback()
    }
  }

  private onSeek(value: number, mode: SliderChangeMode): void {
    if (!this.initialized || this.duration <= 0) {
      return
    }
    const seekMs = (value / PROGRESS_MAX_VALUE) * this.duration
    this.currentPosition = seekMs
    this.ui.playbackPositionMs = seekMs
    this.currentTime = this.formatTime(seekMs)
    this.progressValue = value
    this.seekPreviewTime = this.formatTime(seekMs)
    this.seekPreviewProgressValue = value
    if (mode === SliderChangeMode.Begin) {
      this.isSeekingWithSlider = true
      this.showSeekPreviewUi = true
      this.scheduleSeekPreviewFrame(seekMs, true)
      return
    }
    if (mode === SliderChangeMode.Moving) {
      this.showSeekPreviewUi = true
      this.scheduleSeekPreviewFrame(seekMs)
      return
    }
    this.isSeekingWithSlider = false
    this.showSeekPreviewUi = false
    this.seekTo(seekMs)
    this.resetSeekPreviewState()
  }

  private startProgressTimer(): void {
    this.stopProgressTimer()
    this.progressTimer = setInterval(() => {
      if (!this.initialized) {
        return
      }
      const duration = this.mpvPlayer.getDurationMs()
      if (duration > 0) {
        this.duration = duration
        this.totalTime = this.formatTime(duration)
      }
      const position = this.mpvPlayer.getCurrentPositionMs()
      if (position >= 0) {
        if (this.shouldIgnorePlaybackProgress(position)) {
          return
        }
        if (position > this.currentPosition || position > 0) {
          this.markPlaybackStarted('progress')
        }
        this.currentPosition = position
        this.ui.playbackPositionMs = position
        this.currentTime = this.formatTime(position)
        if (this.duration > 0) {
          this.progressValue = Math.min(PROGRESS_MAX_VALUE, (position / this.duration) * PROGRESS_MAX_VALUE)
          const cacheDuration = Math.max(0, this.mpvPlayer.getCacheDurationMs())
          const bufferedPosition = Math.min(this.duration, position + cacheDuration)
          this.cacheProgressValue = Math.min(PROGRESS_MAX_VALUE, (bufferedPosition / this.duration) * PROGRESS_MAX_VALUE)
        }
        this.vm.reportPlaybackProgress(position, this.ui.CONTROL_PlayStatus === PlayStatus.PAUSE)
      }
    }, 500)
  }

  private stopProgressTimer(): void {
    if (this.progressTimer >= 0) {
      clearInterval(this.progressTimer)
      this.progressTimer = -1
    }
  }

  private scheduleDiagnostics(reason: string, delayMs: number): void {
    this.stopDiagnosticsTimer()
    this.diagnosticsTimer = setTimeout(() => {
      this.updateVideoLayoutFromPlayer()
      this.logVideoDiagnostics(reason)
      this.diagnosticsTimer = -1
    }, delayMs)
  }

  private stopDiagnosticsTimer(): void {
    if (this.diagnosticsTimer >= 0) {
      clearTimeout(this.diagnosticsTimer)
      this.diagnosticsTimer = -1
    }
  }

  private stopLoadingDelayTimer(): void {
    if (this.loadingDelayTimer >= 0) {
      clearTimeout(this.loadingDelayTimer)
      this.loadingDelayTimer = -1
    }
  }

  private stopStartupEndFileErrorTimer(): void {
    if (this.startupEndFileErrorTimer >= 0) {
      clearTimeout(this.startupEndFileErrorTimer)
      this.startupEndFileErrorTimer = -1
    }
  }

  private scheduleStartupEndFileError(reason: number, error: string): void {
    this.stopStartupEndFileErrorTimer()
    this.startupEndFileErrorTimer = setTimeout(() => {
      this.startupEndFileErrorTimer = -1
      if (this.playbackStarted || this.playbackCompleted || !this.sourceLoaded) {
        return
      }
      this.loading = false
      this.ui.CONTROL_PlayStatus = PlayStatus.DONE
      this.ui.playerStatus = PlayerStatus.ERROR
      this.playbackCompleted = true
      this.logMpv(`confirm startup end-file error after delay: reason=${reason}, error=${error}`)
    }, STARTUP_END_FILE_ERROR_CONFIRM_MS)
  }

  private stopInitializeTimer(): void {
    if (this.initializeTimer >= 0) {
      clearTimeout(this.initializeTimer)
      this.initializeTimer = -1
    }
  }

  private stopSubtitleBootstrapTimer(): void {
    if (this.subtitleBootstrapTimer >= 0) {
      clearTimeout(this.subtitleBootstrapTimer)
      this.subtitleBootstrapTimer = -1
    }
  }

  private timeCountdown(): void {
    clearInterval(mpvUiTime)
    if (!this.lock) {
      this.showControls = true
    }
    if (this.vm.appUIState.isFullScreen) {
      this.fullShowLock = true
    }
    this.sec = 3
    mpvUiTime = setInterval(() => {
      if (this.sec <= 0) {
        clearInterval(mpvUiTime)
        if (!this.loading && !this.playbackCompleted && !this.ui.playerMenus?.isShowPlayBackInfo) {
          this.showControls = false
        }
        this.fullShowLock = false
      } else {
        this.sec--
      }
    }, 1000)
  }

  private timeLockCountdown(): void {
    clearInterval(mpvUiLockTime)
    this.fullShowLock = true
    this.sec = 3
    mpvUiLockTime = setInterval(() => {
      if (this.sec <= 0) {
        clearInterval(mpvUiLockTime)
        this.fullShowLock = false
      } else {
        this.sec--
      }
    }, 1000)
  }

  private startOrStop(): void {
    if (this.ui.CONTROL_PlayStatus == PlayStatus.PLAY) {
      this.pausePlayback()
    } else {
      this.resumePlayback()
    }
  }

  private logVideoDiagnostics(reason: string): void {
    const width = this.mpvPlayer.getVideoWidth()
    const height = this.mpvPlayer.getVideoHeight()
    const decoder = this.mpvPlayer.getHardwareDecoder()
    const mode = this.mpvPlayer.getCurrentModeLabel()
    this.logMpv(`${reason}: mode=${mode}, decoder=${decoder}, size=${width}x${height}, duration=${this.duration}, position=${this.currentPosition}`)
  }

  private logMpv(message: string): void {
    console.info(`[MPV] ${message}`)
  }

  private pausePlayback(rememberResume: boolean = true): void {
    if (!this.initialized || !this.sourceLoaded) {
      return
    }
    this.mpvPlayer.pause()
    this.ui.CONTROL_PlayStatus = PlayStatus.PAUSE
    this.ui.playerStatus = PlayerStatus.PAUSED
    this.vm.reportPlaybackProgress(this.currentPosition, true, true)
    if (rememberResume) {
      this.shouldResumeOnPageShow = true
    }
  }

  private stopScopedPlayback(): void {
    this.stopProgressTimer()
    this.stopDiagnosticsTimer()
    this.stopLoadingDelayTimer()
    this.stopStartupEndFileErrorTimer()
    this.stopSubtitleBootstrapTimer()
    this.clearPlaybackHeaders()
    this.mpvPlayer.runCommand(['stop'])
    this.sourceLoaded = false
    this.playbackCompleted = false
    this.ui.CONTROL_PlayStatus = PlayStatus.INIT
    this.ui.playerStatus = PlayerStatus.IDLE
  }

  private resumePlayback(): void {
    if (!this.initialized || !this.sourceLoaded) {
      return
    }
    this.mpvPlayer.play()
    this.ui.CONTROL_PlayStatus = PlayStatus.PLAY
    this.ui.playerStatus = PlayerStatus.PLAYING
    this.shouldResumeOnPageShow = false
    this.vm.reportPlaybackProgress(this.currentPosition, false, true)
  }

  private seekTo(positionMs: number): void {
    if (!this.initialized || this.duration <= 0) {
      return
    }
    this.mpvPlayer.seekToMs(positionMs, false)
    this.pendingSeekTargetMs = positionMs
    this.pendingSeekDeadlineMs = Date.now() + SEEK_SYNC_TIMEOUT_MS
    this.currentPosition = positionMs
    this.ui.playbackPositionMs = positionMs
    this.currentTime = this.formatTime(positionMs)
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
      if (!this.isSeekPreviewAvailable()) {
        return
      }
      const pixelMap = await this.seekPreviewHelper.fetchFrameByTime(
        this.videoUrl,
        positionMs,
        this.ui.videoHeaders
      )
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
    this.showSeekPreviewUi = false
    this.setSeekPreviewPixelMap(undefined)
  }

  private prepareSeekPreview(): void {
    if (this.videoUrl.length === 0 || !this.isSeekPreviewAvailable()) {
      return
    }
    void this.seekPreviewHelper.prepare(this.videoUrl, this.ui.videoHeaders)
  }

  private isSeekPreviewAvailable(): boolean {
    return this.vm.mediaSourceOrNull?.capabilities.framePreviewAvailable === true
  }

  @Builder
  private volumeOverlay(): void {
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
    }
      .visibility(this.showVolumeUi ? Visibility.Visible : Visibility.Hidden)
      .alignSelf(ItemAlign.Center)
  }

  @Builder
  private brightnessOverlay(): void {
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
    }
      .visibility(this.showBrightnessUi ? Visibility.Visible : Visibility.Hidden)
      .width(80)
      .alignSelf(ItemAlign.Center)
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

  private speedFromString(speed: string): number {
    const parsed = Number(speed.replace('f', ''))
    if (!Number.isFinite(parsed) || parsed <= 0) {
      return 1
    }
    return parsed
  }

  private applyPlaybackSpeed(speed: number): void {
    this.playSpeed = speed
    if (this.initialized) {
      this.mpvPlayer.setPlaybackSpeed(speed)
    }
  }

  private sendAVSessionState(status: number): void {
    let playbackState: avSession.AVPlaybackState = {
      speed: this.playSpeed,
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
      default:
        playbackState.state = avSession.PlaybackState.PLAYBACK_STATE_IDLE
        break
    }
    avSessionManager.getSession()?.setAVPlaybackState(playbackState).then(() => {
    }).catch((err: BusinessError) => {
    })
  }

  private formatTime(timeMs: number): string {
    const totalSeconds = Math.floor(timeMs / 1000)
    const hours = Math.floor(totalSeconds / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const seconds = totalSeconds % 60
    if (hours > 0) {
      return `${this.padNumber(hours)}:${this.padNumber(minutes)}:${this.padNumber(seconds)}`
    }
    return `${this.padNumber(minutes)}:${this.padNumber(seconds)}`
  }

  private padNumber(value: number): string {
    if (value < 10) {
      return `0${value}`
    }
    return `${value}`
  }

  private formatSpeedLabel(speed: number): string {
    if (Math.abs(speed - Math.floor(speed)) < 0.001) {
      return `${Math.floor(speed)}`
    }
    return speed.toFixed(2).replace(/0$/, '').replace(/\.$/, '')
  }

  private formatBitrate(bitrate: number): string {
    const BITRATE_MEGA_BIT = 1_000_000
    const BITRATE_KILO_BIT = 1_000
    let value: number
    let unit: string
    if (bitrate > BITRATE_MEGA_BIT) {
      value = bitrate / BITRATE_MEGA_BIT
      unit = ' Mbps'
    } else if (bitrate > BITRATE_KILO_BIT) {
      value = bitrate / BITRATE_KILO_BIT
      unit = ' kbps'
    } else {
      value = bitrate
      unit = ' bps'
    }
    return value.toFixed(2).replace(/\.?0+$/, '') + unit
  }

  private bootstrapTracksForCurrentSource(): void {
    const source = this.vm.mediaSourceOrNull
    if (!source) {
      this.logMpv('bootstrap tracks skipped: source missing')
      return
    }
    this.logMpv(`bootstrap tracks: audioSelected=${source.selectedAudioStream?.Index ?? 'none'}, subtitleSelected=${source.selectedSubtitleStream?.Index ?? 'none'}, subtitleCount=${source.subtitleStreams.length}`)
    this.loadExternalSubtitles(source)
    this.applyInitialTrackSelections()
  }

  private syncNativeTracksToUi(): void {
    const source = this.vm.mediaSourceOrNull
    if (!source) {
      return
    }
    const nativeAudioTracks = this.mpvPlayer.getAudioTrackList().filter((track) => track.id >= 0)
    const nativeSubtitleTracks = this.mpvPlayer.getSubtitleTrackList().filter((track) => track.id >= 0)

    if (source.audioStreams.length === 0 && nativeAudioTracks.length > 0) {
      source.audioStreams = nativeAudioTracks.map((track, index) => this.createNativeTrackStream(track, false, index))
    }
    if (source.subtitleStreams.length === 0 && nativeSubtitleTracks.length > 0) {
      source.subtitleStreams = nativeSubtitleTracks.map((track, index) => this.createNativeTrackStream(track, true, index))
    }

    this.ui.audioStreams = [...source.audioStreams]
    this.ui.subtitleStreams = [...source.subtitleStreams]

    const selectedAudioTrackId = this.mpvPlayer.getCurrentAudioTrackId()
    const selectedAudioStream = this.resolveSelectedStreamFromNativeTrack(
      source.audioStreams,
      nativeAudioTracks,
      selectedAudioTrackId,
      false
    )
    if (selectedAudioStream) {
      source.selectAudioStream(selectedAudioStream)
      this.ui.selectedAudioStream = source.selectedAudioStream ?? selectedAudioStream
    }

    const selectedSubtitleTrackId = this.mpvPlayer.getCurrentSubtitleTrackId()
    const selectedSubtitleStream = this.resolveSelectedStreamFromNativeTrack(
      source.subtitleStreams,
      nativeSubtitleTracks,
      selectedSubtitleTrackId,
      true
    )
    if (selectedSubtitleStream) {
      source.selectSubtitleStream(selectedSubtitleStream)
      this.ui.selectedSubtitleStream = source.selectedSubtitleStream ?? selectedSubtitleStream
    }

    this.logMpv(
      `sync native tracks: audio=${source.audioStreams.length}, subtitle=${source.subtitleStreams.length}, ` +
      `selectedAudio=${this.ui.selectedAudioStream?.Index ?? 'none'}, selectedSubtitle=${this.ui.selectedSubtitleStream?.Index ?? 'none'}`
    )
  }

  private createNativeTrackStream(track: MpvTrackInfo, isSubtitle: boolean,
    index: number): MediaPlaybackStream {
    return new NativeMediaPlaybackStream(track, isSubtitle, index)
  }

  private resolveSelectedStreamFromNativeTrack(
    streams: MediaPlaybackStream[],
    nativeTracks: MpvTrackInfo[],
    selectedTrackId: number,
    isSubtitle: boolean
  ): MediaPlaybackStream | undefined {
    if (streams.length === 0 || nativeTracks.length === 0) {
      return undefined
    }
    const nativeTrack = nativeTracks.find((track) => track.id === selectedTrackId) ??
      nativeTracks.find((track) => track.selected === true)
    if (!nativeTrack) {
      return undefined
    }
    return streams.find((stream) => stream.Index === nativeTrack.id) ??
      streams.find((stream) => this.findTrackIdByStream(stream, isSubtitle) === nativeTrack.id)
  }

  private loadExternalSubtitles(source: MediaPlaybackSource | undefined): void {
    if (!source) {
      return
    }
    source.externalSubtitleStreams.forEach((stream) => {
      this.ensureExternalSubtitleLoaded(stream)
    })
  }

  private ensureExternalSubtitleLoaded(stream: MediaPlaybackExternalSubtitle): void {
    if (this.loadedExternalSubtitleUrls.includes(stream.deliveryUrl)) {
      return
    }
    this.loadedExternalSubtitleUrls.push(stream.deliveryUrl)
    this.applyPlaybackHeaders(stream.headers)
    this.mpvPlayer.runCommand([
      'sub-add',
      stream.deliveryUrl,
      'auto',
      stream.displayTitle.length > 0 ? stream.displayTitle : `external-${stream.index}`,
      stream.language
    ])
    this.logMpv(`queued external subtitle: ${stream.displayTitle || stream.language || `external-${stream.index}`}`)
  }

  private applyPlaybackHeaders(extraHeaders: MediaPlaybackRequestHeader[] = []): void {
    const values = new Map<string, string>()
    this.appendPlaybackHeaders(values, this.ui.videoHeaders)
    const source = this.vm.mediaSourceOrNull
    if (source) {
      for (const subtitle of source.externalSubtitleStreams) {
        this.appendPlaybackHeaders(values, subtitle.headers)
      }
    }
    this.appendPlaybackHeaders(values, extraHeaders)
    const fields: string[] = []
    values.forEach((value: string, name: string): void => {
      fields.push(`${name}: ${value}`)
    })
    this.mpvPlayer.runCommand(['set', 'http-header-fields', fields.join(',')])
  }

  private appendPlaybackHeaders(values: Map<string, string>, headers: MediaPlaybackRequestHeader[]): void {
    for (const header of headers) {
      const name = header.name.trim()
      if (name.length === 0 || header.value.length === 0) {
        continue
      }
      values.set(name, header.value)
    }
  }

  private clearPlaybackHeaders(): void {
    this.mpvPlayer.runCommand(['set', 'http-header-fields', ''])
  }

  private applyInitialTrackSelections(): void {
    const source = this.vm.mediaSourceOrNull
    if (!source) {
      this.logMpv('apply initial tracks skipped: source missing')
      return
    }
    this.logMpv(`apply initial tracks start: audio=${source.selectedAudioStream?.Index ?? 'none'}, subtitle=${source.selectedSubtitleStream?.Index ?? 'none'}, subtitleCount=${source.subtitleStreams.length}`)
    if (source.selectedAudioStream) {
      this.selectAudioTrack(source.selectedAudioStream, false)
    }
    try {
      const initialSubtitleStream = this.resolveInitialSubtitleStream(source)
      if (initialSubtitleStream) {
        const allowFallback = this.requiresSubtitleRestart(initialSubtitleStream) &&
          !this.hasSubtitleSelectionInCurrentUrl(initialSubtitleStream)
        this.logMpv(`initial subtitle candidate: ${initialSubtitleStream.DisplayTitle ?? ''} (#${initialSubtitleStream.Index ?? -1})`)
        this.selectSubtitleTrack(initialSubtitleStream, allowFallback)
      } else {
        this.logMpv(`no initial subtitle candidate, streams=${source.subtitleStreams.map((stream) =>
          `${stream.Index ?? -1}:${stream.DisplayTitle ?? ''}:default=${stream.IsDefault === true}:forced=${stream.IsForced === true}`
        ).join(' | ')}`)
      }
    } catch (error) {
      console.error(`[MPV] apply initial subtitle selection failed: ${JSON.stringify(error)}`)
    }
  }

  private resolveInitialSubtitleStream(source: MediaPlaybackSource): MediaPlaybackStream | undefined {
    if (source.selectedSubtitleStream) {
      return source.selectedSubtitleStream
    }
    const streams = source.subtitleStreams
    if (streams.length === 0) {
      return undefined
    }
    return streams.find((stream) => stream.IsDefault === true)
      ?? streams.find((stream) => this.normalizeTrackText(stream.DisplayTitle).includes('default') ||
        this.normalizeTrackText(stream.DisplayTitle).includes('默认'))
      ?? streams.find((stream) => stream.IsForced === true)
      ?? streams.find((stream) => this.normalizeTrackText(stream.DisplayTitle).includes('forced') ||
        this.normalizeTrackText(stream.DisplayTitle).includes('强制'))
      ?? streams[0]
  }

  private normalizeTrackText(value?: string | null): string {
    if (!value) {
      return ''
    }
    return value.trim().toLowerCase()
  }

  private isTrackTextMatch(trackValue?: string | null, expectedValue?: string | null): boolean {
    const normalizedTrackValue = this.normalizeTrackText(trackValue)
    const normalizedExpectedValue = this.normalizeTrackText(expectedValue)
    if (normalizedTrackValue.length === 0 || normalizedExpectedValue.length === 0) {
      return false
    }
    return normalizedTrackValue === normalizedExpectedValue ||
      normalizedTrackValue.includes(normalizedExpectedValue) ||
      normalizedExpectedValue.includes(normalizedTrackValue)
  }

  private findExternalSubtitleSource(stream: MediaPlaybackStream): MediaPlaybackExternalSubtitle | undefined {
    const source = this.vm.mediaSourceOrNull
    if (!source || stream.Index === undefined || stream.Index === null) {
      return undefined
    }
    return source.externalSubtitleStreams.find((item) => item.index === stream.Index)
  }

  private hasSubtitleSelectionInCurrentUrl(stream: MediaPlaybackStream): boolean {
    if (stream.Index === undefined || stream.Index === null) {
      return false
    }
    const subtitleIndex = `${stream.Index}`
    return this.videoUrl.includes(`SubtitleStreamIndex=${subtitleIndex}`) ||
      this.videoUrl.includes(`subtitleStreamIndex=${subtitleIndex}`)
  }

  private requiresSubtitleRestart(stream: MediaPlaybackStream): boolean {
    const source = this.vm.mediaSourceOrNull
    if (!source) {
      return false
    }
    return source.usesServerTranscoding() ||
      stream.DeliveryMethod == MediaPlaybackSubtitleDeliveryMethod.Encode
  }

  private describeTracks(isSubtitle: boolean): string {
    const nativeTracks = isSubtitle ? this.mpvPlayer.getSubtitleTrackList() : this.mpvPlayer.getAudioTrackList()
    if (nativeTracks.length === 0) {
      return '[]'
    }
    return `[${nativeTracks.map((track) =>
      `id=${track.id},title=${track.title ?? ''},lang=${track.lang ?? ''},codec=${track.codec ?? ''},external=${track.external === true}`
    ).join(' | ')}]`
  }

  private findTrackIdByStream(stream: MediaPlaybackStream, isSubtitle: boolean): number {
    const nativeTracks = isSubtitle ? this.mpvPlayer.getSubtitleTrackList() : this.mpvPlayer.getAudioTrackList()
    if (nativeTracks.length === 0) {
      return -1
    }

    const expectedExternal = stream.IsExternal === true
    const displayTitle = this.normalizeTrackText(stream.DisplayTitle)
    const language = this.normalizeTrackText(stream.Language)
    const codec = this.normalizeTrackText(stream.Codec)

    let filteredTracks = nativeTracks.filter((track) => (track.external === true) === expectedExternal)
    if (filteredTracks.length === 0) {
      filteredTracks = nativeTracks
    }

    let matchedTrack = filteredTracks.find((track) => {
      if (isSubtitle) {
        return this.isTrackTextMatch(track.title, displayTitle) &&
          this.isTrackTextMatch(track.lang, language)
      }
      return this.isTrackTextMatch(track.title, displayTitle) &&
        this.isTrackTextMatch(track.lang, language) &&
        this.isTrackTextMatch(track.codec, codec)
    })
    if (matchedTrack) {
      return matchedTrack.id
    }

    matchedTrack = filteredTracks.find((track) => {
      if (isSubtitle) {
        return this.isTrackTextMatch(track.lang, language)
      }
      return this.isTrackTextMatch(track.lang, language) &&
        this.isTrackTextMatch(track.codec, codec)
    })
    if (matchedTrack) {
      return matchedTrack.id
    }

    matchedTrack = filteredTracks.find((track) => {
      return this.isTrackTextMatch(track.title, displayTitle)
    })
    if (matchedTrack) {
      return matchedTrack.id
    }

    matchedTrack = filteredTracks.find((track) => {
      return this.isTrackTextMatch(track.lang, language)
    })
    if (matchedTrack) {
      return matchedTrack.id
    }

    const source = this.vm.mediaSourceOrNull
    const orderedStreams = isSubtitle ? source?.subtitleStreams : source?.audioStreams
    if (orderedStreams) {
      let embeddedIndex = orderedStreams
        .filter((item: MediaPlaybackStream) => (item.IsExternal === true) === expectedExternal)
        .findIndex((item: MediaPlaybackStream) => item === stream)
      if (embeddedIndex >= 0 && embeddedIndex < filteredTracks.length) {
        return filteredTracks[embeddedIndex].id
      }
    }

    if (filteredTracks.length === 1) {
      return filteredTracks[0].id
    }

    return -1
  }

  private selectAudioTrack(stream: MediaPlaybackStream, allowFallback: boolean = true): boolean {
    const source = this.vm.mediaSourceOrNull
    if (!source || source.usesServerTranscoding() || stream.IsExternal === true) {
      if (allowFallback) {
        this.onAudioTrackSelected(stream)
      }
      return false
    }
    const trackId = this.findTrackIdByStream(stream, false)
    if (trackId < 0) {
      this.logMpv(`audio track not mapped, fallback to restart: ${stream.DisplayTitle ?? ''}, tracks=${this.describeTracks(false)}`)
      if (allowFallback) {
        this.onAudioTrackSelected(stream)
      }
      return false
    }
    if (!source.selectAudioStream(stream)) {
      if (allowFallback) {
        this.onAudioTrackSelected(stream)
      }
      return false
    }
    this.ui.selectedAudioStream = stream
    this.mpvPlayer.selectAudioTrack(trackId)
    this.logMpv(`selected audio track directly: ${stream.DisplayTitle ?? ''} -> ${trackId}`)
    return true
  }

  private schedulePendingSubtitleSelectionRetry(attempt: number = 0): void {
    if (!this.pendingSubtitleSelection) {
      return
    }
    if (attempt >= SUBTITLE_SELECTION_RETRY_DELAYS_MS.length) {
      if (this.pendingSubtitleSelectionAllowFallback) {
        const pendingStream = this.pendingSubtitleSelection
        this.clearPendingSubtitleSelection()
        if (pendingStream) {
          this.logMpv(`subtitle track still not mapped after retries, fallback to restart: ${pendingStream.DisplayTitle ?? ''}`)
          this.onSubtitleSelected(pendingStream)
        }
        return
      }
      if (!this.pendingSubtitleSelectionWaitingLogged) {
        this.pendingSubtitleSelectionWaitingLogged = true
        this.logMpv(`subtitle track not ready yet, keep waiting: ${this.pendingSubtitleSelection.DisplayTitle ?? ''}`)
      }
      this.stopSubtitleBootstrapTimer()
      this.subtitleBootstrapTimer = setTimeout(() => {
        this.subtitleBootstrapTimer = -1
        const pendingStream = this.pendingSubtitleSelection
        if (!pendingStream) {
          return
        }
        if (this.trySelectSubtitleTrackDirect(pendingStream)) {
          this.clearPendingSubtitleSelection(false)
          return
        }
        this.schedulePendingSubtitleSelectionRetry(attempt)
      }, SUBTITLE_SELECTION_BACKGROUND_RETRY_MS)
      return
    }
    this.stopSubtitleBootstrapTimer()
    const delayMs = SUBTITLE_SELECTION_RETRY_DELAYS_MS[attempt]
    this.subtitleBootstrapTimer = setTimeout(() => {
      this.subtitleBootstrapTimer = -1
      const pendingStream = this.pendingSubtitleSelection
      if (!pendingStream) {
        return
      }
      if (this.trySelectSubtitleTrackDirect(pendingStream)) {
        this.clearPendingSubtitleSelection(false)
        return
      }
      this.schedulePendingSubtitleSelectionRetry(attempt + 1)
    }, delayMs)
  }

  private clearPendingSubtitleSelection(stopTimer: boolean = true): void {
    this.pendingSubtitleSelection = undefined
    this.pendingSubtitleSelectionAllowFallback = false
    this.pendingSubtitleSelectionWaitingLogged = false
    if (stopTimer) {
      this.stopSubtitleBootstrapTimer()
    }
  }

  private queuePendingSubtitleSelection(stream: MediaPlaybackStream, allowFallback: boolean): boolean {
    if (stream.IsExternal === true) {
      const externalStream = this.findExternalSubtitleSource(stream)
      if (!externalStream) {
        return false
      }
      this.ensureExternalSubtitleLoaded(externalStream)
    }
    this.pendingSubtitleSelection = stream
    this.pendingSubtitleSelectionAllowFallback = allowFallback
    this.pendingSubtitleSelectionWaitingLogged = false
    this.logMpv(`subtitle track pending mount: ${stream.DisplayTitle ?? ''}, tracks=${this.describeTracks(true)}`)
    this.schedulePendingSubtitleSelectionRetry(0)
    return true
  }

  private trySelectSubtitleTrackDirect(stream: MediaPlaybackStream): boolean {
    const source = this.vm.mediaSourceOrNull
    if (!source || this.requiresSubtitleRestart(stream)) {
      return false
    }
    const trackId = this.findTrackIdByStream(stream, true)
    if (trackId < 0) {
      return false
    }
    if (!source.selectSubtitleStream(stream)) {
      return false
    }
    this.ui.selectedSubtitleStream = stream
    this.ui.currentSubtitle = ''
    this.ui.subtitles = []
    this.mpvPlayer.selectSubtitleTrack(trackId)
    this.logMpv(`selected subtitle track directly: ${stream.DisplayTitle ?? ''} -> ${trackId}`)
    return true
  }

  private selectSubtitleTrack(stream: MediaPlaybackStream, allowFallback: boolean = true): boolean {
    if (this.requiresSubtitleRestart(stream)) {
      if (this.hasSubtitleSelectionInCurrentUrl(stream)) {
        this.logMpv(`subtitle already active in transcode url: ${stream.DisplayTitle ?? ''}`)
        return true
      }
      this.logMpv(`subtitle requires restart: ${stream.DisplayTitle ?? ''}`)
      if (allowFallback) {
        this.onSubtitleSelected(stream)
      }
      return false
    }
    if (this.trySelectSubtitleTrackDirect(stream)) {
      this.clearPendingSubtitleSelection()
      return true
    }
    if (this.queuePendingSubtitleSelection(stream, allowFallback)) {
      return false
    }
    this.logMpv(`subtitle track not mapped, fallback to restart: ${stream.DisplayTitle ?? ''}, tracks=${this.describeTracks(true)}`)
    if (allowFallback) {
      this.onSubtitleSelected(stream)
    }
    return false
  }

  @Builder
  private dialogEpisode(_this: MPVPlayerView): void {
    Column({ space: 10 }) {
      List({ space: 8, initialIndex: _this.ui.playList.findIndex((item: QueueItem) => item.id == _this.ui.currentPlayId) }) {
        ForEach(_this.ui.playList, (item: QueueItem, index: number) => {
          ListItem() {
            Row({ space: 12 }) {
              ImageKnifeComponent({
                imageKnifeOption: queueImageOption(item)
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
            .borderRadius(12)
            .onClick(() => {
              _this.onEpisodeChange(index)
              SmartDialog.dismiss()
            })
          }
        })
      }
      .width('100%')
      .height('100%')
    }
    .height('100%')
    .padding(20)
    .backgroundColor('rgba(0,0,0,0.8)')
    .width(340)
  }

  @Builder
  private dialogSpeed(_this: MPVPlayerView): void {
    Column({ space: 10 }) {
      Stack() {
        Text('3.0X')
          .fontColor(_this.playSpeed === 3 ? $r('app.color.color_main') : Color.White)
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
      }
      .menuItemStyle()
      .onClick(() => {
        _this.applyPlaybackSpeed(3)
        SmartDialog.dismiss()
      })

      Stack() {
        Text('2.0X')
          .fontColor(_this.playSpeed === 2 ? $r('app.color.color_main') : Color.White)
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
      }
      .menuItemStyle()
      .onClick(() => {
        _this.applyPlaybackSpeed(2)
        SmartDialog.dismiss()
      })

      Stack() {
        Text('1.5X')
          .fontColor(_this.playSpeed === 1.5 ? $r('app.color.color_main') : Color.White)
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
      }
      .menuItemStyle()
      .onClick(() => {
        _this.applyPlaybackSpeed(1.5)
        SmartDialog.dismiss()
      })

      Stack() {
        Text('1.25X')
          .fontColor(_this.playSpeed === 1.25 ? $r('app.color.color_main') : Color.White)
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
      }
      .menuItemStyle()
      .onClick(() => {
        _this.applyPlaybackSpeed(1.25)
        SmartDialog.dismiss()
      })

      Stack() {
        Text('1.0X')
          .fontColor(_this.playSpeed === 1 ? $r('app.color.color_main') : Color.White)
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
      }
      .menuItemStyle()
      .onClick(() => {
        _this.applyPlaybackSpeed(1)
        SmartDialog.dismiss()
      })

      Stack() {
        Text('0.75X')
          .fontColor(_this.playSpeed === 0.75 ? $r('app.color.color_main') : Color.White)
          .fontSize(16)
          .fontWeight(FontWeight.Bold)
      }
      .menuItemStyle()
      .onClick(() => {
        _this.applyPlaybackSpeed(0.75)
        SmartDialog.dismiss()
      })
    }
    .height('100%')
    .padding(10)
    .backgroundColor('rgba(0,0,0,0.8)')
    .width(200)
  }

  @Builder
  private nativeAudioMenu(): void {
    Menu() {
      ForEach(this.ui.audioStreams, (item: MediaPlaybackStream) => {
        MenuItem({
          content: item.DisplayTitle
        })
          .selectIcon(true)
          .selected(this.ui.selectedAudioStream == item)
          .onClick(() => {
            this.selectAudioTrack(item)
          })
      })
    }
    .backgroundBlurStyle(BlurStyle.NONE)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.InteractiveFloating, true,
      this.vm.appUIState.systemMaterialAvailable))
  }

  @Builder
  private legacyAudioMenu(): void {
    Menu() {
      ForEach(this.ui.audioStreams, (item: MediaPlaybackStream) => {
        MenuItem({
          content: item.DisplayTitle
        })
          .selectIcon(true)
          .selected(this.ui.selectedAudioStream == item)
          .onClick(() => {
            this.selectAudioTrack(item)
          })
      })
    }
    .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
  }

  @Builder
  private audioMenu(): void {
    if (this.nativeTopNavigationEnabled) {
      this.nativeAudioMenu()
    } else {
      this.legacyAudioMenu()
    }
  }

  @Builder
  private nativeSubtitleMenu(): void {
    Menu() {
      ForEach(this.ui.subtitleStreams, (item: MediaPlaybackStream) => {
        MenuItem({
          content: item.DisplayTitle
        })
          .selectIcon(true)
          .selected(this.ui.selectedSubtitleStream == item)
          .onClick(() => {
            this.selectSubtitleTrack(item)
          })
      })
    }
    .backgroundBlurStyle(BlurStyle.NONE)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.InteractiveFloating, true,
      this.vm.appUIState.systemMaterialAvailable))
  }

  @Builder
  private legacySubtitleMenu(): void {
    Menu() {
      ForEach(this.ui.subtitleStreams, (item: MediaPlaybackStream) => {
        MenuItem({
          content: item.DisplayTitle
        })
          .selectIcon(true)
          .selected(this.ui.selectedSubtitleStream == item)
          .onClick(() => {
            this.selectSubtitleTrack(item)
          })
      })
    }
    .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
  }

  @Builder
  private subtitleMenu(): void {
    if (this.nativeTopNavigationEnabled) {
      this.nativeSubtitleMenu()
    } else {
      this.legacySubtitleMenu()
    }
  }

  @Builder
  private nativeQualityMenu(): void {
    Menu() {
      ForEach(this.ui.qualityOptions, (option: QualityOption) => {
        MenuItem({
          content: `${option.maxHeight}p - ${this.formatBitrate(option.bitrate)}`
        })
          .selectIcon(true)
          .selected(this.ui.selectedBitrate == option.bitrate)
          .onClick(() => {
            this.onQualitySelected(option)
          })
      })
    }
    .backgroundBlurStyle(BlurStyle.NONE)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.InteractiveFloating, true,
      this.vm.appUIState.systemMaterialAvailable))
  }

  @Builder
  private legacyQualityMenu(): void {
    Menu() {
      ForEach(this.ui.qualityOptions, (option: QualityOption) => {
        MenuItem({
          content: `${option.maxHeight}p - ${this.formatBitrate(option.bitrate)}`
        })
          .selectIcon(true)
          .selected(this.ui.selectedBitrate == option.bitrate)
          .onClick(() => {
            this.onQualitySelected(option)
          })
      })
    }
    .backgroundBlurStyle(BlurStyle.COMPONENT_ULTRA_THICK)
  }

  @Builder
  private qualityMenu(): void {
    if (this.nativeTopNavigationEnabled) {
      this.nativeQualityMenu()
    } else {
      this.legacyQualityMenu()
    }
  }

  @Builder
  private reportActionButton(): void {
    Text(this.getReportButtonText())
      .fontColor(this.ui.isContentReported ? 'rgba(255,255,255,0.72)' : Color.White)
      .fontSize(12)
      .fontWeight(FontWeight.Medium)
      .padding({ left: 10, right: 10, top: 6, bottom: 6 })
      .backgroundColor(this.ui.isContentReported ? 'rgba(255,255,255,0.08)' : 'rgba(255,255,255,0.14)')
      .borderRadius(999)
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
  }

  @Builder
  private castActionButton(): void {
    AVCastPicker({
      normalColor: Color.White,
    })
      .width(24)
      .height(24)
      .visibility(this.ui.isCastPlaying ? Visibility.None : Visibility.Visible)
  }

  @Builder
  private topControlContent(): void {
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
      .maxLines(1)
      .textOverflow({ overflow: TextOverflow.MARQUEE })
      .marqueeOptions({ start: true })
    this.reportActionButton()
    if (!this.nativeTopNavigationEnabled) {
      Text(this.getEngineBadgeLabel())
        .fontColor(Color.White)
        .fontSize(10)
        .fontWeight(FontWeight.Medium)
        .padding({ left: 8, right: 8, top: 4, bottom: 4 })
        .backgroundColor('rgba(255,255,255,0.18)')
        .borderRadius(999)
        .margin({ right: 12 })
    }
    this.castActionButton()
  }

  @Builder
  private nativeFullscreenTopControl(): void {
    Row() {
      this.topControlContent()
    }
    .height(52)
    .width('100%')
    .justifyContent(FlexAlign.Start)
    .padding({ left: 12, right: 12 })
    .margin({
      top: 12,
      left: this.vm.appUIState.safeTop + 12,
      right: this.vm.appUIState.safeTop + 12
    })
    .borderRadius(26)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.InteractiveFloating, true,
      this.vm.appUIState.systemMaterialAvailable))
    .visibility(OneMoreUtil.isPc() ? Visibility.None : Visibility.Visible)
  }

  @Builder
  private legacyTopControl(): void {
    Row() {
      this.topControlContent()
    }
    .height(this.vm.appUIState.isFullScreen ? 76 : 56 + this.vm.appUIState.safeTop)
    .width('100%')
    .justifyContent(FlexAlign.Start)
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

  @Builder
  private topControlView(): void {
    if (this.nativeTopNavigationEnabled && this.vm.appUIState.isFullScreen) {
      this.nativeFullscreenTopControl()
    } else {
      this.legacyTopControl()
    }
  }

  @Builder
  private nativePortraitTopActions(): void {
    Row({ space: 8 }) {
      Text(this.ui.title)
        .fontColor(Color.White)
        .fontSize(16)
        .fontWeight(FontWeight.Medium)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.MARQUEE })
        .marqueeOptions({ start: true })
        .layoutWeight(1)
        .hitTestBehavior(HitTestMode.None)
      Row({ space: 4 }) {
        this.reportActionButton()
        this.castActionButton()
      }
      .height(44)
      .width(128)
      .borderRadius(22)
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.InteractiveFloating, true,
        this.vm.appUIState.systemMaterialAvailable))
      .hitTestBehavior(HitTestMode.BLOCK_HIERARCHY)
    }
    .width('calc(100% - 88vp)')
    .height(56)
    .margin({
      top: this.vm.appUIState.safeTop,
      left: 72,
      right: 16
    })
    .alignItems(VerticalAlign.Center)
    .align(Alignment.Top)
    .zIndex(2)
    .hitTestBehavior(HitTestMode.Transparent)
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
    if (this.ui.effectivePlayerEngine === PlayerEngineType.AV) {
      return 'AV'
    }
    if (this.ui.effectivePlayerEngine === PlayerEngineType.MPV) {
      const useHardwareDecode = this.ui.forceMpvSoftwareDecode
        ? false
        : (this.ui.forceMpvHardwareDecode || AppPreference.getInstance().getMpvUseHardwareDecode())
      return useHardwareDecode ? 'MPV 硬解' : 'MPV 软解'
    }
    return '自动'
  }

  @Builder
  private centerOverlay(): void {
    if (this.loading) {
      Image($r('app.media.icon_load'))
        .objectFit(ImageFit.Auto)
        .width('120px')
        .height('120px')
    } else if (this.playbackCompleted) {
      Image($r('app.media.icon_replay'))
        .objectFit(ImageFit.Auto)
        .width('120px')
        .height('120px')
        .onClick(() => {
          this.togglePlayback()
        })
    }
  }

  private onSurfaceTap(): void {
    if (this.lock) {
      this.timeLockCountdown()
      return
    }
    if (this.loading || this.shouldShowStartupLandscapeLoading()) {
      return
    }
    if (OneMoreUtil.isPc()) {
      this.startOrStop()
      return
    }
    if (this.showControls) {
      clearInterval(mpvUiTime)
      this.showControls = false
      this.fullShowLock = false
    } else {
      this.timeCountdown()
    }
  }

  private onSingleTap(): void {
    if (this.lock) {
      this.timeLockCountdown()
      return
    }
    if (this.loading || this.shouldShowStartupLandscapeLoading()) {
      return
    }
    if (OneMoreUtil.isPc()) {
      this.startOrStop()
      return
    }
    if (this.showControls) {
      this.showControls = false
      this.fullShowLock = false
    } else {
      this.timeCountdown()
    }
  }

  private onDoubleTap(): void {
    if (OneMoreUtil.isPc()) {
      return
    }
    if (this.lock || this.loading || this.shouldShowStartupLandscapeLoading() || !this.initialized || !this.sourceLoaded) {
      return
    }
    this.togglePlayback()
  }

  private onOsdSurfaceTap(): void {
    if (this.lock) {
      this.timeLockCountdown()
      return
    }
    if (this.loading || this.shouldShowStartupLandscapeLoading() || OneMoreUtil.isPc()) {
      return
    }
    if (!this.showControls) {
      this.timeCountdown()
    }
  }

  @Builder
  private bottomControlRows(): void {
      Flex({ direction: FlexDirection.Row, alignItems: ItemAlign.Center, justifyContent: FlexAlign.Start }) {
        Text(this.currentTime)
          .fontColor(Color.White)
          .fontSize(13)
          .fontWeight(FontWeight.Bold)
          .margin({ left: 16, right: 8 })
        Stack({ alignContent: Alignment.Center }) {
          Progress({
            value: PROGRESS_MAX_VALUE,
            total: PROGRESS_MAX_VALUE,
            type: ProgressType.Linear
          })
            .height(MPV_PROGRESS_TRACK_HEIGHT)
            .width('100%')
            .padding({ left: MPV_PROGRESS_VISUAL_TRACK_LEFT_INSET, right: MPV_PROGRESS_VISUAL_TRACK_RIGHT_INSET })
            .color(MPV_PROGRESS_BASE_COLOR)

          Progress({
            value: this.cacheProgressValue,
            total: PROGRESS_MAX_VALUE,
            type: ProgressType.Linear
          })
            .height(MPV_PROGRESS_TRACK_HEIGHT)
            .width('100%')
            .padding({ left: MPV_PROGRESS_VISUAL_TRACK_LEFT_INSET, right: MPV_PROGRESS_VISUAL_TRACK_RIGHT_INSET })
            .color(MPV_PROGRESS_CACHE_COLOR)

          Slider({
            value: this.progressValue,
            min: 0,
            max: PROGRESS_MAX_VALUE,
            step: 1,
            style: SliderStyle.OutSet
          })
            .width('100%')
            .height(24)
            .layoutWeight(1)
            .blockColor(Color.White)
            .blockSize({ width: 20, height: 20 })
            .trackColor(MPV_PROGRESS_SLIDER_TRACK_COLOR)
            .selectedColor($r('app.color.color_main'))
            .trackThickness(MPV_PROGRESS_TRACK_HEIGHT)
            .trackBorderRadius(MPV_PROGRESS_TRACK_HEIGHT / 2)
            .selectedBorderRadius(MPV_PROGRESS_TRACK_HEIGHT / 2)
            .showTips(true)
            .onChange((value: number, mode: SliderChangeMode) => {
              this.onSeek(value, mode)
            })
        }
          .layoutWeight(1)
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
      }
      .width('100%')

      Row() {
        Image(this.ui.CONTROL_PlayStatus === PlayStatus.PLAY ? $r('app.media.ic_pause') : $r('app.media.ic_play'))
          .width(44)
          .height(44)
          .padding(12)
          .onClick(() => {
            this.togglePlayback()
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
          Image($r('app.media.ic_audio'))
            .width(44)
            .height(44)
            .padding(12)
        }
        .backgroundColor(Color.Transparent)
        .visibility(this.ui.audioStreams.length > 0 ? Visibility.Visible : Visibility.None)
        .bindMenu(this.audioMenu())

        Button() {
          Image($r('app.media.ic_subtitle'))
            .width(44)
            .height(44)
            .padding(12)
        }
        .backgroundColor(Color.Transparent)
        .visibility(this.ui.subtitleStreams.length > 0 ? Visibility.Visible : Visibility.None)
        .bindMenu(this.subtitleMenu())

        Button() {
          Image($r('app.media.ic_info'))
            .width(44)
            .height(44)
            .padding(10)
        }
        .backgroundColor(Color.Transparent)
        .visibility(this.ui.playerMenus ? Visibility.Visible : Visibility.None)
        .onClick(() => {
          if (this.ui.playerMenus) {
            this.ui.playerMenus.isShowPlayBackInfo = true
            this.showControls = true
            clearInterval(mpvUiTime)
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
        Blank()
          .layoutWeight(1)

        if (this.ui.mediaType == MediaPlaybackItemKind.Episode) {
          Text('选集')
            .fontColor(Color.White)
            .fontSize(16)
            .height('100%')
            .padding({ left: 16, right: 16 })
            .onClick(() => {
              SmartDialog.show({
                builder: this.dialogEpisode,
                builderArgs: this,
                alignment: Alignment.End
              })
            })
        }

        if (this.ui.qualityOptions.length > 0) {
          Button() {
            Text('质量')
              .fontColor(Color.White)
              .fontSize(16)
              .height('100%')
              .padding({ left: 16, right: 16 })
          }
          .backgroundColor(Color.Transparent)
          .bindMenu(this.qualityMenu())
        }

        Text('倍速')
          .fontColor(Color.White)
          .fontSize(16)
          .height('100%')
          .padding({ left: 16, right: 16 })
          .onClick(() => {
            SmartDialog.show({
              builder: this.dialogSpeed,
              builderArgs: this,
              alignment: Alignment.End
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
      }
      .height(44)
      .padding({ left: 8, right: 8 })
      .visibility(
        this.vm.appUIState.isFullScreen || (OneMoreUtil.isBigScreen() && !OneMoreUtil.isPc()) ?
          Visibility.Visible : Visibility.None
      )
  }

  @Builder
  private nativeBottomControls(): void {
    Column() {
      this.bottomControlRows()
    }
    .width('100%')
    .padding({
      top: 8,
      bottom: 8,
      left: 8,
      right: 8
    })
    .margin({
      bottom: 12,
      left: this.vm.appUIState.safeTop + 12,
      right: this.vm.appUIState.safeTop + 12
    })
    .borderRadius(24)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.InteractiveFloating, true,
      this.vm.appUIState.systemMaterialAvailable))
  }

  @Builder
  private legacyBottomControls(): void {
    Column() {
      this.bottomControlRows()
    }
    .width('100%')
    .padding({
      bottom: this.showControls && !this.vm.appUIState.isFullScreen ? this.vm.appUIState.safeBottom : 10,
      left: this.vm.appUIState.isFullScreen ? this.vm.appUIState.safeTop : 0,
      right: this.vm.appUIState.isFullScreen ? this.vm.appUIState.safeTop : 0,
    })
  }

  @Builder
  private bottomControls(): void {
    if (this.nativeTopNavigationEnabled && this.vm.appUIState.isFullScreen) {
      this.nativeBottomControls()
    } else {
      this.legacyBottomControls()
    }
  }

  @Builder
  private seekPreviewOverlay(): void {
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
        Text(this.seekPreviewTime)
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

        Text(`${Math.round(this.seekPreviewProgressValue)}%`)
          .fontColor('#8E9AA8')
          .fontSize(11)
      }
        .width('100%')
    }
      .padding({ top: 14, bottom: 14, left: 16, right: 16 })
      .width(220)
      .backgroundColor('#CC11161D')
      .borderRadius(20)
      .border({ width: 1, color: 'rgba(255,255,255,0.10)' })
      .alignItems(HorizontalAlign.Center)
      .visibility(this.showSeekPreviewUi ? Visibility.Visible : Visibility.Hidden)
  }

  build() {
    Stack({ alignContent: Alignment.Center }) {
      Column() {
        Stack() {
          XComponent({
            id: MPV_XCOMPONENT_ID,
            type: XComponentType.SURFACE,
            libraryname: 'mpvnative',
            controller: this.xComponentController
          })
            .onLoad(() => {
              this.xComponentReady = true
              this.scheduleInitializePlayer()
          })
            .width('100%')
            .height('100%')
            .id(MPV_XCOMPONENT_ID)
            .backgroundColor(Color.Black)
            .onClick(() => {
              this.onSurfaceTap()
            })

          if (this.shouldUseSurfacePlayback()) {
            XComponent({
              id: MPV_OSD_XCOMPONENT_ID,
              type: XComponentType.SURFACE,
              controller: this.osdXComponentController
            })
              .onLoad(() => {
                this.osdSurfaceId = this.osdXComponentController.getXComponentSurfaceId()
                this.osdXComponentReady = this.osdSurfaceId.length > 0 && this.osdSurfaceId !== '0'
                this.appliedOsdSurfaceKey = ''
                if (!this.osdXComponentReady) {
                  console.error('[MPV] osd xcomponent surface id unavailable')
                  return
                }
                this.applyOsdSurfaceBinding(true)
                this.scheduleInitializePlayer()
              })
              .onDestroy(() => {
                this.osdXComponentReady = false
                this.osdSurfaceId = '0'
                this.appliedOsdSurfaceKey = ''
                this.mpvPlayer.clearOsdSurface()
              })
              .width('100%')
              .height('100%')
              .id(MPV_OSD_XCOMPONENT_ID)
              .backgroundColor(Color.Transparent)
              .visibility((this.loading || this.playbackCompleted || this.shouldShowStartupLandscapeLoading())
                ? Visibility.Hidden
                : Visibility.Visible)
              .onClick(() => {
                this.onOsdSurfaceTap()
              })
          }
        }
        .width(this.videoWidth)
        .aspectRatio(this.videoAspectRatio)
        .onAreaChange((_oldValue: Area, newValue: Area) => {
          const width = newValue.width as number
          const height = newValue.height as number
          if (width > 0 && height > 0) {
            this.videoParentAspectRatio = width / height
            mpvChangeWidth = width
            mpvChangeHeight = height
          }
          this.updateOsdSurfaceSize(
            this.getUIContext().vp2px(width),
            this.getUIContext().vp2px(height)
          )
          this.updateVideoLayoutFromPlayer()
        })
      }
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
        }
        .height('100%')
        .alignContent(Alignment.Bottom)
      }

      // 弹幕功能暂不上线，先注释弹幕渲染层。
      // DanmakuViewV2({ model: this.ui.danmuModel })
      //   .padding({
      //     top: this.vm.appUIState.isFullScreen ? 10 : this.vm.appUIState.safeTop + 10,
      //   })
      //   .width('100%')
      //   .height('100%')
      //   .opacity(0.6)
      //   .hitTestBehavior(HitTestMode.None)

      Flex({ alignItems: ItemAlign.Center, justifyContent: FlexAlign.SpaceBetween }) {
        this.volumeOverlay()
        this.seekPreviewOverlay()
        this.brightnessOverlay()
      }
        .width('100%')
        .height('100%')
        .padding({ left: 36, right: 36 })

      if (this.nativeTopNavigationEnabled &&
        !this.vm.appUIState.isFullScreen &&
        this.showControls &&
        !this.shouldShowStartupLandscapeLoading() &&
        !OneMoreUtil.isPc()) {
        this.nativePortraitTopActions()
      }

      Column() {
        if (this.shouldShowLegacyTopControl()) {
          this.topControlView()
        }
        Blank()
          .layoutWeight(1)
          .width('100%')
          .onClick(() => {
            this.onSurfaceTap()
          })
        this.bottomControls()
      }
      .width('100%')
      .height('100%')
      .visibility(this.showControls && !this.shouldShowStartupLandscapeLoading()
        ? Visibility.Visible
        : Visibility.Hidden)

      if (this.vm.appUIState.isFullScreen &&
        this.fullShowLock &&
        this.showControls &&
        !this.showVolumeUi &&
        !this.showBrightnessUi &&
        !this.showSeekPreviewUi &&
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
        }
        .width('100%')
        .alignContent(Alignment.End)
      }

      if (this.ui.playerMenus?.isShowPlayBackInfo) {
        PlaybackInfoCard({
          menus: this.ui.playerMenus,
          onDismiss: () => {
            if (this.ui.playerMenus) {
              this.ui.playerMenus.isShowPlayBackInfo = false
              this.timeCountdown()
            }
          }
        })
      }

      Stack() {
        Button('退出投屏')
          .onClick(() => {
            this.stopCast()
          })
      }
      .width('100%')
      .height('100%')
      .backgroundColor(Color.Black)
      .visibility(this.ui.isCastPlaying ? Visibility.Visible : Visibility.Hidden)

      Image($r('app.media.icon_replay'))
        .objectFit(ImageFit.Auto)
        .width('120px')
        .height('120px')
        .visibility(this.playbackCompleted ? Visibility.Visible : Visibility.Hidden)
        .onClick(() => {
          this.togglePlayback()
        })

      Stack({ alignContent: Alignment.Center }) {
        Image($r('app.media.icon_load'))
          .objectFit(ImageFit.Auto)
          .width('120px')
          .height('120px')
          .visibility((this.loading || this.shouldShowStartupLandscapeLoading())
            ? Visibility.Visible
            : Visibility.Hidden)
          .border({ width: 0 })
          .borderStyle(BorderStyle.Dashed)
      }
      .width('100%')
      .height('100%')
      .visibility((this.loading || this.shouldShowStartupLandscapeLoading())
        ? Visibility.Visible
        : Visibility.Hidden)

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
    .backgroundColor(Color.Black)
    .clip(true)
    .onHover((isHover: boolean) => {
        if (OneMoreUtil.isPc()) {
          if (isHover) {
            if (this.loading || this.shouldShowStartupLandscapeLoading()) {
              return
            }
            if (!this.showControls) {
              this.timeCountdown()
            }
          } else {
            clearInterval(mpvUiTime)
            this.showControls = false
            this.fullShowLock = false
          }
        }
      })
    .onMouse((event) => {
      if (this.loading || this.shouldShowStartupLandscapeLoading()) {
        return
      }
      if (event.action == MouseAction.Move && !this.showControls) {
        this.timeCountdown()
      }
    })
    .onAreaChange((_oldValue: Area, newValue: Area) => {
      const width = newValue.width as number
      const height = newValue.height as number
      if (width > 0 && height > 0) {
        if (this.vm.appUIState.isFullScreen) {
          this.fullShowLock = true
        }
        // 弹幕功能暂不上线，先注释弹幕层尺寸同步。
        // this.ui.danmuModel.setWidth(this.getUIContext().vp2px(width))
        // this.ui.danmuModel.setHeight(this.getUIContext().vp2px(height))
      }
    })
    .gesture(GestureGroup(GestureMode.Exclusive,
      TapGesture({ count: 2 }).onAction(() => {
        this.onDoubleTap()
      }),
      TapGesture({ count: 1 }).onAction(() => {
        this.onSingleTap()
      }),
      PanGesture(this.panOptionBrightAndVolume)
        .onActionStart((event: GestureEvent | undefined) => {
          if (OneMoreUtil.isPc() || this.lock || this.loading || this.shouldShowStartupLandscapeLoading() ||
            this.isHorizontalDrag || this.ui.isFastSpeed) {
            return
          }
          if (event?.fingerList?.[0]) {
            mpvTouchStartX = event.fingerList[0].localX
            mpvTouchStartY = event.fingerList[0].localY
          }
        })
        .onActionUpdate((event: GestureEvent | undefined) => {
          if (OneMoreUtil.isPc() || this.lock || this.loading || this.shouldShowStartupLandscapeLoading() ||
            this.isHorizontalDrag || this.ui.isFastSpeed) {
            return
          }
          if (event?.fingerList?.[0] && mpvChangeHeight > 0) {
            this.isVerticalDrag = true
            const touchY = event.fingerList[0].localY
            const deltaY = touchY - mpvTouchStartY
            const percent = -deltaY / mpvChangeHeight
            if (mpvTouchStartX >= (mpvChangeWidth / 2)) {
              this.showVolumeUi = true
              let playerVolumeValue = this.mCurrentVolumeValue + this.mMaxVolumeValue * percent
              if (playerVolumeValue < 0) {
                playerVolumeValue = 0
              }
              if (playerVolumeValue > this.mMaxVolumeValue) {
                playerVolumeValue = this.mMaxVolumeValue
              }
              this.mCurrentVolumeValue = playerVolumeValue
              if (this.mAudioManager) {
                this.mAudioManager.setVolume(audio.AudioVolumeType.MEDIA, playerVolumeValue, (_err: BusinessError) => {
                })
              }
            } else {
              this.showBrightnessUi = true
              let brightness = this.mCurrentBrightness + 255 * percent
              if (brightness < 0) {
                brightness = 5
              }
              if (brightness > 255) {
                brightness = 255
              }
              this.vm.appUIState.windowClass?.setWindowBrightness(brightness / 255)
              this.mCurrentBrightness = brightness
            }
            mpvTouchStartY = touchY
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
          if (OneMoreUtil.isPc() || !this.initialized || !this.sourceLoaded || this.lock || this.loading ||
            this.shouldShowStartupLandscapeLoading() || this.ui.isFastSpeed || this.isVerticalDrag) {
            return
          }
          if (event?.fingerList?.[0]) {
            this.showControls = false
            mpvTouchStartX = event.fingerList[0].localX
            this.gestureSeekStartPositionMs = this.currentPosition
            this.gestureSeekPositionMs = this.currentPosition
            this.seekPreviewTime = this.formatTime(this.currentPosition)
            this.seekPreviewProgressValue = this.progressValue
          }
        })
        .onActionUpdate((event: GestureEvent | undefined) => {
          if (OneMoreUtil.isPc() || !this.initialized || !this.sourceLoaded || this.lock || this.loading ||
            this.shouldShowStartupLandscapeLoading() || this.isVerticalDrag || this.ui.isFastSpeed) {
            return
          }
          if (event?.fingerList?.[0] && mpvChangeWidth > 0 && this.duration > 0) {
            this.isHorizontalDrag = true
            this.showSeekPreviewUi = true
            const touchX = event.fingerList[0].localX
            const deltaX = touchX - mpvTouchStartX
            let seekPositionMs = this.gestureSeekStartPositionMs + (deltaX * this.duration / mpvChangeWidth)
            if (seekPositionMs < 0) {
              seekPositionMs = 0
            }
            if (seekPositionMs > this.duration) {
              seekPositionMs = this.duration
            }
            this.gestureSeekPositionMs = seekPositionMs
            this.seekPreviewTime = this.formatTime(seekPositionMs)
            this.seekPreviewProgressValue = this.duration > 0
              ? (seekPositionMs / this.duration) * PROGRESS_MAX_VALUE
              : 0
            this.scheduleSeekPreviewFrame(seekPositionMs, !this.seekPreviewPixelMap)
          }
        })
        .onActionEnd(() => {
          if (OneMoreUtil.isPc()) {
            return
          }
          if (this.ui.isFastSpeed) {
            return
          }
          if (this.isHorizontalDrag) {
            this.progressValue = this.seekPreviewProgressValue
            this.seekTo(this.gestureSeekPositionMs)
            this.showControls = false
          }
          this.isHorizontalDrag = false
          this.showSeekPreviewUi = false
          this.resetSeekPreviewState()
        }),
      LongPressGesture()
        .onAction(() => {
          if (OneMoreUtil.isPc()) {
            return
          }
          if (!this.initialized || !this.sourceLoaded || this.loading || this.shouldShowStartupLandscapeLoading() ||
            this.lock || this.ui.isCastPlaying) {
            return
          }
          try {
            this.mpvPlayer.setPlaybackSpeed(3.0)
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
              this.mpvPlayer.setPlaybackSpeed(this.playSpeed)
            } catch (err) {
            }
            this.ui.isFastSpeed = false
          }
        })
    ))
  }
}
