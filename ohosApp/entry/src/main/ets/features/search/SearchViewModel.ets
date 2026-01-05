import { RefreshDataSource } from "@abner/refresh_v2";
import { AxiosResponse } from "@ohos/axios";
import { getItemsApi } from "sdk";
import { BaseItemDtoQueryResult } from "sdk/src/main/ets/generated-client/models";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { getItemImageUrl, getResolutions } from "../../common/CommonFunc";
import { AppPreference } from "../../data/AppPreference";
import { ApiClient } from "../../network/ApiClient";
import { VideoItem } from "../home/hometab/HomeUIState";
import { SearchUIState } from "./SearchUIState";

export class SearchViewModel extends BaseViewModel<SearchUIState> {
  dataSource: RefreshDataSource = new RefreshDataSource() //数据懒加载操作对象，执行数据增删改查

  search(value: string, saveHistory: boolean = false) {
    getItemsApi(ApiClient.Instance()).getItems({
      userId: ApiClient.Instance().userId,
      searchTerm: value,
      fields: ['PrimaryImageAspectRatio', 'CanDelete', 'MediaSourceCount'],
      includeItemTypes: ['Episode','Movie','Video', 'Series'],
      limit: 800,
      imageTypeLimit: 1,
      enableTotalRecordCount: false,
      recursive: true,
    }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
      if (res && res.data && res.data.Items) {
        let videoList = res.data.Items.map<VideoItem>(e => {
          return {
            id: e.Id || '',
            name: e.Name || '',
            image: getItemImageUrl(e),
            subtitle: e.ProductionYear ? e.ProductionYear.toString() : '',
            score: e.CommunityRating ? e.CommunityRating : 0,
            tags: getResolutions(e.MediaStreams),
            type: e.Type,
          }
        })
        this.dataSource.initData(videoList)
        this.ui.totalCount = this.dataSource.totalCount() + ''
        if(saveHistory){
          this.saveHistory(value)
        }
      }
    })
  }

  getSearchHistories() {
    this.ui.histories = AppPreference.getInstance().getSearchHistory()
  }

  saveHistory(value: string) {
    let index = this.ui.histories.indexOf(value)
    if (index !== -1) {
      this.ui.histories.splice(index, 1)
    }
    this.ui.histories.unshift(value)
    if (this.ui.histories.length > 10) {
      this.ui.histories.pop()
    }
    AppPreference.getInstance().setSearchHistory(this.ui.histories)
  }

  clearSearchHistory() {
    this.ui.histories = []
    AppPreference.getInstance().setSearchHistory(this.ui.histories)
  }
}