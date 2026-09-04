import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  BaseItemKind,
  ItemFields,
  ItemSortBy,
  SortOrder
} from 'sdk/src/main/ets/generated-client/models'
import {
  MusicApiService,
  MusicRequestSession,
  MusicService
} from './MusicApiService'
import { MusicDataError, MusicDataErrorCode, MusicDataErrorMapper } from './MusicDataError'
import { MusicMapper } from './MusicMapper'
import {
  MusicAlbum,
  MusicArtist,
  MusicBrowseErrorCode,
  MusicLibrarySection,
  MusicPageResult,
  MusicTrack
} from './MusicModels'
import { MusicPageRequest, MusicQueryPlanner } from './MusicQueryPlanner'

const DEFAULT_PAGE_SIZE: number = 40

export interface MusicBrowseRepositoryPort {
  currentScope(): string
  classifyError(error: Object): MusicBrowseErrorCode
  getAlbums(libraryId: string, startIndex?: number, limit?: number): Promise<MusicPageResult<MusicAlbum>>
  getArtists(libraryId: string, startIndex?: number, limit?: number): Promise<MusicPageResult<MusicArtist>>
  getTracks(libraryId: string, startIndex?: number, limit?: number): Promise<MusicPageResult<MusicTrack>>
  getAlbum(albumId: string): Promise<MusicAlbum>
  getAlbumTracks(albumId: string, startIndex?: number, limit?: number): Promise<MusicPageResult<MusicTrack>>
  getArtist(artistId: string): Promise<MusicArtist>
  getArtistAlbums(artistId: string, startIndex?: number, limit?: number): Promise<MusicPageResult<MusicAlbum>>
  getArtistTracks(artistId: string, limit?: number): Promise<MusicTrack[]>
  setFavorite(itemId: string, favorite: boolean): Promise<boolean>
}

export class MusicRepository implements MusicBrowseRepositoryPort {
  private service: MusicService
  private sessionProvider: () => MusicRequestSession

  constructor(service?: MusicService, sessionProvider?: () => MusicRequestSession) {
    this.service = service || new MusicApiService()
    this.sessionProvider = sessionProvider || (() => this.service.createSession())
  }

  currentScope(): string {
    try {
      return this.sessionProvider().scope
    } catch (_error) {
      return ''
    }
  }

  classifyError(error: Object): MusicBrowseErrorCode {
    const dataError = error instanceof MusicDataError ? error : MusicDataErrorMapper.from(error)
    if (dataError.code === MusicDataErrorCode.AuthenticationExpired) {
      return MusicBrowseErrorCode.AuthenticationExpired
    }
    if (dataError.code === MusicDataErrorCode.PermissionDenied) {
      return MusicBrowseErrorCode.PermissionDenied
    }
    if (dataError.code === MusicDataErrorCode.NotFound) {
      return MusicBrowseErrorCode.NotFound
    }
    return dataError.code === MusicDataErrorCode.Network ? MusicBrowseErrorCode.Offline :
      MusicBrowseErrorCode.RequestFailed
  }

  async getAlbums(libraryId: string, startIndex: number = 0,
    limit: number = DEFAULT_PAGE_SIZE): Promise<MusicPageResult<MusicAlbum>> {
    const requiredLibraryId = this.requireId(libraryId, 'Music library id is required')
    const session = this.requestSession()
    const mapper = new MusicMapper(session.basePath)
    const result = await this.query(session, requiredLibraryId, MusicLibrarySection.Albums, startIndex, limit)
    return mapper.page(result.Items?.map((item: BaseItemDto): MusicAlbum => mapper.album(item)) || [],
      result.StartIndex || 0, result.TotalRecordCount || 0)
  }

  async getArtists(libraryId: string, startIndex: number = 0,
    limit: number = DEFAULT_PAGE_SIZE): Promise<MusicPageResult<MusicArtist>> {
    const requiredLibraryId = this.requireId(libraryId, 'Music library id is required')
    const session = this.requestSession()
    const mapper = new MusicMapper(session.basePath)
    const result = await this.query(session, requiredLibraryId, MusicLibrarySection.Artists, startIndex, limit)
    return mapper.page(result.Items?.map((item: BaseItemDto): MusicArtist => mapper.artist(item)) || [],
      result.StartIndex || 0, result.TotalRecordCount || 0)
  }

  async getTracks(libraryId: string, startIndex: number = 0,
    limit: number = DEFAULT_PAGE_SIZE): Promise<MusicPageResult<MusicTrack>> {
    const requiredLibraryId = this.requireId(libraryId, 'Music library id is required')
    const session = this.requestSession()
    const mapper = new MusicMapper(session.basePath)
    const result = await this.query(session, requiredLibraryId, MusicLibrarySection.Tracks, startIndex, limit)
    return mapper.page(result.Items?.map((item: BaseItemDto): MusicTrack => mapper.track(item)) || [],
      result.StartIndex || 0, result.TotalRecordCount || 0)
  }

  async getAlbum(albumId: string): Promise<MusicAlbum> {
    const requiredAlbumId = this.requireId(albumId, 'Music album id is required')
    const session = this.requestSession()
    const item = await this.service.getItem(session, requiredAlbumId)
    return new MusicMapper(session.basePath).album(item)
  }

  async getAlbumTracks(albumId: string, startIndex: number = 0,
    limit: number = 50): Promise<MusicPageResult<MusicTrack>> {
    const requiredAlbumId = this.requireId(albumId, 'Music album id is required')
    const session = this.requestSession()
    const mapper = new MusicMapper(session.basePath)
    const response = await this.service.getItems(session, {
      userId: session.userId,
      albumIds: [requiredAlbumId],
      recursive: true,
      includeItemTypes: [BaseItemKind.Audio],
      fields: this.fields(),
      enableUserData: true,
      enableImages: true,
      imageTypeLimit: 1,
      sortBy: [ItemSortBy.ParentIndexNumber, ItemSortBy.IndexNumber, ItemSortBy.SortName],
      sortOrder: [SortOrder.Ascending, SortOrder.Ascending, SortOrder.Ascending],
      startIndex: Math.max(0, startIndex),
      limit: Math.max(1, limit),
      enableTotalRecordCount: true
    })
    const items = response.Items?.map((item: BaseItemDto): MusicTrack => mapper.track(item)) || []
    return mapper.page(items, response.StartIndex || 0, response.TotalRecordCount || 0)
  }

  async getArtist(artistId: string): Promise<MusicArtist> {
    const requiredArtistId = this.requireId(artistId, 'Music artist id is required')
    const session = this.requestSession()
    const item = await this.service.getItem(session, requiredArtistId)
    return new MusicMapper(session.basePath).artist(item)
  }

  async getArtistAlbums(artistId: string, startIndex: number = 0,
    limit: number = DEFAULT_PAGE_SIZE): Promise<MusicPageResult<MusicAlbum>> {
    const requiredArtistId = this.requireId(artistId, 'Music artist id is required')
    const session = this.requestSession()
    const mapper = new MusicMapper(session.basePath)
    const response = await this.service.getItems(session, {
      userId: session.userId,
      recursive: true,
      albumArtistIds: [requiredArtistId],
      includeItemTypes: [BaseItemKind.MusicAlbum],
      fields: this.fields(),
      enableUserData: true,
      enableImages: true,
      imageTypeLimit: 1,
      sortBy: [ItemSortBy.SortName],
      sortOrder: [SortOrder.Ascending],
      startIndex: Math.max(0, startIndex),
      limit: Math.max(1, limit),
      enableTotalRecordCount: true
    })
    const items = response.Items?.map((item: BaseItemDto): MusicAlbum => mapper.album(item)) || []
    return mapper.page(items, response.StartIndex || 0, response.TotalRecordCount || 0)
  }

  async getArtistTracks(artistId: string, limit: number = 20): Promise<MusicTrack[]> {
    const requiredArtistId = this.requireId(artistId, 'Music artist id is required')
    const session = this.requestSession()
    const mapper = new MusicMapper(session.basePath)
    const response = await this.service.getItems(session, {
      userId: session.userId,
      recursive: true,
      artistIds: [requiredArtistId],
      includeItemTypes: [BaseItemKind.Audio],
      fields: this.fields(),
      enableUserData: true,
      enableImages: true,
      imageTypeLimit: 1,
      sortBy: [ItemSortBy.PlayCount, ItemSortBy.SortName],
      sortOrder: [SortOrder.Descending, SortOrder.Ascending],
      limit: Math.max(1, limit)
    })
    return response.Items?.map((item: BaseItemDto): MusicTrack => mapper.track(item)) || []
  }

  async setFavorite(itemId: string, favorite: boolean): Promise<boolean> {
    const requiredItemId = this.requireId(itemId, 'Music item id is required')
    const session = this.requestSession()
    return (await this.service.setFavorite(session, requiredItemId, favorite)).IsFavorite === true
  }

  private async query(session: MusicRequestSession, libraryId: string, section: MusicLibrarySection, startIndex: number,
    limit: number): Promise<BaseItemDtoQueryResult> {
    const request: MusicPageRequest = MusicQueryPlanner.normalize({
      libraryId: libraryId,
      section: section,
      startIndex: startIndex,
      limit: limit
    })
    return this.service.getItems(session, {
      userId: session.userId,
      parentId: request.libraryId,
      recursive: true,
      includeItemTypes: MusicQueryPlanner.includeItemTypes(request.section),
      fields: this.fields(),
      enableUserData: true,
      enableImages: true,
      imageTypeLimit: 1,
      sortBy: MusicQueryPlanner.sortBy(request.section),
      sortOrder: MusicQueryPlanner.sortOrder(request.section),
      startIndex: request.startIndex,
      limit: request.limit,
      enableTotalRecordCount: true
    })
  }

  private requestSession(): MusicRequestSession {
    return this.sessionProvider()
  }

  private requireId(value: string, message: string): string {
    const normalized = value.trim()
    if (normalized.length === 0) {
      throw new MusicDataError(MusicDataErrorCode.InvalidRequest, message)
    }
    return normalized
  }

  private fields(): ItemFields[] {
    return [
      ItemFields.ChildCount,
      ItemFields.RecursiveItemCount,
      ItemFields.CumulativeRunTimeTicks,
      ItemFields.Overview,
      ItemFields.ParentId,
      ItemFields.PrimaryImageAspectRatio,
      ItemFields.SortName
    ]
  }
}
