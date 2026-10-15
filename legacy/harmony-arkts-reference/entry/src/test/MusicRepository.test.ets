import { describe, expect, it } from '@ohos/hypium'
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
import {
  MusicRequestSession,
  MusicService
} from '../main/ets/features/music/MusicApiService'
import { MusicDataError, MusicDataErrorCode } from '../main/ets/features/music/MusicDataError'
import { MusicBrowseErrorCode } from '../main/ets/features/music/MusicModels'
import { MusicRepository } from '../main/ets/features/music/MusicRepository'

class FakeMusicService implements MusicService {
  lastItemsRequest?: ItemsApiGetItemsRequest
  lastSessionScope: string = ''
  createSessionCount: number = 0
  favoriteResult: boolean = true

  createSession(): MusicRequestSession {
    this.createSessionCount += 1
    return session()
  }

  async getItems(requestSession: MusicRequestSession,
    request: ItemsApiGetItemsRequest): Promise<BaseItemDtoQueryResult> {
    this.lastSessionScope = requestSession.scope
    this.lastItemsRequest = request
    return {
      Items: [{ Id: 'track-1', Name: 'Track' }],
      StartIndex: request.startIndex || 0,
      TotalRecordCount: 3
    }
  }

  async getItem(requestSession: MusicRequestSession, itemId: string): Promise<BaseItemDto> {
    this.lastSessionScope = requestSession.scope
    return { Id: itemId, Name: 'Item' }
  }

  async setFavorite(requestSession: MusicRequestSession, _itemId: string,
    _favorite: boolean): Promise<UserItemDataDto> {
    this.lastSessionScope = requestSession.scope
    return { IsFavorite: this.favoriteResult }
  }
}

class TestMusicHttpResponse {
  status: number = 0
}

class TestMusicHttpError {
  isAxiosError?: boolean
  response?: TestMusicHttpResponse
}

function session(): MusicRequestSession {
  return new MusicRequestSession(
    'https://music.example|user-1',
    'https://music.example',
    'user-1',
    'server-1',
    {} as ItemsApi,
    {} as UserLibraryApi
  )
}

export default function musicRepositoryTest() {
  describe('MusicRepository', () => {
    it('queriesAlbumTracksByAlbumIdWithPaging', 0, async () => {
      const service = new FakeMusicService()
      const repository = new MusicRepository(service, session)

      const page = await repository.getAlbumTracks('album-1', 1, 2)

      expect(service.lastItemsRequest?.albumIds?.[0]).assertEqual('album-1')
      expect(service.lastItemsRequest?.startIndex).assertEqual(1)
      expect(service.lastItemsRequest?.limit).assertEqual(2)
      expect(service.lastSessionScope).assertEqual('https://music.example|user-1')
      expect(page.items.length).assertEqual(1)
      expect(page.hasMore).assertTrue()
    })

    it('usesStableRequestScopeAndFavoriteResult', 0, async () => {
      const service = new FakeMusicService()
      const repository = new MusicRepository(service)

      expect(repository.currentScope()).assertEqual('https://music.example|user-1')
      expect(await repository.setFavorite('album-1', true)).assertTrue()
      expect(service.createSessionCount).assertEqual(2)
      expect(service.lastSessionScope).assertEqual('https://music.example|user-1')
    })

    it('classifiesHttpAndOfflineErrors', 0, () => {
      const repository = new MusicRepository(new FakeMusicService(), session)

      const unauthorized = new TestMusicHttpError()
      unauthorized.response = new TestMusicHttpResponse()
      unauthorized.response.status = 401
      const forbidden = new TestMusicHttpError()
      forbidden.response = new TestMusicHttpResponse()
      forbidden.response.status = 403
      const missing = new TestMusicHttpError()
      missing.response = new TestMusicHttpResponse()
      missing.response.status = 404
      const offline = new TestMusicHttpError()
      offline.isAxiosError = true

      expect(repository.classifyError(unauthorized as Object)).assertEqual(MusicBrowseErrorCode.AuthenticationExpired)
      expect(repository.classifyError(forbidden as Object)).assertEqual(MusicBrowseErrorCode.PermissionDenied)
      expect(repository.classifyError(missing as Object)).assertEqual(MusicBrowseErrorCode.NotFound)
      expect(repository.classifyError(offline as Object)).assertEqual(MusicBrowseErrorCode.Offline)
    })

    it('rejectsEmptyIdsBeforeCreatingASession', 0, async () => {
      const service = new FakeMusicService()
      const repository = new MusicRepository(service)
      let errorCode: MusicDataErrorCode = MusicDataErrorCode.Unknown

      try {
        await repository.getAlbumTracks('   ')
      } catch (error) {
        errorCode = (error as MusicDataError).code
      }

      expect(errorCode).assertEqual(MusicDataErrorCode.InvalidRequest)
      expect(service.createSessionCount).assertEqual(0)
    })
  })
}
