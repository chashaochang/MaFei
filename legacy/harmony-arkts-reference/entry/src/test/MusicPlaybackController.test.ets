import { describe, expect, it } from '@ohos/hypium'
import {
  MusicPlaybackCandidateStatus,
  MusicPlaybackErrorCode,
  MusicPlaybackSnapshot,
  MusicPlaybackStatus,
  MusicQueueItem,
  MusicResolvedSource,
  MusicPlayMethod,
  MusicTrack
} from '../main/ets/features/music/MusicModels'
import {
  MusicPlaybackBackgroundPort,
  MusicPlaybackController,
  MusicPlaybackSourceResolver,
  MusicPlaybackSystemPort
} from '../main/ets/features/music/MusicPlaybackController'
import { MusicPlaybackReporter } from '../main/ets/features/music/MusicPlaybackApiService'
import { MusicPlaybackState } from '../main/ets/features/music/MusicPlaybackState'
import {
  MusicPlayerEngine,
  MusicPlayerEngineListener
} from '../main/ets/features/music/MusicPlayerEngine'

function track(id: string): MusicTrack {
  return {
    id: id,
    title: id,
    artist: 'Artist',
    artistId: 'artist-1',
    album: 'Album',
    albumId: 'album-1',
    discNumber: 1,
    trackNumber: 1,
    durationMs: 120000,
    imageUrl: '',
    favorite: false,
    playbackPositionMs: 0
  }
}

class FakeMusicResolver implements MusicPlaybackSourceResolver {
  failingIds: string[] = []
  deferredIds: string[] = []
  private deferredResolvers: Map<string, (source: MusicResolvedSource) => void> =
    new Map<string, (source: MusicResolvedSource) => void>()

  async resolve(itemId: string, startPositionMs: number = 0): Promise<MusicResolvedSource> {
    if (this.failingIds.includes(itemId)) {
      throw new Error(`resolve failed: ${itemId}`)
    }
    const source = this.source(itemId, startPositionMs)
    if (this.deferredIds.includes(itemId)) {
      return new Promise<MusicResolvedSource>((resolve: (value: MusicResolvedSource) => void) => {
        this.deferredResolvers.set(itemId, resolve)
      })
    }
    return source
  }

  resolveDeferred(itemId: string, startPositionMs: number = 0): void {
    this.deferredResolvers.get(itemId)?.(this.source(itemId, startPositionMs))
  }

  private source(itemId: string, startPositionMs: number): MusicResolvedSource {
    return {
      itemId: itemId,
      mediaSourceId: `source-${itemId}`,
      playSessionId: `session-${itemId}`,
      liveStreamId: '',
      url: `https://music/${itemId}`,
      playMethod: MusicPlayMethod.DirectPlay,
      durationMs: 120000,
      startPositionMs: startPositionMs,
      scope: 'server|user'
    }
  }

  classifyError(_error: Error): MusicPlaybackErrorCode {
    return MusicPlaybackErrorCode.Network
  }
}

class FakeMusicEngine implements MusicPlayerEngine {
  listener?: MusicPlayerEngineListener
  generation: number = -1
  candidateListener?: MusicPlayerEngineListener
  candidateGeneration: number = -1
  playError: string = ''
  pauseCalls: number = 0
  playCalls: number = 0
  rollbackCalls: number = 0
  confirmCalls: number = 0
  onStage?: (generation: number) => void
  errorDuringStage: string = ''
  private promotionSequence: number = 0
  private stagedPromotion: number = -1
  private previousListener?: MusicPlayerEngineListener
  private previousGeneration: number = -1

  async prepare(_url: string, generation: number, listener: MusicPlayerEngineListener): Promise<void> {
    this.candidateListener = listener
    this.candidateGeneration = generation
    listener.onPrepared(generation, 120000)
  }

  stagePromotion(generation: number): number {
    if (generation !== this.candidateGeneration || !this.candidateListener) {
      throw new Error('missing candidate')
    }
    this.previousListener = this.listener
    this.previousGeneration = this.generation
    this.listener = this.candidateListener
    this.generation = generation
    this.candidateListener = undefined
    this.candidateGeneration = -1
    this.stagedPromotion = ++this.promotionSequence
    if (this.errorDuringStage.length > 0) {
      this.listener?.onError(generation, this.errorDuringStage)
    }
    this.onStage?.(generation)
    return this.stagedPromotion
  }

  confirmPromotion(promotionId: number): void {
    if (promotionId !== this.stagedPromotion) {
      return
    }
    this.confirmCalls += 1
    this.stagedPromotion = -1
    this.previousListener = undefined
    this.previousGeneration = -1
  }

  rollbackPromotion(promotionId: number): void {
    if (promotionId !== this.stagedPromotion) {
      return
    }
    this.rollbackCalls += 1
    this.listener = this.previousListener
    this.generation = this.previousGeneration
    this.previousListener = undefined
    this.previousGeneration = -1
    this.stagedPromotion = -1
  }

  async discard(generation: number): Promise<void> {
    if (generation === this.candidateGeneration) {
      this.candidateListener = undefined
      this.candidateGeneration = -1
    }
  }

  async play(): Promise<void> {
    this.playCalls += 1
    if (this.playError.length > 0) {
      throw new Error(this.playError)
    }
    this.listener?.onPlaying(this.generation)
  }

  async pause(): Promise<void> {
    this.pauseCalls += 1
    this.listener?.onPaused(this.generation)
  }

  async stop(): Promise<void> {
    this.listener?.onStopped(this.generation)
  }

  async seek(positionMs: number): Promise<void> {
    this.listener?.onSeekDone(this.generation, positionMs)
  }

  async release(): Promise<void> {}

  time(positionMs: number): void {
    this.listener?.onTimeUpdate(this.generation, positionMs)
  }
}

class RecordingMusicReporter implements MusicPlaybackReporter {
  events: string[] = []

  async reportStart(source: MusicResolvedSource, _positionMs: number): Promise<void> {
    this.events.push(`start:${source.itemId}`)
  }

  async reportProgress(source: MusicResolvedSource, positionMs: number, paused: boolean): Promise<void> {
    this.events.push(`progress:${source.itemId}:${positionMs}:${paused}`)
  }

  async reportStop(source: MusicResolvedSource, _positionMs: number, _completed: boolean): Promise<void> {
    this.events.push(`stop:${source.itemId}`)
  }
}

class FakeSystemPort implements MusicPlaybackSystemPort {
  async initialize(): Promise<void> {}
  async activate(): Promise<void> {}
  async updateMetadata(_item: MusicQueueItem): Promise<void> {}
  async updatePlaybackState(_snapshot: MusicPlaybackSnapshot): Promise<void> {}
  async deactivate(): Promise<void> {}
  async destroy(): Promise<void> {}
}

class FakeBackgroundPort implements MusicPlaybackBackgroundPort {
  acquired: number = 0
  released: number = 0

  async acquire(): Promise<void> {
    this.acquired += 1
  }

  async release(): Promise<void> {
    this.released += 1
  }
}

export default function musicPlaybackControllerTest() {
  describe('MusicPlaybackController', () => {
    it('reportsStartOnlyAfterPlayingAndStopsBeforeTheNextTrack', 0, async () => {
      const engine = new FakeMusicEngine()
      const reporter = new RecordingMusicReporter()
      const background = new FakeBackgroundPort()
      const controller = new MusicPlaybackController(
        new FakeMusicResolver(),
        engine,
        reporter,
        new MusicPlaybackState(),
        (): string => 'server|user',
        new FakeSystemPort(),
        background
      )

      await controller.playQueue([track('a'), track('b')], 0)
      await controller.next()

      expect(reporter.events[0]).assertEqual('start:a')
      expect(reporter.events[1]).assertEqual('stop:a')
      expect(reporter.events[2]).assertEqual('start:b')
      expect(controller.currentSnapshot().queue[controller.currentSnapshot().currentIndex].id)
        .assertEqual('b')
      expect(background.acquired).assertEqual(1)
    })

    it('reportsProgressAtTenSecondsAndImmediatelyAfterPause', 0, async () => {
      const engine = new FakeMusicEngine()
      const reporter = new RecordingMusicReporter()
      const controller = new MusicPlaybackController(
        new FakeMusicResolver(),
        engine,
        reporter,
        new MusicPlaybackState(),
        (): string => 'server|user',
        new FakeSystemPort(),
        new FakeBackgroundPort()
      )

      await controller.playQueue([track('a')], 0)
      engine.time(9000)
      engine.time(10000)
      await controller.pause()

      expect(reporter.events.includes('progress:a:10000:false')).assertTrue()
      expect(reporter.events.includes('progress:a:10000:true')).assertTrue()
      expect(controller.currentSnapshot().status).assertEqual(MusicPlaybackStatus.Paused)
    })

    it('keepsTheOldTrackWhenTheNextTrackCannotResolve', 0, async () => {
      const resolver = new FakeMusicResolver()
      const engine = new FakeMusicEngine()
      const reporter = new RecordingMusicReporter()
      const controller = new MusicPlaybackController(
        resolver,
        engine,
        reporter,
        new MusicPlaybackState(),
        (): string => 'server|user',
        new FakeSystemPort(),
        new FakeBackgroundPort()
      )

      await controller.playQueue([track('a'), track('b')], 0)
      resolver.failingIds = ['b']
      await controller.next()

      expect(controller.currentSnapshot().queue[controller.currentSnapshot().currentIndex].id).assertEqual('a')
      expect(controller.currentSnapshot().status).assertEqual(MusicPlaybackStatus.Playing)
      expect(controller.currentSnapshot().candidateItemId).assertEqual('b')
      expect(controller.currentSnapshot().candidateStatus).assertEqual(MusicPlaybackCandidateStatus.Error)
      expect(reporter.events.includes('stop:a')).assertFalse()
      await controller.pause()
      expect(engine.pauseCalls).assertEqual(1)
    })

    it('retriesAndSkipsTheFailedFirstTrackOfANewQueue', 0, async () => {
      const resolver = new FakeMusicResolver()
      resolver.failingIds = ['a']
      const engine = new FakeMusicEngine()
      const controller = new MusicPlaybackController(
        resolver,
        engine,
        new RecordingMusicReporter(),
        new MusicPlaybackState(),
        (): string => 'server|user',
        new FakeSystemPort(),
        new FakeBackgroundPort()
      )

      await controller.playQueue([track('a'), track('b')], 0)
      expect(controller.currentSnapshot().queue[0].id).assertEqual('a')
      resolver.failingIds = []
      await controller.retryCurrent()
      expect(controller.currentSnapshot().queue[controller.currentSnapshot().currentIndex].id).assertEqual('a')

      resolver.failingIds = ['a']
      await controller.playQueue([track('a'), track('b')], 0)
      resolver.failingIds = []
      await controller.skipFailed()
      expect(controller.currentSnapshot().queue[controller.currentSnapshot().currentIndex].id).assertEqual('b')
    })

    it('entersErrorWhenEnginePlayFailsAndCanPauseWhileBuffering', 0, async () => {
      const engine = new FakeMusicEngine()
      engine.playError = 'play failed'
      const controller = new MusicPlaybackController(
        new FakeMusicResolver(),
        engine,
        new RecordingMusicReporter(),
        new MusicPlaybackState(),
        (): string => 'server|user',
        new FakeSystemPort(),
        new FakeBackgroundPort()
      )

      await controller.playQueue([track('a')], 0)
      expect(controller.currentSnapshot().status).assertEqual(MusicPlaybackStatus.Error)

      engine.playError = ''
      await controller.retryCurrent()
      engine.listener?.onBuffering(engine.generation)
      await controller.pause()
      expect(engine.pauseCalls).assertEqual(1)
    })

    it('ignoresASlowerSupersededTrackRequest', 0, async () => {
      const resolver = new FakeMusicResolver()
      resolver.deferredIds = ['b']
      const engine = new FakeMusicEngine()
      const controller = new MusicPlaybackController(
        resolver,
        engine,
        new RecordingMusicReporter(),
        new MusicPlaybackState(),
        (): string => 'server|user',
        new FakeSystemPort(),
        new FakeBackgroundPort()
      )

      await controller.playQueue([track('a'), track('b'), track('c')], 0)
      const slowRequest = controller.jumpTo(1)
      const latestRequest = controller.jumpTo(2)
      await latestRequest
      resolver.resolveDeferred('b')
      await slowRequest

      expect(controller.currentSnapshot().queue[controller.currentSnapshot().currentIndex].id).assertEqual('c')
    })

    it('stopsAndCancelsAPendingTrackBeforeItCanAutoplay', 0, async () => {
      const resolver = new FakeMusicResolver()
      resolver.deferredIds = ['b']
      const engine = new FakeMusicEngine()
      const controller = new MusicPlaybackController(
        resolver,
        engine,
        new RecordingMusicReporter(),
        new MusicPlaybackState(),
        (): string => 'server|user',
        new FakeSystemPort(),
        new FakeBackgroundPort()
      )

      await controller.playQueue([track('a'), track('b')], 0)
      const pending = controller.jumpTo(1)
      await controller.stop()
      resolver.resolveDeferred('b')
      await pending

      expect(controller.currentSnapshot().queue[controller.currentSnapshot().currentIndex].id).assertEqual('a')
      expect(controller.currentSnapshot().status).assertEqual(MusicPlaybackStatus.Idle)
      expect(engine.playCalls).assertEqual(1)
    })

    it('rollsBackAStagedTrackWhenStopInvalidatesTheCandidate', 0, async () => {
      const engine = new FakeMusicEngine()
      const controller = new MusicPlaybackController(
        new FakeMusicResolver(),
        engine,
        new RecordingMusicReporter(),
        new MusicPlaybackState(),
        (): string => 'server|user',
        new FakeSystemPort(),
        new FakeBackgroundPort()
      )

      await controller.playQueue([track('a'), track('b')], 0)
      let stopRequest: Promise<void> = Promise.resolve()
      engine.onStage = (): void => {
        stopRequest = controller.stop()
      }
      await controller.jumpTo(1)
      await stopRequest

      const snapshot = controller.currentSnapshot()
      expect(snapshot.queue[snapshot.currentIndex].id).assertEqual('a')
      expect(snapshot.status).assertEqual(MusicPlaybackStatus.Idle)
      expect(engine.generation).assertEqual(1)
      expect(engine.rollbackCalls).assertEqual(1)
    })

    it('rollsBackAStagedTrackWhenTheCandidateErrorsBeforeCommit', 0, async () => {
      const engine = new FakeMusicEngine()
      const controller = new MusicPlaybackController(
        new FakeMusicResolver(),
        engine,
        new RecordingMusicReporter(),
        new MusicPlaybackState(),
        (): string => 'server|user',
        new FakeSystemPort(),
        new FakeBackgroundPort()
      )

      await controller.playQueue([track('a'), track('b')], 0)
      engine.errorDuringStage = 'candidate failed'
      await controller.jumpTo(1)

      const snapshot = controller.currentSnapshot()
      expect(snapshot.queue[snapshot.currentIndex].id).assertEqual('a')
      expect(snapshot.status).assertEqual(MusicPlaybackStatus.Playing)
      expect(snapshot.candidateItemId).assertEqual('b')
      expect(snapshot.candidateStatus).assertEqual(MusicPlaybackCandidateStatus.Error)
      expect(engine.generation).assertEqual(1)
      expect(engine.rollbackCalls).assertEqual(1)
    })

    it('rollsBackAStagedTrackWhenANewerRequestFails', 0, async () => {
      const resolver = new FakeMusicResolver()
      const engine = new FakeMusicEngine()
      const controller = new MusicPlaybackController(
        resolver,
        engine,
        new RecordingMusicReporter(),
        new MusicPlaybackState(),
        (): string => 'server|user',
        new FakeSystemPort(),
        new FakeBackgroundPort()
      )

      await controller.playQueue([track('a'), track('b'), track('c')], 0)
      let latestRequest: Promise<void> = Promise.resolve()
      engine.onStage = (): void => {
        resolver.failingIds = ['c']
        latestRequest = controller.jumpTo(2)
      }
      await controller.jumpTo(1)
      await latestRequest

      const snapshot = controller.currentSnapshot()
      expect(snapshot.queue[snapshot.currentIndex].id).assertEqual('a')
      expect(snapshot.status).assertEqual(MusicPlaybackStatus.Playing)
      expect(snapshot.candidateItemId).assertEqual('c')
      expect(snapshot.candidateStatus).assertEqual(MusicPlaybackCandidateStatus.Error)
      expect(engine.generation).assertEqual(1)
      expect(engine.rollbackCalls).assertEqual(1)
    })

    it('reportsCompletedBeforeAFailingAutomaticSuccessor', 0, async () => {
      const resolver = new FakeMusicResolver()
      const engine = new FakeMusicEngine()
      const reporter = new RecordingMusicReporter()
      const controller = new MusicPlaybackController(
        resolver,
        engine,
        reporter,
        new MusicPlaybackState(),
        (): string => 'server|user',
        new FakeSystemPort(),
        new FakeBackgroundPort()
      )

      await controller.playQueue([track('a'), track('b')], 0)
      resolver.failingIds = ['b']
      engine.listener?.onCompleted(engine.generation)
      await Promise.resolve()
      await Promise.resolve()
      await Promise.resolve()

      expect(reporter.events.includes('stop:a')).assertTrue()
      expect(controller.currentSnapshot().queue[controller.currentSnapshot().currentIndex].id).assertEqual('a')
      expect(controller.currentSnapshot().status).assertEqual(MusicPlaybackStatus.Completed)
    })

    it('skipsAnActivePlaybackErrorAndHidesSkipAtTheQueueEnd', 0, async () => {
      const engine = new FakeMusicEngine()
      engine.playError = 'play failed'
      const controller = new MusicPlaybackController(
        new FakeMusicResolver(),
        engine,
        new RecordingMusicReporter(),
        new MusicPlaybackState(),
        (): string => 'server|user',
        new FakeSystemPort(),
        new FakeBackgroundPort()
      )

      await controller.playQueue([track('a'), track('b')], 0)
      expect(controller.canSkipFailed()).assertTrue()
      engine.playError = ''
      await controller.skipFailed()
      expect(controller.currentSnapshot().queue[controller.currentSnapshot().currentIndex].id).assertEqual('b')
      engine.listener?.onError(engine.generation, 'failed at end')
      expect(controller.canSkipFailed()).assertFalse()
    })

    it('doesNotMutateTheQueueWhileATrackCandidateIsPending', 0, async () => {
      const resolver = new FakeMusicResolver()
      resolver.deferredIds = ['b']
      const engine = new FakeMusicEngine()
      const controller = new MusicPlaybackController(
        resolver,
        engine,
        new RecordingMusicReporter(),
        new MusicPlaybackState(),
        (): string => 'server|user',
        new FakeSystemPort(),
        new FakeBackgroundPort()
      )

      await controller.playQueue([track('a'), track('b'), track('c')], 0)
      const pending = controller.jumpTo(1)
      controller.removeAt(2)
      resolver.resolveDeferred('b')
      await pending

      expect(controller.currentSnapshot().queue.length).assertEqual(3)
      expect(controller.currentSnapshot().queue[2].id).assertEqual('c')
    })
  })
}
