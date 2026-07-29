import { RefreshController, RefreshDataSource } from "@abner/refresh_v2";
import { AxiosResponse } from "@ohos/axios";
import { getItemsApi } from "sdk";
import { BaseItemDtoQueryResult, BaseItemKind, ItemSortBy } from "sdk/src/main/ets/generated-client/models";
import { PageState } from "../../common/base/BaseUIState";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { getItemImageUrl, getResolutions } from "../../common/CommonFunc";
import { ListDialog } from "../../component/ListDialog";
import { sortOptions } from "../../entity/SortOption";
import { ApiClient } from "../../network/ApiClient";
import { AppThemeSurfaceResolver } from "../../theme/AppThemeSurfaceResolver";
import { MediaItem, VideoItem } from "../home/hometab/HomeUIState";
import { VideoListUIState } from "./VideoListUIState";

export class VideoListViewModel extends BaseViewModel<VideoListUIState> {
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
    this.getVideoList()
    this.sortDialog.dismiss()
  }, () => AppThemeSurfaceResolver.useNativeSurface(
    this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable))

  init(mediaItem: MediaItem) {
    this.ui.title = mediaItem.name
    this.mediaItem = mediaItem
    this.refresh()
  }

  getVideoList() {
    if (this.mediaItem!.type === undefined) { //混合影片
      getItemsApi(ApiClient.Instance()).getItems({
        startIndex: this.startIndex,
        limit: this.limit,
        fields: ['PrimaryImageAspectRatio', 'SortName', 'Path', 'ChildCount', 'MediaSourceCount',
          'PrimaryImageAspectRatio'],
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
          let videoList = res.data.Items.map<VideoItem>(e => {
            return {
              id: e.Id || '',
              name: e.Name || '',
              image: getItemImageUrl(e),
              subtitle: e.ProductionYear ? e.ProductionYear.toString() : '',
              score: e.CommunityRating ? e.CommunityRating : 0,
              tags: getResolutions(e.MediaStreams),
              mediaName: this.ui.title,
              type: e.Type,
            }
          })
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
        fields: ['PrimaryImageAspectRatio', 'MediaSourceCount', 'MediaStreams'],
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
          let videoList = res.data.Items.map<VideoItem>(e => {
            return {
              id: e.Id || '',
              name: e.Name || '',
              image: getItemImageUrl(e),
              subtitle: e.ProductionYear ? e.ProductionYear.toString() : '',
              score: e.CommunityRating ? e.CommunityRating : 0,
              tags: getResolutions(e.MediaStreams),
              mediaName: this.ui.title,
              type: e.Type,
            }
          })
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
    this.startIndex = 0
    this.getVideoList()
  }

  loadMore() {
    this.getVideoList()
  }
}
