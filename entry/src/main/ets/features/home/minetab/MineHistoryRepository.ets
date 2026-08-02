import { AxiosResponse } from '@ohos/axios'
import { getItemsApi, ImageUrlsApi } from 'sdk'
import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  BaseItemKind,
  ImageType,
  ItemFields,
  ItemSortBy,
  SortOrder
} from 'sdk/src/main/ets/generated-client/models'
import { getItemImageUrl } from '../../../common/CommonFunc'
import { ApiClient } from '../../../network/ApiClient'
import { MineHistoryCandidate } from './MineHistoryPlanner'

export class MineHistorySourceResult {
  /** Successfully mapped history candidates. */
  items: MineHistoryCandidate[] = []
  /** Whether this individual server request failed. */
  failed: boolean = false
}

export class MineHistoryLoadResult {
  /** In-progress playback items. */
  resume: MineHistorySourceResult = new MineHistorySourceResult()
  /** Fully played items. */
  completed: MineHistorySourceResult = new MineHistorySourceResult()
}

export class MineHistoryRepository {
  private readonly imageApi: ImageUrlsApi = new ImageUrlsApi()

  async load(): Promise<MineHistoryLoadResult> {
    const results = await Promise.all([
      this.fetchResume().then((items: MineHistoryCandidate[]): MineHistorySourceResult => {
        return this.success(items)
      }).catch((error: Error): MineHistorySourceResult => {
        console.warn(`[MineHistory] resume request failed: ${error.message}`)
        return this.failure()
      }),
      this.fetchCompleted().then((items: MineHistoryCandidate[]): MineHistorySourceResult => {
        return this.success(items)
      }).catch((error: Error): MineHistorySourceResult => {
        console.warn(`[MineHistory] completed request failed: ${error.message}`)
        return this.failure()
      })
    ])
    const result = new MineHistoryLoadResult()
    result.resume = results[0]
    result.completed = results[1]
    return result
  }

  private fetchResume(): Promise<MineHistoryCandidate[]> {
    try {
      return getItemsApi(ApiClient.Instance()).getResumeItems({
        userId: ApiClient.Instance().userId,
        limit: 24,
        fields: [ItemFields.PrimaryImageAspectRatio],
        mediaTypes: ['Video'],
        enableUserData: true,
        enableImages: true,
        imageTypeLimit: 1,
        enableImageTypes: [ImageType.Primary, ImageType.Thumb],
        enableTotalRecordCount: false
      }).then((response: AxiosResponse<BaseItemDtoQueryResult>): MineHistoryCandidate[] => {
        return this.mapItems(response.data?.Items || [])
      }).catch((error: Error): MineHistoryCandidate[] => {
        throw new Error(error.message)
      })
    } catch (_) {
      return Promise.reject(new Error('Unable to request resumable history'))
    }
  }

  private fetchCompleted(): Promise<MineHistoryCandidate[]> {
    try {
      return getItemsApi(ApiClient.Instance()).getItems({
        userId: ApiClient.Instance().userId,
        limit: 24,
        recursive: true,
        includeItemTypes: [BaseItemKind.Movie, BaseItemKind.Episode],
        mediaTypes: ['Video'],
        isPlayed: true,
        sortBy: [ItemSortBy.DatePlayed],
        sortOrder: [SortOrder.Descending],
        fields: [ItemFields.PrimaryImageAspectRatio],
        enableUserData: true,
        enableImages: true,
        imageTypeLimit: 1,
        enableImageTypes: [ImageType.Primary, ImageType.Thumb],
        enableTotalRecordCount: false
      }).then((response: AxiosResponse<BaseItemDtoQueryResult>): MineHistoryCandidate[] => {
        return this.mapItems(response.data?.Items || [])
      }).catch((error: Error): MineHistoryCandidate[] => {
        throw new Error(error.message)
      })
    } catch (_) {
      return Promise.reject(new Error('Unable to request completed history'))
    }
  }

  private mapItems(items: BaseItemDto[]): MineHistoryCandidate[] {
    const result: MineHistoryCandidate[] = []
    items.forEach((item: BaseItemDto) => {
      const candidate = this.mapItem(item)
      if (candidate) {
        result.push(candidate)
      }
    })
    return result
  }

  private mapItem(item: BaseItemDto): MineHistoryCandidate | undefined {
    const id = item.Id || ''
    const lastPlayedDate = item.UserData?.LastPlayedDate || ''
    if (id.length === 0 || lastPlayedDate.length === 0) {
      return undefined
    }
    return {
      id,
      name: item.Type === BaseItemKind.Episode ? item.SeriesName || item.Name || '' : item.Name || '',
      image: this.resolvePoster(item),
      lastPlayedDate,
      played: item.UserData?.Played === true,
      percent: item.UserData?.PlayedPercentage || 0
    }
  }

  private resolvePoster(item: BaseItemDto): string {
    if (item.Type === BaseItemKind.Episode && item.SeriesId) {
      return ApiClient.Instance().basePath +
        this.imageApi.getItemImageUrlById(item.SeriesId, ImageType.Primary)
    }
    return getItemImageUrl(item, ImageType.Primary)
  }

  private success(items: MineHistoryCandidate[]): MineHistorySourceResult {
    const result = new MineHistorySourceResult()
    result.items = items
    return result
  }

  private failure(): MineHistorySourceResult {
    const result = new MineHistorySourceResult()
    result.failed = true
    return result
  }
}
