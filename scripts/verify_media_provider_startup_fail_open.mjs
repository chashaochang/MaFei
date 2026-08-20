import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const ENTRY_ABILITY = 'entry/src/main/ets/entryability/EntryAbility.ets'
const COMPOSITION = 'entry/src/main/ets/features/setting/account/MediaProviderComposition.ets'
const COORDINATOR = 'entry/src/main/ets/features/setting/account/MediaAccountSessionCoordinator.ets'
const ACCOUNT_VIEW_MODEL = 'entry/src/main/ets/features/setting/account/AccountViewModel.ets'

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
    '\\b(?:private\\s+)?(?:static\\s+)?(?:async\\s+)?' + methodName +
    '\\s*\\([^)]*\\)\\s*(?::\\s*[^\\{]+)?\\s*\\{'
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

export function validateMediaProviderStartupFailOpen(sources) {
  const entry = requiredSource(sources, ENTRY_ABILITY)
  const composition = requiredSource(sources, COMPOSITION)
  const coordinator = requiredSource(sources, COORDINATOR)
  const accountViewModel = requiredSource(sources, ACCOUNT_VIEW_MODEL)

  const initializeApplication = methodBlock(entry, 'initializeApplication')
  requireBefore(
    initializeApplication,
    'DBManager.getInstance().init(this.context)',
    'MediaProviderComposition.initialize(this.context)',
    'Jellyfin database initialization must remain first'
  )
  requireBefore(
    initializeApplication,
    'MediaProviderComposition.initialize(this.context)',
    'ImageKnife.getInstance().initFileCache',
    'image cache initialization must continue after provider composition'
  )
  if (!/if\s*\(\s*!providerComposition\.isFeiniuAvailable\s*\(\s*\)\s*\)/.test(initializeApplication) ||
    !/continuing with Jellyfin/.test(initializeApplication)) {
    throw new Error('EntryAbility must report Feiniu-only degradation')
  }

  const initialize = methodBlock(composition, 'initialize')
  if (!/MediaProviderComposition\.create\s*\(\s*context\s*\)[\s\S]*?\.catch\s*\(/.test(initialize) ||
    !/MediaProviderComposition\.createUnavailable\s*\(\s*context\s*,\s*error\s*\)/.test(initialize)) {
    throw new Error('composition initialization must fall back after a Feiniu factory failure')
  }
  if (!/if\s*\(\s*composition\.isFeiniuAvailable\s*\(\s*\)\s*\)\s*\{[\s\S]*?shared\s*=\s*composition/.test(initialize)) {
    throw new Error('failed Feiniu initialization must remain retryable')
  }
  const createUnavailable = methodBlock(composition, 'createUnavailable')
  if (!/new\s+UnavailableFeiniuVideoSessionPort\s*\(\s*error\s*\)/.test(createUnavailable) ||
    !/MediaAccountSessionCoordinator\.createWithPorts/.test(createUnavailable) ||
    !/new\s+MediaProviderComposition\s*\(\s*undefined\s*,\s*coordinator\s*,\s*error\s*\)/
      .test(createUnavailable)) {
    throw new Error('fallback composition must keep a Jellyfin-capable coordinator')
  }
  const loginFeiniu = methodBlock(composition, 'loginFeiniu')
  if (!/if\s*\(\s*!sessionService\s*\)\s*\{[\s\S]*?throw\s+this\.initializationFailure/.test(loginFeiniu) ||
    !/throw\s+new\s+Error/.test(loginFeiniu)) {
    throw new Error('Feiniu entry must preserve the initialization failure')
  }

  const createWithPorts = methodBlock(coordinator, 'createWithPorts')
  if (!/CurrentMediaAccountStore\.create\s*\(\s*context\s*\)/.test(createWithPorts) ||
    !/new\s+JellyfinActiveSessionSource\s*\(\s*\)/.test(createWithPorts) ||
    !/MediaProviderSelectionHistoryStore\.create\s*\(\s*context\s*\)/.test(createWithPorts)) {
    throw new Error('fallback coordinator must preserve account selection and Jellyfin mirroring')
  }

  const getAccountList = methodBlock(accountViewModel, 'getAccountList')
  requireBefore(
    getAccountList,
    'this.jellyfinRepository.listAccounts()',
    'composition.isFeiniuAvailable()',
    'Jellyfin accounts must remain available before optional Feiniu storage'
  )
  if (!/if\s*\(\s*composition\.isFeiniuAvailable\s*\(\s*\)\s*\)\s*\{[\s\S]*?FeiniuVideoAccountStore\.create/.test(getAccountList)) {
    throw new Error('account history must isolate optional Feiniu storage')
  }
  if ((getAccountList.match(/FeiniuVideoAccountStore\.create\s*\(/g)?.length ?? 0) !== 1) {
    throw new Error('account history must not open Feiniu storage outside the availability guard')
  }
}

export function validateWorkspace(root = defaultWorkspaceRoot()) {
  const sources = new Map()
  for (const path of [ENTRY_ABILITY, COMPOSITION, COORDINATOR, ACCOUNT_VIEW_MODEL]) {
    sources.set(path, readFileSync(resolve(root, path), 'utf8'))
  }
  validateMediaProviderStartupFailOpen(sources)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspace()
  console.log('Media provider startup fail-open verified')
}
