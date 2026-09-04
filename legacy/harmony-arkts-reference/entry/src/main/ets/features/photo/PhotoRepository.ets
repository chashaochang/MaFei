import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  BaseItemKind,
  ImageType,
  ItemFields
} from 'sdk/src/main/ets/generated-client/models'
import { ApiClient } from '../../network/ApiClient'
import { PhotoApiService, PhotoService } from './PhotoApiService'
import { PhotoMapper } from './PhotoMapper'
import {
  PhotoAlbumItem,
  PhotoBrowseErrorCode,
  PhotoItem,
  PhotoLibrarySection,
  PhotoPageResult,
  PhotoRequestSession
} from './PhotoModels'
import { PhotoPageRequest, PhotoQueryPlanner } from './PhotoQueryPlanner'

const DEFAULT_PHOTO_PAGE_SIZE: number = 60

export interface PhotoBrowseRepositoryPort {
  currentScope(): string
  classifyError(error: Object): PhotoBrowseErrorCode
  getPhotos(parentId: string, startIndex?: number, limit?: number): Promise<PhotoPageResult<PhotoItem>>
  getAlbums(parentId: string, startIndex?: number, limit?: number): Promise<PhotoPageResult<PhotoAlbumItem>>
  getPhoto(photoId: string): Promise<PhotoItem>
  getAlbum(albumId: string): Promise<PhotoAlbumItem>
  setFavorite(photoId: string, favorite: boolean): Promise<boolean>
}

export class PhotoRepository implements PhotoBrowseRepositoryPort {
  private service: PhotoService
  private sessionProvider: () => PhotoRequestSession

  constructor(service?: PhotoService, sessionProvider?: () => PhotoRequestSession) {
    this.service = service || new PhotoApiService()
    this.sessionProvider = sessionProvider || (() => PhotoRepository.apiSession())
  }

  currentScope(): string {
    try {
      return this.sessionProvider().scope
    } catch (_error) {
      return ''
    }
  }

  classifyError(error: Object): PhotoBrowseErrorCode {
    const requestError = error as PhotoRequestError
    const status = requestError.response?.status
    if (status === 401) {
      return PhotoBrowseErrorCode.AuthenticationExpired
    }
    if (status === 403) {
      return PhotoBrowseErrorCode.PermissionDenied
    }
    if (status === 404) {
      return PhotoBrowseErrorCode.NotFound
    }
    return requestError.isAxiosError === true && requestError.response === undefined ?
      PhotoBrowseErrorCode.Offline : PhotoBrowseErrorCode.RequestFailed
  }

  async getPhotos(parentId: string, startIndex: number = 0,
    limit: number = DEFAULT_PHOTO_PAGE_SIZE): Promise<PhotoPageResult<PhotoItem>> {
    const session = this.requestSession()
    const mapper = new PhotoMapper(session.basePath)
    const result = await this.query(session, parentId, PhotoLibrarySection.Photos, startIndex, limit)
    const sourceItems = result.Items || []
    const items = sourceItems
      .filter((item: BaseItemDto): boolean => item.Id !== undefined && item.Id.length > 0 &&
        item.Type === BaseItemKind.Photo)
      .map((item: BaseItemDto): PhotoItem => mapper.photo(item))
    const resultStartIndex = result.StartIndex ?? Math.max(0, startIndex)
    const totalCount = result.TotalRecordCount ?? resultStartIndex + sourceItems.length
    return mapper.page(items, resultStartIndex, sourceItems.length, totalCount)
  }

  async getAlbums(parentId: string, startIndex: number = 0,
    limit: number = DEFAULT_PHOTO_PAGE_SIZE): Promise<PhotoPageResult<PhotoAlbumItem>> {
    const session = this.requestSession()
    const mapper = new PhotoMapper(session.basePath)
    const result = await this.query(session, parentId, PhotoLibrarySection.Albums, startIndex, limit)
    const sourceItems = result.Items || []
    const items = sourceItems
      .filter((item: BaseItemDto): boolean => item.Id !== undefined && item.Id.length > 0 &&
        (item.Type === BaseItemKind.PhotoAlbum || item.Type === BaseItemKind.Folder))
      .map((item: BaseItemDto): PhotoAlbumItem => mapper.album(item))
    const resultStartIndex = result.StartIndex ?? Math.max(0, startIndex)
    const totalCount = result.TotalRecordCount ?? resultStartIndex + sourceItems.length
    return mapper.page(items, resultStartIndex, sourceItems.length, totalCount)
  }

  async getPhoto(photoId: string): Promise<PhotoItem> {
    const session = this.requestSession()
    const item = await this.service.getItem(this.requiredId(photoId, 'Photo'), session.userId)
    return new PhotoMapper(session.basePath).photo(item)
  }

  async getAlbum(albumId: string): Promise<PhotoAlbumItem> {
    const session = this.requestSession()
    const item = await this.service.getItem(this.requiredId(albumId, 'Photo album'), session.userId)
    return new PhotoMapper(session.basePath).album(item)
  }

  async setFavorite(photoId: string, favorite: boolean): Promise<boolean> {
    const session = this.requestSession()
    const itemId = this.requiredId(photoId, 'Photo')
    return (await this.service.setFavorite(itemId, session.userId, favorite)).IsFavorite === true
  }

  private async query(session: PhotoRequestSession, parentId: string, section: PhotoLibrarySection,
    startIndex: number, limit: number): Promise<BaseItemDtoQueryResult> {
    const request: PhotoPageRequest = PhotoQueryPlanner.normalize({
      parentId: parentId,
      section: section,
      startIndex: startIndex,
      limit: limit
    })
    if (request.parentId.length === 0) {
      throw new Error('Photo parent id is required')
    }
    return this.service.getItems({
      userId: session.userId,
      parentId: request.parentId,
      recursive: PhotoQueryPlanner.recursive(request.section),
      includeItemTypes: PhotoQueryPlanner.includeItemTypes(request.section),
      fields: this.fields(),
      enableUserData: true,
      enableImages: true,
      enableImageTypes: [ImageType.Primary],
      imageTypeLimit: 1,
      sortBy: PhotoQueryPlanner.sortBy(request.section),
      sortOrder: PhotoQueryPlanner.sortOrder(request.section),
      startIndex: request.startIndex,
      limit: request.limit,
      enableTotalRecordCount: true
    })
  }

  private requestSession(): PhotoRequestSession {
    return this.sessionProvider()
  }

  private requiredId(value: string, label: string): string {
    const normalized = value.trim()
    if (normalized.length === 0) {
      throw new Error(`${label} id is required`)
    }
    return normalized
  }

  private static apiSession(): PhotoRequestSession {
    if (!ApiClient.isInitialized()) {
      throw new Error('ApiClient is not initialized')
    }
    const api = ApiClient.Instance()
    const basePath: string = String(api.basePath || '')
    const userId: string = String(api.userId || '')
    return {
      scope: `${basePath}|${userId}`,
      basePath: basePath,
      userId: userId
    }
  }

  private fields(): ItemFields[] {
    return [
      ItemFields.CanDownload,
      ItemFields.ChildCount,
      ItemFields.DateCreated,
      ItemFields.Height,
      ItemFields.Overview,
      ItemFields.ParentId,
      ItemFields.Path,
      ItemFields.PrimaryImageAspectRatio,
      ItemFields.RecursiveItemCount,
      ItemFields.SortName,
      ItemFields.Width
    ]
  }
}

interface PhotoHttpResponse {
  status: number
}

interface PhotoRequestError extends Error {
  isAxiosError?: boolean
  response?: PhotoHttpResponse
}
