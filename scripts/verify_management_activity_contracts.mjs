import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

export const managementActivityPaths = Object.freeze({
  dashboardPage: 'entry/src/main/ets/features/management/ManagementDashboardPage.ets',
  models: 'entry/src/main/ets/features/management/activity/ManagementActivityModels.ets',
  query: 'entry/src/main/ets/features/management/activity/ManagementActivityQuery.ets',
  service: 'entry/src/main/ets/features/management/activity/ManagementActivityApiService.ets',
  repository: 'entry/src/main/ets/features/management/activity/ManagementActivityRepository.ets',
  state: 'entry/src/main/ets/features/management/activity/ManagementActivityUIState.ets',
  viewModel: 'entry/src/main/ets/features/management/activity/ManagementActivityViewModel.ets',
  dashboardSection: 'entry/src/main/ets/features/management/activity/ManagementActivityDashboardSection.ets',
  page: 'entry/src/main/ets/features/management/activity/ManagementActivityPage.ets',
  baseStrings: 'entry/src/main/resources/base/element/string.json',
  zhStrings: 'entry/src/main/resources/zh_CN/element/string.json',
  enStrings: 'entry/src/main/resources/en_US/element/string.json'
})

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

export function validateManagementActivity(sources) {
  for (const path of Object.values(managementActivityPaths)) {
    requiredSource(sources, path)
  }

  requirePattern(sources, managementActivityPaths.repository, /request\.startIndex\s*=\s*0/,
    'dashboard activity query must start at zero')
  requirePattern(sources, managementActivityPaths.repository, /request\.limit\s*=\s*7/,
    'dashboard activity query must use Limit=7')
  requirePattern(sources, managementActivityPaths.repository,
    /nowMs\s*-\s*24\s*\*\s*60\s*\*\s*60\s*\*\s*1000/,
    'dashboard activity query must cover exactly the latest 24 hours')
  requirePattern(sources, managementActivityPaths.repository, /request\.hasUserId\s*=\s*true/,
    'dashboard activity query must keep user activity only')
  requirePattern(sources, managementActivityPaths.models,
    /MANAGEMENT_ACTIVITY_PAGE_SIZE\s*:\s*number\s*=\s*25/,
    'full activity pages must use 25 server records')
  requirePattern(sources, managementActivityPaths.query,
    /Math\.max\s*\(\s*0\s*,\s*pageIndex\s*\)\s*\*\s*MANAGEMENT_ACTIVITY_PAGE_SIZE/,
    'activity StartIndex must be pageIndex multiplied by page size')
  requirePattern(sources, managementActivityPaths.query,
    /ManagementActivityView\.User\)\s*return\s+true[\s\S]*ManagementActivityView\.System\)\s*return\s+false[\s\S]*return\s+undefined/,
    'All/User/System must map to omitted/true/false HasUserId')
  requirePattern(sources, managementActivityPaths.service,
    /if\s*\(\s*query\.hasUserId\s*!==\s*undefined\s*\)[\s\S]*params\.HasUserId/,
    'HasUserId must be omitted for the All view')
  requirePattern(sources, managementActivityPaths.repository,
    /totalRecordCount\s*=\s*response\.TotalRecordCount/,
    'activity pagination must retain the server total record count')
  requirePattern(sources, managementActivityPaths.repository,
    /ShortOverview\s*\|\|\s*entry\.Overview/,
    'activity mapping must fall back from ShortOverview to Overview')
  requirePattern(sources, managementActivityPaths.repository,
    /loadMediaTarget[\s\S]*service\.getItem/,
    'activity item actions must refetch the current media object')
  requirePattern(sources, managementActivityPaths.viewModel,
    /generation[\s\S]*isActive\s*\(\s*generation\s*\)/,
    'activity state must reject stale request generations')
  requirePattern(sources, managementActivityPaths.viewModel,
    /displayedPageIndex[\s\S]*retryPageIndex[\s\S]*stale/,
    'activity paging must retain the displayed page and retry target')
  requirePattern(sources, managementActivityPaths.dashboardSection,
    /ManagementSectionStatus\.Error[\s\S]*management_activity_failed[\s\S]*management_retry[\s\S]*onRetry\s*\(\s*\)/,
    'dashboard first-load errors must expose an explicit retry action')
  requirePattern(sources, managementActivityPaths.dashboardPage,
    /onRetry\s*:\s*\(\)\s*=>\s*this\.activityVM\.init\s*\(\s*\)/,
    'dashboard activity retry must recheck access and reinitialize loading')
  requirePattern(sources, managementActivityPaths.page,
    /staleNotice[\s\S]*management_stale_result[\s\S]*management_retry[\s\S]*loadPage\s*\(\s*this\.ui\.retryPageIndex\s*\)[\s\S]*if\s*\(\s*this\.ui\.stale\s*\)\s*this\.staleNotice\s*\(\s*\)/,
    'stale full-page activity must show its retry target')
  requirePattern(sources, managementActivityPaths.repository,
    /loadMediaTarget[\s\S]*ManagementErrorCode\.Unsupported[\s\S]*return\s+result/,
    'missing activity media must map to the unavailable result')
  requirePattern(sources, managementActivityPaths.page,
    /openMedia[\s\S]*try\s*\{[\s\S]*management_activity_item_unavailable[\s\S]*HMRouterMgr\.to[\s\S]*catch\s*\([^)]*\)[\s\S]*management_activity_item_failed/,
    'activity media loading and navigation must catch non-404 failures')
  requirePattern(sources, managementActivityPaths.page,
    /deviceInfo\.sdkApiVersion\s*>=\s*26[\s\S]*AppThemeSurfaceResolver\.material\s*\(\s*AppThemeMaterialRole\.Floating\s*\)[\s\S]*disabledSystemMaterial\s*\(\s*\)[\s\S]*else\s*\{[\s\S]*showDialog/,
    'activity overview dialog must use themed API 26 material with a legacy fallback')
  for (const path of [
    managementActivityPaths.baseStrings,
    managementActivityPaths.zhStrings,
    managementActivityPaths.enStrings
  ]) {
    requirePattern(sources, path, /"name"\s*:\s*"management_activity_item_failed"/,
      'activity media failure feedback must be localized')
  }
  requirePattern(sources, managementActivityPaths.query,
    /applyCurrentPage[\s\S]*filter[\s\S]*sort/,
    'activity filters must apply only to the current server page')
  for (const path of [managementActivityPaths.service, managementActivityPaths.repository]) {
    rejectPattern(sources, path, /\.delete\s*<|\.delete\s*\(|\/ActivityLog\/Entries\/Delete/,
      'activity management must not expose delete operations')
  }
  rejectPattern(sources, managementActivityPaths.page,
    /axiosInstance|\/System\/ActivityLog\/Entries/,
    'activity pages must not perform direct HTTP')
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function loadManagementActivitySources(workspaceRoot = defaultWorkspaceRoot()) {
  const sources = new Map()
  for (const path of Object.values(managementActivityPaths)) {
    sources.set(path, readFileSync(resolve(workspaceRoot, path), 'utf8'))
  }
  return sources
}

export function runManagementActivityValidation(workspaceRoot = defaultWorkspaceRoot()) {
  validateManagementActivity(loadManagementActivitySources(workspaceRoot))
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  runManagementActivityValidation()
}
