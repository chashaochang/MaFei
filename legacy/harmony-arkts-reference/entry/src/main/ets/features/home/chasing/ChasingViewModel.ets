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
import { MediaProviderRegistry } from "../../../media/MediaProviderRegistry";
import { MediaProviderSession } from "../../../media/MediaProviderSession";
import { MediaRef, MediaRefKind } from "../../../media/MediaRef";
import { ApiClient } from "../../../network/ApiClient";
import { VideoItem } from "../hometab/HomeUIState";
import { ChasingUIState } from "./ChasingUIState";
import { ChaseFollowedSeriesItem, ChasePlanner } from "./ChasePlanner";

export class ChasingViewModel extends BaseViewModel<ChasingUIState> {
  private imageApi: ImageUrlsApi = new ImageUrlsApi()
  private refreshVersion: number = 0

  async refresh(): Promise<void> {
    const requestVersion = ++this.refreshVersion
    const session = this.activeChasingSession()
    if (!session) {
      this.applyPlan([], [], [])
      this.ui.followedCount = 0
      this.ui.pageState = PageState.Content
      this.ui.isRefreshing = false
      return
    }
    this.ui.pageState = PageState.Loading
    try {
      const chasingSeriesIds = AppPreference.getInstance().getChasingSeriesIds()
      this.ui.followedCount = chasingSeriesIds.length
      if (chasingSeriesIds.length === 0) {
        this.applyPlan([], [], [])
        this.ui.pageState = PageState.Content
        return
      }
      const lists = await Promise.all([
        this.getResumeList(chasingSeriesIds, session),
        this.getNextUpList(chasingSeriesIds, session),
        this.getFollowedSeriesList(chasingSeriesIds, session)
      ])
      if (!this.isCurrentRequest(session, requestVersion)) {
        return
      }
      this.applyPlan(lists[0], lists[1], lists[2])
      this.ui.pageState = PageState.Content
    } catch (e) {
      if (this.isCurrentRequest(session, requestVersion)) {
        console.error(`[ChasingVM] refresh failed: ${JSON.stringify(e)}`)
        this.ui.pageState = PageState.Error
      }
    } finally {
      if (requestVersion === this.refreshVersion) {
        this.ui.isRefreshing = false
      }
    }
  }

  private activeChasingSession(): MediaProviderSession | undefined {
    const session = MediaProviderRegistry.instance().getActiveSession()
    return session?.capabilities.chasing ? session : undefined
  }

  private isCurrentRequest(session: MediaProviderSession, requestVersion: number): boolean {
    const activeSession = this.activeChasingSession()
    return requestVersion === this.refreshVersion && activeSession?.accountScope === session.accountScope
  }

  private async getResumeList(chasingSeriesIds: string[],
    session: MediaProviderSession): Promise<VideoItem[]> {
    const res: AxiosResponse<BaseItemDtoQueryResult> = await getItemsApi(ApiClient.Instance()).getResumeItems({
      limit: 100,
      fields: [ItemFields.PrimaryImageAspectRatio],
      imageTypeLimit: 1,
      enableImages: true,
      enableImageTypes: [ImageType.Primary, ImageType.Backdrop, ImageType.Thumb],
      enableTotalRecordCount: false,
      mediaTypes: ['Video'],
      userId: session.userId
    })
    return (res.data?.Items || [])
      .filter((item: BaseItemDto): boolean => this.isFollowedSeriesItem(item, chasingSeriesIds))
      .map((item: BaseItemDto): VideoItem => this.mapResumeItem(item, session))
  }

  private async getNextUpList(chasingSeriesIds: string[],
    session: MediaProviderSession): Promise<VideoItem[]> {
    const cutoff = new Date()
    cutoff.setFullYear(cutoff.getFullYear() - 1)
    const tasks: Promise<VideoItem[]>[] = chasingSeriesIds.map((seriesId: string): Promise<VideoItem[]> => {
      return getTvShowsApi(ApiClient.Instance()).getNextUp({
        seriesId,
        limit: 1,
        fields: [ItemFields.PrimaryImageAspectRatio, ItemFields.DateCreated, ItemFields.Path,
          ItemFields.MediaSourceCount],
        userId: session.userId,
        imageTypeLimit: 1,
        enableImageTypes: [ImageType.Primary, ImageType.Backdrop, ImageType.Banner, ImageType.Thumb],
        enableTotalRecordCount: false,
        disableFirstEpisode: false,
        nextUpDateCutoff: cutoff.toISOString(),
        enableResumable: false,
        enableRewatching: false
      }).then((res: AxiosResponse<BaseItemDtoQueryResult>): VideoItem[] => {
        return (res.data?.Items || []).map((item: BaseItemDto): VideoItem => this.mapNextUpItem(item, session))
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

  private async getFollowedSeriesList(chasingSeriesIds: string[],
    session: MediaProviderSession): Promise<ChaseFollowedSeriesItem[]> {
    const res: AxiosResponse<BaseItemDtoQueryResult> = await getItemsApi(ApiClient.Instance()).getItems({
      userId: session.userId,
      ids: chasingSeriesIds,
      includeItemTypes: [BaseItemKind.Series],
      fields: [ItemFields.PrimaryImageAspectRatio],
      enableUserData: true,
      enableImages: true,
      imageTypeLimit: 1,
      enableImageTypes: [ImageType.Primary, ImageType.Backdrop, ImageType.Thumb],
      enableTotalRecordCount: false
    })
    return (res.data?.Items || [])
      .map((item: BaseItemDto): ChaseFollowedSeriesItem => this.mapFollowedSeriesItem(item, session))
  }

  private applyPlan(
    continueItems: VideoItem[],
    nextUpItems: VideoItem[],
    followedSeriesItems: ChaseFollowedSeriesItem[]
  ): void {
    const result = new ChasePlanner().plan(continueItems, nextUpItems, followedSeriesItems)
    this.ui.continueCount = result.sections[0].items.length
    this.ui.nextUpCount = result.sections[1].items.length
    this.ui.completedCount = result.sections[2].items.length
    this.ui.summary = result.summary
    this.ui.totalCount = result.totalCount
    this.ui.sections = result.sections
  }

  private mapResumeItem(item: BaseItemDto, session: MediaProviderSession): VideoItem {
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
      mediaRef: this.mediaRef(item.Id, item.Type, session),
      name,
      subtitle,
      image: this.resolveDisplayImage(item),
      seriesId: item.SeriesId || '',
      type: item.Type,
      percent: item.UserData?.PlayedPercentage ? Math.round(item.UserData.PlayedPercentage) : 0
    }
  }

  private mapNextUpItem(item: BaseItemDto, session: MediaProviderSession): VideoItem {
    return {
      id: item.Id || '',
      mediaRef: this.mediaRef(item.Id, item.Type, session),
      name: item.SeriesName || item.Name || '',
      image: this.resolveDisplayImage(item),
      seriesId: item.SeriesId || '',
      subtitle: `S${item.ParentIndexNumber || 0}:E${item.IndexNumber || 0} - ${item.Name || ''}`,
      type: item.Type,
      percent: 0
    }
  }

  private mapFollowedSeriesItem(item: BaseItemDto,
    session: MediaProviderSession): ChaseFollowedSeriesItem {
    return {
      id: item.Id || '',
      mediaRef: this.mediaRef(item.Id, item.Type, session),
      name: item.Name || '',
      image: this.resolveDisplayImage(item),
      seriesId: item.Id || '',
      subtitle: '已看完',
      type: BaseItemKind.Series,
      percent: 100,
      unplayedItemCount: item.UserData?.UnplayedItemCount,
      played: item.UserData?.Played
    }
  }

  private mediaRef(id: string | undefined, type: BaseItemKind | undefined,
    session: MediaProviderSession): MediaRef | undefined {
    const normalizedId = id?.trim() || ''
    if (normalizedId.length === 0) {
      return undefined
    }
    let kind = MediaRefKind.Unknown
    if (type === BaseItemKind.Movie) {
      kind = MediaRefKind.Movie
    } else if (type === BaseItemKind.Episode) {
      kind = MediaRefKind.Episode
    } else if (type === BaseItemKind.Series) {
      kind = MediaRefKind.Series
    }
    return new MediaRef(session.provider, session.accountScope, normalizedId, kind)
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
