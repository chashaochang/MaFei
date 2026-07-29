import { AxiosInstance, AxiosResponse } from '@ohos/axios';
import { ActivityLogEntryQueryResult, ItemCounts, SessionInfoDto, SystemInfo, TaskInfo, UserDto } from 'sdk/src/main/ets/generated-client/models';
import { getLibraryApi } from 'sdk/src/main/ets/utils/api/library-api';
import { getSessionApi } from 'sdk/src/main/ets/utils/api/session-api';
import { getSystemApi } from 'sdk/src/main/ets/utils/api/system-api';
import { getUserApi } from 'sdk/src/main/ets/utils/api/user-api';
import { ApiClient } from '../../network/ApiClient';

export class ManagementApiService {
  isApiClientInitialized(): boolean {
    return ApiClient.isInitialized()
  }

  async getCurrentUser(): Promise<UserDto> {
    const response: AxiosResponse<UserDto> = await getUserApi(ApiClient.Instance()).getCurrentUser()
    return response.data
  }

  async getSystemInfo(): Promise<SystemInfo> {
    const response: AxiosResponse<SystemInfo> = await getSystemApi(ApiClient.Instance()).getSystemInfo()
    return response.data
  }

  async getItemCounts(): Promise<ItemCounts> {
    const response: AxiosResponse<ItemCounts> = await getLibraryApi(ApiClient.Instance()).getItemCounts({})
    return response.data
  }

  async getUsers(): Promise<UserDto[]> {
    const response: AxiosResponse<UserDto[]> = await getUserApi(ApiClient.Instance()).getUsers({})
    return response.data || []
  }

  async getSessions(): Promise<SessionInfoDto[]> {
    const response: AxiosResponse<SessionInfoDto[]> = await getSessionApi(ApiClient.Instance())
      .getSessions({ activeWithinSeconds: 900 })
    return response.data || []
  }

  async getScheduledTasks(): Promise<TaskInfo[]> {
    const api = ApiClient.Instance()
    const axiosInstance = api.axiosInstance as AxiosInstance
    const response: AxiosResponse<TaskInfo[]> = await axiosInstance.get<TaskInfo[]>(
      `${api.basePath}/ScheduledTasks`,
      { headers: { Authorization: api.authorizationHeader } }
    )
    return response.data || []
  }

  async getActivityLog(): Promise<ActivityLogEntryQueryResult> {
    const api = ApiClient.Instance()
    const axiosInstance = api.axiosInstance as AxiosInstance
    const response: AxiosResponse<ActivityLogEntryQueryResult> =
      await axiosInstance.get<ActivityLogEntryQueryResult>(
        `${api.basePath}/System/ActivityLog/Entries?StartIndex=0&Limit=12`,
        { headers: { Authorization: api.authorizationHeader } }
      )
    return response.data
  }

  async startScheduledTask(taskId: string): Promise<void> {
    const api = ApiClient.Instance()
    const axiosInstance = api.axiosInstance as AxiosInstance
    const response: AxiosResponse<void> = await axiosInstance.post<void>(
      `${api.basePath}/ScheduledTasks/Running/${encodeURIComponent(taskId)}`,
      undefined,
      { headers: { Authorization: api.authorizationHeader } }
    )
    if (response.status < 200 || response.status >= 300) {
      throw Error(`Unexpected scheduled task response: ${response.status}`)
    }
  }
}
