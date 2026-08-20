import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

export const mediaLoadingPaths = Object.freeze({
  viewModel: 'entry/src/main/ets/features/home/mediatab/MediaViewModel.ets',
  uiState: 'entry/src/main/ets/features/home/mediatab/MediaUIState.ets',
  tab: 'entry/src/main/ets/features/home/mediatab/MediaTab.ets'
})

function requiredSource(sources, path) {
  const source = sources.get(path)
  if (source === undefined) {
    throw new Error('missing source: ' + path)
  }
  return source
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

function methodBlock(source, methodName) {
  const match = new RegExp(
    '\\b(?:public\\s+|private\\s+|protected\\s+)?(?:static\\s+)?(?:async\\s+)?' +
      methodName + '\\s*\\([^)]*\\)\\s*(?::\\s*[^\\{]+)?\\s*\\{'
  ).exec(source)
  if (!match) {
    throw new Error('missing method: ' + methodName)
  }
  const openingBrace = source.indexOf('{', match.index)
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
  throw new Error('unterminated method: ' + methodName)
}

export function validateMediaLoadingContracts(sources) {
  const viewModel = requiredSource(sources, mediaLoadingPaths.viewModel)
  const uiState = requiredSource(sources, mediaLoadingPaths.uiState)
  const tab = requiredSource(sources, mediaLoadingPaths.tab)
  const initMethod = methodBlock(viewModel, 'init')
  const getMediaListMethod = methodBlock(viewModel, 'getMediaList')

  rejectPattern(viewModel, /return\s+new\s+Promise\s*\(/,
    'media loading must not wrap Catalog calls in a manual Promise')
  rejectPattern(viewModel, /\bresolve\s*\(\s*\)/,
    'media loading must not settle before child requests finish')
  requirePattern(viewModel,
    /private\s+async\s+getMediaList\s*\(\s*catalog\s*:\s*MediaCatalogProvider\s*\)\s*:\s*Promise<MediaItem\[\]>/,
    'getMediaList must accept the resolved Catalog and return Promise<MediaItem[]>')
  requirePattern(getMediaListMethod,
    /await\s+catalog\.getLibraries\s*\(\s*\)/,
    'getMediaList must load libraries through the neutral Catalog')
  requirePattern(getMediaListMethod, /return\s+libraries\.map\s*\(/,
    'getMediaList must synchronously map the normalized Catalog library array')

  requirePattern(initMethod, /this\.ui\.pageState\s*=\s*PageState\.Loading/,
    'init must enter Loading')
  requirePattern(initMethod,
    /const\s+mediaList\s*=\s*await\s+this\.getMediaList\s*\(\s*catalog\s*\)[\s\S]*?if\s*\(\s*!this\.isCurrent\s*\(\s*catalog\s*,\s*generation\s*\)\s*\)\s*\{[\s\S]*?return[\s\S]*?\}[\s\S]*?this\.ui\.mediaList\s*=\s*mediaList/,
    'init must await the complete media list')
  requirePattern(initMethod,
    /catalog\.session\.capabilities\.management[\s\S]*libraryRepository\.requireAdministrator/,
    'management access must be gated by the active provider capability')
  requirePattern(initMethod, /this\.ui\.pageState\s*=\s*PageState\.Content/,
    'init must expose Content')
  requirePattern(initMethod,
    /catch\s*\([^)]*\)[\s\S]*this\.ui\.mediaList\s*=\s*\[\][\s\S]*PageState\.Error/,
    'init must clear stale media and expose Error')
  requirePattern(initMethod,
    /finally\s*\{[\s\S]*this\.ui\.isRefreshing\s*=\s*false[\s\S]*\}/,
    'init must clear refresh state in finally')

  requirePattern(uiState,
    /import\s*\{\s*BaseUIState\s*\}\s*from\s*["'][^"']+BaseUIState["']/,
    'MediaUIState must import BaseUIState')
  requirePattern(uiState, /class\s+MediaUIState\s+extends\s+BaseUIState/,
    'MediaUIState must extend BaseUIState')

  requirePattern(tab,
    /new\s+MediaUIState\s*\(this\.getUIContext\s*\(\s*\)\s*\)/,
    'MediaTab must pass UIContext to MediaUIState')
  requirePattern(tab, /PageState\.Content[\s\S]*Refresh\s*\(/,
    'MediaTab must render refresh content for Content')
  requirePattern(tab, /PageState\.Loading[\s\S]*LoadingProgress\s*\(/,
    'MediaTab must render LoadingProgress')
  requirePattern(tab,
    /PageState\.Error[\s\S]*FailedView\s*\(\s*\)[\s\S]*\.onClick\s*\(\s*\(\s*\)\s*=>\s*\{[\s\S]*this\.vm\.init\s*\(\s*\)/,
    'MediaTab must render FailedView with retry')
}

export function defaultWorkspaceRoot() {
  return resolve(dirname(fileURLToPath(import.meta.url)), '..')
}

export function loadMediaLoadingSources(workspaceRoot = defaultWorkspaceRoot()) {
  return new Map(Object.values(mediaLoadingPaths).map((path) => [
    path,
    readFileSync(resolve(workspaceRoot, path), 'utf8')
  ]))
}

export function validateWorkspace(workspaceRoot = defaultWorkspaceRoot()) {
  validateMediaLoadingContracts(loadMediaLoadingSources(workspaceRoot))
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspace()
  console.log('Media loading contracts verified.')
}
