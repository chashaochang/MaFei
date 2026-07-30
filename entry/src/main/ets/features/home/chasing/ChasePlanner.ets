export interface ChasePlannerItem {
  id: string
  name: string
  image: string
  seriesId?: string
  mediaName?: string
  type?: string
  percent?: number
  subtitle?: string
  score?: number
  tags?: string[]
  time?: string
}

export interface ChaseFollowedSeriesItem extends ChasePlannerItem {
  unplayedItemCount?: number | null
  played?: boolean
}

export interface ChasePlannerSection {
  kind: string
  title: string
  subtitle: string
  items: ChasePlannerItem[]
}

export interface ChasePlannerResult {
  ok: boolean
  summary: string
  totalCount: number
  sections: ChasePlannerSection[]
}

export class ChasePlanner {
  plan(
    continueItems: ChasePlannerItem[],
    nextUpItems: ChasePlannerItem[],
    followedSeriesItems: ChaseFollowedSeriesItem[]
  ): ChasePlannerResult {
    const normalizedContinue = continueItems
      .filter((item: ChasePlannerItem): boolean => item.id.trim().length > 0)
      .map((item: ChasePlannerItem): ChasePlannerItem => this.normalizeItem(item))
    const continueIds: Set<string> = new Set<string>()
    normalizedContinue.forEach((item: ChasePlannerItem) => {
      continueIds.add(item.id)
    })
    const normalizedNextUp = nextUpItems
      .filter((item: ChasePlannerItem): boolean => item.id.trim().length > 0 && !continueIds.has(item.id))
      .map((item: ChasePlannerItem): ChasePlannerItem => this.normalizeItem(item))
    const activeSeriesKeys: Set<string> = new Set<string>()
    normalizedContinue.forEach((item: ChasePlannerItem) => {
      activeSeriesKeys.add(this.seriesKey(item))
    })
    normalizedNextUp.forEach((item: ChasePlannerItem) => {
      activeSeriesKeys.add(this.seriesKey(item))
    })
    const completedSeriesKeys: Set<string> = new Set<string>()
    const normalizedCompleted = followedSeriesItems
      .filter((item: ChaseFollowedSeriesItem): boolean => {
        const key = this.seriesKey(item)
        if (key.length === 0 || activeSeriesKeys.has(key) || !this.isCompletedSeries(item)) {
          return false
        }
        if (completedSeriesKeys.has(key)) {
          return false
        }
        completedSeriesKeys.add(key)
        return true
      })
      .map((item: ChaseFollowedSeriesItem): ChasePlannerItem => this.normalizeItem(item))

    const sections: ChasePlannerSection[] = [
      {
        kind: 'continue',
        title: '继续观看',
        subtitle: normalizedContinue.length === 0 ? '暂无需要续播的内容' :
          `${normalizedContinue.length} 个内容可继续播放`,
        items: normalizedContinue
      },
      {
        kind: 'nextUp',
        title: '接下来',
        subtitle: normalizedNextUp.length === 0 ? '暂无接下来可播放的剧集' :
          `${normalizedNextUp.length} 集等待播放`,
        items: normalizedNextUp
      },
      {
        kind: 'completed',
        title: '已看完',
        subtitle: normalizedCompleted.length === 0 ? '暂无已看完的剧集' :
          `${normalizedCompleted.length} 部剧集已看完`,
        items: normalizedCompleted
      }
    ]

    return {
      ok: true,
      summary: this.buildSummary(
        normalizedContinue.length,
        normalizedNextUp.length,
        normalizedCompleted.length
      ),
      totalCount: normalizedContinue.length + normalizedNextUp.length + normalizedCompleted.length,
      sections
    }
  }

  private seriesKey(item: ChasePlannerItem): string {
    return (item.seriesId || item.id).trim()
  }

  private isCompletedSeries(item: ChaseFollowedSeriesItem): boolean {
    if (item.unplayedItemCount === 0) {
      return true
    }
    return (item.unplayedItemCount === undefined || item.unplayedItemCount === null) && item.played === true
  }

  private normalizeItem(item: ChasePlannerItem): ChasePlannerItem {
    const percent = item.percent || 0
    return {
      id: item.id,
      name: item.name.trim().length > 0 ? item.name : '未命名内容',
      image: item.image,
      seriesId: item.seriesId,
      mediaName: item.mediaName,
      type: item.type,
      percent: Math.max(0, Math.min(100, percent)),
      subtitle: item.subtitle,
      score: item.score,
      tags: item.tags,
      time: item.time
    }
  }

  private buildSummary(continueCount: number, nextUpCount: number, completedCount: number): string {
    const parts: string[] = []
    if (continueCount > 0) {
      parts.push(`继续观看 ${continueCount}`)
    }
    if (nextUpCount > 0) {
      parts.push(`接下来 ${nextUpCount}`)
    }
    if (completedCount > 0) {
      parts.push(`已看完 ${completedCount}`)
    }
    return parts.length > 0 ? parts.join(' · ') : '暂无追剧内容'
  }
}
