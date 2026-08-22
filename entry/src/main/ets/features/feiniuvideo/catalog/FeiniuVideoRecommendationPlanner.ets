import { FnMediaItem } from './FeiniuVideoDtos'

class FeiniuVideoScoredRecommendation {
  readonly item: FnMediaItem
  readonly score: number
  readonly tieBreaker: number

  constructor(item: FnMediaItem, score: number, tieBreaker: number) {
    this.item = item
    this.score = score
    this.tieBreaker = tieBreaker
  }
}

export class FeiniuVideoRecommendationPlanner {
  rank(latest: FnMediaItem[], highestRated: FnMediaItem[], unfinished: FnMediaItem[],
    completed: FnMediaItem[], favorites: FnMediaItem[], resultLimit: number, seed: string): FnMediaItem[] {
    const limit = Math.max(0, Math.floor(resultLimit))
    if (limit === 0) return []

    const unfinishedIds = this.identities(unfinished)
    const completedIds = this.identities(completed)
    const favoriteIds = this.identities(favorites)
    const genreWeights = new Map<number, number>()
    this.addGenreWeights(genreWeights, unfinished, 4)
    this.addGenreWeights(genreWeights, favorites, 2)

    const latestWeights = this.sourceWeights(latest, 54, 30)
    const highestRatedWeights = this.sourceWeights(highestRated, 18, 6)
    const candidates = this.uniqueCandidates(latest.concat(highestRated))
      .filter((item: FnMediaItem): boolean => !unfinishedIds.has(item.guid) && !completedIds.has(item.guid))
    const scored = candidates.map((item: FnMediaItem): FeiniuVideoScoredRecommendation => {
      return new FeiniuVideoScoredRecommendation(
        item,
        this.score(item, favoriteIds, genreWeights,
          latestWeights.get(item.guid) || 0, highestRatedWeights.get(item.guid) || 0),
        this.stableHash(`${seed}|${item.guid}`)
      )
    })
    scored.sort((left: FeiniuVideoScoredRecommendation,
      right: FeiniuVideoScoredRecommendation): number => {
      if (left.score !== right.score) return right.score - left.score
      if (left.tieBreaker !== right.tieBreaker) return right.tieBreaker - left.tieBreaker
      return left.item.guid.localeCompare(right.item.guid)
    })
    return this.diverseTake(scored, limit)
  }

  private sourceWeights(items: FnMediaItem[], firstWeight: number, lastWeight: number): Map<string, number> {
    const result = new Map<string, number>()
    const denominator = Math.max(1, items.length - 1)
    items.forEach((item: FnMediaItem, index: number): void => {
      const id = item.guid.trim()
      if (id.length === 0) return
      const ratio = index / denominator
      const weight = firstWeight - (firstWeight - lastWeight) * ratio
      result.set(id, Math.max(result.get(id) || 0, weight))
    })
    return result
  }

  private uniqueCandidates(items: FnMediaItem[]): FnMediaItem[] {
    const result: FnMediaItem[] = []
    const indexes = new Map<string, number>()
    items.forEach((item: FnMediaItem): void => {
      if (!this.isHeroCandidate(item)) return
      const existingIndex = indexes.get(item.guid)
      if (existingIndex === undefined) {
        indexes.set(item.guid, result.length)
        result.push(item)
      } else if (this.metadataWeight(item) > this.metadataWeight(result[existingIndex])) {
        result[existingIndex] = item
      }
    })
    return result
  }

  private isHeroCandidate(item: FnMediaItem): boolean {
    const type = `${item.type || ''}`.trim().toLowerCase()
    return item.guid.trim().length > 0 && `${item.title || ''}`.trim().length > 0 &&
      `${item.poster || ''}`.trim().length > 0 &&
      (type === 'movie' || type === 'tv' || type === 'series')
  }

  private metadataWeight(item: FnMediaItem): number {
    let weight = 0
    if (`${item.vote_average || ''}`.trim().length > 0) weight += 1
    if (this.year(item) > 0) weight += 1
    if ((item.genres || []).length > 0) weight += 1
    if ((item.media_stream?.resolutions || []).length > 0) weight += 1
    return weight
  }

  private identities(items: FnMediaItem[]): Set<string> {
    const result = new Set<string>()
    items.forEach((item: FnMediaItem): void => {
      const id = item.guid.trim()
      if (id.length > 0) result.add(id)
    })
    return result
  }

  private addGenreWeights(target: Map<number, number>, items: FnMediaItem[], weight: number): void {
    items.forEach((item: FnMediaItem): void => {
      const genres = item.genres || []
      genres.forEach((genre: number): void => {
        if (genre > 0) target.set(genre, (target.get(genre) || 0) + weight)
      })
    })
  }

  private score(item: FnMediaItem, favoriteIds: Set<string>, genreWeights: Map<number, number>,
    latestWeight: number, highestRatedWeight: number): number {
    let score = latestWeight + highestRatedWeight + this.rating(item) * 3
    const currentYear = new Date().getFullYear()
    const year = this.year(item)
    if (year > 0) {
      const age = Math.max(0, currentYear - year)
      if (age <= 2) score += 10
      else if (age <= 6) score += 6
      else if (age <= 12) score += 2
    }
    let affinity = 0
    const genres = item.genres || []
    genres.forEach((genre: number): void => {
      affinity += genreWeights.get(genre) || 0
    })
    score += Math.min(30, affinity * 3)
    if (favoriteIds.has(item.guid) || (item.is_favorite || 0) > 0) score -= 8
    return score
  }

  private diverseTake(scored: FeiniuVideoScoredRecommendation[], limit: number): FnMediaItem[] {
    const result: FnMediaItem[] = []
    const selectedIds = new Set<string>()
    const kindCounts = new Map<string, number>()
    const genreCounts = new Map<number, number>()
    const maxKindCount = Math.max(1, Math.ceil(limit * 0.67))
    scored.forEach((candidate: FeiniuVideoScoredRecommendation): void => {
      if (result.length >= limit) return
      const item = candidate.item
      const kind = this.kind(item)
      const primaryGenre = (item.genres || []).find((genre: number): boolean => genre > 0)
      if ((kindCounts.get(kind) || 0) >= maxKindCount) return
      if (primaryGenre !== undefined && (genreCounts.get(primaryGenre) || 0) >= 2) return
      result.push(item)
      selectedIds.add(item.guid)
      kindCounts.set(kind, (kindCounts.get(kind) || 0) + 1)
      if (primaryGenre !== undefined) {
        genreCounts.set(primaryGenre, (genreCounts.get(primaryGenre) || 0) + 1)
      }
    })
    scored.forEach((candidate: FeiniuVideoScoredRecommendation): void => {
      if (result.length < limit && !selectedIds.has(candidate.item.guid)) {
        result.push(candidate.item)
        selectedIds.add(candidate.item.guid)
      }
    })
    return result
  }

  private kind(item: FnMediaItem): string {
    const type = `${item.type || ''}`.trim().toLowerCase()
    return type === 'tv' || type === 'series' ? 'series' : 'movie'
  }

  private rating(item: FnMediaItem): number {
    const value = Number(`${item.vote_average || ''}`.trim())
    return Number.isFinite(value) && value > 0 ? Math.min(10, value) : 0
  }

  private year(item: FnMediaItem): number {
    const source = `${item.release_date || item.first_air_date || item.air_date || item.last_air_date || ''}`.trim()
    if (source.length < 4) return 0
    const value = Number(source.substring(0, 4))
    return Number.isFinite(value) && value > 0 ? Math.floor(value) : 0
  }

  private stableHash(value: string): number {
    let hash = 0
    for (let index = 0; index < value.length; index += 1) {
      hash = (hash * 31 + value.charCodeAt(index)) % 2147483647
    }
    return hash
  }
}
