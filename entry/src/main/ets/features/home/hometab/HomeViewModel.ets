import { AxiosError, AxiosInstance, AxiosResponse } from "@ohos/axios";
import { getItemsApi, getTvShowsApi, getUserLibraryApi, getUserViewsApi, ImageUrlsApi } from "sdk";
import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  BaseItemKind,
  CollectionType,
  ImageType,
  ItemFields,
  RecommendationDto,
} from "sdk/src/main/ets/generated-client/models";
import { BaseViewModel } from "../../../common/base/BaseViewModel";
import {
  getItemBackDropImageUrl,
  getItemImageUrl,
  getTypeByCollectionType
} from "../../../common/CommonFunc";
import { ApiClient } from "../../../network/ApiClient";
import {
  HOME_RECENT_MEDIA_ID,
  HomeUIState,
  MediaItem,
  VideoItem
} from "./HomeUIState";

const HOME_FEED_PAGE_SIZE: number = 30
const HOME_RECENT_LIBRARY_LIMIT: number = 16

interface HomeFeedSourceItem {
  item: BaseItemDto
  mediaName: string
}

interface HomeFeedPage {
  items: HomeFeedSourceItem[]
  nextStartIndex: number
  hasMore: boolean
}

interface HomeRecentLibraryPage {
  media: MediaItem
  items: BaseItemDto[]
}

export class HomeViewModel extends BaseViewModel<HomeUIState> {
  imageApi: ImageUrlsApi = new ImageUrlsApi()
  private initialLoadCompleted: boolean = false
  private initVersion: number = 0
  private latestStartIndexes: Map<string, number> = new Map<string, number>()
  private latestHasMoreById: Map<string, boolean> = new Map<string, boolean>()
  private latestLoadedIds: Set<string> = new Set<string>()
  private latestLoadingIds: Set<string> = new Set<string>()
  private latestRequestVersion: number = 0

  init(): void {
    this.initVersion += 1
    const requestVersion = this.initVersion
    if (!this.initialLoadCompleted) {
      this.ui.initialLoading = true
    }
    // 使用 Promise 链，init 立即返回不阻塞
    this.getMediaList().then(() => {
      return Promise.all([
        this.getRecommendationList(),
        this.getResumeList(),
        this.getNextUpList(),
        this.getLatestList()
      ])
    }).then(() => {
      this.finishInit(requestVersion)
    }).catch(() => {
      this.finishInit(requestVersion)
    })
  }

  private finishInit(requestVersion: number): void {
    if (requestVersion !== this.initVersion) {
      return
    }
    this.initialLoadCompleted = true
    this.ui.initialLoading = false
    this.ui.isRefreshing = false
  }

  private getRecommendationList(): Promise<void> {
    const api = ApiClient.Instance()
    const axiosInstance = api.axiosInstance as AxiosInstance
    const requestUrl = `${api.basePath}/Movies/Recommendations?userId=${encodeURIComponent(api.userId)}` +
      '&categoryLimit=4&itemLimit=4&fields=Overview%2CPrimaryImageAspectRatio'
    return axiosInstance.get<RecommendationDto[]>(requestUrl, {
      headers: { Authorization: api.authorizationHeader }
    }).then((res: AxiosResponse<RecommendationDto[]>) => {
      const recommendations: VideoItem[] = []
      const seenIds: Set<string> = new Set<string>()
      const groups: RecommendationDto[] = res.data || []
      for (let groupIndex = 0; groupIndex < groups.length && recommendations.length < 6; groupIndex++) {
        const items: BaseItemDto[] = groups[groupIndex].Items || []
        for (let itemIndex = 0; itemIndex < items.length && recommendations.length < 6; itemIndex++) {
          const mapped = mapRecommendationItem(items[itemIndex], this.imageApi)
          if (mapped && !seenIds.has(mapped.id)) {
            seenIds.add(mapped.id)
            recommendations.push(mapped)
          }
        }
      }
      this.ui.recommendationList = recommendations
      const heroVisible = recommendations.length > 0
      if (this.appUIState.rootNavigationHomeHeroVisible !== heroVisible) {
        this.appUIState.rootNavigationHomeHeroVisible = heroVisible
      }
    }).catch(() => {
    })
  }

  private getMediaList(): Promise<void> {
    this.ui.hasLiveTv = false
    return new Promise((resolve, reject) => {
      getUserViewsApi(ApiClient.Instance()).getUserViews({
        userId: ApiClient.Instance().userId
      }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
        if (res.data) {
          if (res.data.TotalRecordCount && res.data.TotalRecordCount > 0) {
            this.ui.mediaList = res.data.Items?.map<MediaItem>((e: BaseItemDto) => {
              if (e.CollectionType == CollectionType.Livetv) {
                this.ui.hasLiveTv = true
              }
              return {
                name: e.Name || '',
                image: ApiClient.Instance().basePath + new ImageUrlsApi().getItemImageUrlById(e.Id, ImageType.Primary, {
                  tag: e.ImageTags![0]
                }),
                id: e.Id || '',
                childCount: e.ChildCount || 0,
                type: getTypeByCollectionType(e.CollectionType),
                collectionType: e.CollectionType
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
      }).catch((e: Error) => {
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
      }).catch((e: Error) => {
        resolve()
      })
    })
  }

  private getLatestList(): Promise<void> {
    this.latestRequestVersion += 1
    const requestVersion = this.latestRequestVersion
    this.latestStartIndexes = new Map<string, number>()
    this.latestHasMoreById = new Map<string, boolean>()
    this.latestLoadedIds = new Set<string>()
    this.latestLoadingIds = new Set<string>()
    this.ui.latestMap = new Map<string, VideoItem[]>()
    this.ui.latestMediaList = this.ui.mediaList.filter((item: MediaItem): boolean => {
      return isSupportedHomeVideoLibrary(item)
    })
    this.ui.selectedLatestMediaId = resolveLatestMediaSelection(
      this.ui.selectedLatestMediaId, this.ui.latestMediaList)
    this.ui.latestMediaLoading = true
    this.ui.latestMediaLoadingMore = false
    this.ui.latestMediaHasMore = true
    this.ui.latestMediaLoadFailed = false
    return this.loadLatestMediaPage(this.ui.selectedLatestMediaId, true, requestVersion)
  }

  selectLatestMedia(id: string): void {
    if (id !== HOME_RECENT_MEDIA_ID &&
      !this.ui.latestMediaList.some((item: MediaItem): boolean => item.id === id)) {
      return
    }
    this.ui.selectedLatestMediaId = id
    this.ui.latestMediaLoadFailed = false
    this.ui.latestMediaHasMore = this.latestHasMoreById.get(id) ?? true
    if (this.latestLoadedIds.has(id)) {
      this.ui.latestMediaLoading = false
      this.ui.latestMediaLoadingMore = false
      return
    }
    this.ui.latestMediaLoading = true
    this.ui.latestMediaLoadingMore = false
    this.loadLatestMediaPage(id, true, this.latestRequestVersion)
  }

  loadMoreLatestMedia(): void {
    const id = this.ui.selectedLatestMediaId
    if (this.ui.latestMediaLoading || this.ui.latestMediaLoadingMore ||
      !(this.latestHasMoreById.get(id) ?? true)) {
      return
    }
    this.loadLatestMediaPage(id, false, this.latestRequestVersion)
  }

  retryLatestMedia(): void {
    const id = this.ui.selectedLatestMediaId
    const currentItems = this.ui.latestMap.get(id) || []
    this.ui.latestMediaLoadFailed = false
    this.loadLatestMediaPage(id, currentItems.length === 0, this.latestRequestVersion)
  }

  private loadLatestMediaPage(id: string, replace: boolean, requestVersion: number): Promise<void> {
    if (this.latestLoadingIds.has(id)) {
      return Promise.resolve()
    }
    this.latestLoadingIds.add(id)
    const startIndex = replace ? 0 : (this.latestStartIndexes.get(id) || 0)
    if (this.ui.selectedLatestMediaId === id) {
      this.ui.latestMediaLoading = replace
      this.ui.latestMediaLoadingMore = !replace
      this.ui.latestMediaLoadFailed = false
    }
    const media = id === HOME_RECENT_MEDIA_ID ? undefined :
      this.ui.latestMediaList.find((item: MediaItem): boolean => item.id === id)
    let request: Promise<HomeFeedPage>
    if (id === HOME_RECENT_MEDIA_ID) {
      const latestRequests: Promise<HomeRecentLibraryPage>[] = this.ui.mediaList
        .filter((library: MediaItem): boolean => isLegacyHomeLatestLibrary(library))
        .map(
          (library: MediaItem): Promise<HomeRecentLibraryPage> => {
            return getUserLibraryApi(ApiClient.Instance()).getLatestMedia({
              userId: ApiClient.Instance().userId,
              parentId: library.id,
              limit: HOME_RECENT_LIBRARY_LIMIT,
              fields: [ItemFields.PrimaryImageAspectRatio, ItemFields.Path],
              imageTypeLimit: 1,
              enableImageTypes: [ImageType.Primary, ImageType.Backdrop, ImageType.Thumb]
            }).then((res: AxiosResponse<BaseItemDto[]>): HomeRecentLibraryPage => {
              return {
                media: library,
                items: res.data || []
              }
            }).catch((): HomeRecentLibraryPage => {
              return {
                media: library,
                items: []
              }
            })
          })
      request = Promise.all(latestRequests).then((pages: HomeRecentLibraryPage[]): HomeFeedPage => {
        const items = interleaveRecentLibraryItems(pages, HOME_FEED_PAGE_SIZE)
        return {
          items: items,
          nextStartIndex: items.length,
          hasMore: false
        }
      })
    } else {
      request = getItemsApi(ApiClient.Instance()).getItems({
        userId: ApiClient.Instance().userId,
        parentId: media?.id,
        recursive: true,
        startIndex: startIndex,
        limit: HOME_FEED_PAGE_SIZE,
        includeItemTypes: homeFeedItemTypes(media),
        sortBy: ['DateCreated'],
        sortOrder: ['Descending'],
        fields: [ItemFields.PrimaryImageAspectRatio, ItemFields.DateCreated, ItemFields.Path,
          ItemFields.MediaSourceCount],
        imageTypeLimit: 1,
        enableImages: true,
        enableImageTypes: [ImageType.Primary, ImageType.Backdrop, ImageType.Thumb]
      }).then((res: AxiosResponse<BaseItemDtoQueryResult>): HomeFeedPage => {
        const items: BaseItemDto[] = res.data?.Items || []
        const nextStartIndex = startIndex + items.length
        const totalCount = res.data?.TotalRecordCount || 0
        return {
          items: items.map((item: BaseItemDto): HomeFeedSourceItem => {
            return {
              item: item,
              mediaName: media?.name || ''
            }
          }),
          nextStartIndex: nextStartIndex,
          hasMore: items.length === HOME_FEED_PAGE_SIZE &&
            (totalCount === 0 || nextStartIndex < totalCount)
        }
      })
    }
    return request.then((page: HomeFeedPage) => {
      if (requestVersion !== this.latestRequestVersion) {
        return
      }
      const mappedItems: VideoItem[] = page.items.map((source: HomeFeedSourceItem): VideoItem => {
        return mapHomeFeedItem(source.item, source.mediaName || '最近添加', this.imageApi)
      })
      const previousItems: VideoItem[] = replace ? [] : (this.ui.latestMap.get(id) || [])
      const mergedItems: VideoItem[] = appendUniqueVideoItems(previousItems, mappedItems)
      const nextMap = new Map<string, VideoItem[]>(this.ui.latestMap)
      nextMap.set(id, mergedItems)
      this.ui.latestMap = nextMap
      this.latestStartIndexes.set(id, page.nextStartIndex)
      this.latestHasMoreById.set(id, page.hasMore)
      this.latestLoadedIds.add(id)
      if (this.ui.selectedLatestMediaId === id) {
        this.ui.latestMediaLoading = false
        this.ui.latestMediaLoadingMore = false
        this.ui.latestMediaHasMore = page.hasMore
        this.ui.latestMediaLoadFailed = false
      }
    }).catch(() => {
      if (requestVersion === this.latestRequestVersion && this.ui.selectedLatestMediaId === id) {
        this.ui.latestMediaLoading = false
        this.ui.latestMediaLoadingMore = false
        this.ui.latestMediaLoadFailed = true
      }
    }).finally(() => {
      this.latestLoadingIds.delete(id)
    })
  }
}

export function resolveLatestMediaSelection(currentId: string, mediaList: MediaItem[]): string {
  if (currentId === HOME_RECENT_MEDIA_ID) {
    return HOME_RECENT_MEDIA_ID
  }
  if (mediaList.some((item: MediaItem) => item.id === currentId)) {
    return currentId
  }
  return HOME_RECENT_MEDIA_ID
}

export function appendUniqueVideoItems(current: VideoItem[], incoming: VideoItem[]): VideoItem[] {
  const result: VideoItem[] = current.slice()
  const ids: Set<string> = new Set<string>()
  result.forEach((item: VideoItem): void => {
    ids.add(item.id)
  })
  incoming.forEach((item: VideoItem): void => {
    if (!ids.has(item.id)) {
      ids.add(item.id)
      result.push(item)
    }
  })
  return result
}

function interleaveRecentLibraryItems(pages: HomeRecentLibraryPage[], limit: number): HomeFeedSourceItem[] {
  const result: HomeFeedSourceItem[] = []
  let itemIndex = 0
  let appended = true
  while (result.length < limit && appended) {
    appended = false
    for (let pageIndex = 0; pageIndex < pages.length && result.length < limit; pageIndex++) {
      const page = pages[pageIndex]
      const item = page.items[itemIndex]
      if (item) {
        result.push({
          item: item,
          mediaName: page.media.name
        })
        appended = true
      }
    }
    itemIndex += 1
  }
  return result
}

function isLegacyHomeLatestLibrary(item: MediaItem): boolean {
  return item.type !== BaseItemKind.LiveTvChannel && item.type !== BaseItemKind.Audio
}

export function isSupportedHomeVideoLibrary(item: MediaItem): boolean {
  return item.collectionType === undefined ||
    item.collectionType === CollectionType.Unknown ||
    item.collectionType === CollectionType.Movies ||
    item.collectionType === CollectionType.Tvshows ||
    item.collectionType === CollectionType.Homevideos ||
    item.collectionType === CollectionType.Folders
}

export function homeFeedItemTypes(media?: MediaItem): BaseItemKind[] {
  if (!media) {
    return [BaseItemKind.Movie, BaseItemKind.Series, BaseItemKind.Episode, BaseItemKind.Video]
  }
  if (media.type === BaseItemKind.Movie) {
    return [BaseItemKind.Movie]
  }
  if (media.type === BaseItemKind.Series) {
    return [BaseItemKind.Series]
  }
  return [BaseItemKind.Movie, BaseItemKind.Series, BaseItemKind.Video]
}

function mapHomeFeedItem(item: BaseItemDto, mediaName: string, imageApi: ImageUrlsApi): VideoItem {
  let subtitle = item.ProductionYear ? item.ProductionYear.toString() : ''
  if (item.Type === BaseItemKind.Episode) {
    subtitle = `S${item.ParentIndexNumber || 0}:E${item.IndexNumber || 0} - ${item.Name || ''}`
  }
  const seriesId = item.SeriesId || ''
  if (item.Type === BaseItemKind.Episode && seriesId.length > 0) {
    const tag = item.SeriesPrimaryImageTag
    return {
      id: seriesId,
      name: item.SeriesName || item.Name || '',
      image: ApiClient.Instance().basePath + imageApi.getItemImageUrlById(
        seriesId,
        ImageType.Primary,
        tag ? { tag: tag } : {}
      ),
      seriesId: seriesId,
      subtitle: subtitle,
      mediaName: '电视节目',
      type: BaseItemKind.Series
    }
  }
  return {
    id: item.Id || '',
    name: item.Name || '',
    image: getItemImageUrl(item),
    seriesId: item.SeriesId || undefined,
    subtitle: subtitle,
    mediaName: item.Type === BaseItemKind.Movie ? '电影' : mediaName,
    type: item.Type
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
    seriesId: e.SeriesId || undefined,
    type: e.Type,
    percent: e.UserData?.PlayedPercentage ? Math.round(e.UserData.PlayedPercentage) : 0
  };
}

function mapRecommendationItem(e: BaseItemDto, imageApi: ImageUrlsApi): VideoItem | undefined {
  if (!e.Id || e.Type !== BaseItemKind.Movie) {
    return undefined
  }
  const metadata: string[] = ['电影']
  if (e.ProductionYear) {
    metadata.push(e.ProductionYear.toString())
  }
  if (e.CommunityRating) {
    metadata.push(`${e.CommunityRating.toFixed(1)} 分`)
  }
  let image = ''
  if (e.BackdropImageTags && e.BackdropImageTags.length > 0) {
    image = ApiClient.Instance().basePath + imageApi.getItemImageUrlById(
      e.Id, ImageType.Backdrop, { tag: e.BackdropImageTags[0] })
  } else {
    image = getItemBackDropImageUrl(e) || resolveImageUrl(e, imageApi)
  }
  return {
    id: e.Id,
    name: e.Name || '',
    image: image,
    mediaName: '电影',
    type: e.Type,
    subtitle: metadata.join(' · '),
    description: e.Overview || '',
    score: e.CommunityRating || undefined
  }
}
