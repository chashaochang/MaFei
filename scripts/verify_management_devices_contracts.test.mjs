import assert from 'node:assert/strict'
import test from 'node:test'
import {
  loadManagementDeviceSources,
  managementDevicePaths,
  validateManagementDevices
} from './verify_management_devices_contracts.mjs'

function validSources() {
  return loadManagementDeviceSources()
}

test('accepts the current management device implementation', () => {
  assert.doesNotThrow(() => validateManagementDevices(validSources()))
})

test('rejects AccessToken in stable device files', () => {
  const sources = validSources()
  sources.set(managementDevicePaths.models,
    sources.get(managementDevicePaths.models) + '\nAccessToken: string')
  assert.throws(() => validateManagementDevices(sources), /AccessToken/)
})

test('requires current-device rejection in all three layers', () => {
  for (const path of [
    managementDevicePaths.viewModel,
    managementDevicePaths.repository,
    managementDevicePaths.service
  ]) {
    const sources = validSources()
    sources.set(path, sources.get(path).replaceAll('assertNotCurrentDevice', 'skipCurrentCheck'))
    assert.throws(() => validateManagementDevices(sources), /current device rejection/)
  }
})

test('rejects query-string device mutations', () => {
  const sources = validSources()
  sources.set(managementDevicePaths.service,
    sources.get(managementDevicePaths.service)
      .replace('${api.basePath}/Devices/Options', '${api.basePath}/Devices?Id=${deviceId}'))
  assert.throws(() => validateManagementDevices(sources), /query strings/)
})

test('requires fail-closed behavior when current device id is missing', () => {
  const sources = validSources()
  sources.set(managementDevicePaths.protection,
    sources.get(managementDevicePaths.protection)
      .replace('if (!currentDeviceId) return []', 'return candidateIds')
      .replace('!currentDeviceId || deviceId === currentDeviceId',
        'deviceId === currentDeviceId'))
  assert.throws(() => validateManagementDevices(sources), /fail closed/)
})

test('requires device detail permission, confirmation, and terminal states', () => {
  for (const marker of [
    "@Monitor('ui.permissionState')",
    'management_device_login_will_expire',
    'ManagementSectionStatus.Unsupported'
  ]) {
    const sources = validSources()
    sources.set(managementDevicePaths.detailPage,
      sources.get(managementDevicePaths.detailPage).replace(marker, 'missingMarker'))
    assert.throws(() => validateManagementDevices(sources))
  }
})

test('requires dangerous device confirmations to use OverlayPolicy', () => {
  for (const path of [managementDevicePaths.listPage, managementDevicePaths.detailPage]) {
    const sources = validSources()
    sources.set(path,
      sources.get(path).replace('OverlaySurfaceRole.Dangerous',
        'OverlaySurfaceRole.PlatformDefault'))
    assert.throws(() => validateManagementDevices(sources), /dangerous-overlay policy/)
  }
})

test('rejects direct SDK version guards in device confirmations', () => {
  const sources = validSources()
  sources.set(managementDevicePaths.detailPage,
    sources.get(managementDevicePaths.detailPage) + '\nif (deviceInfo.sdkApiVersion >= 26) {}')
  assert.throws(() => validateManagementDevices(sources), /must not hard-code/)
})
