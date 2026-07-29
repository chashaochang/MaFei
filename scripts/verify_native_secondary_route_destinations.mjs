import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const SETTING = 'entry/src/main/ets/features/setting/SettingPage.ets'
const ADD_ACCOUNT = 'entry/src/main/ets/features/setting/account/AddAccountPage.ets'
const CONNECT_SCREEN = 'entry/src/main/ets/features/connect/ConnectScreen.ets'
const MINE_PAGE = 'entry/src/main/ets/features/home/minetab/MinePage.ets'
const MINE_TAB = 'entry/src/main/ets/features/home/minetab/MineTab.ets'
const WEB_ITONG = 'entry/src/main/ets/features/web/WebITongPage.ets'

export const SECONDARY_ROUTE_PATHS = [
  SETTING,
  ADD_ACCOUNT,
  CONNECT_SCREEN,
  MINE_PAGE,
  MINE_TAB,
  WEB_ITONG
]

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

function routerAnnotation(source, path) {
  const match = /@HMRouter\s*\(\s*\{/.exec(source)
  if (!match) {
    throw new Error('missing HMRouter annotation: ' + path)
  }
  return bracedBlock(source, source.indexOf('{', match.index))
}

function componentOptions(source, componentName) {
  const pattern = new RegExp('\\b' + componentName + '\\s*\\(\\s*\\{', 'g')
  return [...source.matchAll(pattern)].map((match) =>
    bracedBlock(source, source.indexOf('{', match.index)))
}

function guardedBlock(source, condition, error) {
  const match = condition.exec(source)
  if (!match) {
    throw new Error(error)
  }
  return bracedBlock(source, source.indexOf('{', match.index))
}

function validateRouteRoot(source, path, pageUrl, title) {
  const annotation = routerAnnotation(source, path)
  if (!pageUrl.test(annotation)) {
    throw new Error('route URL changed: ' + path)
  }
  if (!/\buseNavDst\s*:\s*true\b/.test(annotation)) {
    throw new Error('secondary route must opt out of HMRouter destination wrapping: ' + path)
  }
  if (!/import\s*\{[^}]*\bAppRouteDestination\b[^}]*\}/.test(source) ||
    count(source, /\bAppRouteDestination\s*\(/g) !== 1) {
    throw new Error('secondary route must use exactly one AppRouteDestination: ' + path)
  }
  if (/@kit\.UIDesignKit|\b(?:HdsNavDestination|HdsNavigation|NavDestination|Navigation)\s*\(/.test(source)) {
    throw new Error('secondary route must not construct another navigation host: ' + path)
  }
  const build = methodBlock(source, 'build')
  if (!/^\s*AppRouteDestination\s*\(/.test(build) || !title.test(build)) {
    throw new Error('secondary route must expose its Native title through AppRouteDestination: ' + path)
  }
  return build
}

function validateSetting(source) {
  const build = validateRouteRoot(
    source,
    SETTING,
    /pageUrl\s*:\s*['"]\/Setting['"]\s*(?:,|$)/,
    /title\s*:\s*['"]设置['"]/
  )
  const content = methodBlock(source, 'pageContent')
  const legacyHeader = guardedBlock(
    content,
    /if\s*\(\s*showLegacyActionBar\s*\)\s*\{/,
    'Setting legacy title row must be guarded'
  )
  if (count(content, /\bRow\s*\(\s*\)\s*\{/g) !== 1 ||
    !/app\.media\.ic_back/.test(legacyHeader) || !/HMRouterMgr\.pop\s*\(\s*\)/.test(legacyHeader) ||
    !/Text\s*\(\s*['"]设置['"]\s*\)/.test(legacyHeader)) {
    throw new Error('Setting custom title row must stay only in the Feiniu branch')
  }
  if (!/\.backgroundColor\s*\(\s*showLegacyActionBar\s*\?\s*['"]#101010['"]\s*:\s*Color\.Transparent\s*\)/.test(content)) {
    throw new Error('Setting Native route root must be transparent')
  }
  if (/\bsafeTop\b|SafeAreaEdge\.TOP|\.safeAreaPadding\s*\(/.test(content)) {
    throw new Error('Setting Native content must not own the top safe area')
  }
  if (!/contentBuilder\s*:[\s\S]*?this\.pageContent\s*\(\s*false\s*\)[\s\S]*?legacyContentBuilder\s*:[\s\S]*?this\.pageContent\s*\(\s*true\s*\)/.test(build)) {
    throw new Error('Setting must separate Native and Feiniu title ownership')
  }
}

function validateAddAccount(routeSource, connectSource) {
  const build = validateRouteRoot(
    routeSource,
    ADD_ACCOUNT,
    /pageUrl\s*:\s*RouterConsts\.AddAccountPage\b/,
    /title\s*:\s*['"]新增账号['"]/
  )
  const options = componentOptions(build, 'ConnectScreen')
  if (options.length !== 2 || options.some((value) => !/isFromAddAccount\s*:\s*true/.test(value)) ||
    !/showRouteActionBar\s*:\s*false/.test(options[0]) ||
    !/showRouteActionBar\s*:\s*true/.test(options[1])) {
    throw new Error('AddAccount must hide the custom bar only in its Native destination')
  }
  if (!/@Param\s+showRouteActionBar\s*:\s*boolean\s*=\s*true/.test(connectSource)) {
    throw new Error('ConnectScreen route bar visibility must preserve its legacy default')
  }
  const connectBuild = methodBlock(connectSource, 'build')
  const legacyHeader = guardedBlock(
    connectBuild,
    /if\s*\(\s*this\.showRouteActionBar\s*\)\s*\{/,
    'ConnectScreen ActionBar must be guarded by route ownership'
  )
  if (count(connectBuild, /\bActionBar\s*\(/g) !== 1 || count(legacyHeader, /\bActionBar\s*\(/g) !== 1) {
    throw new Error('ConnectScreen ActionBar must stay outside Native destination content')
  }
  if (!/\.backgroundColor\s*\(\s*this\.showRouteActionBar\s*\?\s*AppThemeSurfaceResolver\.routeBackground\s*\([\s\S]*?\)\s*:\s*Color\.Transparent\s*\)/.test(connectBuild)) {
    throw new Error('AddAccount Native content must leave the route background transparent')
  }
}

function validateMine(routeSource, tabSource) {
  const build = validateRouteRoot(
    routeSource,
    MINE_PAGE,
    /pageUrl\s*:\s*RouterConsts\.MinePage\b/,
    /title\s*:\s*['"]我的['"]/
  )
  const options = componentOptions(build, 'MineTab')
  if (options.length !== 2 || options.some((value) => !/fromHome\s*:\s*true/.test(value)) ||
    !/destinationOwnsTitleBar\s*:\s*true/.test(options[0]) ||
    !/destinationOwnsTitleBar\s*:\s*false/.test(options[1])) {
    throw new Error('MinePage must use the system title only in its Native destination')
  }
  if (!/@Param\s+destinationOwnsTitleBar\s*:\s*boolean\s*=\s*false/.test(tabSource)) {
    throw new Error('MineTab destination title ownership must remain opt-in')
  }
  const spacer = methodBlock(tabSource, 'topSpacerHeight')
  if (!/if\s*\(\s*this\.destinationOwnsTitleBar\s*\)\s*\{\s*return\s+0\s*\}/.test(spacer)) {
    throw new Error('Mine Native destination must not add a second top inset')
  }
  const tabBuild = methodBlock(tabSource, 'build')
  const legacyHeader = guardedBlock(
    tabBuild,
    /if\s*\(\s*!\s*this\.compactTopInset\s*&&\s*!\s*this\.destinationOwnsTitleBar\s*\)\s*\{/,
    'Mine custom header must be guarded by compact and destination ownership'
  )
  if (count(tabBuild, /\bActionBar\s*\(/g) !== 1 || count(legacyHeader, /\bActionBar\s*\(/g) !== 1) {
    throw new Error('Mine custom header must stay outside Native destination content')
  }
  if (!/\.backgroundColor\s*\(\s*this\.destinationOwnsTitleBar\s*\?\s*Color\.Transparent\s*:\s*AppThemeSurfaceResolver\.routeBackground\s*\(/.test(tabBuild)) {
    throw new Error('Mine Native destination root must be transparent')
  }
}

function validateWebItong(source) {
  const build = validateRouteRoot(
    source,
    WEB_ITONG,
    /pageUrl\s*:\s*RouterConsts\.WebITongPage\b/,
    /title\s*:\s*this\.routeTitle\s*\(\s*\)/
  )
  if (!/beforeBack\s*:\s*\(\s*\)\s*=>\s*this\.handleRouteBack\s*\(\s*\)/.test(build) ||
    !/contentBuilder\s*:[\s\S]*?this\.pageContent\s*\(\s*false\s*\)[\s\S]*?legacyContentBuilder\s*:[\s\S]*?this\.pageContent\s*\(\s*true\s*\)/.test(build)) {
    throw new Error('WebITong must use the destination title and back hook only in the route host')
  }
  const back = methodBlock(source, 'handleRouteBack')
  if (!/accessBackward\s*\(\s*\)/.test(back) || !/backward\s*\(\s*\)/.test(back) ||
    !/return\s+true\b/.test(back) || !/return\s+false\b/.test(back)) {
    throw new Error('WebITong system back must consume browser history before route pop')
  }
  if (/HMLifecycleState|onBackPressed/.test(source)) {
    throw new Error('WebITong back ownership must not remain on a separate HMRouter lifecycle observer')
  }
  const content = methodBlock(source, 'pageContent')
  const legacyInset = guardedBlock(
    content,
    /if\s*\(\s*showLegacyTopInset\s*\)\s*\{/,
    'WebITong legacy safe-top spacer must be guarded'
  )
  if (count(content, /\bsafeTop\b/g) !== 1 || !/\bsafeTop\b/.test(legacyInset)) {
    throw new Error('WebITong Native content must leave top safe-area ownership to the destination')
  }
  if (!/Row\s*\(\s*\{\s*space\s*:\s*12\s*\}\s*\)/.test(content) ||
    count(content, /\.backgroundBlurStyle\s*\(/g) < 5 ||
    !/UIConstants\.ACTION_BAR_HEIGHT/.test(content) || !/appUIState\.safeBottom/.test(content)) {
    throw new Error('WebITong must preserve its bottom browser control bar')
  }
}

export function validateNativeSecondaryRouteDestinations(sources) {
  validateSetting(requiredSource(sources, SETTING))
  validateAddAccount(
    requiredSource(sources, ADD_ACCOUNT),
    requiredSource(sources, CONNECT_SCREEN)
  )
  validateMine(
    requiredSource(sources, MINE_PAGE),
    requiredSource(sources, MINE_TAB)
  )
  validateWebItong(requiredSource(sources, WEB_ITONG))
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

function main() {
  const root = process.argv[2] ? resolve(process.argv[2]) : defaultWorkspaceRoot()
  const sources = new Map(SECONDARY_ROUTE_PATHS.map((path) => [
    path,
    readFileSync(resolve(root, path), 'utf8')
  ]))
  validateNativeSecondaryRouteDestinations(sources)
  process.stdout.write('Native secondary route destinations verified\n')
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main()
}
