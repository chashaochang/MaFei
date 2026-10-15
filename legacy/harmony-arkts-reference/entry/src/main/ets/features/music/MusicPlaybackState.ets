import { AppStorageV2 } from '@kit.ArkUI'
import {
  MusicPlaybackCandidateStatus,
  MusicPlaybackErrorCode,
  MusicPlaybackSnapshot,
  MusicPlaybackStatus,
  MusicQueueItem,
  MusicRepeatMode,
  MusicResolvedSource
} from './MusicModels'
import { MusicPlaybackMachine } from './MusicPlaybackMachine'

@ObservedV2
export class MusicPlaybackState {
  @Trace queue: MusicQueueItem[] = []
  @Trace currentIndex: number = -1
  @Trace status: MusicPlaybackStatus = MusicPlaybackStatus.Idle
  @Trace positionMs: number = 0
  @Trace durationMs: number = 0
  @Trace errorCode: MusicPlaybackErrorCode = MusicPlaybackErrorCode.None
  @Trace errorMessage: string = ''
  @Trace candidateItemId: string = ''
  @Trace candidateStatus: MusicPlaybackCandidateStatus = MusicPlaybackCandidateStatus.None
  @Trace candidateErrorCode: MusicPlaybackErrorCode = MusicPlaybackErrorCode.None
  @Trace candidateErrorMessage: string = ''
  @Trace activeScope: string = ''
  @Trace source?: MusicResolvedSource
  @Trace repeatMode: MusicRepeatMode = MusicRepeatMode.Off

  apply(snapshot: MusicPlaybackSnapshot): void {
    this.queue = snapshot.queue.slice()
    this.currentIndex = snapshot.currentIndex
    this.status = snapshot.status
    this.positionMs = snapshot.positionMs
    this.durationMs = snapshot.durationMs
    this.errorCode = snapshot.errorCode
    this.errorMessage = snapshot.errorMessage
    this.candidateItemId = snapshot.candidateItemId || ''
    this.candidateStatus = snapshot.candidateStatus === undefined ?
      MusicPlaybackCandidateStatus.None : snapshot.candidateStatus
    this.candidateErrorCode = snapshot.candidateErrorCode === undefined ?
      MusicPlaybackErrorCode.None : snapshot.candidateErrorCode
    this.candidateErrorMessage = snapshot.candidateErrorMessage || ''
    this.activeScope = snapshot.activeScope
    this.source = snapshot.source
    this.repeatMode = snapshot.repeatMode
  }

  toSnapshot(): MusicPlaybackSnapshot {
    const snapshot: MusicPlaybackSnapshot = {
      queue: this.queue.slice(),
      currentIndex: this.currentIndex,
      status: this.status,
      positionMs: this.positionMs,
      durationMs: this.durationMs,
      errorCode: this.errorCode,
      errorMessage: this.errorMessage,
      candidateItemId: this.candidateItemId,
      candidateStatus: this.candidateStatus,
      candidateErrorCode: this.candidateErrorCode,
      candidateErrorMessage: this.candidateErrorMessage,
      activeScope: this.activeScope,
      repeatMode: this.repeatMode
    }
    if (this.source) {
      snapshot.source = this.source
    }
    return snapshot
  }

  currentItem(): MusicQueueItem | undefined {
    if (this.currentIndex < 0 || this.currentIndex >= this.queue.length) {
      return undefined
    }
    return this.queue[this.currentIndex]
  }

  reset(): void {
    this.apply(MusicPlaybackMachine.emptySnapshot())
  }
}

export function connectMusicPlaybackState(): MusicPlaybackState {
  return AppStorageV2.connect(MusicPlaybackState, () => new MusicPlaybackState())!
}
