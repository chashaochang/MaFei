import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const MUSIC_SESSION_GUARD = 'entry/src/main/ets/features/music/MusicSessionGuard.ets'
const API_CLIENT_CONTROLLER = 'entry/src/main/ets/data/ApiClientController.ets'
const CONNECTION_VIEW_MODEL = 'entry/src/main/ets/features/connect/ConnectionViewModel.ets'
const MINE_VIEW_MODEL = 'entry/src/main/ets/features/home/minetab/MineViewModel.ets'
const ACCOUNT_VIEW_MODEL = 'entry/src/main/ets/features/setting/account/AccountViewModel.ets'
const INDEX_PAGE = 'entry/src/main/ets/features/splash/IndexPage.ets'
const WEB_VIEW_SCREEN = 'entry/src/main/ets/webapp/WebViewScreen.ets'

const WORKSPACE_SOURCES = [
  MUSIC_SESSION_GUARD,
  API_CLIENT_CONTROLLER,
  CONNECTION_VIEW_MODEL,
  MINE_VIEW_MODEL,
  ACCOUNT_VIEW_MODEL,
  INDEX_PAGE,
  WEB_VIEW_SCREEN
]

function requiredSource(sources, path) {
  const source = sources.get(path)
  if (source === undefined) {
    throw new Error('missing source: ' + path)
  }
  return source
}

function bracedBlock(source, openingBrace) {
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
  throw new Error('unterminated block')
}

function methodBlock(source, methodName) {
  const signature = new RegExp(
    '\\b(?:static\\s+)?(?:async\\s+)?' + methodName +
      '(?:\\s*<[^>]+>)?\\s*\\([^)]*\\)\\s*(?::\\s*[^\\{]+)?\\s*\\{'
  )
  const match = signature.exec(source)
  if (!match) {
    throw new Error('missing method: ' + methodName)
  }
  return bracedBlock(source, source.indexOf('{', match.index))
}

function requireBefore(source, first, second, message) {
  const firstIndex = source.indexOf(first)
  const secondIndex = source.indexOf(second)
  if (firstIndex < 0 || secondIndex < 0 || firstIndex >= secondIndex) {
    throw new Error(message)
  }
}

function requireLastBefore(source, first, second, message) {
  const firstIndex = source.lastIndexOf(first)
  const secondIndex = source.lastIndexOf(second)
  if (firstIndex < 0 || secondIndex < 0 || firstIndex >= secondIndex) {
    throw new Error(message)
  }
}

function countMatches(source, pattern) {
  return Array.from(source.matchAll(pattern)).length
}

function requireAllAwaited(source, target, message) {
  const escaped = target.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const calls = countMatches(source, new RegExp(escaped + '\\s*\\(', 'g'))
  const awaited = countMatches(source, new RegExp('await\\s+' + escaped + '\\s*\\(', 'g'))
  if (calls === 0 || calls !== awaited) {
    throw new Error(message)
  }
}

function requireConditionalCleanup(source, methodName) {
  const block = methodBlock(source, methodName)
  if (!/if\s*\([^)]*(?:IdentityChanges|ScopeChanges)\s*\([^)]*\)\s*\)\s*\{[\s\S]*await\s+MusicSessionGuard\.stopAndClear\s*\(\s*\)/.test(block)) {
    throw new Error(methodName + ' must only clear music when identity changes')
  }
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function validateMusicSessionCleanup(sources) {
  const guard = requiredSource(sources, MUSIC_SESSION_GUARD)
  const controller = requiredSource(sources, API_CLIENT_CONTROLLER)
  const connection = requiredSource(sources, CONNECTION_VIEW_MODEL)
  const mine = requiredSource(sources, MINE_VIEW_MODEL)
  const account = requiredSource(sources, ACCOUNT_VIEW_MODEL)
  const indexPage = requiredSource(sources, INDEX_PAGE)
  const webView = requiredSource(sources, WEB_VIEW_SCREEN)

  if (!/private\s+static\s+pending\??\s*:\s*Promise<void>/.test(guard)) {
    throw new Error('MusicSessionGuard must serialize cleanup with one pending promise')
  }
  const guardCleanup = methodBlock(guard, 'stopAndClear')
  if (!/MusicPlaybackController\.instance\s*\(\s*\)\s*\.stopAndClear\s*\(\s*\)/.test(guardCleanup) ||
    !/\.finally\s*\(/.test(guardCleanup) ||
    !/MusicSessionGuard\.pending\s*=\s*undefined/.test(guardCleanup)) {
    throw new Error('MusicSessionGuard must delegate once and reset the pending cleanup')
  }

  for (const methodName of ['logout', 'setupServer', 'setupUser']) {
    if (!new RegExp('async\\s+' + methodName + '\\s*\\(').test(controller)) {
      throw new Error(methodName + ' must be asynchronous')
    }
  }

  const logout = methodBlock(controller, 'logout')
  requireBefore(
    logout,
    'await MusicSessionGuard.stopAndClear()',
    "setAccessToken('')",
    'stop music before credentials change'
  )
  for (const mutation of [
    "setCurrentUserId('')",
    "setUserName('')",
    "setCurrentServerSystemId('')",
    'webview.WebviewController.removeAllCache(true)'
  ]) {
    requireBefore(logout, 'await MusicSessionGuard.stopAndClear()', mutation,
      'stop music before credentials change')
  }

  requireConditionalCleanup(controller, 'setupServer')
  requireConditionalCleanup(controller, 'setupUser')
  const setupServer = methodBlock(controller, 'setupServer')
  requireBefore(setupServer, 'await MusicSessionGuard.stopAndClear()', 'this.serverDao',
    'stop music before server identity mutation')
  const setupUser = methodBlock(controller, 'setupUser')
  requireBefore(setupUser, 'await MusicSessionGuard.stopAndClear()', 'this.userDao.upsert',
    'stop music before account identity mutation')

  requireAllAwaited(connection, 'this.apiClientController.setupServer',
    'ConnectionViewModel must await setupServer')
  requireAllAwaited(connection, 'this.apiClientController.setupUser',
    'ConnectionViewModel must await setupUser')

  const mineLogout = methodBlock(mine, 'logout')
  if (/logoutFeiniu/.test(mineLogout)) {
    if (!/const\s+loggedOut\s*=\s*await\s+composition\.coordinator\s*\(\s*\)\.logoutFeiniu/.test(mineLogout) ||
      !/if\s*\(\s*loggedOut\s*\)\s*\{[\s\S]*?this\.appUIState\.isLogin\s*=\s*false/.test(mineLogout)) {
      throw new Error('MineViewModel must await Feiniu logout before changing login state')
    }
  }
  requireLastBefore(mineLogout, 'await this.apiClientController.logout()', 'this.appUIState.isLogin = false',
    'MineViewModel must await logout before changing login state')

  requireAllAwaited(account, 'this.apiClientController.logout',
    'AccountViewModel must await logout')
  requireAllAwaited(account, 'this.apiClientController.setupServer',
    'AccountViewModel must await setupServer')
  requireAllAwaited(account, 'this.apiClientController.setupUser',
    'AccountViewModel must await setupUser')
  const changeAccount = methodBlock(account, 'changeAccount')
  if (/changeJellyfinAccount/.test(account)) {
    requireBefore(changeAccount, 'await this.changeJellyfinAccount', 'this.navigateHome()',
      'AccountViewModel must finish provider switching before replacing IndexPage')
    const changeJellyfinAccount = methodBlock(account, 'changeJellyfinAccount')
    requireBefore(changeJellyfinAccount, 'await this.apiClientController.logout()',
      'await this.apiClientController.setupServer',
      'AccountViewModel must stop music before replacing account credentials')
    const accountPasswordLogin = methodBlock(account, 'loginByStoredPassword')
    requireBefore(accountPasswordLogin, 'await this.apiClientController.setupUser', 'return true',
      'AccountViewModel must finish account setup before accepting stored credentials')
  } else {
    requireBefore(changeAccount, 'await this.apiClientController.logout()', 'HMRouterMgr.to',
      'AccountViewModel must stop music before replacing account credentials')
    const accountPasswordLogin = methodBlock(account, 'loginByPwd')
    requireBefore(accountPasswordLogin, 'await this.apiClientController.setupUser', 'HMRouterMgr.to',
      'AccountViewModel must finish account setup before replacing IndexPage')
  }

  requireAllAwaited(indexPage, 'this.apiClientController.logout',
    'IndexPage must await logout')
  requireAllAwaited(indexPage, 'this.apiClientController.setupServer',
    'IndexPage must await startup setupServer')
  requireAllAwaited(indexPage, 'this.apiClientController.setupUser',
    'IndexPage must await startup setupUser')

  requireAllAwaited(webView, 'this.apiClientController.setupUser',
    'WebViewScreen must await setupUser before using the authenticated account')
  for (const marker of ['this.isLogin = true', 'this.controller.loadUrl(ApiClient.Instance().basePath)']) {
    const markerIndex = webView.lastIndexOf(marker)
    const setupIndex = webView.lastIndexOf('await this.apiClientController.setupUser', markerIndex)
    if (markerIndex >= 0 && setupIndex < 0) {
      throw new Error('WebViewScreen must await setupUser before authenticated UI work')
    }
  }
}

export function validateWorkspace(root = defaultWorkspaceRoot()) {
  const sources = new Map()
  for (const path of WORKSPACE_SOURCES) {
    sources.set(path, readFileSync(resolve(root, path), 'utf8'))
  }
  validateMusicSessionCleanup(sources)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspace()
  console.log('Music session cleanup verified')
}
