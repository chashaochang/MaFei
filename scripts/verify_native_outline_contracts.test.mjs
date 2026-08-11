import assert from 'node:assert/strict'
import test from 'node:test'
import {
  defaultWorkspaceRoot,
  validateNativeOutlineContracts,
  validateWorkspace
} from './verify_native_outline_contracts.mjs'

const SURFACE_RESOLVER = 'entry/src/main/ets/theme/AppThemeSurfaceResolver.ets'
const VIDEO_DETAIL = 'entry/src/main/ets/features/videodetail/VideoDetailPage.ets'

const TARGETS = [
  ['entry/src/main/ets/component/BaseItemCard.ets', [0.5], true],
  ['entry/src/main/ets/component/HorizontalVideoCard.ets', [0.5], false],
  ['entry/src/main/ets/component/VerticalVideoCard.ets', [0.5], false],
  ['entry/src/main/ets/features/home/hometab/HomeTab.ets', [0.5], false],
  ['entry/src/main/ets/features/videolist/VideoListPage.ets', [0.5], true],
  ['entry/src/main/ets/features/livetv/LiveTvChannelListPage.ets', [0.5], true],
  ['entry/src/main/ets/features/seasondetail/SeasonDetailPage.ets', [0.5, 0.5], true],
  ['entry/src/main/ets/features/cast/CastDetailPage.ets', [0.5, 1], false],
  [VIDEO_DETAIL, [1, 1, 1, 1], false]
]

const resolver = `
export class AppThemeSurfaceResolver {
  static outlineWidth(style: ThemeStyle, available: boolean, feiniuWidth: number): number {
    return AppThemeSurfaceResolver.useNativeSurface(style, available) ? 0 : feiniuWidth
  }
}
`

function outlineCall(width) {
  return `AppThemeSurfaceResolver.outlineWidth(this.themeStyle, this.nativeThemeAvailable, ${width})`
}

function sourceFor(path, widths, whiteFrame) {
  if (path === VIDEO_DETAIL) {
    return [
      `.borderWidth(${outlineCall(1)})`,
      ".borderColor(this.ui.isChasing ? $r('app.color.color_main') : AppThemeSurfaceResolver.contentOutline(",
      '  this.themeStyle, this.nativeThemeAvailable))',
      `.borderWidth(${outlineCall(1)})`,
      ".borderColor(index === this.ui.selectedMediaSourceIndex ? $r('app.color.color_main') :",
      '  AppThemeSurfaceResolver.contentOutline(this.themeStyle, this.nativeThemeAvailable))',
      ...Array.from({ length: 2 }, () => [
        `.borderWidth(${outlineCall(1)})`,
        '.borderColor(AppThemeSurfaceResolver.contentOutline(this.themeStyle, this.nativeThemeAvailable))'
      ]).flat()
    ].join('\n')
  }

  const outlines = widths.map((width, index) => [
    `.borderWidth(${outlineCall(width)})`,
    `.borderColor(AppThemeSurfaceResolver.${index === widths.length - 1 && width === 1 ?
      'contentOutline' : 'mediaOutline'}(this.themeStyle, this.nativeThemeAvailable))`
  ]).flat()
  if (whiteFrame) {
    outlines.push('.borderWidth(1)', '.borderColor(Color.White)')
  }
  return outlines.join('\n')
}

function validSources() {
  return new Map([
    [SURFACE_RESOLVER, resolver],
    ...TARGETS.map(([path, widths, whiteFrame]) => [path, sourceFor(path, widths, whiteFrame)])
  ])
}

test('accepts Native-zero decorative widths, Feiniu legacy widths, and semantic outlines', () => {
  assert.doesNotThrow(() => validateNativeOutlineContracts(validSources()))
})

test('rejects a resolver that removes Feiniu outlines', () => {
  const sources = validSources()
  sources.set(SURFACE_RESOLVER, resolver.replace('? 0 : feiniuWidth', '? 0 : 0'))
  assert.throws(() => validateNativeOutlineContracts(sources), /zero only for an available Native surface/)
})

test('rejects a hard-coded media width', () => {
  const sources = validSources()
  const path = TARGETS[0][0]
  sources.set(path, sources.get(path).replace(`.borderWidth(${outlineCall(0.5)})`, '.borderWidth(0.5)'))
  assert.throws(() => validateNativeOutlineContracts(sources), /preserve Feiniu outline widths/)
})

test('rejects removal of a semantic white label frame', () => {
  const sources = validSources()
  const path = TARGETS[0][0]
  sources.set(path, sources.get(path).replace('.borderColor(Color.White)', '.borderColor(Color.Transparent)'))
  assert.throws(() => validateNativeOutlineContracts(sources), /semantic white label frames/)
})

test('rejects restoring a selected Native outline', () => {
  const sources = validSources()
  sources.set(VIDEO_DETAIL, sources.get(VIDEO_DETAIL).replace(
    `.borderWidth(${outlineCall(1)})`,
    `.borderWidth(this.ui.isChasing ? 1 : ${outlineCall(1)})`
  ))
  assert.throws(() => validateNativeOutlineContracts(sources), /must not restore decorative outlines/)
})

test('validates the current workspace and resolves its default root', () => {
  assert.equal(defaultWorkspaceRoot().endsWith('/ohosApp'), true)
  assert.doesNotThrow(() => validateWorkspace())
})
