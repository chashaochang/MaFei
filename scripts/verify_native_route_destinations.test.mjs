import assert from 'node:assert/strict'
import test from 'node:test'
import {
  defaultWorkspaceRoot,
  validateNativeRouteDestinations
} from './verify_native_route_destinations.mjs'

const destinationPath = 'entry/src/main/ets/component/AppRouteDestination.ets'
const playerPath = 'entry/src/main/ets/features/setting/PlayerEnginePage.ets'
const aboutPath = 'entry/src/main/ets/features/setting/AboutPage.ets'
const searchPath = 'entry/src/main/ets/features/search/SearchPage.ets'
const videoListPath = 'entry/src/main/ets/features/videolist/VideoListPage.ets'
const favoriteListPath = 'entry/src/main/ets/features/favorite/FavoriteListPage.ets'
const liveTvChannelListPath = 'entry/src/main/ets/features/livetv/LiveTvChannelListPage.ets'
const managementDashboardPath = 'entry/src/main/ets/features/management/ManagementDashboardPage.ets'
const managementUsersPath = 'entry/src/main/ets/features/management/ManagementUsersPage.ets'
const managementSessionDetailPath = 'entry/src/main/ets/features/management/ManagementSessionDetailPage.ets'
const managementTaskDetailPath = 'entry/src/main/ets/features/management/ManagementTaskDetailPage.ets'
const managementUserCreatePath = 'entry/src/main/ets/features/management/ManagementUserCreatePage.ets'
const managementUserDetailPath = 'entry/src/main/ets/features/management/ManagementUserDetailPage.ets'
const minePath = 'entry/src/main/ets/features/home/minetab/MineTab.ets'

function validDestination() {
  return [
    "import { NavDestinationHelper } from '@hadss/hmrouter'",
    "import { HdsNavDestination } from '@kit.UIDesignKit'",
    'private helper: NavDestinationHelper = new NavDestinationHelper(this)',
    'beforeBack: () => boolean = () => false',
    '@Prop titleBarVisible: boolean = true',
    '@Prop backButtonVisible: boolean = true',
    '@Prop menus: Array<NavigationMenuItem> = []',
    '@Prop scrollControllers: Array<Scroller> = []',
    'private usesNativeChrome(): boolean {',
    '  return this.appUIState.themeStyle === ThemeStyle.Native && HdsUiCapability.supportsNativeTheme()',
    '}',
    'private usesHdsDestination(): boolean {',
    '  return HdsUiCapability.supportsNativeTheme()',
    '}',
    'private nativeSafeAreaTypes(): Array<LayoutSafeAreaType> {',
    '  return this.usesNativeChrome() ? [LayoutSafeAreaType.SYSTEM] : []',
    '}',
    'private nativeSafeAreaEdges(): Array<LayoutSafeAreaEdge> {',
    '  return this.usesNativeChrome() ? [LayoutSafeAreaEdge.TOP, LayoutSafeAreaEdge.BOTTOM] : []',
    '}',
    'private nativeMenus(): Array<HdsNavigationMenuItemOptions> {',
    '  return this.menus.map((item: NavigationMenuItem): HdsNavigationMenuItemOptions => {',
    '    return { content: { icon: item.icon, label: item.value, action: item.action } }',
    '  })',
    '}',
    'private nativeTitleBarOptions(): HdsNavigationTitleBarOptions {',
    '  return {',
    '    avoidLayoutSafeArea: true,',
    '    enableComponentSafeArea: false,',
    '    content: {',
    '      title: { mainTitle: this.title, mainTitleSize: TitleSize.TITLE_S },',
    '      menu: { value: this.nativeMenus(), maxCount: 3 }',
    '    },',
    '    style: {',
    '      scrollEffectOpts: {',
    '        enableScrollEffect: true,',
    '        scrollEffectType: HdsScrollEffectType.IMMERSIVE_GRADIENT_BLUR,',
    '        blurEffectiveStartOffset: LengthMetrics.vp(8),',
    '        blurEffectiveEndOffset: LengthMetrics.vp(56)',
    '      },',
    '      systemMaterialEffect: {',
    '        materialType: hdsMaterial.MaterialType.IMMERSIVE,',
    '        materialLevel: hdsMaterial.MaterialLevel.ADAPTIVE',
    '      },',
    '      originalStyle: {',
    '        backgroundStyle: {',
    "          backgroundColor: $r('sys.color.ohos_id_color_titlebar_bg_transparent'),",
    '          maskExtraHeight: 16,',
    '          blurRadius: 48',
    '        },',
    '        contentStyle: {',
    '          titleStyle: {',
    "            mainTitleColor: $r('app.color.text_primary'),",
    "            subTitleColor: $r('app.color.text_2')",
    '          },',
    '          menuStyle: {',
    "            iconColor: $r('app.color.text_primary'),",
    "            textColor: $r('app.color.color_main')",
    '          },',
    '          backIconStyle: {',
    "            iconColor: $r('app.color.text_primary')",
    '          }',
    '        }',
    '      },',
    '      scrollEffectStyle: {',
    '        backgroundStyle: {',
    "          backgroundColor: $r('sys.color.ohos_id_color_titlebar_bg'),",
    '          maskExtraHeight: 16,',
    '          blurRadius: 48',
    '        },',
    '        contentStyle: {',
    '          titleStyle: {',
    "            mainTitleColor: $r('app.color.text_primary'),",
    "            subTitleColor: $r('app.color.text_2')",
    '          },',
    '          menuStyle: {',
    "            iconColor: $r('app.color.text_primary'),",
    "            textColor: $r('app.color.color_main')",
    '          },',
    '          backIconStyle: {',
    "            iconColor: $r('app.color.text_primary')",
    '          }',
    '        }',
    '      }',
    '    }',
    '  }',
    '}',
    'private handleBackPressed(): boolean {',
    '  return this.beforeBack() || this.helper.onBackPressed()',
    '}',
    'private hdsContent() {',
    '  if (this.usesNativeChrome()) {',
    '    this.contentBuilder()',
    '  } else {',
    '    this.legacyContentBuilder()',
    '  }',
    '}',
    'private hdsDestination() {',
    '  HdsNavDestination() {',
    '    this.hdsContent()',
    '  }',
    '    .backgroundColor(AppThemeSurfaceResolver.routeBackground(',
    '      this.appUIState.themeStyle,',
    '      HdsUiCapability.supportsNativeTheme()',
    '    ))',
    '    .titleMode(HdsNavDestinationTitleMode.MINI)',
    '    .titleBar(this.nativeTitleBarOptions())',
    '    .hideTitleBar(!this.titleBarVisible || !this.usesNativeChrome())',
    '    .hideBackButton(!this.backButtonVisible)',
    '    .hideToolBar(true)',
    '    .bindToScrollable(this.titleBarVisible && this.usesNativeChrome() ? this.scrollControllers : [])',
    '    .onWillAppear(() => { this.helper.onWillAppear() })',
    '    .onAppear(() => { this.helper.onAppear() })',
    '    .onWillShow(() => { this.helper.onWillShow() })',
    '    .onShown(() => { this.helper.onShown() })',
    '    .onWillHide(() => { this.helper.onWillHide() })',
    '    .onHidden(() => { this.helper.onHidden() })',
    '    .onWillDisappear(() => { this.helper.onWillDisappear() })',
    '    .onDisAppear(() => { this.helper.onDisAppear() })',
    '    .onReady((ctx) => { this.helper.onReady(ctx) })',
    '    .onBackPressed(() => this.handleBackPressed())',
    '    .onResult((result) => { this.helper.onResult(result) })',
    '    .onActive((reason) => { this.helper.onActive(reason) })',
    '    .onInactive((reason) => { this.helper.onInactive(reason) })',
    '    .ignoreLayoutSafeArea(',
    '      this.nativeSafeAreaTypes(),',
    '      this.nativeSafeAreaEdges()',
    '    )',
    "    .width('100%')",
    "    .height('100%')",
    '}',
    'private legacyDestination() {',
    '  NavDestination() {',
    '    this.legacyContentBuilder()',
    '  }',
    '    .backgroundColor(AppThemeSurfaceResolver.routeBackground(',
    '      this.appUIState.themeStyle,',
    '      HdsUiCapability.supportsNativeTheme()',
    '    ))',
    '    .title(this.title, { barStyle: BarStyle.STANDARD })',
    '    .menus([])',
    '    .hideTitleBar(true)',
    '    .hideBackButton(!this.backButtonVisible)',
    '    .attributeModifier(this.helper.modifier)',
    '    .gestureModifier(this.helper.gestureModifier)',
    '    .onWillAppear(() => { this.helper.onWillAppear() })',
    '    .onAppear(() => { this.helper.onAppear() })',
    '    .onWillShow(() => { this.helper.onWillShow() })',
    '    .onShown(() => { this.helper.onShown() })',
    '    .onWillHide(() => { this.helper.onWillHide() })',
    '    .onHidden(() => { this.helper.onHidden() })',
    '    .onWillDisappear(() => { this.helper.onWillDisappear() })',
    '    .onDisAppear(() => { this.helper.onDisAppear() })',
    '    .onReady((ctx) => { this.helper.onReady(ctx) })',
    '    .onBackPressed(() => this.handleBackPressed())',
    '    .onResult((result) => { this.helper.onResult(result) })',
    '    .onActive((reason) => { this.helper.onActive(reason) })',
    '    .onInactive((reason) => { this.helper.onInactive(reason) })',
    '    .onNewParam((param) => { this.helper.onNewParam(param) })',
    '    .expandSafeArea(',
    '      [SafeAreaType.SYSTEM],',
    '      [SafeAreaEdge.BOTTOM]',
    '    )',
    "    .width('100%')",
    "    .height('100%')",
    '}',
    'build() {',
    '  if (this.usesHdsDestination()) {',
    '    this.hdsDestination()',
    '  } else {',
    '    this.legacyDestination()',
    '  }',
    '}'
  ].join('\n')
}

function validPage(name, legacyBackground, publicContent = []) {
  return [
    "import { AppRouteDestination } from '../../component/AppRouteDestination'",
    '@HMRouter({',
    `  pageUrl: RouterConsts.${name},`,
    '  useNavDst: true',
    '})',
    'private pageContent(showLegacyActionBar: boolean) {',
    '  if (showLegacyActionBar) {',
    "    ActionBar({ title: 'Title' })",
    '  }',
    ...publicContent,
    `  Column() {}.width('100%').height('100%').backgroundColor(` +
      `showLegacyActionBar ? $r('app.color.${legacyBackground}') : Color.Transparent)`,
    '}',
    'build() {',
    '  AppRouteDestination({',
    "    title: 'Title',",
    '    contentBuilder: () => { this.pageContent(false) },',
    '    legacyContentBuilder: () => { this.pageContent(true) }',
    '  })',
    '}'
  ].join('\n')
}

function validSearchPage() {
  return [
    "import { AppRouteDestination } from '../../component/AppRouteDestination'",
    '@HMRouter({',
    '  pageUrl: RouterConsts.SearchPage,',
    '  useNavDst: true',
    '})',
    '@Builder',
    'private nativeSearchForm() {',
    '  Row() { this.searchBarContent(true, false) }',
    '}',
    '@Builder',
    'private feiniuSearchBar() {',
    '  Row() { this.searchBarContent(false, true) }',
    '}',
    '@Builder',
    'private pageContent(showLegacyActionBar: boolean) {',
    '  Column() {',
    '    if (showLegacyActionBar) {',
    '      this.feiniuSearchBar()',
    '    } else {',
    '      this.nativeSearchForm()',
    '    }',
    '  }',
    "  .width('100%').height('100%').backgroundColor(" +
      "showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)",
    '}',
    'build() {',
    '  AppRouteDestination({',
    "    title: '搜索',",
    '    contentBuilder: () => { this.pageContent(false) },',
    '    legacyContentBuilder: () => { this.pageContent(true) }',
    '  })',
    '}'
  ].join('\n')
}

function validMediaRoute(name, embedded = false) {
  const build = embedded ? [
    'build() {',
    '  if (this.fromHome) {',
    '    this.embeddedContent()',
    '  } else {',
    '    AppRouteDestination({',
    '      title: this.ui.title,',
    '      contentBuilder: () => { this.pageContent(false) },',
    '      legacyContentBuilder: () => { this.pageContent(true) }',
    '    })',
    '  }',
    '}'
  ] : [
    'build() {',
    '  AppRouteDestination({',
    '    title: this.ui.title,',
    '    contentBuilder: () => { this.pageContent(false) },',
    '    legacyContentBuilder: () => { this.pageContent(true) }',
    '  })',
    '}'
  ]
  return [
    "import { AppRouteDestination } from '../../component/AppRouteDestination'",
    '@HMRouter({',
    `  pageUrl: RouterConsts.${name},`,
    '  useNavDst: true',
    '})',
    '@Builder',
    'private pageContent(showLegacyActionBar: boolean) {',
    '  Column() {',
    '    if (showLegacyActionBar) {',
    "      ActionBar({ title: 'Title' })",
    '    }',
    '  }',
    "  .width('100%').height('100%').backgroundColor(" +
      "showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)",
    '}',
    ...(embedded ? [
      '@Builder',
      'private embeddedContent() {',
      "  Column() {}.width('100%').height('100%').backgroundColor(this.pageBackground())",
      '}'
    ] : []),
    ...build
  ].join('\n')
}

function validManagementRoute(name) {
  return [
    "import { AppRouteDestination } from '../../component/AppRouteDestination'",
    '@HMRouter({',
    `  pageUrl: RouterConsts.${name},`,
    '  useNavDst: true',
    '})',
    '@Builder',
    'private pageContent(showLegacyActionBar: boolean) {',
    '  Column() {',
    '    if (showLegacyActionBar) {',
    "      ActionBar({ title: 'Title' })",
    '    }',
    '  }',
    "  .width('100%').height('100%').backgroundColor(showLegacyActionBar ? " +
      'AppThemeSurfaceResolver.routeBackground(this.themeStyle, this.nativeThemeAvailable) : Color.Transparent)',
    '}',
    'build() {',
    '  AppRouteDestination({',
    "    title: 'Title',",
    '    contentBuilder: () => { this.pageContent(false) },',
    '    legacyContentBuilder: () => { this.pageContent(true) }',
    '  })',
    '}'
  ].join('\n')
}

function validManagementUserDetailRoute() {
  return [
    "import { AppRouteDestination } from '../../component/AppRouteDestination'",
    '@HMRouter({',
    '  pageUrl: RouterConsts.ManagementUserDetailPage,',
    '  useNavDst: true',
    '})',
    'private attemptLeave(onLeave: () => void) { onLeave() }',
    'private handleRouteBack(): boolean {',
    '  this.attemptLeave(() => HMRouterMgr.pop())',
    '  return true',
    '}',
    '@Builder',
    'private pageContent(showLegacyActionBar: boolean) {',
    '  Column() {',
    '    if (showLegacyActionBar) {',
    "      ActionBar({ title: 'Title' })",
    '    }',
    '  }',
    "  .width('100%').height('100%').backgroundColor(showLegacyActionBar ? " +
      'AppThemeSurfaceResolver.routeBackground(this.themeStyle, this.nativeThemeAvailable) : Color.Transparent)',
    '}',
    'build() {',
    '  if (this.embedded) {',
    '    this.pageContent(false)',
    '  } else {',
    '    AppRouteDestination({',
    "      title: 'Title',",
    '      contentBuilder: () => { this.pageContent(false) },',
    '      legacyContentBuilder: () => { this.pageContent(true) },',
    '      beforeBack: () => this.handleRouteBack()',
    '    })',
    '  }',
    '}'
  ].join('\n')
}

function validAboutPublicContent() {
  return [
    "Image($r('app.media.app_icon'))",
    "Text($r('app.string.app_name'))",
    'if (this.versionName.length > 0) {',
    '  Text(this.versionName)',
    '}',
    "Text('HarmonyOS 平台的 Jellyfin 第三方客户端')",
    'this.relatedLinksGroup(true)',
    'this.licenseGroup(true)',
    'this.acknowledgementsGroup(true)'
  ]
}

function validAboutSectionBuilders() {
  return [
    '@Builder',
    'private relatedLinksContent(native: boolean) {',
    "  this.LinkItem('华为应用市场', this.appGalleryUrl, this.appGalleryUrl, native)",
    "  this.LinkItem('GitHub', this.githubUrl, this.githubUrl, native)",
    '}',
    '@Builder',
    'private relatedLinksGroup(native: boolean) {',
    '  this.relatedLinksContent(native)',
    '}',
    '@Builder',
    'private licenseContent(native: boolean) {',
    "  this.InfoItem('许可证', 'GPL-3.0-or-later', native)",
    '}',
    '@Builder',
    'private licenseGroup(native: boolean) {',
    '  this.licenseContent(native)',
    '}',
    '@Builder',
    'private acknowledgementsContent(native: boolean) {',
    "  this.InfoItem('Jellyfin', '感谢 Jellyfin 开源项目', native)",
    "  this.InfoItem('mpv', '感谢 mpv 开源项目', native)",
    "  this.InfoItem('OpenHarmony', '感谢 OpenHarmony 开源项目', native)",
    '}',
    '@Builder',
    'private acknowledgementsGroup(native: boolean) {',
    '  this.acknowledgementsContent(native)',
    '}'
  ].join('\n')
}

function validAboutPage() {
  return [
    "@State versionName: string = ''",
    'private getAppVersion() {',
    '  bundleManager.getBundleInfoForSelf(bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION)',
    '    .then((bundleInfo) => {',
    '      const resolvedVersionName = bundleInfo.versionName.trim()',
    '      if (resolvedVersionName.length > 0) {',
    '        this.versionName = `v${resolvedVersionName}`',
    '      }',
    '    })',
    '}',
    validAboutSectionBuilders(),
    validPage('AboutPage', 'bg_main', validAboutPublicContent())
  ].join('\n')
}

function validSources() {
  return new Map([
    [destinationPath, validDestination()],
    [playerPath, validPage('PlayerEnginePage', 'start_window_background')],
    [aboutPath, validAboutPage()],
    [searchPath, validSearchPage()],
    [videoListPath, validMediaRoute('VideoListPage', true)],
    [favoriteListPath, validMediaRoute('FavoriteListPage', true)],
    [liveTvChannelListPath, validMediaRoute('LiveTvChannelListPage')],
    [managementDashboardPath, validManagementRoute('ManagementDashboardPage')],
    [managementUsersPath, validManagementRoute('ManagementUsersPage')],
    [managementSessionDetailPath, validManagementRoute('ManagementSessionDetailPage')],
    [managementTaskDetailPath, validManagementRoute('ManagementTaskDetailPage')],
    [managementUserCreatePath, validManagementRoute('ManagementUserCreatePage')],
    [managementUserDetailPath, validManagementUserDetailRoute()],
    [minePath, [
      'HMRouterMgr.to(RouterConsts.PlayerEnginePage).push()',
      'HMRouterMgr.push({ pageUrl: RouterConsts.AboutPage })'
    ].join('\n')]
  ])
}

test('accepts shared native and legacy route ownership', () => {
  assert.doesNotThrow(() => validateNativeRouteDestinations(validSources()))
})

test('keeps the HDS destination stable when the active theme changes', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('return HdsUiCapability.supportsNativeTheme()',
      'return this.appUIState.themeStyle === ThemeStyle.Native && HdsUiCapability.supportsNativeTheme()'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /keep one HDS destination stable across Native and Feiniu themes/
  )
})

test('requires Native chrome to be gated by both theme and API capability', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('return this.appUIState.themeStyle === ThemeStyle.Native && HdsUiCapability.supportsNativeTheme()',
      'return HdsUiCapability.supportsNativeTheme()'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /Native chrome must require both Native theme and API 26 capability/
  )
})

test('derives the workspace root from the verifier location', () => {
  assert.equal(defaultWorkspaceRoot().endsWith('/ohosApp'), true)
})

test('rejects a route that lets HMRouter wrap another destination', () => {
  const sources = validSources()
  sources.set(playerPath, sources.get(playerPath).replace('  useNavDst: true\n', ''))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /opt out of HMRouter NavDestination wrapping/
  )
})

test('rejects a custom ActionBar in the native destination', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('    this.contentBuilder()',
      "    ActionBar({ title: 'Wrong' })\n    this.contentBuilder()"))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must not construct the custom ActionBar/
  )
})

test('rejects a standard destination in the Native branch', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('  HdsNavDestination() {', '  NavDestination() {'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /keep exactly one HdsNavDestination root/
  )
})

test('rejects a second destination inside the Native content branch', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('    this.contentBuilder()', '    NavDestination() {}\n    this.contentBuilder()'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /Native HDS content must use only the Native page builder/
  )
})

test('rejects an HDS destination nested inside the Feiniu HDS content branch', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('    this.legacyContentBuilder()',
      '    HdsNavDestination() { this.legacyContentBuilder() }'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /Feiniu HDS content must use only the legacy page builder/
  )
})

test('rejects Native route content that no longer fills the destination body', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace("    .width('100%')\n    .height('100%')", "    .width('100%')\n    .height('90%')"))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /Native HDS destination must preserve title, scroll, and HMRouter ownership/
  )
})

test('rejects hiding the system title bar in the Native route', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('.hideTitleBar(!this.titleBarVisible || !this.usesNativeChrome())',
      '.hideTitleBar(true)'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /Native HDS destination must preserve title, scroll, and HMRouter ownership/
  )
})

test('requires immersive gradient blur for the Native HDS title', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('HdsScrollEffectType.IMMERSIVE_GRADIENT_BLUR', 'HdsScrollEffectType.COMMON_BLUR'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /keep immersive gradient blur from 8vp to 56vp/
  )
})

test('requires the HDS title component to leave component safe-area avoidance disabled', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('enableComponentSafeArea: false', 'enableComponentSafeArea: true'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /Native HDS title must own its system safe-area layout/
  )
})

test('requires the 8vp Native title blur start threshold', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('blurEffectiveStartOffset: LengthMetrics.vp(8)',
      'blurEffectiveStartOffset: LengthMetrics.vp(0)'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /keep immersive gradient blur from 8vp to 56vp/
  )
})

test('requires the 56vp Native title blur end threshold', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('blurEffectiveEndOffset: LengthMetrics.vp(56)',
      'blurEffectiveEndOffset: LengthMetrics.vp(8)'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /keep immersive gradient blur from 8vp to 56vp/
  )
})

test('requires the system scrolled Native HDS title background', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace("backgroundColor: $r('sys.color.ohos_id_color_titlebar_bg'),",
      "backgroundColor: $r('sys.color.ohos_id_color_titlebar_bg_transparent'),"))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must use transparent original and system scrolled backgrounds/
  )
})

test('rejects a non-transparent original Native HDS title background', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('sys.color.ohos_id_color_titlebar_bg_transparent',
      'sys.color.ohos_id_color_titlebar_bg'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must use transparent original and system scrolled backgrounds/
  )
})

test('requires legible Native HDS title chrome colors', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace("mainTitleColor: $r('app.color.text_primary')",
      "mainTitleColor: $r('app.color.on_primary')"))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /title chrome must remain legible in light and dark modes/
  )
})

test('requires a theme-resolved destination background', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace(
      '    .backgroundColor(AppThemeSurfaceResolver.routeBackground(\n' +
      '      this.appUIState.themeStyle,\n' +
      '      HdsUiCapability.supportsNativeTheme()\n' +
      '    ))',
      '    .backgroundColor(Color.Transparent)'
    ))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /keep the Native route transparent and preserve the legacy background/
  )
})

test('requires the immersive material type for the Native HDS title', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('hdsMaterial.MaterialType.IMMERSIVE', 'hdsMaterial.MaterialType.NONE'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /keep the adaptive immersive material/
  )
})

test('requires the adaptive material level for the Native HDS title', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('hdsMaterial.MaterialLevel.ADAPTIVE', 'hdsMaterial.MaterialLevel.THIN'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /keep the adaptive immersive material/
  )
})

test('requires conditional scroll binding for the visible Native title', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('.bindToScrollable(this.titleBarVisible && this.usesNativeChrome() ? this.scrollControllers : [])',
      '.bindToScrollable(this.scrollControllers)'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /Native HDS destination must preserve title, scroll, and HMRouter ownership/
  )
})

test('rejects a shared destination that cannot hide the root back button', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('.hideBackButton(!this.backButtonVisible)', '.hideBackButton(false)'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /Native HDS destination must preserve title, scroll, and HMRouter ownership/
  )
})

test('rejects an outer layout wrapper around the Native destination', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('  HdsNavDestination() {', '  Column() {}\n  HdsNavDestination() {'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must be the direct Native route root/
  )
})

test('rejects manual safeTop padding outside the system title', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace(
      '    .ignoreLayoutSafeArea(',
      '    .padding({ top: this.appUIState.safeTop })\n    .ignoreLayoutSafeArea('
    ))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must not pad content away from the HDS title material/
  )
})

test('rejects a manual Native title spacer', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('    this.contentBuilder()', '    Blank().height(24)\n    this.contentBuilder()'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must not add a second title or safe-area inset/
  )
})

test('requires the Native HDS title to own system safe-area layout', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('avoidLayoutSafeArea: true', 'avoidLayoutSafeArea: false'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must own its system safe-area layout/
  )
})

test('rejects safeAreaPadding on the shared destination', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace(
      '    .ignoreLayoutSafeArea(',
      '    .safeAreaPadding({ top: this.appUIState.safeTop })\n    .ignoreLayoutSafeArea('
    ))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must not pad content away from the HDS title material/
  )
})

test('keeps STANDARD title behavior in the hidden legacy branch', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace(': BarStyle.STANDARD', ': BarStyle.STACK'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /legacy NavDestination must preserve HMRouter ownership and hidden title behavior/
  )
})

test('requires Native HDS SYSTEM TOP and BOTTOM expansion', () => {
  for (const incompleteEdges of ['[]', '[LayoutSafeAreaEdge.BOTTOM]', '[LayoutSafeAreaEdge.TOP]']) {
    const sources = validSources()
    sources.set(destinationPath, sources.get(destinationPath)
      .replace('[LayoutSafeAreaEdge.TOP, LayoutSafeAreaEdge.BOTTOM]', incompleteEdges))
    assert.throws(
      () => validateNativeRouteDestinations(sources),
      /extend through system TOP and BOTTOM without shifting Feiniu content/
    )
  }
})

test('requires the legacy destination to preserve the system bottom safe area', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('[SafeAreaEdge.BOTTOM]', '[]'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /preserve the system bottom safe area/
  )
})

test('rejects HDS construction in an individual route page', () => {
  const sources = validSources()
  sources.set(aboutPath, sources.get(aboutPath) + '\nHdsNavDestination() {}')
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must not construct HDS outside the shared destination/
  )
})

test('rejects native title ownership leaking into legacy content', () => {
  const sources = validSources()
  sources.set(playerPath, sources.get(playerPath)
    .replace('this.pageContent(false)', 'this.pageContent(true)'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /separate native and legacy title ownership/
  )
})

test('rejects an opaque root background in Native route content', () => {
  const sources = validSources()
  sources.set(playerPath, sources.get(playerPath)
    .replace("showLegacyActionBar ? $r('app.color.start_window_background') : Color.Transparent",
      "$r('app.color.start_window_background')"))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /route root must fill its host, be transparent in Native, and preserve its legacy background/
  )
})

test('rejects a route root that no longer fills the content host', () => {
  const sources = validSources()
  sources.set(playerPath, sources.get(playerPath)
    .replace(".width('100%').height('100%').backgroundColor", ".width('100%').backgroundColor"))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /route root must fill its host, be transparent in Native, and preserve its legacy background/
  )
})

test('rejects changing the About legacy route background', () => {
  const sources = validSources()
  sources.set(aboutPath, sources.get(aboutPath)
    .replace("$r('app.color.bg_main')", "$r('app.color.start_window_background')"))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /route root must fill its host, be transparent in Native, and preserve its legacy background/
  )
})

test('rejects internal development copy on the About page', () => {
  const sources = validSources()
  sources.set(aboutPath, sources.get(aboutPath)
    .replace("Text('HarmonyOS 平台的 Jellyfin 第三方客户端')",
      "Text('HarmonyOS 平台的 Jellyfin 第三方客户端')\n  this.SectionTitle('2.0 方向')"))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must not expose internal roadmap, status, or feature-list copy/
  )
})

test('rejects a fake About-page version fallback', () => {
  const sources = validSources()
  sources.set(aboutPath, sources.get(aboutPath)
    .replace("@State versionName: string = ''", "@State versionName: string = 'v1.0.0'"))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must start empty instead of showing a fake fallback/
  )
})

test('rejects an About-page version that no longer comes from bundle info', () => {
  const sources = validSources()
  sources.set(aboutPath, sources.get(aboutPath)
    .replace('bundleManager.getBundleInfoForSelf', 'loadVersion'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /version must come from bundle info/
  )
})

test('rejects rendering the About-page version before bundle info succeeds', () => {
  const sources = validSources()
  sources.set(aboutPath, sources.get(aboutPath)
    .replace('if (this.versionName.length > 0) {\n  Text(this.versionName)\n}', 'Text(this.versionName)'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must hide the version until bundle info succeeds/
  )
})

test('rejects restoring the retired About-page website', () => {
  const sources = validSources()
  sources.set(aboutPath, sources.get(aboutPath) +
    "\nprivate readonly websiteUrl = 'https://mafei.hmos.site/'\n" +
    "this.LinkItem('官网', this.websiteUrl, this.websiteUrl)")
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must not expose the retired website link/
  )
})

test('rejects removing a public About-page link', () => {
  const sources = validSources()
  sources.set(aboutPath, sources.get(aboutPath)
    .replace("  this.LinkItem('华为应用市场', this.appGalleryUrl, this.appGalleryUrl, native)\n", ''))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must keep only its public identity, links, license, and acknowledgements/
  )
})

test('rejects a public About section helper that pageContent no longer renders', () => {
  const sources = validSources()
  sources.set(aboutPath, sources.get(aboutPath)
    .replace('this.licenseGroup(true)\n', ''))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must render each public section exactly once from pageContent/
  )
})

test('rejects an explicit pop layered on top of the system back action', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('    this.contentBuilder()',
      '    this.contentBuilder()\n    Button().onClick(() => HMRouterMgr.pop())'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must use the destination system back action exactly once/
  )
})

test('rejects a Native HDS back handler that bypasses NavDestinationHelper', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('.onBackPressed(() => this.handleBackPressed())', '.onBackPressed(() => true)'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /Native HDS destination must preserve title, scroll, and HMRouter ownership/
  )
})

test('rejects a custom back hook that no longer falls through to NavDestinationHelper', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('return this.beforeBack() || this.helper.onBackPressed()', 'return this.beforeBack()'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /custom back hook must fall through to the HMRouter helper/
  )
})

test('rejects NavDestinationHelper lifecycle ownership in the Native content branch', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('    this.contentBuilder()',
      '    this.contentBuilder()\n    this.helper.onReady(ctx)'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /HDS content branches must not own route lifecycle, gestures, transition, or back events/
  )
})

test('rejects a missing generated hook on the legacy route root', () => {
  const sources = validSources()
  sources.set(destinationPath, sources.get(destinationPath)
    .replace('    .onNewParam((param) => { this.helper.onNewParam(param) })\n', ''))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /legacy NavDestination is missing generated HMRouter hook: onNewParam/
  )
})

test('rejects lifecycle contracts outside the first native migration boundary', () => {
  const sources = validSources()
  sources.set(aboutPath, sources.get(aboutPath).replace('  useNavDst: true',
    "  useNavDst: true,\n  lifecycle: 'aboutLifecycle'"))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /does not cover animator, dialog, singleton, custom lifecycle, result, or new-param contracts/
  )
})

test('rejects an entry point that stops using HMRouter push', () => {
  const sources = validSources()
  sources.set(minePath, sources.get(minePath)
    .replace('HMRouterMgr.to(RouterConsts.PlayerEnginePage).push()', 'openPlayerEngine()'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /player engine entry must keep HMRouter push behavior/
  )
})

test('rejects routing the Native search branch through the legacy cancel row', () => {
  const sources = validSources()
  sources.set(searchPath, sources.get(searchPath)
    .replace('this.nativeSearchForm()', 'this.feiniuSearchBar()'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /Search Native content must use the system title/
  )
})

test('rejects a migrated media route that still paints ActionBar outside the legacy guard', () => {
  const sources = validSources()
  sources.set(liveTvChannelListPath, sources.get(liveTvChannelListPath)
    .replace('if (showLegacyActionBar)', 'if (false)'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /legacy ActionBar must remain isolated behind the legacy flag/
  )
})

test('rejects wrapping embedded home content in a route destination', () => {
  const sources = validSources()
  sources.set(videoListPath, sources.get(videoListPath)
    .replace('  if (this.fromHome) {', '  if (false) {'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /embedded home content must bypass the route destination/
  )
})

test('rejects a management route that paints its resolver background in Native content', () => {
  const sources = validSources()
  sources.set(managementDashboardPath, sources.get(managementDashboardPath)
    .replace('showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground',
      'true ? AppThemeSurfaceResolver.routeBackground'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /route root must fill its host, be transparent in Native, and preserve its legacy background/
  )
})

test('rejects wrapping embedded management detail in a route destination', () => {
  const sources = validSources()
  sources.set(managementUserDetailPath, sources.get(managementUserDetailPath)
    .replace('if (this.embedded)', 'if (false)'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /embedded management detail must bypass the route destination/
  )
})

test('requires management detail to wire the system back action through attemptLeave', () => {
  const sources = validSources()
  sources.set(managementUserDetailPath, sources.get(managementUserDetailPath)
    .replace('beforeBack: () => this.handleRouteBack()', 'beforeBack: () => false'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must intercept the system back action before leaving/
  )
})

test('requires management detail to consume back until unsaved handling completes', () => {
  const sources = validSources()
  sources.set(managementUserDetailPath, sources.get(managementUserDetailPath)
    .replace('return true', 'return false'))
  assert.throws(
    () => validateNativeRouteDestinations(sources),
    /must consume system back until attemptLeave completes/
  )
})
