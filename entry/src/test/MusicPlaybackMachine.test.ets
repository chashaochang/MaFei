import { describe, expect, it } from '@ohos/hypium'
import {
  MusicPlayMethod,
  MusicPlaybackCandidateStatus,
  MusicPlaybackErrorCode,
  MusicPlaybackStatus,
  MusicQueueItem,
  MusicResolvedSource
} from '../main/ets/features/music/MusicModels'
import { MusicPlaybackMachine } from '../main/ets/features/music/MusicPlaybackMachine'
import { MusicQueueSnapshot } from '../main/ets/features/music/MusicQueuePolicy'

function item(id: string): MusicQueueItem {
  return {
    id: id,
    title: id,
    artist: 'Artist',
    album: 'Album',
    albumId: 'album-1',
    imageUrl: '',
    durationMs: 1000,
    favorite: false,
    playbackPositionMs: 0,
    serverId: 'server|user'
  }
}

function queue(id: string): MusicQueueSnapshot {
  return { items: [item(id)], currentIndex: 0, scope: 'server|user' }
}

function source(id: string): MusicResolvedSource {
  return {
    itemId: id,
    mediaSourceId: `source-${id}`,
    playSessionId: `session-${id}`,
    liveStreamId: '',
    url: `https://music/${id}`,
    playMethod: MusicPlayMethod.DirectPlay,
    durationMs: 1000,
    startPositionMs: 0,
    scope: 'server|user'
  }
}

export default function musicPlaybackMachineTest() {
  describe('MusicPlaybackMachine', () => {
    it('commitsANewQueueOnlyAfterTheFirstSourceIsPrepared', 0, () => {
      const machine = new MusicPlaybackMachine(MusicPlaybackMachine.emptySnapshot())
      const activeGeneration = machine.beginQueueCandidate(queue('old'))
      machine.commitPreparation(activeGeneration, source('old'))
      machine.onPlaying(activeGeneration)
      const generation = machine.beginQueueCandidate(queue('new'))

      machine.failPreparation(generation, MusicPlaybackErrorCode.NoPlayableSource)
      expect(machine.snapshot().queue[0].id).assertEqual('old')
      expect(machine.failedCandidate()?.items[0].id).assertEqual('new')
    })

    it('ignoresEventsFromAnOldEngineGeneration', 0, () => {
      const machine = new MusicPlaybackMachine(MusicPlaybackMachine.emptySnapshot())
      const oldGeneration = machine.beginQueueCandidate(queue('old'))
      const currentGeneration = machine.beginQueueCandidate(queue('new'))
      machine.commitPreparation(currentGeneration, source('new'))

      machine.onPlaying(oldGeneration, 1000)
      expect(machine.snapshot().status).assertEqual(MusicPlaybackStatus.Preparing)
      machine.onPlaying(currentGeneration, 1000)
      expect(machine.snapshot().status).assertEqual(MusicPlaybackStatus.Playing)
    })

    it('tracksPauseSeekBufferAndCompletion', 0, () => {
      const machine = new MusicPlaybackMachine(MusicPlaybackMachine.emptySnapshot())
      const generation = machine.beginQueueCandidate(queue('a'))
      machine.commitPreparation(generation, source('a'))
      machine.onPlaying(generation)
      machine.onTimeUpdate(generation, 400)
      machine.onPaused(generation)
      expect(machine.snapshot().positionMs).assertEqual(400)
      expect(machine.snapshot().status).assertEqual(MusicPlaybackStatus.Paused)
      machine.onBuffering(generation)
      expect(machine.snapshot().status).assertEqual(MusicPlaybackStatus.Buffering)
      machine.onCompleted(generation)
      expect(machine.snapshot().positionMs).assertEqual(1000)
    })

    it('keepsTheFailedCandidateForRetryAndSkip', 0, () => {
      const machine = new MusicPlaybackMachine(MusicPlaybackMachine.emptySnapshot())
      const failedQueue: MusicQueueSnapshot = {
        items: [item('a'), item('b')],
        currentIndex: 0,
        scope: 'server|user'
      }
      const generation = machine.beginQueueCandidate(failedQueue)

      machine.failPreparation(generation, MusicPlaybackErrorCode.SystemPlayer, 'prepare failed')

      expect(machine.failedCandidate()?.currentIndex).assertEqual(0)
      expect(machine.failedCandidate()?.items.length).assertEqual(2)
      expect(machine.snapshot().status).assertEqual(MusicPlaybackStatus.Error)
      expect(machine.snapshot().queue[0].id).assertEqual('a')
    })

    it('keepsActiveGenerationWhileANewCandidateIsPreparing', 0, () => {
      const machine = new MusicPlaybackMachine(MusicPlaybackMachine.emptySnapshot())
      const activeGeneration = machine.beginQueueCandidate(queue('old'))
      machine.commitPreparation(activeGeneration, source('old'))
      machine.onPlaying(activeGeneration)

      const requestGeneration = machine.beginQueueCandidate(queue('new'))
      machine.onTimeUpdate(activeGeneration, 500)

      expect(requestGeneration === activeGeneration).assertFalse()
      expect(machine.currentGeneration()).assertEqual(activeGeneration)
      expect(machine.snapshot().status).assertEqual(MusicPlaybackStatus.Playing)
      expect(machine.snapshot().candidateItemId).assertEqual('new')
      machine.failPreparation(requestGeneration, MusicPlaybackErrorCode.Network, 'offline')
      expect(machine.snapshot().status).assertEqual(MusicPlaybackStatus.Playing)
      expect(machine.snapshot().candidateStatus).assertEqual(MusicPlaybackCandidateStatus.Error)
      expect(machine.failedCandidate()?.items[0].id).assertEqual('new')
      expect(machine.snapshot().positionMs).assertEqual(500)
    })

    it('keepsCompletedAndIdleActiveSnapshotsWhenTheSuccessorFails', 0, () => {
      const machine = new MusicPlaybackMachine(MusicPlaybackMachine.emptySnapshot())
      const activeGeneration = machine.beginQueueCandidate(queue('old'))
      machine.commitPreparation(activeGeneration, source('old'))
      machine.onCompleted(activeGeneration)

      const completedRequest = machine.beginQueueCandidate(queue('new'))
      machine.failPreparation(completedRequest, MusicPlaybackErrorCode.Network, 'offline')
      expect(machine.snapshot().queue[0].id).assertEqual('old')
      expect(machine.snapshot().source?.itemId).assertEqual('old')
      expect(machine.snapshot().status).assertEqual(MusicPlaybackStatus.Completed)

      machine.onStopped(activeGeneration)
      const idleRequest = machine.beginQueueCandidate(queue('newer'))
      machine.failPreparation(idleRequest, MusicPlaybackErrorCode.Network, 'offline')
      expect(machine.snapshot().queue[0].id).assertEqual('old')
      expect(machine.snapshot().source?.itemId).assertEqual('old')
      expect(machine.snapshot().status).assertEqual(MusicPlaybackStatus.Idle)
    })

    it('cancelsPendingCandidatesWithoutDroppingTheActiveSnapshot', 0, () => {
      const machine = new MusicPlaybackMachine(MusicPlaybackMachine.emptySnapshot())
      const activeGeneration = machine.beginQueueCandidate(queue('old'))
      machine.commitPreparation(activeGeneration, source('old'))
      machine.onPlaying(activeGeneration)
      const pendingGeneration = machine.beginQueueCandidate(queue('new'))

      expect(machine.cancelPending()).assertEqual(pendingGeneration)
      expect(machine.currentRequestGeneration() === pendingGeneration).assertFalse()
      expect(machine.snapshot().candidateStatus).assertEqual(MusicPlaybackCandidateStatus.None)
      expect(machine.snapshot().queue[0].id).assertEqual('old')
      expect(machine.snapshot().status).assertEqual(MusicPlaybackStatus.Playing)
    })
  })
}
