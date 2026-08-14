import { readFileSync, readdirSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { validateMusicSessionCleanup } from './verify_music_session_cleanup.mjs'

const ROUTER_CONSTS = 'entry/src/main/ets/common/RouterConsts.ets'
const MEDIA_TAB = 'entry/src/main/ets/features/home/mediatab/MediaTab.ets'
const INDEX = 'entry/src/main/ets/pages/Index.ets'
const HOME_SCREEN = 'entry/src/main/ets/features/home/HomeScreen.ets'
const INDEX_PAGE = 'entry/src/main/ets/features/splash/IndexPage.ets'
const MUSIC_AUDIO_SESSION = 'entry/src/main/ets/features/music/MusicAudioSessionManager.ets'
const VIDEO_AV_SESSION = 'entry/src/main/ets/utils/AVSessionManager.ets'
const MUSIC_LIBRARY_VIEW_MODEL = 'entry/src/main/ets/features/music/MusicLibraryViewModel.ets'
const MUSIC_LIBRARY_UI_STATE = 'entry/src/main/ets/features/music/MusicLibraryUIState.ets'
const MUSIC_LIBRARY_PAGE = 'entry/src/main/ets/features/music/MusicLibraryPage.ets'
const MUSIC_LIBRARY_SKELETON = 'entry/src/main/ets/features/music/MusicLibrarySkeleton.ets'
const MUSIC_ALBUM_VIEW_MODEL = 'entry/src/main/ets/features/music/MusicAlbumViewModel.ets'
const MUSIC_ALBUM_UI_STATE = 'entry/src/main/ets/features/music/MusicAlbumUIState.ets'
const MUSIC_ARTIST_VIEW_MODEL = 'entry/src/main/ets/features/music/MusicArtistViewModel.ets'
const MUSIC_ARTIST_UI_STATE = 'entry/src/main/ets/features/music/MusicArtistUIState.ets'
const MUSIC_API_SERVICE = 'entry/src/main/ets/features/music/MusicApiService.ets'
const MUSIC_DATA_ERROR = 'entry/src/main/ets/features/music/MusicDataError.ets'
const MUSIC_ERROR_TEXT = 'entry/src/main/ets/features/music/MusicErrorText.ets'
const MUSIC_MODELS = 'entry/src/main/ets/features/music/MusicModels.ets'
const MUSIC_REPOSITORY = 'entry/src/main/ets/features/music/MusicRepository.ets'
const MUSIC_PLAYBACK_MACHINE = 'entry/src/main/ets/features/music/MusicPlaybackMachine.ets'
const MUSIC_PLAYBACK_CONTROLLER = 'entry/src/main/ets/features/music/MusicPlaybackController.ets'
const MUSIC_PLAYER_ENGINE = 'entry/src/main/ets/features/music/MusicPlayerEngine.ets'
const HARMONY_MUSIC_PLAYER_ENGINE = 'entry/src/main/ets/features/music/HarmonyMusicPlayerEngine.ets'
const MUSIC_MINI_PLAYER = 'entry/src/main/ets/features/music/MusicMiniPlayer.ets'
const API_CLIENT_CONTROLLER = 'entry/src/main/ets/data/ApiClientController.ets'
const CONNECTION_VIEW_MODEL = 'entry/src/main/ets/features/connect/ConnectionViewModel.ets'
const MINE_VIEW_MODEL = 'entry/src/main/ets/features/home/minetab/MineViewModel.ets'
const ACCOUNT_VIEW_MODEL = 'entry/src/main/ets/features/setting/account/AccountViewModel.ets'
const WEB_VIEW_SCREEN = 'entry/src/main/ets/webapp/WebViewScreen.ets'
const MUSIC_SESSION_GUARD = 'entry/src/main/ets/features/music/MusicSessionGuard.ets'

const RESOURCE_PATHS = [
  'entry/src/main/resources/base/element/string.json',
  'entry/src/main/resources/zh_CN/element/string.json',
  'entry/src/main/resources/en_US/element/string.json'
]

const ROUTES = [
  ['MusicLibraryPage', 'entry/src/main/ets/features/music/MusicLibraryPage.ets'],
  ['MusicAlbumPage', 'entry/src/main/ets/features/music/MusicAlbumPage.ets'],
  ['MusicArtistPage', 'entry/src/main/ets/features/music/MusicArtistPage.ets'],
  ['MusicPlayerPage', 'entry/src/main/ets/features/music/MusicPlayerPage.ets']
]

export const REQUIRED_MUSIC_STRING_KEYS = [
  'media_library_type_unsupported',
  'music_albums',
  'music_artists',
  'music_back',
  'music_browse_error_permission_denied',
  'music_cancel',
  'music_clear',
  'music_clear_queue',
  'music_clear_queue_message',
  'music_close_player',
  'music_empty_albums',
  'music_empty_artists',
  'music_empty_tracks',
  'music_error_authentication_expired',
  'music_error_no_playable_source',
  'music_error_not_found',
  'music_error_offline',
  'music_error_permission_denied',
  'music_error_system_player',
  'music_error_transcoding_failed',
  'music_favorite',
  'music_favorite_failed',
  'music_load_failed',
  'music_load_more_failed',
  'music_loading_more',
  'music_next',
  'music_no_overview',
  'music_nothing_playing',
  'music_pause',
  'music_play',
  'music_play_all',
  'music_playback_failed',
  'music_popular_tracks',
  'music_previous',
  'music_queue',
  'music_refresh',
  'music_remove',
  'music_repeat_all',
  'music_repeat_off',
  'music_repeat_one',
  'music_retry',
  'music_skip',
  'music_track_unit',
  'music_tracks',
  'music_unfavorite'
]

const REQUIRED_SOURCE_PATHS = [
  ROUTER_CONSTS,
  MEDIA_TAB,
  INDEX,
  HOME_SCREEN,
  INDEX_PAGE,
  MUSIC_AUDIO_SESSION,
  VIDEO_AV_SESSION,
  MUSIC_LIBRARY_VIEW_MODEL,
  MUSIC_LIBRARY_UI_STATE,
  MUSIC_LIBRARY_PAGE,
  MUSIC_LIBRARY_SKELETON,
  MUSIC_ALBUM_VIEW_MODEL,
  MUSIC_ALBUM_UI_STATE,
  MUSIC_ARTIST_VIEW_MODEL,
  MUSIC_ARTIST_UI_STATE,
  MUSIC_API_SERVICE,
  MUSIC_DATA_ERROR,
  MUSIC_ERROR_TEXT,
  MUSIC_MODELS,
  MUSIC_REPOSITORY,
  MUSIC_PLAYBACK_MACHINE,
  MUSIC_PLAYBACK_CONTROLLER,
  MUSIC_PLAYER_ENGINE,
  HARMONY_MUSIC_PLAYER_ENGINE,
  MUSIC_MINI_PLAYER,
  API_CLIENT_CONTROLLER,
  CONNECTION_VIEW_MODEL,
  MINE_VIEW_MODEL,
  ACCOUNT_VIEW_MODEL,
  WEB_VIEW_SCREEN,
  MUSIC_SESSION_GUARD
]

function requiredSource(sources, path) {
  const source = sources.get(path)
  if (source === undefined) {
    throw new Error('missing source: ' + path)
  }
  return source
}

function countMatches(source, pattern) {
  return Array.from(source.matchAll(pattern)).length
}

function bracedBlock(source, openingBrace) {
  let depth = 0
  for (let index = openingBrace; index < source.length; index += 1) {
    if (source[index] === '{') {
      depth += 1
    } else if (source[index] === '}') {
      depth -= 1
      if (depth === 0) {
        return source.slice(openingBrace + 1, index)
      }
    }
  }
  throw new Error('unterminated block')
}

function methodBlock(source, methodName) {
  const signature = new RegExp(
    '\\b(?:private\\s+|public\\s+|protected\\s+)?(?:static\\s+)?(?:async\\s+)?' +
      methodName + '\\s*\\([^)]*\\)\\s*(?::\\s*[^\\{]+)?\\s*\\{'
  )
  const match = signature.exec(source)
  if (!match) {
    throw new Error('missing method: ' + methodName)
  }
  return bracedBlock(source, source.indexOf('{', match.index))
}

function sorted(values) {
  return Array.from(values).sort()
}

function assertEqualSets(left, right, message) {
  if (JSON.stringify(sorted(left)) !== JSON.stringify(sorted(right))) {
    throw new Error(message)
  }
}

function parseResourceKeys(source, path) {
  let parsed
  try {
    parsed = JSON.parse(source)
  } catch (error) {
    throw new Error('invalid resource json: ' + path + ': ' + error.message)
  }
  if (!Array.isArray(parsed.string)) {
    throw new Error('resource file has no string array: ' + path)
  }
  const keys = parsed.string.map((entry) => entry.name)
  if (new Set(keys).size !== keys.length) {
    throw new Error('duplicate resource key: ' + path)
  }
  return new Set(keys)
}

function musicSources(sources) {
  return Array.from(sources.entries()).filter(([path]) =>
    path.startsWith('entry/src/main/ets/features/music/') && path.endsWith('.ets'))
}

function validateRoutes(sources) {
  const routerConsts = requiredSource(sources, ROUTER_CONSTS)
  for (const [route, path] of ROUTES) {
    const declaration = new RegExp('static\\s+readonly\\s+' + route + '\\s*=')
    if (countMatches(routerConsts, new RegExp(declaration.source, 'g')) !== 1) {
      throw new Error('missing or duplicate music route constant: ' + route)
    }
    const page = requiredSource(sources, path)
    const destination = new RegExp(
      '@HMRouter\\s*\\(\\s*\\{[\\s\\S]*?pageUrl\\s*:\\s*RouterConsts\\.' + route
    )
    if (!destination.test(page)) {
      throw new Error('missing music route destination: ' + route)
    }
  }
}

function validateMediaRouting(sources) {
  const openLibrary = methodBlock(requiredSource(sources, MEDIA_TAB), 'openLibrary')
  if (!/CollectionType\.Music/.test(openLibrary) ||
    !/HMRouterMgr\.to\s*\(\s*RouterConsts\.MusicLibraryPage\s*\)/.test(openLibrary)) {
    throw new Error('music libraries must route to MusicLibraryPage')
  }
  if (!/CollectionType\.Movies/.test(openLibrary) || !/CollectionType\.Tvshows/.test(openLibrary) ||
    !/RouterConsts\.VideoListPage/.test(openLibrary)) {
    throw new Error('video library routing must remain explicit')
  }
  if (/CollectionType\.(?:Photos|Homevideos)[\s\S]{0,240}RouterConsts\.VideoListPage/.test(openLibrary)) {
    throw new Error('unsupported photo libraries must not route to the video list')
  }
  if (!/media_library_type_unsupported/.test(openLibrary)) {
    throw new Error('unsupported library types must show a typed prompt')
  }
}

function validateMiniPlayerOwnership(sources) {
  const ownerCount = countMatches(requiredSource(sources, INDEX), /\bMusicMiniPlayer\s*\(/g)
  if (ownerCount !== 1) {
    throw new Error('there must be exactly one mini player owner')
  }
  for (const path of [HOME_SCREEN, INDEX_PAGE]) {
    if (/\bMusicMiniPlayer\b/.test(requiredSource(sources, path))) {
      throw new Error('mini player must not be owned by ' + path)
    }
  }
}

function validateMiniPlayerCloseAction(sources) {
  const miniPlayer = requiredSource(sources, MUSIC_MINI_PLAYER)
  const closePlayer = methodBlock(miniPlayer, 'closePlayer')
  if (!/this\.controller\.stopAndClear\s*\(\s*\)/.test(closePlayer)) {
    throw new Error('mini player close must stop playback and clear the queue')
  }
  if (!/MenuItem\s*\(\s*\{[\s\S]*app\.string\.music_close_player/.test(miniPlayer) ||
    !/MenuItem\s*\(\s*\{[\s\S]*app\.string\.music_queue/.test(miniPlayer) ||
    countMatches(miniPlayer, /\.bindMenu\s*\(\s*this\.closeMenu\s*\(/g) < 2 ||
    !/\.onClick\s*\(\s*\(\)\s*=>\s*this\.closePlayer\s*\(\s*\)\s*\)/.test(miniPlayer) ||
    /sys\.symbol\.xmark/.test(miniPlayer)) {
    throw new Error('mini player must expose close from the trailing system-material menu')
  }
}

function validateSessionSeparation(sources) {
  const audio = requiredSource(sources, MUSIC_AUDIO_SESSION)
  const video = requiredSource(sources, VIDEO_AV_SESSION)
  if (!/createAVSession\s*\([\s\S]*?['"]audio['"]\s*\)/.test(audio)) {
    throw new Error('music must use an audio AVSession')
  }
  if (!/AVSessionType\s*=\s*['"]video['"]/.test(video) ||
    /AVSessionType\s*=\s*['"]audio['"]/.test(video)) {
    throw new Error('video AVSession must remain video')
  }
}

function validateMusicDependencies(sources) {
  for (const [path, source] of musicSources(sources)) {
    if (/PlayerPageViewModel|player\/queue\/QueueManager|\bQueueManager\b/.test(source)) {
      throw new Error('music must not depend on video queue or page models: ' + path)
    }
    if (/@kit\.UIDesignKit|UIDesignKit/.test(source)) {
      throw new Error('music must not import UIDesignKit directly: ' + path)
    }
    if (/disabledSystemMaterial\s*\(/.test(source)) {
      throw new Error('API 20-25 music paths must not construct disabled system material: ' + path)
    }
    if (/systemMaterial\s*:\s*[^\n]+\?/.test(source)) {
      throw new Error('music Sheet material must use separate native and legacy branches: ' + path)
    }
    if (/url=\$\{source\.url\}/.test(source)) {
      throw new Error('authenticated music URLs must not be logged: ' + path)
    }
    if (/TokenExpiredEvent|emitter\.emit\s*\(\s*['"]?TokenExpired/.test(source)) {
      throw new Error('music must leave token-expiry event ownership to the global interceptor: ' + path)
    }
    if (/JSON\.stringify\s*\(\s*error\s*\)/.test(source)) {
      throw new Error('music must not serialize request errors into logs: ' + path)
    }
  }

  const player = requiredSource(sources, 'entry/src/main/ets/features/music/MusicPlayerPage.ets')
  const legacyPlayer = methodBlock(player, 'legacyPlayerContent')
  if (/AppThemeSurfaceResolver\.material|\.systemMaterial\s*\(/.test(legacyPlayer)) {
    throw new Error('legacy music player must not construct API 26 material')
  }
}

function validateBrowsePresentation(sources) {
  const uiState = requiredSource(sources, MUSIC_LIBRARY_UI_STATE)
  const page = requiredSource(sources, MUSIC_LIBRARY_PAGE)
  const skeleton = requiredSource(sources, MUSIC_LIBRARY_SKELETON)
  const albumPage = requiredSource(sources, ROUTES[1][1])
  for (const name of ['albumsScrollOffset', 'artistsScrollOffset', 'tracksScrollOffset']) {
    if (!new RegExp('@Trace\\s+' + name + '\\s*:\\s*number').test(uiState)) {
      throw new Error('music library tabs must own independent scroll offsets: ' + name)
    }
  }
  if (!/MusicLibrarySkeleton/.test(page) || /LoadingProgress\s*\(\s*\)\s*\.width\(42\)/.test(page)) {
    throw new Error('music library first load must use structural skeletons')
  }
  if (!/MusicLibrarySection\.Tracks/.test(skeleton) || !/Grid\s*\(/.test(skeleton) ||
    !/height\(64\)/.test(skeleton)) {
    throw new Error('music library skeletons must mirror grid and track-row structures')
  }
  if (countMatches(page, /onContentScrollStop:\s*\(\)\s*=>\s*this\.saveOffset/g) !== 3 ||
    !/scrollTo\s*\(\s*\{[\s\S]*?yOffset:\s*this\.savedOffset/.test(page)) {
    throw new Error('music library tabs must save and restore their own scroll offsets')
  }
  const compactHeader = methodBlock(albumPage, 'compactHeader')
  const pageContent = methodBlock(albumPage, 'pageContent')
  if (!/ImageKnifeComponent/.test(compactHeader) ||
    !/this\.compactHeader\s*\(\s*nativeSurface\s*\)/.test(pageContent) ||
    !/this\.playAllButton\s*\(\s*nativeSurface\s*\)/.test(pageContent) ||
    countMatches(pageContent, /MusicTrackList\s*\(/g) < 1 ||
    !/MusicTrackList\s*\([\s\S]*?\)[\s\S]*?\.layoutWeight\s*\(\s*1\s*\)/.test(pageContent)) {
    throw new Error('music album detail must preserve the compact phone summary, controls, and scrolling tracks')
  }
}

function validateBrowseErrors(sources) {
  const dataError = requiredSource(sources, MUSIC_DATA_ERROR)
  for (const [status, code] of [
    [401, 'AuthenticationExpired'],
    [403, 'PermissionDenied'],
    [404, 'NotFound']
  ]) {
    if (!new RegExp(`status\\s*===\\s*${status}[\\s\\S]{0,220}MusicDataErrorCode\\.${code}`).test(dataError)) {
      throw new Error(`music data errors must map HTTP ${status} to ${code}`)
    }
  }
  if (!/isAxiosError\s*===\s*true[\s\S]{0,220}MusicDataErrorCode\.Network/.test(dataError) ||
    !/MusicDataErrorCode\.InvalidRequest/.test(dataError) ||
    !/MusicDataErrorCode\.Unknown/.test(dataError)) {
    throw new Error('music data errors must distinguish network, invalid, and unknown failures')
  }

  const apiService = requiredSource(sources, MUSIC_API_SERVICE)
  const createSession = methodBlock(apiService, 'createSession')
  if (!/MusicDataErrorCode\.AuthenticationExpired/.test(createSession)) {
    throw new Error('music session initialization failures must remain typed')
  }

  const repository = requiredSource(sources, MUSIC_REPOSITORY)
  const classifyError = methodBlock(repository, 'classifyError')
  for (const code of ['AuthenticationExpired', 'PermissionDenied', 'NotFound', 'Offline', 'RequestFailed']) {
    if (!new RegExp(`MusicBrowseErrorCode\\.${code}`).test(classifyError)) {
      throw new Error('music repository must classify browse error: ' + code)
    }
  }
  const requireId = methodBlock(repository, 'requireId')
  if (!/MusicDataErrorCode\.InvalidRequest/.test(requireId)) {
    throw new Error('music repository must reject empty ids with a typed invalid request')
  }

  for (const path of [MUSIC_LIBRARY_VIEW_MODEL, MUSIC_ALBUM_VIEW_MODEL, MUSIC_ARTIST_VIEW_MODEL]) {
    if (!/this\.repository\.classifyError\s*\(/.test(requiredSource(sources, path))) {
      throw new Error('music browse ViewModels must retain typed errors: ' + path)
    }
  }

  const errorText = requiredSource(sources, MUSIC_ERROR_TEXT)
  for (const code of ['AuthenticationExpired', 'PermissionDenied', 'NotFound', 'Offline']) {
    if (!new RegExp(`MusicBrowseErrorCode\\.${code}`).test(errorText)) {
      throw new Error('music browse error text must handle: ' + code)
    }
  }

  for (const [, path] of ROUTES.slice(1, 3)) {
    const page = requiredSource(sources, path)
    if (!/MusicBrowseErrorCode\.NotFound/.test(page) || !/HMRouterMgr\.pop\s*\(\s*\)/.test(page)) {
      throw new Error('music detail 404 states must return to the previous page: ' + path)
    }
  }
}

function validateLoadMoreRetry(sources) {
  const libraryViewModel = requiredSource(sources, MUSIC_LIBRARY_VIEW_MODEL)
  const libraryRetry = methodBlock(libraryViewModel, 'retryLoadMoreCurrent')
  for (const failureFlag of [
    'albumsLoadMoreFailed',
    'artistsLoadMoreFailed',
    'tracksLoadMoreFailed'
  ]) {
    if (!new RegExp(`this\\.ui\\.${failureFlag}\\s*=\\s*false`).test(libraryRetry)) {
      throw new Error('music library load-more retry must clear every section failure flag')
    }
  }
  if (!/this\.loadCurrent\s*\(\s*MusicLoadMode\.More\s*\)/.test(libraryRetry)) {
    throw new Error('music library load-more retry must issue another paging request')
  }

  const albumRetry = methodBlock(requiredSource(sources, MUSIC_ALBUM_VIEW_MODEL), 'retryLoadMoreTracks')
  if (!/this\.ui\.tracksLoadMoreFailed\s*=\s*false/.test(albumRetry) ||
    !/this\.loadMoreTracks\s*\(\s*\)/.test(albumRetry)) {
    throw new Error('music album load-more retry must clear failure before retrying')
  }

  const artistRetry = methodBlock(requiredSource(sources, MUSIC_ARTIST_VIEW_MODEL), 'retryLoadMoreAlbums')
  if (!/this\.ui\.albumsLoadMoreFailed\s*=\s*false/.test(artistRetry) ||
    !/this\.loadMoreAlbums\s*\(\s*\)/.test(artistRetry)) {
    throw new Error('music artist load-more retry must clear failure before retrying')
  }

  const libraryPage = requiredSource(sources, ROUTES[0][1])
  if (countMatches(libraryPage, /onRetryLoadMore\s*:\s*\(\)\s*=>\s*this\.vm\.retryLoadMoreCurrent\s*\(\s*\)/g) !== 3) {
    throw new Error('all music library sections must use the explicit paging retry')
  }
  const albumPage = requiredSource(sources, ROUTES[1][1])
  if (!/onRetryLoadMore\s*:\s*\(\)\s*=>\s*this\.vm\.retryLoadMoreTracks\s*\(\s*\)/.test(albumPage)) {
    throw new Error('music album page must use the explicit paging retry')
  }
  const artistPage = requiredSource(sources, ROUTES[2][1])
  if (!/onClick\s*\(\s*\(\)\s*=>\s*this\.vm\.retryLoadMoreAlbums\s*\(\s*\)\s*\)/.test(artistPage)) {
    throw new Error('music artist page must use the explicit paging retry')
  }
}

function validatePaginationCursors(sources) {
  const libraryUiState = requiredSource(sources, MUSIC_LIBRARY_UI_STATE)
  for (const field of ['albumsNextStartIndex', 'artistsNextStartIndex', 'tracksNextStartIndex']) {
    if (!new RegExp(`@Trace\\s+${field}\\s*:\\s*number\\s*=\\s*0`).test(libraryUiState)) {
      throw new Error('music library pagination must store the server cursor: ' + field)
    }
  }

  const cursorContracts = [
    [MUSIC_LIBRARY_VIEW_MODEL, 'loadAlbums', 'albumsNextStartIndex'],
    [MUSIC_LIBRARY_VIEW_MODEL, 'loadArtists', 'artistsNextStartIndex'],
    [MUSIC_LIBRARY_VIEW_MODEL, 'loadTracks', 'tracksNextStartIndex'],
    [MUSIC_ALBUM_VIEW_MODEL, 'loadMoreTracks', 'tracksNextStartIndex'],
    [MUSIC_ARTIST_VIEW_MODEL, 'loadMoreAlbums', 'albumsNextStartIndex']
  ]
  for (const [path, methodName, field] of cursorContracts) {
    const method = methodBlock(requiredSource(sources, path), methodName)
    if (countMatches(method, new RegExp(`this\\.ui\\.${field}`, 'g')) < 2) {
      throw new Error('music pagination must request from the stored server cursor: ' + path)
    }
    if (!new RegExp(
      `this\\.ui\\.${field}\\s*=\\s*result\\.startIndex\\s*\\+\\s*result\\.items\\.length`
    ).test(method)) {
      throw new Error('music pagination must advance from result.startIndex plus returnedCount: ' + path)
    }
  }

  const albumUiState = requiredSource(sources, MUSIC_ALBUM_UI_STATE)
  if (!/@Trace\s+tracksNextStartIndex\s*:\s*number\s*=\s*0/.test(albumUiState)) {
    throw new Error('music album pagination must store its server cursor')
  }
  const artistUiState = requiredSource(sources, MUSIC_ARTIST_UI_STATE)
  if (!/@Trace\s+albumsNextStartIndex\s*:\s*number\s*=\s*0/.test(artistUiState)) {
    throw new Error('music artist pagination must store its server cursor')
  }
}

function validateTwoPhasePlayback(sources) {
  const engineContract = requiredSource(sources, MUSIC_PLAYER_ENGINE)
  const engine = requiredSource(sources, HARMONY_MUSIC_PLAYER_ENGINE)
  const machine = requiredSource(sources, MUSIC_PLAYBACK_MACHINE)
  const controller = requiredSource(sources, MUSIC_PLAYBACK_CONTROLLER)
  const models = requiredSource(sources, MUSIC_MODELS)

  if (!/stagePromotion\s*\(\s*generation\s*:\s*number\s*\)\s*:\s*number/.test(engineContract) ||
    !/confirmPromotion\s*\(\s*promotionId\s*:\s*number\s*\)\s*:\s*void/.test(engineContract) ||
    !/rollbackPromotion\s*\(\s*promotionId\s*:\s*number\s*\)\s*:\s*void/.test(engineContract) ||
    !/discard\s*\(\s*generation\s*:\s*number\s*\)\s*:\s*Promise<void>/.test(engineContract)) {
    throw new Error('music engine must expose transactional promotion and discard operations')
  }
  const prepare = methodBlock(engine, 'prepare')
  if (/this\.active\s*=|this\.active\?\.player|releaseSlotQuietly\s*\(\s*this\.active/.test(prepare)) {
    throw new Error('music prepare must not replace or release the active player')
  }
  const stagePromotion = methodBlock(engine, 'stagePromotion')
  const confirmPromotion = methodBlock(engine, 'confirmPromotion')
  const rollbackPromotion = methodBlock(engine, 'rollbackPromotion')
  if (!/this\.active\s*=\s*candidate/.test(stagePromotion) ||
    /releaseSlotQuietly\s*\(\s*previous\s*\)/.test(stagePromotion) ||
    !/releaseSlotQuietly\s*\(\s*promotion\.previous\s*\)/.test(confirmPromotion) ||
    !/this\.active\s*=\s*promotion\.previous/.test(rollbackPromotion) ||
    !/releaseSlotQuietly\s*\(\s*promotion\.candidate\s*\)/.test(rollbackPromotion)) {
    throw new Error('music promotion must stage synchronously and support confirm or rollback')
  }
  const prepareTarget = methodBlock(controller, 'prepareTarget')
  if (!/await\s+this\.engine\.prepare/.test(prepareTarget) ||
    !/this\.engine\.stagePromotion/.test(prepareTarget) ||
    !/this\.machine\.commitPreparation/.test(prepareTarget) ||
    !/this\.engine\.confirmPromotion/.test(prepareTarget) ||
    !/this\.engine\.rollbackPromotion/.test(prepareTarget) ||
    prepareTarget.indexOf('this.engine.stagePromotion') > prepareTarget.indexOf('this.machine.commitPreparation') ||
    prepareTarget.indexOf('this.machine.commitPreparation') > prepareTarget.indexOf('this.engine.confirmPromotion')) {
    throw new Error('music controller must atomically stage, commit, then confirm or rollback promotion')
  }
  const atomicPromotion = prepareTarget.slice(
    prepareTarget.indexOf('this.engine.stagePromotion'),
    prepareTarget.indexOf('this.engine.confirmPromotion') + 'this.engine.confirmPromotion'.length)
  if (/await\s/.test(atomicPromotion)) {
    throw new Error('music promotion and state commit must not yield between stage and confirmation')
  }
  if (!/enum\s+MusicPlaybackCandidateStatus/.test(models) ||
    !/candidateItemId\??\s*:\s*string/.test(models) ||
    !/candidateStatus\??\s*:\s*MusicPlaybackCandidateStatus/.test(models)) {
    throw new Error('music playback snapshot must expose candidate preparation state')
  }
  const failPreparation = methodBlock(machine, 'failPreparation')
  if (!/preservesActivePlayback\s*\(\s*\)/.test(failPreparation) ||
    !/MusicPlaybackCandidateStatus\.Error/.test(failPreparation)) {
    throw new Error('candidate preparation failure must preserve active playback and expose candidate error')
  }
}

function validateResources(sources) {
  const allResourceSets = RESOURCE_PATHS.map((path) => parseResourceKeys(requiredSource(sources, path), path))
  const resourceSets = allResourceSets.map((keys) => new Set(Array.from(keys).filter((key) =>
    key.startsWith('music_') || key === 'media_library_type_unsupported')))
  for (let index = 1; index < resourceSets.length; index += 1) {
    assertEqualSets(resourceSets[0], resourceSets[index], 'music resource locales must have equal key sets')
  }
  for (const key of REQUIRED_MUSIC_STRING_KEYS) {
    if (!resourceSets[0].has(key)) {
      throw new Error('missing music resource key: ' + key)
    }
  }

  const referenced = new Set()
  for (const [, source] of musicSources(sources)) {
    for (const match of source.matchAll(/app\.string\.(music_[A-Za-z0-9_]+)/g)) {
      referenced.add(match[1])
    }
  }
  for (const key of referenced) {
    if (!resourceSets[0].has(key)) {
      throw new Error('music source references missing resource key: ' + key)
    }
  }
}

export function validateMusicCoreContracts(fixture) {
  const sources = fixture.sources
  validateRoutes(sources)
  validateMediaRouting(sources)
  validateMiniPlayerOwnership(sources)
  validateMiniPlayerCloseAction(sources)
  validateMusicSessionCleanup(sources)
  validateSessionSeparation(sources)
  validateMusicDependencies(sources)
  validateBrowsePresentation(sources)
  validateBrowseErrors(sources)
  validateLoadMoreRetry(sources)
  validatePaginationCursors(sources)
  validateTwoPhasePlayback(sources)
  validateResources(sources)
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

function readMusicSources(root, sources) {
  const directory = resolve(root, 'entry/src/main/ets/features/music')
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const absolute = join(directory, entry.name)
    if (entry.isDirectory()) {
      continue
    }
    if (entry.isFile() && entry.name.endsWith('.ets')) {
      sources.set(relative(root, absolute), readFileSync(absolute, 'utf8'))
    }
  }
}

export function workspaceMusicCoreFixture(root = defaultWorkspaceRoot()) {
  const sources = new Map()
  for (const path of REQUIRED_SOURCE_PATHS.concat(RESOURCE_PATHS)) {
    sources.set(path, readFileSync(resolve(root, path), 'utf8'))
  }
  for (const [, path] of ROUTES) {
    if (!sources.has(path)) {
      sources.set(path, readFileSync(resolve(root, path), 'utf8'))
    }
  }
  readMusicSources(root, sources)
  return { sources }
}

export function validateWorkspaceMusicCoreContracts(root = defaultWorkspaceRoot()) {
  validateMusicCoreContracts(workspaceMusicCoreFixture(root))
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspaceMusicCoreContracts()
  console.log('Music core contracts verified')
}
