import prompt from '@ohos.promptAction'
import { BusinessError, emitter } from '@kit.BasicServicesKit'
import { window, PiPWindow } from '@kit.ArkUI'
import { common } from '@kit.AbilityKit'
import { avSession } from '@kit.AVSessionKit'
import { MpvNativePlayer } from 'mpvplayer'
import { AppPreference } from '../../data/AppPreference'
import avSessionManager from '../../utils/AVSessionManager'
import { Constants } from '../../utils/Constants'
import { PlayerStatus, PlayStatus } from './PlayStatus'
import { OneMoreUtil } from '../../utils/OneMoreUtil'
import { LiveTvUIState } from '../livetv/LiveTvUIState'
import { LiveTvViewModel } from '../livetv/LiveTvViewModel'
import { BaseItemDto } from 'sdk/src/main/ets/generated-client/models'
import { ImageKnifeComponent } from '@ohos/imageknifepro'

const LIVE_MPV_XCOMPONENT_ID: string = 'liveMpvSurface'
let uiTime: number = 0

@ComponentV2
export struct LivePlayerView {
  @Param @Require videoUrl: string
  @Param @Require onNext: () => void
  @Param @Require onEpisodeChange: (index: number) => void

  @Consumer() ui: LiveTvUIState = new LiveTvUIState(this.getUIContext())
  @Consumer() vm: LiveTvViewModel = new LiveTvViewModel(this.ui)

  @Local loadingVisible: Visibility = Visibility.None
  @Local replayVisible: Visibility = Visibility.None
  @Local videoAspectRatio: number = 16 / 9
  @Local videoWidth: string = '100%'
  @Local showControls: boolean = false
  @Local showChannelMenu: boolean = false
  @Local isPanning: boolean = false
  @Local hintMsgVisibility: boolean = false
  @Local offsetY: number = 0

  private mpvPlayer: MpvNativePlayer = new MpvNativePlayer()
  private xComponentController: XComponentController = new XComponentController()
  private channelListScroller: Scroller = new Scroller()
  private panOptionChangeChannel: PanGestureOptions = new PanGestureOptions({ direction: PanDirection.Vertical })
  private currentPosition: number = 0
  private videoParentAspectRatio: number = 16 / 9
  private xComponentReady: boolean = false
  private initialized: boolean = false
  private initializeRequested: boolean = false
  private sourceLoaded: boolean = false
  private playbackCompleted: boolean = false
  private shouldResumeOnPageShow: boolean = false
  private progressTimer: number = -1
  private initializeTimer: number = -1
  private mFirst: boolean = true
  private last: number = 0
  private sec: number = 2
  private keepScreenOnState?: boolean

  pipController?: PiPWindow.PiPController

  @Monitor('videoUrl')
  onUrlChange(): void {
    this.sourceLoaded = false
    this.playbackCompleted = false
    this.currentPosition = 0
    if (this.initialized) {
      this.loadCurrentVideo()
    } else {
      this.scheduleInitializePlayer()
    }
  }

  @Monitor('showControls')
  onControlVisibilityChanged(): void {
    this.changeSystemBar()
  }

  @Monitor('showChannelMenu')
  onChannelMenuVisibilityChanged(): void {
    if (this.showChannelMenu && this.vm.channelList.length > 0) {
      setTimeout(() => {
        this.scrollToSelectedChannel()
      }, 50)
    }
  }

  @Monitor('vm.appUIState.isFullScreen')
  onScreenChanged(): void {
    this.changeSystemBar()
    if (this.vm.appUIState.isFullScreen) {
      if (OneMoreUtil.isNormalPhone()) {
        if (canIUse('SystemCapability.Window.SessionManager')) {
          this.vm.appUIState.windowClass?.setPreferredOrientation(window.Orientation.USER_ROTATION_LANDSCAPE).catch()
        }
      }
    } else {
      if (OneMoreUtil.isNormalPhone()) {
        if (canIUse('SystemCapability.Window.SessionManager')) {
          this.vm.appUIState.windowClass?.setPreferredOrientation(window.Orientation.USER_ROTATION_PORTRAIT).catch()
        }
      }
    }
  }

  @Monitor('ui.playerStatus')
  onPlayerStateChanged(): void {
    this.sendAVSessionState(this.ui.playerStatus)
    this.updateWindowKeepScreenOn(this.ui.playerStatus === PlayerStatus.PLAYING)
  }

  @Monitor('ui.isCastPlaying')
  onCastChanged(): void {
    if (this.ui.isCastPlaying) {
      this.vm.appUIState.isFullScreen = false
    }
  }

  private changeSystemBar(): void {
    if (!this.vm.appUIState.isFullScreen && this.showControls) {
      this.vm.appUIState.windowClass?.setWindowSystemBarEnable(['status', 'navigation'])
    } else {
      this.vm.appUIState.windowClass?.setWindowSystemBarEnable([])
    }
  }

  private scrollToSelectedChannel(): void {
    if (this.vm.playIndex >= 0 && this.vm.playIndex < this.vm.channelList.length) {
      this.channelListScroller.scrollToIndex(this.vm.playIndex, false, ScrollAlign.CENTER)
    }
  }

  aboutToAppear(): void {
    this.bindPlayerCallbacks()
    emitter.on(Constants.PLAYER_EVENT, (data) => {
      if (!data.data || !data.data['eventName']) {
        return
      }
      let name = data.data['eventName'] as string
      switch (name) {
        case 'onPageShow':
          if (this.ui.isCastPlaying) {
            return
          }
          if (this.shouldResumeOnPageShow && this.sourceLoaded && !this.playbackCompleted) {
            this.resumePlayback()
          } else if (!this.mFirst) {
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
        case 'Destroy':
          this.stop()
          break
        default:
          break
      }
    })

    let context = getContext(this) as common.UIAbilityContext
    context.eventHub.on('onForeground', () => {
      this.shouldResumeOnPageShow = false
      if (canIUse('SystemCapability.Window.SessionManager') && this.pipController) {
        this.pipController.stopPiP().catch(() => {})
      }
    })
  }

  aboutToDisappear(): void {
    this.updateWindowKeepScreenOn(false)
    emitter.off(Constants.PLAYER_EVENT.eventId)
    let context = getContext(this) as common.UIAbilityContext
    context.eventHub.off('onForeground')
    if (this.pipController) {
      this.pipController.stopPiP().catch(() => {})
    }
    this.stopInitializeTimer()
    this.stopProgressTimer()
    this.stop()
    this.vm.appUIState.windowClass?.setWindowSystemBarEnable(['status', 'navigation'])
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
      console.error(`[LivePlayer] setWindowKeepScreenOn(${shouldKeepOn}) failed: ${JSON.stringify(err)}`)
    })
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
    }
  }

  private bindPlayerCallbacks(): void {
    this.mpvPlayer.setListeners({
      onInitialized: () => {
        this.initialized = true
        this.initializeRequested = false
        this.ui.playerStatus = PlayerStatus.INITIALIZED
        this.loadCurrentVideo()
        this.startProgressTimer()
      },
      onEndFile: (reason: number, error: string) => {
        this.stopProgressTimer()
        this.hideLoading()
        this.shouldResumeOnPageShow = false
        this.playbackCompleted = true
        this.ui.CONTROL_PlayStatus = PlayStatus.DONE
        this.ui.playerStatus = reason === 0 ? PlayerStatus.COMPLETED : PlayerStatus.ERROR
        if (reason !== 0) {
          console.error(`[MPV][live] end file reason=${reason}, error=${error}`)
          prompt.showToast({
            message: getContext().resourceManager.getStringByNameSync('Honey_the_video_is_playing_errant_The_system_is_wandering')
          })
          this.replayVisible = Visibility.Visible
        }
      },
      onLoadingChanged: (loading: boolean) => {
        this.loadingVisible = loading ? Visibility.Visible : Visibility.None
      },
      onStartFile: () => {
        this.showLoading()
        this.ui.CONTROL_PlayStatus = PlayStatus.PLAY
        this.ui.playerStatus = PlayerStatus.PLAYING
      },
      onFileLoaded: () => {
        this.sourceLoaded = true
        this.playbackCompleted = false
        this.hideLoading()
        this.ui.CONTROL_PlayStatus = PlayStatus.PLAY
        this.ui.playerStatus = PlayerStatus.PLAYING
        this.updateVideoLayoutFromPlayer()
      },
      onHwdecChanged: (decoder: string) => {
        console.info(`[MPV][live] hwdec changed: ${decoder}`)
      },
      onLogMessage: (level: string, prefix: string, text: string) => {
        const line = text.trim()
        if (!line) {
          return
        }
        if (level === 'error') {
          console.error(`[MPV][${prefix}] ${line}`)
        } else if (level === 'warn') {
          console.warn(`[MPV][${prefix}] ${line}`)
        }
      }
    })
  }

  private scheduleInitializePlayer(): void {
    if (this.initialized || this.initializeRequested || !this.xComponentReady) {
      return
    }
    this.initializeRequested = true
    this.showLoading()
    this.stopInitializeTimer()
    this.initializeTimer = setTimeout(() => {
      this.initializeTimer = -1
      if (!this.xComponentReady || this.initialized) {
        this.initializeRequested = false
        return
      }
      this.initializePlayer()
    }, 16)
  }

  private initializePlayer(): void {
    this.ui.playerStatus = PlayerStatus.IDLE
    this.initializeRequested = false
    this.playbackCompleted = false
    const useHardwareDecode = AppPreference.getInstance().getMpvUseHardwareDecode()
    const cacheSizeMb = AppPreference.getInstance().getMpvCacheSizeMb()
    this.mpvPlayer.configurePlayback(useHardwareDecode, cacheSizeMb, true)
    console.info(`[MPV][live] initialize player with ${this.mpvPlayer.getCurrentModeLabel()}, cache=${cacheSizeMb}MB`)
    if (!this.mpvPlayer.initialize()) {
      this.hideLoading()
      this.ui.playerStatus = PlayerStatus.ERROR
      console.error('[MPV][live] initialize failed')
    }
  }

  private loadCurrentVideo(): void {
    if (!this.initialized || this.videoUrl.length === 0) {
      return
    }
    this.sourceLoaded = true
    this.playbackCompleted = false
    this.showLoading()
    this.ui.CONTROL_PlayStatus = PlayStatus.PLAY
    this.ui.playerStatus = PlayerStatus.PLAYING
    this.mpvPlayer.load(this.videoUrl)
    this.startProgressTimer()
  }

  private startPlayOrResumePlay(): void {
    if (this.initialized && this.sourceLoaded && this.ui.CONTROL_PlayStatus === PlayStatus.PAUSE) {
      this.resumePlayback()
      this.timeCountdown()
      return
    }

    if (this.initialized && this.sourceLoaded && this.ui.CONTROL_PlayStatus === PlayStatus.PLAY) {
      return
    }

    if (this.initialized) {
      this.loadCurrentVideo()
    } else {
      this.scheduleInitializePlayer()
    }
    this.timeCountdown()
  }

  private pause(): void {
    if (!this.initialized || !this.sourceLoaded) {
      return
    }
    this.mpvPlayer.pause()
    this.ui.CONTROL_PlayStatus = PlayStatus.PAUSE
    this.ui.playerStatus = PlayerStatus.PAUSED
    this.shouldResumeOnPageShow = true
  }

  private resumePlayback(): void {
    if (!this.initialized || !this.sourceLoaded) {
      return
    }
    this.mpvPlayer.play()
    this.ui.CONTROL_PlayStatus = PlayStatus.PLAY
    this.ui.playerStatus = PlayerStatus.PLAYING
    this.shouldResumeOnPageShow = false
  }

  private stop(): void {
    this.stopProgressTimer()
    this.stopInitializeTimer()
    this.mpvPlayer.release()
    this.initialized = false
    this.initializeRequested = false
    this.sourceLoaded = false
    this.playbackCompleted = false
    this.currentPosition = 0
    this.ui.CONTROL_PlayStatus = PlayStatus.INIT
    this.ui.playerStatus = PlayerStatus.RELEASED
    this.loadingVisible = Visibility.None
    this.replayVisible = Visibility.None
  }

  private showLoading(): void {
    this.loadingVisible = Visibility.Visible
    this.replayVisible = Visibility.None
  }

  private hideLoading(): void {
    this.loadingVisible = Visibility.None
    this.replayVisible = Visibility.None
  }

  private timeCountdown(): void {
    clearInterval(uiTime)
    this.showControls = true
    this.sec = 2
    uiTime = setInterval(() => {
      if (this.sec <= 0) {
        clearInterval(uiTime)
        this.showControls = false
      } else {
        this.sec--
      }
    }, 1000)
  }

  private debounce(): boolean {
    const delay = 600
    const cur = new Date().getTime()
    if (cur - this.last > delay) {
      this.last = cur
      return true
    }
    return false
  }

  private startProgressTimer(): void {
    this.stopProgressTimer()
    this.progressTimer = setInterval(() => {
      if (!this.initialized) {
        return
      }
      const position = this.mpvPlayer.getCurrentPositionMs()
      if (position >= 0) {
        this.currentPosition = position
      }
      this.updateVideoLayoutFromPlayer()
    }, 500)
  }

  private stopProgressTimer(): void {
    if (this.progressTimer >= 0) {
      clearInterval(this.progressTimer)
      this.progressTimer = -1
    }
  }

  private stopInitializeTimer(): void {
    if (this.initializeTimer >= 0) {
      clearTimeout(this.initializeTimer)
      this.initializeTimer = -1
    }
  }

  private sendAVSessionState(status: number): void {
    let playbackState: avSession.AVPlaybackState = {
      speed: 1,
      position: {
        elapsedTime: this.currentPosition,
        updateTime: Date.now()
      }
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
      default:
        break
    }
    avSessionManager.getSession()?.setAVPlaybackState(playbackState).then(() => {
    }).catch((_err: BusinessError) => {
    })
  }

  async createPipController(): Promise<void> {
    if (!canIUse('SystemCapability.Window.SessionManager')) {
      return
    }
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

  onPipStateChange(state: PiPWindow.PiPState, _reason: string): void {
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

  onPipActionEvent(event: PiPWindow.PiPActionEventType, status: number | undefined): void {
    if (event === 'playbackStateChanged') {
      if (status === 0) {
        this.pause()
      } else {
        this.startPlayOrResumePlay()
      }
    }
  }

  async startPip(): Promise<void> {
    if (!this.pipController) {
      await this.createPipController()
    }
    if (!this.pipController) {
      return
    }
    await this.pipController.startPiP()
  }

  private getCurrentProgramDisplayText(): string | undefined {
    if (this.vm.playIndex < 0 || this.vm.playIndex >= this.vm.channelList.length) {
      return undefined
    }
    const currentProgram = this.vm.channelList[this.vm.playIndex]?.CurrentProgram
    const name = currentProgram?.Name
    if (!name) {
      return undefined
    }
    const timeText = this.formatProgramTime(currentProgram?.StartDate, currentProgram?.EndDate)
    return timeText ? `${timeText} ${name}` : name
  }

  private formatProgramTime(startDate?: string | null, endDate?: string | null): string | undefined {
    if (!startDate || !endDate) {
      return undefined
    }
    const start = new Date(startDate)
    const end = new Date(endDate)
    if (isNaN(start.getTime()) || isNaN(end.getTime())) {
      return undefined
    }
    const pad2 = (value: number): string => value.toString().padStart(2, '0')
    const startText = `${pad2(start.getHours())}:${pad2(start.getMinutes())}`
    const endText = `${pad2(end.getHours())}:${pad2(end.getMinutes())}`
    return `${startText} - ${endText}`
  }

  @Builder
  leftControlView() {
    Column() {
      Text('频道列表')
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor(Color.White)
        .width('100%')
        .padding({ top: 15, bottom: 10, left: 12, right: 12 })
        .textAlign(TextAlign.Start)

      Divider()
        .color('rgba(255, 255, 255, 0.2)')
        .strokeWidth(1)
        .margin({ bottom: 8 })

      List({ space: 6, scroller: this.channelListScroller }) {
        ForEach(this.vm.channelList, (channel: BaseItemDto, index: number) => {
          ListItem() {
            Row() {
              Text(`${index + 1}`)
                .fontSize(12)
                .fontColor(this.vm.playIndex === index ? '#00D4FF' : 'rgba(255, 255, 255, 0.6)')
                .width(30)
                .textAlign(TextAlign.Center)
                .fontWeight(this.vm.playIndex === index ? FontWeight.Bold : FontWeight.Normal)

              Text(channel.Name || '未知频道')
                .fontSize(14)
                .fontColor(this.vm.playIndex === index ? Color.White : 'rgba(255, 255, 255, 0.85)')
                .maxLines(2)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
                .layoutWeight(1)
                .fontWeight(this.vm.playIndex === index ? FontWeight.Medium : FontWeight.Normal)

              if (this.vm.playIndex === index) {
                Image($r('sys.media.ohos_ic_public_play'))
                  .width(16)
                  .height(16)
                  .fillColor('#00D4FF')
                  .margin({ left: 8 })
              }
            }
            .width('100%')
            .padding({ left: 12, right: 12, top: 10, bottom: 10 })
            .backgroundColor(this.vm.playIndex === index ? 'rgba(0, 212, 255, 0.15)' : 'rgba(255, 255, 255, 0.05)')
            .borderRadius(8)
            .border({
              width: this.vm.playIndex === index ? 1 : 0,
              color: 'rgba(0, 212, 255, 0.3)'
            })
            .onClick(() => {
              this.vm.playIndex = index
              this.vm.play()
              this.showChannelMenu = false
              this.vm.showChannelInfoWithDelay()
            })
            .animation({
              duration: 200,
              curve: Curve.EaseInOut
            })
          }
          .margin({ left: 8, right: 8 })
        }, (channel: BaseItemDto) => channel.Id)
      }
      .scrollBar(BarState.Off)
      .scrollBarColor('rgba(255, 255, 255, 0.3)')
      .scrollBarWidth(4)
      .width('100%')
      .layoutWeight(1)
      .padding({ bottom: 8 })
      .edgeEffect(EdgeEffect.Spring)
    }
    .padding({
      bottom: this.showControls && !this.vm.appUIState.isFullScreen ? this.vm.appUIState.safeBottom : 10,
      left: this.vm.appUIState.isFullScreen ? this.vm.appUIState.safeTop : 0,
      right: this.vm.appUIState.isFullScreen ? this.vm.appUIState.safeTop : 0,
      top: 10
    })
    .width(240)
    .height('100%')
    .backgroundColor('rgba(0, 0, 0, 0.75)')
    .backdropBlur(30)
    .shadow({
      radius: 20,
      color: 'rgba(0, 0, 0, 0.5)',
      offsetX: 5,
      offsetY: 0
    })
    .borderRadius({ topRight: 12, bottomRight: 12 })
    .transition(TransitionEffect.OPACITY.animation({ duration: 200 })
      .combine(TransitionEffect.translate({ x: -240 }).animation({ duration: 300, curve: Curve.FastOutSlowIn })))
  }

  @Builder
  channelInfoView() {
    if (this.vm.channelList.length > 0 && this.vm.playIndex < this.vm.channelList.length) {
      Column() {
        Row() {
          Text(`${this.vm.playIndex + 1}`)
            .fontSize(48)
            .fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.color_main'))
        }
        .width('100%')
        .justifyContent(FlexAlign.End)
        .padding({ top: this.vm.appUIState.safeTop })

        Blank().layoutWeight(1)

        Row({ space: 12 }) {
          if (this.vm.getCurrentChannelImageUrl()) {
            ImageKnifeComponent({
              imageKnifeOption: {
                loadSrc: this.vm.getCurrentChannelImageUrl(),
                objectFit: ImageFit.Contain,
                border: {
                  radius: 8
                }
              }
            })
              .width(120)
              .height(60)
          }
          Column({ space: 12 }) {
            Text(this.vm.channelList[this.vm.playIndex].Name || '未知频道')
              .fontSize(24)
              .fontWeight(FontWeight.Bold)
              .fontColor(Color.White)
              .maxLines(2)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
              .layoutWeight(1)
              .textAlign(TextAlign.Start)

            if (this.getCurrentProgramDisplayText()) {
              Text(this.getCurrentProgramDisplayText())
                .fontSize(14)
                .fontColor('rgba(255, 255, 255, 0.8)')
                .maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
                .textAlign(TextAlign.Start)
                .layoutWeight(1)
            }
          }
          .layoutWeight(1)
          .alignItems(HorizontalAlign.Start)
        }
        .width('100%')
        .height(90)
        .padding({ left: 30, right: 30, top: 15, bottom: 15 })
        .backgroundColor('rgba(0, 0, 0, 0.3)')
        .backgroundBlurStyle(BlurStyle.BACKGROUND_ULTRA_THICK)
        .borderRadius(20)
        .margin({
          left: 20,
          right: 20,
          bottom: this.vm.appUIState.isFullScreen ? this.vm.appUIState.safeBottom : 0
        })
      }
      .padding({ left: 100, right: 100 })
      .width('100%')
      .height('100%')
    }
  }

  build() {
    Stack({ alignContent: Alignment.Center }) {
      Text('当前视频正在画中画播放')
        .fontColor(Color.Gray)
        .margin({ bottom: 30 })
        .visibility(this.hintMsgVisibility ? Visibility.Visible : Visibility.Hidden)

      Column() {
        XComponent({
          id: LIVE_MPV_XCOMPONENT_ID,
          type: XComponentType.SURFACE,
          libraryname: 'mpvnative',
          controller: this.xComponentController
        })
          .onLoad(async () => {
            this.xComponentReady = true
            this.scheduleInitializePlayer()
            setTimeout(() => {
              this.mFirst = false
            }, 300)
            if (canIUse('SystemCapability.Window.SessionManager') && !this.pipController) {
              await this.createPipController()
            }
          })
          .onDestroy(() => {
            this.xComponentReady = false
            this.stop()
          })
          .width('100%')
          .height('100%')
          .id(LIVE_MPV_XCOMPONENT_ID)
          .backgroundColor(Color.Black)
      }
      .width(this.videoWidth)
      .aspectRatio(this.videoAspectRatio)
      .onAreaChange((_oldValue: Area, newValue: Area) => {
        const width = newValue.width as number
        const height = newValue.height as number
        if (width > 0 && height > 0) {
          this.videoParentAspectRatio = width / height
          this.updateVideoLayoutFromPlayer()
        }
      })

      if (this.ui.showChannelInfo) {
        this.channelInfoView()
      }

      Row() {
        if (this.showChannelMenu) {
          this.leftControlView()
        }
        Blank().layoutWeight(1)
      }
      .height('100%')
      .gesture(GestureGroup(GestureMode.Parallel,
        TapGesture({ count: 1 })
          .onAction(() => {
            if (this.isPanning) {
              return
            }
            this.showChannelMenu = !this.showChannelMenu
            this.timeCountdown()
          }),
        PanGesture(this.panOptionChangeChannel)
          .onActionStart(() => {
            this.offsetY = 0
            this.isPanning = false
          })
          .onActionUpdate((event: GestureEvent) => {
            this.offsetY = event.offsetY
            if (Math.abs(this.offsetY) > 10) {
              this.isPanning = true
            }
          })
          .onActionEnd(() => {
            if (Math.abs(this.offsetY) > 30 && this.debounce()) {
              if (this.offsetY > 0) {
                this.vm.playPrevious()
              } else {
                this.onNext()
              }
            }
            setTimeout(() => {
              this.isPanning = false
            }, 100)
          })
      ))

      Image($r('app.media.icon_load'))
        .objectFit(ImageFit.Auto)
        .width('120px')
        .height('120px')
        .visibility(this.loadingVisible)
        .border({ width: 0 })
        .borderStyle(BorderStyle.Dashed)
    }
    .width('100%')
    .backgroundColor($r('sys.color.black'))
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
          clearInterval(uiTime)
          this.showControls = false
        }
      }
    })
    .onMouse((event) => {
      if (this.loadingVisible == Visibility.Visible) {
        return
      }
      if (event.action == MouseAction.Move && !this.showControls) {
        this.timeCountdown()
      }
    })
  }
}
