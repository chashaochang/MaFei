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
}

export interface MineHistoryItem extends MineHistoryCandidate {
  /** Parsed timestamp used for deterministic sorting. */
  playedAt: number
}

export class MineHistoryPlanner {
  merge(resume: MineHistoryCandidate[], completed: MineHistoryCandidate[], limit: number): MineHistoryItem[] {
    const byId: Map<string, MineHistoryItem> = new Map<string, MineHistoryItem>()
    resume.concat(completed).forEach((candidate: MineHistoryCandidate) => {
      const playedAt = Date.parse(candidate.lastPlayedDate)
      if (candidate.id.length === 0 || !Number.isFinite(playedAt)) {
        return
      }
      const normalized: MineHistoryItem = {
        id: candidate.id,
        name: candidate.name.length > 0 ? candidate.name : '未命名内容',
        image: candidate.image,
        lastPlayedDate: candidate.lastPlayedDate,
        played: candidate.played,
        percent: Math.min(100, Math.max(0, Math.round(candidate.percent))),
        playedAt
      }
      const current = byId.get(normalized.id)
      if (!current || normalized.playedAt > current.playedAt) {
        byId.set(normalized.id, normalized)
      }
    })
    const items: MineHistoryItem[] = []
    byId.forEach((item: MineHistoryItem) => {
      items.push(item)
    })
    items.sort((left: MineHistoryItem, right: MineHistoryItem) => right.playedAt - left.playedAt)
    return items.slice(0, Math.max(0, limit))
  }
}
