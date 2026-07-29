import assert from 'node:assert/strict'
import test from 'node:test'
import {
  defaultWorkspaceRoot,
  validateNativeThemePageContracts
} from './verify_native_theme_page_contracts.mjs'

const pagePath = 'entry/src/main/ets/features/sample/SamplePage.ets'
const overlayPath = 'entry/src/main/ets/features/sample/SampleOverlay.ets'
const globalPath = 'entry/src/main/ets/component/GlobalMaterial.ets'
const sharedPath = 'entry/src/main/ets/component/SharedCard.ets'

function validPage() {
  return [
    '@ComponentV2',
    'struct SamplePage {',
    '  private useNativeSurface(): boolean { return true }',
    '  @Builder',
    '  private nativeCard() {',
    '    Column() {}',
    '      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())',
    '  }',
    '  @Builder',
    '  private nativeChip() {',
    "    Text('chip').backgroundColor(Color.Transparent)",
    '  }',
    '  @Builder',
    '  private feiniuCard() {',
    "    Column() {}.backgroundColor($r('app.color.bg_1'))",
    '  }',
    '  private cardColor(): ResourceColor {',
    "    return this.useNativeSurface() ? Color.Transparent : $r('app.color.component_bg')",
    '  }',
    '  private pageBackground(): ResourceColor {',
    '    return AppThemeSurfaceResolver.routeBackground(this.themeStyle, true)',
    '  }',
    '  private openDangerousDialog(): void {',
    "    this.showDialog({ backgroundColor: $r('app.color.bg_1') })",
    '  }',
    '  @Builder',
    '  private mediaPlaceholder() {',
    "    Image('poster').backgroundColor($r('app.color.bg_2'))",
    '  }',
    '  @Builder',
    '  private playerSurface() {',
    "    Stack() {}.backgroundColor($r('app.color.bg_main'))",
    '  }',
    '  build() {',
    '    Column() {',
    '      this.nativeCard()',
    '    }',
    '    .backgroundColor(this.pageBackground())',
    '  }',
    '}'
  ].join('\n')
}

function validOverlay() {
  return [
    '@ComponentV2',
    'struct SampleOverlay {',
    '  private openSheet(): void {',
    '    this.bindSheet({',
    '      systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()',
    '    })',
    '  }',
    '}'
  ].join('\n')
}

function validGlobalBuilder() {
  return [
    '@Builder',
    'function nativeGlobalMaterial() {',
    '  Column() {}',
    '    .borderRadius(16)',
    '    .clip(true)',
    '    .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating))',
    '}'
  ].join('\n')
}

function validSources() {
  return new Map([
    [pagePath, validPage()],
    [overlayPath, validOverlay()],
    [globalPath, validGlobalBuilder()]
  ])
}

function validContract() {
  return {
    attributeOwners: [
      { path: globalPath, component: '@global', method: 'nativeGlobalMaterial' }
    ],
    optionOwners: [
      { path: overlayPath, component: 'SampleOverlay', method: 'openSheet' }
    ],
    plainSurfaceOwners: [
      { path: pagePath, component: 'SamplePage', method: 'nativeCard', kind: 'content-group' },
      { path: pagePath, component: 'SamplePage', method: 'nativeChip', kind: 'borderless-control' }
    ],
    transparentNativeRoots: [
      { path: pagePath, component: 'SamplePage', method: 'build' }
    ],
    visualAuditPaths: [],
    opaqueBackgroundExceptions: [
      { path: pagePath, component: 'SamplePage', method: 'playerSurface', kind: 'player' },
      { path: pagePath, component: 'SamplePage', method: 'mediaPlaceholder', kind: 'media' },
      { path: pagePath, component: 'SamplePage', method: 'openDangerousDialog', kind: 'dangerous-dialog' }
    ]
  }
}

test('accepts legal Native material, plain surfaces, transparent roots, exact exceptions, and Feiniu backgrounds', () => {
  assert.doesNotThrow(() => validateNativeThemePageContracts(validSources(), validContract()))
})

test('derives the default workspace root from the verifier location', () => {
  assert.equal(defaultWorkspaceRoot().endsWith('/ohosApp'), true)
})

for (const conflict of [
  'background',
  'backgroundColor',
  'backgroundBlurStyle',
  'backgroundEffect',
  'blur',
  'border',
  'borderWidth',
  'borderColor',
  'shadow',
  'visualEffect'
]) {
  test('rejects .' + conflict + ' in the nearest material @Builder', () => {
    const sources = validSources()
    sources.set(globalPath, validGlobalBuilder().replace(
      '    .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating))',
      '      .' + conflict + "($r('app.color.bg_1'))\n" +
        '    .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating))'
    ))
    assert.throws(
      () => validateNativeThemePageContracts(sources, validContract()),
      new RegExp('Builder also owns \\.' + conflict)
    )
  })
}

test('rejects a conditional material attribute instead of hiding material ownership in an expression', () => {
  const sources = validSources()
  sources.set(globalPath, validGlobalBuilder().replace(
    'AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)',
    'this.useNativeSurface() ? AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating) : undefined'
  ))
  assert.throws(
    () => validateNativeThemePageContracts(sources, validContract()),
    /conditional \.systemMaterial is forbidden/
  )
})

test('rejects a conditional systemMaterial option', () => {
  const sources = validSources()
  sources.set(overlayPath, validOverlay().replace(
    'AppThemeSurfaceResolver.disabledSystemMaterial()',
    'this.native ? AppThemeSurfaceResolver.disabledSystemMaterial() : undefined'
  ))
  assert.throws(
    () => validateNativeThemePageContracts(sources, validContract()),
    /conditional systemMaterial option is forbidden/
  )
})

test('rejects material attributes and options outside their exact owners', () => {
  const attributeContract = validContract()
  attributeContract.attributeOwners[0].method = 'missingOwner'
  assert.throws(
    () => validateNativeThemePageContracts(validSources(), attributeContract),
    /unlisted \.systemMaterial attribute owner/
  )

  const optionContract = validContract()
  optionContract.optionOwners[0].method = 'missingOwner'
  assert.throws(
    () => validateNativeThemePageContracts(validSources(), optionContract),
    /unlisted systemMaterial option owner/
  )
})

test('rejects a ContentGroup material on a registered plain content group', () => {
  const sources = validSources()
  sources.set(pagePath, validPage().replace(
    '.backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())',
    '.systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.ContentGroup))'
  ))
  assert.throws(
    () => validateNativeThemePageContracts(sources, validContract()),
    /plain Native surface must not use \.systemMaterial/
  )
})

test('requires the semantic content group background on a registered plain group', () => {
  const sources = validSources()
  sources.set(pagePath, validPage().replace(
    '.backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())',
    '.backgroundColor(Color.Transparent)'
  ))
  assert.throws(
    () => validateNativeThemePageContracts(sources, validContract()),
    /plain content group must use contentGroupBackground/
  )
})

for (const attribute of ['border', 'borderWidth', 'borderColor']) {
  test('rejects .' + attribute + ' on a registered borderless Native control', () => {
    const sources = validSources()
    sources.set(pagePath, validPage().replace(
      "    Text('chip').backgroundColor(Color.Transparent)",
      "    Text('chip').backgroundColor(Color.Transparent)." + attribute + '(1)'
    ))
    assert.throws(
      () => validateNativeThemePageContracts(sources, validContract()),
      new RegExp('borderless Native control must not use \\.' + attribute)
    )
  })
}

test('allows only an exact top-level @Builder function material owner', () => {
  assert.doesNotThrow(() => validateNativeThemePageContracts(validSources(), validContract()))

  const sources = validSources()
  sources.set(globalPath, validGlobalBuilder().replace('@Builder\n', ''))
  assert.throws(
    () => validateNativeThemePageContracts(sources, validContract()),
    /\.systemMaterial must belong to an @Builder method/
  )
})

for (const color of [
  'bg_main',
  'bg_1',
  'bg_2',
  'component_bg',
  'menu_item_bg',
  'bg_tab',
  'bg_tab_item',
  'tab'
]) {
  test('rejects ' + color + ' as a Native root background', () => {
    const sources = validSources()
    sources.set(pagePath, validPage().replace(
      '.backgroundColor(this.pageBackground())',
      ".backgroundColor($r('app.color." + color + "'))"
    ))
    assert.throws(
      () => validateNativeThemePageContracts(sources, validContract()),
      /Native root must stay transparent/
    )
  })
}

test('rejects delegating a Native root background to cardColor', () => {
  const sources = validSources()
  sources.set(pagePath, validPage().replace(
    '.backgroundColor(this.pageBackground())',
    '.backgroundColor(this.cardColor())'
  ))
  assert.throws(
    () => validateNativeThemePageContracts(sources, validContract()),
    /Native root must stay transparent/
  )
})

test('allows an opaque background only on the Feiniu side of a root conditional', () => {
  const nativePredicateSources = validSources()
  nativePredicateSources.set(pagePath, validPage().replace(
    '.backgroundColor(this.pageBackground())',
    ".backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_main'))"
  ))
  assert.doesNotThrow(() =>
    validateNativeThemePageContracts(nativePredicateSources, validContract()))

  const legacyPredicateSources = validSources()
  legacyPredicateSources.set(pagePath, validPage().replace(
    '.backgroundColor(this.pageBackground())',
    ".backgroundColor(this.showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)"
  ))
  assert.doesNotThrow(() =>
    validateNativeThemePageContracts(legacyPredicateSources, validContract()))
})

test('allows a non-theme root conditional when both branches stay Native-transparent', () => {
  const sources = validSources()
  sources.set(pagePath, validPage().replace(
    '.backgroundColor(this.pageBackground())',
    '.backgroundColor(this.showRouteActionBar ? ' +
      'AppThemeSurfaceResolver.routeBackground(this.themeStyle, true) : Color.Transparent)'
  ))
  assert.doesNotThrow(() => validateNativeThemePageContracts(sources, validContract()))
})

test('rejects a root conditional whose Native branch keeps the old background', () => {
  const sources = validSources()
  sources.set(pagePath, validPage().replace(
    '.backgroundColor(this.pageBackground())',
    ".backgroundColor(this.useNativeSurface() ? $r('app.color.bg_main') : Color.Transparent)"
  ))
  assert.throws(
    () => validateNativeThemePageContracts(sources, validContract()),
    /Native root must stay transparent/
  )
})

test('rejects a reversed root conditional even when its Feiniu branch calls routeBackground', () => {
  const sources = validSources()
  sources.set(pagePath, validPage().replace(
    '.backgroundColor(this.pageBackground())',
    ".backgroundColor(this.useNativeSurface() ? $r('app.color.bg_main') : " +
      'AppThemeSurfaceResolver.routeBackground(this.themeStyle, true))'
  ))
  assert.throws(
    () => validateNativeThemePageContracts(sources, validContract()),
    /Native root must stay transparent/
  )
})

test('rejects cardColor when its Native branch returns an opaque legacy color', () => {
  const sources = validSources()
  sources.set(pagePath, validPage().replace(
    "this.useNativeSurface() ? Color.Transparent : $r('app.color.component_bg')",
    "this.useNativeSurface() ? $r('app.color.component_bg') : Color.Transparent"
  ))
  assert.throws(
    () => validateNativeThemePageContracts(sources, validContract()),
    /Native cardColor returns an opaque legacy color/
  )
})

test('allows an old color only on the Feiniu side of a Native conditional', () => {
  const sources = validSources()
  sources.set(pagePath, validPage().replace(
    "Column() {}.backgroundColor($r('app.color.bg_1'))",
    "Column() {}.backgroundColor(this.useNativeSurface() ? Color.Transparent : $r('app.color.bg_1'))"
  ))
  assert.doesNotThrow(() => validateNativeThemePageContracts(sources, validContract()))
})

test('rejects legacy borders and hardcoded placeholders in audited Native paths', () => {
  for (const legacyVisual of [
    ".borderColor($r('app.color.border'))",
    ".borderColor($r('app.color.border_1'))",
    ".backgroundColor('#CBD2D3')"
  ]) {
    const sources = validSources()
    sources.set(pagePath, validPage().replace(
      '  build() {',
      '  @Builder\n  private sharedCard() { Row() {}' + legacyVisual + ' }\n  build() {'
    ))
    assert.throws(
      () => validateNativeThemePageContracts(sources, validContract()),
      /opaque legacy background can reach the Native theme: .*#sharedCard/
    )
  }
})

test('audits explicitly registered shared component paths', () => {
  const sources = validSources()
  sources.set(sharedPath, [
    '@ComponentV2',
    'struct SharedCard {',
    '  build() {',
    "    Image('poster').backgroundColor($r('app.color.bg_2'))",
    '  }',
    '}'
  ].join('\n'))
  const contract = validContract()
  contract.visualAuditPaths.push(sharedPath)
  assert.throws(
    () => validateNativeThemePageContracts(sources, contract),
    /opaque legacy background can reach the Native theme: .*SharedCard#build/
  )
})

test('follows indirect visual helpers into the Native branch', () => {
  const sources = validSources()
  sources.set(pagePath, validPage().replace(
    '  build() {',
    [
      '  private sharedOutline(): ResourceColor {',
      "    return this.useNativeSurface() ? $r('app.color.border') : Color.Transparent",
      '  }',
      '  @Builder',
      '  private sharedCard() { Row() {}.borderColor(this.sharedOutline()) }',
      '  build() {'
    ].join('\n')
  ))
  assert.throws(
    () => validateNativeThemePageContracts(sources, validContract()),
    /opaque legacy background can reach the Native theme: .*#sharedCard/
  )
})

for (const [method, kind] of [
  ['playerSurface', 'player'],
  ['mediaPlaceholder', 'media'],
  ['openDangerousDialog', 'dangerous-dialog']
]) {
  test(kind + ' exception is required by its exact file and method', () => {
    const contract = validContract()
    contract.opaqueBackgroundExceptions = contract.opaqueBackgroundExceptions
      .filter((entry) => entry.method !== method)
    assert.throws(
      () => validateNativeThemePageContracts(validSources(), contract),
      new RegExp('opaque legacy background can reach the Native theme: .*#' + method)
    )
  })

  test(kind + ' exception does not exempt another method in the same file', () => {
    const sources = validSources()
    sources.set(pagePath, validPage().replace(
      '  build() {',
      "  @Builder\n  private unrelatedCard() { Column() {}.backgroundColor($r('app.color.bg_1')) }\n  build() {"
    ))
    assert.throws(
      () => validateNativeThemePageContracts(sources, validContract()),
      /opaque legacy background can reach the Native theme: .*#unrelatedCard/
    )
  })
}

test('rejects path-wide or unsupported opaque background exceptions', () => {
  const pathWide = validContract()
  pathWide.opaqueBackgroundExceptions[0] = { path: pagePath, kind: 'player' }
  assert.throws(
    () => validateNativeThemePageContracts(validSources(), pathWide),
    /require exact path, component, and method/
  )

  const unsupported = validContract()
  unsupported.opaqueBackgroundExceptions[0].kind = 'whole-page'
  assert.throws(
    () => validateNativeThemePageContracts(validSources(), unsupported),
    /requires an exact supported kind/
  )
})

test('rejects an exact but stale opaque background exception', () => {
  const contract = validContract()
  contract.opaqueBackgroundExceptions.push({
    path: pagePath,
    component: 'SamplePage',
    method: 'nativeCard',
    kind: 'media'
  })
  assert.throws(
    () => validateNativeThemePageContracts(validSources(), contract),
    /does not match a current legacy color/
  )
})
