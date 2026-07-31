import assert from 'node:assert/strict'
import test from 'node:test'
import {
  loadManagementActivitySources,
  managementActivityPaths,
  validateManagementActivity
} from './verify_management_activity_contracts.mjs'

function validSources() {
  return loadManagementActivitySources()
}

test('accepts the current management activity implementation', () => {
  assert.doesNotThrow(() => validateManagementActivity(validSources()))
})

test('requires the dashboard 24 hour user query', () => {
  const sources = validSources()
  sources.set(managementActivityPaths.repository,
    sources.get(managementActivityPaths.repository)
      .replace('request.limit = 7', 'request.limit = 6'))
  assert.throws(() => validateManagementActivity(sources), /Limit=7/)
})

test('requires HasUserId to be omitted for All', () => {
  const sources = validSources()
  sources.set(managementActivityPaths.query,
    sources.get(managementActivityPaths.query)
      .replace('return undefined', 'return true'))
  assert.throws(() => validateManagementActivity(sources), /omitted\/true\/false/)
})

test('rejects activity delete endpoints', () => {
  const sources = validSources()
  sources.set(managementActivityPaths.service,
    sources.get(managementActivityPaths.service) + '\nthis.axios().delete("/ActivityLog/Entries/Delete")')
  assert.throws(() => validateManagementActivity(sources), /delete operations/)
})

test('requires dashboard first-load retry to reinitialize activity', () => {
  const sources = validSources()
  sources.set(managementActivityPaths.dashboardPage,
    sources.get(managementActivityPaths.dashboardPage)
      .replace('onRetry: () => this.activityVM.init(),',
        'onRetry: () => this.activityVM.refresh(),'))
  assert.throws(() => validateManagementActivity(sources), /recheck access/)
})

test('requires stale full-page activity to expose retry', () => {
  const sources = validSources()
  sources.set(managementActivityPaths.page,
    sources.get(managementActivityPaths.page)
      .replace("$r('app.string.management_stale_result')",
        "$r('app.string.management_activity_empty')"))
  assert.throws(() => validateManagementActivity(sources), /stale full-page/)
})

test('requires non-404 media failures to be caught', () => {
  const sources = validSources()
  sources.set(managementActivityPaths.page,
    sources.get(managementActivityPaths.page)
      .replace("$r('app.string.management_activity_item_failed')",
        "$r('app.string.management_activity_item_unavailable')"))
  assert.throws(() => validateManagementActivity(sources), /catch non-404/)
})

test('requires the API 26 overview material guard', () => {
  const sources = validSources()
  sources.set(managementActivityPaths.page,
    sources.get(managementActivityPaths.page)
      .replace('deviceInfo.sdkApiVersion >= 26', 'deviceInfo.sdkApiVersion >= 25'))
  assert.throws(() => validateManagementActivity(sources), /themed API 26/)
})

test('requires localized media failure feedback', () => {
  const sources = validSources()
  sources.set(managementActivityPaths.enStrings,
    sources.get(managementActivityPaths.enStrings)
      .replace('"management_activity_item_failed"', '"management_activity_item_failure_missing"'))
  assert.throws(() => validateManagementActivity(sources), /localized/)
})
