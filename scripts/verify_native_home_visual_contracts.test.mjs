import assert from 'node:assert/strict'
import test from 'node:test'
import { validateNativeHomeVisualContracts } from './verify_native_home_visual_contracts.mjs'

const SURFACE_RESOLVER = 'entry/src/main/ets/theme/AppThemeSurfaceResolver.ets'
const HOME_TAB = 'entry/src/main/ets/features/home/hometab/HomeTab.ets'

const validResolver = `
export class AppThemeSurfaceResolver {
  static appCanvasBackground(ready: boolean, effectiveTheme: ThemeStyle): ResourceColor {
    if (!ready) {
      return $r('app.color.start_window_background')
    }
    return effectiveTheme === ThemeStyle.Native ?
      $r('app.color.native_canvas_background') : $r('app.color.bg_main')
  }

  static material(role: AppThemeMaterialRole): uiMaterial.Material {
    if (role === AppThemeMaterialRole.ContentGroup) {
      return new uiMaterial.ImmersiveMaterial({
        materialColor: $r('sys.color.comp_background_primary')
      })
    }
    return uiMaterial.Material.empty
  }
}
`

const validHomeTab = `
export struct HomeTab {
  private useNativeSurface(): boolean {
    return true
  }

  private phoneContentTopInset(): number {
    return 80
  }

  private homeRefreshContent() {
    List() {
      if (this.vm.appUIState.currentBreakpoint.includes('s') &&
        (!this.useNativeSurface() || !this.showNativeHero())) {
        ListItem().height(this.phoneContentTopInset())
      }
    }
  }

  private nativeHomeHeroProgressIndicator() {
    Progress()
      .color($r('sys.color.icon_primary'))
      .backgroundColor($r('sys.color.icon_secondary'))
  }

  private nativeHomeHeroReadabilityScrim() {
    Column()
      .linearGradient({
        angle: 180,
        colors: [
          ['rgba(0,0,0,0.08)', 0.0],
          ['rgba(0,0,0,0.16)', 0.42],
          ['rgba(0,0,0,0.88)', 1.0]
        ]
      })
  }

  private nativeHomeHeroCanvasTransition() {
    Column()
      .width('100%')
      .height(96)
      .linearGradient({
        angle: 180,
        colors: [
          [Color.Transparent, 0.0],
          [$r('app.color.native_canvas_background'), 1.0]
        ]
      })
  }

  private nativeHomeHero(item: VideoItem) {
    Stack() {
      this.nativeHomeHeroReadabilityScrim()
      this.nativeHomeHeroCanvasTransition()
      Column({ space: 8 }) {}
    }
    .height(430)
  }

  private nativeMediaCount(type: BaseItemKind): string {
    if (type === BaseItemKind.Movie) {
      return this.ui.movieCount
    }
    if (type === BaseItemKind.TvProgram) {
      return this.ui.tvCount
    }
    return this.ui.totalCount
  }

  @Builder
  private nativeMediaNavigationItem(type: BaseItemKind) {
    Stack() {
      Text(this.nativeMediaCount(type))
    }
    .width(90)
    .height(60)
  }

  @Builder
  private nativeAllMediaCount() {
    Row() {
      this.nativeMediaNavigationItem(BaseItemKind.Video)
      this.nativeMediaNavigationItem(BaseItemKind.Movie)
      this.nativeMediaNavigationItem(BaseItemKind.TvProgram)
    }
    .width(286)
    .height(60)
  }
}
`

function sources(resolver = validResolver, homeTab = validHomeTab) {
  return new Map([
    [SURFACE_RESOLVER, resolver],
    [HOME_TAB, homeTab]
  ])
}

test('accepts the Native adaptive canvas, white card, and reactive media entry contract', () => {
  assert.doesNotThrow(() => validateNativeHomeVisualContracts(sources()))
})

test('rejects sharing the Feiniu canvas with the Native theme', () => {
  const resolver = validResolver.replace('native_canvas_background', 'bg_main')
  assert.throws(() => validateNativeHomeVisualContracts(sources(resolver)), /dedicated adaptive background/)
})

test('rejects a transparent Native ContentGroup tint', () => {
  const resolver = validResolver.replace(
    "$r('sys.color.comp_background_primary')", 'Color.Transparent')
  assert.throws(() => validateNativeHomeVisualContracts(sources(resolver)), /primary card color/)
})

test('rejects passing reactive counts through the media Builder signature', () => {
  const homeTab = validHomeTab.replace(
    'nativeMediaNavigationItem(type: BaseItemKind)',
    'nativeMediaNavigationItem(count: string, type: BaseItemKind)')
  assert.throws(() => validateNativeHomeVisualContracts(sources(validResolver, homeTab)),
    /one stable discriminator/)
})

test('rejects media navigation that stops reading one count state', () => {
  const homeTab = validHomeTab.replace('return this.ui.movieCount', "return '--'")
  assert.throws(() => validateNativeHomeVisualContracts(sources(validResolver, homeTab)), /movieCount/)
})

test('rejects an unstable media group size', () => {
  const homeTab = validHomeTab.replace('.width(286)', ".width('100%')")
  assert.throws(() => validateNativeHomeVisualContracts(sources(validResolver, homeTab)), /286x60/)
})

test('rejects a Hero canvas transition that does not end in the adaptive canvas', () => {
  const homeTab = validHomeTab.replace(
    "$r('app.color.native_canvas_background')", "'#F1F2F4'")
  assert.throws(
    () => validateNativeHomeVisualContracts(sources(validResolver, homeTab)),
    /adaptive canvas color/
  )
})

test('rejects a Hero that omits the separate canvas transition layer', () => {
  const homeTab = validHomeTab.replace('      this.nativeHomeHeroCanvasTransition()\n', '')
  assert.throws(
    () => validateNativeHomeVisualContracts(sources(validResolver, homeTab)),
    /separate readability and canvas transition layers/
  )
})

test('rejects fixed white Hero progress colors', () => {
  const homeTab = validHomeTab
    .replace("$r('sys.color.icon_primary')", 'Color.White')
    .replace("$r('sys.color.icon_secondary')", "'rgba(255,255,255,0.30)'")
  assert.throws(
    () => validateNativeHomeVisualContracts(sources(validResolver, homeTab)),
    /adaptive system colors/
  )
})

test('rejects removing the Native no-Hero list spacer from the stable content branch', () => {
  const homeTab = validHomeTab.replace(
    "(!this.useNativeSurface() || !this.showNativeHero())",
    '!this.useNativeSurface()'
  )
  assert.throws(
    () => validateNativeHomeVisualContracts(sources(validResolver, homeTab)),
    /HomeTab must own the Native empty-Hero top inset/
  )
})
