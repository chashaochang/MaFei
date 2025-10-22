import { AxiosResponse } from "@ohos/axios";
import { getItemsApi, getTvShowsApi, getUserLibraryApi, getUserViewsApi, ImageUrlsApi } from "sdk";
import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  BaseItemKind,
  ImageType,
  ItemFields,
} from "sdk/src/main/ets/generated-client/models";
import { getItemImageUrl, getTypeByCollectionType, parseVideoType } from "../../../common/CommonFunc";
import { ApiClient } from "../../../network/ApiClient";
import { HomeUIState, MediaItem, VideoItem } from "./HomeUIState";

export class HomeViewModel {
  ui: HomeUIState

  constructor(ui: HomeUIState) {
    this.ui = ui
  }

  async init() {
    //获取媒体库列表
    await this.getMediaList()
    //获取继续观看列表
    await this.getResumeList()
    //获取接下来列表
    await this.getNextUpList()
    //获取latest
    await this.getLatestList()
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
      }).catch((e:Error) => {
        reject()
      })
    })
  }

  private getResumeList(): Promise<void> {
    return new Promise((resolve, reject) => {
      ///Users/4ce4745330314948b8791958423de149/Items/Resume?Limit=12&Recursive=true&Fields=PrimaryImageAspectRatio&ImageTypeLimit=1&EnableImageTypes=Primary%2CBackdrop%2CThumb&EnableTotalRecordCount=false&MediaTypes=Video
      getItemsApi(ApiClient.Instance()).getResumeItems({
        limit: 12,
        fields: [ItemFields.PrimaryImageAspectRatio],
        imageTypeLimit: 1,
        enableImageTypes: [ImageType.Primary, ImageType.Backdrop, ImageType.Thumb],
        enableTotalRecordCount: false,
        mediaTypes: ['Video'],
        userId: ApiClient.Instance().userId
      }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
        if (res.data) {
          if (res.data.Items && res.data.Items.length > 0) {
            let list = res.data.Items.map<VideoItem>((e: BaseItemDto) => {
              let imageId = ''
              let name = ''
              let subtitle = ''
              switch (e.Type) {
                case BaseItemKind.Movie:
                  imageId = e.Id || ''
                  name = e.Name || ''
                  subtitle = parseVideoType(e.Type)
                  break
                case BaseItemKind.Episode:
                  imageId = e.ParentThumbItemId || ''
                  name = e.SeriesName || ''
                  subtitle = `${e.SeasonName} · ${e.Name}`
                  break
                default:
                  imageId = e.Id || ''
                  name = e.Name || ''
                  subtitle = parseVideoType(e.Type)
                  break
              }
              return {
                id: e.Id || '',
                name: name,
                subtitle: subtitle,
                image: ApiClient.Instance().basePath +
                new ImageUrlsApi().getItemImageUrlById(imageId, ImageType.Thumb, {
                  tag: e.ImageTags![0]
                }),
                type: e.Type,
                percent: e.UserData?.PlayedPercentage ? Math.round(e.UserData.PlayedPercentage) : 0
              }
            }) || []
            this.ui.resumeList = list
            resolve()
          }
        }
      }).catch(() => {
        reject()
      })
    })
  }

  private getNextUpList(): Promise<void> {
    return new Promise((resolve, reject) => {
      let now = new Date()
      now.setFullYear(now.getFullYear() - 1)
      //Shows/NextUp?Limit=24&UserId=4ce4745330314948b8791958423de149&ImageTypeLimit=1&EnableImageTypes=Primary%2CBackdrop%2CBanner%2CThumb&EnableTotalRecordCount=false&DisableFirstEpisode=false&NextUpDateCutoff=2024-10-13T18%3A57%3A41.687Z&EnableResumable=false&EnableRewatching=false
      getTvShowsApi(ApiClient.Instance()).getNextUp({
        limit: 24,
        fields: [ItemFields.PrimaryImageAspectRatio, ItemFields.DateCreated, ItemFields.Path,
          ItemFields.MediaSourceCount],
        userId: ApiClient.Instance().userId,
        imageTypeLimit: 1,
        enableImageTypes: [ImageType.Primary, ImageType.Backdrop, ImageType.Banner, ImageType.Thumb],
        enableTotalRecordCount: false,
        disableFirstEpisode: false,
        nextUpDateCutoff: (now).toISOString(),
        enableResumable: false,
        enableRewatching: false
      }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
        if (res.data) {
          if (res.data.Items && res.data.Items.length > 0) {
            let list = res.data.Items.map<VideoItem>((e: BaseItemDto) => {
              return {
                id: e.Id || '',
                name: e.SeriesName || '',
                image: ApiClient.Instance().basePath +
                new ImageUrlsApi().getItemImageUrlById(e.ParentThumbItemId, ImageType.Thumb, {
                  tag: e.ParentThumbImageTag
                }),
                subtitle: `S${e.ParentIndexNumber}:E${e.IndexNumber} - ${e.Name}`,
              }
            }) || []
            this.ui.nextUpList = list
            resolve()
          }
        }
      }).catch(() => {
        reject()
      })
    })
  }

  private async getLatestList() {
    this.ui.latestMediaList = []
    this.ui.latestMap.clear()
    for (const item of this.ui.mediaList) {
      let list: VideoItem[] = []
      ///Users/4ce4745330314948b8791958423de149/Items/Latest?Limit=16&Fields=PrimaryImageAspectRatio%2CPath&ImageTypeLimit=1&EnableImageTypes=Primary%2CBackdrop%2CThumb&ParentId=66e9b0b6aea8727e12b214521b59c67d
      try {
        let res: AxiosResponse<BaseItemDto[]> = await getUserLibraryApi(ApiClient.Instance()).getLatestMedia({
          limit: 16,
          fields: [ItemFields.PrimaryImageAspectRatio, ItemFields.Path],
          imageTypeLimit: 1,
          enableImageTypes: [ImageType.Primary, ImageType.Backdrop, ImageType.Thumb],
          parentId: item.id,
          userId: ApiClient.Instance().userId
        })
        if (res.data && res.data.length > 0) {
          list = res.data.map<VideoItem>((e) => {
            let subtitle = ''
            if (e.Type === BaseItemKind.Series) {
              subtitle = e.ProductionYear ? e.ProductionYear.toString() : ''
            } else if (e.Type === BaseItemKind.Episode) {
              subtitle = `S${e.ParentIndexNumber}:E${e.IndexNumber} - ${e.Name}`
            } else {
              subtitle = e.ProductionYear ? e.ProductionYear.toString() : ''
            }
            return {
              id: e.Id || '',
              name: e.SeriesName || e.Name || '',
              image: getItemImageUrl(e),
              subtitle: subtitle,
              mediaName: item.name,
              type: e.Type
            }
          })
          this.ui.latestMap.set(item.id, list)
          this.ui.latestMediaList.push(item)
        }
      } catch (error) {

      }

    }
  }
}