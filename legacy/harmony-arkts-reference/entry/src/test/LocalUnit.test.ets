import { describe, it, expect } from '@ohos/hypium';
import { ChasePlanner, ChasePlannerItem } from '../main/ets/features/home/chasing/ChasePlanner';
import { MediaProviderKind } from '../main/ets/media/MediaProviderKind';
import { MediaRef, MediaRefKind } from '../main/ets/media/MediaRef';

export default function localUnitTest() {
  describe('ChasePlanner', () => {
    it('buildsEmptySections', 0, () => {
      const result = new ChasePlanner().plan([], [], [])

      expect(result.summary).assertEqual('暂无追剧内容')
      expect(result.totalCount).assertEqual(0)
      expect(result.sections.length).assertEqual(3)
      expect(result.sections[0].subtitle).assertEqual('暂无需要续播的内容')
      expect(result.sections[1].subtitle).assertEqual('暂无接下来可播放的剧集')
    })

    it('deduplicatesNextUpAndNormalizesItems', 0, () => {
      const continueItems: ChasePlannerItem[] = [
        { id: 'episode-1', name: '', image: 'resume.jpg', percent: 120 }
      ]
      const nextUpItems: ChasePlannerItem[] = [
        { id: 'episode-1', name: '重复剧集', image: 'duplicate.jpg', percent: 0 },
        { id: 'episode-2', name: '下一集', image: 'next.jpg', percent: 0 }
      ]

      const result = new ChasePlanner().plan(continueItems, nextUpItems, [])

      expect(result.summary).assertEqual('继续观看 1 · 接下来 1')
      expect(result.totalCount).assertEqual(2)
      expect(result.sections[0].items[0].name).assertEqual('未命名内容')
      expect(result.sections[0].items[0].percent).assertEqual(100)
      expect(result.sections[1].items.length).assertEqual(1)
      expect(result.sections[1].items[0].id).assertEqual('episode-2')
    })

    it('classifiesOnlyFullyWatchedFollowedSeriesAsCompleted', 0, () => {
      const result = new ChasePlanner().plan(
        [{ id: 'episode-a', seriesId: 'series-a', name: 'A', image: '' }],
        [],
        [
          { id: 'series-a', name: 'A', image: '', unplayedItemCount: 0 },
          { id: 'series-b', name: 'B', image: '', unplayedItemCount: 0 },
          { id: 'series-c', name: 'C', image: '', unplayedItemCount: 2 },
          { id: 'series-d', name: 'D', image: '' }
        ]
      )

      expect(result.sections.length).assertEqual(3)
      expect(result.sections[2].kind).assertEqual('completed')
      expect(result.sections[2].items.length).assertEqual(1)
      expect(result.sections[2].items[0].id).assertEqual('series-b')
    })

    it('usesPlayedFallbackOnlyWhenUnplayedCountIsAbsent', 0, () => {
      const result = new ChasePlanner().plan(
        [],
        [],
        [
          { id: 'series-played', name: 'Played', image: '', played: true },
          { id: 'series-not-played', name: 'Not played', image: '', played: false },
          { id: 'series-count-wins', name: 'Count wins', image: '', unplayedItemCount: 1, played: true }
        ]
      )

      expect(result.sections[2].items.length).assertEqual(1)
      expect(result.sections[2].items[0].id).assertEqual('series-played')
    })

    it('countsCompletedOnlyResults', 0, () => {
      const result = new ChasePlanner().plan(
        [],
        [],
        [{ id: 'series-completed', name: 'Completed', image: '', unplayedItemCount: 0 }]
      )

      expect(result.totalCount).assertEqual(1)
    })

    it('preservesMediaReferencesAcrossNormalizedSections', 0, () => {
      const continueRef = new MediaRef(MediaProviderKind.Jellyfin, 'account', 'episode-continue',
        MediaRefKind.Episode)
      const nextUpRef = new MediaRef(MediaProviderKind.Jellyfin, 'account', 'episode-next',
        MediaRefKind.Episode)
      const completedRef = new MediaRef(MediaProviderKind.Jellyfin, 'account', 'series-completed',
        MediaRefKind.Series)

      const result = new ChasePlanner().plan(
        [{ id: 'episode-continue', name: 'Continue', image: '', mediaRef: continueRef }],
        [{ id: 'episode-next', name: 'Next', image: '', mediaRef: nextUpRef }],
        [{ id: 'series-completed', name: 'Completed', image: '', mediaRef: completedRef,
          unplayedItemCount: 0 }]
      )

      expect(result.sections[0].items[0].mediaRef === continueRef).assertTrue()
      expect(result.sections[1].items[0].mediaRef === nextUpRef).assertTrue()
      expect(result.sections[2].items[0].mediaRef === completedRef).assertTrue()
    })
  })
}
