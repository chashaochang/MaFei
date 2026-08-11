import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const DESTINATION = 'entry/src/main/ets/component/AppRouteDestination.ets'
const PLAYER_ENGINE = 'entry/src/main/ets/features/setting/PlayerEnginePage.ets'
const ABOUT = 'entry/src/main/ets/features/setting/AboutPage.ets'
const SEARCH = 'entry/src/main/ets/features/search/SearchPage.ets'
const VIDEO_LIST = 'entry/src/main/ets/features/videolist/VideoListPage.ets'
const FAVORITE_LIST = 'entry/src/main/ets/features/favorite/FavoriteListPage.ets'
const LIVE_TV_CHANNEL_LIST = 'entry/src/main/ets/features/livetv/LiveTvChannelListPage.ets'
const MANAGEMENT_DASHBOARD = 'entry/src/main/ets/features/management/ManagementDashboardPage.ets'
const MANAGEMENT_USERS = 'entry/src/main/ets/features/management/ManagementUsersPage.ets'
const MANAGEMENT_SESSION_DETAIL = 'entry/src/main/ets/features/management/sessions/ManagementSessionDetailPage.ets'
const MANAGEMENT_DEVICES = 'entry/src/main/ets/features/management/devices/ManagementDevicesPage.ets'
const MANAGEMENT_DEVICE_DETAIL = 'entry/src/main/ets/features/management/devices/ManagementDeviceDetailPage.ets'
const MANAGEMENT_ACTIVITY = 'entry/src/main/ets/features/management/activity/ManagementActivityPage.ets'
const MANAGEMENT_TASK_DETAIL = 'entry/src/main/ets/features/management/ManagementTaskDetailPage.ets'
const MANAGEMENT_USER_CREATE = 'entry/src/main/ets/features/management/ManagementUserCreatePage.ets'
const MANAGEMENT_USER_DETAIL = 'entry/src/main/ets/features/management/ManagementUserDetailPage.ets'
const MINE_TAB = 'entry/src/main/ets/features/home/minetab/MineTab.ets'
const SETTING_PAGE = 'entry/src/main/ets/features/setting/SettingPage.ets'
const ROUTE_PAGES = [
  PLAYER_ENGINE,
  SETTING_PAGE,
  ABOUT,
  SEARCH,
  VIDEO_LIST,
  FAVORITE_LIST,
  LIVE_TV_CHANNEL_LIST,
  MANAGEMENT_DASHBOARD,
  MANAGEMENT_USERS,
  MANAGEMENT_SESSION_DETAIL,
  MANAGEMENT_TASK_DETAIL,
  MANAGEMENT_DEVICES,
  MANAGEMENT_DEVICE_DETAIL,
  MANAGEMENT_ACTIVITY,
  MANAGEMENT_USER_CREATE,
  MANAGEMENT_USER_DETAIL
]
const EMBEDDED_ROUTE_PAGES = new Set([VIDEO_LIST, FAVORITE_LIST])
const RESOLVER_BACKGROUND_PAGES = new Set([
  MANAGEMENT_DASHBOARD,
  MANAGEMENT_USERS,
  MANAGEMENT_SESSION_DETAIL,
  MANAGEMENT_TASK_DETAIL,
  MANAGEMENT_USER_CREATE,
  MANAGEMENT_USER_DETAIL,
  MANAGEMENT_DEVICES,
  MANAGEMENT_DEVICE_DETAIL,
  MANAGEMENT_ACTIVITY
])
const LEGACY_ROUTE_BACKGROUNDS = new Map([
  [PLAYER_ENGINE, 'start_window_background'],
  [SETTING_PAGE, 'start_window_background'],
  [ABOUT, 'bg_main'],
  [SEARCH, 'bg_main'],
  [VIDEO_LIST, 'bg_main'],
  [FAVORITE_LIST, 'bg_main'],
  [LIVE_TV_CHANNEL_LIST, 'bg_main']
])
const INTERNAL_ABOUT_COPY =
  /维护阶段|功能清单|核心能力|2\.0\s*(?:方向|路线图)?|路线图|开发重点|开发计划|任务计划|计划任务|1\.x\s*稳定维护|稳定维护中|当前阶段|设备适配中|产品方向|媒体浏览|播放能力|连接你自己的/

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
        return {
          body: source.slice(openingBrace + 1, index),
          end: index
        }
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
  return bracedBlock(source, source.indexOf('{', match.index)).body
}

function propertyBlock(source, propertyName) {
  const match = new RegExp('\\b' + propertyName + '\\s*:\\s*\\{').exec(source)
  if (!match) {
    throw new Error('missing object property: ' + propertyName)
  }
  return bracedBlock(source, source.indexOf('{', match.index)).body
}

function componentBlock(source, componentName) {
  const pattern = new RegExp('\\b' + componentName + '\\s*\\(\\s*\\)\\s*\\{', 'g')
  const matches = [...source.matchAll(pattern)]
  if (matches.length !== 1) {
    throw new Error(componentName + ' must be constructed exactly once')
  }
  const openingBrace = source.indexOf('{', matches[0].index)
  return {
    start: matches[0].index,
    ...bracedBlock(source, openingBrace)
  }
}

function contentBranches(source) {
  const condition = /if\s*\(\s*this\.usesNativeChrome\s*\(\s*\)\s*\)\s*\{/
  const match = condition.exec(source)
  if (!match) {
    throw new Error('HDS destination content must switch chrome without replacing the route root')
  }
  const nativeBlock = bracedBlock(source, source.indexOf('{', match.index))
  const tail = source.slice(nativeBlock.end + 1)
  const elseMatch = /^\s*else\s*\{/.exec(tail)
  if (!elseMatch) {
    throw new Error('HDS destination content must preserve the Feiniu content branch')
  }
  const elseStart = nativeBlock.end + 1 + tail.indexOf('{', elseMatch.index)
  return {
    native: nativeBlock.body,
    legacy: bracedBlock(source, elseStart).body
  }
}

function routerAnnotation(source, path) {
  const match = /@HMRouter\s*\(\s*\{/.exec(source)
  if (!match) {
    throw new Error('missing HMRouter annotation: ' + path)
  }
  return bracedBlock(source, source.indexOf('{', match.index)).body
}

function validateSharedDestination(source) {
  if (!/import\s*\{[^}]*\bNavDestinationHelper\b[^}]*\}\s*from\s*['"]@hadss\/hmrouter['"]/.test(source) ||
    !/private\s+helper\s*:\s*NavDestinationHelper\s*=\s*new\s+NavDestinationHelper\s*\(\s*this\s*\)/.test(source)) {
    throw new Error('shared destination must own one HMRouter NavDestinationHelper')
  }
  if (!/@kit\.UIDesignKit/.test(source) || /\bHdsNavigation\s*\(|\bNavigation\s*\(/.test(source)) {
    throw new Error('shared route host must use one HDS destination without nesting navigation')
  }
  if (/\bHMRouterMgr\b|\.pop\s*\(/.test(source)) {
    throw new Error('shared route title must use the destination system back action exactly once')
  }
  const destinationGate = methodBlock(source, 'usesHdsDestination')
  if (!/return\s+HdsUiCapability\.supportsNativeTheme\s*\(\s*\)/.test(destinationGate) ||
    /ThemeStyle\./.test(destinationGate)) {
    throw new Error('API 26 support must keep one HDS destination stable across Native and Feiniu themes')
  }
  const chromeGate = methodBlock(source, 'usesNativeChrome')
  if (!/ThemeStyle\.Native/.test(chromeGate) ||
    !/HdsUiCapability\.supportsNativeTheme\s*\(\s*\)/.test(chromeGate)) {
    throw new Error('Native chrome must require both Native theme and API 26 capability')
  }
  for (const configurable of [
    /@Prop\s+titleBarVisible\s*:\s*boolean\s*=\s*true/,
    /@Prop\s+backButtonVisible\s*:\s*boolean\s*=\s*true/,
    /@Prop\s+contentExtendsUnderTitleBar\s*:\s*boolean\s*=\s*false/,
    /@Prop\s+heroTitleChrome\s*:\s*boolean\s*=\s*false/,
    /@Prop\s+titleMaterialFollowsSystem\s*:\s*boolean\s*=\s*true/,
    /@Prop\s+menus\s*:\s*Array<NavigationMenuItem>\s*=\s*\[\s*\]/,
    /@Prop\s+scrollControllers\s*:\s*Array<Scroller>\s*=\s*\[\s*\]/
  ]) {
    if (!configurable.test(source)) {
      throw new Error('shared destination must expose title, back, content-inset, Hero chrome, material, menu, and scroll controls')
    }
  }

  for (const builderName of ['hdsContent', 'hdsDestination', 'legacyDestination']) {
    const builderPattern = new RegExp('@Builder\\s+private\\s+' + builderName + '\\s*\\(')
    if (!builderPattern.test(source)) {
      throw new Error('shared destination must keep @Builder on ' + builderName)
    }
  }

  const contentTopInset = methodBlock(source, 'nativeContentTopInset')
  if (!/if\s*\(\s*this\.contentExtendsUnderTitleBar\s*\|\|\s*!\s*this\.titleBarVisible\s*\|\|\s*!\s*this\.usesNativeChrome\s*\(\s*\)\s*\)\s*\{\s*return\s+0/.test(contentTopInset) ||
    !/return\s+Math\.max\s*\(\s*0\s*,\s*this\.appUIState\.safeTop\s*\)\s*\+\s*UIConstants\.ACTION_BAR_HEIGHT/.test(contentTopInset)) {
    throw new Error('shared destination must derive one stable guarded system-title content inset')
  }

  const titleOptions = methodBlock(source, 'nativeTitleBarOptions')
  if (!/avoidLayoutSafeArea\s*:\s*true/.test(titleOptions) ||
    !/enableComponentSafeArea\s*:\s*false/.test(titleOptions)) {
    throw new Error('Native HDS title must own its system safe-area layout')
  }
  const titleContent = propertyBlock(titleOptions, 'content')
  const title = propertyBlock(titleContent, 'title')
  const menu = propertyBlock(titleContent, 'menu')
  const nativeMainTitle = methodBlock(source, 'nativeMainTitle')
  if (!/return\s+this\.rootHomeLibraryPinned\s*\(\s*\)\s*\?\s*['"]{2}\s*:\s*this\.title/.test(
    nativeMainTitle) ||
    !/mainTitle\s*:\s*this\.nativeMainTitle\s*\(\s*\)/.test(title) ||
    !/mainTitleSize\s*:\s*TitleSize\.TITLE_S/.test(title) ||
    !/value\s*:\s*this\.nativeMenus\s*\(\s*\)/.test(menu) ||
    !/maxCount\s*:\s*3/.test(menu)) {
    throw new Error('Native HDS title must keep the shared title and menu contract')
  }

  const titleStyle = propertyBlock(titleOptions, 'style')
  const scrollEffect = propertyBlock(titleStyle, 'scrollEffectOpts')
  if (!/enableScrollEffect\s*:\s*!\s*this\.rootHomeLibraryPinned\s*\(\s*\)/.test(scrollEffect) ||
    !/scrollEffectType\s*:\s*HdsScrollEffectType\.IMMERSIVE_GRADIENT_BLUR/.test(scrollEffect) ||
    !/blurEffectiveStartOffset\s*:\s*LengthMetrics\.vp\s*\(\s*1\s*\)/.test(scrollEffect) ||
    !/blurEffectiveEndOffset\s*:\s*LengthMetrics\.vp\s*\(\s*24\s*\)/.test(scrollEffect)) {
    throw new Error('Native HDS title must reveal immersive gradient blur from 1vp to 24vp')
  }
  const systemMaterial = propertyBlock(titleStyle, 'systemMaterialEffect')
  if (!/materialType\s*:\s*this\.titleMaterialFollowsSystem\s*\?\s*hdsMaterial\.MaterialType\.ADAPTIVE\s*:\s*hdsMaterial\.MaterialType\.IMMERSIVE/.test(
    systemMaterial) ||
    !/materialLevel\s*:\s*this\.titleMaterialFollowsSystem\s*\?\s*hdsMaterial\.MaterialLevel\.ADAPTIVE\s*:\s*hdsMaterial\.MaterialLevel\.GENTLE/.test(
      systemMaterial)) {
    throw new Error('Native HDS title must follow the system except for the explicit Home material')
  }
  const originalStyle = propertyBlock(titleStyle, 'originalStyle')
  const scrolledStyle = propertyBlock(titleStyle, 'scrollEffectStyle')
  const originalBackground = propertyBlock(originalStyle, 'backgroundStyle')
  const scrolledBackground = propertyBlock(scrolledStyle, 'backgroundStyle')
  if (count(titleOptions, /\bbackgroundColor\s*:/g) !== 2 ||
    count(originalBackground, /\bbackgroundColor\s*:/g) !== 1 ||
    !/backgroundColor\s*:\s*Color\.Transparent/.test(originalBackground) ||
    count(scrolledBackground, /\bbackgroundColor\s*:/g) !== 1 ||
    !/backgroundColor\s*:\s*Color\.Transparent/.test(scrolledBackground)) {
    throw new Error('Native HDS title must keep both title backgrounds transparent')
  }
  if (!/maskExtraHeight\s*:\s*0/.test(originalBackground) ||
    !/blurRadius\s*:\s*0/.test(originalBackground) ||
    !/maskExtraHeight\s*:\s*32/.test(scrolledBackground) ||
    !/blurRadius\s*:\s*24/.test(scrolledBackground)) {
    throw new Error('Native HDS title must keep zero initial blur and a 32vp/24vp scrolled blur mask')
  }
  const originalContentStyle = propertyBlock(originalStyle, 'contentStyle')
  const originalTitleStyle = propertyBlock(originalContentStyle, 'titleStyle')
  const originalMenuStyle = propertyBlock(originalContentStyle, 'menuStyle')
  const originalBackIconStyle = propertyBlock(originalContentStyle, 'backIconStyle')
  if (!/mainTitleColor\s*:\s*this\.heroTitleChrome\s*\?\s*Color\.White\s*:\s*\$r\s*\(\s*['"]app\.color\.text_primary['"]\s*\)/.test(
    originalTitleStyle) ||
    !/subTitleColor\s*:\s*this\.heroTitleChrome\s*\?\s*Color\.White\s*:\s*\$r\s*\(\s*['"]app\.color\.text_2['"]\s*\)/.test(
      originalTitleStyle) ||
    !/iconColor\s*:\s*this\.heroTitleChrome\s*\?\s*Color\.White\s*:\s*\$r\s*\(\s*['"]app\.color\.text_primary['"]\s*\)/.test(
      originalMenuStyle) ||
    !/textColor\s*:\s*this\.heroTitleChrome\s*\?\s*Color\.White\s*:\s*\$r\s*\(\s*['"]app\.color\.color_main['"]\s*\)/.test(
      originalMenuStyle) ||
    !/iconColor\s*:\s*this\.heroTitleChrome\s*\?\s*Color\.White\s*:\s*\$r\s*\(\s*['"]app\.color\.text_primary['"]\s*\)/.test(
      originalBackIconStyle)) {
    throw new Error('Native HDS Hero title chrome must be opt-in only in the original style')
  }

  const scrolledContentStyle = propertyBlock(scrolledStyle, 'contentStyle')
  const scrolledTitleStyle = propertyBlock(scrolledContentStyle, 'titleStyle')
  const scrolledMenuStyle = propertyBlock(scrolledContentStyle, 'menuStyle')
  const scrolledBackIconStyle = propertyBlock(scrolledContentStyle, 'backIconStyle')
  if (!/mainTitleColor\s*:\s*\$r\s*\(\s*['"]app\.color\.text_primary['"]\s*\)/.test(scrolledTitleStyle) ||
    !/subTitleColor\s*:\s*\$r\s*\(\s*['"]app\.color\.text_2['"]\s*\)/.test(scrolledTitleStyle) ||
    !/iconColor\s*:\s*\$r\s*\(\s*['"]app\.color\.text_primary['"]\s*\)/.test(scrolledMenuStyle) ||
    !/textColor\s*:\s*\$r\s*\(\s*['"]app\.color\.color_main['"]\s*\)/.test(scrolledMenuStyle) ||
    !/iconColor\s*:\s*\$r\s*\(\s*['"]app\.color\.text_primary['"]\s*\)/.test(scrolledBackIconStyle)) {
    throw new Error('Native HDS title chrome must remain legible in light and dark modes')
  }
  if (!/\bbeforeBack\s*:\s*\(\s*\)\s*=>\s*boolean\s*=\s*\(\s*\)\s*=>\s*false\b/.test(source)) {
    throw new Error('shared destination must keep its custom back hook optional')
  }
  const backHandler = methodBlock(source, 'handleBackPressed')
  if (!/return\s+this\.beforeBack\s*\(\s*\)\s*\|\|\s*this\.helper\.onBackPressed\s*\(\s*\)/.test(backHandler)) {
    throw new Error('shared destination custom back hook must fall through to the HMRouter helper')
  }

  const hdsContent = methodBlock(source, 'hdsContent')
  if (/\bcontentExtendsUnderTitleBar\b/.test(hdsContent) ||
    count(hdsContent, /\bBlank\s*\(/g) !== 1 ||
    count(hdsContent, /Blank\s*\(\s*\)\s*\.height\s*\(\s*this\.nativeContentTopInset\s*\(\s*\)\s*\)/g) !== 1 ||
    count(hdsContent, /\.layoutWeight\s*\(\s*1\s*\)/g) !== 1) {
    throw new Error('shared HDS content must own exactly one guarded title-bar spacer inside one stable host')
  }
  const branches = contentBranches(hdsContent)
  if (count(branches.native, /\bthis\.contentBuilder\s*\(\s*\)/g) !== 1 ||
    /\bthis\.legacyContentBuilder\s*\(|\b(?:NavDestination|HdsNavDestination|Navigation|HdsNavigation)\s*\(/.test(branches.native)) {
    throw new Error('Native HDS content must use only the Native page builder')
  }
  if (count(branches.legacy, /\bthis\.legacyContentBuilder\s*\(\s*\)/g) !== 1 ||
    /\bthis\.contentBuilder\s*\(|\b(?:NavDestination|HdsNavDestination|Navigation|HdsNavigation)\s*\(/.test(branches.legacy)) {
    throw new Error('Feiniu HDS content must use only the legacy page builder')
  }
  if (/\bActionBar\s*\(/.test(branches.native)) {
    throw new Error('Native route content must not construct the custom ActionBar')
  }
  if (/\.(?:attributeModifier|gestureModifier|onWillAppear|onAppear|onWillShow|onShown|onWillHide|onHidden|onWillDisappear|onDisAppear|onReady|onBackPressed|onResult|onActive|onInactive|onNewParam)\s*\(|this\.helper\b/.test(hdsContent)) {
    throw new Error('HDS content branches must not own route lifecycle, gestures, transition, or back events')
  }
  if (/\bBlank\s*\(|\bSafeArea(?:Type|Edge)\b|\bsafeTop\b|\.safeAreaPadding\s*\(|\.expandSafeArea\s*\(|\.padding\s*\(\s*\{[^}]*\btop\s*:/.test(branches.native)) {
    throw new Error('Native route content must not add a second title or safe-area inset')
  }

  const nativeDestination = methodBlock(source, 'hdsDestination')
  let nativeRoot
  try {
    nativeRoot = componentBlock(nativeDestination, 'HdsNavDestination')
  } catch {
    throw new Error('Native route must keep exactly one HdsNavDestination root')
  }
  if (nativeDestination.slice(0, nativeRoot.start).trim() !== '') {
    throw new Error('HdsNavDestination must be the direct Native route root')
  }
  if (count(nativeRoot.body, /\bthis\.hdsContent\s*\(\s*\)/g) !== 1 ||
    /\bthis\.(?:contentBuilder|legacyContentBuilder)\s*\(|\b(?:NavDestination|Navigation|HdsNavigation)\s*\(/.test(nativeRoot.body)) {
    throw new Error('HDS destination must delegate theme content through one stable branch')
  }
  if (/\.(?:attributeModifier|gestureModifier|onWillAppear|onAppear|onWillShow|onShown|onWillHide|onHidden|onWillDisappear|onDisAppear|onReady|onBackPressed|onResult|onActive|onInactive|onNewParam)\s*\(|this\.helper\b/.test(nativeRoot.body)) {
    throw new Error('Native route content must not own route lifecycle, gestures, transition, or back events')
  }
  const nativeTail = nativeDestination.slice(nativeRoot.end + 1)
  if (count(nativeTail, /\.backgroundColor\s*\(/g) !== 1 ||
    !/\.backgroundColor\s*\(\s*AppThemeSurfaceResolver\.destinationCanvasBackground\s*\(\s*this\.appUIState\.themeStyle\s*,\s*HdsUiCapability\.supportsNativeTheme\s*\(\s*\)\s*\)\s*\)/.test(nativeTail)) {
    throw new Error('shared destination must own an opaque transition canvas behind transparent Native content')
  }
  for (const required of [
    /\.titleMode\s*\(\s*HdsNavDestinationTitleMode\.MINI\s*\)/,
    /\.titleBar\s*\(\s*this\.nativeTitleBarOptions\s*\(\s*\)\s*\)/,
    /\.hideTitleBar\s*\(\s*!\s*this\.titleBarVisible\s*\|\|\s*!\s*this\.usesNativeChrome\s*\(\s*\)\s*\|\|\s*this\.rootHomeLibraryPinned\s*\(\s*\)\s*\)/,
    /\.hideBackButton\s*\(\s*!\s*this\.backButtonVisible\s*\)/,
    /\.hideToolBar\s*\(\s*true\s*\)/,
    /\.bindToScrollable\s*\(\s*this\.titleBarVisible\s*&&\s*this\.usesNativeChrome\s*\(\s*\)\s*\?\s*this\.scrollControllers\s*:\s*\[\s*\]\s*\)/,
    /\.onBackPressed\s*\(\s*\(\s*\)\s*=>\s*this\.handleBackPressed\s*\(\s*\)\s*\)/,
    /\.width\s*\(\s*['"]100%['"]\s*\)[\s\S]*?\.height\s*\(\s*['"]100%['"]\s*\)/
  ]) {
    if (!required.test(nativeTail)) {
      throw new Error('Native HDS destination must preserve title, scroll, and HMRouter ownership')
    }
  }
  const safeAreaTypes = methodBlock(source, 'nativeSafeAreaTypes')
  const safeAreaEdges = methodBlock(source, 'nativeSafeAreaEdges')
  if (!/return\s+this\.usesNativeChrome\s*\(\s*\)\s*\?\s*\[\s*LayoutSafeAreaType\.SYSTEM\s*\]\s*:\s*\[\s*\]/.test(safeAreaTypes) ||
    !/return\s+this\.usesNativeChrome\s*\(\s*\)\s*\?\s*\[\s*LayoutSafeAreaEdge\.TOP\s*,\s*LayoutSafeAreaEdge\.BOTTOM\s*\]\s*:\s*\[\s*\]/.test(safeAreaEdges) ||
    count(nativeTail, /\.ignoreLayoutSafeArea\s*\(/g) !== 1 ||
    !/\.ignoreLayoutSafeArea\s*\(\s*this\.nativeSafeAreaTypes\s*\(\s*\)\s*,\s*this\.nativeSafeAreaEdges\s*\(\s*\)\s*\)/.test(nativeTail)) {
    throw new Error('Native chrome must extend through system TOP and BOTTOM without shifting Feiniu content')
  }
  for (const hook of [
    'onWillAppear',
    'onAppear',
    'onWillShow',
    'onShown',
    'onWillHide',
    'onHidden',
    'onWillDisappear',
    'onDisAppear',
    'onReady',
    'onResult',
    'onActive',
    'onInactive'
  ]) {
    if (!new RegExp('\\.' + hook + '\\s*\\(').test(nativeTail) ||
      !new RegExp('this\\.helper\\.' + hook + '\\s*\\(').test(nativeTail)) {
      throw new Error('Native HDS destination is missing generated HMRouter hook: ' + hook)
    }
  }

  const legacyDestination = methodBlock(source, 'legacyDestination')
  let legacyRoot
  try {
    legacyRoot = componentBlock(legacyDestination, 'NavDestination')
  } catch {
    throw new Error('legacy route must keep exactly one standard NavDestination root')
  }
  if (legacyDestination.slice(0, legacyRoot.start).trim() !== '' ||
    count(legacyRoot.body, /\bthis\.legacyContentBuilder\s*\(\s*\)/g) !== 1 ||
    /\bthis\.contentBuilder\s*\(|\b(?:HdsNavDestination|Navigation|HdsNavigation)\s*\(/.test(legacyRoot.body)) {
    throw new Error('legacy content must remain a plain legacy builder under one standard destination')
  }
  const legacyTail = legacyDestination.slice(legacyRoot.end + 1)
  if (count(legacyTail, /\.backgroundColor\s*\(/g) !== 1 ||
    !/\.backgroundColor\s*\(\s*AppThemeSurfaceResolver\.routeBackground\s*\(\s*this\.appUIState\.themeStyle\s*,\s*HdsUiCapability\.supportsNativeTheme\s*\(\s*\)\s*\)\s*\)/.test(legacyTail)) {
    throw new Error('shared destination must preserve the legacy route background')
  }
  for (const required of [
    /\.title\s*\(\s*this\.title\s*,\s*\{\s*barStyle\s*:\s*BarStyle\.STANDARD\s*\}\s*\)/,
    /\.menus\s*\(\s*\[\s*\]\s*\)/,
    /\.hideTitleBar\s*\(\s*true\s*\)/,
    /\.hideBackButton\s*\(\s*!\s*this\.backButtonVisible\s*\)/,
    /\.attributeModifier\s*\(\s*this\.helper\.modifier\s*\)/,
    /\.gestureModifier\s*\(\s*this\.helper\.gestureModifier\s*\)/,
    /\.onBackPressed\s*\(\s*\(\s*\)\s*=>\s*this\.handleBackPressed\s*\(\s*\)\s*\)/,
    /\.width\s*\(\s*['"]100%['"]\s*\)[\s\S]*?\.height\s*\(\s*['"]100%['"]\s*\)/
  ]) {
    if (!required.test(legacyTail)) {
      throw new Error('legacy NavDestination must preserve HMRouter ownership and hidden title behavior')
    }
  }
  if (count(legacyTail, /\.expandSafeArea\s*\(/g) !== 1 ||
    !/\.expandSafeArea\s*\(\s*\[\s*SafeAreaType\.SYSTEM\s*\]\s*,\s*\[\s*SafeAreaEdge\.BOTTOM\s*\]\s*\)/.test(legacyTail)) {
    throw new Error('legacy NavDestination must preserve the system bottom safe area')
  }
  for (const hook of [
    'onWillAppear',
    'onAppear',
    'onWillShow',
    'onShown',
    'onWillHide',
    'onHidden',
    'onWillDisappear',
    'onDisAppear',
    'onReady',
    'onResult',
    'onActive',
    'onInactive',
    'onNewParam'
  ]) {
    if (!new RegExp('\\.' + hook + '\\s*\\(').test(legacyTail) ||
      !new RegExp('this\\.helper\\.' + hook + '\\s*\\(').test(legacyTail)) {
      throw new Error('legacy NavDestination is missing generated HMRouter hook: ' + hook)
    }
  }

  if (count(source, /\.safeAreaPadding\s*\(/g) !== 0 ||
    /\bnativeDestinationTopInset\b|\.padding\s*\(\s*\{[^}]*\btop\s*:/.test(source)) {
    throw new Error('shared route must not pad content away from the HDS title material')
  }
  const build = methodBlock(source, 'build')
  if (!/if\s*\(\s*this\.usesHdsDestination\s*\(\s*\)\s*\)\s*\{\s*this\.hdsDestination\s*\(\s*\)\s*\}\s*else\s*\{\s*this\.legacyDestination\s*\(\s*\)\s*\}/.test(build) ||
    /\b(?:NavDestination|HdsNavDestination|Navigation|HdsNavigation)\s*\(/.test(build)) {
    throw new Error('shared route build must select HDS by API capability, not by the active theme')
  }
}

function validateAboutPublicContent(source) {
  const pageContent = methodBlock(source, 'pageContent')
  const publicSections = [
    {
      group: 'relatedLinksGroup',
      helper: 'relatedLinksContent',
      required: [
        /this\.LinkItem\s*\(\s*['"]华为应用市场['"]\s*,\s*this\.appGalleryUrl\s*,\s*this\.appGalleryUrl(?:\s*,\s*[A-Za-z_$][\w$]*)?\s*\)/,
        /this\.LinkItem\s*\(\s*['"]GitHub['"]\s*,\s*this\.githubUrl\s*,\s*this\.githubUrl(?:\s*,\s*[A-Za-z_$][\w$]*)?\s*\)/
      ]
    },
    {
      group: 'licenseGroup',
      helper: 'licenseContent',
      required: [/GPL-3\.0-or-later/]
    },
    {
      group: 'acknowledgementsGroup',
      helper: 'acknowledgementsContent',
      required: [
        /this\.InfoItem\s*\(\s*['"]Jellyfin['"]\s*,/,
        /this\.InfoItem\s*\(\s*['"]mpv['"]\s*,/,
        /this\.InfoItem\s*\(\s*['"]OpenHarmony['"]\s*,/
      ]
    }
  ]
  if (INTERNAL_ABOUT_COPY.test(source)) {
    throw new Error('About page must not expose internal roadmap, status, or feature-list copy')
  }
  if (!/@State\s+versionName\s*:\s*string\s*=\s*(['"])\1/.test(source) || /v1\.0\.0/.test(source)) {
    throw new Error('About page version must start empty instead of showing a fake fallback')
  }
  if (/\bwebsiteUrl\b|mafei\.hmos\.site|this\.LinkItem\s*\(\s*['"]官网['"]/.test(source)) {
    throw new Error('About page must not expose the retired website link')
  }

  const versionLoader = methodBlock(source, 'getAppVersion')
  if (!/bundleManager\.getBundleInfoForSelf\s*\(/.test(versionLoader)) {
    throw new Error('About page version must come from bundle info')
  }
  const resolvedVersion = /(?:const|let)\s+([A-Za-z_$][\w$]*)\s*=\s*bundleInfo\.versionName\.trim\s*\(\s*\)/
    .exec(versionLoader)
  if (!resolvedVersion) {
    throw new Error('About page version must normalize the real bundle version')
  }
  const resolvedName = resolvedVersion[1]
  const versionGuardPattern = new RegExp(
    'if\\s*\\(\\s*' + resolvedName + '\\.length\\s*>\\s*0\\s*\\)\\s*\\{'
  )
  const versionGuard = versionGuardPattern.exec(versionLoader)
  if (!versionGuard) {
    throw new Error('About page must ignore an empty bundle version')
  }
  const guardedVersionAssignment = bracedBlock(
    versionLoader,
    versionLoader.indexOf('{', versionGuard.index)
  ).body
  const assignmentPattern = new RegExp(
    'this\\.versionName\\s*=\\s*`v\\$\\{' + resolvedName + '\\}`'
  )
  if (!assignmentPattern.test(guardedVersionAssignment) ||
    count(versionLoader, /this\.versionName\s*=/g) !== 1) {
    throw new Error('About page must publish only a non-empty real bundle version')
  }

  const renderGuardPattern = /if\s*\(\s*this\.versionName\.length\s*>\s*0\s*\)\s*\{/
  const renderGuard = renderGuardPattern.exec(pageContent)
  if (!renderGuard) {
    throw new Error('About page must hide the version until bundle info succeeds')
  }
  const guardedVersionText = bracedBlock(
    pageContent,
    pageContent.indexOf('{', renderGuard.index)
  ).body
  if (!/Text\s*\(\s*this\.versionName\s*\)/.test(guardedVersionText) ||
    count(pageContent, /Text\s*\(\s*this\.versionName\s*\)/g) !== 1) {
    throw new Error('About page must render the version only inside its non-empty guard')
  }

  for (const required of [
    /Image\s*\(\s*\$r\(\s*['"]app\.media\.app_icon['"]\s*\)\s*\)/,
    /Text\s*\(\s*\$r\(\s*['"]app\.string\.app_name['"]\s*\)\s*\)/,
    /HarmonyOS\s+平台的\s+Jellyfin\s+第三方客户端/
  ]) {
    if (!required.test(pageContent)) {
      throw new Error('About page must keep only its public identity, links, license, and acknowledgements')
    }
  }

  for (const section of publicSections) {
    const groupCall = new RegExp('this\\.' + section.group + '\\s*\\(', 'g')
    if (count(pageContent, groupCall) !== 1) {
      throw new Error('About page must render each public section exactly once from pageContent')
    }
    const sectionSource = methodBlock(source, section.group) + '\n' +
      methodBlock(source, section.helper)
    for (const required of section.required) {
      if (!required.test(sectionSource)) {
        throw new Error('About page must keep only its public identity, links, license, and acknowledgements')
      }
    }
  }
}

function validateRoutePage(source, path) {
  const annotation = routerAnnotation(source, path)
  if (!/\buseNavDst\s*:\s*true\b/.test(annotation)) {
    throw new Error('route page must opt out of HMRouter NavDestination wrapping: ' + path)
  }
  if (/\b(?:animator|dialog|lifecycle|singleton)\s*:/.test(annotation) ||
    /\b(?:onResult|onNewParam)\s*\(/.test(source)) {
    throw new Error(
      'native route boundary does not cover animator, dialog, singleton, custom lifecycle, result, or new-param contracts: ' +
      path
    )
  }
  if (!/import\s*\{[^}]*\bAppRouteDestination\b[^}]*\}/.test(source) ||
    count(source, /\bAppRouteDestination\s*\(/g) !== 1) {
    throw new Error('route page must use exactly one shared destination: ' + path)
  }
  if (/@kit\.UIDesignKit|\bHdsNavDestination\s*\(/.test(source)) {
    throw new Error('route page must not construct HDS outside the shared destination: ' + path)
  }

  const pageContent = methodBlock(source, 'pageContent')
  const usesSplitActivityPages = path === MANAGEMENT_ACTIVITY &&
    /if\s*\(\s*showLegacyActionBar\s*\)\s*\{[\s\S]*?this\.legacyPage\s*\(\s*\)[\s\S]*?\}\s*else\s*\{[\s\S]*?this\.nativePage\s*\(\s*\)/.test(pageContent)
  const legacyActivityPage = usesSplitActivityPages ? methodBlock(source, 'legacyPage') : ''
  const nativeActivityPage = usesSplitActivityPages ? methodBlock(source, 'nativePage') : ''
  if (path === SEARCH) {
    if (count(pageContent, /\bActionBar\s*\(/g) !== 0 ||
      !/if\s*\(\s*showLegacyActionBar\s*\)\s*\{[\s\S]*?this\.feiniuSearchBar\s*\(\s*\)[\s\S]*?\}\s*else\s*\{[\s\S]*?this\.nativeSearchHeader\s*\(\s*\)/.test(pageContent)) {
      throw new Error('Search Native content must keep its immersive header and isolate the legacy cancel row')
    }
    const nativeSearchForm = methodBlock(source, 'nativeSearchForm')
    const nativeSearchHeader = methodBlock(source, 'nativeSearchHeader')
    const legacySearchBar = methodBlock(source, 'feiniuSearchBar')
    if (!/\bSearch\s*\(/.test(nativeSearchForm) ||
      !/this\.nativeSearchForm\s*\(\s*\)/.test(nativeSearchHeader) ||
      !/this\.searchBarContent\s*\(\s*false\s*,\s*true\s*\)/.test(legacySearchBar) ||
      !/titleBarVisible\s*:\s*false/.test(source) ||
      !/contentExtendsUnderTitleBar\s*:\s*true/.test(source)) {
      throw new Error('Search Native content must keep its immersive header and isolate the legacy cancel row')
    }
  } else if (!/if\s*\(\s*showLegacyActionBar\s*\)/.test(pageContent) ||
    (usesSplitActivityPages ?
      count(legacyActivityPage, /\bActionBar\s*\(/g) !== 1 || count(nativeActivityPage, /\bActionBar\s*\(/g) !== 0 :
      count(pageContent, /\bActionBar\s*\(/g) !== 1)) {
    throw new Error('legacy ActionBar must remain isolated behind the legacy flag: ' + path)
  }
  const legacyBackground = LEGACY_ROUTE_BACKGROUNDS.get(path)
  const fillsTransparentNativeRoot = usesSplitActivityPages ?
    /\}\s*\.width\s*\(\s*['"]100%['"]\s*\)\s*\.height\s*\(\s*['"]100%['"]\s*\)\s*\.backgroundColor\s*\(\s*Color\.Transparent\s*\)/.test(pageContent) &&
      /\}\s*\.width\s*\(\s*['"]100%['"]\s*\)\s*\.height\s*\(\s*['"]100%['"]\s*\)[\s\S]*?\.backgroundColor\s*\(\s*AppThemeSurfaceResolver\.routeBackground\s*\(/.test(legacyActivityPage) &&
      !/\.backgroundColor\s*\(/.test(nativeActivityPage) :
    path === SEARCH ?
    /if\s*\(\s*showLegacyActionBar\s*\)\s*\{[\s\S]*?\.width\s*\(\s*['"]100%['"]\s*\)\s*\.height\s*\(\s*['"]100%['"]\s*\)\s*\.backgroundColor\s*\(\s*\$r\(\s*['"]app\.color\.bg_main['"]\s*\)\s*\)[\s\S]*?\}\s*else\s*\{[\s\S]*?\.width\s*\(\s*['"]100%['"]\s*\)\s*\.height\s*\(\s*['"]100%['"]\s*\)\s*\.backgroundColor\s*\(\s*Color\.Transparent\s*\)/.test(pageContent) :
    RESOLVER_BACKGROUND_PAGES.has(path) ?
    /\}\s*\.width\s*\(\s*['"]100%['"]\s*\)\s*\.height\s*\(\s*['"]100%['"]\s*\)\s*\.backgroundColor\s*\(\s*showLegacyActionBar\s*\?\s*AppThemeSurfaceResolver\.routeBackground\s*\([\s\S]*?\)\s*:\s*Color\.Transparent\s*\)/.test(pageContent) :
    Boolean(legacyBackground && new RegExp(
      '\\}\\s*\\.width\\s*\\(\\s*[\'\"]100%[\'\"]\\s*\\)\\s*\\.height\\s*\\(\\s*[\'\"]100%[\'\"]\\s*\\)\\s*' +
      '(?:\\.justifyContent\\s*\\([^)]*\\)\\s*)?' +
      '\\.backgroundColor\\s*\\(\\s*showLegacyActionBar\\s*\\?\\s*\\$r\\(\\s*[\'\"]app\\.color\\.' +
      legacyBackground + '[\'\"]\\s*\\)\\s*:\\s*Color\\.Transparent\\s*\\)'
    ).test(pageContent))
  if (!fillsTransparentNativeRoot) {
    throw new Error('route root must fill its host, be transparent in Native, and preserve its legacy background: ' + path)
  }

  const build = methodBlock(source, 'build')
  if (!/AppRouteDestination\s*\(\s*\{[\s\S]*?\btitle\s*:/.test(build)) {
    throw new Error('route page must provide its title to the shared destination: ' + path)
  }
  if (!/contentBuilder\s*:[\s\S]*this\.pageContent\s*\(\s*false\s*\)/.test(build) ||
    !/legacyContentBuilder\s*:[\s\S]*this\.pageContent\s*\(\s*true\s*\)/.test(build)) {
    throw new Error('route page must separate native and legacy title ownership: ' + path)
  }
  if (EMBEDDED_ROUTE_PAGES.has(path)) {
    if (!/if\s*\(\s*this\.fromHome\s*\)\s*\{[\s\S]*?this\.embeddedContent\s*\(\s*\)[\s\S]*?\}\s*else\s*\{[\s\S]*?AppRouteDestination\s*\(/.test(build)) {
      throw new Error('embedded home content must bypass the route destination: ' + path)
    }
    const embeddedContent = methodBlock(source, 'embeddedContent')
    if (!/\.width\s*\(\s*['"]100%['"]\s*\)[\s\S]*?\.height\s*\(\s*['"]100%['"]\s*\)/.test(embeddedContent) ||
      !/\.backgroundColor\s*\(\s*this\.pageBackground\s*\(\s*\)\s*\)/.test(embeddedContent)) {
      throw new Error('embedded home content must keep its full-size theme-owned canvas: ' + path)
    }
  }
  if (path === MANAGEMENT_USER_DETAIL) {
    if (!/if\s*\(\s*this\.embedded\s*\)\s*\{[\s\S]*?this\.pageContent\s*\(\s*false\s*\)[\s\S]*?\}\s*else\s*\{[\s\S]*?AppRouteDestination\s*\(/.test(build)) {
      throw new Error('embedded management detail must bypass the route destination')
    }
    if (!/beforeBack\s*:\s*\(\s*\)\s*=>\s*this\.handleRouteBack\s*\(\s*\)/.test(build)) {
      throw new Error('management detail must intercept the system back action before leaving')
    }
    const routeBack = methodBlock(source, 'handleRouteBack')
    if (!/this\.attemptLeave\s*\(\s*\(\s*\)\s*=>\s*HMRouterMgr\.pop\s*\(\s*\)\s*\)/.test(routeBack) ||
      !/return\s+true\b/.test(routeBack)) {
      throw new Error('management detail must consume system back until attemptLeave completes')
    }
  }
}

function validateEntryPoints(mineSource, settingSource) {
  if (!/HMRouterMgr\.to\s*\(\s*RouterConsts\.SettingPage\s*\)\.push\s*\(\s*\)/.test(mineSource)) {
    throw new Error('Mine settings entry must keep HMRouter push behavior')
  }
  if (!/HMRouterMgr\.to\s*\(\s*RouterConsts\.PlayerEnginePage\s*\)\.push\s*\(\s*\)/.test(settingSource)) {
    throw new Error('player engine entry must keep HMRouter push behavior')
  }
  if (/RouterConsts\.AboutPage/.test(mineSource) || /RouterConsts\.AboutPage/.test(settingSource)) {
    throw new Error('retired About entry must stay removed from Mine and Settings')
  }
}

export function validateNativeRouteDestinations(sources) {
  validateSharedDestination(requiredSource(sources, DESTINATION))
  ROUTE_PAGES.forEach((path) => validateRoutePage(requiredSource(sources, path), path))
  validateAboutPublicContent(requiredSource(sources, ABOUT))
  validateEntryPoints(requiredSource(sources, MINE_TAB), requiredSource(sources, SETTING_PAGE))
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

function main() {
  const root = process.argv[2] ? resolve(process.argv[2]) : defaultWorkspaceRoot()
  const paths = [DESTINATION, ...ROUTE_PAGES, MINE_TAB]
  const sources = new Map(paths.map((path) => [path, readFileSync(resolve(root, path), 'utf8')]))
  validateNativeRouteDestinations(sources)
  process.stdout.write('Native route destinations verified\n')
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main()
}
