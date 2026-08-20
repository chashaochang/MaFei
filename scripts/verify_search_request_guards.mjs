import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

export const searchRequestGuardPaths = {
  viewModel: 'entry/src/main/ets/features/search/SearchViewModel.ets',
  uiState: 'entry/src/main/ets/features/search/SearchUIState.ets',
  page: 'entry/src/main/ets/features/search/SearchPage.ets'
}

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
    '\\b' + methodName + '\\s*\\([^)]*\\)\\s*(?::\\s*[^\\{]+)?\\s*\\{'
  )
  const match = signature.exec(source)
  if (!match) {
    throw new Error('missing method: ' + methodName)
  }
  return bracedBlock(source, source.indexOf('{', match.index))
}

function requirePattern(source, pattern, message) {
  if (!pattern.test(source)) {
    throw new Error(message)
  }
}

function rejectPattern(source, pattern, message) {
  if (pattern.test(source)) {
    throw new Error(message)
  }
}

function requireMatchCount(source, pattern, expected, message) {
  const matches = source.match(pattern) || []
  if (matches.length !== expected) {
    throw new Error(`${message}; expected ${expected}, got ${matches.length}`)
  }
}

function requireEmptyQueryBoundary(search) {
  const emptyIndex = search.indexOf("if (query === '')")
  const clearIndex = search.indexOf('this.clearResults()', emptyIndex)
  const returnIndex = search.indexOf('return', clearIndex)
  const requestIndex = search.indexOf('catalog.search(')
  if (emptyIndex < 0 || clearIndex < emptyIndex || returnIndex < clearIndex ||
    requestIndex < returnIndex) {
    throw new Error('empty query must clear and return before catalog search')
  }
}

export function validateSearchRequestGuards(sources) {
  const viewModel = requiredSource(sources, searchRequestGuardPaths.viewModel)
  const uiState = requiredSource(sources, searchRequestGuardPaths.uiState)
  const page = requiredSource(sources, searchRequestGuardPaths.page)

  requirePattern(viewModel, /private\s+requestGeneration:\s*number\s*=\s*0/,
    'search request generation is missing')

  const search = methodBlock(viewModel, 'search')
  requirePattern(search, /const\s+query\s*=\s*value\.trim\(\)/,
    'search must normalize the query once')
  requirePattern(search, /const\s+generation\s*=\s*\+\+this\.requestGeneration/,
    'search must claim a request generation')
  requireEmptyQueryBoundary(search)
  requirePattern(search, /catalog\s*=\s*currentMediaCatalogProvider\s*\(\s*\)/,
    'search must capture the active catalog provider')
  requirePattern(search, /request\s*=\s*catalog\.search\s*\(\s*\{/,
    'search must use the provider-neutral catalog search')
  rejectPattern(search, /getItemsApi|ApiClient\.Instance\s*\(/,
    'search must not bypass the provider-neutral catalog')
  requirePattern(search, /if\s*\(\s*!this\.isCurrent\s*\(\s*catalog\s*,\s*generation\s*\)\s*\)/,
    'search success must reject stale provider requests')
  requirePattern(search, /this\.finishSearchFailure\s*\(\s*generation\s*,\s*catalog\s*\)/,
    'search failure must retain the captured provider')
  requirePattern(search, /\.catch\s*\(/,
    'search must handle request failures')
  requirePattern(search, /PageState\.Loading/,
    'search loading state is missing')
  requirePattern(search, /PageState\.Content/,
    'search content state is missing')
  requirePattern(search, /this\.saveHistory\(query\)/,
    'submitted history must use the normalized current query')

  const cancel = methodBlock(viewModel, 'cancelPendingSearch')
  requirePattern(cancel, /this\.requestGeneration\s*\+=\s*1/,
    'cancelPendingSearch must invalidate the active generation')
  requirePattern(cancel, /this\.catalog\s*=\s*undefined/,
    'cancelPendingSearch must release the captured provider')

  const failure = methodBlock(viewModel, 'finishSearchFailure')
  requirePattern(failure, /generation\s*!==\s*this\.requestGeneration/,
    'search failure must reject stale generations')
  requirePattern(failure, /!this\.isCurrent\s*\(\s*catalog\s*,\s*generation\s*\)/,
    'search failure must reject a stale provider')
  requirePattern(failure, /PageState\.Error/,
    'search error state is missing')

  const current = methodBlock(viewModel, 'isCurrent')
  requirePattern(current, /MediaProviderRegistry\.instance\s*\(\s*\)\.getActiveSession\s*\(\s*\)/,
    'search guard must read the active provider session')
  requirePattern(current, /this\.catalog\s*===\s*catalog/,
    'search guard must retain provider identity')
  requirePattern(current, /generation\s*===\s*this\.requestGeneration/,
    'search guard must retain request generation')
  requirePattern(current, /activeSession\.accountScope\s*===\s*catalog\.session\.accountScope/,
    'search guard must retain the active account scope')

  const retry = methodBlock(viewModel, 'retrySearch')
  requirePattern(retry, /this\.search\(this\.ui\.searchText\)/,
    'retrySearch must retry the visible query')

  const clear = methodBlock(viewModel, 'clearResults')
  requirePattern(clear, /this\.dataSource\.deleteAll\(\)/,
    'clearResults must clear the RefreshDataSource')
  requirePattern(clear, /this\.ui\.totalCount\s*=\s*''/,
    'clearResults must clear the result count')

  requirePattern(uiState, /@Trace\s+errorMessage:\s*string\s*=\s*''/,
    'search error message state is missing')
  rejectPattern(uiState, /\bsearchResults\b/,
    'unused searchResults state must be removed')
  rejectPattern(uiState, /\bBaseItemDto\b/,
    'unused BaseItemDto import must be removed')

  requirePattern(page,
    /import\s*\{\s*PageState\s*\}\s*from\s*["']\.\.\/\.\.\/common\/base\/BaseUIState["']/,
    'SearchPage must import PageState')
  requirePattern(page,
    /import\s*\{\s*FailedView\s*\}\s*from\s*["']\.\.\/\.\.\/component\/FailedView["']/,
    'SearchPage must import FailedView')

  const disappear = methodBlock(page, 'aboutToDisappear')
  requirePattern(disappear, /this\.vm\.cancelPendingSearch\(\)/,
    'page exit must invalidate pending search')
  rejectPattern(disappear, /saveHistory\(/,
    'page exit must not save an unconfirmed query')
  rejectPattern(page, /searchResults\s*=\s*\[\]/,
    'SearchPage must not clear an unused result array')
  requirePattern(page, /PageState\.Loading[\s\S]*LoadingProgress/,
    'SearchPage loading state is missing')
  requirePattern(page, /PageState\.Error[\s\S]*FailedView\(\)/,
    'SearchPage error state is missing')
  requirePattern(page, /FailedView\(\)[\s\S]*this\.vm\.retrySearch\(\)/,
    'SearchPage error state must retry')
  requirePattern(page, /\.onChange\([\s\S]*this\.vm\.search\(value\)/,
    'SearchPage changes must delegate every query to SearchViewModel')
  requirePattern(page, /\.onSubmit\([\s\S]*this\.vm\.search\(value,\s*true\)/,
    'SearchPage submit must request history persistence')
  requirePattern(page,
    /left:\s*AppThemeTokens\.pageHorizontalPadding\s*\(\s*this\.vm\.appUIState\.themeStyle\s*,\s*this\.vm\.appUIState\.isBigScreen\s*\)/,
    'SearchPage must preserve Native left padding ownership')
  requirePattern(page,
    /right:\s*AppThemeTokens\.pageHorizontalPadding\s*\(\s*this\.vm\.appUIState\.themeStyle\s*,\s*this\.vm\.appUIState\.isBigScreen\s*\)/,
    'SearchPage must preserve Native right padding ownership')
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function workspaceSources(root = defaultWorkspaceRoot()) {
  const sources = new Map()
  for (const path of Object.values(searchRequestGuardPaths)) {
    sources.set(path, readFileSync(resolve(root, path), 'utf8'))
  }
  return sources
}

export function validateWorkspace(root = defaultWorkspaceRoot()) {
  validateSearchRequestGuards(workspaceSources(root))
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspace()
  console.log('Search request guards verified')
}
