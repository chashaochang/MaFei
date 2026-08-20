import assert from 'node:assert/strict'
import { dirname, resolve } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  defaultWorkspaceRoot,
  validateLoginHistoryCurrentAccount,
  validateWorkspace
} from './verify_login_history_current_account.mjs'

function validPageSource() {
  return `
    struct AccountPage {
      fromLogin: boolean = false

      aboutToAppear(): void {
        this.vm.getAccountList(!this.fromLogin)
      }

      private onAccountClick(item: AccountRowViewModel): void {
        if (item.isCurrent) { return }
      }

      private accountRowContent(item: AccountRowViewModel) {
        if (item.isCurrent) { Text('current') }
        else if (!item.isCurrent) { Image('delete') }
      }
    }
  `
}

function validViewModelSource() {
  return `
    class AccountViewModel {
      private markCurrentAccount: boolean = true

      async getAccountList(markCurrentAccount?: boolean): Promise<void> {
        if (markCurrentAccount !== undefined) {
          this.markCurrentAccount = markCurrentAccount
        }
        const current = this.markCurrentAccount ?
          composition.coordinator().currentSelection() : undefined
      }

      async deleteAccount(row: AccountRowViewModel): Promise<boolean> {
        await this.getAccountList()
        return true
      }

      private toRow(account: MediaProviderAccount,
        current?: CurrentMediaAccount): AccountRowViewModel {
        const isCurrent = current !== undefined && current.provider === account.provider &&
          current.providerAccountId === account.providerAccountId
        return new AccountRowViewModel(isCurrent)
      }

      private async changeJellyfinAccount(providerAccountId: string): Promise<boolean> {
        const prepared = await this.prepareJellyfinAccount(stored)
        if (!prepared) { return false }
        const previous = this.captureLegacyJellyfinSession()
        try {
          await this.apiClientController.logout()
          await this.apiClientController.setupServer(prepared.address)
          await composition.coordinator().mirrorLegacyJellyfinSession()
          return true
        } catch (_error) {
          this.restoreLegacyJellyfinSession(previous)
          return false
        }
      }

      private async prepareJellyfinAccount(stored: ServerUser): Promise<Prepared | undefined> {
        const state = await connectionHelper.checkServerUrl(stored.server.hostname)
        const validationApi = this.createValidationApi(state.address)
        await getSystemApi(validationApi).getSystemInfo()
        return prepared
      }

      private createValidationApi(address: string): Api {
        return jellyfin.createApi(address)
      }

      private captureLegacyJellyfinSession(): Snapshot {
        return previous
      }

      private restoreLegacyJellyfinSession(snapshot: Snapshot): void {
        preference.setCurrentServerId(snapshot.serverId)
        preference.setCurrentServerSystemId(snapshot.serverSystemId)
        preference.setCurrentUserId(snapshot.userId)
        preference.setAccessToken(snapshot.accessToken)
        preference.setUserName(snapshot.username)
        ApiClient.update(snapshot.apiBasePath)
        AppStorage.setOrCreate('server', snapshot.server)
      }
    }
  `
}

test('accepts neutral current-account semantics for all row decisions', () => {
  assert.doesNotThrow(() => validateLoginHistoryCurrentAccount(
    validPageSource(), validViewModelSource()))
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
    () => validateLoginHistoryCurrentAccount(
      validPageSource() + '\nApiClient.Instance()', validViewModelSource()),
    /global ApiClient/
  )
  assert.throws(
    () => validateLoginHistoryCurrentAccount(
      validPageSource(),
      validViewModelSource().replace('const isCurrent =', 'ApiClient.Instance()\nconst isCurrent =')),
    /global ApiClient/
  )
})

test('requires login history to have no current account', () => {
  assert.throws(
    () => validateLoginHistoryCurrentAccount(
      validPageSource().replace('!this.fromLogin', 'true'), validViewModelSource()),
    /login history/
  )
})

test('requires the ViewModel to retain and apply the display mode', () => {
  assert.throws(
    () => validateLoginHistoryCurrentAccount(
      validPageSource(),
      validViewModelSource().replace('this.markCurrentAccount = markCurrentAccount', 'return')),
    /retain|suppress/
  )
  assert.throws(
    () => validateLoginHistoryCurrentAccount(
      validPageSource(),
      validViewModelSource().replace('this.markCurrentAccount ?', 'true ?')),
    /suppress/
  )
})

test('requires both neutral identifiers in the account comparison', () => {
  assert.throws(
    () => validateLoginHistoryCurrentAccount(
      validPageSource(),
      validViewModelSource().replace('current.provider === account.provider &&\n          ', '')),
    /provider and providerAccountId/
  )
  assert.throws(
    () => validateLoginHistoryCurrentAccount(
      validPageSource(),
      validViewModelSource().replace(' &&\n          current.providerAccountId === account.providerAccountId', '')),
    /provider and providerAccountId/
  )
})

test('requires all three row decisions to use the neutral row flag', () => {
  assert.throws(
    () => validateLoginHistoryCurrentAccount(
      validPageSource().replace('else if (!item.isCurrent)', 'else if (true)'),
      validViewModelSource()),
    /all three account-row decisions|badge, delete action, and row click/
  )
})

test('requires deletion refresh to preserve the history mode', () => {
  assert.throws(
    () => validateLoginHistoryCurrentAccount(
      validPageSource(),
      validViewModelSource().replace('await this.getAccountList()', 'await this.getAccountList(true)')),
    /deletion refresh/
  )
})

test('requires Jellyfin switching to preflight before mutation and roll back failures', () => {
  assert.throws(
    () => validateLoginHistoryCurrentAccount(
      validPageSource(),
      validViewModelSource().replace(
        'const prepared = await this.prepareJellyfinAccount(stored)',
        'const prepared = stored')),
    /target validation/
  )
  assert.throws(
    () => validateLoginHistoryCurrentAccount(
      validPageSource(),
      validViewModelSource().replace(
        'this.restoreLegacyJellyfinSession(previous)',
        'return false')),
    /restore the previous global session/
  )
  assert.throws(
    () => validateLoginHistoryCurrentAccount(
      validPageSource(),
      validViewModelSource().replace(
        'private async changeJellyfinAccount(providerAccountId: string): Promise<boolean> {',
        'private async changeJellyfinAccount(providerAccountId: string): Promise<boolean> {\n' +
        'this.appUIState.isLogin = false')),
    /must not clear login state/
  )
})

test('requires Jellyfin preflight to use an isolated API client', () => {
  assert.throws(
    () => validateLoginHistoryCurrentAccount(
      validPageSource(),
      validViewModelSource().replace(
        'return jellyfin.createApi(address)',
        'return ApiClient.update(address)')),
    /isolated API instance/
  )
})
