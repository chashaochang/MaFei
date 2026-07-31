import assert from 'node:assert/strict'
import test from 'node:test'
import {
  loadManagementDeviceSessionActivityIntegrationSources,
  managementIntegrationPaths,
  runManagementDeviceSessionActivityIntegrationValidation,
  validateManagementDeviceSessionActivityIntegration
} from './verify_management_device_session_activity_integration.mjs'

function validSources() {
  return loadManagementDeviceSessionActivityIntegrationSources()
}

function replaceRequired(source, before, after) {
  const mutated = source.replace(before, after)
  assert.notEqual(mutated, source, `fixture did not contain: ${before}`)
  return mutated
}

test('accepts the current management device session activity integration', () => {
  assert.doesNotThrow(() =>
    runManagementDeviceSessionActivityIntegrationValidation()
  )
})

test('rejects a missing management route', () => {
  const sources = validSources()
  sources.set(
    managementIntegrationPaths.router,
    replaceRequired(
      sources.get(managementIntegrationPaths.router),
      "static readonly ManagementActivityPage = 'ManagementActivityPage'",
      "static readonly RemovedManagementActivityPage = 'ManagementActivityPage'"
    )
  )

  assert.throws(
    () => validateManagementDeviceSessionActivityIntegration(sources),
    /route constant is missing/
  )
})

test('rejects a page without its administrator permission monitor', () => {
  const sources = validSources()
  sources.set(
    managementIntegrationPaths.sessionDetailPage,
    replaceRequired(
      sources.get(managementIntegrationPaths.sessionDetailPage),
      "@Monitor('ui.permissionState')",
      "@Monitor('ui.pageState')"
    )
  )

  assert.throws(
    () => validateManagementDeviceSessionActivityIntegration(sources),
    /monitor administrator permission/
  )
})

test('rejects session detail routing with more than sessionId', () => {
  const sources = validSources()
  sources.set(
    managementIntegrationPaths.dashboard,
    replaceRequired(
      sources.get(managementIntegrationPaths.dashboard),
      '.withParam({ sessionId: session.id })',
      '.withParam({ sessionId: session.id, session: session })'
    )
  )

  assert.throws(
    () => validateManagementDeviceSessionActivityIntegration(sources),
    /sessionId only/
  )
})

test('rejects mismatched management locale keys', () => {
  const sources = validSources()
  sources.set(
    managementIntegrationPaths.zhCnStrings,
    replaceRequired(
      sources.get(managementIntegrationPaths.zhCnStrings),
      '"name": "management_session_action_failed"',
      '"name": "management_session_action_failed_removed"'
    )
  )

  assert.throws(
    () => validateManagementDeviceSessionActivityIntegration(sources),
    /locale key set mismatch/
  )
})

test('rejects List.test without a management LocalUnit suite call', () => {
  const sources = validSources()
  sources.set(
    managementIntegrationPaths.listTest,
    replaceRequired(
      sources.get(managementIntegrationPaths.listTest),
      'managementDevicesTest();',
      ''
    )
  )

  assert.throws(
    () => validateManagementDeviceSessionActivityIntegration(sources),
    /management devices suite call is missing/
  )
})
