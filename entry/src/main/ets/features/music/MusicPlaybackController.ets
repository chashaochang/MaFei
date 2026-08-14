import { AppStorageV2 } from '@kit.ArkUI'
import { AppUIState } from '../../entity/AppUIState'
import { ApiClient } from '../../network/ApiClient'
import {
  BackgroundTaskManager,
  BackgroundTaskOwner
} from '../../utils/BackgroundTaskManager'
import { HarmonyMusicPlayerEngine } from './HarmonyMusicPlayerEngine'
import {
  MusicPlaybackErrorCode,
  MusicPlaybackSnapshot,
  MusicPlaybackStatus,
  MusicQueueItem,
  MusicRepeatMode,
  MusicResolvedSource,
  MusicTrack
} from './MusicModels'
import { MusicPlaybackMachine } from './MusicPlaybackMachine'
import musicAudioSessionManager from './MusicAudioSessionManager'
import {
  MusicPlaybackApiService,
  MusicPlaybackReporter
} from './MusicPlaybackApiService'
import { connectMusicPlaybackState, MusicPlaybackState } from './MusicPlaybackState'
import { MusicPlayerEngine, MusicPlayerEngineListener } from './MusicPlayerEngine'
import { MusicQueuePolicy, MusicQueueSnapshot } from './MusicQueuePolicy'
import { MusicSourceResolver } from './MusicSourceResolver'

const PROGRESS_REPORT_INTERVAL_MS: number = 10000

export interface MusicPlaybackSourceResolver {
  resolve(itemId: string, startPositionMs?: number): Promise<MusicResolvedSource>
  classifyError(error: Error): MusicPlaybackErrorCode
}

export interface MusicPlaybackSystemPort {
  initialize(): Promise<void>
  activate(): Promise<void>
  updateMetadata(item: MusicQueueItem): Promise<void>
  updatePlaybackState(snapshot: MusicPlaybackSnapshot): Promise<void>
  deactivate(): Promise<void>
  destroy(): Promise<void>
}

export interface MusicPlaybackBackgroundPort {
  acquire(): Promise<void>
  release(): Promise<void>
}

class NoopMusicPlaybackSystemPort implements MusicPlaybackSystemPort {
  async initialize(): Promise<void> {}
  async activate(): Promise<void> {}
  async updateMetadata(_item: MusicQueueItem): Promise<void> {}
  async updatePlaybackState(_snapshot: MusicPlaybackSnapshot): Promise<void> {}
  async deactivate(): Promise<void> {}
  async destroy(): Promise<void> {}
}

class NoopMusicPlaybackBackgroundPort implements MusicPlaybackBackgroundPort {
  async acquire(): Promise<void> {}
  async release(): Promise<void> {}
}

class MusicBackgroundTaskPort implements MusicPlaybackBackgroundPort {
  private appState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!

  async acquire(): Promise<void> {
    await BackgroundTaskManager.acquireContinuousTask(this.appState.context, BackgroundTaskOwner.Music)
  }

  async release(): Promise<void> {
    await BackgroundTaskManager.releaseContinuousTask(this.appState.context, BackgroundTaskOwner.Music)
  }
}

class MusicAudioSessionPort implements MusicPlaybackSystemPort {
  async initialize(): Promise<void> {
    await musicAudioSessionManager.initialize()
  }

  async activate(): Promise<void> {
    await musicAudioSessionManager.activate()
  }

  async updateMetadata(item: MusicQueueItem): Promise<void> {
    await musicAudioSessionManager.updateMetadata(item)
  }

  async updatePlaybackState(snapshot: MusicPlaybackSnapshot): Promise<void> {
    await musicAudioSessionManager.updatePlaybackState(snapshot)
  }

  async deactivate(): Promise<void> {
    await musicAudioSessionManager.deactivate()
  }

  async destroy(): Promise<void> {
    await musicAudioSessionManager.destroy()
  }
}

export class MusicPlaybackController implements MusicPlayerEngineListener {
  private static shared?: MusicPlaybackController

  private resolver: MusicPlaybackSourceResolver
  private reporter: MusicPlaybackReporter
  private engine: MusicPlayerEngine
  private state: MusicPlaybackState
  private machine: MusicPlaybackMachine
  private scopeProvider: () => string
  private systemPort: MusicPlaybackSystemPort
  private backgroundPort: MusicPlaybackBackgroundPort
  private reportChain: Promise<void> = Promise.resolve()
  private systemChain: Promise<void> = Promise.resolve()
  private startedGeneration: number = -1
  private stoppedGeneration: number = -1
  private lastProgressReportMs: number = 0
  private preparedGeneration: number = -1
  private preparedDurationMs: number = 0
  private backgroundAcquired: boolean = false

  constructor(resolver?: MusicPlaybackSourceResolver, engine?: MusicPlayerEngine,
    reporter?: MusicPlaybackReporter, state?: MusicPlaybackState,
    scopeProvider?: () => string, systemPort?: MusicPlaybackSystemPort,
    backgroundPort?: MusicPlaybackBackgroundPort) {
    const apiService = new MusicPlaybackApiService()
    this.resolver = resolver || new MusicSourceResolver(apiService)
    this.reporter = reporter || apiService
    this.engine = engine || new HarmonyMusicPlayerEngine()
    this.state = state || connectMusicPlaybackState()
    this.machine = new MusicPlaybackMachine(this.state.toSnapshot())
    this.scopeProvider = scopeProvider || (() => MusicPlaybackController.currentApiScope())
    this.systemPort = systemPort || new NoopMusicPlaybackSystemPort()
    this.backgroundPort = backgroundPort || new NoopMusicPlaybackBackgroundPort()
    this.publish(false)
  }

  static instance(): MusicPlaybackController {
    if (!MusicPlaybackController.shared) {
      const controller = new MusicPlaybackController()
      MusicPlaybackController.shared = controller
      musicAudioSessionManager.setCallbacks({
        onPlay: (): void => {
          controller.play().catch((): void => {})
        },
        onPause: (): void => {
          controller.pause().catch((): void => {})
        },
        onPrevious: (): void => {
          controller.previous().catch((): void => {})
        },
        onNext: (): void => {
          controller.next().catch((): void => {})
        },
        onStop: (): void => {
          controller.stopAndClear().catch((): void => {})
        },
        onSeek: (positionMs: number): void => {
          controller.seek(positionMs).catch((): void => {})
        }
      })
      controller.configureSystemPorts(new MusicAudioSessionPort(), new MusicBackgroundTaskPort())
    }
    return MusicPlaybackController.shared
  }

  configureSystemPorts(systemPort: MusicPlaybackSystemPort,
    backgroundPort: MusicPlaybackBackgroundPort): void {
    this.systemPort = systemPort
    this.backgroundPort = backgroundPort
    const snapshot = this.machine.snapshot()
    const item = this.currentItem(snapshot)
    if (item) {
      this.enqueueSystem(async (): Promise<void> => {
        await this.systemPort.initialize()
        await this.systemPort.updateMetadata(item)
        await this.systemPort.updatePlaybackState(snapshot)
        if (snapshot.status === MusicPlaybackStatus.Playing ||
          snapshot.status === MusicPlaybackStatus.Paused ||
          snapshot.status === MusicPlaybackStatus.Buffering) {
          await this.systemPort.activate()
        }
      })
    }
  }

  currentSnapshot(): MusicPlaybackSnapshot {
    return this.machine.snapshot()
  }

  activeScope(): string {
    return this.machine.snapshot().activeScope
  }

  async playQueue(tracks: MusicTrack[], startIndex: number): Promise<void> {
    const scope = this.scopeProvider()
    const queue = MusicQueuePolicy.create(tracks, startIndex, scope)
    if (queue.items.length === 0) {
      this.failRequest(
        scope.length === 0 ? MusicPlaybackErrorCode.Authentication : MusicPlaybackErrorCode.NoPlayableSource,
        scope.length === 0 ? 'Music playback requires an active account' : 'The music queue is empty'
      )
      return
    }
    await this.prepareTarget(queue, queue.currentIndex)
  }

  async play(): Promise<void> {
    const snapshot = this.machine.snapshot()
    if (snapshot.queue.length === 0 || snapshot.currentIndex < 0) {
      return
    }
    if (snapshot.status === MusicPlaybackStatus.Paused ||
      snapshot.status === MusicPlaybackStatus.Buffering) {
      try {
        await this.engine.play()
      } catch (error) {
        this.failActivePlayback((error as Error).message ||
          'The system music player could not resume this track')
      }
      await this.reportChain
      return
    }
    if (snapshot.status === MusicPlaybackStatus.Completed ||
      snapshot.status === MusicPlaybackStatus.Idle) {
      await this.prepareTarget(this.queueSnapshot(snapshot), snapshot.currentIndex, 0)
      return
    }
    if (snapshot.status === MusicPlaybackStatus.Error) {
      await this.retryCurrent()
    }
  }

  async pause(): Promise<void> {
    const status = this.machine.snapshot().status
    if (status !== MusicPlaybackStatus.Playing && status !== MusicPlaybackStatus.Buffering) {
      return
    }
    await this.engine.pause()
    await this.reportChain
  }

  async stop(): Promise<void> {
    const snapshot = this.machine.snapshot()
    const generation = this.machine.currentGeneration()
    const pendingGeneration = this.machine.cancelPending()
    if (pendingGeneration !== generation) {
      await this.engine.discard(pendingGeneration)
    }
    await this.reportStop(snapshot, generation, false)
    await this.engine.stop()
    this.machine.onStopped(generation)
    this.publish()
    await this.releaseBackground()
    await this.enqueueSystem(async (): Promise<void> => {
      await this.systemPort.updatePlaybackState(this.machine.snapshot())
      await this.systemPort.deactivate()
    })
  }

  async seek(positionMs: number): Promise<void> {
    const snapshot = this.machine.snapshot()
    if (!snapshot.source) {
      return
    }
    const target = Math.max(0, snapshot.durationMs > 0 ?
      Math.min(positionMs, snapshot.durationMs) : positionMs)
    await this.engine.seek(target)
  }

  async next(): Promise<void> {
    const snapshot = this.machine.snapshot()
    const target = MusicQueuePolicy.nextIndex(this.queueSnapshot(snapshot))
    if (target >= 0) {
      await this.prepareTarget(this.queueSnapshot(snapshot), target)
    }
  }

  async previous(): Promise<void> {
    const snapshot = this.machine.snapshot()
    const target = MusicQueuePolicy.previousIndex(this.queueSnapshot(snapshot))
    if (target >= 0) {
      await this.prepareTarget(this.queueSnapshot(snapshot), target)
    }
  }

  async jumpTo(index: number): Promise<void> {
    const snapshot = this.machine.snapshot()
    const target = MusicQueuePolicy.jumpIndex(this.queueSnapshot(snapshot), index)
    if (target >= 0 && target !== snapshot.currentIndex) {
      await this.prepareTarget(this.queueSnapshot(snapshot), target)
    }
  }

  removeAt(index: number): void {
    if (this.machine.hasCandidateTransition()) {
      return
    }
    const snapshot = this.machine.snapshot()
    const mutation = MusicQueuePolicy.removeAt(this.queueSnapshot(snapshot), index)
    if (mutation.changed && this.machine.replaceQueue(mutation.queue)) {
      this.publish()
    }
  }

  cycleRepeatMode(): void {
    const current = this.machine.snapshot().repeatMode
    const next = current === MusicRepeatMode.Off ? MusicRepeatMode.All :
      current === MusicRepeatMode.All ? MusicRepeatMode.One : MusicRepeatMode.Off
    this.machine.setRepeatMode(next)
    this.publish()
  }

  async clear(): Promise<void> {
    await this.stopAndClear()
  }

  async stopAndClear(): Promise<void> {
    const snapshot = this.machine.snapshot()
    const generation = this.machine.currentGeneration()
    this.machine.stopAndClear()
    this.preparedGeneration = -1
    this.preparedDurationMs = 0
    this.publish(false)
    await this.reportStop(snapshot, generation, false)
    this.startedGeneration = -1
    this.stoppedGeneration = -1
    try {
      await this.engine.stop()
    } catch (_error) {
    }
    await this.engine.release()
    await this.releaseBackground()
    await this.enqueueSystem(async (): Promise<void> => {
      await this.systemPort.deactivate()
      await this.systemPort.destroy()
    })
  }

  async retryCurrent(): Promise<void> {
    const failedQueue = this.machine.failedCandidate()
    if (failedQueue) {
      await this.prepareTarget(failedQueue, failedQueue.currentIndex)
      return
    }
    const snapshot = this.machine.snapshot()
    if (snapshot.currentIndex >= 0 && snapshot.currentIndex < snapshot.queue.length) {
      await this.prepareTarget(this.queueSnapshot(snapshot), snapshot.currentIndex, snapshot.positionMs)
    }
  }

  async skipFailed(): Promise<void> {
    const queue = this.failedQueueOrActiveError()
    if (!queue) {
      return
    }
    const target = MusicQueuePolicy.nextIndex(queue)
    if (target >= 0) {
      await this.prepareTarget(queue, target)
    }
  }

  canSkipFailed(): boolean {
    const queue = this.failedQueueOrActiveError()
    return queue !== undefined && MusicQueuePolicy.nextIndex(queue) >= 0
  }

  updateCurrentFavorite(favorite: boolean): void {
    this.machine.updateCurrentFavorite(favorite)
    this.publish()
  }

  async reportProgressNow(): Promise<void> {
    const snapshot = this.machine.snapshot()
    if (!snapshot.source || this.startedGeneration !== this.machine.currentGeneration()) {
      return
    }
    await this.enqueueReport((): Promise<void> => this.reporter.reportProgress(
      snapshot.source!,
      snapshot.positionMs,
      snapshot.status === MusicPlaybackStatus.Paused
    ))
    this.lastProgressReportMs = snapshot.positionMs
  }

  async onAppForeground(): Promise<void> {
    const snapshot = this.machine.snapshot()
    this.state.apply(snapshot)
    await this.enqueueSystem((): Promise<void> => this.systemPort.updatePlaybackState(snapshot))
  }

  onPrepared(generation: number, durationMs: number): void {
    if (generation !== this.machine.currentRequestGeneration()) {
      return
    }
    this.preparedGeneration = generation
    this.preparedDurationMs = Math.max(0, durationMs)
  }

  onPlaying(generation: number): void {
    if (!this.machine.onPlaying(generation)) {
      return
    }
    const snapshot = this.machine.snapshot()
    this.publish()
    if (this.startedGeneration !== generation && snapshot.source) {
      this.startedGeneration = generation
      this.stoppedGeneration = -1
      this.lastProgressReportMs = snapshot.positionMs
      this.enqueueReport((): Promise<void> =>
        this.reporter.reportStart(snapshot.source!, snapshot.positionMs))
      this.acquireBackground()
      const item = this.currentItem(snapshot)
      if (item) {
        this.enqueueSystem(async (): Promise<void> => {
          await this.systemPort.initialize()
          await this.systemPort.updateMetadata(item)
          await this.systemPort.updatePlaybackState(snapshot)
          await this.systemPort.activate()
        })
      }
    }
  }

  onPaused(generation: number): void {
    if (!this.machine.onPaused(generation)) {
      return
    }
    const snapshot = this.machine.snapshot()
    this.publish()
    if (snapshot.source && this.startedGeneration === generation) {
      this.enqueueReport((): Promise<void> =>
        this.reporter.reportProgress(snapshot.source!, snapshot.positionMs, true))
      this.lastProgressReportMs = snapshot.positionMs
    }
  }

  onBuffering(generation: number): void {
    if (this.machine.onBuffering(generation)) {
      this.publish()
    }
  }

  onCompleted(generation: number): void {
    if (!this.machine.onCompleted(generation)) {
      return
    }
    this.publish()
    this.handleCompletion(generation)
  }

  onStopped(generation: number): void {
    if (this.machine.onStopped(generation)) {
      this.publish()
    }
  }

  onTimeUpdate(generation: number, positionMs: number): void {
    if (!this.machine.onTimeUpdate(generation, positionMs)) {
      return
    }
    const snapshot = this.machine.snapshot()
    this.publish()
    if (snapshot.source && this.startedGeneration === generation &&
      Math.abs(snapshot.positionMs - this.lastProgressReportMs) >= PROGRESS_REPORT_INTERVAL_MS) {
      this.lastProgressReportMs = snapshot.positionMs
      this.enqueueReport((): Promise<void> =>
        this.reporter.reportProgress(snapshot.source!, snapshot.positionMs, false))
    }
  }

  onSeekDone(generation: number, positionMs: number): void {
    if (!this.machine.onSeekDone(generation, positionMs)) {
      return
    }
    const snapshot = this.machine.snapshot()
    this.publish()
    if (snapshot.source && this.startedGeneration === generation) {
      this.lastProgressReportMs = snapshot.positionMs
      this.enqueueReport((): Promise<void> => this.reporter.reportProgress(
        snapshot.source!,
        snapshot.positionMs,
        snapshot.status === MusicPlaybackStatus.Paused
      ))
    }
  }

  onError(generation: number, message: string): void {
    if (this.machine.isPendingGeneration(generation) &&
      this.machine.failPreparation(generation, MusicPlaybackErrorCode.SystemPlayer, message)) {
      this.publish()
      return
    }
    if (generation !== this.machine.currentGeneration()) {
      return
    }
    this.failActivePlayback(message)
  }

  private async prepareTarget(queue: MusicQueueSnapshot, index: number,
    startPositionMs?: number): Promise<void> {
    if (index < 0 || index >= queue.items.length) {
      return
    }
    const oldSnapshot = this.machine.snapshot()
    const oldGeneration = this.machine.currentGeneration()
    const target = queue.items[index]
    const generation = this.machine.beginTransition(queue, index)
    this.preparedGeneration = -1
    this.preparedDurationMs = 0
    this.publish()
    let source: MusicResolvedSource
    try {
      source = await this.resolver.resolve(
        target.id,
        startPositionMs === undefined ? target.playbackPositionMs : startPositionMs
      )
    } catch (error) {
      const normalized = error as Error
      if (this.machine.failPreparation(generation, this.resolver.classifyError(normalized), normalized.message)) {
        this.publish()
      }
      return
    }
    if (generation !== this.machine.currentRequestGeneration()) {
      return
    }
    if (source.scope !== queue.scope) {
      if (this.machine.failPreparation(
        generation,
        MusicPlaybackErrorCode.Authentication,
        'The active music account changed while preparing playback'
      )) {
        this.publish()
      }
      return
    }
    try {
      await this.engine.prepare(source.url, generation, this)
    } catch (error) {
      const message = (error as Error).message || 'The system music player could not prepare this track'
      await this.engine.discard(generation)
      if (this.machine.failPreparation(generation, MusicPlaybackErrorCode.SystemPlayer, message)) {
        this.publish()
      }
      return
    }
    if (generation !== this.machine.currentRequestGeneration()) {
      await this.engine.discard(generation)
      return
    }
    if (!this.machine.isPendingGeneration(generation)) {
      await this.engine.discard(generation)
      return
    }
    if (source.durationMs <= 0 && this.preparedGeneration === generation && this.preparedDurationMs > 0) {
      source.durationMs = this.preparedDurationMs
    }
    let promotionId: number
    try {
      promotionId = this.engine.stagePromotion(generation)
    } catch (error) {
      const message = (error as Error).message || 'The system music player could not activate this track'
      if (this.machine.failPreparation(generation, MusicPlaybackErrorCode.SystemPlayer, message)) {
        this.publish()
      }
      return
    }
    if (!this.machine.commitPreparation(generation, source)) {
      this.engine.rollbackPromotion(promotionId)
      return
    }
    this.engine.confirmPromotion(promotionId)
    await this.reportStop(oldSnapshot, oldGeneration, oldSnapshot.status === MusicPlaybackStatus.Completed)
    this.startedGeneration = -1
    this.stoppedGeneration = -1
    this.lastProgressReportMs = source.startPositionMs
    this.publish()
    const preparedSnapshot = this.machine.snapshot()
    const preparedItem = this.currentItem(preparedSnapshot)
    if (preparedItem) {
      await this.enqueueSystem(async (): Promise<void> => {
        await this.systemPort.initialize()
        await this.systemPort.updateMetadata(preparedItem)
        await this.systemPort.updatePlaybackState(preparedSnapshot)
      })
    }
    try {
      await this.engine.play()
    } catch (error) {
      const message = (error as Error).message || 'The system music player could not play this track'
      this.failActivePlayback(message)
      return
    }
    await this.reportChain
  }

  private async handleCompletion(generation: number): Promise<void> {
    if (generation !== this.machine.currentGeneration()) {
      return
    }
    const snapshot = this.machine.snapshot()
    const queue = this.queueSnapshot(snapshot)
    await this.reportStop(snapshot, generation, true)
    let target = MusicQueuePolicy.nextIndex(queue)
    if (target < 0) {
      target = MusicQueuePolicy.completionIndex(queue, snapshot.repeatMode)
    }
    if (target >= 0) {
      await this.prepareTarget(queue, target, 0)
      return
    }
    await this.enqueueSystem((): Promise<void> =>
      this.systemPort.updatePlaybackState(this.machine.snapshot()))
  }

  private failedQueueOrActiveError(): MusicQueueSnapshot | undefined {
    const failedQueue = this.machine.failedCandidate()
    if (failedQueue) {
      return failedQueue
    }
    const snapshot = this.machine.snapshot()
    if (snapshot.status !== MusicPlaybackStatus.Error || snapshot.currentIndex < 0 ||
      snapshot.currentIndex >= snapshot.queue.length) {
      return undefined
    }
    return this.queueSnapshot(snapshot)
  }

  private failRequest(code: MusicPlaybackErrorCode, message: string): void {
    const generation = this.machine.beginTransition(MusicQueuePolicy.clear(this.scopeProvider()), -1)
    this.machine.failPreparation(generation, code, message)
    this.publish()
  }

  private failActivePlayback(message: string): void {
    const snapshot = this.machine.snapshot()
    const generation = this.machine.currentGeneration()
    const pendingGeneration = this.machine.currentRequestGeneration()
    this.machine.failActive(MusicPlaybackErrorCode.SystemPlayer, message)
    this.publish()
    if (pendingGeneration !== generation) {
      this.engine.discard(pendingGeneration).catch((): void => {})
    }
    this.reportStop(snapshot, generation, false)
    this.releaseBackground()
  }

  private queueSnapshot(snapshot: MusicPlaybackSnapshot): MusicQueueSnapshot {
    return {
      items: snapshot.queue.slice(),
      currentIndex: snapshot.currentIndex,
      scope: snapshot.activeScope
    }
  }

  private currentItem(snapshot: MusicPlaybackSnapshot): MusicQueueItem | undefined {
    if (snapshot.currentIndex < 0 || snapshot.currentIndex >= snapshot.queue.length) {
      return undefined
    }
    return snapshot.queue[snapshot.currentIndex]
  }

  private publish(updateSystem: boolean = true): void {
    const snapshot = this.machine.snapshot()
    this.state.apply(snapshot)
    if (updateSystem) {
      this.enqueueSystem((): Promise<void> => this.systemPort.updatePlaybackState(snapshot))
    }
  }

  private async reportStop(snapshot: MusicPlaybackSnapshot, generation: number,
    completed: boolean): Promise<void> {
    if (!snapshot.source || this.startedGeneration !== generation || this.stoppedGeneration === generation) {
      return
    }
    this.stoppedGeneration = generation
    await this.enqueueReport((): Promise<void> =>
      this.reporter.reportStop(snapshot.source!, snapshot.positionMs, completed))
  }

  private enqueueReport(operation: () => Promise<void>): Promise<void> {
    this.reportChain = this.reportChain.then(operation).catch((_error: Error): void => {
      console.warn('[MusicPlayback] Jellyfin playback report failed')
    })
    return this.reportChain
  }

  private enqueueSystem(operation: () => Promise<void>): Promise<void> {
    this.systemChain = this.systemChain.then(operation).catch((_error: Error): void => {
      console.warn('[MusicPlayback] system playback integration failed')
    })
    return this.systemChain
  }

  private async acquireBackground(): Promise<void> {
    if (this.backgroundAcquired) {
      return
    }
    this.backgroundAcquired = true
    try {
      await this.backgroundPort.acquire()
    } catch (_error) {
      this.backgroundAcquired = false
    }
  }

  private async releaseBackground(): Promise<void> {
    if (!this.backgroundAcquired) {
      return
    }
    this.backgroundAcquired = false
    try {
      await this.backgroundPort.release()
    } catch (_error) {
    }
  }

  private static currentApiScope(): string {
    if (!ApiClient.isInitialized()) {
      return ''
    }
    const api = ApiClient.Instance()
    let basePath = String(api.basePath || '').trim()
    while (basePath.endsWith('/')) {
      basePath = basePath.slice(0, -1)
    }
    return `${basePath}|${String(api.userId || '')}`
  }
}
