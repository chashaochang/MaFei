import { readFileSync, readdirSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

export const managementLibraryServicePath =
  'entry/src/main/ets/features/management/library/ManagementLibraryApiService.ets'

const entryPointPaths = Object.freeze({
  appState: 'entry/src/main/ets/entity/AppUIState.ets',
  index: 'entry/src/main/ets/features/splash/IndexPage.ets',
  home: 'entry/src/main/ets/features/home/HomeScreen.ets',
  mediaTab: 'entry/src/main/ets/features/home/mediatab/MediaTab.ets',
  dashboard: 'entry/src/main/ets/features/management/ManagementDashboardPage.ets',
  libraries: 'entry/src/main/ets/features/management/library/ManagementLibrariesPage.ets',
  events: 'entry/src/main/ets/events/Events.ets'
})

export const batchDeletePaths = Object.freeze({
  homeState: 'entry/src/main/ets/features/home/hometab/HomeUIState.ets',
  listState: 'entry/src/main/ets/features/videolist/VideoListUIState.ets',
  listViewModel: 'entry/src/main/ets/features/videolist/VideoListViewModel.ets',
  listPage: 'entry/src/main/ets/features/videolist/VideoListPage.ets',
  catalogModels: 'entry/src/main/ets/media/catalog/MediaCatalogModels.ets',
  jellyfinCatalogProvider: 'entry/src/main/ets/media/jellyfin/JellyfinCatalogProvider.ets',
  jellyfinCatalogMapper: 'entry/src/main/ets/media/jellyfin/JellyfinCatalogMapper.ets',
  deleteDialog: 'entry/src/main/ets/features/management/library/ManagementMediaDeleteDialog.ets',
  deletePolicy: 'entry/src/main/ets/features/management/library/ManagementMediaDeletePolicy.ets',
  deleteRepository: 'entry/src/main/ets/features/management/library/ManagementMediaDeleteRepository.ets',
  libraryService: 'entry/src/main/ets/features/management/library/ManagementLibraryApiService.ets'
})

const requiredEndpoints = Object.freeze([
  '/Library/VirtualFolders',
  '/Library/VirtualFolders/LibraryOptions',
  '/Library/VirtualFolders/Name',
  '/Library/VirtualFolders/Paths',
  '/Library/VirtualFolders/Paths/Update',
  '/Libraries/AvailableOptions',
  '/Environment/Drives',
  '/Environment/DirectoryContents',
  '/Environment/ParentPath',
  '/Environment/ValidatePath',
  '/Localization/Countries',
  '/Localization/Cultures',
  '/Items/{itemId}/Refresh',
  '/ScheduledTasks',
  '/ScheduledTasks/Running/{taskId}'
])

const directHttpPattern =
  /axiosInstance|ApiClient\.Instance\s*\(\s*\)|\.(?:get|post|delete)\s*</

export function verifyServiceText(source) {
  for (const endpoint of requiredEndpoints) {
    if (!source.includes(endpoint)) {
      throw new Error('missing endpoint ownership: ' + endpoint)
    }
  }
}

export function verifyUiText(source, path = 'management library UI') {
  if (directHttpPattern.test(source)) {
    throw new Error('direct HTTP is forbidden in ' + path)
  }
}

export function verifyServiceOwnershipText(source) {
  verifyServiceText(source)
  for (const marker of [
    'ApiClient.Instance()',
    'authorizationHeader',
    'getUserApi',
    'getLibraryApi',
    'params:',
    'encodeURIComponent(itemId)'
  ]) {
    if (!source.includes(marker)) {
      throw new Error('missing authenticated API ownership marker: ' + marker)
    }
  }
  if (/['"`]\/[^'"`]*\?[^'"`]*['"`]/.test(source)) {
    throw new Error('query values must use Axios params')
  }
  if (!/getLibraryApi\s*\(\s*ApiClient\.Instance\s*\(\s*\)\s*\)[\s\S]*\.deleteItems\s*\(\s*\{\s*ids\s*:\s*ids\s*\}\s*\)/.test(source)) {
    throw new Error('generated deleteItems ownership is missing')
  }
}

function requireMarker(source, marker, message) {
  if (!source.includes(marker)) {
    throw new Error(message + ': ' + marker)
  }
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
  throw new Error('unterminated contract block')
}

function blockAfterMarker(source, marker, message) {
  const markerIndex = source.indexOf(marker)
  if (markerIndex < 0) {
    throw new Error(message + ': ' + marker)
  }
  const openingBrace = source.indexOf('{', markerIndex)
  if (openingBrace < 0) {
    throw new Error(message + ': ' + marker)
  }
  return bracedBlock(source, openingBrace)
}

export function verifyMediaEntryScopeText(source) {
  requireMarker(source, 'rootNavigationMediaAdminActionsVisible', 'missing explicit Media menu state')
  requireMarker(source, 'selectedDestination === HomeDestination.Media', 'Media menu is not destination scoped')
  requireMarker(source, '&& this.mediaAdministrator', 'Media menu is not administrator scoped')
  requireMarker(source, "publishRootNavigationChrome(false, '首页', false, false)",
    'Media menu state is not cleared when HomeScreen disappears')
}

export function verifyMediaEntryPointsText(sources) {
  requireMarker(sources.appState, 'rootNavigationMediaAdminActionsVisible', 'missing Media menu state')
  requireMarker(sources.appState, 'rootNavigationMediaScanBusy', 'missing Media scan busy state')
  verifyMediaEntryScopeText(sources.home)
  for (const marker of [
    'ManagementLibraryEditorPage',
    'ManagementLibrariesPage',
    'startAllScan()',
    'MediaLibraryRefreshEvent'
  ]) {
    requireMarker(sources.index, marker, 'root Navigation is missing a Media administrator action')
  }
  requireMarker(sources.mediaTab, '!this.rootTitleBarOwned && this.ui.isAdministrator',
    'legacy Media actions are not administrator scoped')
  requireMarker(sources.dashboard, 'RouterConsts.ManagementLibrariesPage',
    'server dashboard is missing the media-library entry')
  requireMarker(sources.libraries, 'MediaLibraryRefreshEvent',
    'management library list does not consume shared refresh')
  requireMarker(sources.events, 'MediaLibraryRefreshEvent', 'shared media-library refresh event is missing')
}

export function verifyBatchDeleteText(sources) {
  requireMarker(sources.homeState, 'canDelete?: boolean', 'video items must retain CanDelete')
  requireMarker(sources.homeState, 'path?: string', 'video items must retain Path')

  const summaryModel = blockAfterMarker(sources.catalogModels, 'export interface MediaSummary',
    'provider-neutral media summary is missing')
  requireMarker(summaryModel, 'readonly canDelete?: boolean',
    'provider-neutral media summary must retain delete capability')
  requireMarker(summaryModel, 'readonly path?: string',
    'provider-neutral media summary must retain source path')

  const jellyfinListQuery = blockAfterMarker(sources.jellyfinCatalogProvider,
    'getItems(request: MediaListRequest)', 'Jellyfin catalog list query is missing')
  requireMarker(jellyfinListQuery, 'ItemFields.CanDelete',
    'Jellyfin catalog list query must request CanDelete')
  requireMarker(jellyfinListQuery, 'ItemFields.Path',
    'Jellyfin catalog list query must request Path')

  const jellyfinSummaryMapper = blockAfterMarker(sources.jellyfinCatalogMapper,
    'private createSummary(', 'Jellyfin summary mapper is missing')
  requireMarker(jellyfinSummaryMapper, 'canDelete: item.CanDelete ?? undefined',
    'Jellyfin summary mapper must retain CanDelete')
  requireMarker(jellyfinSummaryMapper, 'path: item.Path || undefined',
    'Jellyfin summary mapper must retain Path')

  for (const marker of [
    '@Trace selectionMode: boolean = false',
    '@Trace selectedIds: string[] = []',
    '@Trace deletingSelection: boolean = false',
    '@Trace deleteDialogVisible: boolean = false',
    'enterSelection(id: string)',
    'toggleSelection(id: string)',
    'clearSelection()'
  ]) {
    requireMarker(sources.listState, marker, 'video list selection state is incomplete')
  }

  for (const marker of [
    'item.canDelete === true',
    "item.path || ''",
    'this.dataSource.getDataAll()',
    'prepareDeleteSelection()',
    'this.deleteRepository.prepareTargets(targets)',
    'this.deleteRepository.deleteTargets(targets)',
    'this.ui.clearSelection()',
    'eventHub.emit(MediaLibraryRefreshEvent)',
    'this.dataSource.initData(remaining)'
  ]) {
    requireMarker(sources.listViewModel, marker, 'video list batch-delete ownership is incomplete')
  }

  verifyUiText(sources.listPage, batchDeletePaths.listPage)
  if (/\.deleteItems\s*\(/.test(sources.listPage)) {
    throw new Error('VideoListPage must not submit deleteItems directly')
  }
  for (const marker of [
    'ManagementMediaDeleteDialog.show',
    'await this.vm.prepareDeleteSelection()',
    'LongPressGesture()',
    '.priorityGesture(',
    'selectionMenus()',
    'selectable:',
    'selected:',
    'onLongPress:',
    'onSelect:',
    'onBackClick: () =>',
    '.backgroundColor(0x38007DFF)'
  ]) {
    requireMarker(sources.listPage, marker, 'video list selection UI is incomplete')
  }

  for (const marker of ['allowedNames', 'allowedIds.length', 'visibleCount']) {
    requireMarker(sources.deleteDialog, marker, 'batch delete confirmation scope is incomplete')
  }
  requireMarker(sources.deletePolicy, 'summary.allowedNames.push',
    'batch delete policy does not retain eligible names')
  for (const marker of ['prepareTargets(', 'getItemsByIds(ids)', "item?.CanDelete === true", "item?.Path || ''"]) {
    requireMarker(sources.deleteRepository, marker, 'delete capability recheck is incomplete')
  }
  requireMarker(sources.libraryService, 'getItemsByIds(ids: string[])',
    'management service does not expose item capability lookup')
}

function collectUiFiles(directory) {
  const files = []
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) {
      files.push(...collectUiFiles(path))
    } else if (/(?:Page|Section)\.ets$/.test(entry.name)) {
      files.push(path)
    }
  }
  return files
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function loadManagementLibraryServiceText(workspaceRoot = defaultWorkspaceRoot()) {
  return readFileSync(resolve(workspaceRoot, managementLibraryServicePath), 'utf8')
}

export function loadBatchDeleteSources(workspaceRoot = defaultWorkspaceRoot()) {
  return Object.fromEntries(Object.entries(batchDeletePaths).map(([key, path]) =>
    [key, readFileSync(resolve(workspaceRoot, path), 'utf8')]))
}

export function runManagementLibraryValidation(workspaceRoot = defaultWorkspaceRoot()) {
  verifyServiceOwnershipText(loadManagementLibraryServiceText(workspaceRoot))
  const libraryRoot = resolve(workspaceRoot,
    'entry/src/main/ets/features/management/library')
  for (const uiPath of collectUiFiles(libraryRoot)) {
    verifyUiText(readFileSync(uiPath, 'utf8'), relative(workspaceRoot, uiPath))
  }
  verifyMediaEntryPointsText(Object.fromEntries(Object.entries(entryPointPaths).map(([key, path]) =>
    [key, readFileSync(resolve(workspaceRoot, path), 'utf8')])))
  verifyBatchDeleteText(loadBatchDeleteSources(workspaceRoot))
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  runManagementLibraryValidation()
}
