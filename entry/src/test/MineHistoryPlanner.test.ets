import { describe, expect, it } from '@ohos/hypium'
import {
  MineHistoryCandidate,
  MineHistoryPlanner
} from '../main/ets/features/home/minetab/MineHistoryPlanner'

function candidate(
  id: string,
  lastPlayedDate: string,
  played: boolean,
  percent: number
): MineHistoryCandidate {
  return {
    id,
    name: id,
    image: `${id}.jpg`,
    lastPlayedDate,
    played,
    percent
  }
}

export default function mineHistoryPlannerTest() {
  describe('MineHistoryPlanner', () => {
    it('mergesCompletedAndResumeByLatestPlayback', 0, () => {
      const result = new MineHistoryPlanner().merge([
        candidate('episode-1', '2026-08-01T08:00:00Z', false, 45)
      ], [
        candidate('movie-1', '2026-08-02T08:00:00Z', true, 100)
      ], 6)

      expect(result.length).assertEqual(2)
      expect(result[0].id).assertEqual('movie-1')
      expect(result[1].id).assertEqual('episode-1')
    })

    it('deduplicatesAndKeepsFreshestCandidate', 0, () => {
      const result = new MineHistoryPlanner().merge([
        candidate('same', '2026-08-02T09:00:00Z', false, 80)
      ], [
        candidate('same', '2026-08-01T09:00:00Z', true, 100)
      ], 6)

      expect(result.length).assertEqual(1)
      expect(result[0].played).assertFalse()
      expect(result[0].percent).assertEqual(80)
    })

    it('dropsInvalidDatesAndHonorsLimit', 0, () => {
      const result = new MineHistoryPlanner().merge([
        candidate('invalid', '', false, 10),
        candidate('new', '2026-08-02T10:00:00Z', false, 20),
        candidate('old', '2026-08-01T10:00:00Z', false, 30)
      ], [], 1)

      expect(result.length).assertEqual(1)
      expect(result[0].id).assertEqual('new')
    })

    it('normalizesTitleAndPercent', 0, () => {
      const unnamed: MineHistoryCandidate = candidate('unnamed', '2026-08-02T10:00:00Z', false, 135)
      unnamed.name = ''
      const result = new MineHistoryPlanner().merge([unnamed], [], 6)

      expect(result[0].name).assertEqual('未命名内容')
      expect(result[0].percent).assertEqual(100)
    })
  })
}
