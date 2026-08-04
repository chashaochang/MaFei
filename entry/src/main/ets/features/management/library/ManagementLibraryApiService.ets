import { AxiosInstance, AxiosResponse, RawAxiosRequestConfig } from '@ohos/axios'
import {
  AddVirtualFolderDto,
  CountryInfo,
  CultureDto,
  FileSystemEntryInfo,
  MediaPathInfo,
  TaskInfo,
  UpdateLibraryOptionsDto,
  UserDto,
  ValidatePathDto,
  VirtualFolderInfo
} from 'sdk/src/main/ets/generated-client/models'
import { getLibraryApi } from 'sdk/src/main/ets/utils/api/library-api'
import { getUserApi } from 'sdk/src/main/ets/utils/api/user-api'
import { ApiClient } from '../../../network/ApiClient'
import {
  ManagementLibraryAvailableOptions,
  ManagementLibraryOptions
} from './ManagementLibraryModels'

const VIRTUAL_FOLDERS_PATH: string = '/Library/VirtualFolders'
const LIBRARY_OPTIONS_PATH: string = '/Library/VirtualFolders/LibraryOptions'
const LIBRARY_NAME_PATH: string = '/Library/VirtualFolders/Name'
const LIBRARY_PATHS_PATH: string = '/Library/VirtualFolders/Paths'
const LIBRARY_PATH_UPDATE_PATH: string = '/Library/VirtualFolders/Paths/Update'
const AVAILABLE_OPTIONS_PATH: string = '/Libraries/AvailableOptions'
const DRIVES_PATH: string = '/Environment/Drives'
const DIRECTORY_CONTENTS_PATH: string = '/Environment/DirectoryContents'
const PARENT_PATH: string = '/Environment/ParentPath'
const VALIDATE_PATH: string = '/Environment/ValidatePath'
const COUNTRIES_PATH: string = '/Localization/Countries'
const CULTURES_PATH: string = '/Localization/Cultures'
const ITEM_REFRESH_PATH: string = '/Items/{itemId}/Refresh'
const SCHEDULED_TASKS_PATH: string = '/ScheduledTasks'
const SCHEDULED_TASK_RUNNING_PATH: string = '/ScheduledTasks/Running/{taskId}'

export interface ManagementLibraryService {
  getCurrentUser(): Promise<UserDto>
  getVirtualFolders(): Promise<VirtualFolderInfo[]>
  getAvailableOptions(type: string, isNew: boolean): Promise<ManagementLibraryAvailableOptions>
  getCountries(): Promise<CountryInfo[]>
  getCultures(): Promise<CultureDto[]>
  addVirtualFolder(name: string, type: string | undefined, paths: string[],
    options: ManagementLibraryOptions): Promise<void>
  renameVirtualFolder(name: string, newName: string): Promise<void>
  updateLibraryOptions(id: string, options: ManagementLibraryOptions): Promise<void>
  removeVirtualFolder(name: string): Promise<void>
  addMediaPath(name: string, path: string): Promise<void>
  removeMediaPath(name: string, path: string): Promise<void>
  updateMediaPath(name: string, pathInfo: MediaPathInfo): Promise<void>
  getDrives(): Promise<FileSystemEntryInfo[]>
  getDirectoryContents(path: string): Promise<FileSystemEntryInfo[]>
  getParentPath(path: string): Promise<string>
  validatePath(path: string, writable: boolean): Promise<void>
  refreshItem(itemId: string): Promise<void>
  getScheduledTasks(): Promise<TaskInfo[]>
  startScheduledTask(taskId: string): Promise<void>
  deleteItems(ids: string[]): Promise<void>
}

export class ManagementLibraryApiService implements ManagementLibraryService {
  async getCurrentUser(): Promise<UserDto> {
    const response: AxiosResponse<UserDto> = await getUserApi(ApiClient.Instance()).getCurrentUser()
    return response.data
  }

  async getVirtualFolders(): Promise<VirtualFolderInfo[]> {
    const response: AxiosResponse<VirtualFolderInfo[]> = await this.axios().get<VirtualFolderInfo[]>(
      this.url(VIRTUAL_FOLDERS_PATH), this.config())
    this.ensureSuccess(response.status, 'virtual folders')
    return response.data || []
  }

  async getAvailableOptions(type: string, isNew: boolean): Promise<ManagementLibraryAvailableOptions> {
    const response: AxiosResponse<ManagementLibraryAvailableOptions> =
      await this.axios().get<ManagementLibraryAvailableOptions>(
        this.url(AVAILABLE_OPTIONS_PATH),
        this.config({ libraryContentType: type, isNewLibrary: isNew }))
    this.ensureSuccess(response.status, 'available library options')
    return response.data
  }

  async getCountries(): Promise<CountryInfo[]> {
    const response: AxiosResponse<CountryInfo[]> = await this.axios().get<CountryInfo[]>(
      this.url(COUNTRIES_PATH), this.config())
    this.ensureSuccess(response.status, 'countries')
    return response.data || []
  }

  async getCultures(): Promise<CultureDto[]> {
    const response: AxiosResponse<CultureDto[]> = await this.axios().get<CultureDto[]>(
      this.url(CULTURES_PATH), this.config())
    this.ensureSuccess(response.status, 'cultures')
    return response.data || []
  }

  async addVirtualFolder(name: string, type: string | undefined, paths: string[],
    options: ManagementLibraryOptions): Promise<void> {
    const body: AddVirtualFolderDto = { LibraryOptions: options }
    const response: AxiosResponse<void> = await this.axios().post<void>(
      this.url(VIRTUAL_FOLDERS_PATH), body,
      this.config({ name: name, collectionType: type, paths: paths, refreshLibrary: false }))
    this.ensureSuccess(response.status, 'add virtual folder')
  }

  async renameVirtualFolder(name: string, newName: string): Promise<void> {
    const response: AxiosResponse<void> = await this.axios().post<void>(
      this.url(LIBRARY_NAME_PATH), undefined,
      this.config({ name: name, newName: newName, refreshLibrary: false }))
    this.ensureSuccess(response.status, 'rename virtual folder')
  }

  async updateLibraryOptions(id: string, options: ManagementLibraryOptions): Promise<void> {
    const body: UpdateLibraryOptionsDto = { Id: id, LibraryOptions: options }
    const response: AxiosResponse<void> = await this.axios().post<void>(
      this.url(LIBRARY_OPTIONS_PATH), body, this.config())
    this.ensureSuccess(response.status, 'update library options')
  }

  async removeVirtualFolder(name: string): Promise<void> {
    const response: AxiosResponse<void> = await this.axios().delete<void>(
      this.url(VIRTUAL_FOLDERS_PATH),
      this.config({ name: name, refreshLibrary: false }))
    this.ensureSuccess(response.status, 'remove virtual folder')
  }

  async addMediaPath(name: string, path: string): Promise<void> {
    const response: AxiosResponse<void> = await this.axios().post<void>(
      this.url(LIBRARY_PATHS_PATH), undefined,
      this.config({ name: name, path: path, refreshLibrary: false }))
    this.ensureSuccess(response.status, 'add media path')
  }

  async removeMediaPath(name: string, path: string): Promise<void> {
    const response: AxiosResponse<void> = await this.axios().delete<void>(
      this.url(LIBRARY_PATHS_PATH),
      this.config({ name: name, path: path, refreshLibrary: false }))
    this.ensureSuccess(response.status, 'remove media path')
  }

  async updateMediaPath(name: string, pathInfo: MediaPathInfo): Promise<void> {
    const response: AxiosResponse<void> = await this.axios().post<void>(
      this.url(LIBRARY_PATH_UPDATE_PATH), pathInfo,
      this.config({ name: name, refreshLibrary: false }))
    this.ensureSuccess(response.status, 'update media path')
  }

  async getDrives(): Promise<FileSystemEntryInfo[]> {
    const response: AxiosResponse<FileSystemEntryInfo[]> = await this.axios().get<FileSystemEntryInfo[]>(
      this.url(DRIVES_PATH), this.config())
    this.ensureSuccess(response.status, 'environment drives')
    return response.data || []
  }

  async getDirectoryContents(path: string): Promise<FileSystemEntryInfo[]> {
    const response: AxiosResponse<FileSystemEntryInfo[]> = await this.axios().get<FileSystemEntryInfo[]>(
      this.url(DIRECTORY_CONTENTS_PATH),
      this.config({ path: path, includeFiles: false, includeDirectories: true }))
    this.ensureSuccess(response.status, 'directory contents')
    return response.data || []
  }

  async getParentPath(path: string): Promise<string> {
    const response: AxiosResponse<string> = await this.axios().get<string>(
      this.url(PARENT_PATH), this.config({ path: path }))
    this.ensureSuccess(response.status, 'parent path')
    return response.data || ''
  }

  async validatePath(path: string, writable: boolean): Promise<void> {
    const body: ValidatePathDto = {
      Path: path,
      ValidateWritable: writable,
      IsFile: false
    }
    const response: AxiosResponse<void> = await this.axios().post<void>(
      this.url(VALIDATE_PATH), body, this.config())
    this.ensureSuccess(response.status, 'validate path')
  }

  async refreshItem(itemId: string): Promise<void> {
    const endpoint = ITEM_REFRESH_PATH.replace('{itemId}', encodeURIComponent(itemId))
    const response: AxiosResponse<void> = await this.axios().post<void>(
      this.url(endpoint), undefined,
      this.config({ metadataRefreshMode: 'None', imageRefreshMode: 'None' }))
    this.ensureSuccess(response.status, 'refresh library item')
  }

  async getScheduledTasks(): Promise<TaskInfo[]> {
    const response: AxiosResponse<TaskInfo[]> = await this.axios().get<TaskInfo[]>(
      this.url(SCHEDULED_TASKS_PATH), this.config())
    this.ensureSuccess(response.status, 'scheduled tasks')
    return response.data || []
  }

  async startScheduledTask(taskId: string): Promise<void> {
    const endpoint = SCHEDULED_TASK_RUNNING_PATH.replace('{taskId}', encodeURIComponent(taskId))
    const response: AxiosResponse<void> = await this.axios().post<void>(
      this.url(endpoint), undefined, this.config())
    this.ensureSuccess(response.status, 'start scheduled task')
  }

  async deleteItems(ids: string[]): Promise<void> {
    const response: AxiosResponse<void> = await getLibraryApi(ApiClient.Instance())
      .deleteItems({ ids: ids })
    this.ensureSuccess(response.status, 'delete media items')
  }

  private axios(): AxiosInstance {
    return ApiClient.Instance().axiosInstance as AxiosInstance
  }

  private url(path: string): string {
    return `${ApiClient.Instance().basePath}${path}`
  }

  private config(params?: Object): RawAxiosRequestConfig {
    return {
      headers: { Authorization: ApiClient.Instance().authorizationHeader },
      params: params
    }
  }

  private ensureSuccess(status: number, operation: string): void {
    if (status < 200 || status >= 300) {
      throw Error(`Unexpected ${operation} response: ${status}`)
    }
  }
}
