import { AxiosResponse } from '@ohos/axios'
import { getItemsApi, getUserLibraryApi } from 'sdk'
import { ItemsApiGetItemsRequest } from 'sdk/src/main/ets/generated-client/api/items-api'
import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  UserItemDataDto
} from 'sdk/src/main/ets/generated-client/models'
import { ApiClient } from '../../network/ApiClient'

export interface PhotoService {
  getItems(request: ItemsApiGetItemsRequest): Promise<BaseItemDtoQueryResult>
  getItem(itemId: string, userId: string): Promise<BaseItemDto>
  setFavorite(itemId: string, userId: string, favorite: boolean): Promise<UserItemDataDto>
}

export class PhotoApiService implements PhotoService {
  async getItems(request: ItemsApiGetItemsRequest): Promise<BaseItemDtoQueryResult> {
    const response: AxiosResponse<BaseItemDtoQueryResult> =
      await getItemsApi(ApiClient.Instance()).getItems(request)
    return response.data
  }

  async getItem(itemId: string, userId: string): Promise<BaseItemDto> {
    const response: AxiosResponse<BaseItemDto> = await getUserLibraryApi(ApiClient.Instance()).getItem({
      itemId: itemId,
      userId: userId
    })
    return response.data
  }

  async setFavorite(itemId: string, userId: string, favorite: boolean): Promise<UserItemDataDto> {
    let response: AxiosResponse<UserItemDataDto>
    if (favorite) {
      response = await getUserLibraryApi(ApiClient.Instance()).markFavoriteItem({
        itemId: itemId,
        userId: userId
      })
    } else {
      response = await getUserLibraryApi(ApiClient.Instance()).unmarkFavoriteItem({
        itemId: itemId,
        userId: userId
      })
    }
    return response.data
  }
}
