export enum MineHistoryMediaType {
  Movie = 'Movie',
  Episode = 'Episode'
}

export interface MineHistorySeriesEpisodeCandidate {
  seasonNumber?: number
  episodeNumber?: number
}

export interface MineHistorySeriesPosition {
  seasonNumber: number
  episodeNumber: number
  hasMultipleSeasons: boolean
}

export interface MineHistoryCandidate {
  /** Jellyfin item identifier. */
  id: string
  /** Single-line display title. */
  name: string
  /** Resolved poster URL. */
  image: string
  /** Jellyfin user-data playback timestamp. */
  lastPlayedDate: string
  /** Whether Jellyfin considers the item fully played. */
  played: boolean
  /** Current playback percentage. */
  percent: number
  /** Supported history media kind. */
  mediaType: MineHistoryMediaType
  /** Movie community rating when valid. */
  communityRating?: number
  /** Owning series identifier for episode history. */
  seriesId: string
  /** Current episode season number. */
  seasonNumber?: number
  /** Current episode number. */
  episodeNumber?: number
  /** Latest available season number for the series. */
  latestSeasonNumber?: number
  /** Latest available episode number for the series. */
  latestEpisodeNumber?: number
  /** Whether the series has multiple regular seasons. */
  seriesHasMultipleSeasons: boolean
}

export interface MineHistoryItem extends MineHistoryCandidate {
  /** Parsed timestamp used for deterministic sorting. */
  playedAt: number
}

export class MineHistoryPlanner {
  merge(resume: MineHistoryCandidate[], completed: MineHistoryCandidate[], limit: number): MineHistoryItem[] {
    const byHistoryKey: Map<string, MineHistoryItem> = new Map<string, MineHistoryItem>()
    resume.concat(completed).forEach((candidate: MineHistoryCandidate) => {
      const playedAt = Date.parse(candidate.lastPlayedDate)
      if (candidate.id.length === 0 || !Number.isFinite(playedAt)) {
        return
      }
      const normalized: MineHistoryItem = {
        id: candidate.id,
        name: candidate.name.trim(),
        image: candidate.image,
        lastPlayedDate: candidate.lastPlayedDate,
        played: candidate.played,
        percent: Math.min(100, Math.max(0, Math.round(candidate.percent))),
        mediaType: candidate.mediaType,
        communityRating: this.normalizedRating(candidate.mediaType, candidate.communityRating),
        seriesId: candidate.seriesId,
        seasonNumber: this.positiveInteger(candidate.seasonNumber),
        episodeNumber: this.positiveInteger(candidate.episodeNumber),
        latestSeasonNumber: this.positiveInteger(candidate.latestSeasonNumber),
        latestEpisodeNumber: this.positiveInteger(candidate.latestEpisodeNumber),
        seriesHasMultipleSeasons: candidate.seriesHasMultipleSeasons,
        playedAt
      }
      const historyKey = this.historyKey(normalized)
      const current = byHistoryKey.get(historyKey)
      if (!current || normalized.playedAt > current.playedAt) {
        byHistoryKey.set(historyKey, normalized)
      }
    })
    const items: MineHistoryItem[] = []
    byHistoryKey.forEach((item: MineHistoryItem) => {
      items.push(item)
    })
    items.sort((left: MineHistoryItem, right: MineHistoryItem) => right.playedAt - left.playedAt)
    return items.slice(0, Math.max(0, limit))
  }

  private historyKey(item: MineHistoryItem): string {
    if (item.mediaType === MineHistoryMediaType.Episode && item.seriesId.length > 0) {
      return `series:${item.seriesId}`
    }
    return `item:${item.id}`
  }

  latestSeriesPosition(candidates: MineHistorySeriesEpisodeCandidate[]): MineHistorySeriesPosition | undefined {
    let latestSeason = -1
    let latestEpisode = -1
    const regularSeasons: Map<number, boolean> = new Map<number, boolean>()
    candidates.forEach((candidate: MineHistorySeriesEpisodeCandidate) => {
      const seasonNumber = this.positiveInteger(candidate.seasonNumber)
      const episodeNumber = this.positiveInteger(candidate.episodeNumber)
      if (seasonNumber === undefined || episodeNumber === undefined) {
        return
      }
      regularSeasons.set(seasonNumber, true)
      if (seasonNumber > latestSeason ||
        (seasonNumber === latestSeason && episodeNumber > latestEpisode)) {
        latestSeason = seasonNumber
        latestEpisode = episodeNumber
      }
    })
    if (latestEpisode <= 0) {
      return undefined
    }
    return {
      seasonNumber: latestSeason,
      episodeNumber: latestEpisode,
      hasMultipleSeasons: regularSeasons.size > 1 || latestSeason > 1
    }
  }

  private positiveInteger(value?: number): number | undefined {
    if (value === undefined || !Number.isFinite(value) || value <= 0) {
      return undefined
    }
    return Math.floor(value)
  }

  private normalizedRating(mediaType: MineHistoryMediaType, value?: number): number | undefined {
    if (mediaType !== MineHistoryMediaType.Movie || value === undefined ||
      !Number.isFinite(value) || value <= 0 || value > 10) {
      return undefined
    }
    return Math.round(value * 10) / 10
  }
}
