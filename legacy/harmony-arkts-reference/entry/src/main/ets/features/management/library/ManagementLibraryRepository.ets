import {
  CountryInfo,
  CultureDto,
  MediaPathInfo,
  TaskInfo,
  TaskState,
  VirtualFolderInfo
} from 'sdk/src/main/ets/generated-client/models'
import {
  ManagementLibraryApiService,
  ManagementLibraryService
} from './ManagementLibraryApiService'
import { ManagementLibraryChangePlanner } from './ManagementLibraryChangePlanner'
import { ManagementLibraryMapper } from './ManagementLibraryMapper'
import {
  ManagementLibraryDraft,
  ManagementLibraryAvailableOptions,
  ManagementLibraryItem,
  ManagementLibraryOperationCode,
  ManagementLibraryOperationResult,
  ManagementLibraryValidationCode
} from './ManagementLibraryModels'
import { ManagementLibraryPolicy } from './ManagementLibraryPolicy'

const REFRESH_LIBRARY_TASK_KEY: string = 'RefreshLibrary'

interface ManagementLibraryServerErrorPayload {
  Message?: string
  message?: string
  Error?: string
  error?: string
  Detail?: string
  detail?: string
  Title?: string
  title?: string
}

interface ManagementLibraryHttpResponse {
  status: number
  data?: string | ManagementLibraryServerErrorPayload
}

interface ManagementLibraryHttpError {
  response?: ManagementLibraryHttpResponse
  message?: string
}

export enum ManagementLibraryLoadStatus {
  Content = 'content',
  Empty = 'empty',
  Error = 'error',
  Unsupported = 'unsupported'
}

export class ManagementLibraryLoadResult {
  status: ManagementLibraryLoadStatus = ManagementLibraryLoadStatus.Empty
  items: ManagementLibraryItem[] = []
  errorCode: ManagementLibraryOperationCode = ManagementLibraryOperationCode.None
  message: string = ''
}

export class ManagementLibraryRepository {
  private service: ManagementLibraryService

  constructor(service: ManagementLibraryService = new ManagementLibraryApiService()) {
    this.service = service
  }

  async requireAdministrator(): Promise<ManagementLibraryOperationResult> {
    try {
      const user = await this.service.getCurrentUser()
      return user.Policy?.IsAdministrator === true ?
        ManagementLibraryOperationResult.success() :
        ManagementLibraryOperationResult.failure(ManagementLibraryOperationCode.PermissionDenied)
    } catch (error) {
      return this.failureFor(error as Object)
    }
  }

  async loadLibraries(): Promise<ManagementLibraryLoadResult> {
    const result = new ManagementLibraryLoadResult()
    try {
      result.items = (await this.service.getVirtualFolders())
        .map((folder: VirtualFolderInfo) => ManagementLibraryMapper.toItem(folder))
      result.status = result.items.length > 0 ?
        ManagementLibraryLoadStatus.Content : ManagementLibraryLoadStatus.Empty
      return result
    } catch (error) {
      const status = this.getHttpStatus(error as Object)
      result.message = this.getServerErrorMessage(error as Object)
      if (status === 404) {
        result.status = ManagementLibraryLoadStatus.Unsupported
        result.errorCode = ManagementLibraryOperationCode.Unsupported
      } else {
        result.status = ManagementLibraryLoadStatus.Error
        result.errorCode = this.codeForStatus(status)
      }
      return result
    }
  }

  async loadAvailableOptions(type: string, isNew: boolean): Promise<ManagementLibraryAvailableOptions> {
    return await this.service.getAvailableOptions(type, isNew)
  }

  async loadCountries(): Promise<CountryInfo[]> {
    return await this.service.getCountries()
  }

  async loadCultures(): Promise<CultureDto[]> {
    return await this.service.getCultures()
  }

  async loadLibraryById(itemId: string): Promise<ManagementLibraryItem | undefined> {
    const id = itemId.trim()
    if (id.length === 0) return undefined
    const folders = await this.service.getVirtualFolders()
    const folder = folders.find((item: VirtualFolderInfo) => item.ItemId === id)
    return folder ? ManagementLibraryMapper.toItem(folder) : undefined
  }

  async validateWritablePath(path: string): Promise<ManagementLibraryOperationResult> {
    const access = await this.requireAdministrator()
    if (!access.ok) return access
    const normalized = path.trim()
    if (normalized.length === 0) {
      return ManagementLibraryOperationResult.failure(ManagementLibraryOperationCode.NotFound)
    }
    try {
      await this.service.validatePath(normalized, true)
      return ManagementLibraryOperationResult.success()
    } catch (error) {
      return this.failureFor(error as Object)
    }
  }

  async createLibrary(draft: ManagementLibraryDraft): Promise<ManagementLibraryOperationResult> {
    const access = await this.requireAdministrator()
    if (!access.ok) return access

    const validation = ManagementLibraryPolicy.validateDraft(draft)
    if (validation !== ManagementLibraryValidationCode.None) {
      return ManagementLibraryOperationResult.failure(ManagementLibraryOperationCode.Failed, validation)
    }

    const name = draft.name.trim()
    const paths = ManagementLibraryPolicy.normalizePaths(draft.paths)
    try {
      for (const path of paths) {
        await this.service.validatePath(path, true)
      }
      await this.service.addVirtualFolder(name,
        ManagementLibraryPolicy.collectionTypeForCreate(draft.collectionType), paths, draft.options)
    } catch (error) {
      return this.failureFor(error as Object)
    }

    try {
      const folders = await this.service.getVirtualFolders()
      const created = folders.find((folder: VirtualFolderInfo) => folder.Name === name)
      if (created) {
        return ManagementLibraryOperationResult.success(ManagementLibraryMapper.toItem(created))
      }
      return ManagementLibraryOperationResult.failure(ManagementLibraryOperationCode.PartialSuccess,
        'Created library was not present in the server reload')
    } catch (error) {
      return ManagementLibraryOperationResult.failure(ManagementLibraryOperationCode.PartialSuccess,
        this.getServerErrorMessage(error as Object))
    }
  }

  async saveEdit(original: ManagementLibraryDraft,
    edited: ManagementLibraryDraft): Promise<ManagementLibraryOperationResult> {
    const access = await this.requireAdministrator()
    if (!access.ok) return access

    let current: VirtualFolderInfo | undefined
    try {
      const folders = await this.service.getVirtualFolders()
      current = folders.find((folder: VirtualFolderInfo) => folder.ItemId === original.id)
    } catch (error) {
      return this.failureFor(error as Object)
    }
    if (!current) {
      return ManagementLibraryOperationResult.failure(ManagementLibraryOperationCode.NotFound)
    }

    const validation = ManagementLibraryPolicy.validateDraft(edited)
    if (validation !== ManagementLibraryValidationCode.None) {
      return ManagementLibraryOperationResult.failure(ManagementLibraryOperationCode.Failed, validation)
    }

    const plan = ManagementLibraryChangePlanner.plan(original, edited)
    let writeSucceeded = false
    let workingName = current.Name || original.originalName
    try {
      if (plan.newName.length > 0) {
        await this.service.renameVirtualFolder(workingName, plan.newName)
        workingName = plan.newName
        writeSucceeded = true
      }
      for (const path of plan.removedPaths) {
        await this.service.removeMediaPath(workingName, path)
        writeSucceeded = true
      }
      for (const path of plan.addedPaths) {
        await this.service.validatePath(path, true)
        await this.service.addMediaPath(workingName, path)
        writeSucceeded = true
      }
      const changedPathInfos = this.changedPathInfos(original, edited)
      for (const pathInfo of changedPathInfos) {
        await this.service.updateMediaPath(workingName, pathInfo)
        writeSucceeded = true
      }
      if (plan.optionsChanged) {
        await this.service.updateLibraryOptions(original.id, edited.options)
        writeSucceeded = true
      }

      const reloaded = await this.reloadLibrary(original.id)
      if (reloaded) {
        return ManagementLibraryOperationResult.success(reloaded)
      }
      return ManagementLibraryOperationResult.failure(
        writeSucceeded ? ManagementLibraryOperationCode.PartialSuccess : ManagementLibraryOperationCode.NotFound,
        'Edited library was not present in the server reload')
    } catch (error) {
      if (writeSucceeded) {
        return await this.partialFailure(error as Object, original.id)
      }
      return this.failureFor(error as Object)
    }
  }

  async removeLibrary(name: string): Promise<ManagementLibraryOperationResult> {
    const access = await this.requireAdministrator()
    if (!access.ok) return access
    const normalizedName = name.trim()
    if (normalizedName.length === 0) {
      return ManagementLibraryOperationResult.failure(ManagementLibraryOperationCode.NotFound)
    }

    try {
      await this.service.removeVirtualFolder(normalizedName)
    } catch (error) {
      return this.failureFor(error as Object)
    }

    try {
      const folders = await this.service.getVirtualFolders()
      const retained = folders.find((folder: VirtualFolderInfo) => folder.Name === normalizedName)
      if (!retained) return ManagementLibraryOperationResult.success()
      return ManagementLibraryOperationResult.failure(ManagementLibraryOperationCode.PartialSuccess,
        'Removed library was still present in the server reload')
    } catch (error) {
      return ManagementLibraryOperationResult.failure(ManagementLibraryOperationCode.PartialSuccess,
        this.getServerErrorMessage(error as Object))
    }
  }

  async startAllScan(): Promise<ManagementLibraryOperationResult> {
    const access = await this.requireAdministrator()
    if (!access.ok) return access
    try {
      const tasks = await this.service.getScheduledTasks()
      const task = tasks.find((item: TaskInfo) => item.Key === REFRESH_LIBRARY_TASK_KEY)
      if (!task || !task.Id) {
        return ManagementLibraryOperationResult.failure(ManagementLibraryOperationCode.Unsupported)
      }
      if (task.State !== TaskState.Idle) {
        return ManagementLibraryOperationResult.failure(ManagementLibraryOperationCode.Conflict)
      }
      await this.service.startScheduledTask(task.Id)
      return ManagementLibraryOperationResult.success()
    } catch (error) {
      return this.failureFor(error as Object)
    }
  }

  async startLibraryScan(itemId: string): Promise<ManagementLibraryOperationResult> {
    const access = await this.requireAdministrator()
    if (!access.ok) return access
    const id = itemId.trim()
    if (id.length === 0) {
      return ManagementLibraryOperationResult.failure(ManagementLibraryOperationCode.NotFound)
    }
    try {
      await this.service.refreshItem(id)
      return ManagementLibraryOperationResult.success()
    } catch (error) {
      return this.failureFor(error as Object)
    }
  }

  private changedPathInfos(original: ManagementLibraryDraft, edited: ManagementLibraryDraft): MediaPathInfo[] {
    const changed: MediaPathInfo[] = []
    const originalInfos = original.options.PathInfos || []
    const editedInfos = edited.options.PathInfos || []
    editedInfos.forEach((pathInfo: MediaPathInfo) => {
      const path = pathInfo.Path || ''
      const originalInfo = originalInfos.find((item: MediaPathInfo) => (item.Path || '') === path)
      if (path.length > 0 && originalInfo && JSON.stringify(originalInfo) !== JSON.stringify(pathInfo)) {
        changed.push(pathInfo)
      }
    })
    return changed
  }

  private async reloadLibrary(itemId: string): Promise<ManagementLibraryItem | undefined> {
    const folders = await this.service.getVirtualFolders()
    const folder = folders.find((item: VirtualFolderInfo) => item.ItemId === itemId)
    return folder ? ManagementLibraryMapper.toItem(folder) : undefined
  }

  private async partialFailure(error: Object, itemId: string): Promise<ManagementLibraryOperationResult> {
    const result = ManagementLibraryOperationResult.failure(ManagementLibraryOperationCode.PartialSuccess,
      this.getServerErrorMessage(error))
    try {
      result.library = await this.reloadLibrary(itemId)
    } catch (reloadError) {
      const reloadMessage = this.getServerErrorMessage(reloadError as Object)
      if (reloadMessage.length > 0) {
        result.message = result.message.length > 0 ? `${result.message}; ${reloadMessage}` : reloadMessage
      }
    }
    return result
  }

  private failureFor(error: Object): ManagementLibraryOperationResult {
    return ManagementLibraryOperationResult.failure(
      this.codeForStatus(this.getHttpStatus(error)), this.getServerErrorMessage(error))
  }

  private codeForStatus(status: number | undefined): ManagementLibraryOperationCode {
    if (status === 401) return ManagementLibraryOperationCode.AuthenticationExpired
    if (status === 403) return ManagementLibraryOperationCode.PermissionDenied
    if (status === 404) return ManagementLibraryOperationCode.NotFound
    if (status === 409) return ManagementLibraryOperationCode.Conflict
    return ManagementLibraryOperationCode.Failed
  }

  private getHttpStatus(error: Object): number | undefined {
    return (error as ManagementLibraryHttpError).response?.status
  }

  private getServerErrorMessage(error: Object): string {
    const httpError = error as ManagementLibraryHttpError
    const data = httpError.response?.data
    if (typeof data === 'string') return this.normalizeMessage(data)
    if (data) {
      return this.normalizeMessage(data.Message || data.message || data.Error || data.error ||
        data.Detail || data.detail || data.Title || data.title)
    }
    return this.normalizeMessage(httpError.message)
  }

  private normalizeMessage(message: string | undefined): string {
    if (!message) return ''
    const normalized = message.replace(/\s+/g, ' ').trim()
    return normalized.length > 500 ? `${normalized.slice(0, 497)}...` : normalized
  }
}
