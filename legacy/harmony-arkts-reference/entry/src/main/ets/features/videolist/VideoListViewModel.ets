import { RefreshController, RefreshDataSource } from "@abner/refresh_v2";
import { PageState } from "../../common/base/BaseUIState";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { ListDialog } from "../../component/ListDialog";
import { sortOptions } from "../../entity/SortOption";
import { MediaLibraryRefreshEvent } from "../../events/Events";
import { currentMediaCatalogProvider } from "../../media/MediaCatalogAccess";
import { MediaLibraryKind, MediaSummary } from "../../media/catalog/MediaCatalogModels";
import {
  MediaCatalogProvider,
  MediaCatalogSortDirection,
  MediaCatalogSortField
} from "../../media/catalog/MediaCatalogProvider";
import { mediaCatalogSortField } from "../../media/catalog/MediaCatalogSortFieldMapper";
import { MediaRef, MediaRefKind } from "../../media/MediaRef";
import { MediaProviderRegistry } from "../../media/MediaProviderRegistry";
import { AppThemeSurfaceResolver } from "../../theme/AppThemeSurfaceResolver";
import { MediaItem, VideoItem } from "../home/hometab/HomeUIState";
import {
  ManagementLibraryOperationCode,
  ManagementLibraryOperationResult,
  ManagementMediaDeleteSummary,
  ManagementMediaDeleteTarget
} from "../management/library/ManagementLibraryModels";
import { ManagementMediaDeletePolicy } from "../management/library/ManagementMediaDeletePolicy";
import { ManagementMediaDeleteRepository } from "../management/library/ManagementMediaDeleteRepository";
import { VideoListUIState } from "./VideoListUIState";

export class VideoListViewModel extends BaseViewModel<VideoListUIState> {
  private deleteRepository: ManagementMediaDeleteRepository
  private mediaLibraryRefreshPublisher?: () => void
  private catalog?: MediaCatalogProvider
  private requestGeneration: number = 0
  controller: RefreshController = new RefreshController() //刷新控制器，声明全局变量
  dataSource: RefreshDataSource = new RefreshDataSource() //数据懒加载操作对象，执行数据增删改查
  mediaItem?: MediaItem
  startIndex: number = 0
  limit: number = 100
  sortBy: MediaCatalogSortField[] = [mediaCatalogSortField(sortOptions[this.ui.selectedSortIndex].value)]
  sortDialog = new ListDialog("排序", sortOptions.map((e) => e.label), this.ui.selectedSortIndex, (index) => {
    this.ui.selectedSortIndex = index
    this.sortBy = [mediaCatalogSortField(sortOptions[this.ui.selectedSortIndex].value)]
    this.startIndex = 0
    this.clearSelectionAndReload()
    this.getVideoList()
    this.sortDialog.dismiss()
  }, () => AppThemeSurfaceResolver.useNativeSurface(
    this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable))

  constructor(ui: VideoListUIState,
    deleteRepository: ManagementMediaDeleteRepository = new ManagementMediaDeleteRepository(),
    mediaLibraryRefreshPublisher?: () => void) {
    super(ui)
    this.deleteRepository = deleteRepository
    this.mediaLibraryRefreshPublisher = mediaLibraryRefreshPublisher
  }

  init(mediaItem: MediaItem) {
    this.ui.title = mediaItem.name
    this.mediaItem = mediaItem
    try {
      this.catalog = currentMediaCatalogProvider()
    } catch (_error) {
      this.catalog = undefined
      this.ui.pageState = PageState.Error
      return
    }
    this.loadDeleteAccess()
    this.refresh()
  }

  private async loadDeleteAccess(): Promise<void> {
    const catalog = this.catalog
    if (!catalog?.session.capabilities.management) {
      this.ui.isAdministrator = false
      this.ui.clearSelection()
      this.dataSource.reloadData()
      return
    }
    const result = await this.deleteRepository.requireAdministrator()
    if (!this.isCurrentCatalog(catalog)) {
      return
    }
    this.ui.isAdministrator = result.ok
    if (!result.ok) {
      this.ui.clearSelection()
    }
    this.dataSource.reloadData()
  }

  private clearSelectionAndReload(): void {
    this.ui.clearSelection()
    this.dataSource.reloadData()
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
      mediaName: this.ui.title,
      type: this.legacyType(item.ref.kind),
      canDelete: this.catalog?.session.capabilities.management === true && item.canDelete === true,
      path: item.path || ''
    }
  }

  private deleteTarget(item: VideoItem): ManagementMediaDeleteTarget {
    const target = new ManagementMediaDeleteTarget()
    target.id = item.id
    target.name = item.name
    target.path = item.path || ''
    target.canDelete = item.canDelete === true
    return target
  }

  canSelect(item: VideoItem): boolean {
    return ManagementMediaDeletePolicy.canDeleteTarget(
      this.ui.isAdministrator, this.deleteTarget(item))
  }

  enterSelection(item: VideoItem): void {
    if (this.canSelect(item)) {
      this.ui.enterSelection(item.id)
      this.dataSource.reloadData()
    }
  }

  toggleSelection(item: VideoItem): void {
    if (this.ui.isSelected(item.id) || this.canSelect(item)) {
      this.ui.toggleSelection(item.id)
      this.dataSource.reloadData()
    }
  }

  cancelSelection(): void {
    this.clearSelectionAndReload()
  }

  private selectedTargets(): ManagementMediaDeleteTarget[] {
    return this.dataSource.getDataAll()
      .map((value: Object): VideoItem => value as VideoItem)
      .filter((item: VideoItem) => this.ui.isSelected(item.id))
      .map((item: VideoItem): ManagementMediaDeleteTarget => this.deleteTarget(item))
  }

  deleteSummary(): ManagementMediaDeleteSummary {
    return ManagementMediaDeletePolicy.summarize(
      this.selectedTargets(), this.ui.isAdministrator)
  }

  async prepareDeleteSelection(): Promise<ManagementLibraryOperationResult> {
    const targets = this.selectedTargets()
    if (targets.length === 0) {
      return ManagementLibraryOperationResult.failure(
        ManagementLibraryOperationCode.PermissionDenied, 'management_media_delete_no_eligible')
    }
    const preparation = await this.deleteRepository.prepareTargets(targets)
    if (!preparation.operation.ok) {
      if (preparation.operation.code === ManagementLibraryOperationCode.PermissionDenied) {
        this.ui.isAdministrator = false
        this.clearSelectionAndReload()
      }
      return preparation.operation
    }
    const items = this.dataSource.getDataAll()
    items.forEach((value: Object) => {
      const item = value as VideoItem
      const refreshed = preparation.targets.find(
        (candidate: ManagementMediaDeleteTarget) => candidate.id === item.id)
      if (refreshed) {
        item.name = refreshed.name
        item.path = refreshed.path
        item.canDelete = refreshed.canDelete
      }
    })
    this.dataSource.reloadData()
    return preparation.operation
  }

  private publishMediaLibraryRefresh(): void {
    if (this.mediaLibraryRefreshPublisher) {
      this.mediaLibraryRefreshPublisher()
      return
    }
    this.ui.context.getHostContext()?.eventHub.emit(MediaLibraryRefreshEvent)
  }

  private removeDeletedTargets(deletedIds: string[]): void {
    const remaining = this.dataSource.getDataAll().filter((value: Object): boolean =>
      !deletedIds.includes((value as VideoItem).id))
    this.dataSource.initData(remaining)
    this.ui.totalCount = this.dataSource.totalCount() + ''
  }

  async deleteSelection(): Promise<ManagementLibraryOperationResult> {
    if (this.ui.deletingSelection) {
      return ManagementLibraryOperationResult.failure(
        ManagementLibraryOperationCode.Conflict, 'management_media_delete_conflict')
    }
    const targets = this.selectedTargets()
    const summary = ManagementMediaDeletePolicy.summarize(targets, this.ui.isAdministrator)
    if (summary.allowedIds.length === 0) {
      return ManagementLibraryOperationResult.failure(
        ManagementLibraryOperationCode.PermissionDenied, 'management_media_delete_no_eligible')
    }
    this.ui.deletingSelection = true
    try {
      const result = await this.deleteRepository.deleteTargets(targets)
      if (result.ok) {
        this.removeDeletedTargets(summary.allowedIds)
        this.clearSelectionAndReload()
        this.publishMediaLibraryRefresh()
        this.startIndex = 0
        this.getVideoList()
      }
      return result
    } finally {
      this.ui.deletingSelection = false
    }
  }

  getVideoList(): void {
    const catalog = this.catalog
    const mediaItem = this.mediaItem
    if (!catalog || !mediaItem) {
      this.finishRequestFailure()
      return
    }
    const generation = ++this.requestGeneration
    const requestStartIndex = this.startIndex
    const kinds = this.queryKinds(mediaItem)
    const libraryRef = this.libraryRef(catalog, mediaItem)
    const recursive = mediaItem.kind !== undefined || mediaItem.libraryKind !== undefined || mediaItem.id === 'all' ||
      mediaItem.id === 'movies' || mediaItem.id === 'tvs'
    catalog.getItems({
      libraryRef: libraryRef,
      kinds: kinds,
      startIndex: requestStartIndex,
      limit: this.limit,
      sortFields: this.sortBy,
      sortDirections: [this.ui.isDesc ? MediaCatalogSortDirection.Descending :
        MediaCatalogSortDirection.Ascending],
      recursive: recursive,
      collapseBoxSetItems: false,
      favoriteOnly: false
    }).then((page) => {
      if (!this.isCurrentRequest(catalog, generation)) {
        return
      }
      const videoList = page.items.map((item: MediaSummary): VideoItem => this.toVideoItem(item))
      if (page.startIndex === 0) {
        this.ui.pageState = PageState.Content
        this.dataSource.initData(videoList)
        this.controller.finishRefresh()
      } else {
        this.dataSource.pushDataArray(videoList)
      }
      if (videoList.length === 0 && page.startIndex === 0) {
        this.dataSource.deleteAll()
      }
      this.controller.finishLoadMore(!page.hasMore)
      this.startIndex = page.nextStartIndex
      this.ui.totalCount = this.dataSource.totalCount() + ''
    }).catch(() => {
      if (this.isCurrentRequest(catalog, generation)) {
        this.finishRequestFailure()
      }
    })
  }

  private isCurrentCatalog(catalog: MediaCatalogProvider): boolean {
    const activeSession = MediaProviderRegistry.instance().getActiveSession()
    return this.catalog === catalog &&
      (!activeSession || activeSession.accountScope === catalog.session.accountScope)
  }

  private isCurrentRequest(catalog: MediaCatalogProvider, generation: number): boolean {
    return generation === this.requestGeneration && this.isCurrentCatalog(catalog)
  }

  private finishRequestFailure(): void {
    if (this.startIndex === 0) {
      this.ui.pageState = PageState.Error
    }
    this.controller.finishRefresh()
    this.controller.finishLoadMore(false)
  }

  private libraryRef(catalog: MediaCatalogProvider, mediaItem: MediaItem): MediaRef | undefined {
    if (mediaItem.id === 'all' || mediaItem.id === 'movies' || mediaItem.id === 'tvs') {
      return undefined
    }
    if (mediaItem.mediaRef && mediaItem.mediaRef.provider === catalog.session.provider &&
      mediaItem.mediaRef.accountScope === catalog.session.accountScope) {
      return mediaItem.mediaRef
    }
    return new MediaRef(
      catalog.session.provider,
      catalog.session.accountScope,
      mediaItem.id,
      MediaRefKind.Library
    )
  }

  private queryKinds(mediaItem: MediaItem): MediaRefKind[] {
    if (mediaItem.id === 'all') {
      return [MediaRefKind.Video, MediaRefKind.Movie, MediaRefKind.Series]
    }
    if (mediaItem.id === 'movies' || mediaItem.kind === MediaRefKind.Movie ||
      mediaItem.libraryKind === MediaLibraryKind.Movies || mediaItem.type === 'Movie') {
      return [MediaRefKind.Movie]
    }
    if (mediaItem.id === 'tvs' || mediaItem.kind === MediaRefKind.Series ||
      mediaItem.libraryKind === MediaLibraryKind.Series || mediaItem.type === 'Series') {
      return [MediaRefKind.Series]
    }
    if (mediaItem.kind === MediaRefKind.Episode || mediaItem.type === 'Episode') {
      return [MediaRefKind.Episode]
    }
    if (mediaItem.kind === MediaRefKind.Video || mediaItem.type === 'Video') {
      return [MediaRefKind.Video]
    }
    return []
  }

  private legacyType(kind: MediaRefKind): string {
    if (kind === MediaRefKind.Series) {
      return 'Series'
    }
    if (kind === MediaRefKind.Season) {
      return 'Season'
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

  refresh() {
    this.clearSelectionAndReload()
    this.startIndex = 0
    this.getVideoList()
  }

  loadMore() {
    this.getVideoList()
  }
}
