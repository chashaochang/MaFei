import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const APP_PREFERENCE = 'entry/src/main/ets/data/AppPreference.ets'
const ENTRY_ABILITY = 'entry/src/main/ets/entryability/EntryAbility.ets'
const CONNECTION_VIEW_MODEL = 'entry/src/main/ets/features/connect/ConnectionViewModel.ets'
const CONNECT_SCREEN = 'entry/src/main/ets/features/connect/ConnectScreen.ets'
const INDEX_PAGE = 'entry/src/main/ets/features/splash/IndexPage.ets'
const RECOMMENDED_DISCOVERY = 'sdk/src/main/ets/discovery/recommended-server-discovery.ts'
const API_CLIENT = 'entry/src/main/ets/network/ApiClient.ets'

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
    '\\b' + methodName + '(?:\\s*<[^>]+>)?\\s*\\([^)]*\\)\\s*(?::\\s*[^\\{]+)?\\s*\\{'
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

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function validateLoginConnectionGuards(sources) {
  const preference = requiredSource(sources, APP_PREFERENCE)
  const entryAbility = requiredSource(sources, ENTRY_ABILITY)
  const connection = requiredSource(sources, CONNECTION_VIEW_MODEL)
  const connectScreen = requiredSource(sources, CONNECT_SCREEN)
  const indexPage = requiredSource(sources, INDEX_PAGE)
  const discovery = requiredSource(sources, RECOMMENDED_DISCOVERY)
  const apiClient = requiredSource(sources, API_CLIENT)

  if (/getContext\s*\(\s*this\s*\)/.test(preference)) {
    throw new Error('AppPreference must not resolve context from a plain class')
  }
  if (!/private\s+context\s*:\s*common\.Context/.test(preference) ||
    !/initialize\s*\(\s*context\s*:\s*common\.Context\s*\)/.test(preference)) {
    throw new Error('AppPreference must retain explicit production initialization')
  }
  const getInstance = methodBlock(preference, 'getInstance')
  if (!/throw\s+new\s+Error/.test(getInstance) || /new\s+AppPreference/.test(getInstance)) {
    throw new Error('getInstance must fail explicitly before initialization')
  }

  const onCreate = methodBlock(entryAbility, 'onCreate')
  requireBefore(
    onCreate,
    'AppPreference.initialize(this.context.getApplicationContext())',
    'this.appUIState.context = this.context',
    'AppPreference must initialize before other EntryAbility startup work'
  )

  const discoveryCall = /getPublicSystemInfo\s*\(\s*\{([\s\S]*?)\}\s*\)/.exec(discovery)
  if (!discoveryCall) {
    throw new Error('recommended discovery must pass an explicit request config')
  }
  for (const timeout of ['timeout', 'connectTimeout', 'readTimeout']) {
    if (!new RegExp('\\b' + timeout + '\\s*:\\s*HTTP_TIMEOUT\\b').test(discoveryCall[1])) {
      throw new Error('recommended discovery is missing ' + timeout)
    }
  }

  if (/loginAttemptEpoch|withDeadline|LoginDeadlineError|temporaryApi|jellyfin\.createApi/.test(connection)) {
    throw new Error('login must not use the rejected timeout, epoch, or temporary-client rewrite')
  }
  const onLogin = methodBlock(connection, 'onLogin')
  if (!/connectionHelper\.checkServerUrl\s*\(\s*this\.ui\.hostname\s*\)/.test(onLogin)) {
    throw new Error('login must verify the submitted server address')
  }
  requireBefore(
    onLogin,
    'this.apiClientController.setupServer',
    'this.loginByPwd',
    'the application API client must be configured before password authentication'
  )

  const loginByPwd = methodBlock(connection, 'loginByPwd')
  if (!/getUserApi\s*\(\s*ApiClient\.Instance\s*\(\s*\)\s*\)\.authenticateUserByName/.test(loginByPwd)) {
    throw new Error('password authentication must use the configured application API client')
  }
  requireBefore(
    loginByPwd,
    'this.apiClientController.setupUser',
    'preference.setAccessToken',
    'access token must be the final persisted login marker'
  )
  if (!/status\s*===\s*400\s*\|\|\s*status\s*===\s*401/.test(loginByPwd) ||
    !/INVALID_CREDENTIALS_TEXT/.test(loginByPwd)) {
    throw new Error('authentication 400/401 must map to invalid credentials')
  }
  if (!/axiosError\.isAxiosError\s*===\s*true\s*&&\s*!axiosError\.response/.test(loginByPwd) ||
    !/CONNECTION_ERROR_TEXT/.test(loginByPwd) ||
    !/else\s*\{[\s\S]*LOGIN_FAILED_TEXT/.test(loginByPwd)) {
    throw new Error('authentication transport and other HTTP failures must remain distinct')
  }
  if (/console\.(?:log|error|warn)[^\n]*(?:password|AccessToken)|JSON\.stringify\([^\n]*(?:password|AccessToken)/i
    .test(connection)) {
    throw new Error('login secrets must not be logged')
  }

  if (/aboutToDisappear\s*\([^)]*\)\s*(?::\s*[^\{]+)?\s*\{[\s\S]*cancelPendingLogin/.test(connectScreen) ||
    /cancelPendingLogin/.test(connection)) {
    throw new Error('ConnectScreen must not cancel the active login during overlay transitions')
  }
  if (/if\s*\(\s*!this\.appUIState\.isConnecting\s*\)\s*\{\s*ConnectScreen\s*\(\s*\)/.test(indexPage) ||
    !/else\s*\{\s*ConnectScreen\s*\(\s*\)[\s\S]*ConnectingScreen/.test(indexPage)) {
    throw new Error('IndexPage must keep ConnectScreen mounted beneath the connecting overlay')
  }

  if (!/static\s+isInitialized\s*\(\s*\)/.test(apiClient)) {
    throw new Error('ApiClient management initialization guard must be preserved')
  }
  if (!/AuthenticateByName/.test(apiClient) || !/hasRequestAccessToken/.test(apiClient) ||
    !/status\s*===\s*401\s*&&\s*!isPasswordLogin\s*&&\s*hasRequestAccessToken/.test(apiClient)) {
    throw new Error('ApiClient must only emit token expiry for authenticated non-login requests')
  }
  if (!/return\s+Promise\.reject\s*\(\s*error\s*\)/.test(apiClient)) {
    throw new Error('ApiClient response errors must remain rejected')
  }
}

export function validateWorkspace(root = defaultWorkspaceRoot()) {
  const sources = new Map()
  for (const path of [
    APP_PREFERENCE,
    ENTRY_ABILITY,
    CONNECTION_VIEW_MODEL,
    CONNECT_SCREEN,
    INDEX_PAGE,
    RECOMMENDED_DISCOVERY,
    API_CLIENT
  ]) {
    sources.set(path, readFileSync(resolve(root, path), 'utf8'))
  }
  validateLoginConnectionGuards(sources)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspace()
  console.log('Login connection guards verified')
}
