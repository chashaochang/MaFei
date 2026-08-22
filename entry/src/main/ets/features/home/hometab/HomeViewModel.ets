import { BaseViewModel } from "../../../common/base/BaseViewModel"
import { currentMediaCatalogProvider } from "../../../media/MediaCatalogAccess"
import {
  MediaImageRef,
  MediaLibrary,
  MediaLibraryKind,
  MediaPage,
  MediaSummary
} from "../../../media/catalog/MediaCatalogModels"
import { MediaCatalogProvider } from "../../../media/catalog/MediaCatalogProvider"
import { MediaRefKind } from "../../../media/MediaRef"
import {
  HOME_RECENT_MEDIA_ID,
  HomeUIState,
  MediaItem,
  VideoItem
} from "./HomeUIState"
import { HomeSnapshot, HomeSnapshotContent, HomeSnapshotStore } from './HomeSnapshotStore'

const HOME_FEED_PAGE_SIZE: number = 30

export class HomeViewModel extends BaseViewModel<HomeUIState> {
  private catalog?: MediaCatalogProvider
  private initialLoadCompleted: boolean = false
  private initVersion: number = 0
  private latestStartIndexes: Map<string, number> = new Map<string, number>()
  private latestHasMoreById: Map<string, boolean> = new Map<string, boolean>()
  private latestLoadedIds: Set<string> = new Set<string>()
  private latestLoadingIds: Set<string> = new Set<string>()
  private latestRequestVersion: number = 0
  private snapshotStore?: HomeSnapshotStore
  private snapshotAccountScope: string = ''

  init(): void {
    this.initVersion += 1
    const requestVersion = this.initVersion
    if (!this.initialLoadCompleted) {
      this.ui.initialLoading = true
    }
    let catalog: MediaCatalogProvider
    try {
      catalog = currentMediaCatalogProvider()
    } catch (_error) {
      this.catalog = undefined
      this.clearUnavailableCatalogState()
      this.finishInit(requestVersion)
      return
    }
    this.catalog = catalog
    this.restoreSnapshot(catalog)
    const mediaListRequest = this.getMediaList(catalog, requestVersion)
    Promise.all([
      this.getRecommendationList(catalog, requestVersion),
      this.getResumeList(catalog, requestVersion),
      this.getNextUpList(catalog, requestVersion),
      mediaListRequest.then(() => this.getLatestList(catalog, requestVersion))
    ]).then(() => {
      this.finishInit(requestVersion)
    }).catch(() => {
      this.finishInit(requestVersion)
    })
  }

  private clearUnavailableCatalogState(): void {
    this.ui.mediaList = []
    this.ui.resumeList = []
    this.ui.recommendationList = []
    this.ui.nextUpList = []
    this.ui.latestMediaList = []
    this.ui.latestMap = new Map<string, VideoItem[]>()
    this.ui.hasLiveTv = false
    if (this.appUIState.rootNavigationHomeHeroVisible) {
      this.appUIState.rootNavigationHomeHeroVisible = false
    }
  }

  private isCurrentRequest(catalog: MediaCatalogProvider, requestVersion: number): boolean {
    return this.catalog === catalog && this.initVersion === requestVersion
  }

  private finishInit(requestVersion: number): void {
    if (requestVersion !== this.initVersion) {
      return
    }
    this.initialLoadCompleted = true
    this.ui.initialLoading = false
    this.ui.isRefreshing = false
    this.persistSnapshot(requestVersion)
  }

  private restoreSnapshot(catalog: MediaCatalogProvider): void {
    const accountScope = catalog.session.accountScope
    if (this.snapshotAccountScope === accountScope) return
    const context = this.ui.context.getHostContext()
    if (!context) return
    this.snapshotStore = HomeSnapshotStore.create(context)
    this.snapshotAccountScope = accountScope
    const snapshot = this.snapshotStore.load(accountScope)
    if (!snapshot) return
    this.applySnapshot(snapshot)
    this.initialLoadCompleted = true
    this.ui.initialLoading = false
  }

  private applySnapshot(snapshot: HomeSnapshot): void {
    this.ui.mediaList = snapshot.mediaList
    this.ui.resumeList = snapshot.resumeList
    this.ui.recommendationList = snapshot.recommendationList
    this.ui.nextUpList = snapshot.nextUpList
    this.ui.latestMediaList = snapshot.latestMediaList
    this.ui.latestMap = snapshot.latestMap
    this.ui.selectedLatestMediaId = resolveLatestMediaSelection(
      snapshot.selectedLatestMediaId, snapshot.latestMediaList)
    this.ui.hasLiveTv = snapshot.hasLiveTv
    this.ui.latestMediaLoading = false
    this.latestLoadedIds = new Set<string>()
    this.latestStartIndexes = new Map<string, number>()
    snapshot.latestMap.forEach((items: VideoItem[], id: string): void => {
      this.latestLoadedIds.add(id)
      this.latestStartIndexes.set(id, items.length)
    })
    const heroVisible = snapshot.recommendationList.length > 0
    if (this.appUIState.rootNavigationHomeHeroVisible !== heroVisible) {
      this.appUIState.rootNavigationHomeHeroVisible = heroVisible
    }
  }

  private persistSnapshot(requestVersion: number): void {
    const store = this.snapshotStore
    const accountScope = this.snapshotAccountScope
    if (!store || accountScope.length === 0) return
    setTimeout((): void => {
      if (requestVersion === this.initVersion && this.catalog?.session.accountScope === accountScope) {
        try {
          store.save(accountScope, new HomeSnapshotContent(
            this.ui.mediaList,
            this.ui.resumeList,
            this.ui.recommendationList,
            this.ui.nextUpList,
            this.ui.latestMediaList,
            this.ui.latestMap,
            this.ui.selectedLatestMediaId,
            this.ui.hasLiveTv
          ))
        } catch (_error) {
        }
      }
    }, 0)
  }

  private getRecommendationList(catalog: MediaCatalogProvider, requestVersion: number): Promise<void> {
    return catalog.recommendations({
      categoryLimit: 4,
      itemLimit: 4,
      resultLimit: 6
    }).then((items: MediaSummary[]) => {
      if (!this.isCurrentRequest(catalog, requestVersion)) {
        return
      }
      const recommendations: VideoItem[] = items.map((item: MediaSummary): VideoItem => {
        return mapRecommendationItem(item)
      })
      this.ui.recommendationList = recommendations
      const heroVisible = recommendations.length > 0
      if (this.appUIState.rootNavigationHomeHeroVisible !== heroVisible) {
        this.appUIState.rootNavigationHomeHeroVisible = heroVisible
      }
    }).catch(() => {
    })
  }

  private getMediaList(catalog: MediaCatalogProvider, requestVersion: number): Promise<void> {
    if (this.isCurrentRequest(catalog, requestVersion)) {
      this.ui.hasLiveTv = false
    }
    return catalog.getLibraries().then((libraries: MediaLibrary[]) => {
      if (!this.isCurrentRequest(catalog, requestVersion)) {
        return
      }
      this.ui.mediaList = libraries.map((library: MediaLibrary): MediaItem => mapLibraryItem(library))
      this.ui.hasLiveTv = catalog.session.capabilities.liveTv && libraries.some(
        (library: MediaLibrary): boolean => library.kind === MediaLibraryKind.LiveTv)
    }).catch(() => {
    })
  }

  private getResumeList(catalog: MediaCatalogProvider, requestVersion: number): Promise<void> {
    return catalog.resume({
      kinds: [MediaRefKind.Movie, MediaRefKind.Episode, MediaRefKind.Video],
      limit: 12
    }).then((page: MediaPage<MediaSummary>) => {
      if (!this.isCurrentRequest(catalog, requestVersion)) {
        return
      }
      this.ui.resumeList = page.items.map((item: MediaSummary): VideoItem => mapResumeItem(item))
    }).catch(() => {
    })
  }

  private getNextUpList(catalog: MediaCatalogProvider, requestVersion: number): Promise<void> {
    const now = new Date()
    now.setFullYear(now.getFullYear() - 1)
    return catalog.nextUp({
      limit: 24,
      dateCutoff: now.toISOString(),
      disableFirstEpisode: false,
      enableResumable: false,
      enableRewatching: false
    }).then((page: MediaPage<MediaSummary>) => {
      if (!this.isCurrentRequest(catalog, requestVersion)) {
        return
      }
      this.ui.nextUpList = page.items.map((item: MediaSummary): VideoItem => mapNextUpItem(item))
    }).catch(() => {
    })
  }

  private getLatestList(catalog: MediaCatalogProvider, requestVersion: number): Promise<void> {
    if (!this.isCurrentRequest(catalog, requestVersion)) {
      return Promise.resolve()
    }
    this.latestRequestVersion += 1
    const latestRequestVersion = this.latestRequestVersion
    this.latestStartIndexes = new Map<string, number>()
    this.latestHasMoreById = new Map<string, boolean>()
    this.latestLoadedIds = new Set<string>()
    this.latestLoadingIds = new Set<string>()
    this.ui.latestMediaList = this.ui.mediaList.filter((item: MediaItem): boolean => {
      return isSupportedHomeVideoLibrary(item)
    })
    this.ui.selectedLatestMediaId = resolveLatestMediaSelection(
      this.ui.selectedLatestMediaId, this.ui.latestMediaList)
    this.ui.latestMediaLoading = true
    this.ui.latestMediaLoadingMore = false
    this.ui.latestMediaHasMore = true
    this.ui.latestMediaLoadFailed = false
    return this.loadLatestMediaPage(
      this.ui.selectedLatestMediaId, true, latestRequestVersion, catalog)
  }

  selectLatestMedia(id: string): void {
    if (id !== HOME_RECENT_MEDIA_ID &&
      !this.ui.latestMediaList.some((item: MediaItem): boolean => item.id === id)) {
      return
    }
    this.ui.selectedLatestMediaId = id
    this.ui.latestMediaLoadFailed = false
    this.ui.latestMediaHasMore = this.latestHasMoreById.get(id) ?? true
    if (this.latestLoadedIds.has(id)) {
      this.ui.latestMediaLoading = false
      this.ui.latestMediaLoadingMore = false
      return
    }
    this.ui.latestMediaLoading = true
    this.ui.latestMediaLoadingMore = false
    this.loadLatestMediaPage(id, true, this.latestRequestVersion)
  }

  loadMoreLatestMedia(): void {
    const id = this.ui.selectedLatestMediaId
    if (this.ui.latestMediaLoading || this.ui.latestMediaLoadingMore ||
      !(this.latestHasMoreById.get(id) ?? true)) {
      return
    }
    this.loadLatestMediaPage(id, false, this.latestRequestVersion)
  }

  retryLatestMedia(): void {
    const id = this.ui.selectedLatestMediaId
    const currentItems = this.ui.latestMap.get(id) || []
    this.ui.latestMediaLoadFailed = false
    this.loadLatestMediaPage(id, currentItems.length === 0, this.latestRequestVersion)
  }

  private loadLatestMediaPage(id: string, replace: boolean, requestVersion: number,
    requestedCatalog?: MediaCatalogProvider): Promise<void> {
    const catalog = requestedCatalog || this.catalog
    if (!catalog || this.catalog !== catalog || this.latestLoadingIds.has(id)) {
      return Promise.resolve()
    }
    this.latestLoadingIds.add(id)
    const startIndex = replace ? 0 : (this.latestStartIndexes.get(id) || 0)
    if (this.ui.selectedLatestMediaId === id) {
      this.ui.latestMediaLoading = replace
      this.ui.latestMediaLoadingMore = !replace
      this.ui.latestMediaLoadFailed = false
    }
    const media = id === HOME_RECENT_MEDIA_ID ? undefined :
      this.ui.latestMediaList.find((item: MediaItem): boolean => item.id === id)
    return catalog.latest({
      libraryRef: media?.mediaRef,
      kinds: homeFeedItemTypes(media),
      startIndex: startIndex,
      limit: HOME_FEED_PAGE_SIZE,
      recursive: true
    }).then((page: MediaPage<MediaSummary>) => {
      if (requestVersion !== this.latestRequestVersion || this.catalog !== catalog) {
        return
      }
      const mediaName = media?.name || '最近添加'
      const mappedItems: VideoItem[] = page.items.map((item: MediaSummary): VideoItem => {
        return mapHomeFeedItem(item, mediaName)
      })
      const previousItems: VideoItem[] = replace ? [] : (this.ui.latestMap.get(id) || [])
      const mergedItems: VideoItem[] = appendUniqueVideoItems(previousItems, mappedItems)
      const nextMap = new Map<string, VideoItem[]>(this.ui.latestMap)
      nextMap.set(id, mergedItems)
      this.ui.latestMap = nextMap
      this.latestStartIndexes.set(id, page.nextStartIndex)
      const hasMore = id === HOME_RECENT_MEDIA_ID ? false : page.hasMore
      this.latestHasMoreById.set(id, hasMore)
      this.latestLoadedIds.add(id)
      if (this.ui.selectedLatestMediaId === id) {
        this.ui.latestMediaLoading = false
        this.ui.latestMediaLoadingMore = false
        this.ui.latestMediaHasMore = hasMore
        this.ui.latestMediaLoadFailed = false
      }
    }).catch(() => {
      if (requestVersion === this.latestRequestVersion && this.catalog === catalog &&
        this.ui.selectedLatestMediaId === id) {
        this.ui.latestMediaLoading = false
        this.ui.latestMediaLoadingMore = false
        this.ui.latestMediaLoadFailed = true
      }
    }).finally(() => {
      this.latestLoadingIds.delete(id)
    })
  }
}

export function resolveLatestMediaSelection(currentId: string, mediaList: MediaItem[]): string {
  if (currentId === HOME_RECENT_MEDIA_ID) {
    return HOME_RECENT_MEDIA_ID
  }
  if (mediaList.some((item: MediaItem): boolean => item.id === currentId)) {
    return currentId
  }
  return HOME_RECENT_MEDIA_ID
}

export function appendUniqueVideoItems(current: VideoItem[], incoming: VideoItem[]): VideoItem[] {
  const result: VideoItem[] = current.slice()
  const identities: Set<string> = new Set<string>()
  result.forEach((item: VideoItem): void => {
    identities.add(videoItemIdentity(item))
  })
  incoming.forEach((item: VideoItem): void => {
    const identity = videoItemIdentity(item)
    if (!identities.has(identity)) {
      identities.add(identity)
      result.push(item)
    }
  })
  return result
}

function videoItemIdentity(item: VideoItem): string {
  return item.mediaRef?.identity || item.id
}

export function isSupportedHomeVideoLibrary(item: MediaItem): boolean {
  return item.libraryKind === undefined || item.libraryKind === MediaLibraryKind.Movies ||
    item.libraryKind === MediaLibraryKind.Series || item.libraryKind === MediaLibraryKind.Mixed ||
    item.libraryKind === MediaLibraryKind.Other
}

export function homeFeedItemTypes(media?: MediaItem): MediaRefKind[] {
  if (!media) {
    return [MediaRefKind.Movie, MediaRefKind.Series, MediaRefKind.Episode, MediaRefKind.Video]
  }
  if (media.libraryKind === MediaLibraryKind.Movies || media.kind === MediaRefKind.Movie) {
    return [MediaRefKind.Movie]
  }
  if (media.libraryKind === MediaLibraryKind.Series || media.kind === MediaRefKind.Series) {
    return [MediaRefKind.Series]
  }
  return [MediaRefKind.Movie, MediaRefKind.Series, MediaRefKind.Video]
}

function mapLibraryItem(library: MediaLibrary): MediaItem {
  return {
    name: library.title,
    image: library.primaryImage?.url || '',
    mediaRef: library.ref,
    imageRef: library.primaryImage,
    id: library.ref.id,
    childCount: library.itemCount,
    kind: libraryContentKind(library.kind),
    libraryKind: library.kind
  }
}

function libraryContentKind(kind: MediaLibraryKind): MediaRefKind | undefined {
  if (kind === MediaLibraryKind.Movies) {
    return MediaRefKind.Movie
  }
  if (kind === MediaLibraryKind.Series) {
    return MediaRefKind.Series
  }
  if (kind === MediaLibraryKind.LiveTv) {
    return MediaRefKind.LiveChannel
  }
  return undefined
}

function mapHomeFeedItem(item: MediaSummary, mediaName: string): VideoItem {
  return mapSummaryItem(item, item.primaryImage, item.ref.kind === MediaRefKind.Movie ? '电影' : mediaName)
}

function mapResumeItem(item: MediaSummary): VideoItem {
  const mapped = mapSummaryItem(item, preferredLandscapeImage(item), mediaNameForKind(item.ref.kind))
  mapped.percent = Math.round(item.userState.watched.playedPercentage)
  return mapped
}

function mapNextUpItem(item: MediaSummary): VideoItem {
  return mapSummaryItem(item, preferredLandscapeImage(item), '电视节目')
}

function mapRecommendationItem(item: MediaSummary): VideoItem {
  return mapSummaryItem(item, preferredLandscapeImage(item), mediaNameForKind(item.ref.kind))
}

function mapSummaryItem(item: MediaSummary, image: MediaImageRef | undefined,
  mediaName: string): VideoItem {
  return {
    id: item.ref.id,
    name: item.title,
    image: image?.url || '',
    mediaRef: item.ref,
    imageRef: image,
    kind: item.ref.kind,
    seriesRef: item.seriesRef,
    seriesId: item.seriesRef?.id,
    mediaName: mediaName,
    subtitle: item.subtitle || (item.year !== undefined ? item.year.toString() : ''),
    description: item.overview,
    score: item.communityRating,
    tags: item.tags.slice(),
    canDelete: item.canDelete,
    path: item.path
  }
}

function preferredLandscapeImage(item: MediaSummary): MediaImageRef | undefined {
  return item.backdropImage || item.thumbnailImage || item.primaryImage
}

function mediaNameForKind(kind: MediaRefKind): string {
  if (kind === MediaRefKind.Movie) {
    return '电影'
  }
  if (kind === MediaRefKind.Series || kind === MediaRefKind.Episode || kind === MediaRefKind.Season) {
    return '电视节目'
  }
  return ''
}
