import { AxiosError, AxiosResponse } from "@ohos/axios";
import { getItemsApi, getTvShowsApi, getUserLibraryApi, getUserViewsApi, ImageUrlsApi } from "sdk";
import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  BaseItemKind,
  ImageType,
  ItemFields,
} from "sdk/src/main/ets/generated-client/models";
import { BaseViewModel } from "../../../common/base/BaseViewModel";
import { getItemBackDropImageUrl, getTypeByCollectionType, resolveLatestImageUrl } from "../../../common/CommonFunc";
import { ApiClient } from "../../../network/ApiClient";
import { HomeUIState, MediaItem, VideoItem } from "./HomeUIState";

export class HomeViewModel extends BaseViewModel<HomeUIState> {
  imageApi: ImageUrlsApi = new ImageUrlsApi()

  async init() {
    try {
      //获取媒体库列表
      await this.getMediaList()
      //获取继续观看列表
      await this.getResumeList()
      //获取视频数量
      await this.getCounts()
      //获取接下来列表
      await this.getNextUpList()
      //获取latest
      await this.getLatestList()
      this.ui.isRefreshing = false;
    } catch (e) {
      this.ui.isRefreshing = false;
    }
  }

  async getCounts() {
    const totalRes: AxiosResponse<BaseItemDtoQueryResult> = await getItemsApi(ApiClient.Instance()).getItems({
      userId: ApiClient.Instance().userId,
      includeItemTypes: ['Movie', 'Series'],
      recursive: true, // 递归统计所有子目录
      limit: 1
    });
    this.ui.totalCount = totalRes.data.TotalRecordCount + '' || '--'
    const movieRes: AxiosResponse<BaseItemDtoQueryResult> = await getItemsApi(ApiClient.Instance()).getItems({
      userId: ApiClient.Instance().userId,
      includeItemTypes: ['Movie'],
      recursive: true, // 递归统计所有子目录
      limit: 1
    });
    this.ui.movieCount = movieRes.data.TotalRecordCount + '' || '--'
    const tvRes: AxiosResponse<BaseItemDtoQueryResult> = await getItemsApi(ApiClient.Instance()).getItems({
      userId: ApiClient.Instance().userId,
      includeItemTypes: ['Series'],
      recursive: true, // 递归统计所有子目录
      limit: 1
    });
    this.ui.tvCount = tvRes.data.TotalRecordCount + '' || '--'
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
          } else {
            resolve()
          }
        } else {
          resolve()
        }
      }).catch((e: AxiosError) => {
        resolve()
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
        enableImages: true,
        enableImageTypes: [ImageType.Primary, ImageType.Backdrop, ImageType.Thumb],
        enableTotalRecordCount: false,
        mediaTypes: ['Video'],
        userId: ApiClient.Instance().userId
      }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
        if (res && res.data) {
          if (res.data.Items && res.data.Items.length > 0) {
            let list = res.data.Items.map<VideoItem>((e: BaseItemDto) => {
              return mapResumeItem(e, new ImageUrlsApi());
            }) || []
            this.ui.resumeList = list
            resolve()
          } else {
            resolve()
          }
        } else {
          resolve()
        }
      }).catch((e:Error) => {
        resolve()
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
        if (res && res.data) {
          if (res.data.Items && res.data.Items.length > 0) {
            let list = res.data.Items.map<VideoItem>((e: BaseItemDto) => {
              return {
                id: e.Id || '',
                name: e.SeriesName || '',
                image: getItemBackDropImageUrl(e) || resolveImageUrl(e, this.imageApi),
                subtitle: `S${e.ParentIndexNumber}:E${e.IndexNumber} - ${e.Name}`,
              }
            }) || []
            this.ui.nextUpList = list
            resolve()
          } else {
            resolve()
          }
        } else {
          resolve()
        }
      }).catch((e:Error) => {
        resolve()
      })
    })
  }

  private async getLatestList(): Promise<void> {
    return new Promise(async (resolve, reject) => {
      this.ui.latestMediaList = []
      this.ui.latestMap.clear()
      let promises: Promise<void>[] = []
      for (const item of this.ui.mediaList) {
        let promise = new Promise<void>(async (resolve, reject) => {
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
                  image: ApiClient.Instance().basePath + resolveLatestImageUrl(e, this.imageApi),
                  subtitle: subtitle,
                  mediaName: item.name,
                  type: e.Type
                }
              })
              this.ui.latestMap.set(item.id, list)
              this.ui.latestMediaList.push(item)
              resolve()
            } else {
              resolve()
            }
          } catch (error) {
            resolve()
          }
        })
        promises.push(promise)
      }
      Promise.all(promises).then(() => {
        resolve()
      }).catch(() => {
        reject()
      })
    })
  }
}

function resolveImageUrl(e: BaseItemDto, imageApi: ImageUrlsApi, baseFallback?: string): string {
  // 预取各类型标签
  const tags = e.ImageTags || {};
  const thumbTag = tags[ImageType.Thumb];
  const primaryTag = tags[ImageType.Primary];
  const backdropTags: string[] = e.BackdropImageTags || [];

  if (e.ParentThumbItemId) {
    return imageApi.getItemImageUrlById(e.ParentThumbItemId, ImageType.Thumb, { tag: thumbTag });
  }

  if (e.ParentBackdropItemId) {
    return imageApi.getItemImageUrlById(e.ParentBackdropItemId, ImageType.Backdrop, { tag: thumbTag });
  }

  // 如果自身有 Thumb
  if (thumbTag) {
    return imageApi.getItemImageUrlById(e.Id, ImageType.Thumb, { tag: thumbTag });
  }

  // 自身 Primary
  if (primaryTag) {
    return imageApi.getItemImageUrlById(e.Id, ImageType.Primary, { tag: primaryTag });
  }

  // 自身 Backdrop 第一张
  if (backdropTags.length > 0) {
    return imageApi.getItemImageUrlById(e.Id, ImageType.Backdrop, { tag: backdropTags[0] });
  }

  // Episode 特殊：父级缩略图
  if (e.Type === BaseItemKind.Episode) {
    // ParentThumbItemId
    if (e.ParentThumbItemId) {
      return imageApi.getItemImageUrlById(e.ParentThumbItemId, ImageType.Thumb);
    }
    // SeriesId 回退
    if (e.SeriesId) {
      return imageApi.getItemImageUrlById(e.SeriesId, ImageType.Primary);
    }
    // SeasonId 回退
    if (e.SeasonId) {
      return imageApi.getItemImageUrlById(e.SeasonId, ImageType.Primary);
    }
  }

  // 通用父级 Backdrop
  if (e.ParentBackdropItemId && e.ParentBackdropImageTags?.length) {
    return imageApi.getItemImageUrlById(
      e.ParentBackdropItemId,
      ImageType.Backdrop,
      { tag: e.ParentBackdropImageTags[0] }
    );
  }

  // 失败则占位图
  return baseFallback || '/assets/placeholder-video.png';
}

function mapResumeItem(e: BaseItemDto, imageApi: ImageUrlsApi): VideoItem {
  let name = '';
  let subtitle = '';
  switch (e.Type) {
    case BaseItemKind.Movie:
      name = e.Name || '';
      subtitle = '电影';
      break;
    case BaseItemKind.Episode:
      name = e.SeriesName || e.Name || '';
      subtitle = `${e.SeasonName || ''} · ${e.Name || ''}`;
      break;
    default:
      name = e.Name || '';
      subtitle = e.Type || '';
      break;
  }

  return {
    id: e.Id || '',
    name,
    subtitle,
    image: getItemBackDropImageUrl(e) || resolveImageUrl(e, new ImageUrlsApi()),
    type: e.Type,
    percent: e.UserData?.PlayedPercentage ? Math.round(e.UserData.PlayedPercentage) : 0
  };
}