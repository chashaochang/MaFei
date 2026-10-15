import { MediaPlaybackSource } from './MediaPlaybackModels'

export interface MediaPlaybackProgress {
  readonly positionTicks: number
  readonly paused: boolean
  readonly muted: boolean
}

export interface MediaPlaybackStop {
  readonly positionTicks: number
  readonly failed: boolean
}

/** Provider-owned playback reporting and transcode cleanup callbacks. */
export interface MediaPlaybackLifecycle {
  reportStart(source: MediaPlaybackSource): Promise<void>
  reportProgress(source: MediaPlaybackSource, progress: MediaPlaybackProgress): Promise<void>
  reportStop(source: MediaPlaybackSource, stop: MediaPlaybackStop): Promise<void>
  reportCompleted(source: MediaPlaybackSource): Promise<void>
  closeTranscode(source: MediaPlaybackSource): Promise<void>
}
