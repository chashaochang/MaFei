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

export function runManagementLibraryValidation(workspaceRoot = defaultWorkspaceRoot()) {
  verifyServiceOwnershipText(loadManagementLibraryServiceText(workspaceRoot))
  const libraryRoot = resolve(workspaceRoot,
    'entry/src/main/ets/features/management/library')
  for (const uiPath of collectUiFiles(libraryRoot)) {
    verifyUiText(readFileSync(uiPath, 'utf8'), relative(workspaceRoot, uiPath))
  }
  verifyMediaEntryPointsText(Object.fromEntries(Object.entries(entryPointPaths).map(([key, path]) =>
    [key, readFileSync(resolve(workspaceRoot, path), 'utf8')])))
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  runManagementLibraryValidation()
}
