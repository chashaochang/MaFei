import { describe, expect, it } from '@ohos/hypium'
import { FnMediaItem } from '../main/ets/features/feiniuvideo/catalog/FeiniuVideoDtos'
import {
  FeiniuVideoRecommendationPlanner
} from '../main/ets/features/feiniuvideo/catalog/FeiniuVideoRecommendationPlanner'

function candidate(id: string, type: string, rating: string, year: number,
  genres: number[], watched: number = 0): FnMediaItem {
  return {
    guid: id,
    title: id,
    type: type,
    poster: `/${id}.jpg`,
    vote_average: rating,
    release_date: `${year}-01-01`,
    genres: genres,
    watched: watched
  }
}

export default function feiniuVideoRecommendationPlannerTest() {
  describe('FeiniuVideoRecommendationPlanner', () => {
    it('usesUnfinishedGenresWithoutRepeatingContinueWatchingItems', 0, () => {
      const planner = new FeiniuVideoRecommendationPlanner()
      const favorites = [candidate('favorite', 'Movie', '8.0', 2024, [10])]
      const unfinished = [candidate('unfinished', 'Movie', '9.8', 2026, [10])]
      const completed = [candidate('completed', 'Movie', '9.7', 2026, [20], 1)]
      const latest = [
        unfinished[0],
        completed[0],
        candidate('genre-match', 'Movie', '8.2', 2025, [10]),
        candidate('series', 'TV', '8.5', 2025, [30])
      ]
      const highestRated = [candidate('high-score', 'Movie', '9.2', 2025, [20])]

      const result = planner.rank(
        latest, highestRated, unfinished, completed, favorites, 3, 'account|2026-08-21')

      expect(result.length).assertEqual(3)
      expect(result[0].guid).assertEqual('genre-match')
      expect(result.map((item: FnMediaItem): string => item.guid).indexOf('unfinished') >= 0).assertFalse()
      expect(result.map((item: FnMediaItem): string => item.guid).indexOf('completed') >= 0).assertFalse()
    })

    it('prioritizesLatestItemsWhenThereIsNoPersonalHistory', 0, () => {
      const planner = new FeiniuVideoRecommendationPlanner()
      const latest = [
        candidate('latest-first', 'Movie', '7.0', 2024, [10]),
        candidate('latest-second', 'TV', '7.5', 2024, [20])
      ]
      const highestRated = [candidate('older-high-score', 'Movie', '9.9', 2010, [30])]

      const result = planner.rank(latest, highestRated, [], [], [], 3, 'cold-start')

      expect(result[0].guid).assertEqual('latest-first')
      expect(result[1].guid).assertEqual('latest-second')
    })

    it('deduplicatesAndKeepsMovieSeriesDiversityWithStableOrdering', 0, () => {
      const planner = new FeiniuVideoRecommendationPlanner()
      const source = [
        candidate('movie-1', 'Movie', '9.5', 2026, [10]),
        candidate('movie-1', 'Movie', '9.5', 2026, [10]),
        candidate('movie-2', 'Movie', '9.4', 2026, [10]),
        candidate('movie-3', 'Movie', '9.3', 2026, [10]),
        candidate('series-1', 'TV', '8.8', 2026, [20]),
        candidate('series-2', 'TV', '8.7', 2026, [30])
      ]

      const first = planner.rank(source, [], [], [], [], 4, 'stable-seed')
      const second = planner.rank(source, [], [], [], [], 4, 'stable-seed')

      expect(first.map((item: FnMediaItem): string => item.guid).join(','))
        .assertEqual(second.map((item: FnMediaItem): string => item.guid).join(','))
      const identities = new Set<string>()
      first.forEach((item: FnMediaItem): void => {
        identities.add(item.guid)
      })
      expect(identities.size).assertEqual(4)
      expect(first.some((item: FnMediaItem): boolean => item.type === 'TV')).assertTrue()
    })

    it('rejectsEpisodesAndItemsWithoutHeroArtwork', 0, () => {
      const planner = new FeiniuVideoRecommendationPlanner()
      const withoutPoster = candidate('no-poster', 'Movie', '9.0', 2026, [10])
      withoutPoster.poster = ''
      const result = planner.rank([
        candidate('episode', 'Episode', '9.5', 2026, [10]),
        withoutPoster,
        candidate('movie', 'Movie', '8.0', 2025, [20])
      ], [], [], [], [], 6, 'seed')

      expect(result.length).assertEqual(1)
      expect(result[0].guid).assertEqual('movie')
    })
  })
}
