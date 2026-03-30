import {
  command,
  create,
  destroy,
  getAudioTracks,
  getDecodeType,
  getCurrentPosition,
  getCurrentAudioTrack,
  getCurrentSubtitleTrack,
  getCacheDuration,
  getDuration,
  getHardwareDecoder,
  getHwdecMode,
  getSubtitleTracks,
  isDestroying,
  getVideoHeight,
  getVideoWidth,
  isInitialized,
  loadVideo,
  pause,
  play,
  seek,
  selectAudio,
  selectSubtitle,
  setSpeed,
  setAutoBufferFallback,
  setCacheSize,
  setDecodeType,
  setHwdecMode,
  setLogLevel,
  setOsdSurface,
  setOnDurationChangedCallback,
  setOnEndFileCallback,
  setOnFileLoadedCallback,
  setOnHwdecChangedCallback,
  setOnInitializedCallback,
  setOnLoadingCallback,
  setOnLogMessageCallback,
  setOnTrackListChangedCallback,
  setOnStartFileCallback,
  type MpvTrackInfo
} from 'libmpvnative.so'

const HWDEC_MODE_BUFFER: number = 0
const HWDEC_MODE_SURFACE: number = 1
const DECODE_TYPE_HW: number = 0
const DECODE_TYPE_SW: number = 1
const CREATE_RETRY_DELAY_MS: number = 50

export interface MpvNativeListener {
  onInitialized?: () => void
  onEndFile?: (reason: number, error: string) => void
  onDurationChanged?: (durationMs: number) => void
  onLoadingChanged?: (loading: boolean) => void
  onStartFile?: () => void
  onFileLoaded?: () => void
  onTrackListChanged?: () => void
  onHwdecChanged?: (decoder: string) => void
  onLogMessage?: (level: string, prefix: string, text: string) => void
}

export class MpvNativePlayer {
  private handle: number = 0
  private listeners: MpvNativeListener = {}
  private createRetryTimer: number = -1
  private initializePending: boolean = false
  private released: boolean = false

  setListeners(listeners: MpvNativeListener): void {
    this.listeners = listeners
  }

  configurePlayback(useHardwareDecode: boolean, cacheSizeMb: number, preferSurface: boolean = true): void {
    const useSurfacePlayback = preferSurface && useHardwareDecode
    setHwdecMode(useSurfacePlayback ? HWDEC_MODE_SURFACE : HWDEC_MODE_BUFFER)
    setDecodeType(useHardwareDecode ? DECODE_TYPE_HW : DECODE_TYPE_SW)
    setCacheSize(cacheSizeMb)
    setAutoBufferFallback(true)
    setLogLevel('warn')
  }

  setOsdSurface(surfaceId: string, width: number, height: number): void {
    setOsdSurface(surfaceId, Math.max(0, Math.round(width)), Math.max(0, Math.round(height)))
  }

  clearOsdSurface(): void {
    setOsdSurface('0', 0, 0)
  }

  initialize(): boolean {
    if (this.handle > 0 || this.initializePending) {
      return true
    }
    this.released = false
    this.initializePending = true
    setOnInitializedCallback(() => {
      if (this.listeners.onInitialized) {
        this.listeners.onInitialized()
      }
    })
    setOnEndFileCallback((reason: number, error: string) => {
      if (this.listeners.onEndFile) {
        this.listeners.onEndFile(reason, error)
      }
    })
    setOnDurationChangedCallback((durationMs: number) => {
      if (this.listeners.onDurationChanged) {
        this.listeners.onDurationChanged(durationMs)
      }
    })
    setOnLoadingCallback((loading: boolean) => {
      if (this.listeners.onLoadingChanged) {
        this.listeners.onLoadingChanged(loading)
      }
    })
    setOnStartFileCallback(() => {
      if (this.listeners.onStartFile) {
        this.listeners.onStartFile()
      }
    })
    setOnFileLoadedCallback(() => {
      if (this.listeners.onFileLoaded) {
        this.listeners.onFileLoaded()
      }
    })
    setOnTrackListChangedCallback(() => {
      if (this.listeners.onTrackListChanged) {
        this.listeners.onTrackListChanged()
      }
    })
    setOnHwdecChangedCallback((decoder: string) => {
      if (this.listeners.onHwdecChanged) {
        this.listeners.onHwdecChanged(decoder)
      }
    })
    setOnLogMessageCallback((level: string, prefix: string, text: string) => {
      if (this.listeners.onLogMessage) {
        this.listeners.onLogMessage(level, prefix, text)
      }
    })
    return this.tryCreate()
  }

  private tryCreate(): boolean {
    if (this.released) {
      this.initializePending = false
      return false
    }
    const result: number | null = create()
    if (result !== null && result > 0) {
      this.handle = result
      this.initializePending = false
      return true
    }
    if (isDestroying()) {
      this.scheduleCreateRetry()
      return true
    }
    this.initializePending = false
    this.handle = 0
    return false
  }

  private scheduleCreateRetry(): void {
    if (this.createRetryTimer >= 0 || this.released || this.handle > 0) {
      return
    }
    this.createRetryTimer = setTimeout(() => {
      this.createRetryTimer = -1
      if (this.released || this.handle > 0) {
        return
      }
      if (!this.tryCreate()) {
        console.error('[MPV] create failed after pending destroy finished')
      }
    }, CREATE_RETRY_DELAY_MS)
  }

  private stopCreateRetry(): void {
    if (this.createRetryTimer >= 0) {
      clearTimeout(this.createRetryTimer)
      this.createRetryTimer = -1
    }
  }

  isReady(): boolean {
    return this.handle > 0 && isInitialized()
  }

  getHandle(): number {
    return this.handle
  }

  load(url: string, startPositionSeconds: number = 0): void {
    if (this.handle <= 0 || url.length === 0) {
      return
    }
    if (startPositionSeconds > 0) {
      loadVideo(this.handle, url, startPositionSeconds)
    } else {
      loadVideo(this.handle, url)
    }
  }

  play(): void {
    if (this.handle > 0) {
      play(this.handle)
    }
  }

  pause(): void {
    if (this.handle > 0) {
      pause(this.handle)
    }
  }

  seekToMs(positionMs: number, exact: boolean = false): void {
    if (this.handle > 0) {
      seek(this.handle, positionMs / 1000, exact)
    }
  }

  setPlaybackSpeed(speed: number): void {
    if (this.handle > 0 && speed > 0) {
      setSpeed(this.handle, speed)
    }
  }

  runCommand(args: string[]): void {
    if (this.handle > 0 && args.length > 0) {
      command(this.handle, args)
    }
  }

  getAudioTrackList(): MpvTrackInfo[] {
    if (this.handle <= 0) {
      return []
    }
    return getAudioTracks(this.handle)
  }

  getSubtitleTrackList(): MpvTrackInfo[] {
    if (this.handle <= 0) {
      return []
    }
    return getSubtitleTracks(this.handle)
  }

  selectAudioTrack(trackId: number): void {
    if (this.handle > 0 && trackId >= 0) {
      selectAudio(this.handle, trackId)
    }
  }

  selectSubtitleTrack(trackId: number): void {
    if (this.handle > 0) {
      selectSubtitle(this.handle, trackId)
    }
  }

  getCurrentAudioTrackId(): number {
    if (this.handle <= 0) {
      return -1
    }
    return getCurrentAudioTrack(this.handle)
  }

  getCurrentSubtitleTrackId(): number {
    if (this.handle <= 0) {
      return -1
    }
    return getCurrentSubtitleTrack(this.handle)
  }

  getCurrentPositionMs(): number {
    if (this.handle <= 0) {
      return -1
    }
    return getCurrentPosition(this.handle)
  }

  getDurationMs(): number {
    if (this.handle <= 0) {
      return 0
    }
    return getDuration(this.handle)
  }

  getCacheDurationMs(): number {
    if (this.handle <= 0) {
      return 0
    }
    return getCacheDuration(this.handle)
  }

  getVideoWidth(): number {
    if (this.handle <= 0) {
      return 0
    }
    return getVideoWidth(this.handle)
  }

  getVideoHeight(): number {
    if (this.handle <= 0) {
      return 0
    }
    return getVideoHeight(this.handle)
  }

  getHardwareDecoder(): string {
    if (this.handle <= 0) {
      return ''
    }
    return getHardwareDecoder(this.handle)
  }

  getCurrentModeLabel(): string {
    const mode = getHwdecMode()
    const decode = getDecodeType()
    if (mode === HWDEC_MODE_BUFFER) {
      return decode === DECODE_TYPE_SW ? 'buffer-sw' : 'buffer-hw'
    }
    return 'surface-hw'
  }

  release(): void {
    this.released = true
    this.initializePending = false
    this.stopCreateRetry()
    if (this.handle > 0) {
      destroy(this.handle)
    }
    this.handle = 0
  }
}
