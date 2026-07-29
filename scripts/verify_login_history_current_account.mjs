import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const ACCOUNT_PAGE = 'entry/src/main/ets/features/setting/account/AccountPage.ets'

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

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function validateLoginHistoryCurrentAccount(source) {
  if (/\bApiClient\b/.test(source)) {
    throw new Error('AccountPage must not depend on the global ApiClient')
  }

  const helper = methodBlock(source, 'isCurrentAccount')
  if (!/if\s*\(\s*this\.fromLogin\s*\)\s*\{[\s\S]*?return\s+false/.test(helper)) {
    throw new Error('login history must never mark an account as current')
  }
  if (!/const\s+currentUserId\s*=\s*preference\.getCurrentUserId\s*\(\s*\)/.test(helper) ||
    !/const\s+currentServerId\s*=\s*preference\.getCurrentServerId\s*\(\s*\)/.test(helper)) {
    throw new Error('current account must come from persisted user and server identifiers')
  }
  if (!/currentUserId\.length\s*>\s*0/.test(helper) ||
    !/currentServerId\s*!==\s*null/.test(helper)) {
    throw new Error('persisted current account identifiers must be valid before comparison')
  }
  if (!/item\.user\.userId\s*===\s*currentUserId/.test(helper) ||
    !/item\.server\.id\s*===\s*currentServerId/.test(helper)) {
    throw new Error('current account must compare both user and server identifiers')
  }

  const calls = source.match(/this\.isCurrentAccount\s*\(/g)?.length ?? 0
  if (calls !== 3) {
    throw new Error('all three account-row decisions must use isCurrentAccount')
  }
  if ((source.match(/if\s*\(\s*this\.isCurrentAccount\s*\(/g)?.length ?? 0) !== 2 ||
    (source.match(/if\s*\(\s*!\s*this\.isCurrentAccount\s*\(/g)?.length ?? 0) !== 1) {
    throw new Error('badge, delete action, and row click must share current-account semantics')
  }
}

export function validateWorkspace(root = defaultWorkspaceRoot()) {
  validateLoginHistoryCurrentAccount(readFileSync(resolve(root, ACCOUNT_PAGE), 'utf8'))
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspace()
  console.log('Login history current-account guards verified')
}
