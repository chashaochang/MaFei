import {
  ItemCounts,
  SystemInfo,
  TaskInfo,
  TaskState,
  UserDto
} from 'sdk/src/main/ets/generated-client/models';
import { ManagementApiService } from './ManagementApiService';
import {
  ManagementActionResult,
  ManagementActionCode,
  ManagementErrorCode,
  ManagementMediaCounts,
  ManagementSectionKind,
  ManagementSectionResult,
  ManagementSectionStatus,
  ManagementServerOverview,
  ManagementTaskActionCode,
  ManagementTaskActionResult,
  ManagementTaskItem,
  ManagementUserSummary
} from './ManagementModels';
import { ManagementTaskActionPolicy } from './ManagementTaskActionPolicy';

const REFRESH_LIBRARY_TASK_KEY: string = 'RefreshLibrary'

interface ManagementServerErrorPayload {
  Message?: string
  message?: string
  Error?: string
  error?: string
  Detail?: string
  detail?: string
  Title?: string
  title?: string
}

interface ManagementHttpResponse {
  status: number
  data: string | ManagementServerErrorPayload
}

interface ManagementHttpError {
  isAxiosError?: boolean
  response?: ManagementHttpResponse
}

export class ManagementRepository {
  private service: ManagementApiService = new ManagementApiService()

  isApiClientInitialized(): boolean {
    return this.service.isApiClientInitialized()
  }

  async isCurrentUserAdministrator(): Promise<boolean> {
    const user = await this.service.getCurrentUser()
    return user.Policy?.IsAdministrator === true
  }

  async checkCurrentUserAccess(): Promise<ManagementErrorCode> {
    try {
      return await this.isCurrentUserAdministrator() ? ManagementErrorCode.None :
        ManagementErrorCode.PermissionDenied
    } catch (error) {
      return this.classifyError(error as Object)
    }
  }

  async loadTasksSection(): Promise<ManagementSectionResult> {
    const result = this.createResult(ManagementSectionKind.Tasks)
    try {
      const tasks = await this.service.getScheduledTasks()
      result.tasks = tasks
        .filter((item: TaskInfo) => item.IsHidden !== true && this.isTaskBusy(item))
        .map((item: TaskInfo) => this.mapTask(item))
      result.status = result.tasks.length > 0 ? ManagementSectionStatus.Content : ManagementSectionStatus.Empty
    } catch (error) {
      this.applyError(result, error, true)
    }
    return result
  }

  async startLibraryScan(): Promise<ManagementActionResult> {
    const result = new ManagementActionResult()
    try {
      const tasks = await this.service.getScheduledTasks()
      const scanTask = tasks.find((task: TaskInfo) => task.Key === REFRESH_LIBRARY_TASK_KEY)
      if (!scanTask || !scanTask.Id) {
        result.code = ManagementActionCode.ScanTaskUnavailable
        return result
      }
      if (this.isTaskBusy(scanTask)) {
        result.code = ManagementActionCode.ScanAlreadyRunning
        return result
      }
      if (scanTask.State !== TaskState.Idle) {
        result.code = ManagementActionCode.ScanTaskUnavailable
        return result
      }
      await this.service.startScheduledTask(scanTask.Id)
      result.ok = true
      result.code = ManagementActionCode.ScanStarted
      return result
    } catch (error) {
      const status = this.getHttpStatus(error)
      if (status === 401) {
        result.code = ManagementActionCode.AuthenticationExpired
      } else if (status === 403) {
        result.code = ManagementActionCode.PermissionDenied
      } else {
        result.code = ManagementActionCode.ScanStartFailed
      }
      result.message = this.getServerErrorMessage(error)
      return result
    }
  }

  async loadTask(taskId: string): Promise<ManagementTaskItem | null> {
    if (!taskId) return null
    const tasks = await this.service.getScheduledTasks()
    const task = tasks.find((item: TaskInfo) => item.Id === taskId)
    return task ? this.mapTask(task) : null
  }

  async startTask(task: ManagementTaskItem): Promise<ManagementTaskActionResult> {
    const result = new ManagementTaskActionResult()
    if (!ManagementTaskActionPolicy.canStart(task)) {
      result.code = ManagementTaskActionCode.Busy
      return result
    }
    const accessCode = await this.checkCurrentUserAccess()
    if (accessCode !== ManagementErrorCode.None) {
      result.code = accessCode === ManagementErrorCode.AuthenticationExpired ?
        ManagementTaskActionCode.AuthenticationExpired :
        (accessCode === ManagementErrorCode.PermissionDenied ?
          ManagementTaskActionCode.PermissionDenied : ManagementTaskActionCode.Failed)
      return result
    }
    try {
      await this.service.startScheduledTask(task.id)
      result.ok = true
      result.code = ManagementTaskActionCode.Started
      return result
    } catch (error) {
      result.code = ManagementTaskActionPolicy.codeForHttpStatus(this.getHttpStatus(error) || 0)
      result.message = this.getServerErrorMessage(error)
      return result
    }
  }

  async cancelTask(task: ManagementTaskItem): Promise<ManagementTaskActionResult> {
    const result = new ManagementTaskActionResult()
    if (!ManagementTaskActionPolicy.canCancel(task)) {
      result.code = ManagementTaskActionCode.Failed
      return result
    }
    const accessCode = await this.checkCurrentUserAccess()
    if (accessCode !== ManagementErrorCode.None) {
      result.code = accessCode === ManagementErrorCode.AuthenticationExpired ?
        ManagementTaskActionCode.AuthenticationExpired :
        (accessCode === ManagementErrorCode.PermissionDenied ?
          ManagementTaskActionCode.PermissionDenied : ManagementTaskActionCode.Failed)
      return result
    }
    try {
      await this.service.cancelScheduledTask(task.id)
      result.ok = true
      result.code = ManagementTaskActionCode.Cancelled
      return result
    } catch (error) {
      result.code = ManagementTaskActionPolicy.codeForHttpStatus(this.getHttpStatus(error) || 0)
      result.message = this.getServerErrorMessage(error)
      return result
    }
  }

  async loadServerSection(): Promise<ManagementSectionResult> {
    const result = this.createResult(ManagementSectionKind.Server)
    try {
      result.server = this.mapServer(await this.service.getSystemInfo())
      result.status = ManagementSectionStatus.Content
    } catch (error) {
      this.applyError(result, error)
    }
    return result
  }

  async loadCountsSection(): Promise<ManagementSectionResult> {
    const result = this.createResult(ManagementSectionKind.Counts)
    try {
      result.counts = this.mapCounts(await this.service.getItemCounts())
      result.status = ManagementSectionStatus.Content
    } catch (error) {
      this.applyError(result, error)
    }
    return result
  }

  async loadUsersSection(): Promise<ManagementSectionResult> {
    const result = this.createResult(ManagementSectionKind.Users)
    try {
      result.users = this.mapUserSummary(await this.service.getUsers())
      result.status = ManagementSectionStatus.Content
    } catch (error) {
      this.applyError(result, error)
    }
    return result
  }

  private createResult(kind: ManagementSectionKind): ManagementSectionResult {
    const result = new ManagementSectionResult()
    result.kind = kind
    return result
  }

  private applyError(result: ManagementSectionResult, error: Object, unsupportedOn404: boolean = false): void {
    const status = this.getHttpStatus(error)
    result.message = this.getServerErrorMessage(error)
    if (unsupportedOn404 && status === 404) {
      result.status = ManagementSectionStatus.Unsupported
      result.errorCode = ManagementErrorCode.Unsupported
      return
    }
    result.status = ManagementSectionStatus.Error
    result.errorCode = this.classifyError(error)
  }

  private getHttpStatus(error: Object): number | undefined {
    return (error as ManagementHttpError).response?.status
  }

  private classifyError(error: Object): ManagementErrorCode {
    const status = this.getHttpStatus(error)
    if (status === 401) {
      return ManagementErrorCode.AuthenticationExpired
    }
    if (status === 403) {
      return ManagementErrorCode.PermissionDenied
    }
    return this.isOfflineError(error) ? ManagementErrorCode.Offline : ManagementErrorCode.RequestFailed
  }

  private isOfflineError(error: Object): boolean {
    const httpError = error as ManagementHttpError
    return httpError.isAxiosError === true && httpError.response === undefined
  }

  private getServerErrorMessage(error: Object): string {
    const data = (error as ManagementHttpError).response?.data
    if (typeof data === 'string') {
      return this.normalizeServerMessage(data)
    }
    if (data) {
      return this.normalizeServerMessage(data.Message || data.message || data.Error || data.error ||
        data.Detail || data.detail || data.Title || data.title)
    }
    return ''
  }

  private normalizeServerMessage(message: string | undefined): string {
    if (!message) {
      return ''
    }
    const normalized = message.replace(/\s+/g, ' ').trim()
    const lowerCase = normalized.toLowerCase()
    if (lowerCase.includes('<html') || lowerCase.includes('<!doctype')) {
      return ''
    }
    return normalized.length > 240 ? `${normalized.slice(0, 237)}...` : normalized
  }

  private mapServer(info: SystemInfo): ManagementServerOverview {
    const server = new ManagementServerOverview()
    server.name = info.ServerName || ''
    server.version = info.Version || ''
    server.productName = info.ProductName || 'Jellyfin'
    server.operatingSystem = info.OperatingSystemDisplayName || info.OperatingSystem || ''
    server.address = info.LocalAddress || ''
    server.online = !info.IsShuttingDown
    server.pendingRestart = info.HasPendingRestart === true
    return server
  }

  private mapCounts(counts: ItemCounts): ManagementMediaCounts {
    const result = new ManagementMediaCounts()
    result.movies = counts.MovieCount || 0
    result.series = counts.SeriesCount || 0
    result.episodes = counts.EpisodeCount || 0
    result.total = counts.ItemCount || result.movies + result.series + result.episodes
    return result
  }

  private mapUserSummary(users: UserDto[]): ManagementUserSummary {
    const summary = new ManagementUserSummary()
    summary.total = users.length
    summary.administrators = users.filter((user: UserDto) =>
      user.Policy?.IsAdministrator === true).length
    summary.disabled = users.filter((user: UserDto) => user.Policy?.IsDisabled === true).length
    return summary
  }

  private mapTask(task: TaskInfo): ManagementTaskItem {
    const result = new ManagementTaskItem()
    result.id = task.Id || ''
    result.key = task.Key || ''
    result.name = task.Name || ''
    result.description = task.Description || ''
    result.category = task.Category || ''
    result.state = task.State || ''
    result.running = task.State === TaskState.Running
    result.busy = this.isTaskBusy(task)
    result.progress = Math.max(0, Math.min(100, task.CurrentProgressPercentage || 0))
    result.lastExecutionStart = task.LastExecutionResult?.StartTimeUtc || ''
    result.lastExecutionEnd = task.LastExecutionResult?.EndTimeUtc || ''
    result.lastExecutionStatus = task.LastExecutionResult?.Status || ''
    result.lastExecutionError = task.LastExecutionResult?.ErrorMessage ||
      task.LastExecutionResult?.LongErrorMessage || ''
    return result
  }

  private isTaskBusy(task: TaskInfo): boolean {
    return task.State === TaskState.Running || task.State === TaskState.Cancelling
  }

}
