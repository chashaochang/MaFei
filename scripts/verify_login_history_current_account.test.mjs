import assert from 'node:assert/strict'
import { dirname, resolve } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  defaultWorkspaceRoot,
  validateLoginHistoryCurrentAccount,
  validateWorkspace
} from './verify_login_history_current_account.mjs'

function validSource() {
  return `
    struct AccountPage {
      fromLogin: boolean = false

      private isCurrentAccount(item: ServerUser): boolean {
        if (this.fromLogin) {
          return false
        }
        const preference = AppPreference.getInstance()
        const currentUserId = preference.getCurrentUserId()
        const currentServerId = preference.getCurrentServerId()
        return currentUserId.length > 0 && currentServerId !== null &&
          item.user.userId === currentUserId && item.server.id === currentServerId
      }

      build() {
        if (this.isCurrentAccount(item as ServerUser)) { Text('current') }
        if (!this.isCurrentAccount(item as ServerUser)) { Image('delete') }
        if (this.isCurrentAccount(item as ServerUser)) { return }
      }
    }
  `
}

test('accepts persisted current-account semantics for all row decisions', () => {
  assert.doesNotThrow(() => validateLoginHistoryCurrentAccount(validSource()))
})

test('derives the workspace root from the verifier location', () => {
  const scriptsDirectory = dirname(fileURLToPath(import.meta.url))
  assert.equal(defaultWorkspaceRoot(), resolve(scriptsDirectory, '..'))
})

test('validates the current workspace AccountPage', () => {
  assert.doesNotThrow(() => validateWorkspace())
})

test('rejects any dependency on the global ApiClient', () => {
  assert.throws(
    () => validateLoginHistoryCurrentAccount(validSource() + '\nApiClient.Instance()'),
    /global ApiClient/
  )
})

test('requires login history to have no current account', () => {
  assert.throws(
    () => validateLoginHistoryCurrentAccount(validSource().replace('if (this.fromLogin)', 'if (false)')),
    /login history/
  )
})

test('requires non-empty user and non-null server identifiers', () => {
  assert.throws(
    () => validateLoginHistoryCurrentAccount(validSource().replace('currentUserId.length > 0 && ', '')),
    /identifiers must be valid/
  )
  assert.throws(
    () => validateLoginHistoryCurrentAccount(validSource().replace('currentServerId !== null &&\n          ', '')),
    /identifiers must be valid/
  )
})

test('requires both persisted identifiers in the account comparison', () => {
  assert.throws(
    () => validateLoginHistoryCurrentAccount(validSource()
      .replace('item.user.userId === currentUserId && ', '')),
    /compare both user and server/
  )
  assert.throws(
    () => validateLoginHistoryCurrentAccount(validSource()
      .replace(' && item.server.id === currentServerId', '')),
    /compare both user and server/
  )
})

test('requires all three row decisions to use the shared helper', () => {
  assert.throws(
    () => validateLoginHistoryCurrentAccount(validSource()
      .replace('if (!this.isCurrentAccount(item as ServerUser))', 'if (true)')),
    /all three account-row decisions/
  )
})
