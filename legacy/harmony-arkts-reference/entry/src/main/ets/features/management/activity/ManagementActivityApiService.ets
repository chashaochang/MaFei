import { AxiosInstance, AxiosResponse } from '@ohos/axios';
import {
  ActivityLogEntryQueryResult,
  BaseItemDto,
  UserDto
} from 'sdk/src/main/ets/generated-client/models';
import { getUserApi } from 'sdk/src/main/ets/utils/api/user-api';
import { getUserLibraryApi } from 'sdk/src/main/ets/utils/api/user-library-api';
import { ApiClient } from '../../../network/ApiClient';
import { ManagementActivityRequest } from './ManagementActivityModels';

interface ManagementActivityQueryParams {
  StartIndex: number
  Limit: number
  MinDate?: string
  HasUserId?: boolean
}

export class ManagementActivityApiService {
  async getEntries(query: ManagementActivityRequest): Promise<ActivityLogEntryQueryResult> {
    const params: ManagementActivityQueryParams = {
      StartIndex: query.startIndex,
      Limit: query.limit
    }
    if (query.minDate) params.MinDate = query.minDate
    if (query.hasUserId !== undefined) params.HasUserId = query.hasUserId
    const api = ApiClient.Instance()
    const response: AxiosResponse<ActivityLogEntryQueryResult> = await this.axios()
      .get<ActivityLogEntryQueryResult>(`${api.basePath}/System/ActivityLog/Entries`, {
        params: params,
        headers: { Authorization: api.authorizationHeader }
      })
    return response.data
  }

  async getUsers(): Promise<UserDto[]> {
    const response: AxiosResponse<UserDto[]> = await getUserApi(ApiClient.Instance()).getUsers({})
    return response.data || []
  }

  async getItem(itemId: string): Promise<BaseItemDto> {
    const api = ApiClient.Instance()
    const response: AxiosResponse<BaseItemDto> = await getUserLibraryApi(api).getItem({
      itemId: itemId,
      userId: api.userId
    })
    return response.data
  }

  private axios(): AxiosInstance {
    return ApiClient.Instance().axiosInstance as AxiosInstance
  }
}
