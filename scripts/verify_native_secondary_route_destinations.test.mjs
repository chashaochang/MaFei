import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import test from 'node:test'
import {
  SECONDARY_ROUTE_PATHS,
  defaultWorkspaceRoot,
  validateNativeSecondaryRouteDestinations
} from './verify_native_secondary_route_destinations.mjs'

const settingPath = 'entry/src/main/ets/features/setting/SettingPage.ets'
const addAccountPath = 'entry/src/main/ets/features/setting/account/AddAccountPage.ets'
const connectPath = 'entry/src/main/ets/features/connect/ConnectScreen.ets'
const minePagePath = 'entry/src/main/ets/features/home/minetab/MinePage.ets'
const mineTabPath = 'entry/src/main/ets/features/home/minetab/MineTab.ets'
const webPath = 'entry/src/main/ets/features/web/WebITongPage.ets'

function workspaceSources() {
  const root = defaultWorkspaceRoot()
  return new Map(SECONDARY_ROUTE_PATHS.map((path) => [
    path,
    readFileSync(resolve(root, path), 'utf8')
  ]))
}

test('accepts the remaining Native secondary route migrations', () => {
  assert.doesNotThrow(() => validateNativeSecondaryRouteDestinations(workspaceSources()))
})

test('requires every secondary route to disable HMRouter destination wrapping', () => {
  const sources = workspaceSources()
  sources.set(settingPath, sources.get(settingPath).replace('  useNavDst: true\n', ''))
  assert.throws(
    () => validateNativeSecondaryRouteDestinations(sources),
    /opt out of HMRouter destination wrapping/
  )
})

test('keeps the Setting title row in the Feiniu branch', () => {
  const sources = workspaceSources()
  sources.set(settingPath, sources.get(settingPath)
    .replace('if (showLegacyActionBar) {', 'if (!showLegacyActionBar) {'))
  assert.throws(
    () => validateNativeSecondaryRouteDestinations(sources),
    /Setting legacy title row must be guarded/
  )
})

test('hides the AddAccount custom bar only in Native', () => {
  const sources = workspaceSources()
  sources.set(addAccountPath, sources.get(addAccountPath)
    .replace('this.pageContent(false)', 'this.pageContent(true)'))
  assert.throws(
    () => validateNativeSecondaryRouteDestinations(sources),
    /hide the custom bar only in its Native destination/
  )
})

test('requires ConnectScreen to guard its legacy ActionBar', () => {
  const sources = workspaceSources()
  sources.set(connectPath, sources.get(connectPath)
    .replace('if (this.showRouteActionBar) {', 'if (true) {'))
  assert.throws(
    () => validateNativeSecondaryRouteDestinations(sources),
    /ActionBar must be guarded by route ownership/
  )
})

test('uses the system title only in the Native standalone MinePage', () => {
  const sources = workspaceSources()
  sources.set(minePagePath, sources.get(minePagePath)
    .replace('destinationOwnsTitleBar: true', 'destinationOwnsTitleBar: false'))
  assert.throws(
    () => validateNativeSecondaryRouteDestinations(sources),
    /system title only in its Native destination/
  )
})

test('removes the manual Mine top inset from Native destination content', () => {
  const sources = workspaceSources()
  sources.set(mineTabPath, sources.get(mineTabPath)
    .replace('return 0', 'return this.vm.appUIState.safeTop'))
  assert.throws(
    () => validateNativeSecondaryRouteDestinations(sources),
    /must not add a second top inset/
  )
})

test('wires WebITong system back through browser history', () => {
  const sources = workspaceSources()
  sources.set(webPath, sources.get(webPath)
    .replace('      beforeBack: () => this.handleRouteBack(),\n', ''))
  assert.throws(
    () => validateNativeSecondaryRouteDestinations(sources),
    /destination title and back hook/
  )
})

test('rejects WebITong browser back that no longer consumes history', () => {
  const sources = workspaceSources()
  sources.set(webPath, sources.get(webPath)
    .replace('if (this.vm.controller?.accessBackward()) {', 'if (false) {'))
  assert.throws(
    () => validateNativeSecondaryRouteDestinations(sources),
    /consume browser history before route pop/
  )
})

test('keeps the WebITong bottom browser toolbar', () => {
  const sources = workspaceSources()
  sources.set(webPath, sources.get(webPath)
    .replaceAll('.backgroundBlurStyle(', '.opacity('))
  assert.throws(
    () => validateNativeSecondaryRouteDestinations(sources),
    /preserve its bottom browser control bar/
  )
})

test('keeps the WebITong fullscreen top inset in both route branches', () => {
  const sources = workspaceSources()
  sources.set(webPath, sources.get(webPath)
    .replace('this.pageContent(true)', 'this.pageContent(false)'))
  assert.throws(
    () => validateNativeSecondaryRouteDestinations(sources),
    /destination title and back hook/
  )
})
