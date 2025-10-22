import { AxiosResponse } from "@ohos/axios";
import { getUserViewsApi, ImageUrlsApi } from "sdk";
import {
  BaseItemDto,
  BaseItemDtoQueryResult, ImageType,
} from "sdk/src/main/ets/generated-client/models";
import { getTypeByCollectionType } from "../../../common/CommonFunc";
import { ApiClient } from "../../../network/ApiClient";
import { MediaItem } from "../hometab/HomeUIState";
import { MediaUIState } from "./MediaUIState";

export class MediaViewModel {
  ui: MediaUIState

  constructor(ui: MediaUIState) {
    this.ui = ui
  }

  async init() {
    //获取媒体库列表
    await this.getMediaList()
    this.ui.isRefreshing = false;
  }

  private async getMediaList(): Promise<void> {
    return new Promise((resolve, reject) => {
      getUserViewsApi(ApiClient.Instance()).getUserViews({
        userId: ApiClient.Instance().userId
      }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
        if (res.data) {
          if (res.data.TotalRecordCount && res.data.TotalRecordCount > 0) {
            this.ui.mediaList = res.data.Items?.map<MediaItem>((e: BaseItemDto) => {
              return {
                name: e.Name || '',
                image: ApiClient.Instance().basePath + new ImageUrlsApi().getItemImageUrlById(e.Id, ImageType.Primary, {
                  tag: e.ImageTags![0]
                }),
                id: e.Id || '',
                childCount: e.ChildCount || 0,
                type: getTypeByCollectionType(e.CollectionType)
              }
            }) || []
            resolve()
          }
        }
      }).catch(() => {
        reject()
      })
    })
  }
}