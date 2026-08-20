import assert from 'node:assert/strict'
import test from 'node:test'
import {
  knownJellyfinBaseline,
  legacyJellyfinDatabaseBaseline,
  legacyJellyfinDataPaths,
  legacyJellyfinSourceSha256,
  loadLegacyJellyfinDataSources,
  loadMediaProviderBoundarySources,
  mediaProviderBoundaryPaths,
  mediaProviderMigrationStages,
  sharedMediaPaths,
  validateLegacyJellyfinDataBoundaries,
  validateMediaProviderBoundaries,
  validateWorkspace
} from './verify_media_provider_boundaries.mjs'

function neutralSources() {
  return new Map(sharedMediaPaths.map((path) => [path, '']))
}

test('records the current Jellyfin coupling baseline', () => {
  const homeImports = knownJellyfinBaseline.imports[
    'entry/src/main/ets/features/home/hometab/HomeViewModel.ets'
  ]
  assert.deepEqual(homeImports.sdk, [
    'ImageUrlsApi',
    'getItemsApi',
    'getTvShowsApi',
    'getUserLibraryApi',
    'getUserViewsApi'
  ])
  assert.equal(
    knownJellyfinBaseline.apiClientInstanceCalls[
      'entry/src/main/ets/features/player/PlayerPageViewModel.ets'
    ],
    11
  )
  assert.ok(mediaProviderBoundaryPaths.catalog.includes(
    'entry/src/main/ets/features/favorite/FavoriteListViewModel.ets'))
  assert.ok(mediaProviderBoundaryPaths.catalog.includes(
    'entry/src/main/ets/features/home/minetab/MineHistoryRepository.ets'))
  assert.ok(mediaProviderBoundaryPaths.catalog.includes(
    'entry/src/main/ets/component/CastView.ets'))
  assert.ok(mediaProviderBoundaryPaths.catalog.includes(
    'entry/src/main/ets/component/BaseItemCard.ets'))
})

test('accepts provider-neutral shared media sources', () => {
  assert.doesNotThrow(() => validateMediaProviderBoundaries(neutralSources()))
})

test('accepts the current staged workspace baseline', () => {
  assert.doesNotThrow(() => validateWorkspace())
})

test('locks the legacy Jellyfin database identity and column sets', () => {
  assert.equal(legacyJellyfinDatabaseBaseline.name, 'jellyFin.db')
  assert.equal(legacyJellyfinDatabaseBaseline.version, 1)
  assert.deepEqual(legacyJellyfinDatabaseBaseline.serverColumns, [
    'id', 'hostname', 'server_name', 'version', 'last_used_timestamp'
  ])
  assert.deepEqual(legacyJellyfinDatabaseBaseline.userColumns, [
    'id', 'server_id', 'user_id', 'access_token', 'user_name', 'last_login_timestamp'
  ])
  assert.deepEqual(legacyJellyfinDataPaths, [
    'entry/src/main/ets/data/RdbConstants.ets',
    'entry/src/main/ets/data/Rdb.ets',
    'entry/src/main/ets/data/ServerDao.ets',
    'entry/src/main/ets/data/ServerEntity.ets',
    'entry/src/main/ets/data/ServerUser.ets',
    'entry/src/main/ets/data/ApiClientController.ets'
  ])
  assert.deepEqual(Object.keys(legacyJellyfinSourceSha256), legacyJellyfinDataPaths)
  assert.doesNotThrow(() => validateLegacyJellyfinDataBoundaries(loadLegacyJellyfinDataSources()))
})

test('rejects provider schema fields and media Provider imports in legacy data', () => {
  const providerFieldSources = loadLegacyJellyfinDataSources()
  const entityPath = 'entry/src/main/ets/data/ServerEntity.ets'
  providerFieldSources.set(entityPath, providerFieldSources.get(entityPath) + '\nconst provider_kind = "Jellyfin"\n')
  assert.throws(
    () => validateLegacyJellyfinDataBoundaries(providerFieldSources),
    /must not add provider schema fields/
  )

  const providerImportSources = loadLegacyJellyfinDataSources()
  const controllerPath = 'entry/src/main/ets/data/ApiClientController.ets'
  providerImportSources.set(controllerPath,
    providerImportSources.get(controllerPath) +
      '\nimport { MediaProviderKind } from "../media/MediaProviderKind"\n')
  assert.throws(
    () => validateLegacyJellyfinDataBoundaries(providerImportSources),
    /must not import media Provider types/
  )
})

test('rejects Feiniu identity and any legacy database schema drift', () => {
  const feiniuSources = loadLegacyJellyfinDataSources()
  const userPath = 'entry/src/main/ets/data/ServerUser.ets'
  feiniuSources.set(userPath, feiniuSources.get(userPath) + '\nconst FNID = "test"\n')
  assert.throws(
    () => validateLegacyJellyfinDataBoundaries(feiniuSources),
    /must not reference Feiniu or FNID/
  )

  const constantsPath = 'entry/src/main/ets/data/RdbConstants.ets'
  const databaseSources = loadLegacyJellyfinDataSources()
  databaseSources.set(constantsPath,
    databaseSources.get(constantsPath).replace("DB_NAME = 'jellyFin.db'", "DB_NAME = 'media.db'"))
  assert.throws(
    () => validateLegacyJellyfinDataBoundaries(databaseSources),
    /DB_NAME must remain jellyFin\.db/
  )

  const versionSources = loadLegacyJellyfinDataSources()
  versionSources.set(constantsPath, versionSources.get(constantsPath).replace('VERSION = 1', 'VERSION = 2'))
  assert.throws(
    () => validateLegacyJellyfinDataBoundaries(versionSources),
    /VERSION must remain 1/
  )

  const columnSources = loadLegacyJellyfinDataSources()
  columnSources.set(constantsPath,
    columnSources.get(constantsPath).replace("'last_used_timestamp']", "'last_used_timestamp', 'provider']"))
  assert.throws(
    () => validateLegacyJellyfinDataBoundaries(columnSources),
    /must not add provider schema fields|Server columns must remain/
  )

  const uniqueConstraintSources = loadLegacyJellyfinDataSources()
  uniqueConstraintSources.set(constantsPath,
    uniqueConstraintSources.get(constantsPath).replace('hostname TEXT NOT NULL UNIQUE', 'hostname TEXT NOT NULL'))
  assert.throws(
    () => validateLegacyJellyfinDataBoundaries(uniqueConstraintSources),
    /hostname must retain its legacy UNIQUE constraint/
  )

  const indexSources = loadLegacyJellyfinDataSources()
  const rdbPath = 'entry/src/main/ets/data/Rdb.ets'
  indexSources.set(rdbPath,
    indexSources.get(rdbPath) + '\nconst migration = "CREATE UNIQUE INDEX provider_index ON Server(hostname)"\n')
  assert.throws(
    () => validateLegacyJellyfinDataBoundaries(indexSources),
    /must not rebuild the legacy Server table or change its indexes/
  )
})

test('rejects behavior-only edits in legacy Jellyfin data and login files', () => {
  const sources = loadLegacyJellyfinDataSources()
  const controllerPath = 'entry/src/main/ets/data/ApiClientController.ets'
  sources.set(controllerPath, sources.get(controllerPath).replace(
    'this.configureApiClientUser(userId, accessToken)',
    'this.configureApiClientUser(userId, accessToken.trim())'
  ))
  assert.throws(
    () => validateLegacyJellyfinDataBoundaries(sources),
    /must remain byte-for-byte unchanged during Feiniu integration/
  )
})

test('accepts removal of a recorded Jellyfin import binding', () => {
  const sources = neutralSources()
  const path = 'entry/src/main/ets/features/home/hometab/HomeViewModel.ets'
  sources.set(path, 'import { getItemsApi } from "sdk"')
  assert.doesNotThrow(() => validateMediaProviderBoundaries(sources))
})

test('rejects a Feiniu provider import in shared catalog code immediately', () => {
  const sources = neutralSources()
  const path = mediaProviderBoundaryPaths.catalog[0]
  sources.set(path,
    'import { FeiniuVideoCatalogProvider } from "../../features/feiniuvideo/catalog/FeiniuVideoCatalogProvider"')
  assert.throws(
    () => validateMediaProviderBoundaries(sources),
    /must not import Feiniu Video provider code/
  )
})

test('rejects a Feiniu DTO or API reference without an import', () => {
  const sources = neutralSources()
  const path = mediaProviderBoundaryPaths.catalog[1]
  sources.set(path, 'const api: FeiniuVideoApiClient = new FeiniuVideoApiClient()')
  assert.throws(
    () => validateMediaProviderBoundaries(sources),
    /must not reference Feiniu Video API, DTO, or provider identifiers/
  )
})

test('rejects an unrecorded Jellyfin import on the baseline stage', () => {
  const sources = neutralSources()
  const path = 'entry/src/main/ets/features/search/SearchPage.ets'
  sources.set(path, 'import { BaseItemDto } from "sdk/src/main/ets/generated-client/models"')
  assert.throws(
    () => validateMediaProviderBoundaries(sources),
    /unrecorded Jellyfin import source/
  )
})

test('rejects an unrecorded Jellyfin binding from an allowed module', () => {
  const sources = neutralSources()
  const path = 'entry/src/main/ets/features/search/SearchViewModel.ets'
  sources.set(path, 'import { getItemsApi, getVideosApi } from "sdk"')
  assert.throws(
    () => validateMediaProviderBoundaries(sources),
    /unrecorded Jellyfin import: getVideosApi/
  )
})

test('rejects Jellyfin imports once a file is explicitly migrated', () => {
  const sources = neutralSources()
  const path = 'entry/src/main/ets/features/home/hometab/HomeTab.ets'
  sources.set(path, 'import { BaseItemKind } from "sdk/src/main/ets/generated-client/models"')
  assert.throws(
    () => validateMediaProviderBoundaries(sources, { migratedFiles: [path] }),
    /is migrated and must not import Jellyfin/
  )
})

test('catalog stage tightens every shared browsing file', () => {
  assert.doesNotThrow(() => validateMediaProviderBoundaries(
    loadMediaProviderBoundarySources(), { stage: 'catalog' }))
})

test('catalog stage still rejects a Jellyfin import reintroduced into a migrated file', () => {
  const sources = loadMediaProviderBoundarySources()
  const path = mediaProviderMigrationStages.catalog[0]
  sources.set(path, sources.get(path) +
    '\nimport { BaseItemDto } from "sdk/src/main/ets/generated-client/models"')
  assert.throws(
    () => validateMediaProviderBoundaries(sources, { stage: 'catalog' }),
    /is migrated and must not import Jellyfin/
  )
})

test('search stage accepts a provider-neutral migrated search slice', () => {
  const sources = loadMediaProviderBoundarySources()
  for (const path of mediaProviderMigrationStages.search) {
    sources.set(path, '')
  }
  assert.doesNotThrow(() => validateMediaProviderBoundaries(
    sources, { stage: 'search' }))
})

test('final player rules reject ApiClient.Instance without relying on imports', () => {
  const sources = neutralSources()
  const path = 'entry/src/main/ets/player/queue/QueueManager.ets'
  sources.set(path, 'const api = ApiClient.Instance()')
  assert.throws(
    () => validateMediaProviderBoundaries(sources, { enforceFinalPlayerRules: true }),
    /final player boundary forbids ApiClient\.Instance/
  )
})

test('shared player files reject FnMediaApi immediately', () => {
  const sources = neutralSources()
  const path = 'entry/src/main/ets/player/ui/PlayerMenus.ets'
  sources.set(path, 'const api = FnMediaApi.instance()')
  assert.throws(
    () => validateMediaProviderBoundaries(sources, { enforceFinalPlayerRules: true }),
    /must not reference FnMediaApi/
  )
})

test('final player rules reject MediaProviderKind branching', () => {
  const sources = neutralSources()
  const path = 'entry/src/main/ets/player/PlayerEngineResolver.ets'
  sources.set(path, 'if (kind === MediaProviderKind.Jellyfin) { return av }')
  assert.throws(
    () => validateMediaProviderBoundaries(sources, { enforceFinalPlayerRules: true }),
    /final player boundary forbids MediaProviderKind branching/
  )
})

test('rejects unknown stage and migrated path switches', () => {
  assert.throws(
    () => validateMediaProviderBoundaries(neutralSources(), { stage: 'future' }),
    /unknown media provider migration stage/
  )
  assert.throws(
    () => validateMediaProviderBoundaries(neutralSources(), { migratedFiles: ['entry/unknown.ets'] }),
    /unknown shared media path/
  )
})
