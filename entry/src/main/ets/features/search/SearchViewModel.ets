import { RefreshDataSource } from "@abner/refresh_v2";
import { PageState } from "../../common/base/BaseUIState";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { AppPreference } from "../../data/AppPreference";
import { currentMediaCatalogProvider } from "../../media/MediaCatalogAccess";
import { MediaSummary } from "../../media/catalog/MediaCatalogModels";
import { MediaCatalogProvider } from "../../media/catalog/MediaCatalogProvider";
import { MediaProviderRegistry } from "../../media/MediaProviderRegistry";
import { MediaRefKind } from "../../media/MediaRef";
import { VideoItem } from "../home/hometab/HomeUIState";
import { SearchUIState } from "./SearchUIState";

export class SearchViewModel extends BaseViewModel<SearchUIState> {
  dataSource: RefreshDataSource = new RefreshDataSource() //数据懒加载操作对象，执行数据增删改查
  private requestGeneration: number = 0
  private catalog?: MediaCatalogProvider

  search(value: string, saveHistory: boolean = false): void {
    const query = value.trim()
    const generation = ++this.requestGeneration
    if (query === '') {
      this.clearResults()
      this.ui.pageState = PageState.Content
      return
    }

    this.clearResults()
    this.ui.pageState = PageState.Loading
    let request: Promise<MediaSummary[]>
    let catalog: MediaCatalogProvider
    try {
      catalog = currentMediaCatalogProvider()
      this.catalog = catalog
      request = catalog.search({
        query: query,
        kinds: [MediaRefKind.Episode, MediaRefKind.Movie, MediaRefKind.Video, MediaRefKind.Series],
        startIndex: 0,
        limit: 800,
        recursive: true
      }).then((page): MediaSummary[] => page.items)
    } catch (_error) {
      this.finishSearchFailure(generation)
      return
    }
    request.then((items: MediaSummary[]) => {
      if (!this.isCurrent(catalog, generation)) {
        return
      }
      const videoList: VideoItem[] = items.map((item: MediaSummary): VideoItem =>
        this.toVideoItem(item))
      this.dataSource.initData(videoList)
      this.ui.totalCount = this.dataSource.totalCount() + ''
      this.ui.pageState = PageState.Content
      if (saveHistory) {
        this.saveHistory(query)
      }
    }).catch(() => {
      this.finishSearchFailure(generation, catalog)
    })
  }

  cancelPendingSearch(): void {
    this.requestGeneration += 1
    this.catalog = undefined
  }

  retrySearch(): void {
    this.search(this.ui.searchText)
  }

  private clearResults(): void {
    this.dataSource.deleteAll()
    this.ui.totalCount = ''
    this.ui.errorMessage = ''
  }

  private finishSearchFailure(generation: number, catalog?: MediaCatalogProvider): void {
    if (generation !== this.requestGeneration || (catalog && !this.isCurrent(catalog, generation))) {
      return
    }
    this.clearResults()
    this.ui.errorMessage = '搜索失败，请重试'
    this.ui.pageState = PageState.Error
  }

  private isCurrent(catalog: MediaCatalogProvider, generation: number): boolean {
    const activeSession = MediaProviderRegistry.instance().getActiveSession()
    return this.catalog === catalog && generation === this.requestGeneration &&
      (!activeSession || activeSession.accountScope === catalog.session.accountScope)
  }

  private toVideoItem(item: MediaSummary): VideoItem {
    return {
      id: item.ref.id,
      name: item.title,
      image: item.primaryImage?.url || '',
      mediaRef: item.ref,
      imageRef: item.primaryImage,
      kind: item.ref.kind,
      subtitle: item.year !== undefined ? item.year.toString() : item.subtitle,
      score: item.communityRating || 0,
      tags: item.tags.slice(),
      type: this.legacyType(item.ref.kind)
    }
  }

  private legacyType(kind: MediaRefKind): string {
    if (kind === MediaRefKind.Series) {
      return 'Series'
    }
    if (kind === MediaRefKind.Episode) {
      return 'Episode'
    }
    if (kind === MediaRefKind.Movie) {
      return 'Movie'
    }
    if (kind === MediaRefKind.Video) {
      return 'Video'
    }
    return ''
  }

  getSearchHistories(): void {
    this.ui.histories = AppPreference.getInstance().getSearchHistory()
  }

  saveHistory(value: string): void {
    const query = value.trim()
    if (query === '') {
      return
    }
    const index = this.ui.histories.indexOf(query)
    if (index !== -1) {
      this.ui.histories.splice(index, 1)
    }
    this.ui.histories.unshift(query)
    if (this.ui.histories.length > 10) {
      this.ui.histories.pop()
    }
    AppPreference.getInstance().setSearchHistory(this.ui.histories)
  }

  clearSearchHistory(): void {
    this.ui.histories = []
    AppPreference.getInstance().setSearchHistory(this.ui.histories)
  }
}
