import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const workspaceRoot = dirname(dirname(fileURLToPath(import.meta.url)))
const homeScreen = readFileSync(resolve(workspaceRoot,
  'entry/src/main/ets/features/home/HomeScreen.ets'), 'utf8')
const homeTab = readFileSync(resolve(workspaceRoot,
  'entry/src/main/ets/features/home/hometab/HomeTab.ets'), 'utf8')

test('scopes the pinned Home title-bar state to the active bottom tab', () => {
  assert.match(homeScreen,
    /HomeTab\s*\(\s*\{[\s\S]*active\s*:\s*this\.ui\.selectedDestination\s*===\s*HomeDestination\.Home/)
  assert.match(homeTab, /@Param\s+active\s*:\s*boolean\s*=\s*true/)
  assert.match(homeTab,
    /rootNavigationHomeLibraryPinned\s*=\s*this\.active\s*\?\s*this\.latestChipStripPinned\s*:\s*false/)
  assert.match(homeTab,
    /@Monitor\(\s*['"]active['"]\s*\)[\s\S]*onActiveChange\s*\(\s*\)[\s\S]*syncRootNavigationPinnedState\s*\(\s*\)/)
})
