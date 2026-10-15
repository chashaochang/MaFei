import { AxiosResponse } from '@ohos/axios'
import { getItemsApi, getUserLibraryApi } from 'sdk'
import { Api } from 'sdk/src/main/ets/api'
import { ClientInfo } from 'sdk/src/main/ets/models/client-info'
import { DeviceInfo } from 'sdk/src/main/ets/models/device-info'
import {
  ItemsApi,
  ItemsApiGetItemsRequest
} from 'sdk/src/main/ets/generated-client/api/items-api'
import { UserLibraryApi } from 'sdk/src/main/ets/generated-client/api/user-library-api'
import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  UserItemDataDto
} from 'sdk/src/main/ets/generated-client/models'
import { AppPreference } from '../../data/AppPreference'
import { ApiClient } from '../../network/ApiClient'
import {
  MusicDataError,
  MusicDataErrorCode,
  MusicDataErrorMapper
} from './MusicDataError'

export class MusicRequestSession {
  scope: string
  basePath: string
  userId: string
  serverId: string
  itemsApi: ItemsApi
  userLibraryApi: UserLibraryApi

  constructor(scope: string, basePath: string, userId: string, serverId: string,
    itemsApi: ItemsApi, userLibraryApi: UserLibraryApi) {
    this.scope = scope
    this.basePath = basePath
    this.userId = userId
    this.serverId = serverId
    this.itemsApi = itemsApi
    this.userLibraryApi = userLibraryApi
  }
}

export interface MusicService {
  createSession(): MusicRequestSession
  getItems(session: MusicRequestSession, request: ItemsApiGetItemsRequest): Promise<BaseItemDtoQueryResult>
  getItem(session: MusicRequestSession, itemId: string): Promise<BaseItemDto>
  setFavorite(session: MusicRequestSession, itemId: string, favorite: boolean): Promise<UserItemDataDto>
}

export class MusicApiService implements MusicService {
  createSession(): MusicRequestSession {
    if (!ApiClient.isInitialized()) {
      throw new MusicDataError(
        MusicDataErrorCode.AuthenticationExpired,
        'Music requires an initialized account'
      )
    }
    const api = ApiClient.Instance()
    const basePath = this.normalizeBasePath(String(api.basePath || ''))
    const userId = String(api.userId || '')
    const clientInfo: ClientInfo = {
      name: String(api.clientInfo.name || ''),
      version: String(api.clientInfo.version || '')
    }
    const deviceInfo: DeviceInfo = {
      id: String(api.deviceInfo.id || ''),
      name: String(api.deviceInfo.name || '')
    }
    const snapshotApi = new Api(
      basePath, clientInfo, deviceInfo, String(api.accessToken || ''), api.axiosInstance)
    snapshotApi.userId = userId
    return new MusicRequestSession(
      `${basePath}|${userId}`,
      basePath,
      userId,
      this.currentServerId(basePath),
      getItemsApi(snapshotApi),
      getUserLibraryApi(snapshotApi)
    )
  }

  async getItems(session: MusicRequestSession,
    request: ItemsApiGetItemsRequest): Promise<BaseItemDtoQueryResult> {
    try {
      const response: AxiosResponse<BaseItemDtoQueryResult> = await session.itemsApi.getItems(request)
      return response.data
    } catch (error) {
      throw MusicDataErrorMapper.from(error as Object)
    }
  }

  async getItem(session: MusicRequestSession, itemId: string): Promise<BaseItemDto> {
    try {
      const response: AxiosResponse<BaseItemDto> = await session.userLibraryApi.getItem({
        itemId: itemId,
        userId: session.userId
      })
      return response.data
    } catch (error) {
      throw MusicDataErrorMapper.from(error as Object)
    }
  }

  async setFavorite(session: MusicRequestSession, itemId: string,
    favorite: boolean): Promise<UserItemDataDto> {
    try {
      let response: AxiosResponse<UserItemDataDto>
      if (favorite) {
        response = await session.userLibraryApi.markFavoriteItem({
          itemId: itemId,
          userId: session.userId
        })
      } else {
        response = await session.userLibraryApi.unmarkFavoriteItem({
          itemId: itemId,
          userId: session.userId
        })
      }
      return response.data
    } catch (error) {
      throw MusicDataErrorMapper.from(error as Object)
    }
  }

  private currentServerId(fallback: string): string {
    try {
      const serverId = AppPreference.getInstance().getCurrentServerSystemId()
      return serverId.length > 0 ? serverId : fallback
    } catch (_error) {
      return fallback
    }
  }

  private normalizeBasePath(value: string): string {
    return value.endsWith('/') ? value.slice(0, -1) : value
  }
}
