import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const sourceRoot = path.resolve(scriptDir, '../entry/src/main/ets/features/management')

const filterSheets = [
  'ManagementUserFilterSheet.ets',
  'devices/ManagementDeviceFilterSheet.ets',
  'activity/ManagementActivityFilterSheet.ets'
]

const sheetHosts = [
  'ManagementUsersPage.ets',
  'devices/ManagementDevicesPage.ets',
  'activity/ManagementActivityPage.ets'
]

function source(relativePath) {
  return fs.readFileSync(path.join(sourceRoot, relativePath), 'utf8')
}

test('filter sheets rely on the platform close affordance', () => {
  for (const relativePath of filterSheets) {
    assert.doesNotMatch(source(relativePath), /sys\.symbol\.xmark/)
  }
})

test('sheet hosts own close and system material once', () => {
  for (const relativePath of sheetHosts) {
    const value = source(relativePath)
    assert.match(value, /showClose:\s*true/)
    assert.match(value, /systemMaterial:\s*AppThemeSurfaceResolver\.material\(AppThemeMaterialRole\.Floating\)/)
  }
})

test('filter sheet selects are edge aligned and use system menu material', () => {
  for (const relativePath of filterSheets) {
    const value = source(relativePath)
    assert.match(value, /menuAlign\(MenuAlignType\.END\)/)
    assert.match(value, /optionWidth\(OptionWidthMode\.FIT_TRIGGER\)/)
    assert.match(value, /menuSystemMaterial\(AppThemeSurfaceResolver\.material\(AppThemeMaterialRole\.Floating\)\)/)
  }
})

test('filter action rows do not stack a second floating material', () => {
  for (const relativePath of filterSheets) {
    const value = source(relativePath)
    const footerStart = value.lastIndexOf("Button($r('app.string.management_filter_reset'))")
    assert.notEqual(footerStart, -1)
    assert.doesNotMatch(value.slice(footerStart), /\.systemMaterial\(AppThemeSurfaceResolver\.material\(AppThemeMaterialRole\.Floating\)\)/)
  }
})
