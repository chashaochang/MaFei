import { ActivityLogEntry, BaseItemDto, UserDto } from 'sdk/src/main/ets/generated-client/models';
import { ManagementErrorCode } from '../ManagementModels';
import { ManagementRepository } from '../ManagementRepository';
import { ManagementActivityApiService } from './ManagementActivityApiService';
import {
  MANAGEMENT_ACTIVITY_PAGE_SIZE,
  ManagementActivityItem,
  ManagementActivityMediaTarget,
  ManagementActivityPageResult,
  ManagementActivityRequest,
  ManagementActivityView
} from './ManagementActivityModels';
import { ManagementActivityQuery } from './ManagementActivityQuery';

interface ActivityHttpResponse {
  status: number
}

interface ActivityHttpError {
  isAxiosError?: boolean
  response?: ActivityHttpResponse
}

export class ManagementActivityRepository {
  private service: ManagementActivityApiService = new ManagementActivityApiService()
  private management: ManagementRepository = new ManagementRepository()

  isApiClientInitialized(): boolean {
    return this.management.isApiClientInitialized()
  }

  async checkCurrentUserAccess(): Promise<ManagementErrorCode> {
    return this.management.checkCurrentUserAccess()
  }

  async loadDashboard(nowMs: number = Date.now()): Promise<ManagementActivityPageResult> {
    const request = new ManagementActivityRequest()
    request.startIndex = 0
    request.limit = 7
    request.minDate = new Date(nowMs - 24 * 60 * 60 * 1000).toISOString()
    request.hasUserId = true
    return this.load(request)
  }

  async loadPage(pageIndex: number, view: ManagementActivityView): Promise<ManagementActivityPageResult> {
    const request = new ManagementActivityRequest()
    request.startIndex = ManagementActivityQuery.startIndex(pageIndex)
    request.limit = MANAGEMENT_ACTIVITY_PAGE_SIZE
    request.hasUserId = ManagementActivityQuery.hasUserId(view)
    return this.load(request)
  }

  async loadMediaTarget(itemId: string): Promise<ManagementActivityMediaTarget> {
    const result = new ManagementActivityMediaTarget()
    if (!itemId) return result
    try {
      const item = await this.service.getItem(itemId)
      return this.mapMediaTarget(item)
    } catch (error) {
      if (this.classifyError(error as Object) === ManagementErrorCode.Unsupported) {
        return result
      }
      throw error as Error
    }
  }

  classifyError(error: Object): ManagementErrorCode {
    const httpError = error as ActivityHttpError
    const status = httpError.response?.status
    if (status === 401) return ManagementErrorCode.AuthenticationExpired
    if (status === 403) return ManagementErrorCode.PermissionDenied
    if (status === 404) return ManagementErrorCode.Unsupported
    return httpError.isAxiosError === true && httpError.response === undefined ?
      ManagementErrorCode.Offline : ManagementErrorCode.RequestFailed
  }

  private async load(request: ManagementActivityRequest): Promise<ManagementActivityPageResult> {
    const response = await this.service.getEntries(request)
    const users = await this.service.getUsers()
    const userNames = new Map<string, string>()
    users.forEach((user: UserDto) => {
      if (user.Id) userNames.set(user.Id, user.Name || '')
    })
    const result = new ManagementActivityPageResult()
    result.items = (response.Items || []).map((entry: ActivityLogEntry) =>
      this.mapEntry(entry, userNames))
    result.totalRecordCount = response.TotalRecordCount || 0
    result.startIndex = response.StartIndex || request.startIndex
    return result
  }

  private mapEntry(entry: ActivityLogEntry, userNames: Map<string, string>): ManagementActivityItem {
    const result = new ManagementActivityItem()
    result.id = entry.Id || 0
    result.date = entry.Date || ''
    result.severity = entry.Severity || 'Information'
    result.userId = entry.UserId || ''
    result.userName = userNames.get(result.userId) || ''
    result.name = entry.Name || ''
    result.shortOverview = entry.ShortOverview || entry.Overview || ''
    result.overview = entry.Overview || result.shortOverview
    result.type = entry.Type || ''
    result.itemId = entry.ItemId || ''
    return result
  }

  private mapMediaTarget(item: BaseItemDto): ManagementActivityMediaTarget {
    const result = new ManagementActivityMediaTarget()
    const type = item.Type || ''
    if (type !== 'Movie' && type !== 'Series' && type !== 'Episode' && type !== 'Video') {
      return result
    }
    result.available = true
    result.isTvShows = type === 'Series' || type === 'Episode'
    result.id = type === 'Episode' && item.SeriesId ? item.SeriesId : item.Id || ''
    result.mediaName = type
    return result
  }
}
