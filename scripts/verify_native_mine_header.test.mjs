import assert from 'node:assert/strict'
import { dirname, resolve } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import {
  defaultWorkspaceRoot,
  validateNativeMineHeader,
  validateWorkspace
} from './verify_native_mine_header.mjs'

const homeScreenPath = 'entry/src/main/ets/features/home/HomeScreen.ets'
const mineTabPath = 'entry/src/main/ets/features/home/minetab/MineTab.ets'
const minePagePath = 'entry/src/main/ets/features/home/minetab/MinePage.ets'

function validSources() {
  return new Map([
    [homeScreenPath, [
      'MineTab({',
      '  contentBottomInset,',
      '  compactTopInset: this.shell === HomeShellKind.PhoneNativeHds',
      '})'
    ].join('\n')],
    [mineTabPath, [
      '@Param compactTopInset: boolean = false',
      '@Param destinationOwnsTitleBar: boolean = false',
      'private topSpacerHeight(): number {',
      '  if (this.destinationOwnsTitleBar) {',
      '    return 0',
      '  }',
      '  if (this.compactTopInset) {',
      '    return Math.max(0, this.vm.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT',
      '  }',
      '  return this.vm.appUIState.safeTop + UIConstants.ACTION_BAR_HEIGHT',
      '}',
      'Blank().height(this.topSpacerHeight())',
      'if (!this.compactTopInset && !this.destinationOwnsTitleBar) {',
      '  Stack() {}',
      '    .height(UIConstants.ACTION_BAR_HEIGHT + this.vm.appUIState.safeTop)',
      '  Row() {',
      '    ActionBar({ title: \'Mine\' })',
      '  }',
      '    .height(UIConstants.ACTION_BAR_HEIGHT + this.vm.appUIState.safeTop)',
      '}'
    ].join('\n')],
    [minePagePath, [
      'AppRouteDestination({',
      '  contentBuilder: () => {',
      '    MineTab({',
      '      fromHome: true,',
      '      destinationOwnsTitleBar: true',
      '    })',
      '  },',
      '  legacyContentBuilder: () => {',
      '    MineTab({',
      '      fromHome: true,',
      '      destinationOwnsTitleBar: false',
      '    })',
      '  }',
      '})'
    ].join('\n')]
  ])
}

test('accepts a native-phone-only compact Mine header', () => {
  assert.doesNotThrow(() => validateNativeMineHeader(validSources()))
})

test('derives the default workspace root from the verifier location', () => {
  const scriptsDirectory = dirname(fileURLToPath(import.meta.url))
  assert.equal(defaultWorkspaceRoot(), resolve(scriptsDirectory, '..'))
})

test('validates the current workspace', () => {
  assert.doesNotThrow(() => validateWorkspace())
})

test('keeps compact spacing opt-in', () => {
  const sources = validSources()
  sources.set(mineTabPath, validSources().get(mineTabPath)
    .replace('compactTopInset: boolean = false', 'compactTopInset: boolean = true'))
  assert.throws(
    () => validateNativeMineHeader(sources),
    /compact top inset must remain opt-in/
  )
})

test('rejects compact spacing outside the native phone shell', () => {
  const sources = validSources()
  sources.set(homeScreenPath, validSources().get(homeScreenPath)
    .replace('this.shell === HomeShellKind.PhoneNativeHds', 'true'))
  assert.throws(
    () => validateNativeMineHeader(sources),
    /only the API 26 native phone home shell/
  )
})

test('preserves covered content spacing when the root title bar owns the home chrome', () => {
  const sources = validSources()
  sources.set(mineTabPath, validSources().get(mineTabPath)
    .replace(
      '  if (this.compactTopInset) {\n' +
      '    return Math.max(0, this.vm.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT\n' +
      '  }\n',
      ''
    ))
  assert.throws(
    () => validateNativeMineHeader(sources),
    /preserve the covered content top inset/
  )
})

test('rejects a legacy header that is still constructed in compact mode', () => {
  const sources = validSources()
  sources.set(mineTabPath, validSources().get(mineTabPath)
    .replace('if (!this.compactTopInset && !this.destinationOwnsTitleBar) {', 'if (true) {'))
  assert.throws(
    () => validateNativeMineHeader(sources),
    /legacy header must have exactly one compact and destination ownership guard/
  )
})

test('rejects any legacy header node outside the compact-mode guard', () => {
  const sources = validSources()
  sources.set(mineTabPath, validSources().get(mineTabPath)
    .replace(
      '  Row() {\n    ActionBar({ title: \'Mine\' })\n  }\n' +
      '    .height(UIConstants.ACTION_BAR_HEIGHT + this.vm.appUIState.safeTop)\n}',
      '}\nRow() {\n  ActionBar({ title: \'Mine\' })\n}\n' +
      '  .height(UIConstants.ACTION_BAR_HEIGHT + this.vm.appUIState.safeTop)'
    ))
  assert.throws(
    () => validateNativeMineHeader(sources),
    /legacy MineTab header must not exist in compact mode/
  )
})

test('uses the system title only in the Native standalone MinePage branch', () => {
  const sources = validSources()
  sources.set(minePagePath, validSources().get(minePagePath)
    .replace('destinationOwnsTitleBar: true', 'destinationOwnsTitleBar: false'))
  assert.throws(
    () => validateNativeMineHeader(sources),
    /standalone MinePage must use the system title only in its Native destination/
  )
})

test('keeps the legacy standalone MinePage on its existing custom header', () => {
  const sources = validSources()
  sources.set(minePagePath, validSources().get(minePagePath)
    .replace('destinationOwnsTitleBar: false', 'destinationOwnsTitleBar: true'))
  assert.throws(
    () => validateNativeMineHeader(sources),
    /standalone MinePage must use the system title only in its Native destination/
  )
})
