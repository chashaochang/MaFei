import { describe, expect, it } from '@ohos/hypium'
import {
  millisecondsToMusicTicks,
  MusicLibrarySection,
  musicTicksToMilliseconds
} from '../main/ets/features/music/MusicModels'
import { MusicQueryPlanner } from '../main/ets/features/music/MusicQueryPlanner'

export default function musicModelsTest() {
  describe('MusicModels', () => {
    it('convertsJellyfinTicksWithoutRoundingUp', 0, () => {
      expect(musicTicksToMilliseconds(123456)).assertEqual(12)
      expect(musicTicksToMilliseconds(undefined)).assertEqual(0)
      expect(musicTicksToMilliseconds(Number.NaN)).assertEqual(0)
      expect(millisecondsToMusicTicks(12)).assertEqual(120000)
      expect(millisecondsToMusicTicks(-1)).assertEqual(0)
    })

    it('keepsSortOrdersAlignedWithSortFields', 0, () => {
      const sorts = MusicQueryPlanner.sortBy(MusicLibrarySection.Tracks)
      const orders = MusicQueryPlanner.sortOrder(MusicLibrarySection.Tracks)
      expect(sorts.length).assertEqual(orders.length)
    })
  })
}
