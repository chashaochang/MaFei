import {
  MusicPlaybackCandidateStatus,
  MusicPlaybackErrorCode,
  MusicPlaybackSnapshot,
  MusicPlaybackStatus,
  MusicQueueItem,
  MusicRepeatMode,
  MusicResolvedSource
} from './MusicModels'
import { MusicQueueSnapshot } from './MusicQueuePolicy'

export class MusicPlaybackMachine {
  private current: MusicPlaybackSnapshot
  private requestGeneration: number = 0
  private activeGeneration: number = 0
  private pendingQueue?: MusicQueueSnapshot
  private pendingIndex: number = -1
  private failedQueue?: MusicQueueSnapshot
  private failedIndex: number = -1

  constructor(initial: MusicPlaybackSnapshot) {
    this.current = MusicPlaybackMachine.copySnapshot(initial)
  }

  snapshot(): MusicPlaybackSnapshot {
    return MusicPlaybackMachine.copySnapshot(this.current)
  }

  beginQueueCandidate(queue: MusicQueueSnapshot): number {
    return this.beginTransition(queue, queue.currentIndex)
  }

  beginTransition(queue: MusicQueueSnapshot, index: number): number {
    this.requestGeneration += 1
    this.pendingQueue = MusicPlaybackMachine.copyQueue(queue)
    this.pendingIndex = index
    this.clearFailed()
    this.setCandidate(index >= 0 && index < queue.items.length ? queue.items[index].id : '',
      MusicPlaybackCandidateStatus.Preparing)
    if (!this.preservesActivePlayback()) {
      this.current.status = MusicPlaybackStatus.Preparing
      this.current.errorCode = MusicPlaybackErrorCode.None
      this.current.errorMessage = ''
    }
    return this.requestGeneration
  }

  commitPreparation(generation: number, source: MusicResolvedSource): boolean {
    if (!this.acceptsRequest(generation) || !this.pendingQueue ||
      this.pendingIndex < 0 || this.pendingIndex >= this.pendingQueue.items.length) {
      return false
    }
    this.current.queue = this.pendingQueue.items.slice()
    this.current.currentIndex = this.pendingIndex
    this.current.activeScope = this.pendingQueue.scope
    this.current.source = MusicPlaybackMachine.copySource(source)
    this.current.positionMs = Math.max(0, source.startPositionMs)
    this.current.durationMs = Math.max(0, source.durationMs)
    this.current.status = MusicPlaybackStatus.Preparing
    this.current.errorCode = MusicPlaybackErrorCode.None
    this.current.errorMessage = ''
    this.activeGeneration = generation
    this.clearPending()
    this.clearFailed()
    this.clearCandidate()
    return true
  }

  failPreparation(generation: number, errorCode: MusicPlaybackErrorCode, message: string = ''): boolean {
    if (!this.acceptsRequest(generation)) {
      return false
    }
    if (this.pendingQueue && this.pendingIndex >= 0 && this.pendingIndex < this.pendingQueue.items.length) {
      this.failedQueue = MusicPlaybackMachine.copyQueue(this.pendingQueue)
      this.failedIndex = this.pendingIndex
      this.setCandidate(this.pendingQueue.items[this.pendingIndex].id,
        MusicPlaybackCandidateStatus.Error, errorCode, message)
      if (!this.preservesActivePlayback()) {
        this.current.status = MusicPlaybackStatus.Error
        this.current.errorCode = errorCode
        this.current.errorMessage = message
        this.current.queue = [MusicPlaybackMachine.copyItem(this.pendingQueue.items[this.pendingIndex])]
        this.current.currentIndex = 0
        this.current.activeScope = this.pendingQueue.scope
        this.current.positionMs = 0
        this.current.durationMs = this.pendingQueue.items[this.pendingIndex].durationMs
      }
    } else if (!this.failedQueue) {
      this.clearFailed()
      if (!this.preservesActivePlayback()) {
        this.current.status = MusicPlaybackStatus.Error
        this.current.errorCode = errorCode
        this.current.errorMessage = message
      }
    }
    this.clearPending()
    return true
  }

  failedCandidate(): MusicQueueSnapshot | undefined {
    if (!this.failedQueue || this.failedIndex < 0 || this.failedIndex >= this.failedQueue.items.length) {
      return undefined
    }
    const queue = MusicPlaybackMachine.copyQueue(this.failedQueue)
    queue.currentIndex = this.failedIndex
    return queue
  }

  isPendingGeneration(generation: number): boolean {
    return this.acceptsRequest(generation) && this.pendingQueue !== undefined
  }

  cancelPending(): number {
    const generation = this.requestGeneration
    this.requestGeneration += 1
    this.clearPending()
    this.clearFailed()
    this.clearCandidate()
    if (!this.hasActiveSource()) {
      this.current.status = MusicPlaybackStatus.Idle
      this.current.errorCode = MusicPlaybackErrorCode.None
      this.current.errorMessage = ''
    }
    return generation
  }

  hasCandidateTransition(): boolean {
    return this.current.candidateStatus !== undefined &&
      this.current.candidateStatus !== MusicPlaybackCandidateStatus.None
  }

  failActive(errorCode: MusicPlaybackErrorCode, message: string = ''): void {
    this.requestGeneration += 1
    this.clearPending()
    this.clearFailed()
    this.clearCandidate()
    this.current.status = MusicPlaybackStatus.Error
    this.current.errorCode = errorCode
    this.current.errorMessage = message
  }

  onPlaying(generation: number, durationMs?: number): boolean {
    if (!this.accepts(generation)) {
      return false
    }
    if (durationMs !== undefined && durationMs > 0) {
      this.current.durationMs = durationMs
    }
    this.current.status = MusicPlaybackStatus.Playing
    return true
  }

  onPaused(generation: number): boolean {
    return this.setStatus(generation, MusicPlaybackStatus.Paused)
  }

  onBuffering(generation: number): boolean {
    return this.setStatus(generation, MusicPlaybackStatus.Buffering)
  }

  onCompleted(generation: number): boolean {
    if (!this.setStatus(generation, MusicPlaybackStatus.Completed)) {
      return false
    }
    this.current.positionMs = this.current.durationMs
    return true
  }

  onStopped(generation: number): boolean {
    return this.setStatus(generation, MusicPlaybackStatus.Idle)
  }

  onTimeUpdate(generation: number, positionMs: number): boolean {
    if (!this.accepts(generation)) {
      return false
    }
    this.current.positionMs = Math.max(0, positionMs)
    return true
  }

  onSeekDone(generation: number, positionMs: number): boolean {
    return this.onTimeUpdate(generation, positionMs)
  }

  setRepeatMode(repeatMode: MusicRepeatMode): void {
    this.current.repeatMode = repeatMode
  }

  updateCurrentFavorite(favorite: boolean): void {
    if (this.current.currentIndex < 0 || this.current.currentIndex >= this.current.queue.length) {
      return
    }
    const items = this.current.queue.slice()
    const current = items[this.current.currentIndex]
    items[this.current.currentIndex] = {
      id: current.id,
      title: current.title,
      artist: current.artist,
      album: current.album,
      albumId: current.albumId,
      imageUrl: current.imageUrl,
      durationMs: current.durationMs,
      favorite: favorite,
      playbackPositionMs: current.playbackPositionMs,
      serverId: current.serverId
    }
    this.current.queue = items
  }

  replaceQueue(queue: MusicQueueSnapshot): boolean {
    if (queue.scope !== this.current.activeScope || queue.currentIndex < 0 ||
      queue.currentIndex >= queue.items.length || this.current.currentIndex < 0 ||
      this.current.currentIndex >= this.current.queue.length) {
      return false
    }
    const currentItem = this.current.queue[this.current.currentIndex]
    const replacement = queue.items[queue.currentIndex]
    if (currentItem.id !== replacement.id) {
      return false
    }
    this.current.queue = queue.items.slice()
    this.current.currentIndex = queue.currentIndex
    return true
  }

  stopAndClear(): void {
    this.requestGeneration += 1
    this.activeGeneration = 0
    this.current = MusicPlaybackMachine.emptySnapshot()
    this.clearPending()
    this.clearFailed()
  }

  currentGeneration(): number {
    return this.activeGeneration
  }

  currentRequestGeneration(): number {
    return this.requestGeneration
  }

  private setStatus(generation: number, status: MusicPlaybackStatus): boolean {
    if (!this.accepts(generation)) {
      return false
    }
    this.current.status = status
    return true
  }

  private accepts(generation: number): boolean {
    return generation === this.activeGeneration
  }

  private acceptsRequest(generation: number): boolean {
    return generation === this.requestGeneration
  }

  private clearPending(): void {
    this.pendingQueue = undefined
    this.pendingIndex = -1
  }

  private clearFailed(): void {
    this.failedQueue = undefined
    this.failedIndex = -1
  }

  static emptySnapshot(): MusicPlaybackSnapshot {
    return {
      queue: [],
      currentIndex: -1,
      status: MusicPlaybackStatus.Idle,
      positionMs: 0,
      durationMs: 0,
      errorCode: MusicPlaybackErrorCode.None,
      errorMessage: '',
      candidateItemId: '',
      candidateStatus: MusicPlaybackCandidateStatus.None,
      candidateErrorCode: MusicPlaybackErrorCode.None,
      candidateErrorMessage: '',
      activeScope: '',
      repeatMode: MusicRepeatMode.Off
    }
  }

  private static copyQueue(queue: MusicQueueSnapshot): MusicQueueSnapshot {
    return { items: queue.items.slice(), currentIndex: queue.currentIndex, scope: queue.scope }
  }

  private static copySource(source: MusicResolvedSource): MusicResolvedSource {
    return {
      itemId: source.itemId,
      mediaSourceId: source.mediaSourceId,
      playSessionId: source.playSessionId,
      liveStreamId: source.liveStreamId,
      url: source.url,
      playMethod: source.playMethod,
      durationMs: source.durationMs,
      startPositionMs: source.startPositionMs,
      scope: source.scope
    }
  }

  private static copyItem(item: MusicQueueItem): MusicQueueItem {
    return {
      id: item.id,
      title: item.title,
      artist: item.artist,
      album: item.album,
      albumId: item.albumId,
      imageUrl: item.imageUrl,
      durationMs: item.durationMs,
      favorite: item.favorite,
      playbackPositionMs: item.playbackPositionMs,
      serverId: item.serverId
    }
  }

  private static copySnapshot(snapshot: MusicPlaybackSnapshot): MusicPlaybackSnapshot {
    const items: MusicQueueItem[] = snapshot.queue.map((item: MusicQueueItem): MusicQueueItem =>
      MusicPlaybackMachine.copyItem(item))
    const copy: MusicPlaybackSnapshot = {
      queue: items,
      currentIndex: snapshot.currentIndex,
      status: snapshot.status,
      positionMs: snapshot.positionMs,
      durationMs: snapshot.durationMs,
      errorCode: snapshot.errorCode,
      errorMessage: snapshot.errorMessage,
      candidateItemId: snapshot.candidateItemId || '',
      candidateStatus: snapshot.candidateStatus === undefined ?
        MusicPlaybackCandidateStatus.None : snapshot.candidateStatus,
      candidateErrorCode: snapshot.candidateErrorCode === undefined ?
        MusicPlaybackErrorCode.None : snapshot.candidateErrorCode,
      candidateErrorMessage: snapshot.candidateErrorMessage || '',
      activeScope: snapshot.activeScope,
      repeatMode: snapshot.repeatMode
    }
    if (snapshot.source) {
      copy.source = MusicPlaybackMachine.copySource(snapshot.source)
    }
    return copy
  }

  private preservesActivePlayback(): boolean {
    return this.hasActiveSource()
  }

  private hasActiveSource(): boolean {
    return this.activeGeneration > 0 && this.current.source !== undefined &&
      this.current.queue.length > 0 && this.current.currentIndex >= 0 &&
      this.current.currentIndex < this.current.queue.length
  }

  private setCandidate(itemId: string, status: MusicPlaybackCandidateStatus,
    errorCode: MusicPlaybackErrorCode = MusicPlaybackErrorCode.None, message: string = ''): void {
    this.current.candidateItemId = itemId
    this.current.candidateStatus = status
    this.current.candidateErrorCode = errorCode
    this.current.candidateErrorMessage = message
  }

  private clearCandidate(): void {
    this.setCandidate('', MusicPlaybackCandidateStatus.None)
  }
}
