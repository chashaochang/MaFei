import { RefreshDataSource } from "@abner/refresh_v2";
import { AxiosResponse } from "@ohos/axios";
import { getItemsApi } from "sdk";
import { BaseItemDtoQueryResult } from "sdk/src/main/ets/generated-client/models";
import { PageState } from "../../common/base/BaseUIState";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { getItemImageUrl, getResolutions } from "../../common/CommonFunc";
import { AppPreference } from "../../data/AppPreference";
import { ApiClient } from "../../network/ApiClient";
import { VideoItem } from "../home/hometab/HomeUIState";
import { SearchUIState } from "./SearchUIState";

export class SearchViewModel extends BaseViewModel<SearchUIState> {
  dataSource: RefreshDataSource = new RefreshDataSource() //数据懒加载操作对象，执行数据增删改查
  private requestGeneration: number = 0

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
    getItemsApi(ApiClient.Instance()).getItems({
      userId: ApiClient.Instance().userId,
      searchTerm: query,
      fields: ['PrimaryImageAspectRatio', 'CanDelete', 'MediaSourceCount'],
      includeItemTypes: ['Episode', 'Movie', 'Video', 'Series'],
      limit: 800,
      imageTypeLimit: 1,
      enableTotalRecordCount: false,
      recursive: true,
    }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
      if (generation !== this.requestGeneration) {
        return
      }
      const videoList: VideoItem[] = (res.data.Items || []).map<VideoItem>((item) => {
        return {
          id: item.Id || '',
          name: item.Name || '',
          image: getItemImageUrl(item),
          subtitle: item.ProductionYear ? item.ProductionYear.toString() : '',
          score: item.CommunityRating || 0,
          tags: getResolutions(item.MediaStreams),
          type: item.Type,
        }
      })
      this.dataSource.initData(videoList)
      this.ui.totalCount = this.dataSource.totalCount() + ''
      this.ui.pageState = PageState.Content
      if (saveHistory) {
        this.saveHistory(query)
      }
    }).catch(() => {
      if (generation !== this.requestGeneration) {
        return
      }
      this.clearResults()
      this.ui.errorMessage = '搜索失败，请重试'
      this.ui.pageState = PageState.Error
    })
  }

  cancelPendingSearch(): void {
    this.requestGeneration += 1
  }

  retrySearch(): void {
    this.search(this.ui.searchText)
  }

  private clearResults(): void {
    this.dataSource.deleteAll()
    this.ui.totalCount = ''
    this.ui.errorMessage = ''
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
