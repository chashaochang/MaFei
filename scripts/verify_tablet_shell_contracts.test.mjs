import assert from 'node:assert/strict'
import test from 'node:test'
import {
  tabletShellPaths,
  validateTabletShellContracts
} from './verify_tablet_shell_contracts.mjs'

function validSources() {
  return new Map([
    [tabletShellPaths.entryAbility, [
      'class EntryAbility {',
      '  private contentUIContextReady: boolean = false',
      '  private windowSizeChangeCallback = (size: window.Size): void => {',
      '    if (this.mainWindow) {',
      '      this.refreshWindowMetrics(this.mainWindow, size)',
      '    }',
      '  }',
      '  private pxToVp(windowClass, pixels: number): number {',
      '    if (this.contentUIContextReady) {',
      '      return windowClass.getUIContext().px2vp(pixels)',
      '    }',
      '    return pixels / this.resolveWindowDensity(windowClass)',
      '  }',
      '  private async initializeMainWindow(windowClass): Promise<boolean> {',
      '    this.refreshWindowMetrics(windowClass)',
      '    return true',
      '  }',
      '  private registerWindowMetricListeners(windowClass): void {',
      "    windowClass.on('windowSizeChange', this.windowSizeChangeCallback)",
      "    windowClass.on('avoidAreaChange', this.avoidAreaChangeCallback)",
      '  }',
      '  private loadContent(windowStage, windowClass): void {',
      '    windowStage.loadContent("pages/Index", () => {',
      '      this.contentUIContextReady = true',
      '      this.refreshWindowMetrics(windowClass)',
      '      this.registerWindowMetricListeners(windowClass)',
      '    })',
      '  }',
      '  onPortrait = (mediaQueryResult) => {',
      '    const mainWindow = this.mainWindow',
      '    this.refreshWindowMetrics(mainWindow)',
      '  }',
      '}'
    ].join('\n')],
    [tabletShellPaths.homeShellPolicy, [
      'class HomeShellPolicy {',
      '  static resolve(breakpoint, theme, nativeAvailable, isPortraitViewport) {',
      '    if (breakpoint === BreakpointTypeEnum.MD ||',
      '      (breakpoint === BreakpointTypeEnum.LG && isPortraitViewport)) {',
      '      return HomeShellKind.MediumDrawer',
      '    }',
      '    if (breakpoint === BreakpointTypeEnum.LG) {',
      '      return HomeShellKind.LargeSidebar',
      '    }',
      '  }',
      '  static usesOverlayDrawer(shell) {',
      '    return shell === HomeShellKind.MediumDrawer',
      '  }',
      '  static usesEmbeddedSidebar(shell) {',
      '    return shell === HomeShellKind.LargeSidebar',
      '  }',
      '}'
    ].join('\n')],
    [tabletShellPaths.homeScreen, [
      'class HomeScreen {',
      '  private syncPadShellVisibility(): void {',
      '    const shell = this.resolveShell()',
      '    this.ui.isMenuModalVisible = false',
      '    if (HomeShellPolicy.usesOverlayDrawer(shell)) {',
      '      this.ui.isLeftSidebarVisible = false',
      '    } else if (HomeShellPolicy.usesEmbeddedSidebar(shell)) {',
      '      this.ui.isLeftSidebarVisible = true',
      '    }',
      '  }',
      '  private legacyPadContent(shell: HomeShellKind) {',
      '    if (HomeShellPolicy.usesEmbeddedSidebar(shell)) {',
      '      Blank().width(this.ui.isLeftSidebarVisible ? 252 : 12)',
      '    }',
      '  }',
      '  private padChromeBuilder(shell: HomeShellKind) {',
      '    onMenuButtonClick: () => {',
      '      if (HomeShellPolicy.usesEmbeddedSidebar(shell)) {',
      '        this.ui.isLeftSidebarVisible = true',
      '      } else if (HomeShellPolicy.usesOverlayDrawer(shell)) {',
      '        this.ui.isMenuModalVisible = true',
      '      }',
      '    }',
      '    if (this.ui.isMenuModalVisible && HomeShellPolicy.usesOverlayDrawer(shell)) {',
      '      this.menuModalBuilder()',
      '    }',
      '  }',
      '}'
    ].join('\n')],
    [tabletShellPaths.padTopTab, [
      'class PadTopTab {',
      '  private nativeTabItem() {',
      '    AppThemeMaterialRole.HomeLibraryChipSelected',
      '  }',
      '  private feiniuTabItem() {',
      "    $r('app.color.native_fallback_home_library_chip_selected')",
      '  }',
      '}'
    ].join('\n')]
  ])
}

test('accepts the MD overlay and LG embedded sidebar contract', () => {
  assert.doesNotThrow(() => validateTabletShellContracts(validSources()))
})

test('rejects manual density as the authoritative post-content conversion', () => {
  const sources = validSources()
  sources.set(tabletShellPaths.entryAbility,
    sources.get(tabletShellPaths.entryAbility)
      .replace('return windowClass.getUIContext().px2vp(pixels)',
        'return pixels / this.resolveWindowDensity(windowClass)'))
  assert.throws(
    () => validateTabletShellContracts(sources),
    /UIContext px2vp/
  )
})

test('rejects a rotation callback that ignores the latest event size', () => {
  const sources = validSources()
  sources.set(tabletShellPaths.entryAbility,
    sources.get(tabletShellPaths.entryAbility)
      .replace('this.refreshWindowMetrics(this.mainWindow, size)',
        'this.refreshWindowMetrics(this.mainWindow)'))
  assert.throws(
    () => validateTabletShellContracts(sources),
    /latest event size/
  )
})

test('rejects window-size listeners registered before content load', () => {
  const sources = validSources()
  sources.set(tabletShellPaths.entryAbility,
    sources.get(tabletShellPaths.entryAbility)
      .replace('    this.refreshWindowMetrics(windowClass)\n    return true', [
        '    this.refreshWindowMetrics(windowClass)',
        "    windowClass.on('windowSizeChange', this.windowSizeChangeCallback)",
        '    return true'
      ].join('\n')))
  assert.throws(
    () => validateTabletShellContracts(sources),
    /before content load completes/
  )
})

test('rejects MD content width reservation', () => {
  const sources = validSources()
  sources.set(tabletShellPaths.homeScreen,
    sources.get(tabletShellPaths.homeScreen)
      .replace([
        'if (HomeShellPolicy.usesEmbeddedSidebar(shell)) {',
        '      Blank().width(this.ui.isLeftSidebarVisible ? 252 : 12)'
      ].join('\n'), [
        'if (HomeShellPolicy.usesOverlayDrawer(shell) || HomeShellPolicy.usesEmbeddedSidebar(shell)) {',
        '      Blank().width(this.ui.isLeftSidebarVisible ? 252 : 12)'
      ].join('\n')))
  assert.throws(
    () => validateTabletShellContracts(sources),
    /only the LG shell may reserve embedded sidebar width/
  )
})

test('rejects opening the embedded sidebar from the MD menu button', () => {
  const sources = validSources()
  sources.set(tabletShellPaths.homeScreen,
    sources.get(tabletShellPaths.homeScreen)
      .replace('this.ui.isMenuModalVisible = true', 'this.ui.isLeftSidebarVisible = true'))
  assert.throws(
    () => validateTabletShellContracts(sources),
    /overlay drawer otherwise/
  )
})
