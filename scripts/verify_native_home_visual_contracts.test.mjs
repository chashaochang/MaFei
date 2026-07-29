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
      $r('sys.color.comp_background_gray') : $r('app.color.bg_main')
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

test('accepts the Native light canvas, white card, and reactive media entry contract', () => {
  assert.doesNotThrow(() => validateNativeHomeVisualContracts(sources()))
})

test('rejects a white Native app canvas', () => {
  const resolver = validResolver.replace('comp_background_gray', 'comp_background_primary')
  assert.throws(() => validateNativeHomeVisualContracts(sources(resolver)), /system gray background/)
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
