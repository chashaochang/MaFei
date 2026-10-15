export interface MusicPlayerEngineListener {
  onPrepared(generation: number, durationMs: number): void
  onPlaying(generation: number): void
  onPaused(generation: number): void
  onBuffering(generation: number): void
  onCompleted(generation: number): void
  onStopped(generation: number): void
  onTimeUpdate(generation: number, positionMs: number): void
  onSeekDone(generation: number, positionMs: number): void
  onError(generation: number, message: string): void
}

export interface MusicPlayerEngine {
  prepare(url: string, generation: number, listener: MusicPlayerEngineListener): Promise<void>
  stagePromotion(generation: number): number
  confirmPromotion(promotionId: number): void
  rollbackPromotion(promotionId: number): void
  discard(generation: number): Promise<void>
  play(): Promise<void>
  pause(): Promise<void>
  stop(): Promise<void>
  seek(positionMs: number): Promise<void>
  release(): Promise<void>
}
