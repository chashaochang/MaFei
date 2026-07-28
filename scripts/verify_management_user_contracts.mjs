import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

export const managementUserPaths = Object.freeze({
  router: 'entry/src/main/ets/common/RouterConsts.ets',
  models: 'entry/src/main/ets/features/management/ManagementUserModels.ets',
  policy: 'entry/src/main/ets/features/management/ManagementUserPolicy.ets',
  service: 'entry/src/main/ets/features/management/ManagementUserApiService.ets',
  repository: 'entry/src/main/ets/features/management/ManagementUserRepository.ets',
  listState: 'entry/src/main/ets/features/management/ManagementUsersUIState.ets',
  listViewModel: 'entry/src/main/ets/features/management/ManagementUsersViewModel.ets',
  listPage: 'entry/src/main/ets/features/management/ManagementUsersPage.ets',
  detailState: 'entry/src/main/ets/features/management/ManagementUserDetailUIState.ets',
  detailViewModel: 'entry/src/main/ets/features/management/ManagementUserDetailViewModel.ets',
  detailPage: 'entry/src/main/ets/features/management/ManagementUserDetailPage.ets',
  profileSection: 'entry/src/main/ets/features/management/ManagementUserProfileSection.ets',
  accessSection: 'entry/src/main/ets/features/management/ManagementUserAccessSection.ets',
  parentalSection: 'entry/src/main/ets/features/management/ManagementUserParentalSection.ets',
  passwordSection: 'entry/src/main/ets/features/management/ManagementUserPasswordSection.ets',
  createState: 'entry/src/main/ets/features/management/ManagementUserCreateUIState.ets',
  createViewModel: 'entry/src/main/ets/features/management/ManagementUserCreateViewModel.ets',
  createPage: 'entry/src/main/ets/features/management/ManagementUserCreatePage.ets',
  baseStrings: 'entry/src/main/resources/base/element/string.json',
  zhStrings: 'entry/src/main/resources/zh_CN/element/string.json',
  enStrings: 'entry/src/main/resources/en_US/element/string.json'
})

const productionPaths = Object.values(managementUserPaths)
const routeNames = [
  'ManagementUsersPage',
  'ManagementUserDetailPage',
  'ManagementUserCreatePage'
]
const editorSections = [
  'ManagementUserProfileSection',
  'ManagementUserAccessSection',
  'ManagementUserParentalSection',
  'ManagementUserPasswordSection'
]

function requiredSource(sources, path) {
  const source = sources.get(path)
  if (source === undefined) {
    throw new Error('missing source: ' + path)
  }
  return source
}

function requirePattern(sources, path, pattern, message) {
  const source = requiredSource(sources, path)
  if (!pattern.test(source)) {
    throw new Error(message + ': ' + path)
  }
}

function rejectPattern(sources, path, pattern, message) {
  const source = requiredSource(sources, path)
  if (pattern.test(source)) {
    throw new Error(message + ': ' + path)
  }
}

function methodBlock(source, methodName) {
  const match = new RegExp('\\b' + methodName + '\\s*\\([^)]*\\)\\s*(?::\\s*[^\\{]+)?\\s*\\{')
    .exec(source)
  if (!match) {
    throw new Error('missing repository method: ' + methodName)
  }
  const openingBrace = source.indexOf('{', match.index)
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
  throw new Error('unterminated repository method: ' + methodName)
}

function requireFreshPolicyWrite(repository, methodName, patchMethod) {
  const body = methodBlock(repository, methodName)
  const loadIndex = body.indexOf('this.service.getUser(userId)')
  const cloneIndex = body.indexOf('ManagementUserPolicy.' + patchMethod)
  const writeIndex = body.indexOf('this.service.updatePolicy(userId')
  const reloadIndex = body.lastIndexOf('this.service.getUser(userId)')
  if (loadIndex < 0 || cloneIndex < 0 || writeIndex < 0 || reloadIndex <= writeIndex ||
    loadIndex > cloneIndex || cloneIndex > writeIndex) {
    throw new Error(methodName + ' must use a fresh user policy, patch it, submit it, and reload')
  }
}

function resourceKeys(source, path) {
  let json
  try {
    json = JSON.parse(source)
  } catch (error) {
    throw new Error('invalid locale JSON: ' + path)
  }
  if (!Array.isArray(json.string)) {
    throw new Error('locale file is missing string array: ' + path)
  }
  return json.string
    .map((entry) => typeof entry?.name === 'string' ? entry.name : '')
    .filter((name) => name.startsWith('management_user_'))
    .sort()
}

function requireSameLocaleKeys(sources) {
  const paths = [
    managementUserPaths.baseStrings,
    managementUserPaths.zhStrings,
    managementUserPaths.enStrings
  ]
  const keySets = paths.map((path) => resourceKeys(requiredSource(sources, path), path))
  if (keySets[0].length === 0) {
    throw new Error('management_user_ locale keys are missing')
  }
  const expected = JSON.stringify(keySets[0])
  for (let index = 1; index < keySets.length; index += 1) {
    if (JSON.stringify(keySets[index]) !== expected) {
      throw new Error('management_user_ locale keys differ: ' + paths[index])
    }
  }
}

export function validateManagementUserContracts(sources) {
  for (const path of productionPaths) {
    requiredSource(sources, path)
  }

  for (const routeName of routeNames) {
    requirePattern(sources, managementUserPaths.router, new RegExp('\\b' + routeName + '\\b'),
      'missing user management route')
  }
  for (const section of editorSections) {
    requirePattern(sources, managementUserPaths.detailPage, new RegExp('\\b' + section + '\\b'),
      'missing editor section')
  }

  const repository = requiredSource(sources, managementUserPaths.repository)
  requireFreshPolicyWrite(repository, 'saveProfile', 'patchProfile')
  requireFreshPolicyWrite(repository, 'saveAccess', 'patchAccess')
  requireFreshPolicyWrite(repository, 'saveParental', 'patchParental')
  requirePattern(sources, managementUserPaths.repository, /CreatedPolicyPending/,
    'create flow must preserve partial success')
  requirePattern(sources, managementUserPaths.repository,
    /createUser[\s\S]*getUser\(created\.Id\)[\s\S]*updatePolicy\(created\.Id/,
    'create flow must reload the created user before policy update')

  requirePattern(sources, managementUserPaths.policy, /targetId\s*===\s*currentUserId/,
    'current administrator protection is missing')
  requirePattern(sources, managementUserPaths.policy,
    /enabledAdministrators(?:\.length)?\s*<=\s*1/,
    'last enabled administrator protection is missing')
  requirePattern(sources, managementUserPaths.policy, /clonePolicy/,
    'complete policy cloning is missing')

  requirePattern(sources, managementUserPaths.models, /EnableAllFolders|enableAllFolders/,
    'folder access ownership is missing')
  requirePattern(sources, managementUserPaths.models, /EnableAllChannels|enableAllChannels/,
    'channel access ownership is missing')
  requirePattern(sources, managementUserPaths.models, /EnableAllDevices|enableAllDevices/,
    'device access ownership is missing')

  requirePattern(sources, managementUserPaths.service, /['"]\/Channels['"]/,
    'channels endpoint is missing')
  requirePattern(sources, managementUserPaths.service, /['"]\/Devices['"]/,
    'devices endpoint is missing')
  requirePattern(sources, managementUserPaths.service, /\/Localization\/ParentalRatings/,
    'parental ratings endpoint is missing')
  requirePattern(sources, managementUserPaths.service, /\/System\/Configuration\/network/,
    'network configuration endpoint is missing')
  requirePattern(sources, managementUserPaths.service,
    /postUserImage|\/UserImage\?userId=/,
    'user avatar upload must use the Jellyfin UserImage contract')
  rejectPattern(sources, managementUserPaths.service, /\/Users\/[^\s'"]+\/Images\/Primary/,
    'unsupported user avatar endpoint')

  const passwordSecretPattern =
    /AppPreference|Preferences|console\.(?:log|error)[^\n]*password|JSON\.stringify\([^\n]*password/i
  for (const path of [
    managementUserPaths.passwordSection,
    managementUserPaths.detailViewModel,
    managementUserPaths.createViewModel,
    managementUserPaths.repository
  ]) {
    rejectPattern(sources, path, passwordSecretPattern, 'password secret must not be persisted or logged')
  }
  rejectPattern(sources, managementUserPaths.router, /password/i,
    'password secret must not be placed in route parameters')

  for (const pagePath of [
    managementUserPaths.listPage,
    managementUserPaths.detailPage,
    managementUserPaths.createPage
  ]) {
    requirePattern(sources, pagePath, /AppThemeSurfaceResolver/,
      'theme surface ownership is missing')
  }
  requirePattern(sources, managementUserPaths.listPage, /300/,
    'large-screen user pane width is missing')
  requirePattern(sources, managementUserPaths.listPage, /840/,
    'large-screen breakpoint is missing')

  requireSameLocaleKeys(sources)
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function readManagementUserWorkspace(root = defaultWorkspaceRoot()) {
  const sources = new Map()
  for (const path of productionPaths) {
    sources.set(path, readFileSync(resolve(root, path), 'utf8'))
  }
  return sources
}

export function validateWorkspace(root = defaultWorkspaceRoot()) {
  validateManagementUserContracts(readManagementUserWorkspace(root))
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspace()
  console.log('Jellyfin user management contracts verified')
}
