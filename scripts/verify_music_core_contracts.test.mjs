import assert from 'node:assert/strict'
import test from 'node:test'
import {
  REQUIRED_MUSIC_STRING_KEYS,
  validateMusicCoreContracts,
  validateWorkspaceMusicCoreContracts
} from './verify_music_core_contracts.mjs'

const routerPath = 'entry/src/main/ets/common/RouterConsts.ets'
const mediaTabPath = 'entry/src/main/ets/features/home/mediatab/MediaTab.ets'
const indexPath = 'entry/src/main/ets/pages/Index.ets'
const homePath = 'entry/src/main/ets/features/home/HomeScreen.ets'
const splashPath = 'entry/src/main/ets/features/splash/IndexPage.ets'
const audioSessionPath = 'entry/src/main/ets/features/music/MusicAudioSessionManager.ets'
const videoSessionPath = 'entry/src/main/ets/utils/AVSessionManager.ets'
const libraryViewModelPath = 'entry/src/main/ets/features/music/MusicLibraryViewModel.ets'
const libraryUiStatePath = 'entry/src/main/ets/features/music/MusicLibraryUIState.ets'
const libraryPagePath = 'entry/src/main/ets/features/music/MusicLibraryPage.ets'
const librarySkeletonPath = 'entry/src/main/ets/features/music/MusicLibrarySkeleton.ets'
const albumViewModelPath = 'entry/src/main/ets/features/music/MusicAlbumViewModel.ets'
const albumUiStatePath = 'entry/src/main/ets/features/music/MusicAlbumUIState.ets'
const artistViewModelPath = 'entry/src/main/ets/features/music/MusicArtistViewModel.ets'
const artistUiStatePath = 'entry/src/main/ets/features/music/MusicArtistUIState.ets'
const musicApiServicePath = 'entry/src/main/ets/features/music/MusicApiService.ets'
const musicDataErrorPath = 'entry/src/main/ets/features/music/MusicDataError.ets'
const musicErrorTextPath = 'entry/src/main/ets/features/music/MusicErrorText.ets'
const musicModelsPath = 'entry/src/main/ets/features/music/MusicModels.ets'
const musicRepositoryPath = 'entry/src/main/ets/features/music/MusicRepository.ets'
const playbackMachinePath = 'entry/src/main/ets/features/music/MusicPlaybackMachine.ets'
const playbackControllerPath = 'entry/src/main/ets/features/music/MusicPlaybackController.ets'
const playerEnginePath = 'entry/src/main/ets/features/music/MusicPlayerEngine.ets'
const harmonyPlayerEnginePath = 'entry/src/main/ets/features/music/HarmonyMusicPlayerEngine.ets'
const miniPlayerPath = 'entry/src/main/ets/features/music/MusicMiniPlayer.ets'
const apiClientPath = 'entry/src/main/ets/data/ApiClientController.ets'
const connectionPath = 'entry/src/main/ets/features/connect/ConnectionViewModel.ets'
const minePath = 'entry/src/main/ets/features/home/minetab/MineViewModel.ets'
const accountPath = 'entry/src/main/ets/features/setting/account/AccountViewModel.ets'
const webViewPath = 'entry/src/main/ets/webapp/WebViewScreen.ets'
const guardPath = 'entry/src/main/ets/features/music/MusicSessionGuard.ets'
const resourcePaths = [
  'entry/src/main/resources/base/element/string.json',
  'entry/src/main/resources/zh_CN/element/string.json',
  'entry/src/main/resources/en_US/element/string.json'
]

function resourceSource(keys = REQUIRED_MUSIC_STRING_KEYS) {
  return JSON.stringify({
    string: keys.map((name) => ({ name, value: name }))
  })
}

function validCleanupSources() {
  return [
    [guardPath, `
      class MusicSessionGuard {
        private static pending?: Promise<void>
        static stopAndClear(): Promise<void> {
          if (!MusicSessionGuard.pending) {
            MusicSessionGuard.pending = MusicPlaybackController.instance().stopAndClear().finally(() => {
              MusicSessionGuard.pending = undefined
            })
          }
          return MusicSessionGuard.pending
        }
      }
    `],
    [apiClientPath, `
      class ApiClientController {
        async setupServer(): Promise<void> {
          if (this.serverIdentityChanges()) { await MusicSessionGuard.stopAndClear() }
          this.serverDao.getServerByHostname(hostname)
        }
        async setupUser(): Promise<void> {
          if (this.userScopeChanges()) { await MusicSessionGuard.stopAndClear() }
          this.userDao.upsert(serverId, userId, accessToken)
        }
        async logout(): Promise<void> {
          await MusicSessionGuard.stopAndClear()
          preference.setAccessToken('')
          preference.setCurrentUserId('')
          preference.setUserName('')
          preference.setCurrentServerSystemId('')
          webview.WebviewController.removeAllCache(true)
        }
      }
    `],
    [connectionPath, `
      await this.apiClientController.setupServer()
      await this.apiClientController.setupUser()
    `],
    [minePath, `
      class MineViewModel {
        async logout(): Promise<void> {
          await this.apiClientController.logout()
          this.appUIState.isLogin = false
        }
      }
    `],
    [accountPath, `
      class AccountViewModel {
        async changeAccount(): Promise<void> {
          await this.apiClientController.logout()
          HMRouterMgr.to('/IndexPage').pop()
          await this.apiClientController.setupServer()
          await this.apiClientController.setupUser()
        }
        async loginByPwd(): Promise<void> {
          await this.apiClientController.setupUser()
          HMRouterMgr.to('/IndexPage').replace()
        }
      }
    `],
    [splashPath, `
      await this.apiClientController.logout()
      await this.apiClientController.setupServer()
      await this.apiClientController.setupUser()
    `],
    [webViewPath, `
      await this.apiClientController.setupUser()
      this.controller.loadUrl(ApiClient.Instance().basePath)
      this.isLogin = true
    `]
  ]
}

function validMusicCoreFixture() {
  const sources = new Map(validCleanupSources())
  sources.set(routerPath, `
    static readonly MusicLibraryPage = 'MusicLibraryPage'
    static readonly MusicAlbumPage = 'MusicAlbumPage'
    static readonly MusicArtistPage = 'MusicArtistPage'
    static readonly MusicPlayerPage = 'MusicPlayerPage'
  `)
  const page = (route) => `@HMRouter({ pageUrl: RouterConsts.${route}, useNavDst: true })`
  sources.set('entry/src/main/ets/features/music/MusicLibraryPage.ets', `
    ${page('MusicLibraryPage')}
    MusicLibrarySkeleton({})
    onContentScrollStop: () => this.saveOffset(MusicLibrarySection.Albums)
    onContentScrollStop: () => this.saveOffset(MusicLibrarySection.Artists)
    onContentScrollStop: () => this.saveOffset(MusicLibrarySection.Tracks)
    this.scroller.scrollTo({ xOffset: 0, yOffset: this.savedOffset(section), animation: false })
    onRetryLoadMore: () => this.vm.retryLoadMoreCurrent()
    onRetryLoadMore: () => this.vm.retryLoadMoreCurrent()
    onRetryLoadMore: () => this.vm.retryLoadMoreCurrent()
  `)
  sources.set(libraryUiStatePath, `
    @Trace albumsNextStartIndex: number = 0
    @Trace artistsNextStartIndex: number = 0
    @Trace tracksNextStartIndex: number = 0
    @Trace albumsScrollOffset: number = 0
    @Trace artistsScrollOffset: number = 0
    @Trace tracksScrollOffset: number = 0
  `)
  sources.set(albumUiStatePath, '@Trace tracksNextStartIndex: number = 0')
  sources.set(artistUiStatePath, '@Trace albumsNextStartIndex: number = 0')
  sources.set(librarySkeletonPath, `
    MusicLibrarySection.Tracks
    Grid() {}
    Row().height(64)
  `)
  sources.set('entry/src/main/ets/features/music/MusicAlbumPage.ets', `
    ${page('MusicAlbumPage')}
    private compactHeader() { ImageKnifeComponent({}) }
    private playAllButton() {}
    private pageContent() {
      if (false) {} else {
        this.compactHeader(nativeSurface)
        this.playAllButton(nativeSurface)
        Column() { MusicTrackList({}) }.layoutWeight(1).height('100%')
      }
    }
    MusicBrowseErrorCode.NotFound
    HMRouterMgr.pop()
    onRetryLoadMore: () => this.vm.retryLoadMoreTracks()
  `)
  sources.set('entry/src/main/ets/features/music/MusicArtistPage.ets', `
    ${page('MusicArtistPage')}
    MusicBrowseErrorCode.NotFound
    HMRouterMgr.pop()
    Button().onClick(() => this.vm.retryLoadMoreAlbums())
  `)
  sources.set('entry/src/main/ets/features/music/MusicPlayerPage.ets', `
    ${page('MusicPlayerPage')}
    private legacyPlayerContent() { Stack() {} }
    private nativePlayerContent() {
      if (this.useNativeSurface()) {
        AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
      }
    }
  `)
  sources.set(mediaTabPath, `
    private openLibrary(item: MediaItem): void {
      if (item.collectionType === CollectionType.Music) {
        HMRouterMgr.to(RouterConsts.MusicLibraryPage).withParam(item).push()
        return
      }
      if (item.collectionType === CollectionType.Movies || item.collectionType === CollectionType.Tvshows) {
        HMRouterMgr.to(RouterConsts.VideoListPage).withParam(item).push()
        return
      }
      showToast({ message: $r('app.string.media_library_type_unsupported') })
    }
  `)
  sources.set(indexPath, 'MusicMiniPlayer({})')
  sources.set(miniPlayerPath, `
    class MusicMiniPlayer {
      private controller
      private closePlayer(): void {
        this.controller.stopAndClear().catch((): void => {})
      }
      private actions(): void {
        Menu() {
          MenuItem({ content: $r('app.string.music_queue') })
          MenuItem({ content: $r('app.string.music_close_player') })
            .onClick(() => this.closePlayer())
        }
        Button().bindMenu(this.closeMenu(true))
        Button().bindMenu(this.closeMenu(false))
      }
    }
  `)
  sources.set(homePath, 'HomeScreen()')
  sources.set(audioSessionPath, "avSession.createAVSession(context, 'Music', 'audio')")
  sources.set(videoSessionPath, "let type: avSession.AVSessionType = 'video'")
  sources.set(libraryViewModelPath, `
    this.repository.classifyError(error)
    loadAlbums() {
      const startIndex = this.ui.albumsNextStartIndex
      this.ui.albumsNextStartIndex = result.startIndex + result.items.length
    }
    loadArtists() {
      const startIndex = this.ui.artistsNextStartIndex
      this.ui.artistsNextStartIndex = result.startIndex + result.items.length
    }
    loadTracks() {
      const startIndex = this.ui.tracksNextStartIndex
      this.ui.tracksNextStartIndex = result.startIndex + result.items.length
    }
    retryLoadMoreCurrent() {
      this.ui.albumsLoadMoreFailed = false
      this.ui.artistsLoadMoreFailed = false
      this.ui.tracksLoadMoreFailed = false
      this.loadCurrent(MusicLoadMode.More)
    }
  `)
  sources.set(albumViewModelPath, `
    this.repository.classifyError(error)
    loadMoreTracks() {
      const startIndex = this.ui.tracksNextStartIndex
      this.ui.tracksNextStartIndex = result.startIndex + result.items.length
    }
    retryLoadMoreTracks() {
      this.ui.tracksLoadMoreFailed = false
      this.loadMoreTracks()
    }
  `)
  sources.set(artistViewModelPath, `
    this.repository.classifyError(error)
    loadMoreAlbums() {
      const startIndex = this.ui.albumsNextStartIndex
      this.ui.albumsNextStartIndex = result.startIndex + result.items.length
    }
    retryLoadMoreAlbums() {
      this.ui.albumsLoadMoreFailed = false
      this.loadMoreAlbums()
    }
  `)
  sources.set(musicApiServicePath, `
    class MusicApiService {
      createSession() {
        throw new MusicDataError(MusicDataErrorCode.AuthenticationExpired, 'missing account')
      }
    }
  `)
  sources.set(musicDataErrorPath, `
    class MusicDataErrorMapper {
      static from(error) {
        if (status === 401) return MusicDataErrorCode.AuthenticationExpired
        if (status === 403) return MusicDataErrorCode.PermissionDenied
        if (status === 404) return MusicDataErrorCode.NotFound
        if (error.isAxiosError === true) return MusicDataErrorCode.Network
        if (error.name === 'RequiredError') return MusicDataErrorCode.InvalidRequest
        return MusicDataErrorCode.Unknown
      }
    }
  `)
  sources.set(musicErrorTextPath, `
    MusicBrowseErrorCode.AuthenticationExpired
    MusicBrowseErrorCode.PermissionDenied
    MusicBrowseErrorCode.NotFound
    MusicBrowseErrorCode.Offline
  `)
  sources.set(musicRepositoryPath, `
    class MusicRepository {
      classifyError() {
        MusicBrowseErrorCode.AuthenticationExpired
        MusicBrowseErrorCode.PermissionDenied
        MusicBrowseErrorCode.NotFound
        MusicBrowseErrorCode.Offline
        MusicBrowseErrorCode.RequestFailed
      }
      requireId() {
        throw new MusicDataError(MusicDataErrorCode.InvalidRequest, 'invalid id')
      }
    }
  `)
  sources.set(musicModelsPath, `
    enum MusicPlaybackCandidateStatus { None, Preparing, Error }
    interface MusicPlaybackSnapshot {
      candidateItemId?: string
      candidateStatus?: MusicPlaybackCandidateStatus
    }
  `)
  sources.set(playbackMachinePath, `
    class MusicPlaybackMachine {
      private failPreparation() {
        this.preservesActivePlayback()
        MusicPlaybackCandidateStatus.Error
      }
    }
  `)
  sources.set(playbackControllerPath, `
    class MusicPlaybackController {
      private async prepareTarget() {
        await this.engine.prepare()
        const promotionId = this.engine.stagePromotion()
        if (!this.machine.commitPreparation()) {
          this.engine.rollbackPromotion(promotionId)
          return
        }
        this.engine.confirmPromotion(promotionId)
      }
    }
  `)
  sources.set(playerEnginePath, `
    interface MusicPlayerEngine {
      stagePromotion(generation: number): number
      confirmPromotion(promotionId: number): void
      rollbackPromotion(promotionId: number): void
      discard(generation: number): Promise<void>
    }
  `)
  sources.set(harmonyPlayerEnginePath, `
    class HarmonyMusicPlayerEngine {
      private active
      async prepare() { this.candidates.set(1, {}) }
      stagePromotion() {
        const candidate = this.candidates.get(1)
        const previous = this.active
        this.active = candidate
        this.promotions.set(1, { candidate, previous })
        return 1
      }
      confirmPromotion(promotionId) {
        const promotion = this.promotions.get(promotionId)
        this.releaseSlotQuietly(promotion.previous)
      }
      rollbackPromotion(promotionId) {
        const promotion = this.promotions.get(promotionId)
        this.active = promotion.previous
        this.releaseSlotQuietly(promotion.candidate)
      }
    }
  `)
  for (const path of resourcePaths) {
    sources.set(path, resourceSource())
  }
  return { sources }
}

test('workspace music contracts are complete', () => {
  assert.doesNotThrow(() => validateWorkspaceMusicCoreContracts())
})

test('accepts the complete fixture', () => {
  assert.doesNotThrow(() => validateMusicCoreContracts(validMusicCoreFixture()))
})

test('rejects a second mini player owner', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set(indexPath, fixture.sources.get(indexPath) + '\nMusicMiniPlayer({})')
  assert.throws(() => validateMusicCoreContracts(fixture), /exactly one mini player/)
})

test('rejects a mini player close action that leaves playback alive', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set(miniPlayerPath, fixture.sources.get(miniPlayerPath).replace(
    'this.controller.stopAndClear()',
    'this.controller.pause()'
  ))
  assert.throws(() => validateMusicCoreContracts(fixture), /close must stop playback and clear the queue/)
})

test('rejects music importing video queue or page models', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set(
    'entry/src/main/ets/features/music/MusicPlaybackController.ets',
    "import { QueueManager } from '../player/queue/QueueManager'"
  )
  assert.throws(() => validateMusicCoreContracts(fixture), /music must not depend on video queue/)
})

test('rejects replacing the active player during candidate preparation', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set(harmonyPlayerEnginePath, fixture.sources.get(harmonyPlayerEnginePath).replace(
    'async prepare() { this.candidates.set(1, {}) }',
    'async prepare() { this.active = this.candidates.get(1) }'
  ))
  assert.throws(() => validateMusicCoreContracts(fixture), /prepare must not replace/)
})

test('rejects candidate failures that overwrite active playback state', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set(playbackMachinePath, `
    class MusicPlaybackMachine {
      private failPreparation() { this.current.status = MusicPlaybackStatus.Error }
    }
  `)
  assert.throws(() => validateMusicCoreContracts(fixture), /preserve active playback/)
})

test('rejects resource locale drift', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set(resourcePaths[2], resourceSource(REQUIRED_MUSIC_STRING_KEYS.slice(1)))
  assert.throws(() => validateMusicCoreContracts(fixture), /equal key sets/)
})

test('rejects low-version material construction', () => {
  const fixture = validMusicCoreFixture()
  const path = 'entry/src/main/ets/features/music/MusicPlaybackController.ets'
  fixture.sources.set(path, 'AppThemeSurfaceResolver.disabledSystemMaterial()')
  assert.throws(() => validateMusicCoreContracts(fixture), /must not construct disabled system material/)
})

test('rejects converting the video session to audio', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set(videoSessionPath, "let type: avSession.AVSessionType = 'audio'")
  assert.throws(() => validateMusicCoreContracts(fixture), /video AVSession must remain video/)
})

test('rejects routing photos through the video list', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set(mediaTabPath, fixture.sources.get(mediaTabPath).replace(
    'item.collectionType === CollectionType.Movies',
    'item.collectionType === CollectionType.Photos || item.collectionType === CollectionType.Movies'
  ))
  assert.throws(() => validateMusicCoreContracts(fixture), /unsupported photo libraries/)
})

test('rejects a paging retry that remains blocked by the failure flag', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set(albumViewModelPath, `
    this.repository.classifyError(error)
    retryLoadMoreTracks() {
      this.loadMoreTracks()
    }
  `)
  assert.throws(() => validateMusicCoreContracts(fixture), /clear failure before retrying/)
})

test('rejects paging from accumulated item counts', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set(albumViewModelPath, fixture.sources.get(albumViewModelPath).replace(
    'const startIndex = this.ui.tracksNextStartIndex',
    'const startIndex = this.ui.tracks.length'
  ))
  assert.throws(() => validateMusicCoreContracts(fixture), /stored server cursor/)
})

test('rejects advancing a cursor from accumulated item counts', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set(artistViewModelPath, fixture.sources.get(artistViewModelPath).replace(
    'result.startIndex + result.items.length',
    'this.ui.albums.length'
  ))
  assert.throws(() => validateMusicCoreContracts(fixture), /result.startIndex plus returnedCount/)
})

test('rejects a page that routes retry through ordinary load more', () => {
  const fixture = validMusicCoreFixture()
  const path = 'entry/src/main/ets/features/music/MusicLibraryPage.ets'
  fixture.sources.set(path, fixture.sources.get(path).replace(
    'this.vm.retryLoadMoreCurrent()',
    'this.vm.loadMoreCurrent()'
  ))
  assert.throws(() => validateMusicCoreContracts(fixture), /all music library sections/)
})

test('rejects duplicate token-expiry ownership in music', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set(libraryViewModelPath,
    fixture.sources.get(libraryViewModelPath) + '\nTokenExpiredEvent\nemitter.emit(TokenExpiredEvent)')
  assert.throws(() => validateMusicCoreContracts(fixture), /global interceptor/)
})

test('rejects request error serialization in music logs', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set(albumViewModelPath,
    fixture.sources.get(albumViewModelPath) + '\nconsole.error(JSON.stringify(error))')
  assert.throws(() => validateMusicCoreContracts(fixture), /must not serialize request errors/)
})

test('rejects a missing permission-denied browse mapping', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set(musicRepositoryPath,
    fixture.sources.get(musicRepositoryPath).replace('MusicBrowseErrorCode.PermissionDenied', ''))
  assert.throws(() => validateMusicCoreContracts(fixture), /PermissionDenied/)
})

test('rejects a detail 404 state without a back action', () => {
  const fixture = validMusicCoreFixture()
  const albumPagePath = 'entry/src/main/ets/features/music/MusicAlbumPage.ets'
  fixture.sources.set(albumPagePath,
    fixture.sources.get(albumPagePath).replace('HMRouterMgr.pop()', ''))
  assert.throws(() => validateMusicCoreContracts(fixture), /404 states must return/)
})

test('rejects untyped music session initialization failures', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set(musicApiServicePath,
    fixture.sources.get(musicApiServicePath).replace('MusicDataErrorCode.AuthenticationExpired', 'Error'))
  assert.throws(() => validateMusicCoreContracts(fixture), /initialization failures must remain typed/)
})

test('rejects replacing structural music skeletons with a centered spinner', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set(libraryPagePath, fixture.sources.get(libraryPagePath)
    .replace('MusicLibrarySkeleton({})', 'LoadingProgress().width(42)'))
  assert.throws(() => validateMusicCoreContracts(fixture), /structural skeletons/)
})

test('rejects shared music-tab scroll state', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set(libraryUiStatePath, fixture.sources.get(libraryUiStatePath)
    .replace('@Trace artistsScrollOffset: number = 0', ''))
  assert.throws(() => validateMusicCoreContracts(fixture), /independent scroll offsets/)
})

test('rejects an album detail without the compact phone summary', () => {
  const fixture = validMusicCoreFixture()
  fixture.sources.set('entry/src/main/ets/features/music/MusicAlbumPage.ets',
    fixture.sources.get('entry/src/main/ets/features/music/MusicAlbumPage.ets')
      .replace('this.compactHeader(nativeSurface)', 'Blank()'))
  assert.throws(() => validateMusicCoreContracts(fixture), /compact phone summary/)
})
