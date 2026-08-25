import { common } from '@kit.AbilityKit'
import { emitter } from '@kit.BasicServicesKit'
import { PiPWindow } from '@kit.ArkUI'
import {
  PlayerController,
  PlayerEvent,
  PlayerEventListener,
  PlayerEventType,
  PlayerExternalSubtitle,
  PlayerOptions,
  PlayerSource,
  PlayerState,
  PlayerTrack
} from 'mfplayer'
import {
  MediaPlaybackExternalSubtitle,
  MediaPlaybackRequestHeader,
  MediaPlaybackPlayMethod,
  MediaPlaybackSource,
  MediaPlaybackStream,
  MediaPlaybackSubtitleDeliveryMethod
} from '../../media/playback/MediaPlaybackModels'
import { PlayerPageUIState } from './PlayerPageUIState'
import { PlayerPageViewModel } from './PlayerPageViewModel'
import { PlayStatus, PlayerStatus } from './PlayStatus'
import { AppPreference } from '../../data/AppPreference'
import { PlayerChrome } from './PlayerChrome'
import { ThemeStyle } from '../../entity/ThemeStyle'
import { QualityOption } from '../../player/qualityoptions/QualityOption'
import { Constants } from '../../utils/Constants'

/**
 * MFPlayer 的首阶段接入壳层。
 *
 * 当前先使用 SDK 自带控制层验证播放内核、生命周期和媒体数据契约；
 * 后续 PlayerChrome 完成后只替换 build 中的 UI，不再改动播放器接入。
 */
@ComponentV2
export struct MFPlayerView {
  @Param @Require videoUrl: string
  @Param @Require onBack: () => void
  @Param @Require onFullscreenToggle: () => void

  @Consumer() ui: PlayerPageUIState = new PlayerPageUIState(this.getUIContext())
  @Consumer() vm: PlayerPageViewModel = new PlayerPageViewModel(this.ui)

  private controller: PlayerController = new PlayerController()
  private loadedUrl: string = ''
  private videoSurfaceController?: XComponentController
  private pipController?: PiPWindow.PiPController
  private pipActive: boolean = false
  private readonly listener: PlayerEventListener = {
    onPlayerEvent: (event: PlayerEvent): void => {
      this.handlePlayerEvent(event)
    }
  }
  private readonly onHostBackground = (): void => {
    if (!this.pipActive) {
      this.controller.onHostBackground()
    }
  }
  private readonly onHostForeground = (): void => {
    this.controller.onHostForeground()
    if (this.pipActive) {
      void this.stopPip()
    }
  }

  aboutToAppear(): void {
    const context = getContext(this) as common.UIAbilityContext
    this.vm.onNativePlayerAttached()
    this.controller.setCacheDir(context.cacheDir)
    this.controller.addListener(this.listener)
    context.eventHub.on('onBackground', this.onHostBackground)
    context.eventHub.on('onForeground', this.onHostForeground)
    emitter.on(Constants.PLAYER_EVENT, (data) => this.handlePlayerCommand(data))
    this.loadCurrentSource()
  }

  aboutToDisappear(): void {
    const context = getContext(this) as common.UIAbilityContext
    context.eventHub.off('onBackground', this.onHostBackground)
    context.eventHub.off('onForeground', this.onHostForeground)
    emitter.off(Constants.PLAYER_EVENT.eventId)
    this.controller.removeListener(this.listener)
    void this.stopPip()
    this.controller.release()
    this.vm.onNativePlayerReleased()
  }

  @Monitor('videoUrl')
  onVideoUrlChanged(): void {
    this.loadCurrentSource()
  }

  @Monitor('ui.isCastPlaying')
  onCastPlayingChanged(): void {
    if (this.ui.isCastPlaying) {
      if (this.pipActive) {
        void this.stopPip()
      }
      if (this.vm.appUIState.isFullScreen) {
        this.onFullscreenToggle()
      }
    }
  }

  private onVideoSurfaceControllerReady(controller: XComponentController): void {
    this.videoSurfaceController = controller
  }

  private async ensurePipController(): Promise<void> {
    if (this.pipController || !this.videoSurfaceController ||
      !canIUse('SystemCapability.Window.SessionManager')) {
      return
    }
    try {
      const controller = await PiPWindow.create({
        context: getContext(this),
        componentController: this.videoSurfaceController,
        navigationId: Constants.NAVIGATION_ID,
        templateType: PiPWindow.PiPTemplateType.VIDEO_PLAY
      })
      this.pipController = controller
      controller.on('stateChange', (state: PiPWindow.PiPState, _reason: string): void => {
        this.onPipStateChanged(state)
      })
      controller.on('controlPanelActionEvent',
        (event: PiPWindow.PiPActionEventType, status?: number): void => this.onPipActionEvent(event, status))
    } catch (_error) {
      console.error('[MFPlayer] create picture-in-picture controller failed')
    }
  }

  private onPipStateChanged(state: PiPWindow.PiPState): void {
    switch (state) {
      case PiPWindow.PiPState.STARTED:
        this.pipActive = true
        this.controller.play()
        break
      case PiPWindow.PiPState.ABOUT_TO_STOP:
        this.controller.play()
        break
      case PiPWindow.PiPState.STOPPED:
        this.pipActive = false
        break
      default:
        break
    }
  }

  private onPipActionEvent(event: PiPWindow.PiPActionEventType, status?: number): void {
    if (event !== 'playbackStateChanged') {
      return
    }
    if (status === 0) {
      this.controller.pause()
    } else {
      this.controller.play()
    }
  }

  private async startPip(): Promise<void> {
    if (this.ui.isCastPlaying) {
      return
    }
    await this.ensurePipController()
    if (!this.pipController) {
      console.error('[MFPlayer] picture-in-picture surface is not ready')
      return
    }
    try {
      await this.pipController.startPiP()
    } catch (_error) {
      console.error('[MFPlayer] start picture-in-picture failed')
    }
  }

  private async stopPip(): Promise<void> {
    if (!this.pipController) {
      return
    }
    try {
      await this.pipController.stopPiP()
    } catch (_error) {
      console.error('[MFPlayer] stop picture-in-picture failed')
    }
    this.pipActive = false
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

  private mapExternalSubtitle(subtitle: MediaPlaybackExternalSubtitle): PlayerExternalSubtitle {
    return {
      uri: subtitle.deliveryUrl,
      title: subtitle.displayTitle,
      language: subtitle.language,
      headers: this.formatHeaders(subtitle.headers),
      selected: this.vm.mediaSourceOrNull?.selectedSubtitleStream?.Index === subtitle.index
    }
  }

  private createPlayerSource(source?: MediaPlaybackSource): PlayerSource {
    const externalSubtitles: PlayerExternalSubtitle[] = []
    if (source) {
      for (const subtitle of source.externalSubtitleStreams) {
        externalSubtitles.push(this.mapExternalSubtitle(subtitle))
      }
    }
    return {
      uri: this.videoUrl,
      headers: this.formatHeaders(this.ui.videoHeaders),
      externalSubtitles: externalSubtitles,
      dolbyVisionProfileHint: source?.selectedVideoStream?.DvProfile,
      hdrHint: this.isHdrSource(source)
    }
  }

  private normalizeTrackText(value?: string): string {
    return value?.trim().toLowerCase() ?? ''
  }

  private resolveNativeTrack(stream: MediaPlaybackStream | undefined, tracks: PlayerTrack[]): PlayerTrack | undefined {
    if (!stream) {
      return undefined
    }
    const isExternal = stream.IsExternal === true ||
      stream.DeliveryMethod === MediaPlaybackSubtitleDeliveryMethod.External
    const candidates = tracks.filter((track: PlayerTrack): boolean => track.isExternal === isExternal)
    if (candidates.length === 0) {
      return undefined
    }
    if (stream.Index !== undefined) {
      const indexedTrack = candidates.find((track: PlayerTrack): boolean => track.id === stream.Index)
      if (indexedTrack) {
        return indexedTrack
      }
    }
    const title = this.normalizeTrackText(stream.DisplayTitle ?? stream.Title)
    const language = this.normalizeTrackText(stream.Language)
    const codec = this.normalizeTrackText(stream.Codec)
    const hasTitle = (track: PlayerTrack): boolean =>
      title.length > 0 && this.normalizeTrackText(track.title) === title
    const hasLanguage = (track: PlayerTrack): boolean =>
      language.length > 0 && this.normalizeTrackText(track.language) === language
    const hasCodec = (track: PlayerTrack): boolean =>
      codec.length > 0 && this.normalizeTrackText(track.codec) === codec
    const exactTrack = candidates.find((track: PlayerTrack): boolean =>
      hasTitle(track) && (language.length === 0 || hasLanguage(track)) &&
      (codec.length === 0 || hasCodec(track)))
    if (exactTrack) {
      return exactTrack
    }
    const titledTrack = candidates.find((track: PlayerTrack): boolean => hasTitle(track))
    if (titledTrack) {
      return titledTrack
    }
    const languageAndCodecTrack = candidates.find((track: PlayerTrack): boolean =>
      hasLanguage(track) && hasCodec(track))
    if (languageAndCodecTrack) {
      return languageAndCodecTrack
    }
    const languageTrack = candidates.find((track: PlayerTrack): boolean => hasLanguage(track))
    if (languageTrack) {
      return languageTrack
    }
    return candidates.length === 1 ? candidates[0] : undefined
  }

  private syncNativeAudioTrack(tracks: PlayerTrack[] = this.controller.getAudioTracks()): void {
    const selectedTrack = this.resolveNativeTrack(this.vm.mediaSourceOrNull?.selectedAudioStream, tracks)
    if (!selectedTrack || this.controller.getSelectedAudioTrackId() === selectedTrack.id) {
      return
    }
    if (!this.controller.selectAudioTrack(selectedTrack.id)) {
      console.warn(`[MFPlayer] select audio track failed: ${selectedTrack.id}`)
    }
  }

  private syncNativeSubtitleTrack(tracks: PlayerTrack[] = this.controller.getSubtitleTracks()): void {
    const selectedTrack = this.resolveNativeTrack(this.vm.mediaSourceOrNull?.selectedSubtitleStream, tracks)
    if (!selectedTrack || this.controller.getSelectedSubtitleTrackId() === selectedTrack.id) {
      return
    }
    if (!this.controller.selectSubtitleTrack(selectedTrack.id)) {
      console.warn(`[MFPlayer] select subtitle track failed: ${selectedTrack.id}`)
    }
  }

  private syncNativeTrackSelections(audioTracks?: PlayerTrack[], subtitleTracks?: PlayerTrack[]): void {
    this.syncNativeAudioTrack(audioTracks)
    this.syncNativeSubtitleTrack(subtitleTracks)
  }

  private async selectAudioTrack(stream: MediaPlaybackStream): Promise<void> {
    const selected = await this.vm.selectAudioTrack(stream)
    if (selected) {
      this.syncNativeTrackSelections()
    }
  }

  private async selectSubtitleTrack(stream: MediaPlaybackStream): Promise<void> {
    const selected = await this.vm.selectSubtitleTrack(stream)
    if (selected) {
      this.syncNativeTrackSelections()
    }
  }

  private isHdrSource(source?: MediaPlaybackSource): boolean {
    const transfer = source?.selectedVideoStream?.ColorTransfer?.trim().toLowerCase() ?? ''
    return transfer.includes('smpte2084') || transfer.includes('arib-std-b67') ||
      transfer.includes('pq') || transfer.includes('hlg') ||
      (source?.selectedVideoStream?.DvProfile ?? 0) > 0
  }

  private createPlayerOptions(): PlayerOptions {
    const preference = AppPreference.getInstance()
    const directPlay: boolean = this.vm.mediaSourceOrNull?.getEffectivePlayMethod() ===
      MediaPlaybackPlayMethod.DirectPlay
    return {
      autoPlay: this.ui.playWhenReady,
      useHardwareDecode: preference.getMpvUseHardwareDecode(),
      cacheSizeMb: preference.getMpvCacheSizeMb(),
      preferSurface: true,
      startPositionMs: this.ui.initSeek,
      playbackSpeed: 1,
      progressIntervalMs: 500,
      exactSeek: false,
      /* 并行 Range 仅针对原始 Direct Play 文件；HLS/DirectStream/转码仍由播放器原路径处理。 */
      multiConnectionEnabled: directPlay,
      multiConnectionOptions: {
        connections: 12,
        // 代理按块返回数据；首块过大会让首帧等待整块下载完成。
        // 12 条长连接提高公网限速源的聚合吞吐，同时优先缩短首帧等待。
        blockSizeMb: 2,
        forwardWindowMb: 128
      }
    }
  }

  private loadCurrentSource(): void {
    const normalizedUrl = this.videoUrl.trim()
    if (normalizedUrl.length === 0 || normalizedUrl === this.loadedUrl) {
      return
    }
    this.loadedUrl = normalizedUrl
    const loaded = this.controller.load(
      this.createPlayerSource(this.vm.mediaSourceOrNull),
      this.createPlayerOptions()
    )
    if (loaded) {
      this.ui.initSeek = 0
      this.ui.CONTROL_PlayStatus = this.ui.playWhenReady ? PlayStatus.PLAY : PlayStatus.PAUSE
      this.ui.playerStatus = this.ui.playWhenReady ? PlayerStatus.PLAYING : PlayerStatus.PAUSED
    } else {
      console.error(`[MFPlayer] load rejected: ${normalizedUrl}`)
    }
  }

  private handlePlayerEvent(event: PlayerEvent): void {
    this.ui.playbackPositionMs = event.positionMs
    if (event.type === PlayerEventType.TRACKS_CHANGED) {
      this.syncNativeTrackSelections(event.audioTracks, event.subtitleTracks)
    }
    if (event.type === PlayerEventType.PROGRESS_CHANGED) {
      this.vm.reportPlaybackProgress(event.positionMs, !event.playWhenReady)
    }
    if (event.type === PlayerEventType.ERROR) {
      console.error(`[MFPlayer] playback error code=${event.errorCode}, native=${event.nativeErrorCode}, message=${event.errorMessage}`)
    }
    this.syncPlaybackState(event.state, event.playWhenReady)
  }

  private handlePlayerCommand(data: emitter.EventData): void {
    const eventName = data.data?.['eventName'] as string | undefined
    if (!eventName) {
      return
    }
    switch (eventName) {
      case 'Pause':
      case 'onPageHide':
        this.controller.pause()
        break
      case 'Play':
      case 'Resume':
      case 'onPageShow':
        this.controller.play()
        break
      case 'Stop':
      case 'Destroy':
        this.controller.stop()
        break
      case 'Seek': {
        const positionMs = data.data?.['args'] as number | undefined
        if (positionMs !== undefined) {
          this.controller.seekTo(positionMs, false)
        }
        break
      }
      case 'Speed': {
        const rawSpeed = data.data?.['args'] as string | undefined
        const speed = rawSpeed ? Number(rawSpeed.replace('f', '')) : Number.NaN
        if (!Number.isNaN(speed) && speed > 0) {
          this.controller.setPlaybackSpeed(speed)
        }
        break
      }
      default:
        break
    }
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
        this.vm.onNativePlayerReleased()
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

  build() {
    PlayerChrome({
      controller: this.controller,
      title: this.ui.title,
      isFullscreen: this.vm.appUIState.isFullScreen,
      safeTop: this.vm.appUIState.isFullScreen ? 0 : this.vm.appUIState.safeTop,
      safeBottom: this.vm.appUIState.isFullScreen ? 0 : this.vm.appUIState.safeBottom,
      nativeSurface: this.vm.appUIState.themeStyle === ThemeStyle.Native,
      systemMaterialAvailable: this.vm.appUIState.systemMaterialAvailable,
      qualityOptions: this.ui.qualityOptions,
      audioStreams: this.ui.audioStreams,
      subtitleStreams: this.ui.subtitleStreams,
      playList: this.ui.playList,
      selectedBitrate: this.ui.selectedBitrate ?? undefined,
      selectedAudioStream: this.ui.selectedAudioStream,
      selectedSubtitleStream: this.ui.selectedSubtitleStream,
      videoStream: this.vm.mediaSourceOrNull?.selectedVideoStream,
      currentPlayId: this.ui.currentPlayId,
      hasPrevious: this.vm.queueManager.hasPrevious(),
      hasNext: this.vm.queueManager.hasNext(),
      isCastPlaying: this.ui.isCastPlaying,
      menuDismissRequest: this.ui.playerMenuDismissRequest,
      playerMenus: this.ui.playerMenus,
      onVideoSurfaceControllerReady: (controller: XComponentController): void =>
        this.onVideoSurfaceControllerReady(controller),
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
      onControlsVisibleChanged: (visible: boolean): void => {
        this.ui.controlsVisible = visible
      },
      onPanelVisibilityChanged: (visible: boolean): void => {
        this.ui.playerMenuVisible = visible
      },
      onPrevious: (): void => {
        void this.vm.playPrevious()
      },
      onNext: (): void => {
        void this.vm.playNext()
      },
      onQualitySelected: (option: QualityOption): void => {
        void this.vm.changeBitrate(option.bitrate)
      },
      onAudioSelected: (stream): void => {
        void this.selectAudioTrack(stream)
      },
      onSubtitleSelected: (stream): void => {
        void this.selectSubtitleTrack(stream)
      },
      onEpisodeSelected: (index: number): void => {
        void this.vm.changeEpisode(index)
      },
      onStopCast: (): void => {
        void this.vm.stopCast()
      },
      onPictureInPicture: (): void => {
        void this.startPip()
      }
    })
  }
}
