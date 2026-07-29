import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

export const managementUserPaths = Object.freeze({
  router: 'entry/src/main/ets/common/RouterConsts.ets',
  dashboardModels: 'entry/src/main/ets/features/management/ManagementModels.ets',
  dashboardState: 'entry/src/main/ets/features/management/ManagementDashboardUIState.ets',
  dashboardViewModel: 'entry/src/main/ets/features/management/ManagementDashboardViewModel.ets',
  dashboardPage: 'entry/src/main/ets/features/management/ManagementDashboardPage.ets',
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

function stripClass(source, className) {
  const match = new RegExp('(?:export\\s+)?class\\s+' + className + '\\s*\\{').exec(source)
  if (!match) {
    return source
  }
  const openingBrace = source.indexOf('{', match.index)
  let depth = 0
  for (let index = openingBrace; index < source.length; index += 1) {
    if (source[index] === '{') {
      depth += 1
    } else if (source[index] === '}') {
      depth -= 1
      if (depth === 0) {
        return source.slice(0, match.index) + source.slice(index + 1)
      }
    }
  }
  throw new Error('unterminated model class: ' + className)
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


function requireAvatarBoundary(repository) {
  const body = methodBlock(repository, 'uploadAvatar')
  const mimeIndex = body.indexOf('SUPPORTED_AVATAR_MIME_TYPES.includes(contentType)')
  const sizeIndex = body.indexOf('bytes.byteLength > MAX_AVATAR_BYTES')
  const uploadIndex = body.indexOf('this.service.uploadUserImage')
  if (mimeIndex < 0 || sizeIndex < 0 || uploadIndex < 0 ||
    mimeIndex > uploadIndex || sizeIndex > uploadIndex) {
    throw new Error('avatar MIME and byte-size validation must run before upload')
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

  requirePattern(sources, managementUserPaths.dashboardModels, /ManagementSectionKind[\s\S]*Users/,
    'dashboard user section ownership is missing')
  requirePattern(sources, managementUserPaths.dashboardModels, /ManagementUserSummary/,
    'dashboard user summary model is missing')
  requirePattern(sources, managementUserPaths.dashboardState, /usersStatus[\s\S]*users:/,
    'dashboard user summary state is missing')
  requirePattern(sources, managementUserPaths.dashboardViewModel, /refreshUsers[\s\S]*loadUsersSection/,
    'dashboard user summary loading is missing')
  requirePattern(sources, managementUserPaths.dashboardPage, /ManagementUsersPage/,
    'dashboard user navigation is missing')
  rejectPattern(sources, managementUserPaths.dashboardPage, /getUserApi|axiosInstance|\/Users\b/,
    'dashboard page must not own user HTTP')

  requirePattern(sources, managementUserPaths.detailState,
    /profileDirty[\s\S]*accessDirty[\s\S]*parentalDirty[\s\S]*passwordDirty/,
    'independent editor dirty state is missing')
  requirePattern(sources, managementUserPaths.detailState, /deleteStatus/,
    'detail delete operation state is missing')
  requirePattern(sources, managementUserPaths.detailViewModel,
    /checkCurrentUserAccess[\s\S]*loadEditor/,
    'detail page administrator guard is missing')
  for (const editorMethod of ['generation', 'saveCurrentTab', 'canLeave', 'discardCurrentTab']) {
    requirePattern(sources, managementUserPaths.detailViewModel, new RegExp('\\b' + editorMethod + '\\b'),
      'editor lifecycle ownership is missing')
  }
  for (const editorShellBoundary of [/embeddedUserId/, /840/, /showUnsavedDialog/]) {
    requirePattern(sources, managementUserPaths.detailPage, editorShellBoundary,
      'responsive editor shell or unsaved prompt is missing')
  }

  requirePattern(sources, managementUserPaths.profileSection,
    /authenticationProviderId[\s\S]*enableCollectionManagement[\s\S]*enableMediaPlayback/,
    'profile identity, privileges, or playback ownership is missing')
  requirePattern(sources, managementUserPaths.profileSection,
    /enableRemoteAccess[\s\S]*remoteClientBitrateLimitMbps[\s\S]*syncPlayAccess/,
    'profile remote, limit, or SyncPlay ownership is missing')
  for (const administratorBoundary of [/isAdministrator/, /isDisabled/, /enabledAdministratorCount/]) {
    requirePattern(sources, managementUserPaths.profileSection, administratorBoundary,
      'profile administrator protection UI is missing')
  }
  rejectPattern(sources, managementUserPaths.profileSection,
    /enabledFolderIds|enabledChannelIds|enabledDeviceIds|ManagementPasswordDraft|updatePassword/,
    'profile section must not own access arrays or passwords')

  requirePattern(sources, managementUserPaths.accessSection,
    /enableAllFolders[\s\S]*enabledFolderIds[\s\S]*enableAllChannels[\s\S]*enabledChannelIds/,
    'folder or channel access ownership is missing')
  requirePattern(sources, managementUserPaths.accessSection,
    /enableAllDevices[\s\S]*enabledDeviceIds/,
    'device access ownership is missing')
  requirePattern(sources, managementUserPaths.accessSection,
    /foldersStatus[\s\S]*channelsStatus[\s\S]*devicesStatus/,
    'independent access option states are missing')
  rejectPattern(sources, managementUserPaths.accessSection,
    /isAdministrator|enableMediaPlayback|ManagementPasswordDraft|updatePassword/,
    'access section must not own profile or password settings')

  requirePattern(sources, managementUserPaths.parentalSection,
    /maxParentalRating[\s\S]*blockedUnratedItems[\s\S]*allowedTags[\s\S]*blockedTags[\s\S]*schedules/,
    'parental rating, unrated, tag, or schedule ownership is missing')
  requirePattern(sources, managementUserPaths.parentalSection,
    /profile\.isAdministrator[\s\S]*management_user_parental_admin_schedule_hidden/,
    'administrator schedule hiding is missing')
  rejectPattern(sources, managementUserPaths.parentalSection,
    /enabledFolderIds|enabledChannelIds|enabledDeviceIds|ManagementPasswordDraft|updatePassword/,
    'parental section must not own access arrays or passwords')

  const repository = requiredSource(sources, managementUserPaths.repository)
  requireFreshPolicyWrite(repository, 'saveProfile', 'patchProfile')
  requireFreshPolicyWrite(repository, 'saveAccess', 'patchAccess')
  requireFreshPolicyWrite(repository, 'saveParental', 'patchParental')
  requirePattern(sources, managementUserPaths.repository, /CreatedPolicyPending/,
    'create flow must preserve partial success')
  requirePattern(sources, managementUserPaths.repository,
    /createUser[\s\S]*getUser\(created\.Id\)[\s\S]*updatePolicy\(created\.Id/,
    'create flow must reload the created user before policy update')
  requirePattern(sources, managementUserPaths.repository,
    /retryCreateAccess[\s\S]*getUser\(userId\)[\s\S]*patchAccess[\s\S]*updatePolicy\(userId/,
    'partial create access retry must use a fresh user policy')

  requirePattern(sources, managementUserPaths.policy, /targetId\s*===\s*currentUserId/,
    'current administrator protection is missing')
  requirePattern(sources, managementUserPaths.policy,
    /enabledAdministrators(?:\.length)?\s*<=\s*1/,
    'last enabled administrator protection is missing')
  requirePattern(sources, managementUserPaths.policy, /clonePolicy/,
    'complete policy cloning is missing')
  requirePattern(sources, managementUserPaths.policy,
    /validateParental[\s\S]*normalizeTags[\s\S]*conflicts[\s\S]*startHour[\s\S]*endHour/,
    'parental validation is missing')
  const policySource = requiredSource(sources, managementUserPaths.policy)
  const parentalPatch = methodBlock(policySource, 'patchParental')
  for (const parentalField of ['AllowedTags', 'BlockedTags', 'AccessSchedules']) {
    if (!parentalPatch.includes(parentalField)) {
      throw new Error('parental policy copy ownership is missing: ' + managementUserPaths.policy)
    }
  }

  requirePattern(sources, managementUserPaths.models, /EnableAllFolders|enableAllFolders/,
    'folder access ownership is missing')
  requirePattern(sources, managementUserPaths.models, /EnableAllChannels|enableAllChannels/,
    'channel access ownership is missing')
  requirePattern(sources, managementUserPaths.models, /EnableAllDevices|enableAllDevices/,
    'device access ownership is missing')
  let nonSecretModels = requiredSource(sources, managementUserPaths.models)
  nonSecretModels = stripClass(nonSecretModels, 'ManagementPasswordDraft')
  nonSecretModels = stripClass(nonSecretModels, 'ManagementUserCreateDraft')
  if (/\b(?:currentPassword|newPassword|confirmPassword|password)\s*:\s*string\b/i
    .test(nonSecretModels)) {
    throw new Error('password secret fields must stay inside password or create drafts')
  }

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
  requirePattern(sources, managementUserPaths.service, /NewPw:\s*newPassword\s*(?:,|\n|}|$)/,
    'password clearing must preserve an empty NewPw value')
  rejectPattern(sources, managementUserPaths.service, /\/Users\/[^\s'"]+\/Images\/Primary/,
    'unsupported user avatar endpoint')

  requirePattern(sources, managementUserPaths.passwordSection,
    /ManagementPasswordDraft[\s\S]*InputType\.Password[\s\S]*showPasswordIcon/,
    'password section must use protected password inputs')
  requirePattern(sources, managementUserPaths.passwordSection,
    /onSetPassword[\s\S]*onResetPassword[\s\S]*onClearPassword/,
    'password section actions are incomplete')
  requirePattern(sources, managementUserPaths.detailViewModel,
    /setPassword[\s\S]*resetPassword[\s\S]*clearPassword[\s\S]*runPasswordAction/,
    'password action ownership is incomplete')
  requirePattern(sources, managementUserPaths.detailViewModel,
    /deleteUser[\s\S]*repository\.deleteUser/,
    'detail delete action ownership is missing')
  for (const pickerBoundary of [
    /PhotoViewPicker/,
    /PhotoViewMIMETypes\.IMAGE_TYPE/,
    /maxSelectNumber\s*=\s*1/
  ]) {
    requirePattern(sources, managementUserPaths.detailViewModel, pickerBoundary,
      'avatar picker must be limited to one image')
  }
  for (const avatarBoundary of [
    /fileIo\.openSync/,
    /image\.createImageSource/,
    /SUPPORTED_AVATAR_MIME_TYPES/,
    /MAX_AVATAR_BYTES/
  ]) {
    requirePattern(sources, managementUserPaths.detailViewModel, avatarBoundary,
      'avatar picker boundary must inspect MIME and byte size')
  }
  requireAvatarBoundary(repository)

  requirePattern(sources, managementUserPaths.createState,
    /name:[\s\S]*password:[\s\S]*confirmPassword:[\s\S]*access:[\s\S]*saving:[\s\S]*createdUserId:[\s\S]*partialSuccess/,
    'create state is incomplete')
  requirePattern(sources, managementUserPaths.createViewModel,
    /ui\.saving[\s\S]*validate[\s\S]*repository\.createUser[\s\S]*CreatedPolicyPending/,
    'create submit lock, validation, or partial success is missing')
  requirePattern(sources, managementUserPaths.createViewModel,
    /retryAccess[\s\S]*retryCreateAccess[\s\S]*password\s*=\s*(?:''|"")[\s\S]*confirmPassword\s*=\s*(?:''|"")/,
    'create retry or password clearing is missing')
  requirePattern(sources, managementUserPaths.createPage,
    /enabledFolderIds[\s\S]*enabledChannelIds[\s\S]*partialSuccess[\s\S]*management_user_create_retry_access/,
    'create access options or partial-success recovery UI is missing')
  requirePattern(sources, managementUserPaths.listViewModel,
    /deleteUser[\s\S]*repository\.deleteUser[\s\S]*refresh\(false\)/,
    'delete flow must reload the user list after success')
  requirePattern(sources, managementUserPaths.listPage,
    /deleteProtected[\s\S]*currentUserId[\s\S]*enabledAdministratorCount/,
    'delete UI protection is missing')
  requirePattern(sources, managementUserPaths.listPage, /onDeleted[\s\S]*refresh\(false\)/,
    'embedded detail deletion must refresh the user list')
  requirePattern(sources, managementUserPaths.detailPage,
    /deleteProtected[\s\S]*currentUserId[\s\S]*enabledAdministratorCount/,
    'detail delete UI protection is missing')
  requirePattern(sources, managementUserPaths.detailPage,
    /onDeleted[\s\S]*management_user_delete_impact/,
    'detail delete confirmation or embedded callback is missing')

  const passwordSecretPattern =
    /AppPreference|Preferences|console\.(?:log|error)[^\n]*password|JSON\.stringify\([^\n]*password/i
  for (const path of [
    managementUserPaths.passwordSection,
    managementUserPaths.detailViewModel,
    managementUserPaths.createState,
    managementUserPaths.createViewModel,
    managementUserPaths.createPage,
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
