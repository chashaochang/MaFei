import { AxiosResponse } from "@ohos/axios";
import { getItemsApi, getLibraryApi, getUserViewsApi, ImageUrlsApi } from "sdk";
import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  BaseItemKind,
  CollectionType,
  ImageType,
  ItemCounts,
} from "sdk/src/main/ets/generated-client/models";
import { PageState } from "../../../common/base/BaseUIState";
import { getTypeByCollectionType } from "../../../common/CommonFunc";
import { ApiClient } from "../../../network/ApiClient";
import { MediaItem } from "../hometab/HomeUIState";
import { MediaUIState } from "./MediaUIState";

export class MediaViewModel {
  ui: MediaUIState

  constructor(ui: MediaUIState) {
    this.ui = ui
  }

  async init(): Promise<void> {
    this.ui.pageState = PageState.Loading
    try {
      //获取收藏数量
      // await this.getFavoriteCount()
      //获取媒体库列表
      this.ui.mediaList = await this.getMediaList()
      this.ui.pageState = PageState.Content
    } catch (_error) {
      console.error('[MediaVM] init failed')
      this.ui.mediaList = []
      this.ui.pageState = PageState.Error
    } finally {
      this.ui.isRefreshing = false
    }
  }

  // async getFavoriteCount() {
  //   try {
  //     let res: AxiosResponse<ItemCounts> = await getLibraryApi(ApiClient.Instance()).getItemCounts({
  //       userId: ApiClient.Instance().userId,
  //       isFavorite: true,
  //     })
  //     if (res.data) {
  //       //设置收藏数量
  //       this.ui.favoriteCount = 0
  //         // (res.data.ArtistCount || 0)
  //         // + (res.data.AlbumCount || 0)
  //         // + (res.data.ProgramCount || 0)
  //         + (res.data.MovieCount || 0)
  //         + (res.data.EpisodeCount || 0)
  //         + (res.data.SeriesCount || 0)
  //         // + (res.data.BookCount || 0)
  //         // + (res.data.MusicVideoCount || 0)
  //         // + (res.data.TrailerCount || 0)
  //         // + (res.data.SongCount || 0)
  //         // + (res.data.BoxSetCount || 0)
  //         + (res.data.ItemCount || 0);
  //     }
  //   } catch (e) {
  //
  //   }
  // }

  private async getMediaList(): Promise<MediaItem[]> {
    const response: AxiosResponse<BaseItemDtoQueryResult> =
      await getUserViewsApi(ApiClient.Instance()).getUserViews({
        userId: ApiClient.Instance().userId
      })
    const views: BaseItemDto[] = (response.data?.Items || [])
      .filter((item: BaseItemDto): boolean => item.CollectionType !== CollectionType.Livetv)

    return Promise.all(views.map(async (item: BaseItemDto): Promise<MediaItem> => {
      const childResponse: AxiosResponse<BaseItemDtoQueryResult> =
        await getItemsApi(ApiClient.Instance()).getItems({
          userId: ApiClient.Instance().userId,
          parentId: item.Id,
        })
      return {
        name: item.Name || '',
        image: ApiClient.Instance().basePath + new ImageUrlsApi()
          .getItemImageUrlById(item.Id, ImageType.Primary, {
            tag: item.ImageTags![0]
          }),
        id: item.Id || '',
        childCount: childResponse.data?.TotalRecordCount || 0,
        type: getTypeByCollectionType(item.CollectionType),
        collectionType: item.CollectionType
      } as MediaItem
    }))
  }
}
