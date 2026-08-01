import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import test from 'node:test'
import {
  defaultWorkspaceRoot,
  validateNativeConnectingVisualContracts,
  validateNativeLoginVisualContracts
} from './verify_native_login_visual_contracts.mjs'

function workspaceSource() {
  return readFileSync(resolve(
    defaultWorkspaceRoot(),
    'entry/src/main/ets/features/connect/ConnectScreen.ets'
  ), 'utf8')
}

function connectingWorkspaceSource() {
  return readFileSync(resolve(
    defaultWorkspaceRoot(),
    'entry/src/main/ets/features/connect/ConnectingScreen.ets'
  ), 'utf8')
}

test('accepts the Native login visual refresh', () => {
  assert.doesNotThrow(() => validateNativeLoginVisualContracts(workspaceSource()))
})

test('gives the Native connecting screen an opaque canvas', () => {
  assert.doesNotThrow(() => validateNativeConnectingVisualContracts(connectingWorkspaceSource()))
})

test('rejects a transparent Native connecting screen', () => {
  const source = connectingWorkspaceSource().replace(
    "this.useNativeSurface() ? $r('app.color.native_canvas_background') :",
    'this.useNativeSurface() ? Color.Transparent :'
  )
  assert.throws(
    () => validateNativeConnectingVisualContracts(source),
    /opaque Native canvas/
  )
})

test('keeps Feiniu artwork out of the Native connecting content', () => {
  const source = connectingWorkspaceSource().replace(
    "SymbolGlyph($r('sys.symbol.externaldrive'))",
    "Image($r('app.media.nas'))"
  )
  assert.throws(
    () => validateNativeConnectingVisualContracts(source),
    /system status visuals/
  )
})

test('rejects material on the whole Native form', () => {
  const source = workspaceSource().replace(
    '.margin({ top: 20 })',
    `.systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.ContentGroup))
    .margin({ top: 20 })`
  )
  assert.throws(
    () => validateNativeLoginVisualContracts(source),
    /form container must stay transparent/
  )
})

test('requires compact Native fields', () => {
  const source = workspaceSource().replaceAll(
    'this.nativeSurface ? 52 : 56',
    '56'
  )
  assert.throws(
    () => validateNativeLoginVisualContracts(source),
    /52vp height and 14vp radius/
  )
})

test('keeps the fixed blue button exclusive to Feiniu', () => {
  const source = workspaceSource().replace(
    'this.nativeSurface ? Color.Transparent : 0x317aff',
    '0x317aff'
  )
  assert.throws(
    () => validateNativeLoginVisualContracts(source),
    /material capsule/
  )
})

test('preserves login and discovery actions', () => {
  const source = workspaceSource().replace(
    'this.vm.onLogin(this.isFromAddAccount)',
    'this.vm.init()'
  )
  assert.throws(
    () => validateNativeLoginVisualContracts(source),
    /preserve the login callback/
  )
})

test('keeps the Feiniu server-history bitmap', () => {
  const source = workspaceSource().replace("Image($r('app.media.history'))", 'Blank()')
  assert.throws(
    () => validateNativeLoginVisualContracts(source),
    /Feiniu keeps its bitmap/
  )
})
