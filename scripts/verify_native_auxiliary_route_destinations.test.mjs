import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import test from 'node:test'
import {
  AUXILIARY_ROUTE_PATHS,
  defaultWorkspaceRoot,
  validateNativeAuxiliaryRouteDestinations
} from './verify_native_auxiliary_route_destinations.mjs'

const colorModePath = 'entry/src/main/ets/features/setting/ColorModePage.ets'
const accountPath = 'entry/src/main/ets/features/setting/account/AccountPage.ets'
const addGroupPath = 'entry/src/main/ets/features/addgroup/AddGroupPage.ets'
const privacyPath = 'entry/src/main/ets/features/splash/PrivacyPage.ets'
const userAgreementPath = 'entry/src/main/ets/features/setting/UserAgreementPage.ets'

function workspaceSources() {
  const root = defaultWorkspaceRoot()
  return new Map(AUXILIARY_ROUTE_PATHS.map((path) => [
    path,
    readFileSync(resolve(root, path), 'utf8')
  ]))
}

test('accepts the six API 26 Native auxiliary route migrations', () => {
  assert.doesNotThrow(() => validateNativeAuxiliaryRouteDestinations(workspaceSources()))
})

test('requires every migrated route to use the shared destination', () => {
  const sources = workspaceSources()
  sources.set(colorModePath, sources.get(colorModePath).replace('  useNavDst: true\n', ''))
  assert.throws(
    () => validateNativeAuxiliaryRouteDestinations(sources),
    /opt out of HMRouter NavDestination wrapping/
  )
})

test('rejects a custom ActionBar moved into the Native branch', () => {
  const sources = workspaceSources()
  sources.set(addGroupPath, sources.get(addGroupPath)
    .replace('if (showLegacyActionBar) {\n        ActionBar', 'if (!showLegacyActionBar) {\n        ActionBar'))
  assert.throws(
    () => validateNativeAuxiliaryRouteDestinations(sources),
    /custom ActionBar must stay inside the legacy branch/
  )
})

test('rejects an opaque Native route root', () => {
  const sources = workspaceSources()
  sources.set(privacyPath, sources.get(privacyPath).replace(
    "showLegacyActionBar ? $r('app.color.start_window_background') : Color.Transparent",
    "$r('app.color.start_window_background')"
  ))
  assert.throws(
    () => validateNativeAuxiliaryRouteDestinations(sources),
    /route root must be transparent in Native/
  )
})

test('keeps the Native account add action in the content area', () => {
  const sources = workspaceSources()
  sources.set(accountPath, sources.get(accountPath).replace('        this.nativeAddAccountEntry()\n', ''))
  assert.throws(
    () => validateNativeAuxiliaryRouteDestinations(sources),
    /Native account content must render the add-account entry/
  )
})

test('preserves the UserAgreement route URL', () => {
  const sources = workspaceSources()
  sources.set(userAgreementPath, sources.get(userAgreementPath).replace("pageUrl: '/UserAgreement'", "pageUrl: '/Agreement'"))
  assert.throws(
    () => validateNativeAuxiliaryRouteDestinations(sources),
    /route URL changed/
  )
})

test('rejects manual top safe-area ownership inside Native content', () => {
  const sources = workspaceSources()
  sources.set(colorModePath, sources.get(colorModePath).replace(
    '.expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.BOTTOM])',
    '.expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.TOP, SafeAreaEdge.BOTTOM])'
  ))
  assert.throws(
    () => validateNativeAuxiliaryRouteDestinations(sources),
    /must not own the top safe area/
  )
})

test('updates the theme in place under the stable HDS destination', () => {
  const sources = workspaceSources()
  sources.set(colorModePath, sources.get(colorModePath).replace(
    '      this.appUIState.themeStyle = result.effectiveTheme\n',
    ''
  ))
  assert.throws(
    () => validateNativeAuxiliaryRouteDestinations(sources),
    /theme switching must update the shared theme in place under the stable HDS destination/
  )
})

test('rejects route mutations during a hot theme switch', () => {
  const sources = workspaceSources()
  sources.set(colorModePath, sources.get(colorModePath).replace(
    '      this.appUIState.themeStyle = result.effectiveTheme\n',
    '      HMRouterMgr.pop({ animator: false })\n' +
      '      this.appUIState.themeStyle = result.effectiveTheme\n'
  ))
  assert.throws(
    () => validateNativeAuxiliaryRouteDestinations(sources),
    /theme switching must update the shared theme in place under the stable HDS destination/
  )
})
