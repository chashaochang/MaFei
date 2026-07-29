import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const COLOR_MODE = 'entry/src/main/ets/features/setting/ColorModePage.ets'
const ACCOUNT = 'entry/src/main/ets/features/setting/account/AccountPage.ets'
const SERVER_DISCOVERY = 'entry/src/main/ets/features/serverdiscovery/ServerDiscoveryPage.ets'
const ADD_GROUP = 'entry/src/main/ets/features/addgroup/AddGroupPage.ets'
const PRIVACY = 'entry/src/main/ets/features/splash/PrivacyPage.ets'
const USER_AGREEMENT = 'entry/src/main/ets/features/setting/UserAgreementPage.ets'

export const AUXILIARY_ROUTE_PATHS = [
  COLOR_MODE,
  ACCOUNT,
  SERVER_DISCOVERY,
  ADD_GROUP,
  PRIVACY,
  USER_AGREEMENT
]

const ROUTES = [
  {
    path: COLOR_MODE,
    pageUrl: /pageUrl\s*:\s*RouterConsts\.ColorModePage\b/,
    title: /title\s*:\s*\$r\(\s*['"]app\.string\.appearance_title['"]\s*\)/,
    legacyBackground: /\$r\(\s*['"]app\.color\.bg_main['"]\s*\)/,
    legacyNavigation: 'actionBar'
  },
  {
    path: ACCOUNT,
    pageUrl: /pageUrl\s*:\s*RouterConsts\.AccountPage\b/,
    title: /title\s*:\s*this\.fromLogin\s*\?\s*['"]历史登录['"]\s*:\s*['"]切换账号['"]/,
    legacyBackground: /\$r\(\s*['"]app\.color\.bg_main['"]\s*\)/,
    legacyNavigation: 'actionBar'
  },
  {
    path: SERVER_DISCOVERY,
    pageUrl: /pageUrl\s*:\s*RouterConsts\.ServerDiscoveryPage\b/,
    title: /title\s*:\s*['"]局域网内设备['"]/,
    legacyBackground: /\$r\(\s*['"]app\.color\.bg_main['"]\s*\)/,
    legacyNavigation: 'actionBar'
  },
  {
    path: ADD_GROUP,
    pageUrl: /pageUrl\s*:\s*RouterConsts\.AddGroupPage\b/,
    title: /title\s*:\s*['"]加入群聊['"]/,
    legacyBackground: /\$r\(\s*['"]app\.color\.bg_main['"]\s*\)/,
    legacyNavigation: 'actionBar'
  },
  {
    path: PRIVACY,
    pageUrl: /pageUrl\s*:\s*RouterConsts\.PrivacyPage\b/,
    title: /title\s*:\s*['"]隐私协议['"]/,
    legacyBackground: /\$r\(\s*['"]app\.color\.start_window_background['"]\s*\)/,
    legacyNavigation: 'actionBar'
  },
  {
    path: USER_AGREEMENT,
    pageUrl: /pageUrl\s*:\s*['"]\/UserAgreement['"]\s*(?:,|$)/,
    title: /title\s*:\s*['"]用户协议['"]/,
    legacyBackground: /['"]#101010['"]/,
    legacyNavigation: 'row'
  }
]

function requiredSource(sources, path) {
  const source = sources.get(path)
  if (source === undefined) {
    throw new Error('missing source: ' + path)
  }
  return source
}

function count(source, pattern) {
  return source.match(pattern)?.length ?? 0
}

function bracedBlock(source, openingBrace) {
  let depth = 0
  for (let index = openingBrace; index < source.length; index += 1) {
    if (source[index] === '{') {
      depth += 1
    } else if (source[index] === '}') {
      depth -= 1
      if (depth === 0) {
        return {
          body: source.slice(openingBrace + 1, index),
          end: index
        }
      }
    }
  }
  throw new Error('unterminated block')
}

function methodBlock(source, methodName) {
  const signature = new RegExp(
    '\\b(?:private\\s+)?' + methodName + '\\s*\\([^)]*\\)\\s*(?::\\s*[^\\{]+)?\\s*\\{'
  )
  const match = signature.exec(source)
  if (!match) {
    throw new Error('missing method: ' + methodName)
  }
  return bracedBlock(source, source.indexOf('{', match.index)).body
}

function routerAnnotation(source, path) {
  const match = /@HMRouter\s*\(\s*\{/.exec(source)
  if (!match) {
    throw new Error('missing HMRouter annotation: ' + path)
  }
  return bracedBlock(source, source.indexOf('{', match.index)).body
}

function legacyBranch(pageContent, path) {
  const match = /if\s*\(\s*showLegacyActionBar\s*\)\s*\{/.exec(pageContent)
  if (!match) {
    throw new Error('legacy navigation must be guarded by showLegacyActionBar: ' + path)
  }
  return bracedBlock(pageContent, pageContent.indexOf('{', match.index)).body
}

function validateRootBackground(pageContent, route) {
  const conditional = /\.backgroundColor\s*\(\s*showLegacyActionBar\s*\?\s*([\s\S]*?)\s*:\s*Color\.Transparent\s*\)/
    .exec(pageContent)
  if (!conditional || !route.legacyBackground.test(conditional[1])) {
    throw new Error('route root must be transparent in Native and preserve its legacy background: ' + route.path)
  }
}

function validateLegacyNavigation(pageContent, route) {
  const branch = legacyBranch(pageContent, route.path)
  if (route.legacyNavigation === 'actionBar') {
    if (count(pageContent, /\bActionBar\s*\(/g) !== 1 || !/\bActionBar\s*\(/.test(branch)) {
      throw new Error('custom ActionBar must stay inside the legacy branch: ' + route.path)
    }
    return
  }
  if (/\bActionBar\s*\(/.test(pageContent) || !/\bRow\s*\(\s*\)\s*\{/.test(branch) ||
    !/HMRouterMgr\.pop\s*\(\s*\)/.test(branch) || !/Text\s*\(\s*['"]用户协议['"]\s*\)/.test(branch) ||
    count(pageContent, /HMRouterMgr\.pop\s*\(/g) !== 1) {
    throw new Error('the UserAgreement legacy Row must be isolated from Native content')
  }
}

function validateAccountEntry(source, pageContent) {
  const guard = /if\s*\(\s*!\s*showLegacyActionBar\s*&&\s*!\s*this\.fromLogin\s*\)\s*\{/.exec(pageContent)
  if (!guard) {
    throw new Error('Native account content must guard the add-account entry')
  }
  const nativeBranch = bracedBlock(pageContent, pageContent.indexOf('{', guard.index)).body
  if (!/this\.nativeAddAccountEntry\s*\(\s*\)/.test(nativeBranch)) {
    throw new Error('Native account content must render the add-account entry')
  }
  const entry = methodBlock(source, 'nativeAddAccountEntry')
  if (!/Text\s*\(\s*['"]新增账号['"]\s*\)/.test(entry) ||
    !/sys\.symbol\.plus/.test(entry) || !/this\.openAddAccount\s*\(\s*\)/.test(entry)) {
    throw new Error('Native account add entry must keep a visible label, plus icon, and action')
  }
  const opener = methodBlock(source, 'openAddAccount')
  if (!/this\.fromLogin/.test(opener) ||
    !/HMRouterMgr\.to\s*\(\s*RouterConsts\.AddAccountPage\s*\)\.push\s*\(\s*\)/.test(opener)) {
    throw new Error('account add action must preserve the existing AddAccountPage route')
  }
  const legacy = legacyBranch(pageContent, ACCOUNT)
  if (!/rightBtnIcon\s*:\s*this\.fromLogin\s*\?\s*undefined\s*:\s*\$r\(\s*['"]app\.media\.add['"]\s*\)/.test(legacy) ||
    !/this\.openAddAccount\s*\(\s*\)/.test(legacy)) {
    throw new Error('Feiniu account ActionBar must retain the add-account action')
  }
}

function validateColorModeThemeSwitch(source) {
  const selectTheme = methodBlock(source, 'selectTheme')
  if (!/this\.appUIState\.themeStyle\s*=\s*result\.effectiveTheme/.test(selectTheme) ||
    /\bHMRouterMgr\b|\.(?:push|replace|pop)(?:Async)?\s*\(/.test(selectTheme)) {
    throw new Error('theme switching must update the shared theme in place under the stable HDS destination')
  }
}

function validateRoute(source, route) {
  const annotation = routerAnnotation(source, route.path)
  if (!route.pageUrl.test(annotation)) {
    throw new Error('route URL changed: ' + route.path)
  }
  if (!/\buseNavDst\s*:\s*true\b/.test(annotation)) {
    throw new Error('route page must opt out of HMRouter NavDestination wrapping: ' + route.path)
  }
  if (!/import\s*\{[^}]*\bAppRouteDestination\b[^}]*\}/.test(source) ||
    count(source, /\bAppRouteDestination\s*\(/g) !== 1) {
    throw new Error('route page must use exactly one AppRouteDestination: ' + route.path)
  }
  if (/@kit\.UIDesignKit|\bHdsNavDestination\s*\(|\bHdsNavigation\s*\(|\bNavDestination\s*\(|\bNavigation\s*\(/.test(source)) {
    throw new Error('route page must not construct a second navigation host: ' + route.path)
  }

  const pageContent = methodBlock(source, 'pageContent')
  validateLegacyNavigation(pageContent, route)
  validateRootBackground(pageContent, route)
  if (/\bSafeAreaEdge\.TOP\b|\bappUIState\.safeTop\b|\.safeAreaPadding\s*\(/.test(pageContent)) {
    throw new Error('Native route content must not own the top safe area: ' + route.path)
  }

  const build = methodBlock(source, 'build')
  if (!route.title.test(build) ||
    !/contentBuilder\s*:[\s\S]*this\.pageContent\s*\(\s*false\s*\)/.test(build) ||
    !/legacyContentBuilder\s*:[\s\S]*this\.pageContent\s*\(\s*true\s*\)/.test(build)) {
    throw new Error('route page must separate Native and legacy title ownership: ' + route.path)
  }
  if (route.path === ACCOUNT) {
    validateAccountEntry(source, pageContent)
  }
  if (route.path === COLOR_MODE) {
    validateColorModeThemeSwitch(source)
  }
}

export function validateNativeAuxiliaryRouteDestinations(sources) {
  ROUTES.forEach((route) => validateRoute(requiredSource(sources, route.path), route))
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

function main() {
  const root = process.argv[2] ? resolve(process.argv[2]) : defaultWorkspaceRoot()
  const sources = new Map(AUXILIARY_ROUTE_PATHS.map((path) => [
    path,
    readFileSync(resolve(root, path), 'utf8')
  ]))
  validateNativeAuxiliaryRouteDestinations(sources)
  process.stdout.write('Native auxiliary route destinations verified\n')
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main()
}
