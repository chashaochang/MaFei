import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../entry/src/main/ets')

function source(path) {
  return readFileSync(resolve(root, path), 'utf8')
}

test('management sheets use one theme-scoped outer surface', () => {
  for (const path of [
    'features/management/ManagementUsersPage.ets',
    'features/management/devices/ManagementDevicesPage.ets',
    'features/management/activity/ManagementActivityPage.ets'
  ]) {
    const value = source(path)
    assert.match(value,
      /backgroundColor:\s*this\.useNativeMaterial\(\)\s*\?\s*Color\.Transparent\s*:\s*\$r\('app\.color\.bg_1'\)/)
    assert.match(value,
      /systemMaterial:\s*this\.useNativeMaterial\(\)\s*\?[\s\S]*AppThemeMaterialRole\.Floating[\s\S]*disabledSystemMaterial\(\)/)
  }
})

test('activity detail drops nested card backgrounds only inside the native sheet', () => {
  const value = source('features/management/activity/ManagementActivityPage.ets')
  assert.match(value,
    /detailGroupBackground\(inSheet:\s*boolean\)[\s\S]*inSheet\s*&&\s*this\.useNativeMaterial\(\)\s*\?[\s\S]*Color\.Transparent/)
  assert.match(value, /activityDetailBody\(this\.selectedItem!,\s*true\)/)
  assert.match(value, /activityDetailBody\(this\.selectedActivity\(\)!\)/)
  assert.ok((value.match(/backgroundColor\(this\.detailGroupBackground\(inSheet\)\)/g) || []).length === 2)
})

test('existing native sheet contents keep their inner surfaces transparent', () => {
  const filters = [
    'features/management/ManagementUserFilterSheet.ets',
    'features/management/devices/ManagementDeviceFilterSheet.ets',
    'features/management/activity/ManagementActivityFilterSheet.ets'
  ]
  for (const path of filters) {
    assert.match(source(path),
      /backgroundColor\(this\.useNativeMaterial\(\)\s*\?\s*\n?\s*Color\.Transparent/)
  }
  assert.match(source('features/videodetail/components/VideoInfoDetail.ets'),
    /useNativeSurface\([\s\S]*\?\s*\n?\s*Color\.Transparent\s*:\s*\$r\('app\.color\.bg_2'\)/)
  assert.match(source('features/videodetail/components/AudioSubtitleSelector.ets'),
    /backgroundColor\(this\.native\s*\?\s*Color\.Transparent\s*:\s*\$r\('app\.color\.bg_2'\)\)/)
})

test('AlertDialogV2 custom-dialog hosts disable the duplicate platform surface on API 26', () => {
  for (const path of [
    'features/search/SearchPage.ets',
    'features/setting/account/AccountPage.ets'
  ]) {
    const value = source(path)
    assert.match(value, /deviceInfo\.sdkApiVersion\s*>=\s*26/)
    assert.match(value, /openCustomDialog\(\{[\s\S]*systemMaterial:\s*AppThemeSurfaceResolver\.disabledSystemMaterial\(\)/)
  }
})
