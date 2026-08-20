import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const ACCOUNT_PAGE = 'entry/src/main/ets/features/setting/account/AccountPage.ets'
const ACCOUNT_VIEW_MODEL = 'entry/src/main/ets/features/setting/account/AccountViewModel.ets'

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
    '\\b(?:private\\s+)?' + methodName + '\\s*\\([^)]*\\)\\s*(?::\\s*[^\\{]+)?\\s*\\{'
  )
  const match = signature.exec(source)
  if (!match) {
    throw new Error('missing method: ' + methodName)
  }
  return bracedBlock(source, source.indexOf('{', match.index))
}

function requireBefore(source, earlier, later, message) {
  const earlierIndex = source.indexOf(earlier)
  const laterIndex = source.indexOf(later)
  if (earlierIndex < 0 || laterIndex < 0 || earlierIndex >= laterIndex) {
    throw new Error(message)
  }
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function validateLoginHistoryCurrentAccount(pageSource, viewModelSource) {
  if (/\bApiClient\b/.test(pageSource)) {
    throw new Error('AccountPage must not depend on the global ApiClient')
  }

  const aboutToAppear = methodBlock(pageSource, 'aboutToAppear')
  if (!/this\.vm\.getAccountList\s*\(\s*!\s*this\.fromLogin\s*\)/.test(aboutToAppear)) {
    throw new Error('login history must disable current-account marking')
  }

  const accountClick = methodBlock(pageSource, 'onAccountClick')
  const rowContent = methodBlock(pageSource, 'accountRowContent')
  if (!/if\s*\(\s*item\.isCurrent\s*\)\s*\{[\s\S]*?return/.test(accountClick) ||
    !/if\s*\(\s*item\.isCurrent\s*\)/.test(rowContent) ||
    !/else\s+if\s*\(\s*!\s*item\.isCurrent\s*\)/.test(rowContent)) {
    throw new Error('badge, delete action, and row click must use AccountRowViewModel.isCurrent')
  }

  if (!/private\s+markCurrentAccount\s*:\s*boolean\s*=\s*true/.test(viewModelSource) ||
    !/getAccountList\s*\(\s*markCurrentAccount\?\s*:\s*boolean\s*\)/.test(viewModelSource)) {
    throw new Error('AccountViewModel must retain the current-account display mode')
  }
  const getAccountList = methodBlock(viewModelSource, 'getAccountList')
  if (!/if\s*\(\s*markCurrentAccount\s*!==\s*undefined\s*\)\s*\{[\s\S]*?this\.markCurrentAccount\s*=\s*markCurrentAccount/.test(getAccountList) ||
    !/const\s+current\s*=\s*this\.markCurrentAccount\s*\?\s*[\s\S]*?currentSelection\s*\(\s*\)\s*:\s*undefined/.test(getAccountList)) {
    throw new Error('login-history mode must suppress the current selection before rows are built')
  }

  const toRow = methodBlock(viewModelSource, 'toRow')
  if (/\bApiClient\b/.test(toRow)) {
    throw new Error('current-account comparison must not depend on the global ApiClient')
  }
  if (!/current\.provider\s*===\s*account\.provider/.test(toRow) ||
    !/current\.providerAccountId\s*===\s*account\.providerAccountId/.test(toRow)) {
    throw new Error('current account must compare provider and providerAccountId')
  }

  const deleteAccount = methodBlock(viewModelSource, 'deleteAccount')
  if (!/await\s+this\.getAccountList\s*\(\s*\)/.test(deleteAccount)) {
    throw new Error('deletion refresh must preserve the current-account display mode')
  }

  const changeJellyfinAccount = methodBlock(viewModelSource, 'changeJellyfinAccount')
  if (/this\.appUIState\.isLogin\s*=\s*false/.test(changeJellyfinAccount)) {
    throw new Error('Jellyfin account switching must not clear login state before activation succeeds')
  }
  requireBefore(changeJellyfinAccount,
    'await this.prepareJellyfinAccount', 'this.captureLegacyJellyfinSession',
    'Jellyfin target validation must finish before current state is snapshotted')
  requireBefore(changeJellyfinAccount,
    'this.captureLegacyJellyfinSession', 'await this.apiClientController.logout()',
    'Jellyfin current state must be snapshotted before logout')
  requireBefore(changeJellyfinAccount,
    'await this.apiClientController.logout()', 'await this.apiClientController.setupServer',
    'Jellyfin logout must finish before target server setup')
  requireBefore(changeJellyfinAccount,
    'await this.apiClientController.setupServer', 'mirrorLegacyJellyfinSession',
    'Jellyfin target setup must finish before provider activation')
  if (!/catch\s*\([^)]*\)\s*\{[\s\S]*?this\.restoreLegacyJellyfinSession\s*\(\s*previous\s*\)/.test(
    changeJellyfinAccount)) {
    throw new Error('Jellyfin switch failures must restore the previous global session')
  }

  const prepareJellyfinAccount = methodBlock(viewModelSource, 'prepareJellyfinAccount')
  if (!/await\s+connectionHelper\.checkServerUrl/.test(prepareJellyfinAccount) ||
    !/getSystemApi\s*\(\s*validationApi\s*\)\.getSystemInfo/.test(prepareJellyfinAccount) ||
    /ApiClient\.(?:Instance|update)\s*\(/.test(prepareJellyfinAccount)) {
    throw new Error('Jellyfin preflight must validate the target without mutating the global ApiClient')
  }

  const createValidationApi = methodBlock(viewModelSource, 'createValidationApi')
  if (!/jellyfin\.createApi\s*\(/.test(createValidationApi) ||
    /ApiClient\.(?:Instance|update)\s*\(/.test(createValidationApi)) {
    throw new Error('Jellyfin preflight must use an isolated API instance')
  }

  const restoreLegacyJellyfinSession = methodBlock(viewModelSource, 'restoreLegacyJellyfinSession')
  for (const marker of [
    'preference.setCurrentServerId',
    'preference.setCurrentServerSystemId',
    'preference.setCurrentUserId',
    'preference.setAccessToken',
    'preference.setUserName',
    'ApiClient.update',
    "AppStorage.setOrCreate('server'"
  ]) {
    if (!restoreLegacyJellyfinSession.includes(marker)) {
      throw new Error('Jellyfin rollback must restore preferences, API runtime, and AppStorage')
    }
  }
}

export function validateWorkspace(root = defaultWorkspaceRoot()) {
  validateLoginHistoryCurrentAccount(
    readFileSync(resolve(root, ACCOUNT_PAGE), 'utf8'),
    readFileSync(resolve(root, ACCOUNT_VIEW_MODEL), 'utf8')
  )
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspace()
  console.log('Login history current-account guards verified')
}
