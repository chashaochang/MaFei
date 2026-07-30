import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const HOME_SCREEN = 'entry/src/main/ets/features/home/HomeScreen.ets'
const MINE_TAB = 'entry/src/main/ets/features/home/minetab/MineTab.ets'
const MINE_PAGE = 'entry/src/main/ets/features/home/minetab/MinePage.ets'

function requiredSource(sources, path) {
  const source = sources.get(path)
  if (source === undefined) {
    throw new Error('missing source: ' + path)
  }
  return source
}

function count(source, pattern) {
  return source.match(pattern)?.length ?? 0
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
    '\\b(?:private\\s+)?' + methodName + '\\s*\\([^)]*\\)\\s*(?::\\s*[^\\{]+)?\\s*\\{'
  )
  const match = signature.exec(source)
  if (!match) {
    throw new Error('missing method: ' + methodName)
  }
  return bracedBlock(source, source.indexOf('{', match.index))
}

function componentOptions(source, componentName) {
  const pattern = new RegExp('\\b' + componentName + '\\s*\\(\\s*\\{', 'g')
  const matches = [...source.matchAll(pattern)]
  if (matches.length !== 1) {
    throw new Error(componentName + ' must be constructed exactly once')
  }
  return bracedBlock(source, source.indexOf('{', matches[0].index))
}

function allComponentOptions(source, componentName) {
  const pattern = new RegExp('\\b' + componentName + '\\s*\\(\\s*\\{', 'g')
  return [...source.matchAll(pattern)].map((match) =>
    bracedBlock(source, source.indexOf('{', match.index)))
}

function compactHeaderGuard(source) {
  const pattern = /if\s*\(\s*!\s*this\.compactTopInset\s*&&\s*!\s*this\.destinationOwnsTitleBar\s*\)\s*\{/g
  const matches = [...source.matchAll(pattern)]
  if (matches.length !== 1) {
    throw new Error('MineTab legacy header must have exactly one compact and destination ownership guard')
  }
  return bracedBlock(source, source.indexOf('{', matches[0].index))
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function validateNativeMineHeader(sources) {
  const homeScreen = requiredSource(sources, HOME_SCREEN)
  const mineTab = requiredSource(sources, MINE_TAB)
  const minePage = requiredSource(sources, MINE_PAGE)

  if (!/@Param\s+compactTopInset\s*:\s*boolean\s*=\s*false/.test(mineTab)) {
    throw new Error('MineTab compact top inset must remain opt-in')
  }
  if (!/@Param\s+destinationOwnsTitleBar\s*:\s*boolean\s*=\s*false/.test(mineTab)) {
    throw new Error('MineTab destination title ownership must remain opt-in')
  }

  const spacer = methodBlock(mineTab, 'topSpacerHeight')
  if (!/if\s*\(\s*this\.destinationOwnsTitleBar\s*\|\|\s*this\.compactTopInset\s*\)\s*\{\s*return\s+0\s*\}/.test(spacer) ||
    !/return\s+this\.vm\.appUIState\.safeTop\s*\+\s*UIConstants\.ACTION_BAR_HEIGHT/.test(spacer)) {
    throw new Error('system-owned MineTab headers must suppress all manual top inset')
  }
  if (count(mineTab, /Blank\s*\(\s*\)\.height\s*\(\s*this\.topSpacerHeight\s*\(\s*\)\s*\)/g) !== 1) {
    throw new Error('MineTab content must use the guarded top spacer exactly once')
  }

  const headerGuard = compactHeaderGuard(mineTab)
  const legacyHeaderHeight = /\.height\s*\(\s*UIConstants\.ACTION_BAR_HEIGHT\s*\+\s*this\.vm\.appUIState\.safeTop\s*\)/g
  if (count(mineTab, legacyHeaderHeight) !== 2 || count(headerGuard, legacyHeaderHeight) !== 2 ||
    count(mineTab, /\bActionBar\s*\(/g) !== 1 || count(headerGuard, /\bActionBar\s*\(/g) !== 1) {
    throw new Error('legacy MineTab header must not exist in compact mode')
  }

  const homeOptions = componentOptions(homeScreen, 'MineTab')
  if (!/compactTopInset\s*:\s*this\.shell\s*===\s*HomeShellKind\.PhoneNativeHds/.test(homeOptions)) {
    throw new Error('only the API 26 native phone home shell may compact MineTab')
  }

  const standaloneOptions = allComponentOptions(minePage, 'MineTab')
  if (standaloneOptions.length !== 2 || standaloneOptions.some((options) =>
    !/fromHome\s*:\s*true/.test(options) || /compactTopInset/.test(options)) ||
    !/destinationOwnsTitleBar\s*:\s*true/.test(standaloneOptions[0]) ||
    !/destinationOwnsTitleBar\s*:\s*false/.test(standaloneOptions[1])) {
    throw new Error('standalone MinePage must use the system title only in its Native destination')
  }
}

export function validateWorkspace(root = defaultWorkspaceRoot()) {
  const sources = new Map()
  for (const path of [HOME_SCREEN, MINE_TAB, MINE_PAGE]) {
    sources.set(path, readFileSync(resolve(root, path), 'utf8'))
  }
  validateNativeMineHeader(sources)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspace()
  console.log('Native Mine header contracts verified')
}
