import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

export const tabletShellPaths = Object.freeze({
  entryAbility: 'entry/src/main/ets/entryability/EntryAbility.ets',
  homeShellPolicy: 'entry/src/main/ets/features/home/HomeShellPolicy.ets',
  homeScreen: 'entry/src/main/ets/features/home/HomeScreen.ets'
})

function requiredSource(sources, path) {
  const source = sources.get(path)
  if (source === undefined) {
    throw new Error('missing source: ' + path)
  }
  return source
}

function methodBlock(source, methodName) {
  const match = new RegExp('\\b' + methodName + '\\s*\\([^)]*\\)\\s*(?::\\s*[^\\{]+)?\\s*\\{')
    .exec(source)
  if (!match) {
    throw new Error('missing method: ' + methodName)
  }
  const openingBrace = source.indexOf('{', match.index)
  let depth = 0
  for (let index = openingBrace; index < source.length; index += 1) {
    if (source[index] === '{') {
      depth += 1
    } else if (source[index] === '}') {
      depth -= 1
      if (depth === 0) {
        return source.slice(openingBrace + 1, index)
      }
    }
  }
  throw new Error('unterminated method: ' + methodName)
}

export function validateTabletShellContracts(sources) {
  const entryAbility = requiredSource(sources, tabletShellPaths.entryAbility)
  const shellPolicy = requiredSource(sources, tabletShellPaths.homeShellPolicy)
  const homeScreen = requiredSource(sources, tabletShellPaths.homeScreen)

  const pxToVp = methodBlock(entryAbility, 'pxToVp')
  if (!/this\.contentUIContextReady/.test(pxToVp) ||
    !/windowClass\.getUIContext\(\)\.px2vp\(pixels\)/.test(pxToVp) ||
    !/pixels\s*\/\s*this\.resolveWindowDensity\(windowClass\)/.test(pxToVp)) {
    throw new Error('window metrics must use UIContext px2vp after content loads with a pre-content fallback')
  }
  if (/setDefaultDensityEnabled\s*\(/.test(entryAbility)) {
    throw new Error('tablet breakpoints must not lock the window density')
  }
  const loadContent = methodBlock(entryAbility, 'loadContent')
  const readyIndex = loadContent.indexOf('this.contentUIContextReady = true')
  const refreshIndex = loadContent.indexOf('this.refreshWindowMetrics(windowClass)')
  if (readyIndex < 0 || refreshIndex < 0 || readyIndex > refreshIndex) {
    throw new Error('content UIContext must become authoritative before refreshing window metrics')
  }

  if (!/breakpoint\s*===\s*BreakpointTypeEnum\.MD[\s\S]*breakpoint\s*===\s*BreakpointTypeEnum\.LG\s*&&\s*isPortraitViewport[\s\S]*return\s+HomeShellKind\.MediumDrawer/
    .test(shellPolicy)) {
    throw new Error('MD and portrait LG viewports must use the overlay drawer')
  }
  if (!/breakpoint\s*===\s*BreakpointTypeEnum\.LG[\s\S]*return\s+HomeShellKind\.LargeSidebar/
    .test(shellPolicy)) {
    throw new Error('landscape LG viewports must keep the embedded sidebar')
  }

  const shellVisibility = methodBlock(homeScreen, 'syncPadShellVisibility')
  if (!/this\.ui\.isMenuModalVisible\s*=\s*false/.test(shellVisibility) ||
    !/this\.ui\.isLeftSidebarVisible\s*=\s*shell\s*===\s*HomeShellKind\.LargeSidebar/.test(shellVisibility)) {
    throw new Error('breakpoint changes must close the drawer and expose the sidebar only for LG')
  }

  const legacyPadContent = methodBlock(homeScreen, 'legacyPadContent')
  if (!/if\s*\(shell\s*===\s*HomeShellKind\.LargeSidebar\)\s*\{[\s\S]*Blank\(\)\.width\(this\.ui\.isLeftSidebarVisible\s*\?\s*252\s*:\s*12\)/
    .test(legacyPadContent)) {
    throw new Error('only the LG shell may reserve embedded sidebar width')
  }

  const padChrome = methodBlock(homeScreen, 'padChromeBuilder')
  if (!/if\s*\(shell\s*===\s*HomeShellKind\.LargeSidebar\)[\s\S]*this\.ui\.isLeftSidebarVisible\s*=\s*true[\s\S]*else[\s\S]*this\.ui\.isMenuModalVisible\s*=\s*true/
    .test(padChrome)) {
    throw new Error('tablet menu button must open an embedded sidebar only for LG and an overlay drawer otherwise')
  }
  if (!/this\.ui\.isMenuModalVisible\s*&&\s*shell\s*===\s*HomeShellKind\.MediumDrawer[\s\S]*this\.menuModalBuilder\(\)/
    .test(padChrome)) {
    throw new Error('the overlay drawer must render only in the MD shell')
  }
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function readTabletShellWorkspace(root = defaultWorkspaceRoot()) {
  const sources = new Map()
  for (const path of Object.values(tabletShellPaths)) {
    sources.set(path, readFileSync(resolve(root, path), 'utf8'))
  }
  return sources
}

export function validateWorkspace(root = defaultWorkspaceRoot()) {
  validateTabletShellContracts(readTabletShellWorkspace(root))
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  validateWorkspace()
  console.log('Tablet shell contracts verified')
}
