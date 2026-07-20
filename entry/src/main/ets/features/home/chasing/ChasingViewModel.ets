import { AxiosResponse } from "@ohos/axios";
import { getItemsApi, getTvShowsApi, ImageUrlsApi } from "sdk";
import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  BaseItemKind,
  ImageType,
  ItemFields
} from "sdk/src/main/ets/generated-client/models";
import { BaseViewModel } from "../../../common/base/BaseViewModel";
import { PageState } from "../../../common/base/BaseUIState";
import { getItemBackDropImageUrl } from "../../../common/CommonFunc";
import { AppPreference } from "../../../data/AppPreference";
import { ApiClient } from "../../../network/ApiClient";
import { VideoItem } from "../hometab/HomeUIState";
import { ChasingUIState } from "./ChasingUIState";
import { ChasePlanner } from "./ChasePlanner";

export class ChasingViewModel extends BaseViewModel<ChasingUIState> {
  private imageApi: ImageUrlsApi = new ImageUrlsApi()

  async refresh(): Promise<void> {
    this.ui.pageState = PageState.Loading
    try {
      const chasingSeriesIds = AppPreference.getInstance().getChasingSeriesIds()
      this.ui.followedCount = chasingSeriesIds.length
      if (chasingSeriesIds.length === 0) {
        this.applyPlan([], [])
        this.ui.pageState = PageState.Content
        return
      }
      const list = await Promise.all([
        this.getResumeList(chasingSeriesIds),
        this.getNextUpList(chasingSeriesIds)
      ])
      this.applyPlan(list[0], list[1])
      this.ui.pageState = PageState.Content
    } catch (e) {
      console.error(`[ChasingVM] refresh failed: ${JSON.stringify(e)}`)
      this.ui.pageState = PageState.Error
    } finally {
      this.ui.isRefreshing = false
    }
  }

  private async getResumeList(chasingSeriesIds: string[]): Promise<VideoItem[]> {
    const res: AxiosResponse<BaseItemDtoQueryResult> = await getItemsApi(ApiClient.Instance()).getResumeItems({
      limit: 100,
      fields: [ItemFields.PrimaryImageAspectRatio],
      imageTypeLimit: 1,
      enableImages: true,
      enableImageTypes: [ImageType.Primary, ImageType.Backdrop, ImageType.Thumb],
      enableTotalRecordCount: false,
      mediaTypes: ['Video'],
      userId: ApiClient.Instance().userId
    })
    return (res.data?.Items || [])
      .filter((item: BaseItemDto): boolean => this.isFollowedSeriesItem(item, chasingSeriesIds))
      .map((item: BaseItemDto): VideoItem => this.mapResumeItem(item))
  }

  private async getNextUpList(chasingSeriesIds: string[]): Promise<VideoItem[]> {
    const cutoff = new Date()
    cutoff.setFullYear(cutoff.getFullYear() - 1)
    const tasks: Promise<VideoItem[]>[] = chasingSeriesIds.map((seriesId: string): Promise<VideoItem[]> => {
      return getTvShowsApi(ApiClient.Instance()).getNextUp({
        seriesId,
        limit: 1,
        fields: [ItemFields.PrimaryImageAspectRatio, ItemFields.DateCreated, ItemFields.Path,
          ItemFields.MediaSourceCount],
        userId: ApiClient.Instance().userId,
        imageTypeLimit: 1,
        enableImageTypes: [ImageType.Primary, ImageType.Backdrop, ImageType.Banner, ImageType.Thumb],
        enableTotalRecordCount: false,
        disableFirstEpisode: false,
        nextUpDateCutoff: cutoff.toISOString(),
        enableResumable: false,
        enableRewatching: false
      }).then((res: AxiosResponse<BaseItemDtoQueryResult>): VideoItem[] => {
        return (res.data?.Items || []).map((item: BaseItemDto): VideoItem => this.mapNextUpItem(item))
      }).catch((): VideoItem[] => {
        console.warn(`[ChasingVM] get next up failed, seriesId=${seriesId}`)
        return []
      })
    })
    const lists = await Promise.all(tasks)
    const items: VideoItem[] = []
    lists.forEach((list: VideoItem[]) => {
      list.forEach((item: VideoItem) => {
        items.push(item)
      })
    })
    return items
  }

  private applyPlan(continueItems: VideoItem[], nextUpItems: VideoItem[]): void {
    this.ui.continueCount = continueItems.length
    this.ui.nextUpCount = nextUpItems.length
    const result = new ChasePlanner().plan(continueItems, nextUpItems)
    this.ui.summary = result.summary
    this.ui.totalCount = result.totalCount
    this.ui.sections = result.sections
  }

  private mapResumeItem(item: BaseItemDto): VideoItem {
    let name = ''
    let subtitle = ''
    switch (item.Type) {
      case BaseItemKind.Movie:
        name = item.Name || ''
        subtitle = '电影'
        break
      case BaseItemKind.Episode:
        name = item.SeriesName || item.Name || ''
        subtitle = `${item.SeasonName || ''} · ${item.Name || ''}`
        break
      default:
        name = item.Name || ''
        subtitle = item.Type || ''
        break
    }
    return {
      id: item.Id || '',
      name,
      subtitle,
      image: this.resolveDisplayImage(item),
      seriesId: item.SeriesId || '',
      type: item.Type,
      percent: item.UserData?.PlayedPercentage ? Math.round(item.UserData.PlayedPercentage) : 0
    }
  }

  private mapNextUpItem(item: BaseItemDto): VideoItem {
    return {
      id: item.Id || '',
      name: item.SeriesName || item.Name || '',
      image: this.resolveDisplayImage(item),
      seriesId: item.SeriesId || '',
      subtitle: `S${item.ParentIndexNumber || 0}:E${item.IndexNumber || 0} - ${item.Name || ''}`,
      type: item.Type,
      percent: 0
    }
  }

  private resolveDisplayImage(item: BaseItemDto): string {
    const url = getItemBackDropImageUrl(item) || this.resolveImageUrl(item)
    if (url.startsWith('http://') || url.startsWith('https://')) {
      return url
    }
    return ApiClient.Instance().basePath + url
  }

  private resolveImageUrl(item: BaseItemDto): string {
    const tags = item.ImageTags || {};
    const thumbTag = tags[ImageType.Thumb];
    const primaryTag = tags[ImageType.Primary];
    const backdropTags: string[] = item.BackdropImageTags || [];

    if (item.ParentThumbItemId) {
      return this.imageApi.getItemImageUrlById(item.ParentThumbItemId, ImageType.Thumb, { tag: thumbTag });
    }
    if (item.ParentBackdropItemId) {
      return this.imageApi.getItemImageUrlById(item.ParentBackdropItemId, ImageType.Backdrop, { tag: thumbTag });
    }
    if (thumbTag) {
      return this.imageApi.getItemImageUrlById(item.Id, ImageType.Thumb, { tag: thumbTag });
    }
    if (primaryTag) {
      return this.imageApi.getItemImageUrlById(item.Id, ImageType.Primary, { tag: primaryTag });
    }
    if (backdropTags.length > 0) {
      return this.imageApi.getItemImageUrlById(item.Id, ImageType.Backdrop, { tag: backdropTags[0] });
    }
    if (item.Type === BaseItemKind.Episode) {
      if (item.SeriesId) {
        return this.imageApi.getItemImageUrlById(item.SeriesId, ImageType.Primary);
      }
      if (item.SeasonId) {
        return this.imageApi.getItemImageUrlById(item.SeasonId, ImageType.Primary);
      }
    }
    return '/assets/placeholder-video.png'
  }

  private isFollowedSeriesItem(item: BaseItemDto, chasingSeriesIds: string[]): boolean {
    const seriesId = item.SeriesId || (item.Type === BaseItemKind.Series ? item.Id || '' : '')
    return seriesId.length > 0 && chasingSeriesIds.indexOf(seriesId) >= 0
  }
}
