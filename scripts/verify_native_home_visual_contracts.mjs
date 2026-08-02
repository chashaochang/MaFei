import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const SURFACE_RESOLVER = 'entry/src/main/ets/theme/AppThemeSurfaceResolver.ets'
const HOME_TAB = 'entry/src/main/ets/features/home/hometab/HomeTab.ets'
const HOME_LATEST_SECTION =
  'entry/src/main/ets/features/home/hometab/HomeLatestMediaSection.ets'
const HOME_CHIP_SELECTOR =
  'entry/src/main/ets/features/home/hometab/HomeLibraryChipSelector.ets'
const HOME_VIEW_MODEL = 'entry/src/main/ets/features/home/hometab/HomeViewModel.ets'
const APP_ROUTE_DESTINATION = 'entry/src/main/ets/component/AppRouteDestination.ets'
const APP_UI_STATE = 'entry/src/main/ets/entity/AppUIState.ets'
const EVENTS = 'entry/src/main/ets/events/Events.ets'
const INDEX_PAGE = 'entry/src/main/ets/features/splash/IndexPage.ets'

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
    '\\b(?:private\\s+|static\\s+|private\\s+static\\s+)?' + methodName +
      '\\s*\\([^)]*\\)\\s*(?::\\s*[^\\{]+)?\\s*\\{'
  )
  const match = signature.exec(source)
  if (!match) {
    throw new Error('missing method: ' + methodName)
  }
  return bracedBlock(source, source.indexOf('{', match.index))
}

function branchBlock(source, marker) {
  const markerIndex = source.indexOf(marker)
  if (markerIndex < 0) {
    throw new Error('missing branch: ' + marker)
  }
  return bracedBlock(source, source.indexOf('{', markerIndex))
}

function countMatches(source, pattern) {
  return [...source.matchAll(pattern)].length
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function validateNativeHomeVisualContracts(sources) {
  const resolver = requiredSource(sources, SURFACE_RESOLVER)
  const homeTab = requiredSource(sources, HOME_TAB)
  const latestSection = requiredSource(sources, HOME_LATEST_SECTION)
  const chipSelector = requiredSource(sources, HOME_CHIP_SELECTOR)
  const homeViewModel = requiredSource(sources, HOME_VIEW_MODEL)
  const routeDestination = requiredSource(sources, APP_ROUTE_DESTINATION)
  const appUIState = requiredSource(sources, APP_UI_STATE)
  const events = requiredSource(sources, EVENTS)
  const indexPage = requiredSource(sources, INDEX_PAGE)
  const canvas = methodBlock(resolver, 'appCanvasBackground')
  const material = methodBlock(resolver, 'material')

  if (!/effectiveTheme\s*===\s*ThemeStyle\.Native[\s\S]*app\.color\.native_canvas_background/.test(canvas)) {
    throw new Error('Native app canvas must use the dedicated adaptive background')
  }
  if (!/app\.color\.bg_main/.test(canvas)) {
    throw new Error('Feiniu app canvas must keep bg_main')
  }
  if (!/AppThemeMaterialRole\.ContentGroup[\s\S]*materialColor\s*:\s*\$r\('sys\.color\.comp_background_primary'\)/
    .test(material)) {
    throw new Error('Native ContentGroup must use the system primary card color')
  }

  const normalChipMaterial = branchBlock(material,
    'role === AppThemeMaterialRole.HomeLibraryChip')
  const selectedChipMaterial = branchBlock(material,
    'role === AppThemeMaterialRole.HomeLibraryChipSelected')
  if (!/ImmersiveMaterial\s*\(\s*\{\s*interactive\s*:\s*true\s*\}\s*\)/.test(
    normalChipMaterial) || /materialColor|colorInvert|applyShadow|ImmersiveStyle/.test(normalChipMaterial) ||
    !/ImmersiveMaterial\s*\(\s*\{\s*interactive\s*:\s*true\s*\}\s*\)/.test(
      selectedChipMaterial) || /materialColor|colorInvert|applyShadow|ImmersiveStyle/.test(selectedChipMaterial)) {
    throw new Error('Home library Chips must follow the default system immersive material')
  }

  if (/buildLibraryBrowseSection|mediaBrowseItem\s*\(/.test(homeTab)) {
    throw new Error('Home must not keep the redundant media browse shortcut section')
  }
  if (/ForEach\s*\(\s*this\.ui\.latestMediaList/.test(homeTab)) {
    throw new Error('HomeTab must not render one recently-added section per library')
  }

  const duplicateBridge = [
    'HomeLatestMediaSelectionEvent',
    'rootNavigationHomeLibraryIds',
    'rootNavigationHomeLibraryLabels',
    'rootNavigationHomeLibrarySelectedId',
    'nativeTitleStackBuilder'
  ]
  for (const marker of duplicateBridge) {
    if (homeTab.includes(marker) || routeDestination.includes(marker) ||
      appUIState.includes(marker) || events.includes(marker)) {
      throw new Error('Home library sticky header must not use a duplicate-instance bridge: ' + marker)
    }
  }
  if (/HomeLibraryChipSelector/.test(routeDestination) || /\bstackBuilder\s*:/.test(routeDestination)) {
    throw new Error('AppRouteDestination must never create a second home library Chip selector')
  }
  if (/HomeLibraryChipSelector|chipStrip|nativeTabStrip|standardTabStrip/.test(latestSection)) {
    throw new Error('HomeLatestMediaSection must render only the selected media grid')
  }
  if (countMatches(homeTab, /HomeLibraryChipSelector\s*\(/g) !== 1) {
    throw new Error('HomeTab must create exactly one home library Chip selector')
  }

  const tabs = methodBlock(homeTab, 'latestMediaTabs')
  if (!/HOME_RECENT_MEDIA_ID[\s\S]*label\s*:\s*['"]最近添加['"]/.test(tabs) ||
    !/media\.id[\s\S]*media\.name/.test(tabs)) {
    throw new Error('Home feed Chips must start with recent and then use real library names')
  }

  const homeRefresh = methodBlock(homeTab, 'homeRefreshContent')
  const homeList = methodBlock(homeTab, 'homeContentList')
  if (!/useNativeSurface\s*\(\s*\)[\s\S]*homeContentList\s*\(\s*true\s*\)/.test(homeRefresh) ||
    !/else\s*\{[\s\S]*Refresh\s*\([\s\S]*builder\s*:\s*this\.customRefreshComponent\s*\(\s*\)[\s\S]*homeContentList\s*\(\s*false\s*\)/.test(
      homeRefresh)) {
    throw new Error('Native Home must bypass Refresh while Feiniu keeps its existing refresh path')
  }
  if (!/ListItemGroup\s*\(\s*\{\s*header\s*:\s*this\.latestMediaStickyHeader\s*\}/.test(
    homeList) || !/\.sticky\s*\([\s\S]*StickyStyle\.Header/.test(homeList)) {
    throw new Error('Home library Chips must use one real ListItemGroup sticky header')
  }
  if (!/HomeLatestMediaSection\s*\(\s*\{[\s\S]*latestMap\s*:\s*this\.ui\.latestMap[\s\S]*selectedMediaId\s*:\s*this\.ui\.selectedLatestMediaId[\s\S]*loadingMore\s*:\s*this\.ui\.latestMediaLoadingMore/.test(
    homeList)) {
    throw new Error('HomeTab must compose one state-backed latest-media grid')
  }
  if (!/\.onReachEnd\s*\([\s\S]*this\.vm\.loadMoreLatestMedia\s*\(\s*\)/.test(homeList)) {
    throw new Error('Home list must page the selected media feed at the bottom')
  }
  if (!/if\s*\(\s*this\.vm\.appUIState\.currentBreakpoint\.includes\s*\(\s*['"]s['"]\s*\)\s*&&\s*\(\s*!\s*this\.useNativeSurface\s*\(\s*\)\s*\|\|\s*!\s*this\.showNativeHero\s*\(\s*\)\s*\)\s*\)\s*\{\s*ListItem\s*\(\s*\)\.height\s*\(\s*this\.phoneContentTopInset\s*\(\s*\)\s*\)/.test(
    homeList)) {
    throw new Error('HomeTab must own the Native empty-Hero top inset inside the stable root content branch')
  }

  const stickyHeader = methodBlock(homeTab, 'latestMediaStickyHeader')
  if (!/HomeLibraryChipSelector\s*\(/.test(stickyHeader) ||
    !/fadeRightEdge\s*:\s*this\.latestChipStripMenuReserved/.test(stickyHeader) ||
    !/rightContentPadding\s*:\s*this\.latestChipStripMenuReserved\s*\?[\s\S]*HOME_LIBRARY_HEADER_EXTRA_MENU_GAP\s*:\s*0/.test(
      stickyHeader) ||
    !/right\s*:\s*this\.latestMediaHeaderMenuInset\s*\(\s*\)/.test(stickyHeader) ||
    !/backgroundColor\s*\(\s*Color\.Transparent\s*\)/.test(stickyHeader) ||
    !/zIndex\s*\(\s*this\.latestChipStripPinned\s*\?\s*10\s*:\s*0\s*\)/.test(stickyHeader) ||
    !/hitTestBehavior\s*\(\s*HitTestMode\.Transparent\s*\)/.test(stickyHeader)) {
    throw new Error('The single sticky header must preserve a transparent lane beside the title-bar menus')
  }
  if (/backgroundSystemMaterial|backgroundBlurStyle|backdropBlur/.test(stickyHeader)) {
    throw new Error('The whole Chip header row must not receive background material or blur')
  }
  const menuInset = methodBlock(homeTab, 'latestMediaHeaderMenuInset')
  if (!/!this\.latestChipStripMenuReserved[\s\S]*return\s+0/.test(menuInset) ||
    !/HOME_LIBRARY_LIVE_MENU_WIDTH/.test(menuInset) ||
    !/HOME_LIBRARY_SEARCH_MENU_WIDTH/.test(menuInset) ||
    /HOME_LIBRARY_HEADER_EXTRA_MENU_GAP/.test(menuInset)) {
    throw new Error('The header must reserve only the menu width; visual spacing belongs inside the Chip list')
  }
  const topPadding = methodBlock(homeTab, 'latestMediaHeaderTopPadding')
  const chipAlignment = methodBlock(homeTab, 'latestMediaChipAlignmentOffset')
  if (!/return\s+this\.latestMediaHeaderTopInset\s*\(\s*\)/.test(topPadding) ||
    /latestChipStripPinned|HOME_LIBRARY_HEADER_ALIGNMENT_OFFSET/.test(topPadding) ||
    !/latestChipStripPinned\s*\?\s*HOME_LIBRARY_HEADER_ALIGNMENT_OFFSET\s*:\s*0/.test(chipAlignment) ||
    !/\.translate\s*\(\s*\{\s*y\s*:\s*this\.latestMediaChipAlignmentOffset\s*\(\s*\)\s*\}\s*\)/.test(
      stickyHeader)) {
    throw new Error('Only sticky Chips may receive the vertical alignment offset; search must remain fixed')
  }
  const pinState = methodBlock(homeTab, 'updateLatestMediaHeaderPinned')
  if (!/area\.globalPosition\.y\s+as\s+number/.test(pinState) ||
    !/latestChipStripMenuReserved\s*=\s*globalY\s*<=\s*UIConstants\.ACTION_BAR_HEIGHT/.test(
      pinState) ||
    !/globalY\s*<=\s*0/.test(pinState) ||
    !/updateLatestChipStripPinned/.test(pinState)) {
    throw new Error('The single header must publish its real sticky state')
  }

  const chipOptions = methodBlock(chipSelector, 'chipOptions')
  if (!/backgroundSystemMaterial\s*:\s*AppThemeSurfaceResolver\.material\s*\(\s*AppThemeMaterialRole\.HomeLibraryChip\s*\)/.test(
    chipOptions) ||
    !/activatedBackgroundSystemMaterial\s*:\s*AppThemeSurfaceResolver\.material\s*\(\s*AppThemeMaterialRole\.HomeLibraryChipSelected\s*\)/.test(
      chipOptions) ||
    !/activatedBackgroundColor\s*:\s*ColorMetrics\.rgba\(255,\s*255,\s*255,\s*0\.82\)/.test(
      chipOptions) ||
    !/activatedFontColor\s*:\s*ColorMetrics\.numeric\(0x000000\)/.test(chipOptions) ||
    !/allowClose\s*:\s*false/.test(chipOptions)) {
    throw new Error('Every Chip must use immersive material with translucent-white and black selected styling')
  }
  const chipBuild = methodBlock(chipSelector, 'build')
  if (!/List\s*\([\s\S]*scroller\s*:\s*this\.chipScroller/.test(chipBuild) ||
    !/ChipV2\s*\(/.test(chipBuild) ||
    !/padding\s*\(\s*\{\s*right\s*:\s*this\.rightContentPadding\s*\}\s*\)/.test(chipBuild) ||
    !/listDirection\s*\(\s*Axis\.Horizontal\s*\)/.test(chipBuild) ||
    !/fadingEdge\s*\(\s*this\.fadeRightEdge[\s\S]*fadingEdgeLength\s*:\s*LengthMetrics\.vp\(32\)/.test(
      chipBuild)) {
    throw new Error('Home library selector must be one horizontal ChipV2 group')
  }
  const revealSelected = methodBlock(chipSelector, 'revealSelected')
  if (!/chipScroller\.scrollToIndex\s*\(\s*index\s*,\s*smooth\s*,\s*ScrollAlign\.CENTER\s*\)/.test(
    revealSelected)) {
    throw new Error('Selected home library Chip must move into the visible horizontal area')
  }

  const selectLatestMedia = methodBlock(homeTab, 'selectLatestMedia')
  if (!/this\.vm\.selectLatestMedia\s*\(\s*id\s*\)/.test(selectLatestMedia) ||
    /contentScroller|scrollTo|scrollBy/.test(selectLatestMedia)) {
    throw new Error('Home Chip selection must update only the grid without moving the page')
  }
  const feedMinHeight = methodBlock(homeTab, 'latestFeedContentMinHeight')
  if (!/deviceHeight\s*-\s*this\.phoneContentTopInset\s*\(\s*\)\s*-\s*this\.scrollTail\s*\(\s*\)/.test(
    feedMinHeight) || /scrollTail\s*\(\s*\)\s*-\s*UIConstants\.ACTION_BAR_HEIGHT/.test(feedMinHeight)) {
    throw new Error('Sparse library feeds must reserve the viewport without subtracting the title bar twice')
  }

  const titleBarOptions = methodBlock(routeDestination, 'nativeTitleBarOptions')
  if (!/enableScrollEffect\s*:\s*!this\.appUIState\.rootNavigationHomeLibraryPinned/.test(
    titleBarOptions) ||
    /hdsMaterial\.MaterialType\.NONE/.test(titleBarOptions) ||
    !/titleMaterialFollowsSystem\s*\?[\s\S]*hdsMaterial\.MaterialType\.ADAPTIVE/.test(
      titleBarOptions)) {
    throw new Error('The HDS title bar must stop blur without disabling menu material')
  }
  const hdsDestination = methodBlock(routeDestination, 'hdsDestination')
  if (!/hideTitleBar\s*\([\s\S]*rootNavigationHomeLibraryPinned/.test(hdsDestination)) {
    throw new Error('The pinned header must remove the HDS title-bar touch interception layer')
  }
  if (!/if\s*\(\s*this\.latestChipStripPinned\s*\)[\s\S]*Button\s*\(\s*\{\s*type\s*:\s*ButtonType\.Circle/.test(
    stickyHeader)) {
    throw new Error('The interactive sticky header must own its search action')
  }
  if (!/Button\s*\(\s*\{\s*type\s*:\s*ButtonType\.Circle\s*,\s*stateEffect\s*:\s*false\s*\}\s*\)/.test(
    stickyHeader) ||
    !/SymbolGlyph\s*\(\s*\$r\(['"]sys\.symbol\.magnifyingglass['"]\)\s*\)/.test(stickyHeader) ||
    !/\.systemMaterial\s*\(\s*AppThemeSurfaceResolver\.material\s*\(\s*AppThemeMaterialRole\.HomeLibraryChip\s*\)\s*\)/.test(
      stickyHeader) ||
    !/RouterConsts\.SearchPage/.test(stickyHeader) ||
    /stickySearchOptions|ChipV2PrefixSymbolIcon/.test(homeTab)) {
    throw new Error('The sticky search action must be a sharp circular material button, not an empty ChipV2')
  }
  if (!/icon\s*:\s*\$r\(['"]sys\.symbol\.magnifyingglass['"]\)/.test(indexPage) ||
    /icon\s*:\s*\$r\(['"]app\.media\.ic_search['"]\)/.test(indexPage) ||
    !/titleMaterialFollowsSystem\s*:\s*true/.test(indexPage)) {
    throw new Error('The HDS search action must use the sharp system vector symbol')
  }
  const nativeMenus = methodBlock(routeDestination, 'nativeMenus')
  if (!/icon\s*:\s*item\.icon/.test(nativeMenus) ||
    !/isEnabled\s*:\s*item\.isEnabled\s*\?\?\s*true/.test(nativeMenus)) {
    throw new Error('HDS navigation menu actions must remain explicitly enabled')
  }

  const posterGrid = methodBlock(latestSection, 'posterGrid')
  if (!/Grid\s*\(/.test(posterGrid) ||
    !/\.columnsTemplate\s*\(\s*this\.gridColumns\s*\(\s*\)\s*\)/.test(posterGrid) ||
    /List\s*\(|listDirection\s*\(\s*Axis\.Horizontal/.test(posterGrid)) {
    throw new Error('Latest-media content must be one vertically expanding poster grid')
  }
  const feedContent = methodBlock(latestSection, 'feedContent')
  if (!/constraintSize\s*\(\s*\{\s*minHeight\s*:\s*this\.contentMinHeight\s*\}\s*\)/.test(
    feedContent)) {
    throw new Error('Latest-media feed must reserve stable height while switching libraries')
  }

  const loadLatestPage = methodBlock(homeViewModel, 'loadLatestMediaPage')
  if (!/id\s*===\s*HOME_RECENT_MEDIA_ID[\s\S]*getLatestMedia\s*\([\s\S]*groupItems\s*:\s*true/.test(
    loadLatestPage)) {
    throw new Error('Recent media must use Jellyfin grouped latest-media semantics')
  }
  const mapFeedItem = methodBlock(homeViewModel, 'mapHomeFeedItem')
  if (!/BaseItemKind\.Episode\s*&&\s*seriesId\.length\s*>\s*0[\s\S]*id\s*:\s*seriesId[\s\S]*type\s*:\s*BaseItemKind\.Series/.test(
    mapFeedItem)) {
    throw new Error('Recent episodes must collapse into one series card')
  }

  const segmentProgress = methodBlock(homeTab, 'heroSegmentProgress')
  if (!/return\s+index\s*===\s*this\.heroIndex\s*\?\s*this\.heroProgress\s*:\s*0/.test(segmentProgress) ||
    /index\s*<\s*this\.heroIndex/.test(segmentProgress)) {
    throw new Error('Native Hero progress must animate only the current segment')
  }
  const progressTrackColor = methodBlock(homeTab, 'heroProgressTrackColor')
  const progress = methodBlock(homeTab, 'nativeHomeHeroProgressIndicator')
  if (!/rgba\(255,255,255,0\.20\)/.test(progressTrackColor) ||
    !/rgba\(0,0,0,0\.12\)/.test(progressTrackColor) ||
    !/\.color\s*\(\s*\$r\s*\(\s*['"]sys\.color\.icon_primary['"]\s*\)\s*\)/.test(progress) ||
    !/\.backgroundColor\s*\(\s*this\.heroProgressTrackColor\s*\(\s*\)\s*\)/.test(progress)) {
    throw new Error('Native Hero progress must keep strong active-to-track contrast in both color modes')
  }
  const readabilityScrim = methodBlock(homeTab, 'nativeHomeHeroReadabilityScrim')
  if (!/rgba\(0,0,0,0\.08\)/.test(readabilityScrim) ||
    !/rgba\(0,0,0,0\.88\)/.test(readabilityScrim)) {
    throw new Error('Native Hero readability scrim must remain a dedicated dark layer')
  }
  const canvasTransition = methodBlock(homeTab, 'nativeHomeHeroCanvasTransition')
  if (!/\.height\s*\(\s*96\s*\)/.test(canvasTransition) ||
    !/app\.color\.native_canvas_background/.test(canvasTransition)) {
    throw new Error('Native Hero canvas transition must end in the adaptive canvas color')
  }
  const heroStretch = methodBlock(homeTab, 'stretchNativeHero')
  const heroSpringBack = methodBlock(homeTab, 'springBackNativeHero')
  if (!/contentScroller\.currentOffset\s*\(\s*\)\.yOffset\s*>\s*0/.test(heroStretch) ||
    !/state\s*===\s*ScrollState\.Scroll[\s\S]*nativeHeroHeight\s*\+=\s*-\(offset\s*\/\s*HOME_HERO_STRETCH_FACTOR\)/.test(
      heroStretch) ||
    !/ScrollState\.Fling[\s\S]*springBackNativeHero\s*\(\s*\)/.test(heroStretch) ||
    !/interpolatingSpring|nativeHeroSpringCurve/.test(heroSpringBack) ||
    !/nativeHeroHeight\s*=\s*HOME_HERO_BASE_HEIGHT/.test(heroSpringBack) ||
    !/HOME_HERO_BASE_HEIGHT\s*\+\s*HOME_HERO_REFRESH_THRESHOLD/.test(heroSpringBack) ||
    !/ui\.isRefreshing\s*=\s*true[\s\S]*vm\.init\s*\(\s*\)/.test(heroSpringBack)) {
    throw new Error('Native Hero must stretch, trigger refresh at threshold, and spring back')
  }
  const heroCarousel = methodBlock(homeTab, 'nativeHomeHeroCarousel')
  const heroRefreshIndicator = methodBlock(homeTab, 'nativeHomeHeroRefreshIndicator')
  if (!/\.height\s*\(\s*['"]100%['"]\s*\)/.test(heroCarousel) ||
    !/nativeHeroRefreshProgress[\s\S]*nativeHomeHeroRefreshIndicator/.test(heroCarousel) ||
    !/ProgressType\.Ring/.test(heroRefreshIndicator) ||
    !/ProgressStatus\.LOADING/.test(heroRefreshIndicator) ||
    !/ListItem\s*\(\s*\)\s*\{[\s\S]*this\.nativeHomeHeroCarousel\s*\(\s*\)[\s\S]*\.height\s*\(\s*this\.nativeHeroHeight\s*\)/.test(
      homeList) ||
    !/\.clip\s*\(\s*false\s*\)/.test(homeList) ||
    !/\.edgeEffect\s*\(\s*EdgeEffect\.None\s*\)/.test(homeList) ||
    !/\.onScrollFrameBegin\s*\([\s\S]*stretchNativeHero[\s\S]*offsetRemain\s*:\s*0/.test(homeList)) {
    throw new Error('Native Hero height must be driven directly by the unclipped List scroll frame')
  }
  const hero = methodBlock(homeTab, 'nativeHomeHero')
  if (hero.indexOf('this.nativeHomeHeroReadabilityScrim()') < 0 ||
    hero.indexOf('this.nativeHomeHeroCanvasTransition()') < 0 ||
    !/\.height\s*\(\s*['"]100%['"]\s*\)/.test(hero)) {
    throw new Error('Native Hero must keep separate readability and canvas transition layers')
  }
}

export function validateWorkspace(root = defaultWorkspaceRoot()) {
  const paths = [
    SURFACE_RESOLVER,
    HOME_TAB,
    HOME_LATEST_SECTION,
    HOME_CHIP_SELECTOR,
    HOME_VIEW_MODEL,
    APP_ROUTE_DESTINATION,
    APP_UI_STATE,
    EVENTS,
    INDEX_PAGE
  ]
  const sources = new Map(paths.map((path) => [path, readFileSync(resolve(root, path), 'utf8')]))
  validateNativeHomeVisualContracts(sources)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspace()
  console.log('Native home visual contracts passed')
}
