import { RefreshController, RefreshDataSource } from "@abner/refresh_v2";
import { PageState } from "../../common/base/BaseUIState";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { ListDialog } from "../../component/ListDialog";
import { sortOptions } from "../../entity/SortOption";
import { currentMediaCatalogProvider } from "../../media/MediaCatalogAccess";
import { MediaPage, MediaSummary } from "../../media/catalog/MediaCatalogModels";
import {
  MediaCatalogProvider,
  MediaCatalogSortDirection,
  MediaCatalogSortField
} from "../../media/catalog/MediaCatalogProvider";
import { mediaCatalogSortField } from "../../media/catalog/MediaCatalogSortFieldMapper";
import { MediaRefKind } from "../../media/MediaRef";
import { AppThemeSurfaceResolver } from "../../theme/AppThemeSurfaceResolver";
import { VideoItem } from "../home/hometab/HomeUIState";
import { FavoriteListUIState } from "./FavoriteListUIState";

export class FavoriteListViewModel extends BaseViewModel<FavoriteListUIState> {
  controller: RefreshController = new RefreshController() //刷新控制器，声明全局变量
  dataSource: RefreshDataSource = new RefreshDataSource() //数据懒加载操作对象，执行数据增删改查
  startIndex: number = 0
  limit: number = 100
  private catalog?: MediaCatalogProvider
  private requestGeneration: number = 0
  sortBy: MediaCatalogSortField[] = [mediaCatalogSortField(sortOptions[this.ui.selectedSortIndex].value)]
  sortDialog = new ListDialog("排序", sortOptions.map((e) => e.label), this.ui.selectedSortIndex, (index) => {
    this.ui.selectedSortIndex = index
    this.sortBy = [mediaCatalogSortField(sortOptions[this.ui.selectedSortIndex].value)]
    this.refresh()
    this.sortDialog.dismiss()
  }, () => AppThemeSurfaceResolver.useNativeSurface(
    this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable))
  queryTypes: MediaRefKind[] = [
    MediaRefKind.Movie,
    MediaRefKind.Series,
    MediaRefKind.Episode,
    MediaRefKind.Video,
  ];

  getFavoriteList(): void {
    let catalog: MediaCatalogProvider
    try {
      catalog = currentMediaCatalogProvider()
    } catch (_error) {
      this.finishFailure()
      return
    }
    this.catalog = catalog
    const generation = ++this.requestGeneration
    if (!catalog.session.capabilities.favorites) {
      this.finishEmpty(catalog, generation)
      return
    }
    catalog.getItems({
        kinds: this.queryTypes,
        startIndex: this.startIndex,
        limit: this.limit,
        sortFields: this.sortBy,
        sortDirections: [this.ui.isDesc ? MediaCatalogSortDirection.Descending :
          MediaCatalogSortDirection.Ascending],
        recursive: true,
        collapseBoxSetItems: false,
        favoriteOnly: true
      })
      .then((page: MediaPage<MediaSummary>) => {
        if (!this.isCurrent(catalog, generation)) {
          return
        }
        const noMore = !page.hasMore
        if (page.items.length > 0) {
          const videoList = page.items.map<VideoItem>((item: MediaSummary): VideoItem => {
            return {
              id: item.ref.id,
              name: item.title,
              image: item.primaryImage?.url || '',
              imageRef: item.primaryImage,
              mediaRef: item.ref,
              kind: item.ref.kind,
              subtitle: item.year !== undefined ? item.year.toString() : item.subtitle,
              score: item.communityRating || 0,
              tags: item.tags.slice(),
              mediaName: this.ui.title,
              type: this.legacyType(item.ref.kind),
            }
          })
          if (page.startIndex === 0) {
            this.ui.pageState = PageState.Content
            this.dataSource.initData(videoList)
            this.ui.isRefreshing = false
            this.controller.finishRefresh()
          } else {
            this.dataSource.pushDataArray(videoList)
          }
          this.controller.finishLoadMore(noMore)
          this.startIndex = page.nextStartIndex
        } else {
          if (page.startIndex === 0) {
            this.dataSource.deleteAll()
          }
          this.ui.pageState = PageState.Content
          this.ui.isRefreshing = false
          this.controller.finishRefresh()
          this.controller.finishLoadMore(noMore)
        }
        this.ui.totalCount = this.dataSource.totalCount() + ''
      })
      .catch(() => {
        if (!this.isCurrent(catalog, generation)) {
          return
        }
        this.controller.finishRefresh()
        this.controller.finishLoadMore(false)
        this.ui.isRefreshing = false
        if (this.startIndex === 0) {
          this.ui.pageState = PageState.Error
        }
      })
  }

  private legacyType(kind: MediaRefKind): string {
    if (kind === MediaRefKind.Series) return 'Series'
    if (kind === MediaRefKind.Episode) return 'Episode'
    if (kind === MediaRefKind.Movie) return 'Movie'
    if (kind === MediaRefKind.Video) return 'Video'
    return ''
  }

  private isCurrent(catalog: MediaCatalogProvider, generation: number): boolean {
    return this.catalog === catalog && generation === this.requestGeneration &&
      catalog.session.accountScope.length > 0
  }

  private finishEmpty(catalog: MediaCatalogProvider, generation: number): void {
    if (!this.isCurrent(catalog, generation)) return
    this.dataSource.deleteAll()
    this.ui.totalCount = '0'
    this.ui.pageState = PageState.Content
    this.ui.isRefreshing = false
    this.controller.finishRefresh()
    this.controller.finishLoadMore(true)
  }

  private finishFailure(): void {
    this.ui.isRefreshing = false
    this.controller.finishRefresh()
    this.controller.finishLoadMore(false)
    if (this.startIndex === 0) this.ui.pageState = PageState.Error
  }

  refresh() {
    this.ui.isRefreshing = true
    this.startIndex = 0
    this.getFavoriteList()
  }

  loadMore() {
    this.getFavoriteList()
  }
}
