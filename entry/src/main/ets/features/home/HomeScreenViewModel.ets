import { BaseViewModel } from "../../common/base/BaseViewModel"
import { AppPreference } from "../../data/AppPreference"
import { currentMediaCatalogProvider } from "../../media/MediaCatalogAccess"
import {
  MediaCatalogCounts,
  MediaLibrary,
  MediaLibraryKind
} from "../../media/catalog/MediaCatalogModels"
import { MediaCatalogProvider } from "../../media/catalog/MediaCatalogProvider"
import { MediaRefKind } from "../../media/MediaRef"
import { HomeScreenUIState, PadMenuDataSource, PadMenuItem, PadMenuType } from "./HomeScreenUIState"
import { MediaItem } from "./hometab/HomeUIState"

@ObservedV2
export class HomeScreenViewModel extends BaseViewModel<HomeScreenUIState> {
  datasource: PadMenuDataSource = new PadMenuDataSource()
  private catalog?: MediaCatalogProvider
  private initVersion: number = 0

  async init(): Promise<void> {
    this.initVersion += 1
    const requestVersion = this.initVersion
    this.datasource.clearData()
    this.ui.isLoading = true
    this.ui.favoriteCount = 0
    this.ui.chasingCount = AppPreference.getInstance().getChasingSeriesIds().length
    this.datasource.pushData(new PadMenuItem(PadMenuType.ITEM, "首页", $r('app.media.ic_home_sel'),
      $r('app.media.ic_home_unsel')))
    this.datasource.pushData(new PadMenuItem(PadMenuType.ITEM, "追剧", $r('app.media.history'),
      $r('app.media.history'), this.ui.chasingCount))
    try {
      const catalog = currentMediaCatalogProvider()
      this.catalog = catalog
      await this.getFavoriteCount(catalog, requestVersion)
      if (!this.isCurrentRequest(catalog, requestVersion)) {
        return
      }
      this.datasource.pushData(new PadMenuItem(PadMenuType.ITEM, `收藏`, $r('app.media.ic_unlike'),
        $r('app.media.ic_unlike'), this.ui.favoriteCount))
      await this.getCounts(catalog, requestVersion)
      await this.getMediaList(catalog, requestVersion)
    } catch (error) {
      console.error(`[HomeScreenVM] init failed: ${JSON.stringify(error)}`)
    } finally {
      if (requestVersion === this.initVersion) {
        this.ui.isLoading = false
        this.ui.lastSelectedMediaIndex = this.datasource.getFirstMediaItemIndex()
        this.ui.padMenuDataVersion++
      }
    }
  }

  private isCurrentRequest(catalog: MediaCatalogProvider, requestVersion: number): boolean {
    return this.catalog === catalog && this.initVersion === requestVersion
  }

  async getFavoriteCount(catalog: MediaCatalogProvider = currentMediaCatalogProvider(),
    requestVersion: number = this.initVersion): Promise<void> {
    if (!catalog.session.capabilities.favorites) {
      if (this.isCurrentRequest(catalog, requestVersion)) {
        this.ui.favoriteCount = 0
      }
      return
    }
    try {
      const page = await catalog.getItems({
        kinds: [MediaRefKind.Movie, MediaRefKind.Episode, MediaRefKind.Series],
        startIndex: 0,
        limit: 1,
        sortFields: [],
        sortDirections: [],
        recursive: true,
        collapseBoxSetItems: false,
        favoriteOnly: true
      })
      if (this.isCurrentRequest(catalog, requestVersion)) {
        this.ui.favoriteCount = page.totalCount
      }
    } catch (_error) {
      if (this.isCurrentRequest(catalog, requestVersion)) {
        this.ui.favoriteCount = 0
      }
    }
  }

  private async getMediaList(catalog: MediaCatalogProvider,
    requestVersion: number): Promise<void> {
    const libraries = (await catalog.getLibraries()).filter((library: MediaLibrary): boolean => {
      if (library.kind === MediaLibraryKind.LiveTv) {
        return false
      }
      if (library.kind === MediaLibraryKind.Music) {
        return catalog.session.capabilities.music
      }
      if (library.kind === MediaLibraryKind.Photos) {
        return catalog.session.capabilities.photos
      }
      return true
    })
    if (!this.isCurrentRequest(catalog, requestVersion)) {
      return
    }
    const mediaItems = libraries.map((library: MediaLibrary): MediaItem => mapLibraryItem(library))
    if (mediaItems.length > 0) {
      this.datasource.pushData(new PadMenuItem(PadMenuType.TITLE, "媒体库"))
    }
    mediaItems.forEach((item: MediaItem): void => {
      this.appendMediaMenuItem(item)
    })
  }

  private appendMediaMenuItem(item: MediaItem): void {
    let icon: Resource | undefined
    if (item.libraryKind === MediaLibraryKind.Movies) {
      icon = $r('app.media.ic_video')
    } else if (item.libraryKind === MediaLibraryKind.Series) {
      icon = $r('app.media.tv')
    } else if (item.libraryKind === MediaLibraryKind.Music) {
      icon = $r('app.media.ic_audio')
    } else if (item.libraryKind === MediaLibraryKind.Photos) {
      icon = $r('app.media.ic_photo')
    }
    if (!icon) {
      return
    }
    const menuItem = new PadMenuItem(PadMenuType.ITEM, item.name, icon, icon, item.childCount)
    menuItem.extra = item
    this.datasource.pushData(menuItem)
  }

  async getCounts(catalog: MediaCatalogProvider = currentMediaCatalogProvider(),
    requestVersion: number = this.initVersion): Promise<void> {
    const counts: MediaCatalogCounts = await catalog.getCounts()
    if (!this.isCurrentRequest(catalog, requestVersion)) {
      return
    }
    this.datasource.pushData(new PadMenuItem(PadMenuType.TITLE, "分类"))
    const allMediaItem: MediaItem = {
      name: '全部影视',
      id: 'all',
      image: '',
      childCount: 0,
      kind: MediaRefKind.Video,
      libraryKind: MediaLibraryKind.Mixed
    }
    const allItem = new PadMenuItem(PadMenuType.ITEM, `全部`, $r('app.media.all'), $r('app.media.all'),
      counts.totalCount)
    allItem.extra = allMediaItem
    this.datasource.pushData(allItem)

    const movieMediaItem: MediaItem = {
      name: '电影',
      id: 'movies',
      image: '',
      childCount: 0,
      kind: MediaRefKind.Movie,
      libraryKind: MediaLibraryKind.Movies
    }
    const allMovieItem = new PadMenuItem(PadMenuType.ITEM, `电影`, $r('app.media.movie'),
      $r('app.media.movie'), counts.movieCount)
    allMovieItem.extra = movieMediaItem
    this.datasource.pushData(allMovieItem)

    const tvMediaItem: MediaItem = {
      name: '电视节目',
      id: 'tvs',
      image: '',
      childCount: 0,
      kind: MediaRefKind.Series,
      libraryKind: MediaLibraryKind.Series
    }
    const allTvItem = new PadMenuItem(PadMenuType.ITEM, `电视剧`, $r('app.media.tv'), $r('app.media.tv'),
      counts.seriesCount)
    allTvItem.extra = tvMediaItem
    this.datasource.pushData(allTvItem)
  }
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
  return undefined
}
