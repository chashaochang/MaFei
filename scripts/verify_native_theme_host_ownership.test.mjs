import assert from 'node:assert/strict'
import { dirname, resolve } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  defaultWorkspaceRoot,
  validateNativeThemeHostOwnership
} from './verify_native_theme_host_ownership.mjs'

const appStatePath = 'entry/src/main/ets/entity/AppUIState.ets'
const indexPath = 'entry/src/main/ets/pages/Index.ets'
const sharedDestinationPath = 'entry/src/main/ets/component/AppRouteDestination.ets'
const rootPagePath = 'entry/src/main/ets/features/splash/IndexPage.ets'
const homeScreenPath = 'entry/src/main/ets/features/home/HomeScreen.ets'
const chromeTabPath = 'entry/src/main/ets/features/home/phone/HomePhoneChromeTab.ets'
const standardPath = 'entry/src/main/ets/features/home/phone/HomePhoneStandardTabs.ets'
const nativePath = 'entry/src/main/ets/features/home/phone/HomePhoneNativeTabs.ets'
const pointLightPath = 'entry/src/main/ets/theme/FeiniuPointLightModifier.ets'
const homeTabPath = 'entry/src/main/ets/features/home/hometab/HomeTab.ets'
const homeViewModelPath = 'entry/src/main/ets/features/home/hometab/HomeViewModel.ets'
const chasingTabPath = 'entry/src/main/ets/features/home/chasing/ChasingTab.ets'
const favoriteTabPath = 'entry/src/main/ets/features/favorite/FavoriteListPage.ets'
const mediaTabPath = 'entry/src/main/ets/features/home/mediatab/MediaTab.ets'
const mineTabPath = 'entry/src/main/ets/features/home/minetab/MineTab.ets'

function validHomeScreen() {
  return [
    "@Monitor('appUIState.currentBreakpoint')",
    'onBreakpointChange(): void {',
    '  this.updateFeiniuPointLightEnabled()',
    "  this.appUIState.isBigScreen = !this.appUIState.currentBreakpoint.includes('s')",
    "  if (this.appUIState.currentBreakpoint.includes('l')) {",
    '    this.ui.isMenuModalVisible = false',
    '    this.ui.isLeftSidebarVisible = true',
    '  }',
    '  this.contentTabsController.changeIndex(this.contentSelectedIndex())',
    '}',
    'private rootNavigationTitle(): string {',
    "  if (this.ui.selectedDestination === HomeDestination.Chasing) { return '追剧' }",
    "  if (this.ui.selectedDestination === HomeDestination.Favorite) { return '收藏' }",
    "  if (this.ui.selectedDestination === HomeDestination.Media) { return '媒体库' }",
    "  if (this.ui.selectedDestination === HomeDestination.Mine) { return '我的' }",
    "  return '首页'",
    '}',
    'private publishRootNavigationChrome(visible: boolean, title: string, homeActionsVisible: boolean): void {',
    '  this.appUIState.rootNavigationTitleBarVisible = visible',
    '  this.appUIState.rootNavigationTitle = title',
    '  this.appUIState.rootNavigationHomeActionsVisible = homeActionsVisible',
    '}',
    'private updateRootNavigationTitleBar(): void {',
    '  const visible = this.resolveShell() === HomeShellKind.PhoneNativeHds',
    '  this.publishRootNavigationChrome(',
    '    visible,',
    '    this.rootNavigationTitle(),',
    '    visible && this.ui.selectedDestination === HomeDestination.Home',
    '  )',
    '}',
    'aboutToDisappear() {',
    "  this.publishRootNavigationChrome(false, '首页', false)",
    '}',
    'private get nativeFromHomeTopInset(): number {',
    '  return 0',
    '}',
    '@Builder',
    'private homeTabContent() { HomeTab({ contentBottomInset: this.contentBottomInset }) }',
    '@Builder',
    'private chasingTabContent() {',
    '  ChasingTab({',
    '    fromHome: true,',
    '    contentTopInset: this.nativeFromHomeTopInset,',
    '    contentBottomInset: this.contentBottomInset',
    '  })',
    '}',
    '@Builder',
    'private favoriteTabContent() {',
    '  FavoriteListPage({',
    '    fromHome: true,',
    '    contentTopInset: this.nativeFromHomeTopInset,',
    '    contentBottomInset: this.contentBottomInset',
    '  })',
    '}',
    '@Builder',
    'private mediaTabContent() {',
    '  MediaTab({',
    '    contentBottomInset: this.contentBottomInset,',
    '    rootTitleBarOwned: this.shell === HomeShellKind.PhoneNativeHds',
    '  })',
    '}',
    '@Builder',
    'private mineTabContent() { MineTab({ contentBottomInset: this.contentBottomInset }) }',
    '@Builder',
    'private homeContentOwner() {',
    '  Tabs({ index: this.contentSelectedIndex(), controller: this.contentTabsController }) {',
    '    this.homeTabContent()',
    '    this.chasingTabContent()',
    '    this.favoriteTabContent()',
    '    this.mediaTabContent()',
    '    this.mineTabContent()',
    '  }',
    '}',
    '@Builder',
    'private nonNativeHomeShell(shell: HomeShellKind) {',
    '  this.homeContentOwner()',
    '  if (shell === HomeShellKind.LargeSidebar) {',
    '    this.largeSidebarBuilder()',
    '  } else if (shell === HomeShellKind.MediumDrawer) {',
    '    this.mediumDrawerBuilder()',
    '  } else {',
    '    HomePhoneStandardTabs()',
    '  }',
    '}',
    'build() {',
    '  if (this.shell === HomeShellKind.PhoneNativeHds) {',
    '    HomePhoneNativeTabs({',
    '      homeContentBuilder: () => {',
    '        this.homeTabContent()',
    '      },',
    '      chasingContentBuilder: () => {',
    '        this.chasingTabContent()',
    '      },',
    '      favoriteContentBuilder: () => {',
    '        this.favoriteTabContent()',
    '      },',
    '      mediaContentBuilder: () => {',
    '        this.mediaTabContent()',
    '      },',
    '      mineContentBuilder: () => {',
    '        this.mineTabContent()',
    '      }',
    '    })',
    '  } else {',
    '    this.nonNativeHomeShell(this.shell)',
    '  }',
    '}'
  ].join('\n')
}

function validIndex() {
  return [
    'function usesNativeTopSafeArea(appUIState: AppUIState): boolean {',
    '  return appUIState.themeStyle === ThemeStyle.Native && HdsUiCapability.supportsNativeTheme()',
    '}',
    'function nativeTopSafeAreaTypes(appUIState: AppUIState): Array<LayoutSafeAreaType> {',
    '  return usesNativeTopSafeArea(appUIState) ? [LayoutSafeAreaType.SYSTEM] : []',
    '}',
    'function nativeTopSafeAreaEdges(appUIState: AppUIState): Array<LayoutSafeAreaEdge> {',
    '  return usesNativeTopSafeArea(appUIState) ? [LayoutSafeAreaEdge.TOP] : []',
    '}',
    "@Monitor('appUIState.themeStyle')",
    'onThemeStyleChanged(): void {',
    '  this.modifier.updateLayoutSafeArea(this.appUIState)',
    '}',
    'build() {',
    '  Stack() {',
    '    HMNavigation({})',
    '  }',
    '  .ignoreLayoutSafeArea(',
    '    nativeTopSafeAreaTypes(this.appUIState),',
    '    nativeTopSafeAreaEdges(this.appUIState)',
    '  )',
    '}',
    'export class NavModifier {',
    '  updateLayoutSafeArea(appUIState: AppUIState, instance?: NavigationAttribute): void {',
    '    let navInstance = instance || this.instance',
    '    if (!navInstance) {',
    '      return',
    '    }',
    '    navInstance.ignoreLayoutSafeArea(',
    '      nativeTopSafeAreaTypes(appUIState),',
    '      nativeTopSafeAreaEdges(appUIState)',
    '    )',
    '  }',
    '  initializeModifier(instance: NavigationAttribute): void {',
    '    let appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!',
    '    appUIState.navInstance = instance',
    '    instance.hideTitleBar(true)',
    '    this.updateLayoutSafeArea(appUIState, instance)',
    '  }',
    '}'
  ].join('\n')
}

function validRootPage() {
  return [
    '@HMRouter({',
    '  pageUrl: RouterConsts.IndexPage,',
    '  singleton: true,',
    '  useNavDst: true',
    '})',
    'private rootNavigationMenus(): Array<NavigationMenuItem> {',
    '  let menus: Array<NavigationMenuItem> = []',
    '  if (!this.appUIState.rootNavigationHomeActionsVisible) { return menus }',
    '  if (this.appUIState.rootNavigationLiveTvAvailable) {',
    '    HMRouterMgr.to(RouterConsts.LiveTvPage).push()',
    '  }',
    '  HMRouterMgr.to(RouterConsts.SearchPage).push()',
    '  return []',
    '}',
    'private rootNavigationHeroChromeVisible(): boolean {',
    '  return this.appUIState.rootNavigationHomeActionsVisible &&',
    '    this.appUIState.rootNavigationHomeHeroVisible',
    '}',
    'private pageContent() {}',
    'build() {',
    '  AppRouteDestination({',
    '    title: this.appUIState.rootNavigationTitle,',
    '    titleBarVisible: this.appUIState.isLogin && this.appUIState.rootNavigationTitleBarVisible,',
    '    backButtonVisible: false,',
    '    contentExtendsUnderTitleBar: this.appUIState.rootNavigationHomeActionsVisible,',
    '    heroTitleChrome: this.rootNavigationHeroChromeVisible(),',
    '    menus: this.rootNavigationMenus(),',
    '    contentBuilder: () => { this.pageContent() },',
    '    legacyContentBuilder: () => { this.pageContent() }',
    '  })',
    '}'
  ].join('\n')
}

function validHomeTab() {
  return [
    'private syncRootNavigationLiveTvAvailability(): void {',
    '  this.vm.appUIState.rootNavigationLiveTvAvailable = this.ui.hasLiveTv',
    '}',
    'onLiveTvAvailabilityChange(): void {',
    '  this.syncRootNavigationLiveTvAvailability()',
    '}',
    'aboutToAppear(): void {',
    '  this.syncRootNavigationLiveTvAvailability()',
    '}',
    'HomeInsetsPolicy.scrollTail(this.contentBottomInset, safeBottom)'
  ].join('\n')
}

function validHomeViewModel() {
  return [
    'private getRecommendationList(): Promise<void> {',
    '  return Promise.resolve().then(() => {',
    '    const recommendations: VideoItem[] = []',
    '    this.ui.recommendationList = recommendations',
    '    const heroVisible = recommendations.length > 0',
    '    if (this.appUIState.rootNavigationHomeHeroVisible !== heroVisible) {',
    '      this.appUIState.rootNavigationHomeHeroVisible = heroVisible',
    '    }',
    '  })',
    '}'
  ].join('\n')
}

function validFromHomeTopInsetOwner() {
  return [
    '@Param fromHome: boolean = false',
    '@Param contentTopInset: number = 0',
    'private ownedContentTopInset(): number {',
    '  return this.fromHome ? Math.max(0, this.contentTopInset) : 0',
    '}',
    'Column() {}',
    '.padding({ top: this.ownedContentTopInset() })'
  ].join('\n')
}

function validNativeHost() {
  return [
    "import { HdsTabs, HdsTabsController, HdsTabsModifier, hdsMaterial } from '@kit.UIDesignKit'",
    '@BuilderParam homeContentBuilder: () => void',
    '@BuilderParam chasingContentBuilder: () => void',
    '@BuilderParam favoriteContentBuilder: () => void',
    '@BuilderParam mediaContentBuilder: () => void',
    '@BuilderParam mineContentBuilder: () => void',
    'NativeThemeConstructionProbe.markHdsNavigationConstruction()',
    'new HdsTabsController()',
    'private normalizedSafeBottom(): number {',
    '  return Math.max(0, this.safeBottom)',
    '}',
    'private tabBuilder() {',
    '  Column() {}',
    "  .height('100%')",
    "  .width('100%')",
    '}',
    'build() {',
    '  HdsTabs({ index: this.selectedIndex, controller: this.tabsController }) {',
    '    TabContent() { this.homeContentBuilder() }',
    '    TabContent() { this.chasingContentBuilder() }',
    '    TabContent() { this.favoriteContentBuilder() }',
    '    TabContent() { this.mediaContentBuilder() }',
    '    TabContent() { this.mineContentBuilder() }',
    '  }',
    '  .barHeight(UIConstants.BOTTOM_BAR_HEIGHT)',
    '  .barHeight(UIConstants.BOTTOM_BAR_HEIGHT)',
    '  .barFloatingStyle({ barBottomMargin: this.normalizedSafeBottom() })',
    "  .width('100%')",
    "  .height('100%')",
    '}'
  ].join('\n')
}

function validSources() {
  return new Map([
    [appStatePath, [
      '@Trace feiniuPointLightEnabled: boolean = false',
      "@Trace rootNavigationTitle: string = '首页'",
      '@Trace rootNavigationTitleBarVisible: boolean = false',
      '@Trace rootNavigationHomeActionsVisible: boolean = false',
      '@Trace rootNavigationHomeHeroVisible: boolean = false',
      '@Trace rootNavigationLiveTvAvailable: boolean = false'
    ].join('\n')],
    [indexPath, validIndex()],
    [sharedDestinationPath, "import { HdsNavDestination, hdsMaterial } from '@kit.UIDesignKit'"],
    [rootPagePath, validRootPage()],
    [homeScreenPath, validHomeScreen()],
    [chromeTabPath, 'Blank().height(0)'],
    [standardPath, [
      'private tabsController: TabsController = new TabsController()',
      'Tabs({ index: this.selectedIndex, controller: this.tabsController })',
      'CustomTab'
    ].join('\n')],
    [nativePath, validNativeHost()],
    [pointLightPath, [
      'if (deviceInfo.sdkApiVersion < FeiniuPointLightModifier.MIN_API_VERSION)',
      '!options.enabled',
      'return uiEffect.createEffect()',
      'NativeThemeConstructionProbe.markHdsEffectConstruction()',
      'new hdsEffect.HdsEffectBuilder()'
    ].join('\n')],
    [homeTabPath, validHomeTab()],
    [homeViewModelPath, validHomeViewModel()],
    [chasingTabPath, validFromHomeTopInsetOwner()],
    [favoriteTabPath, validFromHomeTopInsetOwner()],
    [mediaTabPath, [
      '@Param rootTitleBarOwned: boolean = false',
      'private contentTopSpacer(): number {',
      '  const safeTop = Math.max(0, this.appUIState.safeTop)',
      '  return this.rootTitleBarOwned ? 0 : safeTop + UIConstants.ACTION_BAR_HEIGHT',
      '}',
      'private topBar() {',
      '  if (!this.rootTitleBarOwned) {',
      '    ActionBar()',
      '  }',
      '}',
      'Blank().height(this.contentTopSpacer())',
      'HomeInsetsPolicy.scrollTail(this.contentBottomInset, safeBottom)'
    ].join('\n')],
    [mineTabPath, 'HomeInsetsPolicy.scrollTail(this.contentBottomInset, safeBottom)']
  ])
}

test('accepts a full-page native HDS content host and isolated non-native shell', () => {
  assert.doesNotThrow(() => validateNativeThemeHostOwnership(validSources()))
})

test('requires a default-hidden root Home Hero presence state', () => {
  const sources = validSources()
  sources.set(appStatePath, sources.get(appStatePath)
    .replace('@Trace rootNavigationHomeHeroVisible: boolean = false\n', ''))
  assert.throws(
    () => validateNativeThemeHostOwnership(sources),
    /root Home Hero presence/
  )
})

test('requires root content extension to remain stable while Hero data changes', () => {
  const sources = validSources()
  sources.set(rootPagePath, sources.get(rootPagePath)
    .replace(
      'contentExtendsUnderTitleBar: this.appUIState.rootNavigationHomeActionsVisible',
      'contentExtendsUnderTitleBar: this.rootNavigationHeroChromeVisible()'
    ))
  assert.throws(
    () => validateNativeThemeHostOwnership(sources),
    /keep Home content extension stable/
  )
})

test('requires Hero title chrome to share the content-extension gate', () => {
  const sources = validSources()
  sources.set(rootPagePath, sources.get(rootPagePath)
    .replace(
      'heroTitleChrome: this.rootNavigationHeroChromeVisible()',
      'heroTitleChrome: true'
    ))
  assert.throws(
    () => validateNativeThemeHostOwnership(sources),
    /Hero title chrome must use the populated Home Hero gate/
  )
})

test('requires Home Hero presence to come from the committed recommendation result', () => {
  const sources = validSources()
  sources.set(homeViewModelPath, sources.get(homeViewModelPath)
    .replace(
      'recommendations.length > 0',
      'this.appUIState.nativeThemeAvailable'
    ))
  assert.throws(
    () => validateNativeThemeHostOwnership(sources),
    /committed recommendation result/
  )
})

test('rejects a HomeTab recommendation monitor that feeds back into the route host', () => {
  const sources = validSources()
  sources.set(homeTabPath, sources.get(homeTabPath) + [
    '',
    "@Monitor('ui.recommendationList')",
    'onRecommendationListChange(): void {',
    '  this.syncRootNavigationHomeHeroVisibility()',
    '}'
  ].join('\n'))
  assert.throws(
    () => validateNativeThemeHostOwnership(sources),
    /must not feed recommendation state back/
  )
})

test('requires redundant Home Hero visibility writes to be suppressed', () => {
  const sources = validSources()
  sources.set(homeViewModelPath, sources.get(homeViewModelPath)
    .replace(
      'if (this.appUIState.rootNavigationHomeHeroVisible !== heroVisible) {',
      'if (true) {'
    ))
  assert.throws(
    () => validateNativeThemeHostOwnership(sources),
    /suppress redundant Hero visibility writes/
  )
})

test('derives the default workspace root from the verifier script location', () => {
  const scriptsDirectory = dirname(fileURLToPath(import.meta.url))
  assert.equal(defaultWorkspaceRoot(), resolve(scriptsDirectory, '..'))
})

test('rejects HDS navigation outside the native phone host', () => {
  const sources = validSources()
  sources.set(standardPath, "import { HdsTabs } from '@kit.UIDesignKit'\nCustomTab")
  assert.throws(
    () => validateNativeThemeHostOwnership(sources),
    /HDS navigation outside native host/
  )
})

test('rejects direct hdsEffect imports outside the point-light helper', () => {
  const sources = validSources()
  sources.set(homeTabPath, "import { hdsEffect } from '@kit.UIDesignKit'")
  assert.throws(
    () => validateNativeThemeHostOwnership(sources),
    /hdsEffect import outside point-light helper/
  )
})

test('keeps the outer Navigation title bar permanently hidden', () => {
  const sources = validSources()
  sources.set(indexPath, validIndex().replace(
    'instance.hideTitleBar(true)',
    'instance.hideTitleBar(!appUIState.rootNavigationTitleBarVisible)'
  ))
  assert.throws(
    () => validateNativeThemeHostOwnership(sources),
    /outer Navigation title bar must remain permanently hidden/
  )
})

test('extends the real Navigation into the top safe area only for the Native theme', () => {
  const ungatedSources = validSources()
  ungatedSources.set(indexPath, validIndex().replace(
    'appUIState.themeStyle === ThemeStyle.Native && HdsUiCapability.supportsNativeTheme()',
    'appUIState.themeStyle === ThemeStyle.Native'
  ))
  assert.throws(
    () => validateNativeThemeHostOwnership(ungatedSources),
    /Navigation safe-area expansion must require Native theme and API 26 capability/
  )

  const feiniuLeakSources = validSources()
  feiniuLeakSources.set(indexPath, validIndex().replace(
    'return usesNativeTopSafeArea(appUIState) ? [LayoutSafeAreaEdge.TOP] : []',
    'return [LayoutSafeAreaEdge.TOP]'
  ))
  assert.throws(
    () => validateNativeThemeHostOwnership(feiniuLeakSources),
    /Navigation safe-area expansion must preserve the Feiniu safe-area layout/
  )

  const missingNavigationOwnerSources = validSources()
  missingNavigationOwnerSources.set(indexPath, validIndex().replace(
    '    navInstance.ignoreLayoutSafeArea(',
    '    navInstance.safeAreaPadding('
  ))
  assert.throws(
    () => validateNativeThemeHostOwnership(missingNavigationOwnerSources),
    /NavModifier must apply the top safe area to the real NavigationAttribute/
  )

  const missingRootOwnerSources = validSources()
  missingRootOwnerSources.set(indexPath, validIndex().replace(
    '  }\n  .ignoreLayoutSafeArea(',
    '  }\n  .backgroundColor('
  ))
  assert.throws(
    () => validateNativeThemeHostOwnership(missingRootOwnerSources),
    /root Stack and real Navigation must extend into the Native top safe area/
  )

  const duplicateColumnOwnerSources = validSources()
  duplicateColumnOwnerSources.set(indexPath, validIndex().replace(
    '    HMNavigation({})',
    '    Column() {}\n' +
      '      .ignoreLayoutSafeArea([LayoutSafeAreaType.SYSTEM], [LayoutSafeAreaEdge.TOP])\n' +
      '    HMNavigation({})'
  ))
  assert.throws(
    () => validateNativeThemeHostOwnership(duplicateColumnOwnerSources),
    /nested root content must not duplicate top safe-area ownership/
  )

  const staleThemeSources = validSources()
  staleThemeSources.set(indexPath, validIndex().replace(
    '  this.modifier.updateLayoutSafeArea(this.appUIState)',
    ''
  ))
  assert.throws(
    () => validateNativeThemeHostOwnership(staleThemeSources),
    /Index must refresh Navigation safe area when the theme changes/
  )
})

test('synchronizes root destination chrome and clears it when HomeScreen leaves', () => {
  const unsynchronizedSources = validSources()
  unsynchronizedSources.set(homeScreenPath, validHomeScreen().replace(
    '  this.appUIState.rootNavigationTitleBarVisible = visible\n',
    ''
  ))
  assert.throws(
    () => validateNativeThemeHostOwnership(unsynchronizedSources),
    /publish only the current root destination chrome state/
  )

  const leakedSources = validSources()
  leakedSources.set(homeScreenPath, validHomeScreen().replace(
    "  this.publishRootNavigationChrome(false, '首页', false)",
    '  this.updateRootNavigationTitleBar()'
  ))
  assert.throws(
    () => validateNativeThemeHostOwnership(leakedSources),
    /clear root destination chrome when leaving the page/
  )
})

test('keeps one Native title bar across all five tabs and maps each destination title', () => {
  const hiddenSecondarySources = validSources()
  hiddenSecondarySources.set(homeScreenPath, validHomeScreen().replace(
    '  const visible = this.resolveShell() === HomeShellKind.PhoneNativeHds',
    '  const visible = this.resolveShell() === HomeShellKind.PhoneNativeHds && ' +
      'this.ui.selectedDestination === HomeDestination.Home'
  ))
  assert.throws(
    () => validateNativeThemeHostOwnership(hiddenSecondarySources),
    /keep the Native title bar visible across tabs/
  )

  const wrongTitleSources = validSources()
  wrongTitleSources.set(homeScreenPath, validHomeScreen().replace("return '媒体库'", "return '马飞'"))
  assert.throws(
    () => validateNativeThemeHostOwnership(wrongTitleSources),
    /map every HomeDestination to its Native title/
  )
})

test('requires IndexPage to own the root system destination', () => {
  const wrappedSources = validSources()
  wrappedSources.set(rootPagePath, validRootPage().replace('  useNavDst: true\n', ''))
  assert.throws(
    () => validateNativeThemeHostOwnership(wrappedSources),
    /IndexPage must opt out of HMRouter wrapping and own one AppRouteDestination/
  )

  const wrongBindingSources = validSources()
  wrongBindingSources.set(rootPagePath, validRootPage().replace(
    'titleBarVisible: this.appUIState.isLogin && this.appUIState.rootNavigationTitleBarVisible',
    'titleBarVisible: true'
  ))
  assert.throws(
    () => validateNativeThemeHostOwnership(wrongBindingSources),
    /bind the current root destination title, menus, and content/
  )

  const staticTitleSources = validSources()
  staticTitleSources.set(rootPagePath, validRootPage().replace(
    'title: this.appUIState.rootNavigationTitle',
    "title: $r('app.string.app_name')"
  ))
  assert.throws(
    () => validateNativeThemeHostOwnership(staticTitleSources),
    /bind the current root destination title, menus, and content/
  )
})

test('keeps dynamic root menu ownership out of the outer Navigation', () => {
  const outerSources = validSources()
  outerSources.set(indexPath, validIndex() + '\nmenus: []')
  assert.throws(
    () => validateNativeThemeHostOwnership(outerSources),
    /outer Navigation must not own the root title or menu actions/
  )

  const directMutationSources = validSources()
  directMutationSources.set(homeTabPath, validHomeTab() + '\nthis.vm.appUIState.navInstance?.menus([])')
  assert.throws(
    () => validateNativeThemeHostOwnership(directMutationSources),
    /publish menu availability without mutating the outer Navigation/
  )

  const leakedSecondaryMenuSources = validSources()
  leakedSecondaryMenuSources.set(rootPagePath, validRootPage().replace(
    '  if (!this.appUIState.rootNavigationHomeActionsVisible) { return menus }\n',
    ''
  ))
  assert.throws(
    () => validateNativeThemeHostOwnership(leakedSecondaryMenuSources),
    /menus only on the Home tab/
  )
})

test('requires external index binding in all tab owners', () => {
  const standardSources = validSources()
  standardSources.set(standardPath, [
    'private tabsController: TabsController = new TabsController()',
    'Tabs({ controller: this.tabsController })',
    'CustomTab'
  ].join('\n'))
  assert.throws(
    () => validateNativeThemeHostOwnership(standardSources),
    /standard host must bind external selectedIndex/
  )

  const nativeSources = validSources()
  nativeSources.set(nativePath, validNativeHost().replace('index: this.selectedIndex, ', ''))
  assert.throws(
    () => validateNativeThemeHostOwnership(nativeSources),
    /native host must bind external selectedIndex/
  )

  const contentSources = validSources()
  contentSources.set(homeScreenPath, validHomeScreen()
    .replace('index: this.contentSelectedIndex(), ', ''))
  assert.throws(
    () => validateNativeThemeHostOwnership(contentSources),
    /content Tabs must bind semantic external index/
  )
})

test('requires a 60vp HDS bar with a separately normalized safe bottom', () => {
  const heightSources = validSources()
  heightSources.set(nativePath, validNativeHost()
    .replaceAll('UIConstants.BOTTOM_BAR_HEIGHT', 'this.safeBottom'))
  assert.throws(
    () => validateNativeThemeHostOwnership(heightSources),
    /native HDS bar height must exclude the bottom safe area/
  )

  const safeSources = validSources()
  safeSources.set(nativePath, validNativeHost()
    .replace('return Math.max(0, this.safeBottom)', 'return this.safeBottom * 2'))
  assert.throws(
    () => validateNativeThemeHostOwnership(safeSources),
    /native safeBottom must be normalized without bottom-bar arithmetic/
  )

  const marginSources = validSources()
  marginSources.set(nativePath, validNativeHost()
    .replace('barBottomMargin: this.normalizedSafeBottom()', 'barBottomMargin: 0'))
  assert.throws(
    () => validateNativeThemeHostOwnership(marginSources),
    /floating bar must own the bottom safe area exactly once/
  )
})

test('rejects a shortened native HDS host', () => {
  const sources = validSources()
  sources.set(nativePath, validNativeHost()
    .replace(".height('100%')\n}", '.height(this.navigationChromeInset)\n}'))
  assert.throws(
    () => validateNativeThemeHostOwnership(sources),
    /native HDS tabs must be a full-page content host/
  )
})

test('requires five real native TabContent pages and all shared builders', () => {
  const tabSources = validSources()
  tabSources.set(nativePath, validNativeHost()
    .replace('    TabContent() { this.mineContentBuilder() }\n', ''))
  assert.throws(
    () => validateNativeThemeHostOwnership(tabSources),
    /native HDS tabs must own exactly five TabContent pages/
  )

  const builderSources = validSources()
  builderSources.set(homeScreenPath, validHomeScreen()
    .replace([
      '      mineContentBuilder: () => {',
      '        this.mineTabContent()',
      '      }'
    ].join('\n'), ''))
  assert.throws(
    () => validateNativeThemeHostOwnership(builderSources),
    /HomeScreen must pass exactly one mineContentBuilder/
  )
})

test('requires five arrow closures that preserve the parent HomeScreen context', () => {
  const bareReferenceSources = validSources()
  bareReferenceSources.set(homeScreenPath, validHomeScreen().replace([
    '      mineContentBuilder: () => {',
    '        this.mineTabContent()',
    '      }'
  ].join('\n'), '      mineContentBuilder: this.mineTabContent'))
  assert.throws(
    () => validateNativeThemeHostOwnership(bareReferenceSources),
    /must not pass a bare parent builder reference: mineContentBuilder/
  )

  const wrongInvocationSources = validSources()
  wrongInvocationSources.set(homeScreenPath, validHomeScreen()
    .replace('        this.favoriteTabContent()', '        this.mediaTabContent()'))
  assert.throws(
    () => validateNativeThemeHostOwnership(wrongInvocationSources),
    /arrow closure must invoke favoriteTabContent exactly once/
  )
})

test('keeps the root destination as the only native top-inset owner', () => {
  const missingOwnerSources = validSources()
  missingOwnerSources.set(homeScreenPath, validHomeScreen()
    .replace('private get nativeFromHomeTopInset(): number {', 'private get otherInset(): number {'))
  assert.throws(
    () => validateNativeThemeHostOwnership(missingOwnerSources),
    /must not duplicate the root destination top inset/
  )

  const duplicateInsetSources = validSources()
  duplicateInsetSources.set(homeScreenPath, validHomeScreen()
    .replace('  return 0', '  return Math.max(0, this.appUIState.safeTop)'))
  assert.throws(
    () => validateNativeThemeHostOwnership(duplicateInsetSources),
    /must not duplicate the root destination top inset/
  )

  const missingChasingSources = validSources()
  missingChasingSources.set(homeScreenPath, validHomeScreen()
    .replace('    contentTopInset: this.nativeFromHomeTopInset,\n', ''))
  assert.throws(
    () => validateNativeThemeHostOwnership(missingChasingSources),
    /ChasingTab must receive the native from-home top inset/
  )

  const leakedHomeSources = validSources()
  leakedHomeSources.set(homeScreenPath, validHomeScreen()
    .replace('HomeTab({ contentBottomInset:', 'HomeTab({ contentTopInset: 1, contentBottomInset:'))
  assert.throws(
    () => validateNativeThemeHostOwnership(leakedHomeSources),
    /HomeScreen must pass contentTopInset only to ChasingTab and FavoriteListPage/
  )
})

test('lets only the phone Native destination own the MediaTab title and ActionBar spacer', () => {
  const ungatedOwnerSources = validSources()
  ungatedOwnerSources.set(homeScreenPath, validHomeScreen().replace(
    'rootTitleBarOwned: this.shell === HomeShellKind.PhoneNativeHds',
    'rootTitleBarOwned: true'
  ))
  assert.throws(
    () => validateNativeThemeHostOwnership(ungatedOwnerSources),
    /delegate its title only to the phone Native HDS destination/
  )

  const visibleNativeActionBarSources = validSources()
  visibleNativeActionBarSources.set(mediaTabPath, validSources().get(mediaTabPath)
    .replace('  if (!this.rootTitleBarOwned) {', '  if (true) {'))
  assert.throws(
    () => validateNativeThemeHostOwnership(visibleNativeActionBarSources),
    /custom ActionBar must stay outside the phone Native HDS branch/
  )

  const legacySpacerRegressionSources = validSources()
  legacySpacerRegressionSources.set(mediaTabPath, validSources().get(mediaTabPath)
    .replace(
      'return this.rootTitleBarOwned ? 0 : safeTop + UIConstants.ACTION_BAR_HEIGHT',
      'return safeTop'
    ))
  assert.throws(
    () => validateNativeThemeHostOwnership(legacySpacerRegressionSources),
    /leave all Native top-inset ownership to the root destination/
  )
})

test('guards route pages and consumes their owned top inset exactly once as padding', () => {
  const unguardedSources = validSources()
  unguardedSources.set(chasingTabPath, validFromHomeTopInsetOwner()
    .replace('this.fromHome ? Math.max(0, this.contentTopInset) : 0', 'Math.max(0, this.contentTopInset)'))
  assert.throws(
    () => validateNativeThemeHostOwnership(unguardedSources),
    /ChasingTab must guard contentTopInset with fromHome/
  )

  const duplicateSources = validSources()
  duplicateSources.set(favoriteTabPath, validFromHomeTopInsetOwner() +
    '\n.padding({ top: this.ownedContentTopInset() })')
  assert.throws(
    () => validateNativeThemeHostOwnership(duplicateSources),
    /FavoriteListPage must consume the owned top inset exactly once/
  )

  const wrongModifierSources = validSources()
  wrongModifierSources.set(chasingTabPath, validFromHomeTopInsetOwner()
    .replace('.padding({ top:', '.margin({ top:'))
  assert.throws(
    () => validateNativeThemeHostOwnership(wrongModifierSources),
    /ChasingTab must consume the owned top inset as top padding/
  )
})

test('rejects fixed native tab-item widths that exceed HDS allocation', () => {
  const sources = validSources()
  sources.set(nativePath, validNativeHost().replace(".width('100%')", '.width(70)'))
  assert.throws(
    () => validateNativeThemeHostOwnership(sources),
    /native tab items must fit the width allocated by HDS/
  )
})

test('keeps business pages in shared builders instead of navigation hosts', () => {
  const sources = validSources()
  sources.set(nativePath, validNativeHost().replace(
    'this.homeContentBuilder()',
    'HomeTab({ contentBottomInset: this.contentBottomInset })'
  ))
  assert.throws(
    () => validateNativeThemeHostOwnership(sources),
    /navigation chrome must not construct HomeTab/
  )
})

test('only the non-native shell may construct the separate content Tabs', () => {
  const sources = validSources()
  sources.set(homeScreenPath, validHomeScreen()
    .replace('  this.homeContentOwner()\n', '')
    .replace('  if (this.shell === HomeShellKind.PhoneNativeHds) {', [
      '  this.homeContentOwner()',
      '  if (this.shell === HomeShellKind.PhoneNativeHds) {'
    ].join('\n')))
  assert.throws(
    () => validateNativeThemeHostOwnership(sources),
    /only the non-native shell may call homeContentOwner/
  )
})

test('keeps native and non-native root branches mutually exclusive', () => {
  const sources = validSources()
  sources.set(homeScreenPath, validHomeScreen()
    .replace('  } else {\n    this.nonNativeHomeShell(this.shell)',
      '  }\n  if (true) {\n    this.nonNativeHomeShell(this.shell)'))
  assert.throws(
    () => validateNativeThemeHostOwnership(sources),
    /shell branches must be one mutually exclusive chain/
  )
})

test('keeps wide and standard chrome inside the non-native shell', () => {
  const sources = validSources()
  sources.set(homeScreenPath, validHomeScreen()
    .replace('    this.largeSidebarBuilder()', [
      '    this.largeSidebarBuilder()',
      '    HomePhoneStandardTabs()'
    ].join('\n')))
  assert.throws(
    () => validateNativeThemeHostOwnership(sources),
    /large sidebar branch constructs wrong chrome owner/
  )
})

test('rejects duplicate breakpoint monitors and missing synchronization duties', () => {
  const duplicateSources = validSources()
  duplicateSources.set(homeScreenPath, validHomeScreen() + [
    '',
    "@Monitor('appUIState.currentBreakpoint')",
    'onDuplicateBreakpointChange(): void {}'
  ].join('\n'))
  assert.throws(
    () => validateNativeThemeHostOwnership(duplicateSources),
    /HomeScreen must define exactly one currentBreakpoint monitor/
  )

  const syncSources = validSources()
  syncSources.set(homeScreenPath, validHomeScreen()
    .replace('  this.contentTabsController.changeIndex(this.contentSelectedIndex())\n', ''))
  assert.throws(
    () => validateNativeThemeHostOwnership(syncSources),
    /breakpoint monitor must synchronize semantic content index/
  )
})

test('requires construction probes before HDS constructors', () => {
  const sources = validSources()
  sources.set(nativePath, validNativeHost()
    .replace([
      'NativeThemeConstructionProbe.markHdsNavigationConstruction()',
      'new HdsTabsController()'
    ].join('\n'), [
      'new HdsTabsController()',
      'NativeThemeConstructionProbe.markHdsNavigationConstruction()'
    ].join('\n')))
  assert.throws(
    () => validateNativeThemeHostOwnership(sources),
    /navigation probe must precede HdsTabsController construction/
  )
})

test('rejects direct bottom-bar arithmetic in tab content', () => {
  const sources = validSources()
  sources.set(homeTabPath, validHomeTab() + '\nUIConstants.BOTTOM_BAR_HEIGHT +\n  appUIState.safeBottom')
  assert.throws(
    () => validateNativeThemeHostOwnership(sources),
    /tab content owns bottom navigation arithmetic/
  )
})
