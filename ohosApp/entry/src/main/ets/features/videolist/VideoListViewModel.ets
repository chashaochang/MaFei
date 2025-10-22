import { GridManager } from "@hadss/scroll_components";
import { AxiosResponse } from "@ohos/axios";
import { getItemsApi } from "sdk";
import { BaseItemDtoQueryResult, BaseItemKind } from "sdk/src/main/ets/generated-client/models";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { getItemImageUrl, getResolutions } from "../../common/CommonFunc";
import { ApiClient } from "../../network/ApiClient";
import { VideoItem } from "../home/hometab/HomeUIState";
import { VideoListUIState } from "./VideoListUIState";

export class VideoListViewModel extends BaseViewModel<VideoListUIState> {
  gridManager: GridManager = new GridManager({ context: this.ui.context });
  scroller: ListScroller = new ListScroller()
  startIndex: number = 0
  limit: number = 100

  getVideoList(id: string, type?: BaseItemKind) {
    if (type === undefined) { //混合影片
      getItemsApi(ApiClient.Instance()).getItems({
        startIndex: this.startIndex,
        limit: this.limit,
        fields: ['PrimaryImageAspectRatio','SortName','Path','ChildCount','MediaSourceCount','PrimaryImageAspectRatio'],
        imageTypeLimit: 1,
        parentId: id,
        sortBy: ['SortName', 'ProductionYear'],
        sortOrder: ['Ascending'],
      }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
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
          this.gridManager.setDataSource(videoList)
        }
      }).catch()
    } else {
      getItemsApi(ApiClient.Instance()).getItems({
        parentId: id,
        recursive: true,
        fields: ['PrimaryImageAspectRatio', 'MediaSourceCount', 'MediaStreams'],
        imageTypeLimit: 1,
        enableImageTypes: ['Primary', 'Backdrop', 'Banner', 'Thumb'],
        sortBy: ['SortName', 'ProductionYear'],
        sortOrder: ['Ascending'],
        includeItemTypes: [type],
        startIndex: this.startIndex,
        limit: this.limit,
      }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
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
          this.gridManager.setDataSource(videoList)
        }
      }).catch()
    }
  }
}