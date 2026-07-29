import assert from 'node:assert/strict'
import { dirname, resolve } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  defaultWorkspaceRoot,
  validateLoginConnectionGuards
} from './verify_login_connection_guards.mjs'

const appPreferencePath = 'entry/src/main/ets/data/AppPreference.ets'
const entryAbilityPath = 'entry/src/main/ets/entryability/EntryAbility.ets'
const connectionPath = 'entry/src/main/ets/features/connect/ConnectionViewModel.ets'
const connectScreenPath = 'entry/src/main/ets/features/connect/ConnectScreen.ets'
const indexPagePath = 'entry/src/main/ets/features/splash/IndexPage.ets'
const discoveryPath = 'sdk/src/main/ets/discovery/recommended-server-discovery.ts'
const apiClientPath = 'entry/src/main/ets/network/ApiClient.ets'

function validSources() {
  return new Map([
    [appPreferencePath, `
      class HarmonyPreferenceBackendFactory { private context: common.Context }
      class AppPreference {
        static initialize(context: common.Context): void {}
        static getInstance(): AppPreference {
          if (!AppPreference.instance) { throw new Error('not initialized') }
          return AppPreference.instance
        }
      }
    `],
    [entryAbilityPath, `
      class EntryAbility {
        onCreate(): void {
          AppPreference.initialize(this.context.getApplicationContext())
          this.appUIState.context = this.context
        }
      }
    `],
    [connectionPath, `
      class ConnectionViewModel {
        onLogin(): void {
          connectionHelper.checkServerUrl(this.ui.hostname)
          this.apiClientController.setupServer()
          this.loginByPwd()
        }
        loginByPwd(): void {
          getUserApi(ApiClient.Instance()).authenticateUserByName({})
          this.apiClientController.setupUser()
          preference.setAccessToken(accessToken)
          const status = axiosError.response?.status
          if (status === 400 || status === 401) {
            this.ui.errorText = INVALID_CREDENTIALS_TEXT
          } else if (axiosError.isAxiosError === true && !axiosError.response) {
            this.ui.errorText = CONNECTION_ERROR_TEXT
          } else {
            this.ui.errorText = LOGIN_FAILED_TEXT
          }
        }
      }
    `],
    [connectScreenPath, 'struct ConnectScreen { build() {} }'],
    [indexPagePath, 'if (isLogin) { HomeScreen() } else { ConnectScreen() ConnectingScreen() }'],
    [discoveryPath, `
      getSystemApi(api).getPublicSystemInfo({
        timeout: HTTP_TIMEOUT,
        connectTimeout: HTTP_TIMEOUT,
        readTimeout: HTTP_TIMEOUT
      })
    `],
    [apiClientPath, `
      class ApiClient {
        static isInitialized(): boolean { return true }
        static update(): void {
          const isPasswordLogin = requestUrl.includes('/Users/AuthenticateByName')
          const hasRequestAccessToken = true
          if (error.response?.status === 401 && !isPasswordLogin && hasRequestAccessToken) {
            emitter.emit(TokenExpiredEvent)
          }
          return Promise.reject(error)
        }
      }
    `]
  ])
}

test('accepts the focused login recovery contract', () => {
  assert.doesNotThrow(() => validateLoginConnectionGuards(validSources()))
})

test('derives the workspace root from the verifier location', () => {
  const scriptsDirectory = dirname(fileURLToPath(import.meta.url))
  assert.equal(defaultWorkspaceRoot(), resolve(scriptsDirectory, '..'))
})

test('rejects context lookup from a plain preference class', () => {
  const sources = validSources()
  sources.set(appPreferencePath, sources.get(appPreferencePath) + '\ngetContext(this)')
  assert.throws(() => validateLoginConnectionGuards(sources), /plain class/)
})

test('rejects preference initialization after startup state access', () => {
  const sources = validSources()
  sources.set(entryAbilityPath, sources.get(entryAbilityPath)
    .replace(
      'AppPreference.initialize(this.context.getApplicationContext())\n          this.appUIState.context = this.context',
      'this.appUIState.context = this.context\n          AppPreference.initialize(this.context.getApplicationContext())'
    ))
  assert.throws(() => validateLoginConnectionGuards(sources), /startup work/)
})

test('rejects the abandoned timeout and epoch rewrite', () => {
  const sources = validSources()
  sources.set(connectionPath, sources.get(connectionPath) + '\nloginAttemptEpoch\nwithDeadline')
  assert.throws(() => validateLoginConnectionGuards(sources), /rejected timeout/)
})

test('requires the configured application API client', () => {
  const sources = validSources()
  sources.set(connectionPath, sources.get(connectionPath)
    .replace('ApiClient.Instance()', 'otherApi'))
  assert.throws(() => validateLoginConnectionGuards(sources), /configured application API/)
})

test('requires the access token to be persisted last', () => {
  const sources = validSources()
  sources.set(connectionPath, sources.get(connectionPath)
    .replace(
      'this.apiClientController.setupUser()\n          preference.setAccessToken(accessToken)',
      'preference.setAccessToken(accessToken)\n          this.apiClientController.setupUser()'
    ))
  assert.throws(() => validateLoginConnectionGuards(sources), /final persisted login marker/)
})

test('keeps invalid credentials distinct from connection failures', () => {
  const sources = validSources()
  sources.set(connectionPath, sources.get(connectionPath)
    .replace('status === 400 || status === 401', 'status === 400'))
  assert.throws(() => validateLoginConnectionGuards(sources), /400\/401/)
})

test('rejects lifecycle cancellation of an active login', () => {
  const sources = validSources()
  sources.set(connectScreenPath,
    'struct ConnectScreen { aboutToDisappear() { this.vm.cancelPendingLogin() } }')
  assert.throws(() => validateLoginConnectionGuards(sources), /must not cancel/)
})

test('requires the login form to stay mounted beneath the overlay', () => {
  const sources = validSources()
  sources.set(indexPagePath,
    'if (isLogin) { HomeScreen() } else { if (!this.appUIState.isConnecting) { ConnectScreen() } ConnectingScreen() }')
  assert.throws(() => validateLoginConnectionGuards(sources), /keep ConnectScreen mounted/)
})

test('requires matching discovery timeouts', () => {
  const sources = validSources()
  sources.set(discoveryPath, sources.get(discoveryPath).replace('readTimeout: HTTP_TIMEOUT', ''))
  assert.throws(() => validateLoginConnectionGuards(sources), /missing readTimeout/)
})
