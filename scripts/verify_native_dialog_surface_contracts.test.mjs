import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../entry/src/main/ets')

function source(path) {
  return readFileSync(resolve(root, path), 'utf8')
}

function bracedBlock(value, openingBrace, label) {
  assert.ok(openingBrace >= 0, `missing conditional block: ${label}`)
  let depth = 0
  for (let index = openingBrace; index < value.length; index += 1) {
    if (value[index] === '{') {
      depth += 1
    } else if (value[index] === '}') {
      depth -= 1
      if (depth === 0) {
        return { body: value.slice(openingBrace + 1, index), end: index }
      }
    }
  }
  assert.fail(`unterminated conditional block: ${label}`)
}

function conditionalBlock(value, marker) {
  const markerIndex = value.indexOf(marker)
  assert.ok(markerIndex >= 0, `missing conditional marker: ${marker}`)
  return bracedBlock(value, value.indexOf('{', markerIndex), marker)
}

function methodBody(value, name) {
  const signature = new RegExp(`\\b(?:private\\s+)?${name}\\s*\\([^)]*\\)[^{]*\\{`)
  const match = signature.exec(value)
  assert.ok(match, `missing method: ${name}`)
  return bracedBlock(value, value.indexOf('{', match.index), name).body
}

function followingElseBlock(value, blockEnd) {
  const tail = value.slice(blockEnd + 1)
  const match = /^\s*else\s*\{/.exec(tail)
  assert.ok(match, 'missing capability fallback branch')
  const openingBrace = blockEnd + 1 + match[0].lastIndexOf('{')
  return bracedBlock(value, openingBrace, 'capability fallback')
}

test('management sheets keep system material out of the blur fallback branch', () => {
  for (const [path, ownerMethod] of [
    ['features/management/ManagementUsersPage.ets', 'filterSheetOptions'],
    ['features/management/devices/ManagementDevicesPage.ets', 'pageContent'],
    ['features/management/activity/ManagementActivityPage.ets', 'pageContent']
  ]) {
    const value = source(path)
    const owner = methodBody(value, ownerMethod)
    const blurBranch = conditionalBlock(owner, 'OverlayMaterialDecision.UseBlurFallback')
    assert.match(blurBranch.body, /backgroundColor:\s*Color\.Transparent/)
    assert.doesNotMatch(blurBranch.body, /systemMaterial\s*:/)

    const materialBranch = conditionalBlock(owner, 'OverlayMaterialDecision.UseFloatingMaterial')
    assert.match(materialBranch.body, /backgroundColor:\s*Color\.Transparent/)
    assert.match(materialBranch.body,
      /systemMaterial:\s*AppThemeSurfaceResolver\.material\(AppThemeMaterialRole\.Floating\)/)

    const disabledBranch = conditionalBlock(owner, 'OverlayMaterialDecision.DisableSystemMaterial')
    assert.match(disabledBranch.body, /backgroundColor:\s*\$r\('app\.color\.bg_1'\)/)
    assert.match(disabledBranch.body,
      /systemMaterial:\s*AppThemeSurfaceResolver\.disabledSystemMaterial\(\)/)
    assert.match(value, /\.bindSheet\(/)
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

test('AlertDialogV2 custom-dialog hosts gate the API 26-only option by material capability', () => {
  for (const [path, gate] of [
    ['features/search/SearchPage.ets', 'if (this.vm.appUIState.systemMaterialAvailable)'],
    ['features/setting/account/AccountPage.ets', 'if (this.appUIState.systemMaterialAvailable)']
  ]) {
    const value = source(path)
    const materialBranch = conditionalBlock(value, gate)
    assert.match(materialBranch.body, /openCustomDialog\(\{/)
    assert.match(materialBranch.body,
      /systemMaterial:\s*AppThemeSurfaceResolver\.disabledSystemMaterial\(\)/)
    const fallbackBranch = followingElseBlock(value, materialBranch.end)
    assert.match(fallbackBranch.body, /openCustomDialog\(\{/)
    assert.doesNotMatch(fallbackBranch.body, /systemMaterial\s*:/)
  }
})
