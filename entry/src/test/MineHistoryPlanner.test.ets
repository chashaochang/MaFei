import { describe, expect, it } from '@ohos/hypium'
import {
  MineHistoryCandidate,
  MineHistoryMediaType,
  MineHistoryPlanner,
  MineHistorySeriesEpisodeCandidate
} from '../main/ets/features/home/minetab/MineHistoryPlanner'

function candidate(
  id: string,
  lastPlayedDate: string,
  played: boolean,
  percent: number,
  mediaType: MineHistoryMediaType = MineHistoryMediaType.Movie,
  communityRating?: number,
  seasonNumber?: number,
  episodeNumber?: number
): MineHistoryCandidate {
  return {
    id,
    name: id,
    image: `${id}.jpg`,
    lastPlayedDate,
    played,
    percent,
    mediaType,
    communityRating,
    seriesId: mediaType === MineHistoryMediaType.Episode ? `${id}-series` : '',
    seasonNumber,
    episodeNumber,
    latestSeasonNumber: undefined,
    latestEpisodeNumber: undefined,
    seriesHasMultipleSeasons: false
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

    it('deduplicatesEpisodesBySeriesAndKeepsLatestPlayedEpisode', 0, () => {
      const olderEpisode = candidate('episode-2', '2026-08-01T09:00:00Z', true, 100,
        MineHistoryMediaType.Episode, undefined, 1, 2)
      const latestEpisode = candidate('episode-3', '2026-08-02T09:00:00Z', false, 35,
        MineHistoryMediaType.Episode, undefined, 1, 3)
      olderEpisode.seriesId = 'series-a'
      latestEpisode.seriesId = 'series-a'

      const result = new MineHistoryPlanner().merge([latestEpisode], [olderEpisode], 8)

      expect(result.length).assertEqual(1)
      expect(result[0].id).assertEqual('episode-3')
      expect(result[0].episodeNumber).assertEqual(3)
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
      unnamed.name = '   '
      const result = new MineHistoryPlanner().merge([unnamed], [], 6)

      expect(result[0].name).assertEqual('')
      expect(result[0].percent).assertEqual(100)
    })

    it('normalizesMovieRatingToOneDecimalAndRejectsInvalidRatings', 0, () => {
      const rated = candidate('rated', '2026-08-02T10:00:00Z', true, 100,
        MineHistoryMediaType.Movie, 8.74)
      const unrated = candidate('unrated', '2026-08-01T10:00:00Z', true, 100,
        MineHistoryMediaType.Movie, -1)
      const outOfRange = candidate('out-of-range', '2026-07-31T10:00:00Z', true, 100,
        MineHistoryMediaType.Movie, 10.1)
      const result = new MineHistoryPlanner().merge([rated, unrated, outOfRange], [], 8)

      expect(result[0].communityRating).assertEqual(8.7)
      expect(result[1].communityRating === undefined).assertTrue()
      expect(result[2].communityRating === undefined).assertTrue()
    })

    it('keepsEpisodeNumbersAndSeriesUpdateMetadata', 0, () => {
      const episode = candidate('episode', '2026-08-02T10:00:00Z', false, 35,
        MineHistoryMediaType.Episode, undefined, 2, 3)
      episode.latestSeasonNumber = 4
      episode.latestEpisodeNumber = 9
      episode.seriesHasMultipleSeasons = true

      const result = new MineHistoryPlanner().merge([episode], [], 8)

      expect(result[0].seasonNumber).assertEqual(2)
      expect(result[0].episodeNumber).assertEqual(3)
      expect(result[0].latestSeasonNumber).assertEqual(4)
      expect(result[0].latestEpisodeNumber).assertEqual(9)
      expect(result[0].seriesHasMultipleSeasons).assertTrue()
    })

    it('findsLatestRegularEpisodeAndCountsDistinctSeasons', 0, () => {
      const episodes: MineHistorySeriesEpisodeCandidate[] = [
        { seasonNumber: 0, episodeNumber: 2 },
        { seasonNumber: 1, episodeNumber: 12 },
        { seasonNumber: 2, episodeNumber: 3 },
        { seasonNumber: 2, episodeNumber: 8 }
      ]
      const result = new MineHistoryPlanner().latestSeriesPosition(episodes)

      expect(result?.seasonNumber).assertEqual(2)
      expect(result?.episodeNumber).assertEqual(8)
      expect(result?.hasMultipleSeasons).assertTrue()
    })

    it('returnsUndefinedWhenSeriesHasNoNumberedEpisode', 0, () => {
      const result = new MineHistoryPlanner().latestSeriesPosition([
        { seasonNumber: undefined, episodeNumber: undefined },
        { seasonNumber: 0, episodeNumber: 12 },
        { seasonNumber: 1, episodeNumber: 0 }
      ])
      expect(result === undefined).assertTrue()
    })
  })
}
