import { common } from '@kit.AbilityKit'
import {
  PlayerController,
  PlayerEvent,
  PlayerEventListener,
  PlayerEventType,
  PlayerOptions,
  PlayerSource,
  PlayerState
} from 'mfplayer'
import { MediaPlaybackRequestHeader } from '../../media/playback/MediaPlaybackModels'
import { LiveTvUIState } from '../livetv/LiveTvUIState'
import { LiveTvViewModel } from '../livetv/LiveTvViewModel'
import { PlayStatus, PlayerStatus } from './PlayStatus'
import { AppPreference } from '../../data/AppPreference'
import { PlayerChrome } from './PlayerChrome'
import { ThemeStyle } from '../../entity/ThemeStyle'

/**
 * 直播播放的 MFPlayer 接入壳层。
 *
 * 首阶段复用 SDK 默认控制层，先完成播放内核替换；频道列表、换台交互会在
 * MaFei 统一 PlayerChrome 阶段重新接回，避免继续维护旧 MPV 控制层。
 */
@ComponentV2
export struct LivePlayerView {
  @Param @Require videoUrl: string
  @Param @Require onBack: () => void
  @Param @Require onNext: () => void
  @Param @Require onEpisodeChange: (index: number) => void
  @Param @Require onFullscreenToggle: () => void

  @Consumer() ui: LiveTvUIState = new LiveTvUIState(this.getUIContext())
  @Consumer() vm: LiveTvViewModel = new LiveTvViewModel(this.ui)

  private controller: PlayerController = new PlayerController()
  private loadedUrl: string = ''
  private readonly listener: PlayerEventListener = {
    onPlayerEvent: (event: PlayerEvent): void => {
      this.handlePlayerEvent(event)
    }
  }
  private readonly onHostBackground = (): void => {
    this.controller.onHostBackground()
  }
  private readonly onHostForeground = (): void => {
    this.controller.onHostForeground()
  }

  aboutToAppear(): void {
    const context = getContext(this) as common.UIAbilityContext
    this.controller.setCacheDir(context.cacheDir)
    this.controller.addListener(this.listener)
    context.eventHub.on('onBackground', this.onHostBackground)
    context.eventHub.on('onForeground', this.onHostForeground)
    this.loadCurrentSource()
  }

  aboutToDisappear(): void {
    const context = getContext(this) as common.UIAbilityContext
    context.eventHub.off('onBackground', this.onHostBackground)
    context.eventHub.off('onForeground', this.onHostForeground)
    this.controller.removeListener(this.listener)
    this.controller.release()
  }

  @Monitor('videoUrl')
  onVideoUrlChanged(): void {
    this.loadCurrentSource()
  }

  private formatHeaders(headers: MediaPlaybackRequestHeader[]): string[] {
    const values: string[] = []
    for (const header of headers) {
      const name = header.name.trim()
      const value = header.value.trim()
      if (name.length > 0 && value.length > 0) {
        values.push(`${name}: ${value}`)
      }
    }
    return values
  }

  private createPlayerSource(): PlayerSource {
    return {
      uri: this.videoUrl,
      headers: this.formatHeaders(this.ui.videoHeaders)
    }
  }

  private createPlayerOptions(): PlayerOptions {
    const preference = AppPreference.getInstance()
    return {
      autoPlay: true,
      useHardwareDecode: preference.getMpvUseHardwareDecode(),
      cacheSizeMb: preference.getMpvCacheSizeMb(),
      preferSurface: true,
      progressIntervalMs: 500,
      exactSeek: false
    }
  }

  private loadCurrentSource(): void {
    const normalizedUrl = this.videoUrl.trim()
    if (normalizedUrl.length === 0 || normalizedUrl === this.loadedUrl) {
      return
    }
    this.loadedUrl = normalizedUrl
    const loaded = this.controller.load(this.createPlayerSource(), this.createPlayerOptions())
    if (!loaded) {
      console.error(`[MFPlayer][LiveTV] load rejected: ${normalizedUrl}`)
    }
  }

  private handlePlayerEvent(event: PlayerEvent): void {
    if (event.type === PlayerEventType.ERROR) {
      console.error(
        `[MFPlayer][LiveTV] playback error code=${event.errorCode}, ` +
        `native=${event.nativeErrorCode}, message=${event.errorMessage}`
      )
    }
    this.syncPlaybackState(event.state, event.playWhenReady)
  }

  private syncPlaybackState(state: PlayerState, playWhenReady: boolean): void {
    switch (state) {
      case PlayerState.PLAYING:
        this.ui.CONTROL_PlayStatus = PlayStatus.PLAY
        this.ui.playerStatus = PlayerStatus.PLAYING
        break
      case PlayerState.PAUSED:
        this.ui.CONTROL_PlayStatus = PlayStatus.PAUSE
        this.ui.playerStatus = PlayerStatus.PAUSED
        break
      case PlayerState.ENDED:
        this.ui.CONTROL_PlayStatus = PlayStatus.DONE
        this.ui.playerStatus = PlayerStatus.COMPLETED
        break
      case PlayerState.ERROR:
        this.ui.CONTROL_PlayStatus = PlayStatus.PAUSE
        this.ui.playerStatus = PlayerStatus.ERROR
        break
      case PlayerState.RELEASED:
        this.ui.playerStatus = PlayerStatus.RELEASED
        break
      case PlayerState.READY:
        this.ui.playerStatus = PlayerStatus.PREPARED
        break
      default:
        if (!playWhenReady) {
          this.ui.CONTROL_PlayStatus = PlayStatus.PAUSE
        }
        break
    }
  }

  private currentChannelTitle(): string {
    const channel = this.vm.channelList[this.vm.playIndex]
    return channel?.Name ?? ''
  }

  build() {
    PlayerChrome({
      controller: this.controller,
      title: this.currentChannelTitle(),
      isFullscreen: this.vm.appUIState.isFullScreen,
      isLive: true,
      nativeSurface: this.vm.appUIState.themeStyle === ThemeStyle.Native,
      systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable,
      hasPrevious: this.vm.playIndex > 0,
      hasNext: this.vm.playIndex < this.vm.channelList.length - 1,
      onRetry: (): void => {
        this.loadedUrl = ''
        this.loadCurrentSource()
      },
      onBack: (): void => {
        this.onBack()
      },
      onFullscreenToggle: (): void => {
        this.onFullscreenToggle()
      },
      onPrevious: (): void => {
        this.vm.playPrevious()
      },
      onNext: (): void => {
        this.onNext()
      }
    })
  }
}
