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
  sources.set(managementUserPaths.router, [
    'ManagementUsersPage',
    'ManagementUserDetailPage',
    'ManagementUserCreatePage'
  ].join('\n'))
  sources.set(managementUserPaths.models, [
    'enableAllFolders',
    'enableAllChannels',
    'enableAllDevices'
  ].join('\n'))
  sources.set(managementUserPaths.policy, [
    'clonePolicy',
    'targetId === currentUserId',
    'enabledAdministrators.length <= 1'
  ].join('\n'))
  sources.set(managementUserPaths.service, [
    "authenticatedGet('/Channels')",
    "authenticatedGet('/Devices')",
    "authenticatedGet('/Localization/ParentalRatings')",
    "authenticatedGet('/System/Configuration/network')",
    'postUserImage({ userId, body })'
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
    '}'
  ].join('\n'))
  sources.set(managementUserPaths.detailPage, [
    'ManagementUserProfileSection',
    'ManagementUserAccessSection',
    'ManagementUserParentalSection',
    'ManagementUserPasswordSection',
    'AppThemeSurfaceResolver'
  ].join('\n'))
  sources.set(managementUserPaths.listPage,
    'AppThemeSurfaceResolver\nif (deviceWidth >= 840) userList(300)')
  sources.set(managementUserPaths.createPage, 'AppThemeSurfaceResolver')
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
    'AppPreference.getInstance().setValue("password", password)')
  assert.throws(
    () => validateManagementUserContracts(sources),
    /password secret/
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
