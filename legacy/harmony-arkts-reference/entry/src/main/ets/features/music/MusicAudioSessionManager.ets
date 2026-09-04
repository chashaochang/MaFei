import { avSession } from '@kit.AVSessionKit'
import {
  MusicPlaybackSnapshot,
  MusicPlaybackStatus,
  MusicQueueItem,
  MusicRepeatMode
} from './MusicModels'

export interface MusicAudioSessionCallbacks {
  onPlay?: () => void
  onPause?: () => void
  onPrevious?: () => void
  onNext?: () => void
  onStop?: () => void
  onSeek?: (positionMs: number) => void
}

export class MusicAudioSessionManager {
  private session?: avSession.AVSession
  private initializationTask?: Promise<void>
  private active: boolean = false
  private callbacks: MusicAudioSessionCallbacks = {}

  private readonly playListener = (): void => this.callbacks.onPlay?.()
  private readonly pauseListener = (): void => this.callbacks.onPause?.()
  private readonly previousListener = (): void => this.callbacks.onPrevious?.()
  private readonly nextListener = (): void => this.callbacks.onNext?.()
  private readonly stopListener = (): void => this.callbacks.onStop?.()
  private readonly seekListener = (positionMs: number): void => this.callbacks.onSeek?.(positionMs)

  setCallbacks(callbacks: MusicAudioSessionCallbacks): void {
    this.callbacks = callbacks
  }

  async initialize(): Promise<void> {
    if (this.session) {
      return
    }
    if (this.initializationTask) {
      await this.initializationTask
      return
    }

    const task = this.createSession()
    this.initializationTask = task
    try {
      await task
    } finally {
      if (this.initializationTask === task) {
        this.initializationTask = undefined
      }
    }
  }

  async activate(): Promise<void> {
    await this.initialize()
    if (!this.session || this.active) {
      return
    }
    await this.session.activate()
    this.active = true
  }

  async updateMetadata(item: MusicQueueItem): Promise<void> {
    await this.initialize()
    if (!this.session) {
      return
    }

    const metadata: avSession.AVMetadata = {
      assetId: item.id,
      title: item.title,
      artist: item.artist,
      album: item.album,
      duration: Math.max(0, item.durationMs)
    }
    if (item.imageUrl.length > 0) {
      metadata.mediaImage = item.imageUrl
    }
    await this.session.setAVMetadata(metadata)
  }

  async updatePlaybackState(snapshot: MusicPlaybackSnapshot): Promise<void> {
    await this.initialize()
    if (!this.session) {
      return
    }

    const playbackState: avSession.AVPlaybackState = {
      state: this.toSessionPlaybackState(snapshot.status),
      speed: 1,
      position: {
        elapsedTime: this.clampPosition(snapshot.positionMs, snapshot.durationMs),
        updateTime: Date.now()
      },
      loopMode: this.toSessionLoopMode(snapshot.repeatMode)
    }
    const currentItem = snapshot.queue[snapshot.currentIndex]
    if (currentItem) {
      playbackState.isFavorite = currentItem.favorite
    }
    await this.session.setAVPlaybackState(playbackState)
  }

  async deactivate(): Promise<void> {
    if (!this.session || !this.active) {
      return
    }
    await this.session.deactivate()
    this.active = false
  }

  async destroy(): Promise<void> {
    if (this.initializationTask) {
      try {
        await this.initializationTask
      } catch (_) {
        return
      }
    }

    const session = this.session
    if (!session) {
      return
    }
    this.removeListeners(session)
    if (this.active) {
      await session.deactivate().catch(() => {})
    }
    try {
      await session.destroy()
    } finally {
      if (this.session === session) {
        this.session = undefined
      }
      this.active = false
    }
  }

  private async createSession(): Promise<void> {
    const session = await avSession.createAVSession(getContext(this), 'MaFei Music', 'audio')
    try {
      this.registerListeners(session)
      this.session = session
    } catch (error) {
      await session.destroy()
      throw error as Error
    }
  }

  private registerListeners(session: avSession.AVSession): void {
    session.on('play', this.playListener)
    session.on('pause', this.pauseListener)
    session.on('playPrevious', this.previousListener)
    session.on('playNext', this.nextListener)
    session.on('stop', this.stopListener)
    session.on('seek', this.seekListener)
  }

  private removeListeners(session: avSession.AVSession): void {
    session.off('play', this.playListener)
    session.off('pause', this.pauseListener)
    session.off('playPrevious', this.previousListener)
    session.off('playNext', this.nextListener)
    session.off('stop', this.stopListener)
    session.off('seek', this.seekListener)
  }

  private toSessionPlaybackState(status: MusicPlaybackStatus): avSession.PlaybackState {
    switch (status) {
      case MusicPlaybackStatus.Preparing:
        return avSession.PlaybackState.PLAYBACK_STATE_PREPARE
      case MusicPlaybackStatus.Playing:
        return avSession.PlaybackState.PLAYBACK_STATE_PLAY
      case MusicPlaybackStatus.Paused:
        return avSession.PlaybackState.PLAYBACK_STATE_PAUSE
      case MusicPlaybackStatus.Buffering:
        return avSession.PlaybackState.PLAYBACK_STATE_BUFFERING
      case MusicPlaybackStatus.Completed:
        return avSession.PlaybackState.PLAYBACK_STATE_COMPLETED
      case MusicPlaybackStatus.Error:
        return avSession.PlaybackState.PLAYBACK_STATE_ERROR
      case MusicPlaybackStatus.Idle:
      default:
        return avSession.PlaybackState.PLAYBACK_STATE_IDLE
    }
  }

  private toSessionLoopMode(repeatMode: MusicRepeatMode): avSession.LoopMode {
    switch (repeatMode) {
      case MusicRepeatMode.One:
        return avSession.LoopMode.LOOP_MODE_SINGLE
      case MusicRepeatMode.All:
        return avSession.LoopMode.LOOP_MODE_LIST
      case MusicRepeatMode.Off:
      default:
        return avSession.LoopMode.LOOP_MODE_SEQUENCE
    }
  }

  private clampPosition(positionMs: number, durationMs: number): number {
    const safePosition = Math.max(0, positionMs)
    return durationMs > 0 ? Math.min(safePosition, durationMs) : safePosition
  }
}

const musicAudioSessionManager = new MusicAudioSessionManager()

export default musicAudioSessionManager
