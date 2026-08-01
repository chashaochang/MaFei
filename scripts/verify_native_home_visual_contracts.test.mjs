import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import { validateNativeHomeVisualContracts } from './verify_native_home_visual_contracts.mjs'

const paths = {
  resolver: 'entry/src/main/ets/theme/AppThemeSurfaceResolver.ets',
  homeTab: 'entry/src/main/ets/features/home/hometab/HomeTab.ets',
  latestSection: 'entry/src/main/ets/features/home/hometab/HomeLatestMediaSection.ets',
  chipSelector: 'entry/src/main/ets/features/home/hometab/HomeLibraryChipSelector.ets',
  homeViewModel: 'entry/src/main/ets/features/home/hometab/HomeViewModel.ets',
  routeDestination: 'entry/src/main/ets/component/AppRouteDestination.ets',
  appUIState: 'entry/src/main/ets/entity/AppUIState.ets',
  events: 'entry/src/main/ets/events/Events.ets',
  indexPage: 'entry/src/main/ets/features/splash/IndexPage.ets'
}

const workspaceRoot = dirname(dirname(fileURLToPath(import.meta.url)))
const current = Object.fromEntries(Object.entries(paths).map(([key, path]) => [
  key,
  readFileSync(resolve(workspaceRoot, path), 'utf8')
]))

function sources(overrides = {}) {
  const values = { ...current, ...overrides }
  return new Map(Object.entries(paths).map(([key, path]) => [path, values[key]]))
}

test('accepts the single-instance sticky home library header', () => {
  assert.doesNotThrow(() => validateNativeHomeVisualContracts(sources()))
})

test('rejects a second selector in AppRouteDestination', () => {
  const routeDestination = current.routeDestination + '\nHomeLibraryChipSelector({})'
  assert.throws(() => validateNativeHomeVisualContracts(sources({ routeDestination })),
    /second home library Chip selector/)
})

test('rejects the former selection event bridge', () => {
  const events = current.events + '\nexport const HomeLatestMediaSelectionEvent = "x"'
  assert.throws(() => validateNativeHomeVisualContracts(sources({ events })),
    /duplicate-instance bridge/)
})

test('rejects duplicated selector state in AppUIState', () => {
  const appUIState = current.appUIState + '\nrootNavigationHomeLibrarySelectedId: string = ""'
  assert.throws(() => validateNativeHomeVisualContracts(sources({ appUIState })),
    /duplicate-instance bridge/)
})

test('rejects more than one selector instance in HomeTab', () => {
  const homeTab = current.homeTab + '\nHomeLibraryChipSelector({})'
  assert.throws(() => validateNativeHomeVisualContracts(sources({ homeTab })),
    /exactly one/)
})

test('rejects moving the selector back into the media grid component', () => {
  const latestSection = current.latestSection + '\nHomeLibraryChipSelector({})'
  assert.throws(() => validateNativeHomeVisualContracts(sources({ latestSection })),
    /render only the selected media grid/)
})

test('rejects removing the real ListItemGroup sticky header', () => {
  const homeTab = current.homeTab.replace('ListItemGroup({ header: this.latestMediaStickyHeader })',
    'ListItemGroup()')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ homeTab })),
    /real ListItemGroup sticky header/)
})

test('rejects material or blur on the whole sticky row', () => {
  const homeTab = current.homeTab.replace('.backgroundColor(Color.Transparent)',
    '.backgroundBlurStyle(BlurStyle.COMPONENT_REGULAR)')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ homeTab })),
    /transparent lane|whole Chip header row/)
})

test('rejects removing the extra gap beside search and live menus', () => {
  const homeTab = current.homeTab.replace('HOME_LIBRARY_HEADER_EXTRA_MENU_GAP : 0', '0 : 0')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ homeTab })),
    /transparent lane/)
})

test('rejects reserving search width before the header sticks', () => {
  const homeTab = current.homeTab.replace(
    "if (!this.latestChipStripMenuReserved) {\n      return 0\n    }", '')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ homeTab })),
    /reserve only the menu width/)
})

test('rejects reserving menu width later than one title-bar height', () => {
  const homeTab = current.homeTab.replace(
    'globalY <= UIConstants.ACTION_BAR_HEIGHT', 'globalY <= 12')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ homeTab })),
    /real sticky state/)
})

test('rejects cumulative Hero progress across previous segments', () => {
  const homeTab = current.homeTab.replace(
    'return index === this.heroIndex ? this.heroProgress : 0',
    'if (index < this.heroIndex) { return 100 }\n    return index === this.heroIndex ? this.heroProgress : 0')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ homeTab })),
    /only the current segment/)
})

test('rejects low-contrast system secondary color for the Hero progress track', () => {
  const homeTab = current.homeTab.replace(
    '.backgroundColor(this.heroProgressTrackColor())',
    ".backgroundColor($r('sys.color.icon_secondary'))")
  assert.throws(() => validateNativeHomeVisualContracts(sources({ homeTab })),
    /active-to-track contrast/)
})

test('rejects removing the sticky vertical alignment offset', () => {
  const homeTab = current.homeTab.replace('HOME_LIBRARY_HEADER_ALIGNMENT_OFFSET : 0', '0 : 0')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ homeTab })),
    /vertical alignment offset/)
})

test('rejects opaque white selected Chip styling', () => {
  const chipSelector = current.chipSelector.replace('ColorMetrics.rgba(255, 255, 255, 0.82)',
    'ColorMetrics.rgba(255, 255, 255, 1)')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ chipSelector })),
    /translucent-white/)
})

test('rejects removing immersive material from unselected Chips', () => {
  const chipSelector = current.chipSelector.replace('AppThemeMaterialRole.HomeLibraryChip)',
    'AppThemeMaterialRole.HomeLibraryChipSelected)')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ chipSelector })),
    /Every Chip/)
})

test('rejects custom tint parameters on system Chip material', () => {
  const resolver = current.resolver.replace(
    'AppThemeSurfaceResolver.homeLibraryChip = new uiMaterial.ImmersiveMaterial({\n          interactive: true',
    "AppThemeSurfaceResolver.homeLibraryChip = new uiMaterial.ImmersiveMaterial({\n          interactive: true,\n          materialColor: '#FFFFFF'")
  assert.throws(() => validateNativeHomeVisualContracts(sources({ resolver })),
    /default system immersive material/)
})

test('rejects close icons on home library Chips', () => {
  const chipSelector = current.chipSelector.replace('allowClose: false', 'allowClose: true')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ chipSelector })),
    /Every Chip/)
})

test('rejects title-bar blur over the pinned header', () => {
  const routeDestination = current.routeDestination.replace(
    'enableScrollEffect: !this.appUIState.rootNavigationHomeLibraryPinned',
    'enableScrollEffect: true')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ routeDestination })),
    /stop blur/)
})

test('rejects disabling menu material while the header is pinned', () => {
  const routeDestination = current.routeDestination.replace(
    'materialType: this.titleMaterialFollowsSystem ?',
    'materialType: this.appUIState.rootNavigationHomeLibraryPinned ? hdsMaterial.MaterialType.NONE : this.titleMaterialFollowsSystem ?')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ routeDestination })),
    /without disabling menu material/)
})

test('rejects the bitmap search menu icon', () => {
  const indexPage = current.indexPage.replace(
    "icon: $r('sys.symbol.magnifyingglass')",
    "icon: $r('app.media.ic_search')")
  assert.throws(() => validateNativeHomeVisualContracts(sources({ indexPage })),
    /sharp system vector symbol/)
})

test('rejects a hard-clipped right edge beside search', () => {
  const chipSelector = current.chipSelector.replace(
    '.fadingEdge(this.fadeRightEdge, {',
    '.fadingEdge(false, {')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ chipSelector })),
    /horizontal ChipV2 group/)
})

test('rejects a pinned header trapped below the HDS touch layer', () => {
  const homeTab = current.homeTab
    .replace('.zIndex(this.latestChipStripPinned ? 10 : 0)', '.zIndex(0)')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ homeTab })),
    /transparent lane/)
})

test('rejects keeping the HDS title-bar touch layer while pinned', () => {
  const routeDestination = current.routeDestination.replace(
    ' ||\n      this.appUIState.rootNavigationHomeLibraryPinned', '')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ routeDestination })),
    /touch interception layer/)
})

test('rejects removing selected-Chip visibility scrolling', () => {
  const chipSelector = current.chipSelector.replace('ScrollAlign.CENTER', 'ScrollAlign.START')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ chipSelector })),
    /visible horizontal area/)
})

test('rejects scrolling the main page when switching libraries', () => {
  const homeTab = current.homeTab.replace('this.vm.selectLatestMedia(id)',
    'this.vm.selectLatestMedia(id)\n    this.contentScroller.scrollToIndex(0)')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ homeTab })),
    /update only the grid/)
})

test('rejects removing stable grid height while switching', () => {
  const latestSection = current.latestSection.replace(
    '.constraintSize({ minHeight: this.contentMinHeight })', '')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ latestSection })),
    /reserve stable height/)
})

test('rejects a horizontal poster list', () => {
  const latestSection = current.latestSection
    .replace('    Grid() {', '    List() {')
    .replace('.columnsTemplate(this.gridColumns())', '.listDirection(Axis.Horizontal)')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ latestSection })),
    /vertically expanding poster grid/)
})

test('rejects ungrouped recently-added requests', () => {
  const homeViewModel = current.homeViewModel.replace('groupItems: true', 'groupItems: false')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ homeViewModel })),
    /grouped latest-media semantics/)
})

test('rejects sharing the Feiniu canvas with Native', () => {
  const resolver = current.resolver.replace('native_canvas_background', 'bg_main')
  assert.throws(() => validateNativeHomeVisualContracts(sources({ resolver })),
    /dedicated adaptive background/)
})
