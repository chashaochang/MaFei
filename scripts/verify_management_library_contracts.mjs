import { readFileSync, readdirSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

export const managementLibraryServicePath =
  'entry/src/main/ets/features/management/library/ManagementLibraryApiService.ets'

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
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  runManagementLibraryValidation()
}
