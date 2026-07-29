import assert from 'node:assert/strict'
import { dirname, resolve } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  defaultWorkspaceRoot,
  managementUserPaths,
  validateManagementUserContracts
} from './verify_management_user_contracts.mjs'

function localeSource() {
  return JSON.stringify({
    string: [
      { name: 'management_user_title', value: 'Users' },
      { name: 'management_user_save', value: 'Save' }
    ]
  })
}

function validSources() {
  const sources = new Map()
  for (const path of Object.values(managementUserPaths)) {
    sources.set(path, '')
  }
  sources.set(managementUserPaths.entryAbility, [
    "import { KeyboardAvoidMode } from '@kit.ArkUI'",
    'class EntryAbility {',
    '  loadContent(windowStage, windowClass): void {',
    "    windowStage.loadContent('pages/Index', (error) => {",
    '      if (error.code) return',
    '      windowClass.getUIContext().setKeyboardAvoidMode(KeyboardAvoidMode.RESIZE)',
    '      this.initializeContentBindings(windowClass)',
    '    })',
    '  }',
    '}'
  ].join('\n'))
  sources.set(managementUserPaths.breakpointPolicy, [
    'if (width < 600) return BreakpointTypeEnum.SM',
    'if (width < 840) return BreakpointTypeEnum.MD',
    'return BreakpointTypeEnum.LG'
  ].join('\n'))
  sources.set(managementUserPaths.router, [
    'ManagementUsersPage',
    'ManagementUserDetailPage',
    'ManagementUserCreatePage'
  ].join('\n'))
  sources.set(managementUserPaths.dashboardModels,
    'enum ManagementSectionKind { Users }\nclass ManagementUserSummary {}')
  sources.set(managementUserPaths.dashboardState,
    'usersStatus = ManagementSectionStatus.Loading\nusers: ManagementUserSummary | null = null')
  sources.set(managementUserPaths.dashboardViewModel,
    'async refreshUsers() { await this.repository.loadUsersSection() }')
  sources.set(managementUserPaths.dashboardPage,
    'HMRouterMgr.to(RouterConsts.ManagementUsersPage).push()')
  sources.set(managementUserPaths.models, [
    'enableAllFolders',
    'enableAllChannels',
    'enableAllDevices',
    'export class ManagementPasswordDraft { currentPassword: string = "" }',
    'export class ManagementUserCreateDraft { password: string = "" }'
  ].join('\n'))
  sources.set(managementUserPaths.policy, [
    'clonePolicy',
    'targetId === currentUserId',
    'enabledAdministrators.length <= 1',
    'static patchParental(policy, draft) {',
    '  next.AllowedTags = draft.allowedTags',
    '  next.BlockedTags = draft.blockedTags',
    '  next.AccessSchedules = draft.schedules',
    '}',
    'validateParental normalizeTags conflicts startHour endHour'
  ].join('\n'))
  sources.set(managementUserPaths.service, [
    "authenticatedGet('/Channels')",
    "authenticatedGet('/Devices')",
    "authenticatedGet('/Localization/ParentalRatings')",
    "authenticatedGet('/System/Configuration/network')",
    'postUserImage({ userId, body })',
    'NewPw: newPassword'
  ].join('\n'))
  sources.set(managementUserPaths.repository, [
    'async saveProfile(userId: string) {',
    '  const current = await this.service.getUser(userId)',
    '  const next = ManagementUserPolicy.patchProfile(current.Policy)',
    '  await this.service.updatePolicy(userId, next)',
    '  await this.service.getUser(userId)',
    '}',
    'async saveAccess(userId: string) {',
    '  const current = await this.service.getUser(userId)',
    '  const next = ManagementUserPolicy.patchAccess(current.Policy)',
    '  await this.service.updatePolicy(userId, next)',
    '  await this.service.getUser(userId)',
    '}',
    'async saveParental(userId: string) {',
    '  const current = await this.service.getUser(userId)',
    '  const next = ManagementUserPolicy.patchParental(current.Policy)',
    '  await this.service.updatePolicy(userId, next)',
    '  await this.service.getUser(userId)',
    '}',
    'async createUser() {',
    '  const created = await this.service.createUser()',
    '  const latest = await this.service.getUser(created.Id)',
    '  await this.service.updatePolicy(created.Id, latest.Policy)',
    '  return CreatedPolicyPending',
    '}',
    'async uploadAvatar(bytes, contentType) {',
    '  if (!SUPPORTED_AVATAR_MIME_TYPES.includes(contentType)) return',
    '  if (bytes.byteLength > MAX_AVATAR_BYTES) return',
    '  await this.service.uploadUserImage(userId, bytes, contentType)',
    '}',
    'async retryCreateAccess(userId) {',
    '  const current = await this.service.getUser(userId)',
    '  const next = ManagementUserPolicy.patchAccess(current.Policy)',
    '  await this.service.updatePolicy(userId, next)',
    '}'
  ].join('\n'))
  sources.set(managementUserPaths.detailPage, [
    'ManagementUserProfileSection',
    'ManagementUserAccessSection',
    'ManagementUserParentalSection',
    'ManagementUserPasswordSection',
    'AppThemeSurfaceResolver',
    'embeddedUserId',
    'left: this.vm.appUIState.deviceWidth >= 600 ? 24 : 16',
    'right: this.vm.appUIState.deviceWidth >= 600 ? 24 : 16',
    'bottom: this.showSaveBar() ? 104 : 24',
    'Stack({ alignContent: Alignment.Bottom })',
    'bottom: this.vm.appUIState.safeBottom + 10',
    'showUnsavedDialog()',
    'deleteProtected currentUserId enabledAdministratorCount',
    'onDeleted management_user_delete_impact'
  ].join('\n'))
  sources.set(managementUserPaths.detailState,
    'profileDirty\naccessDirty\nparentalDirty\npasswordDirty\ndeleteStatus')
  sources.set(managementUserPaths.detailViewModel, [
    'generation',
    'checkCurrentUserAccess()',
    'loadEditor()',
    'markCurrentTabDirty() {',
    'this.ui.profileDirty = JSON.stringify(this.ui.profile) !== JSON.stringify(this.baselineProfile)',
    'this.ui.accessDirty = JSON.stringify(this.ui.access) !== JSON.stringify(this.baselineAccess)',
    'this.ui.parentalDirty = JSON.stringify(this.ui.parental) !== JSON.stringify(this.baselineParental)',
    'this.ui.passwordDirty = this.ui.password.currentPassword.length > 0 ||',
    'this.ui.password.newPassword.length > 0 || this.ui.password.confirmPassword.length > 0',
    '}',
    'saveCurrentTab()',
    'async deleteUser() { await this.repository.deleteUser() }',
    'canLeave()',
    'discardCurrentTab()',
    'setPassword resetPassword clearPassword runPasswordAction',
    'new photoAccessHelper.PhotoViewPicker()',
    'photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE',
    'options.maxSelectNumber = 1',
    'fileIo.openSync(uri)',
    'image.createImageSource(file.fd)',
    'SUPPORTED_AVATAR_MIME_TYPES',
    'MAX_AVATAR_BYTES'
  ].join('\n'))
  sources.set(managementUserPaths.passwordSection, [
    'ManagementPasswordDraft',
    'InputType.Password',
    'showPasswordIcon(true)',
    'onSetPassword',
    'onResetPassword',
    'onClearPassword'
  ].join('\n'))
  sources.set(managementUserPaths.profileSection, [
    'authenticationProviderId',
    'enableCollectionManagement',
    'enableMediaPlayback',
    'enableRemoteAccess',
    'remoteClientBitrateLimitMbps',
    'syncPlayAccess',
    'isAdministrator',
    'isDisabled',
    'enabledAdministratorCount'
  ].join('\n'))
  sources.set(managementUserPaths.accessSection, [
    'enableAllFolders',
    'enabledFolderIds',
    'enableAllChannels',
    'enabledChannelIds',
    'enableAllDevices',
    'enabledDeviceIds',
    'foldersStatus',
    'channelsStatus',
    'devicesStatus'
  ].join('\n'))
  sources.set(managementUserPaths.parentalSection, [
    'maxParentalRating',
    'blockedUnratedItems',
    'allowedTags',
    'blockedTags',
    'schedules',
    'if (this.ui.profile.isAdministrator)',
    'management_user_parental_admin_schedule_hidden'
  ].join('\n'))
  sources.set(managementUserPaths.listPage,
    'AppThemeSurfaceResolver\nif (deviceWidth >= 840) userPane(300)\n' +
      '.width(44)\n.height(44)\n.constraintSize({ minHeight: 72 })\n' +
      'deleteProtected currentUserId enabledAdministratorCount\n' +
      'onDeleted refresh(false)')
  sources.set(managementUserPaths.listViewModel,
    'if (!selectedExists && this.appUIState.deviceWidth >= 840) selectedUserId = users[0].id\n' +
      'async deleteUser() { const result = await repository.deleteUser(); await refresh(false) }')
  sources.set(managementUserPaths.createState, [
    'name: string',
    'password: string',
    'confirmPassword: string',
    'access: ManagementAccessDraft',
    'saving: boolean',
    'createdUserId: string',
    'partialSuccess: boolean'
  ].join('\n'))
  sources.set(managementUserPaths.createViewModel, [
    'if (this.ui.saving || !this.validate()) return',
    'await this.repository.createUser()',
    'CreatedPolicyPending',
    'async retryAccess() { await this.repository.retryCreateAccess() }',
    'this.ui.password = ""',
    'this.ui.confirmPassword = ""'
  ].join('\n'))
  sources.set(managementUserPaths.createPage, [
    'AppThemeSurfaceResolver',
    'left: this.vm.appUIState.deviceWidth >= 600 ? 24 : 16',
    'right: this.vm.appUIState.deviceWidth >= 600 ? 24 : 16',
    'enabledFolderIds',
    'enabledChannelIds',
    'partialSuccess',
    'management_user_create_retry_access'
  ].join('\n'))
  sources.set(managementUserPaths.baseStrings, localeSource())
  sources.set(managementUserPaths.zhStrings, localeSource())
  sources.set(managementUserPaths.enStrings, localeSource())
  return sources
}

test('accepts the complete user-management contract', () => {
  assert.doesNotThrow(() => validateManagementUserContracts(validSources()))
})

test('derives the workspace root from the scripts directory', () => {
  const scriptsDirectory = dirname(fileURLToPath(import.meta.url))
  assert.equal(defaultWorkspaceRoot(), resolve(scriptsDirectory, '..'))
})

test('requires RESIZE keyboard avoidance after content loads', () => {
  const sources = validSources()
  sources.set(managementUserPaths.entryAbility,
    sources.get(managementUserPaths.entryAbility)
      .replace('KeyboardAvoidMode.RESIZE', 'KeyboardAvoidMode.OFFSET'))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /RESIZE keyboard avoidance must be enabled after content loads/
  )
})

test('requires the exact 599/600 and 839/840 breakpoint boundaries', () => {
  const sources = validSources()
  sources.set(managementUserPaths.breakpointPolicy,
    sources.get(managementUserPaths.breakpointPolicy)
      .replace('width < 600', 'width <= 600'))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /599\/600 breakpoint boundary/
  )

  const largeBoundarySources = validSources()
  largeBoundarySources.set(managementUserPaths.breakpointPolicy,
    largeBoundarySources.get(managementUserPaths.breakpointPolicy)
      .replace('width < 840', 'width <= 840'))
  assert.throws(
    () => validateManagementUserContracts(largeBoundarySources),
    /839\/840 breakpoint boundary/
  )
})

test('requires medium-width editor padding to begin at 600vp', () => {
  const sources = validSources()
  sources.set(managementUserPaths.detailPage,
    sources.get(managementUserPaths.detailPage)
      .replaceAll('deviceWidth >= 600', 'deviceWidth >= 840'))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /600vp editor left padding boundary/
  )
})

test('rejects duplicate bottom safe-area ownership in the detail editor', () => {
  const sources = validSources()
  sources.set(managementUserPaths.detailPage,
    sources.get(managementUserPaths.detailPage) +
      '\nbottom: this.vm.appUIState.safeBottom + 24')
  assert.throws(
    () => validateManagementUserContracts(sources),
    /must not apply the bottom safe-area inset twice/
  )
})

test('requires stable user row dimensions', () => {
  const sources = validSources()
  sources.set(managementUserPaths.listPage,
    sources.get(managementUserPaths.listPage)
      .replace('minHeight: 72', 'minHeight: 68'))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /stable user row dimensions/
  )
})

test('rejects policy writes without a fresh server reload', () => {
  const sources = validSources()
  sources.set(managementUserPaths.repository,
    sources.get(managementUserPaths.repository)
      .replace('const current = await this.service.getUser(userId)', 'const current = cachedUser'))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /fresh user policy/
  )
})

test('rejects deletion or demotion without current administrator protection', () => {
  const sources = validSources()
  sources.set(managementUserPaths.policy,
    sources.get(managementUserPaths.policy).replace('targetId === currentUserId', 'false'))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /current administrator protection/
  )
})

test('rejects removal of the last enabled administrator protection', () => {
  const sources = validSources()
  sources.set(managementUserPaths.policy,
    sources.get(managementUserPaths.policy).replace('enabledAdministrators.length <= 1', 'false'))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /last enabled administrator protection/
  )
})

test('rejects password persistence or logging', () => {
  const sources = validSources()
  sources.set(managementUserPaths.passwordSection,
    sources.get(managementUserPaths.passwordSection) +
      '\nAppPreference.getInstance().setValue("password", password)')
  assert.throws(
    () => validateManagementUserContracts(sources),
    /password secret/
  )
})

test('rejects password fields outside password and create drafts', () => {
  const sources = validSources()
  sources.set(managementUserPaths.models,
    sources.get(managementUserPaths.models) +
      '\nexport class ManagementUserItem { password: string = "" }')
  assert.throws(
    () => validateManagementUserContracts(sources),
    /password secret fields/
  )
})

test('requires avatar MIME validation before upload', () => {
  const sources = validSources()
  sources.set(managementUserPaths.repository,
    sources.get(managementUserPaths.repository)
      .replace('SUPPORTED_AVATAR_MIME_TYPES.includes(contentType)', 'true'))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /avatar MIME and byte-size validation/
  )
})

test('requires avatar byte-size validation before upload', () => {
  const sources = validSources()
  sources.set(managementUserPaths.repository,
    sources.get(managementUserPaths.repository)
      .replace('bytes.byteLength > MAX_AVATAR_BYTES', 'false'))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /avatar MIME and byte-size validation/
  )
})

test('requires password clearing to submit an empty NewPw value', () => {
  const sources = validSources()
  sources.set(managementUserPaths.service,
    sources.get(managementUserPaths.service)
      .replace('NewPw: newPassword', 'NewPw: newPassword || null'))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /empty NewPw/
  )
})

test('requires partial create access to be retryable with a fresh policy', () => {
  const sources = validSources()
  sources.set(managementUserPaths.repository,
    sources.get(managementUserPaths.repository)
      .replace('async retryCreateAccess(userId) {\n  const current = await this.service.getUser(userId)',
        'async retryCreateAccess(userId) {\n  const current = cachedUser'))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /partial create access retry/
  )
})

test('requires create submission locking and validation', () => {
  const sources = validSources()
  sources.set(managementUserPaths.createViewModel,
    sources.get(managementUserPaths.createViewModel).replace('this.ui.saving', 'false'))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /create submit lock/
  )
})

test('requires create password fields to clear after server acceptance', () => {
  const sources = validSources()
  sources.set(managementUserPaths.createViewModel,
    sources.get(managementUserPaths.createViewModel)
      .replace('this.ui.password = ""', 'this.ui.password = draft.password'))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /password clearing/
  )
})

test('requires deletion to reload the user list', () => {
  const sources = validSources()
  sources.set(managementUserPaths.listViewModel,
    sources.get(managementUserPaths.listViewModel).replace('await refresh(false)', 'return result'))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /reload the user list/
  )
})

test('requires detail deletion to use the repository', () => {
  const sources = validSources()
  sources.set(managementUserPaths.detailViewModel,
    sources.get(managementUserPaths.detailViewModel)
      .replace('async deleteUser() { await this.repository.deleteUser() }', ''))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /detail delete action ownership/
  )
})

test('requires embedded detail deletion to refresh the list', () => {
  const sources = validSources()
  sources.set(managementUserPaths.listPage,
    sources.get(managementUserPaths.listPage).replace('onDeleted refresh(false)', ''))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /embedded detail deletion/
  )
})

test('rejects the unsupported legacy avatar endpoint', () => {
  const sources = validSources()
  sources.set(managementUserPaths.service,
    sources.get(managementUserPaths.service) + "\npost('/Users/id/Images/Primary')")
  assert.throws(
    () => validateManagementUserContracts(sources),
    /unsupported user avatar endpoint/
  )
})

test('requires identical user-management locale keys', () => {
  const sources = validSources()
  sources.set(managementUserPaths.enStrings,
    JSON.stringify({ string: [{ name: 'management_user_title', value: 'Users' }] }))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /locale keys differ/
  )
})

test('requires dashboard-owned user navigation without page HTTP', () => {
  const sources = validSources()
  sources.set(managementUserPaths.dashboardPage,
    'getUserApi(ApiClient.Instance()).getUsers({})')
  assert.throws(
    () => validateManagementUserContracts(sources),
    /dashboard user navigation|dashboard page must not own user HTTP/
  )
})

test('requires guarded editor lifecycle and independent dirty state', () => {
  const sources = validSources()
  sources.set(managementUserPaths.detailState, 'profileDirty')
  assert.throws(
    () => validateManagementUserContracts(sources),
    /independent editor dirty state/
  )
})

test('requires editor dirty state to ignore unchanged control callbacks', () => {
  const sources = validSources()
  sources.set(managementUserPaths.detailViewModel,
    sources.get(managementUserPaths.detailViewModel).replace(
      'this.ui.profileDirty = JSON.stringify(this.ui.profile) !== JSON.stringify(this.baselineProfile)',
      'this.ui.profileDirty = true'))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /current draft with its baseline/
  )
})

test('rejects access-array ownership in the profile section', () => {
  const sources = validSources()
  sources.set(managementUserPaths.profileSection,
    sources.get(managementUserPaths.profileSection) + '\nenabledFolderIds')
  assert.throws(
    () => validateManagementUserContracts(sources),
    /profile section must not own access arrays/
  )
})

test('rejects profile or password ownership in the access section', () => {
  const sources = validSources()
  sources.set(managementUserPaths.accessSection,
    sources.get(managementUserPaths.accessSection) + '\nupdatePassword')
  assert.throws(
    () => validateManagementUserContracts(sources),
    /access section must not own profile or password settings/
  )
})

test('requires administrators to hide access schedules', () => {
  const sources = validSources()
  sources.set(managementUserPaths.parentalSection,
    sources.get(managementUserPaths.parentalSection)
      .replace('this.ui.profile.isAdministrator', 'false'))
  assert.throws(
    () => validateManagementUserContracts(sources),
    /administrator schedule hiding/
  )
})

test('rejects access-array or password ownership in parental controls', () => {
  const sources = validSources()
  sources.set(managementUserPaths.parentalSection,
    sources.get(managementUserPaths.parentalSection) + '\nenabledDeviceIds')
  assert.throws(
    () => validateManagementUserContracts(sources),
    /parental section must not own access arrays or passwords/
  )
})
