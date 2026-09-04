import { MediaRouteRefData } from '../../media/navigation/MediaRouteRef'

export class PlayOptions {
  // 属性定义
  ids: string[] // UUID 在 ArkTS 中可以用 string 表示
  mediaSourceId: string | null
  startIndex: number
  startPositionTicks: number | null
  audioStreamIndex: number | null
  subtitleStreamIndex: number | null
  /** Provider/account scoped queue. New native callers must prefer this over ids. */
  refs: MediaRouteRefData[]
  /** Scoped single-item fallback used when no queue is supplied. */
  mediaSourceRef?: MediaRouteRefData

  constructor(
    ids?: string[],
    mediaSourceId?: string | null,
    startIndex?: number | null,
    startPositionTicks?: number | null,
    audioStreamIndex?: number | null,
    subtitleStreamIndex?: number | null,
    refs?: MediaRouteRefData[],
    mediaSourceRef?: MediaRouteRefData
  ) {
    this.ids = ids ?? []
    this.mediaSourceId = mediaSourceId ?? null
    this.startIndex = startIndex ?? 0
    this.startPositionTicks = startPositionTicks ?? null
    this.audioStreamIndex = audioStreamIndex ?? null
    this.subtitleStreamIndex = subtitleStreamIndex ?? null
    this.refs = refs ? refs.slice() : []
    this.mediaSourceRef = mediaSourceRef
  }

  static fromJson(json: string): PlayOptions {
    const object: PlayOptions = JSON.parse(json)
    return new PlayOptions(
      object.ids,
      object.mediaSourceId ?? null,
      object.startIndex ?? 0,
      object.startPositionTicks ?? null,
      object.audioStreamIndex ?? null,
      object.subtitleStreamIndex ?? null,
      object.refs ?? [],
      object.mediaSourceRef
    )
  }
}

function parseUUIDs(array: string[]): string[] {
  if (!array) {
    return []
  }
  return array.map(uuid => toUUID(uuid)).filter(u => u !== null) as string[]
}

function toUUID(value: string): string | null {
  try {
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
    return uuidRegex.test(value) ? value : null
  } catch {
    return null
  }
}
