import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const catalogPaths = Object.freeze([
  'entry/src/main/ets/features/home/HomeScreen.ets',
  'entry/src/main/ets/features/home/HomeScreenUIState.ets',
  'entry/src/main/ets/features/home/HomeScreenViewModel.ets',
  'entry/src/main/ets/features/home/hometab/HomeInitialSkeleton.ets',
  'entry/src/main/ets/features/home/hometab/HomeLatestMediaSection.ets',
  'entry/src/main/ets/features/home/hometab/HomeLibraryChipSelector.ets',
  'entry/src/main/ets/features/home/hometab/HomeTab.ets',
  'entry/src/main/ets/features/home/hometab/HomeUIState.ets',
  'entry/src/main/ets/features/home/hometab/HomeViewModel.ets',
  'entry/src/main/ets/features/home/mediatab/MediaTab.ets',
  'entry/src/main/ets/features/home/mediatab/MediaUIState.ets',
  'entry/src/main/ets/features/home/mediatab/MediaViewModel.ets',
  'entry/src/main/ets/features/search/SearchPage.ets',
  'entry/src/main/ets/features/search/SearchUIState.ets',
  'entry/src/main/ets/features/search/SearchViewModel.ets',
  'entry/src/main/ets/features/videolist/VideoListPage.ets',
  'entry/src/main/ets/features/videolist/VideoListUIState.ets',
  'entry/src/main/ets/features/videolist/VideoListViewModel.ets',
  'entry/src/main/ets/features/videodetail/VideoDetailPage.ets',
  'entry/src/main/ets/features/videodetail/VideoDetailUIState.ets',
  'entry/src/main/ets/features/videodetail/VideoDetailViewModel.ets',
  'entry/src/main/ets/features/videodetail/components/AudioSubtitleSelector.ets',
  'entry/src/main/ets/features/videodetail/components/VideoInfoDetail.ets',
  'entry/src/main/ets/features/seasondetail/SeasonDetailPage.ets',
  'entry/src/main/ets/features/seasondetail/SeasonDetailUIState.ets',
  'entry/src/main/ets/features/seasondetail/SeasonDetailViewModel.ets',
  'entry/src/main/ets/features/cast/CastDetailPage.ets',
  'entry/src/main/ets/features/cast/CastDetailUIState.ets',
  'entry/src/main/ets/features/cast/CastDetailViewModel.ets',
  'entry/src/main/ets/features/favorite/FavoriteListPage.ets',
  'entry/src/main/ets/features/favorite/FavoriteListUIState.ets',
  'entry/src/main/ets/features/favorite/FavoriteListViewModel.ets',
  'entry/src/main/ets/features/home/minetab/MineHistoryRepository.ets',
  'entry/src/main/ets/component/CastView.ets',
  'entry/src/main/ets/component/BaseItemCard.ets'
])

const playerPaths = Object.freeze([
  'entry/src/main/ets/player/queue/QueueManager.ets',
  'entry/src/main/ets/player/source/MediaSourceResolver.ets',
  'entry/src/main/ets/player/TrackSelectionHelper.ets',
  'entry/src/main/ets/player/PlayerEngineResolver.ets',
  'entry/src/main/ets/player/ui/PlayerMenus.ets',
  'entry/src/main/ets/player/ui/PlaybackInfoCard.ets',
  'entry/src/main/ets/features/player/PlayerPage.ets',
  'entry/src/main/ets/features/player/PlayerPageUIState.ets',
  'entry/src/main/ets/features/player/PlayerPageViewModel.ets',
  'entry/src/main/ets/features/player/AVPlayerView.ets',
  'entry/src/main/ets/features/player/MPVPlayerView.ets',
  'entry/src/main/ets/features/player/VideoFramePreviewHelper.ets'
])

const searchPaths = Object.freeze([
  'entry/src/main/ets/features/search/SearchPage.ets',
  'entry/src/main/ets/features/search/SearchUIState.ets',
  'entry/src/main/ets/features/search/SearchViewModel.ets'
])

export const legacyJellyfinDataPaths = Object.freeze([
  'entry/src/main/ets/data/RdbConstants.ets',
  'entry/src/main/ets/data/Rdb.ets',
  'entry/src/main/ets/data/ServerDao.ets',
  'entry/src/main/ets/data/ServerEntity.ets',
  'entry/src/main/ets/data/ServerUser.ets',
  'entry/src/main/ets/data/ApiClientController.ets'
])

export const legacyJellyfinDatabaseBaseline = Object.freeze({
  name: 'jellyFin.db',
  version: 1,
  serverColumns: Object.freeze([
    'id',
    'hostname',
    'server_name',
    'version',
    'last_used_timestamp'
  ]),
  userColumns: Object.freeze([
    'id',
    'server_id',
    'user_id',
    'access_token',
    'user_name',
    'last_login_timestamp'
  ])
})

export const legacyJellyfinSourceSha256 = Object.freeze({
  'entry/src/main/ets/data/RdbConstants.ets':
    '25a900795da9a30937b52ba7fc8fe917bc7add6b63c0f1cb9fde8fa503034da2',
  'entry/src/main/ets/data/Rdb.ets':
    '8fb682c90fbc86f58123d61befdf7fb570ca7ac99b08aa8212d7c50da80df12f',
  'entry/src/main/ets/data/ServerDao.ets':
    '3f55ab511c64f931e0b5d27c9937eb023c28c7ff2adeaf42aa35371e24fce6b9',
  'entry/src/main/ets/data/ServerEntity.ets':
    'd4bb85177814f89dcc14fc688198d17994d9ee709b449b81b239b61cafd54a8a',
  'entry/src/main/ets/data/ServerUser.ets':
    '616296cb0487134705f26cbb0d44b1fc736bddd325982f6941d7d756f45f1858',
  'entry/src/main/ets/data/ApiClientController.ets':
    'df1331dc2ec86f74d8592ee76e6ae76fc04c5d930b1ce7dcfae81481f2d471ee'
})

export const mediaProviderBoundaryPaths = Object.freeze({
  catalog: catalogPaths,
  player: playerPaths
})

export const sharedMediaPaths = Object.freeze([...catalogPaths, ...playerPaths])

export const mediaProviderMigrationStages = Object.freeze({
  baseline: Object.freeze([]),
  search: searchPaths,
  catalog: catalogPaths,
  player: Object.freeze([...catalogPaths, ...playerPaths])
})

function deepFreeze(value) {
  if (value && typeof value === 'object' && !Object.isFrozen(value)) {
    Object.freeze(value)
    for (const child of Object.values(value)) {
      deepFreeze(child)
    }
  }
  return value
}

export const knownJellyfinBaseline = deepFreeze({
  imports: {
    'entry/src/main/ets/features/home/HomeScreen.ets': {
      'sdk/src/main/ets/generated-client/models': ['CollectionType']
    },
    'entry/src/main/ets/features/home/HomeScreenViewModel.ets': {
      sdk: ['ImageUrlsApi', 'getItemsApi', 'getLibraryApi', 'getUserViewsApi'],
      'sdk/src/main/ets/generated-client/models': [
        'BaseItemDto',
        'BaseItemDtoQueryResult',
        'BaseItemKind',
        'CollectionType',
        'ImageType',
        'ItemCounts'
      ],
      '../../network/ApiClient': ['ApiClient']
    },
    'entry/src/main/ets/features/home/hometab/HomeLatestMediaSection.ets': {
      'sdk/src/main/ets/generated-client/models': ['BaseItemKind']
    },
    'entry/src/main/ets/features/home/hometab/HomeTab.ets': {
      'sdk/src/main/ets/generated-client/models': ['BaseItemKind']
    },
    'entry/src/main/ets/features/home/hometab/HomeUIState.ets': {
      'sdk/src/main/ets/generated-client/models': ['BaseItemKind', 'CollectionType']
    },
    'entry/src/main/ets/features/home/hometab/HomeViewModel.ets': {
      sdk: ['ImageUrlsApi', 'getItemsApi', 'getTvShowsApi', 'getUserLibraryApi', 'getUserViewsApi'],
      'sdk/src/main/ets/generated-client/models': [
        'BaseItemDto',
        'BaseItemDtoQueryResult',
        'BaseItemKind',
        'CollectionType',
        'ImageType',
        'ItemFields',
        'RecommendationDto'
      ],
      '../../../network/ApiClient': ['ApiClient']
    },
    'entry/src/main/ets/features/home/mediatab/MediaTab.ets': {
      'sdk/src/main/ets/generated-client/models': ['CollectionType']
    },
    'entry/src/main/ets/features/home/mediatab/MediaViewModel.ets': {
      sdk: ['ImageUrlsApi', 'getItemsApi', 'getLibraryApi', 'getUserViewsApi'],
      'sdk/src/main/ets/generated-client/models': [
        'BaseItemDto',
        'BaseItemDtoQueryResult',
        'BaseItemKind',
        'CollectionType',
        'ImageType',
        'ItemCounts'
      ],
      '../../../network/ApiClient': ['ApiClient']
    },
    'entry/src/main/ets/features/search/SearchViewModel.ets': {
      sdk: ['getItemsApi'],
      'sdk/src/main/ets/generated-client/models': ['BaseItemDtoQueryResult'],
      '../../network/ApiClient': ['ApiClient']
    },
    'entry/src/main/ets/features/videolist/VideoListPage.ets': {
      'sdk/src/main/ets/generated-client/models': ['BaseItemKind']
    },
    'entry/src/main/ets/features/videolist/VideoListViewModel.ets': {
      sdk: ['getItemsApi'],
      'sdk/src/main/ets/generated-client/models': [
        'BaseItemDto',
        'BaseItemDtoQueryResult',
        'BaseItemKind',
        'ItemSortBy'
      ],
      '../../network/ApiClient': ['ApiClient']
    },
    'entry/src/main/ets/features/videodetail/VideoDetailPage.ets': {
      'sdk/src/main/ets/generated-client/models': [
        'ExternalUrl',
        'MediaSourceInfo',
        'MediaStream',
        'MediaStreamType'
      ]
    },
    'entry/src/main/ets/features/videodetail/VideoDetailUIState.ets': {
      'sdk/src/main/ets/generated-client/models': [
        'BaseItemPerson',
        'ExternalUrl',
        'MediaSourceInfo',
        'MediaStream'
      ]
    },
    'entry/src/main/ets/features/videodetail/VideoDetailViewModel.ets': {
      sdk: ['ImageUrlsApi', 'getPlaystateApi', 'getTvShowsApi', 'getUserLibraryApi'],
      'sdk/src/main/ets/generated-client/models': [
        'BaseItemDto',
        'BaseItemDtoQueryResult',
        'BaseItemKind',
        'ImageType',
        'ItemFields'
      ],
      '../../network/ApiClient': ['ApiClient']
    },
    'entry/src/main/ets/features/videodetail/components/AudioSubtitleSelector.ets': {
      'sdk/src/main/ets/generated-client/models': ['MediaSourceInfo', 'MediaStream', 'MediaStreamType']
    },
    'entry/src/main/ets/features/videodetail/components/VideoInfoDetail.ets': {
      'sdk/src/main/ets/generated-client/models': ['MediaSourceInfo', 'MediaStream', 'MediaStreamType']
    },
    'entry/src/main/ets/features/seasondetail/SeasonDetailUIState.ets': {
      'sdk/src/main/ets/generated-client/models': ['BaseItemPerson']
    },
    'entry/src/main/ets/features/seasondetail/SeasonDetailViewModel.ets': {
      sdk: ['ImageUrlsApi', 'getPlaystateApi', 'getTvShowsApi', 'getUserLibraryApi'],
      'sdk/src/main/ets/generated-client/models': [
        'BaseItemDto',
        'BaseItemDtoQueryResult',
        'ImageType',
        'ItemFields'
      ],
      '../../network/ApiClient': ['ApiClient']
    },
    'entry/src/main/ets/features/cast/CastDetailPage.ets': {
      'sdk/src/main/ets/generated-client/models': ['BaseItemDto']
    },
    'entry/src/main/ets/features/cast/CastDetailUIState.ets': {
      'sdk/src/main/ets/generated-client/models': ['BaseItemDto']
    },
    'entry/src/main/ets/features/cast/CastDetailViewModel.ets': {
      sdk: ['getItemsApi', 'getUserLibraryApi'],
      'sdk/src/main/ets/generated-client/models': ['BaseItemDto', 'BaseItemDtoQueryResult', 'BaseItemKind'],
      '../../network/ApiClient': ['ApiClient']
    },
    'entry/src/main/ets/features/favorite/FavoriteListViewModel.ets': {
      sdk: ['getItemsApi'],
      'sdk/src/main/ets/generated-client/models': [
        'BaseItemDtoQueryResult',
        'BaseItemKind',
        'ItemSortBy'
      ],
      '../../network/ApiClient': ['ApiClient']
    },
    'entry/src/main/ets/features/home/minetab/MineHistoryRepository.ets': {
      sdk: ['ImageUrlsApi', 'getItemsApi'],
      'sdk/src/main/ets/generated-client/api/items-api': ['ItemsApi'],
      'sdk/src/main/ets/generated-client/models': [
        'BaseItemDto',
        'BaseItemDtoQueryResult',
        'BaseItemKind',
        'ImageType',
        'ItemFields',
        'ItemSortBy',
        'MediaType',
        'SortOrder'
      ],
      '../../../network/ApiClient': ['ApiClient']
    },
    'entry/src/main/ets/component/CastView.ets': {
      sdk: ['ImageUrlsApi'],
      'sdk/src/main/ets/generated-client/models': ['BaseItemPerson', 'ImageType', 'PersonKind'],
      '../network/ApiClient': ['ApiClient']
    },
    'entry/src/main/ets/component/BaseItemCard.ets': {
      sdk: ['ImageUrlsApi'],
      'sdk/src/main/ets/generated-client/models': ['BaseItemDto', 'BaseItemKind'],
      '../network/ApiClient': ['ApiClient']
    },
    'entry/src/main/ets/player/queue/QueueManager.ets': {
      '../source/JellyfinMediaSource': ['JellyfinMediaSource'],
      sdk: ['getItemsApi', 'getTvShowsApi', 'getVideosApi'],
      'sdk/src/main/ets/generated-client/models': [
        'BaseItemDtoQueryResult',
        'BaseItemKind',
        'DeviceProfile',
        'ItemFields',
        'MediaProtocol',
        'MediaStream',
        'MediaStreamType',
        'PlayMethod'
      ],
      '../../network/ApiClient': ['ApiClient']
    },
    'entry/src/main/ets/player/source/MediaSourceResolver.ets': {
      sdk: ['PlaybackInfoResponse', 'getItemsApi', 'getMediaInfoApi', 'getUserLibraryApi'],
      '../../network/ApiClient': ['ApiClient'],
      './JellyfinMediaSource': ['JellyfinMediaSource'],
      'sdk/src/main/ets/generated-client/models': [
        'BaseItemDto',
        'BaseItemDtoQueryResult',
        'BaseItemKind',
        'DeviceProfile'
      ],
      'sdk/src/main/ets/generated-client/models/media-source-info': ['MediaSourceInfo']
    },
    'entry/src/main/ets/player/TrackSelectionHelper.ets': {
      'sdk/src/main/ets/generated-client/models': [
        'MediaStream',
        'MediaStreamType',
        'PlayMethod',
        'SubtitleDeliveryMethod'
      ],
      './source/JellyfinMediaSource': ['JellyfinMediaSource']
    },
    'entry/src/main/ets/player/PlayerEngineResolver.ets': {
      './source/JellyfinMediaSource': ['JellyfinMediaSource']
    },
    'entry/src/main/ets/player/ui/PlayerMenus.ets': {
      'sdk/src/main/ets/generated-client/models': ['MediaStream'],
      '../source/JellyfinMediaSource': ['JellyfinMediaSource']
    },
    'entry/src/main/ets/features/player/PlayerPage.ets': {
      'sdk/src/main/ets/generated-client/models': ['MediaStream']
    },
    'entry/src/main/ets/features/player/PlayerPageUIState.ets': {
      'sdk/src/main/ets/generated-client/models': ['MediaStream'],
      'sdk/src/main/ets/generated-client/models/base-item-kind': ['BaseItemKind']
    },
    'entry/src/main/ets/features/player/PlayerPageViewModel.ets': {
      sdk: ['ImageUrlsApi', 'getHlsSegmentApi', 'getLibraryApi', 'getPlaystateApi', 'getSubtitleApi'],
      '../../network/ApiClient': ['ApiClient'],
      '../../player/source/JellyfinMediaSource': ['JellyfinMediaSource'],
      'sdk/src/main/ets/generated-client/models': [
        'BaseItemDtoQueryResult',
        'BaseItemKind',
        'ItemFields',
        'MediaStream',
        'PlayMethod',
        'RepeatMode'
      ]
    },
    'entry/src/main/ets/features/player/AVPlayerView.ets': {
      'sdk/src/main/ets/generated-client/models': ['BaseItemKind', 'MediaStream']
    },
    'entry/src/main/ets/features/player/MPVPlayerView.ets': {
      'sdk/src/main/ets/generated-client/models': [
        'BaseItemKind',
        'MediaStream',
        'MediaStreamType',
        'PlayMethod',
        'SubtitleDeliveryMethod'
      ],
      '../../player/source/JellyfinMediaSource': ['JellyfinMediaSource']
    },
    'entry/src/main/ets/features/player/VideoFramePreviewHelper.ets': {
      '../../network/ApiClient': ['ApiClient']
    }
  },
  apiClientInstanceCalls: {
    'entry/src/main/ets/features/home/HomeScreenViewModel.ets': 13,
    'entry/src/main/ets/features/home/hometab/HomeViewModel.ets': 14,
    'entry/src/main/ets/features/home/mediatab/MediaViewModel.ets': 5,
    'entry/src/main/ets/features/search/SearchViewModel.ets': 2,
    'entry/src/main/ets/features/videolist/VideoListViewModel.ets': 2,
    'entry/src/main/ets/features/videodetail/VideoDetailViewModel.ets': 22,
    'entry/src/main/ets/features/seasondetail/SeasonDetailViewModel.ets': 16,
    'entry/src/main/ets/features/cast/CastDetailViewModel.ets': 7,
    'entry/src/main/ets/features/favorite/FavoriteListViewModel.ets': 1,
    'entry/src/main/ets/features/home/minetab/MineHistoryRepository.ets': 2,
    'entry/src/main/ets/component/CastView.ets': 1,
    'entry/src/main/ets/component/BaseItemCard.ets': 4,
    'entry/src/main/ets/player/queue/QueueManager.ets': 11,
    'entry/src/main/ets/player/source/MediaSourceResolver.ets': 5,
    'entry/src/main/ets/features/player/PlayerPageViewModel.ets': 11,
    'entry/src/main/ets/features/player/VideoFramePreviewHelper.ets': 1
  }
})

function requiredSource(sources, path) {
  const source = sources.get(path)
  if (source === undefined) {
    throw new Error('missing shared media source: ' + path)
  }
  return source
}

function stripComments(source) {
  let result = ''
  let quote = null
  let escaped = false
  let lineComment = false
  let blockComment = false

  for (let index = 0; index < source.length; index += 1) {
    const char = source[index]
    const next = source[index + 1]

    if (lineComment) {
      if (char === '\n') {
        lineComment = false
        result += char
      } else {
        result += ' '
      }
      continue
    }
    if (blockComment) {
      if (char === '*' && next === '/') {
        result += '  '
        blockComment = false
        index += 1
      } else {
        result += char === '\n' ? '\n' : ' '
      }
      continue
    }
    if (quote) {
      result += char
      if (escaped) {
        escaped = false
      } else if (char === '\\') {
        escaped = true
      } else if (char === quote) {
        quote = null
      }
      continue
    }
    if (char === '/' && next === '/') {
      result += '  '
      lineComment = true
      index += 1
      continue
    }
    if (char === '/' && next === '*') {
      result += '  '
      blockComment = true
      index += 1
      continue
    }
    if (char === '\'' || char === '"' || char === '`') {
      quote = char
    }
    result += char
  }
  return result
}

function importedNames(clause) {
  const names = []
  const normalized = clause.replace(/^type\s+/, '').trim()
  const namedMatch = /\{([\s\S]*?)\}/.exec(normalized)
  if (namedMatch) {
    for (const item of namedMatch[1].split(',')) {
      const name = item.trim().replace(/^type\s+/, '').split(/\s+as\s+/)[0].trim()
      if (name) {
        names.push(name)
      }
    }
  }

  const prefix = normalized.split('{')[0].replace(/,$/, '').trim()
  if (prefix) {
    names.push(prefix.startsWith('* as ') ? '*' : 'default')
  }
  return Array.from(new Set(names)).sort()
}

function sourceImports(source) {
  const imports = []
  const importPattern = /^\s*import\s+(?:type\s+)?(\{[\s\S]*?\}|\*\s+as\s+[\w$]+|[\w$]+(?:\s*,\s*\{[\s\S]*?\})?)\s+from\s+(['"])([^'"]+)\2\s*;?/gm
  const code = stripComments(source)
  for (const match of code.matchAll(importPattern)) {
    imports.push({
      source: match[3],
      names: importedNames(match[1])
    })
  }
  return imports
}

function isJellyfinImport(entry) {
  return entry.source === 'sdk' ||
    entry.source.startsWith('sdk/') ||
    /\/ApiClient(?:\.ets)?$/.test(entry.source) ||
    /Jellyfin[A-Za-z0-9_$]*/.test(entry.source) ||
    entry.names.some((name) => /^Jellyfin[A-Za-z0-9_$]*/.test(name))
}

function countApiClientInstanceCalls(source) {
  return (stripComments(source).match(/\bApiClient\s*\.\s*Instance\s*\(\s*\)/g) || []).length
}

function legacyConstantString(source, name) {
  const match = new RegExp(
    `\\bstatic\\s+readonly\\s+${name}\\s*=\\s*(['\"])([^'\"]+)\\1`
  ).exec(stripComments(source))
  if (!match) {
    throw new Error(`RdbConstants must declare ${name} as a string literal`)
  }
  return match[2]
}

function legacyConstantNumber(source, name) {
  const match = new RegExp(`\\bstatic\\s+readonly\\s+${name}\\s*=\\s*(\\d+)\\b`)
    .exec(stripComments(source))
  if (!match) {
    throw new Error(`RdbConstants must declare ${name} as a numeric literal`)
  }
  return Number(match[1])
}

function legacyTableColumns(source, tableConstant) {
  const tableMatch = new RegExp(
    `\\bstatic\\s+readonly\\s+${tableConstant}\\b[\\s\\S]*?\\bcolumns\\s*:\\s*\\[([^\\]]*)\\]`
  ).exec(stripComments(source))
  if (!tableMatch) {
    throw new Error(`RdbConstants must declare ${tableConstant}.columns`)
  }
  return Array.from(tableMatch[1].matchAll(/(['\"])([^'\"]+)\1/g), (match) => match[2])
}

function normalizedLegacyCreateSql(source, tableConstant) {
  const tableMatch = new RegExp(
    `\\bstatic\\s+readonly\\s+${tableConstant}\\b[\\s\\S]*?` +
      '\\bsqlCreate\\s*:\\s*([\\s\\S]*?),\\s*\\bcolumns\\s*:'
  ).exec(stripComments(source))
  if (!tableMatch) {
    throw new Error(`RdbConstants must declare ${tableConstant}.sqlCreate`)
  }
  const fragments = Array.from(
    tableMatch[1].matchAll(/`([^`]*)`|'([^']*)'|"([^"]*)"/g),
    (match) => match[1] ?? match[2] ?? match[3] ?? ''
  )
  return fragments.join('')
    .replace(/\s+/g, ' ')
    .replace(/\s*,\s*/g, ',')
    .replace(/\(\s*/g, '(')
    .replace(/\s*\)/g, ')')
    .trim()
}

function assertExactLegacyColumns(label, actual, expected) {
  if (actual.length !== expected.length || actual.some((column, index) => column !== expected[index])) {
    throw new Error(`${label} columns must remain ${expected.join(', ')}`)
  }
}

function sourceSha256(source) {
  return createHash('sha256').update(source, 'utf8').digest('hex')
}

function validateLegacyJellyfinSource(path, source) {
  const code = stripComments(source)
  const providerImport = sourceImports(source).find((entry) => {
    return /(?:^|\/)media(?:\/|$)/.test(entry.source) &&
      (entry.names.some((name) => /Provider/.test(name)) || /Provider/.test(entry.source))
  })
  if (providerImport) {
    throw new Error(`${path} must not import media Provider types: ${providerImport.source}`)
  }
  if (/\b(?:FeiniuVideo|Feiniu|FnMediaApi|FNID)\b/i.test(code)) {
    throw new Error(`${path} legacy Jellyfin data must not reference Feiniu or FNID`)
  }
  if (/\b(?:provider_id|provider_kind|provider_type|original_address|connection_route)\b/i.test(code)) {
    throw new Error(`${path} legacy Jellyfin data must not add provider schema fields`)
  }
  if (path.endsWith('/Rdb.ets') &&
    /\b(?:CREATE\s+(?:UNIQUE\s+)?INDEX|DROP\s+TABLE\s+Server|ALTER\s+TABLE\s+Server\s+RENAME)\b/i.test(code)) {
    throw new Error(`${path} must not rebuild the legacy Server table or change its indexes`)
  }
}

export function validateLegacyJellyfinDataBoundaries(sources) {
  for (const path of legacyJellyfinDataPaths) {
    validateLegacyJellyfinSource(path, requiredSource(sources, path))
  }

  const constantsPath = 'entry/src/main/ets/data/RdbConstants.ets'
  const constants = requiredSource(sources, constantsPath)
  const databaseName = legacyConstantString(constants, 'DB_NAME')
  if (databaseName !== legacyJellyfinDatabaseBaseline.name) {
    throw new Error(`RdbConstants DB_NAME must remain ${legacyJellyfinDatabaseBaseline.name}`)
  }
  const version = legacyConstantNumber(constants, 'VERSION')
  if (version !== legacyJellyfinDatabaseBaseline.version) {
    throw new Error(`RdbConstants VERSION must remain ${legacyJellyfinDatabaseBaseline.version}`)
  }
  assertExactLegacyColumns(
    'Server',
    legacyTableColumns(constants, 'TABLE_SERVER'),
    legacyJellyfinDatabaseBaseline.serverColumns
  )
  assertExactLegacyColumns(
    'User',
    legacyTableColumns(constants, 'TABLE_USER'),
    legacyJellyfinDatabaseBaseline.userColumns
  )
  if (!/\bhostname\s+TEXT\s+NOT\s+NULL\s+UNIQUE\b/i.test(stripComments(constants))) {
    throw new Error('Server hostname must retain its legacy UNIQUE constraint')
  }
  for (const path of legacyJellyfinDataPaths) {
    const actualDigest = sourceSha256(requiredSource(sources, path))
    const expectedDigest = legacyJellyfinSourceSha256[path]
    if (actualDigest !== expectedDigest) {
      throw new Error(`${path} must remain byte-for-byte unchanged during Feiniu integration`)
    }
  }
}

function rejectFeiniuDependencies(path, source) {
  const code = stripComments(source)
  const imports = sourceImports(source)
  const feiniuImport = imports.find((entry) => /feiniu[-_]?video/i.test(entry.source))
  if (feiniuImport) {
    throw new Error(`${path} must not import Feiniu Video provider code: ${feiniuImport.source}`)
  }
  if (/\bFnMediaApi\b/.test(code)) {
    throw new Error(`${path} must not reference FnMediaApi`)
  }
  if (/\bFeiniuVideo(?:[A-Z][A-Za-z0-9_$]*)?\b/.test(code) ||
    /\bFn[A-Za-z0-9_$]*(?:Api|Dto|Provider|Mapper|Client|Service|Resolver)\b/.test(code)) {
    throw new Error(`${path} must not reference Feiniu Video API, DTO, or provider identifiers`)
  }
}

function validateKnownJellyfinBaseline(path, source, migrated) {
  const jellyfinImports = sourceImports(source).filter(isJellyfinImport)
  const apiClientCalls = countApiClientInstanceCalls(source)

  if (migrated) {
    if (jellyfinImports.length > 0) {
      throw new Error(`${path} is migrated and must not import Jellyfin SDK, DTO, API client, or media source code`)
    }
    if (apiClientCalls > 0 || /\bJellyfin[A-Z][A-Za-z0-9_$]*\b/.test(stripComments(source))) {
      throw new Error(`${path} is migrated and must not reference Jellyfin client or provider-specific types`)
    }
    return
  }

  const allowedModules = knownJellyfinBaseline.imports[path] || {}
  for (const entry of jellyfinImports) {
    const allowedNames = new Set(allowedModules[entry.source] || [])
    if (allowedNames.size === 0) {
      throw new Error(`${path} has an unrecorded Jellyfin import source: ${entry.source}`)
    }
    for (const name of entry.names) {
      if (!allowedNames.has(name)) {
        throw new Error(`${path} has an unrecorded Jellyfin import: ${name} from ${entry.source}`)
      }
    }
  }

  const allowedCalls = knownJellyfinBaseline.apiClientInstanceCalls[path] || 0
  if (apiClientCalls > allowedCalls) {
    throw new Error(
      `${path} adds ApiClient.Instance() coupling; baseline allows ${allowedCalls}, found ${apiClientCalls}`
    )
  }
}

function validateFinalPlayerRules(sources) {
  for (const path of playerPaths) {
    const code = stripComments(requiredSource(sources, path))
    if (/\bApiClient\s*\.\s*Instance\s*\(\s*\)/.test(code)) {
      throw new Error(`${path} final player boundary forbids ApiClient.Instance()`)
    }
    if (/\bFnMediaApi\b/.test(code)) {
      throw new Error(`${path} final player boundary forbids FnMediaApi`)
    }
    if (/\bMediaProviderKind\s*\./.test(code) ||
      /\b(?:if|switch)\s*\([^)]*\bMediaProviderKind\b/.test(code)) {
      throw new Error(`${path} final player boundary forbids MediaProviderKind branching`)
    }
  }
}

function resolvedOptions(options) {
  const stage = options.stage || 'baseline'
  const stagePaths = mediaProviderMigrationStages[stage]
  if (!stagePaths) {
    throw new Error(`unknown media provider migration stage: ${stage}`)
  }

  const migratedFiles = new Set(stagePaths)
  for (const path of options.migratedFiles || []) {
    if (!sharedMediaPaths.includes(path)) {
      throw new Error(`unknown shared media path: ${path}`)
    }
    migratedFiles.add(path)
  }
  return {
    stage,
    migratedFiles,
    enforceFinalPlayerRules: options.enforceFinalPlayerRules === true || stage === 'player'
  }
}

export function validateMediaProviderBoundaries(sources, options = {}) {
  const validation = resolvedOptions(options)
  for (const path of sharedMediaPaths) {
    const source = requiredSource(sources, path)
    rejectFeiniuDependencies(path, source)
    validateKnownJellyfinBaseline(path, source, validation.migratedFiles.has(path))
  }
  if (validation.enforceFinalPlayerRules) {
    validateFinalPlayerRules(sources)
  }
  return validation
}

export function defaultWorkspaceRoot() {
  return resolve(dirname(fileURLToPath(import.meta.url)), '..')
}

export function loadMediaProviderBoundarySources(workspaceRoot = defaultWorkspaceRoot()) {
  return new Map(sharedMediaPaths.map((path) => [
    path,
    readFileSync(resolve(workspaceRoot, path), 'utf8')
  ]))
}

export function loadLegacyJellyfinDataSources(workspaceRoot = defaultWorkspaceRoot()) {
  return new Map(legacyJellyfinDataPaths.map((path) => [
    path,
    readFileSync(resolve(workspaceRoot, path), 'utf8')
  ]))
}

export function validateWorkspace(workspaceRoot = defaultWorkspaceRoot(), options = {}) {
  const validation = validateMediaProviderBoundaries(loadMediaProviderBoundarySources(workspaceRoot), options)
  validateLegacyJellyfinDataBoundaries(loadLegacyJellyfinDataSources(workspaceRoot))
  return validation
}

function cliOptions(args) {
  const options = { migratedFiles: [] }
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index]
    if (argument === '--final-player') {
      options.enforceFinalPlayerRules = true
    } else if (argument.startsWith('--stage=')) {
      options.stage = argument.slice('--stage='.length)
    } else if (argument === '--stage') {
      options.stage = args[++index]
    } else if (argument.startsWith('--migrated=')) {
      options.migratedFiles.push(...argument.slice('--migrated='.length).split(',').filter(Boolean))
    } else if (argument === '--migrated') {
      options.migratedFiles.push(...(args[++index] || '').split(',').filter(Boolean))
    } else {
      throw new Error(`unknown argument: ${argument}`)
    }
  }
  return options
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const result = validateWorkspace(defaultWorkspaceRoot(), cliOptions(process.argv.slice(2)))
  console.log(
    `Media provider boundaries verified (stage=${result.stage}, migrated=${result.migratedFiles.size}).`
  )
}
