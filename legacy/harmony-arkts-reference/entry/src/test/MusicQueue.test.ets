import { describe, expect, it } from '@ohos/hypium'
import { MusicRepeatMode, MusicTrack } from '../main/ets/features/music/MusicModels'
import { MusicQueuePolicy, MusicQueueSnapshot } from '../main/ets/features/music/MusicQueuePolicy'

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
    durationMs: 1000,
    imageUrl: '',
    favorite: false,
    playbackPositionMs: 0
  }
}

function queueAt(ids: string[], index: number, scope: string = 'server-a|user-a'): MusicQueueSnapshot {
  return MusicQueuePolicy.create(ids.map((id: string): MusicTrack => track(id)), index, scope)
}

export default function musicQueueTest() {
  describe('MusicQueuePolicy', () => {
    it('buildsAQueueAtTheClickedTrack', 0, () => {
      const queue = queueAt(['a', 'b', 'c'], 1)
      expect(queue.currentIndex).assertEqual(1)
      expect(queue.items[1].id).assertEqual('b')
    })

    it('refusesToRemoveTheCurrentTrack', 0, () => {
      const result = MusicQueuePolicy.removeAt(queueAt(['a', 'b', 'c'], 1), 1)
      expect(result.changed).assertFalse()
      expect(result.queue.items.length).assertEqual(3)
    })

    it('keepsCurrentTrackStableWhenRemovingOtherEntries', 0, () => {
      const before = MusicQueuePolicy.removeAt(queueAt(['a', 'b', 'c'], 2), 0)
      expect(before.queue.currentIndex).assertEqual(1)
      expect(before.queue.items[1].id).assertEqual('c')
      const after = MusicQueuePolicy.removeAt(queueAt(['a', 'b', 'c'], 0), 2)
      expect(after.queue.currentIndex).assertEqual(0)
      expect(after.queue.items[0].id).assertEqual('a')
    })

    it('usesExplicitBoundariesAndRepeatTargets', 0, () => {
      const queue = queueAt(['a', 'b'], 1)
      expect(MusicQueuePolicy.nextIndex(queue)).assertEqual(-1)
      expect(MusicQueuePolicy.previousIndex(queue)).assertEqual(0)
      expect(MusicQueuePolicy.completionIndex(queue, MusicRepeatMode.Off)).assertEqual(-1)
      expect(MusicQueuePolicy.completionIndex(queue, MusicRepeatMode.All)).assertEqual(0)
      expect(MusicQueuePolicy.completionIndex(queue, MusicRepeatMode.One)).assertEqual(1)
    })

    it('rejectsInvalidQueuesAndOtherServerScopes', 0, () => {
      expect(queueAt(['a'], 4).items.length).assertEqual(0)
      const queue = queueAt(['a'], 0)
      expect(MusicQueuePolicy.jumpIndex(queue, 3)).assertEqual(-1)
      expect(MusicQueuePolicy.matchesScope(queue, 'server-b|user-a')).assertFalse()
    })
  })
}
