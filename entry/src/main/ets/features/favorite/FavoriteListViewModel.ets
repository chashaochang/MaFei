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
import { VideoItem } from "../home/hometab/HomeUIState";
import { FavoriteListUIState } from "./FavoriteListUIState";

export class FavoriteListViewModel extends BaseViewModel<FavoriteListUIState> {
  controller: RefreshController = new RefreshController() //刷新控制器，声明全局变量
  dataSource: RefreshDataSource = new RefreshDataSource() //数据懒加载操作对象，执行数据增删改查
  startIndex: number = 0
  limit: number = 100
  sortBy: ItemSortBy[] = [sortOptions[this.ui.selectedSortIndex].value]
  sortDialog = new ListDialog("排序", sortOptions.map((e) => e.label), this.ui.selectedSortIndex, (index) => {
    this.ui.selectedSortIndex = index
    this.sortBy = [sortOptions[this.ui.selectedSortIndex].value]
    this.startIndex = 0
    this.getFavoriteList()
    this.sortDialog.dismiss()
  }, () => AppThemeSurfaceResolver.useNativeSurface(
    this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable))
  queryTypes: BaseItemKind[] = [
    BaseItemKind.Movie,
    BaseItemKind.Series,
    // BaseItemKind.Season, //api不支持season类型数量查询
    BaseItemKind.Episode,
    BaseItemKind.Video,
    // BaseItemKind.MusicVideo,
    // BaseItemKind.BoxSet,
    // BaseItemKind.Playlist,
    // BaseItemKind.MusicAlbum,
    // BaseItemKind.Audio,
    // BaseItemKind.Book,
    // BaseItemKind.LiveTvChannel,
    // BaseItemKind.PhotoAlbum,
    // BaseItemKind.Photo,
  ];

  getFavoriteList() {
    getItemsApi(ApiClient.Instance())
      .getItems({
        sortBy: this.sortBy,
        sortOrder: this.ui.isDesc ? ['Descending'] : ['Ascending'],
        filters: ['IsFavorite'],
        recursive: true,
        fields: ['PrimaryImageAspectRatio'],
        collapseBoxSetItems: false,
        excludeLocationTypes: ['Virtual'],
        enableTotalRecordCount: false,
        limit: this.limit,
        startIndex: this.startIndex,
        includeItemTypes: this.queryTypes
      })
      .then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
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
          if(res.data.Items?.length === 0){
            this.dataSource.deleteAll()
          }
          this.ui.pageState = PageState.Content
          this.controller.finishRefresh()
          this.controller.finishLoadMore(noMore)
        }
        this.ui.totalCount = this.dataSource.totalCount() + ''
      })
      .catch(() => {
        this.controller.finishRefresh()
        this.controller.finishLoadMore(false)
        if (this.startIndex === 0) {
          this.ui.pageState = PageState.Error
        }
      })
  }

  refresh() {
    this.startIndex = 0
    this.getFavoriteList()
  }

  loadMore() {
    this.getFavoriteList()
  }
}
