import {
  create,
  destroy,
  getDecodeType,
  getCurrentPosition,
  getDuration,
  getHardwareDecoder,
  getHwdecMode,
  getVideoHeight,
  getVideoWidth,
  isInitialized,
  loadVideo,
  pause,
  play,
  seek,
  setAutoBufferFallback,
  setCacheSize,
  setDecodeType,
  setHwdecMode,
  setLogLevel,
  setOnDurationChangedCallback,
  setOnEndFileCallback,
  setOnFileLoadedCallback,
  setOnHwdecChangedCallback,
  setOnInitializedCallback,
  setOnLoadingCallback,
  setOnLogMessageCallback,
  setOnStartFileCallback
} from 'libmpvnative.so'

const HWDEC_MODE_BUFFER: number = 0
const DECODE_TYPE_HW: number = 0
const DECODE_TYPE_SW: number = 1

export interface MpvNativeListener {
  onInitialized?: () => void
  onEndFile?: (reason: number, error: string) => void
  onDurationChanged?: (durationMs: number) => void
  onLoadingChanged?: (loading: boolean) => void
  onStartFile?: () => void
  onFileLoaded?: () => void
  onHwdecChanged?: (decoder: string) => void
  onLogMessage?: (level: string, prefix: string, text: string) => void
}

export class MpvNativePlayer {
  private handle: number = 0
  private listeners: MpvNativeListener = {}

  setListeners(listeners: MpvNativeListener): void {
    this.listeners = listeners
  }

  configurePlayback(useHardwareDecode: boolean, cacheSizeMb: number): void {
    setHwdecMode(HWDEC_MODE_BUFFER)
    setDecodeType(useHardwareDecode ? DECODE_TYPE_HW : DECODE_TYPE_SW)
    setCacheSize(cacheSizeMb)
    setAutoBufferFallback(true)
    setLogLevel('info')
  }

  initialize(): boolean {
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
    const result: number | null = create()
    if (result !== null && result > 0) {
      this.handle = result
      return true
    }
    this.handle = 0
    return false
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

  getCurrentPositionMs(): number {
    if (this.handle <= 0) {
      return 0
    }
    return getCurrentPosition(this.handle)
  }

  getDurationMs(): number {
    if (this.handle <= 0) {
      return 0
    }
    return getDuration(this.handle)
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
    if (this.handle > 0) {
      destroy(this.handle)
    }
    this.handle = 0
  }
}
