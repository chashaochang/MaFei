import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

export const managementIntegrationPaths = Object.freeze({
  router: 'entry/src/main/ets/common/RouterConsts.ets',
  dashboard: 'entry/src/main/ets/features/management/ManagementDashboardPage.ets',
  dashboardState: 'entry/src/main/ets/features/management/ManagementDashboardUIState.ets',
  dashboardViewModel: 'entry/src/main/ets/features/management/ManagementDashboardViewModel.ets',
  apiService: 'entry/src/main/ets/features/management/ManagementApiService.ets',
  repository: 'entry/src/main/ets/features/management/ManagementRepository.ets',
  models: 'entry/src/main/ets/features/management/ManagementModels.ets',
  sessionDetailPage:
    'entry/src/main/ets/features/management/sessions/ManagementSessionDetailPage.ets',
  devicesPage:
    'entry/src/main/ets/features/management/devices/ManagementDevicesPage.ets',
  deviceDetailPage:
    'entry/src/main/ets/features/management/devices/ManagementDeviceDetailPage.ets',
  activityPage:
    'entry/src/main/ets/features/management/activity/ManagementActivityPage.ets',
  legacySessionDetailPage:
    'entry/src/main/ets/features/management/ManagementSessionDetailPage.ets',
  listTest: 'entry/src/test/List.test.ets',
  baseStrings: 'entry/src/main/resources/base/element/string.json',
  zhCnStrings: 'entry/src/main/resources/zh_CN/element/string.json',
  enUsStrings: 'entry/src/main/resources/en_US/element/string.json'
})

const loadedSourcePaths = Object.freeze([
  managementIntegrationPaths.router,
  managementIntegrationPaths.dashboard,
  managementIntegrationPaths.dashboardState,
  managementIntegrationPaths.dashboardViewModel,
  managementIntegrationPaths.apiService,
  managementIntegrationPaths.repository,
  managementIntegrationPaths.models,
  managementIntegrationPaths.sessionDetailPage,
  managementIntegrationPaths.devicesPage,
  managementIntegrationPaths.deviceDetailPage,
  managementIntegrationPaths.activityPage,
  managementIntegrationPaths.listTest,
  managementIntegrationPaths.baseStrings,
  managementIntegrationPaths.zhCnStrings,
  managementIntegrationPaths.enUsStrings
])

const routedPages = Object.freeze([
  [managementIntegrationPaths.sessionDetailPage, 'ManagementSessionDetailPage'],
  [managementIntegrationPaths.devicesPage, 'ManagementDevicesPage'],
  [managementIntegrationPaths.deviceDetailPage, 'ManagementDeviceDetailPage'],
  [managementIntegrationPaths.activityPage, 'ManagementActivityPage']
])

function requiredSource(sources, path) {
  const source = sources.get(path)
  if (source === undefined) {
    throw new Error('missing source: ' + path)
  }
  return source
}

function requirePattern(sources, path, pattern, message) {
  if (!pattern.test(requiredSource(sources, path))) {
    throw new Error(message + ': ' + path)
  }
}

function rejectPattern(sources, path, pattern, message) {
  if (pattern.test(requiredSource(sources, path))) {
    throw new Error(message + ': ' + path)
  }
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function requireRegisteredTestSuite(sources, moduleName, message) {
  const source = requiredSource(sources, managementIntegrationPaths.listTest)
  const importPattern = new RegExp(
    `import\\s+([A-Za-z_$][\\w$]*)\\s+from\\s+['"]\\.\\/${escapeRegExp(moduleName)}\\.test['"]\\s*;?`
  )
  const match = source.match(importPattern)
  if (!match) {
    throw new Error(
      message + ' import is missing: ' + managementIntegrationPaths.listTest
    )
  }

  const callPattern = new RegExp(
    `\\b${escapeRegExp(match[1])}\\s*\\(\\s*\\)\\s*;?`
  )
  if (!callPattern.test(source)) {
    throw new Error(
      message + ' call is missing: ' + managementIntegrationPaths.listTest
    )
  }
}

function managementLocaleKeys(sources, path) {
  let document
  try {
    document = JSON.parse(requiredSource(sources, path))
  } catch (error) {
    throw new Error('invalid locale JSON: ' + path + ': ' + error.message)
  }

  if (!document || !Array.isArray(document.string)) {
    throw new Error('locale string array is missing: ' + path)
  }

  const names = document.string
    .map((entry) =>
      entry && typeof entry.name === 'string' ? entry.name : ''
    )
    .filter((name) =>
      /^management_(?:session|sessions|device|devices|activity)(?:_|$)/.test(name)
    )

  const seen = new Set()
  const duplicates = new Set()
  for (const name of names) {
    if (seen.has(name)) {
      duplicates.add(name)
    }
    seen.add(name)
  }

  if (duplicates.size > 0) {
    throw new Error(
      'duplicate management session/device/activity locale keys: ' +
        path +
        ': ' +
        Array.from(duplicates).sort().join(', ')
    )
  }

  return seen
}

function requireEqualKeySets(reference, candidate, candidatePath) {
  const missing = Array.from(reference)
    .filter((name) => !candidate.has(name))
    .sort()
  const extra = Array.from(candidate)
    .filter((name) => !reference.has(name))
    .sort()

  if (missing.length > 0 || extra.length > 0) {
    throw new Error(
      'management session/device/activity locale key set mismatch: ' +
        candidatePath +
        '; missing=' +
        missing.join(',') +
        '; extra=' +
        extra.join(',')
    )
  }
}

export function validateManagementDeviceSessionActivityIntegration(
  sources,
  legacySessionDetailExists = false
) {
  for (const path of loadedSourcePaths) {
    requiredSource(sources, path)
  }

  for (const [path, routeName] of routedPages) {
    const escapedRouteName = escapeRegExp(routeName)

    requirePattern(
      sources,
      managementIntegrationPaths.router,
      new RegExp(
        `static\\s+readonly\\s+${escapedRouteName}\\s*=\\s*['"]${escapedRouteName}['"]`
      ),
      'management route constant is missing'
    )
    requirePattern(
      sources,
      path,
      new RegExp(
        `@HMRouter\\s*\\(\\s*\\{[\\s\\S]*?pageUrl\\s*:\\s*RouterConsts\\.${escapedRouteName}` +
          `[\\s\\S]*?useNavDst\\s*:\\s*true[\\s\\S]*?\\}\\s*\\)`
      ),
      'management route page registration is missing'
    )
    requirePattern(
      sources,
      path,
      /\bManagementPermissionState\b/,
      'management page must own administrator permission state'
    )
    requirePattern(
      sources,
      path,
      /@Monitor\s*\(\s*['"]ui\.permissionState['"]\s*\)/,
      'management page must monitor administrator permission'
    )
    requirePattern(
      sources,
      path,
      /ManagementPermissionState\.Denied/,
      'management page must handle denied administrator permission'
    )
    requirePattern(
      sources,
      path,
      /HMRouterMgr\.pop\s*\(/,
      'management page must exit after administrator permission is denied'
    )
  }

  requirePattern(
    sources,
    managementIntegrationPaths.dashboard,
    /import\s*\{\s*ManagementSessionsDashboardSection\s*\}\s*from\s*['"]\.\/sessions\/ManagementSessionsDashboardSection['"]/,
    'dashboard must import the sessions section'
  )
  requirePattern(
    sources,
    managementIntegrationPaths.dashboard,
    /\bManagementSessionsDashboardSection\s*\(\s*\{/,
    'dashboard must mount the sessions section'
  )
  requirePattern(
    sources,
    managementIntegrationPaths.dashboard,
    /import\s*\{\s*ManagementActivityDashboardSection\s*\}\s*from\s*['"]\.\/activity\/ManagementActivityDashboardSection['"]/,
    'dashboard must import the activity section'
  )
  requirePattern(
    sources,
    managementIntegrationPaths.dashboard,
    /\bManagementActivityDashboardSection\s*\(\s*\{/,
    'dashboard must mount the activity section'
  )
  requirePattern(
    sources,
    managementIntegrationPaths.dashboard,
    /HMRouterMgr\.to\s*\(\s*RouterConsts\.ManagementSessionDetailPage\s*\)[\s\S]*?\.withParam\s*\(\s*\{\s*sessionId\s*:\s*session\.id\s*\}\s*\)\s*\.push\s*\(/,
    'session detail routing must pass sessionId only'
  )
  rejectPattern(
    sources,
    managementIntegrationPaths.dashboard,
    /\.withParam\s*\(\s*\{\s*session\s*:/,
    'dashboard must not route a session snapshot'
  )

  if (legacySessionDetailExists) {
    throw new Error(
      'legacy root session detail page must be absent: ' +
        managementIntegrationPaths.legacySessionDetailPage
    )
  }

  rejectPattern(
    sources,
    managementIntegrationPaths.dashboard,
    /\.slice\s*\(\s*0\s*,\s*3\s*\)/,
    'dashboard must not restore the legacy three-session slice'
  )
  rejectPattern(
    sources,
    managementIntegrationPaths.repository,
    /\.slice\s*\(\s*0\s*,\s*6\s*\)/,
    'root management repository must not restore the legacy six-activity slice'
  )
  rejectPattern(
    sources,
    managementIntegrationPaths.apiService,
    /(?:[?&]Limit=12\b|\bLimit\s*:\s*12\b|\blimit\s*:\s*12\b)/,
    'root management service must not restore the legacy activity Limit=12 request'
  )
  rejectPattern(
    sources,
    managementIntegrationPaths.dashboardViewModel,
    /\b(?:sessionTimer|startSessionPolling|refreshSessions|refreshActivities|sessionsLoading|activitiesLoading)\b/,
    'dashboard view model must not own legacy session or activity polling'
  )
  rejectPattern(
    sources,
    managementIntegrationPaths.apiService,
    /\b(?:getSessions|getActivityLog)\s*\(/,
    'root management service must not own session or activity requests'
  )
  rejectPattern(
    sources,
    managementIntegrationPaths.repository,
    /\b(?:loadSessionsSection|loadActivitiesSection)\s*\(/,
    'root management repository must not own session or activity sections'
  )
  rejectPattern(
    sources,
    managementIntegrationPaths.models,
    /\bclass\s+(?:ManagementSessionItem|ManagementActivityItem)\b/,
    'root management models must not own session or activity models'
  )
  rejectPattern(
    sources,
    managementIntegrationPaths.dashboardState,
    /\b(?:sessionsStatus|activitiesStatus|sessionsLoading|activitiesLoading)\b/,
    'dashboard state must not own session or activity feature state'
  )

  const baseKeys = managementLocaleKeys(
    sources,
    managementIntegrationPaths.baseStrings
  )
  const zhCnKeys = managementLocaleKeys(
    sources,
    managementIntegrationPaths.zhCnStrings
  )
  const enUsKeys = managementLocaleKeys(
    sources,
    managementIntegrationPaths.enUsStrings
  )

  requireEqualKeySets(
    baseKeys,
    zhCnKeys,
    managementIntegrationPaths.zhCnStrings
  )
  requireEqualKeySets(
    baseKeys,
    enUsKeys,
    managementIntegrationPaths.enUsStrings
  )

  requireRegisteredTestSuite(
    sources,
    'ManagementSessions',
    'management sessions suite'
  )
  requireRegisteredTestSuite(
    sources,
    'ManagementDevices',
    'management devices suite'
  )
  requireRegisteredTestSuite(
    sources,
    'ManagementActivity',
    'management activity suite'
  )
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function loadManagementDeviceSessionActivityIntegrationSources(
  workspaceRoot = defaultWorkspaceRoot()
) {
  const sources = new Map()
  for (const path of loadedSourcePaths) {
    sources.set(path, readFileSync(resolve(workspaceRoot, path), 'utf8'))
  }
  return sources
}

export function runManagementDeviceSessionActivityIntegrationValidation(
  workspaceRoot = defaultWorkspaceRoot()
) {
  const sources =
    loadManagementDeviceSessionActivityIntegrationSources(workspaceRoot)
  const legacySessionDetailExists = existsSync(
    resolve(
      workspaceRoot,
      managementIntegrationPaths.legacySessionDetailPage
    )
  )
  validateManagementDeviceSessionActivityIntegration(
    sources,
    legacySessionDetailExists
  )
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  runManagementDeviceSessionActivityIntegrationValidation()
}
