import { common } from '@kit.AbilityKit'
import { preferences } from '@kit.ArkData'
import { MediaProviderKind } from '../../../media/MediaProviderKind'
import { MediaRef, MediaRefKind } from '../../../media/MediaRef'
import { MediaImageKind, MediaImageRef, MediaLibraryKind } from '../../../media/catalog/MediaCatalogModels'
import { MediaItem, VideoItem } from './HomeUIState'

const HOME_SNAPSHOT_STORE_NAME: string = 'HomeContentSnapshot'
const HOME_SNAPSHOT_KEY: string = 'home_content_snapshot'
const HOME_SNAPSHOT_VERSION: number = 1
const HOME_SNAPSHOT_MAX_AGE_MS: number = 7 * 24 * 60 * 60 * 1000
const HOME_SNAPSHOT_ITEM_LIMIT: number = 30

interface StoredMediaRef {
  provider: string
  accountScope: string
  id: string
  kind: string
}

interface StoredMediaImageRef {
  owner: StoredMediaRef
  kind: string
  url: string
  cacheKey: string
}

interface StoredMediaItem {
  name: string
  image: string
  mediaRef?: StoredMediaRef
  imageRef?: StoredMediaImageRef
  id: string
  childCount: number
  kind?: string
  libraryKind?: string
}

interface StoredVideoItem {
  id: string
  name: string
  image: string
  mediaRef?: StoredMediaRef
  imageRef?: StoredMediaImageRef
  kind?: string
  seriesRef?: StoredMediaRef
  canDelete?: boolean
  path?: string
  seriesId?: string
  mediaName?: string
  percent?: number
  subtitle?: string
  description?: string
  score?: number
  tags?: string[]
  time?: string
}

interface StoredLatestEntry {
  id: string
  items: StoredVideoItem[]
}

interface StoredHomeSnapshot {
  version: number
  accountScope: string
  savedAt: number
  mediaList: StoredMediaItem[]
  resumeList: StoredVideoItem[]
  recommendationList: StoredVideoItem[]
  nextUpList: StoredVideoItem[]
  latestMediaList: StoredMediaItem[]
  latestEntries: StoredLatestEntry[]
  selectedLatestMediaId: string
  hasLiveTv: boolean
}

export interface HomeSnapshot {
  readonly mediaList: MediaItem[]
  readonly resumeList: VideoItem[]
  readonly recommendationList: VideoItem[]
  readonly nextUpList: VideoItem[]
  readonly latestMediaList: MediaItem[]
  readonly latestMap: Map<string, VideoItem[]>
  readonly selectedLatestMediaId: string
  readonly hasLiveTv: boolean
}

export class HomeSnapshotContent {
  readonly mediaList: MediaItem[]
  readonly resumeList: VideoItem[]
  readonly recommendationList: VideoItem[]
  readonly nextUpList: VideoItem[]
  readonly latestMediaList: MediaItem[]
  readonly latestMap: Map<string, VideoItem[]>
  readonly selectedLatestMediaId: string
  readonly hasLiveTv: boolean

  constructor(mediaList: MediaItem[], resumeList: VideoItem[], recommendationList: VideoItem[],
    nextUpList: VideoItem[], latestMediaList: MediaItem[], latestMap: Map<string, VideoItem[]>,
    selectedLatestMediaId: string, hasLiveTv: boolean) {
    this.mediaList = mediaList
    this.resumeList = resumeList
    this.recommendationList = recommendationList
    this.nextUpList = nextUpList
    this.latestMediaList = latestMediaList
    this.latestMap = latestMap
    this.selectedLatestMediaId = selectedLatestMediaId
    this.hasLiveTv = hasLiveTv
  }
}

export interface HomeSnapshotBackend {
  read(): string
  write(value: string): void
  flush(): void
}

class HarmonyHomeSnapshotBackend implements HomeSnapshotBackend {
  private readonly preference: preferences.Preferences

  constructor(preference: preferences.Preferences) {
    this.preference = preference
  }

  read(): string {
    const value = this.preference.getSync(HOME_SNAPSHOT_KEY, '')
    return typeof value === 'string' ? value : ''
  }

  write(value: string): void {
    this.preference.putSync(HOME_SNAPSHOT_KEY, value)
  }

  flush(): void {
    this.preference.flushSync()
  }
}

export class HomeSnapshotStore {
  private readonly backend: HomeSnapshotBackend

  constructor(backend: HomeSnapshotBackend) {
    this.backend = backend
  }

  static create(context: common.Context): HomeSnapshotStore {
    const preference = preferences.getPreferencesSync(context, { name: HOME_SNAPSHOT_STORE_NAME })
    return new HomeSnapshotStore(new HarmonyHomeSnapshotBackend(preference))
  }

  load(accountScope: string, now: number = Date.now()): HomeSnapshot | undefined {
    try {
      const raw = this.backend.read()
      if (raw.length === 0) return undefined
      const stored = JSON.parse(raw) as StoredHomeSnapshot
      if (stored.version !== HOME_SNAPSHOT_VERSION || stored.accountScope !== accountScope ||
        now - stored.savedAt > HOME_SNAPSHOT_MAX_AGE_MS || now < stored.savedAt) {
        return undefined
      }
      const latestMap = new Map<string, VideoItem[]>()
      const entries = stored.latestEntries || []
      entries.forEach((entry: StoredLatestEntry): void => {
        if (`${entry.id || ''}`.length > 0) {
          latestMap.set(entry.id, (entry.items || []).map((item: StoredVideoItem): VideoItem =>
            this.restoreVideoItem(item)))
        }
      })
      return {
        mediaList: (stored.mediaList || []).map((item: StoredMediaItem): MediaItem => this.restoreMediaItem(item)),
        resumeList: (stored.resumeList || []).map((item: StoredVideoItem): VideoItem => this.restoreVideoItem(item)),
        recommendationList: (stored.recommendationList || [])
          .map((item: StoredVideoItem): VideoItem => this.restoreVideoItem(item)),
        nextUpList: (stored.nextUpList || []).map((item: StoredVideoItem): VideoItem => this.restoreVideoItem(item)),
        latestMediaList: (stored.latestMediaList || [])
          .map((item: StoredMediaItem): MediaItem => this.restoreMediaItem(item)),
        latestMap: latestMap,
        selectedLatestMediaId: `${stored.selectedLatestMediaId || ''}`,
        hasLiveTv: stored.hasLiveTv === true
      }
    } catch (_error) {
      return undefined
    }
  }

  save(accountScope: string, ui: HomeSnapshotContent): void {
    if (accountScope.length === 0 || !this.hasContent(ui)) return
    const latestEntries: StoredLatestEntry[] = []
    ui.latestMap.forEach((items: VideoItem[], id: string): void => {
      latestEntries.push({
        id: id,
        items: items.slice(0, HOME_SNAPSHOT_ITEM_LIMIT)
          .map((item: VideoItem): StoredVideoItem => this.storeVideoItem(item))
      })
    })
    const stored: StoredHomeSnapshot = {
      version: HOME_SNAPSHOT_VERSION,
      accountScope: accountScope,
      savedAt: Date.now(),
      mediaList: ui.mediaList.map((item: MediaItem): StoredMediaItem => this.storeMediaItem(item)),
      resumeList: ui.resumeList.slice(0, HOME_SNAPSHOT_ITEM_LIMIT)
        .map((item: VideoItem): StoredVideoItem => this.storeVideoItem(item)),
      recommendationList: ui.recommendationList.slice(0, HOME_SNAPSHOT_ITEM_LIMIT)
        .map((item: VideoItem): StoredVideoItem => this.storeVideoItem(item)),
      nextUpList: ui.nextUpList.slice(0, HOME_SNAPSHOT_ITEM_LIMIT)
        .map((item: VideoItem): StoredVideoItem => this.storeVideoItem(item)),
      latestMediaList: ui.latestMediaList
        .map((item: MediaItem): StoredMediaItem => this.storeMediaItem(item)),
      latestEntries: latestEntries,
      selectedLatestMediaId: ui.selectedLatestMediaId,
      hasLiveTv: ui.hasLiveTv
    }
    this.backend.write(JSON.stringify(stored))
    this.backend.flush()
  }

  private hasContent(ui: HomeSnapshotContent): boolean {
    return ui.mediaList.length > 0 || ui.recommendationList.length > 0 || ui.resumeList.length > 0 ||
      ui.nextUpList.length > 0 || ui.latestMap.size > 0
  }

  private storeMediaItem(item: MediaItem): StoredMediaItem {
    return {
      name: item.name,
      image: typeof item.image === 'string' ? item.image : '',
      mediaRef: this.storeRef(item.mediaRef),
      imageRef: this.storeImage(item.imageRef),
      id: item.id,
      childCount: item.childCount,
      kind: item.kind,
      libraryKind: item.libraryKind
    }
  }

  private restoreMediaItem(item: StoredMediaItem): MediaItem {
    return {
      name: `${item.name || ''}`,
      image: `${item.image || ''}`,
      mediaRef: this.restoreRef(item.mediaRef),
      imageRef: this.restoreImage(item.imageRef),
      id: `${item.id || ''}`,
      childCount: Math.max(0, item.childCount || 0),
      kind: item.kind as MediaRefKind | undefined,
      libraryKind: item.libraryKind as MediaLibraryKind | undefined
    }
  }

  private storeVideoItem(item: VideoItem): StoredVideoItem {
    return {
      id: item.id,
      name: item.name,
      image: item.image,
      mediaRef: this.storeRef(item.mediaRef),
      imageRef: this.storeImage(item.imageRef),
      kind: item.kind,
      seriesRef: this.storeRef(item.seriesRef),
      canDelete: item.canDelete,
      path: item.path,
      seriesId: item.seriesId,
      mediaName: item.mediaName,
      percent: item.percent,
      subtitle: item.subtitle,
      description: item.description,
      score: item.score,
      tags: item.tags?.slice(),
      time: item.time
    }
  }

  private restoreVideoItem(item: StoredVideoItem): VideoItem {
    return {
      id: `${item.id || ''}`,
      name: `${item.name || ''}`,
      image: `${item.image || ''}`,
      mediaRef: this.restoreRef(item.mediaRef),
      imageRef: this.restoreImage(item.imageRef),
      kind: item.kind as MediaRefKind | undefined,
      seriesRef: this.restoreRef(item.seriesRef),
      canDelete: item.canDelete,
      path: item.path,
      seriesId: item.seriesId,
      mediaName: item.mediaName,
      percent: item.percent,
      subtitle: item.subtitle,
      description: item.description,
      score: item.score,
      tags: item.tags?.slice(),
      time: item.time
    }
  }

  private storeRef(ref?: MediaRef): StoredMediaRef | undefined {
    if (!ref) return undefined
    return {
      provider: ref.provider,
      accountScope: ref.accountScope,
      id: ref.id,
      kind: ref.kind
    }
  }

  private restoreRef(ref?: StoredMediaRef): MediaRef | undefined {
    if (!ref || `${ref.id || ''}`.trim().length === 0 || `${ref.accountScope || ''}`.trim().length === 0) {
      return undefined
    }
    return new MediaRef(
      ref.provider as MediaProviderKind,
      ref.accountScope,
      ref.id,
      ref.kind as MediaRefKind
    )
  }

  private storeImage(image?: MediaImageRef): StoredMediaImageRef | undefined {
    if (!image) return undefined
    return {
      owner: this.storeRef(image.owner)!,
      kind: image.kind,
      url: image.url,
      cacheKey: image.cacheKey
    }
  }

  private restoreImage(image?: StoredMediaImageRef): MediaImageRef | undefined {
    const owner = this.restoreRef(image?.owner)
    if (!image || !owner || `${image.url || ''}`.trim().length === 0) return undefined
    return new MediaImageRef({
      owner: owner,
      kind: image.kind as MediaImageKind,
      url: image.url,
      variant: 'home-snapshot',
      cacheKeyOverride: image.cacheKey,
      headers: []
    })
  }
}
