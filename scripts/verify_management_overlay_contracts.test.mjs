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

const alignedSelectConsumers = [
  'ManagementUserProfileSection.ets',
  'ManagementUserParentalSection.ets'
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
    assert.match(value,
      /backgroundColor:\s*this\.useNativeMaterial\(\)\s*\?\s*Color\.Transparent\s*:\s*\$r\('app\.color\.bg_1'\)/)
    assert.match(value,
      /systemMaterial:\s*this\.useNativeMaterial\(\)\s*\?[\s\S]*AppThemeMaterialRole\.Floating[\s\S]*disabledSystemMaterial\(\)/)
  }
})

test('filter sheets use the constrained aligned selector instead of system Select', () => {
  for (const relativePath of filterSheets) {
    const value = source(relativePath)
    assert.match(value, /ManagementNativeSelect\(\{/)
    assert.doesNotMatch(value, /\bSelect\(\[/)
    assert.doesNotMatch(value, /OptionWidthMode\.FIT_TRIGGER/)
  }
})

test('shared management selector owns right aligned compact trigger and material menu', () => {
  const value = source('ManagementNativeSelect.ets')
  assert.match(value, /valueTextAlign:\s*TextAlign\s*=\s*TextAlign\.End/)
  assert.match(value, /menuPlacement:\s*Placement\s*=\s*Placement\.BottomRight/)
  assert.match(value, /selectorWidth:\s*Length\s*=\s*'auto'/)
  assert.match(value, /\.width\(this\.selectorWidth\)/)
  assert.match(value, /\.constraintSize\(\{ minWidth: 112, maxWidth: 220 \}\)/)
  assert.match(value, /\.constraintSize\(\{ minWidth: 72, maxWidth: 172 \}\)/)
  assert.doesNotMatch(value, /Text\(this\.selectedLabel\(\)\)[\s\S]*?\.layoutWeight\(1\)/)
  assert.doesNotMatch(value, /Row\(\{ space: 6 \}\)[\s\S]*?\.width\('100%'\)/)
  assert.match(value, /\.borderRadius\(8\)/)
  assert.match(value, /AppThemeMaterialRole\.AdaptiveInteractive/)
  assert.match(value, /AppThemeMaterialRole\.Floating/)
  assert.match(value, /\.bindMenu\(this\.menuItems\(\)/)
})

test('shared management selector marks the current menu item with a system checkmark', () => {
  const value = source('ManagementNativeSelect.ets')
  assert.match(value, /symbolIcon\?:\s*SymbolGlyphModifier/)
  assert.match(value, /new SymbolGlyphModifier\(\$r\('sys\.symbol\.checkmark'\)\)/)
  assert.match(value, /selected \? this\.primaryTextColor\(\) : Color\.Transparent/)
  assert.match(value, /this\.menuIcon\(index === this\.selectedIndex\)/)
  assert.doesNotMatch(value, /index === this\.selectedIndex \?.*?: undefined/)
})

test('native user management actions use capsule material controls without visible danger outlines', () => {
  const listPage = source('ManagementUsersPage.ets')
  const detailPage = source('ManagementUserDetailPage.ets')
  assert.match(listPage, /\.borderRadius\(18\)/)
  assert.match(listPage, /\.borderRadius\(20\)\s*\n\s*\.systemMaterial/)
  assert.match(detailPage, /\.borderRadius\(this\.useNativeMaterial\(\) \? 22 : 10\)/)
  assert.match(detailPage, /\.borderRadius\(this\.useNativeMaterial\(\) \? 21 : 8\)/)
  assert.match(detailPage, /AppThemeMaterialRole\.AdaptiveInteractive/)
  assert.match(detailPage,
    /width:\s*AppThemeSurfaceResolver\.outlineWidth\([\s\S]*?nativeThemeAvailable,\s*1\)/)
})

test('native user creation keeps fields quiet and uses capsule material actions', () => {
  const value = source('ManagementUserCreatePage.ets')
  assert.ok((value.match(/sys\.color\.comp_background_tertiary/g) || []).length >= 2)
  assert.match(value, /\.borderRadius\(23\)\s*\n\s*\.systemMaterial\(AppThemeSurfaceResolver\.material/)
  assert.match(value, /\.borderRadius\(this\.useNativeMaterial\(\) \? 20 : 9\)/)
  assert.match(value, /AppThemeMaterialRole\.AdaptiveInteractive/)
  assert.match(value, /AppThemeMaterialRole\.PrimaryInteractive/)
})

test('native device management uses quiet fields and capsule material actions', () => {
  const listPage = source('devices/ManagementDevicesPage.ets')
  const detailPage = source('devices/ManagementDeviceDetailPage.ets')
  const detailPanel = source('devices/ManagementDeviceDetailPanel.ets')
  assert.ok((listPage.match(/\.borderRadius\(18\)/g) || []).length >= 2)
  assert.match(listPage, /AppThemeMaterialRole\.AdaptiveInteractive/)
  assert.match(detailPage, /\.borderRadius\(this\.useNativeMaterial\(\) \? 20 : 10\)/)
  assert.match(detailPanel, /AppThemeSurfaceResolver\.iconWellBackground/)
  assert.match(detailPanel, /sys\.color\.comp_background_tertiary/)
  assert.match(detailPanel, /\.borderRadius\(21\)/)
  assert.match(detailPanel, /\.borderRadius\(22\)/)
})

test('native session detail keeps remote controls quiet and material actions capsule shaped', () => {
  const value = source('sessions/ManagementSessionDetailPage.ets')
  assert.match(value, /sys\.color\.comp_background_tertiary/)
  assert.ok((value.match(/\.borderRadius\(21\)/g) || []).length >= 3)
  assert.match(value, /AppThemeMaterialRole\.AdaptiveInteractive/)
  assert.match(value, /AppThemeMaterialRole\.PrimaryInteractive/)
})

test('native activity page uses capsule filters paging and primary media actions', () => {
  const value = source('activity/ManagementActivityPage.ets')
  assert.match(value, /\.borderRadius\(18\)/)
  assert.match(value, /\.borderRadius\(20\)/)
  assert.match(value, /sys\.symbol\.chevron_right'[\s\S]*?\.fontSize\(20\)/)
  assert.match(value, /\.borderRadius\(22\)[\s\S]*?AppThemeMaterialRole\.PrimaryInteractive/)
})

test('native task detail uses a compact icon well and material capsule actions', () => {
  const value = source('ManagementTaskDetailPage.ets')
  assert.match(value, /iconWellBackground[\s\S]*?\.borderRadius\(8\)/)
  assert.match(value, /\.borderRadius\(this\.useNativeMaterial\(\) \? 23 : 10\)/)
  assert.match(value, /this\.task\?\.busy \?[\s\S]*?AppThemeMaterialRole\.AdaptiveInteractive/)
  assert.match(value, /\.borderRadius\(this\.useNativeMaterial\(\) \? 19 : 12\)/)
})

test('native management action buttons are capsules while selectors remain compact', () => {
  const dashboard = source('ManagementDashboardPage.ets')
  const password = source('ManagementUserPasswordSection.ets')
  const userDetail = source('ManagementUserDetailPage.ets')
  const selector = source('ManagementNativeSelect.ets')
  assert.match(dashboard, /\.borderRadius\(this\.useNativeMaterial\(\) \? 22 : 12\)/)
  assert.match(dashboard, /\.borderRadius\(this\.useNativeMaterial\(\) \? 20 : 12\)/)
  assert.ok((password.match(/\.borderRadius\(this\.useNativeMaterial\(\) \? 21 :/g) || []).length >= 3)
  assert.match(userDetail, /\.borderRadius\(this\.useNativeMaterial\(\) \? 20 : 9\)/)
  assert.match(selector, /\.borderRadius\(8\)/)
})

test('management dashboard uses page refresh instead of repeated card refresh buttons', () => {
  const dashboard = source('ManagementDashboardPage.ets')
  const activity = source('activity/ManagementActivityDashboardSection.ets')
  const sessions = source('sessions/ManagementSessionsDashboardSection.ets')
  assert.match(dashboard, /\.onRefreshing\(\(\) => this\.refreshAll\(\)\)/)
  assert.doesNotMatch(dashboard, /this\.sectionHeader\([^\n]*management_running_tasks[\s\S]*?management_refresh/)
  assert.doesNotMatch(activity, /@Event onRefresh/)
  assert.doesNotMatch(activity, /sys\.symbol\.arrow_clockwise/)
  assert.doesNotMatch(sessions, /@Event onRefresh/)
  assert.doesNotMatch(sessions, /sys\.symbol\.arrow_clockwise/)
})

test('right side user detail selectors share the aligned selector', () => {
  for (const relativePath of alignedSelectConsumers) {
    assert.match(source(relativePath), /ManagementNativeSelect\(\{/)
  }
})

test('native filter groups drop opaque nested backgrounds and action buttons are capsules', () => {
  for (const relativePath of filterSheets) {
    const value = source(relativePath)
    assert.match(value, /backgroundColor\(this\.useNativeMaterial\(\)\s*\?\s*\n?\s*Color\.Transparent/)
    assert.ok((value.match(/\.borderRadius\(22\)/g) || []).length >= 2)
  }
})

test('filter sheets deduct horizontal padding and margins from percentage widths', () => {
  for (const relativePath of filterSheets) {
    const value = source(relativePath)
    assert.doesNotMatch(value, /\.width\('100%'\)\s*\n\s*\.padding\(\{ left:/)
    assert.doesNotMatch(value, /\.width\('100%'\)\s*\n(?:\s*\.[^\n]+\n)*\s*\.margin\(\{ left:/)
    assert.match(value, /\.width\('calc\(100% - 32vp\)'\)/)
  }
})

test('native transparent filter groups do not retain a second outer content gutter', () => {
  const userFilter = source('ManagementUserFilterSheet.ets')
  assert.match(userFilter,
    /left:\s*this\.useNativeMaterial\(\)\s*\?\s*0\s*:\s*16/)
  assert.match(userFilter,
    /right:\s*this\.useNativeMaterial\(\)\s*\?\s*0\s*:\s*16/)

  for (const relativePath of [
    'devices/ManagementDeviceFilterSheet.ets',
    'activity/ManagementActivityFilterSheet.ets'
  ]) {
    const value = source(relativePath)
    assert.match(value, /left:\s*this\.useNativeMaterial\(\)\s*\?\s*0\s*:\s*16/)
    assert.match(value, /right:\s*this\.useNativeMaterial\(\)\s*\?\s*0\s*:\s*16/)
    assert.match(value, /bottom:\s*this\.useNativeMaterial\(\)\s*\?\s*0\s*:\s*16/)
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
