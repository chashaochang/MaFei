import { AxiosResponse } from "@ohos/axios";
import { getItemsApi, getLibraryApi, getUserViewsApi, ImageUrlsApi } from "sdk";
import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  BaseItemKind,
  CollectionType,
  ImageType,
  ItemCounts
} from "sdk/src/main/ets/generated-client/models";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { getTypeByCollectionType } from "../../common/CommonFunc";
import { ApiClient } from "../../network/ApiClient";
import { PadMenuItem, PadMenuType } from "./HomeScreen";
import { HomeScreenUIState, PadMenuDataSource } from "./HomeScreenUIState";
import { MediaItem } from "./hometab/HomeUIState";

@ObservedV2
export class HomeScreenViewModel extends BaseViewModel<HomeScreenUIState> {
  datasource: PadMenuDataSource = new PadMenuDataSource()

  async init() {
    this.datasource.pushData(new PadMenuItem(PadMenuType.ITEM, "首页", $r('app.media.ic_home_sel'),
      $r('app.media.ic_home_unsel')))
    //获取收藏数量
    await this.getFavoriteCount()
    //获取媒体库列表
    await this.getMediaList()
    //获取视频数量
    await this.getCounts()
    //this.ui.isRefreshing = false;
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
        this.datasource.pushData(new PadMenuItem(PadMenuType.ITEM, `收藏`, $r('app.media.ic_unlike'),
          $r('app.media.ic_unlike'), this.ui.favoriteCount))
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
            items = items.filter(e => e.CollectionType != CollectionType.Livetv)
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
              if (mediaItems.length > 0) {
                this.datasource.pushData(new PadMenuItem(PadMenuType.TITLE, "媒体库"))
              }
              mediaItems.forEach((item) => {
                let icon = item.type == BaseItemKind.Video ? $r('app.media.ic_video') : $r('app.media.tv')
                this.datasource.pushData(new PadMenuItem(PadMenuType.ITEM, item.name, icon, icon,
                  item.childCount))
              })
              resolve();
            }).then(() => resolve())
              .catch(() => reject());
          }
        }
      }).catch(() => {
        reject()
      })
    })
  }

  async getCounts() {
    const totalRes: AxiosResponse<BaseItemDtoQueryResult> = await getItemsApi(ApiClient.Instance()).getItems({
      userId: ApiClient.Instance().userId,
      includeItemTypes: ['Movie', 'Series'],
      recursive: true, // 递归统计所有子目录
      limit: 1
    });
    this.datasource.pushData(new PadMenuItem(PadMenuType.TITLE, "分类"))
    this.datasource.pushData(new PadMenuItem(PadMenuType.ITEM, `全部`, $r('app.media.all'), $r('app.media.all'),
      totalRes.data.TotalRecordCount || 0))
    const movieRes: AxiosResponse<BaseItemDtoQueryResult> = await getItemsApi(ApiClient.Instance()).getItems({
      userId: ApiClient.Instance().userId,
      includeItemTypes: ['Movie'],
      recursive: true, // 递归统计所有子目录
      limit: 1
    });
    this.datasource.pushData(new PadMenuItem(PadMenuType.ITEM, `电影`, $r('app.media.movie'), $r('app.media.movie'),
      movieRes.data.TotalRecordCount || 0))
    const tvRes: AxiosResponse<BaseItemDtoQueryResult> = await getItemsApi(ApiClient.Instance()).getItems({
      userId: ApiClient.Instance().userId,
      includeItemTypes: ['Series'],
      recursive: true, // 递归统计所有子目录
      limit: 1
    });
    this.datasource.pushData(new PadMenuItem(PadMenuType.ITEM, `电视剧`, $r('app.media.tv'), $r('app.media.tv'),
      tvRes.data.TotalRecordCount || 0))
  }
}