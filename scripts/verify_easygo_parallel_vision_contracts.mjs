import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const MODULE_CONFIG = 'entry/src/main/module.json5'
const EASY_GO_PROFILE = 'entry/src/main/resources/base/profile/easy_go.json'
const ROUTER_CONSTS = 'entry/src/main/ets/common/RouterConsts.ets'

function requireValue(condition, message) {
  if (!condition) {
    throw new Error(message)
  }
}

export function validateEasyGoContracts({ moduleSource, easyGo, routerSource }) {
  requireValue(
    /["']easyGo["']\s*:\s*["']\$profile:easy_go["']/.test(moduleSource),
    'module.json5 must reference $profile:easy_go'
  )

  requireValue(easyGo.common === undefined, 'EasyGo must not apply through common')
  requireValue(easyGo.phone === undefined, 'EasyGo must not override phone behavior')

  const display = easyGo.tablet?.displayModeOptions
  requireValue(display?.wideWindowMode === 'navigationSplit', 'wide window must use navigationSplit')
  requireValue(display?.squareWindowMode === 'navigationSplit', 'square window must use navigationSplit')

  const options = display?.navigationSplitOptions
  requireValue(options?.homePage === 'navBar', 'homePage must be navBar')
  requireValue(options?.homeNavigationId === 'mainNavigation', 'homeNavigationId must be mainNavigation')
  requireValue(options?.mode === 1, 'mode must be navigation mode 1')
  requireValue(options?.enableReducedContainerSize === true, 'reduced container size must be enabled')
  requireValue(options?.supportLandscapeFullscreen === true, 'landscape requests must exit split mode')
  requireValue(options?.wideSplit?.ratio === '1 | 2', 'wide split ratio must be 1 | 2 on API 26+')
  requireValue(options?.squareSplit?.ratio === '1 | 1', 'square split ratio must be 1 | 1 on API 26+')

  const fullscreen = new Set(options?.fullScreenPages ?? [])
  requireValue(fullscreen.has('PlayerPage'), 'fullScreenPages must include PlayerPage')
  requireValue(fullscreen.has('LiveTvPage'), 'fullScreenPages must include LiveTvPage')
  requireValue(fullscreen.size === 2, 'fullScreenPages must contain only the two approved player routes')

  requireValue(
    /static\s+readonly\s+PlayerPage\s*=\s*['"]PlayerPage['"]/.test(routerSource),
    'RouterConsts.PlayerPage must remain PlayerPage'
  )
  requireValue(
    /static\s+readonly\s+LiveTvPage\s*=\s*['"]LiveTvPage['"]/.test(routerSource),
    'RouterConsts.LiveTvPage must remain LiveTvPage'
  )
}

export function defaultWorkspaceRoot() {
  return resolve(dirname(fileURLToPath(import.meta.url)), '..')
}

export function validateWorkspaceEasyGoContracts(workspaceRoot = defaultWorkspaceRoot()) {
  validateEasyGoContracts({
    moduleSource: readFileSync(resolve(workspaceRoot, MODULE_CONFIG), 'utf8'),
    easyGo: JSON.parse(readFileSync(resolve(workspaceRoot, EASY_GO_PROFILE), 'utf8')),
    routerSource: readFileSync(resolve(workspaceRoot, ROUTER_CONSTS), 'utf8')
  })
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspaceEasyGoContracts()
  console.log('EasyGo Parallel Vision contracts passed.')
}
