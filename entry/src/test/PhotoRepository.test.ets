import { describe, expect, it } from '@ohos/hypium'
import { ItemsApiGetItemsRequest } from 'sdk/src/main/ets/generated-client/api/items-api'
import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  BaseItemKind,
  ImageType,
  ItemSortBy,
  UserItemDataDto
} from 'sdk/src/main/ets/generated-client/models'
import { PhotoService } from '../main/ets/features/photo/PhotoApiService'
import {
  PhotoBrowseErrorCode,
  PhotoRequestSession
} from '../main/ets/features/photo/PhotoModels'
import { PhotoRepository } from '../main/ets/features/photo/PhotoRepository'

class FakePhotoService implements PhotoService {
  itemsResult: BaseItemDtoQueryResult = { Items: [], StartIndex: 0, TotalRecordCount: 0 }
  itemResult: BaseItemDto = { Id: 'photo-1', Type: BaseItemKind.Photo }
  favoriteResult: UserItemDataDto = { IsFavorite: false }
  lastItemsRequest?: ItemsApiGetItemsRequest
  lastItemId: string = ''
  lastUserId: string = ''

  async getItems(request: ItemsApiGetItemsRequest): Promise<BaseItemDtoQueryResult> {
    this.lastItemsRequest = request
    return this.itemsResult
  }

  async getItem(itemId: string, userId: string): Promise<BaseItemDto> {
    this.lastItemId = itemId
    this.lastUserId = userId
    return this.itemResult
  }

  async setFavorite(_itemId: string, _userId: string, _favorite: boolean): Promise<UserItemDataDto> {
    return this.favoriteResult
  }
}

class TestPhotoHttpResponse {
  status: number = 0
}

class TestPhotoHttpError extends Error {
  isAxiosError?: boolean
  response?: TestPhotoHttpResponse
}

function session(): PhotoRequestSession {
  return {
    scope: 'https://photos.example|user-1',
    basePath: 'https://photos.example',
    userId: 'user-1'
  }
}

export default function photoRepositoryTest() {
  describe('PhotoRepository', () => {
    it('queriesRecursivePhotosAndAdvancesByRawServerCount', 0, async () => {
      const service = new FakePhotoService()
      service.itemsResult = {
        Items: [
          { Id: 'photo-1', Type: BaseItemKind.Photo },
          { Id: 'movie-1', Type: BaseItemKind.Movie },
          { Id: '', Type: BaseItemKind.Photo }
        ],
        StartIndex: 20,
        TotalRecordCount: 30
      }
      const repository = new PhotoRepository(service, session)

      const page = await repository.getPhotos(' library-1 ', 20, 60)
      const request = service.lastItemsRequest

      expect(request?.parentId).assertEqual('library-1')
      expect(request?.recursive).assertTrue()
      expect(request?.includeItemTypes?.length).assertEqual(1)
      expect(request?.includeItemTypes?.[0]).assertEqual(BaseItemKind.Photo)
      expect(request?.enableImageTypes?.length).assertEqual(1)
      expect(request?.enableImageTypes?.[0]).assertEqual(ImageType.Primary)
      expect(request?.sortBy?.[0]).assertEqual(ItemSortBy.PremiereDate)
      expect(request?.sortBy?.[1]).assertEqual(ItemSortBy.DateCreated)
      expect(request?.sortBy?.[2]).assertEqual(ItemSortBy.SortName)
      expect(page.items.length).assertEqual(1)
      expect(page.nextStartIndex).assertEqual(23)
      expect(page.hasMore).assertTrue()
    })

    it('queriesDirectChildAlbumsAndFolders', 0, async () => {
      const service = new FakePhotoService()
      service.itemsResult = {
        Items: [
          { Id: 'album-1', Type: BaseItemKind.PhotoAlbum },
          { Id: 'folder-1', Type: BaseItemKind.Folder }
        ],
        StartIndex: 0,
        TotalRecordCount: 2
      }
      const repository = new PhotoRepository(service, session)

      const page = await repository.getAlbums('library-1')
      const request = service.lastItemsRequest

      expect(request?.recursive).assertFalse()
      expect(request?.includeItemTypes?.[0]).assertEqual(BaseItemKind.PhotoAlbum)
      expect(request?.includeItemTypes?.[1]).assertEqual(BaseItemKind.Folder)
      expect(page.items.length).assertEqual(2)
      expect(page.nextStartIndex).assertEqual(2)
    })

    it('loadsFullPhotoDetailsAndRejectsMismatchedKinds', 0, async () => {
      const service = new FakePhotoService()
      service.itemResult = {
        Id: 'photo-1',
        Type: BaseItemKind.Photo,
        CameraMake: 'HUAWEI',
        ExposureTime: 0.01
      }
      const repository = new PhotoRepository(service, session)

      const photo = await repository.getPhoto(' photo-1 ')
      expect(service.lastItemId).assertEqual('photo-1')
      expect(service.lastUserId).assertEqual('user-1')
      expect(photo.exif.cameraMake).assertEqual('HUAWEI')
      expect(photo.exif.exposureTime).assertEqual(0.01)

      service.itemResult = { Id: 'movie-1', Type: BaseItemKind.Movie }
      let rejected = false
      try {
        await repository.getPhoto('movie-1')
      } catch (_error) {
        rejected = true
      }
      expect(rejected).assertTrue()
    })

    it('usesServerFavoriteResultAndClassifiesRequestErrors', 0, async () => {
      const service = new FakePhotoService()
      const repository = new PhotoRepository(service, session)
      service.favoriteResult = { IsFavorite: false }

      expect(await repository.setFavorite('photo-1', true)).assertFalse()

      const unauthorized = new TestPhotoHttpError()
      unauthorized.response = new TestPhotoHttpResponse()
      unauthorized.response.status = 401
      const forbidden = new TestPhotoHttpError()
      forbidden.response = new TestPhotoHttpResponse()
      forbidden.response.status = 403
      const missing = new TestPhotoHttpError()
      missing.response = new TestPhotoHttpResponse()
      missing.response.status = 404
      const offline = new TestPhotoHttpError()
      offline.isAxiosError = true

      expect(repository.classifyError(unauthorized as Object))
        .assertEqual(PhotoBrowseErrorCode.AuthenticationExpired)
      expect(repository.classifyError(forbidden as Object)).assertEqual(PhotoBrowseErrorCode.PermissionDenied)
      expect(repository.classifyError(missing as Object)).assertEqual(PhotoBrowseErrorCode.NotFound)
      expect(repository.classifyError(offline as Object)).assertEqual(PhotoBrowseErrorCode.Offline)
    })
  })
}
