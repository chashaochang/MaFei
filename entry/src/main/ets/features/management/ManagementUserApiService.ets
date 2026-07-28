import { AxiosInstance, AxiosResponse } from '@ohos/axios';
import { ImageApi } from 'sdk/src/main/ets/generated-client/api/image-api';
import { DeviceInfo } from 'sdk/src/main/ets/generated-client/models/device-info';
import { DeviceInfoQueryResult } from 'sdk/src/main/ets/generated-client/models/device-info-query-result';
import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  NameIdPair,
  NetworkConfiguration,
  ParentalRating,
  UserDto,
  UserPolicy
} from 'sdk/src/main/ets/generated-client/models';
import { getLibraryApi } from 'sdk/src/main/ets/utils/api/library-api';
import { getSessionApi } from 'sdk/src/main/ets/utils/api/session-api';
import { getUserApi } from 'sdk/src/main/ets/utils/api/user-api';
import { ApiClient } from '../../network/ApiClient';

export class ManagementUserApiService {
  isApiClientInitialized(): boolean {
    return ApiClient.isInitialized()
  }

  async getCurrentUser(): Promise<UserDto> {
    const response: AxiosResponse<UserDto> = await getUserApi(ApiClient.Instance()).getCurrentUser()
    return response.data
  }

  async getUsers(): Promise<UserDto[]> {
    const response: AxiosResponse<UserDto[]> = await getUserApi(ApiClient.Instance()).getUsers({})
    return response.data || []
  }

  async getUser(userId: string): Promise<UserDto> {
    const response: AxiosResponse<UserDto> = await getUserApi(ApiClient.Instance())
      .getUserById({ userId: userId })
    return response.data
  }

  async createUser(name: string, password: string): Promise<UserDto> {
    const response: AxiosResponse<UserDto> = await getUserApi(ApiClient.Instance())
      .createUserByName({
        createUserByName: {
          Name: name,
          Password: password.length > 0 ? password : null
        }
      })
    return response.data
  }

  async updateUser(userId: string, user: UserDto): Promise<void> {
    await getUserApi(ApiClient.Instance()).updateUser({ userId: userId, userDto: user })
  }

  async updatePolicy(userId: string, policy: UserPolicy): Promise<void> {
    await getUserApi(ApiClient.Instance()).updateUserPolicy({
      userId: userId,
      userPolicy: policy
    })
  }

  async updatePassword(userId: string, currentPassword: string,
    newPassword: string, reset: boolean): Promise<void> {
    await getUserApi(ApiClient.Instance()).updateUserPassword({
      userId: userId,
      updateUserPassword: {
        CurrentPw: currentPassword.length > 0 ? currentPassword : undefined,
        NewPw: newPassword,
        ResetPassword: reset
      }
    })
  }

  async deleteUser(userId: string): Promise<void> {
    await getUserApi(ApiClient.Instance()).deleteUser({ userId: userId })
  }

  async getMediaFolders(): Promise<BaseItemDto[]> {
    const response: AxiosResponse<BaseItemDtoQueryResult> = await getLibraryApi(ApiClient.Instance())
      .getMediaFolders({})
    return response.data.Items || []
  }

  async getAuthProviders(): Promise<NameIdPair[]> {
    const response: AxiosResponse<NameIdPair[]> = await getSessionApi(ApiClient.Instance())
      .getAuthProviders()
    return response.data || []
  }

  async getPasswordResetProviders(): Promise<NameIdPair[]> {
    const response: AxiosResponse<NameIdPair[]> = await getSessionApi(ApiClient.Instance())
      .getPasswordResetProviders()
    return response.data || []
  }

  async getChannels(): Promise<BaseItemDto[]> {
    const result = await this.authenticatedGet<BaseItemDtoQueryResult>('/Channels')
    return result.Items || []
  }

  async getDevices(): Promise<DeviceInfo[]> {
    const result = await this.authenticatedGet<DeviceInfoQueryResult>('/Devices')
    return result.Items || []
  }

  async getParentalRatings(): Promise<ParentalRating[]> {
    return await this.authenticatedGet<ParentalRating[]>('/Localization/ParentalRatings')
  }

  async getNetworkConfiguration(): Promise<NetworkConfiguration> {
    return await this.authenticatedGet<NetworkConfiguration>('/System/Configuration/network')
  }

  async uploadUserImage(userId: string, bytes: ArrayBuffer, contentType: string): Promise<void> {
    const api = ApiClient.Instance()
    const axiosInstance = api.axiosInstance as AxiosInstance
    await axiosInstance.post<void>(
      `${api.basePath}/UserImage?userId=${encodeURIComponent(userId)}`,
      bytes,
      {
        headers: {
          Authorization: api.authorizationHeader,
          'Content-Type': contentType
        }
      }
    )
  }

  async removeUserImage(userId: string): Promise<void> {
    const api = ApiClient.Instance()
    const imageApi = new ImageApi(api.configuration, undefined, api.axiosInstance as AxiosInstance)
    await imageApi.deleteUserImage({ userId: userId })
  }

  private async authenticatedGet<T>(path: string): Promise<T> {
    const api = ApiClient.Instance()
    const axiosInstance = api.axiosInstance as AxiosInstance
    const response: AxiosResponse<T> = await axiosInstance.get<T>(
      `${api.basePath}${path}`,
      { headers: { Authorization: api.authorizationHeader } }
    )
    return response.data
  }
}
