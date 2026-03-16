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
import { PadMenuItem, PadMenuType } from "./HomeScreenUIState";
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
    this.ui.isLoading = false;
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
                let menuItem: PadMenuItem
                if (item.type == BaseItemKind.Movie) {
                  menuItem = new PadMenuItem(PadMenuType.ITEM, item.name, $r('app.media.ic_video'),
                    $r('app.media.ic_video'),
                    item.childCount)
                  menuItem.extra = item
                  this.datasource.pushData(menuItem)
                } else if (item.type == BaseItemKind.Series) {
                  menuItem = new PadMenuItem(PadMenuType.ITEM, item.name, $r('app.media.tv'), $r('app.media.tv'),
                    item.childCount)
                  menuItem.extra = item
                  this.datasource.pushData(menuItem)
                }
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
    let allMediaItem: MediaItem = {
      name: '全部影视',
      id: 'all',
      image: '',
      childCount: 0,
      type: BaseItemKind.Video
    }
    let allItem = new PadMenuItem(PadMenuType.ITEM, `全部`, $r('app.media.all'), $r('app.media.all'),
      totalRes.data.TotalRecordCount || 0)
    allItem.extra = allMediaItem
    this.datasource.pushData(allItem)
    const movieRes: AxiosResponse<BaseItemDtoQueryResult> = await getItemsApi(ApiClient.Instance()).getItems({
      userId: ApiClient.Instance().userId,
      includeItemTypes: ['Movie'],
      recursive: true, // 递归统计所有子目录
      limit: 1
    });
    let movieMediaItem: MediaItem = {
      name: '电影',
      id: 'movies',
      image: '',
      childCount: 0,
      type: BaseItemKind.Movie
    }
    let allMovieItem = new PadMenuItem(PadMenuType.ITEM, `电影`, $r('app.media.movie'), $r('app.media.movie'),
      movieRes.data.TotalRecordCount || 0)
    allMovieItem.extra = movieMediaItem
    this.datasource.pushData(allMovieItem)
    const tvRes: AxiosResponse<BaseItemDtoQueryResult> = await getItemsApi(ApiClient.Instance()).getItems({
      userId: ApiClient.Instance().userId,
      includeItemTypes: ['Series'],
      recursive: true, // 递归统计所有子目录
      limit: 1
    });
    let tvMediaItem: MediaItem = {
      name: '电视节目',
      id: 'tvs',
      image: '',
      childCount: 0,
      type: BaseItemKind.TvProgram
    }
    let allTvItem = new PadMenuItem(PadMenuType.ITEM, `电视剧`, $r('app.media.tv'), $r('app.media.tv'),
      tvRes.data.TotalRecordCount || 0)
    allTvItem.extra = tvMediaItem
    this.datasource.pushData(allTvItem)
  }
}