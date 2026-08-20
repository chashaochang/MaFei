import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const SETTING = 'entry/src/main/ets/features/setting/SettingPage.ets'
const ADD_ACCOUNT = 'entry/src/main/ets/features/setting/account/AddAccountPage.ets'
const JELLYFIN_CONNECT = 'entry/src/main/ets/features/setting/account/JellyfinConnectPage.ets'
const FEINIU_CONNECT = 'entry/src/main/ets/features/feiniuvideo/account/FeiniuVideoConnectPage.ets'
const CONNECT_SCREEN = 'entry/src/main/ets/features/connect/ConnectScreen.ets'
const MINE_PAGE = 'entry/src/main/ets/features/home/minetab/MinePage.ets'
const MINE_TAB = 'entry/src/main/ets/features/home/minetab/MineTab.ets'
const WEB_ITONG = 'entry/src/main/ets/features/web/WebITongPage.ets'

export const SECONDARY_ROUTE_PATHS = [
  SETTING,
  ADD_ACCOUNT,
  JELLYFIN_CONNECT,
  FEINIU_CONNECT,
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
    /title\s*:\s*\$r\(\s*['"]app\.string\.settings_title['"]\s*\)/
  )
  const content = methodBlock(source, 'pageContent')
  const legacyHeader = guardedBlock(
    content,
    /if\s*\(\s*showLegacyActionBar\s*\)\s*\{/,
    'Setting legacy title row must be guarded'
  )
  if (count(content, /\bActionBar\s*\(/g) !== 1 ||
    !/ActionBar\s*\(\s*\{[\s\S]*title\s*:\s*\$r\(\s*['"]app\.string\.settings_title['"]\s*\)/.test(
      legacyHeader)) {
    throw new Error('Setting custom ActionBar must stay only in the Feiniu branch')
  }
  if (!/\.backgroundColor\s*\(\s*showLegacyActionBar\s*\?\s*\$r\(\s*['"]app\.color\.start_window_background['"]\s*\)\s*:\s*Color\.Transparent\s*\)/.test(
    content)) {
    throw new Error('Setting Native route root must be transparent')
  }
  const nativeTopSpacer = methodBlock(source, 'nativeTopSpacer')
  if (!/^\s*return\s+Math\.max\s*\(\s*0\s*,\s*this\.appUIState\.safeTop\s*\)\s*\+\s*UIConstants\.ACTION_BAR_HEIGHT\s*;?\s*$/.test(
    nativeTopSpacer) ||
    !/if\s*\(\s*!\s*showLegacyActionBar\s*\)\s*\{\s*Blank\s*\(\s*\)\.height\s*\(\s*this\.nativeTopSpacer\s*\(\s*\)\s*\)/.test(
      content) ||
    /SafeAreaEdge\.TOP|\.safeAreaPadding\s*\(/.test(content)) {
    throw new Error('Setting Native content must reserve exactly one system-title spacer')
  }
  if (!/contentExtendsUnderTitleBar\s*:\s*true/.test(build) ||
    !/scrollControllers\s*:\s*\[\s*this\.contentScroller\s*\]/.test(build) ||
    !/contentBuilder\s*:[\s\S]*?this\.pageContent\s*\(\s*false\s*\)[\s\S]*?legacyContentBuilder\s*:[\s\S]*?this\.pageContent\s*\(\s*true\s*\)/.test(build)) {
    throw new Error('Setting must separate Native and Feiniu title ownership')
  }
}

function validateAddAccount(routeSource, jellyfinSource, feiniuSource, connectSource) {
  const build = validateRouteRoot(
    routeSource,
    ADD_ACCOUNT,
    /pageUrl\s*:\s*RouterConsts\.AddAccountPage\b/,
    /title\s*:\s*\$r\(\s*['"]app\.string\.account_add_title['"]\s*\)/
  )
  const pageContent = methodBlock(routeSource, 'pageContent')
  const jellyfinRoute = methodBlock(routeSource, 'openJellyfin')
  const feiniuRoute = methodBlock(routeSource, 'openFeiniuVideo')
  if (/\bConnectScreen\s*\(/.test(routeSource) ||
    !/HMRouterMgr\.to\(\s*RouterConsts\.JellyfinConnectPage\s*\)\.push\(\s*\)/.test(jellyfinRoute) ||
    !/HMRouterMgr\.to\(\s*RouterConsts\.FeiniuVideoConnectPage\s*\)\.push\(\s*\)/.test(feiniuRoute) ||
    count(routeSource, /this\.openJellyfin\s*\(\s*\)/g) < 2 ||
    count(routeSource, /this\.openFeiniuVideo\s*\(\s*\)/g) < 2) {
    throw new Error('AddAccount must remain a reachable Jellyfin and Feiniu provider selector')
  }
  const addAccountLegacyHeader = guardedBlock(
    pageContent,
    /if\s*\(\s*showLegacyActionBar\s*\)\s*\{/,
    'AddAccount legacy title row must be guarded'
  )
  if (count(pageContent, /\bActionBar\s*\(/g) !== 1 ||
    count(addAccountLegacyHeader, /\bActionBar\s*\(/g) !== 1 ||
    !/if\s*\(\s*showLegacyActionBar\s*\)\s*\{[\s\S]*this\.feiniuProviderList\s*\(\s*\)[\s\S]*\}\s*else\s*\{[\s\S]*this\.nativeProviderList\s*\(\s*\)/.test(pageContent) ||
    !/contentBuilder\s*:[\s\S]*this\.pageContent\s*\(\s*false\s*\)[\s\S]*legacyContentBuilder\s*:[\s\S]*this\.pageContent\s*\(\s*true\s*\)/.test(
      build)) {
    throw new Error('AddAccount must separate Native and Feiniu provider-selector chrome')
  }

  const jellyfinBuild = validateRouteRoot(
    jellyfinSource,
    JELLYFIN_CONNECT,
    /pageUrl\s*:\s*RouterConsts\.JellyfinConnectPage\b/,
    /title\s*:\s*\$r\(\s*['"]app\.string\.jellyfin_connect_title['"]\s*\)/
  )
  const jellyfinContent = methodBlock(jellyfinSource, 'pageContent')
  const jellyfinOptions = componentOptions(jellyfinContent, 'ConnectScreen')
  if (jellyfinOptions.length !== 1 ||
    !/isFromAddAccount\s*:\s*true/.test(jellyfinOptions[0]) ||
    !/showRouteActionBar\s*:\s*showRouteActionBar/.test(jellyfinOptions[0]) ||
    !/contentBuilder\s*:[\s\S]*this\.pageContent\s*\(\s*false\s*\)[\s\S]*legacyContentBuilder\s*:[\s\S]*this\.pageContent\s*\(\s*true\s*\)/.test(
      jellyfinBuild)) {
    throw new Error('JellyfinConnectPage must own the existing add-account ConnectScreen route')
  }

  const feiniuBuild = validateRouteRoot(
    feiniuSource,
    FEINIU_CONNECT,
    /pageUrl\s*:\s*RouterConsts\.FeiniuVideoConnectPage\b/,
    /title\s*:\s*\$r\(\s*['"]app\.string\.feiniu_video_connect_title['"]\s*\)/
  )
  const feiniuContent = methodBlock(feiniuSource, 'pageContent')
  const feiniuOptions = componentOptions(feiniuContent, 'FeiniuVideoConnectScreen')
  if (feiniuOptions.length !== 1 ||
    !/showRouteActionBar\s*:\s*showRouteActionBar/.test(feiniuOptions[0]) ||
    !/@Param\s+showRouteActionBar\s*:\s*boolean\s*=\s*true/.test(feiniuSource) ||
    !/FeiniuVideoConnectionViewModel/.test(feiniuSource) ||
    /\bConnectScreen\s*\(|\bConnectionViewModel\b/.test(feiniuSource) ||
    !/await\s+this\.vm\.onLogin\s*\(\s*\)/.test(feiniuSource) ||
    !/contentBuilder\s*:[\s\S]*this\.pageContent\s*\(\s*false\s*\)[\s\S]*legacyContentBuilder\s*:[\s\S]*this\.pageContent\s*\(\s*true\s*\)/.test(
      feiniuBuild)) {
    throw new Error('FeiniuVideoConnectPage must keep an independent reachable login flow')
  }

  if (!/@Param\s+showRouteActionBar\s*:\s*boolean\s*=\s*true/.test(connectSource)) {
    throw new Error('ConnectScreen route bar visibility must preserve its legacy default')
  }
  const connectBuild = methodBlock(connectSource, 'build')
  const connectLegacyHeader = guardedBlock(
    connectBuild,
    /if\s*\(\s*this\.showRouteActionBar\s*\)\s*\{/,
    'ConnectScreen ActionBar must be guarded by route ownership'
  )
  if (count(connectBuild, /\bActionBar\s*\(/g) !== 1 ||
    count(connectLegacyHeader, /\bActionBar\s*\(/g) !== 1) {
    throw new Error('ConnectScreen ActionBar must stay outside Native destination content')
  }
  if (!/\.backgroundColor\s*\(\s*this\.showRouteActionBar\s*&&\s*this\.useNativeSurface\s*\(\s*\)\s*\?\s*\$r\(\s*['"]app\.color\.native_canvas_background['"]\s*\)\s*:\s*Color\.Transparent\s*\)/.test(
    connectBuild)) {
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
  const nativeContent = methodBlock(routeSource, 'nativePageContent')
  const legacyContent = methodBlock(routeSource, 'legacyPageContent')
  const nativeOptions = componentOptions(nativeContent, 'MineTab')
  const legacyOptions = componentOptions(legacyContent, 'MineTab')
  if (nativeOptions.length !== 1 || legacyOptions.length !== 1 ||
    !/fromHome\s*:\s*true/.test(nativeOptions[0]) ||
    !/destinationOwnsTitleBar\s*:\s*true/.test(nativeOptions[0]) ||
    !/fromHome\s*:\s*true/.test(legacyOptions[0]) ||
    !/destinationOwnsTitleBar\s*:\s*false/.test(legacyOptions[0]) ||
    !/contentBuilder\s*:[\s\S]*this\.nativePageContent\s*\(\s*\)[\s\S]*legacyContentBuilder\s*:[\s\S]*this\.legacyPageContent\s*\(\s*\)/.test(
      build)) {
    throw new Error('MinePage must use the system title only in its Native destination')
  }
  if (!/@Param\s+destinationOwnsTitleBar\s*:\s*boolean\s*=\s*false/.test(tabSource)) {
    throw new Error('MineTab destination title ownership must remain opt-in')
  }
  const spacer = methodBlock(tabSource, 'topSpacerHeight')
  if (!/if\s*\(\s*this\.destinationOwnsTitleBar\s*\)\s*\{\s*return\s+0\s*\}/.test(spacer)) {
    throw new Error('Mine Native destination must not add a second top inset')
  }
  if (!/if\s*\(\s*this\.compactTopInset\s*\)\s*\{\s*return\s+Math\.max\s*\(\s*0\s*,\s*this\.vm\.appUIState\.safeTop\s*\)\s*\+\s*UIConstants\.ACTION_BAR_HEIGHT\s*\}/.test(spacer) ||
    !/return\s+this\.vm\.appUIState\.safeTop\s*\+\s*UIConstants\.ACTION_BAR_HEIGHT/.test(spacer)) {
    throw new Error('Mine home content must preserve the root title-bar top inset')
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
  if (!/titleBarVisible\s*:\s*false/.test(build) ||
    !/beforeBack\s*:\s*\(\s*\)\s*=>\s*this\.handleRouteBack\s*\(\s*\)/.test(build) ||
    !/contentBuilder\s*:[\s\S]*?this\.pageContent\s*\(\s*true\s*\)[\s\S]*?legacyContentBuilder\s*:[\s\S]*?this\.pageContent\s*\(\s*true\s*\)/.test(build)) {
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
    throw new Error('WebITong fullscreen content must preserve its guarded top inset')
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
    requiredSource(sources, JELLYFIN_CONNECT),
    requiredSource(sources, FEINIU_CONNECT),
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
