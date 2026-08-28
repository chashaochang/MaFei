import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const APP_STATE = 'entry/src/main/ets/entity/AppUIState.ets'
const INDEX = 'entry/src/main/ets/pages/Index.ets'
const SHARED_DESTINATION = 'entry/src/main/ets/component/AppRouteDestination.ets'
const ROOT_PAGE = 'entry/src/main/ets/features/splash/IndexPage.ets'
const HOME_SCREEN = 'entry/src/main/ets/features/home/HomeScreen.ets'
const CHROME_TAB = 'entry/src/main/ets/features/home/phone/HomePhoneChromeTab.ets'
const STANDARD_HOST = 'entry/src/main/ets/features/home/phone/HomePhoneStandardTabs.ets'
const NATIVE_HOST = 'entry/src/main/ets/features/home/phone/HomePhoneNativeTabs.ets'
const POINT_LIGHT_OWNER = 'entry/src/main/ets/theme/FeiniuPointLightModifier.ets'
const HDS_CAPABILITY = 'entry/src/main/ets/theme/HdsUiCapability.ets'
const NATIVE_THEME_CAPABILITY = 'entry/src/main/ets/theme/NativeThemeCapability.ets'
const HOME_SHELL_POLICY = 'entry/src/main/ets/features/home/HomeShellPolicy.ets'
const HOME_TAB = 'entry/src/main/ets/features/home/hometab/HomeTab.ets'
const HOME_VIEW_MODEL = 'entry/src/main/ets/features/home/hometab/HomeViewModel.ets'
const CHASING_TAB = 'entry/src/main/ets/features/home/chasing/ChasingTab.ets'
const FAVORITE_TAB = 'entry/src/main/ets/features/favorite/FavoriteListPage.ets'
const MEDIA_TAB = 'entry/src/main/ets/features/home/mediatab/MediaTab.ets'
const MINE_TAB = 'entry/src/main/ets/features/home/minetab/MineTab.ets'
const TAB_CONTENT_FILES = [
  HOME_TAB,
  CHASING_TAB,
  FAVORITE_TAB,
  MEDIA_TAB,
  MINE_TAB
]
const HDS_NAVIGATION =
  /\b(?:HdsNavDestination|HdsTabs|HdsTabsController|HdsTabsModifier|hdsMaterial)\b/
const DIRECT_INSET =
  /BOTTOM_BAR_HEIGHT[\s\S]{0,160}safeBottom|safeBottom[\s\S]{0,160}BOTTOM_BAR_HEIGHT/
const STANDARD_INDEX =
  /Tabs\s*\(\s*\{[\s\S]{0,180}index\s*:\s*this\.selectedIndex/
const NATIVE_INDEX =
  /HdsTabs\s*\(\s*\{[\s\S]{0,180}index\s*:\s*this\.visibleTabIndex\s*\(\s*this\.selectedIndex\s*\)/
const NATIVE_BAR_HEIGHT =
  /\.barHeight\s*\(\s*UIConstants\.BOTTOM_BAR_HEIGHT\s*\)/g
const NATIVE_SAFE_BOTTOM_MARGIN =
  /barBottomMargin\s*:\s*this\.normalizedSafeBottom\s*\(\s*\)/
const NATIVE_ROOT_HEIGHT =
  /\.height\s*\(\s*['"]100%['"]\s*\)/
const NATIVE_SAFE_BOTTOM_BODY =
  /^\s*return\s+Math\.max\s*\(\s*0\s*,\s*this\.safeBottom\s*\)\s*;?\s*$/
const NATIVE_FIXED_ITEM_WIDTH =
  /}\s*\.height\s*\(\s*['"]100%['"]\s*\)\s*\.width\s*\(\s*\d+(?:\.\d+)?\s*\)/
const CONTENT_INDEX =
  /Tabs\s*\(\s*\{[\s\S]{0,180}index\s*:\s*this\.contentSelectedIndex\(\)/
const BREAKPOINT_MONITOR =
  /@Monitor\s*\(\s*['"]appUIState\.currentBreakpoint['"]\s*\)/g
const ROOT_TITLE_BAR_STATE =
  /@Trace\s+rootNavigationTitleBarVisible\s*:\s*boolean\s*=\s*false/
const ROOT_TITLE_STATE =
  /@Trace\s+rootNavigationTitle\s*:\s*string\s*=\s*['"]首页['"]/
const ROOT_HOME_ACTIONS_STATE =
  /@Trace\s+rootNavigationHomeActionsVisible\s*:\s*boolean\s*=\s*false/
const ROOT_MEDIA_ADMIN_ACTIONS_STATE =
  /@Trace\s+rootNavigationMediaAdminActionsVisible\s*:\s*boolean\s*=\s*false/
const ROOT_HOME_HERO_STATE =
  /@Trace\s+rootNavigationHomeHeroVisible\s*:\s*boolean\s*=\s*false/
const ROOT_LIVE_TV_STATE =
  /@Trace\s+rootNavigationLiveTvAvailable\s*:\s*boolean\s*=\s*false/
const ROOT_TITLE_BAR_INITIALIZER =
  /instance\.hideTitleBar\s*\(\s*true\s*\)/
const ROOT_TITLE_BAR_STATE_UPDATE =
  /this\.appUIState\.rootNavigationTitleBarVisible\s*=\s*visible/
const ROOT_TITLE_STATE_UPDATE =
  /this\.appUIState\.rootNavigationTitle\s*=\s*title/
const ROOT_HOME_ACTIONS_STATE_UPDATE =
  /this\.appUIState\.rootNavigationHomeActionsVisible\s*=\s*homeActionsVisible/
const ROOT_MEDIA_ADMIN_ACTIONS_STATE_UPDATE =
  /this\.appUIState\.rootNavigationMediaAdminActionsVisible\s*=\s*mediaAdminActionsVisible/
const RECOMMENDATION_LIST_MONITOR =
  /@Monitor\s*\(\s*['"]ui\.recommendationList['"]\s*\)/g
const THEME_STYLE_MONITOR =
  /@Monitor\s*\(\s*['"]appUIState\.themeStyle['"]\s*\)/g
const NATIVE_NAV_SAFE_AREA_GATE_BODY =
  /^\s*return\s+appUIState\.themeStyle\s*===\s*ThemeStyle\.Native\s*&&\s*HdsUiCapability\.supportsNativeTheme\s*\(\s*\)\s*;?\s*$/
const NAV_SAFE_AREA_INSTANCE =
  /(?:let|const)\s+navInstance\s*=\s*instance\s*\|\|\s*this\.instance/
const NAV_SAFE_AREA_TYPES_BODY =
  /^\s*return\s+usesNativeTopSafeArea\s*\(\s*appUIState\s*\)\s*\?\s*\[\s*LayoutSafeAreaType\.SYSTEM\s*\]\s*:\s*\[\s*\]\s*;?\s*$/
const NAV_SAFE_AREA_EDGES_BODY =
  /^\s*return\s+usesNativeTopSafeArea\s*\(\s*appUIState\s*\)\s*\?\s*\[\s*LayoutSafeAreaEdge\.TOP\s*\]\s*:\s*\[\s*\]\s*;?\s*$/
const NAV_SAFE_AREA_ARGUMENTS =
  /nativeTopSafeAreaTypes\s*\(\s*appUIState\s*\)\s*,\s*nativeTopSafeAreaEdges\s*\(\s*appUIState\s*\)/
const ROOT_SAFE_AREA_ARGUMENTS =
  /nativeTopSafeAreaTypes\s*\(\s*this\.appUIState\s*\)\s*,\s*nativeTopSafeAreaEdges\s*\(\s*this\.appUIState\s*\)/
const NAV_SAFE_AREA_INITIALIZER =
  /this\.updateLayoutSafeArea\s*\(\s*appUIState\s*,\s*instance\s*\)/
const THEME_SAFE_AREA_REFRESH =
  /this\.modifier\.updateLayoutSafeArea\s*\(\s*this\.appUIState\s*\)/
const NATIVE_FROM_HOME_TOP_INSET =
  /private\s+get\s+nativeFromHomeTopInset\s*\(\s*\)\s*:\s*number\s*\{\s*if\s*\(\s*this\.shell\s*!==\s*HomeShellKind\.PhoneNativeHds\s*\)\s*\{\s*return\s+0\s*;?\s*\}\s*return\s+Math\.max\s*\(\s*0\s*,\s*this\.appUIState\.safeTop\s*\)\s*\+\s*UIConstants\.ACTION_BAR_HEIGHT\s*;?\s*\}/
const FROM_HOME_TOP_INSET_BODY =
  /^\s*return\s+this\.fromHome\s*\?\s*Math\.max\s*\(\s*0\s*,\s*this\.contentTopInset\s*\)\s*:\s*0\s*;?\s*$/
const CONTENT_TOP_INSET_PARAM =
  /@Param\s+contentTopInset\s*:\s*number\s*=\s*0/
const OWNED_TOP_LAYOUT_USE =
  /(?:\.padding\s*\(\s*\{[\s\S]{0,400}\btop\s*:\s*this\.ownedContentTopInset\s*\(\s*\)|\b(?:Blank|ListItem)\s*\(\s*\)\s*\.height\s*\(\s*this\.ownedContentTopInset\s*\(\s*\)\s*\))/
const OWNED_TOP_POSITIVE_GUARD =
  /if\s*\(\s*this\.ownedContentTopInset\s*\(\s*\)\s*>\s*0\s*\)/
const CONTENT_BUILDERS = [
  ['HomeTab', 'homeTabContent', 'homeContentBuilder'],
  ['ChasingTab', 'chasingTabContent', 'chasingContentBuilder'],
  ['FavoriteListPage', 'favoriteTabContent', 'favoriteContentBuilder'],
  ['MediaTab', 'mediaTabContent', 'mediaContentBuilder'],
  ['MineTab', 'mineTabContent', 'mineContentBuilder']
]

function requiredSource(sources, path) {
  const source = sources.get(path)
  if (source === undefined) {
    throw new Error('missing source: ' + path)
  }
  return source
}

function importsUidDesignSymbol(source, symbol) {
  const imports = source.match(/import\s*\{[\s\S]*?\}\s*from\s*['"]@kit\.UIDesignKit['"]/g) ?? []
  return imports.some((statement) => new RegExp('\\b' + symbol + '\\b').test(statement))
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
        return {
          body: source.slice(openingBrace + 1, index),
          start: openingBrace,
          end: index
        }
      }
    }
  }
  throw new Error('unterminated block')
}

function methodBlock(source, methodName) {
  const signature = new RegExp(
    '\\b(?:private\\s+)?' + methodName +
      '\\s*\\([^)]*\\)\\s*(?::\\s*[^\\{]+)?\\s*\\{'
  )
  const match = signature.exec(source)
  if (!match) {
    throw new Error('missing method: ' + methodName)
  }
  const openingBrace = source.indexOf('{', match.index)
  try {
    return bracedBlock(source, openingBrace).body
  } catch (error) {
    throw new Error('unterminated method: ' + methodName)
  }
}

function breakpointMonitorBlock(source) {
  const matches = [...source.matchAll(BREAKPOINT_MONITOR)]
  if (matches.length !== 1) {
    throw new Error('HomeScreen must define exactly one currentBreakpoint monitor')
  }

  const decoratorEnd = matches[0].index + matches[0][0].length
  const tail = source.slice(decoratorEnd)
  const signature =
    /^\s*(?:private\s+)?[A-Za-z_$][\w$]*\s*\([^)]*\)\s*(?::\s*[^\{]+)?\s*\{/
  const match = signature.exec(tail)
  if (!match) {
    throw new Error('currentBreakpoint monitor must decorate a method')
  }

  const openingBrace = decoratorEnd + match.index + match[0].lastIndexOf('{')
  try {
    return bracedBlock(source, openingBrace).body
  } catch (error) {
    throw new Error('unterminated currentBreakpoint monitor method')
  }
}

function firstConditionalBlock(source, pattern, label) {
  const match = pattern.exec(source)
  if (!match) {
    throw new Error('missing HomeScreen shell branch: ' + label)
  }
  return bracedBlock(source, source.indexOf('{', match.index))
}

function nextConditionalBlock(source, previousEnd, pattern, label) {
  const tail = source.slice(previousEnd + 1)
  const match = pattern.exec(tail)
  if (!match || match.index !== 0) {
    throw new Error('HomeScreen shell branches must be one mutually exclusive chain: ' + label)
  }
  const matchIndex = previousEnd + 1 + match.index
  return bracedBlock(source, source.indexOf('{', matchIndex))
}

function requireBranchOwner(block, owner, forbidden, label) {
  for (const other of forbidden) {
    if (new RegExp('\\b' + other + '\\s*\\(').test(block.body)) {
      throw new Error(label + ' branch constructs wrong chrome owner: ' + other)
    }
  }
  if (count(block.body, new RegExp('\\b' + owner + '\\s*\\(', 'g')) !== 1) {
    throw new Error(label + ' branch must own exactly one ' + owner)
  }
}

function builderParamClosure(source, builderParam) {
  const property = new RegExp(
    '\\b' + builderParam + '\\s*:\\s*\\(\\s*\\)\\s*=>\\s*\\{'
  )
  const match = property.exec(source)
  if (!match) {
    throw new Error(
      'HomeScreen must pass ' + builderParam + ' through an arrow closure'
    )
  }
  return bracedBlock(source, source.indexOf('{', match.index)).body
}

function validateFromHomeTopInsetOwner(source, component) {
  if (count(source, new RegExp(CONTENT_TOP_INSET_PARAM.source, 'g')) !== 1) {
    throw new Error(component + ' must declare one zero-default contentTopInset param')
  }

  const ownerBody = methodBlock(source, 'ownedContentTopInset')
  if (!FROM_HOME_TOP_INSET_BODY.test(ownerBody)) {
    throw new Error(component + ' must guard contentTopInset with fromHome')
  }
  if (count(source, /this\.contentTopInset\b/g) !== 1) {
    throw new Error(component + ' must read contentTopInset only inside its fromHome guard')
  }
  const ownedTopCallCount = count(source, /this\.ownedContentTopInset\s*\(\s*\)/g)
  const layoutUseCount = count(source, new RegExp(OWNED_TOP_LAYOUT_USE.source, 'g'))
  const guardCount = count(source, new RegExp(OWNED_TOP_POSITIVE_GUARD.source, 'g'))
  if (layoutUseCount !== 1 || guardCount > 1 || ownedTopCallCount !== layoutUseCount + guardCount) {
    throw new Error(component + ' must consume the owned top inset as exactly one top spacer')
  }
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function validateNativeThemeHostOwnership(sources) {
  const appState = requiredSource(sources, APP_STATE)
  const index = requiredSource(sources, INDEX)
  const rootPage = requiredSource(sources, ROOT_PAGE)
  const homeScreen = requiredSource(sources, HOME_SCREEN)
  const chromeTab = requiredSource(sources, CHROME_TAB)
  const standardHost = requiredSource(sources, STANDARD_HOST)
  const nativeHost = requiredSource(sources, NATIVE_HOST)
  const pointLightOwner = requiredSource(sources, POINT_LIGHT_OWNER)
  const hdsCapability = requiredSource(sources, HDS_CAPABILITY)
  const nativeThemeCapability = requiredSource(sources, NATIVE_THEME_CAPABILITY)
  const homeShellPolicy = requiredSource(sources, HOME_SHELL_POLICY)
  const chasingTab = requiredSource(sources, CHASING_TAB)
  const favoriteTab = requiredSource(sources, FAVORITE_TAB)
  const homeTab = requiredSource(sources, HOME_TAB)
  const homeViewModel = requiredSource(sources, HOME_VIEW_MODEL)
  const mediaTab = requiredSource(sources, MEDIA_TAB)

  for (const [path, source] of sources.entries()) {
    if (path !== NATIVE_HOST && path !== SHARED_DESTINATION && HDS_NAVIGATION.test(source)) {
      throw new Error('HDS navigation outside native host: ' + path)
    }
    if (path !== POINT_LIGHT_OWNER && importsUidDesignSymbol(source, 'hdsEffect')) {
      throw new Error('hdsEffect import outside point-light helper: ' + path)
    }
  }

  for (const path of [APP_STATE, HOME_SCREEN, CHROME_TAB, STANDARD_HOST]) {
    const source = requiredSource(sources, path)
    if (source.includes('@kit.UIDesignKit') || HDS_NAVIGATION.test(source)) {
      throw new Error('UIDesignKit leaked into shared or standard source: ' + path)
    }
  }

  for (const symbol of ['HdsTabs', 'HdsTabsController', 'HdsTabsModifier', 'hdsMaterial']) {
    if (!new RegExp('\\b' + symbol + '\\b').test(nativeHost)) {
      throw new Error('native host is missing ' + symbol)
    }
  }
  if (nativeHost.includes('CustomTab')) {
    throw new Error('native host must not use CustomTab')
  }
  if (!standardHost.includes('TabsController') || !standardHost.includes('CustomTab')) {
    throw new Error('standard host must own TabsController and CustomTab')
  }
  if (!STANDARD_INDEX.test(standardHost)) {
    throw new Error('standard host must bind external selectedIndex')
  }
  if (!NATIVE_INDEX.test(nativeHost)) {
    throw new Error('native host must bind external selectedIndex')
  }
  const nativeBarHeightCalls = nativeHost.match(/\.barHeight\s*\([^)]*\)/g) ?? []
  if (nativeBarHeightCalls.length === 0 ||
    nativeBarHeightCalls.length !== count(nativeHost, NATIVE_BAR_HEIGHT)) {
    throw new Error('native HDS bar height must exclude the bottom safe area')
  }
  const nativeSafeBottomBody = methodBlock(nativeHost, 'normalizedSafeBottom')
  if (!NATIVE_SAFE_BOTTOM_BODY.test(nativeSafeBottomBody)) {
    throw new Error('native safeBottom must be normalized without bottom-bar arithmetic')
  }
  if (!NATIVE_SAFE_BOTTOM_MARGIN.test(nativeHost) ||
    count(nativeHost, /barBottomMargin\s*:/g) !== 1) {
    throw new Error('native HDS floating bar must own the bottom safe area exactly once')
  }
  if (!NATIVE_ROOT_HEIGHT.test(nativeHost) || nativeHost.includes('navigationChromeInset')) {
    throw new Error('native HDS tabs must be a full-page content host')
  }
  if (count(nativeHost, /\bTabContent\s*\(\s*\)/g) !== 4) {
    throw new Error('native HDS tabs must own exactly four visible TabContent pages')
  }
  if (/\b(?:chasingContentBuilder|chasingBadgeCount)\b|['"]追剧['"]/.test(nativeHost)) {
    throw new Error('native HDS phone tabs must hide the chasing destination')
  }
  const tabBuilder = methodBlock(nativeHost, 'tabBuilder')
  if (NATIVE_FIXED_ITEM_WIDTH.test(tabBuilder) ||
    !/}\s*\.height\s*\(\s*['"]100%['"]\s*\)\s*\.width\s*\(\s*['"]100%['"]\s*\)/
      .test(tabBuilder)) {
    throw new Error('native tab items must fit the width allocated by HDS')
  }
  if (!CONTENT_INDEX.test(homeScreen)) {
    throw new Error('content Tabs must bind semantic external index')
  }
  if (!appState.includes('feiniuPointLightEnabled') || appState.includes('illuminatedType')) {
    throw new Error('AppUIState must expose only the generic point-light boolean')
  }
  if (!/static\s+readonly\s+MIN_API_VERSION\s*:\s*number\s*=\s*24\b/.test(nativeThemeCapability) ||
    !/static\s+readonly\s+MIN_SYSTEM_MATERIAL_API_VERSION\s*:\s*number\s*=\s*26\b/.test(
      nativeThemeCapability)) {
    throw new Error('Native theme must start at API 24 while system material remains API 26-only')
  }
  const unsupportedApiIndex = nativeThemeCapability.indexOf(
    'sdkApiVersion < NativeThemeCapability.MIN_API_VERSION'
  )
  const blurFallbackIndex = nativeThemeCapability.indexOf(
    'sdkApiVersion < NativeThemeCapability.MIN_SYSTEM_MATERIAL_API_VERSION'
  )
  const materialReadIndex = nativeThemeCapability.indexOf('try {', blurFallbackIndex)
  if (unsupportedApiIndex < 0 || blurFallbackIndex < 0 || materialReadIndex < 0 ||
    unsupportedApiIndex > blurFallbackIndex || blurFallbackIndex > materialReadIndex) {
    throw new Error('Native theme capability must split unsupported, blur fallback, and system material')
  }
  const blurFallbackBranch = nativeThemeCapability.slice(blurFallbackIndex, materialReadIndex)
  if (!/available\s*:\s*true/.test(blurFallbackBranch) ||
    !/systemMaterialAvailable\s*:\s*false/.test(blurFallbackBranch) ||
    !/renderMode\s*:\s*NativeThemeRenderMode\.BlurFallback/.test(blurFallbackBranch)) {
    throw new Error('API 24–25 Native theme must use blur fallback without system material')
  }
  if (!/static\s+readonly\s+MIN_HDS_API_VERSION\s*:\s*number\s*=\s*24\b/.test(hdsCapability) ||
    !/^\s*return\s+deviceInfo\.sdkApiVersion\s*>=\s*HdsUiCapability\.MIN_HDS_API_VERSION\s*;?\s*$/
      .test(methodBlock(hdsCapability, 'supportsHdsComponents')) ||
    !/^\s*return\s+nativeThemeCapability\.isAvailable\s*\(\s*\)\s*;?\s*$/
      .test(methodBlock(hdsCapability, 'supportsNativeTheme')) ||
    !/^\s*return\s+nativeThemeCapability\.isSystemMaterialAvailable\s*\(\s*\)\s*;?\s*$/
      .test(methodBlock(hdsCapability, 'supportsSystemMaterial'))) {
    throw new Error('HDS capability must separate API 24 components from API 26 system material')
  }
  if (!/if\s*\(\s*theme\s*===\s*ThemeStyle\.Native\s*&&\s*nativeAvailable\s*\)\s*\{\s*return\s+HomeShellKind\.PhoneNativeHds/.test(
    homeShellPolicy) ||
    !/return\s+HomeShellKind\.PhoneStandard/.test(homeShellPolicy)) {
    throw new Error('phone shell must allow API 24–25 Native HDS while keeping Feiniu legacy')
  }
  if (!ROOT_TITLE_BAR_STATE.test(appState)) {
    throw new Error('AppUIState must persist a default-hidden root title-bar state')
  }
  if (!ROOT_TITLE_STATE.test(appState)) {
    throw new Error('AppUIState must persist the current root destination title')
  }
  if (!ROOT_HOME_ACTIONS_STATE.test(appState)) {
    throw new Error('AppUIState must keep root Home actions hidden by default')
  }
  if (!ROOT_MEDIA_ADMIN_ACTIONS_STATE.test(appState)) {
    throw new Error('AppUIState must keep root media administrator actions hidden by default')
  }
  if (!ROOT_HOME_HERO_STATE.test(appState)) {
    throw new Error('AppUIState must persist a default-hidden root Home Hero presence state')
  }
  if (!ROOT_LIVE_TV_STATE.test(appState)) {
    throw new Error('AppUIState must persist root Live TV menu availability')
  }

  const initializeModifier = methodBlock(index, 'initializeModifier')
  if (count(initializeModifier, /instance\.hideTitleBar\s*\(/g) !== 1 ||
    !ROOT_TITLE_BAR_INITIALIZER.test(initializeModifier)) {
    throw new Error('outer Navigation title bar must remain permanently hidden')
  }
  if (!/appUIState\.navInstance\s*=\s*instance/.test(initializeModifier)) {
    throw new Error('NavModifier must publish the current NavigationAttribute instance')
  }
  if (!NAV_SAFE_AREA_INITIALIZER.test(initializeModifier)) {
    throw new Error('NavModifier must initialize the NavigationAttribute safe-area contract')
  }

  const navSafeAreaGate = methodBlock(index, 'usesNativeTopSafeArea')
  if (!NATIVE_NAV_SAFE_AREA_GATE_BODY.test(navSafeAreaGate)) {
    throw new Error('Navigation safe-area expansion must require Native theme availability')
  }
  const navSafeAreaTypes = methodBlock(index, 'nativeTopSafeAreaTypes')
  const navSafeAreaEdges = methodBlock(index, 'nativeTopSafeAreaEdges')
  if (!NAV_SAFE_AREA_TYPES_BODY.test(navSafeAreaTypes) ||
    !NAV_SAFE_AREA_EDGES_BODY.test(navSafeAreaEdges)) {
    throw new Error('Navigation safe-area expansion must preserve the Feiniu safe-area layout')
  }
  const updateLayoutSafeArea = methodBlock(index, 'updateLayoutSafeArea')
  if (!NAV_SAFE_AREA_INSTANCE.test(updateLayoutSafeArea) ||
    count(updateLayoutSafeArea, /navInstance\.ignoreLayoutSafeArea\s*\(/g) !== 1) {
    throw new Error('NavModifier must apply the top safe area to the real NavigationAttribute')
  }
  if (!NAV_SAFE_AREA_ARGUMENTS.test(updateLayoutSafeArea)) {
    throw new Error('NavModifier must reuse the theme-gated top safe-area contract')
  }
  if (count(index, THEME_STYLE_MONITOR) !== 1) {
    throw new Error('Index must monitor themeStyle for Navigation safe-area updates')
  }
  const themeStyleChanged = methodBlock(index, 'onThemeStyleChanged')
  if (!THEME_SAFE_AREA_REFRESH.test(themeStyleChanged)) {
    throw new Error('Index must refresh Navigation safe area when the theme changes')
  }

  const indexBuild = methodBlock(index, 'build')
  const rootStackMatch = /\bStack\s*\(\s*\)\s*\{/.exec(indexBuild)
  if (!rootStackMatch) {
    throw new Error('Index must keep one root Stack host')
  }
  const rootStack = bracedBlock(indexBuild, indexBuild.indexOf('{', rootStackMatch.index))
  const rootStackTail = indexBuild.slice(rootStack.end + 1)
  const nestedSafeAreaOwners = count(rootStack.body, /\.ignoreLayoutSafeArea\s*\(/g)
  if (nestedSafeAreaOwners !== 0) {
    throw new Error('nested root content must not duplicate top safe-area ownership')
  }
  if (count(rootStackTail, /\.ignoreLayoutSafeArea\s*\(/g) !== 1 ||
    !ROOT_SAFE_AREA_ARGUMENTS.test(rootStackTail) ||
    count(index, /\.ignoreLayoutSafeArea\s*\(/g) !== 2) {
    throw new Error('root Stack and real Navigation must extend into the Native top safe area')
  }
  if (/\bsafeTop\b|\.padding\s*\(\s*\{[^}]*\btop\s*:/.test(index)) {
    throw new Error('Navigation safe-area expansion must not hard-code or duplicate the top inset')
  }
  if (/\btitleValue\s*:|\bmenus\s*:|\bHMRouterMgr\b/.test(index)) {
    throw new Error('outer Navigation must not own the root title or menu actions')
  }

  if (!/@HMRouter\s*\(\s*\{[\s\S]*?pageUrl\s*:\s*RouterConsts\.IndexPage[\s\S]*?useNavDst\s*:\s*true[\s\S]*?\}\s*\)/
    .test(rootPage) || count(rootPage, /\bAppRouteDestination\s*\(/g) !== 1) {
    throw new Error('IndexPage must opt out of HMRouter wrapping and own one AppRouteDestination')
  }
  const rootBuild = methodBlock(rootPage, 'build')
  for (const required of [
    /title\s*:\s*this\.appUIState\.rootNavigationTitle/,
    /titleBarVisible\s*:\s*this\.appUIState\.isLogin\s*&&\s*this\.appUIState\.rootNavigationTitleBarVisible/,
    /backButtonVisible\s*:\s*false/,
    /menus\s*:\s*this\.rootNavigationMenus\s*\(\s*\)/,
    /contentBuilder\s*:[\s\S]*?this\.pageContent\s*\(\s*\)/,
    /legacyContentBuilder\s*:[\s\S]*?this\.pageContent\s*\(\s*\)/
  ]) {
    if (!required.test(rootBuild)) {
      throw new Error('IndexPage must bind the current root destination title, menus, and content')
    }
  }
  const rootHeroChrome = methodBlock(rootPage, 'rootNavigationHeroChromeVisible')
  if (!/contentExtendsUnderTitleBar\s*:\s*this\.appUIState\.rootNavigationTitleBarVisible/.test(rootBuild)) {
    throw new Error('IndexPage must keep root content extension stable while Hero data changes')
  }
  if (!/titleMaterialFollowsSystem\s*:\s*true/.test(rootBuild)) {
    throw new Error('IndexPage root title material must follow the system')
  }
  if (!/^\s*return\s+this\.appUIState\.rootNavigationHomeActionsVisible\s*&&\s*this\.appUIState\.rootNavigationHomeHeroVisible\s*;?\s*$/.test(
    rootHeroChrome) ||
    !/heroTitleChrome\s*:\s*this\.rootNavigationHeroChromeVisible\s*\(\s*\)/.test(rootBuild) ||
    count(rootBuild, /this\.rootNavigationHeroChromeVisible\s*\(\s*\)/g) !== 1) {
    throw new Error('IndexPage Hero title chrome must use the populated Home Hero gate')
  }
  const rootMenus = methodBlock(rootPage, 'rootNavigationMenus')
  if (!/if\s*\(\s*!\s*this\.appUIState\.rootNavigationHomeActionsVisible\s*\)\s*\{[\s\S]*?return\s+menus/.test(rootMenus) ||
    !/this\.appUIState\.rootNavigationLiveTvAvailable/.test(rootMenus) ||
    !/HMRouterMgr\.to\s*\(\s*RouterConsts\.LiveTvPage\s*\)\.push\s*\(\s*\)/.test(rootMenus) ||
    !/HMRouterMgr\.to\s*\(\s*RouterConsts\.SearchPage\s*\)\.push\s*\(\s*\)/.test(rootMenus)) {
    throw new Error('IndexPage must expose Live TV and search menus only on the Home tab')
  }

  const rootTitle = methodBlock(homeScreen, 'rootNavigationTitle')
  for (const [destination, title] of [
    ['Chasing', '追剧'],
    ['Favorite', '收藏'],
    ['Media', '媒体库'],
    ['Mine', '我的']
  ]) {
    const mapping = new RegExp(
      'HomeDestination\\.' + destination + '[\\s\\S]{0,120}return\\s+[\'\"]' + title + '[\'\"]'
    )
    if (!mapping.test(rootTitle)) {
      throw new Error('HomeScreen must map every HomeDestination to its Native title')
    }
  }
  if (!/return\s+['"]首页['"]/.test(rootTitle)) {
    throw new Error('HomeScreen must map Home to the Home Native title')
  }

  const publishRootChrome = methodBlock(homeScreen, 'publishRootNavigationChrome')
  if (count(publishRootChrome, ROOT_TITLE_BAR_STATE_UPDATE) !== 1 ||
    count(publishRootChrome, ROOT_TITLE_STATE_UPDATE) !== 1 ||
    count(publishRootChrome, ROOT_HOME_ACTIONS_STATE_UPDATE) !== 1 ||
    count(publishRootChrome, ROOT_MEDIA_ADMIN_ACTIONS_STATE_UPDATE) !== 1 ||
    /navInstance|hideTitleBar/.test(publishRootChrome)) {
    throw new Error('HomeScreen must publish only the current root destination chrome state')
  }
  const updateRootTitleBar = methodBlock(homeScreen, 'updateRootNavigationTitleBar')
  if (!/^\s*const\s+visible\s*=\s*this\.resolveShell\s*\(\s*\)\s*===\s*HomeShellKind\.PhoneNativeHds\s*$/m
      .test(updateRootTitleBar) ||
    !/this\.publishRootNavigationChrome\s*\(\s*visible\s*,\s*this\.rootNavigationTitle\s*\(\s*\)\s*,\s*visible\s*&&\s*this\.ui\.selectedDestination\s*===\s*HomeDestination\.Home\s*,\s*visible\s*&&\s*this\.ui\.selectedDestination\s*===\s*HomeDestination\.Media\s*&&\s*this\.mediaAdministrator\s*\)/
      .test(updateRootTitleBar)) {
    throw new Error('HomeScreen must keep the Native title bar visible across tabs and scope root actions')
  }
  const disappear = methodBlock(homeScreen, 'aboutToDisappear')
  if (!/this\.publishRootNavigationChrome\s*\(\s*false\s*,\s*['"]首页['"]\s*,\s*false\s*,\s*false\s*\)/.test(disappear)) {
    throw new Error('HomeScreen must clear root destination chrome when leaving the page')
  }
  if (!NATIVE_FROM_HOME_TOP_INSET.test(homeScreen)) {
    throw new Error('HomeScreen must reserve the Native phone title bar and safe top for secondary tabs')
  }

  const syncLiveTv = methodBlock(homeTab, 'syncRootNavigationLiveTvAvailability')
  if (!/this\.vm\.appUIState\.rootNavigationLiveTvAvailable\s*=\s*this\.ui\.hasLiveTv/.test(syncLiveTv) ||
    /navInstance\?\.menus|nativeNavigationMenus/.test(homeTab)) {
    throw new Error('HomeTab must publish menu availability without mutating the outer Navigation')
  }
  const homeAboutToAppear = methodBlock(homeTab, 'aboutToAppear')
  if (!/this\.syncRootNavigationLiveTvAvailability\s*\(\s*\)/.test(
    methodBlock(homeTab, 'onLiveTvAvailabilityChange')) ||
    !/this\.syncRootNavigationLiveTvAvailability\s*\(\s*\)/.test(homeAboutToAppear)) {
    throw new Error('HomeTab must keep root menu availability synchronized')
  }

  if (count(homeTab, RECOMMENDATION_LIST_MONITOR) !== 0 ||
    /syncRootNavigationHomeHeroVisibility/.test(homeTab)) {
    throw new Error('HomeTab must not feed recommendation state back into its own route host')
  }

  const recommendationLoad = methodBlock(homeViewModel, 'getRecommendationList')
  if (!/this\.ui\.recommendationList\s*=\s*recommendations[\s\S]{0,200}const\s+heroVisible\s*=\s*recommendations\.length\s*>\s*0/.test(
    recommendationLoad)) {
    throw new Error('HomeViewModel must derive Hero visibility from the committed recommendation result')
  }
  if (!/if\s*\(\s*this\.appUIState\.rootNavigationHomeHeroVisible\s*!==\s*heroVisible\s*\)\s*\{\s*this\.appUIState\.rootNavigationHomeHeroVisible\s*=\s*heroVisible/.test(
    recommendationLoad)) {
    throw new Error('HomeViewModel must suppress redundant Hero visibility writes')
  }

  const breakpointHandler = breakpointMonitorBlock(homeScreen)
  if (!/this\.updateFeiniuPointLightEnabled\s*\(\s*\)/.test(breakpointHandler)) {
    throw new Error('breakpoint monitor must update Feiniu point-light state')
  }
  if (!/this\.appUIState\.isBigScreen\s*=\s*!\s*this\.appUIState\.currentBreakpoint\.includes\s*\(\s*['"]s['"]\s*\)/
    .test(breakpointHandler)) {
    throw new Error('breakpoint monitor must update isBigScreen')
  }

  if (!/this\.syncPadShellVisibility\s*\(\s*\)/.test(breakpointHandler)) {
    throw new Error('breakpoint monitor must synchronize tablet shell visibility')
  }
  const shellVisibility = methodBlock(homeScreen, 'syncPadShellVisibility')
  if (!/this\.ui\.isMenuModalVisible\s*=\s*false/.test(shellVisibility) ||
    !/HomeShellPolicy\.usesOverlayDrawer\(shell\)[\s\S]*this\.ui\.isLeftSidebarVisible\s*=\s*false/.test(shellVisibility) ||
    !/HomeShellPolicy\.usesEmbeddedSidebar\(shell\)[\s\S]*this\.ui\.isLeftSidebarVisible\s*=\s*true/.test(shellVisibility)) {
    throw new Error('tablet shell visibility must reserve the embedded sidebar for LG only')
  }
  if (!/this\.contentTabsController\.changeIndex\s*\(\s*this\.contentSelectedIndex\s*\(\s*\)\s*\)/
    .test(breakpointHandler)) {
    throw new Error('breakpoint monitor must synchronize semantic content index')
  }

  for (const branch of [
    'largeSidebarBuilder',
    'mediumDrawerBuilder',
    'HomePhoneNativeTabs',
    'HomePhoneStandardTabs'
  ]) {
    if (!homeScreen.includes(branch)) {
      throw new Error('missing HomeScreen branch: ' + branch)
    }
  }

  const contentOwner = methodBlock(homeScreen, 'homeContentOwner')
  const legacyPadContent = methodBlock(homeScreen, 'legacyPadContent')
  const nonNativeShell = methodBlock(homeScreen, 'nonNativeHomeShell')
  const buildBlock = methodBlock(homeScreen, 'build')

  if (!/Blank\s*\(\s*\)\.height\s*\(\s*88\s*\)/.test(legacyPadContent) ||
    !/Blank\s*\(\s*\)\.width\s*\(\s*this\.ui\.isLeftSidebarVisible\s*\?\s*252\s*:\s*12\s*\)/
      .test(legacyPadContent) ||
    count(legacyPadContent, /this\.homeContentOwner\s*\(/g) !== 1) {
    throw new Error('legacy tablet content must reserve the title strip and sidebar gutter')
  }
  if (!/HomeShellPolicy\.usesOverlayDrawer\(shell\)\s*\|\|\s*HomeShellPolicy\.usesEmbeddedSidebar\(shell\)[\s\S]*?this\.legacyPadContent\s*\(\s*shell\s*\)/
    .test(nonNativeShell) || count(nonNativeShell, /this\.legacyPadContent\s*\(/g) !== 1) {
    throw new Error('wide legacy shells must route through legacyPadContent')
  }

  for (const [component, builder, builderParam] of CONTENT_BUILDERS) {
    const constructorSource = '\\b' + component + '\\s*\\(\\s*\\{'
    const contentBuilder = methodBlock(homeScreen, builder)
    if (count(contentBuilder, new RegExp(constructorSource, 'g')) !== 1) {
      throw new Error(component + ' must appear exactly once inside ' + builder)
    }
    if (count(homeScreen, new RegExp(constructorSource, 'g')) !== 1) {
      throw new Error(component + ' must have one shared content builder')
    }
    if (new RegExp(constructorSource).test(standardHost) ||
      new RegExp(constructorSource).test(nativeHost) ||
      new RegExp(constructorSource).test(chromeTab)) {
      throw new Error('navigation chrome must not construct ' + component)
    }
    if (count(contentOwner, new RegExp('this\\.' + builder + '\\s*\\(', 'g')) !== 1) {
      throw new Error('standard content Tabs must invoke ' + builder + ' exactly once')
    }
    if (builderParam === 'chasingContentBuilder') {
      if (new RegExp('@BuilderParam\\s+' + builderParam + '\\s*:').test(nativeHost) ||
        count(nativeHost, new RegExp('this\\.' + builderParam + '\\s*\\(', 'g')) !== 0 ||
        count(buildBlock, new RegExp('\\b' + builderParam + '\\s*:', 'g')) !== 0) {
        throw new Error('native HDS phone tabs must hide ' + builderParam)
      }
      continue
    }
    if (!new RegExp('@BuilderParam\\s+' + builderParam + '\\s*:').test(nativeHost) ||
      count(nativeHost, new RegExp('this\\.' + builderParam + '\\s*\\(', 'g')) !== 1) {
      throw new Error('native HDS tabs must invoke ' + builderParam + ' exactly once')
    }
    if (new RegExp(
      '\\b' + builderParam + '\\s*:\\s*this\\.' + builder + '\\b'
    ).test(buildBlock)) {
      throw new Error('HomeScreen must not pass a bare parent builder reference: ' + builderParam)
    }
    if (count(buildBlock, new RegExp('\\b' + builderParam + '\\s*:', 'g')) !== 1) {
      throw new Error('HomeScreen must pass exactly one ' + builderParam)
    }
    const closureBody = builderParamClosure(buildBlock, builderParam)
    if (count(closureBody, new RegExp('this\\.' + builder + '\\s*\\(', 'g')) !== 1) {
      throw new Error('HomeScreen arrow closure must invoke ' + builder + ' exactly once')
    }
  }


  for (const [component, builder] of [
    ['ChasingTab', 'chasingTabContent'],
    ['FavoriteListPage', 'favoriteTabContent']
  ]) {
    const contentBuilder = methodBlock(homeScreen, builder)
    if (!/\bfromHome\s*:\s*true/.test(contentBuilder) ||
      count(contentBuilder, /\bcontentTopInset\s*:\s*this\.nativeFromHomeTopInset\b/g) !== 1) {
      throw new Error(component + ' must receive the native from-home top inset')
    }
  }
  if (count(homeScreen, /\bcontentTopInset\s*:/g) !== 2) {
    throw new Error('HomeScreen must pass contentTopInset only to ChasingTab and FavoriteListPage')
  }
  const mediaBuilder = methodBlock(homeScreen, 'mediaTabContent')
  if (!/rootTitleBarOwned\s*:\s*this\.shell\s*===\s*HomeShellKind\.PhoneNativeHds/.test(mediaBuilder)) {
    throw new Error('MediaTab must delegate its title only to the phone Native HDS destination')
  }
  for (const [component, builder] of [
    ['HomeTab', 'homeTabContent'],
    ['MediaTab', 'mediaTabContent'],
    ['MineTab', 'mineTabContent']
  ]) {
    if (/\bcontentTopInset\s*:/.test(methodBlock(homeScreen, builder))) {
      throw new Error(component + ' must not receive the native from-home top inset')
    }
  }

  validateFromHomeTopInsetOwner(chasingTab, 'ChasingTab')
  validateFromHomeTopInsetOwner(favoriteTab, 'FavoriteListPage')

  if (!/@Param\s+rootTitleBarOwned\s*:\s*boolean\s*=\s*false/.test(mediaTab)) {
    throw new Error('MediaTab must default to legacy title ownership')
  }
  const mediaTopSpacer = methodBlock(mediaTab, 'contentTopSpacer')
  if (!/const\s+safeTop\s*=\s*Math\.max\s*\(\s*0\s*,\s*this\.appUIState\.safeTop\s*\)/.test(mediaTopSpacer) ||
    !/return\s+safeTop\s*\+\s*UIConstants\.ACTION_BAR_HEIGHT/.test(mediaTopSpacer)) {
    throw new Error('MediaTab must reserve the safe top and one title-bar height for its content')
  }
  const mediaTopBar = methodBlock(mediaTab, 'topBar')
  const mediaTitleGuard = /\bif\s*\(\s*!\s*this\.rootTitleBarOwned\s*\)\s*\{/.exec(mediaTopBar)
  if (!mediaTitleGuard) {
    throw new Error('MediaTab custom ActionBar must stay outside the phone Native HDS branch')
  }
  const mediaTitleOwner = bracedBlock(mediaTopBar, mediaTopBar.indexOf('{', mediaTitleGuard.index))
  if (count(mediaTopBar, /\bActionBar\s*\(/g) === 0 ||
    count(mediaTopBar, /\bActionBar\s*\(/g) !== count(mediaTitleOwner.body, /\bActionBar\s*\(/g) ||
    !/Blank\s*\(\s*\)\.height\s*\(\s*this\.contentTopSpacer\s*\(\s*\)\s*\)/.test(mediaTab)) {
    throw new Error('MediaTab custom ActionBar must stay outside the phone Native HDS branch')
  }

  const ownerCall = /this\.homeContentOwner\s*\(/g
  if (count(nonNativeShell, ownerCall) !== 1 || count(buildBlock, ownerCall) !== 0) {
    throw new Error('only the non-native shell may call homeContentOwner')
  }
  const ownerCallIndex = nonNativeShell.search(/this\.homeContentOwner\s*\(/)
  const largeBlock = firstConditionalBlock(
    nonNativeShell,
    /\bif\s*\(\s*HomeShellPolicy\.usesEmbeddedSidebar\(shell\)\s*\)\s*\{/,
    'large sidebar'
  )
  const mediumBlock = nextConditionalBlock(
    nonNativeShell,
    largeBlock.end,
    /^\s*else\s+if\s*\(\s*HomeShellPolicy\.usesOverlayDrawer\(shell\)\s*\)\s*\{/,
    'medium drawer'
  )
  const standardBlock = nextConditionalBlock(
    nonNativeShell,
    mediumBlock.end,
    /^\s*else\s*\{/,
    'standard phone'
  )
  if (ownerCallIndex > largeBlock.start) {
    throw new Error('homeContentOwner must precede the non-native chrome branch')
  }

  const nativeBlock = firstConditionalBlock(
    buildBlock,
    /\bif\s*\(\s*(?:this\.)?shell\s*===\s*HomeShellKind\.PhoneNativeHds\s*\)\s*\{/,
    'native phone'
  )
  const nonNativeBlock = nextConditionalBlock(
    buildBlock,
    nativeBlock.end,
    /^\s*else\s*\{/,
    'non-native shell'
  )

  requireBranchOwner(nativeBlock, 'HomePhoneNativeTabs', ['nonNativeHomeShell'], 'native phone')
  requireBranchOwner(nonNativeBlock, 'nonNativeHomeShell', ['HomePhoneNativeTabs'], 'non-native')
  requireBranchOwner(largeBlock, 'largeSidebarBuilder',
    ['mediumDrawerBuilder', 'HomePhoneStandardTabs'], 'large sidebar')
  requireBranchOwner(mediumBlock, 'mediumDrawerBuilder',
    ['largeSidebarBuilder', 'HomePhoneStandardTabs'], 'medium drawer')
  requireBranchOwner(standardBlock, 'HomePhoneStandardTabs',
    ['largeSidebarBuilder', 'mediumDrawerBuilder'], 'standard phone')

  for (const owner of ['HomePhoneNativeTabs', 'HomePhoneStandardTabs']) {
    if (count(homeScreen, new RegExp('\\b' + owner + '\\s*\\(', 'g')) !== 1) {
      throw new Error('HomeScreen must construct exactly one ' + owner)
    }
  }

  for (const path of TAB_CONTENT_FILES) {
    const source = sources.get(path)
    if (source !== undefined && DIRECT_INSET.test(source)) {
      throw new Error('tab content owns bottom navigation arithmetic: ' + path)
    }
  }

  const navigationProbeIndex = nativeHost.indexOf(
    'NativeThemeConstructionProbe.markHdsNavigationConstruction()'
  )
  const navigationConstructorIndex = nativeHost.indexOf('new HdsTabsController()')
  if (navigationProbeIndex < 0 || navigationConstructorIndex < 0 ||
    navigationProbeIndex > navigationConstructorIndex) {
    throw new Error('navigation probe must precede HdsTabsController construction')
  }

  const versionGuardIndex = pointLightOwner.indexOf(
    'deviceInfo.sdkApiVersion < FeiniuPointLightModifier.MIN_API_VERSION'
  )
  const enabledGuardIndex = pointLightOwner.indexOf('!options.enabled')
  const effectProbeIndex = pointLightOwner.indexOf(
    'NativeThemeConstructionProbe.markHdsEffectConstruction()'
  )
  const constructorIndex = pointLightOwner.indexOf('new hdsEffect.HdsEffectBuilder()')
  if (versionGuardIndex < 0 || enabledGuardIndex < 0 || effectProbeIndex < 0 ||
    constructorIndex < 0 || versionGuardIndex > effectProbeIndex ||
    enabledGuardIndex > effectProbeIndex || effectProbeIndex > constructorIndex) {
    throw new Error('point-light guards and probe must precede construction')
  }
  if (!/static\s+readonly\s+MIN_API_VERSION\s*:\s*number\s*=\s*26\b/.test(
    pointLightOwner
  )) {
    throw new Error('point-light must remain disabled on API 24–25')
  }
  const pointLightToggle = methodBlock(homeScreen, 'updateFeiniuPointLightEnabled')
  if (!/const\s+supportsPointLight\s*=\s*HdsUiCapability\.supportsSystemMaterial\s*\(\s*\)/
    .test(pointLightToggle) ||
    !/this\.appUIState\.themeStyle\s*===\s*ThemeStyle\.Feiniu/.test(pointLightToggle) ||
    !/shell\s*===\s*HomeShellKind\.MediumDrawer[\s\S]*shell\s*===\s*HomeShellKind\.LargeSidebar/
      .test(pointLightToggle)) {
    throw new Error('wide-shell point light must require API 26 system material and Feiniu theme')
  }
}

function collectEtsFiles(directory, root, sources) {
  for (const name of readdirSync(directory)) {
    const absolute = resolve(directory, name)
    if (statSync(absolute).isDirectory()) {
      collectEtsFiles(absolute, root, sources)
    } else if (name.endsWith('.ets')) {
      sources.set(relative(root, absolute), readFileSync(absolute, 'utf8'))
    }
  }
}

export function validateWorkspace(root = defaultWorkspaceRoot()) {
  const sources = new Map()
  collectEtsFiles(resolve(root, 'entry/src/main/ets'), root, sources)
  validateNativeThemeHostOwnership(sources)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspace()
  console.log('Native theme host ownership verified')
}
