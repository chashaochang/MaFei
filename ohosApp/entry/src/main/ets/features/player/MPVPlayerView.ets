import { BaseItemKind, MediaStream, PlayMethod, SubtitleDeliveryMethod } from 'sdk/src/main/ets/generated-client/models'
import { QualityOption } from '../../player/qualityoptions/QualityOption'
import { PlayerPageUIState } from './PlayerPageUIState'
import { PlayerPageViewModel } from './PlayerPageViewModel'
import { PlayStatus, PlayerStatus } from './PlayStatus'
import { MpvNativePlayer } from 'mpvplayer'
import { AppPreference } from '../../data/AppPreference'
import { BusinessError, emitter } from '@kit.BasicServicesKit'
import { Constants } from '../../utils/Constants'
import { avSession } from '@kit.AVSessionKit'
import avSessionManager from '../../utils/AVSessionManager'
import { common } from '@kit.AbilityKit'
import { SmartDialog } from 'ohos_smart_dialog'
import { QueueItem } from '../../entity/QueueItem'
import { JellyfinMediaSource } from '../../player/source/JellyfinMediaSource'
import { ExternalSubtitleStream } from '../../player/source/ExternalSubtitleStream'

const PROGRESS_MAX_VALUE: number = 100
const MPV_XCOMPONENT_ID: string = 'mpvPlayerSurface'
const MPV_OSD_XCOMPONENT_ID: string = 'mpvPlayerOsdSurface'
const SUBTITLE_SELECTION_RETRY_DELAYS_MS: number[] = [150, 300, 600, 1000, 1500]
const SUBTITLE_SELECTION_BACKGROUND_RETRY_MS: number = 2000

@Extend(Stack)
function menuItemStyle() {
  .borderRadius(8)
  .backgroundColor('rgba(255,255,255,0.2)')
  .width('100%')
  .layoutWeight(1)
}

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

  private xComponentController: XComponentController = new XComponentController()
  private osdXComponentController: XComponentController = new XComponentController()
  private mpvPlayer: MpvNativePlayer = new MpvNativePlayer()
  private progressTimer: number = -1
  private diagnosticsTimer: number = -1
  private initializeTimer: number = -1
  private subtitleBootstrapTimer: number = -1
  private sourceLoaded: boolean = false
  private xComponentReady: boolean = false
  private osdXComponentReady: boolean = false
  private initializeRequested: boolean = false
  private shouldResumeOnPageShow: boolean = false
  private pendingSubtitleSelection?: MediaStream
  private pendingSubtitleSelectionAllowFallback: boolean = false
  private pendingSubtitleSelectionWaitingLogged: boolean = false
  private loadedExternalSubtitleUrls: string[] = []
  private osdSurfaceId: string = '0'
  private osdSurfaceWidthPx: number = 0
  private osdSurfaceHeightPx: number = 0
  private appliedOsdSurfaceKey: string = ''
  private videoParentAspectRatio: number = 16 / 9

  @Monitor('ui.playerStatus')
  onPlayerStateChanged(): void {
    this.sendAVSessionState(this.ui.playerStatus)
  }

  @Monitor('videoUrl')
  onUrlChange(): void {
    this.sourceLoaded = false
    this.playbackCompleted = false
    this.currentPosition = 0
    this.progressValue = 0
    this.cacheProgressValue = 0
    this.currentTime = '00:00'
    this.loadedExternalSubtitleUrls = []
    this.clearPendingSubtitleSelection(false)
    if (this.initialized) {
      this.loadCurrentVideo()
    }
  }

  aboutToAppear(): void {
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
    emitter.off(Constants.PLAYER_EVENT.eventId)
    let context = getContext(this) as common.UIAbilityContext
    context.eventHub.off('onForeground')
    SmartDialog.dismiss()
    this.stopInitializeTimer()
    this.stopProgressTimer()
    this.stopDiagnosticsTimer()
    this.stopSubtitleBootstrapTimer()
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
    this.clearPendingSubtitleSelection(false)
    this.ui.playbackPositionMs = 0
    this.cacheProgressValue = 0
  }

  private shouldUseSurfacePlayback(): boolean {
    return !this.vm.mediaSourceOrNull?.isDolbyVisionStream()
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
    }
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
        this.mpvPlayer.setPlaybackSpeed(this.playSpeed)
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
        this.updateVideoLayoutFromPlayer()
        this.bootstrapTracksForCurrentSource()
        if (!this.ui.playWhenReady) {
          this.pausePlayback(false)
        }
        this.logVideoDiagnostics('file-loaded')
        this.scheduleDiagnostics('delayed-file-loaded', 1500)
      },
      onTrackListChanged: () => {
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
    const useHardwareDecode = AppPreference.getInstance().getMpvUseHardwareDecode()
    const cacheSizeMb = AppPreference.getInstance().getMpvCacheSizeMb()
    const source = this.vm.mediaSourceOrNull
    const preferSurface = !source?.isDolbyVisionStream()
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
    this.sourceLoaded = true
    this.playbackCompleted = false
    this.loading = true
    this.cacheProgressValue = 0
    this.ui.CONTROL_PlayStatus = this.ui.playWhenReady ? PlayStatus.PLAY : PlayStatus.PAUSE
    this.ui.playerStatus = this.ui.playWhenReady ? PlayerStatus.PLAYING : PlayerStatus.PAUSED
    const startPositionSeconds = this.ui.initSeek > 0 ? this.ui.initSeek / 1000 : 0
    this.logMpv(`load video with mode=${this.mpvPlayer.getCurrentModeLabel()}`)
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
        this.mpvPlayer.seekToMs(0, true)
        this.playbackCompleted = false
      }
      this.resumePlayback()
    }
  }

  private onSeek(value: number): void {
    if (!this.initialized || this.duration <= 0) {
      return
    }
    const seekMs = (value / PROGRESS_MAX_VALUE) * this.duration
    this.seekTo(seekMs)
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
        this.ui.playbackPositionMs = position
        this.currentTime = this.formatTime(position)
        if (this.duration > 0) {
          this.progressValue = Math.min(PROGRESS_MAX_VALUE, (position / this.duration) * PROGRESS_MAX_VALUE)
          const cacheDuration = Math.max(0, this.mpvPlayer.getCacheDurationMs())
          const bufferedPosition = Math.min(this.duration, position + cacheDuration)
          this.cacheProgressValue = Math.min(PROGRESS_MAX_VALUE, (bufferedPosition / this.duration) * PROGRESS_MAX_VALUE)
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
    if (rememberResume) {
      this.shouldResumeOnPageShow = true
    }
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

  private seekTo(positionMs: number): void {
    if (!this.initialized || this.duration <= 0) {
      return
    }
    this.mpvPlayer.seekToMs(positionMs, false)
    this.currentPosition = positionMs
    this.ui.playbackPositionMs = positionMs
    this.currentTime = this.formatTime(positionMs)
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

  private loadExternalSubtitles(source: JellyfinMediaSource | undefined): void {
    if (!source) {
      return
    }
    source.externalSubtitleStreams.forEach((stream) => {
      this.ensureExternalSubtitleLoaded(stream)
    })
  }

  private ensureExternalSubtitleLoaded(stream: ExternalSubtitleStream): void {
    if (this.loadedExternalSubtitleUrls.includes(stream.deliveryUrl)) {
      return
    }
    this.loadedExternalSubtitleUrls.push(stream.deliveryUrl)
    this.mpvPlayer.runCommand([
      'sub-add',
      stream.deliveryUrl,
      'auto',
      stream.displayTitle.length > 0 ? stream.displayTitle : `external-${stream.index}`,
      stream.language
    ])
    this.logMpv(`queued external subtitle: ${stream.displayTitle || stream.language || stream.deliveryUrl}`)
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

  private resolveInitialSubtitleStream(source: JellyfinMediaSource): MediaStream | undefined {
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

  private findExternalSubtitleSource(stream: MediaStream): ExternalSubtitleStream | undefined {
    const source = this.vm.mediaSourceOrNull
    if (!source || stream.Index === undefined || stream.Index === null) {
      return undefined
    }
    return source.externalSubtitleStreams.find((item) => item.index === stream.Index)
  }

  private hasSubtitleSelectionInCurrentUrl(stream: MediaStream): boolean {
    if (stream.Index === undefined || stream.Index === null) {
      return false
    }
    const subtitleIndex = `${stream.Index}`
    return this.videoUrl.includes(`SubtitleStreamIndex=${subtitleIndex}`) ||
      this.videoUrl.includes(`subtitleStreamIndex=${subtitleIndex}`)
  }

  private requiresSubtitleRestart(stream: MediaStream): boolean {
    const source = this.vm.mediaSourceOrNull
    if (!source) {
      return false
    }
    return source.playMethod == PlayMethod.Transcode ||
      stream.DeliveryMethod == SubtitleDeliveryMethod.Encode
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

  private findTrackIdByStream(stream: MediaStream, isSubtitle: boolean): number {
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
        .filter((item: MediaStream) => (item.IsExternal === true) === expectedExternal)
        .findIndex((item: MediaStream) => item === stream)
      if (embeddedIndex >= 0 && embeddedIndex < filteredTracks.length) {
        return filteredTracks[embeddedIndex].id
      }
    }

    if (filteredTracks.length === 1) {
      return filteredTracks[0].id
    }

    return -1
  }

  private selectAudioTrack(stream: MediaStream, allowFallback: boolean = true): boolean {
    const source = this.vm.mediaSourceOrNull
    if (!source || source.playMethod == PlayMethod.Transcode || stream.IsExternal === true) {
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

  private queuePendingSubtitleSelection(stream: MediaStream, allowFallback: boolean): boolean {
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

  private trySelectSubtitleTrackDirect(stream: MediaStream): boolean {
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

  private selectSubtitleTrack(stream: MediaStream, allowFallback: boolean = true): boolean {
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
            Column() {
              Text(`第 ${item.playIndex} 集  ${item.name == `第 ${item.playIndex} 集` ? '' : item.name}`)
                .fontColor(_this.ui.currentPlayId == item.id ? $r('app.color.color_main') : Color.White)
                .fontSize(14)
                .maxLines(1)
                .ellipsisMode(EllipsisMode.END)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
            }
            .width('100%')
            .height(40)
            .alignItems(HorizontalAlign.Start)
            .justifyContent(FlexAlign.Center)
            .padding(12)
            .borderWidth(1)
            .borderColor(_this.ui.currentPlayId == item.id ? $r('app.color.color_main') : Color.Gray)
            .borderRadius(8)
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
    .width(300)
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
  private audioMenu(): void {
    Menu() {
      ForEach(this.ui.audioStreams, (item: MediaStream) => {
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
  }

  @Builder
  private subtitleMenu(): void {
    Menu() {
      ForEach(this.ui.subtitleStreams, (item: MediaStream) => {
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
  }

  @Builder
  private qualityMenu(): void {
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
        Stack() {
          Progress({
            value: this.cacheProgressValue,
            total: PROGRESS_MAX_VALUE,
            type: ProgressType.Linear
          })
            .height(3)
            .width('100%')
            .color('#80FFFFFF')

          Slider({
            value: this.progressValue,
            min: 0,
            max: PROGRESS_MAX_VALUE,
            step: 1,
            style: SliderStyle.OutSet
          })
            .width('100%')
            .blockColor(Color.White)
            .trackColor('#4DFFFFFF')
            .selectedColor($r('app.color.color_main'))
            .onChange((value: number) => {
              this.onSeek(value)
            })
        }
          .layoutWeight(1)
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
          }
        })
      }
      .justifyContent(FlexAlign.Center)
      .width('100%')

      Row() {
        Blank()
          .layoutWeight(1)

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

        Text(`倍速 ${this.formatSpeedLabel(this.playSpeed)}X`)
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
      }
      .height(36)
      .width('100%')
    }
    .width('100%')
    .padding({ left: 8, right: 8, top: 8, bottom: 16 })
    .backgroundColor('#66000000')
  }

  build() {
    Stack({ alignContent: Alignment.Bottom }) {
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
              this.showControls = !this.showControls
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
              .hitTestBehavior(HitTestMode.None)
          }
        }
        .width(this.videoWidth)
        .aspectRatio(this.videoAspectRatio)
        .onAreaChange((_oldValue: Area, newValue: Area) => {
          const width = newValue.width as number
          const height = newValue.height as number
          if (width > 0 && height > 0) {
            this.videoParentAspectRatio = width / height
          }
          this.updateOsdSurfaceSize(
            this.getUIContext().vp2px(width),
            this.getUIContext().vp2px(height)
          )
          this.updateVideoLayoutFromPlayer()
        })
      }
      .aspectRatio(this.videoAspectRatio)

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

      Text(this.ui.playerMenus?.playBackInfo)
        .backgroundColor($r('sys.color.mask_primary'))
        .borderRadius(16)
        .fontColor(Color.White)
        .fontSize(12)
        .position({ x: 0, y: 0 })
        .padding(16)
        .visibility(this.ui.playerMenus?.isShowPlayBackInfo ? Visibility.Visible : Visibility.None)
        .onClick(() => {
          if (this.ui.playerMenus) {
            this.ui.playerMenus.isShowPlayBackInfo = false
          }
        })
        .margin({
          top: 48,
          bottom: 96,
          left: 12,
          right: 12
        })

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
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Color.Black)
  }
}
