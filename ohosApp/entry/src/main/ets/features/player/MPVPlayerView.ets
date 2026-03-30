import { MediaStream } from 'sdk/src/main/ets/generated-client/models'
import { QualityOption } from '../../player/qualityoptions/QualityOption'
import { PlayerPageUIState } from './PlayerPageUIState'
import { PlayerPageViewModel } from './PlayerPageViewModel'
import { PlayStatus, PlayerStatus } from './PlayStatus'
import { MpvNativePlayer } from 'mpvplayer'
import { AppPreference } from '../../data/AppPreference'

const PROGRESS_MAX_VALUE: number = 100
const MPV_XCOMPONENT_ID: string = 'mpvPlayerSurface'

@ComponentV2
export struct MPVPlayerView {
  @Param @Require videoUrl: string
  @Param @Require onNext: () => void
  @Param @Require onAudioTrackSelected: (stream: MediaStream) => void
  @Param @Require onSubtitleSelected: (stream: MediaStream) => void
  @Param @Require onQualitySelected: (option: QualityOption) => void
  @Param @Require onEpisodeChange: (index: number) => void
  @Param @Require stopCast: () => void

  @Consumer() ui: PlayerPageUIState = new PlayerPageUIState(this.getUIContext())
  @Consumer() vm: PlayerPageViewModel = new PlayerPageViewModel(this.ui)

  @Local progressValue: number = 0
  @Local currentTime: string = '00:00'
  @Local totalTime: string = '00:00'
  @Local duration: number = 0
  @Local currentPosition: number = 0
  @Local showControls: boolean = true
  @Local initialized: boolean = false
  @Local loading: boolean = true
  @Local playbackCompleted: boolean = false

  private xComponentController: XComponentController = new XComponentController()
  private mpvPlayer: MpvNativePlayer = new MpvNativePlayer()
  private progressTimer: number = -1
  private diagnosticsTimer: number = -1
  private sourceLoaded: boolean = false

  @Monitor('videoUrl')
  onUrlChange(): void {
    this.sourceLoaded = false
    this.playbackCompleted = false
    this.currentPosition = 0
    this.progressValue = 0
    this.currentTime = '00:00'
    if (this.initialized) {
      this.loadCurrentVideo()
    }
  }

  aboutToAppear(): void {
    this.bindPlayerCallbacks()
  }

  aboutToDisappear(): void {
    this.stopProgressTimer()
    this.stopDiagnosticsTimer()
    this.mpvPlayer.release()
    this.initialized = false
    this.sourceLoaded = false
  }

  private bindPlayerCallbacks(): void {
    this.mpvPlayer.setListeners({
      onInitialized: () => {
        this.logMpv(`initialized with mode=${this.mpvPlayer.getCurrentModeLabel()}`)
        this.initialized = true
        this.ui.playerStatus = PlayerStatus.INITIALIZED
        this.loading = false
        this.loadCurrentVideo()
        this.startProgressTimer()
      },
      onEndFile: (reason: number, error: string) => {
        this.logMpv(`end file reason=${reason}, error=${error}`)
        this.ui.CONTROL_PlayStatus = PlayStatus.DONE
        this.ui.playerStatus = PlayerStatus.COMPLETED
        this.playbackCompleted = true
      },
      onDurationChanged: (durationMs: number) => {
        if (durationMs > 0) {
          this.duration = durationMs
          this.totalTime = this.formatTime(durationMs)
        }
      },
      onLoadingChanged: (loading: boolean) => {
        this.loading = loading
      },
      onStartFile: () => {
        this.loading = true
        this.logMpv(`start file url=${this.videoUrl}`)
      },
      onFileLoaded: () => {
        this.loading = false
        this.logVideoDiagnostics('file-loaded')
        this.scheduleDiagnostics('delayed-file-loaded', 1500)
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

  private initializePlayer(): void {
    this.ui.playerStatus = PlayerStatus.IDLE
    this.loading = true
    this.playbackCompleted = false
    const useHardwareDecode = AppPreference.getInstance().getMpvUseHardwareDecode()
    const cacheSizeMb = AppPreference.getInstance().getMpvCacheSizeMb()
    this.mpvPlayer.configurePlayback(useHardwareDecode, cacheSizeMb)
    this.logMpv(`initialize player with ${useHardwareDecode ? 'buffer-hw' : 'buffer-sw'}, cache=${cacheSizeMb}MB`)
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
    this.sourceLoaded = true
    this.playbackCompleted = false
    this.loading = true
    this.ui.CONTROL_PlayStatus = PlayStatus.PLAY
    this.ui.playerStatus = PlayerStatus.PLAYING
    this.logMpv(`load video with mode=${this.mpvPlayer.getCurrentModeLabel()}`)
    this.mpvPlayer.load(this.videoUrl)
    this.scheduleDiagnostics('after-load', 2500)
  }

  private togglePlayback(): void {
    if (!this.initialized || !this.sourceLoaded) {
      return
    }
    if (this.ui.CONTROL_PlayStatus === PlayStatus.PLAY) {
      this.mpvPlayer.pause()
      this.ui.CONTROL_PlayStatus = PlayStatus.PAUSE
      this.ui.playerStatus = PlayerStatus.PAUSED
    } else {
      if (this.playbackCompleted) {
        this.mpvPlayer.seekToMs(0, true)
        this.playbackCompleted = false
      }
      this.mpvPlayer.play()
      this.ui.CONTROL_PlayStatus = PlayStatus.PLAY
      this.ui.playerStatus = PlayerStatus.PLAYING
    }
  }

  private onSeek(value: number): void {
    if (!this.initialized || this.duration <= 0) {
      return
    }
    const seekMs = (value / PROGRESS_MAX_VALUE) * this.duration
    this.mpvPlayer.seekToMs(seekMs, false)
    this.currentPosition = seekMs
    this.currentTime = this.formatTime(seekMs)
    this.progressValue = value
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
        this.currentPosition = position
        this.currentTime = this.formatTime(position)
        if (this.duration > 0) {
          this.progressValue = Math.min(PROGRESS_MAX_VALUE, (position / this.duration) * PROGRESS_MAX_VALUE)
        }
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

  @Builder
  private centerOverlay(): void {
    if (this.loading) {
      Column() {
        LoadingProgress()
          .color(Color.White)
          .width(36)
          .height(36)
        Text('MPV 加载中')
          .fontColor(Color.White)
          .fontSize(12)
          .margin({ top: 12 })
      }
      .padding(20)
      .backgroundColor('#66000000')
      .borderRadius(12)
    } else if (this.playbackCompleted) {
      Column() {
        Button('重新播放')
          .fontSize(14)
          .onClick(() => {
            this.togglePlayback()
          })
      }
      .padding(20)
      .backgroundColor('#66000000')
      .borderRadius(12)
    }
  }

  @Builder
  private bottomControls(): void {
    Column() {
      Row() {
        Text(this.currentTime)
          .fontColor(Color.White)
          .fontSize(13)
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
          .onChange((value: number) => {
            this.onSeek(value)
          })
        Text(this.totalTime)
          .fontColor(Color.White)
          .fontSize(13)
          .margin({ left: 8, right: 8 })
        Image($r('app.media.ic_full'))
          .width(44)
          .height(44)
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
      }
      .justifyContent(FlexAlign.Center)
      .width('100%')
    }
    .width('100%')
    .padding({ left: 8, right: 8, top: 8, bottom: 16 })
    .backgroundColor('#66000000')
  }

  build() {
    Stack({ alignContent: Alignment.Bottom }) {
      XComponent({
        id: MPV_XCOMPONENT_ID,
        type: XComponentType.SURFACE,
        libraryname: 'mpvnative',
        controller: this.xComponentController
      })
        .onLoad(() => {
          this.initializePlayer()
        })
        .width('100%')
        .height('100%')
        .id(MPV_XCOMPONENT_ID)
        .backgroundColor(Color.Black)
        .onClick(() => {
          this.showControls = !this.showControls
        })

      Column() {
        this.centerOverlay()
      }
      .width('100%')
      .height('100%')
      .justifyContent(FlexAlign.Center)
      .alignItems(HorizontalAlign.Center)
      .hitTestBehavior(HitTestMode.None)

      if (this.showControls) {
        this.bottomControls()
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Color.Black)
  }
}
