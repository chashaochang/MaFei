import { RefreshController, RefreshDataSource } from "@abner/refresh_v2";
import { AxiosResponse } from "@ohos/axios";
import { getItemsApi } from "sdk";
import { BaseItemDto, BaseItemDtoQueryResult, BaseItemKind, ItemSortBy } from "sdk/src/main/ets/generated-client/models";
import { PageState } from "../../common/base/BaseUIState";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { getItemImageUrl, getResolutions } from "../../common/CommonFunc";
import { ListDialog } from "../../component/ListDialog";
import { sortOptions } from "../../entity/SortOption";
import { ApiClient } from "../../network/ApiClient";
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
  controller: RefreshController = new RefreshController() //刷新控制器，声明全局变量
  dataSource: RefreshDataSource = new RefreshDataSource() //数据懒加载操作对象，执行数据增删改查
  mediaItem?: MediaItem
  startIndex: number = 0
  limit: number = 100
  sortBy: ItemSortBy[] = [sortOptions[this.ui.selectedSortIndex].value]
  sortDialog = new ListDialog("排序", sortOptions.map((e) => e.label), this.ui.selectedSortIndex, (index) => {
    this.ui.selectedSortIndex = index
    this.sortBy = [sortOptions[this.ui.selectedSortIndex].value]
    this.startIndex = 0
    this.clearSelectionAndReload()
    this.getVideoList()
    this.sortDialog.dismiss()
  }, () => AppThemeSurfaceResolver.useNativeSurface(
    this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable))

  constructor(ui: VideoListUIState,
    deleteRepository: ManagementMediaDeleteRepository = new ManagementMediaDeleteRepository()) {
    super(ui)
    this.deleteRepository = deleteRepository
  }

  init(mediaItem: MediaItem) {
    this.ui.title = mediaItem.name
    this.mediaItem = mediaItem
    this.loadDeleteAccess()
    this.refresh()
  }

  private async loadDeleteAccess(): Promise<void> {
    const result = await this.deleteRepository.requireAdministrator()
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

  private toVideoItem(item: BaseItemDto): VideoItem {
    return {
      id: item.Id || '',
      name: item.Name || '',
      image: getItemImageUrl(item),
      subtitle: item.ProductionYear ? item.ProductionYear.toString() : '',
      score: item.CommunityRating ? item.CommunityRating : 0,
      tags: getResolutions(item.MediaStreams),
      mediaName: this.ui.title,
      type: item.Type,
      canDelete: item.CanDelete === true,
      path: item.Path || ''
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

  async deleteSelection(): Promise<ManagementLibraryOperationResult> {
    if (this.ui.deletingSelection) {
      return ManagementLibraryOperationResult.failure(
        ManagementLibraryOperationCode.Conflict, 'management_media_delete_conflict')
    }
    const targets = this.selectedTargets()
    if (targets.length === 0) {
      return ManagementLibraryOperationResult.failure(
        ManagementLibraryOperationCode.PermissionDenied, 'management_media_delete_no_eligible')
    }
    this.ui.deletingSelection = true
    try {
      const result = await this.deleteRepository.deleteTargets(targets)
      if (result.ok) {
        this.clearSelectionAndReload()
        this.startIndex = 0
        this.getVideoList()
      }
      return result
    } finally {
      this.ui.deletingSelection = false
    }
  }

  getVideoList() {
    if (this.mediaItem!.type === undefined) { //混合影片
      getItemsApi(ApiClient.Instance()).getItems({
        startIndex: this.startIndex,
        limit: this.limit,
        fields: ['PrimaryImageAspectRatio', 'SortName', 'CanDelete', 'Path', 'ChildCount',
          'MediaSourceCount'],
        imageTypeLimit: 1,
        parentId: this.mediaItem!.id,
        sortBy: this.sortBy,
        sortOrder: this.ui.isDesc ? ['Descending'] : ['Ascending'],
      }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
        let noMore = false
        if (res.data && res.data.Items && res.data.Items?.length < this.limit) {
          noMore = true
        }
        if (res.data && res.data.Items && res.data.Items.length > 0) {
          let videoList = res.data.Items.map<VideoItem>((item: BaseItemDto) => this.toVideoItem(item))
          if (res.data.StartIndex == 0) {
            this.ui.pageState = PageState.Content
            this.dataSource.initData(videoList)
            //数据懒加载使用：1、数组数据添加，this.dataSource.pushDataArray()，2、单个数据添加，this.dataSource.pushData()
            this.controller.finishRefresh() //关闭下拉刷新，在数据请求回后进行关闭
          } else {
            this.dataSource.pushDataArray(videoList)
          }
          this.ui.totalCount = this.dataSource.totalCount() + ''
          this.controller.finishLoadMore(noMore)
          this.startIndex = this.dataSource.totalCount()
        } else {
          if (this.startIndex === 0) {
            this.ui.pageState = PageState.Content
          }
          if (res.data.Items?.length === 0) {
            this.dataSource.deleteAll()
          }
          this.controller.finishRefresh()
          this.controller.finishLoadMore(noMore)
        }
      }).catch(() => {
        if (this.startIndex === 0) {
          this.ui.pageState = PageState.Error
        }
        this.controller.finishRefresh()
        this.controller.finishLoadMore(false)
      })
    } else {
      let isAll = this.mediaItem?.id === 'all'
      let includeItemTypes = [this.mediaItem!.type]
      let parentId: string | undefined = undefined
      if (isAll) {
        includeItemTypes = [BaseItemKind.Video, BaseItemKind.Movie, BaseItemKind.Series]
      } else if (this.mediaItem?.id === 'movies') {
        includeItemTypes = [BaseItemKind.Movie]
      } else if (this.mediaItem?.id === 'tvs') {
        includeItemTypes = [BaseItemKind.Series]
      } else {
        parentId = this.mediaItem!.id
      }
      getItemsApi(ApiClient.Instance()).getItems({
        parentId: parentId,
        recursive: true,
        fields: ['PrimaryImageAspectRatio', 'CanDelete', 'Path', 'MediaSourceCount', 'MediaStreams'],
        imageTypeLimit: 1,
        enableImageTypes: ['Primary', 'Backdrop', 'Banner', 'Thumb'],
        sortBy: this.sortBy,
        sortOrder: this.ui.isDesc ? ['Descending'] : ['Ascending'],
        includeItemTypes: includeItemTypes,
        startIndex: this.startIndex,
        limit: this.limit,
      }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
        let noMore = false
        if (res.data && res.data.Items && res.data.Items?.length < this.limit) {
          noMore = true
        }
        if (res.data && res.data.Items && res.data.Items.length > 0) {
          let videoList = res.data.Items.map<VideoItem>((item: BaseItemDto) => this.toVideoItem(item))
          if (res.data.StartIndex == 0) {
            this.ui.pageState = PageState.Content
            this.dataSource.initData(videoList)
            //数据懒加载使用：1、数组数据添加，this.dataSource.pushDataArray()，2、单个数据添加，this.dataSource.pushData()
            this.controller.finishRefresh() //关闭下拉刷新，在数据请求回后进行关闭
          } else {
            this.dataSource.pushDataArray(videoList)
          }
          this.controller.finishLoadMore(noMore)
          this.startIndex = this.dataSource.totalCount()
        } else {
          if (this.startIndex === 0) {
            this.ui.pageState = PageState.Content
          }
          if (res.data.Items?.length === 0) {
            this.dataSource.deleteAll()
          }
          this.controller.finishRefresh()
          this.controller.finishLoadMore(noMore)
        }
        this.ui.totalCount = this.dataSource.totalCount() + ''
      }).catch(() => {
        if (this.startIndex === 0) {
          this.ui.pageState = PageState.Error
        }
        this.controller.finishRefresh()
        this.controller.finishLoadMore(false)
      })
    }
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
