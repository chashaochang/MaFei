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
      '  private pxToVp(windowClass, pixels: number): number {',
      '    if (this.contentUIContextReady) {',
      '      return windowClass.getUIContext().px2vp(pixels)',
      '    }',
      '    return pixels / this.resolveWindowDensity(windowClass)',
      '  }',
      '  private loadContent(windowStage, windowClass): void {',
      '    windowStage.loadContent("pages/Index", () => {',
      '      this.contentUIContextReady = true',
      '      this.refreshWindowMetrics(windowClass)',
      '    })',
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
      '}'
    ].join('\n')],
    [tabletShellPaths.homeScreen, [
      'class HomeScreen {',
      '  private syncPadShellVisibility(): void {',
      '    const shell = this.resolveShell()',
      '    this.ui.isMenuModalVisible = false',
      '    this.ui.isLeftSidebarVisible = shell === HomeShellKind.LargeSidebar',
      '  }',
      '  private legacyPadContent(shell: HomeShellKind) {',
      '    if (shell === HomeShellKind.LargeSidebar) {',
      '      Blank().width(this.ui.isLeftSidebarVisible ? 252 : 12)',
      '    }',
      '  }',
      '  private padChromeBuilder(shell: HomeShellKind) {',
      '    onMenuButtonClick: () => {',
      '      if (shell === HomeShellKind.LargeSidebar) {',
      '        this.ui.isLeftSidebarVisible = true',
      '      } else {',
      '        this.ui.isMenuModalVisible = true',
      '      }',
      '    }',
      '    if (this.ui.isMenuModalVisible && shell === HomeShellKind.MediumDrawer) {',
      '      this.menuModalBuilder()',
      '    }',
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

test('rejects MD content width reservation', () => {
  const sources = validSources()
  sources.set(tabletShellPaths.homeScreen,
    sources.get(tabletShellPaths.homeScreen)
      .replace('if (shell === HomeShellKind.LargeSidebar) {',
        'if (shell === HomeShellKind.MediumDrawer || shell === HomeShellKind.LargeSidebar) {'))
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
