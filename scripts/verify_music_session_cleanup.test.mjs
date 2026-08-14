import assert from 'node:assert/strict'
import { dirname, resolve } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  defaultWorkspaceRoot,
  validateMusicSessionCleanup,
  validateWorkspace
} from './verify_music_session_cleanup.mjs'

const guardPath = 'entry/src/main/ets/features/music/MusicSessionGuard.ets'
const controllerPath = 'entry/src/main/ets/data/ApiClientController.ets'
const connectionPath = 'entry/src/main/ets/features/connect/ConnectionViewModel.ets'
const minePath = 'entry/src/main/ets/features/home/minetab/MineViewModel.ets'
const accountPath = 'entry/src/main/ets/features/setting/account/AccountViewModel.ets'
const indexPath = 'entry/src/main/ets/features/splash/IndexPage.ets'
const webViewPath = 'entry/src/main/ets/webapp/WebViewScreen.ets'

function validCleanupFixture() {
  return new Map([
    [guardPath, `
      class MusicSessionGuard {
        private static pending?: Promise<void>
        static stopAndClear(): Promise<void> {
          if (!MusicSessionGuard.pending) {
            MusicSessionGuard.pending = MusicPlaybackController.instance().stopAndClear().finally(() => {
              MusicSessionGuard.pending = undefined
            })
          }
          return MusicSessionGuard.pending
        }
      }
    `],
    [controllerPath, `
      class ApiClientController {
        async setupServer(): Promise<void> {
          if (this.serverIdentityChanges()) {
            await MusicSessionGuard.stopAndClear()
          }
          this.serverDao.getServerByHostname(hostname)
        }
        async setupUser(): Promise<void> {
          if (this.userScopeChanges()) {
            await MusicSessionGuard.stopAndClear()
          }
          this.userDao.upsert(serverId, userId, accessToken)
        }
        async logout(): Promise<void> {
          await MusicSessionGuard.stopAndClear()
          preference.setAccessToken('')
          preference.setCurrentUserId('')
          preference.setUserName('')
          preference.setCurrentServerSystemId('')
          webview.WebviewController.removeAllCache(true)
        }
      }
    `],
    [connectionPath, `
      await this.apiClientController.setupServer()
      await this.apiClientController.setupUser()
    `],
    [minePath, `
      class MineViewModel {
        async logout(): Promise<void> {
          await this.apiClientController.logout()
          this.appUIState.isLogin = false
        }
      }
    `],
    [accountPath, `
      class AccountViewModel {
        async changeAccount(): Promise<void> {
          await this.apiClientController.logout()
          HMRouterMgr.to('/IndexPage').pop()
          await this.apiClientController.setupServer()
          await this.apiClientController.setupUser()
        }
        async loginByPwd(): Promise<void> {
          await this.apiClientController.setupUser()
          HMRouterMgr.to('/IndexPage').replace()
        }
      }
    `],
    [indexPath, `
      await this.apiClientController.logout()
      await this.apiClientController.setupServer()
      await this.apiClientController.setupUser()
    `],
    [webViewPath, `
      await this.apiClientController.setupUser()
      this.controller.loadUrl(ApiClient.Instance().basePath)
      this.isLogin = true
    `]
  ])
}

test('accepts cleanup before logout and identity mutation', () => {
  assert.doesNotThrow(() => validateMusicSessionCleanup(validCleanupFixture()))
})

test('rejects cleanup after credential mutation', () => {
  const fixture = validCleanupFixture()
  fixture.set(controllerPath, fixture.get(controllerPath).replace(
    "await MusicSessionGuard.stopAndClear()\n          preference.setAccessToken('')",
    "preference.setAccessToken('')\n          await MusicSessionGuard.stopAndClear()"
  ))
  assert.throws(() => validateMusicSessionCleanup(fixture), /stop music before credentials change/)
})

test('rejects unconditional setup cleanup', () => {
  const fixture = validCleanupFixture()
  fixture.set(controllerPath, fixture.get(controllerPath).replace(
    `if (this.serverIdentityChanges()) {
            await MusicSessionGuard.stopAndClear()
          }`,
    'await MusicSessionGuard.stopAndClear()'
  ))
  assert.throws(() => validateMusicSessionCleanup(fixture), /only clear music when identity changes/)
})

test('rejects a setup call that is not awaited', () => {
  const fixture = validCleanupFixture()
  fixture.set(connectionPath, fixture.get(connectionPath).replace(
    'await this.apiClientController.setupUser()',
    'this.apiClientController.setupUser()'
  ))
  assert.throws(() => validateMusicSessionCleanup(fixture), /must await setupUser/)
})

test('rejects login state committed before logout finishes', () => {
  const fixture = validCleanupFixture()
  fixture.set(minePath, fixture.get(minePath).replace(
    `await this.apiClientController.logout()
          this.appUIState.isLogin = false`,
    `this.appUIState.isLogin = false
          await this.apiClientController.logout()`
  ))
  assert.throws(() => validateMusicSessionCleanup(fixture), /before changing login state/)
})

test('derives the workspace root from the verifier location', () => {
  const scriptsDirectory = dirname(fileURLToPath(import.meta.url))
  assert.equal(defaultWorkspaceRoot(), resolve(scriptsDirectory, '..'))
})

test('validates the checked-out workspace', () => {
  assert.doesNotThrow(() => validateWorkspace())
})
