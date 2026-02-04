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
    //获取收藏数量
    await this.getFavoriteCount()
    //获取媒体库列表
    await this.getMediaList()
    this.ui.isRefreshing = false;
  }

  async getFavoriteCount() {
    try {
      let res: AxiosResponse<ItemCounts> = await getLibraryApi(ApiClient.Instance()).getItemCounts({
        userId: ApiClient.Instance().userId,
        isFavorite: true,
      })
      if (res.data) {
        //设置收藏数量
        this.ui.favoriteCount = 0
          // (res.data.ArtistCount || 0)
          // + (res.data.AlbumCount || 0)
          // + (res.data.ProgramCount || 0)
          + (res.data.MovieCount || 0)
          + (res.data.EpisodeCount || 0)
          + (res.data.SeriesCount || 0)
          // + (res.data.BookCount || 0)
          // + (res.data.MusicVideoCount || 0)
          // + (res.data.TrailerCount || 0)
          // + (res.data.SongCount || 0)
          // + (res.data.BoxSetCount || 0)
          + (res.data.ItemCount || 0);
      }
    } catch (e) {

    }
  }

  private async getMediaList(): Promise<void> {
    return new Promise((resolve, reject) => {
      getUserViewsApi(ApiClient.Instance()).getUserViews({
        userId: ApiClient.Instance().userId
      }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
        if (res.data) {
          if (res.data.TotalRecordCount && res.data.TotalRecordCount > 0) {
            let items: BaseItemDto[] = res.data.Items || [];
            items = items.filter(e=>e.CollectionType != CollectionType.Livetv)
            Promise.all(items.map(async (e: BaseItemDto): Promise<MediaItem> => {
              const childRes: AxiosResponse<BaseItemDtoQueryResult> =
                await getItemsApi(ApiClient.Instance()).getItems({
                  userId: ApiClient.Instance().userId,
                  parentId: e.Id,
                });
              return {
                name: e.Name || '',
                image: ApiClient.Instance().basePath + new ImageUrlsApi()
                  .getItemImageUrlById(e.Id, ImageType.Primary, {
                    tag: e.ImageTags![0]
                  }),
                id: e.Id || '',
                childCount: childRes.data?.TotalRecordCount || 0,
                type: getTypeByCollectionType(e.CollectionType)
              } as MediaItem;
            })).then(mediaItems => {
              this.ui.mediaList = mediaItems;
              resolve();
            }).catch(() => reject());
            resolve()
          }
        }
      }).catch(() => {
        reject()
      })
    })
  }
}